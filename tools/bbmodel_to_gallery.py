"""Convert a Blockbench .bbmodel into the compact rig format used by the
"Люди и NPC" 3D gallery (HTML): fields n / m / w / h / t / th / r / a / g / u / qb.

Conventions (verified against the gallery's own data and its makeBBModel export):

* The gallery keeps, per render quad, 4 corners in 1/32 block units and stores
  them as int16 (x, y, z) = (-viewer_x, -viewer_y, viewer_z) * 32, where
  viewer = model space shifted down by `yoff` (0 for bedrock rigs, 24 for
  modded-entity rigs, i.e. r.f = 'b' / 'j').
* Geometry is baked in the *rest pose*, i.e. every cube is rotated by its own
  element rotation and then by the world matrix of its bone (group rotations
  included).  Animation playback then uses W_anim * W_rest^-1, which is the
  identity in the rest pose.
* Blockbench corner/uv order per face is taken from Blockbench's
  BufferGeometry.setShape() + CubePreviewController.updateUV().
* Animation rotation keys are negated on x/y and position keys on x, which is
  how the gallery's `worlds()` applies them (it expects .bbmodel raw values).
"""
import base64
import json
import math
import struct

FACES = ['north', 'east', 'south', 'west', 'up', 'down']

# Blockbench vertex order per face, relative to the (possibly rotated) origin.
CORNERS = {
    'east':  lambda f, t: [(t[0], t[1], t[2]), (t[0], t[1], f[2]), (t[0], f[1], t[2]), (t[0], f[1], f[2])],
    'west':  lambda f, t: [(f[0], t[1], f[2]), (f[0], t[1], t[2]), (f[0], f[1], f[2]), (f[0], f[1], t[2])],
    'up':    lambda f, t: [(f[0], t[1], f[2]), (t[0], t[1], f[2]), (f[0], t[1], t[2]), (t[0], t[1], t[2])],
    'down':  lambda f, t: [(f[0], f[1], t[2]), (t[0], f[1], t[2]), (f[0], f[1], f[2]), (t[0], f[1], f[2])],
    'south': lambda f, t: [(f[0], t[1], t[2]), (t[0], t[1], t[2]), (f[0], f[1], t[2]), (t[0], f[1], t[2])],
    'north': lambda f, t: [(t[0], t[1], f[2]), (f[0], t[1], f[2]), (t[0], f[1], f[2]), (f[0], f[1], f[2])],
}
# uv order: (u1,v1) (u2,v1) (u1,v2) (u2,v2) -> index into the face uv rectangle
UV_ORDER = [(0, 1), (2, 1), (0, 3), (2, 3)]
# The gallery stores the four corners in its own order; this permutation maps the
# Blockbench corner order above to the gallery's order (verified on the gallery's
# own mobs: side faces 1,0,2,3 and up/down 2,3,1,0).
QUAD_ORDER = {'north': (1, 0, 2, 3), 'east': (1, 0, 2, 3), 'south': (1, 0, 2, 3),
              'west': (1, 0, 2, 3), 'up': (2, 3, 1, 0), 'down': (2, 3, 1, 0)}


# ---------------------------------------------------------------- matrices
def _rot(rx, ry, rz):
    """R = Rz * Ry * Rx (THREE 'ZYX' / the gallery's rotZYX), degrees."""
    rx, ry, rz = (math.radians(a) for a in (rx, ry, rz))
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    return [cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx,
            sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx,
            -sy,     cy * sx,                cy * cx]


def _mul(a, b):
    """3x4 affine multiply (row major, 12 numbers)."""
    o = [0.0] * 12
    for r in range(3):
        for c in range(3):
            o[r * 4 + c] = a[r * 4] * b[c] + a[r * 4 + 1] * b[4 + c] + a[r * 4 + 2] * b[8 + c]
        o[r * 4 + 3] = a[r * 4] * b[3] + a[r * 4 + 1] * b[7] + a[r * 4 + 2] * b[11] + a[r * 4 + 3]
    return o


def _local(pos, rot):
    r = _rot(*rot) if any(rot) else [1, 0, 0, 0, 1, 0, 0, 0, 1]
    return [r[0], r[1], r[2], pos[0], r[3], r[4], r[5], pos[1], r[6], r[7], r[8], pos[2]]


def _apply(m, p):
    return [m[0] * p[0] + m[1] * p[1] + m[2] * p[2] + m[3],
            m[4] * p[0] + m[5] * p[1] + m[6] * p[2] + m[7],
            m[8] * p[0] + m[9] * p[1] + m[10] * p[2] + m[11]]


