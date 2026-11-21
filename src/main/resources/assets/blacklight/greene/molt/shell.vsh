#version 150
in vec2 param;uniform mat4 vp; uniform float time;
out vec3 wpos;out vec2 surf;out float alphaField,fold,tear,skin;
const float TAU=6.283185;
vec3 surface(float u,float h){
 float grow=smoothstep(7.9,12.35,time),lift=smoothstep(13.25,18.5,time);
 float peel=smoothstep(13.5,18.8,time);
 float a=u*TAU,side=sin(a);
 float rip=.11*sin(3.*a+1.7)+.068*sin(7.*a-.75)+.048*cos(12.*a+3.2);
 float edge=1.19-peel*1.53+rip*peel;
 float loose=smoothstep(edge-.16,edge+.09,h)*peel;
 float rise=pow(grow,.9);
 float top=mix(8.5,19.9,rise)+lift*7.2;
 float bottom=mix(1.5,-4.35,grow)+lift*6.25;
 float y=mix(bottom,top,h);
 float q=2.*h-1.;float profile=pow(max(.000001,1.-pow(abs(q),2.4)),.435);
 float vein=a*11.+h*7.5+sin(a*2.+h*5.)+sin(h*9.-a)*.34;
 float striation=.45*cos(vein)+.19*sin(a*24.+h*17.)+.16*sin(a*16.-h*21.);
 float inflate=1.+.05*sin(a*3.+h*7.)+.023*sin(2.*time-h*11.)+.025*sin(7.*a-h*3.);
 float xrad=mix(3.7,10.05,grow)*(1.-.12*lift);
 float zrad=mix(3.4,7.4,grow)*(1.-.09*lift);
 float flare=loose*(1.4+1.8*sin(a*3.)*sin(a*3.));
 float asym=.96*sin(h*6.)+.43*sin(a*3.+h*5.)-1.9*h*h;
 vec3 p=vec3(.15+asym+cos(a)*(xrad*profile*inflate+striation*profile+flare),y-loose*(1.2+2.*peel),
    .15+sin(a)*(zrad*profile*inflate+striation*profile+flare));
 float retreat=smoothstep(.12,.85,peel);
 p.x=mix(p.x,1.3+cos(a*3.+h*6.)*.8,retreat*.93);
 p.z=mix(p.z,sin(a*3.+h*6.)*.81,retreat*.93);
 p.y=mix(p.y,7.15+9.*h,retreat*.75);
 return p;
}
void main(){
 surf=param;float a=param.x*TAU,h=param.y;
 float grow=smoothstep(7.9,12.35,time),peel=smoothstep(13.5,18.8,time);
 float curl=a*8.+h*8.5+sin(a*2.+h*4.)*.8;
 fold=pow(.5+.5*cos(curl),1.8);
 float tip=.13*sin(a*3.+1.)+.065*sin(a*7.-.6)+.048*cos(a*12.+3.2);
 float entry=grow*1.62-.19-h+.12*cos(curl)+tip*(1.-grow);
 float edge=1.19-peel*1.53+tip*peel;
 float exitField=edge-h+(1.-fold)*.105*peel;
 float seal=smoothstep(11.2,12.6,time)*(1.-smoothstep(13.5,15.5,time));
 float overlap=max(.5+.5*cos(a*13.+h*17.+sin(a*4.)), .5+.5*cos(a*9.-h*24.+1.2*sin(3.*a+h*4.)));
 float apertures=overlap-(.65-.8*seal)+.11*sin(h*34.+4.*a);
 alphaField=min(min(entry,exitField),apertures);tear=exitField;skin=entry;
 wpos=surface(param.x,h);gl_Position=vp*vec4(wpos,1);
}