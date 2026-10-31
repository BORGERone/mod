#!/usr/bin/env python3
"""Build 292 - a trigger command that names its target must not get a second one.

Reported chain: "В радиусе" (blue output) -> "Триггер" with the command
`tp @npc ~ ~5 ~`, expecting the body to be lifted 5 blocks. The game answered
`Неверный аргумент для команды tp @s 8efb... ~ ~5`.

Why: NpcActions#command runs NpcActions#fill first, and fill turns the mod's own
@npc token into the NPC uuid. Only after that does the stock code test the
command against ^(tp|teleport)\\s+([~^\\-0-9].*) - "no target, only coordinates,
so add @s" - and a uuid starts with a hex digit, so the line became
`tp @s <uuid> ~ ~5 ~`, where <uuid> sits in the position slot.

Fix: the shortcut now asks for a real coordinate. The stock replaceFirst is
redirected in `command` and `commandForEntity` through NpcSelfTarget292, which
runs it only when the token after tp/teleport consists of ~ ^ - . and digits
alone. @npc keeps resolving to the NPC, @s and @p are untouched.

Usage: python3 tools/patch_build292.py <base-291.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classfile  # noqa: E402

BASE_VERSION = "0.8.0-build291-launch-fix"
NEW_VERSION = "0.8.0-build292-tp-target-fix"
OUT_NAME = "settledlands-0.8.0-build292-tp-target-fix.jar"

REPLACED = ()
ADDED = (
    "dev/settledlands/npc/NpcSelfTarget292.class",
    "dev/settledlands/mixin/NpcActionsTpFix292.class",
)
REMOVED = ()

MIXINS_JSON = "settledlands.mixins.json"
NEW_MIXINS = ()
COMMON_MIXINS = ("NpcActionsTpFix292",)
DROP_MIXINS = ()
CLIENT_MIXINS = ()

# The redirect has to land on a real INVOKE inside each of these methods: a @At
# that resolves to nothing is a mixin apply failure in game, not a warning.
REDIRECT_SITES = (
    ("dev/settledlands/npc/NpcActions.class",
     "command(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z"),
    ("dev/settledlands/npc/NpcActions.class",
     "commandForEntity(Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z"),
)
REDIRECT_TARGET = ("java/lang/String", "replaceFirst",
                   "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")

CHANGELOG_EN = (
    "Build 292 fixes trigger commands that name their target: the mod fills @npc with the "
    "NPC uuid and then decided, from the first character after tp, that the author wanted "
    "\"teleport myself\" and added @s, so `tp @npc ~ ~5 ~` ran as `tp @s <uuid> ~ ~5 ~` and "
    "the game rejected it. The shortcut now runs only for real coordinates. "
)
PATCHINFO = (
    "Сборка 292 чинит команды триггера, в которых цель указана явно: мод сначала "
    "подставляет в @npc uuid НПС, а потом по первому символу после tp решал, что автор "
    "хотел «телепортировать себя», и дописывал @s. В итоге `tp @npc ~ ~5 ~` уходило как "
    "`tp @s <uuid> ~ ~5 ~`, и игра отвечала «Неверный аргумент для команды». Теперь "
    "короткий путь срабатывает только на настоящие координаты. "
)


def invoke_refs(cf):
    """Yield (owner, name, desc) for every INVOKE in the class, with its method."""
    names = {0xb6: "invokevirtual", 0xb7: "invokespecial", 0xb8: "invokestatic",
             0xb9: "invokeinterface"}
    for m in cf.methods:
        code = None
        for a in m.attrs:
            if a.name == "Code":
                code = a
                break
        if code is None:
            continue
        length = int.from_bytes(code.data[4:8], "big")
        raw = code.data[8:8 + length]      # skip max_stack, max_locals, code_length
        for off, op, _length in classfile.walk_code(raw, "%s.%s" % (cf.name, m.name)):
            if op not in names:
                continue
            idx = (raw[off + 1] << 8) | raw[off + 2]
            tag, val = cf.cp[idx]
            if tag not in (10, 11):
                continue
            cls_i, nat_i = val
            nat_tag, nat = cf.cp[nat_i]
            if nat_tag != 12:
                continue
            yield m.name + m.desc, cf.class_name(cls_i), cf.utf8(nat[0]), cf.utf8(nat[1])


def check_redirect_sites(blob, cls, method):
    cf = classfile.parse(blob, cls)
    hits = [r for r in invoke_refs(cf) if r[0] == method and r[1:] == REDIRECT_TARGET]
    if not hits:
        raise SystemExit("%s: no %s.%s %s call inside %s - the redirect would not apply"
                         % (cls, REDIRECT_TARGET[0], REDIRECT_TARGET[1],
                            REDIRECT_TARGET[2], method))
    return len(hits)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build292-src/out")
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

    for cls, method in REDIRECT_SITES:
        count = check_redirect_sites(blobs[cls], cls, method)
        print("  redirect site: %s %s, %d call(s)" % (cls, method.split("(")[0], count))

    for name in REMOVED:
        if name not in blobs:
            raise SystemExit("class is not in the base jar: %s" % name)
        del blobs[name]
        order.remove(name)
        print("  removed: %s" % name)

    for name in REPLACED:
        src = classes / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
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
    common = [m for m in mixins.get("mixins", []) if m not in DROP_MIXINS]
    client = [m for m in mixins.get("client", []) if m not in DROP_MIXINS]
    for mixin in NEW_MIXINS:
        if mixin not in common and mixin not in client:
            client.append(mixin)
    for mixin in COMMON_MIXINS:
        if mixin not in common:
            common.append(mixin)
    mixins["mixins"] = common
    mixins["client"] = client
    blobs[MIXINS_JSON] = (json.dumps(mixins, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client" % (len(common), len(client)))

    toml = blobs["META-INF/neoforge.mods.toml"].decode("utf-8")
    if 'version="%s"' % BASE_VERSION not in toml:
        raise SystemExit("base toml does not declare %s" % BASE_VERSION)
    toml = toml.replace('version="%s"' % BASE_VERSION, 'version="%s"' % NEW_VERSION)
    toml = toml.replace('displayName="Settled Lands"',
                        'displayName="Settled Lands %s"' % NEW_VERSION)
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
