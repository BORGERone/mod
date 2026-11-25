#!/usr/bin/env python3
"""Comprehensive verification gate for Build 437 (Anchor auto-creation, save sync, coverage & dungeon fix)."""

import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import build436_check
import disasm

ROOT = pathlib.Path(__file__).resolve().parent.parent

LAYERS = build436_check.LAYERS

TEST_SHIMS = {
    "net/minecraft/world/entity/Entity.java": """\
package net.minecraft.world.entity;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.extensions.IEntityExtension;

public class Entity implements IEntityExtension {
    public int tickCount;
    public boolean horizontalCollision;
    public float yRotO;
    public float xRotO;

    private double x;
    private double y;
    private double z;
    private float yRot;
    private float xRot;
    private final CompoundTag persistentData = new CompoundTag();

    @Override
    public CompoundTag getPersistentData() { return persistentData; }

    public boolean isAlive() { return true; }
    public boolean isRemoved() { return false; }
    public boolean isPassenger() { return false; }
    public boolean isInvisible() { return false; }
    public boolean isShiftKeyDown() { return false; }
    public Level level() { return null; }
    public BlockPos blockPosition() { return null; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYRot() { return yRot; }
    public double distanceToSqr(Entity other) { return 0; }
    public Component getName() { return null; }
    public UUID getUUID() { return null; }
    public net.minecraft.world.phys.Vec3 getDeltaMovement() { return null; }
    public double distanceToSqr(double ox, double oy, double oz) { return 0; }
    public net.minecraft.world.phys.AABB getBoundingBox() { return null; }
    public final void moveTo(double nx, double ny, double nz) { this.x = nx; this.y = ny; this.z = nz; }
    public void setDeltaMovement(double dx, double dy, double dz) {}
    public double getEyeY() { return y; }
    public void setYRot(float nyRot) { this.yRot = nyRot; }
    public void setXRot(float nxRot) { this.xRot = nxRot; }
    public void clearFire() {}
    public void load(CompoundTag tag) {}

    @Override public boolean equals(Object other) { return this == other; }
    @Override public int hashCode() { return System.identityHashCode(this); }
}
""",
    "net/minecraft/world/entity/EntityType.java": """\
package net.minecraft.world.entity;

import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class EntityType {
    private final ResourceLocation rl;
    private final Supplier<Entity> factory;

    public EntityType(String id, Supplier<Entity> factory) {
        this.rl = ResourceLocation.tryParse(id);
        this.factory = factory;
    }

    public Entity create(Level level) {
        return factory == null ? null : factory.get();
    }

    public ResourceLocation id() { return rl; }
}
""",
    "net/minecraft/world/level/block/state/BlockState.java": """\
package net.minecraft.world.level.block.state;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

public class BlockState {
    private final Block block;
    private final boolean air;

    public BlockState() { this(null, false); }

    public BlockState(Block block, boolean air) {
        this.block = block;
        this.air = air;
    }

    public Block getBlock() { return block; }

    public <T extends Comparable<T>> T getValue(Property<T> property) { return null; }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public Object setValue(Property property, Comparable value) { return this; }

    public boolean is(Block other) { return block == other; }

    public boolean isAir() { return air; }

    public boolean hasProperty(Property<?> property) { return false; }

    public net.minecraft.world.MenuProvider getMenuProvider(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos) {
        return null;
    }
}
""",
    "net/minecraft/world/item/ItemStack.java": """\
package net.minecraft.world.item;

public final class ItemStack {
    public static final ItemStack EMPTY = new ItemStack();
    private final Item item;
    private int count;

    public ItemStack(net.minecraft.world.level.ItemLike item) { this(item, 1); }

    public ItemStack(net.minecraft.world.level.ItemLike item, int count) {
        this.item = item instanceof Item i ? i : null;
        this.count = count;
    }

    public ItemStack() { this.item = null; this.count = 0; }

    public boolean isEmpty() { return item == null || count <= 0; }
    public Item getItem() { return item; }
    public boolean hasFoil() { return false; }
    public boolean is(Item other) { return item == other; }
    public ItemStack copy() { return isEmpty() ? EMPTY : new ItemStack(item, count); }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public void shrink(int amount) { this.count = Math.max(0, this.count - amount); }
    public int getMaxStackSize() { return 64; }
}
""",
    "net/minecraft/world/level/saveddata/SavedData.java": """\
package net.minecraft.world.level.saveddata;

import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;

public abstract class SavedData {
    public abstract CompoundTag save(CompoundTag tag, HolderLookup.Provider registries);
    public void setDirty() {}
    public boolean isDirty() { return false; }

    public static class Factory<T extends SavedData> {
        public final Supplier<T> constructor;
        public final BiFunction<CompoundTag, HolderLookup.Provider, T> loader;

        public Factory(Supplier<T> constructor, BiFunction<CompoundTag, HolderLookup.Provider, T> loader,
                DataFixTypes type) {
            this.constructor = constructor;
            this.loader = loader;
        }
    }
}
""",
    "net/minecraft/world/level/storage/DimensionDataStorage.java": """\
package net.minecraft.world.level.storage;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.saveddata.SavedData;

public class DimensionDataStorage {
    private final Map<String, SavedData> map = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <T extends SavedData> T computeIfAbsent(SavedData.Factory<T> factory, String name) {
        SavedData existing = map.get(name);
        if (existing != null) return (T) existing;
        T created = factory.constructor.get();
        map.put(name, created);
        return created;
    }
}
""",
    "net/minecraft/core/registries/BuiltInRegistries.java": """\
package net.minecraft.core.registries;

import net.minecraft.core.DefaultedRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class BuiltInRegistries {
    public static DefaultedRegistry<Block> BLOCK = null;
    public static DefaultedRegistry<EntityType> ENTITY_TYPE = null;
    public static DefaultedRegistry<Item> ITEM = null;
    private BuiltInRegistries() {}
}
""",
    "net/minecraft/resources/ResourceKey.java": """\
package net.minecraft.resources;

import net.minecraft.core.Registry;

public class ResourceKey<T> {
    private final ResourceLocation loc;

    public ResourceKey() { this.loc = null; }
    private ResourceKey(ResourceLocation loc) { this.loc = loc; }

    public ResourceLocation location() { return loc; }

    public static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>> registry,
            ResourceLocation location) {
        return location == null ? null : new ResourceKey<>(location);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ResourceKey<?> rk)) return false;
        return loc == null ? rk.loc == null : loc.equals(rk.loc);
    }

    @Override
    public int hashCode() {
        return loc == null ? 0 : loc.hashCode();
    }

    @Override public String toString() {
        return loc == null ? "ResourceKey[null]" : "ResourceKey[minecraft:dimension / " + loc + "]";
    }
}
""",
}


