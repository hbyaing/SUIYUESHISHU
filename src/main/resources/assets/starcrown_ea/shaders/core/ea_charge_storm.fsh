#version 150
uniform float GameTime;
in vec2 texCoord;
in vec4 parameters;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p,vec2(127.1,311.7))) * 43758.5453);
}
float noise2(vec2 p) {
    vec2 i=floor(p), f=fract(p);
    f=f*f*(3.0-2.0*f);
    return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),
               mix(hash(i+vec2(0,1)),hash(i+1.0),f.x),f.y);
}
void main() {
    float u=texCoord.x, v=texCoord.y, alpha=parameters.a;
    vec3 col;
    if (parameters.b > 0.9) {
        // The anime flare is long horizontally, with a finer vertical spike and a small white crossing.
        vec2 d=abs(texCoord);
        float halo=exp(-6.5*length(vec2(d.x*1.48,d.y*.81)));
        float horizontal=exp(-d.y*(23.0+63.0*d.x)-2.24*d.x);
        float vertical=exp(-d.x*(53.0+57.0*d.y)-3.8*d.y);
        float light=max(horizontal,vertical);
        float core=exp(-16.0*length(vec2(d.x*1.7,d.y)));
        col=mix(vec3(.98,.025,.24),vec3(1.0,.93,.97),smoothstep(.24,.83,light+core*.28));
        alpha*=clamp(light*1.6+halo*.94,0.0,1.0)*(1.0-smoothstep(.78,1.0,max(d.x,d.y)));
    } else if (parameters.b > 0.6) {
        float edge=abs(v*2.0-1.0);
        float core=1.0-smoothstep(.06,.29,edge);
        col=mix(vec3(1.0,.075,.24),vec3(1.0,.91,.94),core);
        alpha*=1.0-smoothstep(.24,1.0,edge);
    } else if (parameters.b > 0.3) {
        float edge=abs(v*2.0-1.0);
        float strand=1.0-smoothstep(.1,.60,edge);
        col=mix(vec3(.96,.08,.32),vec3(1.0,.56,.73),strand);
        alpha*=(1.0-smoothstep(.36,1.0,edge))*smoothstep(0.0,.13,u)*(1.0-smoothstep(.83,1.0,u));
    } else {
        float phase=parameters.g*31.0;
        float drift=GameTime*330.0;
        float curl=.105*sin(u*7.0+phase)+.046*sin(u*17.0-phase);
        float grain=noise2(vec2(u*8.0-drift+phase,v*5.4));
        float detail=noise2(vec2(u*26.0+phase-drift*2.0,v*15.0));
        float y=v+curl+.14*(grain-.5)+.047*(detail-.5);
        // Three unevenly torn lanes read as one broad wind sheet and two thin wisps.
        float body=smoothstep(.08,.23,y)*(1.0-smoothstep(.61,.79,y));
        float rim=smoothstep(.72,.79,y)*(1.0-smoothstep(.82,.89,y));
        float thread=smoothstep(.025,.048,y)*(1.0-smoothstep(.061,.085,y));
        float slit=smoothstep(.38,.65,grain)*(1.0-smoothstep(.016,.10,abs(y-.40-.12*sin(u*9.0+phase))));
        float paint=clamp(body*(1.0-slit*.89)+rim*.86+thread*.66,0.0,1.0);
        float light=clamp(.10+grain*.33+detail*.14+rim*.61+.16*sin(u*9.0+phase),0.0,1.0);
        vec3 cool=mix(vec3(.039,.047,.114),vec3(.47,.54,.76),light);
        vec3 rose=mix(vec3(.31,.025,.20),vec3(1.0,.60,.80),light);
        col=mix(cool,rose,smoothstep(.15,.96,parameters.r));
        if(parameters.b>.1){
            float hot=clamp(light*.78+rim*.54+.2*body,0.0,1.0);
            col=mix(vec3(.52,.013,.16),vec3(1.0,.43,.64),hot);
        }
        alpha*=paint*(.71+.18*grain)*smoothstep(0.0,.09,u)*(1.0-smoothstep(.83,1.0,u));
    }
    if (alpha < .006) discard;
    fragColor=vec4(col,alpha);
}
