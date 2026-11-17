#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform float Intensity;
in vec2 uv;
out vec4 fragColor;
void main() {
    vec4 base=texture(Sampler0,uv);
    vec3 glow=texture(Sampler1,uv).rgb;
    fragColor=vec4(base.rgb+glow*Intensity,base.a);
}
