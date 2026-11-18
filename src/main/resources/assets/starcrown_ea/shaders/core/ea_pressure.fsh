#version 150
uniform float GameTime;
in vec2 texCoord;
in vec4 parameters;
out vec4 fragColor;
const float TAU = 6.28318530718;
void main() {
    if (parameters.g > 0.5) {
        vec2 p = texCoord * 2.0 - 1.0;
        float radius = length(p);
        if (radius >= 0.99) discard;
        float falloff = 1.0 - smoothstep(0.14, 0.99, radius);
        float arc = sin(atan(p.y, p.x) * 5.0 - radius * 16.0 + TAU * GameTime * 2700.0);
        float pulse = 0.84 + 0.16 * sin(TAU * GameTime * 1800.0);
        float hot = exp(-radius * radius * 15.0);
        fragColor = vec4(mix(vec3(1.0, 0.014, 0.17), vec3(1.0, 0.58, 0.69), hot),
                         parameters.a * falloff * pulse * (0.55 + 0.45 * hot + 0.18 * arc));
        return;
    }
    // Wind quads wind inward. Draw their far side so the solid blade masks the wind over its runes.
    if (!gl_FrontFacing) discard;
    float u = texCoord.x;
    float v = texCoord.y;
    float phase = TAU * (3.0*u - 1.50*v + parameters.r + 0.10*sin(TAU*(4.0*u + 1.1*v)));
    phase += 0.27*sin(TAU*(u*7.0-v*3.5+GameTime*1800.0));
    float ridge = pow(0.5 + 0.5 * cos(phase), 8.5);
    float haze = pow(0.5 + 0.5 * cos(phase + 0.8), 2.0);
    float filament = 0.58 + 0.42 * sin(TAU * (7.0*v + 2.0*u - GameTime*1600.0));
    float ends = smoothstep(0.0, 0.17, v) * (1.0 - smoothstep(0.83, 1.0, v));
    float sparks = pow(0.5 + 0.5 * sin(TAU*(v*13.0+u*7.0-GameTime*2200.0)),12.0);
    fragColor = vec4(1.0, 0.018 + ridge * 0.10 + sparks*0.24, 0.14 + ridge * 0.15,
                     parameters.a * ends * filament * (0.71 * ridge + 0.13 * haze + 0.16*ridge*sparks));
}
