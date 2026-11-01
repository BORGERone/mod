"""THE COFFIN ON THE SHORE — a 30 second cutscene built from the mod's own rigs.

Run:
    python cine.py            # full render -> the_coffin_on_the_shore.mp4
    python cine.py stills     # preview frames -> stills/s_<t>.jpg
    python cine.py 10 20      # render a time range only (seconds)

Everything in frame is real geometry: Ivy's bones animated from her clips plus
hand authored poses, the coffin model, and a procedural beach village.
"""
import os
import subprocess
import sys
import wave

import cv2
import numpy as np

import bbmodel as bb
import props
import stage

HERE = os.path.dirname(os.path.abspath(__file__))
FPS = 24
W, H = 1920, 1080
SSAA = 2.0
SUN = np.array([0.30, 0.55, 0.78], np.float64)   # afternoon, sea behind camera
UNIT = 1.0 / 16.0

ivy = bb.Model(os.path.join(HERE, "assets", "ivy.bbmodel"))
coffin = bb.Model(os.path.join(HERE, "assets", "corpse_coffin_v2.bbmodel"))

# set dressing sits behind the action
BG = 0.0


def bg_items():
    return props.SCENE


def _mtx(pos, yaw, scale=UNIT):
    c, s = np.cos(np.deg2rad(yaw)), np.sin(np.deg2rad(yaw))
    M = np.eye(4)
    M[:3, :3] = scale * np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    M[:3, 3] = np.asarray(pos, np.float64)
    return M


def ivy_at(t, anim="idle", anim_t=None, yaw=180.0, pos=(0, 0, 0), clip_w=1.0, clip2=None):
    """Ivy placed in the world, optionally cross-faded between two clips."""
    at = t if anim_t is None else anim_t
    if clip2 is None:
        vals = ivy.sample(anim, at)
    else:
        a = ivy.sample(anim, at)
        b = ivy.sample(clip2, at)
        vals = {}
        for k in set(a) | set(b):
            va = a.get(k, {"rot": np.zeros(3), "pos": np.zeros(3)})
            vb = b.get(k, {"rot": np.zeros(3), "pos": np.zeros(3)})
            vals[k] = {"rot": va["rot"] * clip_w + vb["rot"] * (1 - clip_w),
                       "pos": va["pos"] * clip_w + vb["pos"] * (1 - clip_w)}
    M = _mtx(pos, yaw)
    return [(e, M @ Mw) for e, Mw in ivy.walk(vals)]


def coffin_at(pos=(1.4, 0, 0.8), yaw=-32.0):
    M = _mtx(pos, yaw)
    return [(e, M @ Mw) for e, Mw in coffin.walk({})]


def camera(pos, target, fov=36.0, roll=0.0, shake=0.5, t=0.0, seed=1):
    rng = np.random.default_rng(seed + int(t * FPS))
    jitter = rng.normal(0, 0.006 * shake, 3)
    jitter[1] *= 0.6
    p = np.asarray(pos, np.float64) + jitter
    return stage.Camera(p, target, fov=fov, roll=roll)


def ease(a):
    a = min(max(a, 0.0), 1.0)
    return a * a * (3 - 2 * a)


def lerp(a, b, f):
    return a + (b - a) * f


# ------------------------------------------------------------------- shots --
def shot1(t, lt):
    """Wide: she walks along the surf, the coffin already in sight."""
    z = 3.0 - 0.95 * lt
    items = [(bg_items(), props.ATLAS.textures),
             (ivy_at(t, "walk", lt * 1.35, yaw=104.0, pos=(-2.0, 0, z)), ivy.textures),
             (coffin_at((1.2, 0, z - 2.6)), coffin.textures)]
    a = ease(lt / 6.0)
    cam = camera((lerp(-0.6, 0.9, a), lerp(1.62, 1.44, a), z + lerp(5.8, 4.9, a)),
                 (-0.30, lerp(0.92, 0.80, a), z - 1.3), fov=lerp(42, 37, a),
                 shake=0.5, t=t, seed=3)
    return items, cam, dict(focus=5.6, aperture=0.85, fog=0.26, warm=1.05)


def shot2(t, lt):
    """She stops and looks down at it."""
    stop = ease(lt / 1.6)
    items = [(bg_items(), props.ATLAS.textures),
             (ivy_at(t, "walk" if lt < 1.6 else "idle", lt, yaw=lerp(104, 150, stop),
                     pos=(-1.15, 0, -0.55)), ivy.textures),
             (coffin_at((1.05, 0, 0.45), yaw=-26.0), coffin.textures)]
    cam = camera((lerp(-0.15, 0.45, ease(lt / 4.0)), 1.24, 4.8), (0.10, 0.92, 0.1),
                 fov=33, shake=0.4, t=t, seed=7)
    return items, cam, dict(focus=4.6, aperture=1.0, fog=0.26, warm=1.05)


