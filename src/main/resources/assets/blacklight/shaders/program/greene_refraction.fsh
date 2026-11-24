#version 150
uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 InSize;
uniform float Ax, Ay, Az, Bx, By, Bz, Radius, Strength, Age;
uniform float ProjX, ProjY, OffsetX, OffsetY, DepthA, DepthB;
uniform float Inward;
in vec2 texCoord;
out vec4 fragColor;

float sceneDepth(vec2 uv) {
    return DepthB / min(-0.000001, texture(DepthSampler, uv).r * 2.0 - 1.0 + DepthA);
}

uniform float AxisX, AxisY, AxisZ, RightX, RightY, RightZ, UpX, UpY, UpZ;
uniform float PalmRX, PalmRY, PalmRZ, PalmLX, PalmLY, PalmLZ, KneeX, KneeY, KneeZ;
uniform float ShoulderX, ShoulderY, ShoulderZ, Contact, Fracture;

struct Support { vec3 direction; float radius; };
Support supportPoint(vec3 position, mat3 toUnit){
    vec3 p=toUnit*position;
    float r=length(p);
    return Support(p/max(.0001,r),clamp(r+.08,.68,1.31));
}
float loadPoint(vec3 dir,Support p){
    float along=max(0.,dot(dir,p.direction));
    return along*along*along*along*along*along*along*along;
}
// An ellipsoid under four point loads. Each footprint follows a posed joint in 3D.
float membrane(vec3 q,Support r,Support l,Support k,Support s){
    float len=length(q);
    vec3 d=q/max(.0001,len);
    float drift=Age*.057;
    float freeRadius=.99+.085*sin(3.6*d.x+2.1*d.z+drift)*cos(3.1*d.y-drift*.37)
                     +.058*sin(4.7*d.y-2.8*d.z-drift*.54)*cos(2.6*d.x);
    float wr=loadPoint(d,r),wl=loadPoint(d,l),wk=loadPoint(d,k),ws=loadPoint(d,s);
    float sum=wr+wl+wk+ws;
    float support=(wr*r.radius+wl*l.radius+wk*k.radius+ws*s.radius)/max(.0001,sum);
    float radius=mix(freeRadius,support,Contact*min(1.,sum));
    return len-radius;
}
vec4 compressVolume(vec4 original,vec3 start,vec3 ray,float visible,vec2 projection){
    float along=dot(start,ray);
    float det=Radius*Radius-dot(start-along*ray,start-along*ray);
    if(det<=0.)return original;
    float span=sqrt(det);
    float nearT=max(0.,along-span),farT=along+span;
    if(farT<=nearT || visible<nearT)return original;
    vec3 axes=vec3(AxisX,AxisY,AxisZ);
    vec3 right=normalize(vec3(RightX,RightY,RightZ));
    vec3 up=normalize(vec3(UpX,UpY,UpZ));
    vec3 forward=normalize(cross(right,up));
    mat3 basis=mat3(right,up,forward);
    mat3 toUnit=mat3(1./axes.x,0,0,0,1./axes.y,0,0,0,1./axes.z)*transpose(basis);
    Support r=supportPoint(vec3(PalmRX,PalmRY,PalmRZ),toUnit);
    Support l=supportPoint(vec3(PalmLX,PalmLY,PalmLZ),toUnit);
    Support k=supportPoint(vec3(KneeX,KneeY,KneeZ),toUnit);
    Support s=supportPoint(vec3(ShoulderX,ShoulderY,ShoulderZ),toUnit);
    vec3 o=toUnit*(-start),v=toUnit*ray;
    float low=nearT,high=nearT;
    bool inside=membrane(o+v*nearT,r,l,k,s)<0.;
    bool found=false;
    for(int i=1;i<=16;++i){
        high=mix(nearT,farT,float(i)/16.);
        bool nowInside=membrane(o+v*high,r,l,k,s)<0.;
        if(nowInside!=inside){found=true;break;}
        low=high;
    }
    if(!found)return original;
    for(int i=0;i<5;i++){
        float mid=(low+high)*.5;
        if((membrane(o+v*mid,r,l,k,s)<0.)==inside)low=mid;else high=mid;
    }
    float surface=(low+high)*.5;
    if(visible<surface-.015)return original;
    vec3 q=o+v*surface;
    vec2 e=vec2(.012,0.);
    float f=membrane(q,r,l,k,s);
    vec3 g=vec3(membrane(q+e.xyy,r,l,k,s)-f,membrane(q+e.yxy,r,l,k,s)-f,membrane(q+e.yyx,r,l,k,s)-f);
    vec3 normal=normalize(transpose(toUnit)*g);
    if(inside)normal=-normal;
    float grazing=1.-clamp(abs(dot(normal,ray)),0.,1.);
    vec3 dir=normalize(q);
    // Irregular gaps open between the recoiling lobes. The remaining film briefly catches light.
    vec3 slit=normalize(vec3(.71,-.24,.66));
    float seam=min(abs(dot(dir,slit)+.19*sin(dir.y*6.)),abs(dot(dir,vec3(-.22,.87,.44))+.13*sin(5.*dir.x)));
    float intact=1.-smoothstep(seam,seam+.08,Fracture*.94);
    float flash=sin(3.14159*clamp(Fracture*2.8,0.,1.));
    float load=Contact*min(1.,loadPoint(dir,r)+loadPoint(dir,l)+loadPoint(dir,k)+loadPoint(dir,s));
    float edge=grazing*grazing;
    vec2 extent=.5*projection*min(axes.x,axes.y)/max(.9,surface*-ray.z);
    // Normal slopes carry the scenery into the moving wall. Transmission stays high over the hands and eyes.
    vec2 shift=extent*(normal.xy-ray.xy*dot(normal,ray))*(.21+.24*edge+.10*load)*Strength;
    vec2 guard=.5/InSize;
    vec2 uv=clamp(texCoord+clamp(shift,vec2(-.058),vec2(.058)),guard,1.-guard);
    if(sceneDepth(uv)<surface*(-ray.z)-.35*Radius)uv=texCoord;
    vec3 colour=texture(DiffuseSampler,uv).rgb;
    colour*=1.-Strength*(.045+.36*edge+.075*load);
    vec3 light=normalize(basis*vec3(.40,.85,-.29)-ray);
    float sheen=pow(max(0.,dot(normal,light)),16.)*(.034+.10*edge+.055*load);
    colour+=Strength*(sheen+edge*edge*.051)*vec3(.79,.83,.91);
    colour+=flash*(.08+.16*edge)*vec3(.72,.75,.82);
    return vec4(mix(original.rgb,colour,intact),original.a);
}

