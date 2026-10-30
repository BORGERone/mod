#!/usr/bin/env python3
"""Create deterministic Build 264 source/retest ZIP with pinned baseline and candidate."""
import argparse
import hashlib
import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import patch_profession_skins264 as patch

ZIP_NAME = "SettledLands-Build264-Profession-Skins-Source.zip"
BASE_NAME = patch.BASE_NAME
JAR_NAME = "settledlands-0.8.0-build264-profession-skins.jar"
EXPECTED_JAR_SHA = "f9bb1fce2ec0d42fcd4e78f24d2753a3762f729a97293c6089f08ab311b9a790"
EXCLUDED = {"GATE-RESULT-264.txt", "SHA256SUMS-build264.txt", "BACKUP-MANIFEST.sha256", ZIP_NAME}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=pathlib.Path, default=ROOT / ZIP_NAME)
    args = parser.parse_args()
    files: dict[str, bytes] = {}

    def add(path: pathlib.Path, name: str) -> None:
        assert path.is_file() and name not in files, (path, name)
        files[name] = path.read_bytes()

    for pattern in ("*.md", "*.txt"):
        for path in sorted(ROOT.glob(pattern)):
            if path.name not in EXCLUDED and not path.name.lower().endswith(".log"):
                add(path, path.name)
    for folder in ("tools", "tests", "src/build264", "assets/settledlands/dragon_default"):
        for path in sorted((ROOT / folder).rglob("*")):
            if path.is_file() and not any(part.startswith(".") or part == "__pycache__"
                                          for part in path.relative_to(ROOT).parts) \
                    and path.suffix not in (".pyc", ".pyo"):
                add(path, path.relative_to(ROOT).as_posix())
    add(ROOT / BASE_NAME, BASE_NAME)
    add(ROOT / JAR_NAME, JAR_NAME)

    assert digest(files[BASE_NAME]) == patch.BASE_SHA
    assert digest(files[JAR_NAME]) == EXPECTED_JAR_SHA
    required = {
        "START-HERE-RU.md", "HANDOFF-NEXT-SESSION-RU.md", "AI-GUIDE-SettledLands.md",
        "README-Build264-Profession-Skins-RU.txt", "CHECKLIST-TEST-BUILD264-RU.md",
        "BACKUP-264-RU.txt", "tests/gate_build264.py",
        "tests/ProfessionModelProbe264.java",
        "tests/fixture264/dev/settledlands/model/BbModelStorage.java",
        "tools/patch_profession_skins264.py", "tools/make_backup264.py",
        "src/build264/dev/settledlands/npc/NpcStatsGraph.java",
        "src/build264/dev/settledlands/npc/NpcProfessionSkin264.java",
        "src/build264/dev/settledlands/npc/client/NpcStatsGraphUi.java",
        "src/build264/dev/settledlands/npc/client/NpcProfessionModel264.java",
        "src/build264/dev/settledlands/mixin/GuideNpcProfessionSkinMixin264.java",
        "src/build264/dev/settledlands/mixin/NpcProfessionModelStorageMixin264.java",
        "assets/settledlands/dragon_default/ivy_oleander_professions.bbmodel",
        BASE_NAME, JAR_NAME,
    }
    assert required <= files.keys(), required - files.keys()
    assert not any(name.lower().endswith(".log") for name in files)
    files["BACKUP-MANIFEST.sha256"] = "".join(
        digest(files[name]) + "  " + name + "\n" for name in sorted(files)
    ).encode("utf-8")

    out = args.out.resolve()
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, "w") as archive:
        for name in sorted(files):
            info = zipfile.ZipInfo(name, (2026, 10, 30, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, files[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(out) as archive:
        assert archive.testzip() is None and len(archive.namelist()) == len(files)
    print(f"{out}: {out.stat().st_size} bytes sha256={digest(out.read_bytes())}; files={len(files)}")


if __name__ == "__main__":
    main()
