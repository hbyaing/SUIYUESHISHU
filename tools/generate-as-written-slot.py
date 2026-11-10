"""Keep the supplied book's silhouette and prominent structural motifs as transparent line art."""
from pathlib import Path
from math import cos, sin, pi
from PIL import Image, ImageDraw, ImageFilter

root = Path(__file__).resolve().parent.parent
source = Image.open(root/'docs/as-written-slots-1.20.34/book-source.png').convert('RGBA')
mask = source.getchannel('A').point(lambda a: 255 if a else 0)
padded = Image.new('L', (72,72)); padded.paste(mask, (4,4))
inside = padded.filter(ImageFilter.MinFilter(5)).crop((4,4,68,68))
icon = Image.new('RGBA', (64,64))
ink = (85,85,85,255)
for y in range(64):
    for x in range(64):
        if mask.getpixel((x,y)) and not inside.getpixel((x,y)):
            icon.putpixel((x,y),ink)
d = ImageDraw.Draw(icon)


def line(points, width=2):
    d.line(points, fill=ink, width=width, joint='curve')


def bezier(start, control1, control2, end, width=2):
    points=[]
    for i in range(65):
        t=i/64; s=1-t
        points.append((s**3*start[0]+3*s*s*t*control1[0]+3*s*t*t*control2[0]+t**3*end[0],
                       s**3*start[1]+3*s*s*t*control1[1]+3*s*t*t*control2[1]+t**3*end[1]))
    line(points,width)


def ellipse(cx,cy,rx,ry,rotation=0,width=2):
    angle=rotation*pi/180
    points=[]
    for i in range(129):
        t=i*pi/64
        x=rx*cos(t);y=ry*sin(t)
        points.append((cx+x*cos(angle)-y*sin(angle),cy+x*sin(angle)+y*cos(angle)))
    line(points,width)


# The bright frame on the actual cover, not the irregular red sketch.
line([(11,12),(43,5),(55,38),(23,45),(11,12)])
# Spine fold and the two prominent diagonal binding divisions.
bezier((7,11),(12,25),(17,36),(23,48))
bezier((4,25),(5,22),(8,20),(11,18),1)
bezier((12,46),(13,42),(16,39),(19,38),1)
# Page edge and the curved lower binding. The bookmark's exact outer shape is in the mask.
line([(23,48),(57,40)],2)
bezier((23,49),(19,50),(16,52),(16,56),1)
# Concentric medallion and its small curved accents, aligned to the original texture.
ellipse(34.5,25,12,14,-12,2)
ellipse(35,25,8.8,10.5,-12,2)
ellipse(21.5,17.5,2.8,2,-25,1)
bezier((27,37),(24,42),(29,42),(32,39),1)
# The source's central star is retained as a clean eight-ray motif, without the glow.
star=[]
for i in range(16):
    a=-pi/2+i*pi/8
    r=(4.5 if i%4==0 else 3.5) if i%2==0 else 1.35
    star.append((35.5+r*cos(a),24.5+r*sin(a)))
line(star+[star[0]],1)
# Keep all structural strokes within the actual book, hiding the rest of the texture.
alpha=icon.getchannel('A')
for y in range(64):
    for x in range(64):
        if not mask.getpixel((x,y)):icon.putpixel((x,y),(0,0,0,0))
target=root/'src/main/resources/assets/starcrowndoll/textures/slot/empty_as_written_slot.png'
icon.save(target)
print('Source-book structural line art generated:',icon.size)
