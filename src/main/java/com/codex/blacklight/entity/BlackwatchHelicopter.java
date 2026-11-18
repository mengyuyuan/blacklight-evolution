package com.codex.blacklight.entity;

import com.codex.blacklight.military.aircraft.HelicopterFlight;
import com.codex.blacklight.military.aircraft.HelicopterArmament;
import com.codex.blacklight.military.aircraft.HelicopterCombat;
import com.codex.blacklight.military.vehicle.VehicleKinematics;
import com.codex.blacklight.military.vehicle.VehicleSounds;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Mechanical air transport, with real riding troops and a collision-checked landing/unload mission. */
public final class BlackwatchHelicopter extends Entity {
    public enum Phase { IDLE,SPIN_UP,CLIMB,APPROACH,DESCEND,UNLOAD,DEPART,RETURN,CRASH,PURSUIT,STRAFE,MANUAL,HIJACK }
    private static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(BlackwatchHelicopter.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HEALTH=SynchedEntityData.defineId(BlackwatchHelicopter.class,EntityDataSerializers.FLOAT),ROTOR=SynchedEntityData.defineId(BlackwatchHelicopter.class,EntityDataSerializers.FLOAT),BANK=SynchedEntityData.defineId(BlackwatchHelicopter.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> GUN_YAW=SynchedEntityData.defineId(BlackwatchHelicopter.class,EntityDataSerializers.FLOAT),GUN_PITCH=SynchedEntityData.defineId(BlackwatchHelicopter.class,EntityDataSerializers.FLOAT),GUN_RANGE=SynchedEntityData.defineId(BlackwatchHelicopter.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> AIR_SHOTS=SynchedEntityData.defineId(BlackwatchHelicopter.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> CONTROL=SynchedEntityData.defineId(BlackwatchHelicopter.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Float> AUDIO_VELOCITY=SynchedEntityData.defineId(BlackwatchHelicopter.class,EntityDataSerializers.FLOAT),AUDIO_THROTTLE=SynchedEntityData.defineId(BlackwatchHelicopter.class,EntityDataSerializers.FLOAT);
    private final HelicopterCombat combat=new HelicopterCombat(this);
    private static final EntityDataAccessor<CompoundTag> DEPLOYMENT=SynchedEntityData.defineId(BlackwatchHelicopter.class,EntityDataSerializers.COMPOUND_TAG);
    private final com.codex.blacklight.military.aircraft.HelicopterDeployment deployment=new com.codex.blacklight.military.aircraft.HelicopterDeployment(this);
    private final com.codex.blacklight.military.aircraft.HelicopterPilot pilot=new com.codex.blacklight.military.aircraft.HelicopterPilot(this);
    private final com.codex.blacklight.military.aircraft.HelicopterNavigation navigation=new com.codex.blacklight.military.aircraft.HelicopterNavigation(this);
    public com.codex.blacklight.military.aircraft.HelicopterNavigation navigation(){return navigation;}
    private Vec3 home,landing;
    private int phaseAge,blockedTicks,missionAge;
    private int lerpSteps;private double lerpX,lerpY,lerpZ;private float lerpYaw;
    private float rotorAngle,previousRotorAngle;
    public BlackwatchHelicopter(EntityType<? extends BlackwatchHelicopter> type,Level level){super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData(){entityData.define(PHASE,0);entityData.define(HEALTH,360F);entityData.define(ROTOR,0F);entityData.define(BANK,0F);entityData.define(GUN_YAW,0F);entityData.define(GUN_PITCH,0F);entityData.define(GUN_RANGE,30F);entityData.define(AIR_SHOTS,0);entityData.define(CONTROL,new CompoundTag());entityData.define(AUDIO_VELOCITY,0F);entityData.define(AUDIO_THROTTLE,0F);entityData.define(DEPLOYMENT,new CompoundTag());}
    public Phase phase(){return Phase.values()[Mth.clamp(entityData.get(PHASE),0,Phase.values().length-1)];}
    public float health(){return entityData.get(HEALTH);}public float rotor(){return entityData.get(ROTOR);}public float bank(){return entityData.get(BANK);}
    public boolean audioRunning(){return health()>0&&phase()!=Phase.IDLE&&phase()!=Phase.CRASH;}
    public float audioVelocity(){return entityData.get(AUDIO_VELOCITY);}public float audioThrottle(){return entityData.get(AUDIO_THROTTLE);}
    public boolean doorsOpen(){return phase()==Phase.DESCEND||phase()==Phase.UNLOAD;}
    public int missionAge(){return missionAge;}
    public float rotorAngle(float partial){return Mth.lerp(partial,previousRotorAngle,rotorAngle);}
    public Vec3 landing(){return landing;}
    public HelicopterCombat combat(){return combat;}
    public com.codex.blacklight.military.aircraft.HelicopterDeployment deployment(){return deployment;}
    public CompoundTag deploymentData(){return entityData.get(DEPLOYMENT);}
    public void setDeploymentData(CompoundTag value){if(!level().isClientSide&&!value.equals(deploymentData()))entityData.set(DEPLOYMENT,value);}
    /** Source support transport uses an air destination; deployTo retains explicit legacy ground missions. */
    public boolean deployRappellingTo(Vec3 ground){
        if(!(level() instanceof ServerLevel)||health()<=0||pilot.owned()||!deployment.safeSite(ground))return false;
        home=position();landing=ground;missionAge=0;deployment.enable();
        boolean grounded=safeLanding(home);entityData.set(ROTOR,grounded?0F:1F);phase(grounded?Phase.SPIN_UP:Phase.CLIMB);return true;
    }
    public boolean redirectRappel(Vec3 ground){
        if(level().isClientSide||!deployment.enabled()||pilot.owned()||!deployment.safeSite(ground))return false;
        landing=ground;phase(Phase.APPROACH);return true;
    }
    public com.codex.blacklight.military.aircraft.HelicopterPilot pilot(){return pilot;}
    public CompoundTag controlData(){return entityData.get(CONTROL);}
    public void setControlData(CompoundTag tag){if(!level().isClientSide&&!tag.equals(controlData()))entityData.set(CONTROL,tag);}
    public void setFlightPhase(Phase phase){if(!level().isClientSide)phase(phase);}
    public void manualFlight(double forward,double steering,double lift,boolean brake){
        if(level().isClientSide||!Double.isFinite(forward)||!Double.isFinite(steering)||!Double.isFinite(lift)||Math.abs(forward)>1||Math.abs(steering)>1||Math.abs(lift)>1)return;
        if(rotor()<.99F){setDeltaMovement(Vec3.ZERO);return;}
        float yaw=getYRot()+(float)(steering*2.5);
        Vec3 desired=brake?Vec3.ZERO:HelicopterArmament.rotate(new Vec3(0,lift*.35,forward*.65),yaw,0);
        Vec3 change=desired.subtract(getDeltaMovement());if(change.length()>.035)change=change.normalize().scale(.035);
        Vec3 step=getDeltaMovement().add(change);
        if(!clearMove(Vec3.ZERO,yaw))yaw=getYRot();
        final float movementYaw=yaw;
        step=HelicopterFlight.slide(step,candidate->getY()+candidate.y<=level().getMaxBuildHeight()-6
                &&clearMove(candidate,movementYaw));
        setYRot(yaw);setDeltaMovement(step);move(MoverType.SELF,step);fallDistance=0;
        entityData.set(BANK,Mth.clamp((float)-steering*10,-10,10));
    }
    /** An abandoned player aircraft brakes horizontally and descends until its
     * real hull meets solid ground. A sloped pad need not support all nine survey points. */
    public void landWithoutPilot(){
        if(level().isClientSide||phase()==Phase.CRASH)return;
        setDeltaMovement(0,Math.min(0,getDeltaMovement().y),0);
        if(groundContact()){phase(Phase.IDLE);setDeltaMovement(Vec3.ZERO);return;}
        phase(Phase.MANUAL);
        manualFlight(0,0,-1,false);
        if(groundContact()){phase(Phase.IDLE);setDeltaMovement(Vec3.ZERO);}
    }
    private boolean groundContact(){
        var body=envelope(position(),getYRot(),false);
        var sole=new AABB(body.minX+.001,body.minY-.002,body.minZ+.001,
                body.maxX-.001,body.minY+.001,body.maxZ-.001);
        return VehicleKinematics.loaded(level(),sole)&&level().getBlockCollisions(this,sole).iterator().hasNext();
    }
    public float gunYaw(){return entityData.get(GUN_YAW);}public float gunPitch(){return entityData.get(GUN_PITCH);}
    public int airShots(){return entityData.get(AIR_SHOTS);}
    public void recordAirShot(){entityData.set(AIR_SHOTS,airShots()+1);}
    public Vec3 weaponPoint(Vec3 local){return position().add(HelicopterArmament.rotate(local,getYRot(),bank()));}
    public Vec3 aimPoint(){return weaponPoint(HelicopterArmament.GUN).add(HelicopterArmament.rotate(HelicopterArmament.direction(gunYaw(),gunPitch()),getYRot(),bank()).scale(entityData.get(GUN_RANGE)));}
    public HelicopterArmament.Aim podAim(int side){
        var aim=HelicopterArmament.aim(aimPoint().subtract(weaponPoint(HelicopterArmament.pod(side))),getYRot(),bank());
        // Narrow pod traverse keeps an inward shot outside the cabin; the chin gun has its own wider arc.
        return new HelicopterArmament.Aim(Mth.clamp(aim.yaw(),-10,10),Mth.clamp(aim.pitch(),0,65));
    }
    public void aimWeapon(Vec3 point){aimWeapon(point,false);}
    public void aimWeapon(Vec3 point,boolean manual){
        if(level().isClientSide||!HelicopterFlight.finite(point))return;
        Vec3 delta=point.subtract(weaponPoint(HelicopterArmament.GUN));
        var desired=HelicopterArmament.aim(delta,getYRot(),bank());
        var aim=manual?new HelicopterArmament.Aim(desired.yaw(),Mth.clamp(desired.pitch(),-20,90)):HelicopterArmament.limit(desired);
        float step=manual?16F:2.5F;
        entityData.set(GUN_YAW,Mth.wrapDegrees(Mth.approachDegrees(gunYaw(),aim.yaw(),step)));
        entityData.set(GUN_PITCH,Mth.approach(gunPitch(),aim.pitch(),step));
        entityData.set(GUN_RANGE,(float)Math.min(HelicopterCombat.weaponRange(),delta.length()));
    }
    public Vec3 podMuzzle(int side,int tube){
        var aim=podAim(side);var offset=HelicopterArmament.mountOffset(HelicopterArmament.tube(tube),aim.yaw(),aim.pitch());
        return weaponPoint(HelicopterArmament.pod(side).add(offset));
    }
    public Vec3 hellfireLaunchPoint(int side,int slot){return weaponPoint(com.codex.blacklight.military.aircraft.HelicopterHellfireRack.mount(side).add(com.codex.blacklight.military.aircraft.HelicopterHellfireRack.slot(slot)));}
    public void combatFlight(Vec3 point,Vec3 facing){if(!level().isClientSide)fly(point,.5,true,facing);}
    /** Maneuver completion is actual swept arrival, never a stopped/blocked flag.
     * Source normalized speed is scaled to this port's existing flight envelope. */
    public boolean combatMove(Vec3 point,Vec3 facing,double speedFactor){
        return !level().isClientSide&&HelicopterFlight.finite(point)&&HelicopterFlight.finite(facing)
                &&Double.isFinite(speedFactor)&&speedFactor>0
                &&fly(point,HelicopterFlight.MAX_SPEED*Math.min(1,speedFactor),true,facing);
    }
    /** Source movement request expressed through MC acceleration and swept airframe navigation. */
    public void combatVector(Vec3 request,Vec3 facing){
        if(level().isClientSide||!HelicopterFlight.finite(request))return;
        double length=request.length();
        if(length<1e-8){combatFlight(position(),facing);return;}
        fly(position().add(request.scale(HelicopterFlight.BRAKE_DISTANCE/length)),HelicopterFlight.MAX_SPEED*Math.min(1,length),true,facing);
    }
    public void beginStrafe(){if(!level().isClientSide&&phase()==Phase.PURSUIT)phase(Phase.STRAFE);}
    public void endCombat(){if(!level().isClientSide){combat.finish();phase(Phase.RETURN);}}
    private void phase(Phase next){if(phase()!=next){
        if((phase()==Phase.PURSUIT||phase()==Phase.STRAFE)&&next!=Phase.PURSUIT&&next!=Phase.STRAFE)combat.interrupted();
        entityData.set(PHASE,next.ordinal());phaseAge=0;blockedTicks=0;navigation.reset();
    }}
    @Override public void remove(RemovalReason reason){if(!level().isClientSide)combat.interrupted();super.remove(reason);}
    public boolean deployTo(Vec3 destination){
        if(!(level() instanceof ServerLevel)||health()<=0||!HelicopterFlight.finite(destination)||!safeLanding(destination))return false;
        deployment.disable();home=position();landing=destination;missionAge=0;
        boolean grounded=safeLanding(home);entityData.set(ROTOR,grounded?0F:1F);phase(grounded?Phase.SPIN_UP:Phase.CLIMB);return true;
    }
    /** Check the entire fuselage/rotor envelope plus a supported, dry landing footprint. */
    public boolean safeLanding(Vec3 point){
        if(!HelicopterFlight.finite(point)||!clearAt(point,getYRot()))return false;
        for(double x:new double[]{-1.1,0,1.1})for(double z:new double[]{-2.4,0,2.4}){
            BlockPos below=BlockPos.containing(point.add(new Vec3(x,-.05,z).yRot((float)Math.toRadians(getYRot()))));
            if(!level().hasChunkAt(below)||!level().getFluidState(below).isEmpty()||!level().getBlockState(below).isFaceSturdy(level(),below,Direction.UP))return false;
        }
        return true;
    }
    private static AABB envelope(Vec3 at,float yaw,boolean rotor){
        if(rotor)return new AABB(at.x-7,at.y+2.3,at.z-7,at.x+7,at.y+5.8,at.z+7);
        double sin=Math.sin(Math.toRadians(yaw)),cos=Math.cos(Math.toRadians(yaw));
        double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxZ=maxX;
        for(double x:new double[]{-3.1,3.1})for(double z:new double[]{-6.4,4.9}){
            double xx=x*cos+z*sin,zz=z*cos-x*sin;minX=Math.min(minX,xx);maxX=Math.max(maxX,xx);minZ=Math.min(minZ,zz);maxZ=Math.max(maxZ,zz);
        }
        return new AABB(at.x+minX,at.y,at.z+minZ,at.x+maxX,at.y+3.75,at.z+maxZ);
    }
    @Override protected AABB makeBoundingBox(){return envelope(position(),getYRot(),false);}
    private boolean clear(AABB box){return VehicleKinematics.loaded(level(),box.inflate(1))&&level().getWorldBorder().isWithinBounds(box)&&level().noCollision(this,box.deflate(.001))
            &&level().getEntities(this,box,e->e instanceof LivingEntity living&&living.isAlive()&&!hasPassenger(e)&&!(e instanceof MilitarySoldier soldier&&com.codex.blacklight.military.aircraft.HelicopterRappel.belongs(soldier,this))).isEmpty();}
    public boolean clearAt(Vec3 at,float yaw){return clear(envelope(at,yaw,false))&&clear(envelope(at,yaw,true));}
    private boolean clearMove(Vec3 step,float yaw){
        // Preserve both yaw envelopes across the complete translation. An existing
        // overlap may only shrink during translation; it never authorizes a turn into a body.
        for(boolean rotor:new boolean[]{false,true}){
            var before=envelope(position(),getYRot(),rotor).minmax(envelope(position(),yaw,rotor));
            if(yaw!=getYRot()&&!clear(before))return false;
            if(!com.codex.blacklight.military.aircraft.HelicopterMovementClearance.clear(this,before,before.move(step)))return false;
        }
        return true;
    }
    /** Conservative terrain corridor, including yaw-independent body clearance away from the real starting pose.
     * Dynamic bodies remain protected by the authoritative per-tick movement sweep; they are not cached as terrain. */
    public boolean clearAirRoute(Vec3 from,Vec3 to,Vec3 origin,float originYaw){
        if(!HelicopterFlight.finite(from)||!HelicopterFlight.finite(to)||!HelicopterFlight.finite(origin)||!Float.isFinite(originYaw))return false;
        if(Math.min(from.y,to.y)<level().getMinBuildHeight()||Math.max(from.y,to.y)+5.8>level().getMaxBuildHeight())return false;
        for(boolean rotor:new boolean[]{false,true}){
            var a=routeEnvelope(from,origin,originYaw,rotor);var b=routeEnvelope(to,origin,originYaw,rotor);var swept=a.minmax(b);
            if(!VehicleKinematics.loaded(level(),swept.inflate(1))||!level().getWorldBorder().isWithinBounds(swept)
                    ||level().getBlockCollisions(this,swept.deflate(.001)).iterator().hasNext()||level().containsAnyLiquid(swept.deflate(.001)))return false;
        }
        return true;
    }
    private static AABB routeEnvelope(Vec3 at,Vec3 origin,float yaw,boolean rotor){
        if(rotor||at.distanceToSqr(origin)<.000001)return envelope(at,yaw,rotor);
        double radius=Math.hypot(3.1,6.4);return new AABB(at.x-radius,at.y,at.z-radius,at.x+radius,at.y+3.75,at.z+radius);
    }
    private boolean fly(Vec3 goal,double speed,boolean avoid){return fly(goal,speed,avoid,null);}
    private boolean fly(Vec3 goal,double speed,boolean avoid,Vec3 facing){
        if(!avoid)navigation.reset();
        Vec3 waypoint=avoid?navigation.guide(goal):goal;
        boolean holding=waypoint==null;
        Vec3 step=HelicopterFlight.velocity(getDeltaMovement(),position(),holding?position():waypoint,speed);
        boolean landingFlight=!avoid&&phase()==Phase.DESCEND&&goal.equals(landing);
        // Final landing correction belongs to this same swept step. A second
        // unswept move after fly() could skip new rotor/body obstructions.
        if(landingFlight&&position().distanceTo(goal)<.06&&step.length()<.06)step=goal.subtract(position());
        float yaw=HelicopterFlight.heading(facing==null?step:facing.subtract(position()),getYRot());
        if(!clearMove(step,yaw)){
            if(!navigation.searching())blockedTicks++;step=Vec3.ZERO;
            if(avoid&&!holding){
                navigation.obstructed();
                Vec3 forward=goal.subtract(position()).multiply(1,0,1).normalize();
                for(double angle:new double[]{0,Math.PI/4,-Math.PI/4,Math.PI/2,-Math.PI/2}){
                    Vec3 candidate=forward.yRot((float)angle).scale(.14).add(0,.16,0);
                    if(clearMove(candidate,getYRot())){step=candidate;break;}
                }
            }
        }else {blockedTicks=holding&&!navigation.searching()?blockedTicks+1:0;setYRot(yaw);}
        setDeltaMovement(step);move(MoverType.SELF,step);fallDistance=0;
        entityData.set(BANK,Mth.clamp(Mth.wrapDegrees(getYRot()-yRotO)*-3,-14,14));
        return !holding&&position().distanceTo(goal)<(landingFlight?.000001:.22)&&step.length()<.06;
    }
    @Override public void tick(){
        super.tick();
        previousRotorAngle=rotorAngle;rotorAngle+=rotor()*1.25F;
        if(level().isClientSide){
            if(lerpSteps>0){setPos(getX()+(lerpX-getX())/lerpSteps,getY()+(lerpY-getY())/lerpSteps,getZ()+(lerpZ-getZ())/lerpSteps);setYRot(getYRot()+Mth.wrapDegrees(lerpYaw-getYRot())/lerpSteps);lerpSteps--;}
            return;
        }
        if(crashExploded){discard();return;}
        Vec3 audioStart=position(),audioBefore=getDeltaMovement();long audioEpoch=destructionEpoch;
        try {
        phaseAge++;if(phase()!=Phase.IDLE)missionAge++;
        entityData.set(ROTOR,Mth.clamp(rotor()+(phase()==Phase.IDLE||phase()==Phase.CRASH?-.018F:.0125F),0,1));
        deployment.tick();
        if(pilot.tick()){deployment.tick();positionPassengers();return;}
        if(phase()!=Phase.IDLE&&phase()!=Phase.CRASH&&missionAge>5&&getPassengers().stream().limit(2).noneMatch(p->!p.getPersistentData().getBoolean("HelicopterTroop")&&MilitaryVehicle.crewOperational(p)))phase(Phase.CRASH);
        if(phase()==Phase.CRASH){crash();return;}
        if(home==null||landing==null){phase(Phase.IDLE);setDeltaMovement(Vec3.ZERO);return;}
        double cruise=Math.min(level().getMaxBuildHeight()-10,Math.max(home.y,landing.y+16));
        switch(phase()){
            case IDLE -> setDeltaMovement(Vec3.ZERO);
            case SPIN_UP -> {setDeltaMovement(Vec3.ZERO);if(rotor()>=.99F)phase(Phase.CLIMB);}
            case CLIMB -> {if(fly(new Vec3(home.x,cruise,home.z),.4,true))phase(Phase.APPROACH);}
            case APPROACH -> {if(fly(new Vec3(landing.x,cruise,landing.z),HelicopterFlight.MAX_SPEED,true))phase(Phase.DESCEND);}
            case DESCEND -> {
                if(phaseAge%20==1&&!(deployment.enabled()?deployment.safeSite(landing):safeLanding(landing))){phase(Phase.DEPART);break;}
                Vec3 delivery=deployment.enabled()?deployment.hover(landing):landing;
                if(fly(delivery,.35,deployment.enabled())){setDeltaMovement(Vec3.ZERO);phase(Phase.UNLOAD);playSound(VehicleSounds.event("helicopter_door"),1.4F,1);}
            }
            case UNLOAD -> {
                setDeltaMovement(Vec3.ZERO);
                if(deployment.enabled()){
                    if(position().distanceTo(deployment.hover(landing))>.4||!clearAt(position(),getYRot())){phase(Phase.DEPART);break;}
                    if(deployment.unload())phase(Phase.DEPART);break;
                }
                if(!safeLanding(landing)||position().distanceTo(landing)>.35){phase(Phase.DEPART);break;}
                if(phaseAge>=30&&phaseAge%15==0)unloadOne();
                if(getPassengers().stream().noneMatch(p->p.getPersistentData().getBoolean("HelicopterTroop"))||phaseAge>160)phase(Phase.DEPART);
            }
            case DEPART -> {if(fly(new Vec3(getX(),cruise,getZ()),.4,true))phase(combat.start()?Phase.PURSUIT:Phase.RETURN);}
            case PURSUIT,STRAFE -> {if(!deployment.repeat())combat.tick();}
            case RETURN -> {
                if(fly(new Vec3(home.x,cruise,home.z),HelicopterFlight.MAX_SPEED,true)&&phaseAge>80){
                    // The dispatched machine and its remaining crew leave together; delivered troops remain real entities.
                    for(var crew:List.copyOf(getPassengers()))crew.discard();discard();
                }
            }
            default -> {}
        }
        if(blockedTicks>80&&phase()!=Phase.RETURN){if(phase()==Phase.PURSUIT||phase()==Phase.STRAFE)endCombat();else phase(Phase.DEPART);}
        if(!deployment.enabled()&&missionAge>2400&&phase()!=Phase.RETURN&&phase()!=Phase.DEPART)phase(Phase.DEPART);
        positionPassengers();
        }finally{
            // Measure actual physics results even when pilot/combat paths return early.
            // No sound control is used to drive movement or player input.
            if(destructionEpoch==audioEpoch){
                Vec3 moved=position().subtract(audioStart);
                var sound=com.codex.blacklight.military.aircraft.HelicopterAudioState.controls(audioRunning(),rotor(),moved.length(),getDeltaMovement().subtract(audioBefore).length(),moved.y);
                entityData.set(AUDIO_VELOCITY,sound.velocity());entityData.set(AUDIO_THROTTLE,sound.throttle());
            }
        }
    }
    private void positionPassengers(){for(var p:getPassengers())positionRider(p);}
    private void unloadOne(){
        for(var passenger:List.copyOf(getPassengers()))if(passenger instanceof MilitarySoldier troop&&troop.getPersistentData().getBoolean("HelicopterTroop")){
            // A rotated hull has a wider world collision envelope. Keep the preferred doorway exit,
            // then try supported points farther out so delivered soldiers cannot block departure.
            for(double distance:new double[]{3.8,4.6,5.4,6.2,7.0,7.8,8.6})for(int side:new int[]{-1,1})for(double dz:new double[]{0,-1.3,1.3}){
                Vec3 candidate=local(side*distance,.05,dz);var feet=BlockPos.containing(candidate.add(0,-.1,0));
                AABB box=troop.getDimensions(troop.getPose()).makeBoundingBox(candidate);
                if(box.intersects(envelope(position(),getYRot(),false))||box.intersects(envelope(position(),getYRot(),true))
                        ||!VehicleKinematics.loaded(level(),box)||!level().getWorldBorder().isWithinBounds(box)
                        ||!level().getBlockState(feet).isFaceSturdy(level(),feet,Direction.UP)||!level().getFluidState(feet).isEmpty()
                        ||!level().noCollision(troop,box)||!level().getEntitiesOfClass(LivingEntity.class,box.inflate(.05),e->e!=troop&&e.isAlive()).isEmpty())continue;
                troop.stopRiding();troop.setPos(candidate);troop.setDeltaMovement(Vec3.ZERO);troop.fallDistance=0;
                troop.getPersistentData().remove("HelicopterTroop");troop.adoptFieldPost();troop.setAutomaticUnitJoin(true);combat.assignDeliveredTroop(troop);return;
            }
            return;
        }
    }
    public Vec3 local(double x,double y,double z){return new Vec3(x,y,z).yRot((float)Math.toRadians(getYRot())).add(position());}
    @Override protected boolean canAddPassenger(Entity passenger){
        if(health()<=0)return false;if(pilot.hijack().actor(passenger.getUUID()))return true;
        return getPassengers().size()<6&&(passenger instanceof MilitarySoldier||passenger instanceof net.minecraft.world.entity.player.Player&&getPassengers().stream().noneMatch(MilitarySoldier.class::isInstance));
    }
    @Override protected void addPassenger(Entity passenger){super.addPassenger(passenger);if(!level().isClientSide)pilot.reconcile();}
    @Override protected void removePassenger(Entity passenger){super.removePassenger(passenger);if(!level().isClientSide)pilot.reconcile();}
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger){
        // At diagonal headings a fixed local side offset can remain inside the
        // world collision box. Falling back to the roof then traps the rider in the rotor.
        for(double distance:new double[]{4.1,5.3,6.5,7.7,9,11,13,15})for(int side:new int[]{-1,1})for(double z:new double[]{2.1,0,-2}){
            var at=local(side*distance,.05,z);var box=passenger.getDimensions(passenger.getPose()).makeBoundingBox(at);
            if(outsideDescentColumn(box)&&exitClear(passenger,box))return at;
        }
        var above=new Vec3(getX(),envelope(position(),getYRot(),true).maxY+.05,getZ());
        for(int i=0;i<4;i++){
            var at=above.add(0,i,0);if(exitClear(passenger,passenger.getDimensions(passenger.getPose()).makeBoundingBox(at)))return at;
        }
        return super.getDismountLocationForPassenger(passenger);
    }
    private boolean outsideDescentColumn(AABB box){
        // The departing rider may hover in creative flight. A side exit below
        // the rotor still blocks automatic descent unless it clears its XZ footprint.
        var rotor=envelope(position(),getYRot(),true);
        return box.maxX<=rotor.minX||box.minX>=rotor.maxX||box.maxZ<=rotor.minZ||box.minZ>=rotor.maxZ;
    }
    private boolean exitClear(LivingEntity passenger,AABB box){
        return !box.intersects(envelope(position(),getYRot(),false))&&!box.intersects(envelope(position(),getYRot(),true))
                &&VehicleKinematics.loaded(level(),box)&&level().getWorldBorder().isWithinBounds(box)&&level().noCollision(passenger,box);
    }
    @Override public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand){
        var supply=com.codex.blacklight.military.aircraft.HelicopterSupplies.supply(player,this,hand);
        if(supply!=net.minecraft.world.InteractionResult.PASS)return supply;
        if(hand!=net.minecraft.world.InteractionHand.MAIN_HAND||health()<=0||!player.getMainHandItem().isEmpty())return net.minecraft.world.InteractionResult.PASS;
        if(level().isClientSide)return net.minecraft.world.InteractionResult.SUCCESS;
        if(!(player instanceof net.minecraft.server.level.ServerPlayer server)||!level().mayInteract(player,blockPosition()))return net.minecraft.world.InteractionResult.FAIL;
        if(player.getVehicle()!=this&&getPassengers().stream().anyMatch(MilitarySoldier.class::isInstance))return pilot.hijack().request(server)?net.minecraft.world.InteractionResult.CONSUME:net.minecraft.world.InteractionResult.FAIL;
        if(player.getVehicle()==this)return pilot.switchSeat(server)?net.minecraft.world.InteractionResult.CONSUME:net.minecraft.world.InteractionResult.FAIL;
        Vec3 port=local(-1.8,1.6,2.1),other=local(1.8,1.6,2.1);if(player.getEyePosition().distanceToSqr(other)<player.getEyePosition().distanceToSqr(port))port=other;
        if(player.getEyePosition().distanceToSqr(port)>3.5*3.5||level().clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(),port,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player)).getType()!=HitResult.Type.MISS)return net.minecraft.world.InteractionResult.FAIL;
        return player.startRiding(this)?net.minecraft.world.InteractionResult.CONSUME:net.minecraft.world.InteractionResult.FAIL;
    }
    @Override protected void positionRider(Entity passenger,Entity.MoveFunction callback){
        if(!hasPassenger(passenger))return;
        Vec3 point=pilot.seatPoint(passenger);
        callback.accept(passenger,point.x,point.y,point.z);
        if(passenger instanceof LivingEntity living){living.setYBodyRot(-getYRot());living.yBodyRotO=-getYRot();}
    }
    @Override public LivingEntity getControllingPassenger(){return null;}
    @Override public boolean isPickable(){return !isRemoved();}
    @Override public boolean canBeCollidedWith(){return !isRemoved();}
    @Override public boolean canCollideWith(Entity other){return !isPassengerOfSameVehicle(other)&&other.canBeCollidedWith();}
    @Override public boolean isPushable(){return false;}
    @Override public void push(double x,double y,double z){/* Flight and crash movement use the airframe sweep, like other mechanical vehicles. */}
    @Override public boolean hurt(DamageSource source,float amount){
        if(level().isClientSide||isRemoved()||crashExploded||health()<=0||isInvulnerableTo(source)||!Float.isFinite(amount)||amount<=0||source.getEntity()!=null&&hasPassenger(source.getEntity()))return false;
        if(source.getEntity() instanceof LivingEntity actor&&com.codex.blacklight.military.vehicle.VehicleDamageBridge.protectedAircraft(actor,this))return false;
        entityData.set(HEALTH,Math.max(0,health()-amount));markHurt();
        if(health()<=0){phase(Phase.CRASH);playSound(VehicleSounds.event("helicopter_alarm"),2,1);}
        return true;
    }
    private boolean crashExploded;
    private long destructionEpoch;
    private void crash(){
        if(crashExploded||isRemoved())return;
        // The existing 15-second MC fallback must also finish a crash whose
        // next movement step is refused at an unloaded/world-height boundary.
        if(phaseAge>300){finishCrash();return;}
        Vec3 step=getDeltaMovement().multiply(.98,1,.98).add(0,-.035,0);if(step.y<-.8)step=new Vec3(step.x,-.8,step.z);
        if(!VehicleKinematics.loaded(level(),getBoundingBox().expandTowards(step))){setDeltaMovement(Vec3.ZERO);return;}
        setDeltaMovement(step);move(MoverType.SELF,step);entityData.set(BANK,Math.min(35,bank()+.6F));
        if(level() instanceof ServerLevel server&&tickCount%3==0)server.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,getX(),getY()+3,getZ(),3,.8,.3,.8,.03);
        if(onGround()||verticalCollision)finishCrash();
    }
    private void finishCrash(){
        if(crashExploded||isRemoved())return;
        crashExploded=true;long epoch=++destructionEpoch;entityData.set(HEALTH,0F);
        // Vanilla radius-three explosions inspect an approximately six-block
        // neighborhood. At an unloaded boundary retain authored wreck/FX/audio
        // but omit physical blast queries rather than synchronously load terrain.
        Vec3 at=position().add(0,1,0);
        boolean physicalBlast=VehicleKinematics.loaded(level(),new AABB(at,at).inflate(7));
        com.codex.blacklight.military.vehicle.VehicleDestructionEffects.emit(this,
                com.codex.blacklight.military.vehicle.VehicleDestruction.Chassis.HELICOPTER,true,gunYaw(),gunPitch(),physicalBlast);
        if(!currentDestruction(epoch))return;
        for(var passenger:List.copyOf(getPassengers())){
            passenger.stopRiding();if(!currentDestruction(epoch))return;
            passenger.hurt(damageSources().fall(),24);if(!currentDestruction(epoch))return;
        }
        if(physicalBlast){
            // Dismount/damage callbacks may change chunk availability too.
            Vec3 blast=position().add(0,1,0);
            if(VehicleKinematics.loaded(level(),new AABB(blast,blast).inflate(7)))
                level().explode(this,blast.x,blast.y,blast.z,3,false,Level.ExplosionInteraction.NONE);
            else if(level() instanceof ServerLevel server)com.codex.blacklight.firearms.GunplaySounds.impact(server,blast,5);
            if(!currentDestruction(epoch))return;
        }
        discard();
    }
    private boolean currentDestruction(long epoch){return crashExploded&&destructionEpoch==epoch&&!isRemoved();}
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps,boolean teleport){lerpX=x;lerpY=y;lerpZ=z;lerpYaw=yaw;lerpSteps=Math.max(1,steps);}
    @Override protected void addAdditionalSaveData(CompoundTag tag){
        combat.save(tag);pilot.save(tag);deployment.save(tag);
        tag.putBoolean("CrashExplosionCommitted",crashExploded);
        tag.putFloat("MechanicalHealth",health());tag.putInt("FlightPhase",phase().ordinal());tag.putInt("PhaseAge",phaseAge);tag.putInt("MissionAge",missionAge);
        if(home!=null){tag.putDouble("HomeX",home.x);tag.putDouble("HomeY",home.y);tag.putDouble("HomeZ",home.z);}
        if(landing!=null){tag.putDouble("LandingX",landing.x);tag.putDouble("LandingY",landing.y);tag.putDouble("LandingZ",landing.z);}
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag){
        destructionEpoch++;
        navigation.reset();combat.load(tag);pilot.load(tag);deployment.load(tag);
        float health=tag.contains("MechanicalHealth")?tag.getFloat("MechanicalHealth"):360;entityData.set(HEALTH,Float.isFinite(health)?Mth.clamp(health,0,360):360F);
        crashExploded=tag.getBoolean("CrashExplosionCommitted");if(crashExploded)entityData.set(HEALTH,0F);
        home=readPoint(tag,"Home");landing=readPoint(tag,"Landing");
        int phase=tag.getInt("FlightPhase");entityData.set(PHASE,phase>=0&&phase<Phase.values().length?phase:0);
        phaseAge=Mth.clamp(tag.getInt("PhaseAge"),0,2400);missionAge=Mth.clamp(tag.getInt("MissionAge"),0,2400);
        if(health()<=0||phase()==Phase.CRASH)phase(Phase.CRASH);else if(phase()==Phase.HIJACK)phase(pilot.owned()?Phase.MANUAL:home!=null&&landing!=null?Phase.DEPART:Phase.IDLE);else if(!pilot.owned()&&(home==null||landing==null))phase(Phase.IDLE);
        // Migrate only dispatched legacy support, never player-owned or explicit bare deployTo missions.
        if(!tag.contains("AirDeployment")&&tag.getCompound("AirCombat").getBoolean("Enabled")&&!pilot.owned()&&health()>0){
            deployment.enable();if(phase()==Phase.DESCEND||phase()==Phase.UNLOAD)phase(Phase.APPROACH);
        }
        entityData.set(ROTOR,phase()==Phase.IDLE?0F:1F);setNoGravity(true);
    }
    private static Vec3 readPoint(CompoundTag tag,String prefix){
        if(!tag.contains(prefix+"X")||!tag.contains(prefix+"Y")||!tag.contains(prefix+"Z"))return null;
        Vec3 value=new Vec3(tag.getDouble(prefix+"X"),tag.getDouble(prefix+"Y"),tag.getDouble(prefix+"Z"));
        return HelicopterFlight.finite(value)&&Math.abs(value.x)<=30000000&&Math.abs(value.z)<=30000000&&Math.abs(value.y)<=4096?value:null;
    }
}
