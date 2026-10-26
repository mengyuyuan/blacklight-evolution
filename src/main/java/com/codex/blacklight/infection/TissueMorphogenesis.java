package com.codex.blacklight.infection;

import java.util.*;

/** Species-independent organ growth from native bone surfaces. No animal assets,
 * entity registry names, game/client classes or bounding-box pillars are inputs. */
public final class TissueMorphogenesis {
    public static final int MAX_BONES=16,MAX_FACES=1600;
    public record V(float x,float y,float z) {
        public V add(V v){return new V(x+v.x,y+v.y,z+v.z);}
        public V sub(V v){return new V(x-v.x,y-v.y,z-v.z);}
        public V mul(float s){return new V(x*s,y*s,z*s);}
        public float dot(V v){return x*v.x+y*v.y+z*v.z;}
        public V cross(V v){return new V(y*v.z-z*v.y,z*v.x-x*v.z,x*v.y-y*v.x);}
        public float get(int a){return a==0?x:a==1?y:z;}
        public float length(){return (float)Math.sqrt(dot(this));}
        public V unit(){return length()<1e-6F?new V(1,0,0):mul(1/length());}
        public float[] array(){return new float[]{x,y,z};}
    }
    public record Box(V low,V high) {
        public float span(int a){return high.get(a)-low.get(a);}
        public V centre(){return low.add(high).mul(.5F);}
        public float volume(){return span(0)*span(1)*span(2);}
        public boolean usable(){
            for(int a=0;a<3;a++)if(!Float.isFinite(low.get(a))||!Float.isFinite(high.get(a))
                    ||span(a)<.25F||span(a)>64||Math.abs(low.get(a))>52||Math.abs(high.get(a))>52)return false;
            return true;
        }
    }
    /** up/forward/right are model directions expressed in this bone's local bind frame. */
    public record Clearance(V direction,float distance) {}
    public record Bone(String path,int role,Box box,V up,V forward,V right,V modelCentre,List<Clearance> clearances) {
        public Bone(String path,int role,Box box,V up,V forward,V right,V modelCentre){this(path,role,box,up,forward,right,modelCentre,List.of());}
    }
    public record Mesh(String name,boolean skin,int phase,String motion,float[] axis,float[][] pivots,
                       float[][] stages,float[][] faces) {}
    public record Region(String name,String part,int anatomy,float[] reference,List<Mesh> meshes,float[][] outlet,Bone frame,boolean groundSupport) {}
    public record Plan(List<Region> regions,int faces,int organs) {}
    private record Section(V centre,float width,float depth) {}
    private enum BodyPlan { UPRIGHT, QUADRUPED, RADIAL }
    private static final V X=new V(1,0,0),Y=new V(0,1,0),Z=new V(0,0,1);
    private static final float[][] RING={{-1,-.82F},{-.82F,-1},{.82F,-1},{1,-.82F},{1,.82F},{.82F,1},{-.82F,1},{-1,.82F}};
    private TissueMorphogenesis(){}

