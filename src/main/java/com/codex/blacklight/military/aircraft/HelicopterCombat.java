package com.codex.blacklight.military.aircraft;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.military.vehicle.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Escort sortie with original AI infinite-ammo policy and separate finite player stores. */
public final class HelicopterCombat {
    private static final double WEAPON_RANGE=Math.max(VehicleWeaponDefinition.require("helicopter_30mm").weapon().range(),
            Math.max(VehicleWeaponDefinition.require("helicopter_hellfire").weapon().range(),VehicleWeaponDefinition.require("helicopter_70mm").weapon().range()));
    /** MC observation/mission leash covers every installed weapon plus the real chin-muzzle offset.
     * The P1 source acquisition radius is not established by its separate firing/movement gates. */
    private static final double OBSERVATION_RANGE=WEAPON_RANGE+HelicopterArmament.GUN.length()+1.2;
    public static double weaponRange(){return WEAPON_RANGE;}
    private final BlackwatchHelicopter craft;
    private boolean enabled;
    private UUID designated,targetId;
    private Vec3 lastSeen,hoverPoint,lastBase;
    private boolean targetHydra,targetHelicopter,targetTank;
    private final HelicopterTactics tactics;
    private int rounds=240,rockets=8,gunWait,rocketWait,unseen,age,shots,gunShots,rocketShots;
    private final HelicopterGunCycle gunCycle=new HelicopterGunCycle();
    private final HelicopterHellfireAttack hellfireAttack=new HelicopterHellfireAttack();
    private final HelicopterRapidMagazine rapid=new HelicopterRapidMagazine();
    private final HelicopterCircleStrafe circle=new HelicopterCircleStrafe();
    private boolean rapidSelected,switchHeld;
    private UUID manualOperator;
    public HelicopterCombat(BlackwatchHelicopter craft){this.craft=craft;tactics=new HelicopterTactics(craft,circle,hellfireAttack);}
    public String tactic(){return tactics.name();}
    public String maneuverStage(){return tactics.stage();}
    public Vec3 combatDestination(){return tactics.destination();}
    public int rounds(){return rounds;}public int rockets(){return rockets;}public int shots(){return shots;}
    /** MC logistics only; AI keeps its source infinite-ammo firing policy. */
    public int addPlayerAmmunition(String id,int offered){
        if(craft.level().isClientSide||craft.isRemoved()||craft.health()<=0||offered<=0)return 0;
        if(id.equals(HelicopterSupplies.CANNON)){int taken=Math.min(offered,240-rounds);rounds+=taken;return taken;}
        if(id.equals(HelicopterSupplies.HELLFIRE)){int taken=Math.min(offered,8-rockets);rockets+=taken;return taken;}
        return id.equals(HelicopterSupplies.RAPID)?rapid.add(offered):0;
    }
    /** Clear finite stores without changing the one-time player grant or NPC policy. */
    public void emptyPlayerStores(){if(!craft.level().isClientSide){rounds=rockets=0;rapid.clear();}}
    /** Requested MC takeover loadout; normal reloads still transfer finite items. */
    void fillPlayerStores(){if(!craft.level().isClientSide){rounds=240;rockets=8;rapid.add(HelicopterRapidMagazine.CAPACITY*2);}}
    public int gunShots(){return gunShots;}public int rocketShots(){return rocketShots;}
    public HelicopterRapidMagazine rapid(){return rapid;}public boolean rapidSelected(){return rapidSelected;}
    public UUID targetId(){return targetId;}
    public Vec3 lastObserved(){return lastSeen;}
    public HelicopterHellfireAttack hellfireAttack(){return hellfireAttack;}
    public Vec3 hoverPoint(){return hoverPoint;}
    public HelicopterCircleStrafe circle(){return circle;}
    public MilitarySoldier operator(){
        return craft.getPassengers().stream().filter(p->p instanceof MilitarySoldier&&!p.getPersistentData().getBoolean("HelicopterTroop")&&MilitaryVehicle.crewOperational(p))
                .map(p->(MilitarySoldier)p).findFirst().orElse(null);
    }
    public void supportCall(MilitarySoldier caller){
        if(craft.level().isClientSide||caller==null||caller.level()!=craft.level()||!caller.tacticsAvailable())return;
        beginSupport();var target=caller.getTarget();designated=caller.validTarget(target)?target.getUUID():null;
    }
    /** UAV report carries a confirmed target, not a fake human officer or a through-wall live tracker. */
    public boolean supportScan(MilitaryUav scanner,LivingEntity target){
        if(scanner==null||scanner.level()!=craft.level()||!scanner.confirmedForSupport(target)||operator()==null||!operator().validTarget(target))return false;
        beginSupport();designated=target.getUUID();return true;
    }
    private void beginSupport(){if(!enabled){age=unseen=0;targetId=null;lastSeen=hoverPoint=lastBase=null;tactics.reset();circle.reset();hellfireAttack.reset();gunCycle.reset();}enabled=true;}
    public boolean start(){return enabled&&operator()!=null&&craft.health()>120;}
    public void finish(){enabled=false;targetId=null;interrupted();circle.reset();hellfireAttack.reset();}
    /** Phase exit, hijack, death and unload release world-owned coordination. */
    public void interrupted(){tactics.reset();hoverPoint=null;hellfireAttack.abort();gunCycle.reset();}
    public void assignDeliveredTroop(MilitarySoldier troop){
        if(!(craft.level() instanceof ServerLevel level)||!enabled||!troop.tacticsAvailable())return;
        UUID id=targetId==null?designated:targetId;
        if(id!=null&&level.getEntity(id) instanceof LivingEntity target&&troop.validTarget(target)&&troop.hasLineOfSight(target)
                &&BlackwatchSquadSystem.withinTerritory(troop,target.position()))troop.setTarget(target);
    }
    public boolean hostile(LivingEntity target){
        var pilot=operator();return pilot!=null&&pilot.validTarget(target)&&!craft.hasPassenger(target)
                &&(BlacklightInfectionSystem.isInfected(target)||target.getUUID().equals(designated));
    }
    /** Share the caller's opponent with this aircraft's actual weapon operator.
     * This is impact allegiance, not permission to fire. A rocket may arrive after
     * finish() ends the sortie; a new support call still replaces/revokes the designation. */
    public boolean designatesTargetFor(MilitarySoldier actor,LivingEntity target){
        return !craft.isRemoved()&&craft.health()>0&&actor==operator()
                &&target!=null&&target.getUUID().equals(designated)&&hostile(target);
    }
    private static Entity targetBody(LivingEntity target){
        var root=target.getRootVehicle();return root instanceof MilitaryVehicle||root instanceof BlackwatchHelicopter?root:target;
    }
    private Vec3 targetPoint(LivingEntity target){
        var body=targetBody(target);
        if(body==target&&CombatTargeting.multipart(target))return CombatTargeting.closestPoint(craft.weaponPoint(HelicopterArmament.GUN),target);
        return body instanceof MilitaryVehicle vehicle?vehicle.position().add(0,vehicle.definition().height()*.45,0):body.getBoundingBox().getCenter();
    }
    private boolean hostileHull(Entity body){
        if(body==craft||body.isRemoved()||!body.isAlive())return false;
        var passengers=java.util.stream.StreamSupport.stream(body.getIndirectPassengers().spliterator(),false).filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).toList();
        if(passengers.isEmpty()||passengers.stream().anyMatch(p->!hostile(p)))return false;
        var actor=operator();return body instanceof MilitaryVehicle vehicle?vehicle.health()>0&&!VehicleDamageBridge.protectedHull(actor,vehicle)
                :body instanceof BlackwatchHelicopter helicopter&&helicopter.health()>0&&!VehicleDamageBridge.protectedAircraft(actor,helicopter);
    }
    private boolean visible(LivingEntity target){
        Vec3 from=craft.weaponPoint(HelicopterArmament.GUN),to=targetPoint(target);
        return HelicopterShotPath.loaded((ServerLevel)craft.level(),from,to)
                &&craft.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,craft)).getType()==HitResult.Type.MISS;
    }
    public void tick(){
        if(!(craft.level() instanceof ServerLevel level))return;
        cooldown();gunCycle.tick();age++;
        if(!start()||age>2400&&!craft.deployment().enabled()){craft.endCombat();return;}
        LivingEntity target=targetId!=null&&level.getEntity(targetId) instanceof LivingEntity living?living:null;
        if(target!=null&&(!hostile(target)||target.distanceToSqr(craft)>OBSERVATION_RANGE*OBSERVATION_RANGE||craft.landing()==null||target.position().distanceToSqr(craft.landing())>OBSERVATION_RANGE*OBSERVATION_RANGE)){target=null;targetId=null;lastSeen=lastBase=null;interrupted();}
        if(target==null&&age%10==1){
            target=level.getEntitiesOfClass(LivingEntity.class,craft.getBoundingBox().inflate(OBSERVATION_RANGE),this::hostile).stream()
                    .filter(candidate->candidate.distanceToSqr(craft)<=OBSERVATION_RANGE*OBSERVATION_RANGE).filter(this::visible).min(Comparator.comparingDouble(craft::distanceToSqr)).orElse(null);
            if(target!=null){targetId=target.getUUID();interrupted();}
        }
        boolean seen=target!=null&&visible(target);
        if(seen){
            var body=targetBody(target);lastSeen=targetPoint(target);lastBase=body.position();targetHydra=target instanceof BlacklightHydra;
            targetHelicopter=body instanceof BlackwatchHelicopter;targetTank=body instanceof MilitaryVehicle vehicle&&vehicle.chassisId().equals("tank");unseen=0;
        }else unseen++;
        hellfireAttack.tick(seen,seen&&HelicopterHellfireAttack.aimed(HelicopterArmament.direction(craft.getYRot(),0),lastSeen.subtract(craft.position())));
        if(!hellfireAttack.hovering())hoverPoint=null;
        if(unseen>100){craft.endCombat();return;}
        if(lastSeen==null){craft.setDeltaMovement(Vec3.ZERO);return;}
        // The source bank owns maneuver selection. Blackwatch maps to the
        // nonzero variety branch; line-of-fire still uses the observed skin point.
        // MC splash protection can make a native hover opportunity impossible
        // (notably beside troops just delivered by this aircraft). Keep the gun
        // maneuver instead of knowingly spending the whole hover without a shot.
        boolean hoverEligible=seen&&rocketWait==0&&(!hellfireAttack.ready()||missileOpportunity(target));
        boolean gunAllowed=tactics.tick(level,lastBase,lastSeen,seen,targetHydra,targetHelicopter,targetTank,hoverEligible);
        if(hellfireAttack.hovering()){if(hoverPoint==null)hoverPoint=craft.position();}else hoverPoint=null;
        craft.aimWeapon(lastSeen);
        if(seen&&craft.phase()==BlackwatchHelicopter.Phase.STRAFE){
            // startup_tod: heli_gunship_*_user_ai.infiniteAmmo=true. AI firing does not
            // consume/replenish player stores, including after a save/load or hijack.
            if(hellfireAttack.wantsShot()&&rocketWait==0&&fire(target,true)){rocketWait=20;recordShot(true);hellfireAttack.shot();}
            if(gunAllowed&&!hellfireAttack.hovering()&&gunWait==0&&gunCycle.ready()&&fire(target,false)){recordShot(false);gunCycle.shot();gunWait=4;}
        }
    }
    public void cooldown(){if(gunWait>0)gunWait--;if(rocketWait>0)rocketWait--;rapid.tick();}
    public void manual(net.minecraft.server.level.ServerPlayer player,VehicleControlAuthority.State input){
        cooldown();
        if(craft.level().isClientSide||player==null||player.getVehicle()!=craft||craft.pilot().weaponOperator()!=player||!MilitaryVehicle.crewOperational(player)
                ||craft.phase()!=BlackwatchHelicopter.Phase.MANUAL||craft.rotor()<.9F||craft.health()<=0)return;
        boolean switching=(input.actions()&VehicleControlAuthority.NEXT_AMMUNITION)!=0;
        if(!player.getUUID().equals(manualOperator)){manualOperator=player.getUUID();switchHeld=false;}
        if(switching&&!switchHeld)rapidSelected=!rapidSelected;switchHeld=switching;
        var pivot=craft.weaponPoint(HelicopterArmament.GUN);
        var freeAim=pivot.add(Vec3.directionFromRotation((float)input.aimPitch(),(float)input.aimYaw()).scale(weaponRange()));
        var selection=input.brake()?null:CombatTargeting.lockedSelection(player);
        var aim=HelicopterManualAim.resolve(player,craft,selection,freeAim,weaponRange());
        if(!input.brake())craft.aimWeapon(aim.point(),true);
        var lock=aim.locked()?selection:null;
        if(input.primaryFire()&&rounds>0&&gunWait==0&&manualFire(player,false,lock,aim.point())){rounds--;gunWait=4;recordShot(false);}
        if(input.secondaryFire()){
            if(rapidSelected){int side=rapid.readySide();if(side!=0&&manualRapid(player,aim,side))rapid.fired(side);}
            else if(rockets>0&&rocketWait==0&&manualFire(player,true,lock,aim.point())){rockets--;rocketWait=60;recordShot(true);}
        }
    }
    private boolean manualRapid(net.minecraft.server.level.ServerPlayer player,HelicopterManualAim.Solution solution,int side){
        var level=player.serverLevel();Vec3 pivot=HelicopterHellfireRack.rapidPod(side),breech=craft.weaponPoint(pivot);
        Vec3 muzzle=craft.weaponPoint(pivot.add(0,0,.748163));
        if(!VehicleDamageBridge.loaded(level,breech,muzzle)||level.clip(new ClipContext(breech,muzzle,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,craft)).getType()!=HitResult.Type.MISS)return false;
        Vec3 target=solution.point();
        var aim=HelicopterArmament.aim(target.subtract(muzzle),craft.getYRot(),craft.bank());
        Vec3 direction=solution.locked()?target.subtract(muzzle).normalize():HelicopterArmament.rotate(HelicopterArmament.direction(Math.max(-10,Math.min(10,aim.yaw())),Math.max(-60,Math.min(35,aim.pitch()))),craft.getYRot(),craft.bank());
        var projectile=new VehicleOrdnanceProjectile(level,player,craft,"helicopter_70mm");projectile.setPos(muzzle);projectile.launchRapid(direction,craft.getDeltaMovement());
        if(!level.addFreshEntity(projectile))return false;
        GunplayEffects.shot(player,craft,VehicleWeaponDefinition.require("helicopter_70mm").weapon(),muzzle,muzzle.add(direction.scale(12)),0,direction.scale(-1));craft.recordAirShot();return true;
    }
    private void recordShot(boolean rocket){
        if(shots<Integer.MAX_VALUE)shots++;
        if(rocket){if(rocketShots<Integer.MAX_VALUE)rocketShots++;}else if(gunShots<Integer.MAX_VALUE)gunShots++;
    }
    private boolean manualFire(net.minecraft.server.level.ServerPlayer player,boolean rocket,TargetSelection lock,Vec3 point){
        var level=player.serverLevel();var definition=VehicleWeaponDefinition.require(rocket?"helicopter_hellfire":"helicopter_30mm").weapon();int side=rockets%2==0?-1:1;
        var aim=rocket?craft.podAim(side):new HelicopterArmament.Aim(craft.gunYaw(),craft.gunPitch());
        Vec3 direction=HelicopterArmament.rotate(HelicopterArmament.direction(aim.yaw(),aim.pitch()),craft.getYRot(),craft.bank());
        Vec3 breech=craft.weaponPoint(rocket?HelicopterHellfireRack.mount(side):HelicopterArmament.GUN);
        Vec3 muzzle=rocket?craft.hellfireLaunchPoint(side,(8-rockets)/2%4):breech.add(direction.scale(1.2));
        if(!VehicleDamageBridge.loaded(level,breech,muzzle)||level.clip(new ClipContext(breech,muzzle,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()!=HitResult.Type.MISS)return false;
        if(!rocket&&lock!=null){
            var toTarget=point.subtract(breech);
            if(toTarget.lengthSqr()>definition.range()*definition.range()||direction.dot(toTarget.normalize())<.99999)return false;
        }
        if(rocket){
            Vec3 end=HelicopterShotPath.loadedEnd(level,muzzle,muzzle.add(direction.scale(definition.range())));
            var wall=level.clip(new ClipContext(muzzle,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,craft));if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
            var hull=VehicleDamageBridge.nearest(level,craft,null,muzzle,end);
            var hit=FirearmsCombat.firstSurface(level,craft,muzzle,end,e->!craft.hasPassenger(e)&&e.isAlive()&&e.isPickable()&&VehicleDamageBridge.vehicle(e)==null);
            Entity tracked=hull!=null&&(hit==null||hull.distance()<=muzzle.distanceTo(hit.getLocation()))?hull.vehicle():hit==null?null:hit.getEntity();
            // Multipart pick proxies are transient and cannot be resolved by UUID
            // on the following flight tick. Preserve the actual ray's aim point,
            // but track the same persistent living body used by AI targeting.
            var trackedBody=CombatTargeting.resolveTarget(tracked);if(trackedBody!=null)tracked=trackedBody;
            if(lock!=null&&point.distanceTo(muzzle)<=definition.range()&&HelicopterShotPath.loaded(level,muzzle,point)
                    &&level.clip(new ClipContext(muzzle,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,craft)).getType()==HitResult.Type.MISS){
                end=point;tracked=lock.target();
            }
            var launch=lock!=null&&tracked==lock.target()?end.subtract(muzzle).normalize():
                    HelicopterHellfireRack.launchDirection(end.subtract(muzzle),craft.getYRot(),craft.bank(),true);
            var projectile=new VehicleOrdnanceProjectile(level,player,craft,"helicopter_hellfire");projectile.setPos(muzzle);projectile.launchHellfire(launch,craft.getDeltaMovement(),tracked,end,true);
            if(!level.addFreshEntity(projectile))return false;
            GunplayEffects.shot(player,craft,definition,muzzle,muzzle.add(launch.scale(12)),0,launch.scale(-1));
        }else{
            Vec3 end=HelicopterShotPath.loadedEnd(level,muzzle,muzzle.add(direction.scale(definition.range())));
            var wall=level.clip(new ClipContext(muzzle,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
            var mechanical=VehicleDamageBridge.nearest(level,craft,null,muzzle,end);
            var hit=FirearmsCombat.firstBulletBody(level,player,craft,muzzle,end,e->e.getRootVehicle()!=craft&&VehicleDamageBridge.vehicle(e)==null);
            int impact=0;Vec3 normal=direction.scale(-1);
            if(mechanical!=null&&(hit==null||mechanical.distance()<muzzle.distanceTo(hit.getLocation()))){end=mechanical.point();VehicleDamageBridge.hurt(player,mechanical.vehicle(),definition,definition.damage(),null,end,direction);impact=3;}
            else if(hit!=null){
                end=hit.getLocation();impact=2;
                var target=CombatTargeting.resolveTarget(hit.getEntity());
                if(hit.getEntity() instanceof BlackwatchHelicopter helicopter){
                    impact=3;
                    helicopter.hurtAt(player.damageSources().mobProjectile(craft,player),definition.damage(),end);
                }
                else if(target!=null&&!FirearmsCombat.protectedTarget(player,target)
                        &&(!(target instanceof net.minecraft.world.entity.player.Player other)||level.getServer().isPvpAllowed()&&player.canHarmPlayer(other)))
                    hurtGunContact(player,hit.getEntity(),target,muzzle,end,definition.damage());
            }else if(wall.getType()==HitResult.Type.BLOCK){impact=1;normal=Vec3.atLowerCornerOf(wall.getDirection().getNormal());FirearmBlockDamage.bullet(player,definition,wall);}
            GunplayEffects.shot(player,craft,definition,muzzle,end,impact,normal);
        }
        craft.recordAirShot();return true;
    }
    /** Geometry, crew, phase, allegiance and first obstruction are checked at the AI firing boundary. */
    private boolean fire(LivingEntity target,boolean rocket){
        if(!(craft.level() instanceof ServerLevel level)||craft.phase()!=BlackwatchHelicopter.Phase.STRAFE||!hostile(target)||craft.rotor()<.9F||craft.health()<=120)return false;
        var body=targetBody(target);
        if(!rocket&&!HelicopterGunEnvelope.permits(tactics.gunEnvelope(),craft.position(),body.position(),craft.getYRot(),craft.bank(),body instanceof BlackwatchHelicopter))return false;
        var actor=operator();var definition=VehicleWeaponDefinition.require(rocket?"helicopter_hellfire":"helicopter_30mm").weapon();
        int side=rocketShots%2==0?-1:1;
        Vec3 pivot=rocket?HelicopterHellfireRack.mount(side):HelicopterArmament.GUN;
        var aim=rocket?craft.podAim(side):new HelicopterArmament.Aim(craft.gunYaw(),craft.gunPitch());
        Vec3 direction=HelicopterArmament.rotate(HelicopterArmament.direction(aim.yaw(),aim.pitch()),craft.getYRot(),craft.bank());
        Vec3 breech=craft.weaponPoint(pivot),muzzle=rocket?craft.hellfireLaunchPoint(side,rocketShots/2%4):breech.add(direction.scale(1.2));
        Vec3 targetPoint=targetPoint(target),delta=targetPoint.subtract(muzzle);
        // MC muzzle safety/range is an additional constraint, in blocks, not
        // the native actor-local XZ envelope (ordinary60 / strafe100 above).
        if(delta.length()>definition.range()||delta.length()< (rocket?14:8)||!rocket&&direction.dot(delta.normalize())<.9995)return false;
        if(rocket)direction=HelicopterHellfireRack.launchDirection(delta,craft.getYRot(),craft.bank(),false);
        if(!HelicopterShotPath.loaded(level,breech,targetPoint)
                ||level.clip(new ClipContext(breech,targetPoint,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,craft)).getType()!=HitResult.Type.MISS)return false;
        // Guided launch heading is distinct from the observation ray used to
        // validate the intended target. Native flight still collides with walls.
        Vec3 sightDirection=rocket?delta.normalize():direction;
        Vec3 end=HelicopterShotPath.loadedEnd(level,muzzle,muzzle.add(sightDirection.scale(Math.min(definition.range(),delta.length()+2))));
        var wall=level.clip(new ClipContext(muzzle,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,craft));
        if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
        var mechanical=VehicleDamageBridge.nearest(level,craft,null,muzzle,end);
        // Ground hulls use their oriented shape rather than their broad outer AABB.
        // Seated occupants are protected by the first real hull contact.
        var hit=FirearmsCombat.firstSurface(level,craft,muzzle,end,e->!craft.hasPassenger(e)&&e.isPickable()&&e.isAlive()
                &&VehicleDamageBridge.vehicle(e)==null&&(!(e instanceof LivingEntity living)||targetBody(living)==living));
        boolean groundFirst=mechanical!=null&&(hit==null||mechanical.distance()<=muzzle.distanceTo(hit.getLocation()));
        Entity first=groundFirst?mechanical.vehicle():hit==null?null:hit.getEntity();
        if(first==null)return false;
        boolean hullHit=first instanceof MilitaryVehicle||first instanceof BlackwatchHelicopter;
        LivingEntity victim=CombatTargeting.resolveTarget(first);
        if(hullHit){if(first!=targetBody(target)||!hostileHull(first))return false;}
        else if(victim==null||!hostile(victim))return false;
        Vec3 impact=groundFirst?mechanical.point():hit.getLocation();
        if(rocket){
            // Suppress explosive fire near anyone outside this legitimate enemy set, including delivered troops.
            if(!safeExplosiveImpact(level,impact))return false;
            var projectile=new VehicleOrdnanceProjectile(level,actor,craft,"helicopter_hellfire");projectile.setPos(muzzle);projectile.launchHellfire(direction,craft.getDeltaMovement(),target,targetPoint,false);
            if(!level.addFreshEntity(projectile))return false;
            GunplayEffects.shot(actor,craft,definition,muzzle,muzzle.add(direction.scale(12)),0,direction.scale(-1));
        }else{
            if(first instanceof MilitaryVehicle vehicle)VehicleDamageBridge.hurt(actor,vehicle,definition,definition.damage(),null,impact,direction);
            else if(first instanceof BlackwatchHelicopter helicopter)helicopter.hurtAt(actor.damageSources().mobProjectile(craft,actor),definition.damage(),impact);
            else hurtGunContact(actor,first,victim,muzzle,impact,definition.damage());
            GunplayEffects.shot(actor,craft,definition,muzzle,impact,hullHit?3:2,direction.scale(-1));
        }
        craft.recordAirShot();return true;
    }
    /** Selection preflight only. The firing boundary rechecks actual first contact and splash. */
    private boolean missileOpportunity(LivingEntity target){
        if(!(craft.level() instanceof ServerLevel level)||target==null||!hostile(target))return false;
        int side=rocketShots%2==0?-1:1;Vec3 point=targetPoint(target),breech=craft.weaponPoint(HelicopterHellfireRack.mount(side));
        double range=craft.hellfireLaunchPoint(side,rocketShots/2%4).distanceTo(point);
        return range>=14&&range<=VehicleWeaponDefinition.require("helicopter_hellfire").weapon().range()
                &&HelicopterShotPath.loaded(level,breech,point)
                &&level.clip(new ClipContext(breech,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,craft)).getType()==HitResult.Type.MISS
                &&safeExplosiveImpact(level,point);
    }
    private boolean safeExplosiveImpact(ServerLevel level,Vec3 impact){
        var area=new AABB(impact,impact).inflate(HelicopterBlastWave.MAX_RADIUS+2);var actor=operator();
        for(var nearby:level.getEntitiesOfClass(LivingEntity.class,area,LivingEntity::isAlive))if(!hostile(nearby))return false;
        for(var hull:level.getEntitiesOfClass(MilitaryVehicle.class,area))if(VehicleDamageBridge.protectedHull(actor,hull))return false;
        for(var nearby:level.getEntitiesOfClass(BlackwatchHelicopter.class,area))if(!hostileHull(nearby))return false;
        return true;
    }
    /** Both player and AI guns keep parent permissions and the contacted bone. */
    private void hurtGunContact(LivingEntity actor,Entity first,LivingEntity victim,Vec3 from,Vec3 to,float damage){
        if(victim instanceof net.minecraft.server.level.ServerPlayer defender
                &&com.codex.blacklight.power.ProjectileDefenseSystem.hitscan(defender,from,to,damage,4))return;
        var source=actor.damageSources().mobProjectile(craft,actor);
        var body=AnimatedHurtBody.of(victim);
        boolean damaged;
        if(first!=victim&&body!=null){
            // Extend only the narrowed mesh contact by roundoff tolerance. Never
            // rebuild the ray from the player's eye or the aircraft center.
            var contact=gunBodyContact(body,from,to.add(to.subtract(from).normalize().scale(.0001)));
            damaged=contact!=null&&body.hurtAt(source,damage,contact);
        }else damaged=first.hurt(source,damage);
        if(damaged)VehicleOrdnanceProjectile.impactImpulse(victim,to.subtract(from),.65);
    }
    private static HydraHurtContact.Contact gunBodyContact(AnimatedHurtBody body,Vec3 from,Vec3 to){
        return HydraHurtContact.ray(body.hurtRegions(0),from,to,com.codex.blacklight.infection.InfectedAttackStyle.PIERCE);
    }
    public void save(CompoundTag tag){
        var data=new CompoundTag();data.putBoolean("Enabled",enabled);data.putInt("Rounds",rounds);data.putInt("Rockets",rockets);data.putInt("GunWait",gunWait);data.putInt("RocketWait",rocketWait);data.putInt("GunBurstTicks",gunCycle.burstTicks());data.putInt("GunRestTicks",gunCycle.restTicks());data.putInt("Age",age);data.putInt("Unseen",unseen);data.putInt("Shots",shots);data.put("CircleStrafe",circle.save());
        if(lastBase!=null){data.putDouble("BaseX",lastBase.x);data.putDouble("BaseY",lastBase.y);data.putDouble("BaseZ",lastBase.z);data.putBoolean("TargetHydra",targetHydra);}
        data.put("HellfireAttack",hellfireAttack.save());if(hoverPoint!=null){data.putDouble("HoverX",hoverPoint.x);data.putDouble("HoverY",hoverPoint.y);data.putDouble("HoverZ",hoverPoint.z);}
        if(designated!=null)data.putUUID("Designated",designated);if(targetId!=null)data.putUUID("Target",targetId);
        if(lastSeen!=null){data.putDouble("SeenX",lastSeen.x);data.putDouble("SeenY",lastSeen.y);data.putDouble("SeenZ",lastSeen.z);}tag.put("AirCombat",data);
        data.putInt("GunShots",gunShots);data.putInt("RocketShots",rocketShots);data.put("Tactics",tactics.save());
        data.put("RapidMagazine",rapid.save());data.putBoolean("RapidSelected",rapidSelected);
    }
    public void load(CompoundTag tag){
        rapid.load(tag.getCompound("AirCombat").getCompound("RapidMagazine"));rapidSelected=tag.getCompound("AirCombat").getBoolean("RapidSelected");switchHeld=false;manualOperator=null;
        var data=tag.getCompound("AirCombat");enabled=data.getBoolean("Enabled");rounds=clamp(data.getInt("Rounds"),240);rockets=clamp(data.getInt("Rockets"),8);
        gunWait=clamp(data.getInt("GunWait"),30);rocketWait=clamp(data.getInt("RocketWait"),60);gunCycle.restore(data.getInt("GunBurstTicks"),data.getInt("GunRestTicks"));age=clamp(data.getInt("Age"),2401);unseen=clamp(data.getInt("Unseen"),101);shots=Math.max(0,data.getInt("Shots"));
        hellfireAttack.load(data.getCompound("HellfireAttack"));hoverPoint=data.contains("HoverX")&&data.contains("HoverY")&&data.contains("HoverZ")?new Vec3(data.getDouble("HoverX"),data.getDouble("HoverY"),data.getDouble("HoverZ")):null;
        if(!hellfireAttack.hovering()||!HelicopterFlight.finite(hoverPoint)||hoverPoint.distanceToSqr(craft.position())>64){hoverPoint=null;hellfireAttack.abort();}
        gunShots=data.contains("GunShots")?Math.max(0,data.getInt("GunShots")):240-rounds;
        rocketShots=data.contains("RocketShots")?Math.max(0,data.getInt("RocketShots")):8-rockets;
        designated=data.hasUUID("Designated")?data.getUUID("Designated"):null;targetId=data.hasUUID("Target")?data.getUUID("Target"):null;
        circle.load(data.getCompound("CircleStrafe"));
        lastBase=data.contains("BaseX",6)&&data.contains("BaseY",6)&&data.contains("BaseZ",6)?new Vec3(data.getDouble("BaseX"),data.getDouble("BaseY"),data.getDouble("BaseZ")):null;
        if(!HelicopterFlight.finite(lastBase))lastBase=null;targetHydra=data.getBoolean("TargetHydra");
        lastSeen=data.contains("SeenX")?new Vec3(data.getDouble("SeenX"),data.getDouble("SeenY"),data.getDouble("SeenZ")):null;
        if(!HelicopterFlight.finite(lastSeen)||lastSeen.lengthSqr()>9E14)lastSeen=null;
        targetHelicopter=targetTank=false;tactics.restore(data.getCompound("Tactics"));
    }
    private static int clamp(int value,int max){return Math.max(0,Math.min(max,value));}
}
