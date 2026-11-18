#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec4 vertexColor;
out vec2 texCoord;
out vec3 viewPosition;
out vec3 viewNormal;

void main() {
    vec4 eye = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * eye;
    viewPosition = eye.xyz;
    viewNormal = mat3(ModelViewMat) * (Color.rgb * 2.0 - 1.0);
    vertexColor = Color;
    texCoord = UV0;
}
