#!/usr/bin/env python3
"""Build and validate Build 266 profession diagnostic candidate from the pinned Build 262 base."""
import argparse
import hashlib
import json
import pathlib
import struct
import subprocess
import sys
import tempfile
import tomllib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import patch_profession_probe266 as patch

DEFAULT_JAVA = ROOT / ".venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java"


def run(args: list[str]) -> str:
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(f"command failed ({result.returncode}): {' '.join(args)}\n{result.stdout}\n{result.stderr}")
    return result.stdout


def check_model(jar: pathlib.Path) -> None:
    with zipfile.ZipFile(jar) as z:
        asset = z.read(patch.MODEL_ASSET)
        model = json.loads(asset)
        assert model.get("meta", {}).get("model_format") == "bedrock"
        assert len(model.get("elements", [])) > 0 and len(model.get("outliner", [])) > 0
        textures = model.get("textures", [])
        assert len(textures) >= 2
        names = set()
        ids = set()
        for texture in textures:
            name, ident, source = texture.get("name"), texture.get("id"), texture.get("source", "")
            assert name and ident and name not in names and ident not in ids
            names.add(name)
            ids.add(ident)
            assert source.startswith("data:image/png;base64,")
            import base64
            png = base64.b64decode(source.partition(",")[2], validate=True)
            assert png[:8] == b"\x89PNG\r\n\x1a\n"
            width, height = struct.unpack(">II", png[16:24])
            assert width == texture.get("width") and height == texture.get("height")
            assert width == 128 and height == 128
        assert "ivy_oleander.png" in names
        mapping_source = (ROOT / "src/build266/dev/settledlands/npc/NpcProfessionSkin264.java").read_text()
        for texture in __import__("re").findall(r'"([A-Za-z0-9_]+\.png)"', mapping_source):
            assert texture in names, f"mapped texture missing from model: {texture}"
        assert hashlib.sha256(asset).hexdigest() == patch.MODEL_SHA


def check_serialization() -> None:
    node = run([sys.executable, "tools/disasm.py", patch.BASE_NAME,
                "--class", "dev/settledlands/npc/NpcDialogueGraph$Node.class", "--method", "copy"])
    save = run([sys.executable, "tools/disasm.py", patch.BASE_NAME,
                "--class", "dev/settledlands/npc/NpcDialogueGraph.class", "--method", "save"])
    load = run([sys.executable, "tools/disasm.py", patch.BASE_NAME,
                "--class", "dev/settledlands/npc/NpcDialogueGraph.class", "--method", "load"])
    for field in ("damageFilter", "minDamage"):
        assert field in node, field
    for key in ("DamageFilter", "MinDamage"):
        assert key in save and key in load, key


