"""Genera muxy-pc.ico a partir de las mismas formas que ic_launcher_foreground.xml
(el icono de la app Android), dibujadas con Pillow en vez de un renderizador SVG
(evita depender de cairo nativo, que no está instalado en esta máquina)."""

from PIL import Image, ImageDraw

SCALE = 8  # supersample para que las curvas salgan suaves al reducir
W = H = 108 * SCALE
OX, OY = 18 * SCALE, 20 * SCALE  # mismo translate(18,20) que el XML


def pt(x, y):
    return (OX + x * SCALE, OY + y * SCALE)


def ellipse_bbox(x0, y0, x1, y1):
    return [pt(x0, y0), pt(x1, y1)]


def rgba(hexcolor, alpha=255):
    h = hexcolor.lstrip("#")
    r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
    return (r, g, b, alpha)


def cubic_bezier_points(p0, p1, p2, p3, steps=40):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        mt = 1 - t
        x = mt**3 * p0[0] + 3 * mt**2 * t * p1[0] + 3 * mt * t**2 * p2[0] + t**3 * p3[0]
        y = mt**3 * p0[1] + 3 * mt**2 * t * p1[1] + 3 * mt * t**2 * p2[1] + t**3 * p3[1]
        pts.append((x, y))
    return pts


img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

# Fondo redondeado (igual que launcher_background)
radius = 24 * SCALE
draw.rounded_rectangle([0, 0, W, H], radius=radius, fill=rgba("#EADCC2"))

# Nenúfar (aprox. elipse del bbox del path original)
draw.ellipse(ellipse_bbox(10, 38, 62, 64), fill=rgba("#4A6B3D"))
draw.polygon([pt(36, 51), pt(58, 44), pt(62, 51)], fill=rgba("#4F8B87", 90))

# Cabeza
draw.ellipse(ellipse_bbox(13, 8, 59, 48), fill=rgba("#6E9159"))

# Ojos (bulto, blanco, pupila) para cada lado
for cx in (25, 47):
    draw.ellipse(ellipse_bbox(cx - 9, 12 - 9, cx + 9, 12 + 9), fill=rgba("#6E9159"))
    draw.ellipse(ellipse_bbox(cx - 6.5, 12 - 6.5, cx + 6.5, 12 + 6.5), fill=rgba("#FFFCF4"))
    pupil_cx = cx + 0.5
    draw.ellipse(ellipse_bbox(pupil_cx - 3.6, 13 - 3.6, pupil_cx + 3.6, 13 + 3.6), fill=rgba("#1F2C1C"))

# Mofletes
for cx in (19, 53):
    draw.ellipse(ellipse_bbox(cx - 4.5, 29 - 3.2, cx + 4.5, 29 + 3.2), fill=rgba("#DE8B85", 140))

# Sonrisa (bezier cúbica como trazo)
smile_pts = [
    pt(x, y)
    for x, y in cubic_bezier_points((29, 28), (32, 32), (40, 32), (43, 28))
]
draw.line(smile_pts, fill=rgba("#1F2C1C", 191), width=int(2.4 * SCALE), joint="curve")

# Reduce con antialiasing y exporta .ico multi-resolución
sizes = [16, 24, 32, 48, 64, 128, 256]
base = img.resize((256, 256), Image.LANCZOS)
base.save("assets/muxy-pc.ico", sizes=[(s, s) for s in sizes])
base.save("assets/icon_preview.png")
print("ok")