# ---------------------------------------------------------------- rig
def read_rig(model):
    """Outliner -> bones [[name, parent, ox, oy, oz, rx, ry, rz]] + element->bone map."""
    bones, bone_of, by_uuid = [], {}, {}

    def walk(nodes, parent):
        for node in nodes:
            if isinstance(node, str):
                continue
            idx = len(bones)
            org = list(node.get('origin') or [0, 0, 0])
            rot = list(node.get('rotation') or [0, 0, 0])
            while len(org) < 3:
                org.append(0)
            while len(rot) < 3:
                rot.append(0)
            bones.append([node.get('name', 'bone%d' % idx), parent,
                          org[0], org[1], org[2], rot[0], rot[1], rot[2]])
            by_uuid[node.get('uuid')] = idx
            nested = []
            for child in node.get('children') or []:
                if isinstance(child, str):
                    bone_of[child] = idx
                else:
                    nested.append(child)
            walk(nested, idx)

    walk(model['outliner'], -1)
    return bones, bone_of, by_uuid


def rest_worlds(bones):
    """Absolute rest matrices, same math as the gallery's AV.worlds()."""
    worlds = [None] * len(bones)

    def get(i):
        if worlds[i] is not None:
            return worlds[i]
        b = bones[i]
        p = b[1]
        if p is not None and 0 <= p < len(bones):
            pb = bones[p]
            pos = [b[2] - pb[2], b[3] - pb[3], b[4] - pb[4]]
            parent = get(p)
        else:
            pos = [b[2], b[3], b[4]]
            parent = None
        local = _local(pos, [b[5], b[6], b[7]])
        worlds[i] = _mul(parent, local) if parent else local
        return worlds[i]

    return [get(i) for i in range(len(bones))]


# ---------------------------------------------------------------- geometry
def _num(v, default=0.0):
    return float(v) if isinstance(v, (int, float)) else default


def build_quads(model, bones, bone_of, yoff):
    """Bake every cube face into gallery-space ints + uv rect corners."""
    worlds = rest_worlds(bones)
    quads = []
    for el in model.get('elements', []):
        if el.get('type', 'cube') != 'cube':
            continue
        bone = bone_of.get(el.get('uuid'))
        if bone is None:
            continue
        infl = _num(el.get('inflate'))
        f = [min(el['from'][i], el['to'][i]) - infl for i in range(3)]
        t = [max(el['from'][i], el['to'][i]) + infl for i in range(3)]
        if all(abs(t[i] - f[i]) < 1e-9 for i in range(3)):
            continue
        el_rot = el.get('rotation') or [0, 0, 0]
        el_org = el.get('origin') or [0, 0, 0]
        W = worlds[bone]
        # Blockbench parents the cube mesh to the bone, so cube coordinates are
        # expressed in the bone's local frame (origin at the bone origin).
        bo = bones[bone][2:5]
        for face in FACES:
            data = (el.get('faces') or {}).get(face)
            if not data or data.get('texture') is None:
                continue
            uv = data.get('uv') or [0, 0, 0, 0]
            pts = CORNERS[face](f, t)
            if any(el_rot):
                R = _rot(*el_rot)
                M = [R[0], R[1], R[2], 0, R[3], R[4], R[5], 0, R[6], R[7], R[8], 0]
                out = []
                for p in pts:
                    q = _apply(M, [p[0] - el_org[0], p[1] - el_org[1], p[2] - el_org[2]])
                    out.append([q[0] + el_org[0], q[1] + el_org[1], q[2] + el_org[2]])
                pts = out
            out_pos, out_uv = [], []
            for k in QUAD_ORDER[face]:
                q = _apply(W, [pts[k][0] - bo[0], pts[k][1] - bo[1], pts[k][2] - bo[2]])
                vy = q[1] - yoff
                out_pos += [-q[0] * 32, -vy * 32, q[2] * 32]
                out_uv += [uv[UV_ORDER[k][0]] * 16, uv[UV_ORDER[k][1]] * 16]
            quads.append((bone + 1, [int(round(v)) for v in out_pos],
                          [int(round(v)) for v in out_uv]))
    return quads


def pack(quads):
    """-> base64 g / u / qb."""
    g, u, qb = [], [], []
    for bone, pos, uv in quads:
        g += pos
        u += uv
        qb.append(bone)
    return (_b64(struct.pack('<%dh' % len(g), *g)),
            _b64(struct.pack('<%dH' % len(u), *u)),
            _b64(struct.pack('<%dH' % len(qb), *qb)))


def _b64(data):
    return base64.b64encode(data).decode('ascii')


