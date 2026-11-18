package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** P1 Mother sub-FSM and the normal e08m04 health/wave path. The standalone MC
 * encounter omits Times Square dialogue/camera gates, but does not invent a
 * mandatory stage16 or four-round quota. Phase clocks save remaining observed
 * ticks: unloaded time cannot replay damage, waves, or movement. */
public final class GreeneBattleCycle {
    public static final int GUARDED=0,COLLAPSING=1,DOWN=2,RISING=3,SUBMERGING=4,UNDERGROUND=5,EMERGE_WAIT=6,EMERGING=7;
    public static final int COLLAPSE_TICKS=125,RISE_TICKS=50,SUBMERGE_CLIP_TICKS=59,UNDERGROUND_TICKS=400;
    public static final int EMERGE_GATE_TICKS=6,EMERGE_CLIP_TICKS=163,EMERGE_ATTACK_TICKS=80,EMERGE_TARGET_TICKS=27;
    public static final double SOURCE_DEPTH=20,SOURCE_TOLERANCE=.75;
    private final EncounterMob owner;
    private final GreeneBrood brood=new GreeneBrood();
    private final GreeneEmergenceHit emergence;
    private int stage,age,exposureAge,selectedHold=GreeneMotion.DEFAULT_HOLD_TICKS,recoveryRemaining,previousDown;
    private int undergroundRemaining,waveCount=1,armourWave=1,emergeElapsed=-1,nextHydra=1;
    private int floorDelay=-1;
    private float minimumHealth,initialStunnedHealth,healthBasis;
    private boolean initialized,coreDamageable,stunPending,recoveryPending,floorPending,rootControlled;
    private boolean savedNoGravity,savedNoPhysics,disposed,hydrasSpawned,openingPending;
    private double surfaceY,goalY;
    private long lastTick=Long.MIN_VALUE,handoffAt=Long.MIN_VALUE;
    private float handoffAge=-1;
    private float collapseYaw;
    public float collapseYaw(){return owner.level().isClientSide?client().getFloat("CollapseYaw"):collapseYaw;}
    private UUID player;

