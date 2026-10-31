package dev.settledlands.npc.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.NpcProfile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

public final class MobilityWandScreen extends Screen implements NpcUi.PopupHost {
    record GroupSummary(String id, String name, int pointCount) {}
    record GroupData(String id, String name, List<NpcProfile.MobilityPoint> points) {}

    private static final int TOPBAR_H = 34;

    private final int hand;
    private final List<GroupSummary> groups = new ArrayList<>();
    private GroupData current = new GroupData("", "", new ArrayList<>());

    // Graph Canvas & Viewport (Identical architecture to NpcDialogueGraphScreen)
    private final NpcGraphCanvas canvas = new NpcGraphCanvas();
    private final MobilityContextMenu contextMenu = new MobilityContextMenu();

    private int selectedPointIndex = -1;
    private int dragging = -1;
    private double dragMouseWorldX, dragMouseWorldY;
    private int dragOriginX, dragOriginY;

    private int linkFrom = -1;
    private int linkFromPort = 0;

    private boolean panning;
    private double panStartX, panStartY;
    private boolean focusOnInit = true;

    // Top Bar Widgets
    private NpcUi.NpcDropdown groupDropdown;
    private NpcUi.NpcDropdown openDropdown; // set by the dropdown itself only while its popup is open
    private EditBox groupNameBox;

    // Inspector Widgets + Labels (labels captured during layout, drawn in render)
    private final List<AbstractWidget> inspectorWidgets = new ArrayList<>();
    private final List<InspectLabel> inspectorLabels = new ArrayList<>();
    private int inspectorX, inspectorY, inspectorW, inspectorH;
    private int sidebarScroll, sidebarHeight;
    private boolean buildingInspector = false;

    private record InspectLabel(String text, int x, int y, int color) {}

    private MobilityWandScreen(CompoundTag data) {
        super(Component.translatable("gui.settledlands.mobility_wand.graph_title"));
        this.hand = data.getInt("Hand");
        readSnapshot(data);
    }

    public static void open(NpcNet.CfgView packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Screen scr = mc.screen;
        if (scr instanceof MobilityWandScreen mws && mws.hand == packet.data().getInt("Hand")) {
            mws.applySnapshot(packet.data());
        } else {
            mc.setScreen(new MobilityWandScreen(packet.data()));
        }
    }

