package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.NpcProfile;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Build 306: the node workspace itself.
 *
 * <p>This is not the stock dialogue screen with a new coat of paint. It is a screen of its own
 * that owns the graph: the camera, the cards, the ports, the cables, selection, dragging,
 * linking, the node library, the tabs and the save. The painting and the workspace layout are
 * modelled on the Advanced Control Computer of Gadgets &amp; Gizmos (used with the authors'
 * permission); the nodes, the ports and the rules behind them are SettledLands'. There is no
 * Gadgets &amp; Gizmos node type here - the library lists exactly our fifteen.</p>
 *
 * <p>The graph itself is still {@link NpcDialogueGraph}: same file format, same validation,
 * same save packet. Links are read and written through that model only
 * ({@code connect}/{@code disconnect}), so a graph edited here is byte for byte the graph the
 * old editor would have saved.</p>
 *
 * <p>Field editing stays in the old screen for now - the "Старый редактор" button hands the
 * same graph back to it, and the fallback keeps every inspector that already exists.</p>
 */
public class NpcWorkspace306 extends Screen {

    private static final int TAB_MAIN = 0;
    private static final int TAB_WALK = 1;
    private static final int TAB_ANIM = 2;

    private static final int TOP = 26;
    private static final int LIBRARY_W = 132;

    private final int entityId;
    private final Screen parent;
    private final int focus;
    private NpcProfile profile;

    private final NpcDialogueGraph[] graphs = new NpcDialogueGraph[3];
    private int tab;
    private NpcDialogueGraph graph;

    private final NpcGraphCanvas canvas = new NpcGraphCanvas();
    private final Deque<CompoundTag> undo = new ArrayDeque<>();
    private final Deque<CompoundTag> redo = new ArrayDeque<>();

    private int selected = -1;
    private final Set<Integer> multi = new LinkedHashSet<>();
    private int linkFrom = -1;
    private int linkPort;

    private boolean panning;
    private double panStartX;
    private double panStartY;
    private int dragging = -1;
    private final Map<Integer, int[]> grab = new HashMap<>();
    private double grabWorldX;
    private double grabWorldY;

    private boolean boxSelect;
    private double boxX0;
    private double boxY0;
    private double boxX1;
    private double boxY1;

    private EditBox search;
    private String status = "";
    private boolean dirty;

    public NpcWorkspace306(int entityId, NpcProfile profile, Screen parent, int focus) {
        super(Component.translatable("gui.settledlands.workspace.title"));
        this.entityId = entityId;
        this.parent = parent;
        this.focus = focus;
        this.profile = profile == null ? new NpcProfile() : profile.copy();
        loadGraphs();
        resolveSelection();
    }

    /* ------------------------------------------------------------------ model */

    private void loadGraphs() {
        NpcDialogueGraph main;
        try {
            main = profile.dialogueGraph == null
                    ? NpcDialogueGraph.fromLegacy(profile)
                    : profile.dialogueGraph.copy();
        } catch (RuntimeException broken) {
            main = new NpcDialogueGraph();
            status = tr("gui.settledlands.graph.import_failed");
        }
        if (main == null) main = new NpcDialogueGraph();
        graphs[TAB_MAIN] = main;
        graphs[TAB_WALK] = profile.walkGraph == null ? null : profile.walkGraph.copy();
        graphs[TAB_ANIM] = profile.animGraph == null ? null : profile.animGraph.copy();
        graph = graphs[tab];
    }

    private void resolveSelection() {
        if (graph == null) return;
        if (focus == -2 || focus == -3) {
            selected = graph.specialNode(focus == -2 ? 1 : 2);
        } else if (focus >= 0) {
            selected = graph.topicNode(focus);
        } else {
            selected = graph.entry;
        }
        if (selected >= 0 && graph.get(selected) == null) selected = -1;
    }

    private String tr(String key) {
        return Component.translatable(key).getString();
    }

    /* ------------------------------------------------------------------ life */

    @Override
    protected void init() {
        if (search != null) {
            children().remove(search);
            search = null;
        }
        canvas.bounds(LIBRARY_W + 12, TOP, width - 4, height - 4);
        if (graph != null) {
            if (graph.hasCamera) {
                canvas.panX = graph.panX;
                canvas.panY = graph.panY;
                canvas.zoom = graph.zoom;
            } else {
                frameAll();
            }
        }
        search = new EditBox(Minecraft.getInstance().font, NpcNodeLibrary305.searchX(),
                NpcNodeLibrary305.searchY(canvas.top), NpcNodeLibrary305.searchWidth(),
                NpcNodeLibrary305.searchHeight(),
                (Component) (Object) Component
                        .translatable("gui.settledlands.workspace.search"));
        search.setMaxLength(24);
        search.setValue(NpcNodeLibrary305.query());
        search.setResponder(NpcNodeLibrary305::setQuery);
        search.visible = false;
        children().add(search);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        syncCamera();
        if (dirty) {
            save();
        }
        Minecraft.getInstance().setScreen(parent);
    }

