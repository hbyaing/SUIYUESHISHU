#version 150
uniform float GameTime;
in vec2 texCoord;
in vec3 viewNormal;
in vec3 viewPosition;
out vec4 fragColor;
const float TAU = 6.28318530718;
void main() {
    float t = GameTime * 2400.0;
    float facing = max(0.0, dot(normalize(viewNormal), normalize(-viewPosition)));
    float swirl = sin(TAU * (texCoord.x * 4.0 + texCoord.y * 3.0 - t)
                      + 1.4 * sin(TAU * (texCoord.y * 2.0 + t * 0.35)));
    float veins = pow(0.5 + 0.5 * swirl, 6.0);
    float hot = pow(facing, 2.2) * (0.78 + 0.22 * veins);
    vec3 color = mix(vec3(0.95, 0.012, 0.09), vec3(1.0, 0.65, 0.52), hot);
    color += vec3(0.05, 0.09, 0.04) * veins * facing;
    fragColor = vec4(color, 1.0);
}
