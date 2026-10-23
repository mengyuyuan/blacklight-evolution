package com.codex.blacklight.power;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Shared, server-only settlement for human and slime consumption. */
@EventBusSubscriber(modid="blacklight")
public final class AssimilationSystem {
    private static final String SETTLED="BlacklightAssimilationSettled";
    private static final ResourceLocation CREST=ResourceLocation.fromNamespaceAndPath("blacklight","organ_crest");
    public record Yield(float healing,int biomass,int ep,TissueCore core,int quality) {}
    private AssimilationSystem(){}
    public static boolean tissue(Mob target){return target instanceof BlacklightInfectedHost h&&!h.hasMultipartBody()&&!h.devourerIdentity();}
    public static boolean ready(LivingEntity target){
        if(!(target instanceof BlacklightInfectedHost h)||!tissue(h)||h.tissueAge()<TissueGrowth.MATURITY_TICKS)return true;
        float fraction=target.getHealth()/target.getMaxHealth();
        return fraction<=(TissueCore.quality(h.tissueAge())>=2?.10F:.15F)
                &&(TissueCore.quality(h.tissueAge())<2||h.tissueCoreFracture()>=1);
    }
    public static Yield quote(ServerPlayer p,Mob target){
        boolean hunter=target instanceof BlacklightHunter;
        float health=CombatScale.originalMaximum(target);if(!Float.isFinite(health)||health<0)health=0;
        float heal=hunter?8:Math.clamp(health*.2F,2,6);
        heal*=ConsumeProgression.healingMultiplier(p.getData(ModAttachments.GROWTH));
        int biomass=hunter?120:(int)Math.clamp(health*2,10,60),ep=hunter?30:target.getType().is(com.codex.blacklight.combat.ConsumeSystem.PASSIVE)?2:10;
        TissueCore core=null;int quality=0;
        if(tissue(target)){
            var host=(BlacklightInfectedHost)target;float factor=TissueCore.yieldMultiplier(host.tissueAge());
            heal*=factor;biomass=Math.round(biomass*factor);ep=Math.round(ep*factor);
            core=TissueCore.forSeed(TissueOrgans.seed(host.getUUID()));quality=TissueCore.quality(host.tissueAge());
        }
        int jaw=rank(p,TissueCore.JAW);heal*=1+.1F*jaw;biomass=Math.round(biomass*(1+.05F*jaw));
        return new Yield(heal,biomass,ep,core,quality);
    }
    /** Called only after successful execution/removal. A cancelled attempt pays nothing. */
    public static boolean settle(ServerPlayer p,Mob target,Yield reward,boolean heal){
        if(p.level().isClientSide||target.level()!=p.level()||!p.isAlive()||target.getPersistentData().getBoolean(SETTLED)
                ||target.isAlive()&&!target.isRemoved()&&!(target instanceof BlacklightHunter hunter&&hunter.isBeingConsumed()))return false;
        target.getPersistentData().putBoolean(SETTLED,true);
        var research=p.getData(ModAttachments.ORGAN_RESEARCH);int bonus=0;
        if(reward.core()!=null&&reward.quality()>research.sample(reward.core())){
            int previous=research.sample(reward.core());
            for(int rank=previous+1;rank<=reward.quality();rank++)bonus+=40*rank;
            research=research.discover(reward.core(),reward.quality());
            p.setData(ModAttachments.ORGAN_RESEARCH,research);p.syncData(ModAttachments.ORGAN_RESEARCH);
            p.displayClientMessage(Component.translatable("message.blacklight.organ_discovered",Component.translatable("upgrade.blacklight."+reward.core().id),reward.quality(),bonus),false);
        }
        var state=p.getData(ModAttachments.PLAYER_STATE);state.addBiomass(reward.biomass());state.addEvolutionPoints(reward.ep()+bonus);
        p.syncData(ModAttachments.PLAYER_STATE);GrowthSystem.consumed(p,target);
        if(heal)CriticalMassSystem.healFromConsume(p,reward.healing());
        return true;
    }
    public static int rank(Player player,TissueCore core){return ModAttachments.peekData(player,ModAttachments.ORGAN_RESEARCH,com.codex.blacklight.state.OrganResearch.EMPTY).rank(core);}
    public static double grabBonus(Player player){return .5*rank(player,TissueCore.GRASP);}
    public static void purchase(ServerPlayer p,TissueCore core){
        var state=p.getData(ModAttachments.PLAYER_STATE);var progress=p.getData(ModAttachments.ORGAN_RESEARCH);
        int price=TissueCore.cost(progress.rank(core)+1);
        if(!p.isAlive()||p.isSpectator()||!state.isInfected()||!progress.canBuy(core)||state.getEvolutionPoints()<price)return;
        state.addEvolutionPoints(-price);p.syncData(ModAttachments.PLAYER_STATE);
        p.setData(ModAttachments.ORGAN_RESEARCH,progress.buy(core));p.syncData(ModAttachments.ORGAN_RESEARCH);updateCrest(p);
    }
    private static boolean active(Player p){var s=p.getData(ModAttachments.PLAYER_STATE);return s.isInfected()&&s.isPrototypeMode();}
    public static void updateCrest(Player p){CombatScale.modifier(p.getAttribute(Attributes.KNOCKBACK_RESISTANCE),CREST,active(p)?.1*rank(p,TissueCore.CREST):0,AttributeModifier.Operation.ADD_VALUE);}
    @SubscribeEvent public static void damage(LivingDamageEvent.Pre event){
        if(event.getEntity().level().isClientSide||event.getNewDamage()<=0)return;
        float amount=event.getNewDamage();
        if(event.getEntity() instanceof BlacklightInfectedHost h&&tissue(h)&&TissueCore.quality(h.tissueAge())>=2&&h.tissueCoreFracture()<1
                &&!event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))
            amount*=TissueCore.forSeed(TissueOrgans.seed(h.getUUID()))==TissueCore.CARAPACE?.65F:.85F;
        if(event.getSource().getEntity() instanceof Player p&&event.getSource().getDirectEntity()==p&&active(p))amount*=1+.05F*rank(p,TissueCore.MUSCLE);
        if(event.getEntity() instanceof Player p&&active(p)&&!event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))amount*=1-.05F*rank(p,TissueCore.CARAPACE);
        event.setNewDamage(amount);
    }
}
