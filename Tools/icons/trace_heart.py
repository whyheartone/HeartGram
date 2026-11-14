"""Векторные сердца (меню, плитка, монохром, Monet) по форме сердца основной иконки.

    python Tools/icons/trace_heart.py

Берёт слой сердца основной иконки (drawable-xxxhdpi/exteraless_icon_foreground.png), обводит его контур
и пишет векторы, чтобы сердце везде в приложении было той же формы, что и на иконке.
"""
import os

import numpy as np
from PIL import Image
from shapely.geometry import Polygon
from skimage import measure

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, "..", "..", "TMessagesProj", "src", "main", "res"))
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'


def heart_points():
    img = Image.open(os.path.join(RES, "drawable-xxxhdpi", "exteraless_icon_foreground.png")).convert("RGBA")
    a = np.asarray(img.getchannel("A"), float) / 255.0
    # сердце непрозрачное, тень полупрозрачная: берём плотную часть
    contours = measure.find_contours(a, 0.85)
    c = max(contours, key=len)
    s = img.size[0] / 108.0  # пикселей на единицу viewport
    pts = [(x / s, y / s) for y, x in c]
    poly = Polygon(pts).buffer(0).simplify(0.06)
    return list(poly.exterior.coords)[:-1]


def write(name, pts, size_dp, scale=None):
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    path = "M" + " L".join("%.2f,%.2f" % p for p in pts) + " Z"
    group_open = group_close = ""
    if scale:
        group_open = f'    <group android:pivotX="{cx:.2f}" android:pivotY="{cy:.2f}" android:scaleX="{scale}" android:scaleY="{scale}">\n'
        group_close = "    </group>\n"
    with open(os.path.join(RES, "drawable", name + ".xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write(f"""<?xml version="1.0" encoding="utf-8"?>
<vector {NS}
    android:width="{size_dp}dp"
    android:height="{size_dp}dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{group_open}    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{path}"/>
{group_close}</vector>
""")


if __name__ == "__main__":
    pts = heart_points()
    write("exteraless_icon_monochrome", pts, 108)
    write("hg_icon_monet_fg", pts, 108)
    write("exteraless_icon_tile", pts, 72, 1.5)
    write("exteraless_icon_menu", pts, 24, 1.9)
    print("готово, точек:", len(pts))
