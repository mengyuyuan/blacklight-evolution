package com.codex.blacklight.combat;

import com.codex.blacklight.compat.NativeCombatAccess;
import com.codex.blacklight.config.CrossModCombatConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import java.util.*;

/** Observe the actual before/after pools. A foreign hurt implementation can change
 * HP and still return false. Native hit FX must follow the real loss, not that flag.
 * Shield-only progress is recorded separately and never turned into flesh damage. */
public final class NativeHitObservation {
    public enum Result{HEALTH,ABSORPTION,RESERVE,ACCEPTED,NO_PROGRESS}
    public record Trace(String entityType,float requested,boolean returned,Result result,NativeCombatAccess.Pools before,NativeCombatAccess.Pools after,String source,long gameTime){}
    private static final Map<LivingEntity,Trace> LAST=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<LivingEntity,Long> WATCH=new WeakHashMap<>();
    public static boolean watch(LivingEntity entity,int seconds){
        if(entity==null||entity.isRemoved()||!entity.isAlive()||entity.level().isClientSide||seconds<1||seconds>60)return false;
        WATCH.entrySet().removeIf(e->e.getKey().isRemoved()||!e.getKey().isAlive()||e.getValue()<=e.getKey().level().getGameTime());
        if(!WATCH.containsKey(entity)&&WATCH.size()>=32)return false;
        WATCH.put(entity,entity.level().getGameTime()+seconds*20L);LAST.remove(entity);return true;
    }
    private static boolean watching(LivingEntity entity){
        var until=WATCH.get(entity);if(until==null)return false;
        if(entity.isRemoved()||!entity.isAlive()||until<=entity.level().getGameTime()){WATCH.remove(entity);return false;}
        return true;
    }
    public static void forget(LivingEntity entity){WATCH.remove(entity);LAST.remove(entity);}
    private final boolean nativeAttack;
    private final String source;
    private final LivingEntity target;
    private final float requested;
    private final boolean diagnostic;
    private final NativeCombatAccess.Pools before;
    private NativeHitObservation(LivingEntity target,DamageSource damageSource,float amount){
        this.target=target;requested=amount;nativeAttack=CrossModCombat.nativeAttack(damageSource);source=damageSource.typeHolder().unwrapKey().map(k->k.location().toString()).orElse(damageSource.getMsgId());diagnostic=CrossModCombatConfig.NATIVE_DIAGNOSTICS.get()||watching(target);before=NativeCombatAccess.read(target,diagnostic);
    }
    public static NativeHitObservation begin(LivingEntity target,DamageSource source,float amount){
        return target!=null&&!target.level().isClientSide&&!target.isRemoved()&&target.isAlive()&&source!=null
                &&Float.isFinite(amount)&&amount>0&&(CrossModCombat.nativeAttack(source)||watching(target)||target instanceof net.minecraft.world.entity.Mob m&&com.codex.blacklight.infection.NativeInfection.active(m))?new NativeHitObservation(target,source,amount):null;
    }
    public boolean finish(boolean returned){
        var after=NativeCombatAccess.read(target,diagnostic);
        boolean hp=after.health()<before.health(),absorption=after.absorption()<before.absorption();
        if(diagnostic){
            Result result=hp?Result.HEALTH:absorption?Result.ABSORPTION:NativeCombatAccess.reserveSpent(before,after)?Result.RESERVE:returned?Result.ACCEPTED:Result.NO_PROGRESS;
            LAST.put(target,new Trace(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString(),requested,returned,result,before,after,source,target.level().getGameTime()));
        }
        if(hp&&target instanceof net.minecraft.world.entity.Mob mob&&com.codex.blacklight.infection.NativeInfection.active(mob)){
            com.codex.blacklight.infection.TissueCompetition.damaged(mob,before.health()-after.health());
            com.codex.blacklight.infection.NativeTissueVitals.damaged(mob,before.health()-after.health());
            com.codex.blacklight.infection.NativeTissueVascular.damaged(mob,before.health()-after.health());
            com.codex.blacklight.infection.NativeTissueAbilityGoal.damaged(mob,before.health()-after.health());
        }
        return nativeAttack?(returned||hp||absorption):returned;
    }
    public static Optional<Trace> last(LivingEntity target){if(target==null||target.isRemoved()){forget(target);return Optional.empty();}return Optional.ofNullable(LAST.get(target));}
}
