"""Генератор иконок HeartGram: запускается из корня репозитория.

    python tools/icons/gen_icons.py

Исходник: assets/icons/HeartGram-icon-round-1024.png (круглая иконка). Из неё получаются:
  * основная иконка приложения: слой фона (узор круга, продолженный за край) и слой сердца;
  * логотип для настроек и QR;
  * варианты Неон, Снег, Сирень, Космос, Мята, Постер (фон и сердце из hg_art.py);
  * Mono (контур) и Nothing (точечная матрица) с сердцем нужного размера.
Всё пишется прямо в TMessagesProj/src/main/res. Правишь параметры ниже или исходник и запускаешь заново.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "TMessagesProj", "src", "main", "res")
SOURCE = os.path.join(ROOT, "assets", "icons", "HeartGram-icon-round-1024.png")
sys.path.insert(0, HERE)

import icon_layers  # noqa: E402
import hg_art  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'

# сердце одной формы везде (viewport 108)
HEART_PATH = ("M54,75.5 C52.6,74.4 33.5,61.6 33.5,47.6 C33.5,41.2 38.3,36.5 44.1,36.5 C48.3,36.5 51.9,39 54,42.6 "
              "C56.1,39 59.7,36.5 63.9,36.5 C69.7,36.5 74.5,41.2 74.5,47.6 C74.5,61.6 55.4,74.4 54,75.5 Z")

# id, название, сердце: "pink" как у основной иконки или цвет
VARIANTS = [("midnight", "Неон"), ("snow", "Снег"), ("lavender", "Сирень"), ("ocean", "Космос"), ("mint", "Мята"), ("sunset", "Постер")]


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def save(img, name, folder):
    d = os.path.join(RES, folder)
    os.makedirs(d, exist_ok=True)
    img.save(os.path.join(d, name))


def heart_vector(name, color="#FFFFFFFF", size_dp=108, scale=None):
    """Вектор сердца. scale растягивает сердце вокруг центра (для плиток и значков меню)."""
    group_open = group_close = ""
    if scale:
        group_open = f'    <group android:pivotX="54" android:pivotY="56" android:scaleX="{scale}" android:scaleY="{scale}">\n'
        group_close = "    </group>\n"
    write(os.path.join(RES, "drawable", name + ".xml"), f"""<?xml version="1.0" encoding="utf-8"?>
<vector {NS}
    android:width="{size_dp}dp"
    android:height="{size_dp}dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{group_open}    <path
        android:fillColor="{color}"
        android:pathData="{HEART_PATH}"/>
{group_close}</vector>
""")


def adaptive(name, background, foreground, monochrome):
    body = f"""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon {NS}>
    <background android:drawable="@drawable/{background}"/>
    <foreground android:drawable="@drawable/{foreground}"/>
    <monochrome android:drawable="@drawable/{monochrome}"/>
</adaptive-icon>
"""
    write(os.path.join(RES, "mipmap-anydpi-v26", name + ".xml"), body)
    write(os.path.join(RES, "mipmap-anydpi-v26", name + "_round.xml"), body)


# ── основная иконка ───────────────────────────────────────────────────────────
def main_icon():
    for dens, size in DENSITIES.items():
        bg, fg = icon_layers.layers(SOURCE, size)
        save(bg.convert("RGB"), "exteraless_icon_art.png", f"drawable-{dens}")
        save(fg, "exteraless_icon_foreground.png", f"drawable-{dens}")
        # логотип в настройках и в QR: готовая иконка без обрезки по маске
        logo = icon_layers.composite(SOURCE, size, "square").convert("RGB")
        save(logo, "exteraless_icon.png", f"drawable-{dens}")
    heart_vector("exteraless_icon_monochrome")
    # плитка пункта настроек и меню: белое сердце нужного размера
    heart_vector("exteraless_icon_tile", size_dp=72, scale=1.0)
    adaptive("ic_launcher_exteraless", "exteraless_icon_art", "exteraless_icon_foreground", "exteraless_icon_monochrome")
    write(os.path.join(RES, "drawable", "exteraless_icon_blank.xml"), f"""<?xml version="1.0" encoding="utf-8"?>
