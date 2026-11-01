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
            ("grass",  (118, 132, 86), dict(rough=0.3)),
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
    """A beach house with a gable roof and a door/window hint."""
    rnd = np.random.RandomState(seed)
    W, H, D = w * 16, h * 16, d * 16
    out = [
        cube((-W / 2, 0, -D / 2), (W / 2, H * 0.72, D / 2), "wall"),
        cube((-W / 2 - 2, H * 0.72 - 6, -D / 2 - 2), (W / 2 + 2, H * 0.72, D / 2 + 2), "wood_d"),
    ]
    # gable ends as stacked shrinking slabs
    steps = 5
    for i in range(steps):
        f = (i + 1) / steps
        y = H * 0.72 + f * H * 0.30
        out.append(cube((-W / 2 + f * W * 0.5, y, -D / 2 - 1),
                        (W / 2 - f * W * 0.35, y + H * 0.07, D / 2 + 1), "plaster"))
    # roof slopes
    pitch = 34.0
    lift = H * 0.72 + 3
    for sign in (-1, 1):
        out.append(cube((-W / 2 - 4, lift - 2, sign * D * 0.02), (W / 2 + 4, lift + 3, sign * (D / 2 + 8)),
                        "roof", origin=(0, lift + 2, 0), rot=(sign * pitch, 0, 0)))
    # door + two windows on the front face
    out.append(cube((-7, 0, D / 2), (7, 22, D / 2 + 1), "wood"))
    for sx in (-W / 3.4, W / 3.4):
        out.append(cube((sx - 5, H * 0.34, D / 2), (sx + 5, H * 0.34 + 11, D / 2 + 1), "grey"))
        out.append(cube((sx - 1, H * 0.34 - 1, D / 2 + 1), (sx + 1, H * 0.34 + 12, D / 2 + 1.4), "wood_d"))
    return out


def palm(height=3.4, seed=0):
    rnd = np.random.RandomState(seed)
    H = height * 16
    out = []
    seg = 6
    lean = rnd.uniform(-1, 1) * 24
    for i in range(seg):
        y0, y1 = H * i / seg, H * (i + 1) / seg
        x0 = lean * (i / seg) ** 2
        x1 = lean * ((i + 1) / seg) ** 2
        out.append(cube((x0 - 2.2, y0, -2.2), (x1 + 2.2, y1, 2.2), "wood_d"))
    top_x, top_y = lean, H
    for k in range(7):
        a = k * (360 / 7)
        out.append(cube((top_x - 1.5, top_y - 1.5, -1.5), (top_x + 1.5, top_y + 1.5, 1.5), "leaf_d"))
        out.append(cube((top_x - 20, top_y - 1, -5), (top_x + 20, top_y + 1, 5), "leaf",
                        origin=(top_x, top_y, 0), rot=(0, a, 12)))
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
        h = rnd.uniform(1.6, 3.4)
        out.append(cube((x * 16 - 0.4, 0, z * 16 - 0.4), (x * 16 + 0.4, h, z * 16 + 0.4), "grass",
                        origin=(x * 16, 0, z * 16), rot=(rnd.uniform(-9, 9), 0, rnd.uniform(-9, 9))))
    return out


def ridge(seed=0):
    """Rolling green hills far behind the village."""
    rnd = np.random.RandomState(seed)
    out = []
    for i in range(10):
        x = -24 + i * 5.4
        h = rnd.uniform(1.6, 3.6)
        z = -34 + 2.4 * np.sin(i * 0.7)
        out.append(cube((x, 0, z), (x + 7.0, h * 0.7, z + 8.0), "leaf_d"))
        out.append(cube((x + 1.0, 0, z + 1.4), (x + 6.0, h, z + 6.8), "leaf"))
    return out


def jetty(seed=0):
    rnd = np.random.RandomState(seed)
    out = []
    for i in range(12):
        x = -8.0 + i * 1.6
        if rnd.rand() < 0.25:
            continue
        h = rnd.uniform(0.35, 0.7)
        out.append(cube((x, 0, 0), (x + 3.2, h * 16, 3.2), "wood_d"))
    return out


def build_scene():
    S = 1 / 16.0
    items = []
    layout = [(-9.6, -16.5, 3.4, 2.7, 3.0), (-4.4, -19.6, 4.2, 3.1, 3.4),
              (0.8, -17.2, 3.0, 2.4, 2.8), (6.0, -20.4, 3.8, 2.9, 3.2),
              (10.9, -16.8, 3.2, 2.5, 3.0), (15.6, -19.8, 4.0, 3.0, 3.3),
              (-15.2, -22.4, 4.6, 3.2, 3.6)]
    for i, (x, z, sx, sy, sz) in enumerate(layout):
        items += placed(house(sx, sy, sz, seed=i), (x, 0, z), yaw=np.random.RandomState(i).uniform(-16, 16))
    for i, (x, z) in enumerate([(-7.6, -13.6), (9.0, -14.8), (-1.2, -24.0)]):
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
