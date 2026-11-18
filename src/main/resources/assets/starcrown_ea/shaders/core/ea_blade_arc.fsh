#version 150

in float across;
in float opacity;
out vec4 fragColor;

void main() {
    float d = abs(across);
    float aa = clamp(fwidth(across) * 0.5, 0.012, 0.48);
    float core = 1.0 - smoothstep(0.18 - aa, 0.23 + aa, d);
    float red = 1.0 - smoothstep(0.16, 0.57, d);
    float skirt = 1.0 - smoothstep(0.30, 1.0, d);
    float alpha = opacity * (0.18 * skirt + 0.48 * red + 0.34 * core);
    if (alpha < 0.004) discard;
    fragColor = vec4(1.0, 0.014 + 0.54 * core, 0.13 + 0.49 * core, alpha);
}
