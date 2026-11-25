#!/usr/bin/env python3
"""Comprehensive verification gate for Build 439 (Furniture 3D loot scatter via Diegesis/Hands-On)."""

import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import build436_check
import build438_check
import disasm

ROOT = pathlib.Path(__file__).resolve().parent.parent

LAYERS = tuple(dict.fromkeys(build438_check.LAYERS + (ROOT / "build303-src" / "stubs439-src",)))

TEST_SHIMS = dict(build438_check.TEST_SHIMS)
TEST_SHIMS["net/neoforged/neoforge/network/PacketDistributor.java"] = """\
package net.neoforged.neoforge.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class PacketDistributor {
    public static final List<CustomPacketPayload> SENT_TO_SERVER = new ArrayList<>();

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload,
            CustomPacketPayload... payloads) {}

    public static void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload,
            CustomPacketPayload... payloads) {}

    public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... payloads) {
        if (payload != null) SENT_TO_SERVER.add(payload);
        if (payloads != null) {
            for (CustomPacketPayload extra : payloads) {
                if (extra != null) SENT_TO_SERVER.add(extra);
            }
        }
    }

    private PacketDistributor() {}
}
"""

JOML_SHIMS = {
    "org/joml/Vector2fc.java": """\
package org.joml;
public interface Vector2fc {
    float x();
    float y();
}
""",
    "org/joml/Vector2f.java": """\
package org.joml;
public class Vector2f implements Vector2fc {
    public float x;
    public float y;
    public Vector2f() {}
    public Vector2f(float x, float y) { this.x = x; this.y = y; }
    @Override public float x() { return x; }
    @Override public float y() { return y; }
}
""",
    "org/joml/Quaternionfc.java": """\
package org.joml;
public interface Quaternionfc {
    float x();
    float y();
    float z();
    float w();
}
""",
    "org/joml/Quaternionf.java": """\
package org.joml;
public class Quaternionf implements Quaternionfc {
    public float x;
    public float y;
    public float z;
    public float w;
    public Quaternionf() { this(0F, 0F, 0F, 1F); }
    public Quaternionf(float x, float y, float z, float w) {
        this.x = x; this.y = y; this.z = z; this.w = w;
    }
    @Override public float x() { return x; }
    @Override public float y() { return y; }
    @Override public float z() { return z; }
    @Override public float w() { return w; }
    public Quaternionf rotationTo(Vector3fc fromDir, Vector3fc toDir) { return this; }
}
""",
}

