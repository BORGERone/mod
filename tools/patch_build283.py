#!/usr/bin/env python3
"""Build 283 - NPC animation that follows the real speed, and the value output of
the pursuit node.

Report: "NPCs animate with Touch'n Motion, but not adaptively to speed - there are
seams, torn transitions between animations" (every walking NPC), and "I expected
to pull a target output out of the pursuit node, like in Unreal: one execution
output, one target output".

1) Animation (dev/settledlands/model/NpcLocoDriver283):
   the Fresh Animations / Touch'n Motion NPC path was fed
   walkAnimation.position(partialTick) and walkAnimation.speed(partialTick) of the
   mob. That state is advanced once per tick and capped by the mob walk animation,
   so a player-shaped rig got a stepped, speed-independent input: the walk cycle
   advanced in tick steps whatever the NPC did, idle/walk weights jumped between
   ticks, and a catching-up NPC (up to 3x speed since Build 280) animated at the
   cadence of a strolling one.
   New class + three mixins:
     * NpcLocoDriver283  - integrates blend weight and limb phase at frame rate
       from the NPC's real horizontal velocity (0.08 s rise, 0.18 s fall, phase
       at 20 units/s per unit of speed, wrapped on an exact number of cycles);
     * NpcLocoSmoothMixin283 - redirects the two reads in renderFreshNpc to it;
     * NpcLocoProxyMixin283 - right after syncFrom, writes the same frame state
       into the proxy player TnM renders, so its own reads agree;
     * WalkAnimationLocoMixin283 + LocoWalkAccess283 - field access to write a
       whole WalkAnimationState at once (same three fields WalkAnimationStateMixin256
       already shadows).

2) Pursuit target output (PursuitGraphNode272 + NpcPursuitTargetPinMixin283):
   the card gains a second output, a diamond value pin under the execution port.
   Drag it to a node to link it, drag it to empty space to unlink everything.
   The link is stored in the node's own action as an extra parameter
   (`...|<keep>|<catch-up>|<boost>|@<ids>`), so it saves and loads with the graph
   with no new field, and the server reads the three numbers by index and ignores
   the rest. The wire is drawn dashed in the node colour, with a count on the card.

Usage: python3 tools/patch_build283.py <base-282.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build282-possessed-body-animation"
NEW_VERSION = "0.8.0-build283-loco-sync-and-target-pin"
OUT_NAME = "settledlands-0.8.0-build283-loco-sync-and-target-pin.jar"

# Only the pursuit card changed; the lead server controller is untouched, so the
# action keeps being parsed exactly as before.
REPLACED = (
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
)
ADDED = (
    "dev/settledlands/model/NpcLocoDriver283.class",
    "dev/settledlands/model/NpcLocoDriver283$State.class",
    "dev/settledlands/mixin/NpcLocoObserveMixin283.class",
    "dev/settledlands/mixin/NpcLocoSmoothMixin283.class",
    "dev/settledlands/mixin/NpcPursuitTargetPinMixin283.class",
)

MIXINS_JSON = "settledlands.mixins.json"
NEW_MIXINS = (
    "NpcLocoObserveMixin283",
    "NpcLocoSmoothMixin283",
    "NpcPursuitTargetPinMixin283",
)

CHANGELOG_EN = (
    "Build 283: NPC animation follows the real speed, and the pursuit node has a target output. "
    "The Fresh Animations / Touch'n Motion NPC path was fed the mob's walk animation, which is "
    "advanced once per tick and capped by the mob cycle - a player-shaped rig therefore got a "
    "stepped, speed-independent input, so walking NPCs animated at one cadence whatever they did "
    "and idle/walk/run switched in hard steps. A new frame-rate driver integrates the blend weight "
    "and the limb phase from the NPC's actual horizontal velocity (quick rise, slower settle, phase "
    "in units per second and wrapped on whole cycles), the two reads in the render bridge are "
    "redirected to it, and the proxy player Touch'n Motion renders receives the same frame state. "
    "The pursuit node also gained the second output a blueprint node has: under the execution port "
    "there is a diamond value pin that pulls the text value of what the NPC pursues (the same text "
    "%pursuit% resolves to). Drag it to any node to link it, drag it to empty space to unlink; the "
    "links are saved with the graph. "
)
PATCHINFO = (
    "Сборка 283: анимация NPC следует реальной скорости, а у ноды преследования появился выход "
    "цели. Мост Fresh Animations / Touch'n Motion получал мобовскую walk-анимацию — она идёт раз в "
    "тик и ограничена мобовским циклом, — поэтому человеческий риг получал ступенчатый, не "
    "зависящий от скорости вход: идущие NPC анимировались в одном темпе независимо от движения, а "
    "покой/ходьба/бег переключались жёсткими шагами. Новый пофреймовый драйвер интегрирует вес "
    "смешивания и фазу шага из реальной горизонтальной скорости (быстрый подъём, медленный спад, "
    "фаза в единицах в секунду и с заворотом на целые циклы), два чтения в мосте перенаправлены на "
    "него, и прокси-игрок, которого рисует Touch'n Motion, получает то же состояние кадра. У ноды "
    "преследования появился второй выход, как у ноды блюпринта: под выходом выполнения — ромб "
    "выхода значения, который вытягивает текст того, кого NPC преследует (тот же текст, что даёт "
    "%pursuit%). Тяните его на любую ноду, чтобы связать, на пустое место — чтобы снять все связи; "
    "связи сохраняются вместе с графом. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build283-src/out")
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
    for mixin in NEW_MIXINS:
        if mixin not in mixins.get("mixins", []):
            mixins["mixins"].append(mixin)
    blobs[MIXINS_JSON] = (json.dumps(mixins, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    print("  mixins.json: %d entries" % len(mixins["mixins"]))

    toml = blobs["META-INF/neoforge.mods.toml"].decode("utf-8")
    if 'version="%s"' % BASE_VERSION not in toml:
        raise SystemExit("base toml does not declare %s" % BASE_VERSION)
    toml = toml.replace('version="%s"' % BASE_VERSION, 'version="%s"' % NEW_VERSION)
    marker = "description='''"
    toml = toml.replace(marker, marker + CHANGELOG_EN + "\n\n", 1)
    blobs["META-INF/neoforge.mods.toml"] = toml.encode("utf-8")
    print("  neoforge.mods.toml: version %s, description prefix" % NEW_VERSION)

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