    public GreeneBattleCycle(EncounterMob owner){this.owner=owner;this.emergence=new GreeneEmergenceHit(owner);}
    public void initializeNew(){
        if(owner.level().isClientSide||initialized)return;
        initialized=true;lastTick=owner.level().getGameTime();healthBasis=owner.getMaxHealth();minimumHealth=healthBasis;surfaceY=owner.getY();
        openingPending=true;owner.greeneRestoreAppendages(1);publish();
    }
    /** /summon and entity-tag spawns pass through Entity.load too. Only the
     * caller that proved absence of an existing encounter record uses this. */
    public void scheduleOpeningFromSpawnData(){if(!owner.level().isClientSide){openingPending=true;publish();}}
    /** CombatScale may install health units on EntityJoinLevel, after the
     * constructor has initialized this cycle. Preserve proportions, not a
     * second full heal, when that or another maximum-health modifier changes. */
    public void refreshHealthScale(){
        if(owner.level().isClientSide||!initialized)return;
        float current=owner.getMaxHealth();
        if(!Float.isFinite(current)||current<=0)return;
        if(!Float.isFinite(healthBasis)||healthBasis<=0){healthBasis=current;return;}
        if(current==healthBasis)return;
        double factor=(double)current/healthBasis;healthBasis=current;
        minimumHealth=rescale(minimumHealth,factor);initialStunnedHealth=rescale(initialStunnedHealth,factor);
        owner.greeneScaleAppendages(factor);
    }
    private static float rescale(float amount,double factor){return (float)Math.max(-Float.MAX_VALUE,Math.min(Float.MAX_VALUE,amount*factor));}
    public float savedHealthBasis(){return healthBasis;}
    private CompoundTag client(){return owner.greeneBattleData();}
    public int stage(){return owner.level().isClientSide?client().getInt("Stage"):stage;}
    public float age(float partial){
        if(!owner.level().isClientSide)return age+partial;
        var data=client();return data.getBoolean("Frozen")?data.getInt("Age"):Math.max(0,owner.level().getGameTime()-data.getLong("Started")+partial);
    }
    public boolean exposed(){int s=stage();return s>=COLLAPSING&&s<=RISING;}
    public float exposureAge(float partial){
        if(!owner.level().isClientSide)return exposureAge+partial;
        var data=client();return exposed()&&!data.getBoolean("Frozen")?Math.max(0,owner.level().getGameTime()-data.getLong("CollapseStarted")+partial):data.getInt("ExposureAge");
    }
    public int exposureDuration(){
        if(owner.level().isClientSide)return client().getInt("ExposureTotal");
        return stage==RISING?exposureAge-age+RISE_TICKS:exposureAge+Math.max(0,recoveryRemaining)+RISE_TICKS;
    }
    public int previousDown(){return owner.level().isClientSide?client().getInt("PreviousDown"):previousDown;}
    public int armourWave(){return owner.level().isClientSide?Math.max(1,client().getInt("ArmourWave")):armourWave;}
    public int waveCount(){return waveCount;}
    public boolean openingPending(){return owner.level().isClientSide?client().getBoolean("OpeningPending"):openingPending;}
    public float minimumHealth(){return minimumHealth;}
    public boolean coreDamageable(){return owner.level().isClientSide?client().getBoolean("CoreDamageable"):coreDamageable;}
    public boolean controlsRoot(){int s=stage();return s>=SUBMERGING&&s<=EMERGING;}
    public boolean targetable(){if(openingPending())return false;return switch(stage()){
        case SUBMERGING -> age(0)<16;
        case UNDERGROUND,EMERGE_WAIT -> false;
        case EMERGING -> age(0)>=EMERGE_TARGET_TICKS;
        default -> true;
    };}
    public boolean canAttack(){return !openingPending()&&(stage()==GUARDED||stage()==EMERGING&&age(0)>=EMERGE_ATTACK_TICKS&&outside());}
    public boolean busy(){return stage()!=GUARDED;}
    public float handoffAge(){return owner.level().isClientSide?client().getFloat("HandoffAge"):handoffAge;}
    public float handoffElapsed(float partial){long at=owner.level().isClientSide?client().getLong("HandoffAt"):handoffAt;return at==Long.MIN_VALUE?100:owner.level().getGameTime()-at+partial;}
    private double scale(){return GreeneMotion.table().sourceUnitBlocks();}
    private boolean outside(){return owner.getY()-goalY> -SOURCE_TOLERANCE*scale();}
    private boolean playerBusy(){
        return player!=null&&owner.level() instanceof ServerLevel level&&level.getEntity(player) instanceof ServerPlayer p&&p.isAlive()&&DevastatorSystem.busy(p);
    }
    private void choosePlayer(){
        LivingEntity candidate=owner.getLastHurtByMob() instanceof ServerPlayer?owner.getLastHurtByMob():owner.getTarget();
        player=candidate instanceof ServerPlayer p?p.getUUID():null;
    }
    public void collapse(int hold){
        if(owner.level().isClientSide||openingPending||stage!=GUARDED&&stage!=EMERGING)return;
        initializeNew();refreshHealthScale();choosePlayer();selectedHold=GreeneMotion.hold(hold);
        recoveryRemaining=COLLAPSE_TICKS+selectedHold;exposureAge=0;previousDown=0;
        coreDamageable=false;stunPending=false;recoveryPending=false;floorPending=true;floorDelay=-1;
        collapseYaw=owner.yBodyRot;
        handoffAge=-1;handoffAt=Long.MIN_VALUE;releaseRoot();owner.cancelAttack();enter(COLLAPSING);
        processFloor();
    }
    private void processFloor(){
        if(!floorPending)return;
        if(floorDelay<0){if(playerBusy())return;floorDelay=GreeneBattleHealth.finalFloorDelayTicks(armourWave);}
        else if(floorDelay>0)floorDelay--;
        if(floorDelay==0){minimumHealth=GreeneBattleHealth.collapseFloor(armourWave,owner.getMaxHealth());floorPending=false;}
    }
    private void enterDown(){
        refreshHealthScale();
        var entry=GreeneBattleHealth.enterDown(waveCount,owner.getHealth());
        waveCount=entry.nextWaveCount();initialStunnedHealth=entry.initialStunnedHealth();stunPending=true;
        enter(DOWN);processStun();
    }
    private void processStun(){
        if(!stunPending||playerBusy()||stage!=DOWN&&stage!=RISING)return;
        // Persist the debit's ownership before invoking callbacks. Reload or
        // reentrant damage must not run the script a second time.
        stunPending=false;coreDamageable=true;publish();
        // This script amount is already in actual HP. Ordinary hurt converts
        // baseline damage to the recipient's health units exactly once later.
        float damage=GreeneBattleHealth.stunDamage(owner.getHealth(),owner.getMaxHealth())
                /com.codex.blacklight.power.CombatScale.units(owner);
        owner.hurt(owner.damageSources().generic(),damage);
    }
    public void damaged(){
        refreshHealthScale();
        if((stage==DOWN||stage==RISING)&&GreeneBattleHealth.recoveryRequested(initialStunnedHealth,owner.getHealth(),owner.getMaxHealth()))recoveryPending=true;
    }
    private void rise(){previousDown=age;enter(RISING);}
    private void beginSubmerge(){
        coreDamageable=false;stunPending=false;recoveryPending=false;floorPending=false;
        undergroundRemaining=UNDERGROUND_TICKS;surfaceY=owner.getY();goalY=surfaceY-SOURCE_DEPTH*scale();
        takeRoot();enter(SUBMERGING);brood.onSubmerge(owner,waveCount);
        if(!live())dispose();
    }
    private void takeRoot(){
        if(!rootControlled){savedNoGravity=owner.isNoGravity();savedNoPhysics=owner.noPhysics;rootControlled=true;}
        owner.setNoGravity(true);owner.noPhysics=true;owner.setDeltaMovement(Vec3.ZERO);owner.fallDistance=0;
        owner.getNavigation().stop();
    }
    private void releaseRoot(){
        if(rootControlled){owner.setNoGravity(savedNoGravity);owner.noPhysics=savedNoPhysics;rootControlled=false;}
        owner.setDeltaMovement(Vec3.ZERO);owner.fallDistance=0;
    }
    /** Release the old lease before Entity.load applies the incoming vanilla
     * flags. Direct readAdditionalSaveData callers are handled by load below. */
    public void beforeLoad(){emergence.clear();if(rootControlled)releaseRoot();}
    private void vertical(double distance){
        double y=owner.getY();double movement=Math.copySign(Math.min(Math.abs(distance),Math.abs(goalY-y)),goalY-y);
        owner.setPos(owner.getX(),y+movement,owner.getZ());owner.setDeltaMovement(Vec3.ZERO);owner.fallDistance=0;
    }
    private void beginEmerge(){
        refreshHealthScale();
        // MC maps the missing Times Square camera/dialogue completion to this
        // transition, retaining the normal branch order and source raise delays.
        armourWave=waveCount;owner.greeneRestoreAppendages(armourWave);
        if(armourWave<=3)minimumHealth=owner.getHealth();
        if(GreeneBattleHealth.releaseFloorOnEmergence(owner.getHealth(),owner.getMaxHealth()))minimumHealth=0;
        surfaceY=surface();goalY=surfaceY;
        owner.setPos(owner.getX(),surfaceY-SOURCE_DEPTH*scale(),owner.getZ());takeRoot();
        emergeElapsed=0;nextHydra=1;hydrasSpawned=false;brood.onCombatStarted();enter(EMERGE_WAIT);
    }
    /** Mission75836 calls MotherSpawn, attaches the three segments, then
     * MotherEmerge("justCore") before its15s HUD wait. Defer placement until
     * the first real tick: constructors still have the temporary world origin.
     * This first wave must not borrow repeat-emergence Hydra scheduling. */
    private void beginOpening(){
        openingPending=false;choosePlayer();surfaceY=owner.getY();surfaceY=surface(.001);goalY=surfaceY;
        coreDamageable=false;emergeElapsed=-1;hydrasSpawned=false;nextHydra=1;
        owner.cancelAttack();takeRoot();
        owner.setPos(owner.getX(),surfaceY-SOURCE_DEPTH*scale(),owner.getZ());enter(EMERGE_WAIT);
    }
    private double surface(){return surface(2);}
    private double surface(double above){
        // A newly placed actor owns the floor beneath its feet. Sampling from
        // two blocks above it can select a low ceiling and emerge on its roof.
        Vec3 from=new Vec3(owner.getX(),surfaceY+above,owner.getZ()),to=from.add(0,-(SOURCE_DEPTH*scale()+6),0);
        var hit=owner.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
        return hit.getType()==HitResult.Type.BLOCK?hit.getLocation().y:surfaceY;
    }
    private void tickHydras(){
        if(emergeElapsed<0)return;
        emergeElapsed++;
        if(!hydrasSpawned&&emergeElapsed>=1){hydrasSpawned=true;brood.onEmerge(owner,waveCount);if(!live())return;}
        int first=armourWave==2?60:20;
        if(nextHydra<=4&&emergeElapsed>=first+(nextHydra-1)*20){brood.raiseHydra(owner,nextHydra++);}
        if(nextHydra>4){brood.setRespawnEnabled(true);emergeElapsed=-1;}
    }
    public void attackStarted(){if(stage==EMERGING&&canAttack())finishEmerge();}
    private void finishEmerge(){
        handoffAge=age;handoffAt=owner.level().getGameTime();releaseRoot();enter(GUARDED);
    }
    private void enter(int next){
        emergence.clear();stage=next;age=0;lastTick=owner.level().getGameTime();
        if(next==EMERGING)emergence.begin();publish();
    }
    private boolean live(){return !disposed&&!owner.isRemoved()&&owner.isAlive();}
    public void tick(){
        if(owner.level().isClientSide)return;
        initializeNew();if(!live()){dispose();return;}refreshHealthScale();
        if(openingPending){beginOpening();return;}
        long now=owner.level().getGameTime();if(lastTick==now)return;lastTick=now;
        age++;if(exposed()){exposureAge++;if(recoveryRemaining>0)recoveryRemaining--;}
        processFloor();processStun();if(!live()){dispose();return;}
        if(controlsRoot())takeRoot();
        brood.tick(owner);if(!live()){dispose();return;}tickHydras();if(!live()){dispose();return;}
        switch(stage){
            case COLLAPSING -> {if(age>=COLLAPSE_TICKS)enterDown();}
            case DOWN -> {if(recoveryRemaining<=0||recoveryPending&&!playerBusy())rise();}
            case RISING -> {if(age>=RISE_TICKS)beginSubmerge();}
            case SUBMERGING -> {
                undergroundRemaining=Math.max(0,undergroundRemaining-1);
                if(age>16&&age<=58)vertical(SOURCE_DEPTH*scale()/(2.8666698932647705-.800000011920929)/20);
                // Tunneling state and Puppet completion are separate gates.
                if(age>=SUBMERGE_CLIP_TICKS&&owner.getY()-goalY<SOURCE_TOLERANCE*scale())enter(UNDERGROUND);
                if(undergroundRemaining==0)beginEmerge();
            }
            case UNDERGROUND -> {if(--undergroundRemaining<=0)beginEmerge();}
            case EMERGE_WAIT -> {if(player==null)choosePlayer();if(age>=EMERGE_GATE_TICKS&&!playerBusy())enter(EMERGING);}
            case EMERGING -> {
                if(age<=EMERGE_TARGET_TICKS)vertical(SOURCE_DEPTH*scale()/1.3333300352096558/20);
                if(age==EMERGE_TARGET_TICKS&&outside())owner.setPos(owner.getX(),goalY,owner.getZ());
                if(!emergence.tick())return;
                if(age>=EMERGE_CLIP_TICKS&&outside())finishEmerge();
            }
            default -> {}
        }
    }
    public void dispose(){
        if(owner.level().isClientSide||disposed)return;
        disposed=true;emergence.clear();brood.dispose(owner);releaseRoot();publish();
    }
    private void publish(){
        if(owner.level().isClientSide)return;
        long now=owner.level().getGameTime();var data=new CompoundTag();
        data.putFloat("CollapseYaw",collapseYaw);data.putInt("Stage",stage);data.putInt("Age",age);data.putLong("Started",now-age);
        data.putInt("ExposureAge",exposureAge);data.putLong("CollapseStarted",now-exposureAge);data.putInt("ExposureTotal",exposureDuration());
        data.putInt("PreviousDown",previousDown);data.putInt("ArmourWave",armourWave);data.putBoolean("CoreDamageable",coreDamageable);
        data.putFloat("HandoffAge",handoffAge);data.putLong("HandoffAt",handoffAt);data.putBoolean("Frozen",disposed);
        data.putBoolean("OpeningPending",openingPending);
        owner.greeneBattleData(data);
    }
    public CompoundTag save(){
        refreshHealthScale();
        var t=new CompoundTag();t.putFloat("CollapseYaw",collapseYaw);t.putInt("Version",4);t.putBoolean("OpeningPending",openingPending);t.putFloat("HealthBasis",healthBasis);t.putInt("Stage",stage);t.putInt("Age",age);t.putInt("ExposureAge",exposureAge);
        t.putInt("Hold",selectedHold);t.putInt("RecoveryRemaining",recoveryRemaining);t.putInt("PreviousDown",previousDown);t.putInt("UndergroundRemaining",undergroundRemaining);
        t.putInt("WaveCount",waveCount);t.putInt("ArmourWave",armourWave);t.putFloat("MinimumHealth",minimumHealth);t.putFloat("InitialStunnedHealth",initialStunnedHealth);
        t.putBoolean("CoreDamageable",coreDamageable);t.putBoolean("StunPending",stunPending);t.putBoolean("RecoveryPending",recoveryPending);
        t.putBoolean("FloorPending",floorPending);t.putInt("FloorDelay",floorDelay);t.putBoolean("RootControlled",rootControlled);
        t.putBoolean("SavedNoGravity",savedNoGravity);t.putBoolean("SavedNoPhysics",savedNoPhysics);t.putDouble("SurfaceY",surfaceY);t.putDouble("GoalY",goalY);
        t.putInt("EmergeElapsed",emergeElapsed);t.putInt("NextHydra",nextHydra);t.putBoolean("HydrasSpawned",hydrasSpawned);
        t.putFloat("HandoffAge",handoffAge);t.putFloat("HandoffElapsed",Math.max(0,Math.min(4,handoffElapsed(0))));
        if(player!=null)t.putUUID("Player",player);t.put("Brood",brood.save());
        if(stage==EMERGING)t.put("EmergenceHit",emergence.save());return t;
    }
    public void load(CompoundTag t){
        beforeLoad();
        initialized=true;disposed=false;lastTick=owner.level().getGameTime();handoffAge=-1;handoffAt=Long.MIN_VALUE;
        collapseYaw=t.contains("CollapseYaw",99)&&Float.isFinite(t.getFloat("CollapseYaw"))?t.getFloat("CollapseYaw"):owner.getYRot();
        stage=Math.max(0,Math.min(7,t.getInt("Stage")));age=bounded(t,"Age",100000);exposureAge=bounded(t,"ExposureAge",100000);
        // Missing markers are already-existing encounters, never a fresh spawn.
        openingPending=t.getInt("Version")>=4&&t.getBoolean("OpeningPending")&&stage==GUARDED;
        selectedHold=GreeneMotion.hold(t.getInt("Hold"));recoveryRemaining=bounded(t,"RecoveryRemaining",COLLAPSE_TICKS+2400);
        previousDown=bounded(t,"PreviousDown",100000);undergroundRemaining=bounded(t,"UndergroundRemaining",UNDERGROUND_TICKS);
        waveCount=Math.max(1,Math.min(100000,t.getInt("WaveCount")));armourWave=Math.max(1,Math.min(waveCount,t.getInt("ArmourWave")));
        healthBasis=t.contains("HealthBasis")&&Float.isFinite(t.getFloat("HealthBasis"))&&t.getFloat("HealthBasis")>0?t.getFloat("HealthBasis"):owner.getMaxHealth();
        minimumHealth=Float.isFinite(t.getFloat("MinimumHealth"))?t.getFloat("MinimumHealth"):0;
        initialStunnedHealth=Float.isFinite(t.getFloat("InitialStunnedHealth"))?Math.max(0,t.getFloat("InitialStunnedHealth")):owner.getHealth();
        coreDamageable=t.getBoolean("CoreDamageable");stunPending=t.getBoolean("StunPending");recoveryPending=t.getBoolean("RecoveryPending");
        floorPending=t.getBoolean("FloorPending");floorDelay=Math.max(-1,Math.min(60,t.getInt("FloorDelay")));
        rootControlled=t.getBoolean("RootControlled");savedNoGravity=t.getBoolean("SavedNoGravity");savedNoPhysics=t.getBoolean("SavedNoPhysics");
        surfaceY=Double.isFinite(t.getDouble("SurfaceY"))?t.getDouble("SurfaceY"):owner.getY();
        goalY=Double.isFinite(t.getDouble("GoalY"))?t.getDouble("GoalY"):surfaceY;
        emergeElapsed=Math.max(-1,Math.min(100000,t.getInt("EmergeElapsed")));nextHydra=Math.max(1,Math.min(5,t.getInt("NextHydra")));hydrasSpawned=t.getBoolean("HydrasSpawned");
        player=t.hasUUID("Player")?t.getUUID("Player"):null;brood.load(t.getCompound("Brood"));
        // Pre-integration saves cannot recover past victims. Resume their pose
        // without retroactive Hit delivery; the next full emergence starts it.
        if(stage==EMERGING&&t.contains("EmergenceHit",10))emergence.load(t.getCompound("EmergenceHit"));
        if(stage==GUARDED&&t.contains("HandoffAge")&&t.contains("HandoffElapsed")){
            float savedAge=t.getFloat("HandoffAge"),elapsed=t.getFloat("HandoffElapsed");
            if(Float.isFinite(savedAge)&&savedAge>=0&&Float.isFinite(elapsed)&&elapsed>=0&&elapsed<4){
                handoffAge=Math.min(EMERGE_CLIP_TICKS,savedAge);handoffAt=owner.level().getGameTime()-(int)elapsed;
            }
        }
        if(controlsRoot())takeRoot();else if(rootControlled)releaseRoot();publish();
    }
    /** Old snapshots already received player damage but no scripted stun. Do
     * not retroactively debit on load; future complete cycles use the new flow. */
    public void loadLegacy(boolean active,int oldAge,int hold,int oldPhase){
        beforeLoad();
        if(initialized)brood.dispose(owner);
        brood.load(new CompoundTag());
        stage=GUARDED;age=0;exposureAge=0;recoveryRemaining=0;previousDown=0;undergroundRemaining=0;
        emergeElapsed=-1;nextHydra=1;floorDelay=-1;initialStunnedHealth=0;
        coreDamageable=false;stunPending=false;recoveryPending=false;floorPending=false;
        savedNoGravity=false;savedNoPhysics=false;disposed=false;hydrasSpawned=false;openingPending=false;
        lastTick=owner.level().getGameTime();handoffAt=Long.MIN_VALUE;handoffAge=-1;player=null;
        collapseYaw=owner.getYRot();
        initialized=true;selectedHold=GreeneMotion.hold(hold);armourWave=Math.max(1,oldPhase+1);waveCount=armourWave;
        healthBasis=owner.getMaxHealth();minimumHealth=owner.getHealth();surfaceY=owner.getY();goalY=surfaceY;
        if(active){
            exposureAge=Math.max(0,Math.min(GreeneMotion.duration(selectedHold)-1,oldAge));
            stage=GreeneMotion.stage(exposureAge,selectedHold);
            age=stage==COLLAPSING?exposureAge:stage==DOWN?exposureAge-COLLAPSE_TICKS:exposureAge-COLLAPSE_TICKS-selectedHold;
            recoveryRemaining=Math.max(0,COLLAPSE_TICKS+selectedHold-exposureAge);previousDown=selectedHold;
            if(stage!=COLLAPSING){waveCount++;initialStunnedHealth=owner.getHealth();coreDamageable=true;minimumHealth=0;}
            else{floorPending=true;floorDelay=-1;}
        }
        publish();
    }
    private static int bounded(CompoundTag t,String key,int max){return Math.max(0,Math.min(max,t.getInt(key)));}
}
