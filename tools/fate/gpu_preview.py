"""Headless OpenGL QA of the production GLSL; this is NOT a Minecraft runtime capture.
python gpu_preview.py --output <directory> (requires moderngl, numpy, Pillow)
"""
import argparse, gzip, json, math, struct
from pathlib import Path
import moderngl as gl
import numpy as np
from PIL import Image,ImageDraw
p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);args=p.parse_args();args.output.mkdir(parents=True,exist_ok=True)
assets=Path(__file__).resolve().parents[2]/'src/main/resources/assets/starcrowndoll'
ctx=gl.create_standalone_context(require=320);W,H=960,600
shader=ctx.program(vertex_shader=(assets/'shaders/core/fate_retrieval.vsh').read_text(),fragment_shader=(assets/'shaders/core/fate_retrieval.fsh').read_text())
color=ctx.texture((W,H),4);depth=ctx.depth_renderbuffer((W,H));fbo=ctx.framebuffer([color],depth);fbo.use()
def matrix(name,m):shader[name].write(np.asarray(m,dtype='f4').T.tobytes())
I=np.eye(4,dtype='f4')
def look(eye,target):
    eye=np.array(eye,dtype='f4');target=np.array(target,dtype='f4');z=eye-target;z/=np.linalg.norm(z);x=np.cross([0,1,0],z);x/=np.linalg.norm(x);y=np.cross(z,x)
    m=I.copy();m[:3,:3]=[x,y,z];m[:3,3]=-m[:3,:3]@eye;return m
def perspective(fov,aspect,near,far):
    f=1/math.tan(math.radians(fov)/2);return np.array([[f/aspect,0,0,0],[0,f,0,0],[0,0,(far+near)/(near-far),2*far*near/(near-far)],[0,0,-1,0]],dtype='f4')
def vao(rows):
    data=np.array(rows,dtype='f4');buffer=ctx.buffer(data.tobytes());return ctx.vertex_array(shader,[(buffer,'3f 2f 4f','Position','UV0','Color')])
def row(pt,uv=(0,0),col=(1,1,1)):return [*pt,*uv,*col,1]
lines=[]
for path in json.loads((assets/'models/fate/analysis.json').read_text(encoding='utf8'))['paths']:
    for a,b in zip(path['points'],path['points'][1:]):
        a,b=np.array(a),np.array(b);d=b-a
        if np.linalg.norm(d)<1e-8:continue
        d/=np.linalg.norm(d);n=np.cross(d,[0,1,0] if abs(d[1])<.9 else [1,0,0]);n=n/np.linalg.norm(n)*path['radius'];m=np.cross(d,n);m=m/np.linalg.norm(m)*path['radius'];corners=[n,m,-n,-m]
        for j in range(4):
            pts=[a+corners[j],a+corners[(j+1)%4],b+corners[(j+1)%4],b+corners[j]]
            lines += [row(pts[i],col=path['color']) for i in [0,1,2,0,2,3]]
analysis=vao(lines)
c=[[-10.2,3.5,-10.2],[10.2,3.5,-10.2],[10.2,35,-10.2],[-10.2,35,-10.2],[-10.2,3.5,10.2],[10.2,3.5,10.2],[10.2,35,10.2],[-10.2,35,10.2]]
cube=vao([row(c[f[i]]) for f in [[0,3,2,1],[4,5,6,7],[0,4,7,3],[1,2,6,5],[0,1,5,4],[3,7,6,2]] for i in [0,1,2,0,2,3]])
quad=[[-1,-1,0],[1,-1,0],[1,1,0],[-1,1,0]];gate=vao([row(quad[i],((quad[i][0]+1)/2,(quad[i][1]+1)/2)) for i in [0,1,2,0,2,3]])
# Match java.util.Random exactly so the preview shows the game's actual fracture topology.
class Random:
    def __init__(self,seed):self.seed=(seed^0x5DEECE66D)&((1<<48)-1)
    def bits(self,n):self.seed=(self.seed*0x5DEECE66D+11)&((1<<48)-1);return self.seed>>(48-n)
    def double(self):return ((self.bits(26)<<27)+self.bits(27))/(1<<53)
