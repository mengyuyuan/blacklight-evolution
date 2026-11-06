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
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

/** A Blacklight hive sac. Each sac commits to one strain, swells, splits to release one
 * creature, shrinks back and swells again until it is destroyed. */
public final class BlacklightHive extends Monster implements GeoEntity {
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent boss=new ServerBossEvent(Component.translatable("entity.blacklight.blacklight_hive"),BossEvent.BossBarColor.RED,BossEvent.BossBarOverlay.NOTCHED_10);
    private int waves,cooldown=80,brood=-1;
    private boolean rewarded;
    private long nextConventionalHit;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> GROWTH=net.minecraft.network.syncher.SynchedEntityData.defineId(BlacklightHive.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Long> HATCHED=net.minecraft.network.syncher.SynchedEntityData.defineId(BlacklightHive.class,net.minecraft.network.syncher.EntityDataSerializers.LONG);
    /** Ticks for a fresh hive to swell from a seed to its full size (original: it grows on Blacklight tissue). */
    public static final int GROW_TICKS=20*90,OPEN_TICKS=20,SHRINK_TICKS=30;
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(GROWTH,.3F);entityData.define(HATCHED,-1000L);}
    public float growth(){return entityData.get(GROWTH);}
    public void setGrowth(float g){entityData.set(GROWTH,net.minecraft.util.Mth.clamp(g,.3F,1F));refreshDimensions();}
    public float hatchAge(float partial){return level().getGameTime()-entityData.get(HATCHED)+partial;}
    @Override public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(GROWTH.equals(key))refreshDimensions();}
    @Override public EntityDimensions getDimensions(Pose pose){return super.getDimensions(pose).scale(.45F+.55F*growth());}
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
        var server=(ServerLevel)level();
        // Original: the sac swells on Blacklight tissue, splits open to release its brood,
        // shrinks back to a seed and swells again. Killing it while it regrows ends the cycle.
        long sinceHatch=level().getGameTime()-entityData.get(HATCHED);
        if(sinceHatch>=OPEN_TICKS&&sinceHatch<OPEN_TICKS+SHRINK_TICKS)setGrowth(1F-.7F*(sinceHatch-OPEN_TICKS+1)/SHRINK_TICKS);
        else if(sinceHatch>=OPEN_TICKS+SHRINK_TICKS&&growth()<1F){
            setGrowth(growth()+.7F/(waves>0&&brood>=0?BROOD_REGROW[brood]:GROW_TICKS));
            if(tickCount%40==0)playSound(net.minecraft.sounds.SoundEvents.SLIME_SQUISH,1.2F,.4F+.4F*growth());
        }
        if(tickCount%50==0)spreadTissue(server);
        if(!com.codex.blacklight.infection.WorldOutbreakSystem.active(server)){boss.removeAllPlayers();return;}
        boss.setProgress(getHealth()/getMaxHealth());
        if(tickCount%20==0){
            var nearby=server.getPlayers(p->p.isAlive()&&!p.isSpectator()&&p.distanceToSqr(this)<32*32);
            for(var p:java.util.List.copyOf(boss.getPlayers()))if(!nearby.contains(p))boss.removePlayer(p);
            for(var p:nearby)boss.addPlayer(p);
        }
        // The strain is fixed for life once the outbreak has reached this sac.
        if(brood<0){brood=rollBrood();applyBroodName();}
        if(boss.getPlayers().isEmpty()||level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL
                ||!level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING))return;
        if(cooldown>0){cooldown--;return;}
        if(growth()<1F||sinceHatch<OPEN_TICKS+SHRINK_TICKS)return;
        // Ripe: one creature of this sac's strain per cycle, while its own brood is below the cap.
        int alive=level().getEntitiesOfClass(Mob.class,getBoundingBox().inflate(48),this::ownGuard).size();
        if(alive>=BROOD_CAP[brood]||!com.codex.blacklight.infection.WorldOutbreakSystem.populationRoom(server,blockPosition())){cooldown=80;return;}
        Mob mob=broodType().create(level());
        if(mob==null||!place(mob,getBbWidth()*.5+1.2,1.5)&&!placeGuard(mob)){cooldown=80;return;}
        mob.setPersistenceRequired();mob.restrictTo(blockPosition(),24);
        mob.getPersistentData().putUUID("BlacklightHiveGuard",getUUID());
        if(!com.codex.blacklight.infection.WorldOutbreakSystem.addScheduled(server,mob)){cooldown=80;return;}
        waves++;cooldown=0;entityData.set(HATCHED,level().getGameTime());
        playSound(net.minecraft.sounds.SoundEvents.SNIFFER_EGG_HATCH,2F,.5F);playSound(net.minecraft.sounds.SoundEvents.SLIME_SQUISH,1.6F,.3F);
        server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.55F,.05F,.05F),2.2F),getX(),getY()+getBbHeight(),getZ(),60,.9,.4,.9,.08);
    }
    private boolean ownGuard(Mob m){return m.isAlive()&&m.getPersistentData().hasUUID("BlacklightHiveGuard")&&m.getPersistentData().getUUID("BlacklightHiveGuard").equals(getUUID());}
    /** Strain keys, regrow time after each hatch, and the cap on this sac's living brood. */
    private static final String[] BROOD_KEYS={"common","stalker","spitter","brute","hunter"};
    private static final int[] BROOD_REGROW={20*30,20*35,20*45,20*70,20*75},BROOD_CAP={4,4,3,2,2};
    public EntityType<? extends Mob> broodType(){
        return switch(Math.max(0,brood)){
            case 1->ModEntities.BLACKLIGHT_STALKER.get();case 2->ModEntities.BLACKLIGHT_SPITTER.get();
            case 3->ModEntities.BLACKLIGHT_BRUTE.get();case 4->ModEntities.BLACKLIGHT_HUNTER.get();
            default->ModEntities.COMMON_INFECTED.get();
        };
    }
    /** Saved strain key, or "" while the sac has not chosen one yet. */
    public String brood(){return brood<0?"":BROOD_KEYS[brood];}
    private int rollBrood(){
        int stage=com.codex.blacklight.city.CityEcology.stage((ServerLevel)level(),blockPosition());int roll=random.nextInt(100);
        if(stage<=0)return roll<75?0:1;
        if(stage==1)return roll<40?0:roll<70?1:2;
        if(stage==2)return roll<15?0:roll<35?1:roll<55?2:roll<75?3:4;
        return roll<20?1:roll<40?2:roll<65?3:4;
    }
    private void applyBroodName(){
        boss.setName(brood<0?Component.translatable("entity.blacklight.blacklight_hive")
                :Component.translatable("entity.blacklight.blacklight_hive.brood",broodType().getDescription()));
    }
    /** The hive's tissue carpet creeps outward over soil and stone (gated by mobGriefing). */
    private void spreadTissue(ServerLevel level){
        if(!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING))return;
        int radius=2+Math.round(growth()*4);
        for(int i=0;i<3;i++){
            var at=blockPosition().offset(random.nextInt(radius*2+1)-radius,0,random.nextInt(radius*2+1)-radius);
            for(int dy=1;dy>=-2;dy--){
                var ground=at.offset(0,dy-1,0);var state=level.getBlockState(ground);
                if(!level.getBlockState(ground.above()).isAir()&&!level.getBlockState(ground.above()).canBeReplaced())continue;
                if(state.is(ModBlocks.HIVE_FLESH.get())||state.is(ModBlocks.HIVE_CARAPACE.get())||state.is(ModBlocks.HIVE_SAC.get()))break;
                boolean soil=state.is(net.minecraft.tags.BlockTags.DIRT)||state.is(net.minecraft.tags.BlockTags.SAND)||state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                        ||state.is(net.minecraft.world.level.block.Blocks.GRAVEL)||state.is(net.minecraft.world.level.block.Blocks.COBBLESTONE)||state.is(net.minecraft.world.level.block.Blocks.STONE_BRICKS);
                if(!soil||level.getBlockEntity(ground)!=null)continue;
                var block=random.nextInt(9)==0?ModBlocks.HIVE_SAC.get():random.nextInt(4)==0?ModBlocks.HIVE_CARAPACE.get():ModBlocks.HIVE_FLESH.get();
                level.setBlock(ground,block.defaultBlockState(),3);break;
            }
        }
    }
    private boolean placeGuard(Mob mob){return place(mob,7,5);}
    private boolean place(Mob mob,double minRadius,double spread){
        for(int attempt=0;attempt<12;attempt++){
            double angle=random.nextDouble()*Math.PI*2,radius=minRadius+random.nextDouble()*spread;
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
    @Override protected void dropCustomDeathLoot(DamageSource source,int looting,boolean recentlyHit){
        super.dropCustomDeathLoot(source,looting,recentlyHit);
        // Vanilla calls this inside doMobLoot and Forge's captured LivingDrops flow.
        if(!rewarded){
            spawnAtLocation(ModItems.BLACKLIGHT_SAMPLE.get(),2);
            spawnAtLocation(ModItems.LIVING_HIVE_TISSUE.get());
            spawnAtLocation(net.minecraft.world.item.Items.EMERALD,3);
        }
    }
    /** Supreme Parasite injection: the hive dies at once and expels the Supreme Hunter. */
    public void parasiteBurst(ServerPlayer player){
        if(level().isClientSide||!isAlive())return;
        if(level() instanceof ServerLevel s)s.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.6F,.02F,.03F),3F),getX(),getY()+1.5,getZ(),120,1.6,1.2,1.6,.1);
        SupremeHunterRewards.parasiteRelease(this,player);
        hurt(com.codex.blacklight.combat.DevastatorDamage.source(player),getMaxHealth()*10);
        if(isAlive()){setHealth(0);die(com.codex.blacklight.combat.DevastatorDamage.source(player));}
    }
    @Override public void die(DamageSource source){
        super.die(source);
        // LivingDeath may be cancelled by another mod; only accepted deaths pay out.
        if(!level().isClientSide&&dead&&!rewarded){
            rewarded=true;
            com.codex.blacklight.city.MilitaryCampaignSystem.hiveDefeated(this);
            com.codex.blacklight.city.CityEcology.cleared((ServerLevel)level(),blockPosition());
            if(source.getEntity() instanceof ServerPlayer player){
                var state=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);
                if(state.isInfected()){state.addEvolutionPoints(180);state.addBiomass(250);com.codex.blacklight.registry.ModAttachments.setData(player, ModAttachments.PLAYER_STATE,state);com.codex.blacklight.registry.ModAttachments.syncData(player, ModAttachments.PLAYER_STATE);}
                com.codex.blacklight.power.GrowthSystem.award(player,"hive_breaker");
                player.displayClientMessage(Component.translatable("message.blacklight.hive_cleared"),false);
            }
            boss.removeAllPlayers();
        }
    }
    @Override public void remove(RemovalReason reason){boss.removeAllPlayers();super.remove(reason);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putBoolean("BlacklightRewarded",rewarded);tag.putFloat("BlacklightGrowth",growth());tag.putInt("BlacklightWaves",waves);tag.putInt("BlacklightWaveCooldown",cooldown);if(brood>=0)tag.putString("BlacklightBrood",BROOD_KEYS[brood]);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);rewarded=tag.getBoolean("BlacklightRewarded");setGrowth(tag.contains("BlacklightGrowth")?tag.getFloat("BlacklightGrowth"):1F);waves=Math.max(0,tag.getInt("BlacklightWaves"));cooldown=com.codex.blacklight.util.MathCompat.clamp(tag.getInt("BlacklightWaveCooldown"),0,240);brood=java.util.Arrays.asList(BROOD_KEYS).indexOf(tag.getString("BlacklightBrood"));applyBroodName();}
    public int waves(){return waves;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){c.add(new AnimationController<>(this,"core",0,s->s.setAndContinue(RawAnimation.begin().thenLoop("idle"))));}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