    private void syncCamera() {
        if (graph == null) return;
        graph.hasCamera = true;
        graph.panX = canvas.panX;
        graph.panY = canvas.panY;
        graph.zoom = canvas.zoom;
    }

    /* ------------------------------------------------------------------ save */

    private boolean save() {
        syncCamera();
        NpcDialogueGraph main = graphs[TAB_MAIN];
        if (main == null) main = graph;
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("GraphOnly", true);
        if (main != null) tag.put("DialogueGraph", main.save());
        if (graphs[TAB_WALK] != null) tag.put("WalkGraph", graphs[TAB_WALK].save());
        if (graphs[TAB_ANIM] != null) tag.put("AnimGraph", graphs[TAB_ANIM].save());
        if (NpcDialogueGraph.encodedSize(tag) > NpcDialogueGraph.MAX_PACKET_BYTES) {
            status = tr("gui.settledlands.graph.too_large");
            return false;
        }
        NpcNet.sendToServer(new NpcNet.CfgSave(entityId, tag));
        dirty = false;
        status = tr("gui.settledlands.graph.sending");
        return true;
    }

    /* ------------------------------------------------------------------ undo */

    private void remember() {
        if (graph == null) return;
        undo.push(graph.save());
        while (undo.size() > 64) undo.removeLast();
        redo.clear();
    }

    private void restore(CompoundTag tag) {
        NpcDialogueGraph loaded;
        try {
            loaded = NpcDialogueGraph.load(tag);
        } catch (RuntimeException broken) {
            return;
        }
        if (loaded == null) return;
        graphs[tab] = loaded;
        graph = loaded;
        selected = -1;
        multi.clear();
        dirty = true;
        status = "";
    }

    private void undoStep() {
        if (undo.isEmpty() || graph == null) return;
        redo.push(graph.save());
        restore(undo.pop());
    }

    private void redoStep() {
        if (redo.isEmpty() || graph == null) return;
        undo.push(graph.save());
        restore(redo.pop());
    }

    /* ------------------------------------------------------------------ links */

    /** One cable: from node and output index to node and input index, plus its colour. */
    private static final class Link {
        final int from;
        final int port;
        final int to;
        final int toPort;
        final int color;

        Link(int from, int port, int to, int toPort, int color) {
            this.from = from;
            this.port = port;
            this.to = to;
            this.toPort = toPort;
            this.color = color;
        }
    }

    /**
     * Every outgoing link of the graph, read from the same fields the model uses for its own
     * wiring: {@code next}, {@code falseNext}, {@code varInputNode}, {@code choices},
     * {@code triggers} and {@code randomBranches}.
     */
    private List<Link> links() {
        List<Link> out = new ArrayList<>();
        if (graph == null) return out;
        for (Node node : graph.nodes.values()) {
            if (node == null) continue;
            if (node.next >= 0) {
                Node target = graph.get(node.next);
                if (target != null) {
                    int toPort = target.type == NpcDialogueGraph.IF
                            && node.type == NpcDialogueGraph.VARIABLE ? 1 : 0;
                    out.add(new Link(node.id, 0, target.id, toPort, linkColor(node, 0)));
                }
            }
            if (node.type == NpcDialogueGraph.IF && node.falseNext >= 0) {
                Node target = graph.get(node.falseNext);
                if (target != null) {
                    out.add(new Link(node.id, 1, target.id, 0, WorkspaceTheme305.DANGER));
                }
            }
            if (node.type == NpcDialogueGraph.IF && node.varInputNode >= 0
                    && node.varInputNode != node.next) {
                Node source = graph.get(node.varInputNode);
                if (source != null) {
                    out.add(new Link(source.id, 0, node.id, 1, VALUE_WIRE));
                }
            }
            if (!node.choices.isEmpty()) {
                for (Object raw : node.choices) {
                    if (!(raw instanceof Integer)) continue;
                    Node target = graph.get((Integer) raw);
                    if (target != null) {
                        out.add(new Link(node.id, 0, target.id, 0, linkColor(node, 0)));
                    }
                }
            }
            if (!node.triggers.isEmpty()) {
                for (Object raw : node.triggers) {
                    if (!(raw instanceof Integer)) continue;
                    Node target = graph.get((Integer) raw);
                    if (target != null) {
                        out.add(new Link(node.id, 0, target.id, 0, WorkspaceTheme305.ACCENT_LIGHT));
                    }
                }
            }
            if (node.type == NpcDialogueGraph.RANDOM) {
                for (int i = 0; i < node.randomBranches.size(); i++) {
                    Object raw = node.randomBranches.get(i);
                    if (!(raw instanceof Integer)) continue;
                    Node target = graph.get((Integer) raw);
                    if (target != null) {
                        out.add(new Link(node.id, i, target.id, 0, BRANCH_WIRE));
                    }
                }
            }
        }
        return out;
    }

