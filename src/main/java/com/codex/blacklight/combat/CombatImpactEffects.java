package com.codex.blacklight.combat;

import com.codex.blacklight.network.*;
import com.codex.blacklight.registry.ModParticles;
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
        hammerEmitters(p,move,center,radius);
        var form = com.codex.blacklight.registry.ModWeaponSounds.equipped(p);
        boolean hammer = form == PowerForm.HAMMERFISTS;
        TerrainImpactEffects.weaponGroundSound(p,form,hit,hammer?.92F:1F,move.quake()?.90F:1F);
        PacketDistributor.sendToPlayer(p,new PhysicalFeedbackPayload(move.quake()?PhysicalFeedbackPayload.Effect.GROUND_SPIKE:PhysicalFeedbackPayload.Effect.CLAW_HEAVY,move.quake()?1:.42F));
        return true;
    }
    private static void hammerEmitters(ServerPlayer p,WeaponMove move,Vec3 center,float radius) {
        if (!HammerQuakeContact.owns(move) && move!=WeaponMove.HAMMER_DROP && !move.hammerComboSlam()) return;
        var level=p.serverLevel();
        double y=center.y+.12;
        // shockWave001 combines distortion and dust. The existing terrain ripple
        // supplies the distortion at this same contact; sample its dust around it.
        for (int i=0;i<8;i++) {
            double angle=i*Math.PI/4, dx=Math.cos(angle), dz=Math.sin(angle);
            double distance=Math.min(radius*.22,.8);
            level.sendParticles(ModParticles.HAMMER_WAVE_DUST.get(),center.x+dx*distance,y,center.z+dz*distance,
                    0,dx*.14,0,dz*.14,1);
        }
        boolean drop=move==WeaponMove.HAMMER_DROP || move.hammerComboSlam();
        if (move==WeaponMove.HAMMER_QUAKE) return;
        int delay=drop && move==move.uncharged()?0:1;
        level.sendParticles(ModParticles.HAMMER_FIRE.get(),center.x,y+.1,center.z,0,delay,0,0,1);
        level.sendParticles(ModParticles.HAMMER_BURST_DUST.get(),center.x,y,center.z,4,.35,.05,.35,.07);
        level.sendParticles(ModParticles.HAMMER_DIRT.get(),center.x,y,center.z,5,.35,.09,.35,.09);
        if (move==WeaponMove.HAMMER_COMBO_SPECIAL_CHARGED)
            level.sendParticles(ModParticles.HAMMER_DEBRIS.get(),center.x,y,center.z,4,.3,.1,.3,.14);
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
            case FIST -> .66F;
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
        PlayerHitEffects.emit(p,v,strike.style(kind),size*ImpactTier.of(move).effectScale,contact,strike,fullyCharged,move);
    }
}
