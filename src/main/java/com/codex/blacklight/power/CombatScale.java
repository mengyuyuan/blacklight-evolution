package com.codex.blacklight.power;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;

/** Health units are converted once, after armor/resistance and before absorption.
 * Authored attack windows, AI damage attributes and ordinary survival retain their units.
 */
@EventBusSubscriber(modid="blacklight")
public final class CombatScale {
    public static final float UNITS=5;
    private static final ResourceLocation HEALTH=new ResourceLocation("blacklight","health_units");
    private static final ResourceLocation SPEED=new ResourceLocation("blacklight","combat_speed");
    private static final ResourceLocation ATTACK_SPEED=new ResourceLocation("blacklight","combat_attack_speed");
    private static final java.util.Map<Mob,Boolean> INFECTION_STATE=new java.util.WeakHashMap<>();
    private CombatScale(){}
    public static float units(LivingEntity entity){
        var attribute=entity.getAttribute(Attributes.MAX_HEALTH);
        return attribute!=null&&attribute.getModifier(modifierId(HEALTH))!=null?UNITS:1;
    }
    public static float originalMaximum(LivingEntity entity){return entity.getMaxHealth()/units(entity);}
    public static boolean modifier(AttributeInstance attribute,ResourceLocation id,double amount,AttributeModifier.Operation operation){
        if(attribute==null)return false;
        var old=attribute.getModifier(modifierId(id));
        if(old!=null&&old.getAmount()==amount&&old.getOperation()==operation)return false;
        if(old!=null)attribute.removeModifier(modifierId(id));
        if(amount!=0)attribute.addPermanentModifier(new AttributeModifier(modifierId(id),id.toString(),amount,operation));
        return old!=null||amount!=0;
    }
    public static void attributes(LivingEntity entity,boolean scaled){
        float oldUnits=units(entity),absorption=entity.getAbsorptionAmount();
        modifier(entity.getAttribute(Attributes.MAX_HEALTH),HEALTH,scaled?UNITS-1:0,AttributeModifier.Operation.MULTIPLY_TOTAL);
        if(oldUnits!=units(entity))entity.setAbsorptionAmount(absorption*units(entity)/oldUnits);
        boolean enhanced=scaled && (entity instanceof Mob || entity instanceof Player p && PrototypeMetabolism.active(p));
        modifier(entity.getAttribute(Attributes.MOVEMENT_SPEED),SPEED,enhanced?.08:0,AttributeModifier.Operation.MULTIPLY_TOTAL);
        modifier(entity.getAttribute(Attributes.ATTACK_SPEED),ATTACK_SPEED,enhanced?.15:0,AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
    public static void preserveHealthFraction(LivingEntity entity,float previousMaximum,float previousHealth){
        if(previousMaximum>0&&previousMaximum!=entity.getMaxHealth())
            entity.setHealth(com.codex.blacklight.util.MathCompat.clamp(previousHealth/previousMaximum*entity.getMaxHealth(),0,entity.getMaxHealth()));
    }
    /** Also migrates saved ordinary mobs that carry the old global modifiers. */
    public static void synchronizeMob(Mob mob){
        if(mob.level().isClientSide||!com.codex.blacklight.registry.ModAttachments.available(mob))return;
        boolean infected=com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(mob);
        float before=mob.getMaxHealth(),health=mob.getHealth();
        attributes(mob,infected);preserveHealthFraction(mob,before,health);
        INFECTION_STATE.put(mob,infected);
    }
    @SubscribeEvent public static void tick(com.codex.blacklight.combat.ForgeEntityTickEvent.Post event){
        if(!(event.getEntity() instanceof Mob mob)||mob.level().isClientSide||!com.codex.blacklight.registry.ModAttachments.available(mob))return;
        boolean infected=com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(mob);
        Boolean previous=INFECTION_STATE.get(mob);
        if(previous==null||previous!=infected)synchronizeMob(mob);
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide()||!(event.getEntity() instanceof Mob mob))return;
        synchronizeMob(mob);
    }
    public static java.util.UUID modifierId(ResourceLocation id){
        return java.util.UUID.nameUUIDFromBytes(id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public static float damageUnits(LivingEntity entity,float amount){
        return !entity.level().isClientSide&&amount>0
                ?(float)Math.min(Float.MAX_VALUE,(double)amount*units(entity)):amount;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void heal(LivingHealEvent event){
        if(!event.getEntity().level().isClientSide&&event.getAmount()>0)
            event.setAmount(event.getAmount()*units(event.getEntity()));
    }
}
