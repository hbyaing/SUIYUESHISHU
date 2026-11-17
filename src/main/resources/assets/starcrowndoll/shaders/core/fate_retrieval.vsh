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
    if(Mode>4.5){p.xy=p.xy/vec2(Aspect,1.0)+MaskCenter;}
    else if(Mode>2.5){
        float seed=hash(UV0),t=clamp((Age-140.0-seed*2.0)/16.0,0.0,1.0);
        vec2 offset=p.xy-UV0;
        float angle=(seed-.5)*t*2.5,c=cos(angle),s=sin(angle);
        offset=mat2(c,-s,s,c)*offset;
        vec2 outward=normalize(UV0+vec2(.0001));
        p.xy=UV0+offset+outward*t*t*(5.0+seed*3.0);
        if(Mode<3.5)p.xy=p.xy/vec2(Aspect,1.0)+MaskCenter;
    }
    gl_Position=ProjMat*ModelViewMat*vec4(p,1.0);
}
