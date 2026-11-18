package com.codex.blacklight.entity;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import java.util.*;

/** Spawned concrete prop, like P1 SpawnObject. No mined block, item drops or saved cargo. */
public final class HydraDebris extends Projectile {
    private static final EntityDataAccessor<BlockState> MATERIAL=SynchedEntityData.defineId(HydraDebris.class,EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Integer> CARRIER=SynchedEntityData.defineId(HydraDebris.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE=SynchedEntityData.defineId(HydraDebris.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> RELEASED=SynchedEntityData.defineId(HydraDebris.class,EntityDataSerializers.LONG);
    private int impacts;
    public HydraDebris(EntityType<? extends HydraDebris> type,Level level){super(type,level);}
    public HydraDebris(BlacklightHydra owner,BlockState material){this(ModEntities.HYDRA_DEBRIS.get(),owner.level());setOwner(owner);entityData.set(CARRIER,owner.getId());entityData.set(MATERIAL,material);entityData.set(SIZE,owner.visualScale());setPos(owner.combatGrip(0));}
    @Override protected void defineSynchedData(){entityData.define(MATERIAL,Blocks.STONE.defaultBlockState());entityData.define(CARRIER,-1);entityData.define(SIZE,2F);entityData.define(RELEASED,-1L);}
    public BlockState material(){return entityData.get(MATERIAL);}
    public float size(){return entityData.get(SIZE);}
    public boolean released(){return entityData.get(RELEASED)>=0;}
    public float flightAge(float partial){return released()?(float)Math.max(0,level().getGameTime()-entityData.get(RELEASED))+partial:0;}
    public int impacts(){return impacts;}
    public BlacklightHydra carrier(){return level().getEntity(entityData.get(CARRIER)) instanceof BlacklightHydra h?h:null;}
    public Vec3 center(float partial){var h=carrier();return !released()&&h!=null?h.combatGrip(partial):getPosition(partial);}
    public HydraContact.Box shape(float partial){return HydraThrow.box(center(partial),size(),flightAge(partial));}
    @Override public AABB getBoundingBoxForCulling(){return new AABB(center(0),center(1)).inflate(size());}
    public void release(Vec3 target){if(released())return;var h=carrier();if(h==null){discard();return;}setPos(h.combatGrip(0));entityData.set(RELEASED,level().getGameTime());setDeltaMovement(HydraThrow.velocity(position(),target));hasImpulse=true;hurtMarked=true;}
    @Override public boolean shouldBeSaved(){return false;}
    @Override protected void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);discard();}
    @Override protected void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);}
    @Override public void tick(){
        super.tick();var h=carrier();
        if(!released()){
            if(h==null||!h.isAlive()||!HydraThrow.action(h.action())){if(!level().isClientSide)discard();return;}
            setPos(h.combatGrip(0));return;
        }
        Vec3 from=position(),velocity=getDeltaMovement(),to=from.add(velocity);
        if(flightAge(0)>100||h==null||!BlockThrowCollision.loaded(level(),from,to,new Vec3(size(),size(),size()))){if(!level().isClientSide)discard();return;}
        var sweep=new AABB(from,to).inflate(size());var terrain=new ArrayList<AABB>();
        for(var s:level().getBlockCollisions(this,sweep))terrain.addAll(s.toAabbs());
        float age=flightAge(0);
        java.util.function.DoubleFunction<List<HydraContact.Box>> pose=t->List.of(HydraThrow.box(from.lerp(to,t),size(),age+(float)t));
        var block=HydraContact.obstacle(pose,0,1,terrain);
        HydraContact.Obstacle nearest=block;LivingEntity victim=null;
        for(var candidate:level().getEntitiesOfClass(LivingEntity.class,sweep,h::canHitCombatTarget)){
            var hit=HydraContact.obstacle(pose,0,1,List.of(candidate.getBoundingBox()));
            if(hit!=null&&(nearest==null||hit.safeAge()<nearest.safeAge()-.0001F)){nearest=hit;victim=candidate;}
        }
        if(nearest!=null){
            setPos(from.lerp(to,nearest.safeAge()));
            // Predict stopping on the same geometry, but only the server accepts a hit.
            if(level().isClientSide){setDeltaMovement(Vec3.ZERO);return;}
            impacts++;
            if(victim!=null)h.debrisStrike(this,victim,nearest.point());
            else {
                // The exact contacted shape supplies material; no root-centred explosion.
                var at=nearest.point();var top=new BlockHitResult(new Vec3(at.x,nearest.shape().maxY,at.z),net.minecraft.core.Direction.UP,BlockPos.containing(nearest.shape().getCenter()),false);
                if(velocity.y<0&&at.y>=nearest.shape().maxY-.08)TerrainImpactEffects.emit(h,top,2.2F,true);
                else ContactEffects.obstacle(h,at,nearest.shape());
            }
            ((ServerLevel)level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,material()),nearest.point().x,nearest.point().y,nearest.point().z,28,size()*.22,size()*.22,size()*.22,.12);
            discard();return;
        }
        setPos(to);setDeltaMovement(velocity.add(0,-HydraThrow.GRAVITY,0));
    }
}
