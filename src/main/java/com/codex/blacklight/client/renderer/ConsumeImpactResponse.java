package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.combat.ConsumeSequence;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Local contact response of a held body. The server's movement, receiver events and split times continue normally. */
public final class ConsumeImpactResponse {
    enum Impact {
        HAMMER(1.65,.23,3.4), GRIP(1.35,.17,3.2), KNEE(1.2,.16,2.9),
        LASH_FIRST(.9,.12,4.7), LASH_RETURN(1.45,.22,3.4), GRAPPLE(.9,.11,2.8), CUT(1.05,.075,2.7);
        final double stop,travel,rejoin;
        Impact(double stop,double travel,double rejoin){this.stop=stop;this.travel=travel;this.rejoin=rejoin;}
    }
    private static final class Stop {
        final long scene;final double begin;final Impact impact;
        float contactSeconds=Float.NaN;
        Stop(long scene,double begin,Impact impact){this.scene=scene;this.begin=begin;this.impact=impact;}
    }
    private static final Map<LivingEntity,Stop> STOPS=new WeakHashMap<>();
    private final Map<AdaptiveRagdoll.Part,Vec3> atContact=new IdentityHashMap<>();
    private long sequence=Long.MIN_VALUE;
    private double begin;
    private Vec3 ownerBase=Vec3.ZERO,drive=Vec3.ZERO;
    private Impact impact;

    static Impact profile(String clip,int strike){
        return switch(clip){
            case "hammer","hammer_smush" -> Impact.HAMMER;
            case "claws_head" -> Impact.GRIP;
            case "normal_knee_head" -> Impact.KNEE;
            case "whip_lash" -> strike==0?Impact.LASH_FIRST:Impact.LASH_RETURN;
            case "muscle_heart" -> strike==1?Impact.GRIP:null;
            case "stealth_neck","stealth_bearhug","stealth_nelson","stealth_guillotine","stealth_jugular" -> Impact.GRAPPLE;
            case "claws","blade","muscle","whip" -> Impact.CUT;
            default -> null;
        };
    }
    private static String clip(ConsumeVisual visual){
        return ConsumeSequence.key(PowerForm.values()[visual.form()],visual.variant());
    }

    /** NativeConsumePose calls this just before sampling the clip. The wall clock and feet remain live. */
    public static float poseSeconds(LivingEntity entity,ConsumeVisual visual,float partial,float raw){
        Stop hold=STOPS.get(entity);
        if(hold==null||hold.scene!=visual.started()||visual.stage()!=ConsumeVisual.CONSUME)return raw;
        double age=entity.level().getGameTime()+partial-hold.begin;
        if(age<0)return raw;
        if(age>=hold.impact.stop+hold.impact.rejoin){STOPS.remove(entity);return raw;}
        if(Float.isNaN(hold.contactSeconds))hold.contactSeconds=Math.max(0,raw-(float)age/20);
        return (float)(hold.contactSeconds+(raw-hold.contactSeconds)*resume(hold.impact,age));
    }

