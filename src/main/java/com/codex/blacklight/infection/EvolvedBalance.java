package com.codex.blacklight.infection;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.power.CombatScale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.*;
import net.neoforged.neoforge.common.ModConfigSpec;

/** A distinct super-elite tier; inherited age and organ growth continue normally. */
public final class EvolvedBalance {
    public record Rules(double health,double attack,double armor,double toughness,double speed,double knockback,double hitFraction){
        public static final Rules DEFAULT=new Rules(6,5,6,4,1.25,.4,.30);
    }
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue HEALTH,ATTACK,ARMOR,TOUGHNESS,SPEED,KNOCKBACK,HIT_FRACTION;
    static{
        var b=new ModConfigSpec.Builder();
        HEALTH=b.comment("Multiplier on the fully grown/inherited host maximum health.").defineInRange("healthMultiplier",6D,1,100);
        ATTACK=b.comment("Multiplier on the evolved's attack attribute, before player armor and blood units.").defineInRange("attackMultiplier",5D,1,100);
        ARMOR=b.defineInRange("additionalArmor",6D,0,30);TOUGHNESS=b.defineInRange("additionalArmorToughness",4D,0,30);
        SPEED=b.defineInRange("movementMultiplier",1.25D,.5,3);KNOCKBACK=b.defineInRange("additionalKnockbackResistance",.4D,0,1);
        HIT_FRACTION=b.comment("Per-contact damage ceiling as a fraction of an infected player's maximum health, before defenses. Does not force damage upward.").defineInRange("maximumPlayerHealthFractionPerHit",.30D,.05,1);
        SPEC=b.build();
    }
    private EvolvedBalance(){}
    public static Rules rules(){return new Rules(HEALTH.get(),ATTACK.get(),ARMOR.get(),TOUGHNESS.get(),SPEED.get(),KNOCKBACK.get(),HIT_FRACTION.get());}
    private static ResourceLocation id(String suffix){return ResourceLocation.fromNamespaceAndPath("blacklight","evolved_"+suffix);}
    public static void apply(BlacklightInfectedHost host){
        boolean active=host.isEvolved();var r=rules();
        CombatScale.modifier(host.getAttribute(Attributes.MAX_HEALTH),id("health"),active?r.health-1:0,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        CombatScale.modifier(host.getAttribute(Attributes.ATTACK_DAMAGE),id("attack"),active?r.attack-1:0,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        CombatScale.modifier(host.getAttribute(Attributes.ARMOR),id("armor"),active?r.armor:0,AttributeModifier.Operation.ADD_VALUE);
        CombatScale.modifier(host.getAttribute(Attributes.ARMOR_TOUGHNESS),id("toughness"),active?r.toughness:0,AttributeModifier.Operation.ADD_VALUE);
        CombatScale.modifier(host.getAttribute(Attributes.MOVEMENT_SPEED),id("speed"),active?r.speed-1:0,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        CombatScale.modifier(host.getAttribute(Attributes.KNOCKBACK_RESISTANCE),id("knockback"),active?r.knockback:0,AttributeModifier.Operation.ADD_VALUE);
        if(active)host.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(Math.max(64,host.getAttributeValue(Attributes.FOLLOW_RANGE)));
    }
    public static float playerDamage(float incoming,float maximumHealth,float units,float difficulty,Rules rules){
        if(!Float.isFinite(incoming)||incoming<=0||maximumHealth<=0)return 0;
        return (float)Math.min(incoming,Math.max(10,maximumHealth*rules.hitFraction)*difficulty/Math.max(1,units));
    }
}
