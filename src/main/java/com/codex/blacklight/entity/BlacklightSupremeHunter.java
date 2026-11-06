package com.codex.blacklight.entity;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Supreme Hunter: the first authored boss. Its clips are retargeted from the original
 * supreme_hunter package; the server owns the action clock, hit frames and phases. */
public final class BlacklightSupremeHunter extends Monster {
    /** Visual and hitbox scale over the authored rig (user asked for a bigger boss). */
    public static final float SIZE=1.35F;
    private static final EntityDataAccessor<Integer> MOVE=SynchedEntityData.defineId(BlacklightSupremeHunter.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> STARTED=SynchedEntityData.defineId(BlacklightSupremeHunter.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(BlacklightSupremeHunter.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CONSUMER=SynchedEntityData.defineId(BlacklightSupremeHunter.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> WEAK_UNTIL=SynchedEntityData.defineId(BlacklightSupremeHunter.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> HELD=SynchedEntityData.defineId(BlacklightSupremeHunter.class,EntityDataSerializers.INT);
    private final ServerBossEvent bossBar=new ServerBossEvent(Component.translatable("entity.blacklight.supreme_hunter"),BossEvent.BossBarColor.RED,BossEvent.BossBarOverlay.NOTCHED_10);
    private LivingEntity actionTarget;
    private Vec3 committedDirection=Vec3.ZERO,leapTarget=Vec3.ZERO;
    private float committedYaw;
    private double chargeDistance=8;
    private int airTicks,leapAir=30;
    private int thrownId=-1;private long thrownAt;private boolean thrownEscaped;
    private final Map<UUID,Long> trampled=new HashMap<>();
    private int cooldown=20,sequence,poise,lastPoiseTick,introduced;
    private boolean previousNoAi,rewarded,consumed;
    private BlockState carried;
    private final Set<UUID> struck=new HashSet<>();

    public BlacklightSupremeHunter(EntityType<? extends BlacklightSupremeHunter> type,Level level){
        super(type,level);xpReward=200;setPersistenceRequired();
    }
    public static AttributeSupplier.Builder attributes(){
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,SupremeHunterBalance.HEALTH).add(Attributes.MOVEMENT_SPEED,SupremeHunterBalance.SPEED)
                .add(Attributes.ARMOR,SupremeHunterBalance.ARMOR).add(Attributes.ARMOR_TOUGHNESS,SupremeHunterBalance.TOUGHNESS)
                .add(Attributes.ATTACK_DAMAGE,SupremeHunterBalance.DAMAGE).add(Attributes.FOLLOW_RANGE,SupremeHunterBalance.FOLLOW)
                .add(Attributes.KNOCKBACK_RESISTANCE,.95).add(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get(),1.0);
    }
    @Override protected void defineSynchedData(){
        super.defineSynchedData();var b=entityData;b.define(MOVE,0);b.define(STARTED,0L);b.define(PHASE,1);b.define(CONSUMER,-1);b.define(WEAK_UNTIL,-1L);b.define(HELD,-1);
    }
    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(2,new BossGoal());
        goalSelector.addGoal(6,new WaterAvoidingRandomStrollGoal(this,.6));
        goalSelector.addGoal(7,new LookAtPlayerGoal(this,Player.class,24));
        targetSelector.addGoal(1,new HurtByTargetGoal(this));
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true){{setUnseenMemoryTicks(400);}});
    }
    // ---- synced state ----
    public SupremeHunterMove move(){return SupremeHunterMove.byId(entityData.get(MOVE));}
    public float moveAge(float partial){return Math.max(0,level().getGameTime()-entityData.get(STARTED)+partial);}
    public int phase(){return entityData.get(PHASE);}
    public boolean acting(){return move()!=SupremeHunterMove.NONE;}
    public boolean weakened(){return level().getGameTime()<entityData.get(WEAK_UNTIL);}
    public boolean isBeingConsumed(){return entityData.get(CONSUMER)>=0;}
    public int consumerId(){return entityData.get(CONSUMER);}
    public int heldId(){return entityData.get(HELD);}
    public BlockState carried(){return carried;}

    private void begin(SupremeHunterMove move,LivingEntity target){
        actionTarget=target;struck.clear();
        Vec3 delta=target==null?getLookAngle():target.position().subtract(position());
        delta=delta.multiply(1,0,1);
        committedDirection=delta.lengthSqr()<1e-4?Vec3.directionFromRotation(0,getYRot()):delta.normalize();
        committedYaw=(float)Math.toDegrees(Math.atan2(-committedDirection.x,committedDirection.z));
        if(target!=null)leapTarget=target.position();
        face(committedYaw);getNavigation().stop();
        entityData.set(MOVE,move.ordinal());entityData.set(STARTED,level().getGameTime());
    }
    private void finish(){
        if(heldId()>=0)releaseHeld(Vec3.ZERO,false);
        entityData.set(MOVE,0);actionTarget=null;carried=null;
    }
    private void face(float yaw){setYRot(yaw);yBodyRot=yaw;yHeadRot=yaw;}

    // ---- server tick ----
    @Override public void tick(){
        super.tick();
        if(level().isClientSide){clientEffects();return;}
        bossBar.setProgress(getHealth()/getMaxHealth());
        if(tickCount%20==0)refreshAudience();
        if(isBeingConsumed()||!isAlive()){if(heldId()>=0)releaseHeld(Vec3.ZERO,false);return;}
        trackThrown();
        // Aggro: 64 blocks to notice a target, 512 to keep hunting it.
        var follow=getAttribute(Attributes.FOLLOW_RANGE);
        if(follow!=null){double want=getTarget()!=null?SupremeHunterBalance.HUNT_FOLLOW:SupremeHunterBalance.FOLLOW;if(follow.getBaseValue()!=want)follow.setBaseValue(want);}
        if(move()==SupremeHunterMove.NONE&&!getNavigation().isDone()&&getTarget()!=null&&distanceTo(getTarget())>5)trample();
        if(lastPoiseTick>0&&tickCount-lastPoiseTick>SupremeHunterBalance.POISE_DECAY_TICKS)poise=0;
        updatePhase();
        var move=move();
        if(move==SupremeHunterMove.NONE){if(cooldown>0)cooldown--;return;}
        int t=(int)(level().getGameTime()-entityData.get(STARTED));
        if(t>=move.ticks){
            finish();
            cooldown=switch(phase()){case 3->6;case 2->12;default->18;};
            if(move==SupremeHunterMove.LEG_COLLAPSE)entityData.set(WEAK_UNTIL,level().getGameTime()+SupremeHunterBalance.WEAK_TICKS);
            return;
        }
        if(!move.reaction()&&move!=SupremeHunterMove.LEG_COLLAPSE)face(committedYaw);
        getNavigation().stop();
        act(move,t);
        if(heldId()>=0&&level().getEntity(heldId()) instanceof LivingEntity held)carry(held);
    }
    private void refreshAudience(){
        var nearby=((ServerLevel)level()).getPlayers(p->p.isAlive()&&!p.isSpectator()&&p.distanceToSqr(this)<64*64);
        for(var p:List.copyOf(bossBar.getPlayers()))if(!nearby.contains(p))bossBar.removePlayer(p);
        for(var p:nearby)bossBar.addPlayer(p);
    }
    private void updatePhase(){
        float f=getHealth()/getMaxHealth();int want=f<=SupremeHunterBalance.PHASE_THREE?3:f<=SupremeHunterBalance.PHASE_TWO?2:1;
        if(want>phase()&&!move().armored(0)&&!weakened()&&move()!=SupremeHunterMove.LEG_COLLAPSE&&!isBeingConsumed()){
            entityData.set(PHASE,want);
            bossBar.setColor(want==3?BossEvent.BossBarColor.PURPLE:BossEvent.BossBarColor.RED);
            begin(SupremeHunterMove.RAGE,getTarget());
            playSound(SoundEvents.WARDEN_ROAR,3F,.75F);
        }
    }
    private void act(SupremeHunterMove move,int t){
        var level=(ServerLevel)level();
        switch(move){
            case ROAR,RAGE->{
                if(move.eventAt(t)){
                    playSound(SoundEvents.WARDEN_ROAR,3F,move==SupremeHunterMove.ROAR?.65F:.8F);
                    for(var e:victims(move.radius))push(e,1.1,.35,move.stagger/2);
                }
            }
            case CLAW_SLASH,BACKHAND,OVERHAND->{
                // CLAW_SLASH: event 0 is the windup, events 1 and 2 are the two stages (80, then 60).
                boolean stage=move==SupremeHunterMove.CLAW_SLASH?t==move.event(1)||t==move.event(2):move.eventAt(t);
                if(stage){
                    int index=move==SupremeHunterMove.CLAW_SLASH&&t==move.event(2)?1:0;
                    if(index>0)struck.clear();
                    playSound(SoundEvents.RAVAGER_ATTACK,1.6F,index>0?.75F:.6F);
                    for(var e:arcVictims(move))strike(e,move,.55,.2,move.budget(index));
                }
                if(move==SupremeHunterMove.OVERHAND&&t==move.event(0))shake(level,position().add(committedDirection.scale(3)),1.2F);
            }
            case STOMP->{
                if(move.eventAt(t)){
                    shockwave(level,position(),move.radius);
                    for(var e:victims(move.radius))if(e.onGround())strike(e,move,.35,.55);
                }
            }
            case GROUNDSPIKE->{
                // Into: claws plant at 17; the spike erupts under the committed target at 61 (source 26 / 91 frames).
                if(t==move.event(0)){playSound(SoundEvents.WARDEN_SONIC_CHARGE,2F,.6F);if(actionTarget!=null)leapTarget=actionTarget.position();}
                if(t>move.event(0)&&t<move.event(1)&&actionTarget!=null&&actionTarget.isAlive()){
                    Vec3 p=actionTarget.position();leapTarget=leapTarget.lerp(p,.08);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,level.getBlockState(BlockPos.containing(leapTarget).below())),leapTarget.x,leapTarget.y+.1,leapTarget.z,4,.6,.05,.6,.05);
                }
                if(t==move.event(1)){
                    spike(level,leapTarget);
                    for(var e:level.getEntitiesOfClass(LivingEntity.class,new AABB(leapTarget,leapTarget).inflate(move.radius,3,move.radius),this::hostile))
                        strike(e,move,.15,1.25);
                }
            }
            case DEVASTATOR->{
                // Thousand tendrils: long readable windup, then three pulses within 12 blocks.
                if(t<move.event(0)&&t%4==0)level.sendParticles(new DustParticleOptions(new org.joml.Vector3f(.45F,.03F,.03F),2.2F),getX(),getY()+3,getZ(),12,1.6,1.4,1.6,.02);
                if(t==2)playSound(SoundEvents.WARDEN_SONIC_CHARGE,3F,.45F);
                if(move.eventAt(t)){
                    playSound(SoundEvents.WARDEN_ATTACK_IMPACT,3F,.5F);tendrils(level,move.radius);
                    for(var e:victims(move.radius))if(hasLineOfSight(e))strike(e,move,.9,.45);
                }
            }
            case PICKUP_THROW->{
                if(t==move.event(0)){carried=level.getBlockState(blockPosition().below());if(carried.isAir())carried=net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,carried),getX(),getY()+.2,getZ(),30,1,.2,1,.1);playSound(SoundEvents.ROOTED_DIRT_BREAK,2F,.5F);}
                if(t==move.event(1)&&actionTarget!=null){throwDebris(level,actionTarget);carried=null;}
            }
            case SHOULDER_CHARGE->{
                // Source sprint/charge lunge: covers 5-15 blocks depending on distance.
                if(t==move.event(0))chargeDistance=Mth.clamp(actionTarget==null?8:distanceTo(actionTarget)+2,5,13.5);
                if(t>=move.event(0)){double speed=chargeDistance/(move.ticks-move.event(0));
                    setDeltaMovement(committedDirection.x*speed,getDeltaMovement().y,committedDirection.z*speed);hurtMarked=true;}
                for(var e:level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(.8),this::hostile))strike(e,move,1.2,.4);
                if(t%3==0)level.sendParticles(com.codex.blacklight.registry.ModParticles.IMPACT_SMOKE.get(),getX(),getY()+.2,getZ(),3,.6,.05,.6,.01);
            }
            case LEAP->{
                if(t==move.event(0)&&onGround()){
                    Vec3 d=leapTarget.subtract(position());double flat=Math.sqrt(d.x*d.x+d.z*d.z);airTicks=0;
                    double air=20/20.0;double vy=Mth.clamp(.9+Math.max(0,d.y)*.06+flat*.01,.9,1.8);
                    leapAir=(int)Math.ceil(2*vy/.08)+Math.max(0,(int)(d.y*-1.5));
                    setDeltaMovement(d.x/Math.max(1,flat)*Math.min(flat/air/20*1.1,1.8),vy,d.z/Math.max(1,flat)*Math.min(flat/air/20*1.1,1.8));hurtMarked=true;
                    playSound(SoundEvents.RAVAGER_ROAR,2F,.6F);
                }
                if(t>move.event(0)&&t<move.event(1)&&!onGround()){entityData.set(STARTED,entityData.get(STARTED)+1);
                    // Air drag would stop a long leap short: steer onto the landing point every airborne tick.
                    airTicks++;Vec3 rest=leapTarget.subtract(position()).multiply(1,0,1);double left=rest.length(),eta=Math.max(1,leapAir-airTicks);
                    if(left>.5){double sp=Math.min(2.2,left/eta);setDeltaMovement(rest.x/left*sp,getDeltaMovement().y,rest.z/left*sp);hurtMarked=true;}}
                if(t>move.event(0)&&onGround()&&!struck.contains(getUUID())){
                    struck.add(getUUID());entityData.set(STARTED,level.getGameTime()-move.event(1));
                    shockwave(level,position(),move.radius);playSound(SoundEvents.GENERIC_EXPLODE,3F,.6F);
                    level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,getX(),getY()+.5,getZ(),1,0,0,0,0);
                    for(var e:victims(move.radius))strike(e,move,.6,.6);
                }
            }
            case GRAB_THROW->{
                if(t==move.event(0)&&heldId()<0&&actionTarget!=null&&actionTarget.isAlive()&&distanceTo(actionTarget)<move.reach+actionTarget.getBbWidth()
                        &&!(actionTarget instanceof Player p&&p.isCreative())){
                    entityData.set(HELD,actionTarget.getId());playSound(SoundEvents.WARDEN_ATTACK_IMPACT,1.5F,.8F);
                }
                if(t==move.event(1)&&level.getEntity(heldId()) instanceof LivingEntity held){
                    releaseHeld(committedDirection.scale(1.6).add(0,.75,0),true);
                    // Feedback #27: thrown victims stay staggered and take 25 on landing unless they press jump.
                    thrownId=held.getId();thrownAt=level.getGameTime();thrownEscaped=false;
                    if(held instanceof ServerPlayer p)SupremeHunterStagger.apply(p,move.stagger,committedYaw);
                }
            }
            case LEG_COLLAPSE->{if(t==26){shake(level,position(),1F);playSound(SoundEvents.WARDEN_HURT,2F,.5F);}}
            default->{}
        }
    }
    private void trackThrown(){
        if(thrownId<0)return;
        var e=level().getEntity(thrownId);long age=level().getGameTime()-thrownAt;
        if(!(e instanceof LivingEntity victim)||!victim.isAlive()||age>100){thrownId=-1;return;}
        if(!thrownEscaped&&victim instanceof ServerPlayer p&&age>2&&ModAttachments.getData(p,ModAttachments.MOVEMENT_STATE).jumpHeld()){
            // Recovery: the stagger ends in the air and the landing is safe.
            thrownEscaped=true;p.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
            ModAttachments.setData(p,ModAttachments.HIT_REACTION,com.codex.blacklight.combat.HitReaction.EMPTY);ModAttachments.syncData(p,ModAttachments.HIT_REACTION);
            p.fallDistance=0;p.setDeltaMovement(p.getDeltaMovement().multiply(.4,.3,.4).add(0,.25,0));p.hurtMarked=true;
            level().playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,1F,1.3F);
        }
        if(age>3&&victim.onGround()){
            thrownId=-1;
            if(!thrownEscaped){SupremeHunterBalance.hit(this,victim,SupremeHunterMove.GRAB_THROW,SupremeHunterBalance.GRAB_LANDING,damageSources().mobAttack(this));
                ((ServerLevel)level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,level().getBlockState(victim.blockPosition().below())),victim.getX(),victim.getY()+.1,victim.getZ(),20,.5,.1,.5,.1);}
        }
    }
    /** Running tramples anything it runs through (feedback #27). */
    private void trample(){
        long now=level().getGameTime();
        for(var e:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(.4),x->x!=this&&x.isAlive()&&!(x instanceof BlacklightSupremeHunter)&&!(x instanceof Player p&&(p.isCreative()||p.isSpectator())))){
            if(now-trampled.getOrDefault(e.getUUID(),-100L)<20)continue;trampled.put(e.getUUID(),now);
            if(SupremeHunterBalance.hit(this,e,SupremeHunterMove.SHOULDER_CHARGE,SupremeHunterBalance.RUN_BUDGET,damageSources().mobAttack(this)))push(e,.9,.35,20);
        }
        if(trampled.size()>32)trampled.values().removeIf(v->now-v>40);
    }
    private void carry(LivingEntity held){
        if(!held.isAlive()||held.level()!=level()){releaseHeld(Vec3.ZERO,false);return;}
        Vec3 hand=position().add(committedDirection.scale(2.2*SIZE)).add(0,3.3*SIZE,0);
        held.setDeltaMovement(Vec3.ZERO);held.fallDistance=0;
        if(held instanceof ServerPlayer p)p.connection.teleport(hand.x,hand.y,hand.z,p.getYRot(),p.getXRot());
        else held.setPos(hand.x,hand.y,hand.z);
    }
    /** Holding is released by throw, the boss being staggered, or the prisoner's jump input
     * (handled by the player's own movement through the stuck-escape below). */
    private void releaseHeld(Vec3 velocity,boolean thrown){
        var e=level().getEntity(heldId());entityData.set(HELD,-1);
        if(e instanceof LivingEntity held){
            held.setDeltaMovement(velocity);held.hurtMarked=true;
        }
    }
    public boolean struggleFree(ServerPlayer p){
        if(heldId()!=p.getId())return false;
        releaseHeld(new Vec3(0,.4,0),false);return true;
    }
    private boolean hostile(LivingEntity e){
        return e!=this&&e.isAlive()&&!e.isSpectator()&&!(e instanceof Player p&&(p.isCreative()))&&!(e instanceof BlacklightSupremeHunter)
                &&(e instanceof Player||e==getTarget()||e instanceof Mob m&&m.getTarget()==this);
    }
    private List<LivingEntity> victims(double radius){
        return level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(radius,2,radius),e->hostile(e)&&e.distanceToSqr(this)<=radius*radius+getBbWidth()*getBbWidth());
    }
    private List<LivingEntity> arcVictims(SupremeHunterMove move){
        double reach=move.reach+getBbWidth()*.5;
        return level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(reach,1.5,reach),e->hostile(e)
                &&distanceTo(e)<=reach+e.getBbWidth()*.5&&hasLineOfSight(e)
                &&CreatureAttack.inArc(e.getX()-getX(),e.getZ()-getZ(),committedYaw,move.arcCosine));
    }
    private void strike(LivingEntity e,SupremeHunterMove move,double push,double lift){strike(e,move,push,lift,move.budget);}
    private void strike(LivingEntity e,SupremeHunterMove move,double push,double lift,float budget){
        if(!struck.add(e.getUUID()))return;
        if(SupremeHunterBalance.hit(this,e,move,budget,damageSources().mobAttack(this)))push(e,push,lift,move.stagger);
    }
    private void push(LivingEntity e,double push,double lift,int stagger){
        Vec3 d=e.position().subtract(position()).multiply(1,0,1);d=d.lengthSqr()<1e-4?committedDirection:d.normalize();
        double resist=1-Mth.clamp(e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),0,1);
        e.setDeltaMovement(e.getDeltaMovement().add(d.scale(push*resist)).add(0,lift*resist,0));e.hurtMarked=true;
        if(stagger>0&&e instanceof ServerPlayer p)SupremeHunterStagger.apply(p,stagger,(float)Math.toDegrees(Math.atan2(d.x,-d.z)));
        else if(stagger>0)ImpactControl.begin(e,new ImpactProfile(com.codex.blacklight.combat.HitReaction.HEAVY,ImpactProfile.FALL,stagger,0,0,true),getYRot());
    }
    private void shake(ServerLevel level,Vec3 at,float size){
        level.sendParticles(ParticleTypes.EXPLOSION,at.x,at.y+.3,at.z,1,0,0,0,0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,level.getBlockState(BlockPos.containing(at).below())),at.x,at.y+.2,at.z,(int)(30*size),1.4*size,.15,1.4*size,.15);
    }
    private void shockwave(ServerLevel level,Vec3 at,double radius){
        playSound(SoundEvents.GENERIC_EXPLODE,2F,.6F);
        for(int i=0;i<24;i++){double a=i*Math.PI/12;double x=at.x+Math.cos(a)*radius*.8,z=at.z+Math.sin(a)*radius*.8;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,level.getBlockState(BlockPos.containing(x,at.y,z).below())),x,at.y+.2,z,4,.3,.1,.3,.1);}
        level.sendParticles(com.codex.blacklight.registry.ModParticles.IMPACT_SMOKE.get(),at.x,at.y+.2,at.z,20,radius*.4,.1,radius*.4,.04);
    }
    private void spike(ServerLevel level,Vec3 at){
        playSound(SoundEvents.WARDEN_EMERGE,2.5F,.7F);
        var visual=new GroundSpikeEntity(com.codex.blacklight.registry.ModEntities.GROUND_SPIKE.get(),level);
        var surface=level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,BlockPos.containing(at));
        visual.moveTo(at.x,surface.getY(),at.z,committedYaw,0);level.addFreshEntity(visual);
        shake(level,at,1.2F);
    }
    private void tendrils(ServerLevel level,double radius){
        var red=new DustParticleOptions(new org.joml.Vector3f(.5F,.04F,.04F),2.6F);
        for(int i=0;i<36;i++){double a=random.nextDouble()*Math.PI*2,r=2+random.nextDouble()*(radius-2);
            Vec3 tip=position().add(Math.cos(a)*r,1+random.nextDouble()*3,Math.sin(a)*r);
            for(int k=1;k<=6;k++){Vec3 p=position().add(0,2.5,0).lerp(tip,k/6.0);level.sendParticles(red,p.x,p.y,p.z,1,.05,.05,.05,0);}}
        level.sendParticles(ParticleTypes.SONIC_BOOM,getX(),getY()+2.5,getZ(),1,0,0,0,0);
    }
    private void throwDebris(ServerLevel level,LivingEntity target){
        var debris=new SupremeHunterDebris(level,this,carried);
        Vec3 from=position().add(committedDirection.scale(1.5*SIZE)).add(0,3.2*SIZE,0);
        Vec3 aim=target.position().add(0,target.getBbHeight()*.5,0).subtract(from);
        debris.setPos(from.x,from.y,from.z);
        double flat=Math.sqrt(aim.x*aim.x+aim.z*aim.z);double speed=1.6;
        debris.shoot(aim.x,aim.y+flat*.08,aim.z,(float)speed,1.5F);
        level.addFreshEntity(debris);playSound(SoundEvents.RAVAGER_ROAR,1.5F,.9F);
    }

    // ---- damage and reactions ----
    @Override public boolean hurt(DamageSource source,float amount){
        if(level().isClientSide||isBeingConsumed())return false;
        if(amount>0&&source.getEntity() instanceof Player player&&!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)){
            // Original immunity to airborne swats: only Bulletdive / devastators land cleanly.
            boolean air=!consumed&&airborne(player)&&source.getDirectEntity()==player&&!player.isPassenger()
                    &&!DevastatorDamage.isTrueDamage(source);
            if(air){
                amount*=SupremeHunterBalance.AIR_DEFLECT_FACTOR;
                Vec3 d=player.position().subtract(position()).multiply(1,0,1).normalize();
                player.setDeltaMovement(d.x*1.1,.45,d.z*1.1);player.hurtMarked=true;
                playSound(SoundEvents.SHIELD_BLOCK,1.5F,.6F);
            }
        }
        boolean accepted=super.hurt(source,amount);
        if(accepted&&isAlive())react(source,amount);
        return accepted;
    }
    /** Jumping swats and attacks from on top of the boss. onGround alone lags a tick behind teleports and knockback. */
    private boolean airborne(Player player){
        if(player.onGround()&&player.getY()<getY()+1)return false;
        var floor=level().clip(new net.minecraft.world.level.ClipContext(player.position(),player.position().subtract(0,1.2,0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        return floor.getType()==net.minecraft.world.phys.HitResult.Type.MISS||player.getY()>getY()+1.5;
    }
    private void react(DamageSource source,float amount){
        var move=move();int t=(int)(level().getGameTime()-entityData.get(STARTED));
        // The weaken line beats super armor: any accepted hit below it drops the leg.
        if(collapseDue()){if(heldId()>=0)releaseHeld(new Vec3(0,.3,0),false);if(move!=SupremeHunterMove.NONE)finish();begin(SupremeHunterMove.LEG_COLLAPSE,getTarget());poise=0;return;}
        if(move.armored(t)||move.reaction()&&t<8)return;
        if(introduced==0)return;
        boolean heavy=amount>=getMaxHealth()*.04F||DevastatorDamage.isTrueDamage(source)
                ||source.is(net.minecraft.world.damagesource.DamageTypes.THROWN)&&amount>=getMaxHealth()*.005F;
        poise+=heavy?4:1;lastPoiseTick=tickCount;
        if(heldId()>=0&&heavy)releaseHeld(new Vec3(0,.3,0),false);
        // Below the weaken line one stagger knocks the leg out: the consume window.
        if(poise<SupremeHunterBalance.POISE&&!heavy)return;
        poise=0;
        Entity attacker=source.getEntity();
        SupremeHunterMove hit=SupremeHunterMove.HIT_FRONT;
        if(attacker!=null){
            Vec3 d=attacker.position().subtract(position());float rel=Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-d.x,d.z))-getYRot());
            hit=Math.abs(rel)<45?SupremeHunterMove.HIT_FRONT:Math.abs(rel)>135?SupremeHunterMove.HIT_BACK:rel>0?SupremeHunterMove.HIT_LEFT:SupremeHunterMove.HIT_RIGHT;
        }
        if(move!=SupremeHunterMove.NONE&&!move.reaction())finish();
        begin(hit,getTarget());
        if(attacker instanceof LivingEntity a)face((float)Math.toDegrees(Math.atan2(-(a.getX()-getX()),a.getZ()-getZ())));
    }
    @Override public void knockback(double strength,double x,double z){if(!acting())super.knockback(strength*.15,x,z);}
    @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source){return false;}
    @Override public boolean isPushable(){return false;}
    @Override protected void pushEntities(){}

    // ---- consume (low-health execution) ----
    /** One collapse per weakness window; after it closes the boss fights on for a while. */
    private boolean collapseDue(){
        return getHealth()/getMaxHealth()<=SupremeHunterBalance.WEAKEN&&!weakened()&&move()!=SupremeHunterMove.LEG_COLLAPSE
                &&level().getGameTime()>=entityData.get(WEAK_UNTIL)+SupremeHunterBalance.RECOVER_TICKS;
    }
    public boolean consumable(){return (weakened()||move()==SupremeHunterMove.LEG_COLLAPSE)&&isAlive()&&!isBeingConsumed();}
    /** Set by the consume transaction immediately before its execution hit. */
    public void markConsumed(){consumed=true;}
    public boolean claim(Player player){
        if(!consumable())return false;previousNoAi=isNoAi();setNoAi(true);getNavigation().stop();setTarget(null);finish();
        entityData.set(CONSUMER,player.getId());return true;
    }
    public void release(){if(!isBeingConsumed())return;setNoAi(previousNoAi);entityData.set(CONSUMER,-1);}

    @Override public void die(DamageSource source){
        super.die(source);
        if(level().isClientSide||!dead||rewarded)return;
        rewarded=true;bossBar.removeAllPlayers();
        var player=source.getEntity() instanceof ServerPlayer p?p:getKillCredit() instanceof ServerPlayer p?p:null;
        SupremeHunterRewards.defeated(this,player,consumed);
    }
    @Override protected void dropCustomDeathLoot(DamageSource source,int looting,boolean recentlyHit){
        super.dropCustomDeathLoot(source,looting,recentlyHit);
        if(!rewarded){spawnAtLocation(com.codex.blacklight.registry.ModItems.BLACKLIGHT_SAMPLE.get(),3);spawnAtLocation(com.codex.blacklight.registry.ModItems.LIVING_HIVE_TISSUE.get(),2);}
    }
    @Override protected void tickDeath(){
        if(++deathTime>=60&&!level().isClientSide&&!isRemoved()){level().broadcastEntityEvent(this,(byte)60);remove(RemovalReason.KILLED);}
    }
    @Override public void remove(RemovalReason reason){bossBar.removeAllPlayers();if(!level().isClientSide&&heldId()>=0)releaseHeld(Vec3.ZERO,false);super.remove(reason);}
    @Override public void stopSeenByPlayer(ServerPlayer p){super.stopSeenByPlayer(p);bossBar.removePlayer(p);}
    @Override public boolean removeWhenFarAway(double d){return false;}
    @Override protected boolean shouldDespawnInPeaceful(){return false;}

    // ---- save ----
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);tag.putInt("BlacklightPhase",phase());tag.putBoolean("BlacklightRewarded",rewarded);tag.putInt("BlacklightIntro",introduced);
        if(isBeingConsumed())tag.putBoolean("NoAI",previousNoAi);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);entityData.set(PHASE,Mth.clamp(tag.getInt("BlacklightPhase"),1,3));rewarded=tag.getBoolean("BlacklightRewarded");introduced=tag.getInt("BlacklightIntro");
        if(hasCustomName())bossBar.setName(getDisplayName());
    }
    @Override protected SoundEvent getAmbientSound(){return SoundEvents.WARDEN_AMBIENT;}
    @Override protected SoundEvent getHurtSound(DamageSource source){return SoundEvents.RAVAGER_HURT;}
    @Override protected SoundEvent getDeathSound(){return SoundEvents.WARDEN_DEATH;}
    @Override protected float getSoundVolume(){return 2F;}
    @Override public float getVoicePitch(){return .6F;}

    private void clientEffects(){
        var move=move();
        if(move==SupremeHunterMove.DEVASTATOR&&moveAge(0)<move.event(0)&&tickCount%2==0)
            level().addParticle(new DustParticleOptions(new org.joml.Vector3f(.45F,.03F,.03F),1.6F),getRandomX(1.2),getRandomY(),getRandomZ(1.2),0,.05,0);
        if(weakened()&&tickCount%6==0)level().addParticle(new DustParticleOptions(new org.joml.Vector3f(.6F,.05F,.05F),1.2F),getRandomX(.8),getY()+getBbHeight()*.6,getRandomZ(.8),0,-.05,0);
    }

    /** Distance-driven move choice. Weights follow the original's close slashes, mid stomps,
     * thrown debris at range, and leaps or charges to close long gaps. */
    private final class BossGoal extends Goal {
        BossGoal(){setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return !isBeingConsumed()&&getTarget()!=null&&getTarget().isAlive();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){
            var target=getTarget();if(target==null)return;
            if(acting()||weakened())return;
            getLookControl().setLookAt(target,30,30);
            if(introduced==0){introduced=1;begin(SupremeHunterMove.ROAR,target);return;}
            double d=distanceTo(target),dy=target.getY()-getY(),flat=Math.hypot(target.getX()-getX(),target.getZ()-getZ());
            boolean sight=hasLineOfSight(target);
            getNavigation().moveTo(target,d>12?SupremeHunterBalance.RUN_MODIFIER:SupremeHunterBalance.WALK_MODIFIER);
            // Gap closer: beyond 20 blocks (or a ledge it cannot path to) it leaps at once, cooldown or not.
            if(flat>20||Math.abs(dy)>20||dy>5&&getNavigation().isDone()){begin(SupremeHunterMove.LEAP,target);return;}
            if(cooldown>0)return;
            int p=phase();sequence++;
            SupremeHunterMove next=null;
            if(d>9&&sight)next=switch(Math.floorMod(sequence,p>=2?3:2)){case 0->SupremeHunterMove.PICKUP_THROW;case 1->SupremeHunterMove.SHOULDER_CHARGE;default->SupremeHunterMove.GROUNDSPIKE;};
            else if(d>5.5&&sight)next=p>=2&&sequence%3==0?SupremeHunterMove.GROUNDSPIKE:SupremeHunterMove.SHOULDER_CHARGE;
            else if(d<=5.5){
                if(p>=2&&sequence%7==0)next=SupremeHunterMove.DEVASTATOR;
                else if(sequence%5==0)next=SupremeHunterMove.GRAB_THROW;
                else next=switch(Math.floorMod(sequence,4)){case 0->SupremeHunterMove.CLAW_SLASH;case 1->SupremeHunterMove.BACKHAND;case 2->SupremeHunterMove.STOMP;default->SupremeHunterMove.OVERHAND;};
            }
            if(next!=null)begin(next,target);
        }
        @Override public void stop(){getNavigation().stop();}
    }
}
