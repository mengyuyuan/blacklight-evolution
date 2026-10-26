package com.codex.blacklight.entity;

import com.codex.blacklight.registry.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.damagesource.DamageSource;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

/** A persistent encounter core. Three finite reinforcement waves survive reloads. */
public final class BlacklightHive extends Monster implements GeoEntity {
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent boss=new ServerBossEvent(Component.translatable("entity.blacklight.blacklight_hive"),BossEvent.BossBarColor.RED,BossEvent.BossBarOverlay.NOTCHED_10);
    private int waves,hunterWaves,cooldown=80;
    private boolean rewarded;
    private long nextConventionalHit;
    public BlacklightHive(EntityType<? extends BlacklightHive> type,Level level){super(type,level);setPersistenceRequired();xpReward=40;}
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,160).add(Attributes.MOVEMENT_SPEED,0).add(Attributes.KNOCKBACK_RESISTANCE,1);}
    @Override protected void registerGoals(){}
    @Override protected boolean shouldDespawnInPeaceful(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public void knockback(double strength,double x,double z){}
    @Override public void push(double x,double y,double z){}
    @Override public boolean hurt(DamageSource source,float amount){
        if(com.codex.blacklight.combat.HiveDefense.fullDamage(source))return super.hurt(source,amount);
        if(level().isClientSide||level().getGameTime()<nextConventionalHit)return false;
        float chip=com.codex.blacklight.combat.HiveDefense.chipDamage(amount);
        if(chip<=0)return false;
        boolean accepted=super.hurt(source,chip);
        // TaCZ clears vanilla invulnerableTime between normal/AP damage and
        // every bullet. Keep a separate server-owned structural hit interval.
        if(accepted)nextConventionalHit=level().getGameTime()+com.codex.blacklight.combat.HiveDefense.CHIP_INTERVAL;
        return accepted;
    }
    @Override public void tick(){
        super.tick();if(level().isClientSide)return;
        // Death can occur immediately before the 20-tick audience refresh.
        // Never re-add viewers during the corpse's remaining removal ticks.
        if(!isAlive()||isRemoved()){boss.removeAllPlayers();return;}
        if(!com.codex.blacklight.infection.WorldOutbreakSystem.active((net.minecraft.server.level.ServerLevel)level())){boss.removeAllPlayers();return;}
        boss.setProgress(getHealth()/getMaxHealth());
        if(tickCount%20==0){
            var nearby=((net.minecraft.server.level.ServerLevel)level()).getPlayers(p->p.isAlive()&&!p.isSpectator()&&p.distanceToSqr(this)<32*32);
            for(var p:java.util.List.copyOf(boss.getPlayers()))if(!nearby.contains(p))boss.removePlayer(p);
            for(var p:nearby)boss.addPlayer(p);
        }
        if(!isAlive()||boss.getPlayers().isEmpty()||level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL)return;
        if(cooldown>0){cooldown--;return;}
        // Old hives only spawned stalkers/spitters/brutes. Give such a saved hive one
        // finite hunter brood too, without resetting its already spent wave budget.
        int stage=com.codex.blacklight.city.CityEcology.stage((net.minecraft.server.level.ServerLevel)level(),blockPosition());
        if(waves>=3&&(hunterWaves>0||stage<2))return;
        int alive=level().getEntitiesOfClass(Mob.class,getBoundingBox().inflate(24),e->e instanceof BlacklightMutant||e instanceof BlacklightHunter).size();
        if(alive>=6){cooldown=80;return;}
        int spawned=0;
        for(int i=0;i<Math.min(waves>=3?1:2,6-alive);i++){
            Mob mob=stage<2?ModEntities.COMMON_INFECTED.get().create(level()):i==0?ModEntities.BLACKLIGHT_HUNTER.get().create(level()):
                    (waves==2?ModEntities.BLACKLIGHT_BRUTE:ModEntities.BLACKLIGHT_SPITTER).get().create(level());
            if(mob==null||!placeGuard(mob))continue;
            mob.setPersistenceRequired();mob.restrictTo(blockPosition(),24);
            mob.getPersistentData().putUUID("BlacklightHiveGuard",getUUID());
            if(level().addFreshEntity(mob)){
                spawned++;if(mob instanceof BlacklightHunter)hunterWaves++;
            }
        }
        if(spawned>0)waves=Math.min(3,waves+1);cooldown=spawned>0?240:80;
    }
    private boolean placeGuard(Mob mob){
        for(int attempt=0;attempt<12;attempt++){
            double angle=random.nextDouble()*Math.PI*2,radius=7+random.nextDouble()*5;
            double x=getX()+Math.cos(angle)*radius,z=getZ()+Math.sin(angle)*radius;
            for(int step=0;step<=8;step++){
                int offset=step==0?0:(step%2==1?(step+1)/2:-step/2);
                var at=net.minecraft.core.BlockPos.containing(x,getY()+offset,z);
                if(at.getY()<=level().getMinBuildHeight()||at.getY()+mob.getBbHeight()>=level().getMaxBuildHeight())continue;
                mob.moveTo(x,at.getY(),z,random.nextFloat()*360,0);
                var box=mob.getBoundingBox();
                if(!level().hasChunksAt(net.minecraft.core.BlockPos.containing(box.minX,box.minY-1,box.minZ),
                        net.minecraft.core.BlockPos.containing(box.maxX,box.maxY,box.maxZ))
                        ||!level().getWorldBorder().isWithinBounds(box)||!level().getBlockState(at.below()).isSolid()
                        ||level().containsAnyLiquid(box)||!level().noCollision(mob))continue;
                return true;
            }
        }
        return false;
    }
    @Override protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level,DamageSource source,boolean recentlyHit){
        super.dropCustomDeathLoot(level,source,recentlyHit);
        // Vanilla calls this inside doMobLoot and NeoForge's captured LivingDrops flow.
        if(!rewarded){
            spawnAtLocation(ModItems.BLACKLIGHT_SAMPLE.get(),2);
            spawnAtLocation(ModItems.LIVING_HIVE_TISSUE.get());
            spawnAtLocation(net.minecraft.world.item.Items.EMERALD,3);
        }
    }
    @Override public void die(DamageSource source){
        super.die(source);
        // LivingDeath may be cancelled by another mod; only accepted deaths pay out.
        if(!level().isClientSide&&dead&&!rewarded){
            rewarded=true;
            com.codex.blacklight.city.CityEcology.cleared((ServerLevel)level(),blockPosition());
            if(source.getEntity() instanceof ServerPlayer player){
                var state=player.getData(ModAttachments.PLAYER_STATE);
                if(state.isInfected()){state.addEvolutionPoints(180);state.addBiomass(250);player.setData(ModAttachments.PLAYER_STATE,state);player.syncData(ModAttachments.PLAYER_STATE);}
                com.codex.blacklight.power.GrowthSystem.award(player,"hive_breaker");
                player.displayClientMessage(Component.translatable("message.blacklight.hive_cleared"),false);
            }
            boss.removeAllPlayers();
        }
    }
    @Override public void remove(RemovalReason reason){boss.removeAllPlayers();super.remove(reason);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putBoolean("BlacklightRewarded",rewarded);tag.putInt("BlacklightWaves",waves);tag.putInt("BlacklightHunterWaves",hunterWaves);tag.putInt("BlacklightWaveCooldown",cooldown);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);rewarded=tag.getBoolean("BlacklightRewarded");waves=Math.clamp(tag.getInt("BlacklightWaves"),0,3);hunterWaves=Math.clamp(tag.getInt("BlacklightHunterWaves"),0,3);cooldown=Math.clamp(tag.getInt("BlacklightWaveCooldown"),0,240);}
    public int waves(){return waves;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){c.add(new AnimationController<>(this,"core",0,s->s.setAndContinue(RawAnimation.begin().thenLoop("idle"))));}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
