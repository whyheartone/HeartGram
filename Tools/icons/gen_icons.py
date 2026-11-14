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



def curve_points(cx, cy, width, n=1800, smooth=90):
    """Сердце по кривой Nothing; углы (низ и ложбинка сверху) слегка скруглены скользящим средним."""
    raw = []
    for i in range(n):
        t = 2 * math.pi * i / n
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        raw.append((x, y))
    half = smooth // 2
    pts = []
    for i in range(n):
        sx = sum(raw[(i + k) % n][0] for k in range(-half, half + 1)) / (smooth + 1)
        sy = sum(raw[(i + k) % n][1] for k in range(-half, half + 1)) / (smooth + 1)
        pts.append((cx + sx * width / 32.0, cy - sy * width / 32.0 - width * 0.03))
    return pts


def _heart_path(cx=54.0, cy=54.0, width=41.8, n=96):
    """Контур сердца для вектора (viewport 108)."""
    pts = curve_points(cx, cy, width)[::1800 // n]
    return "M" + " L".join("%.2f,%.2f" % p for p in pts) + " Z"


def heart_mask(size):
    """Маска сердца для растровых иконок: центр холста, ширина как у основной иконки."""
    k = 4
    big = Image.new("L", (size * k, size * k), 0)
    ImageDraw.Draw(big).polygon(curve_points(size * k / 2, size * k / 2, size * k * 72 / 108 * 0.58), fill=255)
    return big.resize((size, size), Image.LANCZOS)


# сердце одной формы везде (viewport 108)
HEART_PATH = _heart_path()

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
        group_open = f'    <group android:pivotX="54" android:pivotY="54" android:scaleX="{scale}" android:scaleY="{scale}">\n'
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


def unified_heart(old_fg, size):
    """Сердце главной иконки по общей кривой; цвет берётся из прежнего слоя (верх и низ), плюс мягкая тень."""
    from PIL import ImageFilter
    px = old_fg.convert("RGBA")
    w, h = px.size
    data = px.load()
    ys = [y for y in range(h) for x in range(0, w, 4) if data[x, y][3] > 200]
    y0, y1 = min(ys), max(ys)
    def mean(rows):
        acc = [0, 0, 0]; cnt = 0
        for y in rows:
            for x in range(0, w, 3):
                r, g, b, a = data[x, y]
                if a > 200:
                    acc[0] += r; acc[1] += g; acc[2] += b; cnt += 1
        return tuple(c / max(cnt, 1) for c in acc)
    top = mean(range(y0, y0 + max(2, (y1 - y0) // 4)))
    bot = mean(range(y1 - max(2, (y1 - y0) // 4), y1))
    mask = heart_mask(size)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    shadow = Image.new("RGBA", (size, size), (20, 0, 20, 0))
    shadow.putalpha(mask.filter(ImageFilter.GaussianBlur(size / 60)).point(lambda v: int(v * 0.35)))
    out.alpha_composite(shadow.transform(shadow.size, Image.AFFINE, (1, 0, 0, 0, 1, -size * 0.012)))
    grad = Image.new("RGB", (size, size))
    gp = grad.load()
    cy0 = size / 2 - size * 72 / 108 * 0.58 * 0.5
    cy1 = size / 2 + size * 72 / 108 * 0.58 * 0.5
    for y in range(size):
        t = min(1, max(0, (y - cy0) / (cy1 - cy0)))
        col = tuple(int(top[i] * (1 - t) + bot[i] * t) for i in range(3))
        for x in range(size):
            gp[x, y] = col
    heart = grad.convert("RGBA")
    heart.putalpha(mask)
    out.alpha_composite(heart)
    return out


# ── основная иконка ───────────────────────────────────────────────────────────
def main_icon():
    for dens, size in DENSITIES.items():
        bg, fg = icon_layers.layers(SOURCE, size)
        save(bg.convert("RGB"), "exteraless_icon_art.png", f"drawable-{dens}")
        save(unified_heart(fg, size), "exteraless_icon_foreground.png", f"drawable-{dens}")
        # логотип в настройках и в QR: готовая иконка без обрезки по маске
        logo = icon_layers.composite(SOURCE, size, "square").convert("RGB")
        save(logo, "exteraless_icon.png", f"drawable-{dens}")
    heart_vector("exteraless_icon_monochrome")
    # плитка пункта настроек и меню: белое сердце нужного размера
    heart_vector("exteraless_icon_tile", size_dp=72, scale=1.5)
    heart_vector("exteraless_icon_menu", size_dp=24, scale=1.9)
    adaptive("ic_launcher_exteraless", "exteraless_icon_art", "exteraless_icon_foreground", "exteraless_icon_monochrome")
    write(os.path.join(RES, "drawable", "exteraless_icon_blank.xml"), f"""<?xml version="1.0" encoding="utf-8"?>
<vector {NS} android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#00000000" android:pathData="M0,0h1v1h-1z"/>
</vector>
""")


# ── Mono и Nothing: сердце того же размера, что у основной иконки ────────────
def heart_points(cx, cy, width, n=1800):
    return curve_points(cx, cy, width, n)


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
    mask432 = heart_mask(432)
    for vid, _title in VARIANTS:
        name = f"hg_icon_{vid}"
        hg_art.make(vid, 432).convert("RGB").save(os.path.join(RES, "drawable-xxxhdpi", name + "_img.png"))
        hg_art.make_heart(vid, mask432).save(os.path.join(RES, "drawable-xxxhdpi", name + "_heart.png"))
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
