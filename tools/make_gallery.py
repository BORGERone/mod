"""Собрать 3D-галерею с одной моделью из .bbmodel.

    python3 tools/make_gallery.py --src work/Люди.html \
        --model ivy_oleander_villager.bbmodel --out ivy_oleander_gallery.html

Берёт готовую страницу-галерею, оставляет от неё весь движок (WebGL-просмотрщик,
панель анимаций, пресеты камеры, экспорт .bbmodel) и подменяет данные: MODS и
MOBS — одна запись с моделью из .bbmodel. Миниатюра для карточки рисуется тут же
из уже запечённой геометрии, поэтому карточка совпадает с 3D-видом.
"""
import argparse
import base64
import io
import json
import math
import os
import struct
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel_to_gallery as conv

THUMB = 190          # размер миниатюры в галерее
SS = 4               # суперсэмплинг


# ------------------------------------------------------------------ миниатюра
def _quads(mob):
    g = struct.unpack('<%dh' % (len(base64.b64decode(mob['g'])) // 2), base64.b64decode(mob['g']))
    u = struct.unpack('<%dH' % (len(base64.b64decode(mob['u'])) // 2), base64.b64decode(mob['u']))
    out = []
    for q in range(len(g) // 12):
        pos = [(-g[q * 12 + k * 3] / 32, -g[q * 12 + k * 3 + 1] / 32, g[q * 12 + k * 3 + 2] / 32)
               for k in range(4)]
        uv = [((u[q * 8 + k * 2] / 16) / mob['w'], (u[q * 8 + k * 2 + 1] / 16) / mob['h'])
              for k in range(4)]
        out.append((pos, uv))
    return out


def render_thumb(mob, size=THUMB, ss=SS, light=(0.35, 0.8, 0.5)):
    """Фронтальный ортографический рендер (как пресет «Спереди»)."""
    from PIL import Image
    tex = Image.open(io.BytesIO(base64.b64decode(mob['t']))).convert('RGBA')
    T = np.array(tex).astype(np.float32) / 255.0
    th, tw = T.shape[:2]
    quads = _quads(mob)

    # экранные координаты: x -> -x (как yaw = pi), y -> y, глубина = -z
    def proj(p):
        return (-p[0], p[1], -p[2])

    scr = [[proj(p) for p in pos] for pos, uv in quads]
    allp = np.array([p for s in scr for p in s])
    mn, mx = allp.min(0), allp.max(0)
    w, h = max(1e-4, mx[0] - mn[0]), max(1e-4, mx[1] - mn[1])
    k = size * 0.945 / max(w, h)
    cx, cy = (mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2

    S = size * ss
    img = np.zeros((S, S, 4), dtype=np.float32)
    zbuf = np.full((S, S), -1e9, dtype=np.float32)
    L = np.array(light, dtype=np.float32)
    L /= np.linalg.norm(L)

    for (pos, uv), s in zip(quads, scr):
        xy = np.array([((p[0] - cx) * k + size / 2) * ss for p in s])
        yy = np.array([(-(p[1] - cy) * k + size / 2) * ss for p in s])
        zz = np.array([p[2] for p in s])
        # нормаль в координатах модели
        a = np.subtract(pos[1], pos[0])
        b = np.subtract(pos[2], pos[0])
        n = np.cross(a, b)
        nl = np.linalg.norm(n)
        if nl < 1e-9:
            continue
        n /= nl
        sh = 0.5 + 0.5 * abs(float(np.dot(n, L)))
        uvv = np.array(uv, dtype=np.float32)
        for tri in ((0, 1, 2), (0, 2, 3)):
            xs, ys, zs = xy[list(tri)], yy[list(tri)], zz[list(tri)]
            x0, x1 = int(max(0, math.floor(xs.min()))), int(min(S - 1, math.ceil(xs.max())))
            y0, y1 = int(max(0, math.floor(ys.min()))), int(min(S - 1, math.ceil(ys.max())))
            if x1 < x0 or y1 < y0:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            d = ((ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2]))
            if abs(d) < 1e-9:
                continue
            w0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / d
            w1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / d
            w2 = 1.0 - w0 - w1
            m = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
            if not m.any():
                continue
            z = w0 * zs[0] + w1 * zs[1] + w2 * zs[2]
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            upd = m & (z > sub)
            if not upd.any():
                continue
            uu = w0 * uvv[tri[0], 0] + w1 * uvv[tri[1], 0] + w2 * uvv[tri[2], 0]
            vv = w0 * uvv[tri[0], 1] + w1 * uvv[tri[1], 1] + w2 * uvv[tri[2], 1]
            px = np.clip((uu * tw).astype(int), 0, tw - 1)
            py = np.clip((vv * th).astype(int), 0, th - 1)
            rgb = T[py, px, :3] * sh
            al = T[py, px, 3]
            keep = upd & (al > 0.5)
            sub[keep] = z[keep]
            img[y0:y1 + 1, x0:x1 + 1][keep, :3] = rgb[keep]
            img[y0:y1 + 1, x0:x1 + 1][keep, 3] = 1.0

    out = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), 'RGBA')
    out = out.resize((size, size), Image.LANCZOS)
    buf = io.BytesIO()
    out.save(buf, 'PNG', optimize=True)
    return base64.b64encode(buf.getvalue()).decode('ascii')


# ------------------------------------------------------------------ HTML
def _find_block(text, start):
    """Конец JS-литерала (объект/массив), начиная с индекса открывающей скобки."""
    open_ch = text[start]
    close_ch = {'{': '}', '[': ']'}[open_ch]
    depth = 0
    i = start
    in_str = None
    esc = False
    while i < len(text):
        c = text[i]
        if in_str:
            if esc:
                esc = False
            elif c == '\\':
                esc = True
            elif c == in_str:
                in_str = None
        elif c in '"\'':
            in_str = c
        elif c == open_ch:
            depth += 1
        elif c == close_ch:
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    raise ValueError('не найден конец литерала')


def _replace_literal(html, marker, value):
    i = html.index(marker)
    start = i + len(marker)
    while html[start] not in '{[':
        start += 1
    end = _find_block(html, start)
    return html[:start] + json.dumps(value, ensure_ascii=False, separators=(',', ':')) + html[end:]


def build(src, model_path, out, name=None, mod_slug=None, mod_title=None, rig_format=None):
    model = json.load(open(model_path, encoding='utf-8'))
    name = name or os.path.splitext(os.path.basename(model_path))[0]
    mod_slug = mod_slug or (model.get('meta', {}).get('format_version') and 'ivy_oleander')
    mob, nq = conv.convert(model, name, mod_slug, '', rig_format=rig_format)
    mob['th'] = render_thumb(mob)
    print('квадов: %d, костей: %d, анимаций: %d' % (nq, len(mob['r']['b']), len(mob['a'])))

    with open(src, encoding='utf-8') as f:
        html = f.read()

    html = _replace_literal(html, 'const MODS = ', {mod_slug: mod_title or 'Ivy Oleander'})
    html = _replace_literal(html, 'const MOBS = ', [mob])

    title = 'Ivy Oleander — 3D-просмотрщик модели Minecraft'
    html = html.replace('<title>Люди и NPC из модов Minecraft — 3D-галерея</title>',
                        '<title>%s</title>' % title)

    n_anim = len(mob['a'])
    if n_anim % 10 == 1 and n_anim % 100 != 11:
        word = 'анимация'
    elif n_anim % 10 in (2, 3, 4) and n_anim % 100 not in (12, 13, 14):
        word = 'анимации'
    else:
        word = 'анимаций'
    head = ('<h1>Ivy Oleander<small>селянин · 1 персонаж · 1 мод · '
            'нажми, чтобы покрутить в 3D · ⬇ — скачать .bbmodel · 🎞 %d %s</small></h1>'
            % (n_anim, word))
    old_i = html.index('<h1>')
    old_j = html.index('</h1>', old_i) + len('</h1>')
    html = html[:old_i] + head + html[old_j:]

    with open(out, 'w', encoding='utf-8') as f:
        f.write(html)
    print('готово: %s (%.2f МБ)' % (out, os.path.getsize(out) / 1048576))
    return mob


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--src', required=True)
    ap.add_argument('--model', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--name')
    ap.add_argument('--mod', default='ivy_oleander')
    ap.add_argument('--title', default='Ivy Oleander')
    ap.add_argument('--rig', choices=['b', 'j'])
    a = ap.parse_args()
    build(a.src, a.model, a.out, a.name, a.mod, a.title, a.rig)


if __name__ == '__main__':
    main()
