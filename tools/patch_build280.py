#!/usr/bin/env python3
"""Build 280 - the pursuit NPC really catches up, and the node outputs its target.

Feedback on 279:
  * "the NPC does not speed up when I walk away" - Build 279 rebuilt the path every
    4 ticks while catching up, and every moveTo() restarts the path, so the NPC was
    stopped and restarted all the time and never got up to the catch-up speed;
  * "I want the node to output a 'Target' parameter like in Unreal" - the nick of
    the player (or the identifier of the NPC) this node is bound to.

Repair (NpcPursuit269 is still the real Build 271 source):
  * the path is kept until it is finished, exactly like NpcEscort: repath when
    navigation.isDone(), when the target moved 2 blocks from the snapshot while
    catching up (EscortRules.CATCH_UP_TARGET_MOVED_SQ), or through the escort
    progress watchdog (no movement of half a block in 100 ticks) with the escort
    retry backoff (20/40/80/160 ticks);
  * catch-up speed = GuideNpc#walkingNavigationSpeed(true) x the node's boost,
    ramped in over the distance above the catch-up threshold, released at 3 blocks;
    the boost is a new node setting (1.0-3.0, default 1.5);
  * when the target is more than 24 blocks away and cannot see the NPC, the NPC is
    moved to a stable spot 8 blocks from it (cooldown 20 ticks), so it never stays
    lost behind;
  * the node publishes its target for the nodes after it: %pursuit%, %pursuit_kind%
    and %pursuit_uuid%, resolved in NpcProfile#fill - one new mixin covers both
    dialogue lines and trigger actions/commands.

Usage: python3 tools/patch_build280.py <base-279.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build279-pursuit-distance"
NEW_VERSION = "0.8.0-build280-pursuit-catchup"
OUT_NAME = "settledlands-0.8.0-build280-pursuit-catchup.jar"

REPLACED = (
    "dev/settledlands/npc/NpcPursuit269.class",
    "dev/settledlands/npc/NpcPursuit269$State.class",
    "dev/settledlands/npc/NpcPursuit269$TargetCache.class",
    "dev/settledlands/npc/NpcPursuit269$SlotCache.class",
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
    "dev/settledlands/mixin/NpcPursuitInspectorMixin277.class",
)
ADDED = (
    "dev/settledlands/npc/NpcPursuit269$Published.class",
    "dev/settledlands/mixin/NpcPursuitOutputMixin280.class",
)
MIXINS_JSON = "settledlands.mixins.json"
NEW_MIXIN = "NpcPursuitOutputMixin280"
LANG = "assets/settledlands/lang/%s.json"
LANG_SET = {
    "ru_ru": {
        "gui.settledlands.pursuit.catch_up_speed": "Ускорение догона:",
        "gui.settledlands.pursuit.output": "Цель",
        "gui.settledlands.pursuit.output_row": "Выход: Цель → %pursuit%",
    },
    "en_us": {
        "gui.settledlands.pursuit.catch_up_speed": "Catch-up speed:",
        "gui.settledlands.pursuit.output": "Target",
        "gui.settledlands.pursuit.output_row": "Output: Target → %pursuit%",
    },
}

CHANGELOG_EN = (
    "Build 280: the pursuit NPC now really catches up, and the node outputs its target. "
    "Build 279 rebuilt the path every 4 ticks while catching up and every rebuild restarts the "
    "path, so the NPC kept stopping and never reached the catch-up speed. The path is now kept "
    "until it is finished, exactly like the escort node: it is rebuilt when the path ends, when "
    "the target moved two blocks away from the snapshot, or by the escort progress watchdog "
    "(no movement of half a block in five seconds) with the escort retry backoff. Catch-up speed "
    "is the escort speed times a new node setting, Catch-up speed (1.0-3.0, default 1.5), ramped "
    "in over the distance above the catch-up threshold; the formation point is on the standoff "
    "ring on the pursuer's own bearing. When the target is farther than 24 blocks and cannot see "
    "the NPC, the NPC is moved to a stable spot 8 blocks from it, so it is never left behind for "
    "good. The node also outputs what it is bound to - %pursuit%, %pursuit_kind% and "
    "%pursuit_uuid% - printed on the card and resolved in the text and actions of every node "
    "that runs after it; the inspector shows the output row. "
)
PATCHINFO = (
    "Сборка 280: NPC преследования действительно догоняет, а нода выводит свою цель. "
    "В 279 путь перестраивался каждые 4 тика в режиме догона, а каждый пересчёт начинает путь "
    "заново — NPC всё время останавливался и не успевал разогнаться. Теперь путь держится до "
    "конца, как в ноде сопровождения: пересчёт — когда путь закончился, когда цель отошла на два "
    "блока от снимка, или по сторожу прогресса сопровождения (нет движения на полблока за пять "
    "секунд) с тем же откатом попыток. Скорость догона — скорость сопровождения, умноженная на "
    "новую настройку ноды «Ускорение догона» (1.0–3.0, по умолчанию 1.5), нарастающая по мере "
    "увеличения разрыва; точка подхода лежит на кольце дистанции по направлению самого "
    "преследователя. Если цель дальше 24 блоков и не видит NPC, NPC переносится на устойчивое "
    "место в 8 блоках от неё — так он не отстаёт навсегда. Ещё нода выводит то, к чему "
    "привязана: %pursuit%, %pursuit_kind% и %pursuit_uuid% — видно на карточке и подставляется "
    "в текст и действия всех нод, которые выполняются после неё; в инспекторе есть строка выхода. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build280-src/out")
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
        if name not in blobs:
            raise SystemExit("class is not in the base jar: %s" % name)
        blobs[name] = src.read_bytes()
        print("  replaced: %s" % name)

    for name in ADDED:
        src = classes / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        if name in blobs:
            raise SystemExit("class already in the base jar: %s" % name)
        blobs[name] = src.read_bytes()
        order.append(name)
        print("  added: %s" % name)

    mixins = json.loads(blobs[MIXINS_JSON].decode("utf-8"))
    if NEW_MIXIN not in mixins.get("mixins", []):
        mixins["mixins"].append(NEW_MIXIN)
        blobs[MIXINS_JSON] = (json.dumps(mixins, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    print("  mixins.json: %s" % NEW_MIXIN)

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
