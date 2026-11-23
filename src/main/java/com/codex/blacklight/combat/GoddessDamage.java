package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import java.util.WeakHashMap;

public final class GoddessDamage {
    private static final WeakHashMap<Player,GoddessDamageBudget> windows=new WeakHashMap<>();
    private GoddessDamage(){}
    private static EncounterMob owner(ServerPlayer p,Entity entity){
        if(entity instanceof EncounterMob m && m.goddess.active())return m;
        if(entity==null || !entity.getPersistentData().hasUUID("EncounterOwner"))return null;
        var parent=p.serverLevel().getEntity(entity.getPersistentData().getUUID("EncounterOwner"));
        return parent instanceof EncounterMob m && m.goddess.active()?m:null;
    }
    static boolean fromEncounter(ServerPlayer p,DamageSource s){return owner(p,s.getEntity())!=null || owner(p,s.getDirectEntity())!=null;}
    public static float health(Player player,DamageSource source,float proposed){
        if(!(player instanceof ServerPlayer p) || proposed>=p.getHealth())return proposed;
        var boss=owner(p,source.getEntity());
        if(boss==null)boss=owner(p,source.getDirectEntity());
        if(boss==null)return proposed;
        var attacks=boss.goddess.attacks;
        if(attacks.safe(p))return p.getHealth();
        var direct = source.getDirectEntity();
        int cast = direct != null && direct.getPersistentData().contains("GoddessSequence")
                ? direct.getPersistentData().getInt("GoddessSequence") : attacks.sequence();
        int otherCast = cast == attacks.sequence() ? attacks.previousVolley() : attacks.sequence();
        float lost=windows.computeIfAbsent(p,key->new GoddessDamageBudget()).debit(boss.getUUID(),cast,otherCast,
                p.level().getGameTime(),p.getMaxHealth(),p.getHealth()-proposed);
        return p.getHealth()-lost;
    }
}
