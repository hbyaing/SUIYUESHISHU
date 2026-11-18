"""Bake the Invisible Air mesh and its sprite from Kenney Particle Pack's PNG directory."""

import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image


ASSETS = Path(__file__).resolve().parents[2] / "src/main/resources/assets/starcrowndoll"
NAME = "excalibur_wind_pressure"
SIZE = 128
FRAMES = 36
WAKE_ROWS = 16


def sample(image, x, y):
    """Sample the intensity of a bitmap with transparent black outside the image."""
    h, w = image.shape
    x = np.clip(x, 0, 1) * (w - 1)
    y = np.clip(y, 0, 1) * (h - 1)
    ix, iy = x.astype(int), y.astype(int)
    jx, jy = np.minimum(ix + 1, w - 1), np.minimum(iy + 1, h - 1)
    fx, fy = x - ix, y - iy
    return ((image[iy, ix] * (1 - fx) + image[iy, jx] * fx) * (1 - fy)
            + (image[jy, ix] * (1 - fx) + image[jy, jx] * fx) * fy)


def bake_sprite(particle_dir):
    images = {}
    for name in ("slash_03", "trace_02", "trace_06", "flame_03"):
        bitmap = np.asarray(Image.open(particle_dir / (name + ".png")).convert("RGBA"), dtype=float) / 255
        images[name] = np.max(bitmap[:, :, :3], axis=2) * bitmap[:, :, 3]
    strip = Image.new("RGBA", (SIZE, SIZE * FRAMES))
    v, u = (np.indices((SIZE, SIZE)) + 0.5) / SIZE
    # Leave a gutter between the repeating blade pattern and the two swing streaks.
    height = np.clip((1 - v - WAKE_ROWS / SIZE) / (1 - WAKE_ROWS / SIZE), 0, 1)
    for i in range(FRAMES):
        phase = i / FRAMES
        alpha = np.zeros((SIZE, SIZE))

        def wisp(name, offset, turns, stretch, width, opacity, travel):
            # Wrap both longitudinal travel and rotation to the first frame.
            along = (height * stretch - phase * travel + offset) % 1
            line = offset + turns * height - phase + 0.075 * np.sin(math.tau * (along + offset))
            cross = ((u - line + 0.5) % 1 - 0.5) / width + 0.5
            strength = sample(images[name], cross, along) * opacity
            feather = np.sin(math.pi * along) ** 0.4
            return np.clip(strength * feather, 0, 1)

        # Long, asymmetric raised edges peel away from a mostly open center.
        # Each core splits into one or two fine currents as it travels up the sword.
        streams = [
            ("slash_03", .07, .83, 1.16, .59, 1.00, 1),
            ("trace_06", .13, .77, 1.29, .48, .77, 1),
            ("trace_02", .12, .91, 1.67, .83, .99, 1),
            ("slash_03", .59, .66, .97, .54, .88, 1),
            ("trace_06", .66, .64, 1.11, .56, .66, 1),
            ("trace_02", .53, .80, 1.43, .68, .96, 1),
            ("flame_03", .17, .89, 1.47, .65, .28, 1),
            ("flame_03", .73, .72, 1.65, .51, .22, 2),
        ]
        for stream in streams:
            alpha = 1 - (1 - alpha) * (1 - wisp(*stream))
        ends = np.minimum(1, np.minimum(height / 0.09, (1 - height) / 0.055))
        alpha = np.clip(alpha * ends * 1.18, 0, .89)
        # The narrow integral strip only appears while swinging. Reuse one iris of the trace brush.
        across = np.clip((v - (1 - WAKE_ROWS / SIZE)) * SIZE / WAKE_ROWS, 0, 1)
        edge = sample(images["trace_06"], .5 + (across - .5) * .58, .19 + .62 * u)
        pulse = .75 + .25 * np.cos(math.tau * (u - phase))
        wake = .58 * edge * np.sin(math.pi * u) ** .63 * np.sin(math.pi * across) ** 2 * pulse
        alpha = np.where(v > 1 - WAKE_ROWS / SIZE, wake, alpha)
        pixels = np.empty((SIZE, SIZE, 4), dtype="uint8")
        pixels[:, :, 0] = 191 + 64 * alpha
        pixels[:, :, 1] = 197 + 58 * alpha
        pixels[:, :, 2] = 201 + 54 * alpha
        pixels[:, :, 3] = np.rint(255 * alpha)
        strip.paste(Image.fromarray(pixels), (0, i * SIZE))
    texture = ASSETS / "textures/item" / (NAME + ".png")
    strip.save(texture, optimize=True)
    texture.with_suffix(".png.mcmeta").write_text(json.dumps({
        "animation": {"frametime": 1, "interpolate": False}
    }, indent=2) + "\n", encoding="utf-8")


