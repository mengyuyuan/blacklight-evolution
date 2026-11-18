package com.codex.blacklight.military.aircraft;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.vehicle.VehicleKinematics;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;

/** Native six-person, two-door timed deployment. Local population/loaded terrain are explicit MC budget adapters. */
public final class HelicopterDeployment {
    public static final int[] RELEASE_TICKS={16,19,64,67,128,131};
    public static final int COOLDOWN_TICKS=400,LOCAL_CAPACITY=18;
    public static final double HOVER_HEIGHT=12;
    private final BlackwatchHelicopter craft;
    private boolean enabled,wave;
    private int slot,age,cooldown;
    private long epoch;
    public HelicopterDeployment(BlackwatchHelicopter craft){this.craft=craft;}
    public boolean enabled(){return enabled;}
    public void enable(){enabled=true;wave=false;slot=age=cooldown=0;epoch++;}
    public void disable(){enabled=wave=false;epoch++;releaseAll();sync();}
    public Vec3 hover(Vec3 ground){return ground.add(0,HOVER_HEIGHT,0);}
    public List<MilitarySoldier> descending(){return craft.level().getEntitiesOfClass(MilitarySoldier.class,craft.getBoundingBox().inflate(40),s->HelicopterRappel.belongs(s,craft));}
    public void releaseAll(){for(var troop:descending())HelicopterRappel.release(troop,craft,false);}
    private int population(){return craft.level().getEntitiesOfClass(MilitarySoldier.class,craft.getBoundingBox().inflate(64),LivingEntity::isAlive).size();}
    public boolean roomForWave(){return population()+6<=LOCAL_CAPACITY;}
    public Vec3 floor(Vec3 at,int side){
        Vec3 origin=at.add(new Vec3(side*3.6,1,-.5).yRot((float)Math.toRadians(craft.getYRot())));
        Vec3 end=origin.add(0,-HOVER_HEIGHT-5,0);var column=new AABB(origin,end).inflate(.4,0,.4);
        if(!VehicleKinematics.loaded(craft.level(),column)||!craft.level().getWorldBorder().isWithinBounds(column))return null;
        var hit=craft.level().clip(new ClipContext(origin,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,craft));
        if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=net.minecraft.core.Direction.UP
                ||!craft.level().getFluidState(hit.getBlockPos()).isEmpty())return null;
        Vec3 ground=new Vec3(origin.x,hit.getLocation().y+.002,origin.z);
        if(at.y-ground.y<6||at.y-ground.y>17)return null;
        var passage=new AABB(ground.x-.35,ground.y,ground.z-.35,ground.x+.35,origin.y+1.9,ground.z+.35);
        // Block collision only: another soldier down the same rope does not invalidate the destination.
        if(craft.level().getBlockCollisions(craft,passage).iterator().hasNext())return null;
        return ground;
    }
    public boolean safeSite(Vec3 ground){
        if(!HelicopterFlight.finite(ground))return false;Vec3 at=hover(ground);
        return craft.clearAt(at,craft.getYRot())&&floor(at,-1)!=null&&floor(at,1)!=null;
    }
    public Vec3 findSite(Vec3 reference){
        for(int radius:new int[]{12,20,28})for(int direction=0;direction<8;direction++){
            double angle=direction*Math.PI/4;int x=(int)Math.floor(reference.x+Math.cos(angle)*radius),z=(int)Math.floor(reference.z+Math.sin(angle)*radius);
            if(!craft.level().hasChunkAt(new BlockPos(x,(int)reference.y,z)))continue;
            int y=craft.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);Vec3 candidate=new Vec3(x+.5,y,z+.5);
            if(Math.abs(y-reference.y)<=8&&safeSite(candidate))return candidate;
        }
        return null;
    }
    public void tick(){
        if(craft.level().isClientSide)return;
        if(enabled&&(craft.pilot().owned()||craft.health()<=0)){disable();return;}
        if(cooldown>0)cooldown--;
        if(craft.phase()!=BlackwatchHelicopter.Phase.UNLOAD){
            if(!descending().isEmpty())releaseAll();
            if(wave){wave=false;cooldown=COOLDOWN_TICKS;epoch++;}
        }
        sync();
    }
    /** Called before combat movement, so a re-tasked aircraft still moves at most once this tick. */
    public boolean repeat(){
        if(!enabled||wave||cooldown>0||!craft.combat().start()||!roomForWave()||!(craft.level() instanceof ServerLevel level))return false;
        var id=craft.combat().targetId();
        if(id==null||!(level.getEntity(id) instanceof LivingEntity target)||!craft.combat().hostile(target)
                ||target.distanceToSqr(craft)>96*96||!craft.combat().operator().hasLineOfSight(target))return false;
        Vec3 site=findSite(target.position());cooldown=40; // Failed MC site search is bounded, not retried every tick.
        if(site==null)return false;
        return craft.redirectRappel(site);
    }
    /** A complete wave waits for all detached troops to touch down or abort. No landing or simultaneous firing. */
    public boolean unload(){
        if(!enabled)return false;
        if(!wave){if(!roomForWave())return true;wave=true;slot=age=0;epoch++;}
        age++;
        if(slot<6&&age>=RELEASE_TICKS[slot]){
            int next=slot++;long token=epoch; // Commit before spawn callbacks; a save cannot replay this slot.
            int side=(next&1)==0?-1:1;Vec3 ground=floor(craft.position(),side);
            if(ground!=null&&population()<LOCAL_CAPACITY&&craft.level() instanceof ServerLevel level){
                MilitarySoldier soldier=craft.getPassengers().stream().filter(p->p instanceof MilitarySoldier&&p.getPersistentData().getBoolean("HelicopterTroop"))
                        .map(p->(MilitarySoldier)p).findFirst().orElse(null);
                boolean existing=soldier!=null;
                if(!existing)soldier=ModEntities.BLACKWATCH_SOLDIER.get().create(level);
                if(soldier!=null){
                    soldier.setPos(HelicopterRappel.anchor(craft,side));soldier.setPersistenceRequired();soldier.setAutomaticUnitJoin(false);
                    if(!existing)soldier.initializeNativeLoadout("rappel:"+craft.getUUID()+":"+craft.level().getGameTime()+":"+next);
                    HelicopterRappel.begin(craft,soldier,side);
                    if(!existing&&!level.addFreshEntity(soldier)){HelicopterRappel.release(soldier,craft,false);soldier.discard();}
                    if(token!=epoch||!enabled||craft.isRemoved()||craft.health()<=0)return false;
                }
            }
        }
        if((slot>=6&&descending().isEmpty())||age>=600){
            releaseAll();wave=false;cooldown=COOLDOWN_TICKS;sync();return true;
        }
        sync();return false;
    }
    private void sync(){
        if(craft.level().isClientSide)return;var tag=new CompoundTag();var troops=descending();
        tag.putIntArray("Troops",troops.stream().mapToInt(MilitarySoldier::getId).toArray());
        tag.putIntArray("Sides",troops.stream().mapToInt(HelicopterRappel::side).toArray());craft.setDeploymentData(tag);
    }
    public void save(CompoundTag root){var tag=new CompoundTag();tag.putBoolean("Enabled",enabled);tag.putBoolean("Wave",wave);tag.putInt("Slot",slot);tag.putInt("Age",age);tag.putInt("Cooldown",cooldown);root.put("AirDeployment",tag);}
    public void load(CompoundTag root){
        epoch++;var tag=root.getCompound("AirDeployment");
        if(enabled&&(!tag.getBoolean("Enabled")||root.getInt("FlightPhase")!=BlackwatchHelicopter.Phase.UNLOAD.ordinal()))releaseAll();
        enabled=tag.getBoolean("Enabled");wave=enabled&&tag.getBoolean("Wave");
        slot=Math.max(0,Math.min(6,tag.getInt("Slot")));age=Math.max(0,Math.min(600,tag.getInt("Age")));cooldown=Math.max(0,Math.min(COOLDOWN_TICKS,tag.getInt("Cooldown")));
        craft.setDeploymentData(new CompoundTag());
    }
}
