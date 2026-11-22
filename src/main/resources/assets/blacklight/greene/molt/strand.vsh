#version 150
in vec2 param; uniform int absent,combat;uniform float time;uniform mat4 vp;uniform sampler2D timing;
out vec3 wpos,nn;out float pulse,minor,axis,travel,wound;

uniform sampler2D paths;
vec3 at(int row,float u){
 float x=clamp(u,0.,1.)*15.;int i=int(x);
 return mix(texelFetch(paths,ivec2(i,row),0).xyz,texelFetch(paths,ivec2(min(15,i+1),row),0).xyz,fract(x));
}

void main(){
 int id=gl_InstanceID/3;int thread=gl_InstanceID%3;int slot=id<12?id:id>=202?12+(id-202)/3:-1;if(slot>=0 && (absent & (1<<slot))!=0){gl_Position=vec4(2.,2.,2.,1.);return;}
 vec4 data=texelFetch(timing,ivec2(0,id),0);float fire=data.x, finish=data.y,seed=data.z;
 float big=data.w;
 float live=smoothstep(fire-.88,fire,time)*(1.-smoothstep(finish,finish+.88,time));
 if(combat!=0) live=1.; // Gameplay already extends and retracts the cuttable centerline.
 float u=param.x*live;
 vec3 p=at(id,u),dir=normalize(at(id,u+.009)-at(id,u-.009)+vec3(.000001,0,0));
 vec3 right=normalize(cross(dir,vec3(.15,1.,.03))),up=cross(dir,right);
 float transport=smoothstep(fire+.15,fire+.65,time)*(1.-smoothstep(finish-.2,finish+.5,time));
 if(combat!=0) transport=data.y;
 float direction=combat!=0?data.w:1.;
 float phase=u*(2.4+.7*seed)+direction*time*(.97+.7*seed)+12.*seed;
 pulse=pow(.5+.5*cos(phase*6.2832),8.)*transport;
 if(combat!=0) big=1.;
 float radius=combat!=0?data.x:mix(.065,.235,big);
 float r=radius*(1.14-.52*u)*(1.+pulse*(.55+.43*big));
 float braid=(u*(2.1+seed)+.075*time+float(thread)*.38+seed)*6.2832;
 if(thread!=0){p+=(right*sin(braid)+up*cos(braid))*r*(.88+.53*sin(3.14*u));r*=.28+.08*big;}
 r*=smoothstep(.0,.1,live)*(.09+.91*smoothstep(0.,.08,1.-param.x));
 float theta=param.y*6.283185;
 nn=right*cos(theta)+up*sin(theta);wpos=p+nn*r;
 gl_Position=vp*vec4(wpos,1);
 minor=float(thread!=0);travel=u;axis=param.y;wound=transport;
}