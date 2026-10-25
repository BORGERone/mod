"""Лист-превью к галерее: несколько кадров анимаций, отрисованных так же,
как их показывает встроенный WebGL-просмотрщик.

    python3 tools/render_gallery_sheet.py ivy_oleander_gallery.html preview_gallery.png

Поза считается тем же способом, что и в `AV` внутри страницы (мир = W_anim *
W_rest^-1, поворот ZYX, значения ключей со сдвигом знака по x/y — как их
применяет просмотрщик), поэтому картинка совпадает с тем, что видно в 3D.
"""
import base64
import io
import json
import math
import os
import struct
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'


# ------------------------------------------------------------------ данные
def read_mob(html_path):
    s = open(html_path, encoding='utf-8').read()
    i = s.index('const MOBS = ') + len('const MOBS = ')
    while s[i] not in '[':
        i += 1
    depth, j, ins, esc = 0, i, None, False
    while j < len(s):
        c = s[j]
        if ins:
            if esc:
                esc = False
            elif c == '\\':
                esc = True
            elif c == ins:
                ins = None
        elif c in '"\'':
            ins = c
        elif c == '[':
            depth += 1
        elif c == ']':
            depth -= 1
            if depth == 0:
                break
        j += 1
    return json.loads(s[i:j + 1])[0]


def _b64(s):
    return base64.b64decode(s)


