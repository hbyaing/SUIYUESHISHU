// Portable BB preview. This approximates the original Cycles density field.
// The original material nodes and textures are delivered separately.
#version 150
in vec3 vLocal;
in vec2 vUv;
in vec3 vNormal;
in vec3 vView;
uniform vec3 uCamera;
uniform vec3 uRayDirection;
uniform float uOrthographic;
uniform float uTime;
uniform float uKind;
uniform float uBound;
uniform float uHasMap;
uniform vec3 uTint;
uniform sampler2D Sampler0;
float hash(vec3 p){p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);}
float noise3(vec3 p){
 vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
 return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
float fbm(vec3 p){return .58*noise3(p)+.28*noise3(p*2.03+7.4)+.14*noise3(p*4.13+18.3);}
float triplanar(vec3 p){return dot(texture(Sampler0,p.xy*.46).rgb+texture(Sampler0,p.yz*.46).rgb+texture(Sampler0,p.zx*.46).rgb,vec3(.333333))*.333333;}
vec4 field(vec3 p){
 float r=length(p.xy),a=r*2.8-uTime*2.3;
 // Reject provably empty field regions before the expensive density noise.
 if(uKind<1.5){if(abs(p.z)>=.45||r<.38||r>1.50)return vec4(0.0);}
 else if(length(p/max(uBound,.001))>=1.0)return vec4(0.0);
 mat2 rm=mat2(cos(a),-sin(a),sin(a),cos(a));
 vec3 q=vec3(rm*p.xy,p.z);
 float n=fbm(q*4.7+vec3(uTime*.18,uTime*.08,uTime*.55));
 float envelope=1.0;
 if(uKind<1.5){
  float rw=r+(n-.5)*.26;
  envelope=pow(max(1.0-length(vec2((rw-.94)/.43,p.z/.45)),0.0),1.05);
  if(envelope<=0.0)return vec4(0.0);
 }
 q+=vec3(noise3(q*3.1+1.0),noise3(q*3.1+12.0),noise3(q*3.1+27.0))*.4-.2;
 float fine=fbm(q*16.0+vec3(uTime*.7,0.0,uTime));
 float bill=pow(max((n-.43)*5.1,0.0),1.5)*(.45+fine*.90)*(.72+.8*triplanar(q));
 if(uKind<1.5){
  float rw=r+(n-.5)*.26;
  float phase=atan(p.y,p.x)*3.0-r*7.0-uTime*4.5;
  float spiral=.28+.72*pow(sin(phase)*.5+.5,2.0);
  float f=envelope*spiral*bill,heat=clamp((1.27-rw)/.64,0.0,1.0);
  vec3 color=mix(vec3(.10,0.0,.014),vec3(1.0,.012,.24),smoothstep(.1,.82,heat));
  return vec4(color*(.05+pow(heat,3.0)*2.25),f*12.0);
 }
 vec3 pn=p/max(uBound,.001);
 float f=pow(max(1.0-length(pn),0.0),1.1)*bill;
 if(uKind<2.5)return vec4(vec3(.021,.001,.006),f*18.0);
 if(uKind<3.5)return vec4(mix(vec3(.06,.0,.012),vec3(1.0,.018,.19),n)*3.0,f*12.0);
 if(uKind<4.5)return vec4(vec3(.24,.77,1.0)*4.0,f*7.0);
 return vec4(vec3(1.0,.16,.41)*8.0,f*5.0);
}
out vec4 fragColor;
uniform sampler2D SceneDepth;
uniform float uUseDepth;
uniform mat4 InverseModelViewMat;
uniform mat4 InverseProjectionMat;
uniform vec2 ScreenSize;
void main(){
 if(uKind>.5&&uKind<5.5){
  // Integrate the proxy once per pixel, through its back-facing surface.
  if(gl_FrontFacing)discard;
  vec3 ro=uCamera,rd=normalize(vLocal-ro);
  if(uOrthographic>.5){
   // Orthographic pixels have parallel rays, each through its own domain point.
   // Start outside the bound; using the camera position here shifts the volume.
   rd=normalize(uRayDirection);
   ro=vLocal-rd*(length(vLocal)+uBound+1.0);
  }
  float b=dot(ro,rd),c=dot(ro,ro)-uBound*uBound,h=b*b-c;
  if(h<0.0)discard;
  float t0=max(0.0,-b-sqrt(h)),t1=-b+sqrt(h);
  if(uUseDepth>.5){
   vec2 screenUV=gl_FragCoord.xy/ScreenSize;
   float depth=texture(SceneDepth,screenUV).r;
   if(depth<.999999){
    vec4 view=InverseProjectionMat*vec4(screenUV*2.0-1.0,depth*2.0-1.0,1.0);view/=view.w;
    vec3 local=(InverseModelViewMat*view).xyz;
    vec3 scene=vec3(-local.x,local.z,local.y);
    t1=min(t1,dot(scene-ro,rd));
   }
  }
  if(t1<=t0)discard;
  float dt=(t1-t0)/48.0;
  vec3 rgb=vec3(0.0);float trans=1.0;
  for(int i=0;i<48;i++){
   vec4 d=field(ro+rd*(t0+(float(i)+.5)*dt));
   float alpha=1.0-exp(-max(d.a,0.0)*dt);
   rgb+=trans*alpha*d.rgb;trans*=1.0-alpha;
   if(trans<.015)break;
  }
  float alpha=1.0-trans;if(alpha<.004)discard;
  fragColor=vec4(rgb/max(alpha,.001),alpha);return;
 }
 if(uKind>6.5&&uKind<7.5){fragColor=vec4(.001,.002,.004,1.0);return;}
 if(uKind>7.5){
  vec4 ray=texture(Sampler0,vec2(vUv.x*.5+.5,.5));
  float alpha=ray.a*pow(max(1.0-vUv.x,0.0),1.2);
  if(alpha<.003)discard;
  fragColor=vec4(vec3(.56,.90,1.0)*1.5,alpha);return;
 }
 vec4 tex=uHasMap>.5?texture(Sampler0,vUv+vec2(uTime*.12,uTime*.044)):vec4(1.0);
 vec3 col=tex.rgb*uTint;float alpha=tex.a;
 if(uKind>5.5){
  float fres=1.0-abs(dot(normalize(vNormal),normalize(vView)));
  alpha*=smoothstep(.28,.96,fres);
  col=max(col,vec3(.34,.81,1.0))*1.6;
 }
 if(alpha<.015)discard;
 fragColor=vec4(col,alpha);
}