    private static final int VALUE_WIRE = 0xff4c8dfe;
    private static final int BRANCH_WIRE = 0xff8f7fd6;

    private int linkColor(Node node, int port) {
        if (node.type == NpcDialogueGraph.VARIABLE) return VALUE_WIRE;
        if (port == 1) return WorkspaceTheme305.DANGER;
        return WorkspaceTheme305.SECONDARY;
    }

    private int portCount(Node node, boolean output) {
        if (!output) return 1;
        if (node.type == NpcDialogueGraph.IF) return 2;
        if (node.type == NpcDialogueGraph.RANDOM) return Math.max(2, node.randomMax);
        return 1;
    }

    /* ------------------------------------------------------------------ render */

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        NpcNodeLibrary305.setMouse(mouseX, mouseY);

        WorkspaceTheme305.fill(graphics, 0, 0, width, height, WorkspaceTheme305.CANVAS);
        WorkspaceSkin304.drawCanvas(graphics, canvas);

        if (graph == null) {
            drawEmptyTab(graphics, font, mouseX, mouseY);
        } else {
            drawCables(graphics, font);
            drawNodes(graphics, font);
            drawLiveCable(graphics, mouseX, mouseY);
            drawBox(graphics, mouseX, mouseY);
        }

        NpcNodeLibrary305.draw(graphics, font, height - 4, canvas.top);
        drawTopBar(graphics, font, mouseX, mouseY);
        if (!status.isEmpty()) {
            graphics.drawString(font, status, 8, height - 12, WorkspaceTheme305.ACCENT_LIGHT, false);
        }
    }

    private void drawTopBar(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        WorkspaceTheme305.drawTitleBar(graphics, 0, 0, width, TOP - 2);
        graphics.drawString(font, tr("gui.settledlands.workspace.title"), 8, 8,
                WorkspaceTheme305.PRIMARY, false);
        for (int[] rect : topButtons()) {
            boolean hover = inside(mouseX, mouseY, rect);
            WorkspaceTheme305.drawRow(graphics, rect[0], rect[1], rect[2] - rect[0],
                    rect[3] - rect[1], rect[4] == 0 ? WorkspaceTheme305.ACCENT
                            : WorkspaceTheme305.ACCENT_LIGHT,
                    hover, rect[4] >= 0 && rect[4] == tab);
            graphics.drawString(font, tr(LABELS[Math.max(0, Math.min(LABELS.length - 1, rect[4]))]), rect[0] + 5, rect[1] + 3,
                    hover ? WorkspaceTheme305.PRIMARY_ACTION_TEXT : WorkspaceTheme305.SECONDARY,
                    false);
        }
    }

    /** Button rects, in the order they are drawn; the last field is the action id. */
    private List<int[]> topButtons() {
        List<int[]> out = new ArrayList<>();
        int x = 150;
        String[] tabs = {"gui.settledlands.graph.tab_main", "gui.settledlands.graph.tab_walk",
                "gui.settledlands.graph.tab_anim"};
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < tabs.length; i++) {
            int w = font.width(tr(tabs[i])) + 12;
            out.add(new int[]{x, 4, x + w, 20, i});
            x += w + 4;
        }
        x += 10;
        String[] actions = {"gui.settledlands.graph.save", "gui.settledlands.workspace.legacy",
                "gui.settledlands.graph.close"};
        for (int i = 0; i < actions.length; i++) {
            int w = font.width(tr(actions[i])) + 12;
            out.add(new int[]{x, 4, x + w, 20, 3 + i});
            x += w + 4;
        }
        return out;
    }

    private static final String[] LABELS = {"tab_main", "tab_walk", "tab_anim", "save",
            "legacy", "close"};

    private static boolean inside(int mouseX, int mouseY, int[] rect) {
        return mouseX >= rect[0] && mouseX < rect[2] && mouseY >= rect[1] && mouseY < rect[3];
    }

    private void drawEmptyTab(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        int[] rect = emptyRect();
        boolean hover = inside(mouseX, mouseY, rect);
        WorkspaceTheme305.drawRow(graphics, rect[0], rect[1], rect[2] - rect[0],
                rect[3] - rect[1], WorkspaceTheme305.ACCENT, hover, false);
        graphics.drawCenteredString(font, tr("gui.settledlands.workspace.new_graph"),
                (rect[0] + rect[2]) / 2, rect[1] + 4,
                hover ? WorkspaceTheme305.PRIMARY_ACTION_TEXT : WorkspaceTheme305.SECONDARY);
    }

    private int[] emptyRect() {
        int w = 180;
        int h = 18;
        return new int[]{(width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2};
    }

    /* ------------------------------------------------------------------ painting */

    private void drawNodes(GuiGraphics graphics, Font font) {
        double zoom = canvas.zoom;
        int w = (int) Math.round(NpcGraphCanvas.NODE_W * zoom);
        int h = (int) Math.round(NpcGraphCanvas.NODE_H * zoom);
        for (Node node : graph.nodes.values()) {
            if (node == null || !canvas.visible(node)) continue;
            int x = (int) Math.round(canvas.screenX(node.x));
            int y = (int) Math.round(canvas.screenY(node.y));
            boolean isSelected = selected == node.id || multi.contains(node.id);
            int titleHeight = Math.max(8, (int) Math.round(22.0D * zoom));
            WorkspaceTheme305.drawNodeFrame(graphics, x, y, w, h, titleHeight,
                    headerColor(node), isSelected);

            int textX = x + 8;
            int textWidth = Math.max(8, w - 16);
            graphics.drawString(font, clip(font, nodeTitle(node), textWidth), textX, y + 4,
                    WorkspaceTheme305.PRIMARY, false);
            String body = nodeBody(node);
            if (!body.isEmpty()) {
                int lines = Math.max(1, (h - titleHeight - 8) / 10);
                int line = 0;
                for (String part : wrap(font, body, textWidth, lines)) {
                    graphics.drawString(font, part, textX, y + titleHeight + 4 + line * 10,
                            WorkspaceTheme305.SECONDARY, false);
                    line++;
                }
            }
            drawPorts(graphics, node);
        }
    }

    private void drawPorts(GuiGraphics graphics, Node node) {
        int inX = (int) Math.round(canvas.portScreenX(node, false));
        int inY = (int) Math.round(canvas.portScreenY(node, false, 0));
        WorkspaceTheme305.drawPort(graphics, inX, inY, WorkspaceTheme305.SECONDARY);

        int count = portCount(node, true);
        for (int i = 0; i < count; i++) {
            int outX = (int) Math.round(canvas.portScreenX(node, true));
            int outY = (int) Math.round(canvas.portScreenY(node, true, i));
            int color = node.type == NpcDialogueGraph.RANDOM ? BRANCH_WIRE
                    : node.type == NpcDialogueGraph.IF && i == 1 ? WorkspaceTheme305.DANGER
                            : WorkspaceTheme305.ACCENT_LIGHT;
            WorkspaceTheme305.drawPort(graphics, outX, outY, color);
        }
    }

    private int headerColor(Node node) {
        switch (node.type) {
            case NpcDialogueGraph.LINE:
                return 0xff5c7a99;
            case NpcDialogueGraph.BUTTON:
                return 0xff6b7f5c;
            case NpcDialogueGraph.TRIGGER:
                return 0xff9a6f3f;
            case NpcDialogueGraph.IF:
                return 0xff7a5f9a;
            case NpcDialogueGraph.VARIABLE:
                return 0xff4c6f8f;
            case NpcDialogueGraph.CHECK:
                return 0xff3f7f7a;
            case NpcDialogueGraph.DAMAGE:
                return 0xff9a4a4a;
            case NpcDialogueGraph.TIME:
                return 0xff7f7f3f;
            case NpcDialogueGraph.TIMER:
                return 0xff6f6f7f;
            case NpcDialogueGraph.ANIMATION:
                return 0xff8f5f7f;
            case NpcDialogueGraph.QUEST:
                return 0xff8f7f3f;
            case NpcDialogueGraph.MOVE:
                return 0xff4a7f5f;
            case NpcDialogueGraph.MOBILITY:
                return 0xff4a6f7f;
            case NpcDialogueGraph.RANDOM:
                return 0xff6f5f9f;
            case NpcDialogueGraph.CUTSCENE:
                return 0xff8f5f5f;
            default:
                return WorkspaceTheme305.ACCENT_DARK;
        }
    }

    private String nodeTitle(Node node) {
        switch (node.type) {
            case NpcDialogueGraph.LINE:
                return tr("gui.settledlands.graph.line");
            case NpcDialogueGraph.BUTTON:
                return tr("gui.settledlands.graph.button");
            case NpcDialogueGraph.TRIGGER:
                return tr("gui.settledlands.graph.trigger");
            case NpcDialogueGraph.IF:
                return tr("gui.settledlands.graph.if");
            case NpcDialogueGraph.VARIABLE:
                return tr("gui.settledlands.graph.variable");
            case NpcDialogueGraph.CHECK:
                return tr("gui.settledlands.graph.check");
            case NpcDialogueGraph.DAMAGE:
                return tr("gui.settledlands.graph.damage");
            case NpcDialogueGraph.TIME:
                return tr("gui.settledlands.graph.time");
            case NpcDialogueGraph.TIMER:
                return tr("gui.settledlands.graph.timer");
            case NpcDialogueGraph.ANIMATION:
                return tr("gui.settledlands.graph.anim");
            case NpcDialogueGraph.QUEST:
                return tr("gui.settledlands.graph.quest");
            case NpcDialogueGraph.MOVE:
                return tr("gui.settledlands.graph.move");
            case NpcDialogueGraph.MOBILITY:
                return tr("gui.settledlands.graph.mobility");
            case NpcDialogueGraph.RANDOM:
                return tr("gui.settledlands.graph.random");
            case NpcDialogueGraph.CUTSCENE:
                return tr("gui.settledlands.graph.cutscene");
            default:
                return "#" + node.id;
        }
    }

    private String nodeBody(Node node) {
        String body = rawBody(node);
        return body.length() > 160 ? body.substring(0, 160) + "…" : body;
    }

    private String rawBody(Node node) {
        if (node.text != null && !node.text.isEmpty()) return node.text;
        if (node.action != null && !node.action.isEmpty()) return node.action;
        switch (node.type) {
            case NpcDialogueGraph.VARIABLE:
                return node.pointsName == null || node.pointsName.isEmpty()
                        ? "?" : node.pointsName + " " + node.varValue;
            case NpcDialogueGraph.MOVE:
                return node.moveCoords == null ? "" : node.moveCoords;
            case NpcDialogueGraph.ANIMATION:
                return node.animName == null ? "" : node.animName;
            case NpcDialogueGraph.CUTSCENE:
                return node.cutsceneName == null ? "" : node.cutsceneName;
            case NpcDialogueGraph.MOBILITY:
                return node.mobilityGroupName == null ? "" : node.mobilityGroupName;
            case NpcDialogueGraph.RANDOM:
                return node.randomMin + "…" + node.randomMax;
            default:
                return "";
        }
    }

    private void drawCables(GuiGraphics graphics, Font font) {
        double zoom = canvas.zoom;
        int thickness = Math.max(1, (int) Math.round(2.0D * zoom));
        for (Link link : links()) {
            Node from = graph.get(link.from);
            Node to = graph.get(link.to);
            if (from == null || to == null) continue;
            double x1 = canvas.portScreenX(from, true);
            double y1 = canvas.portScreenY(from, true, link.port);
            double x2 = canvas.portScreenX(to, false);
            double y2 = canvas.portScreenY(to, false, link.toPort);
            drawCable(graphics, x1, y1, x2, y2, link.color, thickness);
        }
    }

    private void drawLiveCable(GuiGraphics graphics, int mouseX, int mouseY) {
        if (linkFrom < 0) return;
        Node from = graph.get(linkFrom);
        if (from == null) return;
        double x1 = canvas.portScreenX(from, true);
        double y1 = canvas.portScreenY(from, true, linkPort);
        drawCable(graphics, x1, y1, mouseX, mouseY, WorkspaceTheme305.ACCENT_LIGHT,
                Math.max(1, (int) Math.round(2.0D * canvas.zoom)));
    }

    /** A cubic cable, sampled into small squares - no shader, no vertex format to match. */
    private void drawCable(GuiGraphics graphics, double x1, double y1, double x2, double y2,
            int color, int thickness) {
        double reach = Math.max(40.0D, Math.abs(x2 - x1) * 0.5D);
        int steps = 22;
        double previousX = x1;
        double previousY = y1;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double u = 1.0D - t;
            double cx1 = x1 + reach;
            double cx2 = x2 - reach;
            double px = u * u * u * x1 + 3 * u * u * t * cx1 + 3 * u * t * t * cx2 + t * t * t * x2;
            double py = u * u * u * y1 + 3 * u * u * t * (y1) + 3 * u * t * t * (y2) + t * t * t * y2;
            drawSegment(graphics, previousX, previousY, px, py, thickness, color);
            previousX = px;
            previousY = py;
        }
    }

    private void drawSegment(GuiGraphics graphics, double x1, double y1, double x2, double y2,
            int thickness, int color) {
        int left = (int) Math.round(Math.min(x1, x2)) - thickness / 2;
        int top = (int) Math.round(Math.min(y1, y2)) - thickness / 2;
        int right = (int) Math.round(Math.max(x1, x2)) + thickness - thickness / 2;
        int bottom = (int) Math.round(Math.max(y1, y2)) + thickness - thickness / 2;
        WorkspaceTheme305.fill(graphics, left, top, right, bottom, color);
    }

    private void drawBox(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!boxSelect) return;
        int x1 = (int) Math.round(Math.min(boxX0, boxX1));
        int y1 = (int) Math.round(Math.min(boxY0, boxY1));
        int x2 = (int) Math.round(Math.max(boxX0, boxX1));
        int y2 = (int) Math.round(Math.max(boxY0, boxY1));
        WorkspaceTheme305.fill(graphics, x1, y1, x2, y2, 0x331f6fbf);
        WorkspaceTheme305.fill(graphics, x1, y1, x2, y1 + 1, WorkspaceTheme305.ACCENT);
        WorkspaceTheme305.fill(graphics, x1, y2 - 1, x2, y2, WorkspaceTheme305.ACCENT);
        WorkspaceTheme305.fill(graphics, x1, y1, x1 + 1, y2, WorkspaceTheme305.ACCENT);
        WorkspaceTheme305.fill(graphics, x2 - 1, y1, x2, y2, WorkspaceTheme305.ACCENT);
    }

    private static String clip(Font font, String text, int width) {
        if (text == null) return "";
        if (font.width(text) <= width) return text;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (font.width(out.toString() + text.charAt(i) + "…") > width) break;
            out.append(text.charAt(i));
        }
        return out + "…";
    }

    private static List<String> wrap(Font font, String text, int width, int lines) {
        List<String> out = new ArrayList<>();
        String rest = text == null ? "" : text.replace('\n', ' ');
        while (!rest.isEmpty() && out.size() < lines) {
            int cut = rest.length();
            while (cut > 0 && font.width(rest.substring(0, cut)) > width) cut--;
            if (cut <= 0) break;
            if (cut < rest.length() && out.size() == lines - 1) {
                out.add(clip(font, rest, width));
                return out;
            }
            int space = rest.lastIndexOf(' ', cut);
            if (space > 0 && cut < rest.length()) cut = space;
            out.add(rest.substring(0, cut));
            rest = rest.substring(Math.min(rest.length(), cut + 1)).trim();
        }
        return out;
    }

    /* ------------------------------------------------------------------ mouse */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int[] rect : topButtons()) {
                if (!inside((int) mouseX, (int) mouseY, rect)) continue;
                topAction(rect[4]);
                return true;
            }
        }
        if (graph == null) {
            if (button == 0 && inside((int) mouseX, (int) mouseY, emptyRect())) {
                NpcDialogueGraph created = new NpcDialogueGraph();
                created.enabled = true;
                graphs[tab] = created;
                graph = created;
                dirty = true;
                frameAll();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (NpcNodeLibrary305.contains(mouseX, mouseY, height - 4, canvas.top)) {
            if (NpcNodeLibrary305.inSearchField(mouseX, mouseY, canvas.top)) {
                NpcNodeLibrary305.setSearchFocused(true);
                search.setValue(NpcNodeLibrary305.query());
                setFocused(search);
                return true;
            }
            NpcNodeLibrary305.setSearchFocused(false);
            setFocused(null);
            int type = NpcNodeLibrary305.entryAt(mouseX, mouseY, height - 4, canvas.top);
            if (type >= 0) addNode(type);
            return true;
        }
        if (!canvas.contains(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);

        if (button == 2) {
            panning = true;
            panStartX = mouseX;
            panStartY = mouseY;
            return true;
        }
        if (button == 1) {
            Link hit = linkAt(mouseX, mouseY);
            if (hit != null) {
                remember();
                graph.disconnect(hit.from, hit.to);
                changed();
                status = tr("gui.settledlands.graph.disconnected");
                return true;
            }
            return true;
        }
        int outPort = canvas.hitPort(graph, mouseX, mouseY, true);
        if (outPort >= 0) {
            linkFrom = outPort;
            linkPort = canvas.hitPortIndex;
            return true;
        }
        int hitNode = canvas.hit(graph, mouseX, mouseY);
        if (hitNode >= 0) {
            if (!multi.contains(hitNode)) {
                multi.clear();
                selected = hitNode;
            }
            dragging = hitNode;
            grab.clear();
            grabWorldX = canvas.worldX(mouseX);
            grabWorldY = canvas.worldY(mouseY);
            Node node = graph.get(hitNode);
            if (node != null) grab.put(hitNode, new int[]{node.x, node.y});
            return true;
        }
        selected = -1;
        multi.clear();
        boxSelect = true;
        boxX0 = mouseX;
        boxY0 = mouseY;
        boxX1 = mouseX;
        boxY1 = mouseY;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX,
            double dragY) {
        if (panning) {
            canvas.pan(mouseX - panStartX, mouseY - panStartY);
            panStartX = mouseX;
            panStartY = mouseY;
            return true;
        }
        if (boxSelect) {
            boxX1 = mouseX;
            boxY1 = mouseY;
            return true;
        }
        if (dragging >= 0 && graph != null) {
            Node node = graph.get(dragging);
            if (node != null) {
                int[] origin = grab.get(dragging);
                int baseX = origin == null ? node.x : origin[0];
                int baseY = origin == null ? node.y : origin[1];
                canvas.move(node, baseX + (canvas.worldX(mouseX) - grabWorldX),
                        baseY + (canvas.worldY(mouseY) - grabWorldY));
                dirty = true;
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (panning && button == 2) {
            panning = false;
            return true;
        }
        if (boxSelect && button == 0) {
            boxSelect = false;
            selectInBox();
            return true;
        }
        if (dragging >= 0 && button == 0) {
            dragging = -1;
            grab.clear();
            changed();
            syncCamera();
            return true;
        }
        if (linkFrom >= 0 && button == 0) {
            int target = canvas.hitPort(graph, mouseX, mouseY, false);
            if (target >= 0 && target != linkFrom) {
                remember();
                boolean connected = false;
                try {
                    connected = graph.connect(linkFrom, target, linkPort);
                } catch (RuntimeException refused) {
                    connected = false;
                }
                if (connected) {
                    status = tr("gui.settledlands.graph.connected");
                    changed();
                } else {
                    undo.poll();
                    status = tr("gui.settledlands.graph.bad_link");
                }
            }
            linkFrom = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void selectInBox() {
        multi.clear();
        double left = Math.min(boxX0, boxX1);
        double right = Math.max(boxX0, boxX1);
        double top = Math.min(boxY0, boxY1);
        double bottom = Math.max(boxY0, boxY1);
        for (Node node : graph.nodes.values()) {
            if (node == null) continue;
            double cx = canvas.screenX(node.x + NpcGraphCanvas.NODE_W / 2.0D);
            double cy = canvas.screenY(node.y + NpcGraphCanvas.NODE_H / 2.0D);
            if (cx >= left && cx <= right && cy >= top && cy <= bottom) multi.add(node.id);
        }
        selected = multi.size() == 1 ? multi.iterator().next() : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (NpcNodeLibrary305.scroll(mouseX, mouseY, scrollY, height - 4,
                graph == null ? TOP : canvas.top)) {
            return true;
        }
        if (canvas.contains(mouseX, mouseY)) {
            canvas.zoomAt(scrollY, mouseX, mouseY);
            syncCamera();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /* ------------------------------------------------------------------ keys */

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean control = (modifiers & 0x2) != 0;
        if (NpcNodeLibrary305.isSearchFocused()) {
            if (keyCode == 256) {
                NpcNodeLibrary305.setQuery("");
                NpcNodeLibrary305.setSearchFocused(false);
                if (search != null) search.setValue("");
                setFocused(null);
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                NpcNodeLibrary305.setSearchFocused(false);
                setFocused(null);
                return true;
            }
            if (search != null && search.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        if (control && keyCode == 83) {
            save();
            return true;
        }
        if (control && keyCode == 90) {
            if ((modifiers & 0x1) != 0) redoStep(); else undoStep();
            return true;
        }
        if (control && keyCode == 89) {
            redoStep();
            return true;
        }
        if (keyCode == 76) {
            NpcNodeLibrary305.toggle();
            return true;
        }
        if (keyCode == 261 && graph != null && selected >= 0) {
            remember();
            graph.remove(selected);
            selected = -1;
            changed();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /* ------------------------------------------------------------------ actions */

    private void topAction(int action) {
        if (action >= 0 && action <= 2) {
            switchTab(action);
            return;
        }
        if (action == 3) {
            save();
            return;
        }
        if (action == 4) {
            syncCamera();
            Minecraft.getInstance().setScreen(
                    new NpcDialogueGraphScreen(entityId, profile, parent, focus));
            return;
        }
        onClose();
    }

    private void switchTab(int next) {
        if (next == tab) return;
        syncCamera();
        tab = next;
        graph = graphs[tab];
        selected = -1;
        multi.clear();
        undo.clear();
        redo.clear();
        if (graph != null) {
            if (graph.hasCamera) {
                canvas.panX = graph.panX;
                canvas.panY = graph.panY;
                canvas.zoom = graph.zoom;
            } else {
                frameAll();
            }
        }
    }

    /** Frames the whole graph, and falls back to a sane camera if it has nothing to frame. */
    private void frameAll() {
        if (graph == null) return;
        canvas.frameAll(graph);
        if (!Double.isFinite(canvas.zoom) || canvas.zoom < 0.05D) {
            canvas.zoom = 1.0D;
            canvas.panX = 60.0D;
            canvas.panY = 90.0D;
        }
        if (!Double.isFinite(canvas.panX) || !Double.isFinite(canvas.panY)) {
            canvas.panX = 60.0D;
            canvas.panY = 90.0D;
        }
        syncCamera();
    }

    private void addNode(int type) {
        if (graph == null) return;
        if (graph.nodes.size() >= NpcDialogueGraph.MAX_NODES) {
            status = tr("gui.settledlands.graph.limit");
            return;
        }
        remember();
        double[] at = viewCentre();
        int x = clampCoord(at[0]);
        int y = clampCoord(at[1]);
        Node node = graph.add(type, x, y);
        if (node == null) {
            undo.poll();
            status = tr("gui.settledlands.graph.limit");
            return;
        }
        selected = node.id;
        multi.clear();
        changed();
    }

    /** The model only accepts coordinates inside its own limit. */
    private static int clampCoord(double value) {
        if (!Double.isFinite(value)) return 0;
        long rounded = Math.round(value);
        long limit = NpcDialogueGraph.MAX_COORD;
        if (rounded > limit) rounded = limit;
        if (rounded < -limit) rounded = -limit;
        return (int) rounded;
    }

    private double[] viewCentre() {
        double x = canvas.worldX((canvas.left + canvas.right) / 2.0D);
        double y = canvas.worldY((canvas.top + canvas.bottom) / 2.0D);
        x -= NpcGraphCanvas.NODE_W / 2.0D;
        y -= NpcGraphCanvas.NODE_H / 2.0D;
        return new double[]{x, y};
    }

    private void changed() {
        dirty = true;
        status = tr("gui.settledlands.graph.unsaved");
    }

    /** The cable under the pointer, sampled the same way it is drawn. */
    private Link linkAt(double mouseX, double mouseY) {
        if (graph == null) return null;
        for (Link link : links()) {
            Node from = graph.get(link.from);
            Node to = graph.get(link.to);
            if (from == null || to == null) continue;
            double x1 = canvas.portScreenX(from, true);
            double y1 = canvas.portScreenY(from, true, link.port);
            double x2 = canvas.portScreenX(to, false);
            double y2 = canvas.portScreenY(to, false, link.toPort);
            double reach = Math.max(40.0D, Math.abs(x2 - x1) * 0.5D);
            for (int i = 0; i <= 22; i++) {
                double t = i / 22.0D;
                double u = 1.0D - t;
                double px = u * u * u * x1 + 3 * u * u * t * (x1 + reach)
                        + 3 * u * t * t * (x2 - reach) + t * t * t * x2;
                double py = u * u * u * y1 + 3 * u * u * t * y1 + 3 * u * t * t * y2
                        + t * t * t * y2;
                if (Math.abs(px - mouseX) <= 6 && Math.abs(py - mouseY) <= 6) return link;
            }
        }
        return null;
    }
}
