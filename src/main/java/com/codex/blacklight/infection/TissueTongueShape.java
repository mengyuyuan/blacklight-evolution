package com.codex.blacklight.infection;

import static com.codex.blacklight.infection.TissueMorphogenesis.V;

/** Fits the existing connected rings to the actual prey. No frame-random points or extra meshes. */
public final class TissueTongueShape {
    private TissueTongueShape(){}
    public static float extension(float tick){
        var c=TissueOrganCycle.cycle(TissueCore.TONGUE);
        if(!Float.isFinite(tick)||tick<c.windup()||tick>=c.end())return 0;
        return TissueOrganCycle.smooth((tick-c.windup())/(c.contact()-c.windup()))
                *(1-TissueOrganCycle.smooth((tick-c.retract())/(c.end()-c.retract())));
    }
    public static void aim(float[] vertices,V target,float extension){
        if(vertices.length<48||vertices.length%24!=0||target==null||!finite(target))return;
        int rings=vertices.length/24;V root=centre(vertices,0),tip=centre(vertices,rings-1);
        V original=tip.sub(root),delta=target.sub(root);
        if(original.length()<1e-5F||delta.length()<1e-5F||!Float.isFinite(extension)||extension<=0)return;
        float e=Math.clamp(extension,0,1);
        V a=original.unit(),b=delta.unit();
        float length=original.length()+(delta.length()-original.length())*e;
        for(int ring=0;ring<rings;ring++){
            V old=centre(vertices,ring);float t=ring/(float)(rings-1);
            V next=root.add(b.mul(length*t));
            for(int i=ring*24;i<ring*24+24;i+=3){
                V section=new V(vertices[i],vertices[i+1],vertices[i+2]).sub(old);
                V rotated=rotate(section,a,b);V p=next.add(rotated);
                // The buried first ring stays exactly on its native mouth/socket.
                if(ring==0)continue;
                vertices[i]=p.x();vertices[i+1]=p.y();vertices[i+2]=p.z();
            }
        }
    }
    private static V rotate(V v,V a,V b){
        float dot=Math.clamp(a.dot(b),-1,1);V cross=a.cross(b);
        if(dot<-.9999F){V axis=a.cross(Math.abs(a.y())<.9?new V(0,1,0):new V(1,0,0)).unit();return axis.mul(2*axis.dot(v)).sub(v);}
        return v.add(cross.cross(v)).add(cross.cross(cross.cross(v)).mul(1/(1+dot)));
    }
    private static V centre(float[] v,int ring){V p=new V(0,0,0);for(int i=ring*24;i<ring*24+24;i+=3)p=p.add(new V(v[i],v[i+1],v[i+2]));return p.mul(.125F);}
    private static boolean finite(V v){return Float.isFinite(v.x())&&Float.isFinite(v.y())&&Float.isFinite(v.z());}
}
