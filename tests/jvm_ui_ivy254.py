#!/usr/bin/env python3
"""End-to-end Java 21 (-Xverify:all) harness for Build 254 (and bug reproduction on Build 253).

Exercises real NpcStatsGraph, NpcStatsGraphUi, NpcDialogueGraph, NpcConfigScreen, NpcBranchScreen,
NpcUi$NpcDropdown, NpcDropdownTop254, NpcDefaultModel254, NpcProfile, GuideNpc, GuideTriggerNpc,
GuideCorpseNpc, NpcClient, BbModelStorage, BbModelParser, and BbModel (with real Gson 2.10.1 + JOML 1.10.5).
"""
import argparse, hashlib, os, pathlib, shutil, subprocess, sys, tarfile, tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tools'), str(ROOT / 'tests')]
import build_joml248
import jvm_npc_copy253 as nc253

GSON_SHA = 'e358710d663ca799101a51d0568ccc6dbd30be9bc55b1a85b1a21a93e96e8a9b'
IVY_SHA = 'cc2ddc5c6f3ec988ea3829912ba18d0f5d7a7f44f1beedc3e9309847f0a32560'

EXTRA_OVERRIDES = {
    'net/minecraft/ChatFormatting.java': '''
package net.minecraft;
public enum ChatFormatting {
    BLACK, DARK_GRAY, GRAY, WHITE, RED, YELLOW, GREEN, AQUA;
}
''',
    'net/minecraft/Util.java': '''
package net.minecraft;
import java.nio.file.Path;
public final class Util {
    public static final class OS {
        public void openPath(Path p) {}
    }
    private static final OS PLATFORM = new OS();
    public static OS getPlatform() { return PLATFORM; }
}
''',
    'net/minecraft/network/chat/FormattedText.java': '''
package net.minecraft.network.chat;
public interface FormattedText {
    String getString();
}
''',
    'net/minecraft/network/chat/Component.java': '''
package net.minecraft.network.chat;
public interface Component extends FormattedText {
    @Override String getString();
    default MutableComponent copy() { return new MutableComponent(getString(), getString()); }
    static MutableComponent literal(String s) { return new MutableComponent(s, "literal:" + s); }
    static MutableComponent empty() { return new MutableComponent("", "empty"); }
    static MutableComponent translatable(String key) { return new MutableComponent(key, key); }
    static MutableComponent translatable(String key, Object... args) {
        StringBuilder sb = new StringBuilder(key);
        if (args != null) {
            for (Object a : args) {
                sb.append('|').append(a instanceof Component c ? c.getString() : String.valueOf(a));
            }
        }
        return new MutableComponent(sb.toString(), sb.toString());
    }
}
''',
    'net/minecraft/network/chat/MutableComponent.java': '''
package net.minecraft.network.chat;
import net.minecraft.ChatFormatting;
public class MutableComponent implements Component {
    public String text;
    public final String keyRepr;
    public MutableComponent(String text, String keyRepr) {
        this.text = text == null ? "" : text;
        this.keyRepr = keyRepr == null ? "" : keyRepr;
    }
    @Override public String getString() { return text; }
    @Override public MutableComponent copy() { return new MutableComponent(text, keyRepr); }
    public MutableComponent append(String s) { this.text += (s == null ? "" : s); return this; }
    public MutableComponent append(Component c) { if (c != null) this.text += c.getString(); return this; }
    public MutableComponent withStyle(ChatFormatting f) { return this; }
}
''',
    'com/mojang/blaze3d/vertex/PoseStack.java': '''
package com.mojang.blaze3d.vertex;
import org.joml.Matrix4f;
public class PoseStack {
    public static final class Pose {
        private final Matrix4f mat = new Matrix4f();
        public Matrix4f pose() { return mat; }
    }
    private final Pose poseObj = new Pose();
    public int depth;
    private final double[] zStack = new double[64];
    public double currentZ() { return zStack[depth]; }
    public void pushPose() { zStack[depth + 1] = zStack[depth]; depth++; }
    public void popPose() { if (depth <= 0) throw new IllegalStateException("popPose underflow"); depth--; }
    public void translate(float x, float y, float z) { zStack[depth] += z; }
    public void translate(double x, double y, double z) { zStack[depth] += z; }
    public void scale(float x, float y, float z) {}
    public Pose last() { return poseObj; }
}
''',
    'net/minecraft/client/gui/Font.java': '''
package net.minecraft.client.gui;
import java.util.*;
import net.minecraft.network.chat.FormattedText;
public class Font {
    public int width(String s) { return s == null ? 0 : s.length() * 6; }
    public int width(FormattedText t) { return t == null ? 0 : width(t.getString()); }
    public String plainSubstrByWidth(String s, int maxWidth) {
        if (s == null) return "";
        int maxChars = Math.max(0, maxWidth / 6);
        return s.length() <= maxChars ? s : s.substring(0, maxChars);
    }
    public List<String> split(FormattedText t, int w) {
        return Collections.singletonList(t == null ? "" : t.getString());
    }
}
''',
    'net/minecraft/client/gui/GuiGraphics.java': '''
package net.minecraft.client.gui;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
public class GuiGraphics {
    public static final class DrawOp {
        public final String kind;
        public final String detail;
        public final double z;
        public final int flushEpoch;
        public DrawOp(String kind, String detail, double z, int flushEpoch) {
            this.kind = kind;
            this.detail = detail;
            this.z = z;
            this.flushEpoch = flushEpoch;
        }
    }
    private final PoseStack pose = new PoseStack();
    public final List<DrawOp> ops = new ArrayList<>();
    public int flushCount;
    public int shadowStringCount;
    public int centeredStringCount;

    public PoseStack pose() { return pose; }
    public void flush() { flushCount++; ops.add(new DrawOp("flush", "", pose.currentZ(), flushCount)); }
    public void enableScissor(int a, int b, int c, int d) {}
    public void disableScissor() {}
    public void hLine(int x1, int x2, int y, int color) {
        ops.add(new DrawOp("hline", "", pose.currentZ(), flushCount));
    }
    public void vLine(int x, int y1, int y2, int color) {
        ops.add(new DrawOp("vline", "", pose.currentZ(), flushCount));
    }
    public void fill(int x1, int y1, int x2, int y2, int color) {
        ops.add(new DrawOp("fill", String.format("0x%08x", color), pose.currentZ(), flushCount));
    }
    public int drawString(Font f, String s, int x, int y, int color, boolean shadow) {
        if (shadow) shadowStringCount++;
        double z = pose.currentZ() + (shadow ? 0.03 : 0.0);
        ops.add(new DrawOp(shadow ? "str_shadow" : "str_flat", s == null ? "" : s, z, flushCount));
        return 0;
    }
    public int drawString(Font f, Component c, int x, int y, int color, boolean shadow) {
        return drawString(f, c == null ? "" : c.getString(), x, y, color, shadow);
    }
    public void drawCenteredString(Font f, String s, int cx, int y, int color) {
        centeredStringCount++;
        drawString(f, s, cx, y, color, true);
    }
    public void drawCenteredString(Font f, Component c, int cx, int y, int color) {
        centeredStringCount++;
        drawString(f, c == null ? "" : c.getString(), cx, y, color, true);
    }
    public void renderItem(ItemStack stack, int x, int y) {
        ops.add(new DrawOp("item", "", pose.currentZ() + 150.0, flushCount));
    }
}
''',
    'net/minecraft/client/gui/components/events/GuiEventListener.java': '''
package net.minecraft.client.gui.components.events;
public interface GuiEventListener {
    default boolean mouseClicked(double x, double y, int b) { return false; }
    default boolean mouseReleased(double x, double y, int b) { return false; }
    default boolean mouseDragged(double x, double y, int b, double dx, double dy) { return false; }
    default boolean mouseScrolled(double x, double y, double sx, double sy) { return false; }
    default boolean keyPressed(int k, int s, int m) { return false; }
    default void setFocused(boolean f) {}
    default boolean isFocused() { return false; }
}
''',
    'net/minecraft/client/gui/components/events/ContainerEventHandler.java': '''
package net.minecraft.client.gui.components.events;
import java.util.List;
public interface ContainerEventHandler extends GuiEventListener {
    List<? extends GuiEventListener> children();
    void setFocused(GuiEventListener l);
    void setDragging(boolean d);
}
''',
    'net/minecraft/client/gui/components/Renderable.java': '''
package net.minecraft.client.gui.components;
import net.minecraft.client.gui.GuiGraphics;
public interface Renderable {
    void render(GuiGraphics g, int mx, int my, float pt);
}
''',
    'net/minecraft/client/gui/narration/NarratableEntry.java': '''
package net.minecraft.client.gui.narration;
public interface NarratableEntry {}
''',
    'net/minecraft/client/gui/narration/NarrationElementOutput.java': '''
package net.minecraft.client.gui.narration;
public interface NarrationElementOutput {}
''',
    'net/minecraft/client/gui/components/Tooltip.java': '''
package net.minecraft.client.gui.components;
import net.minecraft.network.chat.Component;
public class Tooltip {
    public final Component text;
    private Tooltip(Component t) { this.text = t; }
    public static Tooltip create(Component t) { return new Tooltip(t); }
}
''',
    'net/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner.java': '''
package net.minecraft.client.gui.screens.inventory.tooltip;
public interface ClientTooltipPositioner {}
''',
    'net/minecraft/client/gui/components/AbstractWidget.java': '''
package net.minecraft.client.gui.components;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
public abstract class AbstractWidget implements Renderable, GuiEventListener, NarratableEntry {
    protected int x, y, width, height;
    public boolean active = true;
    public boolean visible = true;
    private boolean focused;
    protected boolean isHovered;
    private Component message;
    private Tooltip tooltip;

    public AbstractWidget(int x, int y, int width, int height, Component message) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        this.message = message == null ? Component.empty() : message;
    }
    public int getX() { return x; }
    public int getY() { return y; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public void setWidth(int w) { this.width = w; }
    public Component getMessage() { return message; }
    public void setMessage(Component m) { this.message = m == null ? Component.empty() : m; }
    public void setTooltip(Tooltip t) { this.tooltip = t; }
    public Tooltip getTooltip() { return tooltip; }
    public boolean isHoveredOrFocused() { return isHovered || focused; }
    @Override public void setFocused(boolean f) { this.focused = f; }
    @Override public boolean isFocused() { return focused; }
    public boolean isMouseOver(double mx, double my) {
        return active && visible && mx >= x && mx < x + width && my >= y && my < y + height;
    }
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!visible) return;
        this.isHovered = mx >= x && mx < x + width && my >= y && my < y + height;
        renderWidget(g, mx, my, pt);
    }
    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        g.fill(x, y, x + width, y + height, 0xff333333);
        g.drawCenteredString(Minecraft.getInstance().font, getMessage().getString(), x + width / 2, y + 4, 0xffffffff);
    }
    protected void updateWidgetNarration(NarrationElementOutput out) {}
    public void onClick(double mx, double my) {}
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (active && visible && button == 0 && isMouseOver(mx, my)) {
            onClick(mx, my);
            return true;
        }
        return false;
    }
}
''',
    'net/minecraft/client/gui/components/Button.java': '''
package net.minecraft.client.gui.components;
import net.minecraft.network.chat.Component;
public class Button extends AbstractWidget {
    public interface OnPress { void onPress(Button b); }
    private final OnPress onPress;
    protected Button(int x, int y, int w, int h, Component msg, OnPress onPress, Tooltip tip) {
        super(x, y, w, h, msg);
        this.onPress = onPress;
        setTooltip(tip);
    }
    @Override public void onClick(double mx, double my) { if (onPress != null) onPress.onPress(this); }
    public static Builder builder(Component msg, OnPress onPress) { return new Builder(msg, onPress); }
    public static class Builder {
        private final Component msg;
        private final OnPress onPress;
        private int x, y, w = 150, h = 20;
        private Tooltip tip;
        public Builder(Component msg, OnPress onPress) { this.msg = msg; this.onPress = onPress; }
        public Builder bounds(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; return this; }
        public Builder pos(int x, int y) { this.x = x; this.y = y; return this; }
        public Builder size(int w, int h) { this.w = w; this.h = h; return this; }
        public Builder tooltip(Tooltip t) { this.tip = t; return this; }
        public Button build() { return new Button(x, y, w, h, msg, onPress, tip); }
    }
}
''',
    'net/minecraft/client/gui/components/EditBox.java': '''
package net.minecraft.client.gui.components;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
public class EditBox extends AbstractWidget {
    private String value = "";
    private Predicate<String> filter = s -> true;
    private Consumer<String> responder;
    private int maxLength = 32;
    public EditBox(Font font, int x, int y, int w, int h, Component msg) {
        super(x, y, w, h, msg);
    }
    public String getValue() { return value; }
    public void setValue(String v) {
        String s = v == null ? "" : v;
        if (s.length() > maxLength) s = s.substring(0, maxLength);
        if (filter == null || filter.test(s)) {
            this.value = s;
            if (responder != null) responder.accept(this.value);
        }
    }
    public void setMaxLength(int m) { this.maxLength = m; }
    public void setFilter(Predicate<String> p) { this.filter = p; }
    public void setResponder(Consumer<String> c) { this.responder = c; }
    public void setHint(Component h) {}
}
''',
    'net/minecraft/client/gui/components/MultiLineEditBox.java': '''
package net.minecraft.client.gui.components;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
public class MultiLineEditBox extends AbstractWidget {
    private String value = "";
    private Consumer<String> listener;
    public MultiLineEditBox(Font font, int x, int y, int w, int h, Component placeholder, Component message) {
        super(x, y, w, h, message);
    }
    public int getInnerHeight() { return height; }
    public void setHeight(int h) { this.height = h; }
    public void setValue(String v) {
        this.value = v == null ? "" : v;
        if (listener != null) listener.accept(this.value);
    }
    public void setValueListener(Consumer<String> c) { this.listener = c; }
}
''',
    'net/minecraft/client/gui/components/AbstractSliderButton.java': '''
package net.minecraft.client.gui.components;
import net.minecraft.network.chat.Component;
public abstract class AbstractSliderButton extends AbstractWidget {
    protected double value;
    public AbstractSliderButton(int x, int y, int w, int h, Component msg, double value) {
        super(x, y, w, h, msg);
        this.value = value;
    }
    protected abstract void updateMessage();
    protected abstract void applyValue();
}
''',
    'net/minecraft/client/gui/components/Checkbox.java': '''
package net.minecraft.client.gui.components;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
public class Checkbox extends AbstractWidget {
    public interface OnValueChange { void onValueChange(Checkbox cb, boolean selected); }
    private boolean selected;
    private final OnValueChange callback;
    private Checkbox(int x, int y, int w, Component msg, boolean sel, OnValueChange cb) {
        super(x, y, w, 18, msg);
        this.selected = sel;
        this.callback = cb;
    }
    public boolean selected() { return selected; }
    public static Builder builder(Component msg, Font font) { return new Builder(msg); }
    public static class Builder {
        private final Component msg;
        private int x, y, maxW = 120;
        private boolean sel;
        private OnValueChange cb;
        private Tooltip tip;
        public Builder(Component msg) { this.msg = msg; }
        public Builder pos(int x, int y) { this.x = x; this.y = y; return this; }
        public Builder selected(boolean s) { this.sel = s; return this; }
        public Builder maxWidth(int w) { this.maxW = w; return this; }
        public Builder onValueChange(OnValueChange cb) { this.cb = cb; return this; }
        public Builder tooltip(Tooltip t) { this.tip = t; return this; }
        public Checkbox build() {
            Checkbox c = new Checkbox(x, y, maxW, msg, sel, cb);
            c.setTooltip(tip);
            return c;
        }
    }
}
''',
    'net/minecraft/client/gui/screens/Screen.java': '''
package net.minecraft.client.gui.screens;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
public abstract class Screen implements ContainerEventHandler, Renderable {
    protected final Component title;
    public int width = 960;
    public int height = 540;
    protected Minecraft minecraft;
    protected Font font;
    public final List<Renderable> renderables = new ArrayList<>();
    private final List<GuiEventListener> children = new ArrayList<>();
    private GuiEventListener focused;
    private boolean dragging;

    protected Screen(Component title) {
        this.title = title == null ? Component.empty() : title;
        this.minecraft = Minecraft.getInstance();
        this.font = this.minecraft.font;
    }
    protected void init() {}
    public void init(Minecraft mc, int w, int h) {
        this.minecraft = mc;
        this.font = mc.font;
        this.width = w;
        this.height = h;
        rebuildWidgets();
    }
    protected void clearWidgets() {
        renderables.clear();
        children.clear();
    }
    protected void rebuildWidgets() {
        clearWidgets();
        init();
    }
    protected <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T w) {
        renderables.add(w);
        children.add(w);
        return w;
    }
    @Override public List<? extends GuiEventListener> children() { return children; }
    @Override public void setFocused(GuiEventListener l) { this.focused = l; }
    @Override public void setDragging(boolean d) { this.dragging = d; }
    public void tick() {}
    public void onClose() {}
    public boolean isPauseScreen() { return false; }
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x80000000);
    }
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        for (Renderable r : renderables) {
            r.render(g, mx, my, pt);
        }
    }
    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        for (GuiEventListener c : children) {
            if (c.mouseClicked(mx, my, b)) return true;
        }
        return false;
    }
    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) { return false; }
    @Override public boolean mouseReleased(double mx, double my, int b) { return false; }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) { return false; }
    @Override public boolean keyPressed(int k, int s, int m) { return false; }
    public boolean keyReleased(int k, int s, int m) { return false; }
    public void setTooltipForNextRenderPass(Component c) {}
    public void setTooltipForNextRenderPass(List<?> l) {}
    public void setTooltipForNextRenderPass(List<?> l, ClientTooltipPositioner p, boolean f) {}
    public void setTooltipForNextRenderPass(Tooltip t, ClientTooltipPositioner p, boolean f) {}
}
''',
    'net/minecraft/client/gui/screens/inventory/InventoryScreen.java': '''
package net.minecraft.client.gui.screens.inventory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
public class InventoryScreen {
    public static void renderEntityInInventoryFollowsMouse(
            GuiGraphics g, int x1, int y1, int x2, int y2, int scale, float yOff, float mx, float my, LivingEntity entity) {
        // Real Minecraft 1.21.1 InventoryScreen translates entity preview to Z = 50.0 (up to ~95.0 with model depth)
        g.pose().pushPose();
        g.pose().translate(0.0f, 0.0f, 50.0f);
        g.fill(x1, y1, x2, y2, 0xff224466);
        g.pose().translate(0.0f, 0.0f, 45.0f);
        g.fill(x1, y1, x2, y2, 0xff335577);
        g.pose().popPose();
    }
}
''',
    'net/minecraft/network/syncher/SynchedEntityData.java': '''
package net.minecraft.network.syncher;
import java.util.*;
public class SynchedEntityData {
    public static class Builder {
        private final SynchedEntityData data;
        public Builder() { this.data = new SynchedEntityData(); }
        public Builder(SynchedEntityData data) { this.data = data; }
        public <T> Builder define(EntityDataAccessor<T> a, T def) {
            data.map.put(a, def);
            return this;
        }
    }
    private final Map<EntityDataAccessor<?>, Object> map = new IdentityHashMap<>();
    public static <T> EntityDataAccessor<T> defineId(Class<?> cls, EntityDataSerializer<T> ser) {
        return new EntityDataAccessor<>();
    }
    public <T> void set(EntityDataAccessor<T> a, T val) { map.put(a, val); }
    @SuppressWarnings("unchecked")
    public <T> T get(EntityDataAccessor<T> a) {
        return (T) map.get(a);
    }
}
''',
    'net/minecraft/world/entity/Entity.java': '''
package net.minecraft.world.entity;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.extensions.IEntityExtension;
public class Entity implements IEntityExtension {
    private static int NEXT_ID = 100;
    private final EntityType<?> type;
    protected Level level;
    private UUID uuid = UUID.randomUUID();
    private int id = NEXT_ID++;
    public boolean alive = true;
    public boolean noGravity = false;
    private Component customName;
    private boolean customNameVisible;
    protected final SynchedEntityData entityData = new SynchedEntityData();
    private final CompoundTag persistentData = new CompoundTag();
    public Entity(EntityType<?> type, Level level) {
        this.type = type;
        this.level = level;
        defineSynchedData(new SynchedEntityData.Builder(this.entityData));
    }
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    public double distanceToSqr(Entity other) { return 4.0; }
    public EntityType<?> getType() { return type; }
    public Level level() { return level; }
    public UUID getUUID() { return uuid; }
    public void setUUID(UUID u) { this.uuid = u; }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public boolean isAlive() { return alive; }
    public void setNoGravity(boolean v) { this.noGravity = v; }
    public void setCustomName(Component c) { this.customName = c; }
    public Component getCustomName() { return customName; }
    public void setCustomNameVisible(boolean v) { this.customNameVisible = v; }
    public boolean isCustomNameVisible() { return customNameVisible; }
    public void refreshDimensions() {}
    @Override public CompoundTag getPersistentData() { return persistentData; }
    protected void readAdditionalSaveData(CompoundTag tag) {}
    protected void addAdditionalSaveData(CompoundTag tag) {}
    public CompoundTag saveWithoutId(CompoundTag tag) {
        try {
            addAdditionalSaveData(tag);
            return tag;
        } catch (Throwable t) {
            throw new net.minecraft.ReportedException("Saving entity NBT", t);
        }
    }
    public void load(CompoundTag tag) { readAdditionalSaveData(tag); }
}
''',
    'net/minecraft/world/level/Level.java': '''
package net.minecraft.world.level;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.Entity;
public class Level {
    public boolean isClientSide = false;
    protected final Map<Integer, Entity> byId = new HashMap<>();
    protected final Map<UUID, Entity> byUuid = new HashMap<>();
    public boolean isClientSide() { return isClientSide; }
    public RegistryAccess registryAccess() { return RegistryAccess.EMPTY; }
    public int getMinBuildHeight() { return -64; }
    public int getMaxBuildHeight() { return 320; }
    public Entity getEntity(int id) { return byId.get(id); }
    public void addEntity(Entity e) {
        byId.put(e.getId(), e);
        byUuid.put(e.getUUID(), e);
    }
}
''',
    'net/minecraft/client/player/LocalPlayer.java': '''
package net.minecraft.client.player;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
public class LocalPlayer extends Player {
    public LocalPlayer(Level level) { super(level); }
}
''',
    'net/minecraft/client/multiplayer/ClientLevel.java': '''
package net.minecraft.client.multiplayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
public class ClientLevel extends Level {
    public ClientLevel() { this.isClientSide = true; }
    public Iterable<Entity> entitiesForRendering() { return byId.values(); }
    @Override public RegistryAccess registryAccess() { return RegistryAccess.EMPTY; }
}
''',
    'net/minecraft/client/renderer/texture/AbstractTexture.java': '''
package net.minecraft.client.renderer.texture;
public class AbstractTexture {}
''',
    'com/mojang/blaze3d/platform/NativeImage.java': '''
package com.mojang.blaze3d.platform;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
public class NativeImage implements AutoCloseable {
    private final int width, height;
    public NativeImage(int w, int h) { this.width = w; this.height = h; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public static NativeImage read(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) throw new IOException("Invalid PNG stream");
        return new NativeImage(img.getWidth(), img.getHeight());
    }
    @Override public void close() {}
}
''',
    'net/minecraft/client/renderer/texture/DynamicTexture.java': '''
package net.minecraft.client.renderer.texture;
import com.mojang.blaze3d.platform.NativeImage;
public class DynamicTexture extends AbstractTexture {
    public final NativeImage pixels;
    public DynamicTexture(NativeImage img) { this.pixels = img; }
}
''',
    'net/minecraft/client/renderer/texture/TextureManager.java': '''
package net.minecraft.client.renderer.texture;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
public class TextureManager {
    public final Map<ResourceLocation, AbstractTexture> registered = new LinkedHashMap<>();
    public void register(ResourceLocation id, AbstractTexture tex) { registered.put(id, tex); }
}
''',
    'net/minecraft/client/model/geom/PartPose.java': '''
package net.minecraft.client.model.geom;
public class PartPose {
    public final float x, y, z, xRot, yRot, zRot;
    public PartPose(float x, float y, float z, float xRot, float yRot, float zRot) {
        this.x = x; this.y = y; this.z = z; this.xRot = xRot; this.yRot = yRot; this.zRot = zRot;
    }
    public static PartPose offsetAndRotation(float x, float y, float z, float xr, float yr, float zr) {
        return new PartPose(x, y, z, xr, yr, zr);
    }
}
''',
    'net/minecraft/client/model/geom/ModelPart.java': '''
package net.minecraft.client.model.geom;
import java.util.*;
public class ModelPart {
    public static class Cube {
        public Cube() {}
        public Cube(int u, int v, float x, float y, float z, float dx, float dy, float dz,
                    float gx, float gy, float gz, boolean mirror, float tw, float th, Set<?> faces) {}
    }
    public float x, y, z, xRot, yRot, zRot;
    public PartPose initialPose = new PartPose(0, 0, 0, 0, 0, 0);
    public ModelPart(List<?> cubes, Map<String, ModelPart> children) {}
    public void setInitialPose(PartPose p) { this.initialPose = p; }
    public void setPos(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
    public void setRotation(float xr, float yr, float zr) { this.xRot = xr; this.yRot = yr; this.zRot = zr; }
}
''',
    'com/mojang/blaze3d/vertex/VertexFormat.java': '''
package com.mojang.blaze3d.vertex;
public class VertexFormat {
    public enum Mode { QUADS }
}
''',
    'com/mojang/blaze3d/vertex/DefaultVertexFormat.java': '''
package com.mojang.blaze3d.vertex;
public class DefaultVertexFormat {
    public static final VertexFormat POSITION_COLOR = new VertexFormat();
}
''',
    'com/mojang/blaze3d/vertex/MeshData.java': '''
package com.mojang.blaze3d.vertex;
public class MeshData {}
''',
    'com/mojang/blaze3d/vertex/VertexConsumer.java': '''
package com.mojang.blaze3d.vertex;
public interface VertexConsumer {
    default VertexConsumer setColor(int c) { return this; }
}
''',
    'com/mojang/blaze3d/vertex/BufferBuilder.java': '''
package com.mojang.blaze3d.vertex;
import org.joml.Matrix4f;
public class BufferBuilder implements VertexConsumer {
    public VertexConsumer addVertex(Matrix4f m, float x, float y, float z) { return this; }
    public MeshData build() { return new MeshData(); }
    public MeshData buildOrThrow() { return new MeshData(); }
}
''',
    'com/mojang/blaze3d/vertex/Tesselator.java': '''
package com.mojang.blaze3d.vertex;
public class Tesselator {
    private static final Tesselator INST = new Tesselator();
    public static Tesselator getInstance() { return INST; }
    public BufferBuilder begin(VertexFormat.Mode m, VertexFormat f) { return new BufferBuilder(); }
}
''',
    'com/mojang/blaze3d/vertex/BufferUploader.java': '''
package com.mojang.blaze3d.vertex;
public class BufferUploader {
    public static void drawWithShader(MeshData m) {}
}
''',
    'com/mojang/blaze3d/systems/RenderSystem.java': '''
package com.mojang.blaze3d.systems;
import java.util.function.Supplier;
public class RenderSystem {
    public static void defaultBlendFunc() {}
    public static void enableBlend() {}
    public static void disableBlend() {}
    public static void enableCull() {}
    public static void disableCull() {}
    public static void enableDepthTest() {}
    public static void disableDepthTest() {}
    public static void setShader(Supplier<?> s) {}
}
''',
    'net/minecraft/client/renderer/ShaderInstance.java': '''
package net.minecraft.client.renderer;
public class ShaderInstance {}
''',
    'net/minecraft/client/renderer/GameRenderer.java': '''
package net.minecraft.client.renderer;
public class GameRenderer {
    public static ShaderInstance getPositionColorShader() { return new ShaderInstance(); }
}
''',
    'com/mojang/blaze3d/platform/Window.java': '''
package com.mojang.blaze3d.platform;
public class Window {
    public long getWindow() { return 0L; }
}
''',
    'com/mojang/blaze3d/platform/InputConstants.java': '''
package com.mojang.blaze3d.platform;
public class InputConstants {
    public static boolean isKeyDown(long window, int key) { return false; }
}
''',
    'net/minecraft/client/Minecraft.java': '''
package net.minecraft.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.world.phys.HitResult;
public class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    public LocalPlayer player;
    public ClientLevel level;
    public HitResult hitResult;
    public Screen screen;
    public Font font = new Font();
    public final TextureManager textureManager = new TextureManager();
    public static Minecraft getInstance() { return INSTANCE; }
    public void setScreen(Screen s) { this.screen = s; }
    public TextureManager getTextureManager() { return textureManager; }
    public com.mojang.blaze3d.platform.Window getWindow() { return null; }
}
''',
    'net/neoforged/fml/loading/FMLPaths.java': '''
package net.neoforged.fml.loading;
import java.nio.file.Path;
public enum FMLPaths {
    GAMEDIR;
    public static Path currentGameDir;
    public Path get() { return currentGameDir; }
}
''',
    'testing/UiIvy254Scenario.java': '''
package testing;

import dev.settledlands.model.BbAnimation;
import dev.settledlands.model.BbModel;
import dev.settledlands.model.BbModelManager;
import dev.settledlands.model.BbModelParser;
import dev.settledlands.model.BbModelStorage;
import dev.settledlands.npc.GuideCorpseNpc;
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.GuideTriggerNpc;
import dev.settledlands.npc.NpcContent;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcProfile;
import dev.settledlands.npc.NpcStatsGraph;
import dev.settledlands.npc.client.NpcBranchScreen;
import dev.settledlands.npc.client.NpcClient;
import dev.settledlands.npc.client.NpcConfigScreen;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.NpcStatsGraphUi;
import dev.settledlands.npc.client.NpcUi;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.fml.loading.FMLPaths;

public final class UiIvy254Scenario {
    private static void ok(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    @SuppressWarnings("unchecked")
    private static NpcUi.NpcDropdown findDropdown(Object screen) throws Exception {
        net.minecraft.client.gui.screens.Screen s = (net.minecraft.client.gui.screens.Screen) screen;
        for (Object r : s.renderables) {
            if (r instanceof NpcUi.NpcDropdown d) return d;
        }
        throw new AssertionError("No NpcDropdown found on " + screen.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private static NpcUi.NpcDropdown findModelDropdown(NpcConfigScreen screen) throws Exception {
        Field optField = NpcUi.NpcDropdown.class.getDeclaredField("entries");
        optField.setAccessible(true);
        for (Object r : screen.renderables) {
            if (r instanceof NpcUi.NpcDropdown d) {
                List<?> opts = (List<?>) optField.get(d);
                if (!opts.isEmpty() && String.valueOf(((net.minecraft.network.chat.Component) opts.get(0)).getString()).contains("model.default")) {
                    return d;
                }
            }
        }
        throw new AssertionError("No model NpcDropdown found on NpcConfigScreen");
    }

    private static NpcDialogueGraph makeDefaultNpcDialogueGraph() {
        // Matches NpcDialogueGraph.fromLegacy default layout:
        // #0 "Начальная реплика" at (0, 0), entry = 0
        // #1 "Прибытие #1" at (0, -240), special = 1
        // #2 "Прибытие #2" at (280, -240), special = 2
        NpcDialogueGraph g = new NpcDialogueGraph();
        NpcDialogueGraph.Node start = g.add(0, 0, 0);
        g.entry = start.id;
        NpcDialogueGraph.Node arr1 = g.add(0, 0, -240);
        arr1.special = 1;
        NpcDialogueGraph.Node arr2 = g.add(0, 280, -240);
        arr2.special = 2;
        return g;
    }

    private static void reproduce253(Path gameDir) throws Exception {
        FMLPaths.currentGameDir = gameDir;

        // 1. Bug #1 in 253: Characteristics placed at (568, -240) to the right of "Прибытие #2"
        NpcDialogueGraph g = makeDefaultNpcDialogueGraph();
        ok(NpcStatsGraph.ensure(g), "253 ensure");
        NpcDialogueGraph.Node stats253 = NpcStatsGraph.find(g);
        ok(stats253 != null && stats253.x == 568 && stats253.y == -240,
                "expected 253 right-of-arrival2 (568,-240), got (" + stats253.x + "," + stats253.y + ")");

        // 2. Bug #2 in 253: NpcStatsGraphUi.draw uses drawCenteredString -> shadow Z = +0.03f > overlapping window Z = 0.0f
        ClientLevel cl = new ClientLevel();
        Minecraft mc = Minecraft.getInstance();
        mc.level = cl;
        mc.player = new LocalPlayer(cl);
        GuideNpc npc253 = new GuideNpc(NpcContent.guide(), cl);
        cl.addEntity(npc253);

        NpcConfigScreen cfg = new NpcConfigScreen(npc253.getId(), npc253.profile(), new ArrayList<>());
        mc.screen = cfg;
        cfg.init(mc, 960, 540);
        NpcDialogueGraphScreen graphScr253 = new NpcDialogueGraphScreen(npc253.getId(), npc253.profile(), cfg, 0);
        graphScr253.init(mc, 960, 540);
        GuiGraphics statsGfx253 = new GuiGraphics();
        NpcStatsGraphUi.draw(graphScr253, statsGfx253, stats253);
        ok(statsGfx253.centeredStringCount == 9 && statsGfx253.shadowStringCount == 9,
                "expected 253 NpcStatsGraphUi.draw to emit 9 shadowed centered strings (Z=+0.03f), got " + statsGfx253.shadowStringCount);

        // 4. Bug #4 in 253: default NPC model is "" instead of "ivy_oleander.bbmodel"
        ok("".equals(new NpcProfile().texture) && "".equals(npc253.texturePath()),
                "expected 253 empty default texture");
        ok(GuideNpc.class.getResourceAsStream("/assets/settledlands/dragon_default/ivy_oleander.bbmodel") == null,
                "253 did not bundle ivy_oleander.bbmodel");

        // 3. Bug #3 in 253: NpcConfigScreen.render draws open dropdown popup at Z = 0.0f (behind preview Z = 50..95)
        mc.screen = cfg;
        NpcUi.NpcDropdown dd = findModelDropdown(cfg);
        cfg.openDropdown(dd);
        Method toggle = NpcUi.NpcDropdown.class.getDeclaredMethod("onClick", double.class, double.class);
        toggle.setAccessible(true);
        toggle.invoke(dd, (double) dd.getX() + 2, (double) dd.getY() + 2);
        GuiGraphics gfx = new GuiGraphics();
        cfg.render(gfx, dd.getX() + 10, dd.getY() + dd.getHeight() + 5, 0.0f);
        double popupBackdropZ = -1.0;
        double previewMaxZ = 0.0;
        for (GuiGraphics.DrawOp op : gfx.ops) {
            if ("fill".equals(op.kind) && "0xff335577".equals(op.detail)) previewMaxZ = Math.max(previewMaxZ, op.z);
            if ("fill".equals(op.kind) && "0xff000000".equals(op.detail)) popupBackdropZ = op.z;
        }
        ok(previewMaxZ >= 95.0 && popupBackdropZ == 0.0,
                "expected 253 popup Z=0.0 behind preview Z=" + previewMaxZ + ", got popupZ=" + popupBackdropZ);

        System.out.println("CONFIRMED build 253 bugs: stats=(568,-240) right of arrival2, popupZ=0.0 behind previewZ=95.0, defaultTexture=''");
    }

    private static void verify254(Path gameDir, Path jarPath, Path outDir, Path autostubDir) throws Exception {
        FMLPaths.currentGameDir = gameDir;
        BbModelManager.clearCache();

        // === Requirement #4: ivy_oleander.bbmodel bundled, extracted on client setup, parsed (82 clips + embedded PNG), and default for GuideNpc ===
        Method setupLambda = NpcClient.class.getDeclaredMethod("lambda$setup$1");
        setupLambda.setAccessible(true);
        setupLambda.invoke(null);

        Path modelsDir = BbModelStorage.getModelsDir();
        Path extracted = modelsDir.resolve("ivy_oleander.bbmodel");
        ok(Files.isRegularFile(extracted), "NpcClient setup must extract ivy_oleander.bbmodel into BbModelStorage.getModelsDir()");
        byte[] extractedBytes = Files.readAllBytes(extracted);
        ok(extractedBytes.length == 2722216
                && "cc2ddc5c6f3ec988ea3829912ba18d0f5d7a7f44f1beedc3e9309847f0a32560".equals(sha256(extractedBytes)),
                "extracted ivy_oleander.bbmodel SHA-256 mismatch");
        List<String> available = BbModelStorage.listAvailableModels();
        ok(available.contains("ivy_oleander.bbmodel"), "listAvailableModels must include ivy_oleander.bbmodel");

        BbModel parsed = BbModelParser.parse(extracted.toFile());
        ok(parsed != null, "BbModelParser must parse ivy_oleander.bbmodel");
        ok(parsed.getTextureLocation() != null, "embedded base64 PNG texture must load in BbModelParser");
        Field animsField = BbModel.class.getDeclaredField("animations");
        animsField.setAccessible(true);
        java.util.Map<?, ?> animMap = (java.util.Map<?, ?>) animsField.get(parsed);
        ok(animMap.size() == 82, "expected 82 animations in ivy_oleander.bbmodel, got " + animMap.size());
        for (String shortClip : new String[]{"idle", "walk", "greetings", "orthodox_idle", "orthodox_left_jab"}) {
            BbAnimation clip = parsed.findAnimation(shortClip);
            ok(clip != null, "BbModel.findAnimation must suffix-match ivy_oleander.animation." + shortClip);
        }

        // Default model across NpcProfile, GuideNpc, GuideTriggerNpc, GuideCorpseNpc, and NBT load
        NpcProfile freshProfile = new NpcProfile();
        ok("ivy_oleander.bbmodel".equals(freshProfile.texture), "new NpcProfile().texture must default to ivy_oleander.bbmodel");

        ClientLevel cl = new ClientLevel();
        Minecraft mc = Minecraft.getInstance();
        mc.level = cl;
        mc.player = new LocalPlayer(cl);

        GuideNpc npc = new GuideNpc(NpcContent.guide(), cl);
        cl.addEntity(npc);
        ok("ivy_oleander.bbmodel".equals(npc.texturePath()) && "ivy_oleander.bbmodel".equals(npc.profile().texture),
                "new GuideNpc must default to ivy_oleander.bbmodel on both entityData and profile");
        ok(npc.aiMode() == 0, "default GuideNpc must stay peaceful (aiMode == 0)");

        // Missing Guide tag or missing Texture tag in NBT still defaults to ivy_oleander.bbmodel
        GuideNpc summoned = new GuideNpc(NpcContent.guide(), cl);
        summoned.setProfile(new NpcProfile());
        summoned.profile().texture = "";
        summoned.applyProfile();
        summoned.readAdditionalSaveData(new CompoundTag());
        ok("ivy_oleander.bbmodel".equals(summoned.texturePath()) && "ivy_oleander.bbmodel".equals(summoned.profile().texture),
                "/summon GuideNpc without Guide NBT must sync ivy_oleander.bbmodel");

        CompoundTag noTexProfile = new CompoundTag();
        noTexProfile.putString("Name", "СтарыйНПС");
        NpcProfile loadedLegacy = NpcProfile.load(noTexProfile, RegistryAccess.EMPTY);
        ok("ivy_oleander.bbmodel".equals(loadedLegacy.texture), "legacy NBT without Texture tag must default to ivy_oleander.bbmodel");

        // Explicit "" ("Стандартная (Guide NPC)") and explicit custom model must round-trip faithfully!
        NpcProfile explicitVanilla = new NpcProfile();
        explicitVanilla.texture = "";
        CompoundTag savedVanilla = explicitVanilla.save(RegistryAccess.EMPTY);
        NpcProfile reloadedVanilla = NpcProfile.load(savedVanilla, RegistryAccess.EMPTY);
        ok("".equals(reloadedVanilla.texture), "explicit empty Texture (standard Guide NPC) must be preserved across save/load");

        NpcProfile explicitCustom = new NpcProfile();
        explicitCustom.texture = "ivy.bbmodel";
        ok("ivy.bbmodel".equals(NpcProfile.load(explicitCustom.save(RegistryAccess.EMPTY), RegistryAccess.EMPTY).texture),
                "explicit custom Texture must be preserved across save/load");

        // Trigger and Corpse subclasses must keep ""
        GuideTriggerNpc trigger = new GuideTriggerNpc(NpcContent.guideTrigger(), cl);
        GuideCorpseNpc corpse = new GuideCorpseNpc(NpcContent.guideCorpse(), cl);
        ok("".equals(trigger.texturePath()) && "".equals(trigger.profile().texture), "GuideTriggerNpc must keep empty texture");
        ok("".equals(corpse.texturePath()) && "".equals(corpse.profile().texture), "GuideCorpseNpc must keep empty texture");

        // Dedicated-server ClassLoader check: NpcDefaultModel254, NpcProfile, GuideNpc must NOT touch net.minecraft.client.*
        ClassLoader serverLoader = new URLClassLoader(
                new URL[]{outDir.toUri().toURL(), jarPath.toUri().toURL(), autostubDir.toUri().toURL()},
                ClassLoader.getPlatformClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.minecraft.client.") || name.startsWith("dev.settledlands.npc.client.")) {
                    throw new ClassNotFoundException("Dedicated server forbids client class: " + name);
                }
                return super.loadClass(name, resolve);
            }
        };
        Class<?> srvHelper = Class.forName("dev.settledlands.npc.NpcDefaultModel254", true, serverLoader);
        Class<?> srvProfile = Class.forName("dev.settledlands.npc.NpcProfile", true, serverLoader);
        Class<?> srvGuide = Class.forName("dev.settledlands.npc.GuideNpc", true, serverLoader);
        Object srvProfInst = srvProfile.getConstructor().newInstance();
        ok("ivy_oleander.bbmodel".equals(srvProfile.getField("texture").get(srvProfInst)),
                "dedicated server NpcProfile default texture");
        ok(srvHelper != null && srvGuide != null, "dedicated server classes loaded cleanly");

        // === Requirement #3: NpcConfigScreen & NpcBranchScreen render open dropdown popup at Z = +500 above preview & widgets ===
        NpcConfigScreen cfg = new NpcConfigScreen(npc.getId(), npc.profile(), new ArrayList<>());
        mc.screen = cfg;
        cfg.init(mc, 960, 540);
        NpcUi.NpcDropdown modelDd = findModelDropdown(cfg);
        Field selField = NpcUi.NpcDropdown.class.getDeclaredField("index");
        selField.setAccessible(true);
        int selectedIdx = selField.getInt(modelDd);
        Field optField = NpcUi.NpcDropdown.class.getDeclaredField("entries");
        optField.setAccessible(true);
        List<?> opts = (List<?>) optField.get(modelDd);
        String selectedLabel = ((net.minecraft.network.chat.Component) opts.get(selectedIdx)).getString();
        ok(selectedIdx >= 1 && selectedLabel.contains("ivy_oleander.bbmodel"),
                "NpcConfigScreen model dropdown must select ivy_oleander.bbmodel by default, got idx=" + selectedIdx + " label=" + selectedLabel);

        Method clickDd = NpcUi.NpcDropdown.class.getDeclaredMethod("onClick", double.class, double.class);
        clickDd.setAccessible(true);
        clickDd.invoke(modelDd, (double) modelDd.getX() + 4, (double) modelDd.getY() + 4);
        ok(cfg.activeDropdown() == modelDd, "clicking modelDropdown must open it on NpcConfigScreen");

        GuiGraphics cfgGfx = new GuiGraphics();
        cfg.render(cfgGfx, modelDd.getX() + 10, modelDd.getY() + modelDd.getHeight() + 6, 0.0f);
        ok(cfgGfx.pose().depth == 0, "PoseStack depth must return to 0 after NpcConfigScreen.render");

        int backdropIdx = -1;
        double backdropZ = -1.0;
        double maxOtherZ = 0.0;
        int preFlushEpoch = -1;
        for (int i = 0; i < cfgGfx.ops.size(); i++) {
            GuiGraphics.DrawOp op = cfgGfx.ops.get(i);
            if ("fill".equals(op.kind) && "0xff000000".equals(op.detail) && op.z >= 500.0) {
                backdropIdx = i;
                backdropZ = op.z;
                preFlushEpoch = op.flushEpoch;
            } else if (op.z < 499.0 && !"flush".equals(op.kind)) {
                maxOtherZ = Math.max(maxOtherZ, op.z);
            }
        }
        ok(backdropIdx > 0 && backdropZ == 500.0 && maxOtherZ >= 95.0 && backdropZ > maxOtherZ,
                "NpcConfigScreen dropdown popup must render at Z=500 above preview/widgets (got popupZ=" + backdropZ + ", otherMaxZ=" + maxOtherZ + ")");
        ok(preFlushEpoch >= 1 && cfgGfx.flushCount >= preFlushEpoch + 1,
                "renderPopupTop must flush before and after popup rendering");
        for (int i = backdropIdx; i < cfgGfx.ops.size(); i++) {
            GuiGraphics.DrawOp op = cfgGfx.ops.get(i);
            if (!"flush".equals(op.kind)) {
                ok(op.z >= 500.0, "every popup draw op in NpcConfigScreen must stay at Z>=500, got " + op.kind + " at Z=" + op.z);
            }
        }

        // Also verify NpcBranchScreen open dropdown renders at Z = +500 above CommandTabCompleter & widgets!
        NpcProfile.Topic topic = new NpcProfile.Topic();
        topic.title = "Ветка";
        npc.profile().topics.add(topic);
        NpcBranchScreen br = new NpcBranchScreen(npc.getId(), npc.profile(), 0, new ArrayList<>());
        mc.screen = br;
        br.init(mc, 960, 540);
        NpcUi.NpcDropdown brDd = findDropdown(br);
        clickDd.invoke(brDd, (double) brDd.getX() + 4, (double) brDd.getY() + 4);
        ok(br.activeDropdown() == brDd, "clicking dropdown on NpcBranchScreen must open it");
        GuiGraphics brGfx = new GuiGraphics();
        br.render(brGfx, brDd.getX() + 8, brDd.getY() + brDd.getHeight() + 5, 0.0f);
        ok(brGfx.pose().depth == 0, "PoseStack depth must return to 0 after NpcBranchScreen.render");
        boolean foundBranchPopup500 = false;
        for (GuiGraphics.DrawOp op : brGfx.ops) {
            if ("fill".equals(op.kind) && "0xff000000".equals(op.detail) && op.z == 500.0) {
                foundBranchPopup500 = true;
            }
        }
        ok(foundBranchPopup500, "NpcBranchScreen dropdown popup must also render at Z=500.0");

        // === Requirement #1, #2, #5: Characteristics left of initial line, zero shadow Z-leak, and bottom Randomize [-2..+2] ===
        NpcDialogueGraph g = makeDefaultNpcDialogueGraph();
        ok(NpcStatsGraph.ensure(g), "254 ensure default graph");
        NpcDialogueGraph.Node stats = NpcStatsGraph.find(g);
        NpcDialogueGraph.Node entryNode = g.get(g.entry);
        ok(stats != null && stats.x == -360 && stats.y == 0 && stats.x + NpcStatsGraphUi.WIDTH < entryNode.x,
                "Characteristics must default to (-360, 0) strictly left of initial line (0, 0)");

        NpcDialogueGraphScreen graphScr = new NpcDialogueGraphScreen(npc.getId(), npc.profile(), cfg, 0);
        graphScr.init(mc, 960, 540);
        NpcDialogueGraph scrGraph = graphScr.settledlands$graph();
        NpcDialogueGraph.Node scrStats = NpcStatsGraph.find(scrGraph);
        ok(scrStats != null && scrStats.x == -360 && scrStats.y == 0, "NpcDialogueGraphScreen default Characteristics at (-360, 0)");
        NpcGraphCanvas scrCanvas = graphScr.settledlands$canvas();
        scrCanvas.focus(scrStats);
        ok(scrCanvas.hit(scrGraph, scrCanvas.screenX(scrStats.x + 280), scrCanvas.screenY(scrStats.y + 165)) == scrStats.id,
                "Characteristics hit area must cover full 170px height");

        GuiGraphics statsGfx = new GuiGraphics();
        NpcStatsGraphUi.draw(graphScr, statsGfx, scrStats);
        ok(statsGfx.centeredStringCount == 0 && statsGfx.shadowStringCount == 0,
                "NpcStatsGraphUi.draw must emit 0 shadowed strings (Z=0.0f)");
        for (GuiGraphics.DrawOp op : statsGfx.ops) {
            ok(op.z == 0.0, "every Characteristics draw op must stay at Z=0.0 so overlapping nodes/Inspector cover it, got Z=" + op.z);
        }

        // Verify randomize on real NpcDialogueGraphScreen records undo & marks dirty
        scrStats.varValue = 40;
        scrStats.checkRadius = 50;
        scrStats.checkCooldown = 60;
        Field dirtyField = NpcDialogueGraphScreen.class.getDeclaredField("dirty");
        dirtyField.setAccessible(true);
        dirtyField.setBoolean(graphScr, false);
        ok(NpcStatsGraphUi.randomize(graphScr, scrStats, new Random(19L)), "randomize mutates stats");
        ok(dirtyField.getBoolean(graphScr), "randomize must call settledlands$changed() (dirty == true)");
        Method historyM = NpcDialogueGraphScreen.class.getDeclaredMethod("history", boolean.class);
        historyM.setAccessible(true);
        historyM.invoke(graphScr, false);
        NpcDialogueGraph undone = graphScr.settledlands$graph();
        ok(NpcStatsGraph.health(undone) == 40 && NpcStatsGraph.damage(undone) == 50 && NpcStatsGraph.speed(undone) == 60,
                "Ctrl+Z undo must restore pre-randomize stats (settledlands$remember() called before mutation)");

        // Locale strings check inside JAR
        try (InputStream ruIn = GuideNpc.class.getResourceAsStream("/assets/settledlands/lang/ru_ru.json");
             InputStream enIn = GuideNpc.class.getResourceAsStream("/assets/settledlands/lang/en_us.json")) {
            String ru = new String(ruIn.readAllBytes(), StandardCharsets.UTF_8);
            String en = new String(enIn.readAllBytes(), StandardCharsets.UTF_8);
            ok(ru.contains("\\"gui.settledlands.stats.randomize\\": \\"Рандомизировать\\""), "ru_ru randomize translation");
            ok(en.contains("\\"gui.settledlands.stats.randomize\\": \\"Randomize\\""), "en_us randomize translation");
        }

        // Verify NpcStatsGraphUi constants & randomStep distribution
        ok(NpcStatsGraphUi.WIDTH == 300 && NpcStatsGraphUi.HEIGHT == 170, "Characteristics node dimensions 300x170");
        Random distRng = new Random(20261029L);
        int[] hist = new int[5];
        for (int i = 0; i < 3000; i++) {
            int d = NpcStatsGraph.randomStep(50, distRng) - 50;
            ok(d >= -2 && d <= 2, "randomStep delta out of [-2,+2]: " + d);
            hist[d + 2]++;
        }
        for (int k = 0; k < 5; k++) {
            ok(hist[k] >= 450, "all 5 deltas {-2,-1,0,+1,+2} must occur, delta=" + (k - 2) + " count=" + hist[k]);
        }

        System.out.println("PASS build 254 UI & Ivy: stats at (-360,0) left of initial line, zero Z-shadow leak, dropdown popups at Z=500 above 3D preview, ivy_oleander.bbmodel default (82 clips + PNG + server-safe), bottom Randomize [-2..+2]");
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        Path gameDir = Path.of(args[1]);
        if ("reproduce-253".equals(mode)) {
            reproduce253(gameDir);
        } else if ("verify-254".equals(mode)) {
            verify254(gameDir, Path.of(args[2]), Path.of(args[3]), Path.of(args[4]));
        } else {
            throw new IllegalArgumentException(mode);
        }
    }
}
''',
}


