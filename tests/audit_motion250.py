#!/usr/bin/env python3
"""Read-only bytecode audit of build 250 and optional external animation jars.

This documents a known renderer call-path mismatch, NOT an in-game test or a
proof that it caused a particular player's symptoms. Third-party jars stay
outside the repository and are never included in any release by this tool.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import struct
import sys
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from decode import walk
from disasm import C, code_of, parse_class, ref

BUILD250_SHA = "2187bd12cbbd252570280c850c665945d6ad87b929fd80a4f400217e4c11082d"
JAR250 = ROOT / "settledlands-0.8.0-build250-fresh-motion.jar"
PLAYER = "dev/settledlands/npc/client/PlayerBodyRenderer.class"
BRIDGE = "dev/settledlands/model/PlayerMotionBridge.class"
BBMODEL = "dev/settledlands/model/BbModel.class"
PAL_MODEL = "com/zigythebird/playeranim/mixin/PlayerModelMixin.class"
PAL_RENDERER = "com/zigythebird/playeranim/mixin/PlayerRendererMixin.class"
PAL_ITEM = "com/zigythebird/playeranim/mixin/ItemInHandLayerMixin.class"
HNS_ANIMATOR = "net/dndats/api/animations/PlayerAnimator.class"
INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;"


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def need(ok, reason):
    if not ok:
        raise ValueError(reason)


def load_class(jar, name):
    parsed = parse_class(jar.read(name))
    need(parsed["trailing"] == 0, f"trailing bytes in {name}")
    return parsed


def method(klass, name):
    cp = klass["cp"]
    matches = [m for m in klass["methods"] if cp[m[1]][1] == name]
    need(len(matches) == 1, f"expected exactly one method named {name}, found {len(matches)}")
    return matches[0]


def calls(klass, name):
    cp = klass["cp"]
    co = code_of(method(klass, name)[3], cp)
    need(co is not None, f"{name} has no Code")
    result = []
    for pc, ins in walk(co["code"]):
        if ins[0] in (0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9):
            result.append((pc, ref(cp, struct.unpack(">H", ins[1:3])[0])))
    return result


def annotation(klass, name):
    """Decode Mixin annotations and their nested @At values from this method."""
    cp = klass["cp"]
    attrs = method(klass, name)[3]

    def parse_annotation(c):
        typ = cp[c.u2()][1]
        vals = {}
        for _ in range(c.u2()):
            key = cp[c.u2()][1]
            vals[key] = parse_value(c)
        return {"type": typ, **vals}

    def parse_value(c):
        tag = chr(c.u1())
        if tag in "BCDFIJSZs":
            return cp[c.u2()][1]
        if tag == "e":
            return (cp[c.u2()][1], cp[c.u2()][1])
        if tag == "c":
            return cp[c.u2()][1]
        if tag == "@":
            return parse_annotation(c)
        if tag == "[":
            return [parse_value(c) for _ in range(c.u2())]
        raise ValueError(f"unknown annotation element {tag!r}")

    out = []
    for attr_index, blob in attrs:
        if cp[attr_index][1] not in ("RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations"):
            continue
        c = C(blob)
        for _ in range(c.u2()):
            out.append(parse_annotation(c))
        need(c.p == len(blob), f"unparsed annotation in {name}")
    return out


def inject(klass, name, target, location):
    matches = [a for a in annotation(klass, name) if a.get("type") == INJECT]
    need(len(matches) == 1, f"expected one @Inject for {name}")
    item = matches[0]
    need(target in item.get("method", []), f"unexpected injected method for {name}: {item}")
    need(any(a.get("value") == location for a in item.get("at", [])),
         f"unexpected injection point for {name}: {item}")


def crc(jar):
    bad = jar.testzip()
    need(bad is None, f"bad CRC for {bad}")


def mod_info(jar, modid):
    metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode("utf-8"))
    matches = [m for m in metadata.get("mods", []) if m.get("modId") == modid]
    need(len(matches) == 1, f"expected one metadata entry for {modid}")
    return matches[0]


def audit_build250(path):
    actual = sha256(path)
    need(actual == BUILD250_SHA, f"unexpected build 250 SHA-256: {actual}")
    with zipfile.ZipFile(path) as jar:
        crc(jar)
        player = load_class(jar, PLAYER)
        bridge = load_class(jar, BRIDGE)
        bb = load_class(jar, BBMODEL)
        pre = calls(player, "onRenderPlayerPre")
        cancel = [pc for pc, call in pre if ".setCanceled(Z)V" in call]
        actor = [pc for pc, call in pre if ".renderActor(" in call]
        need(len(cancel) == len(actor) == 1 and cancel[0] < actor[0], "Pre call order changed")
        setup = calls(bridge, "setup")
        model_calls = [x for _, x in setup if "PlayerModel.setupAnim(" in x]
        living = "net/minecraft/client/model/PlayerModel.setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"
        need(model_calls == [living], f"unexpected setupAnim invocation: {model_calls}")
        renderer = calls(bb, "settledlands$renderActorOriginal")
        first = [(pc, s) for pc, s in renderer if pc < 188]
        need(any("com/mojang/math/Axis.YP" in s for _, s in first), "initial yaw not found")
        need(any("PoseStack.mulPose(" in s for _, s in first), "initial pose rotation not found")
        need(not any(".setupRotations(" in s or ".getSwimAmount(" in s
                     or ".isVisuallySwimming(" in s for _, s in renderer),
             "swimming/renderer calls changed; re-evaluate the audit")
    print(f"BUILD 250 SHA-256 {actual}")
    print("OBSERVED: Pre is cancelled before renderActor; bridge calls setupAnim(LivingEntity).")
    print("OBSERVED: actor applies initial yaw, without setupRotations/getSwimAmount/isVisuallySwimming.")
    print("NOTE: isSwimming elsewhere chooses embedded clips and is not the missing root transform.")


def audit_pal(path):
    with zipfile.ZipFile(path) as jar:
        crc(jar)
        info = mod_info(jar, "player_animation_library")
        version = info.get("version")
        need(version == "1.1.6+mc.1.21.1", f"PAL version requires a new audit: {version}")
        pm = load_class(jar, PAL_MODEL)
        pr = load_class(jar, PAL_RENDERER)
        pi = load_class(jar, PAL_ITEM)
        inject(pm, "setupPlayerAnimation", "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", "RETURN")
        inject(pr, "applyBodyTransforms", "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", "RETURN")
        # For items the target/ordinal differ; verify the named mixin methods and their selectors.
        for name in ("changeItemLocation", "changeItemRotationAndScale"):
            anns = [a for a in annotation(pi, name) if a.get("type") == INJECT]
            need(len(anns) == 1 and any(m.startswith("renderArmWithItem(") for m in anns[0].get("method", [])),
                 f"PAL item hook changed: {name}")
        need(any(".updatePart(" in x for _, x in calls(pm, "setupPlayerAnimation")), "PAL part update absent")
        need(any(".handleAnimations(" in x for _, x in calls(pr, "applyBodyTransforms")), "PAL processor absent")
    print(f"PAL {version} SHA-256 {sha256(path)}")
    print("OBSERVED: PAL injects after setupAnim(Entity), not setupAnim(LivingEntity).")
    print("OBSERVED: PAL updates its processor/body at setupRotations and items at renderArmWithItem.")


def audit_hns(path):
    with zipfile.ZipFile(path) as jar:
        crc(jar)
        info = mod_info(jar, "hackersandslashers")
        anim = load_class(jar, HNS_ANIMATOR)
        cp = anim["cp"]
        values = {entry[1] for entry in cp if entry and entry[0] == 1}
        expected = ("attack_layer", "defense_layer", "action_layer", "parkour_layer", "pose_action_layer", "pose_layer", "first_person_pose_layer")
        present = [v for v in expected if v in values]
        names = [n for n in jar.namelist() if n.startswith("assets/hackersandslashers/player_animations/hnsattack_") and n.endswith(".json")]
        keys = Counter()
        parsed = 0
        for name in names:
            data = json.loads(jar.read(name))
            moves = data.get("emote", {}).get("moves", [])
            if isinstance(moves, list):
                parsed += 1
                seen = {k for move in moves if isinstance(move, dict) for k in move if k not in ("tick", "easing", "turn")}
                keys.update(seen)
    print(f"HnS {info.get('version')} SHA-256 {sha256(path)}")
    print("LAYER strings in PlayerAnimator:", ", ".join(present) if present else "none recognized")
    print(f"ATTACK resources: {len(names)} files, {parsed} legacy emote/moves structures; channel frequency:")
    print(" ", ", ".join(f"{k}={v}" for k, v in sorted(keys.items())) or "none recognized")
    print("NOTE: identifiers and keyframe counts are not evidence that these layers were active in the user's game.")


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--build", type=Path, default=JAR250)
    ap.add_argument("--pal", type=Path, help="external Player Animation Library 1.1.6 jar")
    ap.add_argument("--hns", type=Path, help="external Hackers 'n Slashers jar")
    args = ap.parse_args()
    try:
        audit_build250(args.build)
        if args.pal:
            audit_pal(args.pal)
        if args.hns:
            audit_hns(args.hns)
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile, struct.error) as error:
        print(f"AUDIT INCOMPLETE: {error}", file=sys.stderr)
        return 1
    print("AUDIT COMPLETE: bytecode/resource analysis only; no game or visual test was run.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
