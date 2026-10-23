#!/usr/bin/env python3
"""Convert a Blockbench .bbmodel (modded_entity format) to GeckoLib assets.

Produces, next to the given output paths:
  * a Bedrock-format .geo.json that GeckoLib can bake (all cubes - no poly_mesh),
  * a .animation.json with the model's animations converted,
  * the embedded texture as .png.

Coordinate conversion is the standard Blockbench Java -> Bedrock one:
X is mirrored (cube origin = [-to.x, from.y, from.z], pivots negate X,
rotations negate X and Y). Box UV offsets carry over unchanged.

Bones listed in HIDDEN_BONES get "neverRender": true (alternative limb
variants and poses not used by default - the limb system will unhide
them at runtime later, same scheme as the broken_* bones before).

Usage:
  python3 tools/convert_bbmodel.py <in.bbmodel> <out_dir> <model_name>
  -> <out_dir>/<model_name>.geo.json, <model_name>.animation.json, <model_name>.png
"""
import base64
import json
import math
import os
import re
import sys

# Poses/variants present in the model but not part of the default look.
HIDDEN_BONES = {"arms", "left_shoulder", "short_left_arm", "left_arm_stomp"}

# Bedrock Molang sway expressions commonly authored in Blockbench, e.g.
# "math.sin(query.anim_time * 50) * -5". GeckoLib's runtime Molang dialect is not
# guaranteed to match, so the converter bakes them into numeric keyframes by
# sampling one full period (360 / frequency seconds, math.sin takes degrees).
_MOLANG_RE = re.compile(
    r"math\.(sin|cos)\(query\.anim_time \* (-?[\d.]+)\) \* (-?[\d.]+)(?:\s*([+-])\s*([\d.]+))?")
_SAMPLE_STEP = 0.2


def _eval_molang(expr, t):
    m = _MOLANG_RE.fullmatch(expr.strip())
    if not m:
        return None
    fn, freq, amp, sign, offset = m.groups()
    wave = math.sin(math.radians(float(freq) * t)) if fn == "sin" else math.cos(math.radians(float(freq) * t))
    value = wave * float(amp)
    if offset is not None:
        value += float(offset) if sign == "+" else -float(offset)
    return value


def _molang_period(expr):
    m = _MOLANG_RE.fullmatch(expr.strip())
    return 360.0 / abs(float(m.group(2))) if m else None


def mirror_pivot(p):
    return [-p[0], p[1], p[2]]


def convert_cube(el):
    frm, to = el["from"], el["to"]
    cube = {
        "origin": [-to[0], frm[1], frm[2]],
        "size": [to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]],
        "uv": el.get("uv_offset", [0, 0]),
    }
    if el.get("inflate"):
        cube["inflate"] = el["inflate"]
    if el.get("rotation"):
        cube["pivot"] = mirror_pivot(el["origin"])
        r = el["rotation"]
        cube["rotation"] = [-r[0], -r[1], r[2]]
    return cube


def convert_model(data, model_name):
    elements = {el["uuid"]: el for el in data.get("elements", [])}
    bones = []

    def add_bone(node, parent_name):
        bone = {"name": node["name"], "pivot": mirror_pivot(node.get("origin", [0, 0, 0]))}
        if parent_name:
            bone["parent"] = parent_name
        if node["name"] in HIDDEN_BONES:
            bone["neverRender"] = True
        if node.get("rotation"):
            r = node["rotation"]
            bone["rotation"] = [-r[0], -r[1], r[2]]
        cubes = []
        for child in node.get("children", []):
            if isinstance(child, str) and child in elements:
                cubes.append(convert_cube(elements[child]))
        if cubes:
            bone["cubes"] = cubes
        bones.append(bone)
        for child in node.get("children", []):
            if isinstance(child, dict):
                add_bone(child, node["name"])

    for node in data.get("outliner", []):
        if isinstance(node, dict):
            add_bone(node, None)

    desc = {
        "identifier": f"geometry.{model_name}",
        "texture_width": data["resolution"]["width"],
        "texture_height": data["resolution"]["height"],
        "visible_bounds_width": 2,
        "visible_bounds_height": 2.5,
        "visible_bounds_offset": [0, 1.25, 0],
    }
    return {"format_version": "1.12.0", "minecraft:geometry": [{"description": desc, "bones": bones}]}


