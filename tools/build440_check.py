#!/usr/bin/env python3
"""Comprehensive verification gate for Build 440 (Gear edit-pool buttons + in-game Loot Pool editor)."""

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
import build439_check
import disasm
import invoke_kind_check

ROOT = pathlib.Path(__file__).resolve().parent.parent

LAYERS = tuple(dict.fromkeys(build439_check.LAYERS + (ROOT / "build303-src" / "stubs440-src",)))

LANG_KEYS = (
    "gui.settledlands.loot_pool.edit_btn_tip",
    "gui.settledlands.loot_pool.edit_row_tip",
    "gui.settledlands.loot_pool.create_row_tip",
    "gui.settledlands.loot_pool.editor.title",
    "gui.settledlands.loot_pool.editor.close",
    "gui.settledlands.loot_pool.editor.back",
    "gui.settledlands.loot_pool.editor.file_lbl",
    "gui.settledlands.loot_pool.editor.new_pool",
    "gui.settledlands.loot_pool.editor.delete",
    "gui.settledlands.loot_pool.editor.delete_confirm",
    "gui.settledlands.loot_pool.editor.col_item",
    "gui.settledlands.loot_pool.editor.col_count",
    "gui.settledlands.loot_pool.editor.col_rarity",
    "gui.settledlands.loot_pool.editor.col_weight",
    "gui.settledlands.loot_pool.editor.col_chance",
    "gui.settledlands.loot_pool.editor.add_item",
    "gui.settledlands.loot_pool.editor.add_item_short",
    "gui.settledlands.loot_pool.editor.add_item_tip",
    "gui.settledlands.loot_pool.editor.test",
    "gui.settledlands.loot_pool.editor.test_short",
    "gui.settledlands.loot_pool.editor.save",
    "gui.settledlands.loot_pool.editor.save_close",
    "gui.settledlands.loot_pool.editor.save_close_short",
    "gui.settledlands.loot_pool.editor.saved",
    "gui.settledlands.loot_pool.editor.deleted",
    "gui.settledlands.loot_pool.picker.title",
    "gui.settledlands.loot_pool.picker.replace_title",
    "gui.settledlands.loot_pool.picker.mode_all",
    "gui.settledlands.loot_pool.picker.mode_inv",
    "gui.settledlands.loot_pool.picker.add_id",
    "gui.settledlands.loot_pool.picker.add_inv_all",
)

TEST_SHIMS = dict(build439_check.TEST_SHIMS)
TEST_SHIMS["net/neoforged/neoforge/network/PacketDistributor.java"] = """\
package net.neoforged.neoforge.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class PacketDistributor {
    public static final List<CustomPacketPayload> SENT_TO_SERVER = new ArrayList<>();
    public static final List<CustomPacketPayload> SENT_TO_PLAYER = new ArrayList<>();

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload,
            CustomPacketPayload... payloads) {
        if (payload != null) SENT_TO_PLAYER.add(payload);
        if (payloads != null) {
            for (CustomPacketPayload extra : payloads) {
                if (extra != null) SENT_TO_PLAYER.add(extra);
            }
        }
    }

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

TEST_SHIMS["net/minecraft/server/level/ServerPlayer.java"] = """\
package net.minecraft.server.level;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

public class ServerPlayer extends Player {
    @Override
    public boolean hasPermissions(int level) {
        return true;
    }

