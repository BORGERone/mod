"""Build preview_villager.png: original vs. villager re-skin, five angles."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_preview as rp
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ORIG = os.path.join(ROOT, 'ivy_oleander.bbmodel')
NEW = os.path.join(ROOT, 'ivy_oleander_villager.bbmodel')
VIEWS = [(180, 6), (215, 10), (270, 4), (90, 4), (0, 4)]
W = 460
PAD = 14


def main():
    orig, new = [], []
    tmp = os.path.join(ROOT, '.preview_tmp')
    os.makedirs(tmp, exist_ok=True)
    for k, (yaw, pitch) in enumerate(VIEWS):
        for model, bucket, tag in ((ORIG, orig, 'o'), (NEW, new, 'n')):
            path = os.path.join(tmp, f'{tag}{k}.png')
            rp.render(model, None, path, yaw, pitch, W, 1.0)
            bucket.append(Image.open(path))
    H = W * 2 + PAD * 3 + 90
    canvas = Image.new('RGB', (W * len(VIEWS) + PAD * (len(VIEWS) + 1), H), (24, 24, 28))
    for i, im in enumerate(orig):
        canvas.paste(im, (PAD + i * (W + PAD), 70))
    for i, im in enumerate(new):
        canvas.paste(im, (PAD + i * (W + PAD), 70 + W + PAD))
    d = ImageDraw.Draw(canvas)
    d.text((PAD, 26), 'ivy_oleander  (original)', fill=(220, 220, 220))
    d.text((PAD, 70 + W + PAD - 24),
           'ivy_oleander_villager  (new texture, same model and animations)',
           fill=(230, 210, 150))
    out = os.path.join(ROOT, 'preview_villager.png')
    canvas.save(out)
    for f in os.listdir(tmp):
        os.remove(os.path.join(tmp, f))
    os.rmdir(tmp)
    print('written', out)


if __name__ == '__main__':
    main()
