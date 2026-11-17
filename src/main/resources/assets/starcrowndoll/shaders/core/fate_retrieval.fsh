#version 150
uniform float Mode;
uniform float Age;
uniform vec3 CameraLocal;
in vec3 localPos;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;
float sat(float x){return clamp(x,0.0,1.0);}
float hash(vec3 p){return fract(sin(dot(p,vec3(127.1,311.7,74.7)))*43758.5453);}
float noise(vec3 p){
    vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
float pressure(vec3 p){
    float t=Age*.085,r=length(p.xz),theta=atan(p.z,p.x),z=p.y/.24;
    float warp=(noise(vec3(p.xz*.5,z*.12-t*.35))-.5)*2.5;
    float shell=exp(-1.7*pow((r/.24-(29.0+sin(z*.095-t)*2.7+warp))/.65,2.0));
    float phase=z*.33+theta*2.0-t+warp*.43;
    float band=sat((cos(phase)-.967)/.033);
    float second=sat((cos(z*.21-theta*3.0+t*1.3)-.96)*25.0)*.35;
    float arc=sat((sin(theta*3.0+z*.08)+.25)*1.7);
    float height=sat((z-18.0)/12.0)*sat((145.0-z)/18.0);
    return shell*(band+second)*arc*height;
}
void main(){
    if(Mode<.5){
        float grow=smoothstep(6.0,66.0,Age),top=1.8+32.0*grow;
        if(localPos.y>top)discard;
        float life=1.0-smoothstep(96.0,114.0,Age);
        if(life<.01)discard;
        vec3 color=vertexColor.rgb;
        float scan=exp(-pow((localPos.y-top)/.4,2.0));
        color+=vec3(1.0,.22,.06)*scan;
        fragColor=vec4(color*life,1.0);return;
    }
    if(Mode<1.5){
        // Boundary cube only: every pressure line is evaluated by this spatial shader.
        bool inside=all(greaterThan(CameraLocal,vec3(-10.2,3.5,-10.2)))&&all(lessThan(CameraLocal,vec3(10.2,35.0,10.2)));
        if((inside&&gl_FrontFacing)||(!inside&&!gl_FrontFacing))discard;
        vec3 rd=normalize(localPos-CameraLocal),inv=1.0/rd;
        vec3 t0=(vec3(-10.2,3.5,-10.2)-CameraLocal)*inv,t1=(vec3(10.2,35.0,10.2)-CameraLocal)*inv;
        vec3 nearT=min(t0,t1),farT=max(t0,t1);
        float a=max(0.0,max(nearT.x,max(nearT.y,nearT.z))),b=min(farT.x,min(farT.y,farT.z));
        if(b<=a)discard;
        float sum=0.0,stepSize=(b-a)/48.0;
        for(int i=0;i<48;i++)sum+=pressure(CameraLocal+rd*(a+(float(i)+.5)*stepSize))*stepSize;
        float life=smoothstep(30.0,60.0,Age)*(1.0-smoothstep(95.0,114.0,Age));
        float alpha=(1.0-exp(-sum*2.6))*life;
        fragColor=vec4(1.0,.012,.024,alpha*.8);return;
    }
    if(Mode<2.5){
        vec2 q=texCoord*2.0-1.0;float r=length(q),t=Age*.08;
        float opening=smoothstep(80.0,86.0,Age)*(1.0-smoothstep(110.0,114.0,Age));
        r/=max(.001,opening);if(r>1.05)discard;
        float n=noise(vec3(q*8.0,t))*.6+noise(vec3(q*19.0,-t*.7))*.4;
        float rim=exp(-pow((r-.82)/.065,2.0));
        float core=sat(1.0-r*r)*(.65+n*.65);
        float flare=exp(-abs(q.y)*90.0)*exp(-abs(q.x)*1.8)*.5;
        vec3 color=vec3(1.0,.55,.06)*(core+rim)+vec3(1.0,.92,.46)*pow(core,2.0)+flare;
        fragColor=vec4(color,sat((1.0-r)*12.0)*opening);return;
    }
    if(Mode>3.5&&Mode<4.5&&length(localPos.xy)>1.5)discard;
    // Cross light occurs on the still-intact BLACK surface. Its pieces separate only afterwards.
    vec2 q=localPos.xy;
    float cross=max(exp(-abs(q.x+.13*q.y)*150.0),exp(-abs(q.y+.33*q.x)*170.0));
    float ignition=smoothstep(134.0,136.0,Age)*(1.0-smoothstep(140.0,143.0,Age));
    float burst=exp(-dot(q,q)*22.0)*ignition;
    float flash=smoothstep(139.0,140.0,Age)*(1.0-smoothstep(140.0,142.0,Age));
    vec3 color=vec3(cross*ignition+burst+flash*.95);
    if(Mode>4.5){float a=sat(max(cross*ignition,burst)+flash);if(a<.002)discard;fragColor=vec4(1.0,1.0,1.0,a);return;}
    if(Age<116.0){float r=length(q);color+=vec3(.6,.005,.02)*exp(-pow((r-(Age-114.0)*2.5)/.1,2.0));}
    fragColor=vec4(color,1.0);
}