def bake_mesh():
    model_dir = ASSETS / "models/item"
    obj = ["# Wind shells follow the same item-space axis as excalibur.obj.", "mtllib " + NAME + ".mtl"]
    vertex_base = 1
    # y/radius in item units. The unwrapped silver blade runs from y=.667 to 1.710.
    rings = [(.59, .020), (.65, .060), (.76, .079), (.91, .090),
             (1.10, .073), (1.30, .085), (1.51, .066), (1.64, .043),
             (1.71, .025), (1.745, .004)]
    for name, sides, scale, twist in (("outer_wake", 20, 1.0, 0.0), ("inner_current", 12, .57, 2.31)):
        obj.extend(["o " + name, "usemtl pressure"])
        for y, radius in rings:
            t = (y - rings[0][0]) / (rings[-1][0] - rings[0][0])
            for j in range(sides + 1):
                a = math.tau * j / sides + twist
                bend = .012 * math.sin(math.tau * t) * math.sin(math.pi * t)
                x = 0.5 + scale * (radius * math.cos(a) + bend)
                z = 0.5 + scale * radius * .80 * math.sin(a)
                obj.append(f"v {x:.6f} {y:.6f} {z:.6f}")
                v = (WAKE_ROWS + 1 + t * (SIZE - WAKE_ROWS - 2)) / SIZE
                obj.append(f"vt {j / sides:.6f} {v:.6f}")
        for ring in range(len(rings) - 1):
            for side in range(sides):
                a = vertex_base + ring * (sides + 1) + side
                face = [a, a + sides + 1, a + sides + 2, a + 1]
                obj.append("f " + " ".join(f"{v}/{v}" for v in face))
        vertex_base += len(rings) * (sides + 1)
    # Two narrow curved wakes touch the blade at one end and taper into its swing plane.
    # Three-sided cross sections stay visible from either hand and from an observer's back.
    obj.extend(["o swing_wakes", "usemtl pressure"])
    for height, sweep, angle in ((1.59, .42, -.44), (1.32, .29, -.21)):
        for i in range(7):
            t = i / 6
            turn = .78 * t
            x = .52 + sweep * math.sin(turn) / math.sin(.78)
            y = height - sweep * (1 - math.cos(turn)) / math.sin(.78)
            z = .51 + sweep * t * angle
            radius = .001 + .019 * math.sin(math.pi * t) ** .62
            for k in range(4):
                a = math.tau * k / 3
                obj.append(f"v {x:.6f} {y + radius * math.cos(a):.6f} {z + radius * .57 * math.sin(a):.6f}")
                obj.append(f"vt {(.5 + t * (SIZE - 1)) / SIZE:.6f} {(2 + 12 * k / 3) / SIZE:.6f}")
        for i in range(6):
            for k in range(3):
                a = vertex_base + 4 * i + k
                face = (a, a + 1, a + 5, a + 4)
                obj.append("f " + " ".join(f"{v}/{v}" for v in face))
        vertex_base += 7 * 4
    (model_dir / (NAME + ".obj")).write_text("\n".join(obj) + "\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("particle_png_dir", type=Path)
    args = parser.parse_args()
    bake_sprite(args.particle_png_dir)
    bake_mesh()
