package com.codex.blacklight.world;

import com.codex.blacklight.entity.EncounterKind;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.registry.EncounterEntities;
import net.minecraft.core.*;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** The same human rig is visible before the pump starts and after the Mother shell falls. */
public final class GreeneHost extends PathfinderMob {
    public static final int WAITING=0, CHANGING=1, OVERLAP=2, SPENT=3, BLOCKED=4;
    public static final int BREAKOUT=106, CROSSFADE=35;
    private static final EntityDataAccessor<Integer> STAGE=SynchedEntityData.defineId(GreeneHost.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(GreeneHost.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> WATCHING=SynchedEntityData.defineId(GreeneHost.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> NOTICED=SynchedEntityData.defineId(GreeneHost.class,EntityDataSerializers.LONG);
    private int alertTicks;
    private BlockPos station;
    private int elapsed;
    private BlockPos pad;

    public GreeneHost(EntityType<? extends GreeneHost> type, Level level) {
        super(type,level);
        setNoAi(true);
        setPersistenceRequired();
        setInvulnerable(true);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(STAGE,WAITING);
        b.define(START,0L);
        b.define(WATCHING,-1);
        b.define(NOTICED,-1000L);
    }
    public int stage() { return entityData.get(STAGE); }
    public float age(float partial) {
        if(stage()==BLOCKED) return BREAKOUT;
        return (level().isClientSide ? level().getGameTime()-entityData.get(START) : elapsed) + partial;
    }
    public float attention(float partial) {
        float blend=net.minecraft.util.Mth.clamp((level().getGameTime()-entityData.get(NOTICED)+partial)/12F,0,1);
        return entityData.get(WATCHING)>=0?blend:1-blend;
    }
    public float glance(float partial) {
        var subject=level().getEntity(entityData.get(WATCHING));
        if(subject==null) return 0;
        float bearing=(float)Math.toDegrees(Math.atan2(getX()-subject.getX(),subject.getZ()-getZ()));
        return net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(bearing-getYRot()),-73,73)*net.minecraft.util.Mth.DEG_TO_RAD*attention(partial);
    }
    private void notice(Entity subject) {
        int id=subject==null?-1:subject.getId();
        if(entityData.get(WATCHING)!=id) {
            if(id<0 || entityData.get(WATCHING)<0) entityData.set(NOTICED,level().getGameTime());
            entityData.set(WATCHING,id);
        }
    }
    private void observe(ServerLevel level){
        if(alertTicks>0){alertTicks--; return;}
        if(tickCount%4!=0) return;
        var player=level.getNearestPlayer(getX(),getY(),getZ(),14,p->p.isAlive()&&!p.isSpectator());
        boolean heard=player!=null && (player.distanceToSqr(this)<4.5*4.5 ||
                player.distanceToSqr(this)<10*10 && !player.isShiftKeyDown() && player.walkAnimation.speed()>.05F);
        if(heard && hasLineOfSight(player)) {
            notice(player); alertTicks=32;
        } else notice(null);
    }
    private void enter(int stage, int age) {
        elapsed=age;
        entityData.set(STAGE,stage);
        entityData.set(START,level().getGameTime()-age);
        setInvulnerable(stage!=SPENT);
    }
    private static GreeneHost create(ServerLevel level, BlockPos station, BlockPos pad, float yaw) {
        var host=EncounterEntities.GREENE_HOST.get().create(level);
        if (host!=null) {
            host.station=station.immutable();
            host.pad=pad.immutable();
            host.moveTo(pad.getX()+.5,pad.getY(),pad.getZ()+.5,yaw,0);
            host.yBodyRot=host.yBodyRotO=host.yHeadRot=yaw;
        }
        return host;
    }
    static GreeneHost waiting(ServerLevel level, BlockPos station, BlockPos pad, Direction facing) {
        var existing=level.getEntitiesOfClass(GreeneHost.class,new AABB(pad).inflate(2),
                h -> station.equals(h.station) && h.stage()==WAITING && h.isAlive());
        if (!existing.isEmpty()) return existing.get(0);
        var host=create(level,station,pad,facing.toYRot()+48);
        return host!=null && level.addFreshEntity(host) ? host : null;
    }
    static String pressurize(ServerLevel level, BlockPos station, BlockPos pad, Direction facing, ServerPlayer operator) {
        var host=waiting(level,station,pad,facing);
        if(host==null) return "unavailable";
        BossSiteData.get(level).reserve(station,host,operator);
        host.enter(CHANGING,0);
        host.notice(operator);
        PumpStation.startPressure(level,station);
        return "plaza_started";
    }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || station==null) return;
        // A piston, flowing water or a nearby large mob must not move the actor off the emergence pad.
        if(stage()!=SPENT && pad!=null) {
            setPos(pad.getX()+.5,pad.getY(),pad.getZ()+.5);
            setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
        switch(stage()) {
            case WAITING -> {
                observe(level);
                if(tickCount%20==0 && level.hasChunkAt(station) && !(level.getBlockState(station).getBlock() instanceof BossSiteBlock)) discard();
            }
            case CHANGING -> {
                elapsed++;
                if(elapsed==39) level.playSound(null,blockPosition(),SoundEvents.IRON_TRAPDOOR_CLOSE,SoundSource.BLOCKS,.8F,.64F);
                if (elapsed==70 || elapsed==80 || elapsed==94) {
                    double side=elapsed==80?-1:1;
                    double yaw=Math.toRadians(getYRot());
                    double x=getX()+side*1.9*Math.cos(yaw), z=getZ()+side*1.9*Math.sin(yaw);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.CRACKED_STONE_BRICKS.defaultBlockState()),
                            x,getY()+.1,z,9,.32,.09,.32,.11);
                    level.playSound(null,blockPosition(),elapsed==94?SoundEvents.RAVAGER_ROAR:SoundEvents.STONE_BREAK,
                            SoundSource.HOSTILE,1.5F,elapsed==94?.62F:.8F);
                }
                if(elapsed>=BREAKOUT) emerge(level);
            }
            case BLOCKED -> { if(tickCount%10==0) emerge(level); }
            case OVERLAP -> { if(++elapsed>=BREAKOUT+CROSSFADE) discard(); }
            case SPENT -> {
                if(tickCount%60==1) for (var p:level.players()) if(p.distanceToSqr(this)<12*12 && !p.isSpectator())
                    p.displayClientMessage(Component.translatable("survival.blacklight.greene_spent",Component.keybind("key.blacklight.grab"),Component.keybind("key.use")),true);
            }
        }
    }
    private void emerge(ServerLevel level) {
        if (!level.hasChunkAt(station)) return;
        var state=level.getBlockState(station);
        if (!(state.getBlock() instanceof BossSiteBlock) || !state.getValue(BossSiteBlock.MOTHER)) {
            discard();
            return;
        }
        if (!BossSiteBlock.clearPad(level,pad,EncounterKind.ELIZABETH_GREENE,this)) {
            if(stage()!=BLOCKED || tickCount%80==0){
                enter(BLOCKED,BREAKOUT);
                for(var observer:level.players()) if(observer.distanceToSqr(this)<48*48)
                    observer.displayClientMessage(Component.translatable("survival.blacklight.pump_blocked",pad.getX(),pad.getY(),pad.getZ()),true);
            }
            return;
        }
        var mother=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(level);
        if(mother==null){discard();return;}
        float pumpYaw=state.getValue(BossSiteBlock.FACING).toYRot();
        mother.moveTo(getX(),getY(),getZ(),pumpYaw,0);
        mother.yBodyRot=mother.yBodyRotO=mother.yHeadRot=pumpYaw;
        mother.setPersistenceRequired();
        mother.getPersistentData().putLong("PlazaSurface",pad.asLong());
        mother.preparePlazaOpening();
        if(!BossSiteData.get(level).transfer(level,this,mother)){discard();return;}
        enter(OVERLAP,BREAKOUT);
    }
    static boolean releaseFrom(EncounterMob mother, ServerLevel level, BlockPos station) {
        var state=level.getBlockState(station);
        var surface=mother.getPersistentData().contains("PlazaSurface") ? BlockPos.of(mother.getPersistentData().getLong("PlazaSurface"))
                : state.getBlock() instanceof BossSiteBlock ? station.relative(state.getValue(BossSiteBlock.FACING).getOpposite(),12) : mother.blockPosition();
        var host=create(level,station,surface,mother.getYRot());
        if(host==null) return false;
        host.enter(SPENT,0);
        host.setHealth(1);
        return BossSiteData.get(level).transfer(level,mother,host);
    }
    public void absorbedBy(ServerPlayer player) {
        if(stage()!=SPENT) return;
        if(station!=null && level() instanceof ServerLevel server && server.hasChunkAt(station)
                && server.getBlockEntity(station) instanceof PumpStation pump) pump.quietAfterVictory(server);
        BossSiteData.complete(this,player,2);
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(stage()==WAITING && !level().isClientSide && source.getEntity() instanceof ServerPlayer player){
            notice(player); alertTicks=80;
        }
        return (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                || stage()==SPENT && com.codex.blacklight.combat.ConsumeLastChance.executing(this)) && super.hurt(source,amount);
    }
    @Override public boolean isPushable(){return false;}
    @Override public void remove(Entity.RemovalReason reason) {
        if(reason.shouldDestroy()) BossSiteData.removed(this);
        super.remove(reason);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if(station!=null) tag.putLong("Station",station.asLong());
        if(pad!=null) tag.putLong("Surface",pad.asLong());
        tag.putInt("PlazaStage",stage()); tag.putInt("Elapsed",elapsed);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        station=tag.contains("Station")?BlockPos.of(tag.getLong("Station")):null;
        pad=tag.contains("Surface")?BlockPos.of(tag.getLong("Surface")):blockPosition();
        enter(Math.max(WAITING,Math.min(BLOCKED,tag.getInt("PlazaStage"))),Math.max(0,tag.getInt("Elapsed")));
    }
}
