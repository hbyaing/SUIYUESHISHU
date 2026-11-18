"""Paint the shared, opaque Avalon atlas. Requires Python, NumPy, and Pillow.

The broad crossings, enamel fillets, lettering and terminal are drawn here.
The front and reverse share U=.03..78. The four strips at the right serve
the rim, sides, recessed gold and the blue-black lining of the opening.
"""

import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter


WIDTH, HEIGHT = 256, 2048
FACE_LEFT, FACE_RIGHT = 8, 199
THROAT, TIP = 8, 2039
DEFAULT_FILE = (Path(__file__).resolve().parents[2] /
                "src/main/resources/assets/starcrowndoll/textures/item/avalon_enamel.png")


def smooth(a, b, value):
    t = np.clip((value - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def mix(first, second, mask):
    return first + (second - first) * mask[..., None]


def curve(values, knots, positions):
    """Monotone cubic interpolation along the length of the scabbard."""
    knots = np.asarray(knots)
    values = np.asarray(values)
    slope = np.gradient(values, knots)
    i = np.clip(np.searchsorted(knots, positions, side="right") - 1, 0, len(knots) - 2)
    interval = knots[i + 1] - knots[i]
    t = np.clip((positions - knots[i]) / interval, 0, 1)
    return ((2*t**3 - 3*t**2 + 1) * values[i] + (t**3 - 2*t**2 + t) * interval * slope[i]
            + (-2*t**3 + 3*t**2) * values[i+1] + (t**3 - t**2) * interval * slope[i+1])


def surface(x, y, grain, enamel=False):
    # Broad reflections describe the slightly cambered face even under flat item lighting.
    across = x + .028*np.sin(7*y + .3) + .011*np.sin(17*y)
    light = (.78*np.exp(-((across - .31)/.225)**2)
             + .46*np.exp(-((across - .88)/.095)**2)
             + .18*np.sin(3.5*y + .1) + .06*np.cos(18*y - 3*across))
    lip = 5*np.exp(-((x-.017)/.013)**2) + 3*np.exp(-((x-.974)/.017)**2)
    if enamel:
        body = np.array([25, 44, 99]) + light[..., None]*[16, 27, 46]
        return body + grain[..., None]*[.63, .76, .95] + lip[..., None]*[.48, .7, 1.1]
    body = np.array([196, 153, 69]) + light[..., None]*[43, 46, 58]
    return body + grain[..., None]*[1.0, .92, .70] + lip[..., None]*[1.2, 1.5, 1.4]


def enamel_masks(x, y, aa):
    # One band sweeps from the left of the mouth to the right of the chape.
    # It begins partly around the side wall, opens across the face and tapers away.
    stations = [0, .1, .22, .36, .5, .6, .7, .76, .84, .90, .955, 1]
    inner = curve([.08, .088, .117, .207, .345, .473, .654, .795, 1.00, 1.15, 1.30, 1.39], stations, y)
    outer = curve([-.33, -.32, -.30, -.26, -.155, -.039, .125, .265, .464, .665, .935, 1.24], stations, y)
    blue = np.zeros_like(x+y)
    gaps = np.zeros_like(blue)
    for u in (x, 1-x):
        ribbon = smooth(outer-aa, outer+aa, u) * (1-smooth(inner-aa, inner+aa, u))
        blue = np.maximum(blue, ribbon)
        # At each crossing the fine gold rules remain continuous in both directions.
        for fraction in (.255, .51, .764):
            axis = outer + (inner-outer)*fraction
            half = .0046 + .0044*y
            line = 1-smooth(half-aa, half+aa, np.abs(u-axis))
            gaps = np.maximum(gaps, ribbon*line)

        # A separate fan of four fine blades meets the side rail at the throat.
        span = np.maximum(0, u-.134) / .866
        front = .014 + .019*span
        back = .014 + .083*span + .006*np.sin(np.pi*span)
        length_aa = aa*.24
        fan = smooth(front-length_aa, front+length_aa, y) * (1-smooth(back-length_aa, back+length_aa, y))
        fan = fan*smooth(.132-aa, .132+aa, u)
        blue = np.maximum(blue, fan)
        for fraction in (.26, .51, .755):
            axis = front + (back-front)*fraction
            half = .00039 + .00070*span
            line = 1-smooth(half-length_aa, half+length_aa, np.abs(y-axis))
            gaps = np.maximum(gaps, fan*line)
    blue *= (1-smooth(.952, .968, y))
    return blue*(1-gaps), gaps*blue


# Each path is a start point followed by quadratic pen movements. A path occupies
# a single line of fairy script on the scabbard. Coordinates below follow the
# inscription across the table photo, before rotating it onto the longitudinal UV.
INSCRIPTION = [
    [(1,28),(8,35,16,28),(14,24,10,25),(6,24,4,22),(7,20,7,17),
     (4,15,7,11),(9,7,6,4),(17,12,22,14),(29,18,35,13),(38,12,35,20)],
    [(21,23),(30,26,41,23),(47,22,47,26),(46,31,43,31)],
    [(46,30),(48,27,48,20),(49,16,46,12),(47,8,53,8),(57,9,55,14),
     (54,19,60,23),(59,28,66,22),(70,18,70,16)],
    [(66,18),(75,25,79,29),(75,21,69,16)],
    [(72,14),(80,19,83,25),(78,16,74,13),(82,17,88,28)],
    [(90,28),(88,23,86,19),(91,16,93,21),(94,25,95,27),(96,24,99,23)],
    [(95,16),(91,10,89,10),(99,13,103,20),(108,27,111,27),
     (113,25,111,20),(114,17,118,21),(121,24,123,27)],
    [(121,25),(124,19,130,18),(134,16,130,13),(137,17,131,20),
     (125,24,124,29),(123,34,130,31)],
    [(133,25),(139,26,148,32),(151,28,151,19),(148,17,145,23),
     (149,19,152,20)],
]


def inscription(size, scale):
    image = Image.new("L", size)
    draw = ImageDraw.Draw(image)
    for index, stroke in enumerate(INSCRIPTION):
        a = np.array(stroke[0], dtype=float)
        points = [a]
        for segment in stroke[1:]:
            control, b = np.array(segment[:2]), np.array(segment[2:])
            for t in np.linspace(0, 1, 14)[1:]:
                points.append(a*(1-t)**2 + 2*control*(1-t)*t + b*t*t)
            a = b
        for i, (advance, across) in enumerate(points):
            u = .494 + (across-20)/34*.174
            v = .142 + advance/153*.125
            cx = scale*(FACE_LEFT + u*(FACE_RIGHT-FACE_LEFT))
            cy = scale*(THROAT + v*(TIP-THROAT))
            # A broad nib leaves wedge terminals and fine upstrokes.
            tip = min(1, i/6+.25, (len(points)-i)/7+.20)
            radius = scale*tip*(1.12+.50*np.sin(i*.085+index)**2)
            if i:
                draw.line((last, (cx,cy)), fill=255, width=max(1, round(2*radius)))
            draw.ellipse((cx-radius, cy-radius*1.30, cx+radius, cy+radius*1.30), fill=255)
            last = (cx, cy)
    return image


def terminal(x, y, base, grain, aa):
    # Short polished copper-gold chape with an engraved sprig and a tiny pale boss.
    edge = .961 - .016*np.abs(2*x-1) + .0025*np.cos((x-.5)*np.pi*6)
    cap = smooth(edge-.00065, edge+.00065, y)
    hue = (.45 + .40*np.cos((x-.39)*np.pi*2) + .09*np.cos((y-.94)*60))
    copper = np.array([163, 96, 47]) + hue[..., None]*[81, 89, 68] + grain[..., None]*.8
    rgb = mix(base, copper, cap)
    join = (1-smooth(.00040, .00098, np.abs(y-edge)))
    rgb = mix(rgb, np.array([245, 206, 124]), join*.85)

    # Three dark lancets merge into one fine inverted stem in the metal.
    n = (y-.954)/.033
    center = np.abs(x-.5)
    stem_width = curve([.012,.033,.063,.095,.043,.029,.0], [0,.12,.3,.49,.63,.80,1], n)
    stem = (1 - smooth(stem_width-aa, stem_width+aa, center)) * smooth(.0, .04, n)*(1-smooth(.99,1,n))
    leaf_axis = curve([.21,.195,.164,.125,.101,.067,.037], [0,.11,.21,.34,.42,.51,.61], n)
    leaf_width = curve([0,.062,.075,.051,.060,.041,0], [0,.11,.21,.34,.42,.51,.61], n)
    leaves = ((1 - smooth(leaf_width-aa,leaf_width+aa,np.abs(center-leaf_axis)))
              *smooth(-.01,.015,n)*(1-smooth(.59,.62,n)))
    crest = np.maximum(stem, leaves)
    # Tiny chased rim around each dark notch.
    relief = np.roll(crest,1,axis=1)-np.roll(crest,-2,axis=1)
    rgb = mix(rgb, np.array([48,30,29]), crest)
    rgb += relief[...,None]*[17,18,12]

    for dx in (-.135,.135):
        # Gold petals curl around the pearl on the end plate.
        local_y = (y-.943)/.0092
        local_x = (x-.5-dx)/.13 + np.sign(dx)*local_y*.36
        q = local_x**2 + local_y**2
        mask = 1-smooth(.76, 1.12, q)
        tone = np.clip(.44 - local_y*.35 + .12*local_x + .27*np.maximum(0,1-q), 0, 1)
        fold = np.exp(-((local_x-.25*local_y-.2)/.09)**2)*np.exp(-((local_y+.17)/.72)**4)
        petal = np.array([145,91,34])+tone[...,None]*[104,115,92] + fold[...,None]*[17,23,17]
        rgb = mix(rgb, petal, mask)
    q = np.sqrt(((x-.50)/.095)**2 + ((y-.9472)/.0062)**2)
    rim = 1-smooth(.91,1.08,q)
    rgb = mix(rgb, np.array([134,87,40]), rim)
    pearl = 1-smooth(.68,.90,q)
    shine = np.clip(.60 - (x-.5)*2.9 - (y-.947)*62 + .18*np.maximum(0,1-q),0,1)
    glint = np.exp(-(((x-.477)/.033)**2+((y-.9449)/.0016)**2))
    rgb = mix(rgb, np.array([83,91,98])+shine[...,None]*[150,145,126] + glint[...,None]*[41,40,38], pearl)
    return rgb


def bake(output, resolution_scale):
    # Paint at double sampling density so fine curves survive native mipmapping.
    ss = 2*resolution_scale
    ix = (np.arange(WIDTH*ss, dtype=np.float32)[None,:]+.5)/ss
    iy = (np.arange(HEIGHT*ss, dtype=np.float32)[:,None]+.5)/ss
    x = np.clip((ix-FACE_LEFT)/(FACE_RIGHT-FACE_LEFT),0,1)
    y = np.clip((iy-THROAT)/(TIP-THROAT),0,1)
    rng = np.random.default_rng(630531)
    grain = (1.2*rng.standard_normal((len(iy), len(ix[0])), dtype=np.float32)
             + .7*rng.standard_normal((len(iy),1), dtype=np.float32))
    gold, cobalt = surface(x,y,grain), surface(x,y,grain,True)
    aa = .46/(ss*(FACE_RIGHT-FACE_LEFT))
    blue, fillet = enamel_masks(x,y,aa)
    rgb = mix(gold,cobalt,blue)
    rgb = mix(rgb, gold + np.array([15,15,10]), fillet*.80)
    mask = Image.fromarray(np.uint8(blue*255)).filter(ImageFilter.GaussianBlur(.77*ss))
    relief = np.asarray(mask,dtype=np.float32)/255
    dx = np.roll(relief,ss,axis=1)-np.roll(relief,-ss,axis=1)
    dy = np.roll(relief,ss,axis=0)-np.roll(relief,-ss,axis=0)
    rgb += (dx+.4*dy)[...,None]*[11,11,9]
    rgb = terminal(x,y,rgb,grain,aa)

    writing = inscription((WIDTH*ss,HEIGHT*ss),ss)
    ink = np.asarray(writing,dtype=np.float32)/255
    rim = np.asarray(writing.filter(ImageFilter.GaussianBlur(ss*.72)),dtype=float)/255
    rgb += (np.roll(rim,ss,axis=1)-np.roll(rim,-ss,axis=1))[...,None]*[18,16,11]
    rgb = mix(rgb, np.array([52,37,28]), ink*.98)

    for start, color, strength in (
        (.788, [229,188,102], 10), (.842,[186,142,64], 12),
        (.895,[136,92,45], 10), (.950,[39,43,56], 4)
    ):
        tone = np.sin((y*.57 + .09*np.sin(y*11))*np.pi*2)
        stripe = np.array(color) + strength*tone[...,None] + .7*grain[...,None]
        rgb = np.where((ix >= WIDTH*start)[...,None], stripe, rgb)
    texture = Image.fromarray(np.clip(np.rint(rgb),0,255).astype(np.uint8))
    texture = texture.resize((WIDTH*resolution_scale,HEIGHT*resolution_scale),Image.Resampling.LANCZOS)
    output.parent.mkdir(parents=True,exist_ok=True)
    texture.save(output,optimize=True)


if __name__ == "__main__":
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--output",type=Path,default=DEFAULT_FILE)
    cli.add_argument("--scale",type=int,choices=(1,2),default=1,
                     help="Use 1 for the game, 2 for an offline painting study.")
    options = cli.parse_args()
    bake(options.output,options.scale)
