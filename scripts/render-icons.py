#!/usr/bin/env python3
"""Render Mola's icon artwork from one source of geometry.

The launcher icon is a vector drawable and Play Console needs rasters. Rather than keep two
drawings in sync by hand, this script holds the geometry (a 108-unit grid matching the
vector's viewport) and emits every artefact from it.

    python scripts/render-icons.py

Outputs:
  store/icon-512.png                                           Play Console app icon
  store/feature-graphic-1024x500.png                           Play Console feature graphic
  app/src/main/res/drawable/ic_mola_launcher_foreground.xml     adaptive icon foreground
  app/src/main/res/drawable/ic_mola_launcher_monochrome.xml     themed icon (Android 13+)
  app/src/main/res/drawable/ic_mola_notification.xml            status bar icon

Re-run after changing any geometry constant so everything stays the same drawing.
"""

from __future__ import annotations

import math
import pathlib

from PIL import Image, ImageDraw, ImageFont

# --- Palette (mirrors ui/theme/Color.kt) ------------------------------------------------

GREEN_HEX = "#246B5C"        # MolaGreen
CREAM_HEX = "#F7F4EE"        # MolaWarmBackground

GREEN = (36, 107, 92)
CREAM = (247, 244, 238)
INK = (27, 33, 31)           # MolaInk
INK_MUTED = (93, 102, 98)    # MolaInkMuted

# --- Geometry on the 108-unit grid ------------------------------------------------------

PETAL_PIVOT = (54.0, 78.0)

# (rotation, length scale). The outer pair splays wider and shorter, the way a water lily
# opens. It also keeps every tip inside the adaptive icon's 36-unit safe radius.
PETAL_LAYOUT = (
    (-68.0, 0.63),
    (-34.0, 0.85),
    (0.0, 1.00),
    (34.0, 0.85),
    (68.0, 0.63),
)

# Shapes are (start, [(c1, c2, end), ...]) cubic segment lists, implicitly closed.
# A segment whose three points are equal is emitted as a straight line.
PETAL = (
    (54.0, 78.0),
    [
        ((45.0, 66.0), (43.0, 42.0), (51.0, 26.5)),
        ((52.2, 24.2), (55.8, 24.2), (57.0, 26.5)),
        ((65.0, 42.0), (63.0, 66.0), (54.0, 78.0)),
    ],
)

# The figure is drawn full size on the grid, then shrunk about its own centre so the rosette
# clearly reads as the larger form. Reference art puts the figure near 45% of the flower's
# height; anything larger eats the petals it is meant to be cut out of.
FIGURE_PIVOT = (54.0, 57.9)
FIGURE_SCALE = 0.757

# Larger for the single-colour layers, where the figure stands alone.
FIGURE_SOLO_SCALE = 1.35

HEAD = (54.0, 46.0, 6.0, 6.6)  # cx, cy, rx, ry

BODY = (
    (54.0, 53.0),
    [
        ((58.4, 53.0), (61.6, 55.2), (62.8, 58.6)),
        ((64.0, 62.0), (64.6, 64.8), (66.6, 66.6)),
        ((68.0, 67.9), (69.8, 68.5), (71.4, 68.5)),
        ((69.3, 70.5), (66.0, 71.0), (63.4, 70.1)),
        ((61.0, 69.3), (59.2, 67.6), (58.0, 65.4)),
        ((50.0, 65.4), (50.0, 65.4), (50.0, 65.4)),
        ((48.8, 67.6), (47.0, 69.3), (44.6, 70.1)),
        ((42.0, 71.0), (38.7, 70.5), (36.6, 68.5)),
        ((38.2, 68.5), (40.0, 67.9), (41.4, 66.6)),
        ((43.4, 64.8), (44.0, 62.0), (45.2, 58.6)),
        ((46.4, 55.2), (49.6, 53.0), (54.0, 53.0)),
    ],
)

LEGS = (
    (54.0, 64.5),
    [
        ((59.6, 64.5), (65.0, 66.0), (68.6, 68.7)),
        ((71.3, 70.7), (72.2, 72.8), (70.4, 74.2)),
        ((68.1, 75.8), (61.4, 76.4), (54.0, 76.4)),
        ((46.6, 76.4), (39.9, 75.8), (37.6, 74.2)),
        ((35.8, 72.8), (36.7, 70.7), (39.4, 68.7)),
        ((43.0, 66.0), (48.4, 64.5), (54.0, 64.5)),
    ],
)


