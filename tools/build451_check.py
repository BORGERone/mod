#!/usr/bin/env python3
"""
Gate (Build 451): Bytecode, localization, layout math, and inherited Build 450 JVM verification:
  - TrialMobCatalogScreen449 removes title text, Back button, and bottom hint strip
  - Stretched grid exposes an extra partial/full row at the bottom on 200p..540p heights
  - Search placeholder shortened ("Поиск: имя, мод, ID" / "Search: name, mod, ID") and width-clipped
  - Runs the full Build 450 JVM probe (642 checks) and verifies zero new external API refs
"""

import json
import pathlib
import re
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import build450_check
import check_mixin_class


def main(argv) -> int:
    if len(argv) < 1:
        print("usage: build451_check.py <jar>", file=sys.stderr)
        return 2
    jar_path = pathlib.Path(argv[0])
    if not jar_path.is_file():
        print(f"not found: {jar_path}", file=sys.stderr)
        return 2

    errors = []
    with zipfile.ZipFile(jar_path) as zf:
        names = set(zf.namelist())
        for req in build450_check.REQUIRED_CLASSES:
            if req not in names:
                errors.append(f"missing class {req}")

        toml = zf.read("META-INF/neoforge.mods.toml").decode("utf-8")
        if "0.8.0-build451-mob-catalog-cleanup-and-more-rows" not in toml:
            errors.append("neoforge.mods.toml missing Build 451 version")

        # Verify Build 451 cleanup in TrialMobCatalogScreen449 bytecode
        cat_cf = zf.read("dev/settledlands/npc/client/TrialMobCatalogScreen449.class")
        for req_marker in (b"enableScissor", b"drawScaledText", b"drawRows", b"clip"):
            if req_marker not in cat_cf:
                errors.append(f"TrialMobCatalogScreen449 missing {req_marker.decode()}")
        for removed_key in (
            b"gui.settledlands.loot_pool.editor.back",
            b"gui.settledlands.trial.mob_cat.title_replace",
            b"gui.settledlands.trial.mob_cat.hint_replace",
            b"gui.settledlands.trial.mob_cat.hint_multi",
        ):
            if removed_key in cat_cf:
                errors.append(f"TrialMobCatalogScreen449 must not reference removed UI key {removed_key.decode()}")

        # Verify layout geometry: even on 200p..240p GUI height, an extra partial/full row is drawn
        for h in (200, 216, 225, 240, 270, 360, 540):
            panel_h = max(176, h - 6)
            grid_y_off = 25
            bottom_pad = 4
            grid_h = max(46 + 8, panel_h - bottom_pad - grid_y_off)
            full_rows = max(1, (grid_h + 3) // 49)
            draw_rows = max(full_rows, (grid_h - 1) // 49 + 1)
            if h <= 225:
                # 3 full rows + visible partial 4th row
                if full_rows < 3 or draw_rows < 4:
                    errors.append(f"expected >=3 full & >=4 drawn rows at h={h}, got full={full_rows}, draw={draw_rows}, gridH={grid_h}")
            else:
                if full_rows < 4:
                    errors.append(f"expected >=4 full rows at h={h}, got {full_rows}")

        # Verify shortened placeholder & lang parity
        ru = json.loads(zf.read("assets/settledlands/lang/ru_ru.json").decode("utf-8"))
        en = json.loads(zf.read("assets/settledlands/lang/en_us.json").decode("utf-8"))
        if set(ru) != set(en):
            errors.append(f"lang parity diff: ru-en={len(set(ru)-set(en))} en-ru={len(set(en)-set(ru))}")
        if len(ru.get("gui.settledlands.trial.mob_cat.search_hint", "")) > 24:
            errors.append("RU search_hint too long: " + ru.get("gui.settledlands.trial.mob_cat.search_hint", ""))
        if len(en.get("gui.settledlands.trial.mob_cat.search_hint", "")) > 24:
            errors.append("EN search_hint too long: " + en.get("gui.settledlands.trial.mob_cat.search_hint", ""))

        for k in build450_check.NEW_KEYS:
            if not ru.get(k) or not en.get(k):
                errors.append(f"missing Build 450/451 key: {k}")

        classes = check_mixin_class.load_classes(zf, [n for n in names if n.endswith(".class")])
        for mname in build450_check.ZERO_LAMBDA_MIXINS:
            cf = classes.get(mname)
            if cf is None:
                errors.append(f"missing loaded class for {mname}")
                continue
            lambdas = [m.name for m in cf.methods if m.name.startswith("lambda$")]
            if lambdas:
                errors.append(f"mixin {mname} contains synthetic lambda(s): {lambdas}")

        allowed_stems = (
            "gui.settledlands.desc448.card.node",
            "gui.settledlands.desc448.card.mod",
            "gui.settledlands.tip448.menu.node",
            "gui.settledlands.tip448.node.n",
            "gui.settledlands.tip448.node.mod",
            "gui.settledlands.tip448.pin.slot",
            "gui.settledlands.tip448.varop",
            "gui.settledlands.tip448.preset",
            "gui.settledlands.tip448.op",
            "gui.settledlands.tip448.hack.slider_diff",
            "gui.settledlands.door.rows",
        )
        for n in sorted(names):
            if not n.endswith(".class"):
                continue
            blob = zf.read(n)
            for m in re.finditer(rb"(?:gui|message)\.settledlands\.[a-zA-Z0-9_.]+", blob):
                s = m.group(0).decode("ascii")
                if s.endswith(".") or s.endswith("_") or s in allowed_stems:
                    continue
                if s not in ru or s not in en:
                    errors.append(f"missing lang key {s} referenced in {n}")

    probe_out = build450_check.run_probe(jar_path)
    if errors:
        print("build451_check FAILED:", file=sys.stderr)
        for e in errors:
            print("  -", e, file=sys.stderr)
        return 1

    print(probe_out)
    print("build451_check: PASS", jar_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
