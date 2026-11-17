#version 150
in vec2 uv;out vec4 fragColor;
uniform float Age;uniform float Emission;
void main(){
 vec2 p=uv*2.0-1.0;float r=length(p);if(r>1.0)discard;
 float life=smoothstep(0.0,6.0,Age)*(1.0-smoothstep(96.0,114.0,Age));
 float angle=atan(p.y,p.x);float waves=0.8+0.2*sin(angle*5.0-r*18.0+Age*.32);
 float haze=exp(-r*r*4.0)*(1.0-smoothstep(.65,1.0,r));
 float core=exp(-r*r*38.0);float energy=(haze*waves*.8+core*2.5)*life;
 if(energy<.003)discard;
 vec3 rgb=vec3(1.0,.006,.018)*energy+vec3(.5,.14,.08)*core*life;
 fragColor=Emission>.5?vec4(rgb*2.8,gl_FragCoord.z):vec4(rgb,clamp(haze*life,0.0,1.0));
}
