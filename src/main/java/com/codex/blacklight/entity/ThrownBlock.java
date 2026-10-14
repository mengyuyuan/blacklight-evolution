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
    private boolean armed,settled; private int age,release=-1;private float impactDamage=8;
    public ThrownBlock(EntityType<? extends ThrownBlock> type,Level level){super(type,level);}
    public ThrownBlock(Level level,ServerPlayer owner,BlockState state){this(ModEntities.THROWN_BLOCK.get(),level);setOwner(owner);entityData.set(BLOCK,state);entityData.set(HOLDER,owner.getId());}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){builder.define(BLOCK,Blocks.STONE.defaultBlockState());builder.define(HOLDER,-1);builder.define(THROWING,false);builder.define(SETTLED,false);}
    public BlockState block(){return entityData.get(BLOCK);}
    public int holderId(){return entityData.get(HOLDER);}
    public void arm(){armed=true;}
    public boolean throwing(){return entityData.get(THROWING);}
    public void throwFrom(ServerPlayer p){
        if(holderId()!=p.getId()||release>=0||age<8)return;
        release=6;entityData.set(THROWING,true);
        p.setData(ModAttachments.GRAB_STAGE,ConsumeVisual.THROW);p.syncData(ModAttachments.GRAB_STAGE);
        com.codex.blacklight.animation.PlayerActions.get().play(p,com.codex.blacklight.animation.PlayerAction.GRAB_THROW,.08F);
    }
    private Vec3 anchor(Player p){return GrabPlacement.palm(p.position(),p.getYRot(),p.getXRot()).add(0,-.4,0);}
    public void drop(){
        if(level().isClientSide)return;
        if(getOwner() instanceof ServerPlayer p&&holderId()==p.getId()&&p.level()==level()){setPos(p.position().add(0,.5,0));BlockThrowSystem.clear(p);}
        entityData.set(HOLDER,-1);entityData.set(SETTLED,true);settled=true;setDeltaMovement(Vec3.ZERO);
        recover();
    }
    private void recover(){
        if(!armed){discard();return;}
        if(!level().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)){discard();return;}
        // A cancelled item spawn retains the saved projectile, retrying without duplicating.
        var item=new net.minecraft.world.entity.item.ItemEntity(level(),getX(),getY(),getZ(),new net.minecraft.world.item.ItemStack(block().getBlock().asItem()));
        item.setDefaultPickUpDelay();
        if(level().addFreshEntity(item)){armed=false;discard();}
    }
    @Override public void tick(){
        super.tick();age++;
        if(holderId()>=0){
            var entity=level().getEntity(holderId());
            if(entity instanceof Player p&&p.isAlive()&&!p.isSpectator()&&!p.isPassenger()&&(level().isClientSide||getOwner()==p)){
                setPos(anchor(p));setDeltaMovement(Vec3.ZERO);
                if(!level().isClientSide&&p instanceof ServerPlayer server){
                    var state=server.getData(ModAttachments.PLAYER_STATE);
                    if(!state.isInfected()||!state.isPrototypeMode()){drop();return;}
                    if(release==0){
                        impactDamage=state.getActivePower()==com.codex.blacklight.power.PowerForm.MUSCLE_MASS?12:8;
                        // Launch from the near side of the held block, so turning beside a wall cannot bypass it.
                        Vec3 start=p.getEyePosition(),end=position().add(0,.4,0);
                        var obstruction=level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                        if(obstruction.getType()!=HitResult.Type.MISS){setPos(start);drop();return;}
                        entityData.set(HOLDER,-1);BlockThrowSystem.clear(server);
                        var direction=Vec3.directionFromRotation(p.getXRot(),com.codex.blacklight.movement.TraversalSteering.cameraYaw(p));
                        setPos(end);setDeltaMovement(direction.scale(impactDamage>8?1.9:1.5));hasImpulse=true;age=0;
                    }else if(release>0)release--;
                }
                return;
            }
            if(!level().isClientSide)drop();return;
        }
        if(settled||entityData.get(SETTLED)){if(!level().isClientSide&&age%20==0)recover();return;}
        if(level().isClientSide){setPos(position().add(getDeltaMovement()));return;}
        if(!armed){discard();return;}
        var start=position();var velocity=getDeltaMovement();var end=start.add(velocity);
        if(!level().hasChunkAt(net.minecraft.core.BlockPos.containing(end))||age>100||getY()<level().getMinBuildHeight()){drop();return;}
        var wall=level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
        var victim=ProjectileUtil.getEntityHitResult(level(),this,start,end,getBoundingBox().expandTowards(velocity).inflate(.4),this::canHitEntity);
        HitResult hit=victim!=null?victim:wall;
        if(hit.getType()!=HitResult.Type.MISS){
            setPos(hit.getLocation());
            if(victim!=null&&victim.getEntity() instanceof LivingEntity target&&allowed(target)){
                if(target.hurt(damageSources().thrown(this,getOwner()),impactDamage)){
                    target.push(velocity.x*.3,.25,velocity.z*.3);target.hurtMarked=true;
                }
            }
            ((net.minecraft.server.level.ServerLevel)level()).sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,block()),getX(),getY(),getZ(),24,.35,.35,.35,.12);
            level().playSound(null,blockPosition(),block().getSoundType().getBreakSound(),net.minecraft.sounds.SoundSource.PLAYERS,1.1F,.7F);
            drop();return;
        }
        setPos(end);setDeltaMovement(velocity.scale(isInWater()?.8:.99).add(0,-.045,0));
    }
    private boolean allowed(LivingEntity target){
        return getOwner()!=target&&(getOwner()==null||!getOwner().isAlliedTo(target))
                &&(!(getOwner() instanceof Player p)||!(target instanceof Player q)||p.canHarmPlayer(q));
    }
    @Override protected boolean canHitEntity(Entity e){return super.canHitEntity(e)&&e!=getOwner()&&e instanceof LivingEntity living&&allowed(living);}
    @Override protected void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.put("Block",NbtUtils.writeBlockState(block()));tag.putBoolean("Armed",armed);tag.putBoolean("Recover",settled||holderId()>=0);tag.putInt("Age",age);tag.putFloat("Damage",impactDamage);}
    @Override protected void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);entityData.set(BLOCK,NbtUtils.readBlockState(level().holderLookup(Registries.BLOCK),tag.getCompound("Block")));armed=tag.getBoolean("Armed");settled=tag.getBoolean("Recover");age=tag.getInt("Age");impactDamage=Math.max(0,Math.min(12,tag.getFloat("Damage")));}
}
