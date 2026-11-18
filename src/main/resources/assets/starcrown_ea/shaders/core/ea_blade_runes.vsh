#version 150

in vec3 Position;
in vec2 UV0;
in ivec2 UV2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 runeUV;
out float charge;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    runeUV = UV0;
    charge = clamp(float(UV2.x) / 240.0, 0.0, 1.0);
}
