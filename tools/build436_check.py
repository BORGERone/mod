#!/usr/bin/env python3
"""Comprehensive verification gate for Build 436 (Refurbished furniture chests + Diegesis/Handson + Loot Pool)."""

import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import chest_graph_check
import disasm

ROOT = pathlib.Path(__file__).resolve().parent.parent

LAYERS = (
    ROOT / "tools" / "api271",
    ROOT / "build303-src" / "stubs",
    ROOT / "build303-src" / "stubs345-src",
    ROOT / "build303-src" / "stubs396-src",
    ROOT / "build303-src" / "stubs420-src",
    ROOT / "build303-src" / "api-src",
    ROOT / "build303-src" / "api3-src",
    ROOT / "build303-src" / "api4-src",
    ROOT / "build303-src" / "api5-src",
    ROOT / "build303-src" / "api416-src",
    ROOT / "build303-src" / "stubs425-src",
    ROOT / "build303-src" / "stubs436-src",
    ROOT / "build303-src" / "stubs438-src",
)

EXPECTED_SUFFIXES = (
    "_crate",
    "_drawer",
    "_cooler",
    "_storage_cabinet",
    "_fridge",
    "_mail_box",
    "_storage_jar",
)

FORBIDDEN_STATIONS = (
    "_freezer",
    "_microwave",
    "_stove",
    "_toaster",
    "_grill",
    "frying_pan",
    "_cutting_board",
    "recycle_bin",
    "_electricity_generator",
    "workbench",
    "post_box",
)

LANG_KEYS = (
    "gui.settledlands.loot_pool.menu",
    "gui.settledlands.loot_pool.title",
    "gui.settledlands.loot_pool.added",
    "gui.settledlands.loot_pool.file",
    "gui.settledlands.loot_pool.none",
    "gui.settledlands.loot_pool.any",
    "gui.settledlands.loot_pool.limits_hint",
    "gui.settledlands.loot_pool.max_rarity",
    "gui.settledlands.loot_pool.max_weight",
    "gui.settledlands.loot_pool.rolls",
    "gui.settledlands.loot_pool.folder_hint",
    "gui.settledlands.loot_pool.dungeon_only",
    "gui.settledlands.loot_pool.card_limits",
    "gui.settledlands.loot_pool.card_rolls",
    "gui.settledlands.loot_pool.card_hint",
)

COMMON_MIXINS = ("LootPoolWiringMixin436", "LootPoolActionsMixin436")
CLIENT_MIXINS = ("LootPoolGraphUiMixin436", "LootPoolInspectorMixin436")

COMPAT_SOURCES = {
    "com/pockyl/handson_furniture/station/RoomyContainer.java": """\
package com.pockyl.handson_furniture.station;
public interface RoomyContainer {
    int handson$ownSize();
}
""",
    "com/pockyl/diegesis/api/StationContext.java": """\
package com.pockyl.diegesis.api;
import java.util.Collection;
public interface StationContext {
    Collection<?> players();
}
""",
    "com/pockyl/diegesis/api/Diegesis.java": """\
package com.pockyl.diegesis.api;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
public final class Diegesis {
    public static Collection<?> ACTIVE_PLAYERS = null;
    public static BlockPos ACTIVE_POS = null;
    public static Optional<StationContext> activeStation(ServerLevel level, BlockPos pos) {
        if (ACTIVE_PLAYERS == null || ACTIVE_POS == null || !ACTIVE_POS.equals(pos)) {
            return Optional.empty();
        }
        return Optional.of(() -> ACTIVE_PLAYERS);
    }
}
""",
    "Build436CompatProbe.java": """\
import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.handson_furniture.station.RoomyContainer;
import dev.settledlands.chest.ChestDiegesisCompat436;
import java.util.Collections;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import sun.misc.Unsafe;

public final class Build436CompatProbe {
    interface RoomyMock extends Container, RoomyContainer {}

    public static void main(String[] args) throws Exception {
        java.lang.reflect.Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Unsafe unsafe = (Unsafe) f.get(null);
        ServerLevel level = (ServerLevel) unsafe.allocateInstance(ServerLevel.class);
        ServerPlayer p1 = (ServerPlayer) unsafe.allocateInstance(ServerPlayer.class);
        ServerPlayer p2 = (ServerPlayer) unsafe.allocateInstance(ServerPlayer.class);

        BlockPos pos = new BlockPos(10, 64, -5);
        Diegesis.ACTIVE_POS = pos;
        Diegesis.ACTIVE_PLAYERS = Collections.singletonList(p1);
        if (!ChestDiegesisCompat436.isStationOpen(level, pos, p1)) {
            throw new AssertionError("expected p1 station open");
        }
        if (ChestDiegesisCompat436.isStationOpen(level, pos, p2)) {
            throw new AssertionError("expected p2 station closed");
        }
        Diegesis.ACTIVE_PLAYERS = Collections.emptyList();
        if (ChestDiegesisCompat436.isStationOpen(level, pos, p1)) {
            throw new AssertionError("expected station closed once empty");
        }

        RoomyMock roomy = new RoomyMock() {
            @Override public ItemStack getItem(int slot) { return ItemStack.EMPTY; }
            @Override public int getContainerSize() { return 162; }
            @Override public void setItem(int slot, ItemStack stack) {}
            @Override public int getMaxStackSize() { return 64; }
            @Override public boolean canPlaceItem(int slot, ItemStack stack) { return true; }
            @Override public int handson$ownSize() { return 27; }
        };
        int cap = ChestDiegesisCompat436.usableSlotCount(roomy, roomy);
        if (cap != 27) {
            throw new AssertionError("expected handson$ownSize 27, got " + cap);
        }
        System.out.println("Build436CompatProbe: 4 checks passed (Diegesis + RoomyContainer reflection)");
    }
}
""",
}


