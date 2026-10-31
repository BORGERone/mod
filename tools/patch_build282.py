#!/usr/bin/env python3
"""Build 282 - the possessed body gets its animations back.

Report: "animations stopped working, but only on the body I possessed".

The log of the reported session says exactly which branch ran:

    [SettledLands/FreshMotion251] Fresh Animations Player Extension is
    unavailable or disabled for this player; player is shown in a neutral pose
    to avoid conflicting clips.

The instance has no Fresh Animations: Player Extension resource pack (the
resource-reload line lists vanilla, mod packs, moonlight:merged_pack and
quark-emote-pack - no FA pack). All four pinned versions match exactly
(Touch'n Motion 1.0.4, EMF 3.3.9, ETF 7.2.4, EMF Compat Core 2.3.2), so the
bridge starts; but EMF cannot prepare a Fresh Animations run without the pack.
PlayerMotionBridge#setup then takes the "neutral pose" branch on purpose:

    if (!applied) {
        cancel(scope);
        if (freshPath && !disabled) {
            model.getRootPart().getAllParts().forEach(ModelPart::resetPose);   // frozen
        } else {
            model.setupAnimRaw(...);                                           // animated
        }
    }

That rule was written to stop embedded clips fighting a working FA rig. When FA
never runs there is nothing to fight, so the body just stands in its rest pose -
which is the reported bug, and it also hits every other player body drawn through
BbModel (PlayerBodyRenderer) while other NPCs keep animating through
NpcMotionBridge256's own fallback.

Repair: the neutral branch is never taken. One instruction in setup is rewritten:

    1012: 15 11      iload 17        (freshPath)   -> 03 00  iconst_0 ; nop
    1014: 99 00 1d   ifeq -> 1043                  (the ifeq stays: it now always jumps)

Same length, same stack effect, same instruction boundaries, every branch target
and every StackMapTable frame keeps its offset - so the class stays verifier
clean, and only the "FA did not apply" case changes. When FA/TnM do apply,
setup returns at the `if (!applied)` check long before this code, so nothing
changes for instances that have the pack.

The warning text is corrected in place as well (same byte length, padded):
it no longer claims a neutral pose.

Usage: python3 tools/patch_build282.py <base-281.jar> [--out DIR]
"""

import argparse
import hashlib
import pathlib
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classfile                                        # noqa: E402
from classfile import instruction_offsets               # noqa: E402

BASE_VERSION = "0.8.0-build281-pursuit-no-los-crash"
NEW_VERSION = "0.8.0-build282-possessed-body-animation"
OUT_NAME = "settledlands-0.8.0-build282-possessed-body-animation.jar"

TARGET = "dev/settledlands/model/PlayerMotionBridge.class"
METHOD = "setup"
ANIMATOR = "setupAnimRaw"

OLD_WARN = (b"Fresh Animations Player Extension is unavailable or disabled for this player; "
            b"player is shown in a neutral pose to avoid conflicting clips.")
NEW_WARN_TEXT = (b"Fresh Animations Player Extension is unavailable for this body; "
                 b"the embedded actor animation is used instead.")

CHANGELOG_EN = (
    "Build 282: the possessed body animates again. When the instance has no Fresh Animations: "
    "Player Extension resource pack - or EMF cannot prepare a run for the player - "
    "PlayerMotionBridge#setup used to put the body into a neutral rest pose on purpose, so a "
    "possessed player stood frozen while every NPC kept animating. The neutral branch is now "
    "never taken: without a Fresh Animations pose the body falls back to the embedded actor "
    "animation (walk, run, idle, talk), which is the same fallback the pre-251 code used. One "
    "instruction is rewritten in place (iload -> iconst_0), so the class keeps its length, its "
    "stack shapes, its instruction boundaries and every stack map frame; instances where "
    "Fresh Animations and Touch'n Motion do run are untouched, because setup returns before "
    "this branch. The warning text no longer claims a neutral pose. "
)
PATCHINFO = (
    "Сборка 282: тело, в которое вселился игрок, снова анимируется. Если в сборке нет "
    "ресурспака Fresh Animations: Player Extension — или EMF не может подготовить прогон для "
    "игрока — PlayerMotionBridge#setup специально ставил тело в нейтральную позу, и вселённый "
    "игрок стоял замороженным, пока все NPC анимировались. Теперь эта ветка не выполняется "
    "никогда: без позы Fresh Animations тело берёт встроенную актёрскую анимацию (ходьба, бег, "
    "покой, разговор) — тот же откат, что был до 251. Переписана одна инструкция на месте "
    "(iload -> iconst_0), поэтому длина класса, формы стека, границы инструкций и все кадры "
    "стековых карт сохраняются; там, где Fresh Animations и Touch'n Motion работают, ничего не "
    "меняется — setup выходит раньше этой ветки. Текст предупреждения больше не обещает "
    "нейтральную позу. "
)


def fail(msg):
    raise SystemExit("patch_build282: %s" % msg)


