import sys, json, math
import numpy as np
from PIL import Image, ImageDraw

bb_path, tex_path, out_path = sys.argv[1], sys.argv[2], sys.argv[3]
with open(bb_path) as f:
    bb = json.load(f)
tex = Image.open(tex_path).convert("RGBA")
tw, th = tex.size
tpx = np.array(tex, dtype=np.uint8)

def rot_x(v, a):
    c, s = math.cos(a), math.sin(a)
    return np.array([v[0], v[1]*c - v[2]*s, v[1]*s + v[2]*c])
def rot_y(v, a):
    c, s = math.cos(a), math.sin(a)
    return np.array([v[0]*c + v[2]*s, v[1], -v[0]*s + v[2]*c])
def rot_z(v, a):
    c, s = math.cos(a), math.sin(a)
    return np.array([v[0]*c - v[1]*s, v[0]*s + v[1]*c, v[2]])

def get_box_uv_faces(el):
    u0, v0 = el["uv_offset"]
    fr, to = el["from"], el["to"]
    dx = int(math.floor(abs(to[0] - fr[0])))
    dy = int(math.floor(abs(to[1] - fr[1])))
    dz = int(math.floor(abs(to[2] - fr[2])))
    return {
        "up":    (u0 + dz + dx,     v0 + dz, u0 + dz,               v0),
        "down":  (u0 + dz + dx + dx,v0,      u0 + dz + dx,          v0 + dz),
        "east":  (u0,               v0 + dz, u0 + dz,               v0 + dz + dy),
        "north": (u0 + dz,          v0 + dz, u0 + dz + dx,          v0 + dz + dy),
        "west":  (u0 + dz + dx,     v0 + dz, u0 + dz + dx + dz,     v0 + dz + dy),
        "south": (u0 + dz + dx + dz,v0 + dz, u0 + dz + dx + dz + dx,v0 + dz + dy),
    }

views = [
    ("Front",       math.radians(180),  -0.10),
    ("Front-Left",  math.radians(148),  -0.12),
    ("Left",        math.radians(90),   -0.08),
    ("Right",       math.radians(-90),  -0.08),
    ("Back",        math.radians(0),    -0.10),
]

W, H = 256, 320
canvas = Image.new("RGBA", (W * len(views), H), (40, 40, 48, 255))

for vi, (vname, yaw, pitch) in enumerate(views):
    quads = []
    for el in bb.get("elements", []):
        if not el.get("visibility", True):
            continue
        fr = np.array(el["from"], dtype=float)
        to = np.array(el["to"], dtype=float)
        inf = float(el.get("inflate", 0.0))
        fr = fr - inf
        to = to + inf
        orig = np.array(el.get("origin", [0, 0, 0]), dtype=float)
        rot = [math.radians(r) for r in el.get("rotation", [0, 0, 0])]
        box_faces = get_box_uv_faces(el) if "uv_offset" in el else {}

        x0, y0, z0 = fr
        x1, y1, z1 = to
        face_defs = {
            "north": ([ [x1,y1,z0], [x0,y1,z0], [x0,y0,z0], [x1,y0,z0] ], 0.98),
            "south": ([ [x0,y1,z1], [x1,y1,z1], [x1,y0,z1], [x0,y0,z1] ], 0.92),
            "east":  ([ [x1,y1,z1], [x1,y1,z0], [x1,y0,z0], [x1,y0,z1] ], 0.86),
            "west":  ([ [x0,y1,z0], [x0,y1,z1], [x0,y0,z1], [x0,y0,z0] ], 0.86),
            "up":    ([ [x1,y1,z1], [x0,y1,z1], [x0,y1,z0], [x1,y1,z0] ], 1.00),
            "down":  ([ [x1,y0,z0], [x0,y0,z0], [x0,y0,z1], [x1,y0,z1] ], 0.75),
        }
        faces = el.get("faces", {})
        for fname, (corners, shade) in face_defs.items():
            if fname in box_faces:
                u0, v0, u1, v1 = box_faces[fname]
            elif fname in faces and "uv" in faces[fname]:
                u0, v0, u1, v1 = faces[fname]["uv"]
            else:
                continue
            du = max(1, int(round(abs(u1 - u0))))
            dv = max(1, int(round(abs(v1 - v0))))
            if abs(u1 - u0) < 1e-4 and abs(v1 - v0) < 1e-4:
                continue
            c3d = []
            for p in corners:
                v = np.array(p, dtype=float) - orig
                v = rot_x(v, rot[0])
                v = rot_y(v, rot[1])
                v = rot_z(v, rot[2])
                v = v + orig
                v = rot_y(v, yaw)
                v = rot_x(v, pitch)
                c3d.append(v)
            p0, p1, p2, p3 = c3d
            n = np.cross(p1 - p0, p3 - p0)
            is_plane = (abs(fr[0]-to[0]) < 1e-3) or (abs(fr[1]-to[1]) < 1e-3) or (abs(fr[2]-to[2]) < 1e-3)
            if not is_plane and n[2] >= -1e-4:
                continue
            if is_plane and fname not in ("north", "south"):
                continue
            if is_plane and n[2] >= -1e-4:
                continue
            for iu in range(du):
                for iv in range(dv):
                    uu = int(u0 + (iu + 0.5) * (u1 - u0) / du)
                    vv = int(v0 + (iv + 0.5) * (v1 - v0) / dv)
                    if not (0 <= uu < tw and 0 <= vv < th):
                        continue
                    rgba = tpx[vv, uu]
                    if rgba[3] < 20:
                        continue
                    s0, s1 = iu / du, (iu + 1) / du
                    t0, t1 = iv / dv, (iv + 1) / dv
                    def bilerp(s, t):
                        return (1-s)*(1-t)*p0 + s*(1-t)*p1 + s*t*p2 + (1-s)*t*p3
                    q0, q1, q2, q3 = bilerp(s0,t0), bilerp(s1,t0), bilerp(s1,t1), bilerp(s0,t1)
                    zavg = (q0[2] + q1[2] + q2[2] + q3[2]) * 0.25
                    col = (
                        min(255, int(rgba[0] * shade)),
                        min(255, int(rgba[1] * shade)),
                        min(255, int(rgba[2] * shade)),
                        int(rgba[3])
                    )
                    quads.append((zavg, [q0, q1, q2, q3], col))

    quads.sort(key=lambda x: x[0])
    img = Image.new("RGBA", (W, H), (40, 40, 48, 255))
    draw = ImageDraw.Draw(img)
    scale = 7.5
    cx, cy = W // 2, int(H * 0.85)
    for _, pts, col in quads:
        poly = [(cx + p[0] * scale, cy - p[1] * scale) for p in pts]
        draw.polygon(poly, fill=col)
    canvas.paste(img, (vi * W, 0))

canvas.save(out_path)
print("Saved 3D preview to", out_path)
