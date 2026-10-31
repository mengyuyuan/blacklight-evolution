package com.codex.blacklight.infection;

import java.util.*;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;

/** Bind-time corridors between parallel limbs. Growth cannot fill the walking
 * gap while joint positions remain on the original skeleton. No species list,
 * world scan, render-frame collision test or extra entity is involved. */
public final class TissueSilhouette {
    private TissueSilhouette() {}
    public static List<Bone> bind(List<Bone> bones){
        var fitted=new ArrayList<Bone>();
        for(var bone:bones){
            var gaps=new ArrayList<Clearance>();V axis=axis(bone.box());
            if(limb(bone)&&Math.abs(axis.dot(bone.up()))>.85F)for(var other:bones){
                if(other==bone||!limb(other))continue;
                V otherAxis=axis(other.box());
                if(Math.abs(otherAxis.dot(other.up()))<.85F)continue;
                V delta=bone.right().mul(other.modelCentre().x()-bone.modelCentre().x())
                        .sub(bone.up().mul(other.modelCentre().y()-bone.modelCentre().y()))
                        .sub(bone.forward().mul(other.modelCentre().z()-bone.modelCentre().z()));
                float axial=Math.abs(delta.dot(axis));
                if(axial>(span(bone.box(),axis)+span(other.box(),otherAxis))*.35F)continue;
                V radial=delta.sub(axis.mul(delta.dot(axis)));float distance=radial.length();
                if(distance<.25F)continue;
                V direction=radial.mul(1/distance);float nativeHalf=span(bone.box(),direction)*.5F;
                // Do not "repair" intentional native intersections, or squeeze
                // one naturally broad limb into half of an asymmetric pair.
                float limit=Math.max(nativeHalf,distance*.5F-Math.min(.18F,nativeHalf*.08F));
                if(limit>nativeHalf*2)continue;
                gaps.add(new Clearance(direction,limit));
            }
            fitted.add(new Bone(bone.path(),bone.role(),bone.box(),bone.up(),bone.forward(),bone.right(),bone.modelCentre(),List.copyOf(gaps)));
        }
        return List.copyOf(fitted);
    }
    public static V fit(Bone bone,V point){
        if(bone.clearances()==null||bone.clearances().isEmpty())return point;
        V result=point,centre=bone.box().centre();
        for(var gap:bone.clearances()){
            float radius=result.sub(centre).dot(gap.direction());
            if(radius>gap.distance())result=result.sub(gap.direction().mul(radius-gap.distance()));
            else if(radius< -gap.distance())result=result.sub(gap.direction().mul(radius+gap.distance()));
        }
        return result;
    }
    /** Grafts emerge from the surviving native body, not from an invisible
     * procedural replacement shell whose surface was farther out. */
    public static V attachmentShift(Bone bone,V root){
        var box=bone.box();
        return new V(com.codex.blacklight.forge.WingMath.clamp(root.x(),box.low().x()+.025F,box.high().x()-.025F),
                com.codex.blacklight.forge.WingMath.clamp(root.y(),box.low().y()+.025F,box.high().y()-.025F),
                com.codex.blacklight.forge.WingMath.clamp(root.z(),box.low().z()+.025F,box.high().z()-.025F)).sub(root);
    }
    /** Bury the root, preserving the exposed outer organ. Translating every
     * ring by the root offset used to hide whole plates inside the host. */
    public static void graft(float[] vertices,V shift) {
        int rings=vertices.length/24;
        for(int ring=0;ring<rings;ring++) {
            float weight=1-TissueAnatomy.smooth(ring/(float)Math.max(1,rings-1));
            for(int i=ring*24;i<ring*24+24;i+=3){vertices[i]+=shift.x()*weight;vertices[i+1]+=shift.y()*weight;vertices[i+2]+=shift.z()*weight;}
        }
    }
    static Mesh fit(Bone bone,Mesh mesh){
        if(bone.clearances()==null||bone.clearances().isEmpty())return mesh;
        float[][] keys=new float[mesh.stages().length][];
        for(int stage=0;stage<keys.length;stage++){
            keys[stage]=mesh.stages()[stage].clone();V root=centre(keys[stage]),shift=fit(bone,root).sub(root);
            for(int i=0;i<keys[stage].length;i+=3){
                V p=new V(keys[stage][i],keys[stage][i+1],keys[stage][i+2]);p=mesh.skin()?fit(bone,p):p.add(shift);
                keys[stage][i]=p.x();keys[stage][i+1]=p.y();keys[stage][i+2]=p.z();
            }
        }
        float[][] pivots=mesh.pivots()==null?null:new float[mesh.pivots().length][];
        if(pivots!=null)for(int stage=0;stage<pivots.length;stage++){
            float[] p=mesh.pivots()[stage];pivots[stage]=fit(bone,new V(p[0],p[1],p[2])).array();
        }
        return new Mesh(mesh.name(),mesh.skin(),mesh.phase(),mesh.motion(),mesh.axis(),pivots,keys,mesh.faces());
    }
    private static boolean limb(Bone b){return b.role()>=2&&b.role()<=5;}
    private static V centre(float[] points){V c=new V(0,0,0);for(int i=0;i<24;i+=3)c=c.add(new V(points[i],points[i+1],points[i+2]));return c.mul(.125F);}
    private static V axis(Box b){return b.span(0)>b.span(1)&&b.span(0)>b.span(2)?new V(1,0,0):b.span(1)>b.span(2)?new V(0,1,0):new V(0,0,1);}
    private static float span(Box b,V v){return Math.abs(v.x())*b.span(0)+Math.abs(v.y())*b.span(1)+Math.abs(v.z())*b.span(2);}
}
