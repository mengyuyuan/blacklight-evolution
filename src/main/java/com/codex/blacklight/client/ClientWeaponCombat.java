package com.codex.blacklight.client;

import com.codex.blacklight.combat.WeaponMove;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.network.*;
import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.combat.LocalWeaponMotion;
import net.minecraft.client.Minecraft;
import com.codex.blacklight.forge.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClientWeaponCombat {
    private static boolean left,right,action,jump,controlling;
    private static boolean requireLeftRelease,requireRightRelease;
    private static int sentAt;
    private static com.codex.blacklight.power.PowerForm inputForm;
    private static net.minecraft.client.player.LocalPlayer inputOwner;
    private static boolean fixtureOwnsInput;
    private static boolean wheelAttack,wheelSpecial,resumeAttack,resumeSpecial;
    private static com.codex.blacklight.power.PowerForm resumeForm;
    private static int resumeUntil;
    public static void beginWeaponWheel(){
        var mc=Minecraft.getInstance();
        wheelAttack=SkillBindings.down(mc.options.keyAttack)&&!requireLeftRelease;
        wheelSpecial=SkillBindings.down(mc.options.keyUse)&&!requireRightRelease;
        resumeForm=null;
    }
    public static void finishWeaponWheel(com.codex.blacklight.power.PowerForm form){
        var mc=Minecraft.getInstance();
        if(mc.player==null||com.codex.blacklight.power.UtilityPowerSystem.supported(form))return;
        resumeForm=form;resumeAttack=wheelAttack;resumeSpecial=wheelSpecial;resumeUntil=mc.player.tickCount+40;
    }
    /** Installed only after the disposable native skill QA validates its literal directory/save. */
    static void setFixtureOwnsInput(boolean owns){fixtureOwnsInput=owns;}
    private ClientWeaponCombat(){}
    public static boolean active(){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null||com.codex.blacklight.firearms.FirearmsCombat.holdingFirearm(mc.player))return false;
        var state=com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE);
        return !com.codex.blacklight.city.HydraTransport.active(mc.player)&&new PersonalCombatInput(state.isInfected(),state.isPrototypeMode(),state.getActivePower(),
                mc.player.getMainHandItem().isEmpty()&&mc.player.getOffhandItem().isEmpty(),com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.CONSUMING),VehicleControls.ownsInput()).active();
    }
    public static void poll(){
        if(fixtureOwnsInput)return;
        var mc=Minecraft.getInstance();if(mc.getWindow()==null)return;
        long window=mc.getWindow().getWindow();
        boolean l=SkillBindings.down(mc.options.keyAttack),r=SkillBindings.down(mc.options.keyUse);
        boolean a=ClientClawEvents.physicalGrabDown(),j=mc.options.keyJump.isDown();
        wheelAttack&=l;wheelSpecial&=r;resumeAttack&=l;resumeSpecial&=r;
        if(!mc.isWindowActive()||mc.getCameraEntity()!=mc.player
                ||mc.screen!=null&&!(mc.screen instanceof com.codex.blacklight.client.screen.PowerWheelScreen)){resumeForm=null;wheelAttack=false;wheelSpecial=false;}
        if(inputOwner!=mc.player){
            resumeForm=null;wheelAttack=false;wheelSpecial=false;
            inputOwner=mc.player;controlling=false;inputForm=null;sentAt=0;
            left=l;right=r;action=a;jump=j;requireLeftRelease=l;requireRightRelease=r;
        }
        // Match vanilla's controlled-camera input boundary; background/cinematic views
        // must not turn unrelated mouse clicks into attacks on the player entity.
        if(!mc.isWindowActive()||mc.getCameraEntity()!=mc.player||!active()){
            if(controlling&&mc.getConnection()!=null)PacketDistributor.sendToServer(new WeaponInputPayload(false,false,true));
            controlling=false;left=l;right=r;action=a;jump=j;requireLeftRelease=l;requireRightRelease=r;return;
        }
        if(active()){
            var form=com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).getActivePower();
            boolean resume=resumeForm==form&&mc.player.tickCount<=resumeUntil;
            if(resume){
                if(resumeAttack)requireLeftRelease=false;
                if(resumeSpecial)requireRightRelease=false;
                resumeForm=null;
            }else if(mc.player.tickCount>resumeUntil)resumeForm=null;
            boolean formChanged=inputForm!=null&&inputForm!=form;
            // The server already discarded the old action. Forward physical input
            // immediately; preserve GUI/focus release guards, not a new swap guard.
            inputForm=form;
            if(!l)requireLeftRelease=false;if(!r)requireRightRelease=false;
            if(r&&(!right||formChanged||resume)&&!requireRightRelease)prepareSpecialMovement();
            if(resume||formChanged||l!=left||r!=right||a!=action||j!=jump||mc.player.tickCount-sentAt>=10){
                if(l&&(!left||formChanged)&&!requireLeftRelease||r&&(!right||formChanged)&&!requireRightRelease)
                    ClientMovementEvents.sendAttackDirection(mc);
                ClientTargetLock.sync();
                PacketDistributor.sendToServer(new WeaponInputPayload(l&&!requireLeftRelease,r&&!requireRightRelease,a,j,false));sentAt=mc.player.tickCount;
            }
            controlling=true;
            mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);
        }
        left=l;right=r;action=a;jump=j;
    }
    /** Predict only the grounded blade start; the server still selects and owns the action. */
    static boolean prepareSpecialMovement(){
        var mc=Minecraft.getInstance();
        if(!active()||!mc.player.isAlive()||mc.player.isSpectator()||mc.player.isPassenger()
                ||!mc.player.onGround()||!com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).isInfected()
                ||com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).getActivePower()!=com.codex.blacklight.power.PowerForm.BLADE
                ||com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.WEAPON_VISUAL).spec()!=null
                ||com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.CLAW_COMBAT).action()!=com.codex.blacklight.combat.ClawCombatState.Action.IDLE
                ||ClientMovementEvents.isChargingJump()||ClientMovementEvents.isWallRunning())return false;
        return ClientMovementEvents.prepareGroundedWeaponMovement(mc);
    }
    public static void animate(WeaponAnimationPayload payload){
        var mc=Minecraft.getInstance();var move=WeaponMove.byId(payload.move());if(mc.player==null||move==null)return;
        String key=payload.charging()?LocalWeaponMotion.find(move.id)
                .flatMap(LocalWeaponMotion.Definition::charge).map(LocalWeaponMotion.Charge::animation).orElse(move.id):move.id;
        if(payload.clipOffset()>0||!PlayerMotionController.isPlaying(mc.player,key))
            PlayerMotionController.request(mc.player,key,payload.clipOffset());
    }
}

