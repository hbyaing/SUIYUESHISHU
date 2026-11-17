#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 Direction;
in vec2 uv;
out vec4 fragColor;
void main() {
    float scene=texture(Sampler1,uv).r;
    vec3 color=vec3(0.0);
    float nearest=1.0;
    for(int i=-4;i<=4;i++) {
        int index=abs(i);
        float weight=index==0?0.227027:index==1?0.194595:index==2?0.121622:index==3?0.054054:0.016216;
        vec4 sampleColor=texture(Sampler0,clamp(uv+Direction*float(i),vec2(0.0),vec2(1.0)));
        // Source depth remains float32 through both separable passes. Do not smear a rear halo across a foreground body/wall.
        if(sampleColor.a<=scene+0.00001 && max(sampleColor.r,max(sampleColor.g,sampleColor.b))>0.00001) {
            color+=sampleColor.rgb*weight;
            nearest=min(nearest,sampleColor.a);
        }
    }
    fragColor=vec4(color,nearest);
}
