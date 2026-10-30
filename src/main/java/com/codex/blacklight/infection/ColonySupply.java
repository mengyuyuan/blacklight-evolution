package com.codex.blacklight.infection;

import net.minecraft.nbt.CompoundTag;

/** Actual digested meals, paid construction and brood. A saved clock is not a food source. */
public final class ColonySupply {
    // A colony has many connected feeding surfaces, not the single mouth of an individual host.
    private static final TissueDigestion.Profile DIGESTION=new TissueDigestion.Profile(0,1,0,0,8,0);
    private TissueDigestion stomach=new TissueDigestion();
    private int reserve,upkeep;
    public int reserve(){return reserve;}public int queued(){return stomach.queued();}
    public int room(){return TissueMetabolism.MAX_RESERVE-reserve;}
    public int receive(int digested){int n=Math.min(room(),Math.max(0,digested));reserve+=n;return n;}
    public boolean offer(TissueDigestion.Kind kind,int value){return stomach.offer(kind,value,DIGESTION);}
    public void digest(int ticks){reserve=stomach.advance(ticks,reserve,DIGESTION).reserve();}
    public boolean pay(int cost){if(cost<=0||reserve<cost)return false;reserve-=cost;return true;}
    public void refund(int cost){if(cost>0)reserve=(int)Math.min(TissueMetabolism.MAX_RESERVE,(long)reserve+cost);}
    /** Small colonies need one nutrient per ten loaded seconds, large bodies need more. */
    public boolean maintain(int ticks,int solidCells){
        if(ticks<=0||reserve==0)return false;
        upkeep=Math.min(200,upkeep+Math.min(200,ticks));
        if(upkeep>=200){if(!pay(1+Math.min(4,Math.max(0,solidCells)/512)))return false;upkeep-=200;}
        return true;
    }
    public static int broodCost(OutbreakRules.Tier tier){int base=switch(tier){case FRESH->12;case GROWING->20;case MATURE->32;case HUNTER->64;case ANCIENT->120;};return Math.max(1,(int)Math.ceil(base*com.codex.blacklight.config.GameplayBalanceConfig.value(com.codex.blacklight.config.GameplayBalanceConfig.BROOD_COST)));}
    public CompoundTag write(){var t=new CompoundTag();t.putInt("Schema",1);t.putInt("Reserve",reserve);t.putInt("Upkeep",upkeep);t.put("Meals",stomach.write());return t;}
    public static ColonySupply read(CompoundTag t){var s=new ColonySupply();s.reserve=TissueMetabolism.clamp(t.getInt("Reserve"));s.upkeep=com.codex.blacklight.forge.WingMath.clamp(t.getInt("Upkeep"),0,200);s.stomach=TissueDigestion.read(t.getCompound("Meals"));return s;}
}
