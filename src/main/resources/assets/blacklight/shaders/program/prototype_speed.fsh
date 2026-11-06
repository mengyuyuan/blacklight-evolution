#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;
uniform float Intensity;
uniform float BiasX;
uniform float BiasY;

in vec2 texCoord;
in vec2 oneTexel;
out vec4 fragColor;

const float PI = 3.14159265359;
const float SECTORS = 112.0;

float hash(float value) {
    return fract(sin(value * 91.3458 + 17.173) * 47453.5453);
}

void main() {
    vec2 aspect = vec2(InSize.x / max(InSize.y, 1.0), 1.0);
    vec2 focus = vec2(0.5 + BiasX, 0.5 + BiasY);
    vec2 delta = (texCoord - focus) * aspect;
    float radius = length(delta);
    vec2 direction = radius > 0.0001 ? delta / radius : vec2(0.0);

    float aberration = 0.0017 * Intensity * smoothstep(0.18, 0.72, radius);
    vec3 scene;
    scene.r = texture(DiffuseSampler, texCoord + direction * aberration).r;
    scene.g = texture(DiffuseSampler, texCoord).g;
    scene.b = texture(DiffuseSampler, texCoord - direction * aberration).b;

    vec3 radialBlur = scene;
    radialBlur += texture(DiffuseSampler, texCoord - direction * 0.0028 * Intensity).rgb;
    radialBlur += texture(DiffuseSampler, texCoord - direction * 0.0056 * Intensity).rgb;
    radialBlur /= 3.0;
    scene = mix(scene, radialBlur, 0.16 * Intensity);

    float angle01 = (atan(delta.y, delta.x) + PI) / (2.0 * PI);
    float sector = floor(angle01 * SECTORS);
    float localAngle = abs(fract(angle01 * SECTORS) - 0.5) * 2.0;
    float randomA = hash(sector);
    float randomB = hash(sector + floor(Time * 3.0) * 13.0);
    float streakActivity = smoothstep(0.57, 0.92, randomA);
    float angularLine = 1.0 - smoothstep(0.025, 0.13 + randomB * 0.07, localAngle);

    float travel = fract(Time * (2.1 + randomA * 2.8) + randomB * 3.7);
    float radialPosition = mix(0.16, 0.82, travel);
    float radialWidth = mix(0.025, 0.09, randomB);
    float radialSegment = 1.0 - smoothstep(radialWidth, radialWidth * 2.6,
            abs(radius - radialPosition));
    float edgeMask = smoothstep(0.15, 0.34, radius) * (1.0 - smoothstep(0.70, 0.90, radius));

    float luminance = dot(scene, vec3(0.2126, 0.7152, 0.0722));
    float contrast = mix(0.32, 1.0, smoothstep(0.04, 0.72, luminance));
    float streak = streakActivity * angularLine * radialSegment * edgeMask
            * contrast * Intensity * 0.92;
    vec3 streakColor = mix(vec3(0.76, 0.83, 0.85), vec3(0.58, 0.015, 0.025),
            step(0.82, randomB));
    scene = mix(scene, streakColor, clamp(streak, 0.0, 0.72));

    float vignette = smoothstep(0.48, 0.88, radius) * Intensity;
    scene *= 1.0 - vignette * 0.13;
    scene += vec3(0.085, 0.0, 0.006) * vignette * 0.22;
    fragColor = vec4(scene, 1.0);
}
