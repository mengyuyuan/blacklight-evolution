package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.infection.InfectedSoundCue;
import com.codex.blacklight.network.InfectedImpactPayload;
import com.codex.blacklight.registry.ModInfectedSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Bounded presentation at confirmed contacts and phase edges; never adds damage. */
public final class GreeneFeedback {
    private int previousStage=-1;
    private float previousAge;
    private long lastTick=Long.MIN_VALUE;

    public void resetObservation(){previousStage=-1;previousAge=0;}

    public static void broken(EncounterMob owner,Vec3 at){
        var tissue=new InfectedImpactPayload(owner.getId(),owner.getId(),InfectedAttackStyle.BURST.ordinal(),
                at.x,at.y,at.z,0,1,0,2.4F,owner.level().getGameTime()*37+owner.getId());
        ContactEffects.body(owner,tissue,true,ModInfectedSounds.prepare(owner,InfectedSoundCue.ERUPT,at));
    }

    public void tick(EncounterMob owner){
        if(!(owner.level() instanceof ServerLevel)||!owner.isAlive())return;
        long now=owner.level().getGameTime();if(now==lastTick)return;lastTick=now;
        int stage=owner.greeneStage();float age=owner.greeneBattle().age(0);
        // First observation of a loaded boss is not a replay of an old explosion.
        if(previousStage>=0){
            if(stage==GreeneBattleCycle.DOWN&&previousStage==GreeneBattleCycle.COLLAPSING)ground(owner,5.5F);
            if(stage==GreeneBattleCycle.EMERGING&&age>=GreeneBattleCycle.EMERGE_TARGET_TICKS
                    &&(previousStage!=stage||previousAge<GreeneBattleCycle.EMERGE_TARGET_TICKS))ground(owner,7F);
        }
        previousStage=stage;previousAge=age;
    }

    private static void ground(EncounterMob owner,float radius){
        var hit=owner.level().clip(new ClipContext(owner.position().add(0,2,0),owner.position().add(0,-3,0),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
        if(hit.getType()==HitResult.Type.BLOCK)TerrainImpactEffects.emit(owner,hit,radius,true);
    }
}
