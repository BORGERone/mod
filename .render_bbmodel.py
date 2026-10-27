import sys, json, math
from PIL import Image, ImageDraw

bb_path, tex_path, out_path = sys.argv[1], sys.argv[2], sys.argv[3]
with open(bb_path, "r") as f:
    bb = json.load(f)
tex = Image.open(tex_path).convert("RGBA")
tw, th = tex.size

def rot_vec(v, rot):
    if not rot:
        return v
    x, y, z = v
    rx, ry, rz = [math.radians(a) for a in rot]
    cr, sr = math.cos(rx), math.sin(rx)
    y, z = y * cr - z * sr, y * sr + z * cr
    cp, sp = math.cos(ry), math.sin(ry)
    x, z = x * cp + z * sp, -x * sp + z * cp
    cy, sy = math.cos(rz), math.sin(rz)
    x, y = x * cy - y * sy, x * sy + y * cy
    return [x, y, z]

def rot_pt(p, origin, rot):
    if not rot:
        return p
    v = [p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]]
    rv = rot_vec(v, rot)
    return [rv[0] + origin[0], rv[1] + origin[1], rv[2] + origin[2]]

FACE_DEFS = {
    "north": ([0, 1, 2, 3], (0, 0, -1)),
    "south": ([4, 5, 6, 7], (0, 0,  1)),
    "east":  ([7, 6, 1, 0], (1, 0,  0)),
    "west":  ([3, 2, 5, 4], (-1, 0, 0)),
    "up":    ([7, 0, 3, 4], (0, 1,  0)),
    "down":  ([1, 6, 5, 2], (0, -1, 0)),
}

views = [0, 28, 90, -90, 180]
panel_w, panel_h = 256, 320
canvas = Image.new("RGBA", (panel_w * len(views), panel_h), (38, 38, 44, 255))
draw = ImageDraw.Draw(canvas)

pitch = math.radians(6.0)
cp_cam, sp_cam = math.cos(pitch), math.sin(pitch)

for vi, yaw_deg in enumerate(views):
    yaw = math.radians(yaw_deg)
    cy, sy = math.cos(yaw), math.sin(yaw)
    quads = []
    for ei, el in enumerate(bb.get("elements", [])):
        if not el.get("visibility", True):
            continue
        x0, y0, z0 = el["from"]
        x1, y1, z1 = el["to"]
        is_plane = (abs(x1 - x0) < 0.01) or (abs(y1 - y0) < 0.01) or (abs(z1 - z0) < 0.01)
        origin = el.get("origin", [0, 0, 0])
        rot = el.get("rotation", None)
        corners = [
            [x1, y1, z0], [x1, y0, z0], [x0, y0, z0], [x0, y1, z0],
            [x0, y1, z1], [x0, y0, z1], [x1, y0, z1], [x1, y1, z1],
        ]
        rc = [rot_pt(c, origin, rot) for c in corners]
        faces = el.get("faces", {})
        for fn, (idxs, norm) in FACE_DEFS.items():
            if fn not in faces:
                continue
            rn = rot_vec(norm, rot)
            nx = rn[0] * cy + rn[2] * sy
            nz = -rn[0] * sy + rn[2] * cy
            ny = rn[1] * cp_cam - nz * sp_cam
            nz_cam = rn[1] * sp_cam + nz * cp_cam
            if not is_plane and nz_cam > 0.05:
                continue
            if not is_plane and el.get("box_uv") and "uv_offset" in el:
                uo, vo = el["uv_offset"]
                dx = int(math.floor(x1 - x0 + 1e-5))
                dy = int(math.floor(y1 - y0 + 1e-5))
                dz = int(math.floor(z1 - z0 + 1e-5))
                box_map = {
                    "east":  (uo, vo + dz, uo + dz, vo + dz + dy),
                    "north": (uo + dz, vo + dz, uo + dz + dx, vo + dz + dy),
                    "west":  (uo + dz + dx, vo + dz, uo + 2 * dz + dx, vo + dz + dy),
                    "south": (uo + 2 * dz + dx, vo + dz, uo + 2 * dz + 2 * dx, vo + dz + dy),
                    "up":    (uo + dz + dx, vo + dz, uo + dz, vo),
                    "down":  (uo + 2 * dz + dx, vo, uo + dz + dx, vo + dz),
                }
                u0, v0, u1, v1 = box_map[fn]
                du, dv = u1 - u0, v1 - v0
                steps_u = max(1, abs(du))
                steps_v = max(1, abs(dv))
            else:
                uv = faces[fn].get("uv")
                if not uv:
                    continue
                u0, v0, u1, v1 = uv
                du, dv = u1 - u0, v1 - v0
                steps_u = max(1, int(math.ceil(abs(du))))
                steps_v = max(1, int(math.ceil(abs(dv))))
            p0, p1, p2, p3 = [rc[i] for i in idxs]
            for iv in range(steps_v):
                tv0 = iv / steps_v
                tv1 = (iv + 1) / steps_v
                tu_v = v0 + (iv + 0.5) / steps_v * dv
                py = max(0, min(th - 1, int(tu_v)))
                for iu in range(steps_u):
                    tu0 = iu / steps_u
                    tu1 = (iu + 1) / steps_u
                    tu_u = u0 + (iu + 0.5) / steps_u * du
                    px = max(0, min(tw - 1, int(tu_u)))
                    rgba = tex.getpixel((px, py))
                    if rgba[3] < 16:
                        continue
                    def bilerp(u, v):
                        return [
                            (1 - u) * (1 - v) * p0[k] + u * (1 - v) * p3[k] + u * v * p2[k] + (1 - u) * v * p1[k]
                            for k in range(3)
                        ]
                    q = [bilerp(tu0, tv0), bilerp(tu1, tv0), bilerp(tu1, tv1), bilerp(tu0, tv1)]
                    pts2d = []
                    zs = []
                    for pt in q:
                        rx = pt[0] * cy + pt[2] * sy
                        rz = -pt[0] * sy + pt[2] * cy
                        ry = pt[1] * cp_cam - rz * sp_cam
                        rz_c = pt[1] * sp_cam + rz * cp_cam
                        sx = vi * panel_w + panel_w // 2 - rx * 6.8
                        sy_scr = panel_h - 42 - ry * 6.8
                        pts2d.append((sx, sy_scr))
                        zs.append(rz_c)
                    quads.append((sum(zs) / 4.0, pts2d, rgba[:3]))
    quads.sort(key=lambda item: item[0], reverse=True)
    for _, pts2d, col in quads:
        draw.polygon(pts2d, fill=col)

canvas.convert("RGB").save(out_path)
print("Saved 3D preview to", out_path)
