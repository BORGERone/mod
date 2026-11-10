#!/usr/bin/env python3
"""Gate (Build 417): «Незаконно» toggle on Door and Chest lockpicking cards + NPC witness checks.

Verifies on the target jar:
  1) `NpcIllegalHack417` and `NpcIllegalHack417$Session` exist in the jar, and
     `GuideNpcStealth416Mixin` invokes `NpcIllegalHack417#onNpcServerTick` on `customServerAiStep`.
  2) Both `DoorGraph382` and `ChestLock397` persist the «Незаконно» flag in `Node#varLocal`
     (which `NpcDialogueGraph` unconditionally serialises as `"VarLocal"`), and both inspector
     mixins (`NpcDoorRows382Mixin` and `NpcChestRows396Mixin`) expose non-colliding toggle rows
     (`settledlands$doorIllegal` vs `settledlands$chestIllegal`) committing via `propertyChange`.
  3) `DoorEvents368`, `DoorSync375`, `DoorHack377`, `HackPanel377`, `ChestGraph396Events` and
     `ChestNet397` wire start-blocking (`beginIllegalHack`), client defer/open/abort (`isIllegal`,
     `sendOpenDoorHack`, `sendAbortHack`, `abortFromServer`), ESC cancel (`!cancel`), and solve
     re-verification (`checkBeforeComplete`).
  4) Both `ru_ru.json` and `en_us.json` contain `gui.settledlands.hack.{illegal,illegal_on,illegal_off}`
     and `message.settledlands.hack.watched` (`"За вами наблюдают"` in Russian).
  5) Live Java 21 JVM execution of `tools/harness/IllegalHack417Probe.java` against the real classes
     inside the target jar passes all NBT, Hackers 'n Slashers stealth witness, wall/blind-entity
     filtering, mid-lockpick abort, ESC session cleanup, solve guard, and no-HnS fallback scenarios
     (including a second run with Hackers 'n Slashers classes completely absent from the classpath).
"""

import json
import pathlib
import struct
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA = pathlib.Path("/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java")
if not JAVA.is_file():
    import jdk4py
    JAVA = pathlib.Path(jdk4py.JAVA_HOME) / "bin" / "java"
ECJ = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
PROBE = ROOT / "tools" / "harness" / "IllegalHack417Probe.java"
ABORT_PROBE = ROOT / "tools" / "harness" / "AbortPanel418Probe.java"

REQ_CLASSES = (
    "dev/settledlands/npc/NpcIllegalHack417.class",
    "dev/settledlands/npc/NpcIllegalHack417$Session.class",
    "dev/settledlands/npc/NpcHnsStealth416.class",
    "dev/settledlands/mixin/GuideNpcStealth416Mixin.class",
    "dev/settledlands/door/DoorGraph382.class",
    "dev/settledlands/door/DoorSync375.class",
    "dev/settledlands/door/DoorEvents368.class",
    "dev/settledlands/door/DoorHack377.class",
    "dev/settledlands/door/HackPanel377.class",
    "dev/settledlands/chest/ChestLock397.class",
    "dev/settledlands/chest/ChestGraph396Events.class",
    "dev/settledlands/chest/ChestNet397.class",
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class",
    "dev/settledlands/mixin/NpcChestRows396Mixin.class",
)

