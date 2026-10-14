package com.codex.blacklight.entity;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.registry.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Carried terrain becomes one ballistic object; impact does not recursively break terrain. */
public final class ThrownBlock extends Projectile {
    private static final EntityDataAccessor<BlockState> BLOCK=SynchedEntityData.defineId(ThrownBlock.class,EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Integer> HOLDER=SynchedEntityData.defineId(ThrownBlock.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> THROWING=SynchedEntityData.defineId(ThrownBlock.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SETTLED=SynchedEntityData.defineId(ThrownBlock.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CHARGE=SynchedEntityData.defineId(ThrownBlock.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> TWO=SynchedEntityData.defineId(ThrownBlock.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(ThrownBlock.class,EntityDataSerializers.INT),TARGET=SynchedEntityData.defineId(ThrownBlock.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> AIM_X=SynchedEntityData.defineId(ThrownBlock.class,EntityDataSerializers.FLOAT),AIM_Y=SynchedEntityData.defineId(ThrownBlock.class,EntityDataSerializers.FLOAT),AIM_Z=SynchedEntityData.defineId(ThrownBlock.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<net.minecraft.core.BlockPos> SOURCE=SynchedEntityData.defineId(ThrownBlock.class,EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<CompoundTag> CARGO=SynchedEntityData.defineId(ThrownBlock.class,EntityDataSerializers.COMPOUND_TAG);
    private CompoundTag cachedCargoTag;private BlockThrowCargo cachedCargo;private int recoveryIndex;
    private long phaseAt,aimAt=-1,aimComputedAt=-1;
    private Vec3 aimPoint;
    private boolean assist=true,pendingRelease;
    private int chargeSnapshot;
    private final BlockThrowCharge charge=new BlockThrowCharge();
    private double impactKnockback=.85;
    private boolean armed,settled; private int age;private float impactDamage=8;
    public ThrownBlock(EntityType<? extends ThrownBlock> type,Level level){super(type,level);}
    public ThrownBlock(Level level,ServerPlayer owner,BlockState state){this(ModEntities.THROWN_BLOCK.get(),level);setOwner(owner);entityData.set(BLOCK,state);entityData.set(HOLDER,owner.getId());phaseAt=level.getGameTime();}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){builder.define(BLOCK,Blocks.STONE.defaultBlockState());builder.define(HOLDER,-1);builder.define(THROWING,false);builder.define(SETTLED,false);builder.define(CHARGE,0);builder.define(TWO,false);builder.define(CARGO,new CompoundTag());builder.define(SOURCE,net.minecraft.core.BlockPos.ZERO);builder.define(PHASE,0);builder.define(TARGET,-1);builder.define(AIM_X,0F);builder.define(AIM_Y,0F);builder.define(AIM_Z,0F);}
    public BlockState block(){return entityData.get(BLOCK);}
    public int holderId(){return entityData.get(HOLDER);}
    public void arm(){armed=true;}
    public boolean throwing(){return entityData.get(THROWING);}
    public int chargeTicks(){return entityData.get(CHARGE);}
    private boolean validHolder(ServerPlayer p){return !level().isClientSide&&holderId()==p.getId()&&getOwner()==p&&p.level()==level()&&BlockThrowSystem.canUse(p);}
    public void setCargo(BlockThrowCargo cargo){entityData.set(CARGO,cargo.save());entityData.set(TWO,cargo.twoHanded());}
    public BlockThrowCargo cargo(){var tag=entityData.get(CARGO);if(cachedCargo==null||cachedCargoTag!=tag){cachedCargoTag=tag;cachedCargo=BlockThrowCargo.load(level(),tag,block());}return cachedCargo;}
    public boolean twoHanded(){return entityData.get(TWO);}
    public int phase(){return entityData.get(PHASE);}
    public int aimTarget(){return entityData.get(TARGET);}
    public Vec3 aimedVelocity(){return new Vec3(entityData.get(AIM_X),entityData.get(AIM_Y),entityData.get(AIM_Z));}
    public void beginPickup(ServerPlayer p,net.minecraft.core.BlockPos source){entityData.set(SOURCE,source);setPhase(p,0,"pickup");}
    public Vec3 carryCenter(Player p,Vec3 grip,float partial){
        if(phase()!=0)return grip;
        var action=p.getData(ModAttachments.PLAYER_ACTION);
        float t=net.minecraft.util.Mth.clamp((action.seconds(level().getGameTime()+partial)*20/BlockThrowMotion.pickupTicks(twoHanded())-.25F)/.65F,0,1);
        t=t*t*(3-2*t);return Vec3.atLowerCornerOf(entityData.get(SOURCE)).add(cargo().size().scale(.5)).lerp(grip,t);
    }
    private void setPhase(ServerPlayer p,int phase,String motion){entityData.set(PHASE,phase);phaseAt=level().getGameTime();BlockThrowMotion.play(p,twoHanded(),motion);}
    public void acceptAim(ServerPlayer p,Vec3 point,boolean assist){
        long now=level().getGameTime();
        if(!validHolder(p)||!BlockThrowBallistics.finite(point)||point.distanceToSqr(p.getEyePosition())>128*128)return;
        aimPoint=point;this.assist=assist;aimAt=now;aimComputedAt=-1;
    }
    private void updateAim(ServerPlayer p,boolean force){
        long now=level().getGameTime();if(!force&&now-aimComputedAt<2)return;aimComputedAt=now;
        Vec3 point=aimPoint;
        if(point==null||now-aimAt>25)point=p.getEyePosition().add(Vec3.directionFromRotation(p.getXRot(),com.codex.blacklight.movement.TraversalSteering.cameraYaw(p)).scale(96));
        double speed=BlockThrowTuning.speed(BlockThrowSystem.level(p),pendingRelease?chargeSnapshot:chargeTicks())*ThrowObjectSize.speedFactor(cargo().cells().size());
        Vec3 origin=force?BlockThrowMotion.center(p,twoHanded(),0):BlockThrowMotion.launchOrigin(p,twoHanded());
        var aim=BlockThrowAim.solve(p,origin,point,assist,speed);
        entityData.set(TARGET,aim.target());entityData.set(AIM_X,(float)aim.velocity().x);entityData.set(AIM_Y,(float)aim.velocity().y);entityData.set(AIM_Z,(float)aim.velocity().z);
    }
    public void startCharge(ServerPlayer p){
        if(!validHolder(p)||pendingRelease||phase()==4)return;
        charge.start(level().getGameTime());if(phase()==1)setPhase(p,2,"charge");
    }
    public void cancelCharge(){charge.cancel();entityData.set(CHARGE,0);}
    public void cancelInput(ServerPlayer p){
        if(pendingRelease||phase()==4)return;cancelCharge();
        if(validHolder(p)&&phase()==2)setPhase(p,1,"hold");
    }
    public void releaseCharge(ServerPlayer p){
        if(!validHolder(p)||pendingRelease||phase()==4)return;
        int ticks=charge.release(level().getGameTime());if(ticks>=0)commitThrow(p,ticks);
    }
    public void throwFrom(ServerPlayer p){if(validHolder(p)&&!pendingRelease&&phase()!=4)commitThrow(p,0);}
    private void commitThrow(ServerPlayer p,int ticks){
        int tier=BlockThrowSystem.level(p);chargeSnapshot=ticks;
        impactDamage=BlockThrowTuning.damage(tier,ticks)*ThrowObjectSize.damageFactor(cargo().cells().size());impactKnockback=Math.min(4,BlockThrowTuning.knockback(tier,ticks)*ThrowObjectSize.damageFactor(cargo().cells().size()));
        cancelCharge();pendingRelease=true;entityData.set(THROWING,true);
        if(phase()==1)setPhase(p,2,"charge");
    }
    private Vec3 anchor(Player p){return carryCenter(p,BlockThrowMotion.center(p,twoHanded(),1),1);}
    public void drop(){
        if(level().isClientSide)return;
        cancelCharge();
        if(getOwner() instanceof ServerPlayer p&&holderId()==p.getId()&&p.level()==level()){setPos(p.position().add(0,.5,0));BlockThrowSystem.clear(p);BlockThrowMotion.clear(p);}
        entityData.set(HOLDER,-1);entityData.set(SETTLED,true);settled=true;setDeltaMovement(Vec3.ZERO);
        recover();
    }
    private void recover(){
        if(!armed){discard();return;}
        if(!level().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)){discard();return;}
        // A cancelled item spawn retains the saved projectile, retrying without duplicating.
        var cells=cargo().cells();
        while(recoveryIndex<cells.size()){
            var item=new net.minecraft.world.entity.item.ItemEntity(level(),getX(),getY(),getZ(),new net.minecraft.world.item.ItemStack(cells.get(recoveryIndex).state().getBlock().asItem()));item.setDefaultPickUpDelay();
            if(!level().addFreshEntity(item))return;recoveryIndex++;
        }
        armed=false;discard();
    }
    @Override public void tick(){
        super.tick();age++;
        if(holderId()>=0){
            var entity=level().getEntity(holderId());
            if(entity instanceof Player p&&p.isAlive()&&!p.isSpectator()&&!p.isPassenger()&&(level().isClientSide||getOwner()==p)){
                setPos(anchor(p));setDeltaMovement(Vec3.ZERO);
                if(!level().isClientSide&&p instanceof ServerPlayer server){
                    if(!BlockThrowSystem.canUse(server)){drop();return;}
                    long now=level().getGameTime();
                    String expected=BlockThrowMotion.key(twoHanded(),switch(phase()){case 0->"pickup";case 2->"charge";case 4->"release";default->"hold";});
                    var action=server.getData(ModAttachments.PLAYER_ACTION);
                    if(!action.clipId().equals(expected)){
                        server.setData(ModAttachments.PLAYER_ACTION,new com.codex.blacklight.animation.NativeActionState(expected,phaseAt,0,0,com.codex.blacklight.animation.MotionLibrary.duration(expected),com.codex.blacklight.movement.TraversalSteering.cameraYaw(server),phase()==1,-1,action.revision()+1));server.syncData(ModAttachments.PLAYER_ACTION);
                    }
                    server.setYRot(com.codex.blacklight.movement.TraversalSteering.cameraYaw(server));
                    charge.tick(now);entityData.set(CHARGE,charge.ticks(now));
                    if(phase()==0&&now-phaseAt>=BlockThrowMotion.pickupTicks(twoHanded())){
                        setPhase(server,1,"hold");if(pendingRelease||charge.active())setPhase(server,2,"charge");
                    }
                    if(phase()==2&&!pendingRelease&&!charge.active())setPhase(server,1,"hold");
                    if(phase()==2&&pendingRelease&&now-phaseAt>=BlockThrowMotion.windupTicks(twoHanded())){
                        setPhase(server,4,"release");server.setData(ModAttachments.GRAB_STAGE,ConsumeVisual.THROW);server.syncData(ModAttachments.GRAB_STAGE);
                    }
                    updateAim(server,false);
                    if(phase()==4&&now-phaseAt>=BlockThrowMotion.releaseTicks(twoHanded())){
                        Vec3 start=p.getEyePosition(),end=BlockThrowMotion.center(p,twoHanded(),0);
                        var obstruction=level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                        if(obstruction.getType()!=HitResult.Type.MISS){drop();return;}
                        updateAim(server,true);entityData.set(HOLDER,-1);BlockThrowSystem.clear(server);
                        setPos(end);setDeltaMovement(aimedVelocity());hasImpulse=true;age=0;
                    }
                }
                return;
            }
            if(!level().isClientSide)drop();return;
        }
        if(settled||entityData.get(SETTLED)){if(!level().isClientSide&&age%20==0)recover();return;}
        if(level().isClientSide){setPos(position().add(getDeltaMovement()));setDeltaMovement(BlockThrowBallistics.nextVelocity(getDeltaMovement(),isInWater()));return;}
        if(!armed){discard();return;}
        var start=position();var velocity=getDeltaMovement();var end=start.add(velocity);
        if(!level().hasChunkAt(net.minecraft.core.BlockPos.containing(end))||age>BlockThrowBallistics.LIFE||getY()<level().getMinBuildHeight()){drop();return;}
        var wall=BlockThrowCollision.block(level(),this,start,end,cargo().half());if(wall!=null)end=wall;
        var victim=BlockThrowCollision.entity(level(),this,start,end,cargo().half(),this::canHitEntity);
        if(victim!=null||wall!=null){
            Vec3 impact=victim!=null?victim.getLocation():wall;
            setPos(impact);
            if(victim!=null&&victim.getEntity() instanceof LivingEntity target&&allowed(target)){
                if(target.hurt(damageSources().thrown(this,getOwner()),impactDamage)){
                    var push=velocity.normalize().scale(impactKnockback);
                    target.push(push.x,Math.min(.65,.2+impactKnockback*.15),push.z);target.hurtMarked=true;
                }
            }
            ((net.minecraft.server.level.ServerLevel)level()).sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,block()),getX(),getY(),getZ(),24,.35,.35,.35,.12);
            level().playSound(null,blockPosition(),block().getSoundType().getBreakSound(),net.minecraft.sounds.SoundSource.PLAYERS,1.1F,.7F);
            drop();return;
        }
        setPos(end);setDeltaMovement(BlockThrowBallistics.nextVelocity(velocity,isInWater()));
    }
    private boolean allowed(LivingEntity target){
        return getOwner()!=target&&(getOwner()==null||!getOwner().isAlliedTo(target))
                &&(!(getOwner() instanceof Player p)||!(target instanceof Player q)||p.canHarmPlayer(q));
    }
    @Override protected boolean canHitEntity(Entity e){return super.canHitEntity(e)&&e!=getOwner()&&e instanceof LivingEntity living&&allowed(living);}
    @Override protected void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.put("Block",NbtUtils.writeBlockState(block()));tag.putBoolean("Armed",armed);tag.putBoolean("Recover",settled||holderId()>=0);tag.putInt("Age",age);tag.putFloat("Damage",impactDamage);tag.putBoolean("TwoHanded",twoHanded());tag.put("Cargo",cargo().save());tag.putInt("Recovered",recoveryIndex);tag.putDouble("Knockback",impactKnockback);}
    @Override protected void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);entityData.set(BLOCK,NbtUtils.readBlockState(level().holderLookup(Registries.BLOCK),tag.getCompound("Block")));setCargo(BlockThrowCargo.load(level(),tag.getCompound("Cargo"),block()));recoveryIndex=Math.max(0,Math.min(cargo().cells().size(),tag.getInt("Recovered")));armed=tag.getBoolean("Armed");settled=tag.getBoolean("Recover");age=tag.getInt("Age");float savedDamage=tag.getFloat("Damage");impactDamage=Float.isFinite(savedDamage)?Math.max(0,Math.min(100,savedDamage)):0;double savedKnockback=tag.getDouble("Knockback");impactKnockback=Double.isFinite(savedKnockback)?Math.max(0,Math.min(4,savedKnockback)):0;}
}
