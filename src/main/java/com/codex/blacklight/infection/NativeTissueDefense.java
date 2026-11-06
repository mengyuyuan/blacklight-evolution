package com.codex.blacklight.infection;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;

/** Shared directional living armour; finite integrity and food-paid loaded repair. */
public final class NativeTissueDefense {
    private static final class Cached {
        final CompoundTag owner;CompoundTag saved;final TissueDefense value;int tick;
        Cached(CompoundTag owner,CompoundTag saved,TissueDefense value,int tick){this.owner=owner;this.saved=saved;this.value=value;this.tick=tick;}
    }
    private static final Map<Mob,Cached> CACHE=Collections.synchronizedMap(new WeakHashMap<>());
    private NativeTissueDefense(){}
    public static void forget(Mob mob){if(mob!=null)CACHE.remove(mob);}
    private static boolean eligible(Mob mob){return NativeTissueVitals.eligible(mob);}
    public static TissueDefense.Profile profile(Mob mob){
        var s=TissueSubject.of(mob);return s==null?new TissueDefense.Profile(0,0):new TissueDefense.Profile(s.power(TissueCore.OSTEODERM),s.power(TissueCore.NACRE));
    }
    private static Cached state(Mob mob,CompoundTag data){
        var saved=data.getCompound("Defense");var cached=CACHE.get(mob);
        if(cached!=null&&cached.owner==data&&cached.saved==saved)return cached;
        cached=new Cached(data,saved,TissueDefense.read(saved),mob.tickCount);data.put("Defense",saved);CACHE.put(mob,cached);return cached;
    }
    private static void save(CompoundTag data,Cached cached){cached.saved=cached.value.write();data.put("Defense",cached.saved);}
    public static TissueDefense.Decision incoming(Mob mob,DamageSource source,float amount){
        if(!eligible(mob)||!profile(mob).active())return new TissueDefense.Decision(amount,0,0);
        var data=mob.getPersistentData().getCompound(NativeInfection.DATA);
        return TissueDefenseSystem.incoming(mob,state(mob,data).value,profile(mob),source,amount);
    }
    /** Only the completed ordinary damage path may commit wear. */
    public static void accepted(Mob mob,TissueDefense.Decision decision,float maximum){
        if(!eligible(mob)||!profile(mob).active())return;
        var data=mob.getPersistentData().getCompound(NativeInfection.DATA);var state=state(mob,data);
        state.value.accepted(decision,maximum,mob.level().getGameTime());save(data,state);NativeTissue.publish(mob,data);
    }
    static void tick(Mob mob){
        if(!eligible(mob))return;var p=profile(mob);if(!p.active())return;
        var data=mob.getPersistentData().getCompound(NativeInfection.DATA);var state=state(mob,data);
        if(state.tick==mob.tickCount)return;state.tick=mob.tickCount;
        var target=NativeInfectionCombat.target(mob);
        boolean resting=!mob.isPassenger()&&TissueActions.core(mob)==null&&!TissueCompetition.controls(mob)
                &&!com.codex.blacklight.combat.ImpactControl.stunned(mob)
                &&com.codex.blacklight.power.BiomassSystem.claimant(mob)==null
                &&!com.codex.blacklight.registry.ModAttachments.peekData(mob,com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL,com.codex.blacklight.combat.ConsumeVisual.EMPTY).active()
                &&(target==null||!target.isAlive())&&mob.getDeltaMovement().horizontalDistanceSqr()<.0025;
        int spent=state.value.advance(p,NativeTissue.nutrition(mob),resting);save(data,state);
        if(spent>0&&!NativeTissue.spend(mob,spent))throw new IllegalStateException("Native living armour overspent actual nutrition");
        if(mob.tickCount%20==0)NativeTissue.publish(mob,data);
    }
    public static CompoundTag visual(Mob mob,CompoundTag data){var p=profile(mob);return p.active()?state(mob,data).value.visual(p):new CompoundTag();}
    public static CompoundTag saved(Mob mob){
        if(!eligible(mob))return new CompoundTag();var data=mob.getPersistentData().getCompound(NativeInfection.DATA);return state(mob,data).value.write();
    }
}
