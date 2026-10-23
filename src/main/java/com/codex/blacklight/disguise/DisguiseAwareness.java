package com.codex.blacklight.disguise;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import java.util.UUID;

/** Server-only knowledge of the original body and each collected species. */
final class DisguiseAwareness {
    static final String KEY="BlacklightDisguiseAwareness";
    private static final String SHOT="BlacklightShotIdentity";
    private DisguiseAwareness() {}
    static CompoundTag data(Player player) {
        var persistent=player.getPersistentData();
        if(!persistent.contains(KEY)) {
            var value=new CompoundTag();value.putUUID("Life",UUID.randomUUID());
            int alert=player.getData(ModAttachments.PLAYER_STATE).getAlert();
            value.putInt("Base",alert);value.putInt("Identity",alert);
            value.putBoolean("LastForm",player.getData(ModAttachments.PLAYER_STATE).isDisguised());persistent.put(KEY,value);
        }
        CompoundTag data=persistent.getCompound(KEY);
        if(!data.hasUUID("Life"))data.putUUID("Life",UUID.randomUUID());
        return data;
    }
    static int exposure(Player player,boolean disguised) { return Mth.clamp(data(player).getInt(disguised?"Identity":"Base"),0,100); }
    static void expose(Player player,boolean disguised) {
        CompoundTag value=data(player);String key=disguised?"Identity":"Base";
        value.putInt(key,Math.max(60,exposure(player,disguised)));
    }
    static void newIdentity(Player player) { data(player).putInt("Identity",0);advanceEpoch(player); }
    static void rememberIdentity(Player player,String type) {
        if(type.isBlank())return;
        var value=data(player);var alerts=value.getCompound("IdentityAlerts");
        alerts.putInt(type,exposure(player,true));value.put("IdentityAlerts",alerts);
    }
    static void restoreIdentity(Player player,String type) {
        var value=data(player);value.putInt("Identity",Mth.clamp(value.getCompound("IdentityAlerts").getInt(type),0,100));
        advanceEpoch(player);
    }
    static void advanceEpoch(Player player) {
        var value=data(player);value.putLong("Epoch",value.getLong("Epoch")+1);
        value.putBoolean("LastForm",player.getData(ModAttachments.PLAYER_STATE).isDisguised());
    }
    static void decay(Player player) {
        var value=data(player);value.putInt("Base",Math.max(0,exposure(player,false)-5));
        value.putInt("Identity",Math.max(0,exposure(player,true)-5));
        var alerts=value.getCompound("IdentityAlerts");
        for(String key:alerts.getAllKeys())alerts.putInt(key,Math.max(0,alerts.getInt(key)-5));
    }
    static boolean hasExposure(Player player) {
        if(exposure(player,false)>0||exposure(player,true)>0)return true;
        var alerts=data(player).getCompound("IdentityAlerts");
        return alerts.getAllKeys().stream().anyMatch(key->alerts.getInt(key)>0);
    }
    static void shot(Projectile projectile,Player owner) {
        if(projectile.getPersistentData().contains(SHOT))return;
        var source=data(owner);var snapshot=new CompoundTag();snapshot.putUUID("Life",source.getUUID("Life"));
        snapshot.putLong("Epoch",source.getLong("Epoch"));projectile.getPersistentData().put(SHOT,snapshot);
    }
    static boolean sameShotIdentity(Projectile projectile,Player player) {
        var shot=projectile.getPersistentData().getCompound(SHOT);var current=data(player);
        return shot.hasUUID("Life")&&shot.getUUID("Life").equals(current.getUUID("Life"))
                &&shot.getLong("Epoch")==current.getLong("Epoch");
    }
}
