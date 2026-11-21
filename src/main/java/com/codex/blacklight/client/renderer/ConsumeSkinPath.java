package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.ConsumeReceiverEffect;
import com.codex.blacklight.combat.ConsumeSkinGrowth;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/** Map rigid and split MC skin onto an authored 32-pixel-high material rig. */
final class ConsumeSkinPath {
    private record Joint(String name, int parent, double[] position) {}
    private static final Joint[] JOINTS = readJoints();
    private static final Map<String,Integer> IDS = new HashMap<>();
    private static final Vec3[] POSITIONS=new Vec3[JOINTS.length];
    private static final int[] LAST_CHILD=new int[JOINTS.length];
    private static final ConsumeSkinGrowth.Skeleton SKELETON = skeleton();

    private static Joint[] readJoints() {
        try (var stream=ConsumeSkinPath.class.getResourceAsStream("/assets/blacklight/consume/charcoal_rig.json")) {
            if (stream==null) throw new IllegalStateException("Missing receiver material skeleton");
            return new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Joint[].class);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read receiver skeleton", ex);
        }
    }

    private static ConsumeSkinGrowth.Skeleton skeleton() {
        int[] parents=new int[JOINTS.length];
        float[] lengths=new float[JOINTS.length];
        for(int i=0;i<JOINTS.length;i++){
            IDS.put(JOINTS[i].name(),i);
            double[] p=JOINTS[i].position();
            POSITIONS[i]=new Vec3(p[0]/16,p[1]/16,p[2]/16);
            LAST_CHILD[i]=i;
            int parent=parents[i]=JOINTS[i].parent();
            // ConsumeEffect2 stores each child separation on its parent, in skeleton order.
            if(parent>=0){
                lengths[parent]=(float)POSITIONS[i].distanceTo(POSITIONS[parent]);
                LAST_CHILD[parent]=i;
            }
        }
        return new ConsumeSkinGrowth.Skeleton(parents,lengths);
    }

    static ConsumeSkinGrowth.Playback playback(ConsumeReceiverEffect.Cue cue) {
        String joint=switch(cue.joint()){
            case NECK -> "throat"; case MOTION_ROOT -> "base";
            case ANKLE_L -> "leftBoot"; case SPINE_2 -> "ribs";
        };
        return SKELETON.playback(cue,IDS.get(joint));
    }

    // Retain coordinates from the uncompressed MC skin when a piece separates.
    private static final class Chain {
        final int[] bones;
        final double[] knots;
        Chain(String... names) {
            bones=new int[names.length]; knots=new double[names.length];
            for(int i=0;i<names.length;i++){
                bones[i]=IDS.get(names[i]);
                if(i>0)knots[i]=knots[i-1]+POSITIONS[bones[i]].distanceTo(POSITIONS[bones[i-1]]);
            }
            for(int i=1;i<knots.length;i++)knots[i]/=knots[knots.length-1];
        }
        float coverage(ConsumeSkinGrowth.Weights weights,double t){
            t=Math.min(1,Math.max(0,t));
            int i=1;
            while(i<knots.length-1&&t>knots[i])i++;
            float mix=(float)((t-knots[i-1])/(knots[i]-knots[i-1]));
            int a=bones[i-1], b=bones[i];
            Vec3 mapped=POSITIONS[a].lerp(POSITIONS[b],mix);
            return Math.min(1,(1-mix)*weight(weights,a,mapped)+mix*weight(weights,b,mapped));
        }
        private float weight(ConsumeSkinGrowth.Weights weights,int bone,Vec3 point){
            return weights.influence(bone,(float)point.distanceTo(POSITIONS[bone]),
                    (float)point.distanceTo(POSITIONS[LAST_CHILD[bone]]));
        }
    }

    private static final Chain TORSO=new Chain("hips","waist","ribs","collar","throat");
    private static final Chain HEAD=new Chain("throat","face","crown");
    private static final Chain ARM_R=arm("right"), ARM_L=arm("left"), LEG_R=leg("right"), LEG_L=leg("left");
    private static Chain arm(String side){
        return new Chain(side+"Shoulder",side+"Elbow",side+"Cuff",side+"Knuckles",side+"Tip");
    }
    private static Chain leg(String side){return new Chain(side+"Hip",side+"Knee",side+"Boot",side+"Toe");}

    private record Axis(Vec3 root,Vec3 direction,double squaredLength,Chain chain) {
        Axis(Vec3 from,Vec3 to,Chain chain){this(from,to.subtract(from),from.distanceToSqr(to),chain);}
        float coverage(ConsumeSkinGrowth.Weights weights,Vec3 vertex){
            return chain.coverage(weights,squaredLength>1.e-9?vertex.subtract(root).dot(direction)/squaredLength:.5);
        }
    }
    private final Axis[] axes;
    private ConsumeSkinPath(Axis[] axes){this.axes=axes;}

    static ConsumeSkinPath bind(List<AdaptiveRagdoll.Part> parts) {
        var regions=new AdaptiveRagdoll.Part[33];
        for(var part:parts){
            int region=part.bind.region();
            if(region>0&&region<regions.length&&(regions[region]==null||part.volume()>regions[region].volume()))
                regions[region]=part;
        }
        var body=regions[32];
        if(body==null)return null;
        Vec3 neck=regions[1]==null?body.center.add(0,body.half.y,0):regions[1].bind.pivot();
        Vec3 hips=regions[8]!=null&&regions[16]!=null?regions[8].bind.pivot().lerp(regions[16].bind.pivot(),.5)
                :body.center.add(0,-body.half.y,0);
        Axis[] axes=new Axis[regions.length];
        axes[32]=new Axis(hips,neck,TORSO);
        for(int region=1;region<=16;region*=2){
            var part=regions[region];
            if(part==null)continue;
            Vec3 pivot=part.bind.pivot();
            Vec3 end=part.center.add(part.center.subtract(pivot));
            Chain chain=switch(region){case 1->HEAD;case 2->ARM_R;case 4->ARM_L;case 8->LEG_R;default->LEG_L;};
            axes[region]=new Axis(pivot,end,chain);
        }
        return new ConsumeSkinPath(axes);
    }
    float coverage(ConsumeSkinGrowth.Weights weights,int region,Vec3 vertex){
        Axis axis=region>0&&region<axes.length?axes[region]:null;
        return (axis==null?axes[32]:axis).coverage(weights,vertex);
    }

    /** Fallback for renderers which did not submit named body parts. */
    static float unrigged(ConsumeSkinGrowth.Weights weights,double relativeHeight){
        if(relativeHeight>.78)return HEAD.coverage(weights,(relativeHeight-.78)/.22);
        if(relativeHeight<.46)return LEG_R.coverage(weights,1-relativeHeight/.46);
        return TORSO.coverage(weights,(relativeHeight-.46)/.32);
    }
}
