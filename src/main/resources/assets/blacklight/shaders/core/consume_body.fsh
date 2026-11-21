#version 150
#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler3;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
in float vertexDistance;
in vec2 texCoord;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec3 viewNormal;
in vec3 viewPosition;
in vec3 viewLight0;
in vec3 viewLight1;
out vec4 fragColor;

void main() {
    vec4 fibre = texture(Sampler0, texCoord);
    float alpha = vertexColor.a * fibre.a * ColorModulator.a;
    if (alpha < 0.02) discard;

    // Build tangent axes on each flat face so a ridge catches the same light across a moving joint.
    vec3 n = normalize(viewNormal);
    vec3 dx = dFdx(viewPosition), dy = dFdy(viewPosition);
    vec2 ds = dFdx(texCoord), dt = dFdy(texCoord);
    vec3 tangent = cross(dy,n)*ds.x + cross(n,dx)*dt.x;
    vec3 bitangent = cross(dy,n)*ds.y + cross(n,dx)*dt.y;
    float norm = max(dot(tangent,tangent),dot(bitangent,bitangent));
    vec3 bump = texture(Sampler3, texCoord).rgb * 2.0 - 1.0;
    if (norm > 1.e-10)
        n = normalize(n*bump.z + (tangent*bump.x + bitangent*bump.y)*inversesqrt(norm));
    vec3 key = normalize(viewLight0);
    float shade = .52 + .35*max(0.,dot(n,key)) + .13*max(0.,dot(n,normalize(viewLight1)));
    float edge = pow(max(0.,dot(n,normalize(key-normalize(viewPosition)))),20.);
    vec3 colour = (fibre.rgb * shade + vec3(.075,.075,.082)*edge)*lightMapColor.rgb*ColorModulator.rgb;
    colour = mix(overlayColor.rgb,colour,overlayColor.a);
    fragColor = linear_fog(vec4(colour,alpha),vertexDistance,FogStart,FogEnd,FogColor);
}
