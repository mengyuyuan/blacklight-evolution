package com.codex.blacklight.infection;

import static com.codex.blacklight.infection.TissueMorphogenesis.*;

/** Unending, saved-age-driven development of a fixed-budget organ vocabulary.
 * Two adjacent generations are sampled in constant time; no history or meshes
 * accumulate. Genotype and age are server data, never render-frame randomness. */
public final class TissueEvolution {
    public static final long GENERATION_TICKS=24000;
    public record State(long generation,float muscle,float carapace,float jaw,float grasp,float crest,TissueAggregate.Form aggregate) {
        public State(long generation,float muscle,float carapace,float jaw,float grasp,float crest){this(generation,muscle,carapace,jaw,grasp,crest,TissueAggregate.Form.NONE);}
        public boolean active(){return muscle+carapace+jaw+grasp+crest>0||aggregate.active();}
    }
    private static final State BASE=new State(0,0,0,0,0,0);
    private static final float[][] FUNCTIONAL_SUPPORT={
            {.90F,.35F,.10F,.48F,.12F},{.72F,.78F,.08F,.70F,.18F},{.65F,.28F,.10F,1.05F,.12F},
            {.75F,.68F,.12F,.90F,.42F},{.85F,.92F,.10F,.85F,.28F},{.90F,.95F,.30F,.15F,.90F},{.65F,.40F,1.10F,.20F,.20F},
            {1.15F,.50F,.10F,.45F,.20F},{1.20F,.65F,.10F,.55F,.18F},{.65F,.30F,.95F,.65F,.20F},{.65F,.45F,.20F,.85F,.35F}};
    public record Combat(double health,double damage,double armor,double knockback) { }
    private TissueEvolution() {}

