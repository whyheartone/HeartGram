"""Новые иконки HeartGram: у каждого варианта свой нарисованный мир, как у exteraGram
(неон, лёд, космос, постер…), а сердце плоское — чистая заливка, мягкая тень или свечение, без «объёма».
Интерфейс как у hg_backgrounds: make(kind, size) — фон, make_heart(kind, mask) — сердце (RGBA) по маске основной иконки.
"""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

S = 432


def _hex(c):
    c = c.lstrip("#")
    return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))


def _blur(a, r):
    im = Image.fromarray(np.clip(a * 255, 0, 255).astype(np.uint8), "L").filter(ImageFilter.GaussianBlur(r))
    return np.asarray(im).astype(np.float32) / 255.0


def _grid(size):
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    return xx / size, yy / size


def _vgrad(size, top, bottom, angle=0.0):
    x, y = _grid(size)
    t = np.clip(y * np.cos(angle) + x * np.sin(angle), 0, 1)[..., None]
    return np.array(_hex(top), np.float32) * (1 - t) + np.array(_hex(bottom), np.float32) * t


def _radial(size, cx, cy, r, color, strength, base):
    x, y = _grid(size)
    d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2) / r
    k = np.clip(1 - d, 0, 1) ** 2 * strength
    c = np.array(_hex(color), np.float32)
    return base + (c - base) * k[..., None]


def _stars(img, n, seed, size, color=(255, 255, 255), rmax=2.2):
    rng = np.random.default_rng(seed)
    d = ImageDraw.Draw(img, "RGBA")
    for _ in range(n):
        x, y = rng.uniform(0, size, 2)
        r = rng.uniform(0.6, rmax) * size / 432
        a = int(rng.uniform(90, 255))
        d.ellipse((x - r, y - r, x + r, y + r), fill=color + (a,))


def _sparkle(img, cx, cy, r, color):
    d = ImageDraw.Draw(img, "RGBA")
    w = r * 0.16
    d.polygon([(cx, cy - r), (cx + w, cy - w), (cx + r, cy), (cx + w, cy + w), (cx, cy + r),
               (cx - w, cy + w), (cx - r, cy), (cx - w, cy - w)], fill=color + (235,))


def _to_img(arr):
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")


def _snowflake(d, cx, cy, r, width, alpha):
    """Снежинка с шестью лучами и боковыми веточками."""
    import math
    for k in range(6):
        a = k * math.pi / 3
        ex, ey = cx + r * math.cos(a), cy + r * math.sin(a)
        d.line([(cx, cy), (ex, ey)], fill=(255, 255, 255, alpha), width=width)
        for t, ln in ((0.45, 0.32), (0.72, 0.22)):
            bx, by = cx + r * t * math.cos(a), cy + r * t * math.sin(a)
            for side in (-1, 1):
                b = a + side * math.pi / 3
                d.line([(bx, by), (bx + r * ln * math.cos(b), by + r * ln * math.sin(b))],
                       fill=(255, 255, 255, alpha), width=max(1, width - 1))


def _bez(p0, p1, p2, p3, t):
    u = 1 - t
    x = u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0]
    y = u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1]
    dx = 3 * u * u * (p1[0] - p0[0]) + 6 * u * t * (p2[0] - p1[0]) + 3 * t * t * (p3[0] - p2[0])
    dy = 3 * u * u * (p1[1] - p0[1]) + 6 * u * t * (p2[1] - p1[1]) + 3 * t * t * (p3[1] - p2[1])
    import math
    return (x, y), math.atan2(dy, dx)


def _leaf_poly(cx, cy, length, width, ang):
    import math
    pts = []
    for i in range(0, 21):
        t = i / 20
        x = length * t
        y = width * math.sin(math.pi * t) ** 0.8
        pts.append((x, y))
    pts += [(x, -y) for x, y in reversed(pts)]
    c, s = math.cos(ang), math.sin(ang)
    return [(cx + x * c - y * s, cy + x * s + y * c) for x, y in pts]