def build_gson(java: pathlib.Path, gson_source: pathlib.Path, work: pathlib.Path) -> pathlib.Path:
    assert hashlib.sha256(gson_source.read_bytes()).hexdigest() == GSON_SHA
    src_root = work / 'gson_src'
    out = work / 'gson_classes'
    src_root.mkdir()
    out.mkdir()
    with tarfile.open(gson_source, 'r:gz') as tf:
        for member in tf.getmembers():
            if member.isfile() and '/gson/src/main/java/' in member.name and member.name.endswith('.java'):
                rel = member.name.split('/gson/src/main/java/', 1)[1]
                if rel == 'module-info.java':
                    continue
                dest = src_root / rel
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(tf.extractfile(member).read())
            elif member.isfile() and member.name.endswith('GsonBuildConfig.java') and 'java-templates' in member.name:
                dest = src_root / 'com/google/gson/internal/GsonBuildConfig.java'
                dest.parent.mkdir(parents=True, exist_ok=True)
                txt = tf.extractfile(member).read().decode('utf-8').replace('${project.version}', '2.10.1')
                dest.write_text(txt, encoding='utf-8')
    subprocess.run(
        [str(java), '-jar', str(ROOT / 'tools/vendor/ecj-3.46.100.jar'),
         '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-d', str(out), *map(str, sorted(src_root.rglob('*.java')))],
        check=True,
    )
    return out


