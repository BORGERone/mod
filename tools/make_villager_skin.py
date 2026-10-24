"""Generate the "villager" re-skin of ivy_oleander.

The .bbmodel geometry, bones, UV layout and all 82 animations stay untouched —
only the 128x128 texture is repainted (peasant/villager look: linen tunic, coarse
trousers, leather boots, burlap satchel, kerchief, straw sticking out of the
pack, rolled-up sleeves) and re-embedded into a copy of the model.

Usage:  python3 tools/make_villager_skin.py
Requires Pillow.
"""
import base64
import colorsys
import copy
import io
import json
import os
import uuid

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_MODEL = os.path.join(ROOT, 'ivy_oleander.bbmodel')
OUT_NAME = 'ivy_oleander_villager'
OUT_DIR = ROOT

# --------------------------------------------------------------- palettes ----
def hsv(h, s, v):
    r, g, b = colorsys.hsv_to_rgb(h / 360.0, s, v)
    return (round(r * 255), round(g * 255), round(b * 255), 255)


# source families: the brightness range each family occupies in the original
FAMILY_V = {
    'neutral': (0.06, 0.20),
    'red':     (0.18, 0.82),
    'brown':   (0.16, 0.40),
    'skin':    (0.66, 0.96),
}

# target = (hue, saturation, value_at_darkest, value_at_brightest)
TARGETS = {
    'skin':     (28, 0.42, 0.62, 0.86),   # hands / face
    'linen':    (40, 0.34, 0.50, 0.74),   # tunic, sleeves
    'linen_d':  (37, 0.40, 0.36, 0.56),   # tunic shadow parts
    'trouser':  (26, 0.42, 0.20, 0.36),   # coarse trousers
    'boot':     (22, 0.46, 0.10, 0.19),   # boots
    'burlap':   (33, 0.44, 0.34, 0.58),   # satchel / backpack
    'kerchief': (12, 0.52, 0.38, 0.62),   # neckerchief
    'rope':     (36, 0.45, 0.42, 0.62),   # rope belt & straps
    'hair':     (26, 0.44, 0.16, 0.30),   # hair strands
    'outline':  (20, 0.35, 0.05, 0.16),   # (unused, kept for tweaking)
}

BASE_THEME = {'neutral': 'linen', 'red': 'linen', 'brown': 'rope', 'skin': 'skin'}


def all_families(target):
    """Theme that maps every colour family of the original to one palette."""
    return {fam: target for fam in
            ('neutral', 'red', 'brown', 'skin', 'blue')}

# element index -> {source family: target palette}
#   0/1 torso, 2/3 chest wrap, 4..8 satchel, 9/10 head, 11..14 hair planes,
#   15..18 eyes, 19..22 legs, 23..26 arms
THEMES = {
    0:  {'neutral': 'linen', 'red': 'linen_d', 'brown': 'rope', 'skin': 'skin'},
    1:  all_families('hair'),
    2:  {'neutral': 'kerchief', 'brown': 'kerchief', 'red': 'kerchief', 'skin': 'kerchief'},
    3:  all_families('hair'),
    4:  {'brown': 'burlap', 'neutral': 'burlap'},
    5:  {'brown': 'burlap', 'neutral': 'burlap'},
    6:  {'brown': 'burlap', 'neutral': 'burlap'},
    7:  {'brown': 'burlap', 'neutral': 'burlap'},
    8:  {'brown': 'burlap', 'neutral': 'burlap'},
    9:  all_families('skin'),
    10: all_families('hair'),
    # the twin-tail planes stay hair, so a few strands stick out of the scarf
    11: all_families('hair'),
    12: all_families('hair'),
    13: all_families('hair'),
    14: all_families('hair'),
    19: {'neutral': 'trouser', 'red': 'trouser', 'brown': 'trouser', 'skin': 'boot'},
    20: all_families('outline'),
    21: {'neutral': 'trouser', 'red': 'trouser', 'brown': 'trouser', 'skin': 'boot'},
    22: all_families('outline'),
    23: {'neutral': 'linen_d', 'red': 'linen', 'brown': 'rope', 'skin': 'skin'},
    24: all_families('outline'),
    25: {'neutral': 'linen_d', 'red': 'linen', 'brown': 'rope', 'skin': 'skin'},
    26: all_families('outline'),
}

