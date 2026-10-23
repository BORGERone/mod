#!/usr/bin/env python3
"""Convert a Bedrock .geo.json for use with GeckoLib (Java) + our PolyMesh renderer.

Why this exists
---------------
Bedrock entity models may contain ``poly_mesh`` bones (free-form quads).
GeckoLib cannot handle them:
  1. Its PolyMesh deserializer expects *flat* double arrays, while Bedrock
     writes nested ``[[x, y, z], ...]`` - the game crashes on load with
     "Array must have size 1, but has size 3".
  2. Even when parsed, GeckoLib's BakedModelFactory only bakes *cubes* -
     poly_mesh geometry is silently never rendered.

(An earlier version of this script rewrote polys as flat cubes; that rendered
with wrong UV orientation on many faces. Do not bring that back.)

Current approach:
  * ``poly_mesh`` is *removed* from the .geo.json (GeckoLib keeps animating the
    bone, it just has no cubes), and written verbatim to a sidecar file
    ``<name>.polymesh.json`` next to the output. The mod's PolyMeshRenderer
    draws those quads itself, using GeckoLib's animated bone matrices -
    exact positions, per-vertex UVs and normals from Bedrock.
  * Bones named ``broken_*`` get ``"neverRender": true``: in Bedrock they are
    shown only via render-controller part_visibility (ud:broken_* == 1).
    GeckoLib loads neverRender bones hidden; the limb system unhides them
    at runtime with GeoBone.setHidden(false).

Usage:  python3 tools/convert_bedrock_geo.py <in.geo.json> <out.geo.json>
        (sidecar is written as <out basename>.polymesh.json beside the output)
"""
import json
import os
import sys


def convert(data):
    stats = {"bones": 0, "polys": 0}
    sidecar = {"bones": {}}
    for geo in data.get("minecraft:geometry", []):
        desc = geo.get("description", {})
        sidecar["texture_width"] = desc.get("texture_width", 16)
        sidecar["texture_height"] = desc.get("texture_height", 16)
        for bone in geo.get("bones", []):
            if bone.get("name", "").startswith("broken_"):
                bone["neverRender"] = True
            mesh = bone.pop("poly_mesh", None)
            if not mesh:
                continue
            stats["bones"] += 1
            stats["polys"] += len(mesh.get("polys", []))
            sidecar["bones"][bone["name"]] = mesh
    return stats, sidecar


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    with open(sys.argv[1]) as f:
        data = json.load(f)
    stats, sidecar = convert(data)
    with open(sys.argv[2], "w") as f:
        json.dump(data, f, indent="\t")
        f.write("\n")
    if stats["bones"]:
        base = os.path.basename(sys.argv[2])
        name = base[:-len(".geo.json")] if base.endswith(".geo.json") else os.path.splitext(base)[0]
        sidecar_path = os.path.join(os.path.dirname(os.path.abspath(sys.argv[2])), name + ".polymesh.json")
        with open(sidecar_path, "w") as f:
            json.dump(sidecar, f)
            f.write("\n")
        print(f"sidecar: {sidecar_path}")
    print(f"converted: {stats['bones']} poly_mesh bones, {stats['polys']} polys moved to sidecar")


if __name__ == "__main__":
    main()
