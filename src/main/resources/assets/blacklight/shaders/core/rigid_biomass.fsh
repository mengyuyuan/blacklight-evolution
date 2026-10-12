#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in float wetness;
in vec2 texCoord;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec3 viewPosition;
in vec3 viewNormal;
in vec3 viewLight0;
in vec3 viewLight1;

out vec4 fragColor;

vec3 unitVector(vec3 value) {
    return value * inversesqrt(max(dot(value, value), 0.000001));
}

float wetHighlight(vec3 normal, vec3 view, vec3 light, float gloss) {
    vec3 direction = unitVector(light);
    float incident = max(dot(normal, direction), 0.0);
    vec3 halfVector = unitVector(view + direction);
    return pow(max(dot(normal, halfVector), 0.0), gloss) * incident;
}

void main() {
    // Authored claw cuboids use the existing project texture and box UVs. The
    // flexible strands use white texels with their authored vertex RGB.
    vec4 color = texture(Sampler0, texCoord) * vertexColor;
    color *= ColorModulator;
    if (color.a < 0.003) {
        discard;
    }

    float wet = clamp(wetness, 0.0, 1.0);
    vec3 normal = unitVector(viewNormal);
    if (!gl_FrontFacing) {
        normal = -normal;
    }
    vec3 view = unitVector(-viewPosition);
    float facing = clamp(dot(normal, view), 0.0, 1.0);
    float gloss = mix(38.0, 88.0, wet);
    float highlight = wetHighlight(normal, view, viewLight0, gloss)
                    + 0.45 * wetHighlight(normal, view, viewLight1, gloss);
    // A tiny graphite reflection at grazing angles plus a narrow neutral wet
    // glint. Both still receive lightmap, overlay and fog below; no emissive rim.
    float fresnel = pow(1.0 - facing, 5.0);
    float reflection = wet * (0.074 * highlight + 0.024 * fresnel);
    color.rgb += vec3(0.82, 0.85, 0.90) * reflection * ColorModulator.rgb;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
