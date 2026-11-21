package com.codex.blacklight.combat;

import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.network.CombatImpactPayload;
import com.codex.blacklight.network.InfectedImpactPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Accepted player contacts use the P1 material split: flesh spray or metal sparks.
 * The original source sprites stay private; rendering uses authored equivalents. */
public final class PlayerHitEffects {
    private PlayerHitEffects(){}
    public static boolean fleshBody(LivingEntity target) {
        return !(target instanceof IronGolem || target instanceof com.codex.blacklight.entity.MilitaryUav
                || target instanceof net.minecraft.world.entity.monster.AbstractSkeleton
                || target instanceof net.minecraft.world.entity.animal.SnowGolem);
    }

    public static net.minecraft.sounds.SoundEvent hardContactSound(LivingEntity target, Vec3 point) {
        if (metalAt(target, point)) return net.minecraft.sounds.SoundEvents.ANVIL_HIT;
        if (target instanceof net.minecraft.world.entity.monster.AbstractSkeleton) return net.minecraft.sounds.SoundEvents.BONE_BLOCK_HIT;
        if (target instanceof net.minecraft.world.entity.animal.SnowGolem) return net.minecraft.sounds.SoundEvents.SNOW_HIT;
        return null;
    }

    public static boolean metalAt(LivingEntity target, Vec3 point) {
        if (target instanceof IronGolem || target instanceof com.codex.blacklight.entity.MilitaryUav) return true;
        double height = (point.y - target.getBoundingBox().minY) / Math.max(.01, target.getBbHeight());
        var slot = height >= .82 ? net.minecraft.world.entity.EquipmentSlot.HEAD
                : height > .46 ? net.minecraft.world.entity.EquipmentSlot.CHEST
                : height > .18 ? net.minecraft.world.entity.EquipmentSlot.LEGS : net.minecraft.world.entity.EquipmentSlot.FEET;
        if (!(target.getItemBySlot(slot).getItem() instanceof net.minecraft.world.item.ArmorItem armor)) return false;
        var material = armor.getMaterial();
        return material.equals(net.minecraft.world.item.ArmorMaterials.IRON) || material.equals(net.minecraft.world.item.ArmorMaterials.CHAIN)
                || material.equals(net.minecraft.world.item.ArmorMaterials.GOLD) || material.equals(net.minecraft.world.item.ArmorMaterials.NETHERITE);
    }

    public static void emit(ServerPlayer player,LivingEntity target,InfectedAttackStyle style,float size,WeaponCutContact.Report report){
        emit(player,target,style,size,report,FleshStrike.GENERIC,false);
    }
    public static void emit(ServerPlayer player,LivingEntity target,InfectedAttackStyle style,float size,WeaponCutContact.Report report,FleshStrike strike,boolean charged){
        emit(player,target,style,size,report,strike,charged,null);
    }
    static void emit(ServerPlayer player,LivingEntity target,InfectedAttackStyle style,float size,
                     WeaponCutContact.Report report,FleshStrike strike,boolean charged,WeaponMove move){
        if(player.level().isClientSide||target.level()!=player.level())return;
        if (report == null || report.contacts().isEmpty()) {
            var box = target.getBoundingBox();
            Vec3 eye = player.getEyePosition();
            Vec3 point = box.clip(eye, box.getCenter()).orElse(box.getCenter());
            surface(player, target, style, size, strike, charged, point, eye.subtract(point), 0);
            return;
        }
        // A claw can cross more than one patch of skin. Duplicate contacts on one edge share a spray.
        int limit = strike == FleshStrike.CLAW && report.kind() == WeaponCutContact.Kind.SLICE ? 3 : 1;
        var emitted = new java.util.ArrayList<WeaponCutContact.Contact>(limit);
        for (var hit : report.contacts()) {
            if (emitted.stream().anyMatch(old -> java.util.Objects.equals(old.edge(),hit.edge()) || old.point().distanceToSqr(hit.point()) < .0064)) continue;
            var normal = hit.travel().scale(-1);
            if (report.kind()==WeaponCutContact.Kind.SLICE && !metalAt(target,hit.point()))
                normal=FleshStrike.cutSpray(hit.travel(),hit.point().subtract(target.getBoundingBox().getCenter()));
            if ((metalAt(target,hit.point()) || normal.lengthSqr() < 1e-8) && hit.normal().lengthSqr() > 1e-8) normal = hit.normal();
            if(strike==FleshStrike.FIST && move!=null && !metalAt(target,hit.point()))normal=FleshStrike.fistSpray(move,normal);
            if(strike==FleshStrike.HAMMER && hit.edge().equals("hammer-floor-press"))style=InfectedAttackStyle.BURST;
            boolean lightLash=move!=null && move.lightLash();
            if(lightLash && !metalAt(target,hit.point())){
                var forward=hit.point().subtract(player.position()).multiply(1,0,1).normalize();
                var travel=hit.travel();
                // Blood follows the departing length of cable; retain a little forward carry.
                var across=travel.subtract(forward.scale(travel.dot(forward)*.78));
                if(across.lengthSqr()>1e-8)normal=across.normalize();
            }
            int index = emitted.size();
            surface(player, target, style, index == 0 ? size : size * .56F, strike, charged && index == 0, hit.point(), normal, index, strike==FleshStrike.LASH && !lightLash?0:hit.region());
            emitted.add(hit);
            if (emitted.size() == limit) break;
        }
    }

    static void surface(ServerPlayer player, LivingEntity target, InfectedAttackStyle style, float size,
                        FleshStrike strike, boolean charged, Vec3 point, Vec3 normal, int index) {
        surface(player,target,style,size,strike,charged,point,normal,index,0);
    }
    private static void surface(ServerPlayer player,LivingEntity target,InfectedAttackStyle style,float size,
                                FleshStrike strike,boolean charged,Vec3 point,Vec3 normal,int index,int region){
        if(!Double.isFinite(point.x+point.y+point.z+normal.x+normal.y+normal.z))return;
        if(normal.lengthSqr()<1e-8)normal=new Vec3(0,1,0);else normal=normal.normalize();
        if(metalAt(target,point)){
            PacketDistributor.sendToPlayersNear(player.serverLevel(),null,point.x,point.y,point.z,80,
                    new CombatImpactPayload(point.x,point.y,point.z,size,false,false,(float)normal.x,(float)normal.y,(float)normal.z));
        }else{
            PacketDistributor.sendToPlayersNear(player.serverLevel(),null,point.x,point.y,point.z,80,
                    new InfectedImpactPayload(player.getId(),target.getId(),InfectedImpactPayload.contactRegion(InfectedImpactPayload.appearance(style,strike,charged),region),point.x,point.y,point.z,
                            (float)normal.x,(float)normal.y,(float)normal.z,size,player.level().getGameTime()*31+target.getId()*73L+player.getId()+index*101L));
        }
    }
}
