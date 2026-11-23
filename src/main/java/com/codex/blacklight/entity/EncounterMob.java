package com.codex.blacklight.entity;

import com.codex.blacklight.world.BossSiteData;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.registry.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraft.core.particles.*;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.*;

/** Shared clock and collision rules, with species-specific moves and persistent boss stages. */
public final class EncounterMob extends Monster implements GeoEntity,AnimatedHurtBody {
    private static final EntityDataAccessor<Integer> MOVE=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.INT),PHASE=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.INT),BROKEN=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.LONG),EXPOSED=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> EXPOSED_START=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> GUARD_HIT=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> GREENE_HOLD=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> GREENE_HIT=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> FLIGHT=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.INT),FLIGHT_PREVIOUS=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> FLIGHT_START=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> FLIGHT_PREVIOUS_AGE=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.FLOAT),FLIGHT_PITCH=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> GREENE_PROGRAM=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GREENE_CLOSE_SIDE=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> GREENE_CLOSE_START=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.LONG);
    private final GreeneWeaponController greeneWeapons=new GreeneWeaponController(this);
    public void settlePlazaOpening() {
        // The native rise accepts a small tolerance below the surface. Vanilla gravity must resume above the paving.
        if(!greeneClose.opening() || !getPersistentData().contains("PlazaSurface")) return;
        var pad=net.minecraft.core.BlockPos.of(getPersistentData().getLong("PlazaSurface"));
        if(Math.abs(getY()-pad.getY())<1 && level().getBlockState(pad.below()).isFaceSturdy(level(),pad.below(),net.minecraft.core.Direction.UP))
            setPos(getX(),pad.getY()+.001,getZ());
    }
    public void preparePlazaOpening() {
        // This host already supplied its final surface. Tracking must begin with the underground position.
        greeneBattle.beginOpeningAtSurface();
        greeneClose.prepareOpening();
        bar.setVisible(false);
        defenseBar.setVisible(false);
    }
    private static final EntityDataAccessor<java.util.Optional<net.minecraft.core.BlockPos>> PLAZA_TARGET=
            SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.OPTIONAL_BLOCK_POS);
    public java.util.Optional<net.minecraft.core.BlockPos> plazaTruck(){ return entityData.get(PLAZA_TARGET); }
    public void trackPlazaTruck(boolean active){
        var nbt=getPersistentData();
        var target=active && nbt.contains("BlacklightBossConsole")?net.minecraft.core.BlockPos.of(nbt.getLong("BlacklightBossConsole")):null;
        entityData.set(PLAZA_TARGET,java.util.Optional.ofNullable(target));
    }
    private final GreeneCloseCombat greeneClose=new GreeneCloseCombat(this);
    private boolean greeneCloseImpact;
    public GreeneCloseAttack.Side greeneCloseSide(){int side=entityData.get(GREENE_CLOSE_SIDE);return side<0||side>=GreeneCloseAttack.Side.values().length?null:GreeneCloseAttack.Side.values()[side];}
    public float greeneCloseAge(float partial){return greeneCloseSide()==null?0:Math.max(0,level().getGameTime()-entityData.get(GREENE_CLOSE_START)+partial);}
    public boolean greeneCloseImpact(){return greeneCloseImpact;}
    public void syncGreeneClose(GreeneCloseAttack.Side side,float age){
        if(level().isClientSide)return;
        entityData.set(GREENE_CLOSE_SIDE,side==null?-1:side.ordinal());
        if(side!=null)entityData.set(GREENE_CLOSE_START,level().getGameTime()-Math.round(age));
    }
    public boolean damageGreeneClose(LivingEntity victim,HydraHurtContact.Contact contact,float scale){
        if(kind!=EncounterKind.ELIZABETH_GREENE||level().isClientSide||disabled()||!greeneBattle.canAttack()||!enemy(victim))return false;
        long epoch=chargeEpoch,stateEpoch=greeneStateEpoch;
        var prior=damageContact;boolean priorClose=greeneCloseImpact;damageContact=contact.point();greeneCloseImpact=true;
        try{
            var source=damageSources().mobAttack(this);var body=AnimatedHurtBody.of(victim);
            float amount=(float)kind.damage*scale; // MC baseline, preserving source 500/500/200 ratios.
            boolean accepted=body==null?victim.hurt(source,amount):body.hurtAt(source,amount,contact);
            if(accepted&&chargeEpoch==epoch&&greeneStateEpoch==stateEpoch&&isAlive()&&greeneBattle.canAttack())EncounterImpact.confirmed(this,victim,false,scale);
            return accepted;
        }finally{damageContact=prior;greeneCloseImpact=priorClose;}
    }
    private static final EntityDataAccessor<CompoundTag> GREENE_BATTLE=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.COMPOUND_TAG);
    private final GreeneBattleCycle greeneBattle=new GreeneBattleCycle(this);
    private final GreeneFeedback greeneFeedback=new GreeneFeedback();
    private final long[] greeneFlash={-100000,-100000,-100000,-100000};
    public float greeneFlash(int slot,float partial){
        if(slot<0||slot>=4)return 0;
        float age=level().getGameTime()-greeneFlash[slot]+partial;
        return age>=0&&age<7?1-age/7F:0;
    }
    @Override public void handleEntityEvent(byte event){
        if(kind==EncounterKind.ELIZABETH_GREENE&&event>=70&&event<=73){greeneFlash[event-70]=level().getGameTime();return;}
        super.handleEntityEvent(event);
    }
    private final GreeneRoarTiming greeneRoar=new GreeneRoarTiming();
    private int greeneOwnDamageDepth;
    public GreeneBattleCycle greeneBattle(){return greeneBattle;}
    public CompoundTag greeneBattleData(){return entityData.get(GREENE_BATTLE);}
    public void greeneBattleData(CompoundTag data){if(!level().isClientSide)entityData.set(GREENE_BATTLE,data);}
    public float greeneMinimumHealth(){if(spider.first()||goddess.active())return 0;greeneBattle.refreshHealthScale();return greeneBattle.minimumHealth();}
    public boolean greeneOwnDamageActive(){return greeneOwnDamageDepth>0;}
    public float greeneSegmentMaximum(){return GreeneBattleHealth.segmentHealth(greeneBattle.armourWave(),getMaxHealth());}
    public void greeneScaleAppendages(double factor){
        float maximum=greeneSegmentMaximum();
        for(int i=0;i<armor.length;i++)armor[i]=broken(i)?0:(float)Math.max(0,Math.min(maximum,armor[i]*factor));
    }
    public void greeneRestoreAppendages(int wave){
        if(level().isClientSide)return;
        greeneStateEpoch++;
        entityData.set(BROKEN,0);Arrays.fill(armor,GreeneBattleHealth.segmentHealth(wave,getMaxHealth()));Arrays.fill(armorHit,-100);
        entityData.set(PHASE,Math.min(3,Math.max(0,wave-1)));poise=0;
    }
    private static final EntityDataAccessor<Integer> CHARGE_PHASE=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> CHARGE_PHASE_START=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> GOLIATH_CRASH_SAMPLE=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> GOLIATH_WINDOW=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.INT);
    private double leapSpeed;
    private boolean leapDescending;

    public GoliathWindow goliathWindow(){return GoliathWindow.byId(entityData.get(GOLIATH_WINDOW));}
    public int goliathBrokenMask(){return entityData.get(BROKEN);}
    public boolean goliathFinisherReady(){return isAlive() && (goliathWindow()==GoliathWindow.FINISH || ConsumeLastChance.awaitingFinisher(this));}
    private final GoliathCharge.State goliathCharge=new GoliathCharge.State();
    private final GoliathSlamMotion.State goliathSlamMotion=new GoliathSlamMotion.State();
    private final GoliathGait goliathGait=new GoliathGait();
    public GoliathGait goliathGait(){return goliathGait;}
    private long chargeEpoch; // Server-only ownership token, including same-tick cancellation/reentry.
    private long greeneStateEpoch; // Reload/restoration/removal invalidates child damage callbacks.
    private Vec3 chargeContactOrigin;
    private float chargeContactYaw;
    private long chargeContactTick=Long.MIN_VALUE;
    public long goliathCrashSample(){return entityData.get(GOLIATH_CRASH_SAMPLE);}
    public GoliathCharge.Snapshot goliathChargeSnapshot(){return new GoliathCharge.Snapshot(entityData.get(CHARGE_PHASE),entityData.get(CHARGE_PHASE_START));}
    private void syncGoliathCharge(){var state=goliathCharge.snapshot();entityData.set(CHARGE_PHASE,state.phase());entityData.set(CHARGE_PHASE_START,state.phaseStartAge());}
    private float oldFlightPitch;
    private static final EntityDataAccessor<CompoundTag> FLIGHT_BODY=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.COMPOUND_TAG);
    private final FlyerPhysics flyerPhysics=new FlyerPhysics(this);
    public FlyerPhysics flyerPhysics(){return flyerPhysics;}
    public CompoundTag flyerBodyData(){return entityData.get(FLIGHT_BODY);}
    public void flyerBodyData(CompoundTag tag){if(!level().isClientSide)entityData.set(FLIGHT_BODY,tag);}
    private final FlyerFlight flight=new FlyerFlight(this);
    public FlyerFlight flight(){return flight;}
    public int flyerStage(){return entityData.get(FLIGHT);}
    public float flyerAge(float partial){return Math.max(0,level().getGameTime()-entityData.get(FLIGHT_START)+partial);}
    public void flyerStage(int value){flyerStage(value,false);}
    public void flyerStage(int value,boolean restart){if(flyerStage()==value&&!restart)return;entityData.set(FLIGHT_PREVIOUS,flyerStage());entityData.set(FLIGHT_PREVIOUS_AGE,flyerAge(0));entityData.set(FLIGHT,value);entityData.set(FLIGHT_START,level().getGameTime());}
    public float flightPitch(){return entityData.get(FLIGHT_PITCH);}
    public void flightPitch(float value){entityData.set(FLIGHT_PITCH,value);}
    public float[][] flyerSourcePose(float partial){return FlyerMotion.pose(flyerStage(),flyerAge(partial),entityData.get(FLIGHT_PREVIOUS),entityData.get(FLIGHT_PREVIOUS_AGE),level().isClientSide?net.minecraft.util.Mth.lerp(partial,oldFlightPitch,flightPitch()):flightPitch());}
    public float[][] flyerPose(float partial){var pose=FlyerPhysics.sample(this,partial);return pose==null?flyerSourcePose(partial):pose;}
    public void finishFlyerAttack(){entityData.set(MOVE,0);committed=null;cooldown=Math.max(cooldown,32);}
    public boolean flyerStrike(LivingEntity victim,Vec3 point,Vec3 heading){
        damageContact=point;
        try{if(victim.hurt(damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE)*1.15F)){
            victim.knockback(.55,-heading.x,-heading.z);EncounterImpact.confirmed(this,victim,true,.65F);return true;
        }return false;}finally{damageContact=null;}
    }
    private final Map<Integer,Set<UUID>> fistHits=new HashMap<>();
    private boolean fistGround;
    private Vec3 damageContact;
    public Vec3 damageContact(){return damageContact;}
    /** Coming-out Hit supplies its own source sphere contact. Preserve the
     * previous outgoing point across nested damage callbacks. */
    public boolean hurtEmergenceTarget(LivingEntity victim,float amount,HydraHurtContact.Contact contact){
        Vec3 previous=damageContact;damageContact=contact.point();
        try{
            var source=damageSources().mobAttack(this);var body=AnimatedHurtBody.of(victim);
            return body==null?victim.hurt(source,amount):body.hurtAt(source,amount,contact);
        }finally{damageContact=previous;}
    }
    public float guardHitAge(float partial){return Math.max(0,level().getGameTime()-entityData.get(GUARD_HIT)+partial);}
    public boolean sourcePoseCommitted(){return (kind==EncounterKind.GOLIATH||kind==EncounterKind.ELIZABETH_GREENE||kind==EncounterKind.FLYER&&(isAlive()||FlyerPhysics.hasPose(this))||kind==EncounterKind.JUGGERNAUT&&!ImpactControl.stunned(this)&&(move()!=EncounterMove.NONE&&SourceBossAction.get(kind,move())!=null||exposed()))&&!ModAttachments.peekData(this,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY).active();}
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private final EncounterKind kind;
    private final ServerBossEvent bar=new ServerBossEvent(net.minecraft.network.chat.Component.empty(),BossEvent.BossBarColor.RED,BossEvent.BossBarOverlay.PROGRESS);
    private final ServerBossEvent defenseBar=new ServerBossEvent(net.minecraft.network.chat.Component.empty(),BossEvent.BossBarColor.PURPLE,BossEvent.BossBarOverlay.NOTCHED_10);
    private final EncounterPart[] parts;
    private final GreenePart[] greeneParts;
    private final FlyerPart[] flyerParts;
    public FlyerPart[] flyerParts(){return flyerParts;}
    private HydraHurtContact.Contact incomingContact;
    private Vec3 lastIncomingPoint;private long lastIncomingTick=Long.MIN_VALUE;private int incomingEffects;
    @Override public boolean animatedHurtBody(){return kind==EncounterKind.FLYER||kind==EncounterKind.ELIZABETH_GREENE||kind==EncounterKind.GOLIATH;}
    private float skinFraction(float partial){
        return level().isClientSide?partial:Math.max(0,Math.min(1,partial+1));
    }
    public Vec3 skinOrigin(float partial){
        var previous=new Vec3(xo,yo,zo);
        return tickCount==0||previous.distanceToSqr(position())>64?position():previous.lerp(position(),skinFraction(partial));
    }
    public float skinYaw(float partial){
        if(spider.first() && greeneStage()==GreeneBattleCycle.GUARDED){
            var state=spiderData();
            if(spider.actions.move()!=SpiderActions.Move.REST && spider.actions.move()!=SpiderActions.Move.MOUTH_BEAM && state.contains("ActionYaw"))return state.getFloat("ActionYaw");
            if(spider.feeding() && state.contains("MealYaw"))return state.getFloat("MealYaw");
            // Vanilla body control may turn towards a held player. Resume from the visible shell.
            if(state.contains("Facing")){
                float t=net.minecraft.util.Mth.clamp(level().getGameTime()-state.getLong("FaceTick")+partial,0,1);
                return net.minecraft.util.Mth.rotLerp(t,state.getFloat("FaceFrom"),state.getFloat("Facing"));
            }
        }
        if(kind==EncounterKind.ELIZABETH_GREENE && (greeneBattle.exposed() || greeneStage()==GreeneBattleCycle.MOLTING))return greeneBattle.collapseYaw();
        return net.minecraft.util.Mth.rotLerp(skinFraction(partial),yBodyRotO,yBodyRot);
    }
    @Override public List<HydraContact.Region> hurtRegions(float partial){
        if(!animatedHurtBody())return List.of();
        if(kind==EncounterKind.ELIZABETH_GREENE||kind==EncounterKind.GOLIATH){
            Vec3 origin=skinOrigin(partial);
            float facing=skinYaw(partial);
            if(kind==EncounterKind.ELIZABETH_GREENE){
                if(goddess.active())return goddess.regions(origin,partial);
                if(spider.first())return spider.skin(origin,partial);
                var regions=GreeneContact.regions(GreeneMotion.pose(this,partial),origin.add(0,greeneHover(partial),0),facing,entityData.get(BROKEN),greeneOutbreak()?GreeneForm.of(this):null,greeneStrikingSlot());
                if(GreeneFeedingRoots.active(this))regions=GreeneFeedingRoots.addReceivers(regions,this,partial);
                return greeneBattle.targetable()?regions:regions.stream().map(r->new HydraContact.Region(r.bone(),List.of(),r.bounds())).toList();
            }
            return GoliathContact.regions(GoliathMotion.pose(this,partial),origin,facing,entityData.get(BROKEN),GoliathPresentation.SCALE);
        }
        float alpha=level().isClientSide?partial:Math.max(0,Math.min(1,partial+1));
        Vec3 before=new Vec3(xo,yo,zo);
        // Spawn and teleport coordinates are not a traversed flight corridor.
        if(before.distanceToSqr(position())>64)before=position();
        Vec3 origin=level().isClientSide?FlyerPhysics.origin(this,partial):before.lerp(position(),alpha);
        if(FlyerPhysics.hasPose(this))origin=FlyerPhysics.origin(this,partial);
        return FlyerContact.regions(flyerPose(partial),origin,net.minecraft.util.Mth.rotLerp(alpha,yBodyRotO,yBodyRot));
    }
    public HydraHurtContact.Contact incomingContact(){return incomingContact;}
    public Vec3 incomingPoint(){return lastIncomingTick==level().getGameTime()&&lastIncomingPoint!=null?lastIncomingPoint:position();}
    public int incomingEffects(){return incomingEffects;}
    public boolean hurtAt(DamageSource source,float amount,HydraHurtContact.Contact contact){
        if(level().isClientSide||contact==null||!Float.isFinite(amount)||amount<=0)return false;
        var prior=incomingContact;incomingContact=contact;try{
            if(kind==EncounterKind.GOLIATH)return !GoliathLockPart.severed(contact.bone(),goliathBrokenMask())&&hurt(source,amount);
            if(kind==EncounterKind.ELIZABETH_GREENE&&contact.bone().startsWith("weak_"))return hurtPart(Integer.parseInt(contact.bone().substring(5)),source,amount);
            return hurt(source,amount);
        }finally{incomingContact=prior;}
    }
    public void confirmedIncoming(DamageSource source,float amount){
        if(amount<=0)return;
        if(incomingContact==null){
            if(kind==EncounterKind.ELIZABETH_GREENE)BossImpactFeedback.accepted(this,source,amount);
            return;
        }
        var c=incomingContact;lastIncomingPoint=c.point();lastIncomingTick=level().getGameTime();incomingEffects++;
        var n=c.normal();var attacker=source.getEntity();
        var tissue=new com.codex.blacklight.network.InfectedImpactPayload(attacker==null?getId():attacker.getId(),getId(),c.appearance(),c.point().x,c.point().y,c.point().z,(float)n.x,(float)n.y,(float)n.z,kind==EncounterKind.ELIZABETH_GREENE?1.25F:.5F,level().getGameTime()*31+getId());
        var sound=ModInfectedSounds.prepare(this,com.codex.blacklight.infection.InfectedSoundCue.contact(c.style()),c.point());
        int spiderSlot=kind==EncounterKind.ELIZABETH_GREENE && spider.first()?SpiderAnatomy.NAMES.indexOf(c.bone()):-1;
        int leg=SpiderAnatomy.legForRegion(spiderSlot);
        if(leg>=0){
            if(spiderData().getLong("Break"+leg)!=level().getGameTime() || !spider.broken(leg)){
                var response=SpiderAnatomy.tendon(spiderSlot) && spider.exposedTendon(leg)?BossImpactFeedback.Contact.WEAK:
                        spider.broken(leg)?BossImpactFeedback.Contact.FLESH:BossImpactFeedback.Contact.ARMOR;
                BossImpactFeedback.contact(this,source,c.point(),response,amount);
            }
        }else if(goddess.active()){
            var response=goddess.groundWeakSpot(source,c)?BossImpactFeedback.Contact.WEAK:BossImpactFeedback.Contact.FLESH;
            BossImpactFeedback.contact(this,source,c.point(),response,amount);
        }else if(kind==EncounterKind.ELIZABETH_GREENE){
            int slot=c.bone()!=null&&c.bone().startsWith("weak_")?Integer.parseInt(c.bone().substring(5,6)):3;
            if (slot == 3 || armor[slot] > 0)
                BossImpactFeedback.contact(this, source, c.point(), slot < 3 || exposed() ? BossImpactFeedback.Contact.WEAK : BossImpactFeedback.Contact.ARMOR, amount);
            level().broadcastEntityEvent(this,(byte)(70+slot));
            if(sound!=null)sound=new ModInfectedSounds.Prepared(sound.event(),sound.volume()*1.55F,sound.pitch()*.8F);
        }
        ContactEffects.body(this,tissue,c.style()==com.codex.blacklight.infection.InfectedAttackStyle.CRUSH,sound);
    }
    private final float[] armor={90,90,90};private final long[] armorHit={-100,-100,-100};
    private UUID committed;private Vec3 aim=Vec3.ZERO,direction=Vec3.ZERO;private float yaw;
    private int cooldown=20,sequence,lastAge=-1;private boolean blocked;private float poise;
    private final Set<UUID> struck=new HashSet<>();private final Set<UUID> brood=new HashSet<>();
    private final EnumMap<EncounterMove,Long> readyAt=new EnumMap<>(EncounterMove.class);
    private EncounterMove previous=EncounterMove.NONE;
    private long lastThreatAt=-1000;
    private AABB contactSweep;
    public EncounterMob(EntityType<? extends EncounterMob> type,Level level,EncounterKind kind){
        super(type,level);this.kind=kind;entityData.set(FLIGHT_START,level.getGameTime());xpReward=kind.boss?100:20;parts=new EncounterPart[kind.weakParts()];
        for(int i=0;i<parts.length;i++)parts[i]=new EncounterPart(this,i);
        // Legacy class name retained so existing native-ray adapters can use the
        // same AnimatedHurtBody-backed proxies for both source-posed bosses.
        greeneParts=new GreenePart[kind==EncounterKind.ELIZABETH_GREENE?Math.max(GreeneContact.regionBones().size(),SpiderAnatomy.NAMES.size()):kind==EncounterKind.GOLIATH?GoliathContact.regionBones().size():0];
        for(int i=0;i<greeneParts.length;i++)greeneParts[i]=new GreenePart(this,i);
        flyerParts=new FlyerPart[kind==EncounterKind.FLYER?FlyerContact.regionBones().size():0];
        for(int i=0;i<flyerParts.length;i++)flyerParts[i]=new FlyerPart(this,i);
        if(parts.length+flyerParts.length+greeneParts.length>0)setId(ENTITY_COUNTER.getAndAdd(parts.length+flyerParts.length+greeneParts.length+1)+1);
        setNoGravity(kind.flying);if(kind.boss)setPersistenceRequired();
        if(kind==EncounterKind.ELIZABETH_GREENE)greeneBattle.initializeNew();
    }
    private static final EntityDataAccessor<Boolean> GREENE_OUTBREAK =
            SynchedEntityData.defineId(EncounterMob.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<CompoundTag> SPIDER=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> GODDESS=SynchedEntityData.defineId(EncounterMob.class,EntityDataSerializers.COMPOUND_TAG);
    public final GoddessEncounter goddess=new GoddessEncounter(this);
    public final SpiderPhase spider=new SpiderPhase(this);
    public CompoundTag goddessData(){return entityData.get(GODDESS);}
    public void goddessData(CompoundTag tag){if(!level().isClientSide)entityData.set(GODDESS,tag);}
    private boolean spiderLethal;
    public CompoundTag spiderData(){return entityData.get(SPIDER);}
    public void spiderData(CompoundTag tag){entityData.set(SPIDER,tag);}
    public float spiderHealthWrite(float hp){
        if(goddess.active())return goddess.healthWrite(hp);
        if(spider.live() && hp<=0){spiderLethal=true;return 1;}
        return Math.max(hp,greeneMinimumHealth());
    }
    public boolean greeneOutbreak() { return entityData.get(GREENE_OUTBREAK); }
    public double greeneHover(float partial){
        if(!greeneOutbreak() || greeneBattle.armourWave()<2)return 0;
        float age=greeneBattle.age(partial);
        double base=8+.30*Math.sin((tickCount+partial)*.054);
        return switch(greeneStage()){
            case GreeneBattleCycle.COLLAPSING -> base*(1-net.minecraft.util.Mth.clamp(age/85F,0,1));
            case GreeneBattleCycle.DOWN -> 0;
            case GreeneBattleCycle.RISING -> base*net.minecraft.util.Mth.clamp(age/50F,0,1);
            default -> base;
        };
    }
    public int greeneStrikingSlot() {
        var side=greeneCloseSide();
        return side==null?-1:side.segment();
    }
    public void startGreeneOutbreak() {
        entityData.set(GREENE_OUTBREAK,true);
        spider.synchronizeShellHealth();
        greeneBattle.beginOpeningAtSurface();
    }

    // A render-only animation instance belongs to this boss and is never added to the level.
    private GreeneSpider spiderShell;
    public GreeneSpider greeneSpiderShell() {
        if(spiderShell==null) {
            spiderShell=new GreeneSpider(com.codex.blacklight.registry.EncounterEntities.GREENE_SPIDER.get(),level());
            spiderShell.setYRot(180);spiderShell.encounter=this;
        }
        return spiderShell;
    }

    public EncounterKind kind(){return kind;}public String asset(){return kind.id;}
    public EncounterMove move(){return EncounterMove.byId(entityData.get(MOVE));}
    public float moveAge(float partial){return Math.max(0,level().getGameTime()-entityData.get(START)+partial);}
    public GreeneWeaponProgram greeneProgram(){return GreeneWeaponProgram.byId(entityData.get(GREENE_PROGRAM));}
    public int greeneShots(){return greeneWeapons.shots();}
    public SourceBossAction sourceAction(EncounterMove action){
        return kind==EncounterKind.ELIZABETH_GREENE&&(action==EncounterMove.VOLLEY||action==EncounterMove.ORBS)&&action==move()?greeneProgram().action():SourceBossAction.get(kind,action);
    }
    public int actionDuration(EncounterMove action){if(kind==EncounterKind.FLYER&&action==EncounterMove.DIVE)return 80;var source=sourceAction(action);return source==null?action.duration:source.duration();}
    public int actionContact(EncounterMove action){var source=sourceAction(action);return source==null?action.contact:source.contact();}
    public double actionRange(EncounterMove action){var source=sourceAction(action);return source==null?action.range:source.worldRange();}
    public boolean inActionRange(EncounterMove action,LivingEntity target){
        if(kind==EncounterKind.ELIZABETH_GREENE&&action==EncounterMove.HOWL)
            return greeneTargetDistance(target)<GreeneRoarTiming.TRIGGER_DISTANCE;
        if(kind==EncounterKind.GOLIATH&&broken(0)){
            return distanceTo(target)<(broken(1)?17:18.5) && Math.abs(target.getY()-getY())<12;
        }
        if(GoliathSlam.uses(kind,action)){
            Vec3 d=target.position().subtract(position());float facing=(float)Math.toDegrees(Math.atan2(-d.x,d.z));
            var body=AnimatedHurtBody.of(target);
            if(body!=null){
                for(var sphere:GoliathSlam.spheres(actionContact(action),position(),facing,GoliathPresentation.SCALE))
                    for(var region:body.hurtRegions(0))for(var box:region.boxes())
                        if(GoliathSlam.contact(sphere,region.bone(),box)!=null)return true;
                return false;
            }
            return GoliathSlam.canReach(position(),facing,target.getBoundingBox(),GoliathPresentation.SCALE);
        }
        if(kind==EncounterKind.JUGGERNAUT&&JuggernautContact.uses(action)){
            Vec3 d=target.position().subtract(position());float facing=(float)Math.toDegrees(Math.atan2(-d.x,d.z));
            return JuggernautContact.canReach(action,position(),facing,target.getBoundingBox());
        }
        var source=SourceBossAction.get(kind,action);
        if(source!=null&&source.rangeMode.equals("horizontal"))return target.position().subtract(position()).horizontalDistance()<source.worldRange();
        return distanceTo(target)<=actionRange(action)+kind.width*.5+target.getBbWidth()*.5;
    }
    public int phase(){return entityData.get(PHASE);}
    public boolean exposed(){
        if(kind==EncounterKind.ELIZABETH_GREENE)return greeneBattle.exposed();
        return kind==EncounterKind.GOLIATH ? isAlive()&&(goliathWindow()==GoliathWindow.FINISH||entityData.get(EXPOSED)>level().getGameTime()) : entityData.get(EXPOSED)>level().getGameTime();
    }
    public float exposedAge(float partial){
        return kind==EncounterKind.ELIZABETH_GREENE?greeneBattle.exposureAge(partial)
            :Math.max(0,level().getGameTime()-entityData.get(EXPOSED_START)+partial);
    }
    public int greeneHoldTicks(){return entityData.get(GREENE_HOLD);}
    public float greeneHitAge(float partial){return level().getGameTime()-entityData.get(GREENE_HIT)+partial;}
    public int greeneStage(){return greeneBattle.stage();}
    public int exposedDuration(){
        if(kind==EncounterKind.ELIZABETH_GREENE)return greeneBattle.exposureDuration();
        if(kind==EncounterKind.GOLIATH)
            return (int)Math.max(1,entityData.get(EXPOSED)-entityData.get(EXPOSED_START));
        var state=SourceBossAction.get(kind,"EXPOSED");
        return state==null?140:state.duration();
    }
    private void expose(int ticks){expose(ticks,0L);}
    private void expose(int ticks,long impactSample){
        var state=SourceBossAction.get(kind,"EXPOSED");
        if(kind==EncounterKind.ELIZABETH_GREENE){
            int hold=com.codex.blacklight.config.GameplayBalanceConfig.value(com.codex.blacklight.config.GameplayBalanceConfig.GREENE_SOURCE_TIMER)
                    ?GreeneRecoveryTiming.downTicks(random.nextInt(GreeneRecoveryTiming.RANDOM_BITS))
                    :GreeneMotion.hold(com.codex.blacklight.config.GameplayBalanceConfig.value(com.codex.blacklight.config.GameplayBalanceConfig.GREENE_EXPOSED_TICKS));
            entityData.set(GREENE_HOLD,hold);greeneBattle.collapse(hold);
            ModInfectedSounds.play(this,com.codex.blacklight.infection.InfectedSoundCue.ERUPT);
            return;
        }
        else if(state!=null&&kind!=EncounterKind.GOLIATH)ticks=state.duration();
        if(kind==EncounterKind.GOLIATH){
            // A second armor break during an active recovery must not restart its pose.
            if(!isAlive()||exposed()||broken(0))return;
            startGoliathWindow(armor[2]>0?GoliathWindow.HEAD:GoliathWindow.KNEE,ticks,impactSample);
            return;
        }
        long now=level().getGameTime();
        entityData.set(EXPOSED_START,now);entityData.set(EXPOSED,now+ticks);cancelAttack();
    }

    private void startGoliathWindow(GoliathWindow state,int ticks,long impactSample){
        if(!isAlive()||level().isClientSide)return;
        yaw=getYRot();
        entityData.set(GOLIATH_WINDOW,state.ordinal());
        entityData.set(GOLIATH_CRASH_SAMPLE,impactSample);
        setDeltaMovement(getDeltaMovement().multiply(0,1,0));
        long now=level().getGameTime();
        entityData.set(EXPOSED_START,now);entityData.set(EXPOSED,now+ticks);cancelAttack();
        if(kind==EncounterKind.GOLIATH)updateParts();
    }
    private void crashGoliathCharge(int age){
        var phase=goliathChargeSnapshot();
        long sample=GoliathRecovery.packImpactSample(phase.phase(),age-phase.phaseStartAge());
        expose(GoliathRecovery.WALL_TICKS,sample);
        playSound(SoundEvents.STONE_BREAK,1F,.65F);
    }
    private void finishGoliathRecovery(){
        entityData.set(EXPOSED,0L);
        entityData.set(EXPOSED_START,0L);
        entityData.set(GOLIATH_CRASH_SAMPLE,0L);
        entityData.set(GOLIATH_WINDOW,GoliathWindow.NONE.ordinal());
        poise=0;
    }
    public boolean broken(int slot){return (entityData.get(BROKEN)&(1<<slot))!=0;}
    public static AttributeSupplier.Builder attributes(EncounterKind kind){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,kind.health).add(Attributes.ATTACK_DAMAGE,kind.damage).add(Attributes.MOVEMENT_SPEED,kind.speed).add(Attributes.FOLLOW_RANGE,kind==EncounterKind.ELIZABETH_GREENE?GreeneMissileFlight.RANGE:32).add(Attributes.KNOCKBACK_RESISTANCE,kind.boss?.85:.4);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(GREENE_OUTBREAK,false);entityData.define(SPIDER,new CompoundTag());entityData.define(GODDESS,new CompoundTag());entityData.define(MOVE,0);entityData.define(PHASE,0);entityData.define(BROKEN,0);entityData.define(START,0L);entityData.define(EXPOSED,0L);entityData.define(EXPOSED_START,0L);entityData.define(GUARD_HIT,-100000L);entityData.define(GREENE_HOLD,GreeneMotion.DEFAULT_HOLD_TICKS);entityData.define(GREENE_HIT,-100000L);entityData.define(FLIGHT,0);entityData.define(FLIGHT_PREVIOUS,0);entityData.define(FLIGHT_START,0L);entityData.define(FLIGHT_PREVIOUS_AGE,0F);entityData.define(FLIGHT_PITCH,0F);entityData.define(FLIGHT_BODY,new CompoundTag());entityData.define(GREENE_PROGRAM,0);entityData.define(GREENE_CLOSE_SIDE,-1);entityData.define(GREENE_CLOSE_START,0L);entityData.define(PLAZA_TARGET,java.util.Optional.empty());entityData.define(GREENE_BATTLE,new CompoundTag());entityData.define(CHARGE_PHASE,-1);entityData.define(CHARGE_PHASE_START,0F);entityData.define(GOLIATH_CRASH_SAMPLE,0L);entityData.define(GOLIATH_WINDOW,0);}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(2,new FightGoal());goalSelector.addGoal(7,new WaterAvoidingRandomStrollGoal(this,.65));targetSelector.addGoal(1,new HurtByTargetGoal(this));targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,LivingEntity.class,15,true,false,this::enemy));}
    public boolean enemy(LivingEntity target){
        if(target instanceof com.codex.blacklight.world.GreeneHost||target==null||target==this||!target.isAlive()||target.isRemoved()||target.level()!=level()||isAlliedTo(target))return false;
        if(target instanceof Player p)return !p.isCreative()&&!p.isSpectator()&&!com.codex.blacklight.disguise.DisguiseSystem.fooled(this,p)&&(kind==null||!kind.military||BlacklightInfectionSystem.isInfected(p)||getLastHurtByMob()==p);
        if(!(target instanceof Mob))return false;
        return kind!=null&&kind.military?BlacklightInfectionSystem.isInfected(target)||getLastHurtByMob()==target:!BlacklightInfectionSystem.isInfected(target);
    }
    @Override public boolean isAlliedTo(Entity other){if(kind!=null&&other instanceof LivingEntity living&&!(other instanceof Player)){
        if(kind.military&&(other instanceof MilitarySoldier||other instanceof MilitaryUav||other instanceof EncounterMob m&&m.kind.military))return true;
        if(!kind.military&&BlacklightInfectionSystem.isInfected(living))return true;
    }return super.isAlliedTo(other);}
    private boolean disabled(){return !kind.enabled()||!isAlive()||isNoAi()||isPassenger()||ImpactControl.stunned(this)||ModAttachments.peekData(this,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY).active()||com.codex.blacklight.power.BiomassSystem.claimant(this)!=null||level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL;}
    // The truck controls the first move only while its gate is opening. If the
    // console is lost or unloaded, the creature returns to ordinary AI on its own.
    private static final String CONVOY_GATE_TIMEOUT = "ConvoyGateTimeout";

    public void holdAtConvoyGate() {
        getPersistentData().putLong(CONVOY_GATE_TIMEOUT, level().getGameTime() + 40);
        getNavigation().stop();
    }

    private boolean waitingForConvoyGate() {
        return kind == EncounterKind.GOLIATH && getPersistentData().contains(CONVOY_GATE_TIMEOUT)
                && level().getGameTime() < getPersistentData().getLong(CONVOY_GATE_TIMEOUT);
    }

    public void leaveConvoyGate(LivingEntity operator) {
        boolean opening = waitingForConvoyGate();
        getPersistentData().remove(CONVOY_GATE_TIMEOUT);
        if (!opening || operator == null || !enemy(operator) || hurtTime > 0 || exposed()) return;
        cooldown = 0;
        setTarget(operator);
        begin(EncounterMove.CHARGE, operator);
    }

    public boolean begin(EncounterMove move,LivingEntity target){
        if(kind==EncounterKind.ELIZABETH_GREENE&&(!greeneBattle.canAttack()||greeneClose.opening()))return false;
        if(kind==EncounterKind.ELIZABETH_GREENE&&(move==EncounterMove.VOLLEY||move==EncounterMove.ORBS)){
            Entity body=GreeneWeaponTarget.body(target);var mechanical=GreeneWeaponTarget.mechanicalProgram(body);
            if(move==EncounterMove.ORBS&&mechanical==null)return false;
            return beginGreene(mechanical!=null?mechanical:body==null?null:GreeneWeaponProgram.onFoot(greeneTargetDistance(body)),body);
        }
        if(kind==EncounterKind.GOLIATH && (phase()==0 && move==EncounterMove.ERUPTION || phase()==1 && move!=EncounterMove.ERUPTION || phase()==2 && move!=EncounterMove.SLAM))return false;
        if(kind==EncounterKind.FLYER&&flyerStage()!=FlyerMotion.IDLE)return false;
        if(level().isClientSide||disabled()||waitingForConvoyGate()||move==null||move==EncounterMove.NONE||this.move()!=EncounterMove.NONE||cooldown>0||exposed()||!moveReady(move)||!kind.permits(move)||!enemy(target)||!hasLineOfSight(target)||!isWithinRestriction(target.blockPosition())||!inActionRange(move,target))return false;
        leapDescending=false;leapSpeed=0;
        aim=target.getBoundingBox().getCenter();direction=aim.subtract(position()).multiply(1,0,1).normalize();if(direction.lengthSqr()<.01)direction=new Vec3(0,0,1);
        yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));committed=target.getUUID();entityData.set(MOVE,move.ordinal());entityData.set(START,level().getGameTime());lastAge=-1;blocked=false;struck.clear();getNavigation().stop();
        fistHits.clear();fistGround=false;entityData.set(GUARD_HIT,-100000L);
        if(GoliathCharge.uses(kind,move)){chargeEpoch++;goliathCharge.begin();syncGoliathCharge();}
        if(GoliathSlam.uses(kind,move)){chargeEpoch++;goliathSlamMotion.begin();}
        if(kind==EncounterKind.ELIZABETH_GREENE)greeneBattle.attackStarted();
        previous=move;
        if(kind==EncounterKind.ELIZABETH_GREENE&&move==EncounterMove.HOWL)greeneRoar.started();
        else readyAt.put(move,level().getGameTime()+actionDuration(move)+EncounterTactics.recharge(move));
        contactSweep=getBoundingBox();
        var source=SourceBossAction.get(kind,move);if(source!=null&&source.turning.length>0&&!(kind==EncounterKind.GOLIATH&&broken(0)))yaw=getYRot();
        if(kind==EncounterKind.FLYER){flight.begin(target);ModInfectedSounds.play(this,com.codex.blacklight.infection.InfectedSoundCue.TENSION);}
        else if(kind==EncounterKind.JUGGERNAUT)ModInfectedSounds.play(this,com.codex.blacklight.infection.InfectedSoundCue.TENSION);
        else playSound(kind.military?SoundEvents.IRON_GOLEM_ATTACK:SoundEvents.RAVAGER_ROAR,.7F,kind.height>4?.55F:1.05F);return true;
    }
    public boolean beginGreene(GreeneWeaponProgram program,Entity target){
        if(greeneClose.opening()) return false;
        if(kind!=EncounterKind.ELIZABETH_GREENE||program==null||!greeneBattle.canAttack())return false;
        EncounterMove action=program.antiVehicle()?EncounterMove.ORBS:EncounterMove.VOLLEY;
        if(level().isClientSide||disabled()||move()!=EncounterMove.NONE||cooldown>0||exposed()||!moveReady(action)
                ||!GreeneWeaponTarget.accepts(this,program,target)||!GreeneWeaponTarget.visible(this,target)
                ||!program.antiVehicle()&&!GreeneWeaponProgram.onFootRange(greeneTargetDistance(target))
                ||!isWithinRestriction(target.blockPosition())||distanceTo(target)>program.action().range)return false;
        aim=GreeneWeaponTarget.point(target);direction=aim.subtract(position()).multiply(1,0,1).normalize();
        if(direction.lengthSqr()<.01)direction=new Vec3(0,0,1);
        yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));committed=target.getUUID();
        entityData.set(GREENE_PROGRAM,program.ordinal());entityData.set(MOVE,action.ordinal());entityData.set(START,level().getGameTime());
        greeneBattle.attackStarted();
        lastAge=-1;blocked=false;struck.clear();getNavigation().stop();fistHits.clear();fistGround=false;
        previous=action;
        // The source shot timer starts on emotional-state entry, not after the
        // clip. The active MOVE already excludes overlapping programs. Aircraft/
        // tank scheduling still keeps its separate MC encounter recharge.
        readyAt.put(action,level().getGameTime()+(program.antiVehicle()?program.action().duration()+EncounterTactics.recharge(action):program.shootGateTicks()));
        ModInfectedSounds.play(this,com.codex.blacklight.infection.InfectedSoundCue.TENSION);
        return true;
    }
    private void tickGreeneWeaponMove(){
        var action=move();var target=committed==null?null:((ServerLevel)level()).getEntity(committed);int age=(int)moveAge(0);
        if(!GreeneWeaponTarget.accepts(this,greeneProgram(),target)||!isWithinRestriction(target.blockPosition())){
            greeneWeapons.tick(null,null,0);cancelAttack();return;
        }
        if(age>=actionDuration(action)){
            greeneWeapons.tick(null,null,0);entityData.set(MOVE,0);committed=null;struck.clear();getNavigation().stop();
            // Natural on-foot completion returns to the shoot bank. Cancellation
            // retains its own recovery; it must not add a fake reload to every shot.
            cooldown=greeneProgram().antiVehicle()?Math.max(12,action.recovery-phase()*3):0;return;
        }
        getNavigation().stop();setYRot(yaw);yBodyRot=yaw;yHeadRot=yaw;updateParts();
        if(age==lastAge)return;lastAge=age;greeneWeapons.tick(greeneProgram(),target,age);
    }
    public boolean moveReady(EncounterMove move){
        if(kind==EncounterKind.ELIZABETH_GREENE&&move==EncounterMove.HOWL)return greeneRoar.recharged();
        return level().getGameTime()>=readyAt.getOrDefault(move,0L);
    }
    /** All FIG distance gates use source units, including the shooting bank.
     * The explicit projectile safety range remains in Minecraft blocks. */
    private double greeneTargetDistance(Entity target){
        return target==null?Double.POSITIVE_INFINITY:target.position().subtract(position()).horizontalDistance()/GreeneMotion.table().sourceUnitBlocks();
    }
    private boolean greeneRoarReady(LivingEntity target){
        return enemy(target)&&greeneRoar.ready(greeneTargetDistance(target),GreeneWeaponTarget.mechanicalProgram(GreeneWeaponTarget.body(target))!=null,
            target instanceof net.minecraft.server.level.ServerPlayer player&&DevastatorSystem.busy(player));
    }
    private CompoundTag saveGreeneRoar(){
        var state=greeneRoar.snapshot();var tag=new CompoundTag();tag.putInt("Version",1);
        tag.putInt("CloseTicks",state.closeTicks());tag.putInt("RechargeTicks",state.rechargeTicks());
        tag.putBoolean("Close",state.close());tag.putBoolean("Forbidden",state.forbidden());return tag;
    }
    private void loadGreeneRoar(CompoundTag tag){
        var state=tag.getCompound("GreeneRoar");
        if(state.getInt("Version")==1)greeneRoar.restore(new GreeneRoarTiming.Snapshot(state.getInt("CloseTicks"),state.getInt("RechargeTicks"),state.getBoolean("Close"),state.getBoolean("Forbidden")));
        else greeneRoar.restore(new GreeneRoarTiming.Snapshot(0,tag.getCompound("EncounterRecharge").getInt("HOWL"),false,false));
    }
    public void cancelAttack(){if(level().isClientSide)return;greeneClose.cancel();chargeEpoch++;chargeContactOrigin=null;chargeContactTick=Long.MIN_VALUE;goliathCharge.cancel();goliathSlamMotion.cancel();syncGoliathCharge();entityData.set(MOVE,0);committed=null;struck.clear();fistHits.clear();fistGround=false;cooldown=Math.max(cooldown,16);getNavigation().stop();if(kind==EncounterKind.FLYER)flight.cancel();}
    @Override public float getStepHeight(){
        float height=super.getStepHeight();
        return kind==EncounterKind.GOLIATH?Math.max(height,GoliathPresentation.STEP_HEIGHT):height;
    }
    @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source){
        // The authored back slam is a controlled landing, including slopes and roofs.
        if(kind==EncounterKind.GOLIATH && broken(1) && move()==EncounterMove.SLAM)return false;
        return super.causeFallDamage(distance,multiplier,source);
    }
    @Override protected void jumpFromGround(){
        if(spider!=null && spider.live())return;
        super.jumpFromGround();
    }
    @Override public void travel(Vec3 input){
        if(goddess.active() && (goddess.action()!=GoddessEncounter.Action.FLYING || goddess.attacks.move()==GoddessAttacks.Move.RUSH)){
            setDeltaMovement(Vec3.ZERO);
            fallDistance=0;
            return;
        }
        if(spider!=null && spider.live()){
            setDeltaMovement(0,Math.min(0,getDeltaMovement().y),0);
            super.travel(Vec3.ZERO);return;
        }
        if(kind==EncounterKind.ELIZABETH_GREENE&&greeneBattle.controlsRoot()){setDeltaMovement(Vec3.ZERO);fallDistance=0;return;}
        if(kind==EncounterKind.FLYER&&isAlive()&&FlyerPhysics.hasPose(this)){setDeltaMovement(Vec3.ZERO);return;}
        if(kind==EncounterKind.GOLIATH&&broken(1)&&move()==EncounterMove.SLAM){
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        if(kind==EncounterKind.GOLIATH&&exposed()){
            // Keep gravity and block support while preventing residual charge or pathing motion.
            setDeltaMovement(getDeltaMovement().multiply(0,1,0));
            super.travel(Vec3.ZERO);
            return;
        }
        super.travel(input);
    }
    @Override public void tick(){
        oldFlightPitch=flightPitch();
        if(!kind.enabled())setNoAi(true);
        if(kind==EncounterKind.GOLIATH&&!level().isClientSide){
            if(exposed())getNavigation().stop();
            else if(entityData.get(EXPOSED)>0)finishGoliathRecovery();
        }
        if(kind==EncounterKind.ELIZABETH_GREENE){
            greeneBattle.tick();greeneFeedback.tick(this);
            if(!level().isClientSide&&isAlive()&&!isNoAi()){
                var target=getTarget();
                greeneRoar.observe(level().getGameTime(),!disabled()&&greeneBattle.canAttack()&&enemy(target),greeneTargetDistance(target));
            }
        }
        double beforeX=getX(),beforeZ=getZ();
        super.tick();
        if(kind==EncounterKind.GOLIATH){
            goliathGait.tick(Math.hypot(getX()-beforeX,getZ()-beforeZ),
                isAlive()&&move()==EncounterMove.NONE&&!exposed()&&!isPassenger());
        }
        if(kind==EncounterKind.GOLIATH&&!level().isClientSide&&exposed()){
            setYRot(yaw);yBodyRot=yaw;yHeadRot=yaw;
        }
        // The downed mesh extends far beyond the root. Every observer and the
        // server use the heading captured when it fell, regardless of look AI.
        if(kind==EncounterKind.ELIZABETH_GREENE&&greeneBattle.exposed()){
            setYRot(greeneBattle.collapseYaw());
            yBodyRot=yBodyRotO=getYRot();
        }
        if(kind==EncounterKind.FLYER&&FlyerPhysics.hasPose(this)){float bodyYaw=flyerBodyData().getFloat("yaw");setYRot(bodyYaw);yBodyRot=yBodyRotO=bodyYaw;yHeadRot=bodyYaw;}
        updateParts();if(level().isClientSide)return;
        updateBossBars();
        if(kind==EncounterKind.FLYER&&flyerPhysics.tick()){flight.interruptedTick();updateParts();return;}
        if(disabled()){
            if(kind==EncounterKind.ELIZABETH_GREENE)greeneWeapons.tick(null,null,0);
            cancelAttack();
            if(kind==EncounterKind.FLYER&&isAlive()&&ImpactControl.stunned(this)
                    &&!ModAttachments.peekData(this,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY).active())flight.interruptedTick();
            updateParts();return;
        }
        if(cooldown>0)cooldown--;
        if(kind==EncounterKind.FLYER){flight.tick();updateParts();return;}
        int next=getHealth()<getMaxHealth()*.30?2:getHealth()<getMaxHealth()*.65?1:0;
        if(kind.boss&&kind!=EncounterKind.ELIZABETH_GREENE&&kind!=EncounterKind.GOLIATH&&next>phase()){entityData.set(PHASE,next);cancelAttack();cooldown=40;playSound(SoundEvents.RAVAGER_ROAR,1,.6F);}
        if(kind!=EncounterKind.GOLIATH&&entityData.get(EXPOSED)>0&&!exposed()){entityData.set(EXPOSED,0L);entityData.set(BROKEN,0);Arrays.fill(armor,90);poise=0;}
        if(kind==EncounterKind.ELIZABETH_GREENE){
            if(!greeneBattle.canAttack()){greeneWeapons.tick(null,null,0);return;}
            long closeEpoch=chargeEpoch,closeState=greeneStateEpoch;
            greeneClose.tick();
            if(chargeEpoch!=closeEpoch||greeneStateEpoch!=closeState)return;
            if(disabled()||!greeneBattle.canAttack()){greeneWeapons.tick(null,null,0);cancelAttack();updateParts();return;}
            if(move()==EncounterMove.VOLLEY||move()==EncounterMove.ORBS){tickGreeneWeaponMove();return;}
            greeneWeapons.tick(null,null,0);
        }
        var action=move();if(action==EncounterMove.NONE)return;
        var entity=committed==null?null:((ServerLevel)level()).getEntity(committed);
        int age=(int)moveAge(0);LivingEntity target=entity instanceof LivingEntity living?living:null;
        // Once the front slam has landed, finish its ground pulses even if that
        // blow killed the committed target. Death/exposure/stun still cancel above.
        boolean landedSlam=GoliathSlam.uses(kind,action)&&age>=actionContact(action) || kind==EncounterKind.GOLIATH&&broken(1)&&action==EncounterMove.SLAM&&age>=15;
        boolean returningCharge=GoliathCharge.uses(kind,action)&&!GoliathCharge.requiresTarget(goliathChargeSnapshot());
        if((!enemy(target)||!isWithinRestriction(target.blockPosition()))&&!landedSlam&&!returningCharge){cancelAttack();return;}
        getNavigation().stop();
        if(kind==EncounterKind.GOLIATH&&broken(0)){
            tickWoundedGoliath(action,age);
            return;
        }
        if(GoliathCharge.uses(kind,action)){tickGoliathCharge(enemy(target)&&isWithinRestriction(target.blockPosition())?target:null,age);return;}
        var source=SourceBossAction.get(kind,action);
        if(source!=null&&target!=null){float step=source.turnStep(age,distanceTo(target));if(step>0){Vec3 d=target.position().subtract(position());float wanted=(float)Math.toDegrees(Math.atan2(-d.x,d.z));yaw=net.minecraft.util.Mth.approachDegrees(yaw,wanted,step);}}
        setYRot(yaw);yBodyRot=yaw;yHeadRot=yaw;
        if(age>=actionDuration(action)){if(GoliathSlam.uses(kind,action))goliathSlamMotion.cancel();entityData.set(MOVE,0);committed=null;struck.clear();cooldown=Math.max(12,action.recovery-phase()*3);if(kind==EncounterKind.GOLIATH)expose(60);return;}
        if(age==lastAge)return;lastAge=age;
        AABB beforeMove=getBoundingBox();
        if(GoliathSlam.uses(kind,action)&&!moveGoliathSlam(age))return;
        if(action.drives(age)&&!blocked){
            Vec3 step=action==EncounterMove.DODGE?new Vec3(direction.z,0,-direction.x).scale(sequence%2==0?.38:-.38):direction.scale(action==EncounterMove.CHARGE?.40:.46);
            if(action==EncounterMove.POUNCE)step=direction.scale(Math.min(.46,Math.max(0,aim.subtract(position()).dot(direction)-kind.width*.35-.35)));
            if(action==EncounterMove.DIVE){Vec3 delta=aim.subtract(getBoundingBox().getCenter());step=delta.normalize().scale(Math.min(.48,Math.max(0,delta.length()-.7)));}
            var nextPos=position().add(step);if(!isWithinRestriction(net.minecraft.core.BlockPos.containing(nextPos))||!com.codex.blacklight.military.vehicle.VehicleDamageBridge.loaded((ServerLevel)level(),position(),nextPos))blocked=true;
            else {Vec3 before=position();move(MoverType.SELF,step);blocked=horizontalCollision||position().distanceToSqr(before)<step.lengthSqr()*.2;}
        }
        contactSweep=beforeMove.minmax(getBoundingBox()).inflate(.4,.15,.4);
        if(action==EncounterMove.CHARGE&&blocked){
            if(kind==EncounterKind.GOLIATH)crashGoliathCharge(age);
            else expose(40);
            return;
        }
        updateParts(); // Match the committed yaw and this tick's movement before other combat queries.
        GoliathGroundImpact.tick(this,age);
        if(source!=null&&source.mode.equals("juggernaut_fists")){
            if(JuggernautContact.uses(action))strikeFists(action,source,age);
            return;
        }
        if((action==EncounterMove.ERUPTION||action==EncounterMove.SLAM)&&age<action.contact&&age%5==0)ring(action.range,.025,ParticleTypes.SMOKE);
        if(source!=null&&source.mode.equals("radial_wave")&&age<source.contact()&&age%8==0)ring(8,.02,ParticleTypes.SMOKE);
        if(source==null?action.impact(age):source.impact(age)){
            if(action.projectile())shoot(action,target);
            else if(action==EncounterMove.HOWL&&(source==null||!source.mode.equals("radial_wave")))summonBrood(target);
            else if(action!=EncounterMove.SLAM||onGround())strike(action,age);
        }
    }
    private boolean moveGoliathSlam(int age){
        long epoch=chargeEpoch,started=entityData.get(START);
        Vec3 step=goliathSlamMotion.advance(age,yaw).scale(GoliathPresentation.SCALE),destination=position().add(step);
        var swept=getBoundingBox().minmax(getBoundingBox().move(step));var border=level().getWorldBorder();
        boolean available=isWithinRestriction(net.minecraft.core.BlockPos.containing(destination))
                &&com.codex.blacklight.military.vehicle.VehicleKinematics.loaded((ServerLevel)level(),swept)
                &&swept.minX>=border.getMinX()&&swept.maxX<=border.getMaxX()&&swept.minZ>=border.getMinZ()&&swept.maxZ<=border.getMaxZ();
        if(!available){cancelAttack();updateParts();return false;}
        if(step.lengthSqr()>1e-12){
            move(MoverType.SELF,step);
            // A block callback can reload/cancel/rebegin in this same tick.
            // Never apply the old action's contact or cleanup to its replacement.
            if(chargeEpoch!=epoch||entityData.get(START)!=started||move()!=EncounterMove.HAMMER)return false;
            if(disabled()){cancelAttack();updateParts();return false;}
        }
        // Rejected root travel is consumed by the source clock, not a debt.
        // A front-slam retreat/release wall contact is not a charging impact.
        blocked=false;
        return true;
    }
    private void tickGoliathCharge(LivingEntity target,int age){
        if(age==lastAge)return;lastAge=age;
        long epoch=chargeEpoch,started=entityData.get(START);
        // Include travel/impulses which ran in super.tick before the source root
        // move. A skipped sample or >8-block relocation is a discontinuity,
        // never an attack corridor through the teleport (MC safety adapter).
        boolean continuous=chargeContactOrigin!=null&&chargeContactTick+1==level().getGameTime()
                &&chargeContactOrigin.distanceToSqr(position())<=64;
        Vec3 previousOrigin=continuous?chargeContactOrigin:position();float previousYaw=continuous?chargeContactYaw:yaw;
        Vec3 delta=target==null?Vec3.ZERO:target.position().subtract(position());
        float wanted=target==null?yaw:(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        Vec3 local=goliathCharge.advance(age,goliathChargeInputs(target));syncGoliathCharge();
        float turn=GoliathCharge.turnStep(goliathChargeSnapshot(),age);
        if(turn>0)yaw=net.minecraft.util.Mth.approachDegrees(yaw,wanted,turn);
        setYRot(yaw);yBodyRot=yaw;yHeadRot=yaw;
        Vec3 step=local.yRot((float)Math.toRadians(-yaw)).scale(GoliathPresentation.SCALE),nextPos=position().add(step);
        var swept=getBoundingBox().minmax(getBoundingBox().move(step));var border=level().getWorldBorder();
        boolean available=isWithinRestriction(net.minecraft.core.BlockPos.containing(nextPos))
                &&com.codex.blacklight.military.vehicle.VehicleKinematics.loaded((ServerLevel)level(),swept)
                &&swept.minX>=border.getMinX()&&swept.maxX<=border.getMaxX()&&swept.minZ>=border.getMinZ()&&swept.maxZ<=border.getMaxZ();
        boolean horizontalBlocked=false;
        if(available&&step.lengthSqr()>1e-12){
            Vec3 before=position();move(MoverType.SELF,step);
            // Block collision callbacks can replace or cancel this actor too.
            if(chargeEpoch!=epoch||entityData.get(START)!=started||move()!=EncounterMove.CHARGE)return;
            if(disabled()){cancelAttack();return;}
            horizontalBlocked=step.horizontalDistanceSqr()>1e-12&&(horizontalCollision
                    ||position().subtract(before).horizontalDistanceSqr()<step.horizontalDistanceSqr()*.2);
        }
        var obstruction=GoliathCharge.obstruction(goliathChargeSnapshot(),available,horizontalBlocked);
        if(obstruction==GoliathCharge.Obstruction.CANCEL){cancelAttack();return;}
        if(obstruction==GoliathCharge.Obstruction.EXPOSE){
            Vec3 blockedTravel=nextPos.subtract(position());
            crashGoliathCharge(age);
            // Sample the original contact first. Folding the barrier removes its
            // collision and would move the debris impact up into the floor above.
            GoliathTerrainImpact.wall(this,blockedTravel);
            com.codex.blacklight.world.ConvoyRail.crash((ServerLevel)level(),getBoundingBox().expandTowards(blockedTravel).inflate(.15,0,.15));
            return;
        }
        // A clipped windup/return consumes this tick's root request. It must not
        // become a charge collision or accumulate a displacement for later.
        blocked=false;
        updateParts();
        strikeGoliathCharge(age,previousOrigin,previousYaw);
        if(chargeEpoch!=epoch||entityData.get(START)!=started){updateParts();return;}
        if(move()!=EncounterMove.CHARGE||disabled()){cancelAttack();updateParts();return;}
        chargeContactOrigin=position();chargeContactYaw=yaw;chargeContactTick=level().getGameTime();
        // Decide the next segment only after the outgoing segment's final Hit
        // sample, so a newly accepted player hit cannot queue another cycle.
        goliathCharge.afterContact(goliathChargeInputs(target));syncGoliathCharge();
        if(goliathCharge.done()){
            goliathCharge.cancel();syncGoliathCharge();entityData.set(MOVE,0);committed=null;struck.clear();fistHits.clear();
            chargeContactOrigin=null;chargeContactTick=Long.MIN_VALUE;
            // Preserve the existing post-action MC recharge after the actual
            // number of source cycles, not the obsolete fixed96tick estimate.
            readyAt.put(EncounterMove.CHARGE,level().getGameTime()+EncounterTactics.recharge(EncounterMove.CHARGE));
            cooldown=Math.max(12,EncounterMove.CHARGE.recovery-phase()*3);
            expose(60);
        }
        updateParts();
    }
    private GoliathCharge.Inputs goliathChargeInputs(LivingEntity target){
        // Native global Player maps to the committed player in this multiplayer
        // encounter. Other targets do not manufacture the player-only repeat.
        // onGround is the MC support proxy; the three unbound script flags are0.
        if(target==null)return new GoliathCharge.Inputs(false,getY(),getY(),false,0,0,0,0);
        boolean player=target instanceof Player&&enemy(target);
        Vec3 delta=target.position().subtract(position());
        float wanted=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        return new GoliathCharge.Inputs(player,getY(),target.getY(),player&&target.onGround(),
                net.minecraft.util.Mth.wrapDegrees(wanted-yaw),0,0,0);
    }
    private void strikeGoliathCharge(int age,Vec3 previousOrigin,float previousYaw){
        if(goliathChargeSnapshot().phase()>=7){strikeGoliathTaunt(age,previousOrigin,previousYaw);return;}
        var spheres=GoliathCharge.spheres(goliathChargeSnapshot(),age,position(),yaw,GoliathPresentation.SCALE);var bounds=GoliathCharge.bounds(spheres);
        if(bounds==null)return;
        Vec3 anchor=GoliathTaunt.bodyAnchor(goliathChargeSnapshot(),age,position(),yaw,GoliathPresentation.SCALE);
        var server=(ServerLevel)level();var seen=new HashSet<UUID>();long started=entityData.get(START),epoch=chargeEpoch;
        // Query multipart proxies as well as actor bodies, then test source
        // joint spheres against the same visible skin used by other attacks.
        for(var candidate:level().getEntities(this,bounds,e->!e.isRemoved())){
            // A recipient's damage callback can cancel or kill the attacker.
            // Never carry the old segment's spheres into another accepted hit.
            if(chargeEpoch!=epoch||move()!=EncounterMove.CHARGE||entityData.get(START)!=started||disabled())return;
            var victim=CombatTargeting.resolveTarget(candidate);
            if(victim==null||!seen.add(victim.getUUID())||struck.contains(victim.getUUID())||!enemy(victim)
                    ||com.codex.blacklight.firearms.FirearmsCombat.protectedTarget(this,victim))continue;
            var body=AnimatedHurtBody.of(victim);Vec3 point=null,origin=null;String bone=null;
            double nearest=Double.POSITIVE_INFINITY;
            for(var sphere:spheres){
                if(body!=null){
                    for(var region:body.hurtRegions(0))for(var box:region.boxes()){
                        var at=GoliathCharge.contact(sphere,box);if(at==null)continue;
                        double distance=sphere.center().distanceToSqr(at);
                        if(distance>=nearest||!chargeContactVisible(server,anchor,sphere.center())||!chargeContactVisible(server,sphere.center(),at))continue;
                        point=at;origin=sphere.center();bone=region.bone();nearest=distance;
                    }
                }else for(var box:CombatTargeting.hitboxes(victim)){
                    var at=GoliathCharge.contact(sphere,box);if(at==null)continue;
                    double distance=sphere.center().distanceToSqr(at);
                    if(distance>=nearest||!chargeContactVisible(server,anchor,sphere.center())||!chargeContactVisible(server,sphere.center(),at))continue;
                    point=at;origin=sphere.center();nearest=distance;
                }
            }
            if(point==null)continue;
            damageContact=point;
            try{
                var source=damageSources().mobAttack(this);float amount=(float)kind.damage*EncounterMove.CHARGE.damageScale();
                var normal=origin.subtract(point).normalize();if(normal.lengthSqr()<.01)normal=new Vec3(0,1,0);
                boolean accepted=body==null?victim.hurt(source,amount):body.hurtAt(source,amount,
                        new HydraHurtContact.Contact(bone,point,normal,com.codex.blacklight.infection.InfectedAttackStyle.CRUSH));
                if(chargeEpoch!=epoch||move()!=EncounterMove.CHARGE||entityData.get(START)!=started||disabled())return;
                if(accepted){
                    // One accepted blow per victim remains the MC damage adapter;
                    // the native repeat-hit sentinel has not been ported.
                    struck.add(victim.getUUID());if(victim instanceof Player)goliathCharge.playerHit();
                    Vec3 force=point.subtract(origin).multiply(1,0,1).normalize();
                    victim.knockback(.45,-force.x,-force.z);
                    if(chargeEpoch!=epoch||move()!=EncounterMove.CHARGE||entityData.get(START)!=started||disabled())return;
                    EncounterImpact.confirmed(this,victim,false,1);
                }
            }finally{damageContact=null;}
        }
    }
    private boolean chargeContactVisible(ServerLevel server,Vec3 from,Vec3 to){
        var border=server.getWorldBorder();
        return from.x>=border.getMinX()&&from.x<=border.getMaxX()&&from.z>=border.getMinZ()&&from.z<=border.getMaxZ()
                &&to.x>=border.getMinX()&&to.x<=border.getMaxX()&&to.z>=border.getMinZ()&&to.z<=border.getMaxZ()
                &&com.codex.blacklight.military.vehicle.VehicleDamageBridge.loaded(server,from,to)
                &&level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;
    }
    private void strikeGoliathTaunt(int age,Vec3 previousOrigin,float previousYaw){
        var windows=GoliathTaunt.windows(goliathChargeSnapshot(),age,previousOrigin,previousYaw,position(),yaw,GoliathPresentation.SCALE);
        // A hand may already have crossed a wall during non-damaging windup.
        // Its short local sweep alone cannot establish access from the body.
        Vec3 anchor=GoliathTaunt.bodyAnchor(goliathChargeSnapshot(),age,position(),yaw,GoliathPresentation.SCALE);
        var server=(ServerLevel)level();long started=entityData.get(START),epoch=chargeEpoch;
        for(var window:windows){
            var bounds=GoliathTaunt.bounds(window);if(bounds==null)continue;
            var hit=fistHits.computeIfAbsent(window.id(),key->new HashSet<>());var seen=new HashSet<UUID>();
            // Inner/outer source rings share one ID. The source adapter orders
            // the stronger inner ring first, so accepted damage cannot double.
            for(var candidate:level().getEntities(this,bounds,e->!e.isRemoved())){
                if(chargeEpoch!=epoch||move()!=EncounterMove.CHARGE||entityData.get(START)!=started||disabled())return;
                var victim=CombatTargeting.resolveTarget(candidate);
                if(victim==null||!seen.add(victim.getUUID())||hit.contains(victim.getUUID())||!enemy(victim)
                        ||com.codex.blacklight.firearms.FirearmsCombat.protectedTarget(this,victim))continue;
                var body=AnimatedHurtBody.of(victim);ProjectileSphereContact.Hit contact=null;String bone=null;
                for(var sweep:window.sweeps()){
                    if(body!=null){
                        for(var region:body.hurtRegions(0))for(var box:region.boxes()){
                            var found=GoliathTaunt.contact(sweep,box);
                            if(found==null||contact!=null&&found.fraction()>=contact.fraction()
                                    ||!chargeContactVisible(server,anchor,found.center())
                                    ||!chargeContactVisible(server,sweep.from(),found.center())
                                    ||!chargeContactVisible(server,found.center(),found.point()))continue;
                            contact=found;bone=region.bone();
                        }
                    }else for(var box:CombatTargeting.hitboxes(victim)){
                        var found=GoliathTaunt.contact(sweep,box);
                        if(found==null||contact!=null&&found.fraction()>=contact.fraction()
                                ||!chargeContactVisible(server,anchor,found.center())
                                ||!chargeContactVisible(server,sweep.from(),found.center())
                                ||!chargeContactVisible(server,found.center(),found.point()))continue;
                        contact=found;
                    }
                }
                if(contact==null)continue;
                damageContact=contact.point();
                try{
                    // Original bullrush750 remains the existing MC attack
                    // baseline. Preserve100/750/400 relative strength; native
                    // Prop-only5000 does not become actor or block damage.
                    var source=damageSources().mobAttack(this);
                    float amount=(float)kind.damage*EncounterMove.CHARGE.damageScale()*window.sourceDamage()/750F;
                    boolean accepted=body==null?victim.hurt(source,amount):body.hurtAt(source,amount,
                            new HydraHurtContact.Contact(bone,contact.point(),contact.normal(),com.codex.blacklight.infection.InfectedAttackStyle.CRUSH));
                    if(chargeEpoch!=epoch||move()!=EncounterMove.CHARGE||entityData.get(START)!=started||disabled())return;
                    if(accepted){
                        hit.add(victim.getUUID());
                        Vec3 force=contact.point().subtract(contact.center()).multiply(1,0,1).normalize();
                        victim.knockback(.45,-force.x,-force.z);
                    if(chargeEpoch!=epoch||move()!=EncounterMove.CHARGE||entityData.get(START)!=started||disabled())return;
                    EncounterImpact.confirmed(this,victim,false,1);
                    }
                }finally{damageContact=null;}
            }
        }
    }
    private void strikeFists(EncounterMove move,SourceBossAction source,int age){
        for(int i=0;i<source.windows.length;i++){
            var w=source.windows[i];if(!JuggernautContact.active(w,age-1,age))continue;
            if(age==(int)Math.ceil(w.begin()*20))ModInfectedSounds.play(this,com.codex.blacklight.infection.InfectedSoundCue.SWING);
            // Both slam fists form one blow; the two combo releases are independent.
            var hit=fistHits.computeIfAbsent(move==EncounterMove.SLAM?0:i,k->new HashSet<>());
            for(var victim:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(5,3,5),this::enemy)){
                if(hit.contains(victim.getUUID())||!hasLineOfSight(victim))continue;
                Vec3 at=JuggernautContact.contact(move,w,age-1,age,position(),yaw,victim.getBoundingBox());if(at==null)continue;
                damageContact=at;
                try{if(victim.hurt(damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE)*(move==EncounterMove.SLAM?1.5F:.85F))){
                    hit.add(victim.getUUID());struck.add(victim.getUUID());
                    Vec3 force=victim.position().subtract(position()).multiply(1,0,1).normalize();
                    victim.knockback(move==EncounterMove.SLAM?.85:.42,-force.x,-force.z);
                    EncounterImpact.confirmed(this,victim,move==EncounterMove.SLAM,1);
                }}finally{damageContact=null;}
            }
        }
        if(move==EncounterMove.SLAM&&!fistGround&&age>=source.contact()&&age<=source.contact()+8)fistGroundContact(age);
    }
    private void fistGroundContact(int age){
        if(!(level() instanceof ServerLevel server))return;
        for(int step=0;step<=4;step++)for(boolean left:new boolean[]{true,false}){
            float t=age-1+step/4F;var hand=JuggernautContact.fist(EncounterMove.SLAM,t,left,position(),yaw);
            for(Vec3 corner:hand.corners()){
                // Actual oriented cube corners, checked against the struck block's shape.
                if(!level().hasChunkAt(net.minecraft.core.BlockPos.containing(corner)))continue;
                var contact=level().clip(new ClipContext(corner.add(0,.06,0),corner.add(0,-.08,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
                if(contact.getType()!=HitResult.Type.BLOCK||contact.getDirection()!=net.minecraft.core.Direction.UP)continue;
                var state=level().getBlockState(contact.getBlockPos());Vec3 at=contact.getLocation();fistGround=true;
                // Diagnostics are runtime-only and do not change the save format.
                lastGroundAge=age;lastGroundPoint=at;groundImpacts++;
                TerrainImpactEffects.emit(this,contact,3.2F,true);
                for(var victim:level().getEntitiesOfClass(LivingEntity.class,new AABB(at,at).inflate(2.1,.8,2.1),this::enemy)){
                    if(struck.contains(victim.getUUID())||!supportedForShock(victim)||!hasLineOfSight(victim)||HunterContactPose.nearest(victim.getBoundingBox(),at).distanceToSqr(at)>4.41)continue;
                    damageContact=HunterContactPose.nearest(victim.getBoundingBox(),at);
                    try{if(victim.hurt(damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE)*.25F)){
                        struck.add(victim.getUUID());fistHits.computeIfAbsent(0,k->new HashSet<>()).add(victim.getUUID());
                        victim.knockback(.35,at.x-victim.getX(),at.z-victim.getZ());EncounterImpact.confirmed(this,victim,true,.45F);
                    }}finally{damageContact=null;}
                }return;
            }
        }
    }
    private int lastGroundAge=-1,groundImpacts;
    private boolean supportedForShock(LivingEntity victim){
        if(victim.onGround())return true;
        // No-AI and some mod entities leave onGround false while resting on a block.
        // Check the actual support shape rather than making them immune to shock.
        Vec3 feet=victim.position();var support=level().clip(new ClipContext(feet.add(0,.08,0),feet.add(0,-.15,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        return support.getType()==HitResult.Type.BLOCK&&support.getDirection()==net.minecraft.core.Direction.UP;
    }
    private Vec3 lastGroundPoint;
    public Vec3 lastGroundPoint(){return lastGroundPoint;}
    public int lastGroundAge(){return lastGroundAge;}public int groundImpacts(){return groundImpacts;}
    private void updateBossBars(){
        if(kind==EncounterKind.ELIZABETH_GREENE && greeneClose.opening() && !greeneBattle.targetable()){
            bar.setVisible(false);defenseBar.setVisible(false);return;
        }
        if(goddess.active()){
            bar.setName(net.minecraft.network.chat.Component.translatable("boss.blacklight.greene_goddess.name"));
            bar.setProgress(goddess.zeroOnBar()?0:Math.max(0,Math.min(1,getHealth()/getMaxHealth())));
            bar.setColor(BossEvent.BossBarColor.RED);
            bar.setVisible(isAlive()&&kind.enabled());
            defenseBar.setVisible(false);
            return;
        }
        if(spider.first()){
            bar.setName(net.minecraft.network.chat.Component.translatable("boss.blacklight.greene_spider.name"));
            bar.setProgress(greeneStage()==GreeneBattleCycle.MOLTING?0:Math.max(0,Math.min(1,getHealth()/getMaxHealth())));
            bar.setVisible(isAlive() && (spider.live() || greeneStage()==GreeneBattleCycle.MOLTING));defenseBar.setVisible(false);return;
        }
        bar.setProgress(Math.max(0,Math.min(1,getHealth()/getMaxHealth())));bar.setVisible(kind.enabled()&&kind.boss&&isAlive());
        if(!kind.enabled()){defenseBar.setVisible(false);return;}
        bar.setOverlay(BossEvent.BossBarOverlay.NOTCHED_10);
        boolean counterWindow = kind == EncounterKind.ELIZABETH_GREENE ? greeneBattle.coreDamageable() : exposed();
        bar.setColor(counterWindow ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED);
        bar.setName(greeneOutbreak() && greeneBattle.armourWave()>1
                ?net.minecraft.network.chat.Component.translatable("boss.blacklight.greene_goddess.name")
                :net.minecraft.network.chat.Component.translatable("boss.blacklight.phase",getDisplayName(),phase()+1));
        defenseBar.setVisible(kind.boss&&isAlive()&&(parts.length>0||exposed()));
        if(kind==EncounterKind.GOLIATH){
            var window=goliathFinisherReady()?GoliathWindow.FINISH:goliathWindow();
            int slot=broken(1)||!broken(0)&&armor[2]>0?2:broken(0)?1:0;
            var part=slot==2?GoliathLockPart.HEAD:slot==1?GoliathLockPart.RIGHT_ARM:GoliathLockPart.RIGHT_LEG;
            boolean down=window==GoliathWindow.LEG_FALL||window==GoliathWindow.ARM_FALL;
            String cue=switch(window){
                case NONE -> !broken(0)?"stagger":broken(1)?"evade_slam":"evade_spike";
                case LEG_FALL, ARM_FALL -> "down";
                case FINISH -> "finish";
                default -> "open";
            };
            defenseBar.setColor(window==GoliathWindow.NONE?BossEvent.BossBarColor.PURPLE:BossEvent.BossBarColor.YELLOW);
            defenseBar.setName(net.minecraft.network.chat.Component.translatable("boss.blacklight.goliath."+cue,
                    net.minecraft.network.chat.Component.translatable(part.labelKey)));
            float progress=down?1-exposedAge(0)/exposedDuration():window==GoliathWindow.FINISH?0:armor[slot]/90;
            defenseBar.setProgress(Math.max(0,Math.min(1,progress)));
            return;
        }
        if(exposed()){
            defenseBar.setColor(BossEvent.BossBarColor.YELLOW);
            defenseBar.setName(net.minecraft.network.chat.Component.translatable(counterWindow ? "boss.blacklight.exposed" : "boss.blacklight.collapsing"));
            defenseBar.setProgress(kind==EncounterKind.ELIZABETH_GREENE?Math.max(0,Math.min(1,1-exposedAge(0)/Math.max(1,exposedDuration()))):Math.max(0,Math.min(1,(entityData.get(EXPOSED)-level().getGameTime())/(float)Math.max(1,entityData.get(EXPOSED)-entityData.get(EXPOSED_START)))));
        }else{
            int remaining=parts.length-Integer.bitCount(entityData.get(BROKEN));float total=0;for(int i=0;i<parts.length;i++)total+=Math.max(0,armor[i]);
            defenseBar.setColor(BossEvent.BossBarColor.PURPLE);
            defenseBar.setName(net.minecraft.network.chat.Component.translatable("boss.blacklight.weakpoints",remaining,parts.length));
            defenseBar.setProgress(parts.length==0?0:Math.max(0,Math.min(1,total/((kind==EncounterKind.ELIZABETH_GREENE?greeneSegmentMaximum():90)*parts.length))));
        }
    }
    private void strike(EncounterMove action,int age){
        if(GoliathSlam.uses(kind,action)){strikeGoliathSlam(age);return;}
        var source=SourceBossAction.get(kind,action);boolean wave=source!=null&&source.mode.equals("radial_wave"),sphere=wave||source!=null&&source.mode.equals("goliath_slam");
        if(action==EncounterMove.DOUBLE)struck.clear();double reach=actionRange(action)+kind.width*.25;
        if(sphere)reach=source.worldRadius(age);
        if(action==EncounterMove.SLAM||action==EncounterMove.ERUPTION)reach=action.range*Math.min(1,(age-action.contact+1)/11D);
        AABB candidates=sphere?SourceRadialWave.bounds(source,age,position(),getYRot()):getBoundingBox().inflate(reach,2,reach);
        if(candidates==null)return;
        for(var victim:level().getEntitiesOfClass(LivingEntity.class,candidates,this::enemy)){
            // Travel/targeting range is never the impact hitbox for a moving body.
            if((action==EncounterMove.CHARGE||action==EncounterMove.POUNCE||action==EncounterMove.DIVE)&&(contactSweep==null||!contactSweep.intersects(victim.getBoundingBox())))continue;
            if(struck.contains(victim.getUUID()))continue;
            float sourceScale=1;
            if(sphere){
                var contact=SourceRadialWave.contact(source,age,position(),getYRot(),victim.getBoundingBox());
                // Minecraft cover rule: trace from the decoded Hit origin to the
                // sphere's actual contact, never from the boss's elevated eyes.
                if(contact==null||level().clip(new ClipContext(contact.origin(),contact.point(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS)continue;
                sourceScale=contact.damageScale();
            }else if(!hasLineOfSight(victim)||distanceTo(victim)>reach+victim.getBbWidth()*.5||Math.abs(victim.getY()-getY())>Math.max(3,kind.height*.6)||!CreatureAttack.inArc(victim.getX()-getX(),victim.getZ()-getZ(),yaw,Math.cos(Math.toRadians(action.arc*.5))))continue;
            if(source!=null&&source.mode.equals("joint_sweep")&&!sourceJointContact(source,age,victim.getBoundingBox()))continue;
            if(sourceScale<=0)continue;
            if(victim.hurt(damageSources().mobAttack(this),(float)kind.damage*action.damageScale()*sourceScale)){
                struck.add(victim.getUUID());Vec3 force=victim.position().subtract(position()).multiply(1,0,1).normalize();
                boolean heavy=wave||action==EncounterMove.HAMMER||action==EncounterMove.SLAM;
                victim.knockback(wave?.95*sourceScale:heavy?.85:.45,-force.x,-force.z);
                if(source!=null)EncounterImpact.confirmed(this,victim,wave,sourceScale);
            }
        }
        if(age==actionContact(action)&&!GoliathSlam.uses(kind,action)){playSound(SoundEvents.IRON_GOLEM_ATTACK,1,kind.height>4?.6F:1);
            var block=level().getBlockState(blockPosition().below());if(!block.isAir()&&(action==EncounterMove.SLAM||action==EncounterMove.ERUPTION))((ServerLevel)level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,block),getX(),getY()+.12,getZ(),35,kind.width,.1,kind.width,.14);
        }
        if(action==EncounterMove.SLAM||action==EncounterMove.ERUPTION)ring(reach,.06,ParticleTypes.CRIT);
        if(sphere&&!wave&&!GoliathSlam.uses(kind,action)&&age==source.contact())ring(reach,.06,ParticleTypes.CRIT);
        if(wave&&age%3==0)ring(reach,.06,ParticleTypes.CLOUD);
        if(action==EncounterMove.ERUPTION&&(age-action.contact)%4==0){
            for(int i=0;i<6;i++){double a=i*Math.PI/3;Vec3 p=position().add(Math.sin(a)*reach,0,Math.cos(a)*reach);
                if(!level().hasChunkAt(net.minecraft.core.BlockPos.containing(p)))continue;
                var ground=level().clip(new ClipContext(p.add(0,2,0),p.add(0,-3,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
                if(ground.getType()!=HitResult.Type.BLOCK)continue;var spike=ModEntities.GROUND_SPIKE.get().create(level());if(spike!=null){spike.setPos(ground.getLocation());spike.setYRot((float)Math.toDegrees(a));level().addFreshEntity(spike);}
            }
        }
    }
    private void strikeGoliathSlam(int age){
        var spheres=GoliathSlam.spheres(age,position(),getYRot(),GoliathPresentation.SCALE);var bounds=GoliathSlam.bounds(spheres);
        if(bounds==null)return;
        var server=(ServerLevel)level();var seen=new HashSet<UUID>();
        long started=entityData.get(START),epoch=chargeEpoch;
        for(var candidate:level().getEntities(this,bounds,e->!e.isRemoved())){
            if(chargeEpoch!=epoch||move()!=EncounterMove.HAMMER||entityData.get(START)!=started||disabled())return;
            var victim=CombatTargeting.resolveTarget(candidate);
            if(victim==null||!seen.add(victim.getUUID())||struck.contains(victim.getUUID())||!enemy(victim)
                    ||com.codex.blacklight.firearms.FirearmsCombat.protectedTarget(this,victim))continue;
            var body=AnimatedHurtBody.of(victim);GoliathSlam.Contact contact=null;
            for(var sphere:spheres){
                if(body!=null){
                    for(var region:body.hurtRegions(0))for(var box:region.boxes()){
                        var found=GoliathSlam.contact(sphere,region.bone(),box);
                        if(found!=null&&chargeContactVisible(server,getEyePosition(),found.origin())
                                &&chargeContactVisible(server,found.origin(),found.point())){contact=found;break;}
                    }
                }else for(var box:CombatTargeting.hitboxes(victim)){
                    var found=GoliathSlam.contact(sphere,null,box);
                    if(found!=null&&chargeContactVisible(server,getEyePosition(),found.origin())
                            &&chargeContactVisible(server,found.origin(),found.point())){contact=found;break;}
                }
                if(contact!=null)break; // Stronger inner ring wins; never add both.
            }
            if(contact==null)continue;
            damageContact=contact.point();
            try{
                var source=damageSources().mobAttack(this);
                float amount=(float)kind.damage*EncounterMove.HAMMER.damageScale()*contact.damageScale();
                boolean accepted=body==null?victim.hurt(source,amount):body.hurtAt(source,amount,
                        new HydraHurtContact.Contact(contact.bone(),contact.point(),contact.normal(),com.codex.blacklight.infection.InfectedAttackStyle.CRUSH));
                if(chargeEpoch!=epoch||move()!=EncounterMove.HAMMER||entityData.get(START)!=started||disabled())return;
                if(accepted){
                    struck.add(victim.getUUID());var force=contact.point().subtract(contact.origin()).multiply(1,0,1).normalize();
                    victim.knockback(.85,-force.x,-force.z);
                    if(chargeEpoch!=epoch||move()!=EncounterMove.HAMMER||entityData.get(START)!=started||disabled())return;
                    EncounterImpact.confirmed(this,victim,false,contact.damageScale());
                }
            }finally{damageContact=null;}
        }
    }
    private boolean sourceJointContact(SourceBossAction action,int age,AABB victim){
        var table=SourceBossMotion.get(kind);var now=table.actionPose(action,age);var before=table.actionPose(action,Math.max(0,age-1));
        for(var window:action.windows){
            if(age<Math.ceil(window.begin()*20-.001F)||age>Math.floor(window.end()*20+.001F))continue;
            String bone=switch(window.joint()){case "Wrist_R"->"right_hand";case "Wrist_L"->"left_hand";case "Elbow_R"->"right_forearm";case "Elbow_L"->"left_forearm";default->null;};
            if(bone==null)continue;
            var a=table.point(before,bone);var b=table.point(now,bone);if(a==null||b==null)continue;
            Vec3 from=sourcePoint(a),to=sourcePoint(b);
            if(DevastatorCollision.capsuleDistanceSquared(from,to,victim)<=window.radius()*window.radius())return true;
        }return false;
    }
    private Vec3 sourcePoint(org.joml.Vector3f point){double a=Math.toRadians(getYRot()),x=-point.x,z=-point.z;return position().add(x*Math.cos(a)-z*Math.sin(a),point.y,x*Math.sin(a)+z*Math.cos(a));}
    private void ring(double radius,double speed,SimpleParticleType particle){var s=(ServerLevel)level();int points=Math.max(18,Math.min(144,(int)(radius*5)));for(int i=0;i<points;i++){double a=i*2*Math.PI/points;s.sendParticles(particle,getX()+Math.sin(a)*radius,getY()+.15,getZ()+Math.cos(a)*radius,1,0,.05,0,speed);}}
    private void shoot(EncounterMove action,LivingEntity target){
        if(kind==EncounterKind.ELIZABETH_GREENE||!hasLineOfSight(target))return;int count=action==EncounterMove.ORBS?5:1;
        Vec3 muzzle=position().add(0,Math.min(kind.height*.65,4),0);
        for(int i=0;i<count;i++){var shot=new EncounterProjectile(level(),this,action==EncounterMove.GRENADE);shot.setPos(muzzle);
            var solution=BlockThrowBallistics.solve(shot.position(),aim,Vec3.ZERO,1.1,.03);if(solution==null)continue;var v=solution.velocity().yRot((i-(count-1)*.5F)*.12F);shot.shoot(v.x,v.y,v.z,1.1F,0);level().addFreshEntity(shot);
        }playSound(SoundEvents.SLIME_SQUISH,.8F,.65F);
    }
    private void summonBrood(LivingEntity target){
        var server=(ServerLevel)level();brood.removeIf(id->!(server.getEntity(id) instanceof LivingEntity living)||!living.isAlive());
        if(brood.size()>=4||!server.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING))return;
        for(int i=0;i<2&&brood.size()<4;i++){
            double a=(sequence+i)*2.4;Vec3 pos=position().add(Math.sin(a)*(kind.width+2),0,Math.cos(a)*(kind.width+2));
            if(!server.hasChunkAt(net.minecraft.core.BlockPos.containing(pos))||!server.getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(pos)))continue;
            var child=ModEntities.BLACKLIGHT_STALKER.get().create(server);if(child==null)continue;child.setPos(pos);
            if(!server.noCollision(child)||server.getBlockState(child.blockPosition().below()).isAir())continue;
            child.getPersistentData().putUUID("EncounterOwner",getUUID());child.setTarget(target);if(server.addFreshEntity(child))brood.add(child.getUUID());
        }
    }
    public boolean hurtPart(int slot,DamageSource source,float amount){
        if(kind==EncounterKind.GOLIATH){
            if(slot<0||slot>1)return false;
            // Legacy weak_1 is a visual on the healthy leg, not the native wrist.
            var region=hurtRegions(0).get(slot);
            if(region.boxes().isEmpty())return false;
            return hurtAt(source,amount,new HydraHurtContact.Contact(region.bone(),region.bounds().getCenter(),new Vec3(0,1,0),com.codex.blacklight.infection.InfectedAttackStyle.TEAR));
        }
        return hurtPart(slot,source,amount,true);
    }
    private boolean hurtPart(int slot,DamageSource source,float amount,boolean physicalContact){
        long epoch=greeneStateEpoch;
        if(kind==EncounterKind.ELIZABETH_GREENE)greeneBattle.refreshHealthScale();
        if(kind==EncounterKind.ELIZABETH_GREENE&&!greeneBattle.targetable())return false;
        if(level().isClientSide||!isAlive()||slot<0||slot>=parts.length||broken(slot)||!Float.isFinite(amount)||amount<=0||isInvulnerableTo(source)||ConsumeControl.protectedEntity(this)||level().getGameTime()-armorHit[slot]<10)return false;
        if(source.getEntity() instanceof LivingEntity attacker&&isAlliedTo(attacker))return false;
        if(kind!=EncounterKind.ELIZABETH_GREENE&&!super.hurt(source,amount*.15F))return false;
        float healthDamage=kind==EncounterKind.ELIZABETH_GREENE?(float)Math.min(Float.MAX_VALUE,(double)amount*com.codex.blacklight.power.CombatScale.units(this)):amount;
        BossSiteData.participant(this, source.getEntity());
        if(kind==EncounterKind.ELIZABETH_GREENE && greeneClose.counterOpening(slot)) cooldown=Math.max(cooldown,24);
        armorHit[slot]=level().getGameTime();armor[slot]=Math.max(0,armor[slot]-healthDamage);
        if(kind==EncounterKind.ELIZABETH_GREENE){
            if(source.getEntity() instanceof LivingEntity attacker){setLastHurtByMob(attacker);lastThreatAt=level().getGameTime();}
            if(physicalContact){
                var prior=incomingContact;
                if(incomingContact==null){var region=hurtRegions(0).get(slot);Vec3 from=source.getSourcePosition()==null?position():source.getSourcePosition();
                    Vec3 point=region.boxes().stream().map(b->b.nearest(from)).min(Comparator.comparingDouble(from::distanceToSqr)).orElse(region.bounds().getCenter());
                    incomingContact=new HydraHurtContact.Contact(region.bone(),point,from.subtract(point).normalize(),com.codex.blacklight.infection.InfectedAttackStyle.TEAR);
                }
                try{confirmedIncoming(source,amount);}finally{incomingContact=prior;}
            }
        }
        if(kind==EncounterKind.ELIZABETH_GREENE&&(epoch!=greeneStateEpoch||!isAlive()||isRemoved()))return true;
        if(armor[slot]<=0){
            // Copy the dying child's visible pose BEFORE the final break can
            // enter collapse. Commit BROKEN before the spawn callback, so a
            // reentrant save cannot spawn this corpse twice from the same hit.
            GreeneSegmentCorpse corpse=kind==EncounterKind.ELIZABETH_GREENE&&(!greeneOutbreak()||greeneStrikingSlot()==slot)?new GreeneSegmentCorpse(this,slot):null;
            if(corpse!=null) {
                var point=physicalContact&&incomingContact!=null?incomingContact.point():hurtRegions(0).get(slot).bounds().getCenter();
                GreeneFeedback.broken(this,point);
                BossImpactFeedback.contact(this,source,point,BossImpactFeedback.Contact.BREAK,amount);
            }
            entityData.set(BROKEN,entityData.get(BROKEN)|(1<<slot));
            if(corpse!=null){
                level().addFreshEntity(corpse);
                if(epoch!=greeneStateEpoch||!isAlive()||isRemoved())return true;
            }else playSound(SoundEvents.IRON_GOLEM_DAMAGE,1,.6F);
            if(entityData.get(BROKEN)==(1<<parts.length)-1){expose(140);}
        }return true;
    }
    /** Prototype weapon sessions pass their already-authorized swept contact volume. */
    public boolean hurtWeapon(DamageSource source,float amount,WeaponCutContact.Report report,java.util.function.Predicate<AABB> bluntContact){
        if(kind==EncounterKind.GOLIATH){
            var skin=hurtRegions(0);
            if(!report.contacts().isEmpty()){
                var contact=HydraHurtContact.reportedContact(skin,report,goliathWindow().part);
                return contact!=null&&hurtAt(source,amount,contact);
            }
            var active=goliathWindow().part;
            var regions=active==null?skin:skin.stream().sorted(Comparator.comparing(r->!active.acceptsDamage(r.bone()))).toList();
            for(var region:regions)for(var box:region.boxes())if(bluntContact.test(box.bounds)){
                var from=source.getSourcePosition();
                return hurtAt(source,amount,new HydraHurtContact.Contact(region.bone(),from==null?box.center:box.nearest(from),new Vec3(0,1,0),com.codex.blacklight.infection.InfectedAttackStyle.CRUSH));
            }
            return false;
        }
        if(kind==EncounterKind.ELIZABETH_GREENE)for(var region:hurtRegions(0))if(region.bone().startsWith("weak_"))for(var box:region.boxes()){
            for(var contact:report.contacts())if(box.nearest(contact.point()).distanceToSqr(contact.point())<.08*.08)return hurtAt(source,amount,new HydraHurtContact.Contact(region.bone(),contact.point(),contact.travel().normalize().scale(-1),com.codex.blacklight.infection.InfectedAttackStyle.TEAR));
            if(bluntContact.test(box.bounds))return hurtAt(source,amount,new HydraHurtContact.Contact(region.bone(),box.center,new Vec3(0,1,0),com.codex.blacklight.infection.InfectedAttackStyle.CRUSH));
        }
        if(kind!=EncounterKind.ELIZABETH_GREENE&&kind!=EncounterKind.GOLIATH)for(var part:parts)if(part.isPickable()){
            var box=part.getBoundingBox();boolean sharp=report.contacts().stream().anyMatch(c->box.inflate(.08).contains(c.point()));
            if(sharp||bluntContact.test(box))return hurtPart(part.slot,source,amount);
        }
        return hurt(source,amount);
    }
    @Override public boolean hurt(DamageSource source,float amount){
        if(!Float.isFinite(amount)||amount<=0)return false;
        if(kind==EncounterKind.ELIZABETH_GREENE){
            if(goddess.active()||!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))return hurtGreeneCore(source,amount);
            // A bypassing hit can arrive reentrantly inside another hurt hook.
            // Its transaction must not inherit that outer core floor.
            int prior=greeneOwnDamageDepth;greeneOwnDamageDepth=0;
            try{return super.hurt(source,amount);}finally{greeneOwnDamageDepth=prior;}
        }
        if(kind==EncounterKind.GOLIATH)return hurtGoliath(source,amount);
        float scale=1,poiseDamage=0;boolean guardContact=false;
        if(kind!=null&&!exposed()&&amount<getMaxHealth()*5){
            if(kind.armored()||move()==EncounterMove.GUARD){
                Vec3 from=source.getSourcePosition();boolean front=from!=null&&CreatureAttack.inArc(from.x-getX(),from.z-getZ(),getYRot(),Math.cos(Math.toRadians(70)));
                if(front){
                    guardContact=kind==EncounterKind.JUGGERNAUT&&move()==EncounterMove.GUARD&&JuggernautContact.guarding(moveAge(0));
                    scale=kind==EncounterKind.JUGGERNAUT&&!guardContact?.65F:.25F;poiseDamage=Math.min(amount,50);
                }
            }
        }
        boolean accepted=super.hurt(source,amount*scale);
        if (accepted) BossSiteData.participant(this, source.getEntity());
        if(accepted&&!level().isClientSide&&kind==EncounterKind.ELIZABETH_GREENE&&greeneStage()==GreeneMotion.DOWN)entityData.set(GREENE_HIT,level().getGameTime());
        if(accepted&&!level().isClientSide&&kind==EncounterKind.FLYER&&isAlive())flight.hurt(source.getSourcePosition());
        if(accepted&&!level().isClientSide&&guardContact){
            entityData.set(GUARD_HIT,level().getGameTime());
            Vec3 from=source.getEntity()==null?source.getSourcePosition():source.getEntity().getEyePosition();
            if(from==null)from=position().add(getLookAngle().scale(3));
            Vec3 p=JuggernautContact.guardContact(moveAge(0),position(),getYRot(),from);
            ContactEffects.guard(this,p);
        }
        if(accepted&&!level().isClientSide){if(source.getEntity() instanceof LivingEntity attacker&&enemy(attacker))lastThreatAt=level().getGameTime();poise+=poiseDamage;if(poise>=70){expose(70);poise=0;}}
        return accepted;
    }
    private boolean hurtGoliath(DamageSource source,float amount){
        boolean bypass=source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
        var window=goliathWindow();
        var contact=incomingContact;
        if(level().isClientSide||!isAlive()||!bypass&&(ConsumeControl.protectedEntity(this)||source.getEntity() instanceof LivingEntity attacker&&isAlliedTo(attacker)))return false;
        boolean weak=exposed() && window.part!=null && contact!=null && window.part.acceptsDamage(contact.bone());
        boolean down=window==GoliathWindow.LEG_FALL||window==GoliathWindow.ARM_FALL;
        boolean finish=goliathFinisherReady();
        boolean drain=source.is(QuillHarvest.DRAIN);
        float scaled=bypass||finish||drain?amount:amount*(weak?.7F:down?.4F:.035F);
        boolean accepted=super.hurt(source,scaled);
        if(!accepted||bypass)return accepted;
        BossSiteData.participant(this,source.getEntity());
        // An implanted parasite has already pierced the shell. Its periodic drain is not another impact.
        if(drain)return true;
        var response=weak||down||finish?BossImpactFeedback.Contact.WEAK:BossImpactFeedback.Contact.ARMOR;
        int slot=window==GoliathWindow.ARM?1:window==GoliathWindow.KNEE?0:2;
        if(weak && !finish && armor[slot]<=amount)response=BossImpactFeedback.Contact.BREAK;
        if(contact!=null)BossImpactFeedback.contact(this,source,contact.point(),response,amount);
        else if(source.getSourcePosition()!=null){
            var from=source.getSourcePosition();
            var point=hurtRegions(0).stream().flatMap(r->r.boxes().stream()).map(b->b.nearest(from)).min(Comparator.comparingDouble(from::distanceToSqr));
            if(point.isPresent())BossImpactFeedback.contact(this,source,point.get(),response,amount);
        }
        if(!isAlive()||isRemoved()||finish||goliathWindow()!=window)return true;
        if(source.getEntity() instanceof LivingEntity attacker&&enemy(attacker))lastThreatAt=level().getGameTime();
        if(weak){
            armor[slot]=Math.max(0,armor[slot]-amount);
            if(armor[slot]<=0){
                switch(window){
                    case HEAD -> startGoliathWindow(GoliathWindow.KNEE,GoliathWindow.KNEE.ticks,0);
                    case KNEE,ARM -> {
                        boolean arm=window==GoliathWindow.ARM;
                        entityData.set(BROKEN,arm?3:1);
                        entityData.set(PHASE,arm?2:1);
                        setHealth(Math.min(getHealth(),getMaxHealth()*(arm?.31F:.64F)));
                        if(arm)armor[2]=90;
                        playSound(SoundEvents.RAVAGER_HURT,1.35F,.55F);
                        var fall=arm?GoliathWindow.ARM_FALL:GoliathWindow.LEG_FALL;
                        startGoliathWindow(fall,fall.ticks,0);
                    }
                    case FACE -> {
                        setHealth(Math.min(getHealth(),1));
                        startGoliathWindow(GoliathWindow.FINISH,1,0);
                    }
                    default -> {}
                }
            }
        }else if(!broken(0)&&!exposed()){
            poise+=Math.min(amount,50);
            if(poise>=70){poise=0;expose(60);}
        }
        return true;
    }

    /** MotherHealth redirects before the core's own immunity check. Child
     * rejection never changes this event's divisor or reallocates its share. */
    private boolean hurtGreeneCore(DamageSource source,float amount){
        greeneBattle.refreshHealthScale();
        if(level().isClientSide||!isAlive()||isInvulnerableTo(source)||ConsumeControl.protectedEntity(this)||!greeneBattle.targetable())return false;
        if(source.getEntity() instanceof LivingEntity attacker&&isAlliedTo(attacker))return false;
        if(goddess.active()){
            if(!goddess.damageable())return false;
            if(incomingContact!=null){
                int slot=GoddessRig.PARTS.indexOf(incomingContact.bone());
                if(slot>=GoddessHandPose.WRIST){
                    if(!goddess.attacks.hand.hit(slot,source,amount,incomingContact.point()))return false;
                    BossSiteData.participant(this,source.getEntity());
                    BossImpactFeedback.contact(this,source,incomingContact.point(),BossImpactFeedback.Contact.WEAK,amount);
                    return true;
                }
            }
            if(goddess.groundWeakSpot(source,incomingContact))amount*=1.5F;
            boolean accepted;
            greeneOwnDamageDepth++;
            try{accepted=super.hurt(source,amount);}finally{greeneOwnDamageDepth--;}
            if(accepted){
                BossSiteData.participant(this,source.getEntity());
                if(incomingContact!=null)confirmedIncoming(source,amount);
                goddess.afterDamage();
                goddess.attacks.hand.hitController(source,incomingContact);
                if(source.getDirectEntity() instanceof ThrownBlock rock)goddess.rockImpact(rock,incomingContact);
            }
            return accepted;
        }
        if(spider.first()){
            if(!spider.live())return false;
            int slot=incomingContact==null?-1:SpiderAnatomy.NAMES.indexOf(incomingContact.bone());
            float scale=slot==SpiderAnatomy.SAC && Math.abs(spider.tilt(0))>.3F?2F:
                slot==SpiderAnatomy.MOUTH && spider.actions.move()==SpiderActions.Move.RECOIL?1.75F:1F;
            boolean accepted;
            greeneOwnDamageDepth++;
            try{accepted=super.hurt(source,amount*scale);}finally{greeneOwnDamageDepth--;}
            if(accepted){
                BossSiteData.participant(this,source.getEntity());
                if(slot>=0)spider.contact(slot,source,amount,incomingContact);
                confirmedIncoming(source,amount);
            }
            if(spiderLethal){spiderLethal=false;greeneBattle.spiderKilled();}
            return accepted;
        }
        int attached=((1<<parts.length)-1)&~entityData.get(BROKEN);
        long epoch=greeneStateEpoch;
        float share=GreeneMotherHealth.perChild(amount,Integer.bitCount(attached));
        boolean redirected=false;
        for(int slot=0;slot<parts.length;slot++)if((attached&(1<<slot))!=0){
            redirected|=hurtPart(slot,source,share,false);
            if(epoch!=greeneStateEpoch||!isAlive()||isRemoved())return redirected;
        }
        // The collapse event does not make the core damageable. The mission
        // does that on DOWN entry, after the 125-tick collapse animation.
        int stage=greeneStage();
        if(!greeneBattle.coreDamageable()){
            if(redirected)confirmedIncoming(source,amount);
            return redirected;
        }
        // alwaysDoOwn=true: use full incoming damage, never the remaining 40%.
        boolean own;greeneOwnDamageDepth++;
        try{own=super.hurt(source,amount);}finally{greeneOwnDamageDepth--;}
        if(epoch!=greeneStateEpoch||!isAlive()||isRemoved())return own||redirected;
        if(own){
            if(stage==GreeneMotion.DOWN)entityData.set(GREENE_HIT,level().getGameTime());
            if(source.getEntity() instanceof LivingEntity attacker&&enemy(attacker))lastThreatAt=level().getGameTime();
            greeneBattle.damaged();
        }
        // Successful own damage already goes through the normal incoming-hit
        // event. Health redirection is not three separate physical collisions.
        else if(redirected)confirmedIncoming(source,amount);
        if (own || redirected) BossSiteData.participant(this, source.getEntity());
        return own||redirected;
    }
    @Override public void die(DamageSource source) {
        super.die(source);
        if (dead) BossSiteData.defeated(this, source.getEntity());
    }
    @Override public void remove(Entity.RemovalReason reason){
        if (reason.shouldDestroy()) BossSiteData.removed(this);
        if(kind==EncounterKind.ELIZABETH_GREENE)greeneStateEpoch++;
        if(kind==EncounterKind.ELIZABETH_GREENE&&greeneBattle!=null&&reason.shouldDestroy())greeneBattle.dispose();
        super.remove(reason);
    }
    private boolean strikeWoundedContact(LivingEntity victim,HydraContact.Box volume,float damage,com.codex.blacklight.infection.InfectedAttackStyle style){
        var body=AnimatedHurtBody.of(victim);
        var source=damageSources().mobAttack(this);
        if(body==null){
            var point=HunterContactPose.nearest(victim.getBoundingBox(),volume.center);
            return HydraHurtContact.clear(this,volume.center,point) && victim.hurt(source,damage);
        }
        var touch=HydraHurtContact.volume(body.hurtRegions(0),volume,volume.center,style);
        return touch!=null&&HydraHurtContact.clear(this,volume.center,touch.point())&&body.hurtAt(source,damage,touch);
    }

    private boolean sweepGoliathSpike(){
        // Four intermediate source poses resolve the fast rotation during the final frame.
        var previous=GoliathContact.spike(this,-1);
        for(int sample=1;sample<=4;sample++){
            var next=GoliathContact.spike(this,sample/4F-1);
            BlockHitResult ground=null;
            for(int i=0;i<next.size();i++){
                var box=next.get(i);var old=previous.get(i);
                var v=box.center.subtract(old.center);
                for(var victim:level().getEntitiesOfClass(LivingEntity.class,box.bounds.minmax(old.bounds),this::enemy)){
                    if(struck.contains(victim.getUUID()))continue;
                    var bounds=victim.getBoundingBox();var center=bounds.getCenter();
                    var touched=box.sweepBox(center.add(v),center,new Vec3(bounds.getXsize(),bounds.getYsize(),bounds.getZsize()).scale(.5));
                    if(touched==null||!HydraHurtContact.clear(this,old.center,touched.point()))continue;
                    boolean accepted=AnimatedHurtBody.of(victim)==null?victim.hurt(damageSources().mobAttack(this),(float)kind.damage):
                            strikeWoundedContact(victim,box,(float)kind.damage,com.codex.blacklight.infection.InfectedAttackStyle.PIERCE);
                    if(accepted)struck.add(victim.getUUID());
                }
                // On a ledge the lowest corner can miss while another fingertip meets the top face.
                for(int j=0;ground==null && j<box.points.size();j++){
                    var from=old.points.get(j);var to=box.points.get(j);
                    if(to.y>=from.y)continue;
                    var hit=level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
                    if(hit.getType()==HitResult.Type.BLOCK && hit.getDirection()==net.minecraft.core.Direction.UP)ground=hit;
                }
            }
            if(ground!=null){
                TerrainImpactEffects.emit(this,ground,3.2F,true);
                return true;
            }
            previous=next;
        }
        return false;
    }

    private void tickWoundedGoliath(EncounterMove action,int age){
        if(age==lastAge)return;
        lastAge=age;
        setYRot(yaw);yBodyRot=yaw;yHeadRot=yaw;
        if(action==EncounterMove.ERUPTION){
            // Original F88..90 drives the wrist roughly 22 blocks downward in four ticks.
            // Test the moving claw against real victims and solid ground between server ticks.
            if(age>=35 && age<=39 && sweepGoliathSpike()){
                if(!isAlive()||move()!=action)return;
                startGoliathWindow(GoliathWindow.ARM,GoliathWindow.ARM.ticks,0);
            }else if(age>=39){
                cancelAttack();
            }
            return;
        }
        // Remove vanilla gravity while driving the committed arc. Each displacement
        // is resolved by Minecraft collision, including a low roof or a lower street.
        if(age<15)return;
        if(age==15)leapSpeed=Math.min(.34,Math.max(0,aim.subtract(position()).dot(direction)/49));
        double velocity=leapDescending?-.8:(39.5-age)*.035;
        move(MoverType.SELF,direction.scale(leapSpeed).add(0,velocity,0));
        if(velocity>0&&verticalCollision)leapDescending=true;
        if(velocity<0&&onGround()){
            updateParts();
            var floor=new AABB(getX()-6,getY()-.65,getZ()-6,getX()+6,getY()+2.1,getZ()+6);
            var volume=HydraContact.oriented(floor.getCenter(),new Vec3(6,floor.getYsize()*.5,6),new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1));
            for(var victim:level().getEntitiesOfClass(LivingEntity.class,floor,this::enemy)){
                if(victim.position().subtract(position()).horizontalDistanceSqr()>36)continue;
                strikeWoundedContact(victim,volume,(float)kind.damage*1.5F,com.codex.blacklight.infection.InfectedAttackStyle.CRUSH);
                if(!isAlive()||move()!=action)return;
            }
            var support=level().clip(new ClipContext(position().add(0,.15,0),position().add(0,-.4,0),
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            TerrainImpactEffects.emit(this,support,6F,true);
            startGoliathWindow(GoliathWindow.FACE,GoliathWindow.FACE.ticks,0);
        }else if(age>=145){cancelAttack();}
    }

    public float legAngle(boolean right,float partial){float angle;if(move()==EncounterMove.POUNCE||move()==EncounterMove.CHARGE||move()==EncounterMove.DIVE)angle=(float)Math.cos(moveAge(partial))*.5F;else angle=getDeltaMovement().horizontalDistanceSqr()>.0002?(float)Math.cos((tickCount+partial)*.65F)*.5F:0;return right?angle:-angle;}
    public void updateParts(){if(parts==null)return;
        if(greeneParts!=null&&greeneParts.length>0){
            var now=hurtRegions(0);var next=hurtRegions(1);
            var empty=new AABB(position(),position());
            for(int i=0;i<greeneParts.length;i++)greeneParts[i].bounds(i<now.size()?now.get(i).bounds():empty,i<next.size()?next.get(i).bounds():empty);
            // Vanilla derives the entity origin from its movement box. Animated
            // receiving surfaces belong to the proxies, never to that root box.
            return;
        }
        if(flyerParts!=null&&flyerParts.length>0){var now=hurtRegions(0);var next=hurtRegions(1);for(int i=0;i<flyerParts.length;i++)flyerParts[i].bounds(now.get(i).bounds(),next.get(i).bounds());}
        for(int i=0;i<parts.length;i++){
        double yaw=Math.toRadians(getYRot());
        if(kind==EncounterKind.GOLIATH){double side=(i==0?1:-1)*kind.width*.2,angle=legAngle(i==0,0),hip=kind.height*.43,dy=1.2-hip,forward=kind.width*.1;
            double z=forward*Math.cos(angle)-dy*Math.sin(angle),y=hip+dy*Math.cos(angle)+forward*Math.sin(angle)-1.05;
            parts[i].setPos(getX()+side*Math.cos(yaw)-z*Math.sin(yaw),getY()+y,getZ()+side*Math.sin(yaw)+z*Math.cos(yaw));
        }else {double angle=yaw+i*2.094,r=kind.width*.63;parts[i].setPos(getX()+Math.sin(angle)*r,getY()+.15,getZ()+Math.cos(angle)*r);}
    }}
    @Override public boolean isMultipartEntity(){return parts!=null&&parts.length>0||flyerParts!=null&&flyerParts.length>0;}
    @Override public net.minecraftforge.entity.PartEntity<?>[] getParts(){return greeneParts!=null&&greeneParts.length>0?greeneParts:flyerParts!=null&&flyerParts.length>0?flyerParts:parts;}
    @Override public boolean isPickable(){return !animatedHurtBody()&&super.isPickable();}
    @Override public boolean isPushable(){return kind!=EncounterKind.ELIZABETH_GREENE&&super.isPushable();}
    @Override protected void doPush(Entity other){
        // The stationary Mother's root box also covers every walkable gap
        // between the Slugs. Only her current, intact skin can shove a bystander.
        if(kind==EncounterKind.ELIZABETH_GREENE){
            var body=other.getBoundingBox();
            if(hurtRegions(0).stream().noneMatch(r->r.bounds().intersects(body)
                    &&r.boxes().stream().anyMatch(b->b.touches(body))))return;
        }
        super.doPush(other);
    }
    @Override public void knockback(double strength,double x,double z){if(kind!=EncounterKind.ELIZABETH_GREENE)super.knockback(strength,x,z);}
    @Override public void setId(int id){super.setId(id);if(parts!=null)for(int i=0;i<parts.length;i++)parts[i].setId(id+i+1);if(flyerParts!=null)for(int i=0;i<flyerParts.length;i++)flyerParts[i].setId(id+i+1);if(greeneParts!=null)for(int i=0;i<greeneParts.length;i++)greeneParts[i].setId(id+i+1);}
    @Override public void startSeenByPlayer(ServerPlayer p){super.startSeenByPlayer(p);if(kind.boss){bar.addPlayer(p);if(kind!=EncounterKind.ALEX_MERCER)defenseBar.addPlayer(p);}}
    @Override public void stopSeenByPlayer(ServerPlayer p){super.stopSeenByPlayer(p);bar.removePlayer(p);defenseBar.removePlayer(p);}
    @Override public void addAdditionalSaveData(CompoundTag tag){if(kind==EncounterKind.ELIZABETH_GREENE)greeneBattle.refreshHealthScale();super.addAdditionalSaveData(tag);if(greeneOutbreak()){tag.putBoolean("GreeneOutbreak",true);tag.put("SpiderAnatomy",spider.save());if(goddess.active())tag.put("GoddessBattle",goddess.save());}tag.putInt("EncounterPhase",phase());tag.putInt("EncounterSequence",sequence);tag.putInt("EncounterBroken",entityData.get(BROKEN));tag.putLong("EncounterExposed",entityData.get(EXPOSED));tag.putLong("EncounterExposedStart",entityData.get(EXPOSED_START));for(int i=0;i<3;i++)tag.putFloat("EncounterArmor"+i,armor[i]);
        if(kind==EncounterKind.GOLIATH){
            tag.putInt("GoliathWindow",goliathWindow().ordinal());
            if(exposed())tag.putLong("GoliathCrashSample",goliathCrashSample());
        }
        if(kind==EncounterKind.ELIZABETH_GREENE){tag.put("GreeneClose",greeneClose.save());tag.put("GreeneRoar",saveGreeneRoar());tag.put("GreeneWeaponCooldowns",greeneWeapons.save());tag.putInt("GreeneHoldTicks",greeneHoldTicks());tag.put("GreeneBattle",greeneBattle.save());tag.putBoolean("GreeneCycleActive",greeneBattle.exposed());tag.putInt("GreeneCycleAge",Math.max(0,Math.min(exposedDuration()-1,(int)exposedAge(0))));for(int i=0;i<3;i++)tag.putInt("GreenePartCooldown"+i,(int)Math.max(0,Math.min(10,10-(level().getGameTime()-armorHit[i]))));}
        var recharge=new CompoundTag();readyAt.forEach((move,at)->recharge.putInt(move.name(),(int)Math.max(0,Math.min(400,at-level().getGameTime()))));tag.put("EncounterRecharge",recharge);tag.putInt("EncounterPrevious",previous.ordinal());
        var ids=new net.minecraft.nbt.ListTag();for(UUID id:brood)ids.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));tag.put("EncounterBrood",ids);
    }
    @Override public void load(CompoundTag tag){
        if(kind==EncounterKind.ELIZABETH_GREENE)greeneBattle.beforeLoad();
        super.load(tag);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        if(kind==EncounterKind.GOLIATH)goliathGait.reset();
        greeneFeedback.resetObservation();Arrays.fill(greeneFlash,-100000L);
        if(kind==EncounterKind.ELIZABETH_GREENE)greeneStateEpoch++;
        super.readAdditionalSaveData(tag);
        entityData.set(GREENE_OUTBREAK,(tag.getBoolean("GreeneOutbreak") || tag.getBoolean("GreenePursuit")));
        spider.load(tag.getCompound("SpiderAnatomy").copy());
        entityData.set(PHASE,Math.max(0,Math.min(kind==EncounterKind.ELIZABETH_GREENE?3:2,tag.getInt("EncounterPhase"))));
        sequence=Math.max(0,tag.getInt("EncounterSequence"));entityData.set(BROKEN,tag.getInt("EncounterBroken")&((1<<parts.length)-1));
        if(kind==EncounterKind.ELIZABETH_GREENE){
            entityData.set(EXPOSED,0L);entityData.set(EXPOSED_START,0L);
            greeneWeapons.load(tag.getCompound("GreeneWeaponCooldowns"));loadGreeneRoar(tag);
            entityData.set(GREENE_HOLD,GreeneMotion.hold(tag.contains("GreeneHoldTicks")?tag.getInt("GreeneHoldTicks"):GreeneMotion.DEFAULT_HOLD_TICKS));entityData.set(GREENE_HIT,-100000L);
            boolean legacy=!tag.contains("GreeneBattle");
            boolean newSpawn=legacy&&!tag.contains("EncounterPhase")&&!tag.contains("EncounterBroken")
                    &&!tag.contains("EncounterArmor0")&&!tag.contains("GreeneCycleActive")&&!tag.contains("EncounterExposed");
            if(legacy){
                boolean active=tag.contains("GreeneCycleActive")?tag.getBoolean("GreeneCycleActive"):tag.getLong("EncounterExposed")>0&&entityData.get(BROKEN)==7;
                int oldAge=tag.contains("GreeneCycleAge")?tag.getInt("GreeneCycleAge"):(int)Math.max(0,level().getGameTime()-tag.getLong("EncounterExposedStart"));
                greeneBattle.loadLegacy(active,oldAge,greeneHoldTicks(),phase());
            }else greeneBattle.load(tag.getCompound("GreeneBattle"));
            if(goddess.active())goddess.load(tag.getCompound("GoddessBattle"));
            float maximum=GreeneBattleHealth.segmentHealth(greeneBattle.armourWave(),greeneBattle.savedHealthBasis());
            for(int i=0;i<3;i++){
                float stored=tag.contains("EncounterArmor"+i)?tag.getFloat("EncounterArmor"+i):(legacy?90:maximum);
                if(!Float.isFinite(stored))stored=0;
                armor[i]=Math.max(0,Math.min(maximum,legacy?stored/90*maximum:stored));
                if(broken(i))armor[i]=0;
                if(armor[i]<=0)entityData.set(BROKEN,entityData.get(BROKEN)|(1<<i));
                armorHit[i]=level().getGameTime()-10+Math.max(0,Math.min(10,tag.getInt("GreenePartCooldown"+i)));
            }
            greeneBattle.refreshHealthScale();
            if(isAlive()&&entityData.get(BROKEN)==7&&greeneStage()==GreeneBattleCycle.GUARDED)greeneBattle.collapse(greeneHoldTicks());
            if(newSpawn)greeneBattle.scheduleOpeningFromSpawnData();
        }else{
            long now=level().getGameTime();
            long end=tag.getLong("EncounterExposed");
            long start=tag.contains("EncounterExposedStart")?tag.getLong("EncounterExposedStart"):now;
            for(int i=0;i<3;i++)if(tag.contains("EncounterArmor"+i))armor[i]=Math.max(0,Math.min(90,tag.getFloat("EncounterArmor"+i)));
            if(kind==EncounterKind.GOLIATH){
                // The old second plate was on the healthy leg. It never means a severed wrist.
                boolean modern=tag.contains("GoliathWindow");
                int mask=entityData.get(BROKEN)&(modern?3:1);
                if(mask==2)mask=0;
                entityData.set(BROKEN,mask);entityData.set(PHASE,Integer.bitCount(mask));
                for(int slot=0;slot<3;slot++)if(!Float.isFinite(armor[slot]))armor[slot]=90;
                if(!modern){armor[1]=90;armor[2]=90;}
                var state=modern?GoliathWindow.byId(tag.getInt("GoliathWindow")):mask==0&&end>now?GoliathWindow.HEAD:GoliathWindow.NONE;
                if(!state.matchesMask(mask)||!isAlive())state=GoliathWindow.NONE;
                long restored=state==GoliathWindow.FINISH?now+1:state==GoliathWindow.NONE?0:GoliathRecovery.restoredEnd(now,start,end);
                entityData.set(GOLIATH_WINDOW,restored>0?state.ordinal():0);
                entityData.set(EXPOSED,restored);
                entityData.set(EXPOSED_START,restored>0?start:0L);
                entityData.set(GOLIATH_CRASH_SAMPLE,restored>0?GoliathRecovery.sanitizeImpactSample(tag.getLong("GoliathCrashSample")):0L);
                if(end>0&&restored==0)finishGoliathRecovery();
                yaw=getYRot();
            }else{
                entityData.set(EXPOSED,Math.min(now+exposedDuration(),end));
                entityData.set(EXPOSED_START,Math.min(now,start));
            }
        }
        readyAt.clear();var recharge=tag.getCompound("EncounterRecharge");for(var move:EncounterMove.values())if(recharge.contains(move.name()))readyAt.put(move,level().getGameTime()+Math.max(0,Math.min(400,recharge.getInt(move.name()))));previous=EncounterMove.byId(tag.getInt("EncounterPrevious"));
        brood.clear();var ids=tag.getList("EncounterBrood",8);for(int i=0;i<Math.min(4,ids.size());i++)try{brood.add(UUID.fromString(ids.getString(i)));}catch(IllegalArgumentException ignored){}cancelAttack();cooldown=kind==EncounterKind.GOLIATH&&exposed()?0:30;if(kind==EncounterKind.ELIZABETH_GREENE)greeneClose.load(tag.getCompound("GreeneClose"));
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){c.add(new AnimationController<>(this,"body",3,s->s.setAndContinue(RawAnimation.begin().thenLoop(kind()==EncounterKind.BRAWLER&&move()==EncounterMove.NONE&&getDeltaMovement().horizontalDistanceSqr()>.0002?"run":"idle"))));}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps,boolean teleport){super.lerpTo(x,y,z,yaw,pitch,kind==EncounterKind.FLYER||kind==EncounterKind.ELIZABETH_GREENE&&greeneBattle.controlsRoot()?1:steps,teleport);}
    @Override public AABB getBoundingBoxForCulling(){var bounds=super.getBoundingBoxForCulling();if(kind==EncounterKind.ELIZABETH_GREENE && greeneOutbreak()){
            var envelope=bounds.inflate(48,64,48);
            if(spider.live() && spider.actions.move()==com.codex.blacklight.combat.SpiderActions.Move.SPIKES){
                var aim=spider.actions.aim();envelope=envelope.minmax(new AABB(aim,aim).inflate(12,5,12));
            }
            return envelope;
        }if(kind==EncounterKind.FLYER)return bounds.inflate(3,2,3);if(greeneParts!=null)for(var part:greeneParts)if(part!=null)bounds=bounds.minmax(part.getBoundingBox());return bounds;}
    private final class FightGoal extends Goal{
        FightGoal(){setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return !disabled()&&(waitingForConvoyGate()||move()!=EncounterMove.NONE||enemy(getTarget())
                ||kind==EncounterKind.GOLIATH&&exposed()||kind==EncounterKind.FLYER&&flyerStage()!=FlyerMotion.IDLE);}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){var target=getTarget();if(spider.first()||waitingForConvoyGate()||move()!=EncounterMove.NONE||!enemy(target)||exposed()||kind==EncounterKind.ELIZABETH_GREENE&&!greeneBattle.canAttack()||kind==EncounterKind.FLYER&&flyerStage()!=FlyerMotion.IDLE)return;getLookControl().setLookAt(target,30,30);
            double distance=distanceTo(target);
            EncounterMove next=EncounterMove.NONE;
            if(cooldown==0){
                brood.removeIf(id->!( ((ServerLevel)level()).getEntity(id) instanceof LivingEntity living)||!living.isAlive());
                int nearby=level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(5),EncounterMob.this::enemy).size();
                var situation=new EncounterTactics.Situation(distance,phase(),getHealth()/getMaxHealth(),level().getGameTime()-lastThreatAt<60,nearby,brood.size(),previous);
                // Greene's distance observer runs while other attacks are active.
                // begin() remains an explicit action request for scripted fixtures;
                // only the autonomous selector applies the six-second dwell gate.
                next=EncounterTactics.choose(kind,situation,m->kind.permits(m)&&moveReady(m)
                    &&(kind!=EncounterKind.ELIZABETH_GREENE||m!=EncounterMove.HOWL||greeneRoarReady(target)));
                if(begin(next,target)){sequence++;return;}
            }
            double preferred=EncounterTactics.preferredDistance(kind);
            if(kind==EncounterKind.JUGGERNAUT&&JuggernautContact.uses(next)&&!inActionRange(next,target))preferred=Math.min(preferred,1.5);
            if(next!=EncounterMove.NONE)preferred=Math.min(preferred,actionRange(next)+kind.width*.5+target.getBbWidth()*.5-.6);
            if(kind.flying){flight.circle(target);}
            else if(kind.speed>0){
                if(!hasLineOfSight(target)||distance>preferred+.35)getNavigation().moveTo(target,kind==EncounterKind.GOLIATH&&broken(0)?.72:1);
                else if(preferred>=4&&distance<preferred-1){var away=position().subtract(target.position()).multiply(1,0,1).normalize().scale(3);getNavigation().moveTo(getX()+away.x,getY(),getZ()+away.z,1);}
                else getNavigation().stop();
            }
        }
        @Override public void stop(){cancelAttack();}
    }
}