void main() {
    vec4 original = texture(DiffuseSampler, texCoord);
    if (Strength < 0.001) { fragColor = original; return; }
    vec2 projection = vec2(ProjX, ProjY);
    vec3 ray = normalize(vec3((texCoord * 2.0 - 1.0 + vec2(OffsetX, OffsetY)) / projection, -1.0));
    float visible = sceneDepth(texCoord) / -ray.z;
    vec3 start = vec3(Ax, Ay, Az);
    if(Inward>1.5){
        vec3 axis=normalize(vec3(Bx,By,Bz)-start);
        vec2 shift=vec2(0.);
        float along=dot(start,ray), off=dot(start,start)-along*along;
        for(int i=0;i<4;i++){
            float r=Radius-float(i)*1.7;
            if(r<.35 || r*r<off)continue;
            float halfChord=sqrt(max(0.,r*r-off));
            for(int j=0;j<2;j++){
                float depth=along+(j==0?-halfChord:halfChord);
                if(depth<=0. || depth>visible)continue;
                vec3 n=normalize(ray*depth-start);
                float c=dot(n,axis);
                float aperture=smoothstep(Contact-.015,Contact+.035,c);
                float arc=.35+.65*pow(.5+.5*cos((c-Contact)*52.),5.);
                shift+=n.xy*.00165*arc*aperture*(1.-.19*float(i))*(j==0?-1.:1.);
            }
        }
        fragColor=texture(DiffuseSampler,clamp(texCoord+shift*Strength,vec2(.001),vec2(.999)));
        return;
    }
    if(Inward>.5){
        fragColor=compressVolume(original,start,ray,visible,projection);
        return;
    }
    vec3 segment = vec3(Bx, By, Bz) - start;
    // Closest points between this pixel's *visible* ray and the finite discharge.
    vec3 transverse = segment - ray * dot(segment, ray);
    float u = clamp(-dot(transverse, start) / max(dot(transverse, transverse), 0.000001), 0.0, 1.0);
    float t = clamp(dot(start + u * segment, ray), 0.0, visible);
    u = clamp(dot(t * ray - start, segment) / max(dot(segment, segment), 0.000001), 0.0, 1.0);
    vec3 centre = start + u * segment;
    t = clamp(dot(centre, ray), 0.0, visible);
    vec3 radial = t * ray - centre;
    float rho = length(radial) / Radius;
    if (rho >= 1.0 || centre.z > Radius ) {
        fragColor = original;
        return;
    }

    float trail = u * length(segment);
    float angle = trail * 1.44 - Age * 0.82 + rho * 3.0;
    float wave = sin(angle + 0.55 * sin(trail * 0.71 + Age * 0.27));
    float edge = 1.0 - smoothstep(0.68, 1.0, rho);
    vec2 normal = radial.xy / Radius;
    vec2 curl = vec2(-normal.y, normal.x);
    vec2 lens = normal * (-1.35 + 0.67 * wave) + curl * (0.35 * sin(angle * 0.78));
    // The mouth gathers its surroundings. Travelling fronts alternately compress and stretch the scene.
    vec2 pixelRadius = 0.5 * projection * Radius / max(1.0, -centre.z);
    vec2 shift = clamp(lens * pixelRadius * edge * Strength, vec2(-0.095), vec2(0.095));
    vec2 guard = 0.5 / InSize;
    vec2 uv = clamp(texCoord + shift, guard, 1.0 - guard);
    // A foreground pillar must not bleed into a ray that passes behind it.
    if (sceneDepth(uv) < -centre.z - Radius) uv = texCoord;
    vec2 dispersion = (uv - texCoord) * (0.032 + 0.019 * wave);
    vec3 colour = texture(DiffuseSampler, uv).rgb;
    colour.r = texture(DiffuseSampler, clamp(uv + dispersion, guard, 1.0 - guard)).r;
    colour.b = texture(DiffuseSampler, clamp(uv - dispersion, guard, 1.0 - guard)).b;
    // A bowed compression front travels away from the mouth. The dark crest
    // transmits the sampled scene, while the trough between waves stays clear.
    float phase = trail * 0.93 - Age * 0.75 + 3.9 * rho * rho;
    float crest = 0.5 + 0.5 * cos(phase);
    float shadow = smoothstep(0.54, 0.95, crest);
    float shoulder = pow(max(0.0, cos(phase - 1.22)), 10.0);
    float density = (1.0 - smoothstep(0.67, 1.0, rho)) * Strength;
    colour *= 1.0 - density * (0.08 + 0.80 * shadow);
    colour += density * shoulder * (0.027 + 0.095 * colour);
    fragColor = vec4(colour, original.a);
}
