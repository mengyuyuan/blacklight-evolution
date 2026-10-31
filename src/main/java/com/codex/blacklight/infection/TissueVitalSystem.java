package com.codex.blacklight.infection;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import net.minecraftforge.event.entity.living.LivingBreatheEvent;

/** Only our detached hosts use this physiology; other mods' entities keep their normal breathing hooks. */
@EventBusSubscriber(modid="blacklight")
public final class TissueVitalSystem {
    private TissueVitalSystem(){}
    public static void inherit(net.minecraft.nbt.CompoundTag data,Mob source){
        data.putBoolean("NativeWaterResp",!source.canDrownInFluidType(net.minecraftforge.common.ForgeMod.WATER_TYPE.get()));
        data.putBoolean("NativeAirResp",!(source instanceof WaterAnimal)||source.getMaxAirSupply()>300);
        data.putInt("NativeMaxAir",com.codex.blacklight.forge.WingMath.clamp(source.getMaxAirSupply(),300,24000));
    }
    public static void migrate(BlacklightInfectedHost host,net.minecraft.nbt.CompoundTag data){
        if(data.contains("NativeAirResp")&&data.contains("NativeWaterResp")&&data.contains("NativeMaxAir"))return;
        var id=net.minecraft.resources.ResourceLocation.tryParse(data.getString("Type"));
        var type=id==null?null:net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        try{if(type!=null&&type.create(host.level()) instanceof Mob source){inherit(data,source);return;}}catch(RuntimeException ignored){}
        data.putBoolean("NativeWaterResp",data.getString("Family").equals("water"));data.putBoolean("NativeAirResp",!data.getString("Family").equals("water"));data.putInt("NativeMaxAir",300);
    }
    private static boolean vent(BlacklightInfectedHost host){
        double reach=host.getBbWidth()*.35;for(int sign:new int[]{-1,1}){
            var p=BlockPos.containing(host.getX()+reach*sign,host.getY()+host.getBbHeight()*.35,host.getZ());
            if(!host.level().hasChunkAt(p)||host.level().isOutsideBuildHeight(p))continue;
            var state=host.level().getBlockState(p);if(state.getFluidState().isEmpty()&&state.getCollisionShape(host.level(),p).isEmpty())return true;
        }return false;
    }
    public static TissueVitals.Environment environment(BlacklightInfectedHost host,boolean external){
        boolean headWater=host.isEyeInFluid(FluidTags.WATER),headAir=host.getEyeInFluidType().isAir()
                ||host.level().getBlockState(BlockPos.containing(host.getX(),host.getEyeY(),host.getZ())).is(net.minecraft.world.level.block.Blocks.BUBBLE_COLUMN);
        var velocity=host.getDeltaMovement();boolean moving=velocity.horizontalDistanceSqr()>.0025||!host.onGround()&&Math.abs(velocity.y)>.05;
        var data=host.hostData();var p=host.vitalsProfile();
        return new TissueVitals.Environment(headAir,headWater,p.trachea()>0&&vent(host),moving,host.isInWater(),data.contains("NativeAirResp")?data.getBoolean("NativeAirResp"):!host.family().equals("water"),data.contains("NativeWaterResp")?data.getBoolean("NativeWaterResp"):host.family().equals("water"),external,
                host.organActionCore()!=null||host.tissueStrikeElapsed(0)>=0,host.getBbWidth()*host.getBbWidth()*host.getBbHeight(),host.getHealth()/Math.max(.001,host.getMaxHealth()));
    }
    @SubscribeEvent public static void breathe(LivingBreatheEvent event){
        if(!(event.getEntity() instanceof BlacklightInfectedHost host)||host.level().isClientSide||!host.isAlive()||host.hasMultipartBody()||host.devourerIdentity()||DevourerFeedingSystem.isDevourer(host))return;
        var profile=host.vitalsProfile();boolean headAir=host.getEyeInFluidType().isAir();
        boolean external=event.canBreathe()&&!headAir&&!host.hostData().getBoolean("NativeWaterResp");
        var decision=host.vitals().breathe(profile,environment(host,external),host.getAirSupply(),host.hostData().getInt("NativeMaxAir"),host.tissueNutrition());
        if(decision.spent()>0&&!host.spendTissueNutrition(decision.spent()))throw new IllegalStateException("Physiology overspent server reserve");
        if(decision.airGrant()>0)host.setAirSupply(Math.min(host.getMaxAirSupply(),host.getAirSupply()+decision.airGrant()));
        event.setCanBreathe(decision.canBreathe());event.setRefillAirAmount(decision.refill());
        // Forge 1.20.1 separately gates refilling while the head is submerged.
        event.setCanRefillAir(decision.refill()>0);
        if(!decision.canBreathe())event.setConsumeAirAmount(Math.max(1,event.getConsumeAirAmount()));
        if(host.tickCount%10==0||decision.airGrant()>0)host.publishVitals();
    }
}
