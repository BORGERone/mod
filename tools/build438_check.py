#!/usr/bin/env python3
"""Comprehensive verification gate for Build 438 (Shelf wand highlight + Axiom BE graphs + In-room anchor rescue)."""

import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import anchor_check
import build436_check
import build437_check
import disasm
import door_graph_check
import hell_dungeon_check
import invoke_kind_check
import mixin_clash_check

ROOT = pathlib.Path(__file__).resolve().parent.parent

LAYERS = tuple(dict.fromkeys(build437_check.LAYERS + (ROOT / "build303-src" / "stubs438-src",)))

# Extend Build437 TEST_SHIMS with NeoForgeData load forwarding on Entity, non-null ParticleTypes,
# and DefaultRegistry/Player support needed by Build438Probe & HellDungeon425Probe.
TEST_SHIMS = dict(build437_check.TEST_SHIMS)
TEST_SHIMS["net/minecraft/world/entity/Entity.java"] = """\
package net.minecraft.world.entity;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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

    public void load(CompoundTag tag) {
        if (tag == null) return;
        if (tag.contains("NeoForgeData", 10)) {
            CompoundTag nf = tag.getCompound("NeoForgeData");
            for (String k : nf.getAllKeys()) {
                if (nf.contains(k, 9)) persistentData.put(k, nf.getList(k, 10));
                else if (nf.contains(k, 10)) persistentData.put(k, nf.getCompound(k));
                else if (nf.getIntArray(k).length > 0) persistentData.putIntArray(k, nf.getIntArray(k));
                else if (!nf.getString(k).isEmpty()) persistentData.putString(k, nf.getString(k));
                else if (nf.getInt(k) != 0) persistentData.putInt(k, nf.getInt(k));
            }
        }
    }

    @Override public boolean equals(Object other) { return this == other; }
    @Override public int hashCode() { return System.identityHashCode(this); }
}
"""

TEST_SHIMS["net/minecraft/core/particles/ParticleTypes.java"] = """\
package net.minecraft.core.particles;

public class ParticleTypes {
    public static final SimpleParticleType ENCHANT = new SimpleParticleType();
    public static final SimpleParticleType ELECTRIC_SPARK = new SimpleParticleType();
}
"""

TEST_SHIMS["dev/settledlands/npc/NpcContent.java"] = """\
package dev.settledlands.npc;

import net.minecraft.world.item.Item;

public final class NpcContent {
    private static Item wand;

    public static Item wand() {
        return wand;
    }
}
"""


def find_java():
    return build436_check.find_java()


def fail(msg):
    print("build438_check: FAIL - " + msg)
    return 1


def method_calls(raw, method_name):
    return build437_check.method_calls(raw, method_name)


def check_structural(jar: pathlib.Path, base_jar: pathlib.Path | None = None):
    problems = list(build437_check.check_structural(jar))
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        if "dev/settledlands/npc/RadiusPass354$HighlightCandidate.class" not in names:
            problems.append("missing RadiusPass354$HighlightCandidate inner class")

        radius = zf.read("dev/settledlands/npc/RadiusPass354.class")
        chest = zf.read("dev/settledlands/chest/ChestGraph396.class")
        files = zf.read("dev/settledlands/npc/NpcGraphAnchorFiles420.class")
        files_sink = zf.read("dev/settledlands/npc/NpcGraphAnchorFiles420$1.class")
        anchor = zf.read("dev/settledlands/npc/NpcGraphAnchor420.class")
        loot_mgr = zf.read("dev/settledlands/chest/LootPoolManager436.class")
        tpl = zf.read("dev/settledlands/dungeon/DungeonTemplate425.class")
        mgr = zf.read("dev/settledlands/dungeon/DungeonManager425.class")

        # 1. RadiusPass354 must highlight non-door spots (including ChestSpot) via HighlightCandidate
        pass_listing = method_calls(radius, "pass")
        if "HighlightCandidate" not in pass_listing or "door" not in pass_listing:
            problems.append("RadiusPass354#pass does not filter spots via HighlightCandidate / Spot#door")

        # 2. ChestGraph396 must embed/read/sync SettledLandsChestGraph via IBlockEntityExtension
        if b"SettledLandsChestGraph" not in chest or b"IBlockEntityExtension" not in chest:
            problems.append("ChestGraph396 missing SettledLandsChestGraph / IBlockEntityExtension persistence")
        for mname in ("open", "save"):
            if "BlockEntityGraph" not in method_calls(chest, mname):
                problems.append(f"ChestGraph396#{mname} never updates BlockEntity persistent graph")
        if "getChunkNow" not in method_calls(chest, "syncEmbeddedGraphsNear"):
            problems.append("ChestGraph396#syncEmbeddedGraphsNear does not use ServerChunkCache.getChunkNow")

        # 3. Anchor files sync embedded chest graphs on collect & materialize
        if "ensureBlockEntityGraph" not in method_calls(files, "collect"):
            problems.append("NpcGraphAnchorFiles420#collect does not ensureBlockEntityGraph")
        if "writeBlockEntityGraph" not in method_calls(files_sink, "chest"):
            problems.append("NpcGraphAnchorFiles420$1#chest does not writeBlockEntityGraph")

        # 4. Anchor world class uses syncEmbeddedGraphsNear, interiorScore, and ensureForPlayer
        if "syncEmbeddedGraphsNear" not in method_calls(anchor, "passLevel"):
            problems.append("NpcGraphAnchor420#passLevel missing ChestGraph396.syncEmbeddedGraphsNear")
        if "interiorScore" not in method_calls(anchor, "freeSpot"):
            problems.append("NpcGraphAnchor420#freeSpot missing interiorScore check")
        if not method_calls(anchor, "ensureForPlayer"):
            problems.append("NpcGraphAnchor420 missing ensureForPlayer helper")

        # 5. Dungeon template & manager
        if not method_calls(tpl, "hasEmbeddedChestGraphs"):
            problems.append("DungeonTemplate425 missing hasEmbeddedChestGraphs")
        pe_listing = method_calls(tpl, "placeBlockEntitiesAndEntities")
        for needle in ("extractEmbeddedChestGraph", "populateContainer", "freeSpot", "spawn", "seal"):
            if needle not in pe_listing:
                problems.append(f"DungeonTemplate425#placeBlockEntitiesAndEntities missing {needle}")
        if "hasEmbeddedChestGraphs" not in method_calls(mgr, "hasEntities"):
            problems.append("DungeonManager425#hasEntities missing hasEmbeddedChestGraphs check")
        for mname in ("enterDungeon", "createAndEnterUniqueInstance"):
            if "ensureForPlayer" not in method_calls(mgr, mname):
                problems.append(f"DungeonManager425#{mname} missing NpcGraphAnchor420.ensureForPlayer")

    if mixin_clash_check.main([str(jar)]) != 0:
        problems.append("mixin_clash_check failed")
    if door_graph_check.main([str(jar)]) != 0:
        problems.append("door_graph_check failed")
    anchor_args = [str(jar)] + (["--base", str(base_jar)] if base_jar and base_jar.is_file() else [])
    if anchor_check.main(anchor_args) != 0:
        problems.append("anchor_check failed")
    ik_args = [str(jar)] + (["--base", str(base_jar)] if base_jar and base_jar.is_file() else [])
    if invoke_kind_check.main(ik_args) != 0:
        problems.append("invoke_kind_check failed")

    return problems


