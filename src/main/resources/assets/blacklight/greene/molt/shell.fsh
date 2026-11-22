#version 150
in vec3 wpos;in vec2 surf;in float alphaField,fold,tear,skin;uniform vec3 eye;uniform float time;uniform float sceneLight;uniform sampler2D skinMap;out vec4 color;
void main(){
 if(alphaField<0. || wpos.y<.008)discard;
 vec3 n=normalize(cross(dFdx(wpos),dFdy(wpos)));vec3 v=normalize(eye-wpos);
 if(dot(n,v)<0.)n=-n;
 float a=surf.x*6.283185,h=surf.y;
 float phase=11.*a+h*7.5+sin(a*2.+h*5.)+.34*sin(h*9.-a);
 float gross=.5+.5*cos(phase),twist=phase*3.2+2.1*sin(phase*.75+h*10.)+.5*cos(a*17.);
 float strands=.5+.5*cos(twist);
 float fibre=pow(.5+.5*cos(twist*3.+h*11.+time*.24),6.);
 vec3 deep=vec3(.082,.053,.079), fibreRed=vec3(.34,.115,.124);
 float crossing=smoothstep(.30,.76,sin(a*14.-h*22.+1.7*cos(a*3.+h*7.)));
 vec3 c=mix(deep,fibreRed,.32*gross+.24*strands+.12*fold+.12*crossing);
 float returning=pow(.5+.5*cos(11.*h-2.5*time+sin(phase*.32)),5.)*smoothstep(8.5,10.8,time)*(1.-smoothstep(16.5,18.,time));
 c+=vec3(.128,.028,.018)*returning*smoothstep(.47,.91,strands);
 vec3 L=normalize(vec3(-.6,.78,-.42));
 float spec=pow(max(0.,dot(normalize(L+v),n)),26.);
 c=mix(c,texture(skinMap,fract(surf*vec2(16.,10.))).rgb,.46);
 c*=.67+.64*max(0.,dot(n,L));c+=spec*(.15+.35*fibre)*vec3(.78,.64,.61);
 float rim=pow(1.-max(0.,dot(n,v)),3.);
 c+=rim*vec3(.115,.069,.052);
 float wound=1.-smoothstep(.006,.037,min(tear,skin));c=mix(c,vec3(.48,.17,.14),wound*.72);
 color=vec4(c*sceneLight,1);
}