def shot3(t, lt):
    """Low detail: the coffin, red trim, Ivy standing behind."""
    items = [(bg_items(), props.ATLAS.textures),
             (coffin_at((0.0, 0, 0.0), yaw=-20.0), coffin.textures),
             (ivy_at(t, "idle", lt, yaw=196.0, pos=(2.35, 0, -0.5)), ivy.textures)]
    a = ease(lt / 4.2)
    cam = camera((lerp(-1.6, -1.25, a), 0.42, lerp(5.2, 4.4, a)),
                 (0.05, 0.60, 0.1), fov=lerp(44, 40, a), roll=-1.5, shake=0.4, t=t, seed=11)
    return items, cam, dict(focus=3.4, aperture=1.2, fog=0.22, warm=1.10, exposure=1.10)


def shot4(t, lt):
    """She walks up to it and sits down in the sand."""
    w2sit = ease(max(0.0, (lt - 2.0) / 1.9))
    yaw = lerp(150.0, 168.0, ease(lt / 3.6))
    z = lerp(-0.7, 0.35, ease(lt / 3.2))
    items = [(bg_items(), props.ATLAS.textures),
             (ivy_at(t, "walk" if w2sit < 0.99 else "sit", lt + 0.6, yaw=yaw,
                     pos=(-0.95, 0, z), clip_w=1.0 - w2sit, clip2="sit"), ivy.textures),
             (coffin_at((1.15, 0, 0.95), yaw=-26.0), coffin.textures)]
    a = ease(lt / 6.0)
    cam = camera((lerp(-1.9, -1.2, a), lerp(0.9, 1.35, a), lerp(4.6, 3.9, a)),
                 (lerp(0.2, -0.1, a), lerp(0.55, 0.75, a), 0.4), fov=lerp(38, 34, a),
                 roll=lerp(-0.8, 0.6, a), shake=0.5, t=t, seed=13)
    return items, cam, dict(focus=3.6, aperture=1.1, fog=0.24, warm=1.05)


def shot5(t, lt):
    """Seated beside the coffin, reaching for the lid."""
    sit = ivy.sample("sit", lt + 2.6)
    reach = ease(max(0.0, (lt - 1.2) / 2.4))
    arm = ivy.sample("sit", lt + 2.6)
    vals = {k: {"rot": v["rot"].copy(), "pos": v["pos"].copy()} for k, v in sit.items()}
    # raise the right arm toward the lid on top of the sit clip
    for uuid, bone in ((_bone_uuid("rightarm"), 1.0),):
        if uuid in vals:
            vals[uuid]["rot"] = vals[uuid]["rot"] + np.array([0, 0, -55.0]) * reach * bone
    if _bone_uuid("head") in vals:
        vals[_bone_uuid("head")]["rot"] = vals[_bone_uuid("head")]["rot"] + np.array([8.0, 0, 0]) * reach
    M = _mtx((-0.62, 0, 0.55), 172.0)
    body = [(e, M @ Mw) for e, Mw in ivy.walk(vals)]
    items = [(bg_items(), props.ATLAS.textures), (body, ivy.textures),
             (coffin_at((1.15, 0, 1.05), yaw=-26.0), coffin.textures)]
    a = ease(lt / 5.0)
    cam = camera((lerp(1.75, 1.05, a), lerp(1.35, 1.05, a), lerp(4.6, 3.8, a)),
                 (-0.15, 0.80, 0.85), fov=lerp(36, 32, a), roll=lerp(1.0, -0.7, a),
                 shake=0.45, t=t, seed=17)
    return items, cam, dict(focus=3.9, aperture=1.25, fog=0.24, warm=1.02)


