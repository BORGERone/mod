"""Minimal Blockbench (.bbmodel) loader + skeletal animation sampler.

Everything is kept in *model units* (1/16 block) until it is placed by the
caller, which multiplies by UNIT.
"""
import base64
import io
import json

import numpy as np
from PIL import Image

UNIT = 1.0 / 16.0
D2R = np.pi / 180.0


def rot_matrix(x, y, z):
    """Blockbench euler -> 4x4 (degrees, applied X then Y then Z)."""
    rx, ry, rz = np.deg2rad([x, y, z])
    cx, sx = np.cos(rx), np.sin(rx)
    cy, sy = np.cos(ry), np.sin(ry)
    cz, sz = np.cos(rz), np.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]], np.float64)
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], np.float64)
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]], np.float64)
    M = np.eye(4)
    M[:3, :3] = Rz @ Ry @ Rx
    return M


def translate(v):
    M = np.eye(4)
    M[:3, 3] = np.asarray(v, np.float64)
    return M


def pivot_rot(origin, rot):
    o = np.asarray(origin, np.float64)
    return translate(o) @ rot_matrix(*rot) @ translate(-o)


# ---------------------------------------------------------------- geometry --
_FACE_CORNERS = {
    # name: (order of corners, winding normal)
    "north": [(0, 1, 0), (1, 1, 0), (1, 0, 0), (0, 0, 0)],   # -Z
    "south": [(1, 1, 1), (0, 1, 1), (0, 0, 1), (1, 0, 1)],   # +Z
    "east":  [(1, 1, 0), (1, 1, 1), (1, 0, 1), (1, 0, 0)],   # +X
    "west":  [(0, 1, 1), (0, 1, 0), (0, 0, 0), (0, 0, 1)],   # -X
    "up":    [(0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)],   # +Y
    "down":  [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)],   # -Y
}
_NORMALS = {
    "north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0),
    "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0),
}


class Element:
    __slots__ = ("name", "lo", "hi", "origin", "rot", "faces")

    def __init__(self, e):
        self.name = e.get("name", "")
        self.lo = np.array(e["from"], np.float64)
        self.hi = np.array(e["to"], np.float64)
        lo = np.minimum(self.lo, self.hi)
        hi = np.maximum(self.lo, self.hi)
        self.lo, self.hi = lo, hi
        self.origin = np.array(e.get("origin", (lo + hi) / 2), np.float64)
        self.rot = tuple(e.get("rotation", (0, 0, 0)))
        self.faces = {}
        for name, f in (e.get("faces") or {}).items():
            if name not in _FACE_CORNERS:
                continue
            uv = f.get("uv")
            if uv is None:
                continue
            self.faces[name] = (np.array(uv, np.float64), int(f.get("texture", 0) or 0))


class Bone:
    __slots__ = ("name", "uuid", "origin", "rot", "children", "cubes")

    def __init__(self, name, uuid, origin, rot=(0, 0, 0)):
        self.name = name
        self.uuid = uuid
        self.origin = np.array(origin, np.float64)
        self.rot = tuple(rot)
        self.children = []
        self.cubes = []