def find_java():
    return build436_check.find_java()


def fail(msg):
    print("build437_check: FAIL - " + msg)
    return 1


def method_calls(raw, method_name):
    parsed = disasm.parse_class(raw)
    cp = parsed["cp"]
    chunks = []
    for _acc, ni, _di, attrs in parsed["methods"]:
        if cp[ni][1] == method_name:
            code = disasm.code_of(attrs, cp)
            if code is not None:
                listing, _ = disasm.disasm(code["code"], cp, labels_only=True)
                chunks.append(listing)
    return "\n".join(chunks)


def check_structural(jar: pathlib.Path):
    problems = list(build436_check.check_structural(jar))
    with zipfile.ZipFile(jar) as zf:
        anchor = zf.read("dev/settledlands/npc/NpcGraphAnchor420.class")
        core = zf.read("dev/settledlands/npc/NpcGraphAnchorCore420.class")
        chest = zf.read("dev/settledlands/chest/ChestGraph396.class")
        block = zf.read("dev/settledlands/npc/BlockTrigger351.class")
        door = zf.read("dev/settledlands/door/DoorGraph382.class")
        door_ev = zf.read("dev/settledlands/door/DoorEvents368.class")
        loot_mgr = zf.read("dev/settledlands/chest/LootPoolManager436.class")
        tpl = zf.read("dev/settledlands/dungeon/DungeonTemplate425.class")

        for store in (
            b"BlockTriggerStore351",
            b"DoorGraph382Store",
            b"ChestGraph396Store",
            b"DoorStore368",
        ):
            if store not in anchor:
                problems.append("NpcGraphAnchor420 missing store reference: " + store.decode("ascii"))
        if b"SettledLandsAnchorDim" not in anchor:
            problems.append("NpcGraphAnchor420 missing SettledLandsAnchorDim dimension tag")
        if b"BlockEntity" in anchor:
            problems.append("NpcGraphAnchor420 must not reference BlockEntity (anchor_check contract)")

        gp_listing = method_calls(anchor, "graphPlaces")
        for wanted in ("BlockTriggerStore351", "DoorGraph382Store", "ChestGraph396Store", "DoorStore368"):
            if wanted not in gp_listing:
                problems.append("NpcGraphAnchor420#graphPlaces does not query " + wanted)

        if not method_calls(core, "placeWithTurn"):
            problems.append("NpcGraphAnchorCore420 missing placeWithTurn method")
        if "placeWithTurn" not in method_calls(anchor, "materialiseForDungeon"):
            problems.append("NpcGraphAnchor420#materialiseForDungeon missing placeWithTurn fallback")

        for cls_name, raw, mname in (
            ("ChestGraph396", chest, "save"),
            ("BlockTrigger351", block, "save"),
            ("DoorGraph382", door, "save"),
            ("DoorEvents368", door_ev, "onRightClick"),
        ):
            if "anchorForSave" not in method_calls(raw, mname):
                problems.append(f"{cls_name}#{mname} never calls NpcGraphAnchor420.anchorForSave")

        if "materialiseForDungeon" not in method_calls(loot_mgr, "onDungeonRoomPlaced"):
            problems.append("LootPoolManager436#onDungeonRoomPlaced missing materialiseForDungeon")
        if "onDungeonRoomPlaced" not in method_calls(tpl, "placeBlockEntitiesAndEntities"):
            problems.append("DungeonTemplate425#placeBlockEntitiesAndEntities missing onDungeonRoomPlaced")

    return problems


