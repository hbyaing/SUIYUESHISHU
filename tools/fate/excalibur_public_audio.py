"""Build the public Excalibur charge cue from the mod's CC0 Kenney sound effects."""

import argparse
from array import array
import hashlib
import math
from pathlib import Path
import subprocess
import sys


SOUNDS = (Path(__file__).resolve().parents[2] / "src/main/resources/assets/starcrowndoll"
          / "sounds/excalibur")
RATE = 48000
FRAMES = 583369


def stretch(ffmpeg, name, start, end, seconds, pitch, low, high):
    """Keep the sample's pitch and length independently adjustable for a sustained swell."""
    tempo = (end - start) / (pitch * seconds)
    filters = [f"atrim=start={start}:end={end}", "asetpts=PTS-STARTPTS",
               f"aresample={RATE}", f"asetrate={round(RATE * pitch)}", f"aresample={RATE}",
               f"highpass=f={low}", f"lowpass=f={high}"]
    while tempo < .5:
        filters.append("atempo=0.5")
        tempo *= 2
    filters.append(f"atempo={tempo:.10f}")
    raw = subprocess.check_output([ffmpeg, "-v", "error", "-nostdin", "-i", str(SOUNDS / (name + ".ogg")),
                                   "-af", ",".join(filters), "-ac", "1", "-f", "f32le", "pipe:1"])
    samples = array("f")
    samples.frombytes(raw)
    if sys.byteorder != "little":
        samples.byteswap()
    return samples


def add_layer(mix, start, end, samples, envelope, level):
    """Align the stretched audio with the animation clock and join gain points smoothly."""
    first = round(start * RATE)
    length = min(round(end * RATE), len(mix)) - first
    rms = math.sqrt(sum(x * x for x in samples) / len(samples))
    gain = level / max(rms, 1e-6)
    step = (len(samples) - 1) / (length - 1)
    for (begin, g0), (finish, g1) in zip(envelope, envelope[1:]):
        a, b = round(begin * length), round(finish * length)
        for i in range(a, b):
            pos = i * step
            j = int(pos)
            x0, x1 = samples[j], samples[min(j + 1, len(samples) - 1)]
            blend = .5 - .5 * math.cos(math.pi * (i - a) / max(1, b - a - 1))
            mix[first + i] += (x0 + (x1 - x0) * (pos - j)) * (g0 + (g1 - g0) * blend) * gain


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ffmpeg", default="ffmpeg")
    parser.add_argument("--output", type=Path, default=SOUNDS / "chant.ogg")
    args = parser.parse_args()
    mix = array("f", [0]) * FRAMES

    # The lower charge rises first. A second register opens as the blade lifts.
    first = stretch(args.ffmpeg, "charge", .09, .69, 5.77, .83, 65, 2400)
    add_layer(mix, 0, 5.77, first, [(0, 0), (.13, .36), (.50, .53), (.88, .83),
                                    (.95, 1), (1, 0)], .125)
    upper = stretch(args.ffmpeg, "sanctuary", .095, .42, 2.67, 1.10, 610, 5600)
    add_layer(mix, 3.10, 5.77, upper, [(0, 0), (.48, .36), (.82, .91),
                                        (.92, 1), (1, 0)], .065)

    # The pause from 5.77 to 7.77 seconds contains no samples from any layer.
    rising = stretch(args.ffmpeg, "charge", .085, .66, 3.83, 1.14, 90, 5100)
    add_layer(mix, 7.77, 11.60, rising, [(0, 0), (.05, .24), (.45, .47), (.86, .90),
                                          (.985, 1), (1, 0)], .17)
    shimmer = stretch(args.ffmpeg, "sanctuary", .07, .34, 1.90, 1.32, 850, 6100)
    add_layer(mix, 9.70, 11.60, shimmer, [(0, 0), (.57, .4), (.98, 1), (1, 0)], .065)

    # The sword's metal accent starts at the same tick as the separately played beam.
    strike = stretch(args.ffmpeg, "reveal", .045, .52, FRAMES / RATE - 11.60, 1.05, 250, 7500)
    add_layer(mix, 11.60, FRAMES / RATE, strike, [(0, 0), (.016, 1), (.48, .66), (1, 0)], .14)

    peak = max(abs(x) for x in mix)
    pcm = array("h", (round(x * min(1, .85 / max(peak, 1e-6)) * 32767) for x in mix))
    if sys.byteorder != "little":
        pcm.byteswap()
    subprocess.run([args.ffmpeg, "-v", "error", "-y", "-nostdin", "-f", "s16le",
                    "-ar", str(RATE), "-ac", "1", "-i", "pipe:0", "-map_metadata", "-1",
                    "-c:a", "libvorbis", "-q:a", "6", "-fflags", "+bitexact", str(args.output)],
                   input=pcm.tobytes(), check=True)
    print(f"{args.output}  SHA-256 {hashlib.sha256(args.output.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