rng=Random(81742);rings=[]
for r in range(5):
    ring=[]
    for i in range(20):
        a=(i+(r%2)*.38+(rng.double()-.5)*.32)*math.pi*2/20
        radius=0 if r==0 else 5 if r==4 else r*.45*(.78+rng.double()*.44)
        ring.append(np.array([math.cos(a)*radius,math.sin(a)*radius,0]))
    rings.append(ring)
shardrows=[]
for r in range(4):
    for i in range(20):
        j=(i+1)%20;tris=[[rings[r][i],rings[r+1][i],rings[r+1][j]]]
        if r>0:tris.append([rings[r][i],rings[r+1][j],rings[r][j]])
        for tri in tris:
            center=sum(tri)/3
            shardrows += [row(v,center[:2],bary) for v,bary in zip(tri,np.eye(3))]
shards=vao(shardrows)
shader['Aspect'].value=W/H;shader['MaskCenter'].value=(.35,-.16);shader['HandSide'].value=1
def draw(v,mode,age,model,proj):
    matrix('ModelViewMat',model);matrix('ProjMat',proj);shader['Mode'].value=mode;shader['Age'].value=age
    eye=np.linalg.inv(model)@np.array([0,0,0,1]);shader['CameraLocal'].value=tuple(eye[:3]);v.render(gl.TRIANGLES)
def capture(name):
    image=Image.frombytes('RGBA',(W,H),fbo.read(components=4)).transpose(Image.Transpose.FLIP_TOP_BOTTOM).convert('RGB');image.save(args.output/name);return image
ctx.enable(gl.DEPTH_TEST|gl.BLEND);ctx.blend_func=gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA
fbo.clear(.009,.012,.028,1);view=look([24,13,42],[0,16,0]);proj=perspective(46,W/H,.05,300)
draw(analysis,0,76,view,proj);ctx.depth_mask=False;draw(cube,1,76,view,proj);capture('analysis-gpu.png')
ctx.disable(gl.DEPTH_TEST);matrix('ModelViewMat',I);matrix('ProjMat',I)
fbo.clear(.015,.012,.04,1);aperture=I.copy();aperture[0,0]=H/W
draw(gate,2,96,aperture,I);capture('gold-aperture-gpu.png')
# Draw a simple blue checker background solely to expose mask coverage and separation.
bg=Image.new('RGB',(W,H),(18,27,55));d=ImageDraw.Draw(bg)
for y in range(0,H,60):
    for x in range(0,W,60):
        if(x//60+y//60)%2:d.rectangle([x,y,x+59,y+59],fill=(31,45,76))
bgtex=ctx.texture((W,H),3,bg.transpose(Image.Transpose.FLIP_TOP_BOTTOM).tobytes())
bgshader=ctx.program(vertex_shader='#version 150\nin vec2 p;out vec2 uv;void main(){uv=p*.5+.5;gl_Position=vec4(p,0,1);}',fragment_shader='#version 150\nuniform sampler2D image;in vec2 uv;out vec4 color;void main(){color=texture(image,uv);}')
bgvao=ctx.vertex_array(bgshader,[(ctx.buffer(np.array([[-1,-1],[1,-1],[1,1],[-1,-1],[1,1],[-1,1]],dtype='f4').tobytes()),'2f','p')])
frames=[];coverage=[]
for age in list(range(114,161)):
    fbo.clear(0,0,0,1);bgtex.use();bgvao.render();
    if age<158:draw(shards,3,age,I,I)
    if age<143:draw(gate,5,age,I,I)
    im=capture(f'fracture-{age:03}.png');frames.append(im)
    a=np.array(im);coverage.append({'tick':age,'black_fraction':float(np.all(a<8,axis=2).mean()),'white_fraction':float(np.all(a>220,axis=2).mean())})
frames[0].save(args.output/'fracture-gpu.gif',save_all=True,append_images=frames[1:],duration=70,loop=0)
report={'renderer':ctx.info['GL_RENDERER'],'gl_version':ctx.info['GL_VERSION'],'shader_compile_link':'PASS','gl_error':ctx.error,'preview':'standalone production GLSL, not Minecraft runtime','coverage':coverage}
(args.output/'gpu-report.json').write_text(json.dumps(report,indent=2));print(json.dumps({k:v for k,v in report.items() if k!='coverage'}))
