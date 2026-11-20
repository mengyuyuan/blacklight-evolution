package com.codex.blacklight.world;

import com.codex.blacklight.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.HolderLookup;

public final class PumpStation extends BlockEntity {
    public PumpStation(BlockPos pos,BlockState state) { super(ModBlockEntities.PUMP.get(),pos,state); }
    CompoundTag shift = new CompoundTag();
    private boolean hasRig;
    private boolean running;
    private long startedAt;
    private long impactAt;
    private int dent; // 0 intact, 1 deflected across the nose, 2 direct broadside
    private long nextPreview;

    public boolean hasRig() { return hasRig; }
    public boolean running() { return running; }
    public long startedAt() { return startedAt; }
    public long impactAt() { return impactAt; }
    public int dent() { return dent; }

    public static void startPressure(ServerLevel level, BlockPos station) {
        if (!(level.getBlockEntity(station) instanceof PumpStation pump)) return;
        pump.running=true;
        pump.startedAt=level.getGameTime();
        pump.dent=0;
        pump.impactAt=0;
        pump.sync(level);
        level.playSound(null,station,SoundEvents.PISTON_EXTEND,SoundSource.BLOCKS,1.25F,.65F);
    }

    public static void openingImpact(ServerLevel level, BlockPos station, boolean interrupted) {
        if (!(level.getBlockEntity(station) instanceof PumpStation pump) || !pump.running || pump.dent!=0) return;
        pump.dent=interrupted?1:2;
        pump.impactAt=level.getGameTime();
        pump.sync(level);
        if (!pump.hasRig) return;
        var nose=station.relative(pump.getBlockState().getValue(BossSiteBlock.FACING).getClockWise(),interrupted?5:2);
        level.playSound(null,nose,interrupted?SoundEvents.IRON_TRAPDOOR_CLOSE:SoundEvents.ANVIL_LAND,
                SoundSource.BLOCKS,interrupted?1.25F:1.9F,interrupted?.8F:.54F);
        level.sendParticles(ParticleTypes.CRIT,nose.getX()+.5,nose.getY()+1.45,nose.getZ()+.5,
                interrupted?8:16,.65,.23,.4,.12);
    }

    void quietAfterVictory(ServerLevel level) {
        nextPreview=level.getGameTime()+1200;
        running=false;
        sync(level);
        if (hasRig) level.playSound(null,worldPosition,SoundEvents.FIRE_EXTINGUISH,SoundSource.BLOCKS,.9F,.7F);
    }

    private void sync(ServerLevel level) {
        setChanged();
        level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);
        tag.put("Dispatch",shift.copy());
        tag.putLong("NextPreview",nextPreview);
        writeRig(tag);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag=new CompoundTag();
        writeRig(tag);
        return tag;
    }
    private void writeRig(CompoundTag tag) {
        tag.putBoolean("FieldRig",hasRig);
        tag.putBoolean("Running",running);
        tag.putLong("StartedAt",startedAt);
        tag.putLong("ImpactAt",impactAt);
        tag.putInt("Dent",dent);
    }

    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);
        shift=tag.getCompound("Dispatch").copy();
        nextPreview=tag.getLong("NextPreview");
        hasRig=tag.getBoolean("FieldRig");
        running=tag.getBoolean("Running");
        startedAt=tag.getLong("StartedAt");
        impactAt=tag.getLong("ImpactAt");
        dent=tag.getInt("Dent");
    }

    void releaseGate(ServerLevel level, com.codex.blacklight.entity.EncounterMob boss, ServerPlayer operator) {
        boss.holdAtConvoyGate();
        shift.putLong("LatchAt",level.getGameTime());
        shift.putUUID("ConvoyBoss",boss.getUUID());
        shift.putUUID("ConvoyOperator",operator.getUUID());
        setChanged();
    }
    void openGate(ServerLevel level, BlockPos console, BlockState state) {
        if(!shift.hasUUID("ConvoyBoss")) return;
        long elapsed=level.getGameTime()-shift.getLong("LatchAt");
        var facing=state.getValue(BossSiteBlock.FACING);
        int open=(int)Math.min(4,Math.max(0,elapsed)/4);
        if(elapsed%4==0 || elapsed>=16) {
            for(int dx=-open;dx<=open;dx++) for(int y=1;y<=4;y++)
                ConvoyRail.bend(level,ConvoyRail.relative(console,facing,16+dx,y,21));
            if(elapsed<=16) level.playSound(null,console.relative(facing.getOpposite(),7),SoundEvents.PISTON_EXTEND,SoundSource.BLOCKS,1.6F,.55F+open*.08F);
        }
        if (elapsed>=16) {
            var boss=level.getEntity(shift.getUUID("ConvoyBoss"));
            var player=level.getPlayerByUUID(shift.getUUID("ConvoyOperator"));
            if (boss instanceof com.codex.blacklight.entity.EncounterMob mob)
                mob.leaveConvoyGate(player);
            shift.remove("ConvoyBoss");shift.remove("ConvoyOperator");setChanged();
        }
    }

    void watch(ServerLevel level, BlockPos pos, BlockState state) {
        FutianDispatch.tick(level,this);
        long tick=level.getGameTime();
        if (running && hasRig) {
            long age=tick-startedAt;
            if (age>=0 && age%47==4) {
                level.playSound(null,pos,SoundEvents.PISTON_CONTRACT,SoundSource.BLOCKS,age<50?.85F:.3F,.58F);
            }
            if (age==36) {
                var grate=pos.relative(state.getValue(BossSiteBlock.FACING).getOpposite(),11);
                level.playSound(null,grate,SoundEvents.IRON_TRAPDOOR_OPEN,SoundSource.BLOCKS,.95F,.63F);
            }
        }
        if (tick<nextPreview || (tick+pos.asLong())%20!=0 || !level.hasNearbyAlivePlayer(pos.getX()+.5,pos.getY(),pos.getZ()+.5,48)
                || BossSiteData.get(level).occupied(pos)) return;
        var pad=pos.relative(state.getValue(BossSiteBlock.FACING).getOpposite(),12);
        if (level.isPositionEntityTicking(pad) && level.getBlockState(pad).isAir())
            GreeneHost.waiting(level,pos,pad,state.getValue(BossSiteBlock.FACING));
    }
}