def find_java():
    venv_java = pathlib.Path("/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java")
    if venv_java.is_file():
        return str(venv_java)
    found = shutil.which("java")
    if found:
        return found
    raise SystemExit("no java found")


def fail(msg):
    print("build436_check: FAIL - " + msg)
    return 1


def check_structural(jar: pathlib.Path):
    problems = []
    # Reuse chest_graph_check.structural() directly so zero chest regressions slip through.
    problems.extend(chest_graph_check.structural(jar))

    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        required_classes = (
            "dev/settledlands/chest/LootPool436.class",
            "dev/settledlands/chest/ChestDiegesisCompat436.class",
            "dev/settledlands/chest/LootPoolManager436.class",
            "dev/settledlands/chest/ChestLootNet436.class",
            "dev/settledlands/chest/ChestClientEvents436.class",
            "dev/settledlands/npc/client/LootPoolCard436.class",
            "dev/settledlands/mixin/LootPoolGraphUiMixin436.class",
            "dev/settledlands/mixin/LootPoolInspectorMixin436.class",
            "dev/settledlands/mixin/LootPoolWiringMixin436.class",
            "dev/settledlands/mixin/LootPoolActionsMixin436.class",
        )
        for cls in required_classes:
            if cls not in names:
                problems.append("missing class: " + cls)

        mixins = json.loads(zf.read("settledlands.mixins.json").decode("utf-8"))
        for m in COMMON_MIXINS:
            if m not in mixins.get("mixins", []):
                problems.append("common mixin not registered: " + m)
        for m in CLIENT_MIXINS:
            if m not in mixins.get("client", []):
                problems.append("client mixin not registered: " + m)
        if mixins.get("client", [""])[0] != "NpcDropdownFirst333":
            problems.append("NpcDropdownFirst333 is no longer first in client mixins")

        for lp in ("assets/settledlands/lang/ru_ru.json", "assets/settledlands/lang/en_us.json"):
            table = json.loads(zf.read(lp).decode("utf-8"))
            for key in LANG_KEYS:
                if not table.get(key):
                    problems.append("%s missing or empty key: %s" % (lp, key))

        chest_cls = zf.read("dev/settledlands/chest/ChestGraph396.class")
        if b"refurbished_furniture" not in chest_cls:
            problems.append("ChestGraph396 missing refurbished_furniture namespace check")
        for sfx in EXPECTED_SUFFIXES:
            if sfx.encode("utf-8") not in chest_cls:
                problems.append("ChestGraph396 missing storage suffix " + sfx)
        for bad in FORBIDDEN_STATIONS:
            if bad.encode("utf-8") in chest_cls:
                problems.append("ChestGraph396 unexpectedly contains appliance token " + bad)

        loot_mgr = zf.read("dev/settledlands/chest/LootPoolManager436.class")
        if b"isInDungeon" not in loot_mgr:
            problems.append("LootPoolManager436 must check DungeonManager425.isInDungeon")
        if b"usableSlotCount" not in loot_mgr:
            problems.append("LootPoolManager436 must respect ChestDiegesisCompat436.usableSlotCount")

        # Verify LootPoolInspectorMixin436 declares zero synthetic lambdas on target screen
        insp = disasm.parse_class(zf.read("dev/settledlands/mixin/LootPoolInspectorMixin436.class"))
        cp = insp["cp"]
        for _acc, ni, _di, _attrs in insp["methods"]:
            mname = cp[ni][1]
            if mname.startswith("lambda$"):
                problems.append("LootPoolInspectorMixin436 declares synthetic lambda: " + mname)

    return problems