HARNESS_STUBS = {
    "org/slf4j/Logger.java": """\
package org.slf4j;
public interface Logger {
    default void info(String s) {}
    default void warn(String s) {}
    default void warn(String s, Throwable t) {}
    default void error(String s, Throwable t) {}
}
""",
    "org/slf4j/LoggerFactory.java": """\
package org.slf4j;
public final class LoggerFactory {
    private static final Logger NOP = new Logger() {};
    public static Logger getLogger(String name) { return NOP; }
    public static Logger getLogger(Class<?> cls) { return NOP; }
}
""",
    "net/minecraft/core/BlockPos.java": """\
package net.minecraft.core;
public class BlockPos {
    private final int x, y, z;
    public BlockPos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
}
""",
    "net/minecraft/resources/ResourceLocation.java": """\
package net.minecraft.resources;
public final class ResourceLocation {
    private final String ns, path;
    private ResourceLocation(String ns, String path) { this.ns = ns; this.path = path; }
    public static ResourceLocation fromNamespaceAndPath(String ns, String path) {
        return new ResourceLocation(ns, path);
    }
    @Override public String toString() { return ns + ":" + path; }
}
""",
    "net/minecraft/resources/ResourceKey.java": """\
package net.minecraft.resources;
public final class ResourceKey<T> {
    private final ResourceLocation loc;
    public ResourceKey(ResourceLocation loc) { this.loc = loc; }
    public ResourceLocation location() { return loc; }
}
""",
    "net/minecraft/network/chat/Component.java": """\
package net.minecraft.network.chat;
public interface Component {
    String getString();
    static MutableComponent translatable(String key) { return new MutableComponent(key); }
    static MutableComponent translatable(String key, Object... args) { return new MutableComponent(key); }
    static MutableComponent literal(String text) { return new MutableComponent(text); }
}
""",
    "net/minecraft/network/chat/MutableComponent.java": """\
package net.minecraft.network.chat;
public class MutableComponent implements Component {
    private final String key;
    public MutableComponent(String key) { this.key = key; }
    @Override public String getString() { return key; }
}
""",
    "net/minecraft/nbt/Tag.java": """\
package net.minecraft.nbt;
public interface Tag {}
""",
    "net/minecraft/nbt/ListTag.java": """\
package net.minecraft.nbt;
import java.util.ArrayList;
public class ListTag extends ArrayList<Tag> implements Tag {
    public CompoundTag getCompound(int idx) {
        Tag t = (idx >= 0 && idx < size()) ? get(idx) : null;
        return t instanceof CompoundTag ct ? ct : new CompoundTag();
    }
    public String getString(int idx) {
        Tag t = (idx >= 0 && idx < size()) ? get(idx) : null;
        return t instanceof StringTag st ? st.val : "";
    }
}
""",
    "net/minecraft/nbt/StringTag.java": """\
package net.minecraft.nbt;
public class StringTag implements Tag {
    public final String val;
    private StringTag(String val) { this.val = val == null ? "" : val; }
    public static StringTag valueOf(String val) { return new StringTag(val); }
}
""",
    "net/minecraft/nbt/CompoundTag.java": """\
package net.minecraft.nbt;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
public class CompoundTag implements Tag {
    private final Map<String, Object> map = new HashMap<>();
    public void putInt(String k, int v) { map.put(k, Integer.valueOf(v)); }
    public int getInt(String k) {
        Object v = map.get(k);
        return v instanceof Number n ? n.intValue() : 0;
    }
    public void putBoolean(String k, boolean v) { map.put(k, Boolean.valueOf(v)); }
    public boolean getBoolean(String k) {
        Object v = map.get(k);
        return v instanceof Boolean b && b.booleanValue();
    }
    public void putFloat(String k, float v) { map.put(k, Float.valueOf(v)); }
    public float getFloat(String k) {
        Object v = map.get(k);
        return v instanceof Number n ? n.floatValue() : 0.0F;
    }
    public void putDouble(String k, double v) { map.put(k, Double.valueOf(v)); }
    public double getDouble(String k) {
        Object v = map.get(k);
        return v instanceof Number n ? n.doubleValue() : 0.0D;
    }
    public void putLong(String k, long v) { map.put(k, Long.valueOf(v)); }
    public long getLong(String k) {
        Object v = map.get(k);
        return v instanceof Number n ? n.longValue() : 0L;
    }
    public void putString(String k, String v) { map.put(k, v == null ? "" : v); }
    public String getString(String k) {
        Object v = map.get(k);
        return v instanceof String s ? s : "";
    }
    public Tag put(String k, Tag v) { map.put(k, v); return v; }
    public void putIntArray(String k, int[] v) { map.put(k, v == null ? new int[0] : v.clone()); }
    public int[] getIntArray(String k) {
        Object v = map.get(k);
        return v instanceof int[] arr ? arr.clone() : new int[0];
    }
    public Tag get(String k) {
        Object v = map.get(k);
        return v instanceof Tag t ? t : null;
    }
    public CompoundTag getCompound(String k) {
        Object v = map.get(k);
        return v instanceof CompoundTag ct ? ct : new CompoundTag();
    }
    public ListTag getList(String k, int type) {
        Object v = map.get(k);
        return v instanceof ListTag lt ? lt : new ListTag();
    }
    public boolean contains(String k) { return map.containsKey(k); }
    public boolean contains(String k, int type) { return map.containsKey(k); }
    public boolean isEmpty() { return map.isEmpty(); }
    public Set<String> getAllKeys() { return map.keySet(); }
}
""",
    "net/minecraft/world/phys/AABB.java": """\
package net.minecraft.world.phys;
public class AABB {
    public final double minX, minY, minZ, maxX, maxY, maxZ;
    public AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
    }
    public boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
""",
    "net/minecraft/world/level/LevelAccessor.java": """\
package net.minecraft.world.level;
public interface LevelAccessor {}
""",
    "net/minecraft/world/level/Level.java": """\
package net.minecraft.world.level;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
public class Level implements LevelAccessor {
    public boolean isClientSide = false;
    private final ResourceKey<Level> dim =
            new ResourceKey<>(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"));
    protected List<Entity> entities = new ArrayList<>();
    public ResourceKey<Level> dimension() { return dim; }
    public MinecraftServer getServer() { return null; }
    public List<? extends Player> players() { return Collections.emptyList(); }
    public Player getNearestPlayer(Entity self, double maxDist) { return null; }
    @SuppressWarnings("unchecked")
    public <T extends Entity> List<T> getEntitiesOfClass(Class<T> cls, AABB box, Predicate<? super T> pred) {
        List<T> out = new ArrayList<>();
        for (Entity e : entities) {
            if (cls.isInstance(e) && box.contains(e.getX(), e.getY(), e.getZ())) {
                T cast = (T) e;
                if (pred == null || pred.test(cast)) out.add(cast);
            }
        }
        return out;
    }
}
""",
    "net/minecraft/server/players/PlayerList.java": """\
package net.minecraft.server.players;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
public class PlayerList {
    private final Map<UUID, ServerPlayer> map = new HashMap<>();
    public void addPlayer(ServerPlayer p) { map.put(p.getUUID(), p); }
    public ServerPlayer getPlayer(UUID id) { return map.get(id); }
    public List<ServerPlayer> getPlayers() { return new ArrayList<>(map.values()); }
}
""",
    "net/minecraft/server/MinecraftServer.java": """\
package net.minecraft.server;
import net.minecraft.server.players.PlayerList;
public class MinecraftServer {
    private final PlayerList list = new PlayerList();
    public PlayerList getPlayerList() { return list; }
}
""",
    "net/minecraft/server/level/ServerLevel.java": """\
package net.minecraft.server.level;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
public class ServerLevel extends Level {
    private final MinecraftServer server;
    public ServerLevel(MinecraftServer server) { this.server = server; }
    @Override public MinecraftServer getServer() { return server; }
    public void setNpcs(List<? extends Entity> list) {
        this.entities.clear();
        this.entities.addAll(list);
    }
}
""",
    "net/minecraft/world/entity/Entity.java": """\
package net.minecraft.world.entity;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
public class Entity {
    private final Level level;
    private final UUID uuid;
    private final double x, y, z;
    private boolean alive = true;
    private boolean invisible = false;
    private boolean shift = false;
    public Entity(Level level, UUID uuid, double x, double y, double z) {
        this.level = level; this.uuid = uuid;
        this.x = x; this.y = y; this.z = z;
    }
    public Level level() { return level; }
    public UUID getUUID() { return uuid; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public boolean isAlive() { return alive; }
    public boolean isRemoved() { return !alive; }
    public boolean isInvisible() { return invisible; }
    public boolean isShiftKeyDown() { return shift; }
    public void setShiftKeyDown(boolean v) { this.shift = v; }
    public Component getName() { return Component.literal("Tester"); }
    public double distanceToSqr(Entity other) {
        double dx = other.x - x, dy = other.y - y, dz = other.z - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
""",
    "net/minecraft/world/entity/LivingEntity.java": """\
package net.minecraft.world.entity;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.level.Level;
public class LivingEntity extends Entity {
    public float yHeadRot;
    public float yBodyRot;
    private final Set<UUID> visibleSet = new HashSet<>();
    public LivingEntity(Level level, UUID uuid, double x, double y, double z) {
        super(level, uuid, x, y, z);
    }
    public void setLineOfSight(Entity target, boolean los) {
        if (los) visibleSet.add(target.getUUID());
        else visibleSet.remove(target.getUUID());
    }
    public boolean hasLineOfSight(Entity target) {
        return target != null && visibleSet.contains(target.getUUID());
    }
}
""",
    "net/minecraft/world/entity/Mob.java": """\
package net.minecraft.world.entity;
import java.util.UUID;
import net.minecraft.world.level.Level;
public class Mob extends LivingEntity {
    private LivingEntity target;
    public Mob(Level level, UUID uuid, double x, double y, double z) {
        super(level, uuid, x, y, z);
    }
    public LivingEntity getTarget() { return target; }
    public void setTarget(LivingEntity t) { this.target = t; }
}
""",
    "net/minecraft/world/entity/PathfinderMob.java": """\
package net.minecraft.world.entity;
import java.util.UUID;
import net.minecraft.world.level.Level;
public class PathfinderMob extends Mob {
    public PathfinderMob(Level level, UUID uuid, double x, double y, double z) {
        super(level, uuid, x, y, z);
    }
}
""",
    "net/minecraft/world/entity/player/Player.java": """\
package net.minecraft.world.entity.player;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
public class Player extends LivingEntity {
    private boolean spectator = false;
    public Player(Level level, UUID uuid, double x, double y, double z) {
        super(level, uuid, x, y, z);
    }
    public boolean isSpectator() { return spectator; }
}
""",
    "net/minecraft/server/level/ServerPlayer.java": """\
package net.minecraft.server.level;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
public class ServerPlayer extends Player {
    private String lastMsg;
    private boolean lastOverlay;
    public ServerPlayer(Level level, UUID uuid, double x, double y, double z) {
        super(level, uuid, x, y, z);
    }
    public void displayClientMessage(Component msg, boolean overlay) {
        this.lastMsg = msg == null ? null : msg.getString();
        this.lastOverlay = overlay;
    }
    public void clearMessages() { this.lastMsg = null; this.lastOverlay = false; }
    public String lastMessageKey() { return lastMsg; }
    public boolean lastOverlay() { return lastOverlay; }
}
""",
    "net/minecraft/network/protocol/common/custom/CustomPacketPayload.java": """\
package net.minecraft.network.protocol.common.custom;
import net.minecraft.resources.ResourceLocation;
public interface CustomPacketPayload {
    record Type<T extends CustomPacketPayload>(ResourceLocation id) {}
    Type<? extends CustomPacketPayload> type();
}
""",
    "net/minecraft/network/codec/StreamCodec.java": """\
package net.minecraft.network.codec;
import java.util.function.Function;
public interface StreamCodec<B, V> {
    static <B, C, T1> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1, Function<T1, C> ctor) {
        return new StreamCodec<B, C>() {};
    }
    default <O> StreamCodec<B, O> map(Function<? super V, ? extends O> to, Function<? super O, ? extends V> from) {
        return new StreamCodec<B, O>() {};
    }
}
""",
    "net/minecraft/network/codec/ByteBufCodecs.java": """\
package net.minecraft.network.codec;
public final class ByteBufCodecs {
    public static final StreamCodec<Object, String> STRING_UTF8 = new StreamCodec<Object, String>() {};
}
""",
    "io/netty/buffer/ByteBuf.java": """\
package io.netty.buffer;
public abstract class ByteBuf {}
""",
    "net/neoforged/neoforge/network/PacketDistributor.java": """\
package net.neoforged.neoforge.network;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
public final class PacketDistributor {
    private static CustomPacketPayload last;
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload, CustomPacketPayload... more) {
        last = payload;
    }
    public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... more) {
        last = payload;
    }
    public static void clearSent() { last = null; }
    public static CustomPacketPayload lastPayload() { return last; }
}
""",
    "dev/settledlands/npc/GuideNpc.java": """\
package dev.settledlands.npc;
import java.util.UUID;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
public class GuideNpc extends PathfinderMob {
    public GuideNpc(Level level, double x, double y, double z) {
        super(level, UUID.randomUUID(), x, y, z);
    }
    public boolean isTalking() { return false; }
}
""",
    "dev/settledlands/npc/GuideTriggerNpc.java": """\
package dev.settledlands.npc;
import net.minecraft.world.level.Level;
public class GuideTriggerNpc extends GuideNpc {
    public GuideTriggerNpc(Level level, double x, double y, double z) { super(level, x, y, z); }
}
""",
    "dev/settledlands/npc/GuideCorpseNpc.java": """\
package dev.settledlands.npc;
import net.minecraft.world.level.Level;
public class GuideCorpseNpc extends GuideNpc {
    public GuideCorpseNpc(Level level, double x, double y, double z) { super(level, x, y, z); }
}
""",
    "dev/settledlands/npc/NpcStatsInteraction.java": """\
package dev.settledlands.npc;
import net.minecraft.world.entity.player.Player;
public final class NpcStatsInteraction {
    public static boolean isBodiless(Player p) { return false; }
}
""",
    "dev/settledlands/npc/GuideDialogue.java": """\
package dev.settledlands.npc;
import net.minecraft.server.level.ServerPlayer;
public final class GuideDialogue {
    public static boolean isInDialogue(ServerPlayer p) { return false; }
}
""",
    "com/mojang/blaze3d/platform/Window.java": """\
package com.mojang.blaze3d.platform;
public class Window {
    public long getWindow() { return 1L; }
}
""",
    "com/mojang/blaze3d/platform/InputConstants.java": """\
package com.mojang.blaze3d.platform;
import java.util.HashSet;
import java.util.Set;
public final class InputConstants {
    private static final Set<Integer> DOWN = new HashSet<>();
    public static void setDownForTests(int key, boolean down) {
        if (down) DOWN.add(Integer.valueOf(key));
        else DOWN.remove(Integer.valueOf(key));
    }
    public static void clearAllForTests() { DOWN.clear(); }
    public static boolean isKeyDown(long window, int key) {
        return window != 0L && DOWN.contains(Integer.valueOf(key));
    }
}
""",
    "net/minecraft/client/gui/Font.java": """\
package net.minecraft.client.gui;
public class Font {}
""",
    "net/minecraft/client/gui/GuiGraphics.java": """\
package net.minecraft.client.gui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
public class GuiGraphics {
    private final List<String> texts = new ArrayList<>();
    public void fill(int x1, int y1, int x2, int y2, int color) {}
    public void drawCenteredString(Font font, Component text, int x, int y, int color) {
        if (text != null) texts.add(text.getString());
    }
    public boolean containsText(String sub) {
        for (String s : texts) {
            if (s != null && s.contains(sub)) return true;
        }
        return false;
    }
}
""",
    "net/minecraft/client/gui/screens/Screen.java": """\
package net.minecraft.client.gui.screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
public class Screen {
    public int width = 800;
    public int height = 450;
    public Font font = new Font();
    protected Screen(Component title) {}
    protected void init() {}
    public boolean isPauseScreen() { return false; }
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return false;
    }
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) { return false; }
    public void removed() {}
    public void onClose() { Minecraft.getInstance().setScreen(null); }
    public void tick() {}
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}
    public void render(GuiGraphics g, int mx, int my, float pt) {}
}
""",
    "net/minecraft/world/inventory/AbstractContainerMenu.java": """\
package net.minecraft.world.inventory;
public class AbstractContainerMenu {}
""",
    "net/minecraft/world/inventory/InventoryMenu.java": """\
package net.minecraft.world.inventory;
public class InventoryMenu extends AbstractContainerMenu {}
""",
    "net/minecraft/client/player/LocalPlayer.java": """\
package net.minecraft.client.player;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
public class LocalPlayer extends Player {
    public InventoryMenu inventoryMenu = new InventoryMenu();
    public AbstractContainerMenu containerMenu = inventoryMenu;
    public LocalPlayer(Level level) {
        super(level, UUID.randomUUID(), 0.0D, 64.0D, 0.0D);
    }
}
""",
    "net/minecraft/client/Minecraft.java": """\
package net.minecraft.client;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
public class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    public Screen screen;
    public LocalPlayer player;
    public net.minecraft.client.multiplayer.ClientLevel level;
    private final Window window = new Window();
    public static Minecraft getInstance() { return INSTANCE; }
    public Window getWindow() { return window; }
    public void setScreen(Screen s) {
        if (this.screen != null && this.screen != s) {
            this.screen.removed();
        }
        this.screen = s;
    }
}
""",
    "net/minecraft/client/multiplayer/ClientLevel.java": """\
package net.minecraft.client.multiplayer;
import net.minecraft.world.level.Level;
public class ClientLevel extends Level {}
""",
}

