#version 150

uniform sampler2D Sampler0;
in vec2 runeUV;
in float charge;
out vec4 fragColor;

const vec2 taps[8] = vec2[8](
    vec2(1, 0), vec2(-1, 0), vec2(0, 1), vec2(0, -1),
    vec2(0.707, 0.707), vec2(-0.707, 0.707), vec2(0.707, -0.707), vec2(-0.707, -0.707)
);

void main() {
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    // The original emissive UVs stay on each counter-rotating mesh. A small dilation widens
    // only those glyphs, leaving their black negative space and the cylinder silhouette readable.
    float mask = texture(Sampler0, runeUV).r;
    for (int i = 0; i < 8; i++) {
        vec2 uv = clamp(runeUV + taps[i] * texel * (3.1 * charge), 0.5 * texel, 1.0 - 0.5 * texel);
        mask = max(mask, texture(Sampler0, uv).r);
    }
    mask = smoothstep(0.08, 0.84, mask);
    vec3 carbon = vec3(0.019, 0.011, 0.030);
    vec3 vermilion = mix(vec3(1.0, 0.006, 0.007), vec3(1.0, 0.018, 0.105), charge);
    fragColor = vec4(mix(carbon, vermilion, mask), mix(charge * 0.31, 1.0, mask));
}
