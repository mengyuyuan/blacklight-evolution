package com.codex.blacklight.client.animation;

import com.codex.blacklight.client.ClientCarryPlacement;
import com.codex.blacklight.client.renderer.ConsumeAbsorptionFx;
import com.codex.blacklight.client.renderer.ConsumeFracture;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Minecraft adaptation of the P1 puppet -> unpowered -> consume-by-parent handoff.
 * Timings come from the existing paired scene. Solver gains are MC units, not P1 constants. */
public final class RagdollDrive {
    private static final java.util.Map<LivingEntity,Long> HURT_FLASH=new java.util.WeakHashMap<>();
    private RagdollDrive() {}
    public static void impactFlash(LivingEntity e,com.codex.blacklight.network.RagdollImpactPayload hit){
        // A throw release carries no wound and must not pretend another hit occurred.
        if(!hit.refinement()&&!hit.wounds().isEmpty())HURT_FLASH.put(e,e.level().getGameTime()+10);
    }
    public static boolean hurtFlash(LivingEntity e){
        // Removed corpses no longer tick hurtTime; use a world-clock deadline for them.
        return e.level().getGameTime()<HURT_FLASH.getOrDefault(e,Long.MIN_VALUE)
                ||!e.isDeadOrDying()&&!e.isRemoved()&&e.hurtTime>0;
    }
    /** Short wound packets accompany an authored standing flinch. Only a knockdown,
     * death or actual sever transfers that pose to a free physical body. */
    public static boolean fullImpact(com.codex.blacklight.network.RagdollImpactPayload hit){
        return hit.ticks()>=12||hit.severMask()!=0||!hit.planes().isEmpty();
    }
    public static boolean captured(LivingEntity e) {
        var v=e.getData(ModAttachments.CONSUME_VISUAL);
        return v.active() && !ConsumeSequence.mountedStage(v.stage())
                && v.stage()!=ConsumeVisual.FLIGHT && v.stage()!=ConsumeVisual.NONE
                && e.level().getEntity(v.consumerId()) instanceof LivingEntity owner && owner.isAlive();
    }
    public static boolean supported(LivingEntity e) {
        return RagdollImpact.supportedBody(e) && RagdollImpact.legacyBody(e) && !e.isInvisible()
                && !com.codex.blacklight.client.renderer.AdaptiveRagdoll.supports(e);
    }
    public static float flow(LivingEntity e,float partial) {
        return captured(e) ? ConsumeFracture.phase(e,partial).returnFlow() : 0;
    }
    /** Per-part absorption front. Connected tissue keeps its joints until it reaches the sink. */
    public static float partFlow(LivingEntity e,int region,float partial) {
        float f=flow(e,partial);if(f<=0)return 0;
        var style=ConsumeFracture.phase(e,partial).style();
        float delay=switch(style) {
            case HEAD_CRUSH,HEAD_IMPACT -> region==1?0:region==32?.10F:.20F;
            case ARM_RIP,RIP -> region==2?0:region==32?.10F:.18F;
            default -> region==32?0:region==1?.08F:.16F;
        };
        return smooth((f-delay)/(1-delay));
    }
    public static float power(LivingEntity e) {
        if(!captured(e))return 0;
        var v=e.getData(ModAttachments.CONSUME_VISUAL);
        if(!v.consuming())return .36F;
        var form=PowerForm.values()[v.form()];
        var scene=ConsumeReceiverScene.profile(form,false,v.variant());
        float seconds=(e.level().getGameTime()-v.started())/20F;
        float release=ConsumeReceiverScene.absorptionSeconds(form,v.variant());
        if(!Float.isFinite(release))release=scene==null?v.duration()/20F*.65F:scene.deformSeconds();
        return .72F*(1-smooth((seconds-release)/.18F));
    }
    public static boolean attached(LivingEntity e) {
        if(!captured(e))return false;
        var v=e.getData(ModAttachments.CONSUME_VISUAL);
        if(!v.consuming())return true;
        return v.motionProgress(e.level().getGameTime(),1)<ConsumePlacementTrack.gripEnd(PowerForm.values()[v.form()]);
    }
    public static int gripRegion(LivingEntity e) {
        var v=e.getData(ModAttachments.CONSUME_VISUAL);
        return v.form()==PowerForm.BLADE.ordinal()?32:1;
    }
    /** The same interpolated physical body drawn this frame, not the standing bounding box. */
    public static Vec3 contact(LivingEntity e,int region,float partial){
        Vec3 point=ImpactRagdoll.contact(e,region,partial);
        if(point==null)point=com.codex.blacklight.client.renderer.PigRagdoll.contact(e,region,partial);
        if(point==null)point=com.codex.blacklight.client.renderer.AdaptiveRagdoll.contact(e,region,partial);
        if(point!=null)return point;
        var v=e.getData(ModAttachments.CONSUME_VISUAL);
        String key=GrappleMotion.sceneKey(e,v);float time=GrappleMotion.receiverProgress(e,v,partial);
        var row=ConsumePlacementTrack.contactFrame(key,time).orElse(null);
        if(row!=null&&ConsumeSequence.humanoid(e)){
            int index=region==1?3:10;double scale=ConsumeSequence.receiverScale(e);
            float yaw=(float)Math.toRadians(180-GrappleMotion.facing(e,v,partial));
            return e.getPosition(partial).add(ClientCarryPlacement.consumeOffset(e,partial))
                    .add(new Vec3(row[index]*scale,row[index+1]*scale,-row[index+2]*scale).yRot(yaw));
        }
        boolean quad=ConsumeSequence.quadruped(e);
        return transform(frame(e,partial),new Vec3(0,e.getBbHeight()*(region==1?(quad?.65:.86):.5),region==1&&quad?-e.getBbWidth()*.45:0));
    }
    public static Vec3 sink(LivingEntity e,float partial) {
        Vec3 placement=ClientCarryPlacement.consumeOffset(e,partial);
        return e.getPosition(partial).add(placement).add(ConsumeAbsorptionFx.destination(e,partial,placement));
    }
    /** Same transform as the living renderer, including paired root motion and interpolation. */
    public static Matrix4f frame(LivingEntity e,float partial) {
        var at=e.getPosition(partial);var matrix=new Matrix4f().translation((float)at.x,(float)at.y,(float)at.z);
        if(captured(e)) {
            var offset=ClientCarryPlacement.consumeOffset(e,partial);
            matrix.translate((float)offset.x,(float)offset.y,(float)offset.z).mul(ConsumeVictimPose.bodyTransform(e,partial));
        }
        return matrix.rotateY((float)Math.toRadians(180-net.minecraft.util.Mth.rotLerp(partial,e.yBodyRotO,e.yBodyRot)));
    }
    public static Vec3 transform(Matrix4f matrix,Vec3 p) {
        var v=matrix.transformPosition(new Vector3f((float)p.x,(float)p.y,(float)p.z));return new Vec3(v.x,v.y,v.z);
    }
    public static Vec3 limit(Vec3 v,double max) {return v.lengthSqr()>max*max?v.normalize().scale(max):v;}
    public static float smooth(float f) {f=Math.clamp(f,0,1);return f*f*(3-2*f);}
    /** Acceleration-limited attraction, retaining existing momentum rather than teleporting parts. */
    public static Vec3 pull(Vec3 position,Vec3 velocity,Vec3 destination,float flow,double step) {
        Vec3 delta=destination.subtract(position);
        double speed=Math.min(1.0,delta.length()*(.22+flow*.95));
        Vec3 desired=delta.normalize().scale(speed);
        return velocity.add(limit(desired.subtract(velocity),(.055+flow*.13)*step));
    }
}
