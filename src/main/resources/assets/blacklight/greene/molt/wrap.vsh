#version 150
in vec2 param;uniform mat4 vp;uniform float time;uniform vec3 eye;
out vec3 wpos,nn;out float pulse,minor,axis,travel,wound;
const float TAU=6.283185;
float noise1(float id){return fract(sin(id*78.417+4.13)*4379.291);}
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

vec3 line(float v,float id){
 float seed=noise1(id);float dir=mix(-1.,1.,step(.51,noise1(id+9.)));
 float h0=.01+.43*noise1(id+12.);
 float h=h0+v*(1.-h0)*(.68+.32*noise1(id+5.));
 float u=id*.61803+dir*(.21+seed*.51)*v+.019*sin(14.*v+id+time*.66);
 float a=TAU*u;vec3 radial=normalize(vec3(cos(a),.05,sin(a)));
 float open=smoothstep(13.6+seed*.7,17.6+seed,time);
 float yawSlide=open*(.22+.45*seed)*v*v;
 vec3 p=surface(u+yawSlide,h);
 float loose=sin(3.14159*v);
 p+=radial*((.29+.86*seed)*loose+.10+.12*cos(9.*v+id+time*.9)
   +1.95*open*loose*sin(4.*v+id));
 p.y+=.17*sin(v*12.+id)+open*loose*sin(id)*1.3;
 return p;
}
void main(){
 float id=float(gl_InstanceID),seed=noise1(id);
 float grow=smoothstep(7.85+seed*2.15,11.25+seed*1.4,time);
 float retreat=smoothstep(14.45+seed*1.86,18.0+seed*1.46,time);
 float start=retreat*.85;float end=max(start,grow-retreat*.15);
 float v=mix(start,end,param.x);vec3 p=line(v,id);
 vec3 dir=normalize(line(v+.002,id)-line(v-.002,id)+vec3(.0001,0,0));
 vec3 tx=normalize(cross(dir,vec3(.04,1.,.02))),ty=cross(dir,tx);
 float phase=time*.64+2.15*v+seed*2.;
 pulse=.53*pow(.5+.5*cos(TAU*phase),7.);
 float r=(.06+.21*seed)*(.65+.44*pulse)*(.10+.90*sin(3.14159*param.x))
       *smoothstep(.01,.12,end-start);
 nn=tx*cos(param.y*TAU)+ty*sin(param.y*TAU);wpos=p+nn*r;
 gl_Position=vp*vec4(wpos,1.);minor=.5;travel=v;axis=param.y;wound=1.;
}