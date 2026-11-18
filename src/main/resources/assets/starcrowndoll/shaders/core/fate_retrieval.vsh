#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float Mode;
uniform float Age;
uniform float Aspect;
uniform vec2 MaskCenter;
out vec3 localPos;
out vec2 texCoord;
out vec4 vertexColor;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
void main(){
    localPos=Position;texCoord=UV0;vertexColor=Color;
    vec3 p=Position;
    if(Mode>5.5){localPos.xy*=1.6;}
    else if(Mode>4.5){p.xy*=vec2(Aspect+2.0,2.5);localPos.xy=p.xy;p.xy=p.xy/vec2(Aspect,1.0)+MaskCenter;}
    else if(Mode>2.5){
        float seed=hash(UV0),t=clamp((Age-140.0-seed*1.5)/15.0,0.0,1.0);
        vec2 offset=p.xy-UV0;
        offset.x*=cos(t*(2.6+seed*3.0));
        float angle=(seed-.5)*t*3.2,c=cos(angle),s=sin(angle);
        offset=mat2(c,-s,s,c)*offset;
        vec2 outward=normalize(UV0+vec2(.0001));
        p.xy=UV0+offset+outward*(.42*t+6.8*t*t)+vec2((seed-.5)*t,.2*t);
        if(Mode<3.5)p.xy=p.xy/vec2(Aspect,1.0)+MaskCenter;
    }
    gl_Position=ProjMat*ModelViewMat*vec4(p,1.0);
}
