"""Procedural set dressing for the beach scene.

Everything is built from plain boxes so it goes through the same renderer as the
bbmodel characters. Textures are generated (no downloads) into a small atlas and
addressed with a handful of fixed UV tiles.
"""
import numpy as np

from bbmodel import Element

TILE = 16
ATLAS_N = 8
ATLAS_SIZE = TILE * ATLAS_N


def _tile(rnd, base, rough=0.10, stripes=None, vertical=False, speckle=0.0):
    img = np.zeros((TILE, TILE, 3), np.float32)
    img[:, :] = np.array(base, np.float32) / 255.0
    img += rnd.normal(0, 5.5, (TILE, TILE, 3)).astype(np.float32) / 255.0
    if stripes:
        c = np.array(stripes, np.float32) / 255.0
        for i in range(TILE):
            if (i // 2) % 2 == 0:
                if vertical:
                    img[:, i] = img[:, i] * 0.75 + c * 0.25
                else:
                    img[i, :] = img[i, :] * 0.75 + c * 0.25
    if speckle:
        for _ in range(int(speckle * TILE * TILE)):
            x, y = rnd.randint(0, TILE), rnd.randint(0, TILE)
            img[y, x] *= rnd.uniform(0.55, 1.25)
    return np.clip(img, 0, 1)


class Atlas:
    def __init__(self):
        rnd = np.random.RandomState(4)
        img = np.zeros((ATLAS_SIZE, ATLAS_SIZE, 3), np.float32)
        self.index = {}
        specs = [
            ("wall",   (232, 230, 220), dict(rough=0.06, stripes=(150, 140, 130))),
            ("plaster", (223, 214, 198), dict(speckle=0.10)),
            ("roof",   (150, 66, 58), dict(stripes=(96, 40, 36), vertical=False)),
            ("roof_d", (108, 52, 48), dict(rough=0.2)),
            ("wood",   (128, 92, 58), dict(stripes=(96, 66, 40), vertical=True)),
            ("wood_d", (92, 66, 44), dict(rough=0.2, stripes=(64, 44, 30), vertical=True)),
            ("leaf",   (86, 128, 70), dict(speckle=0.55)),
            ("leaf_d", (60, 96, 56), dict(speckle=0.7)),
            ("rock",   (150, 148, 142), dict(speckle=0.5)),
            ("rock_d", (112, 110, 106), dict(speckle=0.4)),
            ("grass",  (128, 140, 96), dict(rough=0.3)),
            ("sand_d", (188, 172, 140), dict(speckle=0.3)),
            ("grey",   (170, 172, 176), dict(rough=0.1)),
            ("dark",   (60, 56, 54), dict(rough=0.2)),
        ]
        for i, (name, base, kw) in enumerate(specs):
            tx, ty = (i % ATLAS_N) * TILE, (i // ATLAS_N) * TILE
            img[ty:ty + TILE, tx:tx + TILE] = _tile(rnd, base, **kw)
            self.index[name] = (tx // TILE, ty // TILE)
        self.textures = [img]


ATLAS = Atlas()


def _uv(tile):
    tx, ty = ATLAS.index[tile]
    u0, v0 = tx * TILE, ty * TILE
    return np.array([u0, v0, u0 + TILE, v0 + TILE], np.float64)


def cube(lo, hi, tile, origin=None, rot=(0, 0, 0)):
    uvs = _uv(tile)
    faces = {name: {"uv": uvs.copy(), "texture": 0}
             for name in ("north", "south", "east", "west", "up", "down")}
    el = Element({"name": tile, "from": list(lo), "to": list(hi),
                  "origin": list(origin if origin is not None else
                                 ((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2))})
    el.rot = rot
    el.faces = {}
    for name, f in faces.items():
        el.faces[name] = (f["uv"], 0)
    return el


def placed(cubes, pos, yaw=0.0):
    """Place model-space cubes (model units) into world blocks."""
    S = 1 / 16.0
    c, s = np.cos(np.deg2rad(yaw)), np.sin(np.deg2rad(yaw))
    M = np.eye(4)
    M[:3, :3] = S * np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    M[:3, 3] = np.asarray(pos, np.float64)
    return [(e, M) for e in cubes]


# ------------------------------------------------------------------ pieces --
def house(w=3.4, h=2.6, d=3.0, seed=0):
    """A small beach house: plaster walls, timber band, stepped pitched roof."""
    rnd = np.random.RandomState(seed)
    W, H, D = w * 16, h * 16, d * 16
    body_h = H * 0.70
    out = [
        cube((-W / 2, 0, -D / 2), (W / 2, body_h, D / 2), "wall"),
        cube((-W / 2 - 1.5, body_h - 3, -D / 2 - 1.5), (W / 2 + 1.5, body_h, D / 2 + 1.5), "wood_d"),
    ]
    # roof as a stack of shrinking slabs -> reads as a pitched roof far away
    steps = 5
    for i in range(steps):
        f = i / steps
        y0 = body_h + f * H * 0.30
        y1 = y0 + H * 0.30 / steps + 0.5
        inset = f * W * 0.34
        out.append(cube((-W / 2 - 5 + inset, y0, -D / 2 - 5 + inset * D / W),
                        (W / 2 + 5 - inset, y1, D / 2 + 5 - inset * D / W), "roof"))
    # door + windows on the front face
    out.append(cube((-7, 0, D / 2 - 0.6), (7, body_h * 0.86, D / 2 + 0.6), "wood"))
    for sx in (-W / 3.4, W / 3.4):
        out.append(cube((sx - 5, body_h * 0.40, D / 2 - 0.6),
                        (sx + 5, body_h * 0.40 + body_h * 0.44, D / 2 + 0.6), "grey"))
    return out


def palm(height=3.4, seed=0):
    rnd = np.random.RandomState(seed)
    H = height * 16
    out = []
    seg = 6
    lean = rnd.uniform(-1, 1) * 20
    for i in range(seg):
        y0, y1 = H * i / seg, H * (i + 1) / seg
        x0 = lean * (i / seg) ** 2
        x1 = lean * ((i + 1) / seg) ** 2
        out.append(cube((x0 - 2.0, y0, -2.0), (x1 + 2.0, y1, 2.0), "wood_d"))
    top_x, top_y = lean, H
    out.append(cube((top_x - 2.5, top_y - 3, -2.5), (top_x + 2.5, top_y, 2.5), "leaf_d"))
    for k in range(7):
        a = np.deg2rad(k * (360 / 7) + rnd.uniform(-12, 12))
        dx, dz = np.cos(a), np.sin(a)
        for j in range(1, 5):
            r = j * 4.2
            drop = 0.8 + 2.6 * (j ** 1.5)
            cx, cz = top_x + dx * r, dz * r
            out.append(cube((cx - 2.6, top_y - drop - 1.4, cz - 2.6),
                            (cx + 2.6, top_y - drop + 1.4, cz + 2.6), "leaf" if j % 2 else "leaf_d"))
    return out


def rock(size=0.5, seed=0):
    rnd = np.random.RandomState(seed)
    S = size * 16
    return [cube((-S, 0, -S * 0.8), (S, S * rnd.uniform(0.5, 0.9), S * 0.8), "rock"),
            cube((-S * 0.6, S * 0.2, -S * 0.5), (S * 0.75, S * 0.95, S * 0.55), "rock_d",
                 origin=(0, 0, 0), rot=(0, rnd.uniform(0, 60), 0))]


def driftwood(size=0.9, seed=0):
    rnd = np.random.RandomState(seed)
    L = size * 16
    return [cube((-L, 0, -2), (L, 4, 2), "wood_d", origin=(0, 2, 0),
                 rot=(0, rnd.uniform(-40, 40), rnd.uniform(-8, 8))),
            cube((-L * 0.4, 0, -1.5), (L * 0.25, 3, 1.5), "wood", origin=(0, 1, 0),
                 rot=(0, rnd.uniform(-30, 30), 0))]


def grass_tuft(n=3, seed=3):
    rnd = np.random.RandomState(seed)
    out = []
    for _ in range(n):
        x, z = rnd.uniform(-0.28, 0.28), rnd.uniform(-0.28, 0.28)
        h = rnd.uniform(1.4, 3.0)
        out.append(cube((x * 16 - 0.30, 0, z * 16 - 0.30), (x * 16 + 0.30, h, z * 16 + 0.30), "grass",
                        origin=(x * 16, 0, z * 16), rot=(rnd.uniform(-22, 22), 0, rnd.uniform(-22, 22))))
    return out


def ridge(seed=0):
    """Rolling green hills far behind the village."""
    rnd = np.random.RandomState(seed)
    out = []
    for i in range(8):
        x = (-40 + i * 6.0) * 16
        h = rnd.uniform(1.3, 2.6) * 16
        z = (-27 + 2.0 * np.sin(i * 0.7)) * 16
        out.append(cube((x, 0, z), (x + 7.0 * 16, h * 0.7, z + 8.0 * 16), "leaf_d"))
        out.append(cube((x + 1.0 * 16, 0, z + 1.4 * 16),
                        (x + 6.0 * 16, h, z + 6.8 * 16), "leaf"))
    return out


def jetty(seed=0):
    rnd = np.random.RandomState(seed)
    out = []
    for i in range(14):
        x = (-9.0 + i * 1.5) * 16
        if rnd.rand() < 0.2:
            continue
        h = rnd.uniform(0.4, 0.8) * 16
        out.append(cube((x, 0, 0), (x + 3.0, h, 3.0), "wood_d"))
    return out


def build_scene():
    S = 1 / 16.0
    items = []
    layout = [(-16.0, -15.5, 3.2, 2.5, 2.9), (-11.0, -19.0, 3.8, 2.8, 3.2),
              (-6.2, -16.5, 2.9, 2.3, 2.7), (-1.4, -20.0, 3.4, 2.6, 3.0),
              (-21.0, -22.0, 4.2, 3.0, 3.4), (-24.0, -17.0, 3.0, 2.4, 2.8),
              (3.4, -22.5, 3.6, 2.7, 3.1)]
    for i, (x, z, sx, sy, sz) in enumerate(layout):
        items += placed(house(sx, sy, sz, seed=i), (x, 0, z), yaw=np.random.RandomState(i).uniform(-16, 16))
    for i, (x, z) in enumerate([(-14.0, -12.5), (-3.5, -13.5), (-9.0, -21.5), (5.5, -18.0)]):
        items += placed(palm(3.2 + 0.4 * i, seed=10 + i), (x, 0, z))
    items += placed(ridge(), (0, 0, 0))
    for i, (x, z, s) in enumerate([(-3.8, 2.6, 0.45), (3.2, -2.0, 0.35), (-2.6, 5.6, 0.28),
                                   (4.6, 3.6, 0.4), (-5.2, -1.0, 0.32)]):
        items += placed(rock(s, seed=20 + i), (x, 0, z), yaw=i * 33.0)
    for i, (x, z, s) in enumerate([(2.4, 3.4, 0.9), (-3.0, -3.2, 0.7), (5.4, -1.2, 0.6)]):
        items += placed(driftwood(s, seed=30 + i), (x, 0, z), yaw=i * 27.0)
    rnd = np.random.RandomState(7)
    for i in range(26):
        x, z = rnd.uniform(-9, 9), rnd.uniform(-11, 7)
        items += placed(grass_tuft(seed=40 + i), (x, 0, z))
    for i, (x, z) in enumerate([(-6.0, -34.0), (6.0, -35.0), (0.0, -40.0)]):
        items += placed(jetty(50 + i), (x, -0.4, z))
    return [(e, M) for e, M in items]


SCENE = build_scene()
