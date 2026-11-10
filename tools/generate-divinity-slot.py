"""Rasterize the existing seated doll white mold, without skin or ornaments."""
from pathlib import Path
import json
import math
from PIL import Image, ImageDraw

assets = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/starcrowndoll'
elements = []
for name, count in [('head', 1), ('body', 3), ('right_arm', 1), ('left_arm', 1)]:
    elements += json.loads((assets / f'models/block/doll_part_{name}.json').read_text(encoding='utf-8'))['elements'][:count]

def transform(point, rotation):
    if rotation:
        origin = rotation['origin']; point = [v-o for v,o in zip(point, origin)]
        axis = 'xyz'.index(rotation['axis']); a,b = [i for i in range(3) if i != axis]
        angle = math.radians(rotation['angle']) * (-1 if axis == 1 else 1)
        point[a],point[b] = point[a]*math.cos(angle)-point[b]*math.sin(angle),point[a]*math.sin(angle)+point[b]*math.cos(angle)
        point = [v+o for v,o in zip(point, origin)]
    x,y,z = point; yaw = math.radians(-25); pitch = math.radians(17)
    x,z = x*math.cos(yaw)-z*math.sin(yaw), x*math.sin(yaw)+z*math.cos(yaw)
    return x, -y*math.cos(pitch)+z*math.sin(pitch), y*math.sin(pitch)+z*math.cos(pitch)

faces = []
indices = [(0,1,3,2),(4,6,7,5),(0,4,5,1),(2,3,7,6),(0,2,6,4),(1,5,7,3)]
for e in elements:
    lo,hi = e['from'],e['to']
    vertices = [transform([hi[0] if i&1 else lo[0],hi[1] if i&2 else lo[1],hi[2] if i&4 else lo[2]], e.get('rotation')) for i in range(8)]
    for f,ids in enumerate(indices):
        points = [vertices[i] for i in ids]
        faces.append((sum(p[2] for p in points)/4, points, [190,215,200,245,205,230][f]))
all_points = [p for _,ps,_ in faces for p in ps]
minx,maxx = min(p[0] for p in all_points),max(p[0] for p in all_points)
miny,maxy = min(p[1] for p in all_points),max(p[1] for p in all_points)
scale = min(54/(maxx-minx),56/(maxy-miny))
image = Image.new('RGBA',(64,64)); draw = ImageDraw.Draw(image)
for _,ps,tone in sorted(faces, reverse=True, key=lambda f:f[0]):
    xy = [((p[0]-(minx+maxx)/2)*scale+32,(p[1]-(miny+maxy)/2)*scale+32) for p in ps]
    draw.polygon(xy,fill=(tone,tone,tone,255),outline=(90,90,90,255))
# Visible edges are already occluded by the frontmost faces. Keep only those
# structural strokes; every face interior and the outside stay transparent.
image.putdata([(80,80,80,255) if a and r==90 else (0,0,0,0) for r,g,b,a in image.getdata()])
target=assets/'textures/slot/empty_divinity_slot.png'; target.parent.mkdir(exist_ok=True); image.save(target)
print(target)