def compile_and_run_probes(jar: pathlib.Path):
    jar = jar.resolve()
    java = find_java()
    ecj = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"

    with tempfile.TemporaryDirectory() as td_raw:
        td = pathlib.Path(td_raw)
        merged = td / "merged"
        stubs_out = td / "stubs_out"
        probe_out = td / "probe_out"
        compat_src = td / "compat_src"
        compat_out = td / "compat_out"
        work_dir = td / "workdir"
        for d in (merged, stubs_out, probe_out, compat_src, compat_out, work_dir):
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

        # 1. Run Build436Probe
        probe436 = ROOT / "tools" / "harness" / "Build436Probe.java"
        r2 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{jar}:{stubs_out}", "-d", str(probe_out), str(probe436)],
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise AssertionError("Build436Probe compile failed:\n" + r2.stdout + r2.stderr)
        r3 = subprocess.run(
            [java, "-cp", f"{probe_out}:{jar}:{stubs_out}", "Build436Probe"],
            cwd=str(work_dir), capture_output=True, text=True)
        if r3.returncode != 0:
            raise AssertionError(f"Build436Probe failed (rc={r3.returncode}):\n{r3.stdout}\n{r3.stderr}")
        sys.stdout.write(r3.stdout)

        # 2. Run Diegesis + RoomyContainer live reflection probe
        compat_files = []
        for rel, body in COMPAT_SOURCES.items():
            dst = compat_src / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            dst.write_text(body, encoding="utf-8")
            compat_files.append(str(dst))
        r4 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{jar}:{stubs_out}", "-d", str(compat_out)] + compat_files,
            capture_output=True, text=True)
        if r4.returncode != 0:
            raise AssertionError("Build436CompatProbe compile failed:\n" + r4.stdout + r4.stderr)
        r5 = subprocess.run(
            [java, "-cp", f"{compat_out}:{jar}:{stubs_out}", "Build436CompatProbe"],
            capture_output=True, text=True)
        if r5.returncode != 0:
            raise AssertionError(f"Build436CompatProbe failed (rc={r5.returncode}):\n{r5.stdout}\n{r5.stderr}")
        sys.stdout.write(r5.stdout)

        # 3. Re-run all four ChestGraph probes with merged stubs & verify exact expectations
        chest_probes = (
            ("ChestGraph396Probe", chest_graph_check.EXPECT),
            ("ChestHack397Probe", chest_graph_check.EXPECT_LOCK),
            ("ChestOpen401Probe", chest_graph_check.EXPECT_OPEN),
            ("ChestClose402Probe", chest_graph_check.EXPECT_CLOSE),
        )
        for cls, expect in chest_probes:
            src = ROOT / "tools" / "harness" / f"{cls}.java"
            rc = subprocess.run(
                [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
                 "-cp", f"{jar}:{stubs_out}", "-d", str(probe_out), str(src)],
                capture_output=True, text=True)
            if rc.returncode != 0:
                raise AssertionError(f"{cls} compile failed:\n{rc.stdout}\n{rc.stderr}")
            rr = subprocess.run(
                [java, "-cp", f"{probe_out}:{jar}:{stubs_out}", cls],
                capture_output=True, text=True)
            if rr.returncode != 0:
                raise AssertionError(f"{cls} crashed (rc={rr.returncode}):\n{rr.stdout}\n{rr.stderr}")
            got = {}
            for line in rr.stdout.splitlines():
                parts = line.split(None, 1)
                if len(parts) == 2:
                    got[parts[0]] = parts[1].strip()
            for k, want in expect.items():
                if got.get(k) != want:
                    raise AssertionError(f"{cls}: {k}={got.get(k)!r}, expected {want!r}")
        print("ChestRegressionProbes (396/397/401/402): PASS")


def main(argv):
    if len(argv) != 1:
        print("usage: build436_check.py <jar>")
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
        compile_and_run_probes(jar)
    except AssertionError as ex:
        return fail(str(ex))

    print("build436_check: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