def shot6(t, lt):
    """She stands and walks away; the camera pulls back, the coffin stays."""
    rise = ease(lt / 1.8)
    walk = 2.6 * ease(max(0.0, (lt - 1.2) / 3.8))
    yaw = 118.0
    pos = (-0.9 + walk * np.sin(np.deg2rad(yaw)), 0, 0.4 + walk * np.cos(np.deg2rad(yaw)))
    if rise < 0.99:
        body = ivy_at(t, "sit", 3.6, yaw=172.0, pos=(-0.9, 0, 0.4),
                      clip_w=1.0 - rise, clip2="idle")
    else:
        body = ivy_at(t, "walk", lt * 1.4, yaw=yaw, pos=pos)
    items = [(bg_items(), props.ATLAS.textures), (body, ivy.textures),
             (coffin_at((1.15, 0, 1.15), yaw=-26.0), coffin.textures)]
    a = ease(lt / 5.6)
    cam = camera((lerp(1.5, 0.1, a), lerp(1.5, 2.0, a), lerp(5.4, 8.6, a)),
                 (-0.5, lerp(1.0, 0.75, a), 0.2), fov=lerp(35, 42, a),
                 shake=0.5, t=t, seed=19)
    return items, cam, dict(focus=6.8, aperture=0.7, fog=0.30, warm=1.0)


SHOTS = [(0.0, 6.0, shot1), (6.0, 10.0, shot2), (10.0, 14.2, shot3),
         (14.2, 20.2, shot4), (20.2, 25.2, shot5), (25.2, 30.8, shot6)]
TOTAL = SHOTS[-1][1]
TITLES = [("I", 1.4, 4.0), ("II", 6.8, 9.0), ("III", 20.6, 23.2)]

_UUID = {}


def _bone_uuid(name):
    if not _UUID:
        def walk(b):
            _UUID[b.name] = b.uuid
            for c in b.children:
                walk(c)
        for r in ivy.roots:
            walk(r)
    return _UUID.get(name, "")


# ------------------------------------------------------------------ render --
def render(t, ssaa=SSAA, w=W, h=H):
    for t0, t1, fn in SHOTS:
        if t0 <= t < t1:
            items, cam, look = fn(t, t - t0)
            break
    st = stage.Stage(w, h, ssaa=ssaa)
    st.cam = cam
    bd = stage.Backdrop(st.w, st.h, cam, SUN, shore_z=-26.0)
    st.new_frame(bd, t)
    st.shadow(items, SUN, strength=0.46)
    st.draw(items, SUN, ambient=0.86)
    img = st.finish(focus=look["focus"], aperture=look["aperture"], fog=look["fog"],
                    bloom=0.30, exposure=look.get("exposure", 1.05), grain=0.008,
                    vignette=0.26, seed=int(t * FPS), warm=look["warm"])

    fade = min(1.0, t / 1.6) * min(1.0, max(0.0, (TOTAL - t) / 2.2))
    img = img * fade

    out_h = img.shape[0]
    bar = int(out_h * 0.055)
    canvas = np.zeros_like(img)
    canvas[bar:out_h - bar] = img[bar:out_h - bar]

    for label, t0, t1 in TITLES:
        if t0 <= t < t1:
            k = min(1.0, (t - t0) / 0.5, (t1 - t) / 0.5)
            a = np.clip(k, 0, 1)
            out = canvas
            scale = 1.8
            (tw, thh), _ = cv2.getTextSize(label, cv2.FONT_HERSHEY_DUPLEX, scale, 3)
            x0, y0 = int(out.shape[1] * 0.075), int(bar + out.shape[0] * 0.085)
            tm = np.zeros(out.shape[:2], np.uint8)
            cv2.putText(tm, label, (x0, y0 + thh), cv2.FONT_HERSHEY_DUPLEX, scale,
                        255, 3, cv2.LINE_AA)
            alpha = (tm.astype(np.float32) / 255.0)[..., None] * (0.55 * a)
            canvas = out * (1 - alpha) + alpha
    return np.clip(canvas, 0, 1)


def frame_bytes(img):
    return (np.clip(img, 0, 1) * 255).astype(np.uint8).tobytes()  # rawvideo rgb24


