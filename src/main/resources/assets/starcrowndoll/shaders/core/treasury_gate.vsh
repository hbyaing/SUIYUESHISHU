#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 gateCoord;
flat out float gateTicks;
flat out float launchTick;
flat out float openingOffset;
flat out float variation;
flat out vec2 reflectionAxis;
void main() {
    gateCoord = UV0;
    gateTicks = round(Color.r * 255.0);
    launchTick = round(Color.g * 255.0);
    float opening = round(Color.b * 255.0);
    // 255 marks a refill. Its initial liquid ridge is visible at birth; departure uses the actual clock.
    openingOffset = opening == 255.0 ? 2.0 : -opening;
    variation = Color.a;
    float angle = variation * 6.2831853;
    reflectionAxis = vec2(cos(angle),sin(angle));
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
