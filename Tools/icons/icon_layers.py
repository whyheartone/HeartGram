"""Слои адаптивной иконки HeartGram из круглой картинки.

Фон: узор круга, продолженный за его край (каждый пиксель снаружи берёт цвет
с края круга по тому же лучу), сердце в фоне закрашено соседями.
Передний план: само сердце с прозрачностью.
Масштаб: исходный круг (1024) = видимые 72 dp из 108 dp холста.
"""
import numpy as np
from PIL import Image, ImageFilter


def heart_alpha(rgb):
    r = rgb[..., 0].astype(np.float32)
    return np.clip((r - 85.0) / (190.0 - 85.0), 0.0, 1.0)


def layers(src_path, size):
    src = np.asarray(Image.open(src_path).convert("RGBA")).astype(np.float32)
    n = src.shape[0]
    rgb = src[..., :3]
    a = heart_alpha(rgb) * (src[..., 3] / 255.0)
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    a = a * (np.hypot(xx - n / 2.0, yy - n / 2.0) < n * 0.44)  # только сердце, не край круга

    # фон без сердца: заливаем сердце и его кайму размытым окружением
    mask = Image.fromarray((a > 0.02).astype(np.uint8) * 255).filter(ImageFilter.MaxFilter(25))
    m = np.asarray(mask).astype(np.float32) / 255.0
    bg = rgb.copy()
    known = 1.0 - m
    acc = np.zeros_like(bg)
    w = np.zeros(bg.shape[:2], np.float32)
    for radius in (40, 80, 160):
        k = np.asarray(Image.fromarray((known * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius))).astype(np.float32) / 255.0
        c = np.stack([np.asarray(Image.fromarray((bg[..., i] * known).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius))).astype(np.float32) for i in range(3)], -1)
        acc += c
        w += k
    fill = acc / np.maximum(w[..., None], 1e-3)
    bg = bg * known[..., None] + fill * m[..., None]

    # холст size: круг занимает 72/108, снаружи — цвет края по лучу
    inner = size * 72.0 / 108.0
    scale = n / inner
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float32)
    cx = cy = (size - 1) / 2.0
    dx, dy = (xs - cx) * scale, (ys - cy) * scale  # в пикселях исходника от центра
    r = np.sqrt(dx * dx + dy * dy)
    rmax = n * 0.46
    k = np.where(r > rmax, rmax / np.maximum(r, 1e-3), 1.0)
    sx = np.clip(n / 2.0 + dx * k, 0, n - 1).astype(np.int32)
    sy = np.clip(n / 2.0 + dy * k, 0, n - 1).astype(np.int32)
    sharp = Image.fromarray(np.clip(bg[sy, sx], 0, 255).astype(np.uint8), "RGB")
    soft = sharp.filter(ImageFilter.GaussianBlur(size / 14.0))
    # снаружи круга — мягкое продолжение узора (без лучей), внутри — как было
    t = np.clip((r - rmax * 0.92) / (rmax * 0.25), 0.0, 1.0)[..., None]
    mixed = np.asarray(sharp).astype(np.float32) * (1 - t) + np.asarray(soft).astype(np.float32) * t
    bg_img = Image.fromarray(np.clip(mixed, 0, 255).astype(np.uint8), "RGB")

    # передний план: сердце
    fg_full = np.dstack([rgb, a * 255.0]).astype(np.uint8)
    fg_src = Image.fromarray(fg_full, "RGBA").resize((round(inner), round(inner)), Image.LANCZOS)
    fg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    off = (size - round(inner)) // 2
    fg.alpha_composite(fg_src, (off, off))
    return bg_img.convert("RGBA"), fg


def composite(src_path, size, shape="square"):
    """Готовая иконка (для старых лаунчеров и превью): фон + сердце, вырезанные маской 72 dp."""
    big = round(size * 108 / 72)
    bg, fg = layers(src_path, big)
    bg.alpha_composite(fg)
    off = (big - size) // 2
    img = bg.crop((off, off, off + size, off + size))
    if shape == "circle":
        from PIL import ImageDraw
        m = Image.new("L", (size * 4, size * 4), 0)
        ImageDraw.Draw(m).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
        img.putalpha(m.resize((size, size), Image.LANCZOS))
    elif shape == "squircle":
        from PIL import ImageDraw
        m = Image.new("L", (size * 4, size * 4), 0)
        ImageDraw.Draw(m).rounded_rectangle((0, 0, size * 4 - 1, size * 4 - 1), radius=size * 4 * 0.3, fill=255)
        img.putalpha(m.resize((size, size), Image.LANCZOS))
    return img


if __name__ == "__main__":
    import sys
    src, out = sys.argv[1], sys.argv[2]
    bg, fg = layers(src, 432)
    bg.save(out + "/bg.png")
    fg.save(out + "/fg.png")
    for shape in ("square", "squircle", "circle"):
        composite(src, 288, shape).save(f"{out}/{shape}.png")
