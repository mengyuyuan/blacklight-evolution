package com.codex.blacklight.client;

import com.codex.blacklight.movement.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.network.MovementInputPayload;
import net.minecraft.client.Minecraft;
import com.codex.blacklight.forge.PacketDistributor;

public final class ClientWingFlight {
    public static boolean tick(Minecraft mc){
        var p=mc.player;if(p==null||!WingFlightSystem.eligible(p)||ClientMovementEvents.weaponOwnsMovement(mc)||WingFlightSystem.consumeOwnsMovement(p))return false;
        boolean input=mc.screen==null&&mc.isWindowActive();
        int forward=input?(SkillBindings.down(mc.options.keyUp)?1:0)-(SkillBindings.down(mc.options.keyDown)?1:0):0;
        int side=input?(SkillBindings.down(mc.options.keyRight)?1:0)-(SkillBindings.down(mc.options.keyLeft)?1:0):0;
        boolean up=input&&SkillBindings.down(mc.options.keyJump),down=input&&SkillBindings.down(mc.options.keyShift),boost=input&&SkillBindings.down(mc.options.keySprint);
        float yaw=com.codex.blacklight.client.camera.PrototypeChaseCamera.ownsView()?com.codex.blacklight.client.camera.PrototypeChaseCamera.controlYaw():p.getYRot();
        applyInput(mc,forward,side,up,down,boost,yaw);
        return true;
    }
    static void applyInput(Minecraft mc,int forward,int side,boolean up,boolean down,boolean boost,float yaw){
        var p=mc.player;if(p==null)return;
        var movement=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE);
        movement.setCameraYaw(yaw);movement.acceptInput(up,false,forward,side,boost,down);
        movement.cancelCharge();movement.wallTraversal().reset();movement.endDash();
        PacketDistributor.sendToServer(new MovementInputPayload(up,false,forward,side,boost,down,yaw));
        if(forward!=0||side!=0){
            p.setYRot(yaw);p.setYHeadRot(yaw);
            p.yBodyRot=net.minecraft.util.Mth.rotLerp(.25F,p.yBodyRot,yaw);
        }
        if(p.onGround()&&!WingFlightSystem.flying(p)){
            boolean sprint=boost&&forward>0&&!down;p.setSprinting(sprint);
            if(sprint&&!ClientClawEvents.isGroundSpikeActionActive()){
                double max=MovementTuningSettings.sprintMaxSpeed()*com.codex.blacklight.power.MovementUpgrade.sprintMultiplier(ModAttachments.getData(p,ModAttachments.PLAYER_STATE).getMovementUpgradeMask());
                if(com.codex.blacklight.power.UtilityPowerSystem.active(p,com.codex.blacklight.power.PowerForm.ARMOR))max*=.75;
                p.setDeltaMovement(TraversalSteering.sprint(p,p.getDeltaMovement(),max,MovementTuningSettings.sprintAcceleration(),forward,side));
            }
        }
        // Equipping wings must not freeze the shared gait/facing snapshot. This
        // also updates remote players instead of leaving their last ground pose.
        PrototypeAnimationController.tickLevel(mc,false,forward!=0||side!=0,boost);
    }
    private ClientWingFlight(){}
}