class Model:
    def __init__(self, path):
        d = json.load(open(path, "r", encoding="utf-8"))
        self.name = d.get("name", "model")
        self.elements = {}
        by_uuid = {}
        for e in d.get("elements", []):
            if e.get("type", "cube") not in ("cube", "cube_"):
                continue
            el = Element(e)
            by_uuid[e["uuid"]] = el
            self.elements[e["uuid"]] = el
        self.textures = []
        for t in d.get("textures", []):
            src = t.get("source", "")
            if src.startswith("data:image"):
                raw = base64.b64decode(src.split(",", 1)[1])
                img = Image.open(io.BytesIO(raw)).convert("RGBA")
                arr = np.asarray(img, np.float32) / 255.0
                # Blockbench textures are authored dark; lift them so the
                # sunlit sand does not swallow the model.
                rgb = np.clip(arr[..., :3] * 1.30, 0, 1) ** 0.98
                arr = np.dstack([rgb, arr[..., 3]])
                self.textures.append(arr.astype(np.float32))
            else:
                self.textures.append(np.ones((64, 64, 4), np.float32))
        self.roots = []
        for node in d.get("outliner", []):
            self.roots.append(self._bone(node, by_uuid))
        self.animations = {a["name"]: a for a in d.get("animations", [])}

    def _bone(self, node, by_uuid):
        if isinstance(node, str):
            b = Bone("__cube__", node, (0, 0, 0))
            if node in by_uuid:
                b.cubes.append(by_uuid[node])
            return b
        b = Bone(node.get("name", "bone"), node.get("uuid", ""),
                 node.get("origin", (0, 0, 0)), node.get("rotation", (0, 0, 0)))
        for ch in node.get("children", []):
            c = self._bone(ch, by_uuid)
            if c.cubes and c.name == "__cube__":
                b.cubes.extend(c.cubes)
            else:
                b.children.append(c)
        return b

    # ------------------------------------------------------------ animation --
    def sample(self, anim_name, t):
        a = self.animations.get(anim_name)
        if a is None:
            return {}
        length = float(a.get("length") or 1.0)
        if a.get("loop") == "loop" and length > 0:
            t = t % length
        out = {}
        for uuid, anim in a.get("animators", {}).items():
            bone = {"rot": np.zeros(3), "pos": np.zeros(3)}
            prev = {}
            for kf in anim.get("keyframes", []):
                ch = kf.get("channel")
                if ch not in ("rotation", "position"):
                    continue
                prev.setdefault(ch, []).append(kf)
            for ch, keys in prev.items():
                keys = sorted(keys, key=lambda k: k["time"])
                vals = []
                for k in keys:
                    dp = k["data_points"][0]
                    vals.append((k["time"], np.array([dp["x"], dp["y"], dp["z"]], np.float64)))
                if t <= vals[0][0]:
                    v = vals[0][1]
                elif t >= vals[-1][0]:
                    v = vals[-1][1]
                else:
                    for i in range(len(vals) - 1):
                        t0, v0 = vals[i]
                        t1, v1 = vals[i + 1]
                        if t0 <= t <= t1:
                            f = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
                            v = v0 + (v1 - v0) * f
                            break
                bone["rot" if ch == "rotation" else "pos"] = v
            out[uuid] = bone
        return out

    # ------------------------------------------------------------- hierarchy --
    def walk(self, values=None, roots=None):
        """Return [(element, 4x4 world matrix in model units), ...]."""
        values = values or {}
        placed = []
        for b in (roots if roots is not None else self.roots):
            self._walk_bone(b, np.eye(4), values, placed)
        return placed

    def _walk_bone(self, b, parent, values, out):
        v = values.get(b.uuid) or {}
        rot = np.array(b.rot, np.float64) + v.get("rot", np.zeros(3))
        pos = np.asarray(b.origin, np.float64) + v.get("pos", np.zeros(3))
        M = parent @ translate(pos) @ rot_matrix(*rot) @ translate(-np.asarray(b.origin, np.float64))
        for cube in b.cubes:
            Mc = M @ pivot_rot(cube.origin, cube.rot)
            out.append((cube, Mc))
        for c in b.children:
            self._walk_bone(c, M, values, out)


# ---------------------------------------------------------------- utilities --
def cube_faces(cube):
    """Yield (name, quad(4,3), uv(4,2), normal). UV is in texture pixels."""
    lo, hi = cube.lo, cube.hi
    span = hi - lo
    for name, corners in _FACE_CORNERS.items():
        f = cube.faces.get(name)
        if f is None:
            continue
        uv, _tex = f
        quad = np.array([[lo[0] + c[0] * span[0],
                          lo[1] + c[1] * span[1],
                          lo[2] + c[2] * span[2]] for c in corners], np.float64)
        u1, v1, u2, v2 = uv
        uvs = np.array([[u1, v1], [u2, v1], [u2, v2], [u1, v2]], np.float64)
        yield name, quad, uvs, np.array(_NORMALS[name], np.float64)


def bbox_world(placed):
    pts = []
    for cube, M in placed:
        for _n, quad, _uv, _n2 in cube_faces(cube):
            pts.append((M[:3, :3] @ quad.T).T + M[:3, 3])
    if not pts:
        return None
    p = np.concatenate(pts, axis=0)
    return p.min(axis=0), p.max(axis=0)
