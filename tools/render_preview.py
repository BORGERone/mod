"""Tiny z-buffered software rasteriser for Blockbench .bbmodel files.

Renders the model with its embedded texture (or an external PNG) so texture
edits can be checked without opening Blockbench.

    python3 tools/render_preview.py model.bbmodel [texture.png|none] out.png [yaw] [pitch]

Requires numpy + Pillow.
"""
import base64
import io
import json
import math
import sys

import numpy as np
from PIL import Image

def load(model_path, tex_path=None):
    d = json.load(open(model_path))
    if tex_path:
        tex = Image.open(tex_path).convert('RGBA')
    else:
        src = d['textures'][0]['source']
        tex = Image.open(io.BytesIO(base64.b64decode(src.split(',', 1)[1]))).convert('RGBA')
    return d, tex

# corner00 = uv (umin,vmin); u_dir, v_dir unit vectors in model space
FACE_DEF = {
    # name: (corner_fn, u_axis, v_axis)
    'north': ((0, 1, 0), (1, 0, 0), (0, -1, 0)),
    'south': ((1, 1, 1), (-1, 0, 0), (0, -1, 0)),
    'east':  ((1, 1, 0), (0, 0, 1), (0, -1, 0)),
    'west':  ((0, 1, 1), (0, 0, -1), (0, -1, 0)),
    'up':    ((0, 1, 0), (1, 0, 0), (0, 0, 1)),
    'down':  ((0, 0, 1), (1, 0, 0), (0, 0, -1)),
}

