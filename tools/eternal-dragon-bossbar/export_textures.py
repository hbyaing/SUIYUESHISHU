"""Rebuild the approved HUD as independent frame, health and ornament layers.

Requires Python 3 and Pillow. Run from any working directory.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageOps

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/starcrowndoll/textures/gui/eternal_dragon'
OUT.mkdir(parents=True, exist_ok=True)
SIZE = (960, 240)
frame = Image.new('RGBA', SIZE)
draw = ImageDraw.Draw(frame)
def rect(color, x, y, w, h):
    draw.rectangle((x, y, x+w-1, y+h-1), fill=color)
rect('#100c12',72,112,816,40)
rect('#100c12',80,104,800,56)
rect('#581d2b',80,112,800,40)
rect('#913341',88,112,784,4)
rect('#24131e',88,120,784,28)
rect('#490f21',92,124,776,20)
rect('#6d2a38',88,152,784,4)
for j in range(19): rect('#331722',104+j*40,108,20,4)
rect('#b54b50',80,120,4,24)
rect('#b54b50',876,120,4,24)
frame.save(OUT/'frame.png')

fill = Image.new('RGBA', SIZE)
draw = ImageDraw.Draw(fill)
rect('#971c36',92,124,776,20)
rect('#d13348',92,124,776,4)
rect('#bd263e',92,128,776,8)
rect('#6e162d',92,140,776,4)
fill.save(OUT/'fill.png')

wing = Image.new('RGBA',(26,34))
d = ImageDraw.Draw(wing)
d.polygon([(24,30),(22,21),(22,12),(18,7),(1,1),(6,10),(4,12),
           (9,15),(7,18),(13,21),(12,25),(18,26),(19,31),(24,33)], fill='#15111b')
d.polygon([(22,28),(20,14),(17,10),(5,4),(10,12),(7,12),
           (13,17),(10,18),(17,23),(15,24),(20,26)], fill='#713021')
d.polygon([(7,6),(17,11),(20,16),(21,25),(15,16)], fill='#a4472e')
d.polygon([(10,14),(17,17),(21,25),(16,22)], fill='#8c3828')
d.polygon([(17,23),(21,26),(22,30),(20,29)], fill='#9c422c')
d.line([(24,31),(21,13),(17,9),(3,2)], fill='#302331',width=2)
d.line([(21,26),(16,15),(6,10)], fill='#241b29',width=1)
d.line([(22,28),(17,21),(8,17)], fill='#241b29',width=1)
d.line([(22,30),(19,26),(13,25)], fill='#241b29',width=1)
d.line([(20,12),(17,8),(6,3)], fill='#55404a',width=1)
d.line([(23,29),(21,17)], fill='#4c2b32',width=1)
wing.save(Path(__file__).parent/'assets/left_wing.png')
wing = wing.resize((78,102),Image.Resampling.NEAREST)
decoration = Image.new('RGBA', SIZE)
decoration.alpha_composite(wing,(8,50))
decoration.alpha_composite(ImageOps.mirror(wing),(874,50))
head = Image.open(Path(__file__).parent/'assets/dragon_head_64.png').convert('RGBA')
decoration.alpha_composite(head.resize((128,128),Image.Resampling.NEAREST),(416,70))
decoration.save(OUT/'decoration.png')
for name in ('frame','fill','decoration'):
    (OUT/(name+'.png.mcmeta')).write_text('{"texture":{"blur":false,"clamp":true}}\n',encoding='utf-8')
print('Exported independent frame, full-width fill, and unchanged head/wing layers')