def compile_and_run_probe(jar: pathlib.Path):
    jar = jar.resolve()
    java = find_java()
    ecj = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"

    with tempfile.TemporaryDirectory() as td_raw:
        td = pathlib.Path(td_raw)
        merged = td / "merged"
        stubs_out = td / "stubs_out"
        shims_src = td / "shims_src"
        shims_out = td / "shims_out"
        probe_out = td / "probe_out"
        work_dir = td / "workdir"
        for d in (merged, stubs_out, shims_src, shims_out, probe_out, work_dir):
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

        # Build test shims cleanly on top of stubs_out
        shim_files = []
        for rel, body in TEST_SHIMS.items():
            dst = shims_src / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            dst.write_text(body, encoding="utf-8")
            shim_files.append(str(dst))
        r2 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{stubs_out}:{jar}", "-d", str(shims_out)] + shim_files,
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise AssertionError("test shims compile failed:\n" + r2.stdout + r2.stderr)

        probe437 = ROOT / "tools" / "harness" / "Build437Probe.java"
        r3 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{shims_out}:{jar}:{stubs_out}", "-d", str(probe_out), str(probe437)],
            capture_output=True, text=True)
        if r3.returncode != 0:
            raise AssertionError("Build437Probe compile failed:\n" + r3.stdout + r3.stderr)

        r4 = subprocess.run(
            [java, "-cp", f"{probe_out}:{shims_out}:{jar}:{stubs_out}", "Build437Probe"],
            cwd=str(work_dir), capture_output=True, text=True)
        if r4.returncode != 0:
            raise AssertionError(f"Build437Probe failed (rc={r4.returncode}):\n{r4.stdout}\n{r4.stderr}")
        sys.stdout.write(r4.stdout)


def main(argv):
    if len(argv) != 1:
        print("usage: build437_check.py <jar>")
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        return fail("no such jar: %s" % jar)

    problems = check_structural(jar)
    if problems:
        for p in problems:
            print("  - " + p)
        return fail("structural check failed")

    try:
        compile_and_run_probe(jar)
    except AssertionError as ex:
        return fail(str(ex))

    print("build437_check: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
