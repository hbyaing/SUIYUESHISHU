#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float Time;
uniform float GlowPass;
in vec4 vertexColor;
in vec2 texCoord;
in vec3 viewPosition;
in vec3 viewNormal;
out vec4 fragColor;

void main() {
    float distanceAlong = texCoord.x;
    float turn = texCoord.y;
    float clock = Time;
    const float tau = 6.2831853;
    float shear = 0.12 * sin(turn*tau*3.0+distanceAlong*0.35-clock*5.0);
    vec3 broad = texture(Sampler0, vec2(fract(distanceAlong * 0.12 - clock * 1.75 + shear),turn)).rgb;
    vec3 detail = texture(Sampler0,vec2(fract(distanceAlong * 0.23 - clock * 2.8),fract(turn * 2.0 + 0.23+shear))).rgb;
    vec3 normal = normalize(viewNormal);
    vec3 view = -viewPosition / max(length(viewPosition), 0.00001);
    float facing = clamp(abs(dot(normal, view)), 0.0, 1.0);
    float lower = 0.5 - 0.5 * sin(tau * turn);
    float lobe = smoothstep(0.22,0.78,0.49*broad.r+0.25*detail.r+0.26*broad.b);
    float ribbon = pow(0.5+0.5*cos(tau*turn*4.0-distanceAlong*0.8+clock*12.0),6.0);
    float rim = pow(1.0-facing,2.2);
    float alpha = vertexColor.a * ColorModulator.a;
    if (GlowPass < 0.5) {
        float corona = clamp(rim*1.14 + lower*0.63*(0.30+0.70*lobe),0.0,1.0);
        vec3 gold = mix(vec3(1.0,0.36,0.065),vec3(1.0,0.99,0.75),lobe);
        vec3 core = mix(gold,vec3(1.0,1.0,0.93),smoothstep(0.30,0.8,facing)*(0.19+0.81*lobe));
        vec3 rimColor = mix(vec3(0.25,0.008,0.015),vec3(1.0,0.061,0.023),0.43+0.44*ribbon);
        fragColor = vec4(mix(core,rimColor,corona) * ColorModulator.rgb, alpha);
    } else {
        vec3 red = mix(vec3(1.0,0.018,0.017),vec3(1.0,0.27,0.04),lobe);
        fragColor = vec4(red,alpha * (rim*0.19+ribbon*(0.04+detail.g*0.11)));
    }
    if (fragColor.a < 0.002) discard;
}