# --- Geometry helpers -------------------------------------------------------------------

def cubic(p0, c1, c2, p3, steps=48):
    """Flatten one cubic bezier into points, excluding the start point."""
    out = []
    for i in range(1, steps + 1):
        t = i / steps
        u = 1.0 - t
        x = u * u * u * p0[0] + 3 * u * u * t * c1[0] + 3 * u * t * t * c2[0] + t * t * t * p3[0]
        y = u * u * u * p0[1] + 3 * u * u * t * c1[1] + 3 * u * t * t * c2[1] + t * t * t * p3[1]
        out.append((x, y))
    return out


def flatten(shape):
    """Turn a shape into a polygon point list."""
    start, segments = shape
    points = [start]
    current = start
    for c1, c2, end in segments:
        if c1 == c2 == end:
            points.append(end)
        else:
            points.extend(cubic(current, c1, c2, end))
        current = end
    return points


def scale_y(points, factor, pivot):
    """Shorten a petal along its own axis, matching android:scaleY on a group."""
    if factor == 1.0:
        return points
    cy = pivot[1]
    return [(x, (y - cy) * factor + cy) for x, y in points]


def scale_xy(points, factor, pivot):
    """Uniform scale about a pivot, matching android:scaleX/scaleY on a group."""
    if factor == 1.0:
        return points
    cx, cy = pivot
    return [((x - cx) * factor + cx, (y - cy) * factor + cy) for x, y in points]


def rotate(points, angle_deg, pivot):
    """Clockwise-on-screen rotation, matching android:rotation in a y-down space.

    Android applies a group's scale before its rotation, both about the pivot, so callers
    compose these in that order to match what the vector drawable renders.
    """
    if angle_deg == 0.0:
        return points
    rad = math.radians(angle_deg)
    cos_a, sin_a = math.cos(rad), math.sin(rad)
    cx, cy = pivot
    return [
        ((x - cx) * cos_a - (y - cy) * sin_a + cx, (x - cx) * sin_a + (y - cy) * cos_a + cy)
        for x, y in points
    ]


def petal_polygons():
    petal = flatten(PETAL)
    return [
        rotate(scale_y(petal, length, PETAL_PIVOT), angle, PETAL_PIVOT)
        for angle, length in PETAL_LAYOUT
    ]


def figure_polygons(scale=FIGURE_SCALE, pivot=FIGURE_PIVOT):
    return [scale_xy(flatten(shape), scale, pivot) for shape in (BODY, LEGS)]


def head_circle(scale=FIGURE_SCALE, pivot=FIGURE_PIVOT):
    cx, cy, rx, ry = HEAD
    px, py = pivot
    return (cx - px) * scale + px, (cy - py) * scale + py, rx * scale, ry * scale


def figure_points(scale=FIGURE_SCALE):
    points = []
    for poly in figure_polygons(scale=scale):
        points.extend(poly)
    cx, cy, rx, ry = head_circle(scale=scale)
    points += [(cx - rx, cy - ry), (cx + rx, cy + ry)]
    return points


def art_points():
    points = []
    for poly in petal_polygons():
        points.extend(poly)
    return points + figure_points()


def bounds(points):
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    return min(xs), min(ys), max(xs), max(ys)


def max_art_radius(centre=(54.0, 54.0)):
    """Furthest the artwork reaches from centre. Must stay under 36 for adaptive icons."""
    cx, cy = centre
    return max(math.hypot(x - cx, y - cy) for x, y in art_points())


# --- Raster rendering -------------------------------------------------------------------

SS = 8  # supersampling factor, for antialiasing


def draw_logo(draw, *, scale, offset, leaf=GREEN, figure=CREAM):
    """Paint the rosette and the carved-out figure onto an existing canvas."""
    ox, oy = offset

    def place(points):
        return [(x * scale + ox, y * scale + oy) for x, y in points]

    for poly in petal_polygons():
        draw.polygon(place(poly), fill=leaf)

    cx, cy, rx, ry = head_circle()
    draw.ellipse(
        [
            (cx - rx) * scale + ox, (cy - ry) * scale + oy,
            (cx + rx) * scale + ox, (cy + ry) * scale + oy,
        ],
        fill=figure,
    )
    for poly in figure_polygons():
        draw.polygon(place(poly), fill=figure)


