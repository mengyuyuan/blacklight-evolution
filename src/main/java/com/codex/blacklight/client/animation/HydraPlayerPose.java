package com.codex.blacklight.client.animation;
import com.codex.blacklight.animation.*;
import com.codex.blacklight.city.HydraTransport;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

/** Source hit/fall poses with a paired horn brace and inertial dangling limbs. No sitting pose. */
public final class HydraPlayerPose {
    private static final java.util.Map<Player,BlacklightPose> LAST_GRIP=new java.util.WeakHashMap<>();
    private HydraPlayerPose(){}
    public static boolean visible(Player p){
        double age=p.level().getGameTime()-com.codex.blacklight.registry.ModAttachments.getData(p,ModAttachments.HYDRA_THROWN_AT);
        return HydraTransport.active(p)||age>=0&&age<24&&!p.onGround();
    }
    public static BlacklightPose sample(Player p,float partial){
        var h=HydraTransport.carrier(p);
        if(h==null){
            double age=p.level().getGameTime()+partial-com.codex.blacklight.registry.ModAttachments.getData(p,ModAttachments.HYDRA_THROWN_AT);
            if(age<0||age>=24||p.onGround()){LAST_GRIP.remove(p);return null;}
            var clip=MotionLibrary.clip("reaction_fall");
            var result=MotionLibrary.pose("reaction_fall",Math.min(clip.duration()-.001F,(float)age/20),false);
            result.orElseEmpty("Root").translation().zero();
            var previous=LAST_GRIP.get(p);
            if(previous!=null)result=BlacklightPose.interpolatePose(previous,result,smooth((float)age/4));
            var locomotion=com.codex.blacklight.registry.ModAttachments.getData(p,ModAttachments.PLAYER_STATE).isPrototypeMode()?PlayerMotionController.pose(p,partial):new BlacklightPose();
            return BlacklightPose.interpolatePose(result,locomotion,smooth(((float)age-14)/10));
        }
        float time=h.actionTime()+partial;
        float phase=(float)(p.level().getGameTime()%10000+partial)*.24F;
        var pose=MotionLibrary.pose("reaction_body",.18F+.06F*(float)Math.sin(phase*.6F),false);
        var root=pose.orElseEmpty("Root");root.translation().zero();
        var velocity=h.gripPosition(p,partial).subtract(h.gripPosition(p,partial-1)).yRot((float)Math.toRadians(h.getYRot()-180));
        float tuck=smooth((float)Math.max(0,-velocity.y)*2);
        float windup=h.action()==8?smooth(time/48):0;
        float pitch=(float)net.minecraft.util.Mth.clamp(velocity.z*.45,-.45,.45)-windup*.32F;
        root.rotation().rotateX(pitch).rotateZ((float)net.minecraft.util.Mth.clamp(-velocity.x*.35,-.3,.3));
        pose.orElseEmpty("Torso").rotation().rotateX(tuck*.26F).rotateY((float)Math.sin(phase)*.14F);
        pose.orElseEmpty("Head").rotation().rotateX(-.12F+tuck*.26F).rotateY((float)Math.sin(phase*.7F)*.2F);
        for(String side:new String[]{"L","R"}){
            float shift=side.equals("L")?0:2.0F;
            // Legs hang and recoil independently; no copied vanilla riding angles.
            pose.orElseEmpty("Thigh_"+side).rotation().rotationXYZ(-.3F-tuck*.35F+(float)Math.sin(phase+shift)*.28F,0,side.equals("L")?-.12F:.12F);
            pose.orElseEmpty("Leg_"+side).rotation().rotationX(.55F+tuck*.5F+(float)Math.sin(phase+shift+.8F)*.25F);
            pose.orElseEmpty("Arm_"+side).rotation().rotationXYZ(-.65F,0,side.equals("L")?-.28F:.28F);
            pose.orElseEmpty("Hand_"+side).rotation().rotationX(-1F);
        }
        // Keep the torso clamped while its limbs react. Rotating about the feet
        // otherwise makes the whole actor slide through the horn contact.
        var skeleton=BlacklightSkeleton.get();
        var chest=skeleton.getBoundTransformFor(pose,skeleton.chest).getTranslation(new Vector3f());
        var correction=new Vector3f(0,p.getBbHeight()*.5F/.9375F,0).sub(chest);
        skeleton.rootJoint.getLocalTransform().invert().transformDirection(correction);
        root.translation().add(correction);
        for(String side:new String[]{"L","R"}){
            var target=new Vector3f(side.equals("L")?-.34F:.34F,p.getBbHeight()*.5F/.9375F+.06F,-.17F);
            BlacklightBoneIk.reach(skeleton,pose,side,target,new Vector3f(0,.2F,0),8);
        }
        // Blend into capture, rather than snap from standing to a fixed mannequin.
        float weight=h.action()==2?smooth((time-50)/8):1;
        var result=BlacklightPose.interpolatePose(MotionLibrary.pose("basic_fall",phase*.1F,true),pose,weight);
        LAST_GRIP.put(p,result.copy());return result;
    }
    private static float smooth(float f){f=net.minecraft.util.Mth.clamp(f,0,1);return f*f*(3-2*f);}
}
