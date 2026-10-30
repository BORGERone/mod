#!/usr/bin/env python3
"""Deterministic and JVM-fixture gate for Build 269 pursuit, from confirmed Build 268."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import tomllib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from disasm import parse_class
import patch_pursuit269 as build

JAVA = ROOT / ".venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java"
BASE = ROOT / build.BASE_NAME
JAR = ROOT / "settledlands-0.8.0-build269-pursuit.jar"


def run(args: list[str], cwd: pathlib.Path = ROOT) -> str:
    result = subprocess.run(args, cwd=cwd, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(f"failed: {' '.join(map(str,args))}\n{result.stdout}\n{result.stderr}")
    return result.stdout


def api_files() -> list[pathlib.Path]:
    return [
        *sorted((ROOT / "tools/api269/org/spongepowered").rglob("*.java")),
        ROOT / "tools/api269/net/minecraft/nbt/CompoundTag.java",
        ROOT / "tools/api269/net/minecraft/nbt/ListTag.java",
        ROOT / "tools/api269/net/minecraft/client/gui/Font.java",
        ROOT / "tools/api269/net/minecraft/client/gui/GuiGraphics.java",
        ROOT / "tools/api269/net/minecraft/network/chat/Component.java",
        ROOT / "tools/api269/net/minecraft/network/chat/MutableComponent.java",
        ROOT / "tools/api269/com/mojang/blaze3d/vertex/PoseStack.java",
        ROOT / "tools/api269/net/minecraft/world/entity/Entity.java",
        ROOT / "tools/api269/net/minecraft/world/entity/LivingEntity.java",
        ROOT / "tools/api269/net/minecraft/world/entity/PathfinderMob.java",
        ROOT / "tools/api269/net/minecraft/world/entity/ai/navigation/PathNavigation.java",
        ROOT / "tools/api269/net/minecraft/world/level/Level.java",
        ROOT / "tools/api269/net/minecraft/server/MinecraftServer.java",
        ROOT / "tools/api269/net/minecraft/server/level/ServerLevel.java",
        ROOT / "tools/api269/net/minecraft/server/level/ServerPlayer.java",
        ROOT / "tools/api269/net/minecraft/world/phys/Vec3.java",
        ROOT / "tools/api269/net/minecraft/resources/ResourceKey.java",
        ROOT / "tools/api269/net/minecraft/resources/ResourceLocation.java",
    ]


def expected_diff() -> None:
    run([sys.executable, "tools/verify_jar.py", str(JAR), "--base", str(BASE),
         *sum((["--expect-changed", name] for name in sorted(build.CHANGED)), []),
         *sum((["--expect-added", name] for name in sorted(build.ADDED)), [])])


def verify_metadata() -> None:
    with zipfile.ZipFile(JAR) as jar:
        assert jar.testzip() is None
        info = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode("utf-8"))
        assert info["mods"][0]["version"] == build.NEW_VERSION
        mixins = json.loads(jar.read("settledlands.mixins.json").decode("utf-8"))
        assert "NpcPursuitActionMixin269" in mixins["mixins"]
        assert "NpcPursuitAiMixin269" in mixins["mixins"]
        assert "NpcPursuitGraphUiMixin269" in mixins["client"]
        assert "ScreenAccessorPursuit269" in mixins["client"]
        for locale in ("ru_ru", "en_us"):
            data = json.loads(jar.read(f"assets/settledlands/lang/{locale}.json").decode("utf-8"))
            for key in ("pursuit_commands", "pursuit_start_npc", "pursuit_start_player", "pursuit_stop"):
                assert f"gui.settledlands.graph.{key}" in data
        names = jar.namelist()
        assert not any("CutsceneFade" in name or "CutsceneFade" in name for name in names)
        assert "assets/settledlands/dragon_default/ivy_oleander_professions.bbmodel" in names

        checks = {
            "dev/settledlands/mixin/NpcPursuitActionMixin269.class": "settledlands$runPursuitAction",
            "dev/settledlands/mixin/NpcPursuitAiMixin269.class": "settledlands$tickPursuit",
            "dev/settledlands/mixin/NpcPursuitGraphUiMixin269.class": "settledlands$addPursuitCommands",
        }
        for class_path, handler_name in checks.items():
            parsed = parse_class(jar.read(class_path))
            cp = parsed["cp"]
            method = next(method for method in parsed["methods"] if cp[method[1]][1] == handler_name)
            attrs = {cp[index][1] for index, _ in method[3]}
            assert "RuntimeVisibleAnnotations" in attrs, (class_path, attrs)
        action_class = parse_class(jar.read("dev/settledlands/mixin/NpcPursuitActionMixin269.class"))
        cp = action_class["cp"]
        entity_handler = next(method for method in action_class["methods"]
                              if cp[method[1]][1] == "settledlands$runPursuitActionForEntity")
        attrs = {cp[index][1] for index, _ in entity_handler[3]}
        assert "RuntimeVisibleAnnotations" in attrs, attrs
        accessor = parse_class(jar.read("dev/settledlands/mixin/ScreenAccessorPursuit269.class"))
        cp = accessor["cp"]
        method = next(method for method in accessor["methods"] if cp[method[1]][1] == "settledlands$addRenderableWidget")
        attrs = {cp[index][1] for index, _ in method[3]}
        assert "RuntimeInvisibleAnnotations" in attrs, attrs


def run_formation_fixture() -> None:
    ecj = ROOT / "tools/vendor/ecj-3.46.100.jar"
    with tempfile.TemporaryDirectory(prefix="pursuit269-fixture-") as td:
        work = pathlib.Path(td)
        api_out, test_out = work / "api", work / "test"
        api_out.mkdir(); test_out.mkdir()
        run([str(JAVA), "-jar", str(ecj), "-21", "-encoding", "UTF-8", "-g:none", "-nowarn",
             "-cp", str(JAR), "-d", str(api_out), *map(str, api_files())])
        run([str(JAVA), "-jar", str(ecj), "-21", "-encoding", "UTF-8", "-g:none", "-nowarn",
             "-cp", os.pathsep.join((str(api_out), str(JAR))), "-d", str(test_out),
             str(ROOT / "tests/java269/PursuitFormationProbe269.java")])
        output = run([str(JAVA), "-cp", os.pathsep.join((str(test_out), str(api_out), str(JAR))),
                      "PursuitFormationProbe269"])
        assert "PURSUIT 269 FIXTURE: PASS" in output, output


def runtime_link_gate() -> None:
    targets = [
        "dev/settledlands/npc/NpcPursuit269#isPursuitAction(java.lang.String)",
        "dev/settledlands/npc/NpcPursuit269#tick(dev.settledlands.npc.GuideNpc)",
        "dev/settledlands/npc/NpcPursuit269#saveNpcData(dev.settledlands.npc.GuideNpc,net.minecraft.nbt.CompoundTag)",
        "dev/settledlands/npc/NpcPursuit269#loadNpcData(dev.settledlands.npc.GuideNpc,net.minecraft.nbt.CompoundTag)",
        "dev/settledlands/mixin/NpcPursuitActionMixin269#settledlands$runPursuitAction(net.minecraft.server.level.ServerPlayer,dev.settledlands.npc.GuideNpc,int,java.lang.String,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable)",
        "dev/settledlands/mixin/NpcPursuitActionMixin269#settledlands$runPursuitActionForEntity(net.minecraft.world.entity.Entity,dev.settledlands.npc.GuideNpc,int,java.lang.String,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable)",
        "dev/settledlands/mixin/NpcPursuitAiMixin269#settledlands$tickPursuit(dev.settledlands.npc.GuideNpc,org.spongepowered.asm.mixin.injection.callback.CallbackInfo)",
    ]
    output = run([sys.executable, "tools/jvm_verify.py", str(JAR), *targets, "--java", str(JAVA)])
    for target in targets:
        token = target.split("(", 1)[0]
        assert "DECLARED " + token in output, target
        if "NpcPursuitActionMixin269#" not in token:
            assert "LINKED " + token in output, target


def main() -> None:
    global JAVA
    parser = argparse.ArgumentParser()
    parser.add_argument("--java", type=pathlib.Path, default=JAVA)
    JAVA = parser.parse_args().java.resolve()
    assert JAVA.is_file() and BASE.is_file() and JAR.is_file()
    assert hashlib.sha256(BASE.read_bytes()).hexdigest() == build.BASE_SHA
    with tempfile.TemporaryDirectory(prefix="pursuit269-rebuild-") as td:
        rebuilt = pathlib.Path(td) / JAR.name
        run([sys.executable, "tools/patch_pursuit269.py", "--base", str(BASE), "--out", str(rebuilt), "--java", str(JAVA)])
        assert rebuilt.read_bytes() == JAR.read_bytes(), "Build269 is not deterministic"
    run([sys.executable, "tools/validate_jar.py", str(JAR), "--base", str(BASE)])
    expected_diff()
    verify_metadata()
    runtime_link_gate()
    run_formation_fixture()
    print("BUILD 269 PURSUIT GATE: PASS (deterministic rebuild, Build268-preserving exact diff, JAR/TOML/mixins/locales, JVM links, command parsing and row-formation fixture)")
    print("Minecraft/NeoForge was not launched; graph UI and live pathfinding still require a user test on a copied world.")


if __name__ == "__main__":
    main()
