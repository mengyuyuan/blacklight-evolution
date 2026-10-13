#version 150
#moj_import <fog.glsl>
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord;
out vec4 fragColor;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);}
void main(){
    vec2 p=texCoord*2.0-1.0;
    float billow=noise(texCoord*5.0)*.65+noise(texCoord*13.0)*.35;
    float edge=1.0-smoothstep(.30,1.0,length(p)+.25*(billow-.5));
    vec4 color=vertexColor*ColorModulator;
    color.rgb*=.65+.55*billow;
    color.a*=edge*(.15+.85*smoothstep(.25,.7,billow));
    if(color.a<.003)discard;
    fragColor=linear_fog(color,vertexDistance,FogStart,FogEnd,FogColor);
}
