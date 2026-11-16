#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float uOrthographic;
out vec3 vLocal;
out vec2 vUv;
out vec3 vNormal;
out vec3 vView;
void main(){
 vLocal=vec3(-Position.x,Position.z,Position.y);
 vUv=UV0;
 vec4 mv=ModelViewMat*vec4(Position,1.0);
 vNormal=normalize(transpose(inverse(mat3(ModelViewMat)))*Normal);
 vView=mix(-mv.xyz,vec3(0.0,0.0,1.0),uOrthographic);
 gl_Position=ProjMat*mv;
}