def _rose(img, cx, cy, r):
    """Роза сверху: кольца лепестков со светом сверху-слева и тёмной серединой, мягкие тени между лепестками."""
    import math
    d = ImageDraw.Draw(img, "RGBA")
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).ellipse((cx - r * 1.05, cy - r * 0.95, cx + r * 1.05, cy + r * 1.15), fill=(30, 0, 12, 140))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(r * 0.12)))
    rings = [(1.0, 9, "#C93A62", "#F58FAE"), (0.78, 7, "#D84A74", "#FFA7C0"), (0.56, 6, "#E5607F", "#FFC0D1"),
             (0.36, 5, "#F27C98", "#FFD4DF"), (0.18, 4, "#FF9DB4", "#FFE2EA")]
    for ri, (k, n, dark, light) in enumerate(rings):
        for i in range(n):
            a = ri * 0.7 + i * 2 * math.pi / n
            px, py = cx + r * k * 0.34 * math.cos(a), cy + r * k * 0.34 * math.sin(a)
            pr = r * k * 0.52
            petal = Image.new("RGBA", img.size, (0, 0, 0, 0))
            pd = ImageDraw.Draw(petal)
            pd.ellipse((px - pr, py - pr, px + pr, py + pr), fill=_hex(dark) + (255,))
            # свет на внешней стороне лепестка
            lx, ly = px + pr * 0.28 * math.cos(a), py + pr * 0.28 * math.sin(a)
            pd.ellipse((lx - pr * 0.72, ly - pr * 0.72, lx + pr * 0.72, ly + pr * 0.72), fill=_hex(light) + (255,))
            petal = petal.filter(ImageFilter.GaussianBlur(r * 0.012))
            img.alpha_composite(petal)
            # тонкая тёмная кромка у границы лепестка к центру
            edge = Image.new("RGBA", img.size, (0, 0, 0, 0))
            ImageDraw.Draw(edge).arc((px - pr, py - pr, px + pr, py + pr), math.degrees(a) + 120, math.degrees(a) + 240,
                                     fill=(120, 10, 40, 150), width=max(2, int(r * 0.03)))
            img.alpha_composite(edge.filter(ImageFilter.GaussianBlur(r * 0.01)))
    ImageDraw.Draw(img, "RGBA").ellipse((cx - r * 0.08, cy - r * 0.08, cx + r * 0.08, cy + r * 0.08), fill=(150, 20, 55, 200))



