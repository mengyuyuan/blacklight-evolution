package com.codex.blacklight.firearms;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.VehicleWeaponDefinition;
import com.codex.blacklight.network.GunplayFeedbackPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** One bounded packet replaces dozens of server particle packets per rifle shot. */
public final class GunplayEffects {
    private GunplayEffects(){}
    public static int weapon(WeaponDefinition d){int index=WeaponCatalog.ALL.indexOf(d);if(index>=0)return index;
        for(int i=0;i<VehicleWeaponDefinition.ALL.size();i++)if(VehicleWeaponDefinition.ALL.get(i).weapon().equals(d))return 8+i;return 0;}
    public static boolean bullet(int id){return id>=0&&id<3||id==12;}
    public static boolean cannon(int id){return id==8||id==10||id==11;}
    public static void metalContact(LivingEntity actor,Vec3 at,Vec3 normal){
        if(!(actor.level() instanceof ServerLevel level))return;
        var packet=new GunplayFeedbackPayload(actor.getId(),0,2,3,at.x,at.y,at.z,at.x,at.y,at.z,(float)normal.x,(float)normal.y,(float)normal.z,1,level.random.nextLong());
        PacketDistributor.sendToPlayersNear(level,null,at.x,at.y,at.z,64,packet);
        level.playSound(null,at.x,at.y,at.z,net.minecraft.sounds.SoundEvents.CHAIN_HIT,net.minecraft.sounds.SoundSource.PLAYERS,.7F,.9F);
    }
    public static Vec3 muzzle(LivingEntity actor,Vec3 direction){
        var eye=actor.getEyePosition().add(0,-.18,0);var d=direction.normalize();var right=d.cross(new Vec3(0,1,0)).normalize();
        var at=eye.add(d.scale(.8)).add(right.scale(actor.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT?.15:-.15));
        var wall=actor.level().clip(new ClipContext(eye,at,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor));
        return wall.getType()==HitResult.Type.MISS?at:wall.getLocation().add(Vec3.atLowerCornerOf(wall.getDirection().getNormal()).scale(.025));
    }
    public static void shot(LivingEntity actor,WeaponDefinition d,Vec3 from,Vec3 to,int impact,Vec3 normal){shot(actor,null,d,from,to,impact,normal);}
    public static void shot(LivingEntity actor,MilitaryVehicle vehicle,WeaponDefinition d,Vec3 from,Vec3 to,int impact,Vec3 normal){
        if(!(actor.level() instanceof ServerLevel level))return;int weapon=weapon(d);
        var packet=new GunplayFeedbackPayload(vehicle==null?actor.getId():vehicle.getId(),weapon,0,impact,from.x,from.y,from.z,to.x,to.y,to.z,(float)normal.x,(float)normal.y,(float)normal.z,1,level.random.nextLong());
        if(packet.valid())PacketDistributor.sendToPlayersNear(level,null,from.x,from.y,from.z,96,packet);
        GunplaySounds.fire(level,actor,from,weapon);
    }
    public static void explosion(ServerLevel level,LivingEntity actor,WeaponDefinition d,Vec3 at,Vec3 normal){
        var packet=new GunplayFeedbackPayload(actor==null?-1:actor.getId(),weapon(d),1,1,at.x,at.y,at.z,at.x,at.y,at.z,(float)normal.x,(float)normal.y,(float)normal.z,Math.clamp(d.radius(),.5F,5),level.random.nextLong());
        if(packet.valid())PacketDistributor.sendToPlayersNear(level,null,at.x,at.y,at.z,112,packet);
        GunplaySounds.impact(level,at,d.radius());
    }
}
