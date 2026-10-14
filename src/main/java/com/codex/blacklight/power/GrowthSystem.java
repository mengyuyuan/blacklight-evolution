package com.codex.blacklight.power;

import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.entity.BlacklightMutant;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import java.util.*;

/** EP buys upgrades; biomass is reserved for death recovery. Learned actions remain free. */
public final class GrowthSystem {
    private static final ResourceLocation HEALTH=ResourceLocation.fromNamespaceAndPath("blacklight","vitality");
    private static final Map<ServerPlayer,Integer> LAST_HURT=new WeakHashMap<>();
    private GrowthSystem(){}
    public static void purchase(ServerPlayer p,GrowthUpgrade upgrade){
        var state=p.getData(ModAttachments.PLAYER_STATE);var progress=p.getData(ModAttachments.GROWTH);
        if(upgrade.requires()!=null&&!progress.owns(upgrade.requires()))return;
        if(!p.isAlive()||p.isSpectator()||!state.isInfected()||progress.owns(upgrade)||state.getEvolutionPoints()<upgrade.cost)return;
        state.addEvolutionPoints(-upgrade.cost);
        p.setData(ModAttachments.PLAYER_STATE,state);p.syncData(ModAttachments.PLAYER_STATE);
        p.setData(ModAttachments.GROWTH,progress.buy(upgrade));p.syncData(ModAttachments.GROWTH);updateHealth(p);if(BiomassSystem.active(p))p.refreshDimensions();
        award(p,"first_evolution");p.displayClientMessage(Component.translatable("message.blacklight.growth_bought",Component.translatable("upgrade.blacklight."+upgrade.id)),true);
    }
    public static void consumed(ServerPlayer p,Mob mob){
        p.setData(ModAttachments.GROWTH,p.getData(ModAttachments.GROWTH).consume());p.syncData(ModAttachments.GROWTH);award(p,"first_consume");
        if(mob instanceof BlacklightMutant mutant){
            var form=switch(mutant.kind()){case STALKER->PowerForm.BLADE;case SPITTER->PowerForm.WHIPFIST;case BRUTE->PowerForm.HAMMERFISTS;};
            var state=p.getData(ModAttachments.PLAYER_STATE);
            if(state.unlock(form)){
                p.setData(ModAttachments.PLAYER_STATE,state);p.syncData(ModAttachments.PLAYER_STATE);
                p.displayClientMessage(Component.translatable("message.blacklight.gene_unlocked",Component.translatable("power.blacklight."+form.id())),false);
            }
        }
    }
    public static void award(ServerPlayer p,String id){
        var advancement=p.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("blacklight",id));
        if(advancement!=null)p.getAdvancements().award(advancement,"done");
    }
    public static void updateHealth(ServerPlayer p){
        var attribute=p.getAttribute(Attributes.MAX_HEALTH);if(attribute==null)return;
        float before=p.getMaxHealth(),health=p.getHealth();
        boolean infected=p.getData(ModAttachments.PLAYER_STATE).isInfected();
        var growth=p.getData(ModAttachments.GROWTH);
        double bonus=growth.owns(GrowthUpgrade.VITALITY_IV)?70:growth.owns(GrowthUpgrade.VITALITY_III)?45:
                growth.owns(GrowthUpgrade.VITALITY_II)?25:growth.owns(GrowthUpgrade.VITALITY)?10:0;
        CombatScale.modifier(attribute,HEALTH,infected?bonus:0,AttributeModifier.Operation.ADD_VALUE);
        CombatScale.attributes(p,infected);
        CombatScale.preserveHealthFraction(p,before,health);
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;updateHealth(p);
        if(p.tickCount%20!=0)return;
        if(!p.getData(ModAttachments.PLAYER_STATE).isInfected())return;
        award(p,"awakening");
        if(!BiomassSystem.active(p)&&p.isAlive()&&p.tickCount%100==0&&p.tickCount-LAST_HURT.getOrDefault(p,p.tickCount-200)>=160&&p.getData(ModAttachments.GROWTH).owns(GrowthUpgrade.REGENERATION))p.heal(1);
    }
    @SubscribeEvent public static void hurt(LivingDamageEvent.Post e){if(e.getNewDamage()>0&&e.getEntity() instanceof ServerPlayer p)LAST_HURT.put(p,p.tickCount);}
}