def load_font(size):
    for name in ("segoeuib.ttf", "arialbd.ttf", "seguisb.ttf", "segoeui.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return None


def fit(target_w, target_h, box, occupancy):
    """Scale and offset that centre `box` inside the target at the given occupancy."""
    x0, y0, x1, y1 = box
    scale = min(target_w * occupancy / (x1 - x0), target_h * occupancy / (y1 - y0))
    ox = (target_w - (x1 - x0) * scale) / 2.0 - x0 * scale
    oy = (target_h - (y1 - y0) * scale) / 2.0 - y0 * scale
    return scale, (ox, oy)


def render_icon(path, size=512, occupancy=0.76):
    """Full-bleed square store icon. Play applies its own corner masking."""
    big = size * SS
    image = Image.new("RGB", (big, big), CREAM)
    scale, offset = fit(big, big, bounds(art_points()), occupancy)
    draw_logo(ImageDraw.Draw(image), scale=scale, offset=offset)
    image.resize((size, size), Image.LANCZOS).save(path, "PNG", optimize=True)
    return path


def render_feature_graphic(path, size=(1024, 500)):
    width, height = size
    big = (width * SS // 4, height * SS // 4)
    image = Image.new("RGB", big, CREAM)
    draw = ImageDraw.Draw(image)

    box = bounds(art_points())
    scale, _ = fit(big[1], big[1], box, 0.66)
    art_w = (box[2] - box[0]) * scale
    art_h = (box[3] - box[1]) * scale
    left = big[0] * 0.085
    draw_logo(
        draw,
        scale=scale,
        offset=(left - box[0] * scale, (big[1] - art_h) / 2.0 - box[1] * scale),
    )

    title_font = load_font(int(big[1] * 0.20))
    body_font = load_font(int(big[1] * 0.075))
    text_x = left + art_w + big[0] * 0.055
    if title_font and body_font:
        draw.text((text_x, big[1] * 0.28), "Mola", font=title_font, fill=INK)
        draw.text((text_x, big[1] * 0.545), "Kendi koyduğun sınırda,", font=body_font, fill=INK_MUTED)
        draw.text((text_x, big[1] * 0.645), "kısa bir durak.", font=body_font, fill=INK_MUTED)
    image.resize(size, Image.LANCZOS).save(path, "PNG", optimize=True)
    return path


# --- Vector drawable generation ---------------------------------------------------------

GENERATED_NOTE = "GENERATED by scripts/render-icons.py - edit the geometry there, not here."


def num(value):
    return f"{value:g}"


def shape_path_data(shape):
    start, segments = shape
    parts = [f"M{num(start[0])},{num(start[1])}"]
    for c1, c2, end in segments:
        if c1 == c2 == end:
            parts.append(f"L{num(end[0])},{num(end[1])}")
        else:
            parts.append(
                f"C{num(c1[0])},{num(c1[1])} "
                f"{num(c2[0])},{num(c2[1])} "
                f"{num(end[0])},{num(end[1])}"
            )
    return " ".join(parts) + " Z"


def head_path_data():
    cx, cy, rx, ry = HEAD
    left, right = num(cx - rx), num(cx + rx)
    return (
        f"M{left},{num(cy)} A{num(rx)},{num(ry)} 0 0,1 {right},{num(cy)} "
        f"A{num(rx)},{num(ry)} 0 0,1 {left},{num(cy)} Z"
    )


def path_element(colour, data, indent):
    pad = " " * indent
    return (
        f"{pad}<path\n"
        f'{pad}    android:fillColor="{colour}"\n'
        f'{pad}    android:pathData="{data}" />'
    )


def figure_elements(colour, indent):
    return "\n".join(
        path_element(colour, data, indent)
        for data in (head_path_data(), shape_path_data(BODY), shape_path_data(LEGS))
    )


def vector_wrapper(body, comment, size=108):
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!--\n"
        f"    {GENERATED_NOTE}\n\n"
        f"{comment}\n"
        "-->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{size}dp"\n'
        f'    android:height="{size}dp"\n'
        f'    android:viewportWidth="{size}"\n'
        f'    android:viewportHeight="{size}">\n\n'
        f"{body}\n"
        "</vector>\n"
    )


def render_foreground_xml(path):
    petal_data = shape_path_data(PETAL)
    blocks = []
    for angle, length in PETAL_LAYOUT:
        if angle == 0.0 and length == 1.0:
            blocks.append(path_element(GREEN_HEX, petal_data, 4))
            continue
        blocks.append(
            "    <group\n"
            f'        android:pivotX="{num(PETAL_PIVOT[0])}"\n'
            f'        android:pivotY="{num(PETAL_PIVOT[1])}"\n'
            f'        android:scaleY="{num(length)}"\n'
            f'        android:rotation="{num(angle)}">\n'
            + path_element(GREEN_HEX, petal_data, 8)
            + "\n    </group>"
        )

    figure = (
        "    <group\n"
        f'        android:pivotX="{num(FIGURE_PIVOT[0])}"\n'
        f'        android:pivotY="{num(FIGURE_PIVOT[1])}"\n'
        f'        android:scaleX="{num(FIGURE_SCALE)}"\n'
        f'        android:scaleY="{num(FIGURE_SCALE)}">\n'
        + figure_elements(CREAM_HEX, 8)
        + "\n    </group>"
    )

    comment = (
        "    Five round-tipped lotus petals with a meditating figure carved out of them in\n"
        "    the background colour. Five rounded petals, the outer pair splayed and\n"
        "    shortened, read as a water lily rather than the seven-point serrated shape\n"
        f"    they would otherwise resemble. Artwork reaches {max_art_radius():.1f} units from\n"
        "    centre, inside the 36-unit safe radius, so no launcher mask clips the rosette."
    )
    body = "\n\n".join(["\n".join(blocks), figure])
    pathlib.Path(path).write_text(vector_wrapper(body, comment), encoding="utf-8")
    return path


def render_monochrome_xml(path):
    """Themed icon (Android 13+). Single colour, so the rosette is dropped."""
    body = (
        "    <group\n"
        f'        android:pivotX="{num(FIGURE_PIVOT[0])}"\n'
        f'        android:pivotY="{num(FIGURE_PIVOT[1])}"\n'
        f'        android:scaleX="{num(FIGURE_SOLO_SCALE)}"\n'
        f'        android:scaleY="{num(FIGURE_SOLO_SCALE)}"\n'
        f'        android:translateY="{num(54.0 - FIGURE_PIVOT[1])}">\n'
        + figure_elements("#FFFFFFFF", 8)
        + "\n    </group>"
    )
    comment = (
        "    The figure alone. A five-petal rosette in one flat colour would collapse into a\n"
        "    blob, and the system supplies the tint here."
    )
    pathlib.Path(path).write_text(vector_wrapper(body, comment), encoding="utf-8")
    return path


def render_notification_xml(path, size=24, margin=1.0):
    """Status bar icon: the figure alone, fitted to a small box."""
    box = bounds(figure_points(scale=1.0))
    span = size - 2 * margin
    scale = min(span / (box[2] - box[0]), span / (box[3] - box[1]))
    tx = (size - (box[2] - box[0]) * scale) / 2.0 - box[0] * scale
    ty = (size - (box[3] - box[1]) * scale) / 2.0 - box[1] * scale

    body = (
        "    <group\n"
        '        android:pivotX="0"\n'
        '        android:pivotY="0"\n'
        f'        android:scaleX="{scale:.4f}"\n'
        f'        android:scaleY="{scale:.4f}"\n'
        f'        android:translateX="{tx:.4f}"\n'
        f'        android:translateY="{ty:.4f}">\n'
        + figure_elements("#FFFFFFFF", 8)
        + "\n    </group>"
    )
    comment = (
        "    The figure alone, fitted to the 24dp box. Android tints status bar icons, so this\n"
        "    stays a flat white silhouette on transparency."
    )
    pathlib.Path(path).write_text(vector_wrapper(body, comment, size=size), encoding="utf-8")
    return path


def main():
    root = pathlib.Path(__file__).resolve().parent.parent
    store = root / "store"
    store.mkdir(exist_ok=True)
    res = root / "app/src/main/res/drawable"

    radius = max_art_radius()
    print(f"art radius from centre: {radius:.2f} / 36.00 safe -> {'OK' if radius <= 36 else 'CLIPS'}")
    for produced in (
        render_icon(store / "icon-512.png"),
        render_feature_graphic(store / "feature-graphic-1024x500.png"),
        render_foreground_xml(res / "ic_mola_launcher_foreground.xml"),
        render_monochrome_xml(res / "ic_mola_launcher_monochrome.xml"),
        render_notification_xml(res / "ic_mola_notification.xml"),
    ):
        print(pathlib.Path(produced).relative_to(root).as_posix())


if __name__ == "__main__":
    main()
