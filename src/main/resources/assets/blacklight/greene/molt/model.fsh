#version 150
in vec3 rgb,norm,wp;in vec2 texuv;flat in int surface;
in float digested;uniform sampler2D animal;uniform float sceneLight;out vec4 color;uniform sampler2D atlas,spiderSkin,civilian0,civilian1,civilian2,civilian3;uniform vec3 eye;uniform float time;
uniform int liveForm;uniform float damage,coreFlash;uniform ivec2 lightUV;uniform sampler2D levelLight;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
vec4 civilian(int tile,vec2 uv){
    if(tile==0)return texture(civilian0,uv);if(tile==1)return texture(civilian1,uv);if(tile==2)return texture(civilian2,uv);return texture(civilian3,uv);
}
void main(){
 if(liveForm==0 && wp.y<-.012)discard;
 vec3 c=rgb;vec3 n=normalize(norm);
 if(surface==1&&texuv.y<.8){vec2 at=texuv*vec2(2.,4.);vec4 tx=civilian(int(floor(at.x))+2*int(floor(at.y)),fract(at));if(tx.a<.42)discard;c*=tx.rgb;}
 if(surface==7)c*=texture(atlas,texuv).rgb;
 if(surface==8)c*=texture(spiderSkin,texuv).rgb;
 if(surface==6){vec4 tx=texture(animal,texuv);c*=tx.rgb;}
 if(surface==2){vec2 at=texuv*2.;vec4 tx=civilian(int(floor(at.x))+2*int(floor(at.y)),fract(at));if(tx.a<.42)discard;c*=tx.rgb;}
 if(liveForm==1){
   vec3 view=normalize(eye-wp);if(dot(n,view)<0.)n=-n;
   vec3 sun=normalize(vec3(-.42,.78,-.48));float diffuse=.66+.31*max(0.,dot(n,sun))+.06*max(0.,n.y);
   float wet=surface==7?smoothstep(.09,.24,c.r-c.g):0.;
   float shine=.13*wet*pow(max(0.,dot(n,normalize(sun+view))),23.);
   vec3 lit=(diffuse*c+shine*vec3(1.,.87,.82))*texelFetch(levelLight,lightUV,0).rgb;
   lit=mix(lit,vec3(.98,.28,.25),damage*.28);lit=mix(lit,vec3(.98,.89,.80),coreFlash*.4);
   color=vec4(lit,1);return;
 }
 if((surface==7||surface==1) && wp.y>19. && time>18.2 && time<20.3) {
   float charge=1.-smoothstep(.0,.42,abs(time-(19.05-.063*wp.x)));
   c+=charge*.52*vec3(.64,.20,.10);
 }
 if(surface==3){
   vec2 p=wp.xz;float tile=hash(floor(p*.5));float joints=min(abs(fract(p.x*.5)-.5),abs(fract(p.y*.5)-.5));
   c*=.96+.08*tile-.09*(1.-smoothstep(.002,.021,joints));
   c=mix(c,vec3(.47,.43,.33),(.24+.26*step(.50,fract(p.y*.18)))*(1.-smoothstep(.06,.15,abs(abs(p.x)-18.))));
   c*=1.-.39*exp(-dot(p/vec2(10.,8.),p/vec2(10.,8.)));
   float pulse=max(0.,1.-abs(length(p)-(time-20.15)*17.)/.8)*smoothstep(20.15,20.35,time);
   c+=vec3(.64,.16,.11)*pulse;
 }
 if(surface==4&&abs(n.y)<.45){
   vec2 loc=vec2(abs(n.x)>.6?wp.z:wp.x,wp.y);
   vec2 w=fract(loc/vec2(2.6,3.4));float glass=step(.14,w.x)*step(w.x,.85)*step(.23,w.y)*step(w.y,.68);
   c=mix(c,vec3(.32,.40,.46)*(.55+.28*hash(floor(loc/vec2(2.6,3.4)))),glass*.8);
 }
 if(surface==5){
   float pulse= .5+.5*sin(2.8*time-.75*length(wp.xz));
   c*=.7+pulse*.6;
 }
 if(digested>.998)discard;
 c=mix(c,vec3(.185,.046,.058),smoothstep(.025,.36,digested)*.93);
 if(dot(n,eye-wp)<0.)n=-n;
 float light=.70+.32*max(0.,dot(n,normalize(vec3(-.43,.72,-.48))))+.08*max(0.,n.y);
 if(surface<3){c=c*light;
   c+=vec3(.14,.055,.04)*pow(1.-abs(dot(n,normalize(eye-wp))),3.);
 }else c*=light;
 float fog=0.;
 c=mix(c,vec3(.61,.65,.66),fog);
 color=vec4(c*sceneLight,1);
}