    public static State sample(long seed,long age) {
        return sample(seed,age,TissueCore.forSeed(seed));
    }
    private static State sample(long seed,long age,TissueCore primary) {
        long elapsed=Math.max(0,TissueGrowth.clamp(age)-TissueGrowth.ANCIENT_TICKS);
        if(elapsed==0)return BASE;
        long generation=elapsed/GENERATION_TICKS;
        float t=elapsed%GENERATION_TICKS/(float)GENERATION_TICKS;
        t=t*t*(3-2*t);
        State a=generation==0?BASE:genotype(seed,generation,primary),b=genotype(seed,generation+1,primary);
        return new State(generation,lerp(a.muscle,b.muscle,t),lerp(a.carapace,b.carapace,t),
                lerp(a.jaw,b.jaw,t),lerp(a.grasp,b.grasp,t),lerp(a.crest,b.crest,t));
    }
    private static State genotype(long seed,long generation) {
        return genotype(seed,generation,TissueCore.forSeed(seed));
    }
    private static State genotype(long seed,long generation,TissueCore primary) {
        long key=TissueGrowth.mix(seed^TissueGrowth.mix(generation));
        int focus=primary.structuralBias().ordinal();
        float[] traits=new float[5];
        for(int i=0;i<traits.length;i++)traits[i]=i==focus?.8F+.2F*TissueGrowth.sample(key,810+i)
                :.12F+.33F*TissueGrowth.sample(key,810+i);
        // Long grasping/biting organs always retain supporting muscle and armour.
        traits[0]=Math.max(traits[0],Math.max(traits[2],traits[3])*.55F);
        traits[1]=Math.max(traits[1],traits[4]*.5F);
        return new State(generation,traits[0],traits[1],traits[2],traits[3],traits[4]);
    }
    /** Every functional organ develops its supporting body, not only its weapon.
     * Profiles share the skin/socket deformation field without granting unrelated attacks. */
    public static State nativeState(TissueCore core,long seed,long age,boolean authored){
        if(core.legacy())return authored?authoredState(seed,age,core):sample(seed,age,core);
        float[] profile=FUNCTIONAL_SUPPORT[core.ordinal()-5];
        float g=attachmentGrowth(age)*(.9F+.2F*TissueGrowth.sample(seed,978+core.ordinal()));
        return new State(Math.max(0,(age-TissueGrowth.ANCIENT_TICKS)/GENERATION_TICKS),g*profile[0],g*profile[1],g*profile[2],g*profile[3],g*profile[4]);
    }
    public static boolean supportingMesh(TissueCore core,String mesh){
        if(mesh.startsWith("scapular_plate")||mesh.startsWith("costal_arch"))return core==TissueCore.HYDRAULIC||core==TissueCore.CHELA||core==TissueCore.BLADE||core==TissueCore.HORN;
        if(mesh.startsWith("shoulder_crest"))return core==TissueCore.HORN||core==TissueCore.BLADE;
        return core==TissueCore.PHARYNX&&TissueGraftLayout.owns(TissueCore.JAW,mesh);
    }
    /** Additional remodeling of an already developed donor. Never duplicate its
     * captured initial bulk or shrink a stronger inherited phenotype. */
    public static State additionalDevelopment(State captured,State current){
        return new State(current.generation(),extra(captured.muscle,current.muscle,.38F),
                extra(captured.carapace,current.carapace,.22F),extra(captured.jaw,current.jaw,.48F),
                extra(captured.grasp,current.grasp,.22F),extra(captured.crest,current.crest,.10F));
    }
    private static float extra(float before,float after,float response){return Math.max(0,after-before)/(1+response*before);}
    public static State retainStronger(State a,State b){return new State(Math.max(a.generation,b.generation),
            Math.max(a.muscle,b.muscle),Math.max(a.carapace,b.carapace),Math.max(a.jaw,b.jaw),Math.max(a.grasp,b.grasp),Math.max(a.crest,b.crest));}
    /** Dedicated host anatomy persists; later organs bud on that same body. */
    public static float attachmentGrowth(long age){return TissueAnatomy.smooth((TissueGrowth.clamp(age)-12000)/36000F);}
    public static State authoredState(long seed,long age){
        return authoredState(seed,age,TissueCore.forSeed(seed));
    }
    private static State authoredState(long seed,long age,TissueCore primary){
        float growth=attachmentGrowth(age);if(growth<=0)return BASE;
        var first=genotype(seed,1,primary);var current=sample(seed,age,primary);
        return new State(current.generation,.65F*growth*first.muscle+.35F*current.muscle,
                .65F*growth*first.carapace+.35F*current.carapace,.65F*growth*first.jaw+.35F*current.jaw,
                .65F*growth*first.grasp+.35F*current.grasp,.65F*growth*first.crest+.35F*current.crest);
    }
    /** Continuous, diminishing whole-skeleton development complements local
     * organ differentiation. There is no final biological stage or linear runaway. */
    public static float desiredScale(long age) {
        double elapsed=Math.max(0,TissueGrowth.clamp(age)-TissueGrowth.ANCIENT_TICKS);
        return (float)(1+.30*Math.log1p(elapsed/GENERATION_TICKS));
    }
    /** Sublinear, age-derived combat growth. Stable IDs replace modifiers, so
     * neither saves nor repeated updates compound the previous generation. */
    public static Combat combat(long seed,long age) {
        return combat(seed,age,TissueCore.forSeed(seed));
    }
    public static Combat combat(long seed,long age,TissueCore primary) {
        double elapsed=Math.max(0,TissueGrowth.clamp(age)-TissueGrowth.ANCIENT_TICKS);
        double growth=Math.log1p(elapsed/GENERATION_TICKS);
        // Inherited allocation is fixed. Remodeling noise must not make an
        // older generation weaker than yesterday's differently shaped organs.
        State state=genotype(seed,1,primary);
        double maturity=TissueGrowth.progress(age);
        return new Combat(1.8*maturity+.90*growth*(1+.35*state.muscle+.30*state.carapace),
                .075*growth*(1+.8*state.muscle+.65*state.jaw),
                maturity*(1.5+6.5*state.carapace)+2*growth*state.carapace,.6*-Math.expm1(-.12*growth*state.crest));
    }
    public static float tendrilLength(State s){return 1+.30F*s.grasp+.10F*s.muscle;}
    public static float tendrilGirth(State s){return 1+.24F*s.muscle+.14F*s.grasp;}
    public static float tentacles(State s){float t=Math.clamp((s.grasp-.5F)/.3F,0,1);return t*t*(3-2*t);}
    public static int tentacleCount(long seed){return TissueGrowth.sample(seed,845)<.5F?4:6;}