DIEGESIS_COMMON_SHIMS = {
    "com/pockyl/handson_furniture/station/RoomyContainer.java": """\
package com.pockyl.handson_furniture.station;
public interface RoomyContainer {
    int handson$ownSize();
}
""",
    "com/pockyl/diegesis/api/OpenSide.java": """\
package com.pockyl.diegesis.api;
public enum OpenSide { RIGHT, LEFT, FRONT, BACK }
""",
    "com/pockyl/diegesis/api/state/SlotRef.java": """\
package com.pockyl.diegesis.api.state;
import java.util.Objects;
public final class SlotRef {
    private final String group;
    private final int index;
    private SlotRef(String group, int index) { this.group = group; this.index = index; }
    public static SlotRef of(String group, int index) { return new SlotRef(group, index); }
    public String group() { return group; }
    public int index() { return index; }
    @Override public boolean equals(Object o) {
        return o instanceof SlotRef r && index == r.index && Objects.equals(group, r.group);
    }
    @Override public int hashCode() { return Objects.hash(group, index); }
}
""",
    "com/pockyl/diegesis/api/state/ItemPose.java": """\
package com.pockyl.diegesis.api.state;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
public final class ItemPose {
    private final float x, y, z, lift, scale;
    private final Quaternionfc rotation;
    private final int on;
    public ItemPose(float x, float y, float z, float lift, Quaternionfc rotation, float scale, int on) {
        this.x = x; this.y = y; this.z = z; this.lift = lift;
        this.rotation = rotation != null ? rotation : new Quaternionf(0F, 0F, 0F, 1F);
        this.scale = scale;
        this.on = on;
    }
    public float x() { return x; }
    public float y() { return y; }
    public float z() { return z; }
    public float lift() { return lift; }
    public float top() { return y + lift * 2.0F; }
    public Quaternionfc rotation() { return rotation; }
    public float scale() { return scale; }
    public int on() { return on; }
    public ItemPose sanitized() {
        float sx = Math.max(-256F, Math.min(256F, Float.isFinite(x) ? x : 0F));
        float sy = Math.max(-256F, Math.min(256F, Float.isFinite(y) ? y : 0F));
        float sz = Math.max(-256F, Math.min(256F, Float.isFinite(z) ? z : 0F));
        float sl = Math.max(0F, Math.min(256F, Float.isFinite(lift) ? lift : 0F));
        float ss = Math.max(0.05F, Math.min(16F, Float.isFinite(scale) ? scale : 1F));
        return new ItemPose(sx, sy, sz, sl, rotation, ss, on);
    }
}
""",
    "com/pockyl/diegesis/api/state/StationSchema.java": """\
package com.pockyl.diegesis.api.state;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
public final class StationSchema {
    public record SlotGroup(String name, int size) {}
    private final Map<String, SlotGroup> slotGroups = new LinkedHashMap<>();
    public StationSchema(int itemsSize) {
        slotGroups.put("items", new SlotGroup("items", itemsSize));
        slotGroups.put("extra", new SlotGroup("extra", 4));
    }
    public Map<String, SlotGroup> slotGroups() { return Collections.unmodifiableMap(slotGroups); }
}
""",
    "com/pockyl/diegesis/api/state/StationState.java": """\
package com.pockyl.diegesis.api.state;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
public final class StationState {
    public record Changes(boolean data) {}
    private final StationSchema schema;
    private final Map<SlotRef, ItemPose> poses = new HashMap<>();
    private final Map<SlotRef, ItemStack> items = new HashMap<>();
    private CompoundTag data = new CompoundTag();
    public StationState() { this(new StationSchema(27)); }
    public StationState(StationSchema schema) { this.schema = schema; }
    public StationSchema schema() { return schema; }
    public CompoundTag data() { return data.copy(); }
    public void setData(CompoundTag next) { this.data = next == null ? new CompoundTag() : next.copy(); }
    public ItemStack getItem(SlotRef slot) { return items.getOrDefault(slot, ItemStack.EMPTY); }
    public void setItem(SlotRef slot, ItemStack stack) { items.put(slot, stack == null ? ItemStack.EMPTY : stack); }
    public ItemPose getPose(SlotRef slot) { return poses.get(slot); }
    public void setPose(SlotRef slot, ItemPose pose) {
        if (pose == null) poses.remove(slot);
        else poses.put(slot, pose);
    }
    public void clearPose(SlotRef slot) { poses.remove(slot); }
    public void pollStorages() {}
    public StationState copy() {
        StationState c = new StationState(schema);
        c.poses.putAll(this.poses);
        c.items.putAll(this.items);
        c.data = this.data.copy();
        return c;
    }
}
""",
    "com/pockyl/diegesis/api/state/StationHost.java": """\
package com.pockyl.diegesis.api.state;
public interface StationHost {
    StationState stationState();
}
""",
    "com/pockyl/diegesis/api/scene/PlacingOptions.java": """\
package com.pockyl.diegesis.api.scene;
import java.util.List;
public final class PlacingOptions {
    private final float itemSize;
    private final float[] scaleRange;
    private final List<PlaceSurface> surfaces;
    public PlacingOptions(float itemSize, float[] scaleRange, List<PlaceSurface> surfaces) {
        this.itemSize = itemSize; this.scaleRange = scaleRange; this.surfaces = surfaces;
    }
    public float itemSize() { return itemSize; }
    public float[] scaleRange() { return scaleRange; }
    public List<PlaceSurface> surfaces() { return surfaces; }
}
""",
    "com/pockyl/diegesis/impl/client/scene/PlacedShape.java": """\
package com.pockyl.diegesis.impl.client.scene;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
public final class PlacedShape {
    private final boolean flat;
    private final float hx, hy, hz;
    public PlacedShape(boolean flat, float hx, float hy, float hz) {
        this.flat = flat; this.hx = hx; this.hy = hy; this.hz = hz;
    }
    public boolean isFlat() { return flat; }
    public Vector3f extents(Quaternionfc q, float scale) {
        float x = q.x(), y = q.y(), z = q.z(), w = q.w();
        float xx = x * x, yy = y * y, zz = z * z;
        float xy = x * y, xz = x * z, yz = y * z;
        float wx = w * x, wy = w * y, wz = w * z;
        float m00 = 1F - 2F * (yy + zz), m01 = 2F * (xy - wz), m02 = 2F * (xz + wy);
        float m10 = 2F * (xy + wz), m11 = 1F - 2F * (xx + zz), m12 = 2F * (yz - wx);
        float m20 = 2F * (xz - wy), m21 = 2F * (yz + wx), m22 = 1F - 2F * (xx + yy);
        float ex = (Math.abs(m00) * hx + Math.abs(m01) * hy + Math.abs(m02) * hz) * scale;
        float ey = (Math.abs(m10) * hx + Math.abs(m11) * hy + Math.abs(m12) * hz) * scale;
        float ez = (Math.abs(m20) * hx + Math.abs(m21) * hy + Math.abs(m22) * hz) * scale;
        return new Vector3f(ex, ey, ez);
    }
}
""",
    "com/pockyl/diegesis/impl/client/scene/PlacedItem.java": """\
package com.pockyl.diegesis.impl.client.scene;
import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import net.minecraft.world.item.ItemStack;
public final class PlacedItem {
    private final SlotRef slot;
    private final ItemStack stack;
    private final PlacedShape shape;
    private ItemPose stored;
    public ItemPose guessed;
    public PlacedItem(SlotRef slot, ItemStack stack, PlacedShape shape) {
        this.slot = slot; this.stack = stack; this.shape = shape;
    }
    public SlotRef slot() { return slot; }
    public ItemStack stack() { return stack; }
    public PlacedShape shape() { return shape; }
    public void setStored(ItemPose pose) { this.stored = pose; }
    public ItemPose pose() { return stored != null ? stored : guessed; }
}
""",
    "com/pockyl/diegesis/impl/client/scene/NodeInstance.java": """\
package com.pockyl.diegesis.impl.client.scene;
public final class NodeInstance {
    private final PlacedItem placed;
    public NodeInstance(PlacedItem placed) { this.placed = placed; }
    public PlacedItem placed() { return placed; }
}
""",
    "com/pockyl/diegesis/impl/client/scene/SceneInstance.java": """\
package com.pockyl.diegesis.impl.client.scene;
import com.pockyl.diegesis.api.scene.PlaceSurface;
import com.pockyl.diegesis.api.scene.PlacingOptions;
import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.impl.client.ClientStation;
import dev.settledlands.chest.DiegesisScatterClient439;
import java.util.ArrayList;
import java.util.List;
import org.joml.Quaternionf;
public final class SceneInstance {
    public final ClientStation station;
    public final List<NodeInstance> placed = new ArrayList<>();
    public final PlacingOptions placingOptions;
    private int stockCursor = 0;
    public SceneInstance(ClientStation station, PlacingOptions opts) {
        this.station = station; this.placingOptions = opts;
    }
    public void update(StationState state, float dt) {
        for (NodeInstance n : placed) {
            PlacedItem item = n.placed();
            ItemPose existing = state.getPose(item.slot());
            if (existing != null) {
                item.setStored(existing);
                item.guessed = null;
            } else if (item.guessed == null) {
                item.guessed = freeSpot(item, item.shape());
            }
        }
    }
    public ItemPose freeSpot(PlacedItem item, PlacedShape shape) {
        Object custom = DiegesisScatterClient439.scatterFreeSpot(this, item, shape);
        if (custom instanceof ItemPose ip) return ip;
        PlaceSurface s = placingOptions.surfaces().get(0);
        float x = Math.min(s.from().x(), s.to().x()) + 1.0F + (stockCursor % 4) * 1.5F;
        float z = Math.min(s.from().y(), s.to().y()) + 1.0F + (stockCursor / 4) * 1.5F;
        stockCursor++;
        return new ItemPose(x, s.height(), z, 0.5F, new Quaternionf(0F, 0F, 0F, 1F), 1.0F, -1);
    }
}
""",
    "com/pockyl/diegesis/api/client/StationView.java": """\
package com.pockyl.diegesis.api.client;
import com.pockyl.diegesis.api.state.StationState;
import net.minecraft.core.BlockPos;
public interface StationView {
    boolean isFocused();
    BlockPos pos();
    StationState state();
}
""",
    "com/pockyl/diegesis/impl/client/ClientStation.java": """\
package com.pockyl.diegesis.impl.client;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.impl.client.scene.SceneInstance;
import net.minecraft.core.BlockPos;
public final class ClientStation implements StationView {
    public SceneInstance scene;
    private final BlockPos pos;
    private final StationState state;
    public boolean focused;
    public ClientStation(BlockPos pos, StationState state) { this.pos = pos; this.state = state; }
    @Override public StationState state() { return state; }
    @Override public BlockPos pos() { return pos; }
    @Override public boolean isFocused() { return focused; }
}
""",
}