def build_cubes(model, bones, bone_of):
    """Cube list for the gallery's .bbmodel exporter (absolute coords)."""
    cubes = []
    for el in model.get('elements', []):
        if el.get('type', 'cube') != 'cube':
            continue
        bone = bone_of.get(el.get('uuid'))
        if bone is None:
            continue
        faces = {}
        for face in FACES:
            data = (el.get('faces') or {}).get(face)
            if data and data.get('texture') is not None:
                faces[face] = list(data.get('uv') or [0, 0, 0, 0])
        box_uv = el.get('box_uv') and el.get('uv_offset')
        uvf = [box_uv[0], box_uv[1]] if box_uv else faces
        if box_uv:
            # keep the gallery's canonical (bbBoxFaces) rectangles
            uvf = [_num(box_uv[0]), _num(box_uv[1])]
        entry = [bone, el['from'][0], el['from'][1], el['from'][2],
                 el['to'][0], el['to'][1], el['to'][2],
                 _num(el.get('inflate')), 1 if el.get('mirror_uv') else 0, uvf]
        org = el.get('origin') or [0, 0, 0]
        rot = el.get('rotation') or [0, 0, 0]
        if any(rot) or any(abs(org[i] - bones[bone][2 + i]) > 1e-9 for i in range(3)):
            entry += [org[0], org[1], org[2], rot[0], rot[1], rot[2]]
        cubes.append(entry)
    return cubes


# ---------------------------------------------------------------- animations
def build_animations(model, bones, negate=True):
    names = {b[0] for b in bones}
    loop_map = {'loop': 'l', 'once': 'o', 'hold': 'h'}
    ip_map = {'linear': 'l', 'catmullrom': 'c', 'step': 's', 'bezier': 'b'}
    ch_map = {'rotation': 'r', 'position': 'p', 'scale': 's'}
    sign = {'r': (-1, -1, 1), 'p': (-1, 1, 1), 's': (1, 1, 1)}
    anims = []
    for anim in model.get('animations') or []:
        channels = {}
        max_t = 0.0
        for animator in (anim.get('animators') or {}).values():
            bone = animator.get('name')
            if bone not in names:
                continue
            if animator.get('type') not in (None, 'bone'):
                continue
            for kf in animator.get('keyframes') or []:
                key = ch_map.get(kf.get('channel'))
                if key is None:
                    continue
                pts = kf.get('data_points') or []
                if not pts:
                    continue
                pts = [p for p in pts if isinstance(p, dict)]
                if not pts:
                    continue
                v0 = pts[0]
                v1 = pts[-1]
                pre = [_num(v0.get(a)) for a in 'xyz']
                post = [_num(v1.get(a)) for a in 'xyz']
                if negate:
                    pre = [pre[i] * sign[key][i] for i in range(3)]
                    post = [post[i] * sign[key][i] for i in range(3)]
                item = [_num(kf.get('time')), pre if len(pts) > 1 else 0, post,
                        ip_map.get(kf.get('interpolation'), 'l')]
                if kf.get('easing'):
                    item.append(kf['easing'])
                channels.setdefault(bone, {}).setdefault(key, []).append(item)
                max_t = max(max_t, _num(kf.get('time')))
        if not channels:
            continue
        for bone in channels:
            for key in channels[bone]:
                channels[bone][key].sort(key=lambda k: k[0])
        length = _num(anim.get('length')) or max_t
        if length <= 0:
            length = 1
        anims.append([anim.get('name') or 'animation',
                      loop_map.get(str(anim.get('loop')).lower(), 'o'),
                      length, channels])
    return anims


# ---------------------------------------------------------------- assembly
def texture_payload(model):
    textures = model.get('textures') or []
    if not textures:
        raise ValueError('в .bbmodel нет текстуры')
    tex = textures[0]
    src = tex.get('source') or ''
    if src.startswith('data:'):
        src = src.split(',', 1)[1]
    res = model.get('resolution') or {}
    w = tex.get('uv_width') or tex.get('width') or res.get('width')
    h = tex.get('uv_height') or tex.get('height') or res.get('height')
    return src, int(w), int(h)


def convert(model, name, mod, thumb_b64='', rig_format=None, negate_anim=True):
    bones, bone_of, _ = read_rig(model)
    yoff = 24 if rig_format == 'j' else 0
    quads = build_quads(model, bones, bone_of, yoff)
    g, u, qb = pack(quads)
    tex, w, h = texture_payload(model)
    mob = {
        'n': name,
        'm': mod,
        'w': w,
        'h': h,
        't': tex,
        'th': thumb_b64,
        'r': {'f': rig_format or 'b', 'b': bones, 'c': build_cubes(model, bones, bone_of)},
        'a': build_animations(model, bones, negate_anim),
        'g': g, 'u': u, 'qb': qb,
    }
    return mob, len(quads)
