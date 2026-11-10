"""Build an offline white-model workbench from the game's current doll meshes."""

import argparse
import base64
import json
from pathlib import Path


HERE = Path(__file__).resolve().parent
ASSETS = HERE.parents[1] / 'src/main/resources/assets/starcrowndoll'
PARTS = {
    'head': [('head', 0), ('head', 1)],
    'body': [('body', 0), ('rightLeg', 0), ('leftLeg', 0),
             ('body', 1), ('rightLeg', 1), ('leftLeg', 1)],
    'right_arm': [('rightArm', 0), ('rightArm', 1)],
    'left_arm': [('leftArm', 0), ('leftArm', 1)],
}


def build():
    boxes = []
    for source, parts in PARTS.items():
        path = ASSETS / 'models/block' / f'doll_part_{source}.json'
        elements = json.loads(path.read_text(encoding='utf-8'))['elements']
        for element, (part, layer) in zip(elements, parts, strict=True):
            boxes.append({'part': part, 'layer': layer, 'element': element})

    samples = {}
    for name in ('chengran', 'yule'):
        png = (ASSETS / 'textures/block' / f'{name}_skin.png').read_bytes()
        samples[name] = 'data:image/png;base64,' + base64.b64encode(png).decode('ascii')

    data = json.dumps({'boxes': boxes, 'samples': samples}, ensure_ascii=False, separators=(',', ':'))
    html = (HERE / 'studio.html').read_text(encoding='utf-8')
    html = html.replace('/* STUDIO_CSS */', (HERE / 'studio.css').read_text(encoding='utf-8'))
    html = html.replace('/* MODEL_DATA */', data)
    return html.replace('/* STUDIO_JS */', (HERE / 'studio.js').read_text(encoding='utf-8'))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='verify the portable page is up to date')
    options = parser.parse_args()
    target = HERE / 'index.html'
    output = build()
    if options.check:
        if target.read_text(encoding='utf-8') != output:
            parser.exit(1, 'index.html is stale. Rebuild it with python build.py.\n')
    else:
        target.write_text(output, encoding='utf-8', newline='\n')