# inflated duplicate "shells" of torso / head / legs / arms (dropped, see main)
SHELLS = {1, 3, 10, 20, 22, 24, 26}

EYE_WHITE = (238, 231, 209, 255)
PUPIL = (58, 44, 34, 255)

SKIN_L = (233, 197, 152, 255)
SKIN = (220, 181, 135, 255)
SKIN_D = (188, 149, 106, 255)
SKIN_DD = (146, 112, 78, 255)
NECK = (116, 88, 62, 255)
HAIR = (74, 52, 38, 255)
KER = (122, 130, 94, 255)
KER_L = (144, 152, 114, 255)
KER_D = (96, 104, 72, 255)
KER_DD = (72, 80, 56, 255)

ROPE = (146, 112, 70, 255)
ROPE_L = (168, 132, 88, 255)
LINEN_SH = (150, 128, 94, 255)
PATCH = (168, 146, 108, 255)
PATCH_D = (128, 108, 78, 255)
SEAM = (128, 104, 70, 255)
STRAW = (196, 168, 96, 255)
STRAW_D = (168, 138, 74, 255)
BOOT = (52, 34, 22, 255)
BOOT_CUFF = (84, 55, 34, 255)
FOLD = (74, 52, 34, 255)
FOLD_L = (108, 78, 52, 255)
KNEE_PATCH = (120, 96, 64, 255)
HAND = (206, 164, 120, 255)
HAND_D = (176, 138, 98, 255)
CUFF = (168, 146, 108, 255)
PLAID_L = (176, 96, 62, 255)
PLAID_D = (118, 56, 34, 255)


# ------------------------------------------------------------------ utils ----
def family(col):
    """Classify an original texture pixel into a colour family."""
    r, g, b, a = col
    if a == 0:
        return 'transparent'
    mx, mn = max(r, g, b), min(r, g, b)
    v = mx / 255.0
    s = 0 if mx == 0 else (mx - mn) / mx
    h = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)[0] * 360
    if (r, g, b) in ((206, 190, 190), (255, 0, 18)):
        return 'EYE'
    if 200 < h < 270:
        return 'blue'
    if s < 0.13:
        return 'neutral'
    if v < 0.20 and s < 0.45:
        return 'neutral'
    if r > 140 and s < 0.42 and (h < 25 or h > 340):
        return 'skin'
    if h < 18 or h > 340:
        return 'red'
    if 18 <= h < 55:
        return 'brown'
    return 'neutral'


def remap(col, target):
    """Recolour one pixel, keeping the original's shading relationships."""
    hue, sat, vmin, vmax = TARGETS[target]
    lo, hi = FAMILY_V.get(family(col), (0.0, 1.0))
    v = colorsys.rgb_to_hsv(col[0] / 255, col[1] / 255, col[2] / 255)[2]
    t = 0.0 if hi <= lo else min(1.0, max(0.0, (v - lo) / (hi - lo)))
    return hsv(hue, sat, vmin + (vmax - vmin) * t)


def rect(fv):
    u = fv['uv']
    return (int(min(u[0], u[2])), int(min(u[1], u[3])),
            int(max(u[0], u[2])), int(max(u[1], u[3])))


# -------------------------------------------------------------- head art ----
# The model's front face is 8x8 at (8,8). Rows 0..1 are the kerchief edge, the
# eye quads (elements 15-18) sit on row 5, so that row stays plain skin and the
# eyes are drawn by the geometry itself.
FACE = [
    'KKKKKKKK',   # 0 kerchief edge
    'KKKKKKKK',   # 1 kerchief
    'SSSSSSSS',   # 2 forehead
    'SSSssSSS',   # 3 forehead highlight
    'SSSSSSSS',   # 4
    'SSSSSSSS',   # 5 eye row (the eye quads sit on top of it)
    'SSnssnSS',   # 6 nose + cheeks
    'DSSSSSSD',   # 7 jaw
]
FACE_CHARS = {'K': KER_D, 'S': SKIN, 's': SKIN_L, 'D': SKIN_D, 'n': SKIN_D}


