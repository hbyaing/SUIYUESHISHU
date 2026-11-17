#version 150
uniform sampler2D Sampler0;
in vec2 uv;
out vec4 fragColor;
void main() {
    vec3 color=texture(Sampler0,uv).rgb;
    float light=max(color.r,max(color.g,color.b));
    // Preserve gold shading. Only the actual original mesh emits, never background/player bright pixels.
    vec3 emission=color*smoothstep(0.22,0.85,light)*0.95;
    fragColor=vec4(emission,gl_FragCoord.z);
}
