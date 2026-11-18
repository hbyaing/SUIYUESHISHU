#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 texCoord;
out vec4 parameters;
void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    if (Color.b > 0.9) {
        view.xy += UV0;
        texCoord = sign(UV0);
    }
    gl_Position = ProjMat * view;
    parameters = Color;
}
