#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec3 body;
out vec3 viewNormal;
out vec2 texCoord;
flat out vec4 binding;
flat out float contactTurn;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    body = (vec3(float(UV1.x), float(UV1.y), float(UV2.x)) - 16384.0) / 2048.0;
    viewNormal = mat3(ModelViewMat) * Normal;
    texCoord = UV0;
    binding = Color;
    contactTurn = (float(UV2.y) - 16384.0) / 2048.0;
}
