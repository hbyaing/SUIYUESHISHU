#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 light = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    // Open fringes add light. The dense ribbon covers the blue sky with its authored gold or ivory.
    float coverage = smoothstep(0.04, 0.82, light.a);
    float emission = coverage + (1.0 - coverage) * light.a;
    float fog = linear_fog_fade(vertexDistance, FogStart, FogEnd);
    fragColor = vec4(light.rgb * emission, coverage) * fog;
}
