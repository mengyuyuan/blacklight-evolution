package com.codex.blacklight.combat;

import com.codex.blacklight.network.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Different cues for a swing, a solid ground contact, and a successful body hit. */
public final class CombatImpactEffects {
    private CombatImpactEffects(){}
    public static void swing(ServerPlayer p,WeaponMove move,Vec3 forward){
        boolean hammer=move.form==PowerForm.HAMMERFISTS;
        boolean blade=move.form==PowerForm.BLADE;
        com.codex.blacklight.registry.ModWeaponSounds.play(p,com.codex.blacklight.registry.ModWeaponSounds.equipped(p),"swing",p.position(),.75F,WeaponImpact.finisher(move)?.86F:1F);

    }
    public static void ground(ServerPlayer p,WeaponMove move,Vec3 forward){
        if(!p.onGround())return;
        if(move==WeaponMove.BLADE_DIVE){BladeEffects.ground(p);return;}
        var level=p.serverLevel();var center=p.position().add(forward.scale(move.quake()?0:1.05));
        var hit=level.clip(new ClipContext(center.add(0,.9,0),center.add(0,-1.2,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        ground(p,move,hit);
    }
    /** The physical fist sweep supplies its actual block surface, including slabs and slopes. */
    public static void ground(ServerPlayer p,WeaponMove move,BlockHitResult hit){
        if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=net.minecraft.core.Direction.UP)return;
        var level=p.serverLevel();var center=hit.getLocation();float radius=(float)(move.quake()?AttackReach.reach(p,move):(move==move.uncharged()?1.65:2.2)*AttackReach.scale(p));
        if(!TerrainImpactEffects.emit(p,hit,radius,move.quake()))return;
        com.codex.blacklight.registry.ModWeaponSounds.play(p,com.codex.blacklight.registry.ModWeaponSounds.equipped(p),"ground",center,1F,move.quake()?.9F:1F);
        PacketDistributor.sendToPlayer(p,new PhysicalFeedbackPayload(move.quake()?PhysicalFeedbackPayload.Effect.GROUND_SPIKE:PhysicalFeedbackPayload.Effect.CLAW_HEAVY,move.quake()?1:.42F));
    }
    public static void target(ServerPlayer p,LivingEntity v,WeaponMove move){
        target(p,v,move,WeaponCutContact.Report.NONE,false);
    }
    public static void target(ServerPlayer p,LivingEntity v,WeaponMove move,WeaponCutContact.Report contact){
        target(p,v,move,contact,false);
    }
    public static void target(ServerPlayer p,LivingEntity v,WeaponMove move,WeaponCutContact.Report contact,boolean fullyCharged){
        var strike=FleshStrike.of(p.getData(ModAttachments.PLAYER_STATE).getActivePower(),move);
        float size=switch(strike){
            case FIST -> .48F;
            case LASH -> .6F;
            case HAMMER -> .8F;
            case MUSCLE -> .7F;
            case EDGE -> move.form==PowerForm.BLADE?.65F:.5F;
            default -> .5F;
        };
        var kind=contact.kind();
        if(contact.contacts().isEmpty()&&(strike==FleshStrike.EDGE||strike==FleshStrike.LASH))
            kind=WeaponCutContact.contactKind(move);
        PlayerHitEffects.emit(p,v,strike.style(kind),size*ImpactTier.of(move).effectScale,contact,strike,fullyCharged);
    }
}
