"""Blender 4.5: export supplied source assets, without altering the source projects.
blender -b --python tools/fate/export_assets.py -- --sources <fate-msh-folder>
"""
import argparse, bpy, gzip, json, math, struct, sys
from pathlib import Path
from mathutils import Vector
args=argparse.ArgumentParser();args.add_argument('--sources',type=Path,required=True)
source=args.parse_args(sys.argv[sys.argv.index('--')+1:]).sources
dest=Path(__file__).resolve().parents[2]/'src/main/resources/assets/starcrowndoll'
(dest/'models/fate').mkdir(parents=True,exist_ok=True)
(dest/'textures/fate').mkdir(parents=True,exist_ok=True)
audit={}
def write_mesh(name,parts):
    total=0
    with gzip.open(dest/f'models/fate/{name}.mesh.gz','wb') as f:
        f.write(struct.pack('>ii',0x46415431,len(parts)))
        for texture,glow,rows in parts:
            raw=texture.encode('ascii');f.write(struct.pack('>H',len(raw))+raw)
            raw=glow.encode('ascii');f.write(struct.pack('>H',len(raw))+raw)
            f.write(struct.pack('>i',len(rows)))
            for row in rows:f.write(struct.pack('>8f',*row))
            total+=len(rows)
    audit[name]={'triangles':total//3,'parts':len(parts)}
def geometry(o,pivot,scale,uv_constant=None):
    dg=bpy.context.evaluated_depsgraph_get();ev=o.evaluated_get(dg);m=ev.to_mesh();m.calc_loop_triangles()
    normal=ev.matrix_world.to_3x3().inverted().transposed();rows=[]
    for tri in m.loop_triangles:
        for li in tri.loops:
            loop=m.loops[li];p=ev.matrix_world@m.vertices[loop.vertex_index].co
            n=(normal@loop.normal).normalized()
            uv=uv_constant or (m.uv_layers.active.data[li].uv if m.uv_layers.active else (.5,.5))
            rows.append((p.z*scale,(pivot-p.x)*scale,-p.y*scale,n.z,-n.x,-n.y,uv[0],1-uv[1]))
    ev.to_mesh_clear();return rows
bpy.ops.wm.open_mainfile(filepath=str(source/'outputs/王律之键_Bab_ilu.blend'))
# Preserve all authored component boundaries; a single bevel segment is sufficient at item scale.
for o in bpy.context.scene.objects:
    for modifier in o.modifiers:
        if modifier.type=='BEVEL':modifier.segments=1
parts=[]
for i,mat in enumerate(bpy.data.materials):
    objects=[o for o in bpy.context.scene.objects if o.type=='MESH' and any(s.material==mat for s in o.material_slots)]
    if not objects:continue
    color=mat.diffuse_color
    image=bpy.data.images.new(f'fate_gold_{i}',width=4,height=4)
    # diffuse_color is linear; store an sRGB texture with the same linear response.
    rgb=[12.92*c if c<=.0031308 else 1.055*c**(1/2.4)-.055 for c in color[:3]]
    image.pixels=list((*rgb,1))*16
    image.filepath_raw=str(dest/f'textures/fate/gold_{i}.png');image.file_format='PNG';image.save()
    rows=[]
    for o in objects:rows+=geometry(o,2.1,1.85/8.3,(.5,.5))
    parts.append((f'textures/fate/gold_{i}.png','',rows))
write_mesh('bab_ilu',parts)
bpy.ops.wm.read_factory_settings(use_empty=True)
folder=source/'work/ea_source/fbx/sword-of-rupture-enuma-elish-ea'
bpy.ops.import_scene.fbx(filepath=str(folder/'source/EA/EA.fbx'))
mapping={'bout':'Bout','lame_bas':'Lame','lame_haut':'Lame','lame_mid':'Lame','poignee':'Manche','support_01':'support_01','support_02':'support_02','support_03':'support_03'}
parts=[];converted=set()
for o in bpy.context.scene.objects:
    if o.type!='MESH':continue
    tex=mapping[o.name];glow='textures/fate/ea_lame_glow.png' if tex=='Lame' else ''
    if tex not in converted:
        for suffix,out in [('Base_Color',tex.lower()),('Emissive','lame_glow')]:
            matches=list((folder/'textures').glob(f'{tex}_{suffix}.*'))
            if not matches:continue
            image=bpy.data.images.load(str(matches[0]));image.scale(1024,1024)
            image.filepath_raw=str(dest/f'textures/fate/ea_{out}.png');image.file_format='PNG';image.save()
        converted.add(tex)
    parts.append((f'textures/fate/ea_{tex.lower()}.png',glow,geometry(o,.105,1.95/.23368)))
write_mesh('ea',parts)
bpy.ops.wm.open_mainfile(filepath=str(source/'outputs/王律之键_世界解析_三面斜岔.blend'))
bpy.context.scene.frame_set(160)
paths=[]
for o in bpy.context.scene.objects:
    if o.type!='CURVE' or not o.users_collection or not any('解析世界' in c.name for c in o.users_collection):continue
    c=o.data;mat=c.materials[0] if c.materials else None
    emission=next((n for n in mat.node_tree.nodes if n.type=='EMISSION'),None) if mat and mat.use_nodes else None
    color=list(emission.inputs[0].default_value[:3]) if emission else [1,.02,.04]
    for sp in c.splines:
        points=[o.matrix_world@Vector(p.co[:3]) for p in sp.points]
        if len(points)<2:continue
        paths.append({'name':o.name,'radius':c.bevel_depth*.24,'color':color,'points':[[p.x*.24,p.z*.24,-p.y*.24] for p in points]})
(dest/'models/fate/analysis.json').write_text(json.dumps({'paths':paths},ensure_ascii=False,separators=(',',':')),encoding='utf-8')
audit['analysis']={'curves':len(paths),'source':'approved three radial sheets; wind excluded'}
out=Path(__file__).resolve().parent/'asset-audit.json';out.write_text(json.dumps(audit,indent=2),encoding='utf-8');print(audit)
