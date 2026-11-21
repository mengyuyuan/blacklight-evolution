#version 150
#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform mat4 ModelViewMat;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
in float vertexDistance;
in vec2 texCoord;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec3 viewPosition;
in vec3 viewNormal;
in vec3 viewLight0;
in vec3 viewLight1;
out vec4 fragColor;

// DDS face order +X, -X, +Y / -Y, +Z, -Z. Keep filtering inside each face.
vec2 cubeAtlas(vec3 r){
    vec3 a=abs(r);
    vec2 uv,tile;
    if(a.x>=max(a.y,a.z)){
        bool pos=r.x>=0.;uv=vec2(pos?-r.z:r.z,-r.y)/a.x;tile=vec2(pos?0.:1.,0.);
    }else if(a.y>=a.z){
        bool pos=r.y>=0.;uv=vec2(r.x,pos?r.z:-r.z)/a.y;tile=pos?vec2(2.,0.):vec2(0.,1.);
    }else{
        bool pos=r.z>=0.;uv=vec2(pos?r.x:-r.x,-r.y)/a.z;tile=vec2(pos?1.:2.,1.);
    }
    return (tile+clamp(uv*.5+.5,vec2(.5/64.),vec2(1.-.5/64.)))/vec2(3.,2.);
}

void main(){
    vec4 tissue=texture(Sampler0,texCoord);
    // char_alex_cloth lit_PR. Alpha-composite this coat over the already-lit clothing.
    float x=1.-clamp(vertexColor.a,0.,1.);
    float opacity=1.-x*pow(1.-(1.-x)*tissue.a,8.);
    if(opacity<.006)discard;
    vec2 xy=texture(Sampler3,texCoord).ag*2.-1.;
    vec3 mapNormal=vec3(xy,sqrt(max(0.,1.-dot(xy,xy))));
    vec3 n=normalize(viewNormal);
    vec3 dp1=dFdx(viewPosition),dp2=dFdy(viewPosition);
    vec2 du1=dFdx(texCoord),du2=dFdy(texCoord);
    vec3 t=cross(dp2,n)*du1.x+cross(n,dp1)*du2.x;
    vec3 b=cross(dp2,n)*du1.y+cross(n,dp1)*du2.y;
    float basis=max(dot(t,t),dot(b,b));
    if(basis>1.e-12)n=normalize((t*mapNormal.x+b*mapNormal.y)*inversesqrt(basis)+n*mapNormal.z);
    vec3 r=transpose(mat3(ModelViewMat))*reflect(normalize(viewPosition),n);
    vec3 env=texture(Sampler4,cubeAtlas(normalize(r))).rgb;
    float diffuse=.54+.30*max(0.,dot(n,normalize(viewLight0)))+.16*max(0.,dot(n,normalize(viewLight1)));
    vec3 color=tissue.rgb*diffuse+env*(.33*tissue.a);
    color*=lightMapColor.rgb*ColorModulator.rgb;
    color=mix(overlayColor.rgb,color,overlayColor.a);
    fragColor=linear_fog(vec4(color,opacity*ColorModulator.a),vertexDistance,FogStart,FogEnd,FogColor);
}