<vector {NS} android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#00000000" android:pathData="M0,0h1v1h-1z"/>
</vector>
""")


# ── Mono и Nothing: сердце того же размера, что у основной иконки ────────────
def heart_points(cx, cy, width, n=1800):
    pts = []
    for i in range(n):
        t = 2 * math.pi * i / n
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((cx + x * width / 32.0, cy - y * width / 32.0 - width * 0.03))
    return pts


def mono_nothing():
    S = 1024
    visible = S * 72 / 108          # видимая часть адаптивной иконки
    heart_w = visible * 0.58        # ширина сердца как у основной иконки

    # Nothing: точечная матрица в видимой части, сердце горит белым
    img = Image.new("RGB", (S, S), (10, 10, 10))
    d = ImageDraw.Draw(img)
    cell = visible * 0.86 / 24      # шаг точек как раньше, но матрица на весь холст: точки видны и за краем маски
    grid = int(S / cell) + 3
    x0 = y0 = S / 2 - cell * grid / 2
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).polygon(heart_points(S / 2, S / 2, heart_w * 1.0), fill=255)
    for gy in range(grid):
        for gx in range(grid):
            cx, cy = x0 + cell * (gx + 0.5), y0 + cell * (gy + 0.5)
            if cx < -cell or cy < -cell or cx > S + cell or cy > S + cell:
                continue
            box = mask.crop((int(cx - cell / 2), int(cy - cell / 2), int(cx + cell / 2), int(cy + cell / 2)))
            data = list(box.getdata())
            cov = sum(data) / (255.0 * max(1, len(data)))
            r = cell * 0.37
            if cov > 0.15:
                v = int(80 + 175 * min(1, cov * 1.15))
                col = (v, v, v)
            else:
                col = (30, 30, 30)
            d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=col)
    # Mono: тёмный фон, тонкие линии, белый контур сердца
    mono = Image.new("RGB", (S, S), (31, 31, 31))
    dm = ImageDraw.Draw(mono)
    line, w = (72, 72, 72), int(S * 0.012)
    for a, b, c, e in [(0, 0.30, 0.30, 0.30), (0.30, 0.30, 0.30, 0.0), (1.0, 0.34, 0.72, 0.34), (0.72, 0.34, 0.72, 0.0),
                       (0, 0.70, 0.34, 0.70), (0.34, 0.70, 0.34, 1.0), (0.70, 1.0, 0.70, 0.68), (0.70, 0.68, 1.0, 0.68)]:
        dm.line((a * S, b * S, c * S, e * S), fill=line, width=w)
    r = S * 0.012
    for x, y in heart_points(S / 2, S / 2, heart_w):
        dm.ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255))
    for dens, size in DENSITIES.items():
        save(img.resize((size, size), Image.LANCZOS), "nothing_icon_background.png", f"drawable-{dens}")
        save(mono.resize((size, size), Image.LANCZOS), "mono_icon_background.png", f"drawable-{dens}")
    adaptive("ic_launcher_mono", "mono_icon_background", "app_icon_empty_foreground", "exteraless_icon_monochrome")
    adaptive("ic_launcher_nothing", "nothing_icon_background", "app_icon_empty_foreground", "exteraless_icon_monochrome")


# ── варианты hg_art ───────────────────────────────────────────────────────────
def variants():
    heart_mask = icon_layers.layers(SOURCE, 432)[1].getchannel("A")
    for vid, _title in VARIANTS:
        name = f"hg_icon_{vid}"
        hg_art.make(vid, 432).convert("RGB").save(os.path.join(RES, "drawable-xxxhdpi", name + "_img.png"))
        hg_art.make_heart(vid, heart_mask).save(os.path.join(RES, "drawable-xxxhdpi", name + "_heart.png"))
        write(os.path.join(RES, "drawable", name + "_bg.xml"),
              f'<?xml version="1.0" encoding="utf-8"?>\n<bitmap {NS} android:src="@drawable/{name}_img" android:gravity="fill"/>\n')
        write(os.path.join(RES, "drawable", name + "_fg.xml"),
              f'<?xml version="1.0" encoding="utf-8"?>\n<bitmap {NS} android:src="@drawable/{name}_heart" android:gravity="fill"/>\n')
        adaptive(f"ic_launcher_hg_{vid}", name + "_bg", name + "_fg", "exteraless_icon_monochrome")
        for suffix in ("", "_round"):
            write(os.path.join(RES, "mipmap", f"ic_launcher_hg_{vid}{suffix}.xml"), f"""<?xml version="1.0" encoding="utf-8"?>
<layer-list {NS}>
    <item android:drawable="@drawable/{name}_bg"/>
    <item android:drawable="@drawable/{name}_fg"/>
</layer-list>
""")


if __name__ == "__main__":
    main_icon()
    mono_nothing()
    variants()
    print("готово:", RES)
