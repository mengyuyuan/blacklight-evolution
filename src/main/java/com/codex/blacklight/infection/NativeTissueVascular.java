package com.codex.blacklight.infection;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;

/** Contact-only cooperation. Never stops or replaces source navigation or motion. */
public final class NativeTissueVascular {
    private static final class Cached {
        final CompoundTag owner; CompoundTag saved; final TissueVascular duct;
        final String dimension; int tick=Integer.MIN_VALUE; float health;
        Cached(Mob mob,CompoundTag owner,CompoundTag saved){this.owner=owner;this.saved=saved;duct=TissueVascular.read(saved);dimension=mob.level().dimension().location().toString();health=mob.getHealth();}
    }
    private static final Map<Mob,Cached> CACHE=Collections.synchronizedMap(new WeakHashMap<>());
    private NativeTissueVascular(){}
    private static boolean eligible(Mob mob){return mob!=null&&!mob.level().isClientSide
            &&!(mob instanceof com.codex.blacklight.entity.BlacklightInfectedHost)&&TissueActions.eligible(mob);}
    static boolean grief(Mob mob){return net.neoforged.neoforge.event.EventHooks.canEntityGrief(mob.level(),mob);}
    public static void forget(Mob mob){if(mob!=null)CACHE.remove(mob);}
    private static Cached state(Mob mob){
        var data=mob.getPersistentData().getCompound("BlacklightNativeInfection");var saved=data.getCompound("Vascular");var cached=CACHE.get(mob);
        if(cached!=null&&cached.owner==data&&cached.saved==saved&&cached.dimension.equals(mob.level().dimension().location().toString()))return cached;
        var next=new Cached(mob,data,saved);data.put("Vascular",saved);CACHE.put(mob,next);NativeTissue.vascular(mob,new CompoundTag());return next;
    }
    private static void save(Cached c){var next=c.duct.write();if(!next.equals(c.saved)){c.owner.put("Vascular",next);c.saved=next;}}
    public static boolean startAt(Mob mob,BlockPos pos){
        if(!eligible(mob)||pos==null)return false;var c=state(mob);boolean started=c.duct.startAt(mob,pos);save(c);return started;
    }
    public static void tick(Mob mob){
        if(!eligible(mob)){forget(mob);return;}
        if(!CACHE.containsKey(mob)&&TissueActions.power(mob,TissueCore.VASCULAR)<=0)return;
        var c=state(mob);if(c.tick==mob.tickCount)return;c.tick=mob.tickCount;
        if(mob.getHealth()+.001F<c.health)c.duct.interrupt(mob);c.health=mob.getHealth();
        c.duct.tick(mob);save(c);
    }
    public static void damaged(Mob mob,float actual){
        if(!eligible(mob)||!Float.isFinite(actual)||actual<=0)return;var c=CACHE.get(mob);if(c==null)return;
        c.duct.interrupt(mob);c.health=mob.getHealth();save(c);
    }
    public static boolean active(Mob mob){var c=CACHE.get(mob);return c!=null&&c.duct.active();}
    public static int delivered(Mob mob){var c=CACHE.get(mob);return c==null?0:c.duct.delivered();}
    public static CompoundTag saved(Mob mob){return eligible(mob)?mob.getPersistentData().getCompound("BlacklightNativeInfection").getCompound("Vascular").copy():new CompoundTag();}
}
