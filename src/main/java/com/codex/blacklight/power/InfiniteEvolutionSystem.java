package com.codex.blacklight.power;

import com.codex.blacklight.combat.CrossModCombat;
import com.codex.blacklight.combat.DevastatorDamage;
import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
@EventBusSubscriber(modid="blacklight")
public final class InfiniteEvolutionSystem {
    private static final ResourceLocation SPEED=new ResourceLocation("blacklight","infinite_mobility");
    private static final Map<ServerPlayer,Integer> LAST_HURT=new WeakHashMap<>();
    private InfiniteEvolutionSystem(){}
    public static long rank(Player p,EvolutionAttribute a){return com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.INFINITE_EVOLUTION).rank(a);}
    public static long effectiveRank(Player p,EvolutionAttribute a){return EvolutionProgression.infiniteUnlocked(p)?rank(p,a):0;}
    public static boolean purchase(ServerPlayer p,EvolutionAttribute a){
        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);var progress=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.INFINITE_EVOLUTION);
        int cost=PlayerEvolutionConfig.COST.get();
        if(!p.isAlive()||p.isSpectator()||!EvolutionProgression.infiniteUnlocked(p)||progress.rank(a)==Long.MAX_VALUE||state.getEvolutionPoints()<cost)return false;
        state.addEvolutionPoints(-cost);com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.INFINITE_EVOLUTION,progress.buy(a));
        com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.INFINITE_EVOLUTION);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);EvolutionProgression.refresh(p);GrowthSystem.updateHealth(p);updateMobility(p);
        return true;
    }
    public static double mobility(Player p){return PrototypeMetabolism.active(p)?EvolutionAttribute.mobilityMultiplier(effectiveRank(p,EvolutionAttribute.MOBILITY)):1;}
    private static void updateMobility(ServerPlayer p){CombatScale.modifier(p.getAttribute(Attributes.MOVEMENT_SPEED),SPEED,mobility(p)-1,AttributeModifier.Operation.MULTIPLY_TOTAL);}
    /** Called after the existing health-unit conversion. Never scales a hit twice. */
    public static float damage(LivingEntity victim,DamageSource source,float amount){
        if(CrossModCombat.administrative(source))return amount;
        double value=amount;
        if(source.getEntity() instanceof Player owner&&PrototypeMetabolism.active(owner)&&CrossModCombat.nativeAttack(source)){
            boolean skill=DevastatorDamage.isTrueDamage(source)||source.typeHolder().unwrapKey().map(k->k.location().getNamespace().equals("blacklight")).orElse(false);
            value*=skill?EvolutionAttribute.skillMultiplier(effectiveRank(owner,EvolutionAttribute.SKILL_POWER)):
                    EvolutionAttribute.attackMultiplier(effectiveRank(owner,EvolutionAttribute.STRENGTH));
        }
        if(victim instanceof Player p&&PrototypeMetabolism.active(p))value*=EvolutionAttribute.defenseMultiplier(effectiveRank(p,EvolutionAttribute.RESILIENCE));
        return (float)Math.min(Float.MAX_VALUE,Math.max(0,value));
    }
    @SubscribeEvent public static void hurt(com.codex.blacklight.combat.ForgeDamageEvent.Post event){if(event.getNewDamage()>0&&event.getEntity() instanceof ServerPlayer p)LAST_HURT.put(p,p.tickCount);}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!(event.player instanceof ServerPlayer p)||!p.isAlive()||p.isRemoved()||!ModAttachments.available(p))return;
        EvolutionProgression.refresh(p);updateMobility(p);
        if(p.tickCount%20==0&&PrototypeMetabolism.active(p)&&!BiomassSystem.active(p)&&p.isAlive()
                &&p.tickCount-(long)LAST_HURT.getOrDefault(p,p.tickCount-240000)>=PlayerEvolutionConfig.RECOVERY_DELAY.get()&&p.getHealth()<p.getMaxHealth())
            p.heal((float)Math.min(Float.MAX_VALUE,effectiveRank(p,EvolutionAttribute.RECOVERY)*PlayerEvolutionConfig.RECOVERY_PER_LEVEL.get()/CombatScale.units(p)));
    }
}
