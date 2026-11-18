#version 150
in vec2 gateCoord;
flat in float gateTicks;
flat in float launchTick;
flat in float openingOffset;
flat in float variation;
flat in vec2 reflectionAxis;
out vec4 fragColor;

uniform sampler2D Sampler0;
uniform float Time;
uniform float Opening;
uniform int SolidPass;
uniform int ChainGate;
uniform vec2 CloseWindow;
uniform vec3 TreasuryAmber;
uniform vec3 TreasuryLemon;
uniform vec3 TreasuryPearl;

const float TAU = 6.2831853;

// Filter a continuous strip by the pixel footprint. Thin glints lose energy in the distance.
float strip(float distanceToStrip, float halfWidth, float pixel) {
    float feather = max(pixel,halfWidth*.44);
    float coverage = 1.0-smoothstep(max(0.0,halfWidth-feather),halfWidth+feather,abs(distanceToStrip));
    return coverage*min(1.0,halfWidth/feather);
}

vec4 liquidMouth(vec2 uv) {
    bool chain = ChainGate != 0;
    float ticks = gateTicks+Time*20.0;
    float age = ticks+openingOffset;
    float shotAge = ticks-launchTick;
    float closure = chain ? Opening : 1.0-smoothstep(CloseWindow.x,CloseWindow.y,shotAge);
    if (age <= 0.0 || closure < .005) discard;
    float t = max(age,0.0)*.05;
    float seed = variation*TAU;
    float settle = smoothstep(0.0,16.0,age);
    float front = (chain ? 1.0 : .035+.965*smoothstep(0.0,13.0,age))*closure;
    float visibility = smoothstep(0.0,1.7,age)*smoothstep(0.0,.30,closure);
    // The mouth, every reflected ridge and the departing wave share this exact radius.
    float r = length(uv);
    float pixel = max(.001,length(vec2(dFdx(r),dFdy(r)))*.60);
    // The sheet stays in place. Only the leading liquid ridge travels across it.
    if (r > front+.23+pixel) discard;
    vec2 radial = uv/max(r,.002);
    float angle = atan(uv.y,uv.x+.00001);
    float edge = r-front;
    float edgeWidth = max(.018,pixel);
    float interior = 1.0-smoothstep(-edgeWidth,edgeWidth,edge);
    float amberMist = exp(-max(0.0,edge)*18.0)*(1.0-interior);

    mat2 basis = mat2(reflectionAxis.x,reflectionAxis.y,-reflectionAxis.y,reflectionAxis.x);
    // G is the foreshortening-corrected, local water reflection from Saral Shots' CC0 photograph:
    // https://commons.wikimedia.org/wiki/File:Circular_waves.jpg . R is the workbench's original noise.
    vec2 samples = texture(Sampler0,vec2(.5)+basis*uv*(.445+.010*sin(t*.9+seed))).rg-.5;
    float turn = seed*.35+t*.42;
    float density = chain ? 2.45 : 3.48;
    float cycles = r*(density+.13*r)-t*(chain ? .12 : .20) + .055*sin(t*.55+seed);
    float phase = TAU*cycles;
    float pixelCycle = max(.003,pixel*(density+.26*r));
    vec2 lightAxis = normalize(vec2(-.24+.56*sin(r*8.0+turn),-1.0)+reflectionAxis*.30);
    float side = dot(radial,lightAxis);
    float light = smoothstep(-.67,.90,side+.45*sin(1.7+angle*2.0+r*5.7+turn)+samples.y*.32);
    float skyArc = smoothstep(.35,.91,cos(angle+2.7*r+seed*.45-.48));
    light = max(light,.77*skyArc);

    // Each fold has a softly transmitting reverse slope and a directional specular face.
    // Its broad light side changes from gate to gate and from one wave to the next.
    float ridgeDistance = fract(cycles+.5)-.5;
    float narrowReflection = strip(ridgeDistance,.045,pixelCycle);
    float wideReflection = strip(ridgeDistance,chain ? .143 : .205,pixelCycle);
    float ridge = mix(narrowReflection,wideReflection,light);
    float gleam = ridge*(.28+.72*light);
    float shoulder = (.5+.5*cos(phase));
    float broadLight = shoulder*(.56+.44*shoulder)*(.47+.53*light);
    float counterLight = strip(ridgeDistance+.195,.024,pixelCycle)
                       * smoothstep(-.52,.49,-side+.44*sin(angle*3.0-r*6.0+seed));
    float waveStart = smoothstep(.06,.19,r);
    float waveEnd = 1.0-smoothstep(.87,1.035,r);
    float waves = waveStart*waveEnd;
    gleam *= waves;
    broadLight *= waves;
    counterLight *= waves;

    // A raised collar leads the first wave, then thins into a discontinuous amber meniscus.
    float waveHead = strip(edge+.026,mix(.055,.022,settle),pixel);
    float outerGlint = waveHead*mix(.65,.18+.52*light,settle);
    float contact = (1.0-smoothstep(.005,.19,r))*(.72+.15*cos(t*2.8+seed));
    contact += .34*strip(r-.10,.026,pixel)*smoothstep(-.2,.68,side);
    float departure = 0.0;
    if (!chain && shotAge > -4.0 && shotAge < CloseWindow.y) {
        float progress = smoothstep(-.35,CloseWindow.y*.58,shotAge);
        float waveRadius = shotAge < 0.0 ? mix(.27,.06,smoothstep(-4.0,0.0,shotAge)) : .06+.97*progress;
        float pulse = smoothstep(-4.0,-.55,shotAge)*(1.0-smoothstep(CloseWindow.y*.45,CloseWindow.y,shotAge));
        departure = strip(r-waveRadius,.047+.016*(1.0-progress),pixel)*pulse*(.53+.40*light);
    }

    float reflectance = clamp(gleam+.34*counterLight+.86*departure+.66*outerGlint,0.0,1.0);
    // Warm sand-gold remains in the troughs. Some background transmits through every weakly lit slope.
    float trough = (1.0-shoulder)*waves;
    vec3 honey = mix(TreasuryAmber,vec3(1.0,.84,.32),.50+.20*light+.20*samples.x);
    vec3 color = mix(honey,TreasuryLemon,clamp(.25+.64*broadLight+.49*outerGlint+.37*contact-.12*trough,0.0,1.0));
    color = mix(color,TreasuryPearl,clamp(reflectance*1.08+.74*contact,0.0,1.0));
    color = mix(color,TreasuryAmber,1.0-interior);
    float veil = .52+.12*light+.09*samples.x-.23*trough;
    float sheet = veil+.30*broadLight+.23*contact;
    float opacity = mix(sheet,1.0,max(reflectance,contact*.78));
    if (chain) {
        // The links emerge through a lightly tinted center. Only the reflected rim breathes while held.
        float root = smoothstep(.06,.32,r);
        color = mix(color,TreasuryAmber,.13+.11*(1.0-root));
        opacity *= mix(.36,1.0,root);
        opacity *= 1.0-.065*(.5+.5*cos(t*1.65+seed))*waves;
    }
    float alpha = visibility*(interior*opacity + .32*amberMist);
    alpha *= 1.0-smoothstep(.11,.21,max(0.0,edge));

    // Depth belongs only to the most reflective facets. The second pass composites the transmitting sheet.
    if (alpha < .014 || ((alpha >= .97) != (SolidPass != 0))) discard;
    return vec4(color,SolidPass != 0 ? 1.0 : alpha);
}

void main() {
    fragColor = liquidMouth(gateCoord);
}
