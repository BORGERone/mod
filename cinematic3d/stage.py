"""Tiny software renderer for Blockbench models: textured painter-sorted quads
with a z-buffer, clustered soft shadows, depth fog, DOF and a filmic grade."""
import cv2
import numpy as np


# ------------------------------------------------------------------ camera --
class Camera:
    def __init__(self, pos, target, fov=36.0, roll=0.0):
        self.pos = np.asarray(pos, np.float64)
        f = np.asarray(target, np.float64) - self.pos
        self.forward = f / (np.linalg.norm(f) + 1e-9)
        up = np.array([0.0, 1.0, 0.0])
        if abs(np.dot(up, self.forward)) > 0.999:
            up = np.array([0.0, 0.0, 1.0])
        self.right = np.cross(self.forward, up)
        self.right /= np.linalg.norm(self.right) + 1e-9
        self.up = np.cross(self.right, self.forward)
        self.fov = fov
        self.roll = roll

    def dirs(self, nx, ny):
        """Ray directions for normalised screen coords (nx, ny) in [-1, 1]."""
        tv = np.tan(np.deg2rad(self.fov) * 0.5)
        th = tv * (nx.shape[1] / max(nx.shape[0], 1))
        dx, dy = nx * th, ny * tv
        if abs(self.roll) > 1e-6:
            r = np.deg2rad(self.roll)
            dx, dy = dx * np.cos(r) - dy * np.sin(r), dx * np.sin(r) + dy * np.cos(r)
        d = (self.forward[None, None, :] + self.right[None, None, :] * dx[..., None]
             + self.up[None, None, :] * dy[..., None])
        return d / (np.linalg.norm(d, axis=2, keepdims=True) + 1e-9)


class _Proj:
    """Screen projection helper for one camera at one resolution."""

    def __init__(self, cam, w, h):
        self.cam, self.w, self.h = cam, w, h
        self.f = (h * 0.5) / np.tan(np.deg2rad(cam.fov) * 0.5)

    def project(self, p):
        d = p - self.cam.pos
        x = d @ self.cam.right
        y = d @ self.cam.up
        z = d @ self.cam.forward
        if abs(self.cam.roll) > 1e-6:
            r = np.deg2rad(self.cam.roll)
            x, y = x * np.cos(r) - y * np.sin(r), x * np.sin(r) + y * np.cos(r)
        zz = np.maximum(z, 1e-4)
        sx = self.w * 0.5 + self.f * x / zz
        sy = self.h * 0.5 - self.f * y / zz
        return np.stack([sx, sy], -1), z


