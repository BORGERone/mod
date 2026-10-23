#!/usr/bin/env python3
"""Convert a Bedrock .geo.json so GeckoLib (Java) can load and render it.

Why this exists
---------------
Bedrock entity models may contain ``poly_mesh`` bones (free-form quads).
GeckoLib has two problems with them:
  1. Its PolyMesh deserializer expects *flat* double arrays, while Bedrock
     writes nested ``[[x, y, z], ...]`` - the game crashes on load with
     "Array must have size 1, but has size 3".
  2. Even when parsed, GeckoLib's BakedModelFactory only bakes *cubes* -
     poly_mesh geometry is silently never rendered.

So this converter rewrites every poly_mesh quad as a flat (zero-thickness)
cube carrying UV on exactly one face. GeckoLib skips faces without UV,
so each converted quad renders once, exactly where Bedrock put it.
Axis-aligned quads (the vast majority in practice) convert losslessly;
tilted quads are projected onto their dominant axis (small cosmetic loss
on things like jaw/teeth angles).

Usage:  python3 tools/convert_bedrock_geo.py <in.geo.json> <out.geo.json>
"""
import json
import sys

FACES = {  # (axis, sign) -> Bedrock/GeckoLib face name
    (0, 1): "east", (0, -1): "west",
    (1, 1): "up", (1, -1): "down",
    (2, 1): "south", (2, -1): "north",
}


def quad_to_cube(positions, uvs, poly, tex_w, tex_h, normalized):
    """Turn one poly_mesh polygon into a flat GeckoLib cube (or None to skip)."""
    pts = [positions[v[0]] for v in poly]
    tex = [uvs[v[2]] for v in poly]

    # Normal from winding order (Newell-ish, first three points are enough here).
    ax = [pts[1][i] - pts[0][i] for i in range(3)]
    bx = [pts[2][i] - pts[0][i] for i in range(3)]
    normal = [
        ax[1] * bx[2] - ax[2] * bx[1],
        ax[2] * bx[0] - ax[0] * bx[2],
        ax[0] * bx[1] - ax[1] * bx[0],
    ]
    axis = max(range(3), key=lambda i: abs(normal[i]))
    if normal[axis] == 0:
        return None
    face = FACES[(axis, 1 if normal[axis] > 0 else -1)]

    origin = [min(p[i] for p in pts) for i in range(3)]
    size = [max(p[i] for p in pts) - origin[i] for i in range(3)]
    size[axis] = 0  # flat: only the chosen face has UV, the rest are skipped by GeckoLib
    if any(size[i] == 0 for i in range(3) if i != axis):
        return None  # degenerate line/point

    us = [t[0] * tex_w if normalized else t[0] for t in tex]
    vs = [t[1] * tex_h if normalized else t[1] for t in tex]
    uv = [min(us), min(vs)]
    uv_size = [max(us) - min(us), max(vs) - min(vs)]

    rnd = lambda vals: [round(float(v), 4) for v in vals]  # noqa: E731
    return {
        "origin": rnd(origin),
        "size": rnd(size),
        "uv": {face: {"uv": rnd(uv), "uv_size": rnd(uv_size)}},
    }


def convert(data):
    stats = {"bones": 0, "quads": 0, "skipped": 0}
    for geo in data.get("minecraft:geometry", []):
        desc = geo.get("description", {})
        tex_w = desc.get("texture_width", 16)
        tex_h = desc.get("texture_height", 16)
        for bone in geo.get("bones", []):
            # Bedrock shows these only via render-controller part_visibility
            # (ud:broken_* properties). GeckoLib has no render controllers, so
            # bake the default state in: broken variants start hidden and the
            # limb-loss system unhides them at runtime via GeoBone.setHidden.
            if bone.get("name", "").startswith("broken_"):
                bone["neverRender"] = True
            mesh = bone.pop("poly_mesh", None)
            if not mesh:
                continue
            stats["bones"] += 1
            normalized = mesh.get("normalized_uvs", False)
            cubes = bone.setdefault("cubes", [])
            for poly in mesh.get("polys", []):
                cube = quad_to_cube(mesh["positions"], mesh["uvs"], poly, tex_w, tex_h, normalized)
                if cube is None:
                    stats["skipped"] += 1
                else:
                    cubes.append(cube)
                    stats["quads"] += 1
    return stats


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    with open(sys.argv[1]) as f:
        data = json.load(f)
    stats = convert(data)
    with open(sys.argv[2], "w") as f:
        json.dump(data, f, indent="\t")
        f.write("\n")
    print(f"converted: {stats['bones']} poly_mesh bones -> {stats['quads']} flat cubes, "
          f"{stats['skipped']} degenerate quads skipped")


if __name__ == "__main__":
    main()