def make_fixture(java: pathlib.Path, jar: pathlib.Path, gson_source: pathlib.Path, tmp: pathlib.Path):
    joml_out = tmp / 'joml'
    build_joml248.build(java, joml_out)
    gson_out = build_gson(java, gson_source, tmp)

    fake = tmp / 'auto'
    fake.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, TMPDIR=str(fake))
    targets = [
        'dev/settledlands/npc/NpcCopyPickPayload#buildCopyEgg(dev.settledlands.npc.GuideNpc)',
        'dev/settledlands/npc/client/NpcPickCopy#onKeyTriggered(net.neoforged.neoforge.client.event.InputEvent$InteractionKeyMappingTriggered)',
        'dev/settledlands/npc/NpcContent#spawnEgg',
        'dev/settledlands/npc/GuideNpc#readAdditionalSaveData(net.minecraft.nbt.CompoundTag)',
        'dev/settledlands/npc/IvyCombatAi#launchFireball',
        'dev/settledlands/npc/GuideTriggerNpc#isInvisible',
        'dev/settledlands/npc/GuideCorpseNpc#isCoffin',
        'dev/settledlands/npc/CorpseRecovery#syncHealth(dev.settledlands.npc.GuideCorpseNpc)',
        'dev/settledlands/npc/client/NpcClient#setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent)',
        'dev/settledlands/model/BbModel#getName',
    ]
    r = subprocess.run(
        [sys.executable, str(ROOT / 'tools/jvm_verify.py'), str(jar), *targets, '--java', str(java)],
        env=env, capture_output=True, text=True,
    )
    assert r.returncode == 0 and 'ИТОГ: OK' in r.stdout, (r.stdout, r.stderr)
    autos = list(fake.glob('jvmverify-*'))
    assert len(autos) == 1
    autostub = autos[0]
    for n in ('java', 'javax', 'jdk', 'sun', 'dev', 'org/joml', 'com/google/gson'):
        shutil.rmtree(autostub / n, ignore_errors=True)

    merged_overrides = dict(nc253.OVERRIDES)
    merged_overrides.pop('testing/NpcCopy253Scenario.java', None)
    merged_overrides.update(EXTRA_OVERRIDES)

    src_dir = tmp / 'src'
    out = tmp / 'out'
    out.mkdir()
    sources = []
    for rel, code in merged_overrides.items():
        p = src_dir / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(code.strip() + '\n', encoding='utf-8')
        sources.append(p)
        cls_stub = autostub / rel.replace('.java', '.class')
        if cls_stub.exists():
            cls_stub.unlink()

    cp = ':'.join(map(str, [jar, joml_out, gson_out, autostub]))
    subprocess.run(
        [str(java), '-jar', str(ROOT / 'tools/vendor/ecj-3.46.100.jar'),
         '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-cp', cp, '-d', str(out), *map(str, sorted(sources))],
        check=True,
    )
    # Copy JOML and Gson classes into out so URLClassLoader in server-isolation check has a clean 3-entry classpath
    for extra in (joml_out, gson_out):
        for cf in extra.rglob('*.class'):
            dest = out / cf.relative_to(extra)
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(cf, dest)
    return out, autostub