def _vec(point, times):
    """Convert one keyframe data point at the given sample times -> {time: vec}."""
    raw = [point["x"], point["y"], point["z"]]

    def value_at(component, t):
        v = component
        if isinstance(v, str):
            baked = _eval_molang(v, t)
            if baked is None:
                raise ValueError(f"unsupported Molang expression, cannot bake: {v!r}")
            return baked
        return float(v)

    return {f"{t:.4g}": [value_at(c, t) for c in raw] for t in times}


def convert_animations(data, model_name):
    animations = {}
    for anim in data.get("animations", []):
        # Locate Molang expressions first: they define the true loop length.
        loop_length = float(anim.get("length", 1) or 0)
        for animator in anim.get("animators", {}).values():
            for kf in animator.get("keyframes", []):
                for point in kf.get("data_points", []):
                    for component in point.values():
                        if isinstance(component, str):
                            period = _molang_period(component)
                            if period:
                                loop_length = max(loop_length, round(period, 4))

        bones = {}
        for animator in anim.get("animators", {}).values():
            if animator.get("type") != "bone":
                continue
            channels = {}
            for kf in animator.get("keyframes", []):
                point = kf["data_points"][0]
                has_molang = any(isinstance(c, str) for c in point.values())
                if has_molang:
                    steps = max(2, int(round(loop_length / _SAMPLE_STEP)))
                    times = [i * loop_length / steps for i in range(steps)] + [loop_length]
                else:
                    times = [float(kf["time"])]
                for time_key, vec in _vec(point, times).items():
                    if kf["channel"] == "rotation":
                        vec = [-vec[0], -vec[1], vec[2]]
                    elif kf["channel"] == "position":
                        vec = [-vec[0], vec[1], vec[2]]
                    channels.setdefault(kf["channel"], {})[time_key] = vec
            if channels:
                bones[animator["name"]] = channels
        animations[f"animation.{model_name}.{anim['name']}"] = {
            "loop": anim.get("loop") in (True, "loop"),
            "animation_length": loop_length if loop_length > 0 else 1,
            "bones": bones,
        }
    return {"format_version": "1.10.0", "animations": animations}


def main():
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    src, out_dir, model_name = sys.argv[1], sys.argv[2], sys.argv[3]
    with open(src) as f:
        data = json.load(f)
    if data.get("meta", {}).get("model_format") not in ("modded_entity", None):
        print(f"warning: unexpected model_format {data.get('meta')} - continuing anyway")

    os.makedirs(out_dir, exist_ok=True)
    geo = convert_model(data, model_name)
    with open(os.path.join(out_dir, model_name + ".geo.json"), "w") as f:
        json.dump(geo, f, indent="\t")
        f.write("\n")

    anims = convert_animations(data, model_name)
    with open(os.path.join(out_dir, model_name + ".animation.json"), "w") as f:
        json.dump(anims, f, indent="\t")
        f.write("\n")

    tex = data["textures"][0]["source"]
    png = base64.b64decode(tex.split(",", 1)[1] if "," in tex else tex)
    with open(os.path.join(out_dir, model_name + ".png"), "wb") as f:
        f.write(png)

    n_cubes = sum(len(b.get("cubes", [])) for b in geo["minecraft:geometry"][0]["bones"])
    print(f"{model_name}: {len(geo['minecraft:geometry'][0]['bones'])} bones, {n_cubes} cubes, "
          f"animations: {list(anims['animations'])}, texture {len(png)} bytes")


if __name__ == "__main__":
    main()
