package com.codex.blacklight.power;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

/** Health units are converted once, after armor/resistance and before absorption.
 * Authored attack windows, AI damage attributes and ordinary survival retain their units.
 */
@EventBusSubscriber(modid="blacklight")
public final class CombatScale {
    public static final float UNITS=5;
    private static final ResourceLocation HEALTH=ResourceLocation.fromNamespaceAndPath("blacklight","health_units");
    private static final ResourceLocation SPEED=ResourceLocation.fromNamespaceAndPath("blacklight","combat_speed");
    private static final ResourceLocation ABSORPTION=ResourceLocation.fromNamespaceAndPath("blacklight","absorption_units");
    private static final ResourceLocation ATTACK_SPEED=ResourceLocation.fromNamespaceAndPath("blacklight","combat_attack_speed");
    private static final java.util.Map<Mob,Boolean> INFECTION_STATE=new java.util.WeakHashMap<>();
    private CombatScale(){}
    public static float units(LivingEntity entity){
        var attribute=entity.getAttribute(Attributes.MAX_HEALTH);
        return attribute!=null&&attribute.hasModifier(HEALTH)?UNITS:1;
    }
    public static float originalMaximum(LivingEntity entity){return entity.getMaxHealth()/units(entity);}
    public static boolean modifier(AttributeInstance attribute,ResourceLocation id,double amount,AttributeModifier.Operation operation){
        if(attribute==null)return false;
        var old=attribute.getModifier(id);
        if(old!=null&&old.amount()==amount&&old.operation()==operation)return false;
        if(old!=null)attribute.removeModifier(id);
        if(amount!=0)attribute.addPermanentModifier(new AttributeModifier(id,amount,operation));
        return old!=null||amount!=0;
    }
    public static void attributes(LivingEntity entity,boolean scaled){
        float oldUnits=units(entity),absorption=entity.getAbsorptionAmount();
        modifier(entity.getAttribute(Attributes.MAX_HEALTH),HEALTH,scaled?UNITS-1:0,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(entity.getAttribute(Attributes.MAX_ABSORPTION),ABSORPTION,scaled?UNITS-1:0,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if(oldUnits!=units(entity))entity.setAbsorptionAmount(absorption*units(entity)/oldUnits);
        boolean enhanced=scaled && (entity instanceof Mob || entity instanceof Player p && PrototypeMetabolism.active(p));
        modifier(entity.getAttribute(Attributes.MOVEMENT_SPEED),SPEED,enhanced?.08:0,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(entity.getAttribute(Attributes.ATTACK_SPEED),ATTACK_SPEED,enhanced?.15:0,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
    public static void preserveHealthFraction(LivingEntity entity,float previousMaximum,float previousHealth){
        if(previousMaximum>0&&previousMaximum!=entity.getMaxHealth())
            entity.setHealth(Math.clamp(previousHealth/previousMaximum*entity.getMaxHealth(),0,entity.getMaxHealth()));
    }
    /** Also migrates saved ordinary mobs that carry the old global modifiers. */
    public static void synchronizeMob(Mob mob){
        if(mob.level().isClientSide||mob.isRemoved())return;
        boolean infected=com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(mob);
        float before=mob.getMaxHealth(),health=mob.getHealth();
        attributes(mob,infected);preserveHealthFraction(mob,before,health);
        INFECTION_STATE.put(mob,infected);
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof Mob mob)||mob.level().isClientSide||mob.isRemoved())return;
        boolean infected=com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(mob);
        Boolean previous=INFECTION_STATE.get(mob);
        if(previous==null||previous!=infected)synchronizeMob(mob);
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide()||!(event.getEntity() instanceof Mob mob))return;
        synchronizeMob(mob);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void damage(LivingDamageEvent.Pre event){
        if(!event.getEntity().level().isClientSide&&event.getNewDamage()>0)
            event.setNewDamage(com.codex.blacklight.combat.CrossModCombat.defend(event.getEntity(), event.getSource(),
                    (float)Math.min(Float.MAX_VALUE,(double)event.getNewDamage()*units(event.getEntity()))));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void heal(LivingHealEvent event){
        if (event.getEntity().getPersistentData().getBoolean("adaptive_passives:healing")) return;
        if(!event.getEntity().level().isClientSide&&event.getAmount()>0)
            event.setAmount(event.getAmount()*units(event.getEntity()));
    }
}
