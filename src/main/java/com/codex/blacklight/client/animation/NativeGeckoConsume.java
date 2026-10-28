package com.codex.blacklight.client.animation;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.model.GeoModel;

public final class NativeGeckoConsume {
    private NativeGeckoConsume() {}
    public static boolean apply(GeoModel<?> model,LivingEntity entity,float partial) {
        var visual=entity.getData(ModAttachments.CONSUME_VISUAL);if(!visual.consuming()&&!com.codex.blacklight.combat.GrappleMotion.active(visual))return false;
        var form=com.codex.blacklight.power.PowerForm.values()[Math.clamp(visual.form(),0,com.codex.blacklight.power.PowerForm.values().length-1)];
        boolean large=com.codex.blacklight.combat.ConsumeSequence.mountedStage(visual.stage());
        var projection=com.codex.blacklight.combat.ConsumePlacementTrack.projection(com.codex.blacklight.combat.GrappleMotion.sceneKey(entity,visual),com.codex.blacklight.combat.GrappleMotion.receiverProgress(entity,visual,partial));
        if(projection.isEmpty())return false;
        double[] frame=projection.get();
        String[] names={"hips","torso","body","head","right_arm","left_arm","right_forearm","left_forearm","right_thigh","left_thigh","right_leg","left_leg","right_shin","left_shin"};
        int[] joints={6,1,1,0,2,3,7,8,4,5,4,5,9,10};
        var worlds=new java.util.IdentityHashMap<software.bernie.geckolib.cache.object.GeoBone,org.joml.Quaternionf>();
        var root=model.getAnimationProcessor().getBone("root");
        if(root!=null){var bind=root.getInitialSnapshot();root.updateRotation(bind.getRotX(),bind.getRotY(),bind.getRotZ());}
        int bound=0;
        for(int i=0;i<names.length;i++) {
            var bone=model.getAnimationProcessor().getBone(names[i]);if(bone==null)continue;
            int joint=joints[i];
            var q=com.codex.blacklight.combat.ConsumePlacementTrack.quaternion(frame,7+joint*4);
            // Projection is in vanilla's Y-down model space; Gecko's geometry uses Y-up.
            q.set(-q.x,q.y,-q.z,q.w);
            boolean support=joint==4||joint==5||joint==9||joint==10;
            q=new org.joml.Quaternionf().slerp(q,large?(support?0:.65F):1);
            var world=q.mul(bindWorld(bone));worlds.put(bone,new org.joml.Quaternionf(world));
            var parent=bone.getParent();
            var parentWorld=parent==null?new org.joml.Quaternionf():worlds.getOrDefault(parent,bindWorld(parent));
            var local=new org.joml.Quaternionf(parentWorld).invert().mul(world);
            var angles=local.getEulerAnglesZYX(new org.joml.Vector3f());
            bone.updateRotation(angles.x,angles.y,angles.z);
            bound++;
        }
        ReceiverPoseDiagnostics.record(entity,model,com.codex.blacklight.combat.GrappleMotion.sceneKey(entity,visual),bound);
        return bound>0;
    }
    private static org.joml.Quaternionf bindWorld(software.bernie.geckolib.cache.object.GeoBone bone){
        var bind=bone.getInitialSnapshot();
        var local=new org.joml.Quaternionf().rotationZYX(bind.getRotZ(),bind.getRotY(),bind.getRotX());
        return bone.getParent()==null?local:bindWorld(bone.getParent()).mul(local);
    }
}
