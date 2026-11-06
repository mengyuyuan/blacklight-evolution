package com.codex.blacklight.infection;

import com.codex.blacklight.config.InfectionEcologyConfig;
import com.codex.blacklight.power.CombatScale;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import java.util.*;

/** Infection attached to the original entity. Never serializes, replaces or ticks a duplicate native AI.
 * Attributes use owned IDs and native effective baselines; save/reload cannot compound bonuses.
 * Advanced anatomical replacement requires an explicit species adapter, not a fabricated capability copy. */
public final class NativeInfection {
    static final String DATA="BlacklightNativeInfection";
    private static final ResourceLocation HEALTH=new ResourceLocation("blacklight","native_infection_health"),ATTACK=new ResourceLocation("blacklight","native_infection_attack"),ARMOR=new ResourceLocation("blacklight","native_infection_armor");
    private static final Map<Mob,Integer> TICKED=new WeakHashMap<>();
    private NativeInfection(){}
    public static boolean preserve(LivingEntity mob){
        if(mob==null)return false;
        String ns=BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace();
        return mob instanceof Mob&&!ns.equals("blacklight")&&(InfectionEcologyConfig.value(InfectionEcologyConfig.PRESERVE_EXTERNAL)&&!ns.equals("minecraft")
                ||InfectionEcologyConfig.matches(mob,InfectionEcologyConfig.value(InfectionEcologyConfig.PRESERVE)));
    }
    public static boolean active(LivingEntity mob){
        // Non-player effect state and server-only preserve policy are not a
        // reliable render handshake. Only the server publishes this schema;
        // cure clears the entire projection, and no client decision grants AI,
        // damage, nutrients, organs or infection on the authoritative server.
        if(mob instanceof Mob nativeMob&&mob.level().isClientSide)
            return !mob.isRemoved()&&NativeTissue.snapshot(nativeMob).getInt("Schema")==1;
        return preserve(mob)&&BlacklightInfectionSystem.isInfected(mob);
    }
    public static long age(Mob mob){return Math.max(0,ModAttachments.peekData(mob,ModAttachments.NATIVE_INFECTION_AGE,0L));}
    public static long seed(Mob mob){var id=mob.getUUID();return id.getMostSignificantBits()^id.getLeastSignificantBits();}
    public static TissueCore core(Mob mob){var known=NativeTissue.knownCore(mob);return known!=null?known:TissueCore.forNewHost(seed(mob),0,mob instanceof net.minecraft.world.entity.animal.WaterAnimal);}
    private static double baseline(AttributeInstance attribute,ResourceLocation id){
        return com.codex.blacklight.compat.NativeAttributeBaseline.without(attribute,id);
    }
    public static void clear(Mob mob){
        if(mob==null)return;
        if(!mob.level().isClientSide)TissueCompetition.forget(mob);
        NativeTissueAbilityGoal.clear(mob);NativeTissueCompetitionGoal.clear(mob);NativeInfectionCombat.clear(mob);NativeTissue.forget(mob);TICKED.remove(mob);
        if(!ModAttachments.available(mob))return;
        if(!mob.getPersistentData().contains(DATA))return;
        float maximum=mob.getMaxHealth(),health=mob.getHealth();
        CombatScale.modifier(mob.getAttribute(Attributes.MAX_HEALTH),HEALTH,0,AttributeModifier.Operation.MULTIPLY_TOTAL);
        CombatScale.modifier(mob.getAttribute(Attributes.ATTACK_DAMAGE),ATTACK,0,AttributeModifier.Operation.MULTIPLY_TOTAL);
        CombatScale.modifier(mob.getAttribute(Attributes.ARMOR),ARMOR,0,AttributeModifier.Operation.ADDITION);
        ModAttachments.setData(mob,ModAttachments.NATIVE_TISSUE,new CompoundTag());ModAttachments.syncData(mob,ModAttachments.NATIVE_TISSUE);
        mob.getPersistentData().remove(DATA);ModAttachments.setData(mob,ModAttachments.NATIVE_INFECTION_AGE,0L);ModAttachments.syncData(mob,ModAttachments.NATIVE_INFECTION_AGE);TICKED.remove(mob);CombatScale.preserveHealthFraction(mob,maximum,health);
    }
    public static void tick(Mob mob,ServerLevel level){
        if(mob==null||level==null||mob.level()!=level||!mob.isAlive()||!ModAttachments.available(mob)||!active(mob))return;
        NativeTissueCompetitionGoal.update(mob);
        TissueCompetition.tick(mob);
        NativeTissueAbilityGoal.maintain(mob);
        NativeTissueDefense.tick(mob);
        NativeTissueFeeding.tick(mob);
        NativeTissueVascular.tick(mob);
        Integer previous=TICKED.put(mob,mob.tickCount);
        // Count actual loaded entity ticks, never offline time or duplicate callbacks.
        int elapsed=previous==null?0:Math.max(0,Math.min(20,mob.tickCount-previous));
        var data=mob.getPersistentData().getCompound(DATA);
        data.putInt("PendingTicks",Math.min(40,Math.max(0,data.getInt("PendingTicks"))+elapsed));
        mob.getPersistentData().put(DATA,data);
        if(previous!=null&&data.getInt("PendingTicks")<20)return;
        // A partial saved batch is not a new feeding opportunity. Initialize
        // attributes on load, but pay/integrate only complete loaded-tick batches.
        int work=data.getInt("PendingTicks")>=20?20:0;
        data.putInt("PendingTicks",data.getInt("PendingTicks")-work);
        long age=NativeTissue.advance(mob,level,data,work);
        mob.getPersistentData().put(DATA,data);
        ModAttachments.setData(mob,ModAttachments.NATIVE_INFECTION_AGE,age);ModAttachments.syncData(mob,ModAttachments.NATIVE_INFECTION_AGE);
        NativeTissue.publish(mob,data);
        NativeInfectionCombat.update(mob);
        float maximum=mob.getMaxHealth(),health=mob.getHealth();
        double baseHealth=baseline(mob.getAttribute(Attributes.MAX_HEALTH),HEALTH),baseAttack=baseline(mob.getAttribute(Attributes.ATTACK_DAMAGE),ATTACK);
        var growth=NativeTissue.combat(mob);var rules=InfectionStatScaling.rules();
        double hp=baseHealth>0?InfectionStatScaling.health(baseHealth,rules)/baseHealth-1+growth.health():0;
        double atk=baseAttack>0?InfectionStatScaling.attack(baseHealth,baseAttack,rules)/baseAttack-1+growth.damage():0;
        CombatScale.modifier(mob.getAttribute(Attributes.MAX_HEALTH),HEALTH,hp,AttributeModifier.Operation.MULTIPLY_TOTAL);
        CombatScale.modifier(mob.getAttribute(Attributes.ATTACK_DAMAGE),ATTACK,atk,AttributeModifier.Operation.MULTIPLY_TOTAL);
        CombatScale.modifier(mob.getAttribute(Attributes.ARMOR),ARMOR,growth.armor(),AttributeModifier.Operation.ADDITION);
        CombatScale.preserveHealthFraction(mob,maximum,health);
        // Native attacks and contact-timed organ goals own damage. Metabolism owns paid repair.
    }
}