DIEGESIS_MODERN_SHIMS = dict(DIEGESIS_COMMON_SHIMS)
DIEGESIS_MODERN_SHIMS.update({
    "com/pockyl/diegesis/api/scene/PlaceSurface.java": """\
package com.pockyl.diegesis.api.scene;
import com.pockyl.diegesis.api.OpenSide;
import java.util.Set;
import org.joml.Vector2fc;
public final class PlaceSurface {
    private final Vector2fc from, to;
    private final float height, clearance;
    private final Set<OpenSide> open;
    private final boolean flatLies;
    public PlaceSurface(Vector2fc from, Vector2fc to, float height, float clearance, Set<OpenSide> open) {
        this(from, to, height, clearance, open, false);
    }
    public PlaceSurface(Vector2fc from, Vector2fc to, float height, float clearance,
            Set<OpenSide> open, boolean flatLies) {
        this.from = from; this.to = to; this.height = height; this.clearance = clearance;
        this.open = open; this.flatLies = flatLies;
    }
    public Vector2fc from() { return from; }
    public Vector2fc to() { return to; }
    public float height() { return height; }
    public float clearance() { return clearance; }
    public Set<OpenSide> open() { return open; }
    public boolean flatLies() { return flatLies; }
}
""",
    "com/pockyl/diegesis/impl/client/scene/Placing.java": """\
package com.pockyl.diegesis.impl.client.scene;
import com.pockyl.diegesis.api.scene.PlacingOptions;
public final class Placing {
    public interface Shape {}
    public static float leastHalf(PlacingOptions opts, Shape shape) {
        if (shape instanceof PlacedShape ps) {
            return ps.isFlat() ? 0.15F : 0.0F;
        }
        return 0.0F;
    }
}
""",
    "com/pockyl/diegesis/api/scene/Placing.java": """\
package com.pockyl.diegesis.api.scene;
import com.pockyl.diegesis.impl.client.scene.PlacedShape;
public final class Placing {
    public static float leastHalf(PlacingOptions opts, PlacedShape shape) {
        return shape != null && shape.isFlat() ? 0.15F : 0.0F;
    }
}
""",
    "com/pockyl/diegesis/api/client/StationBehavior.java": """\
package com.pockyl.diegesis.api.client;
import com.pockyl.diegesis.api.state.StationState;
public interface StationBehavior {
    interface Factory {
        StationBehavior create();
    }
    default void onSceneReady(StationView view) {}
    default void onOpen(StationView view) {}
    default void onStateChanged(StationView view, StationState.Changes changes) {}
    default void frame(StationView view, float deltaSeconds) {}
}
""",
    "com/pockyl/diegesis/api/client/DiegesisClient.java": """\
package com.pockyl.diegesis.api.client;
public final class DiegesisClient {
    public static StationBehavior.Factory LAST_FACTORY;
    public static void addBehavior(StationBehavior.Factory factory) {
        LAST_FACTORY = factory;
    }
}
""",
})