def draw_head(img):
    px = img.load()
    x0, y0 = 8, 8
    for r, row in enumerate(FACE):
        for c, ch in enumerate(row):
            px[x0 + c, y0 + r] = FACE_CHARS[ch]

    # side faces (0..8 and 16..24)
    for sx in (0, 16):
        for r in range(8):
            for c in range(8):
                if r < 3:
                    col = KER_L if r == 0 else KER
                    if c in (0, 7):
                        col = KER_D
                elif r < 5 and c >= 5:
                    col = HAIR                       # hair behind the ear
                elif r == 3:
                    col = KER_D if c < 4 else KER
                else:
                    col = SKIN if r < 7 else SKIN_D
                    if r == 4 and c in (2, 3):
                        col = SKIN_L                 # cheekbone
                    if r == 5 and c in (5, 6):
                        col = SKIN_D
                px[sx + c, y0 + r] = col
        for c, r in ((5, 5), (6, 5), (5, 6), (6, 6)):   # ear
            px[sx + c, y0 + r] = SKIN_D
        px[sx + 5, y0 + 5] = SKIN_DD

    # back of the head (24..32): knotted kerchief
    for r in range(8):
        for c in range(8):
            col = KER
            if c in (0, 7):
                col = KER_D
            if r == 0:
                col = KER_L
            if c in (3, 4) and 2 <= r <= 5:
                col = KER_D
            if r == 7:
                col = KER_DD
            px[24 + c, y0 + r] = col

    # top of the head (8..16, 0..8): flat kerchief with seams
    for r in range(8):
        for c in range(8):
            col = KER_L if (c in (1, 2, 5, 6) and r in (1, 2, 5, 6)) else KER
            if c in (0, 7) or r in (0, 7):
                col = KER
            px[8 + c, r] = col

    # bottom of the head (16..24, 0..8): jaw + neck, visible right under the chin
    for r in range(8):
        for c in range(8):
            if r < 3:
                col = SKIN_D if c in (2, 3, 4, 5) else SKIN_DD
            elif r < 6:
                col = SKIN_DD
            else:
                col = NECK
            px[16 + c, r] = col


# ---------------------------------------------------------------- details ----
def stitches(px, x0, y0, x1, y1, color, step=3):
    for y in range(y0, y1):
        for x in range(x0, x1):
            if (x - x0) % step == 0 and y in (y0, y1 - 1):
                px[x, y] = color


def draw_details(img, d):
    px = img.load()

    # tunic front: rope belt + knot
    for x in range(37, 43):
        px[x, 13] = ROPE
        px[x, 14] = ROPE_L
    px[39, 12] = ROPE
    px[40, 12] = ROPE
    px[38, 11] = ROPE_L
    for y in range(5, 13):                    # side seams of the tunic
        px[37, y] = LINEN_SH
        px[42, y] = LINEN_SH
    for y in (6, 9, 12, 15):                  # coarse weave on the back
        for x in range(37, 43):
            c = px[x, y]
            px[x, y] = tuple(max(0, int(v * 0.88)) if i < 3 else v
                             for i, v in enumerate(c))
    for y in range(6, 9):                     # sewn-on patch
        for x in range(37, 40):
            px[x, y] = PATCH
    for x in (37, 39):
        for y in (6, 8):
            px[x, y] = PATCH_D

    # neckerchief: plaid bands + stripes
    u = rect(d['elements'][2]['faces']['north'])
    for x in range(u[0], u[2]):
        px[x, u[1]] = PLAID_L
        if (x - u[0]) % 3 == 0:
            for y in range(u[1], u[3]):
                px[x, y] = PLAID_D
    for y in (u[1] + 2, u[3] - 1):
        for x in range(u[0], u[2]):
            px[x, y] = PLAID_L

    # rolled-up sleeves: bare forearms on the lower end of every arm side
    for idx in (23, 25):
        for fname in ('north', 'south', 'east', 'west'):
            fv = d['elements'][idx]['faces'].get(fname)
            if not fv:
                continue
            x0, y0, x1, y1 = rect(fv)
            if y1 - y0 < 8:
                continue
            for y in range(y1 - 4, y1):
                for x in range(x0, x1):
                    px[x, y] = HAND if y < y1 - 2 else HAND_D
            for x in range(x0, x1):
                px[x, y1 - 5] = CUFF

    # boots: dark bottom rows on every leg face
    for idx in (19, 20, 21, 22):
        for fv in d['elements'][idx]['faces'].values():
            x0, y0, x1, y1 = rect(fv)
            if y1 - y0 < 3:
                continue
            for x in range(x0, x1):
                px[x, y1 - 1] = BOOT
                px[x, y1 - 2] = BOOT
                px[x, y1 - 3] = BOOT_CUFF

    # trousers: fold lines and a knee patch
    for idx in (19, 21):
        for fname in ('north', 'south'):
            x0, y0, x1, y1 = rect(d['elements'][idx]['faces'][fname])
            for x in range(x0, x1):
                px[x, y0 + 2] = FOLD_L
                px[x, y0 + 3] = FOLD
                px[x, y0 + 4] = FOLD_L
            for y in range(y0 + 6, y1 - 3):
                px[x1 - 2, y] = FOLD
    for y in range(10, 13):
        for x in range(61, 64):
            px[x, y] = KNEE_PATCH
    for x in range(61, 64):
        px[x, 10] = (92, 72, 48, 255)

    # satchel: burlap seams, rope straps, straw poking out of the top
    stitches(px, 5, 37, 13, 47, SEAM, 3)
    stitches(px, 18, 37, 26, 47, SEAM, 3)
    for x in range(6, 12):
        px[x, 42] = ROPE
    for x in range(19, 25):
        px[x, 42] = ROPE
    for (x, y), col in (((6, 33), STRAW), ((7, 34), STRAW), ((9, 33), STRAW),
                        ((11, 34), STRAW), ((12, 33), STRAW),
                        ((7, 33), STRAW_D), ((10, 34), STRAW_D)):
        px[x, y] = col