def render(model_path, tex_path, out, yaw=30, pitch=10, size=800, zoom=1.0, ss=3):
    d, tex = load(model_path, tex_path)
    T = np.array(tex)
    H, W = T.shape[:2]
    quads = []
    for e in d['elements']:
        x0, y0, z0 = e['from']
        x1, y1, z1 = e['to']
        lo = (min(x0, x1), min(y0, y1), min(z0, z1))
        hi = (max(x0, x1), max(y0, y1), max(z0, z1))
        pts = [(lo[0], lo[1], lo[2]), (hi[0], lo[1], lo[2]), (lo[0], hi[1], lo[2]), (lo[0], lo[1], hi[2]),
               (hi[0], hi[1], lo[2]), (lo[0], hi[1], hi[2]), (hi[0], lo[1], hi[2]), (hi[0], hi[1], hi[2])]
        for fname, fv in e['faces'].items():
            if fv.get('texture') is None:
                continue
            uv = fv['uv']
            umin, umax = min(uv[0], uv[2]), max(uv[0], uv[2])
            vmin, vmax = min(uv[1], uv[3]), max(uv[1], uv[3])
            if umax - umin <= 0 or vmax - vmin <= 0:
                continue
            c00, ua, va = FACE_DEF[fname]
            # size of the face in model space along u and v
            sw = (hi[0] - lo[0]) * abs(ua[0]) + (hi[1] - lo[1]) * abs(ua[1]) + (hi[2] - lo[2]) * abs(ua[2])
            sh = (hi[0] - lo[0]) * abs(va[0]) + (hi[1] - lo[1]) * abs(va[1]) + (hi[2] - lo[2]) * abs(va[2])
            if sw <= 0 or sh <= 0:
                continue
            p00 = tuple(lo[i] if c00[i] == 0 else hi[i] for i in range(3))
            P = []
            UV = []
            for (su, sv) in ((0, 0), (1, 0), (1, 1), (0, 1)):
                p = tuple(p00[i] + ua[i] * sw * su + va[i] * sh * sv for i in range(3))
                P.append(p)
                UV.append((umin + (umax - umin) * su, vmin + (vmax - vmin) * sv))
            # normal
            n = np.cross(np.subtract(P[1], P[0]), np.subtract(P[3], P[0]))
            quads.append((P, UV, fname, n))
    # rotation matrices
    ry, rx = math.radians(yaw), math.radians(pitch)
    Ry = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
    Rx = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
    center = np.array([0, 17.0, 1.0])
    items = []
    for P, UV, fname, n in quads:
        Pw = [(Ry @ (np.array(p) - center)) for p in P]
        Pw = [Rx @ p for p in Pw]
        nw = Rx @ (Ry @ n)
        if nw[2] < -1e-6:   # backface
            continue
        items.append((Pw, UV, np.mean([p[2] for p in Pw])))
    allp = np.array([p for P, U, z in items for p in P])
    minx, maxx = allp[:, 0].min(), allp[:, 0].max()
    miny, maxy = allp[:, 1].min(), allp[:, 1].max()
    s = size * 0.9 / max(maxx - minx, maxy - miny) * zoom
    S = size * ss
    img = np.zeros((S, S, 4), dtype=np.uint8)
    zbuf = np.full((S, S), -1e9, dtype=float)
    def u8(p):
        return (((p[0] - (minx + maxx) / 2) * s + size / 2) * ss,
                (-(p[1] - (miny + maxy) / 2) * s + size / 2) * ss)
    items.sort(key=lambda it: it[2])
    for Pw, UV, z in items:
        P2 = np.array([u8(p) for p in Pw])
        # affine: uv from xy using 3 vertices
        A = np.array([[P2[0][0], P2[0][1], 1], [P2[1][0], P2[1][1], 1], [P2[2][0], P2[2][1], 1]], float)
        U = np.array([UV[0][0], UV[1][0], UV[2][0]], float)
        V = np.array([UV[0][1], UV[1][1], UV[2][1]], float)
        Z = np.array([Pw[0][2], Pw[1][2], Pw[2][2]], float)
        try:
            cu = np.linalg.solve(A, U)
            cv = np.linalg.solve(A, V)
            cz = np.linalg.solve(A, Z)
        except np.linalg.LinAlgError:
            continue
        bx0 = max(int(np.floor(P2[:, 0].min())), 0); bx1 = min(int(np.ceil(P2[:, 0].max())) + 1, S)
        by0 = max(int(np.floor(P2[:, 1].min())), 0); by1 = min(int(np.ceil(P2[:, 1].max())) + 1, S)
        if bx1 <= bx0 or by1 <= by0:
            continue
        xs, ys = np.meshgrid(np.arange(bx0, bx1) + 0.5, np.arange(by0, by1) + 0.5)
        uu = cu[0] * xs + cu[1] * ys + cu[2]
        vv = cv[0] * xs + cv[1] * ys + cv[2]

        def edge(a, b):
            return (b[0] - a[0]) * (ys - a[1]) - (b[1] - a[1]) * (xs - a[0])

        def inside(tri):
            a, b, c = [P2[i] for i in tri]
            e0, e1, e2 = edge(a, b), edge(b, c), edge(c, a)
            return ((e0 >= 0) & (e1 >= 0) & (e2 >= 0)) | ((e0 <= 0) & (e1 <= 0) & (e2 <= 0))

        zz = cz[0] * xs + cz[1] * ys + cz[2]
        mask = inside((0, 1, 2)) | inside((0, 2, 3))
        ui = np.clip(np.floor(uu).astype(int), 0, W - 1)
        vi = np.clip(np.floor(vv).astype(int), 0, H - 1)
        col = T[vi, ui]
        zr = zbuf[by0:by1, bx0:bx1]
        a = mask & (col[:, :, 3] > 0) & (zz >= zr)
        reg = img[by0:by1, bx0:bx1]
        reg[a] = col[a]
        zr[a] = zz[a]
        zbuf[by0:by1, bx0:bx1] = zr
        img[by0:by1, bx0:bx1] = reg
    out_img = Image.fromarray(img).resize((size, size), Image.LANCZOS)
    bg = Image.new('RGBA', out_img.size, (30, 30, 36, 255))
    bg.alpha_composite(out_img)
    bg.convert('RGB').save(out)
    return out

if __name__ == '__main__':
    model = sys.argv[1]
    texp = None if (len(sys.argv) < 3 or sys.argv[2] == 'none') else sys.argv[2]
    out = sys.argv[3] if len(sys.argv) > 3 else 'preview.png'
    yaw = float(sys.argv[4]) if len(sys.argv) > 4 else 30
    pitch = float(sys.argv[5]) if len(sys.argv) > 5 else 10
    print(render(model, texp, out, yaw, pitch))