# ---------------------------------------------------------------- backdrop --
class Backdrop:
    def __init__(self, w, h, cam, sun, shore_z=-30.0):
        self.w, self.h, self.cam, self.sun = w, h, cam, np.asarray(sun, np.float64)
        self.shore_z = shore_z
        self.sky_top = np.array([0.20, 0.38, 0.66], np.float32)
        self.horizon = np.array([0.74, 0.82, 0.90], np.float32)
        self.sea = np.array([0.10, 0.27, 0.42], np.float32)
        self.sand = np.array([0.80, 0.73, 0.58], np.float32)

    def _noise(self, x, z, freq, seed):
        n = 0.0
        a = 1.0
        tot = 0.0
        for i in range(5):
            f = freq * (2.03 ** i)
            n = n + a * (np.sin(x * f + seed * 1.7) * np.sin(z * f * 1.21 - seed * 0.9)
                         + 0.5 * np.sin((x + z) * f * 0.7 + seed))
            tot += a
            a *= 0.52
        return n / tot

    def render(self, t, scale=2):
        w, h = max(self.w // scale, 2), max(self.h // scale, 2)
        nx = (np.linspace(-1, 1, w, dtype=np.float32))[None, :].repeat(h, 0)
        ny = (np.linspace(1, -1, h, dtype=np.float32))[:, None].repeat(w, 1)
        d = self.cam.dirs(nx, ny)

        up = np.clip(d[..., 1], 0, 1)
        col = self.horizon * (1 - up[..., None] ** 0.62) + self.sky_top * (up[..., None] ** 0.62)
        ang = np.clip(d @ self.sun, -1, 1)
        glow = np.exp(-(1 - ang) * 9.0) * 0.55 + np.exp(-(1 - ang) * 1.6) * 0.22
        col = col + glow[..., None] * np.array([0.60, 0.70, 0.94], np.float32)

        # clouds: noise sampled on a squashed sky plane (no seam, no streaks)
        den = np.maximum(d[..., 1], 0.02)
        px, pz = d[..., 0] / den, d[..., 2] / den
        r = np.sqrt(px * px + pz * pz)
        sq = 1.0 / (1.0 + r * 0.06)
        cx, cz = px * sq * 0.95, pz * sq * 0.95
        n1 = self._noise(cx + t * 0.03, cz + t * 0.014, 1.15, 4.0)
        n2 = self._noise(cx * 2.1 - t * 0.02, cz * 2.1, 1.9, 9.0)
        cl = n1 * 0.95 + n2 * 0.30
        cl = np.clip((cl - 0.30) * 4.2, 0, 1) ** 0.62
        el = np.clip(d[..., 1], 0, 1)
        cl = cl * np.clip((el - 0.01) * 26.0, 0, 1) ** 0.5
        lit = np.clip(0.62 + 0.38 * (d @ self.sun), 0, 1)
        thick = np.clip(cl * 1.15, 0, 1)[..., None]
        sunlit = np.array([1.00, 0.99, 0.96], np.float32)
        shadow = np.array([0.60, 0.66, 0.76], np.float32)
        cl_col = shadow * (1 - thick) + sunlit * thick
        cl_col = cl_col * (0.80 + 0.20 * lit[..., None])
        col = col * (1 - 0.95 * cl[..., None]) + cl_col * (0.95 * cl[..., None])

        eps = 1e-4
        ground = d[..., 1] < -eps
        ty = np.where(ground, -self.cam.pos[1] / np.minimum(d[..., 1], -eps), 400.0)
        gx = self.cam.pos[0] + d[..., 0] * ty
        gz = self.cam.pos[2] + d[..., 2] * ty

        n1 = self._noise(gx * 0.5, gz * 0.5, 1.0, 3.0)
        n2 = self._noise(gx * 3.2, gz * 3.2, 1.0, 11.0)
        sand_col = self.sand * (0.95 + 0.055 * n1[..., None] + 0.030 * n2[..., None])

        shore = self.shore_z + 1.6 * np.sin(gx * 0.33) + 0.7 * np.sin(gx * 1.02)
        sea_m = np.clip((shore - gz) / 1.6, 0, 1)
        wave = 0.5 + 0.5 * np.sin(gx * 0.9 + t * 1.9) * np.sin(gz * 1.7 - t * 1.2)
        foam = np.clip(1.0 - np.abs(shore - gz) / 1.2, 0, 1) ** 2
        sea_col = self.sea * (0.72 + 0.36 * wave[..., None]) + foam[..., None] * 0.25
        gcol = sand_col * (1 - sea_m[..., None]) + sea_col * sea_m[..., None]

        haze = np.clip(ty / 120.0, 0, 1)[..., None] ** 1.1
        gcol = gcol * (1 - 0.55 * haze) + self.horizon * 0.55 * haze

        img = np.where(ground[..., None], gcol, col)
        dist = np.where(ground, np.clip(ty, 0.5, 400.0), 400.0)

        img = cv2.resize(img.astype(np.float32), (self.w, self.h), interpolation=cv2.INTER_LINEAR)
        dist = cv2.resize(dist.astype(np.float32), (self.w, self.h), interpolation=cv2.INTER_NEAREST)
        return img, dist


# ------------------------------------------------------------------- stage --
class Stage:
    def __init__(self, w, h, ssaa=2.0):
        self.out_w, self.out_h = w, h
        self.ssaa = ssaa
        self.w, self.h = int(w * ssaa), int(h * ssaa)
        self.img = np.zeros((self.h, self.w, 3), np.float32)
        self.dist = np.full((self.h, self.w), 400.0, np.float32)
        self.zbuf = np.full((self.h, self.w), 1e9, np.float32)
        self.cam = None

    def new_frame(self, backdrop, t):
        self.img, self.dist = backdrop.render(t, scale=2)
        self.zbuf = np.full((self.h, self.w), 1e9, np.float32)

    # -------------------------------------------------------------- shadows --
    def shadow(self, items, sun, strength=0.5, soft=0.0):
        """Exact projected shadow: a cube is convex, so its ground shadow is the
        convex hull of its 8 projected corners. Union + blur = soft shadow."""
        sun = np.asarray(sun, np.float64)
        sun = sun / np.linalg.norm(sun)
        pr = _Proj(self.cam, self.w, self.h)
        mask = np.zeros((self.h, self.w), np.float32)
        for placed, _tex in items:
            for cube, M in placed:
                lo, hi = cube.lo, cube.hi
                if (hi - lo).min() <= 0.0:
                    continue
                corners = np.array([[x, y, z] for x in (lo[0], hi[0])
                                    for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
                wpts = (M[:3, :3] @ corners.T).T + M[:3, 3]
                t = np.maximum(wpts[:, 1], 0.0) / max(sun[1], 0.05)
                g = wpts - sun[None, :] * t[:, None]
                g3 = g[:, [0, 0, 2]]
                g3[:, 1] = 0.02
                sp, z = pr.project(g3)
                if (z <= 0.05).any():
                    continue
                hull = cv2.convexHull(sp.astype(np.float32)).astype(np.int32)
                cv2.fillConvexPoly(mask, hull, 1.0, lineType=cv2.LINE_AA)
        k = max(int(self.h / 120), 3) | 1
        if soft > 0:
            k = max(k, int(self.h * soft)) | 1
        soft_mask = cv2.GaussianBlur(mask, (k, k), 0)
        self.img *= (1.0 - strength * np.clip(soft_mask, 0, 1)[..., None])

    # ---------------------------------------------------------------- draw --
    def draw(self, items, sun, ambient=0.65):
        sun = np.asarray(sun, np.float64)
        sun = sun / np.linalg.norm(sun)
        pr = _Proj(self.cam, self.w, self.h)
        sky = np.array([0.55, 0.63, 0.78], np.float32)

        quads = []
        for placed, tex in items:
            tex = tex[0] if isinstance(tex, (list, tuple)) else tex
            if tex.ndim == 2 or tex.shape[2] == 3:
                tex = np.dstack([tex[..., :3], np.ones(tex.shape[:2], np.float32)])
            for cube, M in placed:
                R, Tr = M[:3, :3], M[:3, 3]
                for name, quad, uvs, nrm in _faces(cube):
                    wn = R @ nrm
                    center = R @ ((quad[0] + quad[2]) * 0.5) + Tr
                    # backface cull: a hidden face costs the same as a visible one
                    if float(wn @ (center - self.cam.pos)) > 0:
                        continue
                    wq = (R @ quad.T).T + Tr
                    cam_d = wq - self.cam.pos
                    depth = cam_d @ self.cam.forward
                    if (depth <= 0.12).any():
                        continue
                    sp, z = pr.project(wq)
                    quads.append((float(depth.mean()), sp, z, uvs, tex, wn, wq))
        quads.sort(key=lambda q: -q[0])

        for depth, sp, z, uvs, tex, wn, wq in quads:
            x0 = max(int(np.floor(sp[:, 0].min())), 0)
            x1 = min(int(np.ceil(sp[:, 0].max())) + 1, self.w)
            y0 = max(int(np.floor(sp[:, 1].min())), 0)
            y1 = min(int(np.ceil(sp[:, 1].max())) + 1, self.h)
            if x1 - x0 < 1 or y1 - y0 < 1:
                continue
            H, W = y1 - y0, x1 - x0
            if W < 2 and H < 2 and depth > 6.0:
                continue

            u1, v1 = uvs[0]
            u2, v2 = uvs[2]
            a1, a2 = int(np.floor(min(u1, u2))), int(np.ceil(max(u1, u2)))
            b1, b2 = int(np.floor(min(v1, v2))), int(np.ceil(max(v1, v2)))
            a2, b2 = max(a2, a1 + 1), max(b2, b1 + 1)
            th, tw = tex.shape[:2]
            if a1 < 0 or b1 < 0 or a2 > tw or b2 > th:
                continue
            region = tex[b1:b2, a1:a2].astype(np.float32)
            a = region[..., 3:4]
            # a fully transparent UV region means the face is not textured at
            # all (hidden geometry) - do not draw it
            if float((a > 0.5).mean()) < 0.06:
                continue
            patch = region[..., :3]
            if u2 < u1:
                patch = patch[:, ::-1]
            if v2 < v1:
                patch = patch[::-1, :]
            ph, pw = patch.shape[:2]

            src = np.array([[0, 0], [pw, 0], [pw, ph], [0, ph]], np.float32)
            dst = (sp - np.array([x0, y0], np.float32)).astype(np.float32)
            area = abs(cv2.contourArea(dst.reshape(-1, 1, 2)))
            if area < 1.2:
                continue
            Mtex = cv2.getPerspectiveTransform(src, dst)
            warp = cv2.warpPerspective(patch, Mtex, (W, H), flags=cv2.INTER_LINEAR,
                                       borderMode=cv2.BORDER_REPLICATE)
            m = np.zeros((H, W), np.float32)
            cv2.fillConvexPoly(m, dst.astype(np.int32), 1.0, lineType=cv2.LINE_AA)
            if float((a > 0.5).mean()) < 0.995:   # partially transparent overlay
                alpha = cv2.warpPerspective(np.ascontiguousarray(a), Mtex, (W, H),
                                            flags=cv2.INTER_LINEAR,
                                            borderMode=cv2.BORDER_REPLICATE)
                if u2 < u1:
                    alpha = alpha[:, ::-1]
                if v2 < v1:
                    alpha = alpha[::-1, :]
                m *= (alpha > 0.45)


            # lighting
            n = wn / (np.linalg.norm(wn) + 1e-9)
            diff = max(float(n @ sun), 0.0)
            shade = ambient + (1.0 - ambient) * diff
            up = max(float(n[1]), 0.0)
            tint = sky * (0.10 * up * ambient)
            col = warp * shade + tint * warp.mean(axis=2, keepdims=True)
            # aerial perspective
            fog = 1.0 - np.exp(-max(depth - 8.0, 0.0) / 95.0)
            col = col * (1 - 0.55 * fog) + np.array([0.74, 0.82, 0.90], np.float32) * 0.55 * fog

            region = self.img[y0:y1, x0:x1]
            zq = np.full((H, W), float(depth), np.float32)
            keep = (m > 0.5) & (zq < self.zbuf[y0:y1, x0:x1] + 0.35)
            region[keep] = col[keep]
            self.zbuf[y0:y1, x0:x1][keep] = depth
            self.dist[y0:y1, x0:x1][keep] = depth

    # -------------------------------------------------------------- finish --
    def finish(self, focus=4.0, aperture=1.0, fog=0.30, bloom=0.32,
               exposure=1.0, grain=0.010, vignette=0.30, seed=0, warm=1.0):
        # downscale first: every pass below is far cheaper at delivery size
        if self.ssaa != 1.0:
            img = cv2.resize(np.clip(self.img, 0, 1), (self.out_w, self.out_h),
                             interpolation=cv2.INTER_AREA)
            dist = cv2.resize(self.dist, (self.out_w, self.out_h),
                              interpolation=cv2.INTER_NEAREST)
        else:
            img, dist = self.img, self.dist
        H, W = self.out_h, self.out_w
        img = self._dof(img, dist, focus, aperture, H)
        if fog > 0:
            haze = np.clip(dist / 95.0, 0, 1)[..., None] ** 1.2
            img = img * (1 - fog * haze) + np.array([0.74, 0.82, 0.92], np.float32) * fog * haze

        x = np.clip(img * exposure, 0, None)
        hi = np.clip(x - 0.78, 0, None) ** 1.15
        x = x + cv2.GaussianBlur(hi, (0, 0), H / 110.0) * bloom

        lum = (0.2126 * x[..., 0] + 0.7152 * x[..., 1] + 0.0722 * x[..., 2])[..., None]
        x = (x - 0.42) * 1.09 + 0.42
        x = x - 0.14 * np.clip(x, 0, 1) ** 3
        sh = np.clip(1 - lum * 1.9, 0, 1) ** 1.4
        hi2 = np.clip((lum - 0.45) * 2.2, 0, 1) ** 1.1
        x = x + sh * np.array([0.028, 0.012, 0.0], np.float32)
        x = x + hi2 * np.array([0.0, 0.008, 0.024], np.float32) * warm

        sat = (0.2126 * x[..., 0] + 0.7152 * x[..., 1] + 0.0722 * x[..., 2])[..., None]
        x = sat + (x - sat) * 1.06

        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        nx = (xx - W / 2) / (W / 2)
        ny = (yy - H / 2) / (H / 2)
        vig = 1 - vignette * np.clip(np.sqrt(nx ** 2 + ny ** 2 * 0.85) - 0.42, 0, 1) ** 1.5
        x = x * vig[..., None]

        rng = np.random.default_rng(seed)
        x = x + rng.normal(0, grain, (H, W, 1)).astype(np.float32)
        return np.clip(x, 0, 1)

    @staticmethod
    def _dof(img, dist, focus, aperture, H):
        if aperture <= 0.001:
            return img
        coc = np.clip(np.abs(dist - focus) / max(focus, 0.5) * aperture, 0, 1) ** 1.2
        if coc.max() < 0.02:
            return img
        b1 = cv2.GaussianBlur(img, (0, 0), max(H / 300.0, 0.8))
        b2 = cv2.GaussianBlur(img, (0, 0), max(H / 120.0, 2.0))
        w1 = np.clip(coc * 2.0, 0, 1)[..., None]
        w2 = np.clip(coc * 2.0 - 1.0, 0, 1)[..., None] * 0.85
        out = img * (1 - w1) + b1 * w1
        out = out * (1 - w2) + b2 * w2
        return out


def _faces(cube):
    from bbmodel import cube_faces
    return cube_faces(cube)
