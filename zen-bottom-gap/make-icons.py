#!/usr/bin/env python3
"""Рисует иконки расширения (16/32/48/96/128) без внешних ассетов.

Идея знака: «страница», приподнятая над нижней кромкой, — оранжевая
полоса внизу и есть тот самый отступ. Рисуем в 4x (512) и уменьшаем
LANCZOS, чтобы на маленьких размерах не было рваных краёв.

    python3 make-icons.py
"""

from pathlib import Path

from PIL import Image, ImageDraw

S = 4                  # супер-сэмплинг
CANVAS = 128 * S       # рисуем в координатах 128×128, умноженных на S
OUT = Path(__file__).parent / "extension" / "icons"
SIZES = (16, 32, 48, 96, 128)

BG_TOP = (255, 148, 96)
BG_BOTTOM = (238, 82, 44)
PAGE = (253, 253, 254)
LINE = (188, 198, 212)
FLOOR = (255, 255, 255, 185)


def px(v):
    return v * S


def rounded_gradient(size, radius):
    """Скруглённый квадрат с вертикальным градиентом."""
    grad = Image.new("RGB", (1, size), BG_TOP)
    draw = ImageDraw.Draw(grad)
    for y in range(size):
        t = y / max(1, size - 1)
        color = tuple(round(a + (b - a) * t) for a, b in zip(BG_TOP, BG_BOTTOM))
        draw.point((0, y), fill=color)
    grad = grad.resize((size, size))

    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(grad, (0, 0), mask)
    return out


def draw_icon(detailed=True):
    """detailed=False — упрощённый знак для 16/32 px: без строк, кромка жирнее."""
    img = rounded_gradient(CANVAS, px(27))

    # «страница» — приподнята, снизу остаётся полоса фона = отступ
    page = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    d = ImageDraw.Draw(page)
    d.rounded_rectangle((px(30), px(21), px(98), px(86)), radius=px(10), fill=PAGE)

    # строки «контента» — на мелких размерах превращаются в кашу, убираем
    if detailed:
        for y, w in ((37, 46), (50, 46), (63, 30)):
            d.rounded_rectangle((px(40), px(y), px(40 + w), px(y + 6)), radius=px(3), fill=LINE)

    img.alpha_composite(page)

    # волосяная линия нижней кромки — к ней и меряется отступ
    floor = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    df = ImageDraw.Draw(floor)
    if detailed:
        df.rounded_rectangle((px(30), px(105), px(98), px(108)), radius=px(2), fill=FLOOR)
    else:
        df.rounded_rectangle((px(28), px(103), px(100), px(109)), radius=px(3), fill=(255, 255, 255, 235))
    img.alpha_composite(floor)

    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    masters = {True: draw_icon(True), False: draw_icon(False)}
    for size in SIZES:
        icon = masters[size >= 48].resize((size, size), Image.LANCZOS)
        icon.save(OUT / f"icon-{size}.png", optimize=True)
        print("icons/icon-%d.png" % size)


if __name__ == "__main__":
    main()
