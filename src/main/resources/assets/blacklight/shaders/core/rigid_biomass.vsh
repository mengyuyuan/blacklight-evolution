#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 WorldPose;
uniform mat3 WorldNormal;
uniform vec2 InstanceLight;
uniform float InstanceAlpha;
uniform float CurveMode;
uniform vec3 CurveB;
uniform vec3 CurveC;
uniform vec3 CurveReference;
uniform vec4 CurveMetrics;
uniform vec2 CurveTwist;
uniform int FogShape;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out float wetness;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec3 viewPosition;
out vec3 viewNormal;
out vec3 viewLight0;
out vec3 viewLight1;

void main() {
    // Match the dynamic consumer's camera-relative position and inverse-scale
    // normal before the global camera matrix. Cylindrical fog stays world-up.
    vec3 localPosition = Position;
    vec3 localNormal = Normal;
    if (CurveMode > 0.5) {
        float t = clamp((Position.y - CurveMetrics.x) / CurveMetrics.y, 0.0, 1.0);
        float one = 1.0 - t;
        vec3 tangent = 2.0 * (CurveB * one + (CurveC - CurveB) * t);
        tangent /= max(length(tangent), 0.00001);
        // A single strand reference is projected onto every tangent. This
        // preserves the repaired frame when a bent strand changes direction.
        vec3 right = CurveReference - dot(CurveReference, tangent) * tangent;
        right /= max(length(right), 0.00001);
        vec3 up = cross(right, tangent);
        float crossScale = CurveMetrics.w * max(0.08, 1.0 - t * 0.86);
        vec2 rotated = vec2(Position.x * CurveTwist.x - Position.z * CurveTwist.y,
                            Position.x * CurveTwist.y + Position.z * CurveTwist.x) * crossScale;
        localPosition = 2.0 * CurveB * one * t + CurveC * t * t
                        + right * rotated.x + up * rotated.y;
        vec2 rotatedNormal = vec2(Normal.x * CurveTwist.x - Normal.z * CurveTwist.y,
                                  Normal.x * CurveTwist.y + Normal.z * CurveTwist.x);
        localNormal = right * rotatedNormal.x + up * rotatedNormal.y
                      + tangent * Normal.y * CurveMetrics.y / CurveMetrics.z;
    }
    vec4 worldPosition = WorldPose * vec4(localPosition, 1.0);
    vec4 position = ModelViewMat * worldPosition;
    vec3 normal = normalize(WorldNormal * localNormal);
    gl_Position = ProjMat * position;
    vertexDistance = fog_distance(worldPosition.xyz, FogShape);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal,
                                     vec4(Color.rgb, Color.a * InstanceAlpha));
    lightMapColor = texelFetch(Sampler2, ivec2(InstanceLight) / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    wetness = clamp(UV0.x, 0.0, 1.0);
    viewPosition = position.xyz;
    mat3 viewRotation = mat3(ModelViewMat);
    viewNormal = viewRotation * normal;
    viewLight0 = viewRotation * Light0_Direction;
    viewLight1 = viewRotation * Light1_Direction;
}