# ------------------------------------------------------------------- audio --
def _wav(path, data, sr=48000):
    data = np.clip(data, -1, 1)
    pcm = (data * 32767).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(pcm.shape[1] if pcm.ndim > 1 else 1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(pcm.tobytes())


def build_audio(path, total):
    sr = 48000
    n = int(total * sr)
    t = np.linspace(0, total, n, endpoint=False)
    rng = np.random.default_rng(7)
    L = np.zeros(n)
    R = np.zeros(n)

    def add(sig, pan=0.0, gain=1.0):
        L[:] += sig * gain * (0.5 - pan * 0.4)
        R[:] += sig * gain * (0.5 + pan * 0.4)

    # surf: filtered noise, slow swell
    noise = rng.normal(0, 1, n)
    k = np.hanning(400)
    noise = np.convolve(noise, k / k.sum(), mode="same")
    swell = 0.55 + 0.45 * np.sin(2 * np.pi * 0.13 * t + 1.1)
    add(noise * swell * 0.22, -0.3)
    add(np.roll(noise, 900) * swell * 0.20, 0.3)

    # wind: very low band
    wind = rng.normal(0, 1, n)
    k2 = np.hanning(4000)
    wind = np.convolve(wind, k2 / k2.sum(), mode="same")
    add(wind * 0.30, 0.0)

    # footsteps while she walks
    def steps(t0, t1, rate):
        out = np.zeros(n)
        tt = t0
        while tt < t1:
            i = int(tt * sr)
            ln = int(0.10 * sr)
            if i + ln < n:
                env = np.exp(-np.linspace(0, 9, ln))
                cr = rng.normal(0, 1, ln) * env
                out[i:i + ln] += cr * 0.5
            tt += rate
        return out
    add(steps(0.4, 5.6, 0.52) * 0.35, -0.2)
    add(steps(14.4, 18.6, 0.5) * 0.30, -0.2)
    add(steps(26.6, 30.2, 0.5) * 0.30, 0.25)

    # low drone pad
    pad = (np.sin(2 * np.pi * 55 * t) * 0.5 + np.sin(2 * np.pi * 82.5 * t) * 0.25
           + np.sin(2 * np.pi * 110.2 * t) * 0.15)
    pad *= 0.30 + 0.22 * np.sin(2 * np.pi * 0.07 * t)
    add(pad * 0.35, 0.0)
    add(pad * 0.30, 0.0)

    # distant gull
    for t0, f0 in ((8.4, 1450), (21.6, 1700)):
        ln = int(0.6 * sr)
        i = int(t0 * sr)
        if i + ln < n:
            tt = np.linspace(0, 0.6, ln)
            g = np.sin(2 * np.pi * (f0 + 300 * np.sin(2 * np.pi * 6 * tt)) * tt)
            g *= np.hanning(ln) * 0.05
            add(g, 0.5)

    # whoosh on the cuts
    for tc in (6.0, 10.0, 14.2, 20.2, 25.2):
        ln = int(0.5 * sr)
        i = int((tc - 0.25) * sr)
        if 0 <= i and i + ln < n:
            tt = np.linspace(0, 1, ln)
            w = rng.normal(0, 1, ln) * np.sin(np.pi * tt) ** 2 * 0.10
            add(w, 0.0)

    st = np.stack([L, R], axis=1)
    st /= max(np.abs(st).max(), 1e-6) / 0.72
    _wav(path + ".wav", st, sr)
    ff = _ffmpeg()
    subprocess.run([ff, "-y", "-i", path + ".wav", "-c:a", "aac", "-b:a", "192k", path],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    os.remove(path + ".wav")


def _ffmpeg():
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()


# -------------------------------------------------------------------- main --
def main():
    args = sys.argv[1:]
    if args and args[0] == "stills":
        os.makedirs(os.path.join(HERE, "stills"), exist_ok=True)
        for t in (0.8, 3.0, 5.0, 7.0, 9.0, 11.0, 13.5, 15.5, 17.5, 19.0, 21.5,
                  23.5, 25.5, 27.0, 29.0, 30.0):
            img = render(t, ssaa=1.0, w=960, h=540)
            cv2.imwrite(os.path.join(HERE, "stills", f"s_{t}.jpg"),
                        cv2.cvtColor((np.clip(img, 0, 1) * 255).astype(np.uint8),
                                     cv2.COLOR_RGB2BGR))
            print("still", t)
        return

    t0, t1 = 0.0, TOTAL
    if len(args) >= 2:
        t0, t1 = float(args[0]), float(args[1])

    out = os.path.join(HERE, "the_coffin_on_the_shore.mp4")
    audio = os.path.join(HERE, "score.m4a")
    build_audio(audio, TOTAL)
    ff = _ffmpeg()
    proc = subprocess.Popen(
        [ff, "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
         "-r", str(FPS), "-i", "-", "-i", audio,
         "-c:v", "libx264", "-preset", "medium", "-crf", "18",
         "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k",
         "-shortest", out],
        stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    n0, n1 = int(t0 * FPS), int(t1 * FPS)
    for i in range(n0, n1):
        t = i / FPS
        proc.stdin.write(frame_bytes(render(t)))
        if (i - n0) % FPS == 0:
            print(f"  {i}/{int(TOTAL * FPS)} {t:.1f}s", flush=True)
    proc.stdin.close()
    proc.wait()
    print("done", out, f"{os.path.getsize(out) / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
