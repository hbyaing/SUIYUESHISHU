#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform mat4 InverseProjectionMat;
uniform vec2 NativeSize;
uniform vec2 VolumeSize;
out vec4 fragColor;
float viewDepth(vec2 uv,float depth){
 vec4 p=InverseProjectionMat*vec4(uv*2.0-1.0,depth*2.0-1.0,1.0);
 return abs(p.z/p.w);
}
float slope(float left,float center,float right){
 float a=center-left,b=right-center;
 return a*b>0.0?sign(a)*min(abs(a),abs(b)):0.0;
}
void main(){
 vec2 uv=gl_FragCoord.xy/NativeSize;
 float d=texture(Sampler1,uv).r;
 bool sky=d>=.999999;
 float z=sky?0.0:viewDepth(uv,d);
 // Window depth is affine across one projected plane. Extrapolate its slope
 // instead of rejecting alternating rows on a receding floor. The minimum
 // one-sided slope stays on the current surface at foreground silhouettes.
 vec2 pixel=1.0/NativeSize;
 vec2 gradient=vec2(slope(texture(Sampler1,uv-vec2(pixel.x,0)).r,d,texture(Sampler1,uv+vec2(pixel.x,0)).r),
                    slope(texture(Sampler1,uv-vec2(0,pixel.y)).r,d,texture(Sampler1,uv+vec2(0,pixel.y)).r));
 vec2 p=uv*VolumeSize-.5,base=floor(p),f=fract(p);
 vec4 sum=vec4(0.0);float weight=0.0;
 for(int y=0;y<2;y++)for(int x=0;x<2;x++){
  vec2 tap=clamp((base+vec2(x,y)+.5)/VolumeSize,.5/VolumeSize,1.0-.5/VolumeSize);
  // A reduced pixel center can lie between native depth texels. Compare at
  // the texel actually fetched, rather than a half-pixel displaced plane point.
  ivec2 depthPixel=clamp(ivec2(tap*NativeSize),ivec2(0),ivec2(NativeSize)-1);
  vec2 depthUV=(vec2(depthPixel)+.5)/NativeSize;
  float td=texelFetch(Sampler1,depthPixel,0).r;
  bool ts=td>=.999999;
  if(sky!=ts)continue;
  float expected=clamp(d+dot((depthUV-uv)*NativeSize,gradient),0.0,.999999);
  float edge=sky?1.0:1.0-smoothstep(max(.035,z*.002),max(.07,z*.004),abs(viewDepth(depthUV,td)-viewDepth(depthUV,expected)));
  float w=(x==0?1.0-f.x:f.x)*(y==0?1.0-f.y:f.y)*edge;
  sum+=texture(Sampler0,tap)*w;weight+=w;
 }
 // Never smear the volume across a rejected foreground silhouette.
 if(weight<.00001)discard;
 sum/=weight;if(sum.a<.004)discard;
 fragColor=vec4(sum.rgb/max(sum.a,.00001),sum.a);
}
