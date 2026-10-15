package com.codex.blacklight.city;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Server owns travel and return coordinates. No client-provided position or forced camera. */
public final class CityTravel {
    public static final ResourceKey<Level> CITY = ResourceKey.create(Registries.DIMENSION,
        new ResourceLocation("blacklight", "city"));
    private static final String RETURN = "BlacklightCityReturn";

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        var root = Commands.literal("blacklightcity")
            .then(Commands.literal("travel").executes(c -> travel(c.getSource().getPlayerOrException())))
            .then(Commands.literal("credits").executes(c -> {
                c.getSource().sendSuccess(()->Component.literal("深圳福田市民中心周边 · Map data © OpenStreetMap contributors (ODbL 1.0), generated with Arnis 3.3.0. https://www.openstreetmap.org/copyright"),false);return 1;
            }))
            .then(Commands.literal("status").executes(c -> {
                var level = c.getSource().getServer().getLevel(CITY);
                if (level == null) return 0;
                var data = CityData.get(level);
                c.getSource().sendSuccess(() -> Component.translatable("city.blacklight.status",
                    Integer.bitCount(data.destroyedMask), Long.bitCount(data.infectedMask)), false);
                return 1;
            }));
        for (String setting : new String[]{"population", "spread", "facade"}) {
            root.then(Commands.literal(setting).requires(s -> s.hasPermission(2))
                .then(Commands.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c -> {
                    var level = c.getSource().getServer().getLevel(CITY);
                    if (level == null) return 0;
                    var data = CityData.get(level);
                    boolean enabled = com.mojang.brigadier.arguments.BoolArgumentType.getBool(c, "enabled");
                    if (setting.equals("population")) data.population = enabled;
                    if (setting.equals("spread")) data.spread = enabled;
                    if (setting.equals("facade")) data.facade = enabled;
                    data.setDirty();
                    c.getSource().sendSuccess(() -> Component.literal(setting + " = " + enabled), true);
                    return 1;
                })));
        }
        root.then(CityPregenerator.command());
        event.getDispatcher().register(root);
    }

    public static int travel(ServerPlayer player) {
        if (player.isPassenger() || player.isSleeping() || !player.isAlive() || player.getCooldowns().isOnCooldown(CityBlocks.TRANSIT.get())) return 0;
        var server = player.getServer();
        if (server == null) return 0;
        if (player.serverLevel().dimension().equals(CITY)) {
            CompoundTag tag = player.getPersistentData().getCompound(RETURN);
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("dimension"));
            ServerLevel target = id == null ? server.overworld() : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            if (target == null || target.dimension().equals(CITY)) target = server.overworld();
            BlockPos origin = tag.contains("x") ? BlockPos.containing(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")) : target.getSharedSpawnPos();
            BlockPos safe = safePosition(target, origin);
            if (safe == null) { player.displayClientMessage(Component.translatable("city.blacklight.no_safe_exit"), false); return 0; }
            player.teleportTo(target, safe.getX()+.5, safe.getY(), safe.getZ()+.5, tag.getFloat("yaw"), tag.getFloat("pitch"));
            player.getPersistentData().remove(RETURN);
        } else {
            ServerLevel target = server.getLevel(CITY);
            if (target == null) { player.displayClientMessage(Component.translatable("city.blacklight.unavailable"), false); return 0; }
            BlockPos safe = safePosition(target, CitySystem.futian(target)?new BlockPos(670,64,820):new BlockPos(26,64,26));
            if (safe == null) return 0;
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", player.level().dimension().location().toString());
            tag.putDouble("x", player.getX()); tag.putDouble("y", player.getY()); tag.putDouble("z", player.getZ());
            tag.putFloat("yaw",player.getYRot());tag.putFloat("pitch",player.getXRot());
            player.getPersistentData().put(RETURN,tag);
            player.teleportTo(target,safe.getX()+.5,safe.getY(),safe.getZ()+.5,CitySystem.futian(target)?180:0,0);
            player.displayClientMessage(Component.translatable("city.blacklight.welcome"),false);
        }
        player.fallDistance=0; player.setDeltaMovement(0,0,0);
        player.getCooldowns().addCooldown(CityBlocks.TRANSIT.get(),40);
        return 1;
    }

    private static BlockPos safePosition(ServerLevel level,BlockPos origin) {
        // Check the saved floor first; if it changed, find nearby ground without destroying blocks.
        for(int radius=0;radius<=8;radius++) for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            BlockPos p=origin.offset(dx,0,dz);
            if(safe(level,p))return p;
            p=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p);
            if(safe(level,p))return p;
        }
        return null;
    }
    private static boolean safe(ServerLevel level,BlockPos p) {
        return level.getWorldBorder().isWithinBounds(p) && p.getY()>level.getMinBuildHeight() && p.getY()+1<level.getMaxBuildHeight()
            && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
            && level.getBlockState(p.below()).isFaceSturdy(level,p.below(),net.minecraft.core.Direction.UP)
            && !level.getBlockState(p.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK);
    }
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event) {
        if(event.getOriginal().getPersistentData().contains(RETURN)) event.getEntity().getPersistentData().put(RETURN,event.getOriginal().getPersistentData().getCompound(RETURN).copy());
    }
}
