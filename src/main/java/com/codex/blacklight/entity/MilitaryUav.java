package com.codex.blacklight.entity;

import com.codex.blacklight.infection.BlacklightInfectionSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.*;

/** Reconnaissance only: sustained unobstructed scan, finite battery, interruptible transmission and bounded Blackwatch support. */
public final class MilitaryUav extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> SCAN=SynchedEntityData.defineId(MilitaryUav.class,EntityDataSerializers.INT),TARGET=SynchedEntityData.defineId(MilitaryUav.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SIGNAL=SynchedEntityData.defineId(MilitaryUav.class,EntityDataSerializers.INT);
    private final com.codex.blacklight.military.UavRecon recon=new com.codex.blacklight.military.UavRecon();
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private Vec3 home,reconPoint;private UUID owner,scanTarget,reconAnchor;private boolean atReconPoint;private int battery=6000,nextReconPoint;
    public MilitaryUav(EntityType<? extends MilitaryUav> type,Level level){super(type,level);setNoGravity(true);xpReward=8;}
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,35).add(Attributes.MOVEMENT_SPEED,.3).add(Attributes.FOLLOW_RANGE,24).add(Attributes.KNOCKBACK_RESISTANCE,.2);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(SCAN,0);entityData.define(TARGET,-1);entityData.define(SIGNAL,0);}
    @Override protected void registerGoals(){}
    public String asset(){return "recon_uav";}public int scanProgress(){return entityData.get(SCAN);}public int scanTargetId(){return entityData.get(TARGET);}
    public void assign(MilitarySoldier soldier){owner=soldier.getUUID();home=soldier.position();}
    public UUID owner(){return owner;}
    public int signalProgress(){return entityData.get(SIGNAL);}
    public int supportCooldown(){return recon.supportWait();}
    /** Original FX are green idle, yellow seeing, red alert; duration is the Minecraft recon clock. */
    public int warningLamp(){return !isAlive()?-1:scanProgress()>=com.codex.blacklight.military.UavRecon.SCAN_TICKS?2:scanProgress()>0?1:0;}
    /** P1 excludes helicopter/tank targets from its seeing branch. The Minecraft
     * bridge follows only a contact this scanner has already recognized on foot. */
    private static Entity anchor(LivingEntity target){var root=target.getRootVehicle();return root instanceof BlackwatchHelicopter||root instanceof MilitaryVehicle v&&v.chassisId().equals("tank")?root:target;}
    private static boolean enclosed(LivingEntity target){return anchor(target)!=target;}
    private boolean visible(LivingEntity target){
        var body=anchor(target);double range=body instanceof BlackwatchHelicopter?44:22;var point=body==target?target.getEyePosition():body.getBoundingBox().getCenter();
        return body.isAlive()&&!body.isRemoved()&&distanceToSqr(body)<range*range&&com.codex.blacklight.military.vehicle.VehicleDamageBridge.loaded((ServerLevel)level(),getEyePosition(),point)
                &&level().clip(new net.minecraft.world.level.ClipContext(getEyePosition(),point,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this)).getType()==net.minecraft.world.phys.HitResult.Type.MISS;
    }
    /** A public support request cannot bypass the real server acquisition/transmission. */
    public boolean confirmedForSupport(LivingEntity target){return !level().isClientSide&&isAlive()&&!isRemoved()&&!isNoAi()&&!com.codex.blacklight.combat.ImpactControl.stunned(this)&&candidate(target)&&!enclosed(target)&&recon.ready(target.getUUID())&&visible(target);}
    private void syncRecon(){entityData.set(SCAN,recon.scan());entityData.set(SIGNAL,recon.signal());}
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){boolean accepted=super.hurt(source,amount);if(accepted&&!level().isClientSide)resetScan();return accepted;}

    private boolean deathBurst;
    @Override public void die(net.minecraft.world.damagesource.DamageSource source){
        super.die(source);if(!deathBurst&&level() instanceof ServerLevel server){deathBurst=true;resetScan();
            playSound(com.codex.blacklight.military.vehicle.VehicleSounds.event("uav_break"),1,1);
            com.codex.blacklight.military.vehicle.VehicleDestructionEffects.emit(this,com.codex.blacklight.military.vehicle.VehicleDestruction.Chassis.UAV,true,0,0,false);
            // The source replaces the live machine with uav001Husk. Its cosmetic receipt owns the wreck.
            discard();
        }
    }
    private boolean candidate(LivingEntity target){return target!=null&&target!=this&&target.isAlive()&&!target.isRemoved()&&target.level()==level()&&BlacklightInfectionSystem.isInfected(target)&&(!(target instanceof Player p)||!p.isCreative()&&!p.isSpectator());}
    @Override public boolean isAlliedTo(Entity target){return target instanceof MilitarySoldier||target instanceof MilitaryUav||target instanceof EncounterMob m&&m.kind().military||super.isAlliedTo(target);}
    @Override public void tick(){
        super.tick();if(level().isClientSide)return;var level=(ServerLevel)level();
        if(!isAlive()||isNoAi()||level.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL||com.codex.blacklight.combat.ImpactControl.stunned(this)){recon.tick(null,false,false);resetScan();setDeltaMovement(Vec3.ZERO);return;}
        if(home==null)home=position();if(--battery<=0){resetScan();discard();return;}
        var captain=owner==null?null:level.getEntity(owner);if(captain instanceof MilitarySoldier soldier&&soldier.isAlive()&&position().distanceToSqr(soldier.position())<48*48)home=soldier.position();
        LivingEntity target=scanTarget==null?null:level.getEntity(scanTarget) instanceof LivingEntity l?l:null;
        if(target!=null&&(!candidate(target)||distanceToSqr(anchor(target))>(anchor(target) instanceof BlackwatchHelicopter?44*44:24*24)||enclosed(target)&&scanProgress()<com.codex.blacklight.military.UavRecon.SCAN_TICKS)){target=null;resetScan();}
        if(target==null&&tickCount%10==0)target=level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(22),t->candidate(t)&&!enclosed(t)).stream().filter(this::visible).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        boolean seen=target!=null&&visible(target);
        if(target!=null){
            if(!target.getUUID().equals(scanTarget)){scanTarget=target.getUUID();entityData.set(TARGET,target.getId());}
            var body=anchor(target);if(!body.getUUID().equals(reconAnchor)){reconAnchor=body.getUUID();reconPoint=null;atReconPoint=false;}
            float bearing=(float)Math.toDegrees(Math.atan2(getX()-body.getX(),body.getZ()-getZ()));
            setYRot(net.minecraft.util.Mth.approachDegrees(getYRot(),bearing,10));yBodyRot=yHeadRot=getYRot();setXRot(0);
        }
        var event=com.codex.blacklight.military.UavRecon.Event.NONE;
        if(target!=null&&enclosed(target))recon.trackRecognized(target.getUUID(),seen,true);
        else event=recon.tick(target==null?null:target.getUUID(),seen,true);
        syncRecon();
        if(event==com.codex.blacklight.military.UavRecon.Event.DETECTED)report(target);
        if(event==com.codex.blacklight.military.UavRecon.Event.REQUEST_SUPPORT){
            boolean dispatched=com.codex.blacklight.military.aircraft.HelicopterSupport.request(this,target);
            recon.dispatched(dispatched);syncRecon();
        }
        if(!seen&&scanProgress()==0)resetScan();
        Vec3 desired=home.add(Math.sin(tickCount*.018)*7,5,Math.cos(tickCount*.018)*7);
        if(seen){
            var body=anchor(target);boolean air=body instanceof BlackwatchHelicopter;
            double distance=position().subtract(body.position()).horizontalDistance();
            if(reconPoint!=null&&!atReconPoint&&position().distanceToSqr(reconPoint)<=.5*.5){atReconPoint=true;nextReconPoint=tickCount+60;}
            double pointDistance=reconPoint==null?0:reconPoint.subtract(body.position()).horizontalDistance();
            boolean stale=reconPoint!=null&&(pointDistance<(air?20:4)||pointDistance>(air?35:10)||Math.abs(reconPoint.y-body.getY())>5);
            // Source near/far reactions are 15/40 for a helicopter, 3/15 otherwise.
            // Its 3-second reselection starts after arrival, not halfway through flight.
            if(reconPoint==null||stale||reservedAir(level,position(),reconPoint)||atReconPoint&&(tickCount>=nextReconPoint||distance<(air?15:3)||distance>=(air?40:15))){
                reconPoint=chooseReconPoint(level,body);atReconPoint=false;
            }
            if(reconPoint!=null)desired=atReconPoint?position():reconPoint;
        }
        Vec3 delta=desired.subtract(position());Vec3 step=delta.lengthSqr()>.16*.16?delta.normalize().scale(.16):delta;
        if(horizontalCollision)step=new Vec3(-step.z,.15,step.x);
        var exit=reservationExit(level);if(exit!=null)step=exit;
        if(level.hasChunkAt(net.minecraft.core.BlockPos.containing(position().add(step)))&&level.getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(position().add(step)))&&!reservedAir(level,position(),position().add(step))&&level.noCollision(this,getBoundingBox().move(step)))move(MoverType.SELF,step);
        setDeltaMovement(Vec3.ZERO);
    }
    private Vec3 chooseReconPoint(ServerLevel level,Entity target){
        // P1 engage_uav uses 20..35 for helicopters and 4..10 otherwise, vertical difference 1..5,
        // at most 20 candidates, minimum relocation 2 and a 3-second reselection track.
        // These are translated into bounded loaded Minecraft air positions; no chunk tickets.
        for(int i=0;i<20;i++){
            double angle=random.nextDouble()*Math.PI*2,radius=target instanceof BlackwatchHelicopter?20+random.nextDouble()*15:4+random.nextDouble()*6;
            Vec3 point=target.position().add(Math.cos(angle)*radius,3,Math.sin(angle)*radius);
            var block=net.minecraft.core.BlockPos.containing(point);
            if(!level.hasChunkAt(block)||!level.getWorldBorder().isWithinBounds(block))continue;
            double ground=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,block.getX(),block.getZ());
            point=new Vec3(point.x,Math.max(point.y,ground+2),point.z);
            if(point.y-target.getY()>5||point.distanceToSqr(position())<4||!com.codex.blacklight.military.vehicle.VehicleDamageBridge.loaded(level,position(),point))continue;
            if(!reservedAir(level,position(),point)&&clearReconPath(level,point))return point;
        }
        return position();
    }
    private boolean clearReconPath(ServerLevel level,Vec3 point){
        var delta=point.subtract(position());int steps=(int)Math.ceil(delta.length()/.75);
        if(steps>128)return false;
        for(int i=1;i<=steps;i++)if(!level.noCollision(this,getBoundingBox().move(delta.scale((double)i/steps))))return false;
        return true;
    }
    private boolean reservedAir(ServerLevel level,Vec3 from,Vec3 to){
        var begin=getBoundingBox().move(from.subtract(position()));var end=begin.move(to.subtract(from));var swept=begin.expandTowards(to.subtract(from));
        for(var craft:level.getEntitiesOfClass(BlackwatchHelicopter.class,getBoundingBox().inflate(96))){
            var zone=reservation(craft);if(zone==null)continue;
            if(!swept.intersects(zone))continue;
            // A reservation can appear around an existing UAV. Let it retreat without
            // increasing its swept overlap; otherwise the reservation would trap it inside.
            double overlap=overlap(begin,zone);
            if(overlap>0&&overlap(end,zone)<overlap-1e-8&&overlap(swept,zone)<=overlap+1e-8)continue;
            return true;
        }
        return false;
    }
    private net.minecraft.world.phys.AABB reservation(BlackwatchHelicopter craft){
        var p=craft.landing();if(p==null)return null;var phase=craft.phase();
        boolean reserved=phase==BlackwatchHelicopter.Phase.SPIN_UP||phase==BlackwatchHelicopter.Phase.CLIMB||phase==BlackwatchHelicopter.Phase.APPROACH||phase==BlackwatchHelicopter.Phase.DESCEND||phase==BlackwatchHelicopter.Phase.UNLOAD
                ||phase==BlackwatchHelicopter.Phase.DEPART&&craft.getY()<p.y+8;
        return reserved?new net.minecraft.world.phys.AABB(p.x-7.5,p.y,p.z-7.5,p.x+7.5,p.y+6.5,p.z+7.5):null;
    }
    private Vec3 reservationExit(ServerLevel level){
        var box=getBoundingBox();
        for(var craft:level.getEntitiesOfClass(BlackwatchHelicopter.class,box.inflate(96))){
            var zone=reservation(craft);if(zone==null||!box.intersects(zone))continue;
            var exits=new ArrayList<>(List.of(new Vec3(zone.minX-box.maxX-.01,0,0),new Vec3(zone.maxX-box.minX+.01,0,0),new Vec3(0,zone.maxY-box.minY+.01,0),new Vec3(0,0,zone.minZ-box.maxZ-.01),new Vec3(0,0,zone.maxZ-box.minZ+.01)));
            exits.sort(Comparator.comparingDouble(Vec3::lengthSqr));
            for(var exit:exits){var step=exit.normalize().scale(Math.min(.16,exit.length()));
                if(!reservedAir(level,position(),position().add(step))&&level.noCollision(this,box.move(step)))return step;
            }
            return Vec3.ZERO;
        }
        return null;
    }
    private static double overlap(net.minecraft.world.phys.AABB a,net.minecraft.world.phys.AABB b){return Math.max(0,Math.min(a.maxX,b.maxX)-Math.max(a.minX,b.minX))*Math.max(0,Math.min(a.maxY,b.maxY)-Math.max(a.minY,b.minY))*Math.max(0,Math.min(a.maxZ,b.maxZ)-Math.max(a.minZ,b.minZ));}
    private void resetScan(){scanTarget=null;reconPoint=null;reconAnchor=null;atReconPoint=false;recon.interrupt();syncRecon();entityData.set(TARGET,-1);}
    private void report(LivingEntity target){
        if(target instanceof ServerPlayer player)com.codex.blacklight.disguise.DisguiseSystem.detectedByScanner(player);
        for(var soldier:level().getEntitiesOfClass(MilitarySoldier.class,getBoundingBox().inflate(32),MilitarySoldier::tacticsAvailable))if(soldier.validTarget(target)&&BlackwatchSquadSystem.withinTerritory(soldier,target.position())){
            BlackwatchSquadSystem.squad(soldier).observe(new BlackwatchSquad.Contact(soldier.getUUID(),target.getUUID(),BlackwatchSquadSystem.position(target.position()),BlackwatchSquadSystem.position(position()),level().getGameTime(),false));soldier.setTarget(target);
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putInt("UavBattery",battery);tag.putInt("UavReportWait",recon.reportWait());tag.putInt("UavSupportWait",recon.supportWait());if(owner!=null)tag.putUUID("UavOwner",owner);if(home!=null){tag.putDouble("UavHomeX",home.x);tag.putDouble("UavHomeY",home.y);tag.putDouble("UavHomeZ",home.z);}}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);battery=tag.contains("UavBattery")?Math.max(0,Math.min(6000,tag.getInt("UavBattery"))):6000;owner=tag.hasUUID("UavOwner")?tag.getUUID("UavOwner"):null;home=tag.contains("UavHomeX")&&tag.contains("UavHomeY")&&tag.contains("UavHomeZ")?new Vec3(tag.getDouble("UavHomeX"),tag.getDouble("UavHomeY"),tag.getDouble("UavHomeZ")):null;if(home!=null&&!Double.isFinite(home.lengthSqr()))home=null;recon.restoreCooldowns(tag.getInt("UavReportWait"),tag.getInt("UavSupportWait"));resetScan();}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){c.add(new AnimationController<>(this,"body",2,s->s.setAndContinue(RawAnimation.begin().thenLoop("idle"))));}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