    /** Whole semantic words, including camelCase; "heart" is not an ear/head. */
    public static int semanticRole(String name) {
        String normalized=name.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);
        Set<String> words=new HashSet<>(Arrays.asList(normalized.split("[^a-z0-9]+")));
        if(!Collections.disjoint(words,Set.of("head","skull")))return 1;
        if(words.contains("tail"))return 6;
        if(!Collections.disjoint(words,Set.of("body","torso","chest","ribcage","spine","pelvis","hips")))return 0;
        boolean left=words.contains("left")||words.contains("l"),right=words.contains("right")||words.contains("r");
        if(!left&&!right)return -1;
        if(!Collections.disjoint(words,Set.of("arm","forearm","hand","elbow","shoulder","wing","foreleg"))||words.contains("front")&&words.contains("leg"))return left?3:2;
        if(!Collections.disjoint(words,Set.of("leg","thigh","shin","knee","foot","ankle","hindleg")))return left?5:4;
        return -1;
    }

    /** Geometry fallback for custom models whose bones do not use familiar names. */
    public static List<Bone> inferRoles(List<Bone> input) {
        var usable=input.stream().filter(b->b.box.usable()).sorted(Comparator.comparing(Bone::path)).toList();if(usable.isEmpty())return List.of();
        V average=new V(0,0,0);for(var b:usable)average=average.add(b.modelCentre);average=average.mul(1F/usable.size());
        V centre=average;float scale=usable.stream().map(b->Math.max(b.box.span(0),Math.max(b.box.span(1),b.box.span(2)))).max(Float::compare).orElse(1F);
        Bone trunk=usable.stream().filter(b->b.role==0).max(Comparator.comparingDouble(b->b.box.volume())).orElseGet(()->usable.stream()
                .max(Comparator.comparingDouble(b->b.box.volume()/(1+b.modelCentre.sub(centre).length()/scale))).orElseThrow());
        Bone head=usable.stream().filter(b->b.role==1).findFirst().orElse(null);
        if(head==null)head=usable.stream().filter(b->b!=trunk&&b.role<0)
                .filter(b->Math.max(b.box.span(0),Math.max(b.box.span(1),b.box.span(2)))/Math.min(b.box.span(0),Math.min(b.box.span(1),b.box.span(2)))<2.25F)
                .filter(b->trunk.modelCentre.y-b.modelCentre.y+trunk.modelCentre.z-b.modelCentre.z>scale*.25F)
                .max(Comparator.comparingDouble(b->trunk.modelCentre.y-b.modelCentre.y+trunk.modelCentre.z-b.modelCentre.z)).orElse(null);
        var result=new ArrayList<Bone>();
        for(var b:usable) {
            int role=b.role;
            if(role<0) {
                if(b==trunk)role=0;else if(b==head)role=1;
                else {
                    float ratio=b.box.span(longest(b.box))/Math.min(b.box.span(0),Math.min(b.box.span(1),b.box.span(2)));
                    boolean left=b.modelCentre.x>trunk.modelCentre.x;
                    boolean upright=Math.abs(trunk.up.dot(axis(longest(trunk.box))))>.65F;
                    boolean hind=upright?b.modelCentre.y>trunk.modelCentre.y+scale*.25F:b.modelCentre.z>trunk.modelCentre.z;
                    role=ratio>1.8F?(hind?(left?5:4):(left?3:2)):0;
                }
            }
            result.add(new Bone(b.path,role,b.box,b.up,b.forward,b.right,b.modelCentre));
        }
        return List.copyOf(result);
    }

    public static Plan generate(long seed,List<Bone> input,boolean organs) {
        return generate(seed,input,organs,-1);
    }
    public static Plan generate(long seed,List<Bone> input,boolean organs,int donorPlan) {
        // Volume, semantic role and the full bone path make selection independent
        // of map iteration order. Tiny details/invalid planes never become trunks.
        var bones=TissueSilhouette.bind(inferRoles(input).stream().sorted(Comparator
                .comparingInt((Bone b)->b.role==0?0:b.role==1?1:2)
                .thenComparing(Comparator.comparingDouble((Bone b)->b.box.volume()).reversed())
                .thenComparing(Bone::path)).limit(MAX_BONES).toList());
        Bone trunk=bones.stream().filter(b->b.role==0).findFirst().orElse(null);
        boolean upright=trunk!=null&&Math.abs(trunk.up.dot(axis(longest(trunk.box))))>.65F;
        BodyPlan shape=bones.stream().filter(b->b.role>=2&&b.role<=5).count()>=6?BodyPlan.RADIAL
                :upright?BodyPlan.UPRIGHT:BodyPlan.QUADRUPED;
        TissueCore core=TissueCore.forSeed(seed);
        var result=new ArrayList<Region>();int budget=0,organCount=0;
        for(var b:bones) {
            long key=TissueGrowth.mix(seed^b.path.hashCode());var meshes=new ArrayList<Mesh>();
            int phase=b.role==0||b.role==1?0:b.role==TissueOrgans.dominantArm(seed)?1:2;
            BodyPlan organShape=donorPlan>=0&&donorPlan<BodyPlan.values().length?BodyPlan.values()[donorPlan]:shape;
            if(b.role==1)head(meshes,b,key,organs,organShape,core);else envelope(meshes,b,key,phase,upright);
            float[][] outlet=null;
            if(organs&&b==trunk){outlet=gland(meshes,b,seed);carapace(meshes,b,key,upright,core);ribCage(meshes,b,key,upright,core);}
            if(organs&&b.role>=2&&b.role<=5&&(b.role==TissueOrgans.dominantArm(seed)||TissueGrowth.sample(key,3)>.3F)) {
                extremity(meshes,b,key,phase,upright,donorPlan);
            }
            for(int i=0;i<meshes.size();i++)meshes.set(i,TissueSilhouette.fit(b,TissueHypertrophy.extend(b,meshes.get(i),seed)));
            outlet=TissueHypertrophy.extendPoints(outlet,b,seed);
            int faces=meshes.stream().mapToInt(m->m.faces.length).sum();
            if(budget+faces>MAX_FACES)continue;
            organCount+=(int)meshes.stream().filter(m->!m.skin).count();
            budget+=faces;Box box=b.box;
            boolean support=b.role>=2&&b.role<=5&&(!upright||b.role>=4||legBone(b.path));
            result.add(new Region(b.path,b.path,b.role,new float[]{box.low.x,box.low.y,box.low.z,box.high.x,box.high.y,box.high.z},List.copyOf(meshes),outlet,b,support));
        }
        return new Plan(List.copyOf(result),budget,organCount);
    }
    /** First-batch functional structures, rooted to the actual native joint frame. */
    public static boolean tailBone(String path){return semanticRole(path.substring(path.lastIndexOf('/')+1))==6;}
    /** World-up is not a spider leg's length axis. Preserve the rod's true minor cross-section. */
    public static Bone functionalFrame(Bone b){
        if(b==null||b.role<2||b.role>5&&!(b.role==6&&tailBone(b.path)))return b;
        int major=longest(b.box);V axis=axis(major);
        float distal=Math.abs(b.box.low.get(major))>Math.abs(b.box.high.get(major))?-1:1;
        V up=axis.mul(-distal),right=b.right.sub(axis.mul(b.right.dot(axis)));
        if(right.length()<.01F)right=axis((major+1)%3);right=right.unit();
        V forward=up.cross(right).unit();if(forward.dot(b.forward)<0)forward=forward.mul(-1);
        return new Bone(b.path,b.role,b.box,up,forward,right,b.modelCentre,b.clearances);
    }
    public static List<Mesh> functional(TissueCore core,Bone b,int plan,long seed){
        var out=new ArrayList<Mesh>();if(b==null||!b.box.usable()||core.legacy())return List.of();
        var f=functionalFrame(b);var box=b.box;V c=box.centre(),up=f.up.unit(),front=f.forward.unit(),right=f.right.unit();
        float w=box.span(principal(right)),h=box.span(principal(up)),d=box.span(principal(front));
        boolean limb=b.role>=2&&b.role<=5,fore=b.role==2||b.role==3;
        float side=b.modelCentre.x<0?-1:1;V outer=right.mul(side);
        if(core==TissueCore.GILL_LAMELLA&&b.role==1){
            for(int sign:new int[]{-1,1}){
                V root=c.add(right.mul(sign*w*.30F)).sub(up.mul(h*.22F));
                organ(out,core.id+"_operculum_"+sign,0,"",right,null,List.of(new Section(root,h*.15F,d*.14F),
                        new Section(root.add(right.mul(sign*w*.17F)).sub(front.mul(d*.05F)),h*.19F,d*.18F),
                        new Section(root.add(right.mul(sign*w*.13F)).sub(front.mul(d*.23F)),h*.12F,d*.07F)),up,front,b,seed);
            }
        }else if(core.vital()&&core!=TissueCore.GILL_LAMELLA&&b.role==0){
            if(core==TissueCore.LUNG||core==TissueCore.AIRFLOW){
                for(int sign:new int[]{-1,1}){V root=c.add(up.mul(h*.25F)).add(right.mul(sign*w*.16F));
                    organ(out,core.id+"_lobe_"+sign,1,"",right,null,List.of(new Section(root,w*.10F,d*.13F),
                            new Section(root.sub(up.mul(h*.18F)),w*.20F,d*.23F),new Section(root.sub(up.mul(h*(core==TissueCore.AIRFLOW?.48F:.35F))),w*.12F,d*.17F)),right,front,b,seed);
                }
            }else if(core==TissueCore.BOOK_LUNG||core==TissueCore.TRACHEA){
                for(int sign:new int[]{-1,1}){V root=c.add(right.mul(sign*w*.34F)).sub(up.mul(h*.18F));
                    organ(out,core.id+"_vent_"+sign,2,"",right,null,List.of(new Section(root,w*.05F,d*.12F),
                            new Section(root.add(front.mul(d*.18F)).sub(up.mul(h*.08F)),w*.09F,d*.20F),
                            new Section(root.sub(front.mul(d*.18F)).sub(up.mul(h*(core==TissueCore.TRACHEA?.32F:.16F))),w*.045F,d*.16F)),right,front,b,seed);
                }
            }else if(core==TissueCore.BRANCHIAL_HEART){
                for(int sign:new int[]{-1,1}){V root=c.add(up.mul(h*.24F)).add(right.mul(sign*w*.22F));
                    organ(out,core.id+"_pump_"+sign,0,"",right,null,List.of(new Section(root,w*.07F,d*.09F),new Section(root.sub(up.mul(h*.13F)),w*.12F,d*.15F),new Section(root.sub(up.mul(h*.21F)),w*.07F,d*.08F)),right,front,b,seed);
                }
            }else{
                V root=c.add(up.mul(h*(core==TissueCore.HEART?.20F:-.08F))).add(right.mul(core==TissueCore.SPLEEN?-w*.24F:0)).add(front.mul(d*.12F));
                organ(out,core.id+"_reservoir",1,"",right,null,List.of(new Section(root,w*.08F,d*.10F),new Section(root.sub(up.mul(h*.14F)),w*.17F,d*.18F),new Section(root.sub(up.mul(h*.25F)),w*.07F,d*.08F)),right,front,b,seed);
            }
        }else if(core==TissueCore.GILL_RAKER&&b.role==1){
            for(int sign:new int[]{-1,1})for(int fold=0;fold<2;fold++){
                V root=c.add(right.mul(sign*w*.33F)).add(front.mul(d*(.05F+fold*.18F))).sub(up.mul(h*.22F));
                organ(out,core.id+"_sieve_"+sign+"_"+fold,0,"",right,null,List.of(new Section(root,w*.08F,d*.09F),
                        new Section(root.add(right.mul(sign*w*.12F)).sub(up.mul(h*.10F)),w*.10F,d*.12F),
                        new Section(root.add(front.mul(d*.12F)).sub(up.mul(h*.14F)),w*.035F,d*.05F)),right,front,b,seed);
            }
        }else if(core==TissueCore.EXTRAORAL&&b.role==1){
            for(int sign:new int[]{-1,1}){
                V root=c.add(front.mul(d*.32F)).add(up.mul(sign*h*.13F));
                organ(out,core.id+"_contact_"+sign,0,"",right,null,List.of(new Section(root,w*.18F,h*.06F),
                        new Section(root.add(front.mul(d*.25F)),w*.32F,h*.10F),
                        new Section(root.add(front.mul(d*.48F)),w*.38F,h*.04F)),right,up,b,seed);
            }
        }else if(core.digestive()&&core!=TissueCore.GILL_RAKER&&b.role==(core==TissueCore.CROP?1:0)){
            if(core==TissueCore.CROP){
                V root=c.sub(up.mul(h*.30F)).add(front.mul(d*.10F));
                organ(out,core.id+"_pouch",0,"",right,null,List.of(new Section(root,w*.20F,d*.18F),
                        new Section(root.sub(up.mul(h*.16F)).add(front.mul(d*.14F)),w*.30F,d*.29F),
                        new Section(root.sub(up.mul(h*.27F)),w*.19F,d*.18F)),right,front,b,seed);
            }else if(core==TissueCore.GIZZARD){
                V root=c.sub(up.mul(h*.22F)).add(front.mul(d*.14F));
                organ(out,core.id+"_ventral",2,"",right,null,List.of(new Section(root,w*.23F,d*.19F),
                        new Section(root.sub(up.mul(h*.22F)),w*.32F,d*.26F),
                        new Section(root.sub(up.mul(h*.31F)),w*.21F,d*.17F)),right,front,b,seed);
            }else if(core==TissueCore.FERMENT){
                V root=c.sub(up.mul(h*.23F)).sub(front.mul(d*.16F));
                organ(out,core.id+"_chamber",1,"",right,null,List.of(new Section(root,w*.24F,d*.17F),
                        new Section(root.sub(up.mul(h*.18F)).sub(front.mul(d*.11F)),w*.36F,d*.26F),
                        new Section(root.sub(up.mul(h*.25F)).add(front.mul(d*.13F)),w*.29F,d*.20F),
                        new Section(root.sub(up.mul(h*.27F)).add(front.mul(d*.27F)),w*.18F,d*.12F)),right,front,b,seed);
            }else if(core==TissueCore.ABSORPTIVE){
                V root=c.sub(up.mul(h*.26F)).add(right.mul(w*.13F));
                organ(out,core.id+"_fold",1,"",right,null,List.of(new Section(root,w*.14F,d*.14F),
                        new Section(root.sub(up.mul(h*.15F)).add(front.mul(d*.16F)),w*.21F,d*.20F),
                        new Section(root.sub(up.mul(h*.20F)).sub(front.mul(d*.17F)),w*.17F,d*.16F)),right,front,b,seed);
            }else if(core==TissueCore.PANCREAS){
                V root=c.sub(up.mul(h*.24F)).sub(right.mul(w*.16F));
                organ(out,core.id+"_lobe",1,"",right,null,List.of(new Section(root,w*.15F,d*.14F),
                        new Section(root.sub(up.mul(h*.13F)).add(front.mul(d*.15F)),w*.19F,d*.18F),
                        new Section(root.sub(up.mul(h*.19F)).add(front.mul(d*.24F)),w*.10F,d*.10F)),right,front,b,seed);
            }else{
                V root=c.sub(up.mul(h*.23F));
                organ(out,core.id+"_pad",2,"",right,null,List.of(new Section(root,w*.30F,d*.28F),
                        new Section(root.sub(up.mul(h*.20F)),w*.44F,d*.41F),
                        new Section(root.sub(up.mul(h*.28F)),w*.35F,d*.30F)),right,front,b,seed);
            }
        }else if(core==TissueCore.TENDON&&limb){
            V root=c.add(up.mul(h*.32F)).sub(front.mul(d*.37F));
            organ(out,core.id+"_cable",2,"",right,null,List.of(new Section(root,w*.22F,d*.11F),
                    new Section(c.sub(front.mul(d*.48F)),w*.13F,d*.13F),
                    new Section(c.sub(up.mul(h*.32F)).sub(front.mul(d*.35F)),w*.18F,d*.11F)),right,front,b,seed);
        }else if(core==TissueCore.HYDRAULIC&&limb){
            V root=c.add(up.mul(h*.22F));
            organ(out,core.id+"_pressure",2,"",right,null,List.of(new Section(root,w*.44F,d*.44F),
                    new Section(c.add(up.mul(h*.10F)),w*.55F,d*.54F),new Section(c.sub(up.mul(h*.07F)),w*.43F,d*.42F)),right,front,b,seed);
        }else if(core==TissueCore.HYDROSTAT&&(fore||b.role==6&&tailBone(b.path))){
            // A coiled muscular wrist belongs to the receiving limb. It uncoils during its own grab.
            var sections=new ArrayList<Section>();for(int i=0;i<6;i++){
                float t=i/5F,angle=-.35F+(float)Math.PI*1.35F*t,r=Math.min(w,d)*(.17F-.055F*t);
                V point=c.sub(up.mul(h*(.03F+.31F*t))).add(outer.mul(w*.40F*(float)Math.cos(angle))).add(front.mul(d*.40F*(float)Math.sin(angle)));
                sections.add(new Section(point,r,r));
            }organ(out,core.id+"_arm",1,"",right,null,sections,outer,up,b,seed);
        }else if(core==TissueCore.BLADE&&fore){
            V root=c.add(outer.mul(w*.40F)).add(up.mul(h*.30F));
            organ(out,core.id+"_edge",1,"",right,null,List.of(new Section(root,w*.24F,d*.17F),
                    new Section(root.sub(up.mul(h*.34F)).add(outer.mul(w*.58F)),w*.43F,d*.10F),
                    new Section(root.sub(up.mul(h*.72F)).add(outer.mul(w*.20F)).add(front.mul(d*.15F)),.025F,.035F)),outer,front,b,seed);
        }else if(core==TissueCore.CHELA&&fore){
            V root=c.add(outer.mul(w*.10F)).sub(up.mul(h*.28F));float length=Math.max(w*1.1F,Math.min(h*.30F,w*1.7F));
            for(int jaw:new int[]{-1,1}){
                V hinge=root.add(outer.mul(jaw*w*.28F));
                organ(out,core.id+(jaw<0?"_inner":"_outer"),1,"",up,null,List.of(new Section(hinge,w*.24F,d*.25F),
                        new Section(hinge.add(front.mul(length*.50F)).add(outer.mul(jaw*w*.30F)),w*.28F,d*.30F),
                        new Section(hinge.add(front.mul(length*.85F)).add(outer.mul(jaw*w*.10F)),w*.16F,d*.22F),
                        new Section(hinge.add(front.mul(length)).sub(outer.mul(jaw*w*.12F)),w*.10F,d*.13F)),outer,up,b,seed);
            }
        }else if(core==TissueCore.HORN&&b.role==1){
            V root=c.add(front.mul(d*.20F)).add(up.mul(h*.15F));
            organ(out,core.id+"_crown",0,"",right,null,List.of(new Section(root,w*.30F,h*.17F),
                    new Section(root.add(front.mul(d*.32F)).add(up.mul(h*.10F)),w*.21F,h*.12F),
                    new Section(root.add(front.mul(d*.66F)).add(up.mul(h*.20F)),.035F,.035F)),right,up,b,seed);
        }else if(core==TissueCore.PHARYNX&&b.role==1){
            for(int jaw:new int[]{-1,1}){
                V root=c.add(front.mul(d*.24F)).add(up.mul(jaw*h*.12F));
                organ(out,core.id+"_"+jaw,0,"",right,null,List.of(new Section(root,w*.27F,h*.1F),
                        new Section(root.add(front.mul(d*.42F)),w*.22F,h*.09F),
                        new Section(root.add(front.mul(d*.62F)).sub(up.mul(jaw*h*.18F)),w*.08F,h*.06F)),right,up,b,seed);
            }
        }else if(core==TissueCore.SADDLE&&fore){
            V root=c.add(up.mul(h*.35F)).sub(front.mul(d*.25F));
            organ(out,core.id+"_spring",2,"",right,null,List.of(new Section(root,w*.26F,d*.16F),
                    new Section(c.add(up.mul(h*.10F)).sub(front.mul(d*.65F)),w*.48F,d*.22F),
                    new Section(c.sub(up.mul(h*.24F)).sub(front.mul(d*.20F)),w*.28F,d*.18F)),right,front,b,seed);
        }else if(core==TissueCore.CLUB&&fore){
            V root=c.sub(up.mul(h*.12F));
            organ(out,core.id+"_head",1,"",right,null,List.of(new Section(root,w*.27F,d*.26F),
                    new Section(c.sub(up.mul(h*.34F)),w*.38F,d*.34F),
                    new Section(c.sub(up.mul(h*.53F)).add(front.mul(d*.14F)),w*.70F,d*.56F),
                    new Section(c.sub(up.mul(h*.70F)).add(front.mul(d*.12F)),w*.57F,d*.44F)),right,front,b,seed);
        }else if(core==TissueCore.TONGUE&&b.role==1){
            V root=c.add(front.mul(d*.30F)).sub(up.mul(h*.12F));
            organ(out,core.id+"_tether",0,"",right,null,List.of(new Section(root,w*.14F,h*.08F),
                    new Section(root.add(front.mul(d*.85F)),w*.12F,h*.07F),
                    new Section(root.add(front.mul(d*1.65F)),w*.09F,h*.06F),
                    new Section(root.add(front.mul(d*2.4F)),w*.19F,h*.10F)),right,up,b,seed);
        }else if(core==TissueCore.CNIDOCYTE&&fore){
            V root=c.add(up.mul(h*.24F)).add(outer.mul(w*.38F));
            organ(out,core.id+"_battery",1,"",right,null,List.of(new Section(root,w*.20F,d*.21F),
                    new Section(c.add(outer.mul(w*.52F)),w*.28F,d*.30F),
                    new Section(c.sub(up.mul(h*.35F)).add(outer.mul(w*.42F)),w*.20F,d*.20F),
                    new Section(c.sub(up.mul(h*.49F)).add(outer.mul(w*.45F)).add(front.mul(d*.24F)),w*.07F,d*.07F)),right,front,b,seed);
        }
        return out.stream().map(m->TissueSilhouette.fit(b,m)).toList();
    }
    /** Limbless/custom bodies grow a rooted functional socket instead of silently losing the organ. */
    public static List<Mesh> functionalFallback(TissueCore core,Bone body,int plan,long seed){
        if(core.legacy()||body==null||!body.box.usable())return List.of();
        return functional(core,functionalMount(core,body,seed),plan,seed);
    }
    public static Bone functionalMount(TissueCore core,Bone body,long seed){
        boolean sided=core==TissueCore.BLADE||core==TissueCore.CHELA||core==TissueCore.HYDROSTAT||core==TissueCore.CLUB||core==TissueCore.SADDLE||core==TissueCore.CNIDOCYTE;
        int role=(core.digestive()&&core!=TissueCore.CROP&&core!=TissueCore.GILL_RAKER||core.vital()&&core!=TissueCore.GILL_LAMELLA)?0:switch(core){case HORN,PHARYNX,TONGUE,CROP,EXTRAORAL,GILL_RAKER,GILL_LAMELLA->1;case TENDON,HYDRAULIC->4;default->2;};
        int side=!sided?0:core==TissueCore.BLADE||core==TissueCore.CLUB?TissueOrganLayout.bladeSide(seed):-TissueOrganLayout.bladeSide(seed);
        V c=body.box.centre(),half=body.box.high.sub(body.box.low).mul(sided?.24F:.32F);
        V centre=c.add(body.right.mul(side*span(body.box,body.right)*.38F));
        return new Bone(body.path+"/grown_"+side,role,new Box(centre.sub(half),centre.add(half)),body.up,body.forward,body.right,
                new V(side==0?body.modelCentre.x:side*8,body.modelCentre.y,body.modelCentre.z));
    }
    private static float span(Box box,V direction){return Math.abs(direction.x)*box.span(0)+Math.abs(direction.y)*box.span(1)+Math.abs(direction.z)*box.span(2);}
    private static int longest(Box b){return b.span(0)>b.span(1)&&b.span(0)>b.span(2)?0:b.span(1)>b.span(2)?1:2;}
    private static int principal(V v){return Math.abs(v.x)>Math.abs(v.y)&&Math.abs(v.x)>Math.abs(v.z)?0:Math.abs(v.y)>Math.abs(v.z)?1:2;}
    private static V axis(int a){return a==0?X:a==1?Y:Z;}
    private static float sample(long seed,int index,float lo,float hi){return lo+(hi-lo)*TissueGrowth.sample(seed,index);}
    static boolean legBone(String path) {
        String leaf=path.substring(path.lastIndexOf('/')+1).replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);
        Set<String> words=new HashSet<>(Arrays.asList(leaf.split("[^a-z0-9]+")));
        return !Collections.disjoint(words,Set.of("leg","foreleg","hindleg","foot","ankle","shin","knee","thigh"));
    }
    private static void envelope(List<Mesh> out,Bone b,long key,int phase,boolean upright) {
        Box box=b.box;int along=longest(box),a=(along+1)%3,c=(along+2)%3;
        V direction=axis(along),across=axis(a),depth=axis(c),centre=box.centre();
        var keys=new ArrayList<List<Section>>();boolean body=b.role==0,tail=b.role==6;
        boolean dominant=phase==1&&b.role>=2&&b.role<=5;
        float lesion=TissueGrowth.sample(key,12)>.5F?1:-1;
        float distal=Math.abs(box.low.get(along))>Math.abs(box.high.get(along))?-1:1;
        for(int stage=0;stage<3;stage++) {
            float g=stage==2?1:stage*.32F;var rings=new ArrayList<Section>();
            for(int ring=0;ring<4;ring++) {
                float t=ring/3F,belly=(float)Math.sin(Math.PI*t);
                V point=centre.add(direction.mul((t-.5F)*box.span(along)));
                float w=box.span(a)*.5F,d=box.span(c)*.5F;
                if(body) {
                    // A high shoulder and narrow waist, rather than an inflated barrel.
                    float front=direction.dot(b.forward)>0?t:1-t;
                    // +Y usually descends from an upright shoulder. Four-footed
                    // trunks instead use the actual local forward direction.
                    int frontRing=direction.dot(b.forward)>0?3-ring:ring;
                    float shoulder=upright?new float[]{.75F,1,.25F,0}[ring]:new float[]{.25F,1,.2F,0}[frontRing];
                    float profile=upright?new float[]{1.12F,1.22F,.80F,.94F}[ring]
                            :new float[]{1.02F,1.22F,.84F,.86F}[frontRing];
                    float thickness=1+.12F*shoulder;
                    w*=1+((Math.abs(across.dot(b.right))>.65F?profile:thickness)-1)*g;
                    d*=1+((Math.abs(depth.dot(b.right))>.65F?profile:thickness)-1)*g;
                    if(!upright)point=point.add(b.up.mul(box.span(principal(b.up))*.10F*shoulder*g));
                } else if(!tail) {
                    int r=distal>0?ring:3-ring;
                    float[] profile=dominant?new float[]{1.08F,1.32F,.74F,1.02F}:new float[]{1,.98F,.65F,.86F};
                    w*=1+(profile[r]-1)*g;d*=1+(profile[r]-1)*.62F*g;
                    // Preserve sockets and contact points; a real elbow/ankle remains thin.
                    point=point.add(across.mul(lesion*box.span(a)*(dominant?.085F:.035F)*g*belly));
                    point=point.add(b.forward.mul(box.span(along)*(r==1?-.09F:r==2?.035F:0)*g));
                } else {w*=1-.25F*g*t;d*=1-.25F*g*t;}
                rings.add(new Section(point,w,d));
            }keys.add(rings);
        }
        out.add(loft("tissue_"+b.path,true,phase,"",X,null,keys,across,depth,b.up,b.forward,key));
    }
    private static void head(List<Mesh> out,Bone b,long key,boolean organs,BodyPlan shape,TissueCore core) {
        Box box=b.box;V centre=box.centre();int along=principal(b.forward),vertical=principal(b.up);
        if(along==vertical){envelope(out,b,key,0,false);return;}
        int across=3-along-vertical;V forward=axis(along).mul(Math.signum(b.forward.get(along)));
        V down=axis(vertical).mul(-Math.signum(b.up.get(vertical))),right=axis(across);
        float width=box.span(across)*.5F,depth=box.span(along)*.5F,height=box.span(vertical)*.5F;
        boolean radial=shape==BodyPlan.RADIAL,biped=shape==BodyPlan.UPRIGHT;
        V hinge=centre.sub(forward.mul(depth*.8F)).add(down.mul(height*.32F));
        if(biped) {
            // A continuous cervical sleeve joins the actual neck plane to the
            // split cranium. Jaw opening cannot expose an empty gap underneath it.
            V lower=centre.add(down.mul(height*.99F)).sub(forward.mul(depth*.15F));
            V upper=centre.add(down.mul(height*.05F)).sub(forward.mul(depth*.25F));
            var neck=List.of(new Section(lower,width*.34F,depth*.30F),new Section(upper,width*.40F,depth*.35F));
            out.add(loft("tissue_neck",true,0,"",X,null,List.of(neck,neck,neck),right,forward,b.up,b.forward,key));
        }
        if(biped)splitSkull(out,b,key,centre,right,down,forward,width,height,depth);
        for(boolean jaw:new boolean[]{false,true}) {
            if(biped&&!jaw)continue;
            var keys=new ArrayList<List<Section>>();
            for(int stage=0;stage<3;stage++) {
                float g=stage==2?1:stage*.32F;var rings=new ArrayList<Section>();
                for(int ring=0;ring<3;ring++) {
                    float t=ring/2F;
                    float stretch=(radial?-.08F:biped?.18F:sample(key,21,.30F,.48F))*g*t;
                    float h=height*(jaw?.30F:.70F)*(1-(jaw?.28F:radial?.46F:.35F)*g*t);
                    V p=centre.add(forward.mul((t*2-1)*depth+depth*stretch));
                    p=p.add(down.mul(height*(jaw?.70F:-.30F)+(jaw?.22F:-.10F)*height*g*t));
                    float taper=radial?.57F:biped?.38F:.42F;
                    rings.add(new Section(p,width*(1+g*(.16F-taper*t)),h));
                }keys.add(rings);
            }
            float[][] pivots={hinge.array(),hinge.array(),hinge.array()};
            out.add(loft(jaw?"mandible":"skull",true,0,jaw?"jaw":"",forward.cross(down).unit(),pivots,keys,right,down,b.up,b.forward,key));
        }
        if(!organs)return;
        // The visible mouth is a recessed volume. Teeth grow from its palate,
        // not as a pair of barely visible needles under an opaque head cube.
        organ(out,"palate",0,"",X,null,List.of(
                new Section(centre.sub(forward.mul(depth*.7F)).add(down.mul(height*.38F)),width*.72F,height*.10F),
                new Section(centre.add(forward.mul(depth*1.25F)).add(down.mul(height*.30F)),width*.72F,height*.10F)),right,down,b,key);
        for(int side:new int[]{-1,1})for(int tooth=0;tooth<(radial?1:2);tooth++) {
            var keys=new ArrayList<List<Section>>();
            for(int stage=0;stage<3;stage++) {
                float g=stage*.5F;
                V root=centre.add(forward.mul(depth*(.58F+tooth*.48F)*g)).add(right.mul(side*width*.68F)).add(down.mul(height*.04F));
                keys.add(List.of(new Section(root,Math.max(.03F,width*.15F*g),Math.max(.03F,depth*.18F*g)),
                        new Section(root.add(down.mul(height*(tooth==0?.62F:.48F)*g)).add(forward.mul(depth*.09F*g)),.025F,.025F)));
            }
            V socket=centre.sub(forward.mul(depth*.8F)).add(right.mul(side*width*.5F));
            float[][] sockets={socket.array(),socket.array(),socket.array()};
            out.add(loft("fang_"+side+"_"+tooth,false,0,biped?"split":"",biped?down.mul(-side):X,biped?sockets:null,keys,right,forward,b.up,b.forward,key));
        }
        // A pair of swept cheek blades is rooted inside the skull and frames
        // the jaw. The centre of the face stays readable between them.
        float cheek=core==TissueCore.JAW?1:.48F;
        for(int side:new int[]{-1,1}) {
            V socket=centre.sub(forward.mul(depth*.8F)).add(right.mul(side*width*.5F));
            float[][] sockets={socket.array(),socket.array(),socket.array()};
            organ(out,"cheek_blade_"+side,0,biped?"split":"",biped?down.mul(-side):X,biped?sockets:null,List.of(
                new Section(centre.add(right.mul(side*width*.7F)).sub(forward.mul(depth*.35F)),width*.34F*cheek,height*.55F*cheek),
                new Section(centre.add(right.mul(side*width*(.7F+((radial?1.40F:1.05F)-.7F)*cheek))).add(forward.mul(depth*(radial?.85F:.48F)*cheek)).sub(down.mul(height*.12F)),width*.24F*cheek,height*.40F*cheek),
                new Section(centre.add(right.mul(side*width*(radial?.60F:biped?.92F:.72F))).add(forward.mul(depth*(.35F+((radial?1.95F:biped?1.15F:1.46F)-.35F)*cheek))).add(down.mul(height*(biped?.95F:.5F)*cheek)),.025F,.025F)),right,down,b,key);
        }
    }
    private static void splitSkull(List<Mesh> out,Bone b,long key,V centre,V right,V down,V forward,float width,float height,float depth) {
        for(int side:new int[]{-1,1}) {
            var keys=new ArrayList<List<Section>>();
            for(int stage=0;stage<3;stage++) {
                float g=stage==2?1:stage*.32F;var rings=new ArrayList<Section>();
                for(int r=0;r<3;r++) {
                    float t=r*.5F;
                    V p=centre.add(forward.mul((t*2-1)*depth+depth*.18F*g*t))
                            .add(right.mul(side*width*(.5F+.20F*g*t)))
                            .add(down.mul(height*(-.30F-.10F*g*t)));
                    rings.add(new Section(p,width*.5F*(1-.30F*g*t),height*.70F*(1-.35F*g*t)));
                }
                keys.add(rings);
            }
            V pivot=centre.sub(forward.mul(depth*.8F)).add(right.mul(side*width*.5F));
            float[][] pivots={pivot.array(),pivot.array(),pivot.array()};
            out.add(loft(side<0?"skull":"skull_lobe",true,0,"split",down.mul(-side),pivots,keys,right,down,b.up,b.forward,key));
        }
    }
    private static float[][] gland(List<Mesh> out,Bone b,long seed) {
        Box box=b.box;V centre=box.centre(),normal=b.right.unit().mul(TissueOrgans.dominantArm(seed)==3?1:-1);
        float radius=Math.min(box.span(principal(b.up)),box.span(principal(b.forward)))*.17F;
        radius=Math.clamp(radius,.3F,2.4F);
        float extent=box.span(principal(normal))*.5F;var keys=new ArrayList<List<Section>>();float[][] outlet=new float[3][];
        V across=b.forward.unit(),depth=normal.cross(across).unit();
        for(int stage=0;stage<3;stage++) {
            float g=stage*.5F;
            V root=centre.add(normal.mul(extent*(1+.45F*g))).add(b.up.mul(box.span(principal(b.up))*.1F));
            V lip=root.add(normal.mul(radius*.30F*g));outlet[stage]=lip.array();
            keys.add(List.of(new Section(root.sub(normal.mul(radius*.8F*g)),Math.max(.04F,radius*g),Math.max(.04F,radius*g)),
                    new Section(lip,Math.max(.03F,radius*.65F*g),Math.max(.03F,radius*.55F*g))));
        }
        out.add(loft("tendril_gland",false,0,"",X,null,keys,across,depth,b.up,b.forward,seed));return outlet;
    }
    private static void carapace(List<Mesh> out,Bone b,long key,boolean upright,TissueCore core) {
        Box box=b.box;V along=(upright?b.up:b.forward).unit(),normal=(upright?b.forward.mul(-1):b.up).unit(),across=b.right.unit();
        float extent=box.span(principal(normal))*.5F,width=box.span(principal(across))*.5F,length=box.span(principal(along));
        float plate=core==TissueCore.CARAPACE?1:.50F,crest=core==TissueCore.CREST?1:.22F;
        // Two large overlapping scapular plates grow out of a buried, broad
        // base. Their wedge profile carries the silhouette; no floating lid.
        for(int side:new int[]{-1,1}) {
            float asym=side==(TissueGrowth.sample(key,29)>.5F?1:-1)?1.12F:.84F;
            V c=box.centre().add(across.mul(side*width*.35F)).add(normal.mul(extent*.15F));
            organ(out,"scapular_plate_"+side,0,"",X,null,List.of(
                    new Section(c.sub(along.mul(length*.30F)).add(normal.mul(extent*.65F)),width*.40F,extent*.16F*plate),
                    new Section(c.add(along.mul(length*.12F)).add(normal.mul(extent*(.90F+.22F*asym*plate))),width*.48F,extent*.16F*plate),
                    new Section(c.add(along.mul(length*.34F)).add(normal.mul(extent*.82F)),width*.28F,extent*.16F*plate)),across,normal,b,key);
            // Only two load-bearing crests, swept rearward from the shoulder.
            V root=c.add(along.mul(length*.06F)).add(normal.mul(extent*.88F));
            organ(out,"shoulder_crest_"+side,1,"",X,null,List.of(
                    new Section(root,width*.22F*(.55F+.45F*crest),extent*.24F*(.55F+.45F*crest)),
                    new Section(root.sub(along.mul(length*.18F*crest)).add(normal.mul(extent*.52F*asym*crest)),width*.16F*(.55F+.45F*crest),extent*.15F),
                    new Section(root.sub(along.mul(length*.42F*crest)).add(normal.mul(extent*.90F*asym*crest)),.025F,.025F)),across,normal,b,key);
        }
    }
    private static void ribCage(List<Mesh> out,Bone b,long key,boolean upright,TissueCore core) {
        Box box=b.box;V along=(upright?b.up:b.forward).unit(),normal=(upright?b.forward.mul(-1):b.up).unit(),right=b.right.unit();
        float width=box.span(principal(right))*.5F,height=box.span(principal(normal))*.5F,length=box.span(principal(along));
        // Secondary ribs leave the flanks and limb gaps readable. Only the
        // armour lineage develops a second pair; every host is not every kit.
        int pairs=core==TissueCore.CARAPACE?2:1;
        for(int side:new int[]{-1,1})for(int rib=0;rib<pairs;rib++) {
            V c=box.centre().add(along.mul((rib-(pairs-1)*.5F)*length*.32F));
            float w=width*(rib==0?.94F:1.04F),thickness=Math.clamp(length*.038F,.22F,.60F);
            organ(out,"costal_arch_"+side+"_"+rib,1,"",X,null,List.of(
                    new Section(c.add(right.mul(side*w*.64F)).add(normal.mul(height*.85F)),thickness,height*.20F),
                    new Section(c.add(right.mul(side*w*1.14F)).add(normal.mul(height*.22F)),thickness,height*.17F),
                    new Section(c.add(right.mul(side*w*.95F)).sub(normal.mul(height*.60F)).add(along.mul(length*.09F)),thickness*.65F,height*.10F)),along,right,b,key);
        }
    }
    private static void extremity(List<Mesh> out,Bone b,long key,int phase,boolean upright,int donorPlan) {
        Box box=b.box;int a=longest(box);V direction=axis(a),centre=box.centre();
        // Choose the end farthest from the real joint (local origin), including
        // horizontal spider legs and rotated limbs rather than assuming +Y.
        float sign=Math.abs(box.low.get(a))>Math.abs(box.high.get(a))?-1:1;
        direction=direction.mul(sign);int u=(a+1)%3,v=(a+2)%3;
        V across=axis(u),depth=axis(v);float width=box.span(u)*.5F,length=box.span(a);
        boolean dominant=phase==1;
        boolean claw=donorPlan==2||dominant||TissueGrowth.sample(key,43)>.65F;int digits=donorPlan==2?2:claw?3:1;
        boolean planted=direction.dot(b.up)<-.65F&&(b.role>=4||!upright||legBone(b.path));
        if(planted){across=b.right.unit();depth=b.up.unit().mul(-1);}
        for(int digit=0;digit<digits;digit++) {
            var keys=new ArrayList<List<Section>>();float spread=(digit-(digits-1)*.5F)*width*.75F;
            for(int stage=0;stage<3;stage++) {
                float g=stage==2?1:stage*.32F;
                V root=centre.add(direction.mul(length*.31F)).add(across.mul(spread*g));
                float radius=Math.max(.025F,width*(claw?.32F:.62F)*g);
                if(planted) {
                    // Toes sweep along the ground from a real distal joint. No
                    // growth may extend below the original support plane.
                    keys.add(List.of(new Section(root,radius,Math.max(.025F,width*.22F*g)),
                            new Section(root.add(b.forward.mul((width*1.25F+length*.18F)*(dominant?1.35F:1)*g)).add(direction.mul(length*.07F*g)),radius*.78F,Math.max(.025F,width*.15F*g)),
                            new Section(root.add(b.forward.mul((width*1.8F+length*.29F)*(dominant?1.35F:1)*g)).add(direction.mul(length*.17F*g)),.025F,.025F)));
                } else keys.add(List.of(new Section(root,radius,Math.max(.025F,box.span(v)*.25F*g)),
                        new Section(root.add(direction.mul(length*.28F*g)).sub(depth.mul(width*1.15F*g)),radius*.85F,Math.max(.025F,radius*.65F)),
                        new Section(root.add(direction.mul(length*(dominant?.64F:.44F)*g)).sub(depth.mul(width*.55F*g)),.025F,.025F)));
            }
            float[][] pivots=new float[3][];
            for(int k=0;k<3;k++)pivots[k]=keys.get(k).getFirst().centre.array();
            V hinge=planted?b.forward.cross(b.up).unit():across;
            out.add(loft((claw?"claw_":"blade_")+digit,false,phase,planted?"toe":"hook",hinge,pivots,keys,across,depth,b.up,b.forward,key));
        }
        if(dominant) {
            V side=b.right.unit().mul(b.role==3?1:-1),back=b.forward.unit().mul(-1);
            V root=centre.sub(direction.mul(length*.20F)).add(side.mul(box.span(principal(side))*.38F));
            organ(out,"ulnar_blade",phase,"",X,null,List.of(
                    new Section(root,width*.55F,width*.55F),
                    new Section(root.add(direction.mul(length*.23F)).add(back.mul(width*1.45F)),width*.38F,width*.64F),
                    new Section(root.add(direction.mul(length*.58F)).add(back.mul(width*2.15F)),.025F,.025F)),side,back,b,key);
        }
    }
    /** A small authored vocabulary of organs, fitted procedurally to native
     * bones. These are coherent surfaces, not random boxes on a host AABB. */
    private static void organ(List<Mesh> out,String name,int phase,String motion,V hinge,float[][] pivots,
            List<Section> mature,V across,V depth,Bone b,long seed) {
        var keys=new ArrayList<List<Section>>();V root=mature.getFirst().centre;
        for(int stage=0;stage<3;stage++) {
            float g=stage==2?1:stage*.32F;var rings=new ArrayList<Section>();
            for(var s:mature)rings.add(new Section(root.add(s.centre.sub(root).mul(g)),Math.max(.015F,s.width*g),Math.max(.015F,s.depth*g)));
            keys.add(rings);
        }
        out.add(loft(name,false,phase,motion,hinge,pivots,keys,across,depth,b.up,b.forward,seed));
    }
    private static Mesh loft(String name,boolean skin,int phase,String motion,V hinge,float[][] pivots,
            List<List<Section>> keys,V a,V b,V up,V forward,long seed) {
        int sections=keys.getFirst().size();float[][] stages=new float[3][sections*8*3];
        for(int k=0;k<3;k++)for(int r=0;r<sections;r++)for(int s=0;s<8;s++) {
            float x=RING[s][0],y=RING[s][1];if(skin&&k==0){x=Math.copySign(1,x);y=Math.copySign(1,y);}
            var section=keys.get(k).get(r);V p=section.centre.add(a.mul(x*section.width)).add(b.mul(y*section.depth));
            int i=(r*8+s)*3;stages[k][i]=p.x;stages[k][i+1]=p.y;stages[k][i+2]=p.z;
        }
        var faces=new ArrayList<float[]>();
        for(int r=0;r<sections-1;r++)for(int s=0;s<8;s++) {
            int[] ids={r*8+s,(r+1)*8+s,(r+1)*8+(s+1)%8,r*8+(s+1)%8};
            V outward=a.mul((RING[s][0]+RING[(s+1)%8][0])*.5F).add(b.mul((RING[s][1]+RING[(s+1)%8][1])*.5F));
            int material=material(name,skin,r,s,sections,outward,up,forward);
            float start=skin?.08F+.34F*TissueGrowth.sample(seed,s/2+r*13):.22F;
            addFace(faces,stages[2],ids,outward,material,start,Math.min(.94F,start+.38F));
        }
        for(int end:new int[]{0,sections-1}) {
            V direction=keys.get(2).get(end).centre.sub(keys.get(2).get(end==0?1:sections-2).centre);
            for(int[] cap:new int[][]{{0,1,2,3},{0,3,4,7},{7,4,5,6}}) {
                int[] ids=cap.clone();for(int i=0;i<4;i++)ids[i]+=end*8;
                int mat=name.contains("gland")||name.equals("palate")?6:name.startsWith("fang")?4
                        :!skin?3:name.equals("mandible")?2:name.startsWith("skull")&&end==0?0:3;
                addFace(faces,stages[2],ids,direction,mat,.15F,.58F);
            }
        }
        return new Mesh(name,skin,phase,motion,hinge.array(),pivots,stages,faces.toArray(float[][]::new));
    }
    private static int material(String name,boolean skin,int ring,int side,int sections,V outward,V up,V forward) {
        if(!skin) {
            if(name.equals("palate"))return 6;
            if(name.startsWith("fang"))return 4;
            if(name.contains("gland"))return 2;
            if(name.startsWith("costal"))return side==2||side==6?2:7;
            // A narrow blood groove distinguishes the cutting edge from the
            // broad charcoal face; no uniformly ivory claws or red boxing gloves.
            return side==1||side==5?2:side==3?1:3;
        }
        if(name.startsWith("skull"))return ring==0&&side==7?0:outward.dot(up)<-.4F?6:side==3||side==7?1:3;
        if(name.equals("mandible"))return outward.dot(up)>.4F?6:side==1||side==5?1:3;
        // Keep torn islands of original hide, especially around joint sockets.
        // Most mature coverage is dark structural tissue, with flesh confined
        // to the exposed channels between those structures.
        if(ring==0&&side==7)return 0;
        if(outward.dot(up)>.45F||outward.dot(forward)<-.55F)return 3;
        return side==1||side==5?2:side==2||side==6?1:3;
    }
    private static V vertex(float[] vertices,int index){int i=index*3;return new V(vertices[i],vertices[i+1],vertices[i+2]);}
    private static void addFace(List<float[]> faces,float[] mature,int[] ids,V outward,int material,float start,float end) {
        V normal=vertex(mature,ids[1]).sub(vertex(mature,ids[0])).cross(vertex(mature,ids[2]).sub(vertex(mature,ids[0])));
        if(normal.dot(outward)<0){int i=ids[1];ids[1]=ids[3];ids[3]=i;}
        faces.add(new float[]{ids[0],ids[1],ids[2],ids[3],material,start,end});
    }
}
