"""Rebuild the personal-use chant from the two decoded Saber Japanese archive WAVs."""

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import wave
import zipfile


PACK_AUDIO = "assets/starcrowndoll/sounds/excalibur/chant.ogg"
ASSET = Path(__file__).resolve().parents[2] / "src/main/resources" / PACK_AUDIO
TAKES = {
    "432944184": (282853, "6fcf81abdbdfa61523a976ffba42b352f63f071e08fb6757d672fd10dba255be"),
    "877602325": (216649, "125bd7daa31907dee44996fe5b05151e648bff1b5c1587f505b7360888b361ca"),
}
# At 48 kHz the first phrase ends at 5.770 s and the call starts 0.130 s
# into the second take (10 ms RMS windows at -45 dBFS). Start its file at
# 7.640 s so the two audible endpoints are exactly two seconds apart.
RELEASE_START_FRAME = 366720


def write_pack(path, audio):
    metadata = {
        "pack": {
            "pack_format": 15,
            "description": "Excalibur · personal Japanese voice pack · Saber — Ayako Kawasumi",
        }
    }
    captions = {
        "zh_cn": ("Saber 吟唱并呼唤圣剑真名", "真名吟唱中 · 保持持剑",
                  "按住使用 0.5 秒开始吟唱，唱毕自动解放真名。"),
        "en_us": ("Saber chants and calls Excalibur", "Invoking the true name · Keep the sword in hand",
                  "Hold use for 0.5s to chant. Excalibur fires on the final word."),
        "ja_jp": ("セイバーが詠唱し、剣の真名を告げる", "真名解放の詠唱中 · 剣を持ったまま",
                  "使用ボタンを0.5秒押し続けると詠唱開始。最後の一声で真名を解放する。"),
    }
    files = {PACK_AUDIO: audio.read_bytes(), "pack.mcmeta": json.dumps(metadata, ensure_ascii=False)}
    for locale, (subtitle, message, tooltip) in captions.items():
        strings = {
            "subtitles.starcrowndoll.excalibur_chant": subtitle,
            "message.starcrowndoll.excalibur.chant": message,
            "tooltip.starcrowndoll.excalibur.light": tooltip,
        }
        files[f"assets/starcrowndoll/lang/{locale}.json"] = json.dumps(strings, ensure_ascii=False)
    with zipfile.ZipFile(path, "w") as pack:
        for name, contents in files.items():
            # Standardize metadata when building the same pack on different operating systems.
            entry = zipfile.ZipInfo(name)
            entry.create_system = 0
            entry.external_attr = 0x20
            pack.writestr(entry, contents)


def read_take(path, wem_id):
    frame_count, pcm_hash = TAKES[wem_id]
    with wave.open(str(path), "rb") as wav:
        format_ = (wav.getnchannels(), wav.getsampwidth(), wav.getframerate(), wav.getcomptype())
        if format_ != (1, 2, 48000, "NONE") or wav.getnframes() != frame_count:
            raise ValueError(f"{path}: expected {frame_count} mono 48 kHz PCM16 frames for WEM {wem_id}")
        samples = wav.readframes(frame_count)
    if hashlib.sha256(samples).hexdigest() != pcm_hash:
        raise ValueError(f"{path}: PCM differs from the Japanese archive recording for WEM {wem_id}")
    return samples


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("select", type=Path, help="Decoded Japanese Ultimate selection, WEM 432944184")
    parser.add_argument("release", type=Path, help="Decoded Japanese standard-speed release, WEM 877602325")
    parser.add_argument("--ffmpeg", default="ffmpeg")
    output = parser.add_mutually_exclusive_group()
    output.add_argument("--output", type=Path, help="Write the mixed Ogg (default: the source-tree asset)")
    output.add_argument("--resource-pack", type=Path, metavar="ZIP",
                        help="Build an optional Minecraft Java 1.20.1 resource pack for personal use")
    args = parser.parse_args()
    target = args.resource_pack or args.output or ASSET
    if args.resource_pack and target.suffix.lower() != ".zip":
        parser.error("--resource-pack requires a .zip output path")
    if target.resolve() in (args.select.resolve(), args.release.resolve()):
        parser.error("Choose an output path different from the input WAVs")

    selection = read_take(args.select, "432944184")
    release = read_take(args.release, "877602325")
    pause_frames = RELEASE_START_FRAME - TAKES["432944184"][0]
    pcm = selection + bytes(pause_frames * 2) + release
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=target.parent) as directory:
        encoded = Path(directory) / "chant.ogg"
        subprocess.run([args.ffmpeg, "-v", "error", "-y", "-f", "s16le", "-ar", "48000",
                        "-ac", "1", "-i", "pipe:0", "-map_metadata", "-1", "-c:a",
                        "libvorbis", "-q:a", "6", "-fflags", "+bitexact", str(encoded)],
                       input=pcm, check=True)
        if args.resource_pack:
            archive = Path(directory) / "pack.zip"
            write_pack(archive, encoded)
            archive.replace(target)
        else:
            encoded.replace(target)
    print(f"{target}  SHA-256 {hashlib.sha256(target.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