# Adjust Placing$Shape link inside Modern PlacedShape so Placing.leastHalf(options, Shape) matches
DIEGESIS_MODERN_SHIMS["com/pockyl/diegesis/impl/client/scene/PlacedShape.java"] = (
    DIEGESIS_MODERN_SHIMS["com/pockyl/diegesis/impl/client/scene/PlacedShape.java"]
    .replace("public final class PlacedShape {",
             "public final class PlacedShape implements com.pockyl.diegesis.impl.client.scene.Placing.Shape {")
)

DIEGESIS_LEGACY_SHIMS = dict(DIEGESIS_COMMON_SHIMS)
DIEGESIS_LEGACY_SHIMS.update({
    # No flatLies(), no Placing.leastHalf(), no DiegesisClient.addBehavior() — beta.12..15 surface!
    "com/pockyl/diegesis/api/scene/PlaceSurface.java": """\
package com.pockyl.diegesis.api.scene;
import com.pockyl.diegesis.api.OpenSide;
import java.util.Set;
import org.joml.Vector2fc;
public final class PlaceSurface {
    private final Vector2fc from, to;
    private final float height, clearance;
    private final Set<OpenSide> open;
    public PlaceSurface(Vector2fc from, Vector2fc to, float height, float clearance, Set<OpenSide> open) {
        this.from = from; this.to = to; this.height = height; this.clearance = clearance;
        this.open = open;
    }
    public Vector2fc from() { return from; }
    public Vector2fc to() { return to; }
    public float height() { return height; }
    public float clearance() { return clearance; }
    public Set<OpenSide> open() { return open; }
}
""",
})