HNS_STUBS = {
    "net/dndats/hackersandslashers/HackersAndSlashers.java": """\
package net.dndats.hackersandslashers;
public final class HackersAndSlashers {
    public static final class ServerConfig {
        public boolean enable_stealth_system = true;
    }
    public static final ServerConfig serverConfig = new ServerConfig();
}
""",
    "net/dndats/hackersandslashers/utils/helper/EntityHelper.java": """\
package net.dndats.hackersandslashers.utils.helper;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Collections;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
public final class EntityHelper {
    private static final Map<Mob, Map<UUID, Double>> SIGHT = new HashMap<>();
    public static double getSightLevel(Player p, Mob m) {
        Map<UUID, Double> map = SIGHT.get(m);
        return map == null ? 0.0D : map.getOrDefault(p.getUUID(), 0.0D);
    }
    public static void setSightLevel(Player p, Mob m, double v) {
        Map<UUID, Double> map = SIGHT.computeIfAbsent(m, k -> new HashMap<>());
        if (v <= 0.0D) map.remove(p.getUUID());
        else map.put(p.getUUID(), Double.valueOf(v));
    }
    public static Set<UUID> getTrackedPlayers(Mob m) {
        Map<UUID, Double> map = SIGHT.get(m);
        return map == null ? Collections.emptySet() : map.keySet();
    }
    public static boolean hasSuspicionEntries(Mob m) { return false; }
    public static void clearSuspicionEntries(Mob m) {}
}
""",
    "net/dndats/hackersandslashers/utils/helper/DatapackHelper.java": """\
package net.dndats.hackersandslashers.utils.helper;
import net.minecraft.world.entity.Mob;
public final class DatapackHelper {
    public static boolean hasMobTrueVision(Mob m) { return false; }
}
""",
}

