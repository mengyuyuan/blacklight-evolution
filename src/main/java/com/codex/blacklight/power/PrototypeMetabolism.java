package com.codex.blacklight.power;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;

/** Prototype mode suspends hunger unless the world enables the survival hunger rule.
 * With the rule on, food drains normally and consuming creatures refills it. */
public final class PrototypeMetabolism {
    /** /gamerule blacklightPrototypeHunger true|false. Off keeps the old behavior. */
    public static final GameRules.Key<GameRules.BooleanValue> HUNGER=GameRules.register("blacklightPrototypeHunger",GameRules.Category.PLAYER,GameRules.BooleanValue.create(false));
    private PrototypeMetabolism() { }
    public static void initialize(){}

    public static boolean active(Player player) {
        if (player == null || !player.isAlive() || player.isSpectator() || !ModAttachments.available(player)) return false;
        var state = ModAttachments.getData(player, ModAttachments.PLAYER_STATE);
        return state.isInfected() && state.isPrototypeMode();
    }
    /** Client copies the rule from a synced attachment; the server reads the world rule. */
    public static boolean hungerEnabled(Player player){
        if(player==null)return false;
        if(player.level().isClientSide)return ModAttachments.peekData(player,ModAttachments.PROTOTYPE_HUNGER,false);
        return player.level().getGameRules().getBoolean(HUNGER);
    }
    /** Hunger, its HUD and the sprint food gate are bypassed only while this is true. */
    public static boolean hungerSuspended(Player player){return active(player)&&!hungerEnabled(player);}
    /** Called once per server second; only sends when the rule actually changed. */
    public static void sync(ServerPlayer player){
        boolean rule=player.level().getGameRules().getBoolean(HUNGER);
        if(ModAttachments.peekData(player,ModAttachments.PROTOTYPE_HUNGER,false)!=rule){
            ModAttachments.setData(player,ModAttachments.PROTOTYPE_HUNGER,rule);ModAttachments.syncData(player,ModAttachments.PROTOTYPE_HUNGER);
        }
    }
    /** Consumed tissue feeds the host when hunger is enabled. Larger bodies give more food. */
    public static void feed(ServerPlayer player,Mob target){
        if(!hungerEnabled(player)||!ModAttachments.getData(player,ModAttachments.PLAYER_STATE).isInfected())return;
        float health=CombatScale.originalMaximum(target);
        if(!Float.isFinite(health)||health<0)health=0;
        int food=(int)net.minecraft.util.Mth.clamp(Math.ceil(health/5),2,20);
        if(target instanceof com.codex.blacklight.entity.BlacklightSupremeHunter)food=20;
        player.getFoodData().eat(food,.6F);
    }
}
