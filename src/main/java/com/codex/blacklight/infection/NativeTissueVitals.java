package com.codex.blacklight.infection;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.WaterAnimal;

/** Shared, food-paid circulation augments the original mob's own breathing. */
public final class NativeTissueVitals {
    private record Cached(CompoundTag owner,CompoundTag saved,TissueVitals value){}
    private static final Map<Mob,Cached> CACHE=Collections.synchronizedMap(new WeakHashMap<>());
    private NativeTissueVitals(){}
    public static void forget(Mob mob){if(mob!=null)CACHE.remove(mob);}
    public static boolean eligible(Mob mob){return mob!=null&&!mob.level().isClientSide&&TissueActions.eligible(mob)
            &&!(mob instanceof com.codex.blacklight.entity.BlacklightInfectedHost);}
    public static TissueVitals.Profile profile(Mob mob){
        var s=TissueSubject.of(mob);if(s==null)return TissueVitals.Profile.EMPTY;
        return new TissueVitals.Profile(s.power(TissueCore.GILL_LAMELLA),s.power(TissueCore.LUNG),s.power(TissueCore.AIRFLOW),
                s.power(TissueCore.BOOK_LUNG),s.power(TissueCore.TRACHEA),s.power(TissueCore.HEART),s.power(TissueCore.BRANCHIAL_HEART),s.power(TissueCore.SPLEEN));
    }
    private static TissueVitals state(Mob mob,CompoundTag data){
        var saved=data.getCompound("Vitals");var cached=CACHE.get(mob);
        if(cached!=null&&cached.owner()==data&&cached.saved()==saved)return cached.value();
        var vitals=saved.isEmpty()?new TissueVitals():TissueVitals.read(saved);
        data.put("Vitals",saved);CACHE.put(mob,new Cached(data,saved,vitals));return vitals;
    }
    private static void save(Mob mob,CompoundTag data,TissueVitals vitals){
        var saved=vitals.write();data.put("Vitals",saved);CACHE.put(mob,new Cached(data,saved,vitals));
    }
    private static boolean vent(Mob mob){
        double reach=mob.getBbWidth()*.35;
        for(int sign:new int[]{-1,1}){
            var p=BlockPos.containing(mob.getX()+reach*sign,mob.getY()+mob.getBbHeight()*.35,mob.getZ());
            if(!mob.level().hasChunkAt(p)||mob.level().isOutsideBuildHeight(p))continue;
            var block=mob.level().getBlockState(p);if(block.getFluidState().isEmpty()&&block.getCollisionShape(mob.level(),p).isEmpty())return true;
        }return false;
    }
    public static TissueVitals.Breath breathe(Mob mob,boolean sourceCanBreathe,boolean sourceWaterRespiration){
        if(!eligible(mob))return null;var p=profile(mob);if(!p.active())return null;
        boolean headWater=mob.isEyeInFluid(FluidTags.WATER),headAir=mob.getEyeInFluidType().isAir()
                ||mob.level().getBlockState(BlockPos.containing(mob.getX(),mob.getEyeY(),mob.getZ())).is(net.minecraft.world.level.block.Blocks.BUBBLE_COLUMN);
        var velocity=mob.getDeltaMovement();boolean moving=velocity.horizontalDistanceSqr()>.0025||!mob.onGround()&&Math.abs(velocity.y)>.05;
        boolean external=sourceCanBreathe&&!headAir&&!sourceWaterRespiration;
        var environment=new TissueVitals.Environment(headAir,headWater,p.trachea()>0&&vent(mob),moving,mob.isInWater(),
                !(mob instanceof WaterAnimal)||mob.getMaxAirSupply()>300,sourceWaterRespiration,external,
                TissueActions.core(mob)!=null||TissueCompetition.controls(mob)||mob.swinging,
                mob.getBbWidth()*mob.getBbWidth()*mob.getBbHeight(),mob.getHealth()/Math.max(.001,mob.getMaxHealth()));
        var data=mob.getPersistentData().getCompound(NativeInfection.DATA);var vitals=state(mob,data);
        var decision=vitals.breathe(p,environment,mob.getAirSupply(),mob.getMaxAirSupply(),NativeTissue.nutrition(mob));save(mob,data,vitals);
        if(decision.spent()>0&&!NativeTissue.spend(mob,decision.spent()))throw new IllegalStateException("Native physiology overspent server reserve");
        if(decision.airGrant()>0)mob.setAirSupply(Math.min(mob.getMaxAirSupply(),mob.getAirSupply()+decision.airGrant()));
        if(mob.tickCount%10==0||decision.airGrant()>0)NativeTissue.publish(mob,data);
        return decision;
    }
    public static int recover(Mob mob,int normal){
        if(!eligible(mob))return normal;var p=profile(mob);if(!p.active())return normal;
        var data=mob.getPersistentData().getCompound(NativeInfection.DATA);var vitals=state(mob,data);
        var decision=vitals.recover(normal,p,NativeTissue.nutrition(mob),mob.getHealth()/Math.max(.001,mob.getMaxHealth()));save(mob,data,vitals);
        if(decision.spent()>0&&!NativeTissue.spend(mob,decision.spent()))throw new IllegalStateException("Native circulation overspent server reserve");
        return decision.ticks();
    }
    public static void damaged(Mob mob,float actual){
        if(!eligible(mob)||!Float.isFinite(actual)||actual<=0||!profile(mob).active())return;
        var data=mob.getPersistentData().getCompound(NativeInfection.DATA);var vitals=state(mob,data);
        vitals.damaged(actual/Math.max(.001,mob.getMaxHealth()));save(mob,data,vitals);NativeTissue.publish(mob,data);
    }
    static void repaired(Mob mob,double paid){
        if(!eligible(mob)||paid<=0||!profile(mob).active())return;
        var data=mob.getPersistentData().getCompound(NativeInfection.DATA);var vitals=state(mob,data);vitals.repaired(paid);save(mob,data,vitals);
    }
    public static CompoundTag visual(Mob mob,CompoundTag data){var p=profile(mob);return p.active()?state(mob,data).visual(p):new CompoundTag();}
    public static CompoundTag saved(Mob mob){return eligible(mob)?mob.getPersistentData().getCompound(NativeInfection.DATA).getCompound("Vitals").copy():new CompoundTag();}
}