def check_java_model_materializer(jar: pathlib.Path, java: pathlib.Path) -> None:
    with zipfile.ZipFile(jar) as z:
        model = json.loads(z.read(patch.MODEL_ASSET))
    textures = {texture["name"]: texture["source"] for texture in model["textures"]}
    with tempfile.TemporaryDirectory(prefix="sl264-model-probe-") as td:
        work = pathlib.Path(td)
        classes, models = work / "classes", work / "models"
        classes.mkdir()
        models.mkdir()
        ecj = ROOT / "tools/vendor/ecj-3.46.100.jar"
        run([str(java), "-jar", str(ecj), "-21", "-cp", str(jar), "-d", str(classes),
             str(ROOT / "tests/fixture264/dev/settledlands/model/BbModelStorage.java"),
             str(ROOT / "tests/ProfessionModelProbe264.java")])
        output = run([str(java), f"-Dsettledlands.test.models={models}", "-cp",
                      str(classes) + ":" + str(jar), "ProfessionModelProbe264"])
        rows = output.strip().splitlines()
        assert len(rows) == 64, f"expected 64 profession variants, got {len(rows)}"
        for row in rows:
            profession, male, japanese, texture_name, digest = row.split(",")
            assert texture_name in textures, (profession, male, japanese, texture_name)
            expected = hashlib.sha256(textures[texture_name].encode("utf-8")).hexdigest()
            assert digest == expected, f"wrong embedded texture for {row}"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--java", type=pathlib.Path, default=DEFAULT_JAVA)
    parser.add_argument("--base", type=pathlib.Path, default=ROOT / patch.BASE_NAME)
    parser.add_argument("--jar", type=pathlib.Path, default=ROOT / "settledlands-0.8.0-build266-profession-probe.jar")
    args = parser.parse_args()
    base, jar, java = (p.resolve() for p in (args.base, args.jar, args.java))
    assert java.is_file() and base.is_file() and jar.is_file()
    assert hashlib.sha256(base.read_bytes()).hexdigest() == patch.BASE_SHA
    assert hashlib.sha256(patch.MODEL_SOURCE.read_bytes()).hexdigest() == patch.MODEL_SHA

    with tempfile.TemporaryDirectory(prefix="sl264-rebuild-") as td:
        rebuilt = pathlib.Path(td) / jar.name
        run([sys.executable, "tools/patch_profession_probe266.py", "--base", str(base),
             "--out", str(rebuilt), "--java", str(java)])
        assert rebuilt.read_bytes() == jar.read_bytes(), "candidate is not reproducible"

    run([sys.executable, "tools/validate_jar.py", str(jar), "--base", str(base)])
    verify = [sys.executable, "tools/verify_jar.py", str(jar), "--base", str(base)]
    for name in sorted(patch.CHANGED):
        verify += ["--expect-changed", name]
    for name in sorted(patch.ADDED):
        verify += ["--expect-added", name]
    run(verify)

    with zipfile.ZipFile(jar) as z:
        assert z.testzip() is None
        info = tomllib.loads(z.read("META-INF/neoforge.mods.toml").decode("utf-8"))
        assert info["mods"][0]["version"] == patch.NEW_VERSION
        for locale in ("ru_ru", "en_us"):
            strings = json.loads(z.read(f"assets/settledlands/lang/{locale}.json"))
            assert strings["gui.settledlands.stats.male"] == ("муж" if locale == "ru_ru" else "Male")
            assert strings["gui.settledlands.stats.japanese"] == ("яп" if locale == "ru_ru" else "JP")
            for index in range(17):
                assert f"gui.settledlands.stats.profession.{index}" in strings
    check_serialization()
    ui_source = (ROOT / "src/build266/dev/settledlands/npc/client/NpcStatsGraphUi.java").read_text()
    skin_source = (ROOT / "src/build266/dev/settledlands/npc/NpcProfessionSkin264.java").read_text()
    assert "NpcUi.dropdown" in ui_source and "popup.popupClicked(x, y)" in ui_source
    common_probe = (ROOT / "src/build266/dev/settledlands/mixin/GuideNpcProfessionSkinMixin264.java").read_text()
    storage_probe = (ROOT / "src/build266/dev/settledlands/mixin/NpcProfessionModelStorageMixin264.java").read_text()
    assert "[SL-PROFESSION-266] apply" in common_probe
    assert "[SL-PROFESSION-266] resolve" in storage_probe
    assert " + \"  ▾\"" in ui_source and "nx < PROFESSION_X + 28" not in ui_source
    profile_method = skin_source.split("public static String modelName(NpcProfile profile)", 1)[1]
    assert "profile.texture" not in profile_method and "profile.dialogueGraph" in profile_method
    run([sys.executable, "tools/jvm_verify.py", str(jar),
         "dev/settledlands/npc/client/NpcStatsGraphUi#mouseClicked(dev.settledlands.npc.client.NpcDialogueGraphScreen,double,double,int)",
         "dev/settledlands/npc/NpcProfessionSkin264#modelName(dev.settledlands.npc.NpcProfile)", "--java", str(java)])
    check_model(jar)
    check_java_model_materializer(jar, java)
    print("BUILD 266 PROBE GATE: PASS (deterministic build, JAR/diff, dropdown plumbing, settings copy+NBT fields, model schema/PNG, 64 exact texture selections, event-only diagnostic traces)")
    print("Minecraft/NeoForge was not launched; this probe has not been visually verified in-game.")


if __name__ == "__main__":
    main()
