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
import os
import sys

# Poses/variants present in the model but not part of the default look.
HIDDEN_BONES = {"arms", "left_shoulder", "short_left_arm", "left_arm_stomp"}


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


def convert_animations(data, model_name):
    animations = {}
    for anim in data.get("animations", []):
        bones = {}
        for animator in anim.get("animators", {}).values():
            if animator.get("type") != "bone":
                continue
            channels = {}
            for kf in animator.get("keyframes", []):
                point = kf["data_points"][0]
                vec = [float(point["x"]), float(point["y"]), float(point["z"])]
                if kf["channel"] == "rotation":
                    vec = [-vec[0], -vec[1], vec[2]]
                elif kf["channel"] == "position":
                    vec = [-vec[0], vec[1], vec[2]]
                channels.setdefault(kf["channel"], {})[f"{kf['time']:.4g}"] = vec
            if channels:
                bones[animator["name"]] = channels
        animations[f"animation.{model_name}.{anim['name']}"] = {
            "loop": anim.get("loop") in (True, "loop"),
            "animation_length": anim.get("length", 1),
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
