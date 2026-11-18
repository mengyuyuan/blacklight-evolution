package com.codex.blacklight.combat;

import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.network.CombatImpactPayload;
import com.codex.blacklight.network.InfectedImpactPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.Vec3;
import com.codex.blacklight.forge.PacketDistributor;

/** Accepted player contacts use the P1 material split: flesh spray or metal sparks.
 * The original source sprites stay private; rendering uses authored equivalents. */
public final class PlayerHitEffects {
    private PlayerHitEffects(){}
    public static void emit(ServerPlayer player,LivingEntity target,InfectedAttackStyle style,float size,WeaponCutContact.Report report){
        emit(player,target,style,size,report,FleshStrike.GENERIC,false);
    }
    public static void emit(ServerPlayer player,LivingEntity target,InfectedAttackStyle style,float size,WeaponCutContact.Report report,FleshStrike strike,boolean charged){
        if(player.level().isClientSide||target.level()!=player.level())return;
        Vec3 point,normal;
        if(report!=null&&!report.contacts().isEmpty()){
            var hit=report.contacts().get(0);point=hit.point();
            // A cutting plane faces across the swing. Flesh emission uses incoming
            // hit-volume travel; only metal sparks need the contact surface normal.
            normal=hit.travel().scale(-1);
            if((target instanceof IronGolem||normal.lengthSqr()<1e-8)&&hit.normal().lengthSqr()>1e-8)normal=hit.normal();
        }else{
            var box=target.getBoundingBox();var eye=player.getEyePosition();var center=box.getCenter();
            point=box.clip(eye,center).orElse(center);normal=eye.subtract(point);
        }
        if(!Double.isFinite(point.x+point.y+point.z+normal.x+normal.y+normal.z))return;
        if(normal.lengthSqr()<1e-8)normal=new Vec3(0,1,0);else normal=normal.normalize();
        if(target instanceof IronGolem){
            PacketDistributor.sendToPlayersNear(player.serverLevel(),null,point.x,point.y,point.z,80,
                    new CombatImpactPayload(point.x,point.y,point.z,size,false,false,(float)normal.x,(float)normal.y,(float)normal.z));
        }else{
            PacketDistributor.sendToPlayersNear(player.serverLevel(),null,point.x,point.y,point.z,80,
                    new InfectedImpactPayload(player.getId(),target.getId(),InfectedImpactPayload.appearance(style,strike,charged),point.x,point.y,point.z,
                            (float)normal.x,(float)normal.y,(float)normal.z,size,player.level().getGameTime()*31+target.getId()*73L+player.getId()));
        }
    }
}
