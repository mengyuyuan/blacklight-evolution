package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.entity.GreeneProjectile;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.military.vehicle.VehicleDamageBridge;
import com.codex.blacklight.military.vehicle.VehicleDefinition;
import com.codex.blacklight.military.vehicle.VehicleHullShape;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Independent coming_out Hit session, NOT a roar or a strongest-window merge.
 * P1 marks the owner's shared CollisionActionBehaviour receipt before dispatch:
 * even a receiver that rejects damage consumes that victim for both tracks.
 *
 * MC adapters: model units become blocks once; LivingEntity/physical hulls map
 * to source Health/HitReaction receivers; living damage uses the existing .06
 * Greene balance, hulls use raw source damage and their existing armor policy.
 * No terrain destruction, invented prop receiver, eye-ray gate, or native
 * impulse fidelity is claimed. Native force/mass/receiver behavior is pending. */
public final class GreeneEmergenceHit {
    private record Contact(Entity target,ProjectileSphereContact.Hit hit,String bone){}
    private final EncounterMob owner;
    private final GreeneEmergenceClock clock=new GreeneEmergenceClock();
    private final Map<UUID,Float> receipts=new HashMap<>();
    private Vec3 previous;
    private long lastTick=Long.MIN_VALUE;

    public GreeneEmergenceHit(EncounterMob owner){this.owner=owner;}
    public void begin(){clear();clock.reset();previous=owner.position();lastTick=owner.level().getGameTime();}
    public void clear(){clock.clear();receipts.clear();previous=null;lastTick=Long.MIN_VALUE;}
    private boolean live(GreeneEmergenceClock.Frame frame){
        return clock.isCurrent(frame)&&owner.isAlive()&&!owner.isRemoved()&&owner.greeneStage()==GreeneBattleCycle.EMERGING;
    }
    /** false means a callback replaced/canceled the session: its caller must
     * stop the old stage's remaining work as well. No unloaded-time catch-up. */
    public boolean tick(){
        if(!(owner.level() instanceof ServerLevel level))return true;
        long now=level.getGameTime();if(lastTick==now)return true;
        Vec3 current=owner.position();
        // MC relocation boundary: an unobserved update, load or >8-block jump
        // does not attack along a teleport corridor. Ordinary root Move is <1.
        if(previous==null||lastTick!=now-1||previous.distanceToSqr(current)>64)previous=current;
        lastTick=now;
        var frame=clock.advance();
        receipts.entrySet().removeIf(entry->frame.elapsedSeconds()-entry.getValue()>5F);
        for(var track:frame.tracks()){
            if(!live(frame))return false;
            var samples=GreeneEmergenceShape.samples(track,previous,current,owner.getYRot(),GreeneMotion.table().sourceUnitBlocks());
            var bounds=GreeneEmergenceShape.bounds(samples);if(bounds==null)continue;
            var candidates=new LinkedHashSet<Entity>();
            candidates.addAll(CombatTargeting.candidates(owner,bounds,this::eligibleLiving));
            for(var entity:level.getEntities(owner,bounds,e->!e.isRemoved())){
                var hull=VehicleDamageBridge.vehicle(entity);
                if(hull!=null&&hull.health()>0)candidates.add(hull);
            }
            // Native only moves the earliest fraction to the front; it does
            // not globally sort. Keep MC spatial-query order for the rest.
            var contacts=new ArrayList<Contact>();var fractions=new ArrayList<Integer>();
            for(var candidate:candidates){
                if(receipts.containsKey(candidate.getUUID()))continue;
                for(int i=0;i<samples.size();i++){
                    var contact=contact(candidate,samples.get(i));
                    if(contact==null||!withinLoadedWorld(level,samples.get(i).center(),contact.hit.point()))continue;
                    contacts.add(contact);fractions.add(i);break;
                }
            }
            if(!contacts.isEmpty()){
                int first=0;for(int i=1;i<contacts.size();i++)if(fractions.get(i)<fractions.get(first))first=i;
                Collections.swap(contacts,0,first);
            }
            for(var contact:contacts){
                if(!live(frame))return false;
                var target=contact.target;
                if(!eligible(target)||receipts.containsKey(target.getUUID()))continue;
                // Write before hurt, not only after accepted damage. Reentrant
                // saves capture the debit, and either track sees the same map.
                receipts.put(target.getUUID(),frame.elapsedSeconds());
                dispatch(track,contact);
                if(!live(frame))return false;
            }
        }
        if(!live(frame))return false;
        previous=current;
        if(frame.activeAfter()==0)receipts.clear();
        return true;
    }
    private boolean eligibleLiving(LivingEntity target){
        if(target==owner||target.level()!=owner.level()||!target.isAlive()||target.isRemoved()
                ||target instanceof Player p&&(p.isCreative()||p.isSpectator()))return false;
        // Retain explicit scoreboard protection, while source faction allies
        // receive the authored reduced friendly-fire damage. A disguise is
        // acquisition policy; it cannot make a body immune to this area Hit.
        return owner.getTeam()==null||!owner.getTeam().isAlliedTo(target.getTeam());
    }
    private boolean eligible(Entity target){
        if(target.isRemoved()||target.level()!=owner.level())return false;
        if(target instanceof LivingEntity living)return eligibleLiving(living);
        if(target instanceof MilitaryVehicle hull)return hull.health()>0&&!VehicleDamageBridge.protectedHull(owner,hull);
        return false;
    }
    private Contact contact(Entity target,GreeneEmergenceShape.Sample sample){
        if(!eligible(target))return null;
        Contact result=null;
        if(target instanceof LivingEntity living){
            var body=AnimatedHurtBody.of(living);
            if(body!=null){
                for(var region:body.hurtRegions(0))for(var box:region.boxes()){
                    var hit=GreeneEmergenceShape.contact(sample,box);result=nearer(result,target,hit,region.bone());
                }
            }else for(var box:CombatTargeting.hitboxes(living))result=nearer(result,target,GreeneEmergenceShape.contact(sample,box),null);
        }else if(target instanceof MilitaryVehicle hull){
            var shape=VehicleHullShape.from(hull.definition(),hull.turretYaw(),hull.gunPitch());
            for(var corners:shape.worldCorners(new VehicleDefinition.Vec3(hull.getX(),hull.getY(),hull.getZ()),hull.getYRot())){
                var points=corners.stream().map(p->new Vec3(p.x(),p.y(),p.z())).toList();
                result=nearer(result,target,GreeneEmergenceShape.contact(sample,points),null);
            }
        }else result=nearer(result,target,GreeneEmergenceShape.contact(sample,target.getBoundingBox()),null);
        return result;
    }
    private static Contact nearer(Contact previous,Entity target,ProjectileSphereContact.Hit hit,String bone){
        return hit!=null&&(previous==null||hit.center().distanceToSqr(hit.point())<previous.hit.center().distanceToSqr(previous.hit.point()))?new Contact(target,hit,bone):previous;
    }
    private static boolean withinLoadedWorld(ServerLevel level,Vec3 from,Vec3 to){
        var border=level.getWorldBorder();
        if(!(from.x>=border.getMinX()&&from.x<=border.getMaxX()&&from.z>=border.getMinZ()&&from.z<=border.getMaxZ()
                &&to.x>=border.getMinX()&&to.x<=border.getMaxX()&&to.z>=border.getMinZ()&&to.z<=border.getMaxZ()))return false;
        // A tunneling root may legitimately lie below minBuildHeight. The
        // general vehicle helper also validates Y and is unsuitable here.
        int minX=net.minecraft.util.Mth.floor(Math.min(from.x,to.x)-.001)>>4,maxX=net.minecraft.util.Mth.floor(Math.max(from.x,to.x)+.001)>>4;
        int minZ=net.minecraft.util.Mth.floor(Math.min(from.z,to.z)-.001)>>4,maxZ=net.minecraft.util.Mth.floor(Math.max(from.z,to.z)+.001)>>4;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(level.getChunkSource().getChunkNow(x,z)==null)return false;
        return true;
        // The source overlaps Health/HitReaction colliders from an underground
        // root. A root-to-eye block ray would incorrectly suppress emergence.
    }
    private void dispatch(GreeneEmergenceClock.Track track,Contact contact){
        if(contact.target instanceof LivingEntity living){
            float friendly=owner.isAlliedTo(living)?track.friendlyFire():1F;
            var point=new HydraHurtContact.Contact(contact.bone,contact.hit.point(),contact.hit.normal(),InfectedAttackStyle.CRUSH);
            owner.hurtEmergenceTarget(living,track.damage()*GreeneProjectile.LIVING_DAMAGE_ADAPTER*friendly,point);
        }else if(contact.target instanceof MilitaryVehicle hull)VehicleDamageBridge.hurtPhysical(owner,hull,track.damage(),null);
    }
    public CompoundTag save(){
        var tag=new CompoundTag();tag.put("Clock",clock.save());var list=new ListTag();
        receipts.forEach((id,at)->{var row=new CompoundTag();row.putUUID("Victim",id);row.putFloat("At",at);list.add(row);});
        tag.put("Receipts",list);return tag;
    }
    public void load(CompoundTag tag){
        clear();clock.load(tag.getCompound("Clock"));
        if(clock.hasActive())for(var value:tag.getList("Receipts",10)){
            var row=(CompoundTag)value;float at=row.getFloat("At");
            if(row.hasUUID("Victim")&&Float.isFinite(at)&&at>=0&&at<=clock.seconds()&&clock.seconds()-at<=5F)receipts.put(row.getUUID("Victim"),at);
        }
        // Entity.load has already placed the incoming root. Previous unloaded
        // coordinates are deliberately not a traversed attack corridor.
        previous=owner.position();lastTick=owner.level().getGameTime();
    }
}
