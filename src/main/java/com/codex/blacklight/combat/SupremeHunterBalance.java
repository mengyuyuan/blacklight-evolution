package com.codex.blacklight.combat;

/** Fixed boss encounter values in pre-CombatScale units. */
public final class SupremeHunterBalance {
    public static final double HEALTH=20000,DAMAGE=16,ARMOR=10,TOUGHNESS=4,SPEED=.34,FOLLOW=64,HUNT_FOLLOW=512;
    /** Original: thrown cars, debris and bodies are the main way to stagger and wound the Supreme Hunter. */
    public static final float THROWN_BONUS=8F;
    public static final float PLAYER_ATTACK_FACTOR=.8F,ORDINARY_PLAYER_HURT_FACTOR=.6F,AIR_DEFLECT_FACTOR=.25F;
    public static final float PHASE_TWO=.66F,PHASE_THREE=.33F,WEAKEN=.12F;
    public static final float RUN_BUDGET=20,GRAB_LANDING=25;
    /** Base .34 (x CombatScale 1.08): walk ~ villager sprint, run ~ a player with Speed II sprinting. */
    public static final double WALK_MODIFIER=.95,RUN_MODIFIER=1.6;
    public static final int WEAK_TICKS=200,RECOVER_TICKS=160,POISE=10,POISE_DECAY_TICKS=40;
    private static final ThreadLocal<SupremeHunterMove> CURRENT=new ThreadLocal<>();
    private static final ThreadLocal<Float> BUDGET=new ThreadLocal<>();
    private SupremeHunterBalance(){}
    public static float rawMultiplier(SupremeHunterMove move){
        return switch(move){case CLAW_SLASH->.7F;case BACKHAND->1F;case OVERHAND->1.3F;case STOMP->.9F;case GROUNDSPIKE->1.4F;
            case DEVASTATOR->1F;case PICKUP_THROW->.9F;case SHOULDER_CHARGE->1.1F;case LEAP->1.2F;case GRAB_THROW->.8F;default->.5F;};
    }
    public static SupremeHunterMove current(){return CURRENT.get();}
    /** Overrides the move's budget for one hit (combo stage, landing, trample). */
    public static boolean hit(net.minecraft.world.entity.Mob boss,net.minecraft.world.entity.LivingEntity victim,SupremeHunterMove move,float budget,net.minecraft.world.damagesource.DamageSource source){
        var previous=BUDGET.get();BUDGET.set(budget);
        try{return hit(boss,victim,move,source);}finally{if(previous==null)BUDGET.remove();else BUDGET.set(previous);}
    }
    /** Every boss hit runs inside this scope so the shared damage budget knows the move. */
    public static boolean hit(net.minecraft.world.entity.Mob boss,net.minecraft.world.entity.LivingEntity victim,SupremeHunterMove move){
        return hit(boss,victim,move,boss.damageSources().mobAttack(boss));
    }
    public static boolean hit(net.minecraft.world.entity.Mob boss,net.minecraft.world.entity.LivingEntity victim,SupremeHunterMove move,net.minecraft.world.damagesource.DamageSource source){
        var previous=CURRENT.get();CURRENT.set(move);
        try{
            float raw=(float)(boss.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)*rawMultiplier(move));
            return victim.hurt(source,raw);
        }finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
    public static float infectedDamage(SupremeHunterMove move,float difficulty){
        var override=BUDGET.get();
        return Math.max(.2F,override!=null?override:move.budget)*difficulty/com.codex.blacklight.power.CombatScale.UNITS;
    }
}
