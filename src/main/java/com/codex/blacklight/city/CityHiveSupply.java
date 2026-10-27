package com.codex.blacklight.city;

import com.codex.blacklight.infection.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/** The authored seven-port organism owns one saved, paid stomach and one loaded brood clock. */
public final class CityHiveSupply {
    private CityHiveSupply(){}
    public record Port(int hive,int index,BlockPos pos){}
    public static Port port(ServerLevel level,BlockPos pos){
        if(!CityTravel.isCity(level.dimension()))return null;
        for(int hive=0;hive<2;hive++)for(int i=0;i<7;i++)if(CitySystem.port(level,hive,i).equals(pos))return new Port(hive,i,pos.immutable());
        return null;
    }
    public static boolean live(ServerLevel level,CityData data,Port p){
        return p!=null&&!data.destroyed(p.hive)&&(data.brokenPorts[p.hive]&(1<<p.index))==0
                &&level.hasChunkAt(p.pos)&&level.isPositionEntityTicking(p.pos)&&level.getBlockState(p.pos).is(CityBlocks.HATCH.get());
    }
    public static ColonySupply supply(ServerLevel level,BlockPos pos){var data=CityData.get(level);var p=port(level,pos);return live(level,data,p)?data.hiveSupply[p.hive]:null;}
    public static boolean feed(ServerLevel level,BlockPos pos,Player player,InteractionHand hand){
        var data=CityData.get(level);var p=port(level,pos);if(!live(level,data,p))return false;
        return ColonyFeeding.feedAt(level,pos,player,hand,data.hiveSupply[p.hive],data::setDirty);
    }
    public static void tick(ServerLevel level,CityData data,int hive){
        long now=level.getGameTime();if(data.lastHiveSupplyTick[hive]==now)return;
        data.lastHiveSupplyTick[hive]=now;data.hiveSustained[hive]=false;if(data.destroyed(hive)||!WorldOutbreakSystem.active(level))return;
        Port mouth=null;
        for(int n=0;n<7;n++){int index=Math.floorMod(data.hiveFeedCursor[hive]+n,7);var candidate=new Port(hive,index,CitySystem.port(level,hive,index));if(live(level,data,candidate)){mouth=candidate;break;}}
        if(mouth==null)return;data.hiveFeedCursor[hive]=(mouth.index+1)%7;
        var stock=data.hiveSupply[hive];stock.digest(20);data.nextHiveFoodTick[hive]=ColonyFeeding.scavenge(level,mouth.pos,stock,data.nextHiveFoodTick[hive]);
        if(stock.maintain(20,0)){
            data.hiveSustained[hive]=true;data.hiveLoadedTicks[hive]=(int)Math.min(Integer.MAX_VALUE,(long)data.hiveLoadedTicks[hive]+20);data.hiveBroodTicks[hive]=Math.min(200,data.hiveBroodTicks[hive]+20);
        }data.setDirty();
    }
    public static boolean ready(CityData data,int hive){return !data.destroyed(hive)&&data.hiveSustained[hive]&&data.hiveBroodTicks[hive]>=200&&data.hiveSupply[hive].reserve()>=ColonySupply.broodCost(OutbreakRules.Tier.FRESH);}
    public static Mob hatch(ServerLevel level,CityData data,int hive,BlockPos pos,OutbreakRules.Tier tier){
        return hatch(level,data,hive,pos,tier,false);
    }
    public static Mob hatchHydra(ServerLevel level,CityData data,int hive,BlockPos pos){return hatch(level,data,hive,pos,OutbreakRules.Tier.ANCIENT,true);}
    private static Mob hatch(ServerLevel level,CityData data,int hive,BlockPos pos,OutbreakRules.Tier tier,boolean hydra){
        var p=port(level,pos);if(p==null||p.hive!=hive||!ready(data,hive)||!live(level,data,p)||!CityConfig.SPAWNS.get()||!data.population)return null;
        int stage=CityEcology.stage(level,pos);if(tier==OutbreakRules.Tier.GROWING&&stage<1||(tier==OutbreakRules.Tier.MATURE||tier==OutbreakRules.Tier.HUNTER)&&stage<2||tier==OutbreakRules.Tier.ANCIENT&&stage<3)return null;
        var stock=data.hiveSupply[hive];int cost=ColonySupply.broodCost(tier);if(!stock.pay(cost))return null;
        var state=level.getBlockState(pos);var outside=pos.relative(state.getValue(CityHatchBlock.FACING),3).below();
        // Reserve the single organism's brood before external join callbacks can reenter another mouth.
        data.hiveBroodTicks[hive]=0;boolean committed=false;
        try{
            Mob child=hydra?WorldOutbreakSystem.spawnHydraAt(level,outside):WorldOutbreakSystem.spawnAt(level,outside,tier);
            if(child==null)return null;committed=true;
            child.getPersistentData().putBoolean("BlacklightCityPopulation",true);child.addTag("BlacklightCityPopulation");
            data.hiveBirths[hive]=(int)Math.min(Integer.MAX_VALUE,(long)data.hiveBirths[hive]+1);data.setDirty();return child;
        }finally{if(!committed){stock.refund(cost);data.hiveBroodTicks[hive]=200;}}
    }
}