def compile_and_run_all_probes(jar: pathlib.Path):
    jar = jar.resolve()
    java = find_java()
    ecj = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"

    with tempfile.TemporaryDirectory() as td_raw:
        td = pathlib.Path(td_raw)
        merged = td / "merged"
        stubs_out = td / "stubs_out"
        shims_src = td / "shims_src"
        shims_out = td / "shims_out"
        compat_src = td / "compat_src"
        compat_out = td / "compat_out"
        probe_out = td / "probe_out"
        work_dir = td / "workdir"
        for d in (merged, stubs_out, shims_src, shims_out, compat_src, compat_out, probe_out, work_dir):
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

        # Build436 compat interfaces
        compat_files = []
        for rel, body in build436_check.COMPAT_SOURCES.items():
            if rel == "Build436CompatProbe.java":
                continue
            dst = compat_src / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            dst.write_text(body, encoding="utf-8")
            compat_files.append(str(dst))
        r2c = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{shims_out}:{jar}:{stubs_out}", "-d", str(compat_out)] + compat_files,
            capture_output=True, text=True)
        if r2c.returncode != 0:
            raise AssertionError("compat sources compile failed:\n" + r2c.stdout + r2c.stderr)

        probes = [
            ("Build436Probe", ROOT / "tools" / "harness" / "Build436Probe.java"),
            ("Build437Probe", ROOT / "tools" / "harness" / "Build437Probe.java"),
            ("Build438Probe", ROOT / "tools" / "harness" / "Build438Probe.java"),
        ]
        probe_srcs = [str(p) for _, p in probes]
        r3 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{shims_out}:{compat_out}:{jar}:{stubs_out}", "-d", str(probe_out)] + probe_srcs,
            capture_output=True, text=True)
        if r3.returncode != 0:
            raise AssertionError("Probes compile failed:\n" + r3.stdout + r3.stderr)

        for main_cls, _ in probes:
            r4 = subprocess.run(
                [java, "-cp", f"{probe_out}:{shims_out}:{compat_out}:{jar}:{stubs_out}", main_cls],
                cwd=str(work_dir), capture_output=True, text=True)
            if r4.returncode != 0:
                raise AssertionError(f"{main_cls} failed (rc={r4.returncode}):\n{r4.stdout}\n{r4.stderr}")
            sys.stdout.write(r4.stdout)


def main(argv):
    if len(argv) < 1 or len(argv) > 3:
        print("usage: build438_check.py <jar> [--base <base_jar>]")
        return 2
    jar = pathlib.Path(argv[0])
    base = None
    if len(argv) == 3 and argv[1] == "--base":
        base = pathlib.Path(argv[2])
    elif (ROOT / "settledlands-0.8.0-build437-anchor-furniture-fix.jar").is_file():
        base = ROOT / "settledlands-0.8.0-build437-anchor-furniture-fix.jar"

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

    print("build438_check: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