def unpack(mob):
    g = struct.unpack('<%dh' % (len(_b64(mob['g'])) // 2), _b64(mob['g']))
    u = struct.unpack('<%dH' % (len(_b64(mob['u'])) // 2), _b64(mob['u']))
    qb = struct.unpack('<%dH' % (len(_b64(mob['qb'])) // 2), _b64(mob['qb']))
    nq = len(qb)
    pos = np.zeros((nq * 6, 3), np.float32)
    uv = np.zeros((nq * 6, 2), np.float32)
    for q in range(nq):
        v = [(-g[q * 12 + k * 3] / 32.0, -g[q * 12 + k * 3 + 1] / 32.0, g[q * 12 + k * 3 + 2] / 32.0)
             for k in range(4)]
        t = [((u[q * 8 + k * 2] / 16.0) / mob['w'], (u[q * 8 + k * 2 + 1] / 16.0) / mob['h'])
             for k in range(4)]
        for j, k in enumerate((0, 1, 2, 0, 2, 3)):
            pos[q * 6 + j] = v[k]
            uv[q * 6 + j] = t[k]
    return pos, uv, np.array(qb, np.int32)


# ------------------------------------------------------------------ поза
def _rot(rx, ry, rz):
    rx, ry, rz = (math.radians(a) for a in (rx, ry, rz))
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    return np.array([cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx,
                     sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx,
                     -sy, cy * sx, cy * cx], np.float64).reshape(3, 3)


def _local(pos, rot, scl):
    R = _rot(*rot)
    if scl is not None:
        R = R * np.array(scl, np.float64)[None, :]
    M = np.eye(4)
    M[:3, :3] = R
    M[:3, 3] = pos
    return M


def _worlds(bones, deltas):
    n = len(bones)
    W = [None] * n

    def get(i):
        if W[i] is not None:
            return W[i]
        b = bones[i]
        p = b[1]
        if 0 <= p < n:
            pb = bones[p]
            pos = [b[2] - pb[2], b[3] - pb[3], b[4] - pb[4]]
            parent = get(p)
        else:
            pos = [b[2], b[3], b[4]]
            parent = None
        rot = [b[5], b[6], b[7]]
        scl = None
        d = deltas.get(i)
        if d:
            # та же раскладка знаков, что в worlds() внутри страницы
            if 'r' in d:
                rot = [rot[0] - d['r'][0], rot[1] - d['r'][1], rot[2] + d['r'][2]]
            if 'p' in d:
                pos = [pos[0] - d['p'][0], pos[1] + d['p'][1], pos[2] + d['p'][2]]
            if 's' in d:
                scl = d['s']
        M = _local(pos, rot, scl)
        W[i] = M if parent is None else parent @ M
        return W[i]

    return [get(i) for i in range(n)]


def _sample(kfs, t):
    kfs = kfs
    bi = -1
    for i, k in enumerate(kfs):
        if k[0] <= t + 1e-6:
            bi = i
        else:
            break
    if bi < 0:
        return np.array(kfs[0][1] or kfs[0][2], float)
    b = kfs[bi]
    vb = np.array(b[2], float)
    if bi == len(kfs) - 1:
        return vb
    a = kfs[bi + 1]
    va = np.array(a[1] or a[2], float)
    if b[3] == 's':
        return vb
    dt = a[0] - b[0]
    al = (t - b[0]) / dt if dt > 0 else 1.0
    return vb + (va - vb) * al


def skin(mob, anim=None, t=0.0):
    """Мировые матрицы скиннинга для кадра (как AV.skins/blendSkins)."""
    bones = mob['r']['b']
    n = len(bones)
    rest = _worlds(bones, {})
    inv_rest = [np.linalg.inv(M) for M in rest]
    if anim is None:
        return [np.eye(4) for _ in range(n)]
    deltas = {}
    by_name = {}
    for i, b in enumerate(bones):
        by_name.setdefault(b[0], i)
    for bone, ch in anim[3].items():
        i = by_name.get(bone)
        if i is None:
            continue
        d = {}
        for key, chn in ch.items():
            d[key] = _sample(chn, t)
        deltas[i] = d
    posed = _worlds(bones, deltas)
    return [posed[i] @ inv_rest[i] for i in range(n)]


def pose(pos, qb, skin_mats, yoff):
    out = pos.copy()
    for q in range(len(qb)):
        b = qb[q] - 1
        if b < 0:
            continue
        M = skin_mats[b]
        s = slice(q * 6, q * 6 + 6)
        p = out[s].copy()
        p[:, 1] += yoff
        out[s] = p @ M[:3, :3].T + M[:3, 3]
        out[s, 1] -= yoff
    return out


# ------------------------------------------------------------------ растр
def render(pos, uv, tex, size=300, yaw=math.pi, pitch=0.12, ss=3, light=(0.35, 0.8, 0.5)):
    T = np.array(tex).astype(np.float32) / 255.0
    th, tw = T.shape[:2]
    cy, sy = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)
    R = np.array([[cy, 0, sy], [sp * sy, cp, -sp * cy], [-cp * sy, sp, cp * cy]], np.float32)
    v = pos @ R.T
    nq = len(pos) // 6
    mn, mx = v.min(0), v.max(0)
    k = size * 0.9 / max(mx[0] - mn[0], mx[1] - mn[1], 1e-4)
    cx, cyy = (mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2
    S = size * ss
    img = np.zeros((S, S, 4), np.float32)
    zb = np.full((S, S), -1e9, np.float32)
    L = np.array(light, np.float32)
    L /= np.linalg.norm(L)
    for f in range(nq):
        p = v[f * 6:f * 6 + 6]
        t = uv[f * 6:f * 6 + 6]
        a = p[1] - p[0]
        b = p[2] - p[0]
        n = np.cross(a, b)
        nl = np.linalg.norm(n)
        if nl < 1e-9:
            continue
        sh = 0.5 + 0.5 * abs(float(n / nl @ L))
        xy = np.stack([(p[:, 0] - cx) * k + size / 2, (-(p[:, 1] - cyy)) * k + size / 2], 1) * ss
        for tri in ((0, 1, 2), (3, 4, 5)):
            xs, ys, zs = xy[list(tri), 0], xy[list(tri), 1], p[list(tri), 2]
            x0, x1 = int(max(0, math.floor(xs.min()))), int(min(S - 1, math.ceil(xs.max())))
            y0, y1 = int(max(0, math.floor(ys.min()))), int(min(S - 1, math.ceil(ys.max())))
            if x1 < x0 or y1 < y0:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            den = ((ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2]))
            if abs(den) < 1e-9:
                continue
            w0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / den
            w1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / den
            w2 = 1 - w0 - w1
            m = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
            if not m.any():
                continue
            z = w0 * zs[0] + w1 * zs[1] + w2 * zs[2]
            sub = zb[y0:y1 + 1, x0:x1 + 1]
            upd = m & (z > sub)
            if not upd.any():
                continue
            uu = w0 * t[tri[0], 0] + w1 * t[tri[1], 0] + w2 * t[tri[2], 0]
            vv = w0 * t[tri[0], 1] + w1 * t[tri[1], 1] + w2 * t[tri[2], 1]
            px = np.clip((uu * tw).astype(int), 0, tw - 1)
            py = np.clip((vv * th).astype(int), 0, th - 1)
            keep = upd & (T[py, px, 3] > 0.5)
            sub[keep] = z[keep]
            tgt = img[y0:y1 + 1, x0:x1 + 1]
            tgt[keep, :3] = T[py, px, :3][keep] * sh
            tgt[keep, 3] = 1.0
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), 'RGBA')
    return im.resize((size, size), Image.LANCZOS)


def main():
    html = sys.argv[1] if len(sys.argv) > 1 else 'ivy_oleander_gallery.html'
    out = sys.argv[2] if len(sys.argv) > 2 else 'preview_gallery.png'
    mob = read_mob(html)
    pos, uv, qb = unpack(mob)
    tex = Image.open(io.BytesIO(_b64(mob['t']))).convert('RGBA')
    yoff = 0 if mob['r']['f'] == 'b' else 24

    def find(suffix):
        for a in mob['a']:
            if a[0].endswith('.' + suffix):
                return a
        return None

    shots = [('Поза покоя', None, 0.0, math.pi)]
    for nm, t, yaw in (('walk', 0.22, 2.1), ('greetings', 0.8, 2.6), ('sit', 0.0, 2.6), ('die', 1.42, 2.4)):
        a = find(nm)
        if a:
            shots.append((nm, a, t, yaw))
    shots.append(('idle', find('idle'), 1.25, math.pi * 0.62))

    W, PAD = 300, 8
    cols = 3
    rows = (len(shots) + cols - 1) // cols
    canvas = Image.new('RGB', (cols * W + PAD * (cols + 1), rows * (W + 30) + PAD), (18, 19, 23))
    dr = ImageDraw.Draw(canvas)
    font = ImageFont.truetype(FONT, 15) if os.path.exists(FONT) else None
    for i, (label, anim, t, yaw) in enumerate(shots):
        S = skin(mob, anim, t)
        p = pose(pos, qb, S, yoff)
        im = render(p, uv, tex, W, yaw)
        bg = Image.new('RGBA', im.size, (24, 26, 31, 255))
        bg.alpha_composite(im)
        x = PAD + (i % cols) * (W + PAD)
        y = PAD + (i // cols) * (W + 30)
        canvas.paste(bg.convert('RGB'), (x, y + 22))
        dr.text((x + 4, y + 4), label, fill=(226, 228, 236), font=font)
    canvas.save(out)
    print('готово:', out, canvas.size)


if __name__ == '__main__':
    main()
