package com.codex.blacklight.infection;

import com.codex.blacklight.entity.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.damagesource.DamageSource;

/** Attack structure, not merely the creature's colour or health. */
public enum InfectedAttackStyle {
    TEAR(14), PIERCE(16), CRUSH(20), WHIP(18), BURST(24), RESONANCE(14);
    public final int lifetime;
    InfectedAttackStyle(int lifetime){this.lifetime=lifetime;}
    public static InfectedAttackStyle resolve(int id){return values()[com.codex.blacklight.forge.WingMath.clamp(id,0,values().length-1)];}
    public static InfectedAttackStyle of(Mob attacker,DamageSource source){
        if(source.is(net.minecraft.world.damagesource.DamageTypes.SONIC_BOOM)||attacker instanceof net.minecraft.world.entity.monster.Guardian)return RESONANCE;
        if(source.getDirectEntity() instanceof InfectedTumor||source.getDirectEntity() instanceof BlacklightSpit||source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.ThrownPotion||source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile||attacker instanceof BlacklightHive)return BURST;
        if(source.getDirectEntity() instanceof Projectile)return PIERCE;
        if(attacker instanceof BlacklightInfectedHost host){
            var profile=host.nativeCombat().profile();
            if(!host.nativeAction().isEmpty()&&(profile==NativeInfectedProfile.SONIC||profile==NativeInfectedProfile.GUARDIAN))return RESONANCE;
            if(host.isEvolved())return switch(host.evolvedArchetype()){
                case STRENGTH,ARMORED->CRUSH;case TENDRIL,REGENERATOR->WHIP;case SENSORY->PIERCE;default->TEAR;
            };
            var core=host.organActionCore();if(core==null&&host.tissueStrikeElapsed(0)>=0)core=host.nativeTissueCore();
            if(core!=null)return switch(core){
                case CLUB,MUSCLE,HYDRAULIC->CRUSH;case HORN,CNIDOCYTE->PIERCE;
                case GRASP,HYDROSTAT,TONGUE,EXTRAORAL->WHIP;default->TEAR;
            };
            if(host.speciesId().equals("minecraft:iron_golem")||host.speciesId().equals("minecraft:ravager")||host.speciesId().equals("minecraft:warden"))return CRUSH;
        }
        if(attacker instanceof BlacklightHunter hunter)return hunter.attackMove()==com.codex.blacklight.combat.CreatureAttack.SLAM?CRUSH:TEAR;
        if(attacker instanceof BlacklightMutant mutant)return mutant.kind()==BlacklightMutant.Kind.BRUTE?CRUSH:mutant.kind()==BlacklightMutant.Kind.SPITTER?BURST:TEAR;
        if(attacker instanceof BlacklightHydra||attacker instanceof HiveTendril)return PIERCE;
        if(attacker instanceof net.minecraft.world.entity.monster.Ravager||attacker instanceof net.minecraft.world.entity.animal.IronGolem||attacker instanceof net.minecraft.world.entity.monster.warden.Warden)return CRUSH;
        return TEAR;
    }
}
