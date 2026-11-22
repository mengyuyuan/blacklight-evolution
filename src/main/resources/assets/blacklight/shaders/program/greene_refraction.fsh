#version 150
uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 InSize;
uniform float Ax, Ay, Az, Bx, By, Bz, Radius, Strength, Age;
uniform float ProjX, ProjY, OffsetX, OffsetY, DepthA, DepthB;
in vec2 texCoord;
out vec4 fragColor;

float sceneDepth(vec2 uv) {
    return DepthB / min(-0.000001, texture(DepthSampler, uv).r * 2.0 - 1.0 + DepthA);
}

void main() {
    vec4 original = texture(DiffuseSampler, texCoord);
    if (Strength < 0.001) { fragColor = original; return; }
    vec2 projection = vec2(ProjX, ProjY);
    vec3 ray = normalize(vec3((texCoord * 2.0 - 1.0 + vec2(OffsetX, OffsetY)) / projection, -1.0));
    float visible = sceneDepth(texCoord) / -ray.z;
    vec3 start = vec3(Ax, Ay, Az);
    vec3 segment = vec3(Bx, By, Bz) - start;
    // Closest points between this pixel's *visible* ray and the finite discharge.
    vec3 transverse = segment - ray * dot(segment, ray);
    float u = clamp(-dot(transverse, start) / max(dot(transverse, transverse), 0.000001), 0.0, 1.0);
    float t = clamp(dot(start + u * segment, ray), 0.0, visible);
    u = clamp(dot(t * ray - start, segment) / max(dot(segment, segment), 0.000001), 0.0, 1.0);
    vec3 centre = start + u * segment;
    t = clamp(dot(centre, ray), 0.0, visible);
    vec3 radial = t * ray - centre;
    float rho = length(radial) / Radius;
    if (rho >= 1.0 || centre.z > Radius ) {
        fragColor = original;
        return;
    }

    float trail = u * length(segment);
    float angle = trail * 1.44 - Age * 0.82 + rho * 3.0;
    float wave = sin(angle + 0.55 * sin(trail * 0.71 + Age * 0.27));
    float edge = 1.0 - smoothstep(0.68, 1.0, rho);
    vec2 normal = radial.xy / Radius;
    vec2 curl = vec2(-normal.y, normal.x);
    vec2 lens = normal * (-1.35 + 0.67 * wave) + curl * (0.35 * sin(angle * 0.78));
    // The mouth gathers its surroundings. Travelling fronts alternately compress and stretch the scene.
    vec2 pixelRadius = 0.5 * projection * Radius / max(1.0, -centre.z);
    vec2 shift = clamp(lens * pixelRadius * edge * Strength, vec2(-0.095), vec2(0.095));
    vec2 guard = 0.5 / InSize;
    vec2 uv = clamp(texCoord + shift, guard, 1.0 - guard);
    // A foreground pillar must not bleed into a ray that passes behind it.
    if (sceneDepth(uv) < -centre.z - Radius) uv = texCoord;
    vec2 dispersion = (uv - texCoord) * (0.032 + 0.019 * wave);
    vec3 colour = texture(DiffuseSampler, uv).rgb;
    colour.r = texture(DiffuseSampler, clamp(uv + dispersion, guard, 1.0 - guard)).r;
    colour.b = texture(DiffuseSampler, clamp(uv - dispersion, guard, 1.0 - guard)).b;
    // A bowed compression front travels away from the mouth. The dark crest
    // transmits the sampled scene, while the trough between waves stays clear.
    float phase = trail * 0.93 - Age * 0.75 + 3.9 * rho * rho;
    float crest = 0.5 + 0.5 * cos(phase);
    float shadow = smoothstep(0.54, 0.95, crest);
    float shoulder = pow(max(0.0, cos(phase - 1.22)), 10.0);
    float density = (1.0 - smoothstep(0.67, 1.0, rho)) * Strength;
    colour *= 1.0 - density * (0.08 + 0.80 * shadow);
    colour += density * shoulder * (0.027 + 0.095 * colour);
    fragColor = vec4(colour, original.a);
}
