package com.codex.blacklight.infection;

import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;

/** The same bounded digestion used by converted hosts, on the real native mob.
 * Cached values never point back to their weak entity key or to foreign NBT. */
public final class NativeTissueDigestion {
    private record Cached(CompoundTag owner,CompoundTag saved,TissueDigestion value){}
    private static final Map<Mob,Cached> CACHE=Collections.synchronizedMap(new WeakHashMap<>());
    private NativeTissueDigestion(){}
    public static void forget(Mob mob){if(mob!=null)CACHE.remove(mob);}
    private static boolean eligible(Mob mob){return mob!=null&&!mob.level().isClientSide&&TissueActions.eligible(mob)
            &&!(mob instanceof com.codex.blacklight.entity.BlacklightInfectedHost);}
    public static TissueDigestion.Profile profile(Mob mob){
        var s=TissueSubject.of(mob);if(s==null)return new TissueDigestion.Profile(0,0,0,0,0,0,0);
        return new TissueDigestion.Profile(s.power(TissueCore.GIZZARD),s.power(TissueCore.FERMENT),s.power(TissueCore.ABSORPTIVE),
                s.power(TissueCore.PANCREAS),s.power(TissueCore.CROP),s.power(TissueCore.FAT),s.power(TissueCore.GILL_RAKER));
    }
    private static TissueDigestion state(Mob mob,CompoundTag data){
        var saved=data.getCompound("Digestion");var cached=CACHE.get(mob);
        if(cached!=null&&cached.owner()==data&&cached.saved()==saved)return cached.value();
        var digestion=TissueDigestion.read(saved);
        data.put("Digestion",saved);CACHE.put(mob,new Cached(data,saved,digestion));return digestion;
    }
    private static void save(Mob mob,CompoundTag data,TissueDigestion digestion){
        var saved=digestion.write();data.put("Digestion",saved);CACHE.put(mob,new Cached(data,saved,digestion));
    }
    static int deposit(Mob mob,CompoundTag data,int amount){
        var p=profile(mob);if(!p.digestive()&&!data.contains("Digestion",10))return TissueMetabolism.add(data.getInt("Nutrition"),amount);
        var digestion=state(mob,data);int reserve=digestion.deposit(data.getInt("Nutrition"),amount,p);save(mob,data,digestion);return reserve;
    }
    static void advance(Mob mob,CompoundTag data,int elapsed){
        if(!eligible(mob)||elapsed<=0)return;
        var p=profile(mob);if(!p.digestive()&&!data.contains("Digestion",10))return;
        var digestion=state(mob,data);var step=digestion.advance(elapsed,data.getInt("Nutrition"),p);
        data.putInt("Nutrition",step.reserve());save(mob,data,digestion);
    }
    public static boolean hasRoom(Mob mob){
        if(!eligible(mob))return false;
        return state(mob,mob.getPersistentData().getCompound(NativeInfection.DATA)).queued()<profile(mob).capacity();
    }
    public static boolean offer(Mob mob,TissueDigestion.Kind kind,int nutrition){
        if(!eligible(mob)||nutrition<=0||kind==null)return false;
        var data=mob.getPersistentData().getCompound(NativeInfection.DATA);
        var digestion=state(mob,data);if(!digestion.offer(kind,nutrition,profile(mob)))return false;
        save(mob,data,digestion);mob.getPersistentData().put(NativeInfection.DATA,data);NativeTissue.publish(mob,data);return true;
    }
    /** Called only by the accepted-death receipt, never from a tentative hurt. */
    public static void meal(Mob mob,int nutrition){
        if(!eligible(mob)||nutrition<=0)return;
        var subject=TissueSubject.of(mob);
        if(profile(mob).digestive()||subject!=null&&subject.power(TissueCore.EXTRAORAL)>0)offer(mob,TissueDigestion.Kind.MEAT,nutrition);
        else NativeTissue.feed(mob,nutrition);
    }
    public static CompoundTag visual(Mob mob,CompoundTag data){
        var p=profile(mob);return p.digestive()||data.contains("Digestion",10)?state(mob,data).visual(p):new CompoundTag();
    }
    public static CompoundTag saved(Mob mob){
        if(!eligible(mob))return new CompoundTag();
        return mob.getPersistentData().getCompound(NativeInfection.DATA).getCompound("Digestion").copy();
    }
}
