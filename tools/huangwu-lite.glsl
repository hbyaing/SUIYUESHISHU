// Huangwu study v4: stronger annular fog, still one 12-sample bounded 3D field.
// ro / rd / core / disk are all in the same local space. Host supplies nearest scene t.
float hwSphere(vec3 ro,vec3 rd,float radius){
 float b=dot(ro,rd),h=b*b-dot(ro,ro)+radius*radius;
 if(h<0.0)return 1e8;
 float a=-b-sqrt(h),c=-b+sqrt(h);
 return a>0.0?a:(c>0.0?c:1e8);
}
vec3 hwTone(vec3 c){return pow(max(c/(1.0+c),vec3(0.0)),vec3(1.0/2.2));}
float hwHash3(vec3 p){p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);}
float hwNoise3(vec3 p){
 vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
 return mix(mix(mix(hwHash3(i),hwHash3(i+vec3(1,0,0)),f.x),
                mix(hwHash3(i+vec3(0,1,0)),hwHash3(i+vec3(1,1,0)),f.x),f.y),
            mix(mix(hwHash3(i+vec3(0,0,1)),hwHash3(i+vec3(1,0,1)),f.x),
                mix(hwHash3(i+vec3(0,1,1)),hwHash3(i+vec3(1,1,1)),f.x),f.y),f.z);
}
// One bounded 3D cloud, 12 samples total. No stacked sheets or texture lookups.
vec4 hwFogVolume(vec3 ro,vec3 rd,float limit,float time,vec3 normal,float inner,float outer,vec3 hot){
 vec3 axis=cross(vec3(1,0,0),normal);
 vec3 o=vec3(ro.x,dot(ro,normal),dot(ro,axis));
 vec3 d=vec3(rd.x,dot(rd,normal),dot(rd,axis));
 vec3 extent=vec3(outer,outer*.47,outer);
 vec3 so=o/extent,sd=d/extent;
 float A=dot(sd,sd),B=dot(so,sd),C=dot(so,so)-1.0,h=B*B-A*C;
 if(h<=0.0)return vec4(0);
 float start=max(0.0,(-B-sqrt(h))/A),end=min(limit,(-B+sqrt(h))/A);
 if(end<=start)return vec4(0);
 float dt=(end-start)/12.0,trans=1.0;
 vec3 light=vec3(0);
 for(int i=0;i<12;i++){
  vec3 p=o+d*(start+(float(i)+.5)*dt);
  float r=length(p.xz);
  if(r<inner*.72||r>outer)continue;
  float angle=atan(p.z,p.x),spin=r*.70-time*.30;
  mat2 rotation=mat2(cos(spin),-sin(spin),sin(spin),cos(spin));
  vec2 swirl=rotation*p.xz;
  vec3 sampleP=vec3(swirl.x,p.y*1.7,swirl.y)*2.15+vec3(0,time*.13,0);
  float coarse=hwNoise3(sampleP);
  float detail=hwNoise3(sampleP*2.03+vec3(9.7,3.2,17.8));
  float n=coarse*.76+detail*.24;
  float ripple=sin(angle*3.0+r*2.0-time*.65+n*2.0);
  float mid=.15*sin(angle*3.0+r*1.4-time*.4);
  float height=outer*(.20+.11*coarse);
  float vertical=exp(-pow((p.y-mid)/height,2.0)*2.1);
  float envelope=smoothstep(inner*.72,inner*1.20,r)*(1.0-smoothstep(outer*.68,outer,r));
  float cloud=smoothstep(.25,.71,n);
  // A continuous low-density base keeps the circular vortex readable between the curls.
  float density=(.08+.92*cloud*cloud)*vertical*envelope*(.66+.34*ripple);
  float heat=pow(clamp((outer-r)/(outer-inner),0.0,1.0),.85);
  vec3 emission=mix(vec3(.004,.001,.008),hot*.88,heat)*(.12+1.30*n*n);
  float alpha=1.0-exp(-density*6.4*dt);
  light+=trans*alpha*emission;trans*=1.0-alpha;
 }
 return vec4(light,1.0-trans);
}
vec4 hwLite(vec3 ro,vec3 rd,float sceneT,float time,int stage,float tilt,float strength){
 float radius=stage==2?.80:(stage==0?.49:.57);
 float outer=stage==2?2.55:(stage==0?1.25:1.45);
 float pulse=stage==0?(1.0+.035*sin(time*4.0)):1.0;
 radius*=pulse;outer*=pulse;
 float coreT=hwSphere(ro,rd,radius);
 bool coreVisible=coreT<sceneT;
 vec3 color=vec3(0.0);float opacity=0.0;
 vec3 cyan=vec3(.12,1.15,1.55),red=vec3(1.65,.013,.16),violet=vec3(.26,.009,.085);
 if(stage==0){red=vec3(.09,.65,1.8);violet=vec3(.02,.08,.34);}
 if(stage==1){cyan=vec3(1.3,.1,.48);red=vec3(2.0,.035,.30);}
 if(coreVisible){
  vec3 p=ro+rd*coreT,n=normalize(p);
  float fres=pow(1.0-max(dot(n,-rd),0.0),5.0);
  color=vec3(.001,.0015,.003)+cyan*fres*.85;
  if(stage!=2){
   float a=atan(n.z,n.x),r=acos(clamp(n.y,-1.0,1.0));
   float wave=.5+.5*sin(r*17.0+a*4.0+sin(r*6.0-a*3.0-time)*1.4+time*2.0);
   color+=red*(.06+.65*pow(wave,6.0))*(.35+.65*max(dot(n,normalize(vec3(-1,2,3))),0.0));
  }
  opacity=1.0;
 }
 // Local halo from the closest point of the straight ray, with a finite support.
 float closest=-dot(ro,rd);
 vec3 q=ro+rd*closest;float impact=length(q);
 if(closest>0.0&&closest<sceneT&&!coreVisible&&impact<outer*1.3){
  float gap=max(0.0,impact-radius),aa=max(fwidth(impact),.0015);
  float edge=exp(-gap/max(.018,aa*1.5));
  float broad=exp(-gap*7.0)*.11;
  float angle=atan(q.y,q.x);
  float arcs=.35+.65*pow(.5+.5*sin(angle*3.0+impact*5.0-time*1.8),3.0);
  float corona=exp(-pow((impact-radius*1.32)/(.22+radius*.08),2.0))*arcs*.10;
  color+=cyan*(edge*.75+broad)+red*corona;
  opacity=max(opacity,clamp(edge*.8+broad+corona*.5,0.0,.9));
 }
 // Integrate a single 3D cloud up to the first solid core / scene intersection.
 vec3 normal=vec3(0.0,cos(tilt),sin(tilt));
 vec4 fog=hwFogVolume(ro,rd,min(sceneT,coreT),time,normal,radius*1.10,outer,red);
 color=color*(1.0-fog.a)+fog.rgb;opacity=opacity+(1.0-opacity)*fog.a;
 // Two tapered cyan-white rays, anchored to local X and rejected behind the core/scene.
 // A finite cylindrical glow supplies a soft skirt around a thin white center.
 if(stage==2){
  float rayDenom=dot(rd.yz,rd.yz);
  if(rayDenom>.00001){
   float t=-dot(ro.yz,rd.yz)/rayDenom;
   vec3 p=ro+rd*t;float x=abs(p.x),lengthRay=3.05;
   if(t>0.0&&t<sceneT&&t<coreT&&x>radius*.80&&x<lengthRay){
    float progress=clamp((x-radius)/(lengthRay-radius),0.0,1.0);
    float taper=pow(1.0-progress,1.7);
    float width=max(.005,.025*taper),d=length(p.yz),aa=max(fwidth(d),.001);
    float line=1.0-smoothstep(width,width+aa,d);
    float glow=exp(-d*d/(.004+.012*taper))*taper*.20;
    float strengthRay=line*taper;
    color=color*(1.0-strengthRay*.8)+vec3(1.65,2.15,2.25)*strengthRay+vec3(.12,.70,.85)*glow;
    opacity=max(opacity,clamp(strengthRay+glow,0.0,1.0));
   }
  }
 }
 // Premultiplied light + alpha; exposure is a host artistic control.
 return vec4(color*strength,opacity);
}
