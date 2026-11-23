package com.codex.blacklight.power;

import com.codex.blacklight.world.FieldNotes;
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
    public record Yield(float healing,int biomass,int ep,TissueCore core,int quality,java.util.Map<TissueCore,Integer> samples) {
        public Yield { samples=java.util.Map.copyOf(samples); }
        public Yield(float healing,int biomass,int ep,TissueCore core,int quality){this(healing,biomass,ep,core,quality,core==null?java.util.Map.of():java.util.Map.of(core,quality));}
    }
    private AssimilationSystem(){}
    public static boolean tissue(Mob target){return target instanceof BlacklightInfectedHost h&&!h.hasMultipartBody()&&!h.devourerIdentity();}
    public static boolean requiresCoreWeakening(BlacklightInfectedHost host){
        return tissue(host)&&host.tissueAge()>=TissueGrowth.MATURITY_TICKS;
    }
    public static boolean ready(LivingEntity target){
        if(!(target instanceof BlacklightInfectedHost h)||!requiresCoreWeakening(h))return true;
        float fraction=target.getHealth()/target.getMaxHealth();
        return fraction<=(TissueCore.quality(h.tissueAge())>=2?.10F:.15F)
                &&(TissueCore.quality(h.tissueAge())<2||h.tissueCoreFracture()>=1);
    }
    /** Both currencies use the live maximum, including the target's combat health scaling. */
    public static int fullReward(LivingEntity target) {
        float maximum = target.getMaxHealth();
        return Float.isFinite(maximum) && maximum > 0 ? Math.round(maximum * 0.25F) : 0;
    }
    public static Yield quote(ServerPlayer p,Mob target){
        boolean hunter=target instanceof BlacklightHunter;
        float health=CombatScale.originalMaximum(target);if(!Float.isFinite(health)||health<0)health=0;
        float heal=hunter?8:Math.clamp(health*.2F,2,6);
        if(target instanceof BlacklightCommonInfected)
            heal=Math.max(heal,(float)(p.getMaxHealth()/CombatScale.units(p)*OutbreakConfig.COMMON_HEAL.get()));
        heal*=ConsumeProgression.healingMultiplier(p.getData(ModAttachments.GROWTH));
        int reward = fullReward(target);
        TissueCore core=null;int quality=0;
        if(tissue(target)){
            var host=(BlacklightInfectedHost)target;float factor=TissueCore.yieldMultiplier(host.tissueAge());
            heal*=factor;
            core=host.knownNativeTissueCore();quality=core==null?0:TissueCore.quality(host.tissueAge());
        }
        if(NativeInfection.active(target)){
            long age=NativeInfection.age(target);float factor=TissueCore.yieldMultiplier(age);heal*=factor;
            core=NativeInfection.core(target);quality=TissueCore.quality(age);
        }
        heal *= 1 + .1F * rank(p, TissueCore.JAW);
        var samples=new java.util.EnumMap<TissueCore,Integer>(TissueCore.class);
        if(core!=null&&quality>0)samples.put(core,quality);
        var inherited=tissue(target)?((BlacklightInfectedHost)target).tissueInheritance()
                :NativeInfection.active(target)?NativeTissue.inheritance(target):TissueInheritance.EMPTY;
        for(var entry:inherited.organs().entrySet()){
            var organ=entry.getValue();
            if(organ.mature()&&organ.integrated()>=100)samples.merge(entry.getKey(),(int)Math.min(3,Math.floor(organ.integrated()/100)),Math::max);
        }
        return new Yield(heal,reward,reward,core,quality,samples);
    }
    /** Called only after successful execution/removal. A cancelled attempt pays nothing. */
    public static boolean settle(ServerPlayer p,Mob target,Yield reward,boolean heal){
        if(p.level().isClientSide||target.level()!=p.level()||!p.isAlive()||target.getPersistentData().getBoolean(SETTLED)
                ||target.isAlive()&&!target.isRemoved()&&!(target instanceof BlacklightHunter hunter&&hunter.isBeingConsumed()))return false;
        target.getPersistentData().putBoolean(SETTLED,true);
        var research=p.getData(ModAttachments.ORGAN_RESEARCH);int bonus=0;
        for(var entry:reward.samples().entrySet()){
            var core=entry.getKey();int quality=Math.clamp(entry.getValue(),0,3);
            if(quality<=research.sample(core))continue;
            int previous=research.sample(core),sampleBonus=0;
            for(int rank=previous+1;rank<=quality;rank++)sampleBonus+=40*rank;
            bonus+=sampleBonus;research=research.discover(core,quality);
            FieldNotes.record(p, Component.translatable("message.blacklight.organ_discovered",Component.translatable("upgrade.blacklight."+core.id),quality,sampleBonus));
        }
        if(research!=p.getData(ModAttachments.ORGAN_RESEARCH)){p.setData(ModAttachments.ORGAN_RESEARCH,research);p.syncData(ModAttachments.ORGAN_RESEARCH);}
        var state=p.getData(ModAttachments.PLAYER_STATE);state.addBiomass(reward.biomass());state.addEvolutionPoints(reward.ep());state.addEvolutionPoints(bonus);
        p.syncData(ModAttachments.PLAYER_STATE);GrowthSystem.consumed(p,target);
        if(heal)CriticalMassSystem.healFromConsume(p,reward.healing());
        PrototypeMetabolism.feed(p,target);
        if (target instanceof com.codex.blacklight.entity.BlacklightInfectedHost host && host.claimGanglion())
            p.drop(new net.minecraft.world.item.ItemStack(com.codex.blacklight.registry.ModItems.EVOLVED_GANGLION.get()), false);
        if(target instanceof com.codex.blacklight.entity.MilitarySoldier soldier
                && soldier.commandRole()==com.codex.blacklight.military.MilitaryRole.COMMANDER
                && target.getRandom().nextInt(3)==0){
            var key=new net.minecraft.world.item.ItemStack(com.codex.blacklight.registry.ModItems.BLOODTOX_COMMAND_KEY.get());
            if(!p.getInventory().add(key)) p.drop(key,false,true);
            FieldNotes.record(p, Component.translatable("survival.blacklight.command_acquired"));
        }
        return true;
    }
    /** Shares the consume settlement marker, but does not grant consume-only progression. */
    public static boolean settleKill(ServerPlayer p,Mob target,int biomass,int ep){return settleKill(p,target,biomass,ep,true);}
    static boolean settleKill(ServerPlayer p,Mob target,int biomass,int ep,boolean sync){
        if(p.level().isClientSide||target.level()!=p.level()||!p.isAlive()||p.isRemoved()||p.isSpectator()
                ||target.getHealth()>0||target.isAlive()||target.getPersistentData().getBoolean(SETTLED)
                ||biomass<0||ep<0||biomass==0&&ep==0)return false;
        var state=p.getData(ModAttachments.PLAYER_STATE);if(!state.isInfected())return false;
        target.getPersistentData().putBoolean(SETTLED,true);
        state.addBiomass(biomass);state.addEvolutionPoints(ep);if(sync)p.syncData(ModAttachments.PLAYER_STATE);
        return true;
    }
    public static int rank(Player player,TissueCore core){return ModAttachments.peekData(player,ModAttachments.ORGAN_RESEARCH,com.codex.blacklight.state.OrganResearch.EMPTY).rank(core);}
    public static double grabBonus(Player player){return .5*rank(player,TissueCore.GRASP);}
    public static void purchase(ServerPlayer p,TissueCore core){
        // The seven new entries currently drive infected-host anatomy/AI. Do not sell an unimplemented player skill.
        if(!core.legacy())return;
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
        boolean trueDamage=com.codex.blacklight.combat.DevastatorDamage.isTrueDamage(event.getSource());
        if(event.getEntity() instanceof BlacklightInfectedHost h&&tissue(h)&&TissueCore.quality(h.tissueAge())>=2&&h.tissueCoreFracture()<1
                &&!event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)&&!trueDamage)
            amount*=h.nativeTissueCore()==TissueCore.CARAPACE?.65F:.85F;
        if(event.getSource().getEntity() instanceof Player p&&event.getSource().getDirectEntity()==p&&active(p))amount*=1+.05F*rank(p,TissueCore.MUSCLE);
        if(event.getEntity() instanceof Player p&&active(p)&&!event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)&&!trueDamage)amount*=1-.05F*rank(p,TissueCore.CARAPACE);
        event.setNewDamage(amount);
    }
}
