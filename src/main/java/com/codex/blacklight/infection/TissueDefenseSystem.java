package com.codex.blacklight.infection;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.phys.Vec3;

public final class TissueDefenseSystem {
    private TissueDefenseSystem(){}
    public static TissueDefense.Decision incoming(BlacklightInfectedHost host,DamageSource source,float amount){
        var direct=source.getDirectEntity();boolean piercing=direct instanceof Projectile;
        boolean physical=(piercing||direct instanceof LivingEntity&&direct==source.getEntity())
                &&!source.is(DamageTypeTags.BYPASSES_ARMOR)&&!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                &&!com.codex.blacklight.combat.DevastatorDamage.isTrueDamage(source);
        Vec3 toward=null;if(piercing&&direct.getDeltaMovement().lengthSqr()>.00001)toward=direct.getDeltaMovement().scale(-1);
        else if(source.getSourcePosition()!=null)toward=source.getSourcePosition().subtract(host.position());
        if(toward==null||toward.horizontalDistanceSqr()<.000001)physical=false;
        double angle=Math.toRadians(host.yBodyRot);var facing=new Vec3(-Math.sin(angle),0,Math.cos(angle));
        double front=toward==null?0:facing.dot(new Vec3(toward.x,0,toward.z).normalize());
        double height=piercing?(direct.getY()-host.getY())/Math.max(.001,host.getBbHeight()):.55;
        return host.defense().reduce(host.defenseProfile(),amount,physical,piercing,front,height);
    }
    public static void tick(BlacklightInfectedHost host){
        if(!host.isAlive()||host.hasMultipartBody()||host.devourerIdentity()||DevourerFeedingSystem.isDevourer(host))return;
        var p=host.defenseProfile();if(!p.active())return;
        boolean rest=TissueFeeding.free(host)&&(host.getTarget()==null||!host.getTarget().isAlive())&&host.getDeltaMovement().horizontalDistanceSqr()<.0025;
        int spent=host.defense().advance(p,host.tissueNutrition(),rest);if(spent>0&&!host.spendTissueNutrition(spent))throw new IllegalStateException("Armour repair overspent actual nutrition");
        if(spent>0||host.tickCount%20==0)host.publishDefense();
    }
}