NO_DIEGESIS_PROBE_SRC = """\
import dev.settledlands.chest.ChestDiegesisCompat436;
import dev.settledlands.chest.ChestLootNet436;
import dev.settledlands.chest.ChestOpenWatch401;
import dev.settledlands.chest.DiegesisScatterClient439;
import java.util.Collections;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class Build439NoDiegesisProbe {
    public static void main(String[] args) {
        DiegesisScatterClient439.registerBehavior();
        ChestOpenWatch401.onClientTick((ClientTickEvent.Post) null);
        if (ChestDiegesisCompat436.markLootScatter(new Object(), Collections.singletonList(0), 99L)) {
            throw new AssertionError("expected false when Diegesis absent");
        }
        if (DiegesisScatterClient439.scatterFreeSpot(new Object(), new Object(), new Object()) != null) {
            throw new AssertionError("expected null when Diegesis absent");
        }
        ChestLootNet436.sendScatterPoses(new BlockPos(0, 64, 0), 99L, "test");
        System.out.println("Build439NoDiegesisProbe: zero-Diegesis safety PASS");
    }
}
"""


def find_java():
    return build436_check.find_java()


def fail(msg):
    print("build439_check: FAIL - " + msg)
    return 1


def check_structural(jar: pathlib.Path, base_jar: pathlib.Path | None = None):
    problems = list(build438_check.check_structural(jar, base_jar))
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        expected_classes = (
            "dev/settledlands/chest/ChestDiegesisCompat436.class",
            "dev/settledlands/chest/ChestDiegesisCompat436$PoseEntry.class",
            "dev/settledlands/chest/LootPoolManager436.class",
            "dev/settledlands/chest/ChestLootNet436.class",
            "dev/settledlands/chest/ChestLootNet436$ScatterPoses.class",
            "dev/settledlands/chest/ChestOpenWatch401.class",
            "dev/settledlands/chest/DiegesisScatterClient439.class",
            "dev/settledlands/chest/ChestScatterClientInit439.class",
            "dev/settledlands/mixin/compat/DiegesisScatterMixin439.class",
        )
        for cls in expected_classes:
            if cls not in names:
                problems.append("missing Build 439 class: " + cls)

        compat_cfg = json.loads(zf.read("settledlands.compat.mixins.json").decode("utf-8"))
        if compat_cfg.get("required", True):
            problems.append("settledlands.compat.mixins.json must set required=false")
        if "ApothDedupMixin" not in compat_cfg.get("mixins", []):
            problems.append("settledlands.compat.mixins.json lost ApothDedupMixin")
        if "DiegesisScatterMixin439" not in compat_cfg.get("client", []):
            problems.append("settledlands.compat.mixins.json missing client DiegesisScatterMixin439")

        # Pure reflection contract: no class references to com/pockyl/* outside DiegesisScatterMixin439
        for cls_name in (
            "dev/settledlands/chest/ChestDiegesisCompat436.class",
            "dev/settledlands/chest/LootPoolManager436.class",
            "dev/settledlands/chest/ChestLootNet436.class",
            "dev/settledlands/chest/ChestOpenWatch401.class",
            "dev/settledlands/chest/DiegesisScatterClient439.class",
            "dev/settledlands/chest/ChestScatterClientInit439.class",
        ):
            parsed = disasm.parse_class(zf.read(cls_name))
            cp = parsed["cp"]
            for idx in range(1, len(cp)):
                entry = cp[idx]
                if entry is not None and entry[0] == "Class":
                    target = cp[entry[1]][1]
                    if "com/pockyl/" in target:
                        problems.append(f"{cls_name} has hard Class ref to {target}")

        # Verify key hook wiring
        loot_mgr = zf.read("dev/settledlands/chest/LootPoolManager436.class")
        if "markLootScatter" not in build438_check.method_calls(loot_mgr, "populateContainer"):
            problems.append("LootPoolManager436#populateContainer does not call markLootScatter")
        watch = zf.read("dev/settledlands/chest/ChestOpenWatch401.class")
        if "tickWatch" not in build438_check.method_calls(watch, "onClientTick"):
            problems.append("ChestOpenWatch401#onClientTick does not call DiegesisScatterClient439#tickWatch")
        compat = zf.read("dev/settledlands/chest/ChestDiegesisCompat436.class")
        if b"SettledLandsScatterSeed" not in compat:
            problems.append("ChestDiegesisCompat436 missing SettledLandsScatterSeed tag key")
        net = zf.read("dev/settledlands/chest/ChestLootNet436.class")
        if "commitScatteredPoses" not in build438_check.method_calls(net, "onScatterPoses"):
            problems.append("ChestLootNet436#onScatterPoses does not call commitScatteredPoses")

    return problems


