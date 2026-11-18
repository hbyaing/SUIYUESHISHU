#version 150

in vec4 vertexColor;
in vec2 texCoord;
uniform vec4 ColorModulator;
out vec4 fragColor;

void main() {
    fragColor = vertexColor * ColorModulator;
    float across = abs(texCoord.y * 2.0 - 1.0);
    fragColor.a *= 1.0 - smoothstep(0.18, 1.0, across);
    if (fragColor.a <= 0.001) discard;
}