def make(kind, size=S):
    s = size
    if kind == "midnight":  # неон: тёмный пол с сеткой и фиолетовым свечением
        arr = _vgrad(s, "#0B0A14", "#1A0F2E")
        arr = _radial(s, 0.5, 0.55, 0.7, "#5B1F8C", 0.55, arr)
        img = _to_img(arr)
        d = ImageDraw.Draw(img, "RGBA")
        for i in range(-12, 13):  # перспективная сетка внизу
            d.line([(s * 0.5 + i * s * 0.02, s * 0.62), (s * 0.5 + i * s * 0.16, s)], fill=(190, 90, 255, 60), width=2)
        for k in range(6):
            y = s * (0.62 + 0.38 * (k / 6) ** 1.8)
            d.line([(0, y), (s, y)], fill=(190, 90, 255, 55), width=2)
        return img
    if kind == "snow":  # снег: холодный градиент и крупные чёткие снежинки
        arr = _vgrad(s, "#CFE3FA", "#6E9DE6", 0.25)
        arr = _radial(s, 0.5, 0.5, 0.75, "#E9F3FF", 0.45, arr)  # мягкий равномерный свет в центре, без пятна в углу
        arr = _radial(s, 0.9, 0.95, 0.7, "#3D6FD0", 0.4, arr)
        img = _to_img(arr).convert("RGBA")
        big = Image.new("RGBA", (s * 2, s * 2), (0, 0, 0, 0))
        bd = ImageDraw.Draw(big)
        rng = np.random.default_rng(21)
        for cx, cy, r, w, al in ((0.27, 0.27, 0.12, 5, 235), (0.76, 0.3, 0.09, 4, 205), (0.72, 0.76, 0.13, 5, 235),
                                 (0.25, 0.74, 0.08, 4, 190), (0.5, 0.2, 0.05, 3, 170), (0.52, 0.84, 0.045, 3, 160)):
            _snowflake(bd, cx * s * 2, cy * s * 2, r * s * 2, int(w * s / 216), al)
        for _ in range(26):
            x, y, rr = rng.uniform(0, s * 2), rng.uniform(0, s * 2), rng.uniform(2, 6) * s / 216
            bd.ellipse((x - rr, y - rr, x + rr, y + rr), fill=(255, 255, 255, int(rng.uniform(120, 230))))
        img.alpha_composite(big.resize((s, s), Image.LANCZOS))
        return img.convert("RGB")
    if kind == "lavender":  # сирень: сочный фиолетовый градиент и лучи света
        arr = _vgrad(s, "#C9B3FF", "#6A4BE0", 0.15)
        arr = _radial(s, 0.3, 0.1, 0.9, "#F6EEFF", 0.85, arr)
        arr = _radial(s, 0.85, 0.95, 0.6, "#3A22A8", 0.6, arr)
        img = _to_img(arr).convert("RGBA")
        rays = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        rd = ImageDraw.Draw(rays)
        for a0 in (-0.1, 0.12, 0.3):
            rd.polygon([(s * 0.25, -s * 0.1), (s * (0.5 + a0), s * 1.1), (s * (0.7 + a0), s * 1.1)], fill=(255, 255, 255, 38))
        img.alpha_composite(rays.filter(ImageFilter.GaussianBlur(s / 30)))
        return img.convert("RGB")
    if kind == "ocean":  # космос: глубокий синий, туманность, звёзды, кольцо планеты
        arr = _vgrad(s, "#050A28", "#0E1F63", 0.2)
        arr = _radial(s, 0.8, 0.2, 0.7, "#3A5BFF", 0.5, arr)
        arr = _radial(s, 0.15, 0.85, 0.6, "#8A2BE2", 0.4, arr)
        img = _to_img(arr).convert("RGBA")
        _stars(img, 110, 7, s)
        for cx, cy, r in ((0.16, 0.2, 20), (0.86, 0.78, 26), (0.78, 0.12, 12)):
            _sparkle(img, cx * s, cy * s, r * s / 432, (255, 224, 140))
        return img.convert("RGB")
    if kind == "mint":  # мята: глубокий зелёно-бирюзовый градиент и крупные мягкие круги
        arr = _vgrad(s, "#5EEDBE", "#0A7A68", 0.5)
        img = _to_img(arr).convert("RGBA")
        for (cx, cy, r, alpha) in ((0.95, 1.0, 0.62, 46), (0.05, 0.0, 0.42, 34), (0.78, 0.18, 0.2, 26)):
            ring = Image.new("RGBA", (s, s), (0, 0, 0, 0))
            ImageDraw.Draw(ring).ellipse((s * (cx - r), s * (cy - r), s * (cx + r), s * (cy + r)), fill=(255, 255, 255, alpha))
            img.alpha_composite(ring.filter(ImageFilter.GaussianBlur(s / 90)))
        return img.convert("RGB")
    if kind == "rose":  # роза: винный фон, крупные розы с листьями по углам, свет сверху
        arr = _vgrad(s, "#6A1736", "#2A0A18", 0.2)
        arr = _radial(s, 0.5, 0.0, 0.9, "#B02A5A", 0.55, arr)
        S2 = s * 2
        base = _to_img(arr).resize((S2, S2), Image.LANCZOS).convert("RGBA")
        k = S2 / 108.0
        d = ImageDraw.Draw(base, "RGBA")
        import math
        spots = ((24, 24, 19, 0.9), (87, 30, 13, 2.6), (84, 86, 21, 4.0), (22, 80, 14, 5.2), (56, 98, 9, 3.3))
        for (x, y, r, ang) in spots:  # листья позади каждой розы
            for da in (-0.7, 0.7, 2.4, -2.4):
                a2 = ang + da
                lx, ly = x + math.cos(a2) * r * 0.55, y + math.sin(a2) * r * 0.55
                d.polygon(_leaf_poly(lx * k, ly * k, r * 1.15 * k, r * 0.34 * k, a2), fill=(30, 98, 68, 255))
                d.polygon(_leaf_poly(lx * k, ly * k, r * 1.15 * k, r * 0.1 * k, a2), fill=(78, 168, 120, 150))
        for (x, y, r, ang) in spots:
            _rose(base, x * k, y * k, r * k)
        return base.resize((s, s), Image.LANCZOS).convert("RGB")
    if kind == "sunset":  # постер: небо и два плавных холма
        img = Image.new("RGB", (s, s), _hex("#FFB05A"))
        d = ImageDraw.Draw(img)
        sky = _vgrad(s, "#FFC76B", "#FF6F61")
        img = _to_img(sky)
        d = ImageDraw.Draw(img)
        x = np.linspace(-0.05, 1.05, s)
        for base, amp, ph, col in ((0.72, 0.05, 0.6, "#C2335B"), (0.84, 0.045, 2.4, "#7A2A6B")):
            ys = (base + amp * np.sin(x * 2 * np.pi * 0.9 + ph)) * s
            pts = [(float(xx * s), float(yy)) for xx, yy in zip(x, ys)] + [(s * 1.05, s * 1.05), (-s * 0.05, s * 1.05)]
            d.polygon(pts, fill=_hex(col))
        return img
    raise KeyError(kind)


