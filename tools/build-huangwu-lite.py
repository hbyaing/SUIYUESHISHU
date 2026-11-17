"""Embed the approved v4 field into the Forge GLSL 150 host. --check audits generated output."""
from pathlib import Path
import json, sys
root=Path(__file__).resolve().parents[1]
out=root/'src/main/resources/assets/starcrowndoll/shaders/core'
header='''#version 150
uniform sampler2D Sampler0;
uniform mat4 InverseProjectionMat;
uniform mat4 InverseModelViewMat;
uniform vec2 IntegrationSize;
uniform float uTime;
uniform float uStage;
uniform float uFade;
out vec4 fragColor;
'''
host='''
vec3 localPoint(vec4 p){p/=p.w;return (InverseModelViewMat*vec4(p.xyz,1.0)).xyz;}
void main(){
 vec2 uv=gl_FragCoord.xy/IntegrationSize,ndc=uv*2.0-1.0;
 vec4 optical=InverseProjectionMat*vec4(0,0,1,0);
 // Recover the actual optical eye, including bob/hurt/portal translations.
 vec3 ro=abs(optical.w)>.000001?localPoint(optical):localPoint(InverseProjectionMat*vec4(ndc,-1,1));
 vec3 farPoint=localPoint(InverseProjectionMat*vec4(ndc,.9999,1));
 vec3 rd=normalize(farPoint-ro);
 float depth=texture(Sampler0,uv).r,sceneT=1e8;
 if(depth<.999999){vec3 hit=localPoint(InverseProjectionMat*vec4(ndc,depth*2.0-1.0,1));sceneT=max(0.0,dot(hit-ro,rd));}
 vec4 field=hwLite(ro,rd,sceneT,uTime,int(uStage+.5),radians(55.0),1.0);
 if(field.a<.001||uFade<=.001)discard;
 // The shared compositor expects straight RGB here and stores premultiplied RGB offscreen.
 fragColor=vec4(hwTone(field.rgb/max(field.a,.001)),field.a*uFade);
}
'''
identity=[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]
uniforms=[dict(name=n,type='matrix4x4',count=16,values=identity) for n in ['InverseProjectionMat','InverseModelViewMat']]
uniforms += [dict(name='IntegrationSize',type='float',count=2,values=[640,400])]
uniforms += [dict(name=n,type='float',count=1,values=[v]) for n,v in [('uTime',0),('uStage',2),('uFade',1)]]
spec=dict(blend=dict(func='add',srcrgb='srcalpha',dstrgb='1-srcalpha',srcalpha='one',dstalpha='1-srcalpha'),vertex='starcrowndoll:huangwu_lite',fragment='starcrowndoll:huangwu_lite',attributes=['Position','UV0'],samplers=[dict(name='Sampler0')],uniforms=uniforms)
files={'huangwu_lite.fsh':header+(root/'tools/huangwu-lite.glsl').read_text(encoding='utf8')+host,'huangwu_lite.json':json.dumps(spec,indent=2)+'\n'}
for name,data in files.items():
 if '--check' in sys.argv: assert (out/name).read_text(encoding='utf8')==data,name+' is stale'
 else: (out/name).write_text(data,encoding='utf8')
print('PASS generated Forge host matches approved kernel' if '--check' in sys.argv else 'Generated Forge lite host')
