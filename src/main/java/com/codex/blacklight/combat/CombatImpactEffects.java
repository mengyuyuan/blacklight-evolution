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
import com.codex.blacklight.forge.PacketDistributor;

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
    public static boolean ground(ServerPlayer p,WeaponMove move,BlockHitResult hit){
        if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=net.minecraft.core.Direction.UP)return false;
        var level=p.serverLevel();var center=hit.getLocation();float radius=(float)(move.quake()?AttackReach.reach(p,move):(move==move.uncharged()?1.65:2.2)*AttackReach.scale(p));
        if(!TerrainImpactEffects.emit(p,hit,radius,move.quake()))return false;
        var form = com.codex.blacklight.registry.ModWeaponSounds.equipped(p);
        boolean hammer = form == PowerForm.HAMMERFISTS;
        com.codex.blacklight.registry.ModWeaponSounds.play(p, form, "ground", center, hammer ? .72F : 1F, move.quake() ? .9F : 1F);
        TerrainImpactEffects.surfaceSound(p, hit, hammer ? .88F : .62F, move.quake() ? .75F : .9F);
        PacketDistributor.sendToPlayer(p,new PhysicalFeedbackPayload(move.quake()?PhysicalFeedbackPayload.Effect.GROUND_SPIKE:PhysicalFeedbackPayload.Effect.CLAW_HEAVY,move.quake()?1:.42F));
        return true;
    }
    public static void devastator(ServerPlayer p,LivingEntity victim,WeaponMove move,Vec3 origin) {
        if (AnimatedHurtBody.of(victim)!=null) return;
        var box=victim.getBoundingBox();
        var center=box.getCenter();
        int kind=DevastatorSystem.kind(move);
        Vec3 from=kind==0?new Vec3(center.x,box.minY-.20,center.z):origin;
        Vec3 axis=center.subtract(from).normalize();
        var entry=box.clip(from,center).orElse(CombatTargeting.closestPoint(from,victim));
        var style=switch(kind) {
            case 0 -> com.codex.blacklight.infection.InfectedAttackStyle.PIERCE;
            case 1 -> com.codex.blacklight.infection.InfectedAttackStyle.WHIP;
            case 2 -> com.codex.blacklight.infection.InfectedAttackStyle.BURST;
            default -> com.codex.blacklight.infection.InfectedAttackStyle.CRUSH;
        };
        PlayerHitEffects.surface(p,victim,style,1.42F,FleshStrike.GENERIC,false,entry,kind==0?axis:axis.scale(-1),0);
    }
    public static void target(ServerPlayer p,LivingEntity v,WeaponMove move){
        target(p,v,move,WeaponCutContact.Report.NONE,false);
    }
    public static void target(ServerPlayer p,LivingEntity v,WeaponMove move,WeaponCutContact.Report contact){
        target(p,v,move,contact,false);
    }
    public static void target(ServerPlayer p,LivingEntity v,WeaponMove move,WeaponCutContact.Report contact,boolean fullyCharged){
        var strike=FleshStrike.of(ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getActivePower(),move);
        float size=switch(strike){
            case FIST -> .48F;
            case LASH -> .6F;
            case HAMMER -> .8F;
            case MUSCLE -> .7F;
            case EDGE -> .76F;
            case CLAW -> .62F;
            default -> .5F;
        };
        var kind=contact.kind();
        if(contact.contacts().isEmpty()&&(strike==FleshStrike.EDGE||strike==FleshStrike.CLAW||strike==FleshStrike.LASH))
            kind=WeaponCutContact.contactKind(move);
        PlayerHitEffects.emit(p,v,strike.style(kind),size*ImpactTier.of(move).effectScale,contact,strike,fullyCharged);
    }
}
