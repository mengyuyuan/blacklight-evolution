#version 150
in vec3 pos,normal,ink;in vec2 uv;in float joint;
uniform mat4 camera,root;uniform sampler2D bones,actors;
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

uniform int absent; uniform int row0;uniform vec3 hit[3];uniform float drain,radius;out float digested;
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
   mat4 bone=mat4(texelFetch(bones,ivec2((j%2)*4+0,j/2),0),texelFetch(bones,ivec2((j%2)*4+1,j/2),0),texelFetch(bones,ivec2((j%2)*4+2,j/2),0),texelFetch(bones,ivec2((j%2)*4+3,j/2),0));
   p=(root*bone*vec4(p,1)).xyz;if(kind==0)p*=.9912102;n=mat3(root)*mat3(bone)*n;
 }else if(kind==2){
   int i=gl_InstanceID;if(i<12 && (absent & (1<<i))!=0){gl_Position=vec4(2.,2.,2.,1.);return;}vec4 a=texelFetch(actors,ivec2(0,i),0), b=texelFetch(actors,ivec2(1,i),0),d=texelFetch(actors,ivec2(2,i),0);
   float wave=sin(b.x),climb=b.y,lean=b.z;
   if(j>=2){bool left=j<6;int paired=(j-2)%4;float side=left?1.:-1.;int base=left?2:6;
     if(paired<2){float swing=mix(.25+wave*.79*side,1.30+wave*.75*side,climb);
       if(paired==1)hinge(p,n,pivots[base+1],rx(.45+.45*climb+.26*wave*side));
       hinge(p,n,pivots[base],rz(side*(.08+.27*climb))*rx(swing));
     }else{if(paired==3)hinge(p,n,pivots[base+3],rx(-.55-.40*wave*side));
       hinge(p,n,pivots[base+2],rx(.15-.75*wave*side+.35*climb));}
   }
   if(j==1)hinge(p,n,pivots[1],rx(lean*.42-.2*climb));
   mat3 r=ry(a.w)*rz(d.x)*rx(-lean);
   p=a.xyz+.5*(vec3(0,.65,0)+r*(p-vec3(0,.65,0)));n=r*n;
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