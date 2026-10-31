#!/usr/bin/env python3
"""Build 274 - make the pursuit hitPort injector cancellable.

Crash (latest.log, 31.10 01:09):
    Caused by: org.spongepowered.asm.mixin.injection.callback.CancellationException:
        The call hitPort is not cancellable.
        at CallbackInfo.cancel(CallbackInfo.java:101)
        at CallbackInfoReturnable.setReturnValue(CallbackInfoReturnable.java:106)
        at NpcGraphCanvas.handler$...$settledlands$pursuitHitPort(...)

NpcPursuitCanvasMixin272#settledlands$pursuitHitPort is @Inject(TAIL) WITHOUT
cancellable = true, but its body calls cir.setReturnValue(-1) as soon as the
node under the cursor is a pursuit node - i.e. right after the pursuit node is
created. Mixin throws CancellationException because setReturnValue() cancels
the method and the injection is not cancellable.

Repair: add the `cancellable = true` element to that method's @Inject annotation
element_value_pairs. Nothing else in the class changes.

Usage: python3 tools/patch_build274.py <base-273.jar> [--out DIR]
"""

import argparse
import hashlib
import pathlib
import sys
import zipfile

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from patch_build273 import (                            # noqa: E402
    apply_edits, cp_entry_bytes, scan, u2b, utf8_entry, utf8_of,
)

BASE_VERSION = "0.8.0-build273-pursuit-mixinfix"
NEW_VERSION = "0.8.0-build274-pursuit-hitport-fix"

CANVAS_MIXIN = "dev/settledlands/mixin/NpcPursuitCanvasMixin272.class"
HANDLER = "settledlands$pursuitHitPort"
DESC_INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;"
TAG_BOOLEAN = 0x5A

CHANGELOG_EN = (
    "Build 274: fixes the crash when a pursuit node is created. The hitPort "
    "injector of NpcPursuitCanvasMixin272 called CallbackInfoReturnable.setReturnValue "
    "although its @Inject was not declared cancellable, so Mixin threw "
    "CancellationException: \"The call hitPort is not cancellable\" the first time "
    "the new pursuit card was under the cursor. The annotation now carries "
    "cancellable = true; no other class changed. "
)
PATCHINFO = (
    "Сборка 274: исправлен вылет при создании ноды преследования. Инжектор "
    "settledlands$pursuitHitPort вызывал setReturnValue без cancellable = true — "
    "Mixin бросал CancellationException. Добавлен cancellable = true, остальные "
    "классы не изменялись. "
)


def _skip_element_value(data, pos):
    tag = data[pos]
    pos += 1
    if tag in (0x5A, 0x42, 0x43, 0x44, 0x46, 0x48, 0x49, 0x4A, 0x53):
        return pos + 2
    if tag in (0x73, 0x63):                      # String / Class
        return pos + 2
    if tag == 0x65:                              # enum: type + const name
        return pos + 4
    if tag == 0x40:                              # nested annotation
        pos += 2                                 # type_index
        pairs = int.from_bytes(data[pos:pos + 2], "big")
        pos += 2
        for _ in range(pairs):
            pos += 2                             # element name
            pos = _skip_element_value(data, pos)
        return pos
    if tag == 0x5B:                              # array
        count = int.from_bytes(data[pos:pos + 2], "big")
        pos += 2
        for _ in range(count):
            pos = _skip_element_value(data, pos)
        return pos
    raise SystemExit("unknown element_value tag 0x%02x" % tag)


