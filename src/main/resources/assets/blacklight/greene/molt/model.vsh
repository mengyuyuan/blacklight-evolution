#version 150
in vec3 pos,normal,ink;in vec2 uv;in float joint;
uniform mat4 camera,root;uniform sampler2D bones,actors;
uniform int severed; uniform vec2 legBends[8];
const vec3 legJoint[16]=vec3[16](vec3(3.26562,5.22297,-1.25000),vec3(6.04688,9.63932,-3.42188),vec3(3.78125,4.07980,-0.46875),vec3(7.46875,7.68422,-0.90625),vec3(3.50000,4.26225,1.23438),vec3(3.75000,6.98466,2.59375),vec3(3.35938,3.60484,2.03125),vec3(5.46875,5.93191,3.54688),vec3(-1.54688,4.86653,-0.60938),vec3(-4.54688,9.62314,-2.59375),vec3(-1.84375,4.12370,0.10938),vec3(-7.31250,7.87852,0.28125),vec3(-1.56250,4.59460,1.76562),vec3(-2.54688,7.51469,3.04688),vec3(-1.85938,3.40105,2.43750),vec3(-4.20312,5.87183,3.79688));
uniform int kind; uniform float time;uniform vec3 pivots[12];
out vec3 rgb, norm,wp;out vec2 texuv;flat out int surface;
mat3 rx(float a){float c=cos(a),s=sin(a);return mat3(1,0,0,0,c,s,0,-s,c);}
mat3 ry(float a){float c=cos(a),s=sin(a);return mat3(c,0,-s,0,1,0,s,0,c);}
mat3 rz(float a){float c=cos(a),s=sin(a);return mat3(c,s,0,-s,c,0,0,0,1);}
void hinge(inout vec3 p,inout vec3 n,vec3 pivot,mat3 r){p=pivot+r*(p-pivot);n=r*n;}

uniform sampler2D paths;
vec3 at(int row,float u){
 float x=clamp(u,0.,1.)*15.;int i=int(x);
 return mix(texelFetch(paths,ivec2(i,row),0).xyz,texelFetch(paths,ivec2(min(15,i+1),row),0).xyz,fract(x));
}

uniform sampler2D clamber;uniform int absent; uniform int row0;uniform vec3 hit[3];uniform float drain,radius;out float digested;
vec3 siphon(vec3 p,vec3 local,int row,float progress,float d){
 float delay=.43*clamp(d,0.,1.);
 float a=smoothstep(delay,1.+delay,1.45*progress);
 digested=a;
 vec3 tip=at(row,1.);
 float track=smoothstep(.10,1.,a);
 vec3 cursor=at(row,1.-track);
 vec3 direction=normalize(at(row,max(0.,1.-track-.035))-cursor+vec3(.00001));
 vec3 offset=p-tip;
 float longitudinal=dot(direction,offset);
 vec3 strip=offset-direction*longitudinal;
 return cursor+strip*(1.-smoothstep(0.,.34,a))+
    direction*longitudinal*(1.-smoothstep(.16,.73,a));
}

void main(){
 digested=0.;
 int j=int(joint+.1);vec3 p=pos, n=normal;rgb=ink;texuv=uv;surface=kind;
 if(kind==0 || kind==1){
   if(kind==0 && j>=13 && j<=28){
     int k=j-13,leg=k/2;
     if(k%2==1){
       if((severed & (1<<leg))!=0){gl_Position=vec4(2.,2.,2.,1.);return;}
       hinge(p,n,legJoint[k],rz(legBends[leg].y));
     }
     hinge(p,n,legJoint[leg*2],rz(legBends[leg].x));
   }
   if(kind==0)surface=8;else if(j<35)surface=7;
   mat4 bone=mat4(texelFetch(bones,ivec2((j%4)*4+0,j/4),0),texelFetch(bones,ivec2((j%4)*4+1,j/4),0),texelFetch(bones,ivec2((j%4)*4+2,j/4),0),texelFetch(bones,ivec2((j%4)*4+3,j/4),0));
   p=(root*bone*vec4(p,1)).xyz;if(kind==0)p*=.9912102;n=mat3(root)*mat3(bone)*n;
 }else if(kind==2){
   int i=gl_InstanceID;if(i<12 && (absent & (1<<i))!=0){gl_Position=vec4(2.,2.,2.,1.);return;}vec4 a=texelFetch(actors,ivec2(0,i),0), b=texelFetch(actors,ivec2(1,i),0),d=texelFetch(actors,ivec2(2,i),0);
   vec4 x=texelFetch(clamber,ivec2(j*3,i),0),y=texelFetch(clamber,ivec2(j*3+1,i),0),z=texelFetch(clamber,ivec2(j*3+2,i),0);
   vec4 v=vec4(p,1.);
   p=vec3(dot(x,v),dot(y,v),dot(z,v));n=vec3(dot(x.xyz,n),dot(y.xyz,n),dot(z.xyz,n));
   p=siphon(p,pos,i,d.z,length(pos-vec3(0,1.14,0))/1.4);
   texuv=vec2((uv.x+mod(d.y,2.))*.5,(uv.y+floor(d.y*.5))*.5);
 }else if(kind==6){
   p=(root*vec4(p,1)).xyz;n=mat3(root)*n;
   vec3 ds=vec3(length(pos-hit[0]),length(pos-hit[1]),length(pos-hit[2]));
   int k=clamp(j,0,2);
   p=siphon(p,pos,row0+k,drain,ds[k]/radius);
 }else{surface=3+j;p=(root*vec4(p,1)).xyz;}
 wp=p;norm=n;gl_Position=camera*vec4(p,1);
}