    /** One bone-local field for the skin, embedded organs, pivots and gland.
     * Native joint rotation and inter-part spacing remain owned by the skeleton;
     * global growth scales that skeleton and its collider together. */
    public static V develop(Bone b,V p,State s) {
        return develop(b,p,s,b.role()==4||b.role()==5||TissueMorphogenesis.legBone(b.path()));
    }
    public static V develop(Bone b,V p,State s,boolean groundSupport) {
        return TissueAggregate.develop(b,developNative(b,p,s,groundSupport),s.aggregate,groundSupport);
    }
    private static V developNative(Bone b,V p,State s,boolean groundSupport) {
        if(!s.active())return p;
        Box box=b.box();V c=box.centre(),d=p.sub(c),right=b.right(),up=b.up(),forward=b.forward();
        if(b.role()==0) {
            V along=axis(box);float length=span(box,along),t=Math.clamp(d.dot(along)/length+.5F,0,1);
            float support=(float)Math.sin(Math.PI*t);
            boolean upright=Math.abs(along.dot(up))>.65F;
            V dorsal=upright?forward.mul(-1):up;
            float side=d.dot(right),back=d.dot(dorsal);
            return p.add(right.mul(side*(.38F*s.muscle+.14F*s.carapace)*support))
                    .add(dorsal.mul(back*(.26F*s.muscle+.22F*s.carapace)*support))
                    .add(dorsal.mul(span(box,dorsal)*.10F*s.crest*support));
        }
        if(b.role()==1) {
            float depth=span(box,forward);V hinge=c.sub(forward.mul(depth*.4F));
            float front=Math.max(0,p.sub(hinge).dot(forward));
            float weight=Math.clamp(front/(depth*.9F),0,1);
            return p.add(forward.mul(front*(.48F*s.jaw+.04F*s.grasp)))
                    .add(right.mul(d.dot(right)*(.26F*s.jaw+.14F*s.carapace)*weight))
                    .add(up.mul(d.dot(up)*(.12F*s.jaw+.13F*s.carapace)*weight));
        }
        if(b.role()<2||b.role()>5)return p;
        V along=axis(box);float length=span(box,along);
        float raw=d.dot(along)/length+.5F,t=Math.clamp(raw,0,1);
        float sign=Math.abs(box.low().dot(along))>Math.abs(box.high().dot(along))?-1:1;
        float distal=sign>0?t:1-t;
        V section=c.add(along.mul(d.dot(along))),radial=p.sub(section);
        float belly=(float)Math.sin(Math.PI*t);
        float width=1+.38F*s.muscle*belly+.12F*s.grasp*distal;
        float depth=1+.38F*s.muscle*belly+.10F*s.carapace*distal;
        V across=right.sub(along.mul(right.dot(along)));
        if(across.length()<.1F)across=forward.sub(along.mul(forward.dot(along)));
        across=across.unit();V thickness=along.cross(across).unit();
        V result=section.add(across.mul(radial.dot(across)*width)).add(thickness.mul(radial.dot(thickness)*depth));
        // The foot plane remains planted; horizontal grasping limbs may reach out.
        boolean planted=groundSupport&&along.mul(sign).dot(up)<-.65F;
        float beyond=Math.max(0,sign>0?raw-1:-raw);
        if(!planted)result=result.add(along.mul(sign*length*.22F*s.grasp*beyond));
        else {
            result=result.add(forward.mul(length*.10F*s.grasp*distal));
            float floor=c.dot(up)-span(box,up)*.5F;
            result=result.add(up.mul(Math.max(0,floor-result.dot(up))));
        }
        return TissueSilhouette.fit(b,result);
    }
    /** Hard organs grow from the same developed attachment. Length never outruns its cross-section. */
    public static void organ(float[] vertices,Bone bone,String name,State s) {
        organ(vertices,bone,name,s,bone.role()==4||bone.role()==5||TissueMorphogenesis.legBone(bone.path()));
    }
    public static void organ(float[] vertices,Bone bone,String name,State s,boolean groundSupport) {
        if(!s.active())return;
        boolean follows=name.startsWith("tissue_")||name.equals("tendril_gland")||name.startsWith("scapular_plate")
                ||name.startsWith("costal_arch")||bone.role()==1;
        V root=centre(vertices,0),socket=develop(bone,root,s,groundSupport);
        int rings=vertices.length/24;
        for(int ring=0;ring<rings;ring++) {
            V section=centre(vertices,ring);
            float body=follows?0:.18F*s.muscle+.12F*s.carapace;
            float reach=1+Math.min(body*.9F,.18F*s.grasp+.14F*s.crest);
            V mapped=follows?develop(bone,section,s,groundSupport):socket.add(section.sub(root).mul(reach));
            for(int i=ring*24;i<ring*24+24;i+=3) {
                V point=new V(vertices[i],vertices[i+1],vertices[i+2]);
                V result=follows?develop(bone,point,s,groundSupport):mapped.add(point.sub(section).mul(1+body));
                if(!follows&&bone.role()>=2&&bone.role()<=5) {
                    V direction=axis(bone.box());
                    float sign=Math.abs(bone.box().low().dot(direction))>Math.abs(bone.box().high().dot(direction))?-1:1;
                    if(groundSupport&&direction.mul(sign).dot(bone.up())<-.65F) {
                        float floor=bone.box().centre().dot(bone.up())-span(bone.box(),bone.up())*.5F;
                        result=result.add(bone.up().mul(Math.max(0,floor-result.dot(bone.up()))));
                    }
                }
                vertices[i]=result.x();vertices[i+1]=result.y();vertices[i+2]=result.z();
            }
            if(name.equals("tendril_gland")) {
                V correction=mapped.sub(centre(vertices,ring));
                for(int i=ring*24;i<ring*24+24;i+=3){vertices[i]+=correction.x();vertices[i+1]+=correction.y();vertices[i+2]+=correction.z();}
            }
        }
    }
    private static V centre(float[] vertices,int ring) {
        V sum=new V(0,0,0);for(int i=ring*24;i<ring*24+24;i+=3)sum=sum.add(new V(vertices[i],vertices[i+1],vertices[i+2]));
        return sum.mul(.125F);
    }
    private static V axis(Box b){return b.span(0)>b.span(1)&&b.span(0)>b.span(2)?new V(1,0,0):b.span(1)>b.span(2)?new V(0,1,0):new V(0,0,1);}
    private static float span(Box b,V direction){return Math.abs(direction.x())*b.span(0)+Math.abs(direction.y())*b.span(1)+Math.abs(direction.z())*b.span(2);}
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
}