    public MinecraftServer getServer() {
        return null;
    }
}
"""


def fail(msg):
    print("build440_check: FAIL - " + msg)
    return 1


def _cp_class_refs(class_bytes):
    p = disasm.parse_class(class_bytes)
    cp = p["cp"]
    refs = set()
    for i in range(1, len(cp)):
        entry = cp[i]
        if entry and entry[0] == 7:  # CONSTANT_Class
            name = disasm.cls_name(cp, i)
            if name:
                refs.add(name)
    return refs


def check_structural(jar: pathlib.Path, base_jar: pathlib.Path | None = None):
    problems = []
    problems.extend(build436_check.check_structural(jar))
    problems.extend(build439_check.check_structural(jar, base_jar))

    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        required = (
            "dev/settledlands/chest/ChestLootNet436$PoolQuery.class",
            "dev/settledlands/chest/ChestLootNet436$PoolData.class",
            "dev/settledlands/chest/ChestLootNet436$PoolSave.class",
            "dev/settledlands/npc/client/LootPoolGearButton440.class",
            "dev/settledlands/npc/client/LootPoolItemPickerScreen440.class",
            "dev/settledlands/npc/client/LootPoolEditorScreen440.class",
            "dev/settledlands/npc/client/LootPoolEditorScreen440$RowData.class",
            "dev/settledlands/npc/client/LootPoolEditorScreen440$RowSlot.class",
            "dev/settledlands/npc/client/LootPoolEditorScreen440$PreviewDrop.class",
            "dev/settledlands/npc/client/LootPoolCard436$TrackedDropdown.class",
            "dev/settledlands/mixin/LootPoolDropdownMixin440.class",
        )
        for cls in required:
            if cls not in names:
                problems.append("missing class: " + cls)

        mixins = json.loads(zf.read("settledlands.mixins.json").decode("utf-8"))
        client = mixins.get("client", [])
        if "LootPoolDropdownMixin440" not in client:
            problems.append("LootPoolDropdownMixin440 missing from settledlands.mixins.json client list")
        if not client or client[0] != "NpcDropdownFirst333":
            problems.append("NpcDropdownFirst333 must remain first in client mixins")

        for lp in ("assets/settledlands/lang/ru_ru.json", "assets/settledlands/lang/en_us.json"):
            table = json.loads(zf.read(lp).decode("utf-8"))
            for key in LANG_KEYS:
                if not table.get(key):
                    problems.append(f"{lp} missing or empty key: {key}")
            if "\u2264" in str(table.get("gui.settledlands.loot_pool.editor.node_caps", "")):
                problems.append(f"{lp} node_caps contains glyph-overlapping '<=' symbol")

        # Anti-blur invariant: editor & item picker override renderBackground and never call Screen#renderBackground
        for scr_cls in (
            "dev/settledlands/npc/client/LootPoolEditorScreen440.class",
            "dev/settledlands/npc/client/LootPoolItemPickerScreen440.class",
        ):
            blob = zf.read(scr_cls)
            parsed = disasm.parse_class(blob)
            cp = parsed["cp"]
            has_bg = any((cp[ni][1].decode() if isinstance(cp[ni][1], bytes) else cp[ni][1]) == "renderBackground"
                         for _a, ni, _d, _at in parsed["methods"])
            if not has_bg:
                problems.append(f"{scr_cls} must override renderBackground to disable vanilla blur")
            if "net/minecraft/client/gui/screens/Screen.renderBackground" in build438_check.method_calls(blob, "renderBackground"):
                problems.append(f"{scr_cls}#renderBackground calls super.renderBackground (would trigger blur)")

        # Dedicated server safety: common classes must never reference client GUI classes
        forbidden_client = (
            "net/minecraft/client/gui/screens/Screen",
            "net/minecraft/client/gui/GuiGraphics",
            "dev/settledlands/npc/client/LootPoolEditorScreen440",
            "dev/settledlands/npc/client/LootPoolItemPickerScreen440",
            "dev/settledlands/npc/client/LootPoolGearButton440",
        )
        for common_cls in (
            "dev/settledlands/chest/LootPool436.class",
            "dev/settledlands/chest/LootPoolManager436.class",
            "dev/settledlands/chest/ChestLootNet436.class",
        ):
            refs = _cp_class_refs(zf.read(common_cls))
            for bad in forbidden_client:
                if bad in refs:
                    problems.append(f"{common_cls} references client class {bad}")

        # Verify zero synthetic lambdas in mixins
        for mixin_cls in (
            "dev/settledlands/mixin/LootPoolInspectorMixin436.class",
            "dev/settledlands/mixin/LootPoolDropdownMixin440.class",
        ):
            parsed = disasm.parse_class(zf.read(mixin_cls))
            cp = parsed["cp"]
            for _acc, ni, _di, _attrs in parsed["methods"]:
                mname = cp[ni][1]
                if mname.startswith("lambda$"):
                    problems.append(f"{mixin_cls} declares synthetic lambda: {mname}")

        # Verify inspector places gearButton & tracks pool dropdown
        insp = zf.read("dev/settledlands/mixin/LootPoolInspectorMixin436.class")
        if b"gearButton" not in insp or b"trackPoolDropdown" not in insp:
            problems.append("LootPoolInspectorMixin436 missing gearButton or trackPoolDropdown calls")

        # Verify dropdown mixin calls renderDropdownRowGears & handleDropdownRowGearClick
        dd = zf.read("dev/settledlands/mixin/LootPoolDropdownMixin440.class")
        if b"renderDropdownRowGears" not in dd or b"handleDropdownRowGearClick" not in dd:
            problems.append("LootPoolDropdownMixin440 missing gear render/click calls")

    return problems


def compile_and_run_probes(jar: pathlib.Path):
    jar = jar.resolve()
    java = build439_check.find_java()
    ecj = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"

    with tempfile.TemporaryDirectory() as td_raw:
        td = pathlib.Path(td_raw)
        merged = td / "merged"
        stubs_out = td / "stubs_out"
        shims_out = td / "shims_out"
        probe_out = td / "probe_out"
        work_dir = td / "workdir"
        for d in (merged, stubs_out, shims_out, probe_out, work_dir):
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

        build439_check._compile_map(java, ecj, TEST_SHIMS, f"{stubs_out}:{jar}", shims_out)

        probe440 = ROOT / "tools" / "harness" / "Build440Probe.java"
        r2 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{shims_out}:{jar}:{stubs_out}", "-d", str(probe_out), str(probe440)],
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise AssertionError("Build440Probe compile failed:\n" + r2.stdout + r2.stderr)

        r3 = subprocess.run(
            [java, "-cp", f"{probe_out}:{shims_out}:{jar}:{stubs_out}", "Build440Probe"],
            cwd=str(work_dir), capture_output=True, text=True)
        if r3.returncode != 0:
            raise AssertionError(f"Build440Probe failed (rc={r3.returncode}):\n{r3.stdout}\n{r3.stderr}")
        sys.stdout.write(r3.stdout)


def main(argv):
    if len(argv) != 1:
        print("usage: build440_check.py <jar>")
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        return fail("no such jar: %s" % jar)

    base439 = ROOT / "settledlands-0.8.0-build439-furniture-3d-loot-scatter.jar"
    if base439.is_file():
        rc = invoke_kind_check.main([str(jar), "--base", str(base439)])
        if rc != 0:
            return fail("invoke_kind_check failed")

    problems = check_structural(jar, base439 if base439.is_file() else None)
    if problems:
        for p in problems:
            print("  - " + p)
        return fail("structural check failed")

    try:
        build439_check.compile_and_run_all_probes(jar)
        compile_and_run_probes(jar)
    except AssertionError as ex:
        return fail(str(ex))

    print("build440_check: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