JAR_EXTRACT_FOR_PROBE = (
    "dev/settledlands/npc/NpcIllegalHack417.class",
    "dev/settledlands/npc/NpcIllegalHack417$Session.class",
    "dev/settledlands/npc/NpcHnsStealth416.class",
    "dev/settledlands/npc/NpcDialogueGraph.class",
    "dev/settledlands/npc/NpcDialogueGraph$Node.class",
    "dev/settledlands/npc/NpcDialogueGraph$Option.class",
    "dev/settledlands/npc/BlockTrigger351.class",
    "dev/settledlands/npc/BlockName359.class",
    "dev/settledlands/door/DoorGraph382.class",
    "dev/settledlands/door/DoorSync375.class",
    "dev/settledlands/door/DoorHack377.class",
    "dev/settledlands/door/HackPanel377.class",
    "dev/settledlands/chest/ChestGraph396.class",
    "dev/settledlands/chest/ChestLock397.class",
)


def method_listing(raw, name):
    parsed = disasm.parse_class(raw)
    cp = parsed["cp"]
    for _acc, name_idx, _desc, attrs in parsed["methods"]:
        if cp[name_idx][1] == name:
            co = disasm.code_of(attrs, cp)
            if co is not None:
                listing, _ = disasm.disasm(co["code"], cp)
                return listing
    return ""


