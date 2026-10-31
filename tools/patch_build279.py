#!/usr/bin/env python3
"""Build 279 - the pursuit node keeps its distance, and catches up like the escort node.

Feedback on 278:
  * "the NPC walks right up to the player, point-blank" - the pursuit controller
    stopped the navigation 1.25 blocks from a formation point that itself sits
    1.4 blocks ahead of the target, and it never cleared the move control or the
    leftover velocity, so the NPC kept sliding into the player between repaths;
  * "if the player runs far away the NPC must catch up, like the escort node";
  * "the node needs settings for how close the NPC comes".

Repair (the pursuit controller is rebuilt from the real Build 271 source, whose
recompiled bytecode is identical to the shipped class - same code_len for all 17
methods - so this is a source-side patch, not a rewrite of unknown code):

  * NpcPursuit269: the standoff is measured to the target itself and a stop now
    runs NpcEscort#stopMotion (navigation stop + zeroed move-control target +
    zeroed xxa/yya/zza + horizontal velocity cleared);
  * NpcPursuit269: catch-up is the escort mechanic - above the configured
    distance the NPC uses GuideNpc#walkingNavigationSpeed(true) with a 4-tick
    repath instead of 10, and points straight at the target; released at the
    escort threshold (3 blocks). The formation slot now sits behind the target
    instead of in front of it;
  * both numbers are written into the node action after a separator:
    trigger:pursuit:start:<npc|player>:<target>|<keep>|<catch-up>, so they are
    saved with the graph and need no new node field; old actions (278 and
    earlier) keep the defaults 1.6 / 8.0 blocks;
  * PursuitGraphNode272: reads/writes those two numbers, the card gets a
    "Distance / Catch up" line;
  * NpcPursuitInspectorMixin277: two more rows - standoff and catch-up distance -
    as the screen's own double fields (real editing, clamped and validated);
  * lang: gui.settledlands.pursuit.keep_distance, gui.settledlands.pursuit.catch_up.

Usage: python3 tools/patch_build279.py <base-278.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build278-pursuit-card"
NEW_VERSION = "0.8.0-build279-pursuit-distance"
OUT_NAME = "settledlands-0.8.0-build279-pursuit-distance.jar"

REPLACED = (
    "dev/settledlands/npc/NpcPursuit269.class",
    "dev/settledlands/npc/NpcPursuit269$State.class",
    "dev/settledlands/npc/NpcPursuit269$TargetCache.class",
    "dev/settledlands/npc/NpcPursuit269$SlotCache.class",
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
    "dev/settledlands/mixin/NpcPursuitInspectorMixin277.class",
)
LANG = "assets/settledlands/lang/%s.json"
LANG_SET = {
    "ru_ru": {
        "gui.settledlands.pursuit.keep_distance": "Дистанция:",
        "gui.settledlands.pursuit.catch_up": "Догонять с:",
    },
    "en_us": {
        "gui.settledlands.pursuit.keep_distance": "Distance:",
        "gui.settledlands.pursuit.catch_up": "Catch up from:",
    },
}

CHANGELOG_EN = (
    "Build 279: the pursuit node keeps its distance and catches up like the escort node. "
    "The NPC no longer walks into the player: the standoff is measured to the target itself, "
    "and a stop clears the navigation, the move control and the leftover velocity "
    "(NpcEscort#stopMotion), so the last path cannot push the body into the player any more. "
    "A target that runs away is chased in the escort catch-up mode - the fast navigation speed "
    "and a 4-tick repath instead of 10, released at 3 blocks - and a pursuer now heads for a point on the standoff "
    "ring on its own bearing instead of a formation point in front of the target, which it could "
    "only reach by walking into the player. The node has two settings of its own: how close "
    "the NPC may come (1.0-8.0 blocks, default 1.6) and from what distance it starts catching up "
    "(2-48 blocks, default 8). Both are stored in the action itself "
    "(trigger:pursuit:start:npc:<target>|<distance>|<catch-up>) and therefore saved with the graph; "
    "nodes made by older builds keep the defaults. "
)
PATCHINFO = (
    "Сборка 279: нода преследования держит дистанцию и догоняет цель так же, как нода сопровождения. "
    "NPC больше не подходит вплотную: дистанция считается до самой цели, а остановка гасит навигацию, "
    "move control и остаточную скорость (NpcEscort#stopMotion), поэтому последний путь не толкает тело "
    "в игрока. Убегающую цель NPC догоняет в режиме догона сопровождения — повышенная скорость и "
    "перестроение пути каждые 4 тика вместо 10, выход из догона на 3 блоках; преследователь идёт "
    "к точке на кольце дистанции по своему направлению, а не к точке строя перед целью, до которой "
    "можно было дойти только сквозь игрока. У ноды две своих настройки: насколько близко можно подойти "
    "(1.0–8.0 блока, по умолчанию 1.6) и с какого расстояния начинать догон (2–48 блоков, "
    "по умолчанию 8). Обе хранятся в самом действии "
    "(trigger:pursuit:start:npc:<цель>|<дистанция>|<догон>) и сохраняются вместе с графом; "
    "ноды прошлых сборок получают значения по умолчанию. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build279-src/out")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    classes = pathlib.Path(args.classes)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

    for name in REPLACED:
        src = classes / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        blobs[name] = src.read_bytes()
        print("  replaced: %s" % name)

    toml = blobs["META-INF/neoforge.mods.toml"].decode("utf-8")
    if 'version="%s"' % BASE_VERSION not in toml:
        raise SystemExit("base toml does not declare %s" % BASE_VERSION)
    toml = toml.replace('version="%s"' % BASE_VERSION, 'version="%s"' % NEW_VERSION)
    marker = "description='''"
    toml = toml.replace(marker, marker + CHANGELOG_EN + "\n\n", 1)
    blobs["META-INF/neoforge.mods.toml"] = toml.encode("utf-8")
    print("  neoforge.mods.toml: version %s, description prefix" % NEW_VERSION)

    for locale, values in LANG_SET.items():
        name = LANG % locale
        data = json.loads(blobs[name].decode("utf-8"))
        added = 0
        for key, value in values.items():
            if key not in data:
                added += 1
            data[key] = value
        blobs[name] = (json.dumps(data, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        print("  lang %s: %d key(s)" % (locale, added))

    if "settledlands-patch-info.txt" in blobs:
        blobs["settledlands-patch-info.txt"] = (PATCHINFO + "\n").encode("utf-8")

    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in order:
            zi = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, blobs[name])

    digest = hashlib.sha256(out_path.read_bytes()).hexdigest()
    (outdir / (OUT_NAME + ".sha256")).write_text("%s  %s\n" % (digest, OUT_NAME))
    print("wrote %s" % out_path)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
