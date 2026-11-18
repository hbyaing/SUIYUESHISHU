#version 150
uniform sampler2D Sampler0;
uniform vec3 Tint;
in vec2 uv;
in vec4 tintColor;
in vec3 surface;
out vec4 fragColor;
void main(){
    vec4 skin=texture(Sampler0,uv)*tintColor;
    if(skin.a<.1)discard;
    vec3 n=normalize(surface);
    float lum=dot(skin.rgb,vec3(.299,.587,.114));
    float light=clamp(.52+.32*dot(n,normalize(vec3(-.7,.8,.4)))+.32*(lum-.5),0.,1.);
    vec3 warm=mix(vec3(.77,.39,.095),vec3(1.,.965,.46),smoothstep(.08,.82,light));
    vec3 cold=mix(vec3(.21,.14,.42),vec3(.57,.56,.82),smoothstep(.12,.50,light));
    cold=mix(cold,vec3(.99,.985,.86),smoothstep(.48,.66,light));
    vec3 inverse=mix(vec3(.58,.16,.61),vec3(.0,1.,.87),smoothstep(.40,.56,light));
    fragColor=vec4(mix(mix(mix(skin.rgb,warm,Tint.x),cold,Tint.y),inverse,Tint.z),skin.a);
}
