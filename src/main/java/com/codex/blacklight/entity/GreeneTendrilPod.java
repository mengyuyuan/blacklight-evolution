package com.codex.blacklight.entity;

import com.codex.blacklight.combat.SpiderFoley;
import com.codex.blacklight.registry.ModEntities;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** A planted one-shot encounter hazard. Ordinary hits can cut it open before the tendrils lash out. */
public final class GreeneTendrilPod extends Mob {
    public static final int WINDUP=28;
    public static final double RADIUS=4;
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(GreeneTendrilPod.class,EntityDataSerializers.LONG);
    private UUID mother;
    private boolean spent;
    public GreeneTendrilPod(EntityType<? extends GreeneTendrilPod> type,Level level){
        super(type,level);setNoAi(true);setNoGravity(true);setPersistenceRequired();xpReward=0;
    }
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(START,0L);}
    @Override public boolean isPushable(){return false;}
    @Override protected void doPush(Entity other){}
    @Override public void addAdditionalSaveData(CompoundTag t){
        super.addAdditionalSaveData(t);if(mother!=null)t.putUUID("Mother",mother);
        t.putLong("Planted",entityData.get(START));t.putBoolean("Spent",spent);
    }
    @Override public void readAdditionalSaveData(CompoundTag t){
        super.readAdditionalSaveData(t);mother=t.hasUUID("Mother")?t.getUUID("Mother"):null;
        entityData.set(START,t.getLong("Planted"));spent=t.getBoolean("Spent");
    }
    public float age(float partial){return Math.max(0,level().getGameTime()-entityData.get(START)+partial);}
    public static void plant(EncounterMob boss,Vec3 tip){
        if(!(boss.level() instanceof ServerLevel l))return;
        var hit=l.clip(new ClipContext(tip.add(0,.65,0),tip.add(0,-1.25,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
        if(hit.getType()!=HitResult.Type.BLOCK || hit.getDirection()!=net.minecraft.core.Direction.UP)return;
        var pod=ModEntities.GREENE_TENDRIL_POD.get().create(l);if(pod==null)return;
        pod.setPos(hit.getLocation().add(0,.02,0));pod.mother=boss.getUUID();
        if(boss.goddess.active())pod.getPersistentData().putBoolean("GoddessPod",true);pod.entityData.set(START,l.getGameTime());
        if(!l.getBlockCollisions(pod,pod.getBoundingBox().deflate(.005)).iterator().hasNext() && l.addFreshEntity(pod))SpiderFoley.play(pod,pod.position(),SpiderFoley.Cue.SHED,1.25F);
    }
    @Override public boolean hurt(DamageSource source,float amount){
        if(level().isClientSide || isRemoved() || spent || amount<=0 || !(source.getEntity() instanceof net.minecraft.world.entity.player.Player))return false;
        cut();return true;
    }
    private void cut(){
        SpiderFoley.play(this,position(),SpiderFoley.Cue.TEAR,1.25F);
        if(level() instanceof ServerLevel l)splinters(l,8);
        discard();
    }
    private void splinters(ServerLevel l,int amount){
        var shards=new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        l.sendParticles(shards,getX(),getY()+.45,getZ(),amount,.31,.17,.31,.11);
    }
    @Override public void tick(){
        super.tick();
        if(!(level() instanceof ServerLevel l))return;
        var owner=mother==null?null:l.getEntity(mother);
        if(!(owner instanceof EncounterMob boss) || (getPersistentData().getBoolean("GoddessPod") ? !boss.goddess.active()||!boss.goddess.damageable() : !boss.spider.live())){discard();return;}
        float age=age(0);
        if(spent){if(age>=WINDUP+12)discard();return;}
        var floor=l.clip(new ClipContext(position().add(0,.10,0),position().add(0,-.20,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        if(floor.getType()!=HitResult.Type.BLOCK){cut();return;}
        if(age==12 || age==22)SpiderFoley.play(this,position(),SpiderFoley.Cue.STRAIN,age==12?.95F:1.25F);
        if(age<WINDUP)return;
        if(age>WINDUP+2){discard();return;}
        spent=true;
        var center=position().add(0,.56,0);
        for(var p:l.getEntitiesOfClass(ServerPlayer.class,getBoundingBox().inflate(RADIUS),p->p.isAlive()&&!p.isCreative()&&!p.isSpectator())){
            var contact=p.getBoundingBox().getCenter();
            if(contact.distanceToSqr(center)>RADIUS*RADIUS || l.clip(new ClipContext(center,contact,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS)continue;
            if(p.hurt(boss.damageSources().mobAttack(boss),(float)boss.kind().damage*.8F)){
                var away=contact.subtract(center).multiply(1,0,1).normalize();
                p.push(away.x*.54,.34,away.z*.54);p.hurtMarked=true;
            }
        }
        SpiderFoley.ground(boss,position(),1.65F,SpiderFoley.Cue.RUPTURE);
        splinters(l,14);
    }
}
