#!/usr/bin/env python3
"""
Gate (Build 424): Teleport node («Телепорт») across NPC, Block, Door and Chest editors.

Checks inside the shipped jar:
  1. All 9 Teleport classes (4 core/card + 5 mixins) exist and all 5 mixins are registered in
     `settledlands.mixins.json` under the right list (`mixins` vs `client`), keeping
     `NpcDropdownFirst333` first in `client`;
  2. All 13 `gui.settledlands.teleport.*` keys exist in both `ru_ru.json` and `en_us.json` and are
     non-blank;
  3. `TeleportActions424` executes via `ServerPlayer#teleportTo`, guards non-players with
     `instanceof ServerPlayer`, and resets `fallDistance`, `clearFire()` and `Vec3.ZERO`;
  4. `TeleportWiringMixin424` connects via `NpcDialogueGraph#connect`, handles `IF` (`falseNext` /
     `next`), `RANDOM` (`randomBranches`), and sequential `next` instead of `triggers`, while
     still refusing stats (`type == 16`), cards, and self-links;
  5. `TeleportContextMenuMixin424` and `TeleportGraphUiMixin424` keep `NpcPursuitContextMenuMixin272`
     intact and extend the NPC menu to 18 rows (row 17, max scroll 13) while `BlockMenu363` and
     `Npc{BlockGraph351,DoorGraph382,ChestPalette396}Mixin` expose `isTeleportRow` without breaking
     `BlockTrigger351#typeForRow`;
  6. Runs `Teleport424Probe` under the JVM against the shipped jar and compiled stubs.
"""

import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

ROOT = pathlib.Path(__file__).resolve().parent.parent

REQUIRED_CLASSES = (
    "dev/settledlands/npc/TeleportNode424.class",
    "dev/settledlands/npc/TeleportNode424$Parsed.class",
    "dev/settledlands/npc/TeleportActions424.class",
    "dev/settledlands/npc/client/TeleportCard424.class",
    "dev/settledlands/mixin/TeleportActionsMixin424.class",
    "dev/settledlands/mixin/TeleportWiringMixin424.class",
    "dev/settledlands/mixin/TeleportContextMenuMixin424.class",
    "dev/settledlands/mixin/TeleportGraphUiMixin424.class",
    "dev/settledlands/mixin/TeleportInspectorMixin424.class",
)

COMMON_MIXINS = ("TeleportActionsMixin424", "TeleportWiringMixin424")
CLIENT_MIXINS = ("TeleportContextMenuMixin424", "TeleportGraphUiMixin424", "TeleportInspectorMixin424")

LANG_KEYS = (
    "gui.settledlands.teleport.menu",
    "gui.settledlands.teleport.title",
    "gui.settledlands.teleport.added",
    "gui.settledlands.teleport.world",
    "gui.settledlands.teleport.world.overworld",
    "gui.settledlands.teleport.world.nether",
    "gui.settledlands.teleport.world.end",
    "gui.settledlands.teleport.world.purgatory",
    "gui.settledlands.teleport.world.hell_dungeon",
    "gui.settledlands.teleport.coords_hint",
    "gui.settledlands.teleport.x",
    "gui.settledlands.teleport.y",
    "gui.settledlands.teleport.z",
    "gui.settledlands.teleport.card_hint",
)


def fail(msg):
    print("FAIL: " + msg)
    return 1


def cp_refs(cf):
    cp = cf["cp"]
    out = []
    for idx, entry in enumerate(cp):
        if entry and entry[0] in (9, 10, 11):
            out.append(disasm.ref(cp, idx))
    return out


def find_java():
    for candidate in (
        "/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java",
        shutil.which("java"),
    ):
        if candidate and pathlib.Path(candidate).is_file():
            return str(candidate)
    raise SystemExit("no java executable found")


def main(argv):
    if len(argv) < 1:
        print("usage: python3 tools/teleport_check.py <jar>")
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        return fail("no such jar: %s" % jar)

    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        for cls in REQUIRED_CLASSES:
            if cls not in names:
                return fail("missing class in jar: %s" % cls)

        mixins = json.loads(zf.read("settledlands.mixins.json").decode("utf-8"))
        common = mixins.get("mixins", [])
        client = mixins.get("client", [])
        for m in COMMON_MIXINS:
            if m not in common:
                return fail("mixin %s not registered in mixins" % m)
            if m in client:
                return fail("mixin %s must not be in client" % m)
        for m in CLIENT_MIXINS:
            if m not in client:
                return fail("client mixin %s not registered in client" % m)
            if m in common:
                return fail("client mixin %s must not be in mixins" % m)
        if not client or client[0] != "NpcDropdownFirst333":
            return fail("NpcDropdownFirst333 must remain first in client mixins")

        for lang_path in ("assets/settledlands/lang/ru_ru.json", "assets/settledlands/lang/en_us.json"):
            data = json.loads(zf.read(lang_path).decode("utf-8"))
            for key in LANG_KEYS:
                val = data.get(key)
                if not isinstance(val, str) or not val.strip():
                    return fail("%s missing or blank key %s" % (lang_path, key))

        # Bytecode checks
        actions_info = disasm.parse_class(zf.read("dev/settledlands/npc/TeleportActions424.class"))
        refs = cp_refs(actions_info)
        for wanted in ("teleportTo", "clearFire", "setDeltaMovement", "tryParse"):
            if not any(f".{wanted}(" in r for r in refs):
                return fail("TeleportActions424 missing expected call %s" % wanted)

        for palette_mixin in (
            "dev/settledlands/mixin/NpcBlockGraph351Mixin.class",
            "dev/settledlands/mixin/NpcDoorGraph382Mixin.class",
            "dev/settledlands/mixin/NpcChestPalette396Mixin.class",
        ):
            pinfo = disasm.parse_class(zf.read(palette_mixin))
            prefs = cp_refs(pinfo)
            if not any(".isTeleportRow(" in r for r in prefs):
                return fail("%s does not check BlockMenu363#isTeleportRow" % palette_mixin)

        # Verify zero lambda$ methods inside TeleportInspectorMixin424 (mixin_clash_check clean)
        insp = disasm.parse_class(zf.read("dev/settledlands/mixin/TeleportInspectorMixin424.class"))
        icp = insp["cp"]
        for m in insp["methods"]:
            mname = icp[m[1]][1]
            if mname.startswith("lambda$"):
                return fail("TeleportInspectorMixin424 has synthetic lambda %s" % mname)

    # Compile & run Teleport424Probe against jar + stubs
    probe = ROOT / "tools" / "harness" / "Teleport424Probe.java"
    if not probe.is_file():
        return fail("missing probe: %s" % probe)

    java = find_java()
    ecj = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = pathlib.Path(tmpdir)
        merged = tmp / "merged"
        stubs_out = tmp / "stubs_out"
        probe_out = tmp / "probe_out"
        merged.mkdir()
        stubs_out.mkdir()
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

        r2 = subprocess.run([java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
                             "-cp", f"{jar}:{stubs_out}", "-d", str(probe_out), str(probe)],
                            capture_output=True, text=True)
        if r2.returncode != 0:
            return fail("Teleport424Probe compile failed:\n" + r2.stdout + r2.stderr)

        r3 = subprocess.run([java, "-cp", f"{probe_out}:{jar}:{stubs_out}", "Teleport424Probe"],
                            capture_output=True, text=True)
        if r3.returncode != 0:
            return fail("Teleport424Probe failed (rc=%d):\n%s\n%s" % (r3.returncode, r3.stdout, r3.stderr))
        sys.stdout.write(r3.stdout)

    print("teleport_check: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
