#version 150
in vec2 corner;uniform mat4 vp;uniform vec3 eye;uniform float time;
out vec2 disk;out float density,age,tint;
float H(float n){return fract(sin(n*313.49+345.75)*4417.89);}
void main(){
 float i=float(gl_InstanceID),seed=H(i);float duration=1.6+1.8*H(i+14.);
 float start=2.4+14.65*H(i+7.);age=(time-start)/duration;
 float f=clamp(age,0.,1.);float a=6.283*H(i+2.);
 float old=step(8.,start),late=step(13.,start);
 float r=mix(10.+4.*H(i+3.),6.+3.*H(i+16.),old);r-=f*(1.5+2.3*old);
 float y=(.05+1.4*seed)*(1.-late)+late*(6.+9.*H(i+9.));
 vec3 p=vec3(r*cos(a+f*.22),y+f*(1.6+seed*3.5),r*sin(a+f*.22));
 vec3 view=normalize(eye-p),right=normalize(cross(vec3(0,1,0),view)),up=cross(view,right);
 float size=(.54+H(i+6.)*1.47)*(.62+f*1.32);
 vec3 wp=p+(right*corner.x+up*corner.y)*size;
 density=smoothstep(0.,.17,age)*(1.-smoothstep(.31,1.,age))*.29;
 disk=corner;tint=H(i+21.);gl_Position=vp*vec4(wp,1.);
}