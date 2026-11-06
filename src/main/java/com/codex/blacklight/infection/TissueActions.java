package com.codex.blacklight.infection;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;

/** Value-only native action presentation; the original mob remains the actual actor.
 * Target IDs and phase clocks are transient and never copied to foreign NBT/capabilities. */
public final class TissueActions {
    private TissueActions(){}
    public static boolean eligible(Mob mob){
        if(!ModAttachments.available(mob)||!mob.isAlive())return false;
        return mob instanceof BlacklightInfectedHost host?!host.hasMultipartBody()&&!host.devourerIdentity()
                :!mob.isMultipartEntity()&&NativeInfection.active(mob);
    }
    private static CompoundTag nativeAction(Mob mob){return NativeTissue.snapshot(mob).getCompound("OrganAction");}
    public static TissueCore core(Mob mob){
        if(!ModAttachments.available(mob))return null;
        return mob instanceof BlacklightInfectedHost h?h.organActionCore():TissueCore.byId(nativeAction(mob).getString("Core"));
    }
    public static int target(Mob mob){return mob instanceof BlacklightInfectedHost h?h.organActionTarget():nativeAction(mob).getInt("Target");}
    public static float elapsed(Mob mob,float partial){
        if(core(mob)==null)return -1;
        return mob instanceof BlacklightInfectedHost h?h.organActionElapsed(partial):mob.level().getGameTime()-nativeAction(mob).getLong("Started")+partial;
    }
    public static Vec3 direction(Mob mob){
        if(mob instanceof BlacklightInfectedHost h)return h.organActionDirection();
        var tag=nativeAction(mob);return new Vec3(tag.getDouble("X"),0,tag.getDouble("Z"));
    }
    public static void set(Mob mob,TissueCore core,int target,Vec3 direction){
        if(!ModAttachments.available(mob)||mob.level().isClientSide)return;
        if(mob instanceof BlacklightInfectedHost h){h.setOrganAction(core,target,direction);return;}
        var tag=new CompoundTag();
        if(core!=null&&eligible(mob)){
            tag.putString("Core",core.key());tag.putInt("Target",target);tag.putLong("Started",mob.level().getGameTime());
            tag.putDouble("X",direction.x);tag.putDouble("Z",direction.z);
        }
        NativeTissue.action(mob,tag);
    }
    public static void retract(Mob mob){
        if(mob instanceof BlacklightInfectedHost h){h.retractOrganAction();return;}
        var core=core(mob);if(core==null||mob.level().isClientSide)return;
        int closing=TissueOrganCycle.cycle(core).retract();if(elapsed(mob,0)>=closing)return;
        var tag=nativeAction(mob).copy();tag.putLong("Started",mob.level().getGameTime()-closing);NativeTissue.action(mob,tag);
    }
    public static String family(Mob mob){
        if(mob instanceof BlacklightInfectedHost h)return h.family();
        return mob instanceof net.minecraft.world.entity.animal.WaterAnimal?"water"
                :mob instanceof FlyingMob||mob instanceof net.minecraft.world.entity.animal.FlyingAnimal?"air":"ground";
    }
    public static double power(Mob mob,TissueCore core){var subject=TissueSubject.of(mob);return subject==null?0:subject.power(core);}
    public static boolean spend(Mob mob,int amount){var subject=TissueSubject.of(mob);return subject!=null&&subject.spend(amount);}
    public static boolean legacyStrike(Mob mob){return mob instanceof BlacklightInfectedHost h&&h.tissueStrikeElapsed(0)>=0;}
    public static int recover(Mob mob,int ticks){return mob instanceof BlacklightInfectedHost h?h.recoverTissueAction(ticks):NativeTissueVitals.recover(mob,ticks);}
    public static boolean gripping(TissueCore core){return core==TissueCore.CHELA||core==TissueCore.HYDROSTAT||core==TissueCore.TONGUE||core==TissueCore.EXTRAORAL;}
    public static boolean gripActive(Mob mob){
        var core=core(mob);if(!gripping(core))return false;
        var cycle=TissueOrganCycle.cycle(core);float elapsed=elapsed(mob,0);return elapsed>=cycle.contact()&&elapsed<cycle.retract();
    }
    public static boolean legacyGrip(Mob mob){
        return mob instanceof BlacklightInfectedHost h&&h.tissueStrikeElapsed(0)>=TissueOrgans.HIT&&h.tissueStrikeElapsed(0)<TissueOrgans.DURATION;
    }
    public static double gripReach(Mob mob){
        double legacy=mob instanceof BlacklightInfectedHost h?h.tissueOrganReach():0;var core=core(mob);
        return Math.max(legacy,core==null?0:TissueOrganCycle.reach(core));
    }
}
