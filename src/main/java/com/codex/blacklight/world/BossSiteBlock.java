package com.codex.blacklight.world;

import com.codex.blacklight.entity.EncounterKind;
import com.codex.blacklight.registry.EncounterEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/** The console faces the operator. Its marked emergence pad is twelve blocks behind it. */
public final class BossSiteBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty MOTHER = BooleanProperty.create("mother");
    private static final com.mojang.serialization.MapCodec<BossSiteBlock> CODEC = simpleCodec(BossSiteBlock::new);
    @Override protected com.mojang.serialization.MapCodec<BossSiteBlock> codec() { return CODEC; }
    public BossSiteBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(MOTHER, false));
    }
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PumpStation(pos,state);
    }
    @Override public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel server) || type!=com.codex.blacklight.registry.ModBlockEntities.PUMP.get()) return null;
        return (ignored,pos,current,pump) -> {
            if(current.getValue(MOTHER)) ((PumpStation)pump).watch(server,pos,current);
            else ((PumpStation)pump).openGate(server,pos,current);
        };
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOTHER);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (player instanceof ServerPlayer operator && level instanceof ServerLevel server && !player.isSpectator()) {
            String outcome = activate(server, pos, operator);
            player.displayClientMessage(Component.translatable("survival.blacklight." + outcome), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static String activate(ServerLevel level, BlockPos console, ServerPlayer operator) {
        var state = level.getBlockState(console);
        if (!(state.getBlock() instanceof BossSiteBlock) || operator.isSpectator() || !operator.isAlive()
                || operator.level() != level || (level.dimension()!=Level.OVERWORLD &&
                    !(state.getValue(MOTHER) && level.dimension()==com.codex.blacklight.city.CityTravel.WORLD))) return "unavailable";
        if (level.getDifficulty() == Difficulty.PEACEFUL) return "peaceful";
        var record = BossSiteData.get(level);
        if (record.occupied(console)) return "active";
        var kind = state.getValue(MOTHER) ? EncounterKind.ELIZABETH_GREENE : EncounterKind.GOLIATH;
        var facing = state.getValue(FACING);
        var pad = console.relative(facing.getOpposite(), 12);
        if (kind==EncounterKind.ELIZABETH_GREENE && !hasPumpKey(operator)) return "pump_key_needed";
        if (kind==EncounterKind.GOLIATH && !operator.isHolding(com.codex.blacklight.registry.ModItems.CONTAINMENT_DECODER.get())) return "decoder_needed";
        if (!clearPad(level,pad,kind,null)) return "obstructed";
        if (kind==EncounterKind.ELIZABETH_GREENE) return GreeneHost.pressurize(level,console,pad,facing,operator);
        var boss = EncounterEntities.CREATURES.get(kind).get().create(level);
        if (boss == null) return "unavailable";
        boss.moveTo(pad.getX() + .5, pad.getY(), pad.getZ() + .5, facing.toYRot(), 0);
        boss.setPersistenceRequired();
        record.reserve(console, boss, operator);
        try {
            if (!level.addFreshEntity(boss) || boss.isRemoved()) return "unavailable";
        } finally {
            if (level.getEntity(boss.getUUID()) != boss || boss.isRemoved()) record.release(boss);
        }
        ConvoyRail.reset(level,console,facing);
        if(level.getBlockEntity(console) instanceof PumpStation station) station.releaseGate(level,boss,operator);
        if (!operator.isCreative()) boss.setTarget(operator);
        level.playSound(null, console, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2F, .6F);
        return "convoy_started";
    }
    public static boolean hasPumpKey(Player player) {
        var key=com.codex.blacklight.registry.ModItems.BLOODTOX_STARTER.get();
        return player.getMainHandItem().is(key) || player.getOffhandItem().is(key);
    }

    static boolean clearPad(ServerLevel level, BlockPos pad, EncounterKind kind, net.minecraft.world.entity.Entity ignored) {
        var body = AABB.ofSize(net.minecraft.world.phys.Vec3.atBottomCenterOf(pad).add(0, kind.height / 2, 0),
                kind.width, kind.height, kind.width);
        return level.hasChunksAt(BlockPos.containing(body.minX, body.minY, body.minZ), BlockPos.containing(body.maxX, body.maxY, body.maxZ))
                && level.getWorldBorder().isWithinBounds(body) && level.noCollision(ignored, body)
                && !level.containsAnyLiquid(body) && level.getBlockState(pad.below()).isFaceSturdy(level, pad.below(), Direction.UP);
    }
}
