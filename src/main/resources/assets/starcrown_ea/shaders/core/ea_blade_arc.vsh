#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 ScreenSize;
out float across;
out float opacity;

void main() {
    vec4 center = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // Normal carries the centerline tangent. Different hand matrices and both camera perspectives
    // yield a ribbon of the same projected shape, even when viewed nearly along the sword axis.
    vec4 tangent = ProjMat * ModelViewMat * vec4(Normal, 0.0);
    vec2 direction = (tangent.xy * center.w - center.xy * tangent.w) * ScreenSize;
    vec2 side = vec2(-direction.y, direction.x) / max(length(direction), 0.00001);
    across = sign(UV0.x);
    float pixels = abs(UV0.x * ProjMat[1][1]) * ScreenSize.y / max(2.0 * abs(center.w), 0.00001);
    float footprint = max(pixels, 1.25);
    opacity = Color.a * min(1.0, pixels / footprint);
    center.xy += across * side * (2.0 * footprint / ScreenSize) * center.w;
    gl_Position = center;
}