    static double resume(Impact hit,double age){
        double t=Math.max(0,Math.min(1,(age-hit.stop)/hit.rejoin));
        return t*t*(3-2*t);
    }
    static double displacement(Impact hit,double age,int region){
        double delay=region==1?0:region==32?.32:region==2||region==4?.47:.70;
        double s=age-hit.stop-delay;
        if(s<=0||s>=6)return 0;
        double fade=Math.min(1,(6-s)/1.5);
        double strength=region==1?1:region==32?.81:region==2||region==4?.69:.42;
        return hit.travel*strength*Math.sin(1.10*s)*Math.exp(-.38*s)*fade;
    }
    private void record(LivingEntity victim,LivingEntity actor,ConsumeVisual visual,Impact hit,double now){
        sequence=visual.started();begin=now;impact=hit;
        STOPS.put(actor,new Stop(sequence,now,hit));
        STOPS.put(victim,new Stop(sequence,now,hit));
    }
    void hit(LivingEntity victim,LivingEntity actor,ConsumeVisual visual,int strike,Vec3 contact,Vec3 center,
             List<AdaptiveRagdoll.Part> parts,Vec3[] positions,float partial){
        if(visual.stage()!=ConsumeVisual.CONSUME)return;
        Impact hit=profile(clip(visual),strike);
        if(hit==null||hit==Impact.CUT)return;
        record(victim,actor,visual,hit,victim.level().getGameTime()+partial);
        ownerBase=actor.getPosition(partial);
        Vec3 facing=Vec3.directionFromRotation(0,visual.facing());
        Vec3 inward=center.subtract(contact).normalize();
        if(hit==Impact.LASH_FIRST||hit==Impact.LASH_RETURN){
            Vec3 towards=ownerBase.add(0,actor.getBbHeight()*.62,0).subtract(center).normalize();
            drive=(hit==Impact.LASH_FIRST?towards.scale(-.55):towards).add(inward.scale(.37)).normalize();
        }else{
            drive=inward.add(facing.scale(.68)).normalize();
            // Keep an overhead contact visibly loading the shoulders even if its skin normal is vertical.
            if(drive.lengthSqr()<.25)drive=facing;
        }
        atContact.clear();
        for(int i=0;i<parts.size();i++)atContact.put(parts.get(i),positions[i]);
    }

    void apply(LivingEntity victim,LivingEntity actor,ConsumeVisual visual,List<AdaptiveRagdoll.Part> parts,
               Vec3[] positions,float partial){
        if(visual.stage()!=ConsumeVisual.CONSUME)return;
        if(impact==null||visual.started()!=sequence)return;
        double age=victim.level().getGameTime()+partial-begin;
        if(age<0||age>=impact.stop+6.8)return;
        double resume=resume(impact,age);
        Vec3 translation=actor.getPosition(partial).subtract(ownerBase);
        for(int i=0;i<parts.size();i++){
            var part=parts.get(i);Vec3 start=atContact.get(part);
            if(start==null)continue;
            Vec3 at=resume<1?start.add(translation).lerp(positions[i],resume):positions[i];
            positions[i]=at.add(drive.scale(displacement(impact,age,part.bind.region())));
        }
    }
    Vec3 fragmentImpulse(){return impact==Impact.HAMMER||impact==Impact.GRIP?drive.scale(.054):Vec3.ZERO;}

    static void windup(ConsumeVisual visual,float secondsUntilSplit,List<AdaptiveRagdoll.Part> parts,Vec3[] positions){
        if(visual.stage()!=ConsumeVisual.CONSUME||!(secondsUntilSplit>0&&secondsUntilSplit<.22F))return;
        if(profile(clip(visual),0)!=Impact.CUT)return;
        double progress=1-secondsUntilSplit/.22;
        // Load the limbs before failure. The central section remains on the weapon's contact path.
        Vec3 center=null;
        for(int i=0;i<parts.size();i++)if(parts.get(i).bind.region()==32){center=positions[i];break;}
        if(center==null)return;
        for(int i=0;i<parts.size();i++){
            int region=parts.get(i).bind.region();
            if(region==32)continue;
            Vec3 relative=positions[i].subtract(center);
            double strain=region==2||region==4?.12:.05;
            positions[i]=positions[i].add(relative.scale(strain*progress*progress));
        }
    }
    void separated(LivingEntity victim,LivingEntity owner,ConsumeVisual visual,Vec3 normal,List<AdaptiveRagdoll.Part> parts,double now){
        if(visual.stage()!=ConsumeVisual.CONSUME||profile(clip(visual),0)!=Impact.CUT)return;
        record(victim,owner,visual,Impact.CUT,now);atContact.clear();
        for(var part:parts)if(part.consumeHalf!=0)
            part.velocity=part.velocity.add(normal.scale(part.consumeHalf*.055));
    }
}
