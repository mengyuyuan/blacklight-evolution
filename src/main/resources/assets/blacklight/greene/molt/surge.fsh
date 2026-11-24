#version 150
in vec3 world,normal;
in float seed,alive;
uniform vec3 eye;
uniform float released,fade,crestFade,sceneLight;
uniform int plumes;
out vec4 color;

float grain(vec3 p){
    // Continuous three-dimensional folds, anchored to each volume rather than to the viewing angle.
    vec3 q=p+.56*sin(p.yzx*1.78+vec3(1.2,3.3,2.5));
    return .5+.25*sin(q.x+q.y*.72)*cos(q.z-q.y)+.15*sin(q.y*2.14+q.z*1.83+q.x*.50)
             +.10*cos(q.z*3.32-q.x*2.11+q.y*1.13);
}

void main(){
    vec3 p=world*.51+vec3(released*.28,-released*1.4,seed*4.);
    float coarse=grain(p),detail=grain(p*2.4+coarse*1.7);
    float body=smoothstep(.15,.77,coarse*.70+detail*.30);
    float torn=plumes==1?smoothstep(.24,.64,coarse):.24+.76*smoothstep(.17,.64,coarse);
    vec3 n=normalize(normal);
    vec3 view=eye-world;
    float facing=abs(dot(n,normalize(view+vec3(.00001))));
    float fadeNear=smoothstep(.7,4.8,length(view));
    float opacity=alive*fade*(plumes==1?1.:crestFade)*fadeNear*torn*(.10+.43*body)*smoothstep(.015,.46,facing)*(.60+.40*facing);
    if(opacity<.008)discard;
    float light=.5+.5*dot(n,normalize(vec3(-.38,.82,.42)));
    vec3 blood=mix(vec3(.135,.021,.042),vec3(.58,.122,.139),.19+body*.53+light*.22);
    blood=mix(blood,vec3(.70,.42,.37),.31*detail*light);
    color=vec4(blood*(.71+.29*sceneLight),opacity);
}