def _compile_map(java, ecj, src_map, base_cp, out_dir):
    src_dir = out_dir.parent / (out_dir.name + "_src")
    src_dir.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)
    files = []
    for rel, body in src_map.items():
        dst = src_dir / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_text(body, encoding="utf-8")
        files.append(str(dst))
    r = subprocess.run(
        [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
         "-cp", base_cp, "-d", str(out_dir)] + files,
        capture_output=True, text=True)
    if r.returncode != 0:
        raise AssertionError(f"compile into {out_dir.name} failed:\n{r.stdout}\n{r.stderr}")


def compile_and_run_all_probes(jar: pathlib.Path):
    jar = jar.resolve()
    java = find_java()
    ecj = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"

    with tempfile.TemporaryDirectory() as td_raw:
        td = pathlib.Path(td_raw)
        merged = td / "merged"
        stubs_out = td / "stubs_out"
        shims_out = td / "shims_out"
        joml_out = td / "joml_out"
        compat436_out = td / "compat436_out"
        modern_diegesis_out = td / "modern_diegesis_out"
        legacy_diegesis_out = td / "legacy_diegesis_out"
        probe_out = td / "probe_out"
        legacy_probe_out = td / "legacy_probe_out"
        nodiesis_out = td / "nodiesis_out"
        work_dir = td / "workdir"
        for d in (merged, stubs_out, probe_out, legacy_probe_out, nodiesis_out, work_dir):
            d.mkdir(parents=True, exist_ok=True)

        for layer in LAYERS:
            for src in layer.rglob("*.java"):
                dst = merged / src.relative_to(layer)
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)
        stub_files = sorted(str(p) for p in merged.rglob("*.java"))
        r1 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", str(jar), "-d", str(stubs_out)] + stub_files,
            capture_output=True, text=True)
        if r1.returncode != 0:
            raise AssertionError("stubs compile failed:\n" + r1.stdout + r1.stderr)

        _compile_map(java, ecj, TEST_SHIMS, f"{stubs_out}:{jar}", shims_out)
        _compile_map(java, ecj, JOML_SHIMS, f"{shims_out}:{jar}:{stubs_out}", joml_out)

        compat436_only = {k: v for k, v in build436_check.COMPAT_SOURCES.items()
                          if k != "Build436CompatProbe.java"}
        _compile_map(java, ecj, compat436_only, f"{shims_out}:{jar}:{stubs_out}", compat436_out)
        _compile_map(java, ecj, DIEGESIS_MODERN_SHIMS,
                     f"{joml_out}:{shims_out}:{jar}:{stubs_out}", modern_diegesis_out)
        _compile_map(java, ecj, DIEGESIS_LEGACY_SHIMS,
                     f"{joml_out}:{shims_out}:{jar}:{stubs_out}", legacy_diegesis_out)

        # 1. Regression probes 436..438
        reg_probes = [
            ("Build436Probe", ROOT / "tools" / "harness" / "Build436Probe.java"),
            ("Build437Probe", ROOT / "tools" / "harness" / "Build437Probe.java"),
            ("Build438Probe", ROOT / "tools" / "harness" / "Build438Probe.java"),
        ]
        r_reg = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{shims_out}:{compat436_out}:{jar}:{stubs_out}", "-d", str(probe_out)]
            + [str(p) for _, p in reg_probes],
            capture_output=True, text=True)
        if r_reg.returncode != 0:
            raise AssertionError("Regression probes compile failed:\n" + r_reg.stdout + r_reg.stderr)

        for main_cls, _ in reg_probes:
            run = subprocess.run(
                [java, "-cp", f"{probe_out}:{shims_out}:{compat436_out}:{jar}:{stubs_out}", main_cls],
                cwd=str(work_dir), capture_output=True, text=True)
            if run.returncode != 0:
                raise AssertionError(f"{main_cls} failed (rc={run.returncode}):\n{run.stdout}\n{run.stderr}")
            sys.stdout.write(run.stdout)

        # 2. Build439Probe against full Diegesis 1.21.1 beta.18+ API + JOML
        r_439 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{modern_diegesis_out}:{joml_out}:{shims_out}:{jar}:{stubs_out}",
             "-d", str(probe_out), str(ROOT / "tools" / "harness" / "Build439Probe.java")],
            capture_output=True, text=True)
        if r_439.returncode != 0:
            raise AssertionError("Build439Probe compile failed:\n" + r_439.stdout + r_439.stderr)
        run_439 = subprocess.run(
            [java, "-cp", f"{probe_out}:{modern_diegesis_out}:{joml_out}:{shims_out}:{jar}:{stubs_out}",
             "Build439Probe"],
            cwd=str(work_dir), capture_output=True, text=True)
        if run_439.returncode != 0:
            raise AssertionError(f"Build439Probe failed (rc={run_439.returncode}):\n{run_439.stdout}\n{run_439.stderr}")
        sys.stdout.write(run_439.stdout)

        # 3. Build439LegacyProbe against trimmed Diegesis beta.12..15 API (no flatLies/leastHalf/addBehavior)
        r_leg = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{legacy_diegesis_out}:{joml_out}:{shims_out}:{jar}:{stubs_out}",
             "-d", str(legacy_probe_out), str(ROOT / "tools" / "harness" / "Build439LegacyProbe.java")],
            capture_output=True, text=True)
        if r_leg.returncode != 0:
            raise AssertionError("Build439LegacyProbe compile failed:\n" + r_leg.stdout + r_leg.stderr)
        run_leg = subprocess.run(
            [java, "-cp", f"{legacy_probe_out}:{legacy_diegesis_out}:{joml_out}:{shims_out}:{jar}:{stubs_out}",
             "Build439LegacyProbe"],
            cwd=str(work_dir), capture_output=True, text=True)
        if run_leg.returncode != 0:
            raise AssertionError(f"Build439LegacyProbe failed (rc={run_leg.returncode}):\n{run_leg.stdout}\n{run_leg.stderr}")
        sys.stdout.write(run_leg.stdout)

        # 4. Zero-Diegesis / zero-HandsOn runtime check (must not throw NoClassDefFoundError)
        _compile_map(java, ecj, {"Build439NoDiegesisProbe.java": NO_DIEGESIS_PROBE_SRC},
                     f"{shims_out}:{jar}:{stubs_out}", nodiesis_out)
        run_nd = subprocess.run(
            [java, "-cp", f"{nodiesis_out}:{shims_out}:{jar}:{stubs_out}", "Build439NoDiegesisProbe"],
            cwd=str(work_dir), capture_output=True, text=True)
        if run_nd.returncode != 0:
            raise AssertionError(f"Build439NoDiegesisProbe failed (rc={run_nd.returncode}):\n{run_nd.stdout}\n{run_nd.stderr}")
        sys.stdout.write(run_nd.stdout)


def main(argv):
    if len(argv) < 1 or len(argv) > 3:
        print("usage: build439_check.py <jar> [--base <base_jar>]")
        return 2
    jar = pathlib.Path(argv[0])
    base = None
    if len(argv) == 3 and argv[1] == "--base":
        base = pathlib.Path(argv[2])
    elif (ROOT / "settledlands-0.8.0-build438-shelf-highlight-axiom-anchor-fix.jar").is_file():
        base = ROOT / "settledlands-0.8.0-build438-shelf-highlight-axiom-anchor-fix.jar"

    if not jar.is_file():
        return fail("no such jar: %s" % jar)

    problems = check_structural(jar, base)
    if problems:
        for p in problems:
            print("  - " + p)
        return fail("structural check failed")

    try:
        compile_and_run_all_probes(jar)
    except AssertionError as ex:
        return fail(str(ex))

    print("build439_check: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
