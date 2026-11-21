#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out vec2 texCoord;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec3 viewPosition;
out vec3 viewNormal;
out vec3 viewLight0;
out vec3 viewLight1;

void main() {
    vec4 position = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * position;
    vertexDistance = fog_distance(Position, FogShape);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord=UV0;
    viewPosition = position.xyz;
    // VertexConsumer already applies the pose's normal matrix. The remaining
    // ModelViewMat uses the same camera rotation for the normal and both lights.
    mat3 viewRotation = mat3(ModelViewMat);
    viewNormal = viewRotation * Normal;
    viewLight0 = viewRotation * Light0_Direction;
    viewLight1 = viewRotation * Light1_Direction;
}