def main():
    q = argparse.ArgumentParser()
    q.add_argument('--java', required=True, type=pathlib.Path)
    q.add_argument('--jar', required=True, type=pathlib.Path)
    q.add_argument('--gson-source', required=True, type=pathlib.Path)
    q.add_argument('--base-jar', type=pathlib.Path)
    a = q.parse_args()
    java = a.java.resolve()
    jar = a.jar.resolve()
    gson = a.gson_source.resolve()

    with tempfile.TemporaryDirectory(prefix='uiivy254-') as d:
        tmp = pathlib.Path(d)
        out, autostub = make_fixture(java, jar, gson, tmp)

        if a.base_jar is not None:
            base_jar = a.base_jar.resolve()
            game253 = tmp / 'game253'
            game253.mkdir()
            cp_base = ':'.join(map(str, [out, base_jar, autostub]))
            r = subprocess.run(
                [str(java), '-Xverify:all', '-cp', cp_base, 'testing.UiIvy254Scenario', 'reproduce-253', str(game253)],
                capture_output=True, text=True,
            )
            assert r.returncode == 0 and 'CONFIRMED build 253 bugs:' in r.stdout, (r.stdout, r.stderr)
            print(r.stdout.strip(), flush=True)

        game254 = tmp / 'game254'
        game254.mkdir()
        cp = ':'.join(map(str, [out, jar, autostub]))
        r = subprocess.run(
            [str(java), '-Xverify:all', '-cp', cp, 'testing.UiIvy254Scenario', 'verify-254',
             str(game254), str(jar), str(out), str(autostub)],
            capture_output=True, text=True,
        )
        assert r.returncode == 0 and 'PASS build 254 UI & Ivy:' in r.stdout, (r.stdout, r.stderr)
        print(r.stdout.strip(), flush=True)


if __name__ == '__main__':
    main()
