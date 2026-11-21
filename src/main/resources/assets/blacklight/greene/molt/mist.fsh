#version 150
in vec2 disk;in float density,age,tint;out vec4 color;
void main(){float q=dot(disk,disk);if(q>=1.||density<.002)discard;
 float noise=.52+.48*sin(disk.x*13.+2.1*sin(disk.y*8.+tint*19.)+age*3.);
 float a=pow(1.-q,2.)*density*(.55+.45*noise);
 color=vec4(mix(vec3(.105,.068,.08),vec3(.42,.32,.30),tint*.69),a);
}