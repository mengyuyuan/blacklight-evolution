#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Pulse;
uniform float Heavy;
uniform float Side;
uniform float Time;

in vec2 texCoord;
in vec2 oneTexel;
out vec4 fragColor;

void main() {
    vec2 centre = vec2(0.5 + 0.012 * Side * Pulse, 0.5);
    vec2 delta = texCoord - centre;
    float radius = length(delta * vec2(InSize.x / max(InSize.y, 1.0), 1.0));
    float edge = smoothstep(0.10, 0.78, radius);

    // Colour split grows toward the frame edge and leaves the target readable.
    float split = 0.0065 * Pulse * edge;
    vec3 colour;
    colour.r = texture(DiffuseSampler, centre + delta * (1.0 + split)).r;
    colour.g = texture(DiffuseSampler, texCoord).g;
    colour.b = texture(DiffuseSampler, centre + delta * (1.0 - split)).b;

    // Short zoom blur on heavy blows only.
    vec3 blurred = vec3(0.0);
    float weight = 0.0;
    float reach = 0.028 * Pulse * Heavy;
    for (int i = 0; i < 7; i++) {
        float t = float(i) / 6.0;
        float k = 1.0 - t * 0.65;
        blurred += texture(DiffuseSampler, centre + delta * (1.0 - reach * t * edge)).rgb * k;
        weight += k;
    }
    blurred /= weight;
    colour = mix(colour, blurred, clamp(Pulse * Heavy * 0.9 * edge, 0.0, 0.78));

    float luminance = dot(colour, vec3(0.2126, 0.7152, 0.0722));
    colour = mix(colour, vec3(luminance), 0.32 * Pulse * edge);
    colour = (colour - 0.5) * (1.0 + 0.16 * Pulse) + 0.5;
    colour += vec3(1.0, 0.92, 0.86) * 0.055 * Pulse * Pulse * (1.0 - edge);

    float vignette = smoothstep(0.32, 0.96, radius) * min(Pulse, 1.0);
    colour = mix(colour, colour * vec3(0.52, 0.07, 0.07) + vec3(0.085, 0.0, 0.01), vignette * 0.58);

    float grain = fract(sin(dot(texCoord * InSize + Time * 61.0, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
    colour += grain * 0.035 * Pulse * edge;
    fragColor = vec4(clamp(colour, 0.0, 1.0), 1.0);
}
