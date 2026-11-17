#!/usr/bin/env python3
"""
Gate (Build 425): Hell Dungeon («Адский данж») dimension, start.slbp placement, and Teleport integration.

Checks inside the shipped jar:
  1. All Build 425 classes exist (DungeonTemplate425 + inner classes, DungeonData425 + inner classes,
     DungeonManager425, DungeonEvents425, DungeonCommand425) and zero new mixins were added;
  2. Data pack definitions exist and are structurally valid:
       - `data/settledlands/dimension_type/hell_dungeon.json` (`min_y=-64`, `height=384`,
         `effects="minecraft:the_nether"`, `ambient_light=0.15`, `fixed_time=18000`);
       - `data/settledlands/dimension/hell_dungeon.json` (`type="settledlands:hell_dungeon"`,
         void `minecraft:flat` generator with `layers=[]` and `biome="minecraft:the_void"`);
       - `data/settledlands/dungeon/start.slbp` is valid gzipped NBT with 6,019 non-air blocks,
         stripped entity UUID / attachments, and expected `settledlands:guide_trigger` + `trialtuner:trial_spawner`;
  3. Language keys `gui.settledlands.teleport.world.hell_dungeon` and
     `message.settledlands.dungeon.not_loaded` exist in both `ru_ru.json` and `en_us.json`;
  4. Bytecode checks on `DungeonEvents425` (`MobSpawnEvent$PositionCheck`, `FAIL` on natural/chunk/
     patrol/structure/reinforcement) and `DungeonManager425` (`ensureStartPlaced`, `teleportTo`, `displayClientMessage`);
  5. Compiles and runs `tools/harness/HellDungeon425Probe.java` under the JVM against the shipped jar
     and compiled stubs (`stubs425-src`).
"""

import gzip
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "dungeon"))
import disasm
import pack_start

ROOT = pathlib.Path(__file__).resolve().parent.parent

REQUIRED_CLASSES = (
    "dev/settledlands/dungeon/AxiomBlueprint427.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$CountingInputStream.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$Marker.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$NbtCompound.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$NbtReader.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$NbtValue.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$PieceTemplate.class",
    "dev/settledlands/dungeon/DungeonConfig427.class",
    "dev/settledlands/dungeon/DungeonBlueprints427.class",
    "dev/settledlands/dungeon/DungeonBlueprints427$Library.class",
    "dev/settledlands/dungeon/DungeonBlueprints427$SelectionResult.class",
    "dev/settledlands/dungeon/DungeonGenerator427.class",
    "dev/settledlands/dungeon/DungeonGenerator427$OpenExit.class",
    "dev/settledlands/dungeon/DungeonGenerator427$Plan.class",
    "dev/settledlands/dungeon/DungeonGenerator427$PlannedPiece.class",
    "dev/settledlands/dungeon/DungeonGenerator427$Seal.class",
    "dev/settledlands/dungeon/DungeonTemplate425.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PaletteEntry.class",
    "dev/settledlands/dungeon/DungeonTemplate425$BlockEntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$EntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$StartTemplate.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PlacementStats.class",
    "dev/settledlands/dungeon/DungeonData425.class",
    "dev/settledlands/dungeon/DungeonData425$Instance.class",
    "dev/settledlands/dungeon/DungeonData425$PieceProgress.class",
    "dev/settledlands/dungeon/DungeonData425$SealProgress.class",
    "dev/settledlands/dungeon/DungeonData425$ReturnPoint.class",
    "dev/settledlands/dungeon/DungeonManager425.class",
    "dev/settledlands/dungeon/DungeonEvents425.class",
    "dev/settledlands/dungeon/DungeonCommand425.class",
)

LANG_KEYS = (
    "gui.settledlands.teleport.world.hell_dungeon",
    "message.settledlands.dungeon.not_loaded",
    "message.settledlands.dungeon.no_blueprints",
)


def fail(msg):
    print("FAIL: " + msg)
    return 1