def find_inject_annotation(data, method_attr):
    """Absolute offsets of the @Inject annotation inside one attribute."""
    start = method_attr["start"]
    pos = start
    count = int.from_bytes(data[pos:pos + 2], "big")
    pos += 2
    for _ in range(count):
        ann_start = pos
        type_idx = int.from_bytes(data[pos:pos + 2], "big")
        pos += 2
        pairs_pos = pos
        pairs = int.from_bytes(data[pos:pos + 2], "big")
        pos += 2
        for _p in range(pairs):
            pos += 2                             # element name_index
            pos = _skip_element_value(data, pos)
        yield type_idx, pairs_pos, pairs, ann_start, pos


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_name = "settledlands-0.8.0-build274-pursuit-hitport-fix.jar"
    out_path = outdir / out_name

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}

    data = blobs[CANVAS_MIXIN]
    info = scan(data)
    cp_count = info["cp_count"]

    method = None
    for m in info["methods"]:
        if utf8_of(data, cp_count, m["name_idx"]) == HANDLER:
            method = m
            break
    if method is None:
        raise SystemExit("%s: method %s not found" % (CANVAS_MIXIN, HANDLER))

    cp_add = []

    def add(entry_bytes):
        cp_add.append(entry_bytes)
        return cp_count + len(cp_add) - 1

    name_idx = add(utf8_entry("cancellable"))
    true_idx = add(cp_entry_bytes(3, (1).to_bytes(4, "big")))     # Integer 1

    target = None
    for attr in method["attrs"]:
        if utf8_of(data, cp_count, attr["name_idx"]) not in (
                "RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations"):
            continue
        for type_idx, pairs_pos, pairs, ann_start, ann_end in find_inject_annotation(data, attr):
            try:
                desc = utf8_of(data, cp_count, type_idx)
            except Exception:
                continue
            if desc != DESC_INJECT:
                continue
            # already cancellable?
            pos = pairs_pos + 2
            for _ in range(pairs):
                n_idx = int.from_bytes(data[pos:pos + 2], "big")
                v_start = pos + 2
                v_end = _skip_element_value(data, v_start)
                if utf8_of(data, cp_count, n_idx) == "cancellable":
                    raise SystemExit("%s: %s already declares cancellable" % (CANVAS_MIXIN, HANDLER))
                pos = v_end
            target = (pairs_pos, pairs, ann_end, attr)
            break
        if target:
            break

    if target is None:
        raise SystemExit("%s: no @Inject annotation on %s" % (CANVAS_MIXIN, HANDLER))

    pairs_pos, pairs, ann_end, attr = target
    pair_bytes = u2b(name_idx) + bytes([TAG_BOOLEAN]) + u2b(true_idx)
    edits = [
        # the annotation lives inside an attribute: its u4 length must grow too
        (attr["start"] - 4, attr["start"],
         (attr["len"] + len(pair_bytes)).to_bytes(4, "big")),
        (pairs_pos, pairs_pos + 2, u2b(pairs + 1)),
        (ann_end, ann_end, pair_bytes),
    ]
    blobs[CANVAS_MIXIN] = apply_edits(data, edits, info, cp_add)
    print("  %s: @Inject on %s -> cancellable = true (%d -> %d pairs)"
          % (CANVAS_MIXIN, HANDLER, pairs, pairs + 1))

    toml = blobs["META-INF/neoforge.mods.toml"].decode("utf-8")
    if 'version="%s"' % BASE_VERSION not in toml:
        raise SystemExit("base toml does not declare %s" % BASE_VERSION)
    toml = toml.replace('version="%s"' % BASE_VERSION, 'version="%s"' % NEW_VERSION)
    marker = "description='''"
    toml = toml.replace(marker, marker + CHANGELOG_EN + "\n\n", 1)
    blobs["META-INF/neoforge.mods.toml"] = toml.encode("utf-8")

    if "settledlands-patch-info.txt" in blobs:
        blobs["settledlands-patch-info.txt"] = (PATCHINFO + "\n").encode("utf-8")

    order = [i.filename for i in infos]
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in order:
            zi = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, blobs[name])

    digest = hashlib.sha256(out_path.read_bytes()).hexdigest()
    (outdir / (out_name + ".sha256sums.txt")).write_text(
        "%s  %s\n" % (digest, out_name), encoding="utf-8")
    print("  wrote %s" % out_path)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
