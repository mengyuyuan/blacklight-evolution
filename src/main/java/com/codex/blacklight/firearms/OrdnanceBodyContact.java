package com.codex.blacklight.firearms;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.infection.InfectedAttackStyle;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** One explosion surface per living body, retaining independent boss limbs. */
final class OrdnanceBodyContact {
    record Surface(LivingEntity target,Vec3 point,HydraHurtContact.Contact contact){
        boolean hurt(DamageSource source,float damage){
            var body=AnimatedHurtBody.of(target);
            return body!=null&&contact!=null?body.hurtAt(source,damage,contact):target.hurt(source,damage);
        }
    }
    static Surface nearest(LivingEntity target,Vec3 center,java.util.function.Predicate<Vec3> exposed){
        var body=AnimatedHurtBody.of(target);
        if(body==null){
            var point=CombatTargeting.hitboxes(target).stream().map(box->CombatTargeting.closestPoint(center,box))
                    .filter(exposed).min(java.util.Comparator.comparingDouble(center::distanceToSqr)).orElse(null);
            return point==null?null:new Surface(target,point,null);
        }
        var contact=nearestContact(body.hurtRegions(0),center,exposed);
        return contact==null?null:new Surface(target,contact.point(),contact);
    }
    static HydraHurtContact.Contact nearestContact(java.util.List<HydraContact.Region> regions,Vec3 center,java.util.function.Predicate<Vec3> exposed){
        HydraHurtContact.Contact nearest=null;double distance=Double.POSITIVE_INFINITY;
        for(var region:regions)for(var box:region.boxes()){
            Vec3 point=box.nearest(center);double d=center.distanceToSqr(point);
            // A covered near limb must not shield another exposed limb. There
            // is still one chosen surface and one parent contact per blast.
            if(d<distance&&exposed.test(point)){
                distance=d;Vec3 normal=center.subtract(point).normalize();if(normal.lengthSqr()<.01)normal=new Vec3(0,1,0);
                nearest=new HydraHurtContact.Contact(region.bone(),point,normal,InfectedAttackStyle.CRUSH);
            }
        }
        return nearest;
    }
    private OrdnanceBodyContact(){}
}
