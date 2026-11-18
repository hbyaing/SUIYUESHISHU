#version 150
uniform float Mode;
uniform float Age;
uniform float HandSide;
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
        vec2 q=texCoord*2.0-1.0;
        float opening=smoothstep(80.0,87.0,Age)*(1.0-.17*smoothstep(110.0,114.0,Age));
        if(opening<.005)discard;
        q/=opening;
        float r=length(q),time=Age*.075;
        float depth=sqrt(max(0.0,1.0-r*r/.74));
        vec3 sphere=vec3(q,depth);
        float clouds=noise(sphere*5.2+vec3(0.,time*.25,-time*.48))*.65
                    +noise(sphere*14.8+vec3(time*.39,0.,time*.27))*.35;
        float edge=abs(r-.84);
        float shell=exp(-edge*edge/.00020);
        float rim=exp(-edge*edge/.0070);
        float interior=(1.0-smoothstep(.72,.85,r));
        float liquid=smoothstep(.27,.74,clouds+.52*(1.0-r));
        float flare=exp(-abs(q.y)*110.0-abs(q.x)*1.65);
        vec3 orange=vec3(.97,.53,.19),gold=vec3(1.0,.88,.32),whiteGold=vec3(1.,.987,.67);
        vec3 body=mix(orange,gold,liquid);
        body=mix(body,whiteGold,smoothstep(.33,.95,depth+clouds*.36));
        body=mix(body,vec3(1.,.77,.36),rim*.55)+vec3(.15,.16,.13)*shell+flare*.38;
        float halo=exp(-max(r-.84,0.0)*19.0)*.52;
        float alpha=sat(interior+rim*.85+halo+flare*.9)*(1.0-smoothstep(.95,1.0,max(abs(q.x),abs(q.y))));
        fragColor=vec4(body,alpha*opening);return;
    }
    vec2 q=localPos.xy*vec2(HandSide,1.);
    float r=length(q);
    bool localLight=Mode>4.5;
    bool spectator=Mode>3.5&&!localLight;
    float extent=spectator?1.-smoothstep(1.05,1.42,r):1.;
    if(extent<.002)discard;
    vec3 col=vec3(.004,.003,.009);
    float alpha=localLight?0.:1.;
    if(Age>=111.&&Age<119.){
        float impulse=smoothstep(111.,113.5,Age)*(1.-smoothstep(116.,119.,Age));
        float ring=(Age-112.)*.45;
        float phi=atan(q.y,q.x);
        float streak=pow(.5+.5*cos(phi*127.+5.*sin(phi*9.)),7.)*(.45+.55*sin(phi*11.5+r*13.));
        float red=exp(-abs(r-ring)*4.)+.18*streak;
        float cyan=exp(-abs(r-ring-.045)*58.);
        float violet=exp(-abs(r-ring+.035)*75.);
        col=mix(vec3(.96,.006,.027)*(.64+red),vec3(.0,1.,.86),cyan);
        col=mix(col,vec3(.66,.04,.94),violet);
        if(localLight)alpha=impulse*(Age<114.?.76:.15+.62*max(cyan,violet));else col*=impulse;
    }
    if(Age>=131.&&Age<144.){
        float ignition=smoothstep(131.,136.,Age)*(1.-smoothstep(140.,144.,Age));
        // Unequal rays converge in the fist; opposed beams have different thickness.
        float lines=0.;
        vec2 directions[4]=vec2[4](normalize(vec2(-.30,.96)),normalize(vec2(-.60,-.80)),
                                     normalize(vec2(.46,-.89)),normalize(vec2(1.,-.29)));
        for(int i=0;i<4;i++){
            vec2 beam=directions[i];
            float axial=dot(q,beam),width=(i==3?.004:.010)+max(axial,0.)*(i==0?.034:.008);
            float distance=abs(q.x*beam.y-q.y*beam.x)/width;
            lines=max(lines,smoothstep(-.015,.04,axial)*exp(-distance*distance));
        }
        float white=ignition*max(lines,exp(-r*r*220.));
        col=mix(col,vec3(.985,.986,1.),white);
        if(localLight)alpha=max(alpha,white);
        float sweep=smoothstep(139.5,140.4,Age)*(1.-smoothstep(141.,143.5,Age));
        float slash=exp(-pow((q.y-.56*q.x-(Age-141.)*.6)/.29,2.))*sweep;
        col=mix(col,vec3(1.),slash);if(localLight)alpha=max(alpha,slash);
    }
    if(!localLight&&Age>140.){
        float seed=fract(sin(dot(texCoord,vec2(127.1,311.7)))*43758.5453);
        float flight=smoothstep(140.,148.,Age);
        vec3 px=vertexColor.rgb/max(fwidth(vertexColor.rgb),vec3(.0001));
        float bevel=1.-smoothstep(.35,1.9,min(px.r,min(px.g,px.b)));
        float face=sin((Age-140.)*(.29+seed*.28)+seed*9.);
        float edgeLight=bevel*(.25+.75*max(0.,face));
        float gleam=smoothstep(.72,.985,seed)*smoothstep(.1,.96,face);
        vec3 shard=mix(vec3(.008,.006,.019),vec3(.85,.88,1.),gleam);
        col=mix(col,shard+edgeLight*vec3(.28,.26,.42),flight);
    }
    if(alpha<.002)discard;
    fragColor=vec4(col,alpha*extent);
}
