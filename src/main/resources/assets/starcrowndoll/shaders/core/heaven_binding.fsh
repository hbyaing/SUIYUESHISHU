#version 150

uniform sampler2D Sampler0;
uniform float GameTime;
in vec3 body;
in vec3 viewNormal;
in vec2 texCoord;
flat in vec4 binding;  // Unfurl, single lock pulse, contact height, release opacity.
flat in float contactTurn;
out vec4 fragColor;

const float TAU = 6.2831853;

// x is the mask, y the rounded bevel, z the reflected lip, w the distance to the belt's centreline.
vec4 links(float turn, float height, float hand, float offset) {
    float band = fract(1.05 * height - hand * turn + offset + 0.5) - 0.5;
    float index = 8.0 * turn + hand * 0.50 * height + offset;
    float along = fract(index + 0.5) - 0.5;
    float upright = mod(floor(index + 0.5), 2.0);
    float across = band * mix(1.91, 3.15, upright);
    vec2 q = vec2(sign(along) * max(abs(along) - 0.225, 0.0), across);
    float r = length(q);
    float ring = r - 0.164;
    float thick = mix(0.064, 0.074, upright);
    float aa = clamp(fwidth(ring) * 0.75, 0.005, 0.060);
    float mask = 1.0 - smoothstep(thick - aa, thick + aa, abs(ring));
    float tube = clamp(1.0 - abs(ring) / thick, 0.0, 1.0);
    float litEdge = dot(q / max(r, 0.001), vec2(-0.40, 0.91)) * ring / thick;
    float bevel = clamp(0.16 + 0.48 * sqrt(tube) + 0.44 * litEdge, 0.0, 1.0);
    float lip = smoothstep(0.58, 0.92, bevel);
    return vec4(mask, bevel, lip, abs(band));
}

void main() {
    vec4 skin = texture(Sampler0, texCoord);
    if (skin.a < 0.10) discard;

    float turn = atan(body.z, body.x + 0.00001) / TAU;
    float spread = binding.r;
    float pulse = binding.g;
    float distanceAround = abs(fract(turn - contactTurn + 0.5) - 0.5);
    float distanceFromGrip = length(vec2(1.06 * distanceAround, 0.88 * (body.y - binding.b)));
    float reach = mix(-0.065, 1.24, spread);
    float reveal = 1.0 - smoothstep(reach - 0.09, reach + 0.07, distanceFromGrip);
    reveal = mix(reveal, 1.0, smoothstep(0.89, 1.0, spread));
    if (reveal * binding.a < 0.003) discard;

    vec4 a = links(turn, body.y, 1.0, 0.18);
    vec4 b = links(turn, body.y, -1.0, 0.64);
    float swap = step(0.0, sin(TAU * (1.05 * body.y + 0.13)));
    vec4 upper = mix(a, b, swap);
    vec4 lower = mix(b, a, swap);
    float chain = max(upper.x, lower.x);
    float bevel = mix(lower.y, upper.y, upper.x);
    float lip = mix(lower.z, upper.z, upper.x);
    // A raised diamond pin joins each pair of crossing belts.
    float pinDistance = a.w + b.w;
    float pinAA = clamp(fwidth(pinDistance), 0.002, 0.018);
    float stud = 1.0 - smoothstep(0.043 - pinAA, 0.043 + pinAA, pinDistance);
    float facet = 1.0 - smoothstep(0.003, 0.045, pinDistance);
    chain = max(chain, stud);
    bevel = mix(bevel, 0.38 + 0.58 * facet, stud);
    lip = max(lip, stud * facet);

    float luma = dot(skin.rgb, vec3(0.24, 0.65, 0.11));
    float light = clamp(dot(viewNormal / max(length(viewNormal), 0.001), vec3(-0.36, 0.70, 0.58)), -1.0, 1.0);
    float edge = pow(max(0.0, cos(TAU * (turn - body.y * 0.55 - GameTime * 96.0))), 10.0);
    vec3 bronze = vec3(0.37, 0.145, 0.033);
    vec3 amber = vec3(0.97, 0.56, 0.105);
    vec3 lemon = vec3(1.0, 0.89, 0.48);
    vec3 pearl = vec3(1.0, 0.975, 0.79);
    vec3 metal = mix(bronze, amber, smoothstep(0.06, 0.71, bevel + 0.07 * light));
    metal = mix(metal, lemon, lip * (0.70 + 0.20 * edge));
    float contactRim = (1.0 - smoothstep(0.01, 0.12, abs(distanceFromGrip - reach))) * (1.0 - spread);
    metal = mix(metal, pearl, clamp(0.59 * pulse * (0.30 + 0.70 * max(lip, stud)) + 0.42 * contactRim, 0.0, 0.85));
    vec3 glaze = mix(vec3(0.51,0.28,0.07), vec3(0.87,0.61,0.24), luma);
    float alpha = mix(0.09 + 0.05 * luma, 0.93, chain);
    fragColor = vec4(mix(glaze, metal, chain), skin.a * binding.a * reveal * alpha);
}
