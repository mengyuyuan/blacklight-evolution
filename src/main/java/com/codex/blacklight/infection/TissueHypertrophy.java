package com.codex.blacklight.infection;

import java.util.Arrays;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;

/** Coordinated attachment, organ volume and tip development. Original mature
 * keys and topology stay immutable; all changes live in the real bone frame. */
public final class TissueHypertrophy {
    private record Growth(float root,float core,float tip,float vigor) {}
    private TissueHypertrophy() {}
    public static Mesh extend(Bone bone,Mesh mesh) { return extend(bone,mesh,0); }
    public static Mesh extend(Bone bone,Mesh mesh,long seed) {
        float[][] keys=Arrays.copyOf(mesh.stages(),5);
        for(int stage=1;stage<=2;stage++) {
            Growth growth=growth(stage==1?.5F:1,seed,bone.role(),mesh.phase());
            float[] mature=mesh.stages()[2],grown=mature.clone();
            V root=centre(mature,0);
            V across=ringAxis(mature,0),depth=ringAxis(mature,1);
            float length=0;
            for(int ring=1;ring<mature.length/24;ring++)length+=centre(mature,ring).sub(centre(mature,ring-1)).length();
            for(int i=0;i<grown.length;i+=3) {
                V p=point(mature,i);
                if(mesh.skin()||bone.role()==1)p=surface(bone,p,growth,seed);
                else {
                    int ring=i/24;
                    float t=ring/(float)(mature.length/24-1);
                    V section=centre(mature,ring);
                    V developed;
                    boolean followsSurface=mesh.name().equals("tendril_gland")
                            ||mesh.name().startsWith("scapular_plate")||mesh.name().startsWith("costal_arch");
                    if(followsSurface)developed=surface(bone,section,growth,seed);
                    else developed=organCentre(bone,mesh,root,section,t,length,growth,seed);
                    float belly=(float)Math.sin(Math.PI*t);
                    float width=1+(.24F*growth.root+.16F*growth.core*belly)*growth.vigor;
                    float thickness=1+(.23F*growth.root+.17F*growth.core*belly)*growth.vigor;
                    V radial=p.sub(section);
                    p=developed.add(across.mul(radial.dot(across)*width)).add(depth.mul(radial.dot(depth)*thickness));
                }
                if(planted(bone)) {
                    float floor=bone.box().centre().dot(bone.up())-span(bone.box(),bone.up())*.5F;
                    p=p.add(bone.up().mul(Math.max(0,floor-p.dot(bone.up()))));
                }
                put(grown,i,p);
            }
            keys[stage+2]=grown;
        }
        float[][] pivots=extendPoints(mesh.pivots(),bone,seed,mesh.phase());
        return new Mesh(mesh.name(),mesh.skin(),mesh.phase(),mesh.motion(),mesh.axis(),pivots,keys,mesh.faces());
    }
    public static float[][] extendPoints(float[][] original,Bone bone) { return extendPoints(original,bone,0); }
    public static float[][] extendPoints(float[][] original,Bone bone,long seed) { return extendPoints(original,bone,seed,0); }
    private static float[][] extendPoints(float[][] original,Bone bone,long seed,int phase) {
        if(original==null)return null;
        float[][] points=Arrays.copyOf(original,5);
        V p=new V(original[2][0],original[2][1],original[2][2]);
        for(int stage=1;stage<=2;stage++) {
            V result=surface(bone,p,growth(stage==1?.5F:1,seed,bone.role(),phase),seed);
            points[stage+2]=new float[]{bounded(result.x()),bounded(result.y()),bounded(result.z())};
        }
        return points;
    }
    /** The attachment develops before the organ belly, and reach follows mass.
     * A saved genotype sets only small coherent variations, never frame noise. */
    private static Growth growth(float amount,long seed,int role,int phase) {
        float a=Math.clamp(amount,0,1);
        float vigor=(.94F+.12F*TissueGrowth.sample(seed,701+role))*(phase==1?1.06F:phase==2?.94F:1);
        return new Growth(TissueAnatomy.smooth(a/.72F),TissueAnatomy.smooth((a-.08F)/.92F),
                TissueAnatomy.smooth((a-.22F)/.78F),vigor);
    }
    private static V surface(Bone bone,V p,Growth g,long seed) {
        if(bone.role()==0)return body(bone,p,g,seed);
        if(bone.role()==1)return head(bone,p,g);
        if(bone.role()>=2&&bone.role()<=5)return limb(bone,p,g);
        return p;
    }
    private static V body(Bone bone,V p,Growth g,long seed) {
        Box box=bone.box();V along=longAxis(box),centre=box.centre(),d=p.sub(centre);
        float t=Math.clamp(d.dot(along)/span(box,along)+.5F,0,1);
        boolean upright=Math.abs(along.dot(bone.up()))>.65F;
        float peak=upright||along.dot(bone.forward())<=0?1/3F:2/3F;
        float shoulder=TissueAnatomy.smooth(t<=peak?t/peak:(1-t)/(1-peak));
        V dorsal=upright?bone.forward().mul(-1):bone.up();
        float side=d.dot(bone.right()),rise=d.dot(dorsal);
        float preferred=TissueOrgans.dominantArm(seed)==3?1:-1;
        float asym=side*preferred>=0?1.07F:.94F;
        float mass=(.45F*g.root+.55F*g.core)*g.vigor;
        float axialDirection=upright?-1:Math.signum(along.dot(bone.forward()));
        // Shoulder, chest and waist change as connected volumes. The neck/hip
        // sockets stay put while intermediate sections can shift in depth too.
        return p.add(bone.right().mul(side*.14F*mass*asym*shoulder))
                .add(dorsal.mul(rise*(rise>0?.14F:.09F)*mass*shoulder))
                .add(along.mul(span(box,along)*.035F*g.core*shoulder*axialDirection))
                .add(dorsal.mul(span(box,dorsal)*.02F*g.root*shoulder));
    }
    private static V head(Bone bone,V p,Growth g) {
        Box box=bone.box();V centre=box.centre(),forward=bone.forward(),right=bone.right();
        float depth=span(box,forward)*.5F;
        V hinge=centre.sub(forward.mul(depth*.8F));
        float front=Math.max(0,p.sub(hinge).dot(forward));
        float weight=Math.clamp(front/(depth*1.8F),0,1);
        // Skull, palate, jaw and tooth roots share this field and their actual
        // hinges, so the expanding cranium cannot leave the mouth behind.
        return p.add(forward.mul(front*(.08F*g.tip+.025F*g.core)*g.vigor))
                .add(right.mul(p.sub(centre).dot(right)*(.13F*g.root+.11F*g.core)*weight*g.vigor))
                .add(bone.up().mul(p.sub(centre).dot(bone.up())*(.11F*g.root+.13F*g.core)*weight*g.vigor));
    }
    private static V limb(Bone bone,V p,Growth g) {
        Box box=bone.box();V along=longAxis(box),d=p.sub(box.centre());
        float t=Math.clamp(d.dot(along)/span(box,along)+.5F,0,1);
        float distal=Math.abs(box.low().dot(along))>Math.abs(box.high().dot(along))?1-t:t;
        float belly=(float)Math.sin(Math.PI*t),palm=TissueAnatomy.smooth(distal);
        V section=box.centre().add(along.mul(d.dot(along)));
        V first=axis((principal(along)+1)%3),second=axis((principal(along)+2)%3);
        V radial=p.sub(section);
        float width=1+(.16F*g.core*belly+.12F*g.root*palm)*g.vigor;
        float depth=1+(.16F*g.core*belly+.11F*g.root*palm)*g.vigor;
        // Small centre-line reshaping complements both cross-section axes.
        // The joint and planted end centres are stationary, not longer legs.
        V bend=bone.forward().mul(span(box,along)*.020F*g.core*belly)
                .add(along.mul(span(box,along)*.018F*g.core*(float)Math.sin(t*Math.PI*2)));
        return TissueSilhouette.fit(bone,section.add(bend).add(first.mul(radial.dot(first)*width)).add(second.mul(radial.dot(second)*depth)));
    }
    private static V organCentre(Bone bone,Mesh mesh,V root,V section,float t,float length,Growth g,long seed) {
        V socket=surface(bone,root,g,seed),d=section.sub(root);
        float rootWidth=1+.24F*g.root*g.vigor,rootDepth=1+.23F*g.root*g.vigor;
        // Reach is constrained by the supporting root's cross-section. Its
        // later curve develops with the tip instead of making a thin spear.
        float supported=Math.min(.28F,(float)Math.sqrt(rootWidth*rootDepth)-1);
        float reach=1+.85F*supported*g.tip;
        V result;
        if(mesh.motion().equals("toe")||planted(bone)) {
            V vertical=bone.up().mul(d.dot(bone.up()));
            result=socket.add(vertical).add(d.sub(vertical).mul(reach));
            float side=Math.signum(root.sub(bone.box().centre()).dot(bone.right()));
            result=result.add(bone.up().mul(length*.025F*g.tip*t*t))
                    .add(bone.right().mul(side*length*.022F*g.tip*t*t));
        } else {
            result=socket.add(d.mul(reach));
            float direction=Math.signum(d.dot(bone.forward()));
            result=result.add(bone.forward().mul(direction*length*.032F*g.core*(float)Math.sin(Math.PI*t)));
            if(mesh.name().startsWith("shoulder_crest")) {
                float side=Math.signum(root.sub(bone.box().centre()).dot(bone.right()));
                result=result.add(bone.right().mul(side*length*.025F*g.tip*t*t))
                        .add(bone.forward().mul(-length*.025F*g.tip*t*t));
            }
        }
        return result;
    }
    private static boolean planted(Bone bone) {
        if(!(bone.role()==4||bone.role()==5||TissueMorphogenesis.legBone(bone.path())))return false;
        V direction=longAxis(bone.box());
        if(Math.abs(bone.box().low().dot(direction))>Math.abs(bone.box().high().dot(direction)))direction=direction.mul(-1);
        return direction.dot(bone.up())<-.65F;
    }
    private static V ringAxis(float[] points,int axis) {
        V sum=new V(0,0,0);
        for(int s=0;s<8;s++) {
            boolean positive=axis==0?s>=2&&s<=5:s>=4;
            sum=sum.add(point(points,s*3).mul(positive?1:-1));
        }
        return sum.unit();
    }
    private static V longAxis(Box b) { return axis(b.span(0)>b.span(1)&&b.span(0)>b.span(2)?0:b.span(1)>b.span(2)?1:2); }
    private static int principal(V v) { return Math.abs(v.x())>.65F?0:Math.abs(v.y())>.65F?1:2; }
    private static V axis(int a) { return a==0?new V(1,0,0):a==1?new V(0,1,0):new V(0,0,1); }
    private static float span(Box b,V direction) {
        return Math.abs(direction.x())*b.span(0)+Math.abs(direction.y())*b.span(1)+Math.abs(direction.z())*b.span(2);
    }
    private static V centre(float[] points,int ring) {
        V result=new V(0,0,0);for(int i=ring*24;i<ring*24+24;i+=3)result=result.add(point(points,i));
        return result.mul(1/8F);
    }
    private static V point(float[] points,int i) { return new V(points[i],points[i+1],points[i+2]); }
    private static void put(float[] points,int i,V p) { points[i]=bounded(p.x());points[i+1]=bounded(p.y());points[i+2]=bounded(p.z()); }
    private static float bounded(float x) { return Math.clamp(x,-79.99F,79.99F); }
}