def locate_setup_patch_point(data):
    """Return (instruction offset, local index) of the `iload freshPath` in setup."""
    cf = classfile.parse(data, TARGET)
    cp = cf.cp
    method = next((m for m in cf.methods if m.name == METHOD), None)
    if method is None:
        fail("no %s method in %s" % (METHOD, TARGET))
    attr = method.attr("Code")
    if attr is None:
        fail("%s has no Code" % METHOD)
    code_len = int.from_bytes(attr.data[4:8], "big")
    code = attr.data[8:8 + code_len]
    bounds = sorted(instruction_offsets(code))
    bset = set(bounds)

    def u2(i):
        return int.from_bytes(code[i:i + 2], "big")

    def s2(i):
        return int.from_bytes(code[i:i + 2], "big", signed=True)

    def method_ref(idx):
        entry = cp[idx]
        if entry[0] != 10:
            return None, None, None
        cls_i, nat_i = entry[1]
        nat = cp[nat_i]
        return cp[cp[cls_i][1]][1], cp[nat[1][0]][1], cp[nat[1][1]][1]

    found = []
    for i in bounds:
        if code[i] != 0x99:                     # ifeq
            continue
        target = i + s2(i + 1)
        if target not in bset:
            continue
        animator = None
        for k in bounds:
            if k < target or k > target + 40:
                continue
            if code[k] == 0xb6:                 # invokevirtual
                owner, name, _ = method_ref(u2(k + 1))
                if name == ANIMATOR:
                    animator = (k, owner)
                    break
        if animator is None:
            continue
        prev = [b for b in bounds if b < i][-1]
        if code[prev] != 0x15:                  # iload
            continue
        local = code[prev + 1]
        # the very same boolean decides `freshPath`; make sure the pattern after
        # the ifeq is `getstatic disabled; ifne <same target>`
        if code[i + 3] != 0xb2 or code[i + 6] != 0x9a:
            continue
        if i + 6 + s2(i + 7) != target:
            continue
        field_cls, field_nat = cp[u2(i + 4)][1]
        if cp[cp[field_cls][1]][1] != "dev/settledlands/model/PlayerMotionBridge":
            continue
        if cp[cp[field_nat][1][0]][1] != "disabled":
            continue
        found.append((prev, local, target, animator[0], animator[1], code_len))

    if len(found) != 1:
        fail("expected exactly one guarded neutral-pose branch in %s, found %d"
             % (METHOD, len(found)))
    prev, local, target, animator, owner, code_len = found[0]
    if owner != "dev/settledlands/model/BbModel":
        fail("unexpected animator owner %s" % owner)

    # the patched local must be the one `freshPath` was stored into
    stores = [b for b in bounds[:bounds.index(prev)]
              if code[b] == 0x36 and code[b + 1] == local]
    if not stores:
        fail("local %d is never stored before the branch - wrong variable" % local)

    return prev, local, target, animator, attr, code, code_len


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

    if TARGET not in blobs:
        fail("%s is not in the base jar" % TARGET)
    data = blobs[TARGET]

    prev, local, target, animator, attr, code, code_len = locate_setup_patch_point(data)
    base_abs = attr.offset + 8
    print("  %s#%s: freshPath is local %d, iload at %d, neutral-pose branch -> %d, %s at %d"
          % (TARGET, METHOD, local, prev, target, ANIMATOR, animator))

    if data[base_abs + prev:base_abs + prev + 2] != bytes([0x15, local]):
        fail("byte check before patch failed")
    patched = bytearray(data)
    patched[base_abs + prev] = 0x03              # iconst_0
    patched[base_abs + prev + 1] = 0x00          # nop
    if len(patched) != len(data):
        fail("class length changed")

    # re-parse: same length, same instruction boundaries, new opcode in place
    cf = classfile.parse(bytes(patched), TARGET)
    method = next(m for m in cf.methods if m.name == METHOD)
    code_attr = method.attr("Code")
    new_code = code_attr.data[8:8 + code_len]
    if new_code != code[:prev] + b"\x03\x00" + code[prev + 2:]:
        fail("patched Code does not match the intended edit")
    # the two patched bytes are two instructions now (iconst_0, nop); every other
    # boundary, every branch target and every stack map frame keeps its offset.
    expected = set(instruction_offsets(code)) | {prev + 1}
    if set(instruction_offsets(new_code)) != expected:
        fail("instruction boundaries moved")
    if code_attr.data[8 + len(new_code):] != attr.data[8 + len(code):]:
        fail("the code attribute tail (exception table / stack maps) changed")
    print("  patch: iload %d -> iconst_0 at code offset %d (length unchanged)" % (local, prev))

    if bytes(patched).count(OLD_WARN) != 1:
        fail("the neutral-pose warning string was not found exactly once")
    pad = len(OLD_WARN) - len(NEW_WARN_TEXT)
    if pad < 1:
        fail("replacement warning text is longer than the original")
    patched = bytes(patched).replace(OLD_WARN, NEW_WARN_TEXT + b" " * pad)
    print("  warning text corrected in place (%d bytes, padded)" % len(OLD_WARN))
    blobs[TARGET] = patched

    toml = blobs["META-INF/neoforge.mods.toml"].decode("utf-8")
    if 'version="%s"' % BASE_VERSION not in toml:
        fail("base toml does not declare %s" % BASE_VERSION)
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
