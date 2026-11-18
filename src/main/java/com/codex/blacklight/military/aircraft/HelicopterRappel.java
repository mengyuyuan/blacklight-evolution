package com.codex.blacklight.military.aircraft;

import com.codex.blacklight.combat.ConsumeControl;
import com.codex.blacklight.entity.BlackwatchHelicopter;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.military.vehicle.VehicleKinematics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.codex.blacklight.combat.ForgeEntityTickEvent;

/** Detached, independently saved zipline infantry; the helicopter never carries them down to the ground. */
@EventBusSubscriber(modid="blacklight")
public final class HelicopterRappel {
    private static final String KEY="BlacklightHelicopterRappel";
    public static final int EXIT_TICKS=14,LAND_TICKS=15;
    // startup FIG MoveAtVelocity @1399788: (0,-11,0) source world units/second.
    public static final double DOWN_PER_TICK=11.0/20;
    private HelicopterRappel(){}
    public static boolean active(MilitarySoldier troop){return troop.getPersistentData().contains(KEY);}
    public static boolean belongs(MilitarySoldier troop,BlackwatchHelicopter craft){
        var data=troop.getPersistentData().getCompound(KEY);
        return data.hasUUID("Craft")&&data.getUUID("Craft").equals(craft.getUUID());
    }
    public static Vec3 anchor(BlackwatchHelicopter craft,int side){return craft.local(side*3.6,.85,-.5);}
    public static int side(MilitarySoldier troop){return troop.getPersistentData().getCompound(KEY).getInt("Side")<0?-1:1;}
    public static void begin(BlackwatchHelicopter craft,MilitarySoldier troop,int side){
        if(craft.level().isClientSide||troop.level()!=craft.level()||active(troop))return;
        var data=new CompoundTag();data.putUUID("Craft",craft.getUUID());data.putInt("Side",side<0?-1:1);
        data.putBoolean("PreviousNoAi",troop.isNoAi());data.putBoolean("PreviousNoGravity",troop.isNoGravity());
        troop.getPersistentData().put(KEY,data);troop.getPersistentData().putUUID("HelicopterDeliveredBy",craft.getUUID());
        troop.stopRiding();troop.setPos(anchor(craft,side));troop.setDeltaMovement(Vec3.ZERO);
        troop.getNavigation().stop();troop.setNoAi(true);troop.setNoGravity(true);troop.fallDistance=0;
        troop.getPersistentData().remove("HelicopterTroop");
    }
    public static void release(MilitarySoldier troop,BlackwatchHelicopter craft,boolean grounded){
        if(!active(troop))return;var data=troop.getPersistentData().getCompound(KEY);
        troop.getPersistentData().remove(KEY); // Ownership ends before AI/handoff callbacks.
        troop.setNoAi(data.getBoolean("PreviousNoAi"));troop.setNoGravity(data.getBoolean("PreviousNoGravity"));
        troop.fallDistance=0;
        if(grounded&&troop.isAlive()){
            troop.setDeltaMovement(Vec3.ZERO);troop.adoptFieldPost();troop.setAutomaticUnitJoin(true);
            if(craft!=null&&!craft.isRemoved())craft.combat().assignDeliveredTroop(troop);
        }
    }
    @SubscribeEvent public static void tick(ForgeEntityTickEvent.Post event){
        if(!(event.getEntity() instanceof MilitarySoldier troop)||!(troop.level() instanceof ServerLevel level)||!active(troop))return;
        var data=troop.getPersistentData().getCompound(KEY);
        BlackwatchHelicopter craft=data.hasUUID("Craft")&&level.getEntity(data.getUUID("Craft")) instanceof BlackwatchHelicopter h?h:null;
        if(craft==null||craft.isRemoved()||craft.health()<=0||craft.pilot().owned()
                ||craft.phase()!=BlackwatchHelicopter.Phase.UNLOAD||!craft.deployment().enabled()
                ||!troop.isAlive()||troop.isPassenger()||ConsumeControl.protectedEntity(troop)||troop.hurtTime>0){
            release(troop,craft,false);return;
        }
        int age=data.getInt("Age")+1;data.putInt("Age",age);
        if(age>200||troop.distanceToSqr(craft)>40*40){release(troop,craft,false);return;}
        troop.setDeltaMovement(Vec3.ZERO);troop.fallDistance=0;
        if(age<=EXIT_TICKS)return; // Source deplane pose duration; detached doorway hold is an MC attachment adapter.
        var sweep=troop.getBoundingBox().expandTowards(0,-DOWN_PER_TICK-.05,0);
        if(!VehicleKinematics.loaded(level,sweep)||!level.getWorldBorder().isWithinBounds(sweep)||troop.isInLava()){
            release(troop,craft,false);return;
        }
        if(troop.isInWaterOrBubble()){release(troop,craft,false);return;}
        troop.move(MoverType.SELF,new Vec3(0,-DOWN_PER_TICK,0));troop.setDeltaMovement(Vec3.ZERO);troop.fallDistance=0;
        if(troop.onGround()){
            int landed=data.getInt("Landed")+1;data.putInt("Landed",landed);
            if(landed>=LAND_TICKS)release(troop,craft,true);
        }else data.putInt("Landed",0);
    }
    /** Client pose lookup uses the aircraft's authoritative tracked IDs, never unsynchronized persistent data. */
    public static boolean clientActive(MilitarySoldier troop){
        if(!troop.level().isClientSide)return active(troop);
        for(var craft:troop.level().getEntitiesOfClass(BlackwatchHelicopter.class,troop.getBoundingBox().inflate(40)))
            for(int id:craft.deploymentData().getIntArray("Troops"))if(id==troop.getId())return true;
        return false;
    }
}
