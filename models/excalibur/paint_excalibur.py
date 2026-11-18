"""Paint the revealed sword's opaque 512 x 1024 atlas. Requires Pillow and NumPy.

Front and reverse share silver, quillon, and escutcheon islands. The hilt wraps
once around U. Each auxiliary strip contains one small metal or lacquer swatch.
All paths, reflections, and surface colours below are painted from scratch.
"""

import argparse
from functools import partial
from itertools import pairwise
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

WIDTH, HEIGHT = 512, 1024
DEFAULT_FILE = (
    Path(__file__).resolve().parents[2]
    / "src/main/resources/assets/starcrowndoll/textures/item/excalibur_sword_atlas.png"
)
BLADE, GUARD, CREST, GRIP = (
    (8, 8, 215, 1015),
    (225, 8, 507, 166),
    (230, 180, 413, 504),
    (226, 520, 507, 842),
)
GUARD_X, GUARD_Z = 72.4, (120, 162)
CREST_X, CREST_Z = 21.7, (142, 226)


def smooth(a, b, t):
    t = np.clip((t - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    return a + (b - a) * t[..., None]


def colour(base, change, t):
    return np.array(base) + t[..., None] * np.array(change)


def gaussian(t, where, width):
    return np.exp(-(((t - where) / width) ** 2))


def coords(box, scale):
    x0, y0, x1, y1 = box
    w, h = (x1 - x0) * scale + 1, (y1 - y0) * scale + 1
    return (
        np.linspace(0, 1, w, dtype=np.float32)[None, :],
        np.linspace(0, 1, h, dtype=np.float32)[:, None],
    )


def path_points(path, transform):
    """Polyline a sequence of hand-painted quadratic strokes in its authored units."""
    a = np.array(path[0])
    points = [transform(*a)]
    for segment in path[1:]:
        b = np.array(segment[-2:])
        if len(segment) == 4:
            c = np.array(segment[:2])
            for t in np.linspace(0, 1, 17)[1:]:
                points.append(transform(*((1 - t) ** 2 * a + 2 * t * (1 - t) * c + t * t * b)))
        else:
            points.append(transform(*b))
        a = b
    return points


def mask_array(image):
    return np.asarray(image, dtype=np.float32) / 255


def soften(array, amount):
    return mask_array(
        Image.fromarray(np.uint8(np.clip(array, 0, 1) * 255)).filter(
            ImageFilter.GaussianBlur(amount)
        )
    )


def edge_light(mask, radius, scale):
    # Signed, opposing lips make the cast channel read as an inset under neutral lighting.
    blurred = soften(mask, scale * radius)
    return (np.roll(blurred, scale, axis=1) - np.roll(blurred, -scale, axis=1)) + 0.60 * (
        np.roll(blurred, scale, axis=0) - np.roll(blurred, -scale, axis=0)
    )


# The blade carries a single cut inscription just above the base of the sword.
# Coordinates run from the outer terminal toward the hilt. These are ornamental strokes.
RUNES = [
    [(4, 21), (3, 15, 5, 7), (6, 4, 10, 7), (15, 11, 22, 13), (18, 15, 15, 19), (12, 23, 15, 24)],
    [(6, 20), (7, 24, 12, 18)],
    [(18, 5), (24, 0, 26, 9), (27, 18, 31, 19), (33, 20, 36, 10), (34, 10, 31, 11)],
    [
        (32, 9),
        (35, 5, 37, 7),
        (39, 9, 36, 13),
        (45, 20, 49, 24),
        (50, 24, 48, 18),
        (44, 5, 47, 7),
        (53, 14, 57, 18),
        (58, 17, 58, 11),
        (60, 3, 64, 10),
        (66, 14, 66, 17),
        (70, 20, 75, 23),
    ],
    [(69, 13), (74, 14, 81, 22)],
    [
        (74, 12),
        (81, 19, 83, 14),
        (84, 4, 88, 8),
        (94, 15, 95, 22),
        (96, 24, 98, 21),
        (98, 13, 97, 7),
        (97, 2, 99, 3),
        (100, 9, 103, 13),
        (110, 6, 119, 11),
    ],
    [(110, 15), (105, 22, 107, 21), (111, 16, 115, 17), (123, 18, 132, 20), (132, 16, 130, 13)],
    [(122, 9), (126, 0, 132, 5), (136, 7, 131, 10)],
]


def blade(scale):
    u, v = coords(BLADE, scale)
    # The tip uses the same orthographic projection on every mesh triangle.
    # Paint the taper into UV space so the shoulder and cutting edge converge
    # continuously, including across the centre of each final triangulated quad.
    z = 626 - 486 * v
    root = np.maximum(z - 552, 0)
    tip = np.maximum(z - 580, 0)
    width_at_580 = 13.5496
    half_width = np.where(
        z < 580,
        14.3 - 0.0184 * root - 0.00030 * root**2,
        width_at_580 - 0.0352 * tip - (width_at_580 - 0.0352 * 46) * (tip / 46) ** 2,
    )
    width_ratio = np.maximum(half_width / 14.3, 0.004)
    s = np.clip((u * 2 - 1) / width_ratio, -1, 1)
    q = np.abs(s)
    # Opposite facets see different portions of the same broad reflection.
    fine_detail = smooth(1.5, 6.0, width_ratio * (BLADE[2] - BLADE[0]))
    ripple = 0.055 * np.sin(7.8 * v + 0.5) + 0.024 * np.sin(16 * v)
    face = (
        0.41
        + 0.15 * (1 - q)
        + 0.21 * np.sin(0.95 + v * 3.1)
        + 0.23 * gaussian(s + ripple, -0.33, 0.28)
        - 0.14 * gaussian(s - ripple, 0.55, 0.33)
    )
    face += np.where(s < 0, 0.105, -0.045) + 0.040 * q
    steel = colour([130, 150, 164], [90, 80, 69], face)
    # The blade is satin silver. Keep it clean, with a long break in the
    # reflection where the centre plane meets the honed cutting edge.
    bevel = smooth(0.601, 0.619, q)
    edge_value = (
        0.51
        + 0.17 * np.sin(3.9 * v + 0.47)
        + np.where(s < 0, 0.23, -0.22)
        + 0.16 * q
        + 0.110 * gaussian(v + 0.08 * s, 0.36, 0.16)
    )
    bevel_rgb = colour([145, 158, 167], [89, 81, 70], edge_value)
    steel = mix(steel, bevel_rgb, bevel)
    keen = colour(
        [172, 185, 192],
        [71, 65, 58],
        0.48 + 0.18 * np.sin(4 * v - 0.7) + np.where(s < 0, 0.22, -0.08) + 0.15 * q,
    )
    steel = mix(steel, keen, smooth(0.819, 0.842, q))
    edge_glint = gaussian(q, 0.975, 0.018) * (0.58 + 0.42 * np.sin(4.5 * v + 0.92) ** 2)
    steel += (edge_glint * fine_detail)[..., None] * [20, 20, 19]
    centre = gaussian(s, 0, 0.012) * (0.38 + 0.54 * gaussian(v, 0.54, 0.35))
    steel += (centre * fine_detail)[..., None] * [11, 12, 12]
    # Very fine aligned polishing marks, without speckles or mottling.
    polish_u = np.where(z > 552, (s + 1) * 0.5, u)
    polish = 0.47 * np.sin(391 * polish_u + 4 * v) + 0.29 * np.sin(680 * polish_u - 3 * v)
    steel += (polish * fine_detail)[..., None]

    size = (steel.shape[1], steel.shape[0])
    ink = Image.new("L", size)
    pen = ImageDraw.Draw(ink)

    def lettering(advance, across, group_spacing):
        x = 0.50 + (across - 14.5) / 24 * 0.243
        h = 298 - (advance + group_spacing) / 179 * 64
        return (x * (size[0] - 1), (626 - h) / (626 - 140) * (size[1] - 1))

    # Preserve the negative space between the terminal marks and the linked
    # middle strokes when the blade is downsampled in first person.
    for stroke, spacing in zip(RUNES, (0, 0, 8.5, 17, 17, 26, 44, 44)):
        points = path_points(stroke, partial(lettering, group_spacing=spacing))
        for i, (a, b) in enumerate(pairwise(points)):
            end = min(1, 0.38 + (i + 1) / 7, 0.38 + (len(points) - i) / 7)
            pen.line(
                (a, b),
                fill=255,
                width=max(1, round(scale * (2.04 + 0.70 * np.sin(0.095 * i) ** 2) * end)),
            )
    carved = mask_array(ink)
    steel = mix(steel, np.array([92, 111, 125]), carved * 0.95)
    steel += edge_light(carved, 0.8, scale)[..., None] * [20, 22, 22]
    return steel


def gold(u, v, light=0):
    across = u + 0.040 * np.sin(4.8 * v + 0.24)
    glint = (
        0.69 * gaussian(across, 0.23, 0.18)
        + 0.43 * gaussian(across, 0.82, 0.108)
        + 0.18 * (1 - v)
        + 0.09 * np.cos(6 * v - 1.9 * u)
        + light
    )
    return colour([188, 137, 54], [53, 58, 62], glint)


def guard(scale):
    u, v = coords(GUARD, scale)
    x = (u * 2 - 1) * GUARD_X
    z = GUARD_Z[1] - (GUARD_Z[1] - GUARD_Z[0]) * v
    a = np.abs(x)
    lower = np.interp(a, [0, 16, 32, 46, 59, 72.4], [120.8, 121.5, 124, 127.7, 132.5, 139])
    upper = np.interp(a, [0, 14, 27, 39, 50, 60.9], [143.7, 144.7, 147.4, 150.9, 155.2, 161.4])
    upper += 0.535 * np.maximum(0, a - 60.9)
    profile = (z - lower) / (upper - lower)

    # The reflection of a large strip light follows the shallow convex wing.
    # Its bright face, the darker opposing card, and a low warm bounce wrap
    # over the edge shoulder. A slower wave breaks the symmetry across the hub.
    stripe = profile + 0.080 * np.sin(4.8 * u + 0.46) + 0.045 * np.sin(9 * u - 0.76)
    bounce = gaussian(stripe, 0.11, 0.11)
    shadow_card = gaussian(stripe, 0.31, 0.145)
    diffuser = gaussian(stripe, 0.69, 0.215)
    light_edge = gaussian(stripe, 0.65, 0.066)
    longitudinal = 0.5 + 0.5 * np.cos(6.5 * u - 0.85)
    rgb = colour([194, 140, 57], [18, 19, 12], longitudinal)
    rgb = rgb + diffuser[..., None] * [33, 44, 53]
    rgb += light_edge[..., None] * [11, 15, 21]
    rgb += bounce[..., None] * [14, 13, 10] - shadow_card[..., None] * [33, 39, 27]

    # Put the rolled gold lip just inside the perimeter. Separate the opposing
    # shoulder with a fine darker return so it remains visible in diffuse light.
    line_low = gaussian(z - lower, 1.8, 0.64)
    line_high = gaussian(upper - z, 1.64, 0.67)
    lip_shadow = gaussian(upper - z, 2.96, 0.84)
    foot_shadow = gaussian(z - lower, 3.15, 1.08)
    rgb += line_high[..., None] * [36, 45, 49] + line_low[..., None] * [23, 25, 25]
    rgb -= lip_shadow[..., None] * [21, 23, 15] + foot_shadow[..., None] * [17, 20, 14]
    rgb += (0.45 * np.sin(325 * u + 2.2 * v))[..., None]
    seam = gaussian(x, 0, 0.47) * smooth(120.7, 123, z) * (1 - smooth(141.8, 143, z))
    rgb -= seam[..., None] * [25, 30, 20]
    # A single, unbroken navy chevron follows each horn of the crossguard.
    size = (rgb.shape[1], rgb.shape[0])
    ink = Image.new("L", size)
    pen = ImageDraw.Draw(ink)
    outline = [
        (8.25, 138.15),
        (9.75, 138.5),
        (30.05, 129.4),
        (70.55, 155.2),
        (71.55, 153.55),
        (30.1, 127.24),
        (8.10, 136.43),
    ]
    for mirror in (-1, 1):
        points = [
            (
                (0.5 + mirror * px / (2 * GUARD_X)) * (size[0] - 1),
                (GUARD_Z[1] - pz) / (GUARD_Z[1] - GUARD_Z[0]) * (size[1] - 1),
            )
            for px, pz in outline
        ]
        pen.polygon(points, fill=255)
    mask = mask_array(ink)
    navy = colour([11, 29, 66], [21, 38, 63], 0.32 + 0.45 * gaussian(u + 0.06 * v, 0.33, 0.39))
    rgb = mix(rgb, navy, mask)
    rgb += edge_light(mask, 0.7, scale)[..., None] * [24, 25, 15]
    return rgb


def crest(scale):
    u, v = coords(CREST, scale)
    x = (2 * u - 1) * CREST_X
    z = CREST_Z[1] - (CREST_Z[1] - CREST_Z[0]) * v
    rgb = gold(u, v, 0.10)
    size = (rgb.shape[1], rgb.shape[0])
    ink = Image.new("L", size)
    pen = ImageDraw.Draw(ink)

    def world(px, pz):
        return (
            (0.5 + px / (2 * CREST_X)) * (size[0] - 1),
            (CREST_Z[1] - pz) / (CREST_Z[1] - CREST_Z[0]) * (size[1] - 1),
        )

    def fill(path, sign=1, value=255):
        pen.polygon(path_points(path, lambda px, pz: world(px * sign, pz)), fill=value)

    # Four pieces of rich blue enamel are set into one sculpted gold throat.
    diamond = [
        (0, 145.8),
        (1.5, 149.4, 5.45, 153.1),
        (2.05, 154.7, 0, 160.0),
        (-1.1, 156.2, -5.4, 153.05),
        (-1.9, 150.9, 0, 145.8),
    ]
    fill(diamond)
    petals = [
        (12.3, 155.6),
        (12.7, 164.1, 12.2, 169.5),
        (12.0, 172.1, 9.05, 173.6),
        (6.1, 174.6, 2.8, 172.0),
        (2.2, 169.8, 3.1, 165.8),
        (4.5, 161.3, 8.4, 158.2),
        (9.85, 156.8, 12.3, 155.6),
    ]
    for sign in (-1, 1):
        fill(petals, sign)
    field = [
        (-11.95, 177.3),
        (-9.15, 173.9, -5.15, 174.0),
        (-1.4, 174.1, -1.65, 178.4),
        (-1.65, 185, -1.0, 189.5),
        (-2.45, 191.4),
        (-1.95, 192.7),
        (-4.18, 194.5),
        (0, 203.0),
        (4.18, 194.5),
        (1.95, 192.7),
        (2.45, 191.4),
        (1.0, 189.5),
        (1.65, 185, 1.65, 178.4),
        (1.4, 174.1, 5.15, 174.0),
        (9.15, 173.9, 11.95, 177.3),
        (11.95, 202.9),
        (8.5, 205.3, 6.9, 209.4),
        (4.1, 214.0, 1.1, 217.2),
        (0, 215.2),
        (-1.1, 217.2),
        (-4.1, 214, -6.9, 209.4),
        (-8.5, 205.3, -11.95, 202.9),
    ]
    fill(field)
    mask = mask_array(ink)
    azure = (
        0.37
        + 0.53 * gaussian(u + 0.075 * v, 0.29, 0.2)
        + 0.34 * gaussian(u - 0.05 * v, 0.77, 0.10)
        + 0.13 * np.cos(3.9 * v + 0.3)
    )
    enamel = colour([9, 32, 80], [26, 45, 66], azure)
    depth = soften(mask, scale * 1.7)
    enamel -= (1 - depth)[..., None] * [7, 9, 12]
    rgb = mix(rgb, enamel, mask)
    rgb += edge_light(mask, 1.1, scale)[..., None] * [34, 33, 23]
    # Cast highlights along the long outer lips stop cleanly at the ends of the prongs.
    rail = gaussian(np.abs(x), 20.1, 0.42) * smooth(146, 151, z) * (1 - smooth(178, 184, z))
    return rgb + rail[..., None] * [25, 23, 15]


def wrapped_grip(scale):
    u, v = coords(GRIP, scale)
    # The prop grip is waisted, smooth blue lacquer. Its soft vertical reflections
    # wrap seamlessly around the circumference and tighten at the ferrule.
    circum = (u + 0.014 * np.sin(5.7 * v - 0.1)) % 1

    def wrap_light(c, w):
        distance = ((circum - c + 0.5) % 1) - 0.5
        return np.exp(-((distance / w) ** 2))

    lacquer = (
        0.16
        + 0.45 * wrap_light(0.20, 0.135)
        + 0.37 * wrap_light(0.73, 0.14)
        + 0.51 * wrap_light(0.20, 0.029)
        + 0.31 * wrap_light(0.73, 0.042)
    )
    lacquer *= 0.83 + 0.15 * np.cos(4.9 * v - 1.4)
    dark_seat = (1 - smooth(0.0, 0.037, v)) + (smooth(0.963, 1, v))
    rgb = colour([11, 26, 59], [33, 57, 93], lacquer)
    return rgb - dark_seat[..., None] * [4, 7, 9]


def atlas(output):
    scale = 3
    image = Image.new("RGB", (WIDTH * scale, HEIGHT * scale), (176, 134, 61))

    def place(box, values):
        patch = Image.fromarray(np.uint8(np.clip(np.rint(values), 0, 255)))
        x0, y0, _, _ = box
        pad = 5 * scale
        # Duplicate border texels in all directions, including between the UV islands.
        expanded = np.pad(np.asarray(patch), ((pad, pad), (pad, pad), (0, 0)), mode="edge")
        image.paste(Image.fromarray(expanded), (x0 * scale - pad, y0 * scale - pad))

    for box, painter in [(BLADE, blade), (GUARD, guard), (CREST, crest), (GRIP, wrapped_grip)]:
        place(box, painter(scale))
    swatches = [
        (242, [232, 188, 99]),
        (278, [202, 150, 64]),
        (314, [146, 96, 38]),
        (350, [21, 48, 98]),
        (386, [226, 233, 232]),
        (422, [179, 194, 201]),
        (458, [123, 141, 153]),
        (494, [13, 30, 63]),
    ]
    for cx, col in swatches:
        u, v = coords((cx - 16, 861, cx + 16, 1015), scale)
        tone = 0.35 * np.cos(2 * np.pi * u) + 5.3 * np.sin(np.pi * v) - 1.1 * np.cos(4 * np.pi * v)
        values = np.array(col) + tone[..., None]
        box = (max(221, cx - 16), 861, cx + 16, 1015)
        place(box, values)
    output.parent.mkdir(parents=True, exist_ok=True)
    image.resize((WIDTH, HEIGHT), Image.Resampling.LANCZOS).save(output, optimize=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_FILE)
    atlas(parser.parse_args().output)
