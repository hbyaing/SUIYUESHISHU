import base64
import io
import json
import subprocess
import sys
from pathlib import Path

from PIL import Image

root = Path(__file__).resolve().parents[2]
source = Path(sys.argv[1])
assets = root / "src/main/resources/assets/starcrowndoll"
model = json.loads(source.read_text(encoding="utf-8-sig"))
textures = model["textures"]
atlas = Image.new("RGBA", (len(textures) * 256, 256))
for index, texture in enumerate(textures):
    assert [texture["uv_width"], texture["uv_height"]] == [256, 256]
    png = base64.b64decode(texture["source"].split(",", 1)[1], validate=True)
    image = Image.open(io.BytesIO(png)).convert("RGBA").resize((256, 256), Image.Resampling.NEAREST)
    atlas.paste(image, (index * 256, 0))
atlas.save(assets / "textures/entity/enkidu.png")
subprocess.run(["node", str(root / "tools/minggulong-runtime/convert-geckolib.cjs"),
                str(source), str(assets / "geo/enkidu.geo.json"),
                str(assets / "animations/enkidu.animation.json"), "enkidu"], check=True)
