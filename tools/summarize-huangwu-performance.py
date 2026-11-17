"""Summarize QA-only GPU queries and paired in-game captures; does not estimate whole-game FPS."""
import argparse, json, re
from pathlib import Path
from PIL import Image, ImageDraw
import numpy as np

p=argparse.ArgumentParser()
p.add_argument('client',type=Path)
p.add_argument('output',type=Path)
a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
text=(a.client/'performance-results.txt').read_text(encoding='utf8')
assert 'COMPLETE paired GPU timing' in text and 'FAIL' not in text
results=[];rows=[]
for line in text.splitlines():
    m=re.match(r'RESULT (\S+) native=([\d.]+) ms half=([\d.]+) ms reduction=([\d.]+)%',line)
    if not m:continue
    name,native,half,reduction=m.groups()
    left=Image.open(a.client/'screenshots'/f'{name}-native.png').convert('RGB')
    right=Image.open(a.client/'screenshots'/f'{name}-half.png').convert('RGB')
    assert left.size==right.size==(1280,800)
    difference=np.abs(np.asarray(left,dtype=np.float32)-np.asarray(right,dtype=np.float32))
    results.append(dict(scene=name,native_ms=float(native),half_ms=float(half),reduction_percent=float(reduction),mean_rgb_error_255=float(difference.mean()),p99_max_channel_error_255=float(np.quantile(difference.max(2),.99))))
    row=Image.new('RGB',(1280,424),'#181818');draw=ImageDraw.Draw(row)
    draw.text((10,5),f'{name} | native {native} ms | half {half} ms | GPU pass reduction {reduction}%',fill='white')
    row.paste(left.resize((640,400)),(0,24));row.paste(right.resize((640,400)),(640,24));rows.append(row)
assert len(results)==6
sheet=Image.new('RGB',(1280,424*len(rows)))
for i,row in enumerate(rows):sheet.paste(row,(0,i*424))
sheet.save(a.output/'paired-contact-sheet.jpg',quality=93)
(a.output/'gpu-summary.json').write_text(json.dumps(dict(scope='Actual production effect draws, native scene/core depth copy, meshes and reconstruction; RTX 4060 Laptop GPU; 1280x800; 12 warmup pairs, 24 measured pairs per scene; no external shaders.',results=results),indent=2)+'\n',encoding='utf8')
print(json.dumps(results,indent=2))
