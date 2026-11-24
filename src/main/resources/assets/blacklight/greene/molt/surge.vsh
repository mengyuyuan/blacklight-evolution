#version 150
in vec2 param;
uniform mat4 vp;
uniform float terrainSpan,front,charge,released;
uniform sampler2D terrain;
uniform int plumes;
out vec3 world,normal;
out float seed,alive;
const float TAU=6.2831853;
float hash(float i){return fract(sin(i*91.719+16.13)*43758.55);}

void main(){
    float id=float(gl_InstanceID);
    float pressure=1.-exp(-released*5.);
    float radius=mix(8.4,1.1,smoothstep(0.,1.,charge))+front;
    seed=hash(id+2.);
    alive=smoothstep(0.,.3,charge);
    vec3 p;
    if(plumes==0 && gl_InstanceID==2){
        // Thin torn wake: the floor remains visible between the dense lip and the settling fragments.
        float a=param.x*TAU,u=param.y;
        float r=max(0.,radius*(.18+.76*u));
        float curl=sin(a*7.+u*13.-released*1.8)+.4*cos(a*12.-u*8.);
        p=vec3(cos(a)*r,.15+(.58+.35*curl)*sin(3.14159*u),sin(a)*r);
        normal=normalize(vec3(.20*cos(a)*curl,1.,.20*sin(a)*curl));
        alive*=pressure*.34*smoothstep(.02,.22,u)*(1.-smoothstep(.81,.99,u))*smoothstep(-.85,.55,curl);
        seed=.81;
    }else if(plumes==0){
        // A curled sheet: with the same footprint its trailing and leading faces lie at different heights.
        float angle=param.x*TAU;
        float turn=param.y*TAU;
        vec2 radial=vec2(cos(angle),sin(angle));
        float lobe=.50+.24*sin(11.*angle+.4*sin(3.*angle))+.20*sin(17.*angle-2.*released);
        float width=mix(.48,3.35,pressure)*(.8+.40*lobe)*(1.+id*.48);
        float height=mix(1.75,.62,charge)+pressure*(1.45+2.4*lobe);
        float ripple=pressure*(.48*sin(angle*13.+released)+.33*cos(angle*6.));
        float r=radius+ripple-width*(.52+.48*cos(turn))-id*pressure*(2.1+1.8*lobe);
        float y=.1+height*(.49+.47*sin(turn))*(1.-.42*id);
        p=vec3(radial.x*max(.02,r),y,radial.y*max(.02,r));
        normal=normalize(vec3(-radial.x*cos(turn),sin(turn),-radial.y*cos(turn)));
        seed=.21+id*.57;
        alive*=1.-.52*id;
    }else{
        // Independently curling ellipsoid wisps shear away behind the front.
        float az=TAU*(id+.7*seed)/64.;
        vec2 radial=vec2(cos(az),sin(az)),side=vec2(-radial.y,radial.x);
        float travel=hash(id+5.);
        float curl=released*(1.85+2.*travel)+TAU*seed;
        float spread=smoothstep(.02,.58,released);
        float r=max(.15,radius-(.55+11.*travel)*spread);
        float w=(.50+1.3*seed)+spread*(1.55+1.7*travel);
        float tall=.48+spread*(.72+2.2*hash(id+9.));
        float azimuth=TAU*param.x+curl, latitude=3.141593*(param.y-.5);
        float span=cos(latitude),vertical=sin(latitude);
        vec2 disk=vec2(cos(azimuth),sin(azimuth))*span;
        float crinkle=.90+.14*sin(azimuth*3.+latitude*5.+curl)*span;
        vec2 xz=radial*(r+w*.73*disk.y*crinkle)+side*(w*1.45*disk.x*crinkle+.57*sin(curl)*spread);
        p=vec3(xz.x,.12+tall*(1.+vertical)*.80+.42*(1.+sin(curl))*spread,xz.y);
        normal=normalize(vec3(side.x*disk.x+radial.x*disk.y,vertical,side.y*disk.x+radial.y*disk.y));
        alive*=mix(.12,.73,spread);
    }
    vec2 ground=textureLod(terrain,p.xz/terrainSpan+.5,0.).rg;
    p.y+=ground.r;
    alive*=1.-smoothstep(.38,.92,ground.g);
    world=p;
    gl_Position=vp*vec4(p,1.);
}