def fail(msg):
    print("illegal_hack_check: FAIL - %s" % msg, file=sys.stderr)
    return 1


def methods_and_refs(raw):
    parsed = disasm.parse_class(raw)
    cp = parsed["cp"]
    mnames = {cp[name_idx][1] for _acc, name_idx, _desc, _attrs in parsed["methods"]}
    strings = {str(v[1]) for v in cp if v and v[0] == 1}
    refs = set()
    for value in cp:
        if not value or value[0] not in (9, 10, 11):
            continue
        ci, nti = struct.unpack(">HH", value[1])
        try:
            name, desc = disasm.nat(cp, nti)
            owner = disasm.cls_name(cp, ci)
        except Exception:
            continue
        refs.add((owner, name, desc))
    return mnames, strings, refs


def run_jvm_probe(blobs, jar_path):
    with tempfile.TemporaryDirectory(prefix="sl_illegal417_") as tmp:
        tmp_path = pathlib.Path(tmp)
        stub_src = tmp_path / "stub_src"
        stub_out = tmp_path / "stub_out"
        hns_src = tmp_path / "hns_src"
        hns_out = tmp_path / "hns_out"
        jar_extract = tmp_path / "jar_extract"
        probe_out = tmp_path / "probe_out"
        for d in (stub_src, stub_out, hns_src, hns_out, jar_extract, probe_out):
            d.mkdir(parents=True, exist_ok=True)

        for rel, code in HARNESS_STUBS.items():
            p = stub_src / rel
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(code, encoding="utf-8")
        for rel, code in HNS_STUBS.items():
            p = hns_src / rel
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(code, encoding="utf-8")

        for entry in JAR_EXTRACT_FOR_PROBE:
            if entry in blobs:
                dest = jar_extract / entry
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(blobs[entry])

        # Compile runtime stubs and HnS API doubles.
        r1 = subprocess.run(
            [str(JAVA), "-jar", str(ECJ), "-21", "-proc:none", "-nowarn",
             "-d", str(stub_out)] + [str(p) for p in sorted(stub_src.rglob("*.java"))],
            capture_output=True, text=True)
        if r1.returncode != 0:
            raise RuntimeError("stub compile failed:\n" + r1.stderr)

        r2 = subprocess.run(
            [str(JAVA), "-jar", str(ECJ), "-21", "-proc:none", "-nowarn",
             "-cp", str(stub_out), "-d", str(hns_out)]
            + [str(p) for p in sorted(hns_src.rglob("*.java"))],
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise RuntimeError("hns compile failed:\n" + r2.stderr)

        # Compile IllegalHack417Probe and AbortPanel418Probe against extracted real classes + stubs.
        cp_compile = "%s:%s:%s" % (jar_extract, hns_out, stub_out)
        r3 = subprocess.run(
            [str(JAVA), "-jar", str(ECJ), "-21", "-proc:none", "-nowarn",
             "-cp", cp_compile, "-d", str(probe_out), str(PROBE), str(ABORT_PROBE)],
            capture_output=True, text=True)
        if r3.returncode != 0:
            raise RuntimeError("probe compile failed:\n" + r3.stderr)

        # Run probe (jar_extract + stub_out BEFORE jar_path so real mod classes + test doubles execute!).
        cp_run = "%s:%s:%s:%s:%s" % (probe_out, jar_extract, hns_out, stub_out, jar_path)
        run1 = subprocess.run(
            [str(JAVA), "-cp", cp_run, "IllegalHack417Probe"],
            capture_output=True, text=True)
        if run1.returncode != 0:
            raise RuntimeError("probe run failed:\n" + run1.stderr + "\n" + run1.stdout)

        expected = (
            "NBT_ROUNDTRIP doorDefaultLegal=true doorSavedIllegal=true chestDefaultLegal=true chestSavedIllegal=true",
            "START_BLOCKED refused=true watchedMsg=true noSession=true",
            "WALL_AND_BLIND_ALLOWED ok=true",
            "MID_HACK_ABORT stillActiveAt35=true abortedOn92=true",
            "ESC_CLEANUP noFalsePositive=true",
            "SOLVE_GUARD illegalRefused=true legalAllowed=true",
            "FALLBACK_NO_HNS behindAllowed=true turnAborted=true",
        )
        lines = [ln.strip() for ln in run1.stdout.splitlines() if ln.strip()]
        for want in expected:
            if want not in lines:
                raise RuntimeError("missing probe assertion %r in:\n%s" % (want, run1.stdout))

        # Run Build 418 abort-panel held-key guard probe against real HackPanel377 + DoorSync375 + DoorHack377.
        run_abort = subprocess.run(
            [str(JAVA), "-cp", cp_run, "AbortPanel418Probe"],
            capture_output=True, text=True)
        if run_abort.returncode != 0:
            raise RuntimeError("AbortPanel418Probe run failed:\n" + run_abort.stderr + "\n" + run_abort.stdout)
        abort_expected = (
            "ABORT_HELD_KEYS kept=true swallowedP=true swallowedEsc=true text=true stayedWithP=true closedOnRelease=true noCancel=true",
            "ABORT_GUARD preHeld=true grace=true latePSwallowed=true",
            "ABORT_ESC_HATCH escHoldEscaped=true normalEscCancel=true",
        )
        abort_lines = [ln.strip() for ln in run_abort.stdout.splitlines() if ln.strip()]
        for want in abort_expected:
            if want not in abort_lines:
                raise RuntimeError("missing AbortPanel418Probe assertion %r in:\n%s"
                                   % (want, run_abort.stdout))

        # Also verify clean operation when Hackers 'n Slashers classes are completely absent!
        no_hns_src = tmp_path / "NoHns417Probe.java"
        no_hns_src.write_text("""\
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcHnsStealth416;
import dev.settledlands.npc.NpcIllegalHack417;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class NoHns417Probe {
    public static void main(String[] args) {
        NpcHnsStealth416.resetReflectionForTests();
        NpcIllegalHack417.clearSessionsForTests();
        MinecraftServer srv = new MinecraftServer();
        ServerLevel lvl = new ServerLevel(srv);
        ServerPlayer p = new ServerPlayer(lvl, UUID.randomUUID(), 0.0D, 64.0D, 4.0D);
        srv.getPlayerList().addPlayer(p);
        GuideNpc npc = new GuideNpc(lvl, 0.0D, 64.0D, 0.0D);
        npc.yHeadRot = 0.0F; // facing +Z directly toward p
        npc.setLineOfSight(p, true);
        lvl.setNpcs(Arrays.asList(npc));
        boolean hnsOff = !NpcHnsStealth416.isHnsStealthActive();
        boolean blocked = !NpcIllegalHack417.beginIllegalHack(p, lvl, new BlockPos(0, 64, 4), true, "k");
        boolean watched = NpcIllegalHack417.MSG_WATCHED.equals(p.lastMessageKey());
        System.out.println("NO_HNS_OK=" + (hnsOff && blocked && watched));
    }
}
""", encoding="utf-8")
        r4 = subprocess.run(
            [str(JAVA), "-jar", str(ECJ), "-21", "-proc:none", "-nowarn",
             "-cp", "%s:%s" % (jar_extract, stub_out), "-d", str(probe_out), str(no_hns_src)],
            capture_output=True, text=True)
        if r4.returncode != 0:
            raise RuntimeError("NoHns417Probe compile failed:\n" + r4.stderr)
        run2 = subprocess.run(
            [str(JAVA), "-cp", "%s:%s:%s:%s" % (probe_out, jar_extract, stub_out, jar_path), "NoHns417Probe"],
            capture_output=True, text=True)
        if run2.returncode != 0 or "NO_HNS_OK=true" not in run2.stdout:
            raise RuntimeError("NoHns417Probe failed:\n" + run2.stderr + "\n" + run2.stdout)


def main(argv):
    if len(argv) != 1:
        print("usage: illegal_hack_check.py <jar>", file=sys.stderr)
        return 2
    jar_path = pathlib.Path(argv[0])
    if not jar_path.is_file():
        return fail("no such jar: %s" % jar_path)

    with zipfile.ZipFile(jar_path) as zf:
        blobs = {n: zf.read(n) for n in zf.namelist()}

    for cls in REQ_CLASSES:
        if cls not in blobs:
            return fail("missing class %s" % cls)

    # 1) Language keys in ru_ru.json and en_us.json.
    ru = json.loads(blobs["assets/settledlands/lang/ru_ru.json"].decode("utf-8"))
    en = json.loads(blobs["assets/settledlands/lang/en_us.json"].decode("utf-8"))
    if ru.get("message.settledlands.hack.watched") != "За вами наблюдают":
        return fail("ru_ru.json missing exact 'За вами наблюдают' under message.settledlands.hack.watched")
    for k in ("gui.settledlands.hack.illegal",
              "gui.settledlands.hack.illegal_on",
              "gui.settledlands.hack.illegal_off",
              "message.settledlands.hack.watched"):
        if k not in ru or k not in en:
            return fail("missing lang key %s in ru_ru.json / en_us.json" % k)

    # 2) Structural checks on DoorGraph382, ChestLock397, rows mixins, and GuideNpcStealth416Mixin.
    dm, _, drefs = methods_and_refs(blobs["dev/settledlands/door/DoorGraph382.class"])
    if not {"illegal", "setIllegal", "illegalAt"}.issubset(dm):
        return fail("DoorGraph382 missing illegal/setIllegal/illegalAt")
    if ("dev/settledlands/npc/NpcDialogueGraph$Node", "varLocal", "Z") not in drefs:
        return fail("DoorGraph382 does not store illegal in Node#varLocal")

    cm, _, crefs = methods_and_refs(blobs["dev/settledlands/chest/ChestLock397.class"])
    if not {"illegal", "setIllegal"}.issubset(cm):
        return fail("ChestLock397 missing illegal/setIllegal")
    if ("dev/settledlands/npc/NpcDialogueGraph$Node", "varLocal", "Z") not in crefs:
        return fail("ChestLock397 does not store illegal in Node#varLocal")

    drm, _, drrefs = methods_and_refs(blobs["dev/settledlands/mixin/NpcDoorRows382Mixin.class"])
    crm, _, crrefs = methods_and_refs(blobs["dev/settledlands/mixin/NpcChestRows396Mixin.class"])
    if "settledlands$doorIllegal" not in drm or "settledlands$chestIllegal" not in crm:
        return fail("inspector mixins missing settledlands$doorIllegal / settledlands$chestIllegal")
    if not any(o == "dev/settledlands/door/DoorGraph382" and n == "setIllegal" for o, n, _ in drrefs):
        return fail("NpcDoorRows382Mixin never calls DoorGraph382#setIllegal")
    if not any(o == "dev/settledlands/chest/ChestLock397" and n == "setIllegal" for o, n, _ in crrefs):
        return fail("NpcChestRows396Mixin never calls ChestLock397#setIllegal")

    _, _, gmrefs = methods_and_refs(blobs["dev/settledlands/mixin/GuideNpcStealth416Mixin.class"])
    if not any(o == "dev/settledlands/npc/NpcIllegalHack417" and n == "onNpcServerTick"
               for o, n, _ in gmrefs):
        return fail("GuideNpcStealth416Mixin does not call NpcIllegalHack417#onNpcServerTick")

    # Build 418 structural guard: HackPanel377#abortFromServer must NOT call Minecraft#setScreen(null)
    # directly while keys may be held (which leaked 'P' -> Social Interactions in singleplayer);
    # instead it must transition via markAborted() and close in render() only after allReleased().
    panel_bytes = blobs["dev/settledlands/door/HackPanel377.class"]
    abort_code = method_listing(panel_bytes, "abortFromServer")
    if not abort_code or "markAborted(" not in abort_code:
        return fail("HackPanel377#abortFromServer does not call markAborted()")
    if "Minecraft.setScreen(" in abort_code:
        return fail("HackPanel377#abortFromServer calls Minecraft#setScreen directly (Build 417 held-key bleed bug)")
    render_code = method_listing(panel_bytes, "render")
    send_at = render_code.find("DoorHack377.send(")
    if send_at < 0:
        return fail("HackPanel377#render missing DoorHack377.send")
    pre_send = render_code[:send_at]
    abort_rel = pre_send.find("HackPanel377.allReleased(")
    abort_cls = pre_send.find("HackPanel377.onClose(")
    if abort_rel < 0 or abort_cls < 0 or abort_rel > abort_cls:
        return fail("HackPanel377#render does not guard aborted close behind allReleased()")

    # 3) Live Java 21 JVM execution of IllegalHack417Probe + AbortPanel418Probe + NoHns417Probe.
    try:
        run_jvm_probe(blobs, jar_path)
    except Exception as exc:
        return fail(str(exc))

    print("illegal_hack_check: OK  %s  (Door & Chest «Незаконно» saved in Node#varLocal, inspector"
          " toggles distinct & clash-free, start-refusal + mid-hack abort + abort held-key guard"
          " + ESC cleanup + solve guard + no-HnS fallback verified in live Java 21 JVM)" % jar_path.name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
