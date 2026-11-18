#version 150

uniform sampler2D ScenePatch;
uniform vec2 BladeBase;
uniform vec2 BladeTip;
uniform vec2 CropOrigin;
uniform vec2 PatchSize;
uniform vec2 CopiedSize;
uniform float BladeRadius;
uniform float Time;
uniform float PixelScale;

out vec4 fragColor;

void main() {
    vec2 blade = BladeTip - BladeBase;
    float bladeLength = length(blade);
    if (bladeLength < 2.0 || BladeRadius < 0.5) discard;

    vec2 along = blade / bladeLength;
    vec2 across = vec2(-along.y, along.x);
    vec2 fromBase = gl_FragCoord.xy - BladeBase;
    float t = dot(fromBase, along) / bladeLength;
    if (t <= 0.0 || t >= 1.0) discard;

    float radius = BladeRadius * mix(1.0, 0.74, t) * (1.0 - smoothstep(0.82, 1.0, t));
    float x = dot(fromBase, across) / max(radius, 0.01);
    float radial = abs(x);
    if (radial >= 1.0 || radial <= 0.12) discard;
    float edge = smoothstep(0.12, 0.40, radial) * (1.0 - smoothstep(0.89, 1.0, radial));
    float ends = smoothstep(0.0, 0.07, t) * (1.0 - smoothstep(0.94, 1.0, t));

    // Project winding strands onto the screen-facing cylinder. Their back halves are much fainter.
    float weight = 0.0;
    vec2 slope = vec2(0.0);
    for (int i = 0; i < 3; ++i) {
        float strand = float(i);
        float phase = t * (11.7 + 0.45 * strand) - Time * 0.095 + strand * 2.11
                    + 0.20 * sin(t * 7.6 + strand - Time * 0.027);
        float depth = cos(phase);
        float center = 0.82 * sin(phase);
        float halfWidth = 0.15 + 0.09 * (0.5 + 0.5 * depth);
        float d = (x - center) / halfWidth;
        float profile = 1.0 - smoothstep(0.05, 1.0, abs(d));
        float breaks = smoothstep(-0.71, 0.27, sin(t * 17.8 - Time * 0.044 + strand * 3.5));
        float influence = profile * mix(0.24, 1.0, smoothstep(-0.4, 0.85, depth)) * breaks;
        weight += influence;
        // A change in optical slope bends a line differently on the two shoulders of each strand.
        slope += influence * vec2(sign(x) * (2.18 + 0.48 * d), 1.09 * cos(phase * 1.35 + t * 4.0));
    }

    float coverage = clamp(weight, 0.0, 1.0) * edge * ends;
    if (coverage < 0.025) discard;
    vec2 offset = (across * slope.x + along * slope.y) * (edge * ends * PixelScale / max(1.0, weight));
    vec2 p = gl_FragCoord.xy - CropOrigin;
    vec2 displaced = clamp(p + offset, vec2(0.5), CopiedSize - vec2(0.5));
    vec4 original = texelFetch(ScenePatch, ivec2(clamp(floor(p), vec2(0.0), CopiedSize - 1.0)), 0);
    vec3 refracted = texture(ScenePatch, displaced / PatchSize).rgb;
    fragColor = vec4(mix(original.rgb, refracted, smoothstep(0.025, 0.52, coverage)), original.a);
}
