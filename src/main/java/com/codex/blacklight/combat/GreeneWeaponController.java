package com.codex.blacklight.combat;

import com.codex.blacklight.entity.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Ten physical spawners keep independent cooldowns across Shoot tracks/programs. */
public final class GreeneWeaponController {
    private final EncounterMob owner;
    private final GreeneWeaponCadence[] clocks=new GreeneWeaponCadence[10];
    private final java.util.Set<GreeneWeaponProgram.Shot> sounded=new java.util.HashSet<>();
    private long started=Long.MIN_VALUE;
    private int shots;
    public GreeneWeaponController(EncounterMob owner){this.owner=owner;for(int i=0;i<clocks.length;i++)clocks[i]=new GreeneWeaponCadence();}
    public int shots(){return shots;}
    public void tick(GreeneWeaponProgram program,Entity target,int age){
        boolean allowed=program!=null&&GreeneWeaponTarget.accepts(owner,program,target);
        if(allowed&&started!=owner.level().getGameTime()-age){started=owner.level().getGameTime()-age;sounded.clear();}
        float[][] pose=allowed?GreeneMotion.pose(owner,0):null;
        Vec3 releaseSound=null;
        for(int i=0;i<clocks.length;i++){
            GreeneWeaponProgram.Shot request=null;
            if(allowed)for(var shot:program.shots())if(shot.slot()==i+1&&shot.active(age)){request=shot;break;}
            final var shot=request;
            boolean fired=clocks[i].tick(.05,shot!=null,shot==null?0:shot.ammo().fireRate,()->fire(shot,pose,target));
            if(fired&&sounded.add(shot))releaseSound=GreeneMouth.position(pose,owner.position(),owner.yBodyRot,shot.slot());
        }
        // Authored MC Foley: one cue per newly firing Shoot window, coalescing
        // simultaneous ports. The missing original projectile audio is not claimed.
        if(releaseSound!=null)com.codex.blacklight.registry.ModInfectedSounds.play(owner,com.codex.blacklight.infection.InfectedSoundCue.LAUNCH,releaseSound);
    }
    private boolean fire(GreeneWeaponProgram.Shot shot,float[][] pose,Entity target){
        Vec3 from=GreeneMouth.position(pose,owner.position(),owner.yBodyRot,shot.slot());
        if(!owner.level().hasChunkAt(net.minecraft.core.BlockPos.containing(from))||!owner.level().getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(from)))return false;
        Vec3 direction;
        if(shot.fixed())direction=GreeneMouth.direction(pose,owner.yBodyRot,shot.euler());
        else {
            Vec3 aim=GreeneWeaponTarget.point(target);
            if(shot.user().deadReckoning)aim=GreeneMissileFlight.lead(from,aim,target.getDeltaMovement().scale(20),shot.ammo().initialSpeed);
            direction=aim.subtract(from).normalize();
        }
        if(direction.lengthSqr()<1e-10)return false;
        // Accuracy distribution and projectile-template FX are not reconstructed.
        // No invented Euler spread is applied to FixedDirection=false tracks.
        var projectile=new GreeneProjectile(owner.level(),owner,shot.ammo(),shot.user(),from,direction,target);
        if(!owner.level().addFreshEntity(projectile))return false;
        shots++;return true;
    }
    public CompoundTag save(){var tag=new CompoundTag();for(int i=0;i<clocks.length;i++)tag.putDouble("Socket"+(i+1),clocks[i].remainingSeconds());return tag;}
    public void load(CompoundTag tag){for(int i=0;i<clocks.length;i++){double v=tag.getDouble("Socket"+(i+1));clocks[i].restore(Double.isFinite(v)?Math.max(0,Math.min(5,v)):0);}}
}
