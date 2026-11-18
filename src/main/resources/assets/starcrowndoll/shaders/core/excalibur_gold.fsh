#version 150
#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform mat4 ProjMat;
uniform vec4 GuardUV;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec4 normal;
out vec4 fragColor;

void main() {
    vec4 surface = texture(Sampler0, texCoord0);
    if (surface.a < 0.1) discard;

    if (all(greaterThan(texCoord0, GuardUV.xy)) && all(lessThan(texCoord0, GuardUV.zw))) {
        vec2 uv = (texCoord0 - GuardUV.xy) / (GuardUV.zw - GuardUV.xy);
        // Recover the transverse normal from the vanilla entity vertex shader in both GUI and held views.
        vec2 facing = clamp(normal.xy / vec2(ProjMat[0][0], ProjMat[1][1]), -1.0, 1.0);
        float along = uv.x + 0.10 * (uv.y - 0.5) + 0.36 * facing.x - 0.15 * facing.y;
        float reflection = 1.0 - smoothstep(0.025, 0.24, abs(along - 0.48));
        float gold = smoothstep(0.04, 0.24, surface.r - surface.b);
        surface.rgb = mix(surface.rgb, vec3(1.0, 0.91, 0.70), gold * reflection * 0.38);
    }

    vec4 lit = surface * vertexColor * ColorModulator;
    lit.rgb = mix(overlayColor.rgb, lit.rgb, overlayColor.a);
    fragColor = linear_fog(lit * lightMapColor, vertexDistance, FogStart, FogEnd, FogColor);
}