    private void readSnapshot(CompoundTag tag) {
        this.groups.clear();
        ListTag list = tag != null ? tag.getList("MobilityGroups", 10) : new ListTag();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag g = list.getCompound(i);
            String id = g.getString("Id");
            String name = g.getString("Name");
            int count = Math.max(0, g.getInt("PointCount"));
            if (!id.isBlank()) {
                this.groups.add(new GroupSummary(id, name, count));
            }
        }
        CompoundTag currTag = tag != null ? tag.getCompound("CurrentGroup") : new CompoundTag();
        this.current = readCurrentGroup(currTag);
        ensureNodePositions();
    }

    private static GroupData readCurrentGroup(CompoundTag tag) {
        String id = tag.getString("Id");
        String name = tag.getString("Name");
        List<NpcProfile.MobilityPoint> pts = new ArrayList<>();
        ListTag ptList = tag.getList("Points", 10);
        for (int i = 0; i < ptList.size() && pts.size() < 32; i++) {
            CompoundTag pt = ptList.getCompound(i);
            int type = pt.contains("Type") ? pt.getInt("Type") : 0;
            String coords = NpcProfile.clean(pt.getString("Coordinates"), 64);
            int chance = pt.contains("Chance") ? NpcProfile.clamp(pt.getInt("Chance"), 0, 100) : 100;
            String dur = pt.contains("Duration") ? NpcProfile.clean(pt.getString("Duration"), 16) : "5-10";
            NpcProfile.MobilityPoint mp = new NpcProfile.MobilityPoint(coords, chance, dur);
            mp.type = type;
            mp.next = pt.contains("Next") ? pt.getInt("Next") : -1;
            mp.falseNext = pt.contains("FalseNext") ? pt.getInt("FalseNext") : -1;
            mp.randomMin = pt.contains("RandomMin") ? pt.getInt("RandomMin") : 1;
            mp.randomMax = pt.contains("RandomMax") ? pt.getInt("RandomMax") : 2;
            if (pt.contains("RandomBranches")) {
                for (int b : pt.getIntArray("RandomBranches")) mp.randomBranches.add(b);
            }
            mp.canvasX = pt.contains("CanvasX") ? pt.getInt("CanvasX") : 0;
            mp.canvasY = pt.contains("CanvasY") ? pt.getInt("CanvasY") : 0;
            mp.targetGroupId = pt.getString("TargetGroup");
            pts.add(mp);
        }
        return new GroupData(id, name, pts);
    }

    private void applySnapshot(CompoundTag tag) {
        int oldPointCount = this.current.points().size();
        readSnapshot(tag);
        if (this.selectedPointIndex >= this.current.points().size()) {
            this.selectedPointIndex = this.current.points().size() - 1;
        }
        if (this.current.points().size() != oldPointCount) {
            ensureNodePositions();
        }
        rebuildWidgets();
    }

    private void ensureNodePositions() {
        int count = this.current.points().size();
        if (count == 0) return;
        boolean anyNonZero = false;
        for (NpcProfile.MobilityPoint pt : this.current.points()) {
            if (pt.canvasX != 0 || pt.canvasY != 0) {
                anyNonZero = true;
                break;
            }
        }
        if (!anyNonZero) {
            double radius = Math.max(140.0, 45.0 * count);
            for (int i = 0; i < count; i++) {
                NpcProfile.MobilityPoint pt = this.current.points().get(i);
                if (count == 1) {
                    pt.canvasX = 40;
                    pt.canvasY = 40;
                } else {
                    double angle = (2.0 * Math.PI * i / count) - (Math.PI / 2.0);
                    pt.canvasX = (int) Math.round((Math.cos(angle) * radius) / 8.0) * 8;
                    pt.canvasY = (int) Math.round((Math.sin(angle) * radius) / 8.0) * 8;
                }
            }
        }
    }

    private boolean isInsideInspector(double x, double y) {
        return selectedPointIndex >= 0 && x >= inspectorX && x <= inspectorX + inspectorW && y >= inspectorY && y <= inspectorY + inspectorH;
    }

    private boolean isSpaceDown() {
        if (getFocused() instanceof EditBox) return false;
        try {
            long window = Minecraft.getInstance().getWindow().getWindow();
            if (window != 0 && InputConstants.isKeyDown(window, 32)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    protected void init() {
        canvas.bounds(0, TOPBAR_H, this.width, this.height);
        inspectorW = Math.min(230, Math.max(180, this.width / 4));
        inspectorX = this.width - inspectorW - 10;
        inspectorY = TOPBAR_H + 8;
        inspectorH = this.height - TOPBAR_H - 16;

        if (focusOnInit) {
            canvas.frameAllPoints(this.current.points());
            focusOnInit = false;
        }

        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();
        this.inspectorWidgets.clear();
        this.inspectorLabels.clear();

        inspectorW = Math.min(230, Math.max(180, this.width / 4));
        inspectorX = this.width - inspectorW - 10;
        inspectorY = TOPBAR_H + 8;
        inspectorH = this.height - TOPBAR_H - 16;
        canvas.bounds(0, TOPBAR_H, this.width, this.height);

        // --- Top bar, right side: [+ Добавить] [✕]
        Button closeBtn = Button.builder(Component.literal("✕"), b -> this.onClose())
                .bounds(this.width - 30, 6, 24, 20).build();
        this.addRenderableWidget(closeBtn);

        Component addLabel = Component.translatable("gui.settledlands.mobility_wand.add_short");
        int addW = Math.min(160, Math.max(96, this.font.width(addLabel) + 18));
        int addX = this.width - 34 - addW;
        Button addPointBtn = Button.builder(addLabel, b -> {
            Minecraft mc = Minecraft.getInstance();
            String pCoords = "";
            if (mc.player != null) {
                pCoords = ((int) Math.floor(mc.player.getX())) + " " +
                          ((int) Math.floor(mc.player.getY())) + " " +
                          ((int) Math.floor(mc.player.getZ()));
            }
            double centerWx = canvas.worldX((canvas.left + canvas.right) / 2.0);
            double centerWy = canvas.worldY((canvas.top + canvas.bottom) / 2.0);
            send("wand.node.add", 0, ((int) centerWx) + "," + ((int) centerWy), pCoords);
        }).bounds(addX, 6, addW, 20).build();
        this.addRenderableWidget(addPointBtn);

        // --- Top bar, left side: group dropdown (group add/delete live inside its entries)
        List<Component> entries = new ArrayList<>();
        entries.add(Component.translatable("gui.settledlands.mobility_wand.new_group"));
        int activeSel = 0;
        for (int i = 0; i < groups.size(); i++) {
            GroupSummary gs = groups.get(i);
            entries.add(Component.literal(gs.name() + " (" + gs.pointCount() + ")"));
            if (gs.id().equals(current.id())) {
                activeSel = i + 1;
            }
        }
        if (groups.isEmpty()) {
            entries.add(Component.translatable("gui.settledlands.graph.no_group"));
        }
        final int delIdx = entries.size();
        if (!current.id().isEmpty()) {
            entries.add(Component.translatable("gui.settledlands.mobility_wand.del_group"));
        }
        final int groupCount = groups.size();
        final boolean hasCurrent = !current.id().isEmpty();

        int dropdownW = Math.min(170, Math.max(110, this.width / 5));
        groupDropdown = new NpcUi.NpcDropdown(8, 6, dropdownW, entries, activeSel, idx -> {
            if (idx == 0) {
                send("wand.group.new", 0, "", "");
            } else if (hasCurrent && idx == delIdx) {
                send("wand.group.del", 0, "", "");
            } else if (idx >= 1 && idx <= groupCount) {
                send("wand.group.select", 0, groups.get(idx - 1).id(), "");
            }
        });
        this.addRenderableWidget(groupDropdown);

        // --- Group name field fills the gap between dropdown and the add button
        int nameX = 14 + dropdownW;
        int nameW = Math.min(220, Math.max(50, addX - nameX - 6));
        groupNameBox = new EditBox(this.font, nameX, 6, nameW, 20,
                Component.translatable("gui.settledlands.mobility_wand.group_name_hint"));
        groupNameBox.setHint(Component.translatable("gui.settledlands.mobility_wand.group_name_hint"));
        groupNameBox.setValue(current.name());
        groupNameBox.setResponder(name -> {
            if (!name.isBlank() && !name.equals(current.name())) {
                send("wand.group.name", 0, name, "");
            }
        });
        this.addRenderableWidget(groupNameBox);

        // --- Inspector Widgets if a node is selected
        if (selectedPointIndex >= 0 && selectedPointIndex < current.points().size()) {
            buildingInspector = true;
            buildInspectorWidgets();
            buildingInspector = false;
        }
    }

    private <T extends AbstractWidget> T addInspectorWidget(T widget) {
        this.addRenderableWidget(widget);
        this.inspectorWidgets.add(widget);
        return widget;
    }

    private void buildInspectorWidgets() {
        NpcProfile.MobilityPoint sel = current.points().get(selectedPointIndex);
        int inspX = inspectorX + 8;
        int fieldW = inspectorW - 16;
        int y = inspectorY + 28 - sidebarScroll;

        if (sel.type == NpcProfile.MobilityPoint.TYPE_POINT) {
            inspectorLabels.add(new InspectLabel(tr("gui.settledlands.mobility_wand.point_coords"), inspX, y, 0xff94a3b8));
            y += 12;
            EditBox coordsBox = new EditBox(this.font, inspX, y, fieldW, 18, Component.literal("Coords"));
            coordsBox.setValue(sel.coordinates);
            coordsBox.setResponder(t -> send("wand.point.coords", selectedPointIndex, t, ""));
            addInspectorWidget(coordsBox);
            y += 24;

            inspectorLabels.add(new InspectLabel(tr("gui.settledlands.mobility_wand.point_chance"), inspX, y, 0xff94a3b8));
            y += 12;
            EditBox chanceBox = new EditBox(this.font, inspX, y, fieldW, 18, Component.literal("Chance"));
            chanceBox.setValue(String.valueOf(sel.chance));
            chanceBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,3}"));
            chanceBox.setResponder(t -> send("wand.point.chance", selectedPointIndex, t, ""));
            addInspectorWidget(chanceBox);
            y += 22;

            inspectorLabels.add(new InspectLabel(tr("gui.settledlands.mobility_wand.point_duration"), inspX, y, 0xff94a3b8));
            y += 12;
            EditBox durBox = new EditBox(this.font, inspX, y, fieldW, 18, Component.literal("Duration"));
            durBox.setValue(sel.duration);
            durBox.setResponder(t -> send("wand.point.duration", selectedPointIndex, t, ""));
            addInspectorWidget(durBox);
            y += 24;

            int halfW = (fieldW - 4) / 2;
            Button upBtn = Button.builder(Component.literal("▲"), b -> {
                if (selectedPointIndex > 0) {
                    send("wand.point.up", selectedPointIndex, "", "");
                    selectedPointIndex--;
                    rebuildWidgets();
                }
            }).bounds(inspX, y, halfW, 18).build();
            addInspectorWidget(upBtn);

            Button downBtn = Button.builder(Component.literal("▼"), b -> {
                if (selectedPointIndex < current.points().size() - 1) {
                    send("wand.point.down", selectedPointIndex, "", "");
                    selectedPointIndex++;
                    rebuildWidgets();
                }
            }).bounds(inspX + halfW + 4, y, halfW, 18).build();
            addInspectorWidget(downBtn);
            y += 24;

            Button delBtn = Button.builder(Component.translatable("gui.settledlands.mobility_wand.del_point"), b -> {
                send("wand.point.del", selectedPointIndex, "", "");
                selectedPointIndex = -1;
                rebuildWidgets();
            }).bounds(inspX, y, fieldW, 18).build();
            addInspectorWidget(delBtn);

        } else if (sel.type == NpcProfile.MobilityPoint.TYPE_RANDOM) {
            inspectorLabels.add(new InspectLabel(tr("gui.settledlands.mobility_wand.random_min_label"), inspX, y, 0xff94a3b8));
            y += 12;
            EditBox minBox = new EditBox(this.font, inspX, y, fieldW, 18, Component.literal("Random Min"));
            minBox.setValue(String.valueOf(sel.randomMin));
            minBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,2}"));
            minBox.setResponder(t -> send("wand.node.random_min", selectedPointIndex, t, ""));
            addInspectorWidget(minBox);
            y += 22;

            inspectorLabels.add(new InspectLabel(tr("gui.settledlands.mobility_wand.random_max_label"), inspX, y, 0xff94a3b8));
            y += 12;
            EditBox maxBox = new EditBox(this.font, inspX, y, fieldW, 18, Component.literal("Random Max"));
            maxBox.setValue(String.valueOf(sel.randomMax));
            maxBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,2}"));
            maxBox.setResponder(t -> send("wand.node.random_max", selectedPointIndex, t, ""));
            addInspectorWidget(maxBox);
            y += 28;

            Button delBtn = Button.builder(Component.translatable("gui.settledlands.mobility_wand.del_point"), b -> {
                send("wand.point.del", selectedPointIndex, "", "");
                selectedPointIndex = -1;
                rebuildWidgets();
            }).bounds(inspX, y, fieldW, 18).build();
            addInspectorWidget(delBtn);

        } else if (sel.type == NpcProfile.MobilityPoint.TYPE_IF) {
            inspectorLabels.add(new InspectLabel(tr("gui.settledlands.mobility_wand.if_chance"), inspX, y, 0xff94a3b8));
            y += 12;
            EditBox chanceBox = new EditBox(this.font, inspX, y, fieldW, 18, Component.literal("If Chance"));
            chanceBox.setValue(String.valueOf(sel.chance));
            chanceBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,3}"));
            chanceBox.setResponder(t -> send("wand.node.if_chance", selectedPointIndex, t, ""));
            addInspectorWidget(chanceBox);
            y += 24;

            inspectorLabels.add(new InspectLabel(tr("gui.settledlands.mobility_wand.if_true_out"), inspX, y, 0xff4ade80));
            inspectorLabels.add(new InspectLabel(tr("gui.settledlands.mobility_wand.if_false_out"), inspX, y + 10, 0xfff87171));
            y += 26;

            Button delBtn = Button.builder(Component.translatable("gui.settledlands.mobility_wand.del_point"), b -> {
                send("wand.point.del", selectedPointIndex, "", "");
                selectedPointIndex = -1;
                rebuildWidgets();
            }).bounds(inspX, y, fieldW, 18).build();
            addInspectorWidget(delBtn);
        }
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private void send(String action, int pointIndex, String key, String value) {
        NpcNet.sendToServer(new NpcNet.CfgEdit(NpcNet.MOBILITY_EDITOR_ID, action, pointIndex, 0, this.hand,
                key != null ? key : "", value != null ? value : ""));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.openDropdown != null) {
            NpcUi.NpcDropdown dd = this.openDropdown;
            boolean handled = dd.popupClicked(mouseX, mouseY);
            if (!dd.popupOpen()) {
                this.openDropdown = null;
            }
            return true;
        }

        if (contextMenu.open) {
            int row = contextMenu.rowAt(mouseX, mouseY);
            boolean inside = contextMenu.contains(mouseX, mouseY);
            double wx = contextMenu.worldX, wy = contextMenu.worldY;
            int connectFrom = contextMenu.pendingWireFrom;
            int connectPort = contextMenu.pendingWirePort;
            int targetNode = contextMenu.targetNodeId;
            contextMenu.close();

            if (button == 0 && row >= 0) {
                if (targetNode >= 0 && targetNode < current.points().size()) {
                    if (row == 0) { // Duplicate node
                        NpcProfile.MobilityPoint src = current.points().get(targetNode);
                        if (src != null) {
                            send("wand.node.add", src.type, (src.canvasX + 32) + "," + (src.canvasY + 32), src.coordinates);
                        }
                    } else if (row == 1) { // Delete node
                        send("wand.point.del", targetNode, "", "");
                        if (selectedPointIndex == targetNode) selectedPointIndex = -1;
                        rebuildWidgets();
                    }
                    return true;
                } else if (targetNode < 0) {
                    String coordsSuffix = (connectFrom >= 0) ? (":" + connectFrom + ":" + connectPort) : "";
                    if (row == 0) { // Point
                        Minecraft mc = Minecraft.getInstance();
                        String pCoords = "";
                        if (mc.player != null) {
                            pCoords = ((int) Math.floor(mc.player.getX())) + " " +
                                      ((int) Math.floor(mc.player.getY())) + " " +
                                      ((int) Math.floor(mc.player.getZ()));
                        }
                        send("wand.node.add", 0, ((int) wx) + "," + ((int) wy) + coordsSuffix, pCoords);
                    } else if (row == 1) { // Random
                        send("wand.node.add", 1, ((int) wx) + "," + ((int) wy) + coordsSuffix, "");
                    } else if (row == 2) { // If
                        send("wand.node.add", 2, ((int) wx) + "," + ((int) wy) + coordsSuffix, "");
                    } else if (row == 3 && connectFrom < 0) { // New Group
                        send("wand.group.new", 0, "", "");
                    }
                    return true;
                }
            }
            if (button == 0 || inside) return true;
        }

        // Outside canvas (top bar) -> let super dispatch to widgets
        if (!canvas.contains(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // Shift + Left Click on cable -> disconnect cable
        if (button == 0 && hasShiftDown()) {
            if (disconnectHitCable(mouseX, mouseY)) return true;
        }

        // Middle Click (button == 2) -> disconnect cable/port or start pan
        if (button == 2) {
            if (disconnectHitCable(mouseX, mouseY)) return true;
            int outPort = canvas.hitPointPort(current.points(), mouseX, mouseY, true);
            if (outPort >= 0) {
                send("wand.node.disconnect", outPort, "", String.valueOf(canvas.hitPortIndex));
                return true;
            }
            int inPort = canvas.hitPointPort(current.points(), mouseX, mouseY, false);
            if (inPort >= 0) {
                disconnectIncoming(inPort);
                return true;
            }
            panning = true;
            panStartX = mouseX;
            panStartY = mouseY;
            return true;
        }

        // Right Click (button == 1) -> gesture tracking for pan vs context menu
        if (button == 1) {
            int hitNode = canvas.hitPoint(current.points(), mouseX, mouseY);
            contextMenu.press(mouseX, mouseY);
            contextMenu.targetNodeId = hitNode;
            if (hitNode >= 0) {
                selectedPointIndex = hitNode;
                rebuildWidgets();
            }
            return true;
        }

        // Left Click (button == 0)
        if (button == 0) {
            // Inside inspector:
            if (isInsideInspector(mouseX, mouseY)) {
                if (mouseX >= inspectorX + inspectorW - 20 && mouseX <= inspectorX + inspectorW - 4
                        && mouseY >= inspectorY + 2 && mouseY <= inspectorY + 18) {
                    selectedPointIndex = -1;
                    rebuildWidgets();
                    return true;
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }

            // Check output port hit FIRST -> always pulls wire!
            int outPort = canvas.hitPointPort(current.points(), mouseX, mouseY, true);
            if (outPort >= 0) {
                linkFrom = outPort;
                linkFromPort = canvas.hitPortIndex;
                selectedPointIndex = outPort;
                rebuildWidgets();
                return true;
            }

            // Check node card hit -> select and start drag
            int hitNode = canvas.hitPoint(current.points(), mouseX, mouseY);
            if (hitNode >= 0) {
                selectedPointIndex = hitNode;
                rebuildWidgets();
                dragging = hitNode;
                dragMouseWorldX = canvas.worldX(mouseX);
                dragMouseWorldY = canvas.worldY(mouseY);
                NpcProfile.MobilityPoint sn = current.points().get(hitNode);
                dragOriginX = sn.canvasX;
                dragOriginY = sn.canvasY;
                return true;
            }

            // Clicked on empty canvas space:
            if (isSpaceDown()) {
                panning = true;
                panStartX = mouseX;
                panStartY = mouseY;
                return true;
            } else {
                if (selectedPointIndex >= 0) {
                    selectedPointIndex = -1;
                    rebuildWidgets();
                }
                return true;
            }
        }

        return false;
    }

    private boolean disconnectHitCable(double mx, double my) {
        List<NpcProfile.MobilityPoint> pts = current.points();
        for (int i = 0; i < pts.size(); i++) {
            NpcProfile.MobilityPoint a = pts.get(i);
            if (a.type == NpcProfile.MobilityPoint.TYPE_POINT) {
                if (a.next >= 0 && a.next < pts.size() && canvas.hitPointCable(a, 0, pts.get(a.next), 0, mx, my)) {
                    send("wand.node.disconnect", i, "", "0");
                    return true;
                }
            } else if (a.type == NpcProfile.MobilityPoint.TYPE_RANDOM) {
                for (int p = 0; p < a.randomBranches.size(); p++) {
                    int t = a.randomBranches.get(p);
                    if (t >= 0 && t < pts.size() && canvas.hitPointCable(a, p, pts.get(t), 0, mx, my)) {
                        send("wand.node.disconnect", i, "", String.valueOf(p));
                        return true;
                    }
                }
            } else if (a.type == NpcProfile.MobilityPoint.TYPE_IF) {
                if (a.next >= 0 && a.next < pts.size() && canvas.hitPointCable(a, 0, pts.get(a.next), 0, mx, my)) {
                    send("wand.node.disconnect", i, "", "0");
                    return true;
                }
                if (a.falseNext >= 0 && a.falseNext < pts.size() && canvas.hitPointCable(a, 1, pts.get(a.falseNext), 0, mx, my)) {
                    send("wand.node.disconnect", i, "", "1");
                    return true;
                }
            }
        }
        return false;
    }

    private void disconnectIncoming(int inPort) {
        List<NpcProfile.MobilityPoint> pts = current.points();
        for (int i = 0; i < pts.size(); i++) {
            NpcProfile.MobilityPoint a = pts.get(i);
            if (a.next == inPort) {
                send("wand.node.disconnect", i, "", "0");
            }
            if (a.falseNext == inPort) {
                send("wand.node.disconnect", i, "", "1");
            }
            if (a.type == NpcProfile.MobilityPoint.TYPE_RANDOM) {
                for (int p = 0; p < a.randomBranches.size(); p++) {
                    if (a.randomBranches.get(p) == inPort) {
                        send("wand.node.disconnect", i, "", String.valueOf(p));
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 1 && contextMenu.isPressed()) {
            if (contextMenu.drag(mouseX, mouseY)) {
                canvas.pan(contextMenu.deltaX, contextMenu.deltaY);
            }
            return true;
        }

        if (isInsideInspector(mouseX, mouseY)) {
            for (AbstractWidget w : inspectorWidgets) {
                if (w.isMouseOver(mouseX, mouseY) && w.mouseDragged(mouseX, mouseY, button, dx, dy)) {
                    return true;
                }
            }
            return true;
        }

        if (panning) {
            canvas.pan(dx, dy);
            return true;
        }

        if (linkFrom >= 0) {
            return true;
        }

        if (dragging >= 0 && dragging < current.points().size()) {
            double curMouseWorldX = canvas.worldX(mouseX);
            double curMouseWorldY = canvas.worldY(mouseY);
            double deltaWorldX = curMouseWorldX - dragMouseWorldX;
            double deltaWorldY = curMouseWorldY - dragMouseWorldY;
            NpcProfile.MobilityPoint sn = current.points().get(dragging);
            canvas.move(sn, dragOriginX + deltaWorldX, dragOriginY + deltaWorldY);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1 && contextMenu.isPressed()) {
            contextMenu.release(mouseX, mouseY, canvas);
            return true;
        }

        if (linkFrom >= 0) {
            int to = canvas.hitPointPort(current.points(), mouseX, mouseY, false);
            if (to < 0) to = canvas.hitPoint(current.points(), mouseX, mouseY);
            if (to >= 0 && to != linkFrom) {
                send("wand.node.connect", linkFrom, String.valueOf(to), String.valueOf(linkFromPort));
            } else if (canvas.contains(mouseX, mouseY)) {
                contextMenu.showForWire(mouseX, mouseY, canvas, linkFrom, linkFromPort);
            }
            linkFrom = -1;
            return true;
        }

        if (dragging >= 0 && dragging < current.points().size()) {
            NpcProfile.MobilityPoint sn = current.points().get(dragging);
            send("wand.node.move", dragging, sn.canvasX + "," + sn.canvasY, "");
        }
        dragging = -1;
        panning = false;

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.openDropdown != null) {
            if (this.openDropdown.popupScrolled(mouseX, mouseY, (int) Math.signum(scrollY))) {
                return true;
            }
            this.openDropdown.closePopup();
            this.openDropdown = null;
            return true;
        }

        if (isInsideInspector(mouseX, mouseY)) {
            sidebarScroll = Math.max(0, sidebarScroll - (int) (scrollY * 18));
            rebuildWidgets();
            return true;
        }

        if (canvas.contains(mouseX, mouseY)) {
            canvas.zoomAt(scrollY, mouseX, mouseY);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 70 && !(getFocused() instanceof EditBox)) { // 'F' key frames all nodes
            canvas.frameAllPoints(current.points());
            return true;
        }
        if (key == 261 && !(getFocused() instanceof EditBox) && selectedPointIndex >= 0) { // Delete removes the selected node
            send("wand.point.del", selectedPointIndex, "", "");
            selectedPointIndex = -1;
            rebuildWidgets();
            return true;
        }
        if (key == 256) { // Escape key
            if (linkFrom >= 0 || dragging >= 0 || panning) {
                linkFrom = -1; dragging = -1; panning = false; return true;
            }
            if (contextMenu.open) {
                contextMenu.close(); return true;
            }
            if (selectedPointIndex >= 0) {
                selectedPointIndex = -1; rebuildWidgets(); return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // 1. Screen & Canvas Background
        g.fill(0, 0, this.width, this.height, 0xff10151d);
        g.fill(canvas.left, canvas.top, canvas.right, canvas.bottom, 0xff181f2a);

        // 2. Canvas Area Scissored
        g.enableScissor(canvas.left, canvas.top, canvas.right, canvas.bottom);
        grid(g);
        wires(g, mouseX, mouseY);
        for (int i = 0; i < current.points().size(); i++) {
            NpcProfile.MobilityPoint pt = current.points().get(i);
            if (canvas.visible(pt)) {
                drawNode(g, pt, i);
            }
        }
        g.disableScissor();

        // 3. Top bar background
        g.fill(0, 0, this.width, TOPBAR_H, 0xff10151d);
        g.fill(0, TOPBAR_H - 1, this.width, TOPBAR_H, 0xff242e3b);

        // 4. Floating Inspector Panel Background
        if (selectedPointIndex >= 0 && selectedPointIndex < current.points().size()) {
            g.fill(inspectorX, inspectorY, inspectorX + inspectorW, inspectorY + inspectorH, 0xee141c28);
            g.fill(inspectorX, inspectorY, inspectorX + inspectorW, inspectorY + 1, 0xff3b4e66);
            g.fill(inspectorX, inspectorY + inspectorH - 1, inspectorX + inspectorW, inspectorY + inspectorH, 0xff3b4e66);
            g.fill(inspectorX, inspectorY, inspectorX + 1, inspectorY + inspectorH, 0xff3b4e66);
            g.fill(inspectorX + inspectorW - 1, inspectorY, inspectorX + inspectorW, inspectorY + inspectorH, 0xff3b4e66);

            g.fill(inspectorX + 1, inspectorY + 1, inspectorX + inspectorW - 1, inspectorY + 20, 0xff202c3d);
            g.fill(inspectorX + 1, inspectorY + 20, inspectorX + inspectorW - 1, inspectorY + 21, 0xff3b4e66);

            NpcProfile.MobilityPoint sel = current.points().get(selectedPointIndex);
            int headerColor = sel.type == NpcProfile.MobilityPoint.TYPE_POINT ? 0xff2dd4bf
                    : (sel.type == NpcProfile.MobilityPoint.TYPE_RANDOM ? 0xffc084fc : 0xff7dd3fc);
            String heading = sel.type == NpcProfile.MobilityPoint.TYPE_POINT ? ("📍 Точка #" + (selectedPointIndex + 1))
                    : (sel.type == NpcProfile.MobilityPoint.TYPE_RANDOM ? ("🎲 Random #" + (selectedPointIndex + 1))
                    : ("❓ If #" + (selectedPointIndex + 1)));

            g.drawString(this.font, heading, inspectorX + 8, inspectorY + 6, headerColor, false);
            g.drawString(this.font, "✕", inspectorX + inspectorW - 14, inspectorY + 6, 0xffaab8ca, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);

        // Render Inspector Labels captured during layout
        if (selectedPointIndex >= 0 && selectedPointIndex < current.points().size()) {
            for (InspectLabel l : inspectorLabels) {
                if (l.y() >= inspectorY + 20 && l.y() < inspectorY + inspectorH - 8) {
                    g.drawString(this.font, l.text(), l.x(), l.y(), l.color(), false);
                }
            }
        }

        // Bottom status & zoom
        g.drawString(this.font, "ЛКМ: выбор / перенос | Тянуть из правого края: провод | ПКМ: меню / панорама | Колесо: зум | F: центрировать", 10, this.height - 14, 0x8894a3b8, false);
        g.drawString(this.font, Math.round(canvas.zoom * 100) + "%", this.width - 36, this.height - 14, 0x88aab8ca, false);

        if (this.openDropdown != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 500);
            this.openDropdown.renderPopup(g, mouseX, mouseY);
            g.pose().popPose();
        }

        if (contextMenu.open) {
            drawContextMenu(g, mouseX, mouseY);
        }
    }

    private void grid(GuiGraphics g) {
        double step = 32 * canvas.zoom;
        while (step < 16) step *= 4;
        int startX = (int) (canvas.left + (canvas.panX % step + step) % step);
        int startY = (int) (canvas.top + (canvas.panY % step + step) % step);
        for (double x = startX; x < canvas.right; x += step) g.vLine((int) x, canvas.top, canvas.bottom, 0xff242e3b);
        for (double y = startY; y < canvas.bottom; y += step) g.hLine(canvas.left, canvas.right, (int) y, 0xff242e3b);
    }

    private void drawNode(GuiGraphics g, NpcProfile.MobilityPoint n, int index) {
        int color = n.type == NpcProfile.MobilityPoint.TYPE_POINT ? 0xff0f766e
                : (n.type == NpcProfile.MobilityPoint.TYPE_RANDOM ? 0xff6b21a8 : 0xff0284c7);

        double sx = canvas.screenX(n.canvasX);
        double sy = canvas.screenY(n.canvasY);

        g.pose().pushPose();
        g.pose().translate(sx, sy, 0);
        g.pose().scale((float) canvas.zoom, (float) canvas.zoom, 1);

        int w = NpcGraphCanvas.NODE_W;
        int h = NpcGraphCanvas.NODE_H;
        boolean isSel = (index == selectedPointIndex);

        // Card border & background
        g.fill(-2, -2, w + 2, h + 2, isSel ? 0xffe9bd67 : 0xff46576b);
        g.fill(0, 0, w, h, 0xff26313f);
        g.fill(0, 0, w, 22, color);

        // Header Title
        String title = n.type == NpcProfile.MobilityPoint.TYPE_POINT ? ("📍 Точка #" + (index + 1))
                : (n.type == NpcProfile.MobilityPoint.TYPE_RANDOM ? ("🎲 Random [" + n.randomMin + "-" + n.randomMax + "]")
                : ("❓ If (" + n.chance + "%)"));
        g.drawString(this.font, title, 8, 7, 0xfff0f3f7, false);

        // Body Content
        if (n.type == NpcProfile.MobilityPoint.TYPE_POINT) {
            String coords = n.coordinates.isBlank() ? "— (не заданы)" : n.coordinates;
            g.drawString(this.font, "xyz: " + coords, 8, 35, 0xffcbd5e1, false);
            g.drawString(this.font, "Шанс: " + n.chance + "% | Пауза: " + n.duration + "с", 8, 52, 0xff94a3b8, false);
            if (n.next >= 0 && n.next < current.points().size()) {
                g.drawString(this.font, "➜ След: #" + (n.next + 1), 8, 70, 0xff2dd4bf, false);
            }
        } else if (n.type == NpcProfile.MobilityPoint.TYPE_RANDOM) {
            g.drawString(this.font, "Диапазон чисел: [" + n.randomMin + " - " + n.randomMax + "]", 8, 35, 0xffe9d5ff, false);
            g.drawString(this.font, "Количество выходов: " + n.randomMax, 8, 52, 0xffc084fc, false);
        } else if (n.type == NpcProfile.MobilityPoint.TYPE_IF) {
            g.drawString(this.font, "Вероятность True: " + n.chance + "%", 8, 35, 0xff7dd3fc, false);
            g.drawString(this.font, "• True: верхний (зеленый)", 8, 52, 0xff4ade80, false);
            g.drawString(this.font, "• False: нижний (красный)", 8, 68, 0xfff87171, false);
        }

        // Left input port circle
        g.fill(-5, h / 2 - 5, 5, h / 2 + 5, 0xffc4d8ea);

        // Right output port circle(s)
        if (n.type == NpcProfile.MobilityPoint.TYPE_IF) {
            int py0 = (int) (h * 0.35);
            int py1 = (int) (h * 0.70);
            g.fill(w - 5, py0 - 5, w + 5, py0 + 5, 0xff4ade80);
            g.fill(w - 5, py1 - 5, w + 5, py1 + 5, 0xfff87171);
            g.drawString(this.font, "T", w - 12, py0 - 4, 0xff4ade80, false);
            g.drawString(this.font, "F", w - 12, py1 - 4, 0xfff87171, false);
        } else if (n.type == NpcProfile.MobilityPoint.TYPE_RANDOM) {
            int ports = Math.max(2, n.randomMax);
            for (int p = 0; p < ports; p++) {
                int py = (int) (h * ((p + 1.0) / (ports + 1.0)));
                g.fill(w - 5, py - 4, w + 5, py + 4, 0xffa855f7);
                g.drawString(this.font, String.valueOf(p + 1), w - 12, py - 4, 0xffe9d5ff, false);
            }
        } else {
            g.fill(w - 5, h / 2 - 5, w + 5, h / 2 + 5, 0xff2dd4bf);
        }

        g.pose().popPose();
    }

    private void wires(GuiGraphics g, int mx, int my) {
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = g.pose().last().pose();
        int segments = 0;

        List<NpcProfile.MobilityPoint> pts = current.points();
        int count = pts.size();

        // 1. Detect hovered cable
        boolean canHover = canvas.contains(mx, my) && !contextMenu.open && linkFrom < 0 && dragging < 0 && !panning
                && canvas.hitPoint(pts, mx, my) < 0
                && canvas.hitPointPort(pts, mx, my, true) < 0
                && canvas.hitPointPort(pts, mx, my, false) < 0;
        int hoverFrom = -1, hoverFromPort = 0, hoverTo = -1, hoverToPort = 0, hoverColor = 0;

        if (canHover) {
            findHover:
            for (int i = 0; i < count; i++) {
                NpcProfile.MobilityPoint a = pts.get(i);
                if (a.type == NpcProfile.MobilityPoint.TYPE_POINT) {
                    if (a.next >= 0 && a.next < count) {
                        if (canvas.hitPointCable(a, 0, pts.get(a.next), 0, mx, my)) {
                            hoverFrom = i; hoverFromPort = 0; hoverTo = a.next; hoverToPort = 0;
                            hoverColor = 0xff2dd4bf;
                            break findHover;
                        }
                    }
                } else if (a.type == NpcProfile.MobilityPoint.TYPE_RANDOM) {
                    for (int p = 0; p < a.randomBranches.size(); p++) {
                        int t = a.randomBranches.get(p);
                        if (t >= 0 && t < count) {
                            if (canvas.hitPointCable(a, p, pts.get(t), 0, mx, my)) {
                                hoverFrom = i; hoverFromPort = p; hoverTo = t; hoverToPort = 0;
                                hoverColor = 0xffa855f7;
                                break findHover;
                            }
                        }
                    }
                } else if (a.type == NpcProfile.MobilityPoint.TYPE_IF) {
                    if (a.next >= 0 && a.next < count) {
                        if (canvas.hitPointCable(a, 0, pts.get(a.next), 0, mx, my)) {
                            hoverFrom = i; hoverFromPort = 0; hoverTo = a.next; hoverToPort = 0;
                            hoverColor = 0xff4ade80;
                            break findHover;
                        }
                    }
                    if (a.falseNext >= 0 && a.falseNext < count) {
                        if (canvas.hitPointCable(a, 1, pts.get(a.falseNext), 0, mx, my)) {
                            hoverFrom = i; hoverFromPort = 1; hoverTo = a.falseNext; hoverToPort = 0;
                            hoverColor = 0xfff87171;
                            break findHover;
                        }
                    }
                }
            }
        }

        // 2. Draw normal cables
        boolean anyExplicit = false;
        for (int i = 0; i < count; i++) {
            NpcProfile.MobilityPoint a = pts.get(i);
            if (a.type == NpcProfile.MobilityPoint.TYPE_POINT) {
                if (a.next >= 0 && a.next < count) {
                    anyExplicit = true;
                    if (!(hoverFrom == i && hoverFromPort == 0 && hoverTo == a.next && hoverToPort == 0)) {
                        segments += cable(b, matrix, a, 0, pts.get(a.next), 0, 0xff2dd4bf, false);
                    }
                }
            } else if (a.type == NpcProfile.MobilityPoint.TYPE_RANDOM) {
                for (int p = 0; p < a.randomBranches.size(); p++) {
                    int t = a.randomBranches.get(p);
                    if (t >= 0 && t < count) {
                        anyExplicit = true;
                        if (!(hoverFrom == i && hoverFromPort == p && hoverTo == t && hoverToPort == 0)) {
                            segments += cable(b, matrix, a, p, pts.get(t), 0, 0xffa855f7, false);
                        }
                    }
                }
            } else if (a.type == NpcProfile.MobilityPoint.TYPE_IF) {
                if (a.next >= 0 && a.next < count) {
                    anyExplicit = true;
                    if (!(hoverFrom == i && hoverFromPort == 0 && hoverTo == a.next && hoverToPort == 0)) {
                        segments += cable(b, matrix, a, 0, pts.get(a.next), 0, 0xff4ade80, false);
                    }
                }
                if (a.falseNext >= 0 && a.falseNext < count) {
                    anyExplicit = true;
                    if (!(hoverFrom == i && hoverFromPort == 1 && hoverTo == a.falseNext && hoverToPort == 0)) {
                        segments += cable(b, matrix, a, 1, pts.get(a.falseNext), 0, 0xfff87171, false);
                    }
                }
            }
        }

        // Fallback sequential ring cables if no explicit connections
        if (!anyExplicit && count >= 2) {
            for (int i = 0; i < count; i++) {
                int next = (i + 1) % count;
                segments += cable(b, matrix, pts.get(i), 0, pts.get(next), 0, 0x552dd4bf, false);
            }
        }

        // 3. Draw hovered cable with glow!
        if (hoverFrom >= 0 && hoverTo >= 0 && hoverFrom < count && hoverTo < count) {
            segments += cable(b, matrix, pts.get(hoverFrom), hoverFromPort, pts.get(hoverTo), hoverToPort, hoverColor, true);
        }

        // 4. Draw active pulled wire!
        if (linkFrom >= 0 && linkFrom < count) {
            NpcProfile.MobilityPoint a = pts.get(linkFrom);
            double x1 = canvas.portScreenX(a, true);
            double y1 = canvas.portScreenY(a, true, linkFromPort);
            int activeColor = a.type == NpcProfile.MobilityPoint.TYPE_IF
                    ? (linkFromPort == 1 ? 0xfff87171 : 0xff4ade80)
                    : (a.type == NpcProfile.MobilityPoint.TYPE_RANDOM ? 0xffa855f7 : 0xff2dd4bf);
            segments += curve(b, matrix, x1, y1, mx, my, activeColor, false);
        }

        if (segments > 0) {
            MeshData mesh = b.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        } else {
            b.build();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private int cable(BufferBuilder b, Matrix4f m, NpcProfile.MobilityPoint a, int fromPort, NpcProfile.MobilityPoint c, int toPort, int color, boolean hovered) {
        double x1 = canvas.portScreenX(a, true);
        double y1 = canvas.portScreenY(a, true, fromPort);
        double x2 = canvas.portScreenX(c, false);
        double y2 = canvas.portScreenY(c, false, toPort);
        double margin = Math.max(25.0 * canvas.zoom, Math.abs(x2 - x1) * 0.45);
        if (Math.max(x1, x2) + margin < canvas.left || Math.min(x1, x2) - margin > canvas.right
                || Math.max(y1, y2) < canvas.top || Math.min(y1, y2) > canvas.bottom) {
            return 0;
        }
        return curve(b, m, x1, y1, x2, y2, color, hovered);
    }

    private int curve(BufferBuilder b, Matrix4f m, double x1, double y1, double x2, double y2, int color, boolean hovered) {
        double bend = Math.max(25.0 * canvas.zoom, Math.abs(x2 - x1) * 0.45);
        double px = x1, py = y1;
        double baseHalfWidth = canvas.zoom < 0.30 ? Math.max(0.45, canvas.zoom / 0.30 * 0.85) : 1.1;
        int segments = 0;

        if (hovered) {
            double glowWidth = baseHalfWidth + 2.5;
            int glowColor = (color & 0x00FFFFFF) | 0x55000000;
            for (int i = 1; i <= 20; i++) {
                double t = i / 20.0, u = 1 - t;
                double x = u * u * u * x1 + 3 * u * u * t * (x1 + bend) + 3 * u * t * t * (x2 - bend) + t * t * t * x2;
                double y = u * u * u * y1 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y2;
                double dx = x - px, dy = y - py, length = Math.sqrt(dx * dx + dy * dy);
                if (length > 0.001) {
                    double nx = -dy / length * glowWidth, ny = dx / length * glowWidth;
                    b.addVertex(m, (float) (px + nx), (float) (py + ny), 0).setColor(glowColor);
                    b.addVertex(m, (float) (px - nx), (float) (py - ny), 0).setColor(glowColor);
                    b.addVertex(m, (float) (x - nx), (float) (y - ny), 0).setColor(glowColor);
                    b.addVertex(m, (float) (x + nx), (float) (y + ny), 0).setColor(glowColor);
                }
                px = x; py = y;
            }
            segments += 20;
            px = x1; py = y1;
            double coreWidth = baseHalfWidth + 0.5;
            int coreColor = 0xFFFFFFFF;
            for (int i = 1; i <= 20; i++) {
                double t = i / 20.0, u = 1 - t;
                double x = u * u * u * x1 + 3 * u * u * t * (x1 + bend) + 3 * u * t * t * (x2 - bend) + t * t * t * x2;
                double y = u * u * u * y1 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y2;
                double dx = x - px, dy = y - py, length = Math.sqrt(dx * dx + dy * dy);
                if (length > 0.001) {
                    double nx = -dy / length * coreWidth, ny = dx / length * coreWidth;
                    b.addVertex(m, (float) (px + nx), (float) (py + ny), 0).setColor(coreColor);
                    b.addVertex(m, (float) (px - nx), (float) (py - ny), 0).setColor(coreColor);
                    b.addVertex(m, (float) (x - nx), (float) (y - ny), 0).setColor(coreColor);
                    b.addVertex(m, (float) (x + nx), (float) (y + ny), 0).setColor(coreColor);
                }
                px = x; py = y;
            }
            segments += 20;
            return segments;
        }

        for (int i = 1; i <= 20; i++) {
            double t = i / 20.0, u = 1 - t;
            double x = u * u * u * x1 + 3 * u * u * t * (x1 + bend) + 3 * u * t * t * (x2 - bend) + t * t * t * x2;
            double y = u * u * u * y1 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y2;
            double dx = x - px, dy = y - py, length = Math.sqrt(dx * dx + dy * dy);
            if (length > 0.001) {
                double nx = -dy / length * baseHalfWidth, ny = dx / length * baseHalfWidth;
                b.addVertex(m, (float) (px + nx), (float) (py + ny), 0).setColor(color);
                b.addVertex(m, (float) (px - nx), (float) (py - ny), 0).setColor(color);
                b.addVertex(m, (float) (x - nx), (float) (y - ny), 0).setColor(color);
                b.addVertex(m, (float) (x + nx), (float) (y + ny), 0).setColor(color);
            }
            px = x; py = y;
        }
        return 20;
    }

    private void drawContextMenu(GuiGraphics g, int mouseX, int mouseY) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 600);
        int x = contextMenu.left, y = contextMenu.top, w = contextMenu.width, h = contextMenu.height;
        g.enableScissor(x, y, x + w, y + h);
        g.fill(x, y, x + w, y + h, 0xff111923);
        g.renderOutline(x, y, w, h, 0xff7f98b6);

        if (contextMenu.targetNodeId >= 0) {
            NpcProfile.MobilityPoint target = contextMenu.targetNodeId < current.points().size()
                    ? current.points().get(contextMenu.targetNodeId) : null;
            String title = target != null ? ("Узел #" + (contextMenu.targetNodeId + 1)) : "Узел";
            g.drawString(this.font, title, x + 8, y + 6, 0xffe3bd75, false);
            int hovered = contextMenu.rowAt(mouseX, mouseY);
            for (int row = 0; row < 2; row++) {
                int ry = y + MobilityContextMenu.HEADER_H + row * MobilityContextMenu.ROW_H;
                if (row == hovered) g.fill(x + 2, ry, x + w - 2, ry + MobilityContextMenu.ROW_H, 0xff34465d);
                String label = row == 0 ? "📋 Дублировать узел" : "✕ Удалить узел";
                int col = row == 0 ? 0xff84dab0 : 0xfff87171;
                g.drawString(this.font, label, x + 8, ry + 7, col, false);
            }
        } else {
            String header = (contextMenu.pendingWireFrom >= 0) ? "⚡ Создать и связать:" : "Создать узел:";
            g.drawString(this.font, header, x + 8, y + 6, 0xff84dab0, false);
            int rows = (contextMenu.pendingWireFrom >= 0) ? 3 : 4;
            String[] labels = {
                "📍 Точка маршрута",
                "🎲 Случайность (Random)",
                "❓ Условие (If)",
                "📁 Новая группа"
            };
            int[] colors = {
                0xff2dd4bf,
                0xffc084fc,
                0xff7dd3fc,
                0xfffde68a
            };
            int hovered = contextMenu.rowAt(mouseX, mouseY);
            for (int row = 0; row < rows; row++) {
                int ry = y + MobilityContextMenu.HEADER_H + row * MobilityContextMenu.ROW_H;
                if (row == hovered) g.fill(x + 2, ry, x + w - 2, ry + MobilityContextMenu.ROW_H, 0xff34465d);
                g.drawString(this.font, labels[row], x + 8, ry + 7, colors[row], false);
            }
        }
        g.disableScissor();
        g.pose().popPose();
    }

    @Override
    public void openDropdown(NpcUi.NpcDropdown dropdown) {
        this.openDropdown = dropdown;
    }

    @Override
    public NpcUi.NpcDropdown activeDropdown() {
        return this.openDropdown;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- Context Menu with identical gesture policy to NpcGraphContextMenu ---
    public static final class MobilityContextMenu {
        public static final int ROW_H = 22, HEADER_H = 20;
        public static final int MENU_W = 164;
        public static final double DRAG_THRESHOLD_SQ = 4.0 * 4.0;

        public boolean open;
        public int left, top, width = MENU_W, height;
        public double worldX, worldY, deltaX, deltaY;
        public int pendingWireFrom = -1;
        public int pendingWirePort = 0;
        public int targetNodeId = -1;
        private boolean pressed, dragged;
        private double startX, startY, lastX, lastY;

        public void press(double x, double y) {
            open = false;
            pressed = true;
            dragged = false;
            startX = lastX = x;
            startY = lastY = y;
            deltaX = deltaY = 0;
            pendingWireFrom = -1;
            pendingWirePort = 0;
            targetNodeId = -1;
        }

        public boolean isPressed() { return pressed; }

        public boolean drag(double x, double y) {
            deltaX = deltaY = 0;
            if (!pressed) return false;
            double dx = x - startX, dy = y - startY;
            if (!dragged && dx * dx + dy * dy < DRAG_THRESHOLD_SQ) return false;
            dragged = true;
            deltaX = x - lastX;
            deltaY = y - lastY;
            lastX = x;
            lastY = y;
            return true;
        }

        public boolean release(double x, double y, NpcGraphCanvas canvas) {
            if (!pressed) return false;
            pressed = false;
            double dx = x - startX, dy = y - startY;
            if (dragged || dx * dx + dy * dy >= DRAG_THRESHOLD_SQ || !canvas.contains(startX, startY)) {
                dragged = false;
                return false;
            }
            if (targetNodeId >= 0) {
                showForNode(startX, startY, canvas, targetNodeId);
            } else {
                show(startX, startY, canvas);
            }
            return true;
        }

        public void showForWire(double x, double y, NpcGraphCanvas canvas, int wireFrom, int wirePort) {
            show(x, y, canvas);
            this.pendingWireFrom = wireFrom;
            this.pendingWirePort = wirePort;
        }

        public void showForNode(double x, double y, NpcGraphCanvas canvas, int nodeId) {
            this.targetNodeId = nodeId;
            this.pendingWireFrom = -1;
            this.pendingWirePort = 0;
            worldX = canvas.worldX(x);
            worldY = canvas.worldY(y);
            width = MENU_W;
            height = HEADER_H + 2 * ROW_H + 4;
            left = (int) Math.max(canvas.left, Math.min(canvas.right - width, x));
            top = (int) Math.max(canvas.top, Math.min(canvas.bottom - height, y));
            open = true;
        }

        public void show(double x, double y, NpcGraphCanvas canvas) {
            this.targetNodeId = -1;
            this.pendingWireFrom = -1;
            this.pendingWirePort = 0;
            worldX = canvas.worldX(x);
            worldY = canvas.worldY(y);
            width = MENU_W;
            int rows = 4;
            height = HEADER_H + rows * ROW_H + 4;
            left = (int) Math.max(canvas.left, Math.min(canvas.right - width, x));
            top = (int) Math.max(canvas.top, Math.min(canvas.bottom - height, y));
            open = true;
        }

        public void close() {
            open = false;
            pendingWireFrom = -1;
            targetNodeId = -1;
        }

        public boolean contains(double mx, double my) {
            return open && mx >= left && mx <= left + width && my >= top && my <= top + height;
        }

        public int rowAt(double mx, double my) {
            if (!contains(mx, my)) return -1;
            int r = (int) ((my - (top + HEADER_H)) / ROW_H);
            int maxRows = (targetNodeId >= 0) ? 2 : (pendingWireFrom >= 0 ? 3 : 4);
            return (r >= 0 && r < maxRows) ? r : -1;
        }
    }
}
