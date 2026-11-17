"""Check the release mesh stream, all referenced textures and authored analysis paths."""
import gzip
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


if __name__ == "__main__":
    report = {name: check_mesh(name) for name in ("bab_ilu", "ea")}
    paths = json.loads((ASSETS / "models/fate/analysis.json").read_text(encoding="utf-8"))["paths"]
    for path in paths:
        assert path["radius"] > 0 and len(path["points"]) >= 2
        assert all(len(p) == 3 and all(math.isfinite(v) for v in p) for p in path["points"])
    report["analysis_paths"] = len(paths)
    print(json.dumps(report, indent=2))
