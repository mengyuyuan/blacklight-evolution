#version 150
in vec3 rgb,norm,wp;in vec2 texuv;flat in int surface;
in float digested;uniform sampler2D animal;uniform float sceneLight;out vec4 color;uniform sampler2D atlas,skins;uniform vec3 eye;uniform float time;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
void main(){
 if(wp.y<-.012)discard;
 vec3 c=rgb;vec3 n=normalize(norm);
 if(surface==1&&texuv.y<.8){vec4 tx=texture(atlas,texuv);if(tx.a<.42)discard;c*=tx.rgb;}
 if(surface==6){vec4 tx=texture(animal,texuv);c*=tx.rgb;}
 if(surface==2){vec4 tx=texture(skins,texuv);if(tx.a<.42)discard;c*=tx.rgb;}
 if(surface==1 && wp.y>19. && time>18.2 && time<20.3) {
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
 float light=.72+.50*max(0.,dot(n,normalize(vec3(-.43,.72,-.48))))+.14*max(0.,n.y);
 if(surface<3){c=pow(max(c,vec3(0)),vec3(.84))*(light+.07);
   c+=vec3(.14,.055,.04)*pow(1.-abs(dot(n,normalize(eye-wp))),3.);
 }else c*=light;
 float fog=0.;
 c=mix(c,vec3(.61,.65,.66),fog);
 color=vec4(c*sceneLight,1);
}