#!/usr/bin/env python3
"""Build 281 - the crash when the pursued target runs far away.

Build 280 called Entity#hasLineOfSight(Entity) in the catch-up relocation. Entity
has no such method (LivingEntity does), so the server died the first time a target
got farther than 24 blocks:

    java.lang.NoSuchMethodError: 'boolean net.minecraft.world.entity.Entity.
                                    hasLineOfSight(net.minecraft.world.entity.Entity)'
        at dev.settledlands.npc.NpcPursuit269.tick(NpcPursuit269.java:302)

Repair:
  * the visibility test no longer calls Minecraft at all: the NPC is considered
    hidden when it is outside the target's 70-degree view cone, computed from the
    target's yaw - the same data the formation geometry already uses;
  * everything else of Build 280 stays: escort repath discipline, catch-up speed
    setting, relocation and the %pursuit% output;
  * gate 3 gains phase D (external API references): every Minecraft / NeoForge
    call our classes make must already be made by a jar that runs in game. This
    is the phase that fails on Build 280 and passes on 281.

Build 280 - the pursuit NPC really catches up, and the node outputs its target.

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

Usage: python3 tools/patch_build281.py <base-280.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build280-pursuit-catchup"
NEW_VERSION = "0.8.0-build281-pursuit-no-los-crash"
OUT_NAME = "settledlands-0.8.0-build281-pursuit-no-los-crash.jar"

REPLACED = (
    # Only the controller changed. The inner classes are recompiled byte-identical
    # in meaning, so the shipped Build 280 copies stay - the jar diff stays honest.
    "dev/settledlands/npc/NpcPursuit269.class",
)
ADDED = ()
MIXINS_JSON = None
NEW_MIXIN = None
LANG = "assets/settledlands/lang/%s.json"
LANG_SET = {}

CHANGELOG_EN = (
    "Build 281: fixed the crash when the pursued NPC is left far behind. Build 280 asked "
    "Entity#hasLineOfSight whether the target can see the NPC, and Entity has no such method - "
    "the server stopped with NoSuchMethodError the first time a target got farther than 24 blocks. "
    "The check no longer calls Minecraft at all: the NPC counts as hidden when it is outside the "
    "target's 70-degree view cone, computed from the target's yaw, the same data the formation "
    "geometry already uses. Everything else stays as in 280 - the escort repath discipline that "
    "lets the NPC accelerate, the catch-up speed setting, the relocation to a stable spot 8 blocks "
    "from a target that outran the path, and the %pursuit% output of the node. The verification "
    "gate also gained a phase for this class of error: every Minecraft call our classes make must "
    "already be made by a jar that runs in game. "
)
PATCHINFO = (
    "Сборка 281: исправлен вылет, когда преследуемый NPC остаётся далеко позади. В 280 "
    "проверка видимости вызывала Entity#hasLineOfSight, а такого метода у Entity нет — сервер "
    "падал с NoSuchMethodError, как только цель оказывалась дальше 24 блоков. Теперь проверка "
    "вообще не обращается к Minecraft: NPC считается скрытым, если он вне конуса обзора цели "
    "(70 градусов), который считается по yaw цели — те же данные, что уже использует геометрия "
    "строя. Всё остальное как в 280: дисциплина пересчёта пути сопровождения, из-за которой NPC "
    "разгоняется, настройка «Ускорение догона», перенос к устойчивому месту в 8 блоках от цели, "
    "которая оторвалась, и выход ноды %pursuit%. В гейт добавлена фаза для такого класса ошибок: "
    "каждый вызов Minecraft из наших классов должен уже встречаться в сборке, которая работает "
    "в игре. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build281-src/out")
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


    toml = blobs["META-INF/neoforge.mods.toml"].decode("utf-8")
    if 'version="%s"' % BASE_VERSION not in toml:
        raise SystemExit("base toml does not declare %s" % BASE_VERSION)
    toml = toml.replace('version="%s"' % BASE_VERSION, 'version="%s"' % NEW_VERSION)
    marker = "description='''"
    toml = toml.replace(marker, marker + CHANGELOG_EN + "\n\n", 1)
    blobs["META-INF/neoforge.mods.toml"] = toml.encode("utf-8")
    print("  neoforge.mods.toml: version %s, description prefix" % NEW_VERSION)

    for locale, values in (LANG_SET or {}).items():
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
