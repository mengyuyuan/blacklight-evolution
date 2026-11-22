#version 150
in vec3 wpos,nn;in float pulse,minor,axis,travel,wound; uniform int combat;uniform vec3 eye;uniform float sceneLight;uniform sampler2D fiberMap;out vec4 color;
void main(){if(combat==0 && wpos.y<.025)discard;
 vec3 n=normalize(nn),v=normalize(eye-wpos),l=normalize(vec3(-.55,.84,-.38));
 float spec=pow(max(0.,dot(n,normalize(v+l))),18.);
 float ridge=.5+.5*cos(6.283*(axis*5.+travel*2.));
 vec3 c=mix(vec3(.116,.065,.087),vec3(.31,.116,.126),ridge*.58+minor*.16);
 vec3 tex=texture(fiberMap,vec2(fract(axis),fract(travel*12.))).rgb;c=mix(c,tex,.65);
 c*=.65+.68*max(0.,dot(n,l));c+=pulse*(.32+.33*minor)*vec3(.49,.078,.058)+spec*vec3(.28,.21,.20);
 color=vec4(c*sceneLight,1.);
}