HEART = {k: ("", "", "") for k in ("rose", "midnight", "snow", "lavender", "ocean", "mint", "sunset")}


def _layer(rgb, alpha):
    im = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    im.putalpha(Image.fromarray(np.clip(alpha * 255, 0, 255).astype(np.uint8), "L"))
    return im


def _glow(out, a, color, radius, strength):
    s = a.shape[0]
    g = Image.new("RGBA", (s, s), color + (255,))
    g.putalpha(Image.fromarray(np.clip(_blur(a, radius) * strength * 255, 0, 255).astype(np.uint8), "L"))
    out.alpha_composite(g)


def _shadow(out, a, s, color=(10, 4, 30), dy=0.025, blur=0.03, strength=0.5):
    sh = np.roll(a, int(s * dy), axis=0)
    _glow(out, sh, color, s * blur, strength)


def make_heart(kind, mask):
    s = mask.size[0]
    a = np.asarray(mask.convert("L")).astype(np.float32) / 255.0
    out = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    yy, _xx = np.mgrid[0:s, 0:s].astype(np.float32)
    ys, _xs = np.where(a > 0.5)
    y0, y1 = ys.min(), ys.max()
    t = np.clip((yy - y0) / max(float(y1 - y0), 1), 0, 1)[..., None]

    def flat(color, shadow_col, shadow_strength=0.35, glow=None, grad=None):
        """Плоское сердце, как стрелка у exteraGram: чистая заливка, только мягкая тень/свечение."""
        if glow:
            _glow(out, a, glow[0], s / 10, glow[1])
        _shadow(out, a, s, color=shadow_col, dy=0.02, blur=0.03, strength=shadow_strength)
        if grad:
            c0, c1 = np.array(grad[0], np.float32), np.array(grad[1], np.float32)
            rgb = c0 * (1 - t) + c1 * t
        else:
            rgb = np.broadcast_to(np.array(color, np.float32), (s, s, 3)).copy()
        out.alpha_composite(_layer(rgb, a))
        return out

    if kind == "midnight":  # неоновый контур
        m = Image.fromarray((a * 255).astype(np.uint8), "L")
        inner = np.asarray(m.filter(ImageFilter.MinFilter(2 * int(s * 0.03) + 1))).astype(np.float32) / 255.0
        ring = np.clip(a - inner, 0, 1)
        for rad, col, st in ((s / 8, (255, 40, 160), 0.9), (s / 22, (255, 70, 190), 1.0)):
            _glow(out, ring, col, rad, st)
        _glow(out, ring, (255, 255, 255), s / 160, 1.0)
        out.alpha_composite(_layer(np.dstack([np.full((s, s), v, np.float32) for v in (255, 235, 250)]), ring))
        return out
    if kind == "snow":  # снег: белое сердце, холодная тень
        return flat((255, 255, 255), (30, 60, 140), 0.45)
    if kind == "rose":  # роза: светлое сердце с тёплой тенью
        return flat((255, 240, 244), (60, 0, 20), 0.5)
    if kind == "lavender":  # тёмное сердце на сиреневом, как тёмная стрелка у экстеры
        return flat(None, (30, 10, 100), 0.45, grad=((58, 36, 130), (30, 18, 84)))
    if kind == "ocean":  # космос: белое сердце с голубым свечением
        return flat((255, 255, 255), (5, 20, 90), 0.4, glow=((110, 170, 255), 0.75))
    if kind == "mint":  # белое сердце, переходящее в нежно-розовый
        return flat((255, 255, 255), (0, 80, 64), 0.4)
    if kind == "sunset":  # постер: плоское сердце с жёсткой тенью и контуром
        off = int(s * 0.03)
        dark = np.full((s, s, 3), np.array([70, 20, 70], np.float32))
        shadow = np.roll(np.roll(a, off, axis=0), off, axis=1)
        out.alpha_composite(_layer(dark, shadow))
        m = Image.fromarray((a * 255).astype(np.uint8), "L")
        grown = np.asarray(m.filter(ImageFilter.MaxFilter(2 * int(s * 0.012) + 1))).astype(np.float32) / 255
        out.alpha_composite(_layer(dark, grown))
        out.alpha_composite(_layer(np.full((s, s, 3), np.array([255, 244, 214], np.float32)), a))
        return out
    raise KeyError(kind)
