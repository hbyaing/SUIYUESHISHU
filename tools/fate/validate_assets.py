"""Check the release mesh stream, all referenced textures and authored analysis paths."""
import gzip
import hashlib
import json
import math
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/starcrowndoll"


def check_mesh(name):
    data = memoryview(gzip.decompress((ASSETS / f"models/fate/{name}.mesh.gz").read_bytes()))
    offset = 0

    def unpack(fmt):
        nonlocal offset
        result = struct.unpack_from(fmt, data, offset)
        offset += struct.calcsize(fmt)
        return result

    def utf():
        nonlocal offset
        size, = unpack(">H")
        value = bytes(data[offset:offset + size]).decode("utf-8")
        offset += size
        return value

    magic, count = unpack(">II")
    assert magic == 0x46415431 and 1 <= count <= 32
    total = 0
    for _ in range(count):
        texture, glow = utf(), utf()
        for path in (texture, glow):
            if path:
                assert (ASSETS / path).is_file(), path
        vertices, = unpack(">I")
        assert 0 < vertices <= 600_000 and vertices % 3 == 0
        for _ in range(vertices):
            x, y, z, nx, ny, nz, u, v = unpack(">8f")
            assert all(math.isfinite(a) for a in (x, y, z, nx, ny, nz, u, v))
            assert abs(nx * nx + ny * ny + nz * nz - 1) < .01
            assert -.001 <= u <= 1.001 and -.001 <= v <= 1.001
        total += vertices // 3
    assert offset == len(data), "Unexpected trailing mesh bytes"
    return {"parts": count, "triangles": total}


def check_excalibur_display():
    models = ASSETS / "models/item"

    def read(name):
        return json.loads((models / f"{name}.json").read_text(encoding="utf-8"))

    sword = read("excalibur_mesh")["display"]
    concealed = read("excalibur_concealed")["perspectives"]
    pressure = read("excalibur_wind_pressure")
    assert pressure["parent"] == "starcrowndoll:item/excalibur_mesh"
    assert not pressure.get("display"), "Wind must inherit the visible sword's hand transforms"
    sheath_models = [read(name)["display"] for name in ("avalon", "avalon_sheathed")]
    for view in ("firstperson", "thirdperson"):
        assert sword[f"{view}_righthand"]["scale"] == sword[f"{view}_lefthand"]["scale"], view
        for hand in ("righthand", "lefthand"):
            context = f"{view}_{hand}"
            assert concealed[context]["parent"] == "starcrowndoll:item/excalibur_wind_pressure", context
            scale = sword[context]["scale"]
            assert all(math.isclose(axis, scale[0]) for axis in scale), (context, scale)
            assert all(display[context]["scale"] == scale for display in sheath_models), context
            if view == "firstperson":
                assert scale[0] > .73, "First-person blade reverted to the shorter two-hand model"
                for model_display in (sword, *sheath_models):
                    pitch, _, roll = model_display[context]["rotation"]
                    assert abs(pitch) < .001 and abs(roll) < .001, f"{context}: blade or scabbard tilts from vertical"
            else:
                assert math.isclose(scale[0], 1.28), "First-person tuning changed the calibrated third-person sword"
    return {view: sword[f"{view}_righthand"]["scale"][0] for view in ("firstperson", "thirdperson")}


def check_excalibur_chant():
    audio = (ASSETS / "sounds/excalibur/chant.ogg").read_bytes()
    expected = "eca52db1736fe5e343c47074061ff0fe410236b9b1bac8ba525435570f92dcf4"
    assert hashlib.sha256(audio).hexdigest() == expected, "Public charge sound differs from the 1.20.56 mix"
    assert audio.startswith(b"OggS"), "Missing Ogg header"
    header = 27 + audio[26]
    assert audio[header:header + 7] == b"\x01vorbis", "Missing Vorbis identification header"
    channels, rate = struct.unpack_from("<BI", audio, header + 11)
    assert (channels, rate) == (1, 48000), "Expected mono 48 kHz Vorbis"
    last_page = audio.rfind(b"OggS")
    assert audio[last_page+5] & 4, "Missing end-of-stream page"
    samples, = struct.unpack_from("<Q", audio, last_page+6)
    assert samples == 583369, "Public charge sound is out of sync with the beam"
    return {"sample_rate": rate, "samples": samples, "sha256": expected}


if __name__ == "__main__":
    report = {name: check_mesh(name) for name in ("bab_ilu", "ea")}
    paths = json.loads((ASSETS / "models/fate/analysis.json").read_text(encoding="utf-8"))["paths"]
    for path in paths:
        assert path["radius"] > 0 and len(path["points"]) >= 2
        assert all(len(p) == 3 and all(math.isfinite(v) for v in p) for p in path["points"])
    report["analysis_paths"] = len(paths)
    report["excalibur_hand_scales"] = check_excalibur_display()
    report["excalibur_chant"] = check_excalibur_chant()
    print(json.dumps(report, indent=2))
