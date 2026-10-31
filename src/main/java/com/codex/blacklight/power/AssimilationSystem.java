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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
/** Shared, server-only settlement for human and slime consumption. */
@EventBusSubscriber(modid="blacklight")
public final class AssimilationSystem {
    private static final String SETTLED="BlacklightAssimilationSettled";
    private static final ResourceLocation CREST=new ResourceLocation("blacklight","organ_crest");
    public record Yield(float healing,int biomass,int ep,TissueCore core,int quality,java.util.Map<TissueCore,Integer> samples) {
        public Yield { samples=java.util.Map.copyOf(samples); }
        public Yield(float healing,int biomass,int ep,TissueCore core,int quality){this(healing,biomass,ep,core,quality,core==null?java.util.Map.of():java.util.Map.of(core,quality));}
    }
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
        float heal=hunter?8:com.codex.blacklight.forge.WingMath.clamp(health*.2F,2,6);
        if(target instanceof BlacklightCommonInfected)
            heal=Math.max(heal,(float)(p.getMaxHealth()/CombatScale.units(p)*OutbreakConfig.COMMON_HEAL.get()));
        heal*=ConsumeProgression.healingMultiplier(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.GROWTH));
        int biomass=hunter?120:(int)com.codex.blacklight.forge.WingMath.clamp(health*2,10,60),ep=hunter?30:target.getType().is(com.codex.blacklight.combat.ConsumeSystem.PASSIVE)?2:10;
        TissueCore core=null;int quality=0;
        if(tissue(target)){
            var host=(BlacklightInfectedHost)target;float factor=TissueCore.yieldMultiplier(host.tissueAge());
            heal*=factor;biomass=Math.round(biomass*factor);ep=Math.round(ep*factor);
            core=host.knownNativeTissueCore();quality=core==null?0:TissueCore.quality(host.tissueAge());
        }
        int jaw=rank(p,TissueCore.JAW);heal*=1+.1F*jaw;biomass=Math.round(biomass*(1+.05F*jaw));
        var samples=new java.util.EnumMap<TissueCore,Integer>(TissueCore.class);
        if(core!=null&&quality>0)samples.put(core,quality);
        if(tissue(target))for(var entry:((BlacklightInfectedHost)target).tissueInheritance().organs().entrySet()){
            var organ=entry.getValue();
            if(organ.mature()&&organ.integrated()>=100)samples.merge(entry.getKey(),(int)Math.min(3,Math.floor(organ.integrated()/100)),Math::max);
        }
        return new Yield(heal,biomass,ep,core,quality,samples);
    }
    /** Called only after successful execution/removal. A cancelled attempt pays nothing. */
    public static boolean settle(ServerPlayer p,Mob target,Yield reward,boolean heal){
        if(p.level().isClientSide||target.level()!=p.level()||!p.isAlive()||target.getPersistentData().getBoolean(SETTLED)
                ||target.isAlive()&&!target.isRemoved()&&!(target instanceof BlacklightHunter hunter&&hunter.isBeingConsumed()))return false;
        target.getPersistentData().putBoolean(SETTLED,true);
        var research=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.ORGAN_RESEARCH);int bonus=0;
        for(var entry:reward.samples().entrySet()){
            var core=entry.getKey();int quality=com.codex.blacklight.forge.WingMath.clamp(entry.getValue(),0,3);
            if(quality<=research.sample(core))continue;
            int previous=research.sample(core),sampleBonus=0;
            for(int rank=previous+1;rank<=quality;rank++)sampleBonus+=40*rank;
            bonus+=sampleBonus;research=research.discover(core,quality);
            p.displayClientMessage(Component.translatable("message.blacklight.organ_discovered",Component.translatable("upgrade.blacklight."+core.id),quality,sampleBonus),false);
        }
        if(research!=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.ORGAN_RESEARCH)){com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.ORGAN_RESEARCH,research);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.ORGAN_RESEARCH);}
        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);state.addBiomass(reward.biomass());state.addEvolutionPoints(reward.ep()+bonus);
        com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);GrowthSystem.consumed(p,target);
        if(heal)CriticalMassSystem.healFromConsume(p,reward.healing());
        return true;
    }
    public static int rank(Player player,TissueCore core){return ModAttachments.peekData(player,ModAttachments.ORGAN_RESEARCH,com.codex.blacklight.state.OrganResearch.EMPTY).rank(core);}
    public static double grabBonus(Player player){return .5*rank(player,TissueCore.GRASP);}
    public static void purchase(ServerPlayer p,TissueCore core){
        // The seven new entries currently drive infected-host anatomy/AI. Do not sell an unimplemented player skill.
        if(!core.legacy())return;
        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);var progress=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.ORGAN_RESEARCH);
        int price=TissueCore.cost(progress.rank(core)+1);
        if(!p.isAlive()||p.isSpectator()||!state.isInfected()||!progress.canBuy(core)||state.getEvolutionPoints()<price)return;
        state.addEvolutionPoints(-price);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.ORGAN_RESEARCH,progress.buy(core));com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.ORGAN_RESEARCH);updateCrest(p);
    }
    private static boolean active(Player p){var s=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);return s.isInfected()&&s.isPrototypeMode();}
    public static void updateCrest(Player p){CombatScale.modifier(p.getAttribute(Attributes.KNOCKBACK_RESISTANCE),CREST,active(p)?.1*rank(p,TissueCore.CREST):0,AttributeModifier.Operation.ADDITION);}
    public static float damage(LivingEntity entity,net.minecraft.world.damagesource.DamageSource source,float damage){
        if(entity.level().isClientSide||damage<=0)return damage;
        float amount=damage;
        boolean trueDamage=com.codex.blacklight.combat.DevastatorDamage.isTrueDamage(source);
        if(entity instanceof BlacklightInfectedHost h&&tissue(h)&&TissueCore.quality(h.tissueAge())>=2&&h.tissueCoreFracture()<1
                &&!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)&&!trueDamage)
            amount*=h.nativeTissueCore()==TissueCore.CARAPACE?.65F:.85F;
        if(source.getEntity() instanceof Player p&&source.getDirectEntity()==p&&active(p))amount*=1+.05F*rank(p,TissueCore.MUSCLE);
        if(entity instanceof Player p&&active(p)&&!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)&&!trueDamage)amount*=1-.05F*rank(p,TissueCore.CARAPACE);
        return amount;
    }
}
