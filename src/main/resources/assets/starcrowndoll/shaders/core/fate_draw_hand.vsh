#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 uv;
out vec4 tintColor;
out vec3 surface;
void main(){
    gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);
    uv=UV0;tintColor=Color;surface=mat3(ModelViewMat)*Normal;
}