# ------------------------------------------------------------------- main ----
def main():
    d = json.load(open(SRC_MODEL))
    src = d['textures'][0]['source']
    tex = Image.open(io.BytesIO(base64.b64decode(src.split(',', 1)[1]))).convert('RGBA')

    out = tex.copy()
    px_src, px_out = tex.load(), out.load()

    # 1) recolour every face rectangle according to its element's theme
    for i, e in enumerate(d['elements']):
        theme = THEMES.get(i, BASE_THEME)
        for fv in e['faces'].values():
            if fv.get('texture') is None:
                continue
            x0, y0, x1, y1 = rect(fv)
            for y in range(y0, y1):
                for x in range(x0, x1):
                    col = px_src[x, y]
                    if col[3] == 0:
                        continue
                    if family(col) == 'EYE':
                        px_out[x, y] = EYE_WHITE
                        continue
                    px_out[x, y] = remap(col, theme.get(family(col), 'linen'))

    # 2) eyes: warm sclera on the flat quads, brown pupil instead of the red dot
    for (x, y) in ((26, 32), (27, 32), (26, 33), (27, 33)):
        px_out[x, y] = EYE_WHITE
    for (x, y) in ((30, 32), (30, 33)):
        px_out[x, y] = PUPIL

    # 3) drop the thin strands painted on the inflated "shell" duplicates: over
    #    cloth they read as holes. Pixels shared with a real part are kept.
    owners = {}
    for i, e in enumerate(d['elements']):
        for fv in e['faces'].values():
            x0, y0, x1, y1 = rect(fv)
            for y in range(y0, y1):
                for x in range(x0, x1):
                    owners.setdefault((x, y), set()).add(i)
    for i in SHELLS:
        for fv in d['elements'][i]['faces'].values():
            x0, y0, x1, y1 = rect(fv)
            for y in range(y0, y1):
                for x in range(x0, x1):
                    if owners.get((x, y), set()) <= SHELLS:
                        px_out[x, y] = (0, 0, 0, 0)

    # 4) hand-painted head + costume details
    draw_head(out)
    draw_details(out, d)

    out.save(os.path.join(OUT_DIR, OUT_NAME + '.png'))

    # 5) copy of the model with the new texture embedded (geometry untouched)
    m = copy.deepcopy(d)
    m['name'] = OUT_NAME
    m['model_identifier'] = OUT_NAME
    t = m['textures'][0]
    t['name'] = OUT_NAME + '.png'
    t['uuid'] = str(uuid.uuid4())
    t['width'], t['height'] = 128, 128
    buf = io.BytesIO()
    out.save(buf, format='PNG')
    t['source'] = 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()
    with open(os.path.join(OUT_DIR, OUT_NAME + '.bbmodel'), 'w') as fh:
        json.dump(m, fh, separators=(',', ':'))
    print('written', OUT_NAME + '.bbmodel / ' + OUT_NAME + '.png')


if __name__ == '__main__':
    main()