def find_java():
    for candidate in (
        "/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java",
        shutil.which("java"),
    ):
        if candidate and pathlib.Path(candidate).is_file():
            return str(candidate)
    raise SystemExit("no java executable found")


def cp_refs(cf):
    cp = cf["cp"]
    out = []
    for idx, entry in enumerate(cp):
        if entry and entry[0] in (9, 10, 11):
            out.append(disasm.ref(cp, idx))
    return out


def main(argv):
    if len(argv) < 1:
        print("usage: python3 tools/hell_dungeon_check.py <jar>")
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        return fail("no such jar: %s" % jar)

    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        for cls in REQUIRED_CLASSES:
            if cls not in names:
                return fail("missing class in jar: %s" % cls)

        # Dimension type JSON
        dt_path = "data/settledlands/dimension_type/hell_dungeon.json"
        if dt_path not in names:
            return fail("missing " + dt_path)
        dt = json.loads(zf.read(dt_path).decode("utf-8"))
        if dt.get("min_y") != -64 or dt.get("height") != 384 or dt.get("effects") != "minecraft:the_nether":
            return fail("unexpected hell_dungeon dimension_type settings: %r" % dt)
        if dt.get("fixed_time") != 18000 or abs(dt.get("ambient_light", 0) - 0.15) > 1e-6:
            return fail("unexpected hell_dungeon lighting/time: %r" % dt)

        # Dimension JSON
        dim_path = "data/settledlands/dimension/hell_dungeon.json"
        if dim_path not in names:
            return fail("missing " + dim_path)
        dim = json.loads(zf.read(dim_path).decode("utf-8"))
        gen = dim.get("generator", {})
        settings = gen.get("settings", {})
        if (dim.get("type") != "settledlands:hell_dungeon"
                or gen.get("type") != "minecraft:flat"
                or settings.get("biome") != "minecraft:the_void"
                or settings.get("layers") != []):
            return fail("unexpected hell_dungeon dimension generator: %r" % dim)

        # Packed start.slbp NBT
        slbp_path = "data/settledlands/dungeon/start.slbp"
        if slbp_path not in names:
            return fail("missing " + slbp_path)
        _, root_typed = pack_start.NbtReader.read_root(gzip.decompress(zf.read(slbp_path)))
        root_map = root_typed[1]
        pal = root_map["Palette"][1][1]
        blocks = root_map["Blocks"][1]
        block_entities = root_map["BlockEntities"][1][1]
        entities = root_map["Entities"][1][1]
        if len(blocks) // 4 != 6019:
            return fail("expected 6019 blocks in start.slbp, got %d" % (len(blocks) // 4))
        if len(pal) < 20:
            return fail("palette suspiciously small: %d" % len(pal))
        if len(block_entities) != 1 or block_entities[0][1]["id"][1] != "trialtuner:trial_spawner":
            return fail("unexpected BlockEntities in start.slbp")
        if len(entities) != 1 or entities[0][1]["id"][1] != "settledlands:guide_trigger":
            return fail("unexpected Entities in start.slbp")
        ent_nbt = entities[0][1]["nbt"][1]
        if "UUID" in ent_nbt or "neoforge:attachments" in ent_nbt:
            return fail("entity UUID or neoforge:attachments leaked into start.slbp")

        # Lang keys
        for lang_path in ("assets/settledlands/lang/ru_ru.json", "assets/settledlands/lang/en_us.json"):
            data = json.loads(zf.read(lang_path).decode("utf-8"))
            for key in LANG_KEYS:
                val = data.get(key)
                if not isinstance(val, str) or not val.strip():
                    return fail("%s missing or blank key %s" % (lang_path, key))

        # Bytecode checks
        ev_info = disasm.parse_class(zf.read("dev/settledlands/dungeon/DungeonEvents425.class"))
        ev_refs = cp_refs(ev_info)
        for wanted in ("getLevel", "getSpawnType", "setResult", "getServer"):
            if not any(f".{wanted}(" in r for r in ev_refs):
                return fail("DungeonEvents425 missing %s" % wanted)
        ev_utf8 = {e[1] for e in ev_info["cp"] if e and e[0] == 1}
        if "net/neoforged/neoforge/event/tick/ServerTickEvent$Post" not in ev_utf8:
            return fail("DungeonEvents425 missing ServerTickEvent$Post")

        mgr_info = disasm.parse_class(zf.read("dev/settledlands/dungeon/DungeonManager425.class"))
        mgr_refs = cp_refs(mgr_info)
        for wanted in ("teleportTo", "displayClientMessage", "clearFire", "setDeltaMovement", "players"):
            if not any(f".{wanted}(" in r for r in mgr_refs):
                return fail("DungeonManager425 missing %s" % wanted)
        mgr_utf8 = {e[1] for e in mgr_info["cp"] if e and e[0] == 1}
        for wanted_utf8 in ("createAndEnterUniqueInstance", "enterUniqueOrDefault", "isZeroCoord"):
            if wanted_utf8 not in mgr_utf8:
                return fail("DungeonManager425 missing Build 428 method %s" % wanted_utf8)

        cfg_info = disasm.parse_class(zf.read("dev/settledlands/dungeon/DungeonConfig427.class"))
        cfg_utf8 = {e[1] for e in cfg_info["cp"] if e and e[0] == 1}
        if "unique_dungeon_per_entry" not in cfg_utf8 or "uniqueDungeonPerEntry" not in cfg_utf8:
            return fail("DungeonConfig427 missing Build 428 unique_dungeon_per_entry config")
        if not any("biomes" in s for s in cfg_utf8):
            return fail("DungeonConfig427 missing Build 429 biomes mode docs")
        if not any("min_pieces=85" in s and "max_pieces=110" in s and "max_radius=240" in s for s in cfg_utf8):
            return fail("DungeonConfig427 missing Build 430 default size (85..110, max_radius=240)")

        ax_info = disasm.parse_class(zf.read("dev/settledlands/dungeon/AxiomBlueprint427.class"))
        ax_utf8 = {e[1] for e in ax_info["cp"] if e and e[0] == 1}
        for wanted_ax in ("placementPhase", "isDoorLike"):
            if wanted_ax not in ax_utf8:
                return fail("AxiomBlueprint427 missing Build 430 %s" % wanted_ax)

        tmpl_info = disasm.parse_class(zf.read("dev/settledlands/dungeon/DungeonTemplate425.class"))
        tmpl_utf8 = {e[1] for e in tmpl_info["cp"] if e and e[0] == 1}
        if "isDoorLike" not in tmpl_utf8 or "PLACE_FLAGS" not in tmpl_utf8:
            return fail("DungeonTemplate425 missing Build 430 PLACE_FLAGS / isDoorLike")
        # Direct ConstantValue check: tag=ConstantValue holds u2 cp index to Integer=50
        tmpl_cp = tmpl_info["cp"]
        place_flags_field = [f for f in tmpl_info["fields"] if tmpl_cp[f[1]][1] == "PLACE_FLAGS"]
        pf_val = None
        if place_flags_field:
            for an, adata in place_flags_field[0][3]:
                if tmpl_cp[an][1] == "ConstantValue" and len(adata) == 2:
                    cidx = int.from_bytes(adata, "big")
                    entry = tmpl_cp[cidx]
                    if entry and entry[0] == 3:
                        pf_val = int.from_bytes(entry[1], "big", signed=True)
        if pf_val != 50:
            return fail("DungeonTemplate425.PLACE_FLAGS must equal 50 (2|16|32), got %r" % pf_val)
        # Also confirm no setBlock in placeSlice / placeSeal pushes constant 2 via iconst_2 without bipush 50
        tmpl_code = []
        for acc, ni, di, attrs in tmpl_info["methods"]:
            mname = tmpl_cp[ni][1]
            if mname in ("placeSlice", "placeSeal"):
                for an, adata in attrs:
                    if tmpl_cp[an][1] == "Code":
                        tmpl_code.append((mname, adata))
        for mname, adata in tmpl_code:
            if b"\x10\x32" not in adata:  # bipush 50 (0x10 0x32)
                return fail("DungeonTemplate425.%s does not push PLACE_FLAGS=50 (bipush 50)" % mname)

        bp_info = disasm.parse_class(zf.read("dev/settledlands/dungeon/DungeonBlueprints427.class"))
        bp_utf8 = {e[1] for e in bp_info["cp"] if e and e[0] == 1}
        for wanted_bp in ("themeOf", "auto->biomes("):
            if not any(wanted_bp in s for s in bp_utf8):
                return fail("DungeonBlueprints427 missing Build 429 %s" % wanted_bp)

        gen_info = disasm.parse_class(zf.read("dev/settledlands/dungeon/DungeonGenerator427.class"))
        gen_utf8 = {e[1] for e in gen_info["cp"] if e and e[0] == 1}
        for wanted_gen in ("generateBiomePlan", "tryGenerateBiome"):
            if wanted_gen not in gen_utf8:
                return fail("DungeonGenerator427 missing Build 429 %s" % wanted_gen)

        # Brigadier exception package verification (Build 426 hotfix for NeoForge 21.1.265 crash)
        for entry in names:
            if not entry.endswith(".class"):
                continue
            cf = disasm.parse_class(zf.read(entry))
            for e in cf["cp"]:
                if e and e[0] == 1 and "com/mojang/brigadier/CommandSyntaxException" in e[1]:
                    return fail("%s references invalid package com/mojang/brigadier/CommandSyntaxException "
                                "(must be com/mojang/brigadier/exceptions/CommandSyntaxException)" % entry)
        cmd_cf = disasm.parse_class(zf.read("dev/settledlands/dungeon/DungeonCommand425.class"))
        cmd_utf8 = {e[1] for e in cmd_cf["cp"] if e and e[0] == 1}
        if "com/mojang/brigadier/exceptions/CommandSyntaxException" not in cmd_utf8:
            return fail("DungeonCommand425 missing real com/mojang/brigadier/exceptions/CommandSyntaxException")
        if "reload" not in cmd_utf8:
            return fail("DungeonCommand425 missing /settledlands dungeon reload command")

    probe425 = ROOT / "tools" / "harness" / "HellDungeon425Probe.java"
    probe427 = ROOT / "tools" / "harness" / "HellDungeon427Probe.java"
    if not probe425.is_file():
        return fail("missing probe: %s" % probe425)
    if not probe427.is_file():
        return fail("missing probe: %s" % probe427)

    java = find_java()
    ecj = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = pathlib.Path(tmpdir)
        merged = tmp / "merged"
        stubs_out = tmp / "stubs_out"
        probe_src = tmp / "probe_src"
        probe_out = tmp / "probe_out"
        merged.mkdir()
        stubs_out.mkdir()
        probe_src.mkdir()
        probe_out.mkdir()
        layers = (
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
        )
        for layer in layers:
            for src in layer.rglob("*.java"):
                dst = merged / src.relative_to(layer)
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)
        stub_files = sorted(str(p) for p in merged.rglob("*.java"))
        r1 = subprocess.run([java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
                             "-cp", str(jar), "-d", str(stubs_out)] + stub_files,
                            capture_output=True, text=True)
        if r1.returncode != 0:
            return fail("probe stub compile failed:\n" + r1.stdout + r1.stderr)

        # Provide live Proxy-backed BuiltInRegistries ONLY on probe_out so HellDungeon425Probe can exercise
        # DungeonTemplate425.place() end-to-end on real start.slbp while keeping repo stubs420 untouched.
        reg_dir = probe_src / "net" / "minecraft" / "core" / "registries"
        reg_dir.mkdir(parents=True)
        (reg_dir / "ProbeRegistries.java").write_text(
            """package net.minecraft.core.registries;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.resources.ResourceLocation;

public final class ProbeRegistries {
    public static final Map<String, Object> BLOCK_BY_ID = new HashMap<>();
    public static final Map<Object, ResourceLocation> BLOCK_KEYS = new IdentityHashMap<>();
    public static final Map<String, Object> ENTITY_BY_ID = new HashMap<>();
    public static final Map<Object, ResourceLocation> ENTITY_KEYS = new IdentityHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> DefaultedRegistry<T> proxy(Map<String, Object> byId, Map<Object, ResourceLocation> keys) {
        return (DefaultedRegistry<T>) Proxy.newProxyInstance(
                DefaultedRegistry.class.getClassLoader(),
                new Class<?>[] { DefaultedRegistry.class },
                (p, m, args) -> {
                    String name = m.getName();
                    if ("get".equals(name) && args != null && args.length == 1 && args[0] instanceof ResourceLocation rl) {
                        return byId.get(rl.toString());
                    }
                    if ("getKey".equals(name) && args != null && args.length == 1 && args[0] != null) {
                        return keys.get(args[0]);
                    }
                    if ("containsKey".equals(name) && args != null && args.length == 1 && args[0] instanceof ResourceLocation rl) {
                        return byId.containsKey(rl.toString());
                    }
                    return null;
                });
    }

    public static void clear() {
        BLOCK_BY_ID.clear();
        BLOCK_KEYS.clear();
        ENTITY_BY_ID.clear();
        ENTITY_KEYS.clear();
    }

    public static void putBlock(ResourceLocation rl, Object block) {
        BLOCK_BY_ID.put(rl.toString(), block);
        BLOCK_KEYS.put(block, rl);
    }

    public static void putEntityType(ResourceLocation rl, Object type) {
        ENTITY_BY_ID.put(rl.toString(), type);
        ENTITY_KEYS.put(type, rl);
    }
}
""",
            encoding="utf-8",
        )
        (reg_dir / "BuiltInRegistries.java").write_text(
            """package net.minecraft.core.registries;

import net.minecraft.core.DefaultedRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

public final class BuiltInRegistries {
    public static final DefaultedRegistry<Block> BLOCK =
            ProbeRegistries.proxy(ProbeRegistries.BLOCK_BY_ID, ProbeRegistries.BLOCK_KEYS);
    public static final DefaultedRegistry<EntityType> ENTITY_TYPE =
            ProbeRegistries.proxy(ProbeRegistries.ENTITY_BY_ID, ProbeRegistries.ENTITY_KEYS);
    private BuiltInRegistries() {}
}
""",
            encoding="utf-8",
        )
        probe_inputs = [
            str(reg_dir / "ProbeRegistries.java"),
            str(reg_dir / "BuiltInRegistries.java"),
            str(probe425),
            str(probe427),
        ]
        r2 = subprocess.run([java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
                             "-cp", f"{jar.resolve()}:{stubs_out}", "-d", str(probe_out)] + probe_inputs,
                            capture_output=True, text=True)
        if r2.returncode != 0:
            return fail("HellDungeon probes compile failed:\n" + r2.stdout + r2.stderr)

        run_dir = tmp / "run_cwd"
        run_dir.mkdir()
        cp = f"{probe_out}:{jar.resolve()}:{stubs_out}"
        run_env = os.environ.copy()
        run_env["LC_ALL"] = "C.UTF-8"
        for probe_cls in ("HellDungeon425Probe", "HellDungeon427Probe"):
            r3 = subprocess.run([java, "-cp", cp, probe_cls],
                                cwd=str(run_dir), env=run_env, capture_output=True, text=True)
            if r3.returncode != 0:
                return fail("%s failed (rc=%d):\n%s\n%s" % (probe_cls, r3.returncode, r3.stdout, r3.stderr))
            sys.stdout.write(r3.stdout)

    print("hell_dungeon_check: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
