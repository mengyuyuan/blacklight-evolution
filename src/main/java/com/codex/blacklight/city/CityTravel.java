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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Server owns travel and return coordinates; the client frames the synchronized hydra scene. */
public final class CityTravel {
    public static final ResourceKey<Level> CITY = ResourceKey.create(Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath("blacklight", "city"));
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
        if (HydraTransport.active(player)||player.isPassenger() || player.isSleeping() || !player.isAlive() || player.getCooldowns().isOnCooldown(CityBlocks.TRANSIT.get())) return 0;
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

    public static BlockPos arrivalOrigin(ServerLevel level) {
        return CitySystem.futian(level)?new BlockPos(670,64,820):new BlockPos(26,64,26);
    }

    /** Used only after the hydra's bounded arrival ticket is ready. */
    public static boolean enterFromHydra(ServerPlayer player,BlockPos returnPos) {
        return enterFromHydra(player,returnPos,1.6F);
    }
    public static boolean enterFromHydra(ServerPlayer player,BlockPos returnPos,float bodySize) {
        return transferFromHydra(player,null,returnPos,bodySize);
    }
    /** Only the server's clamping hydra can choose the endpoint; no client coordinates. */
    public static boolean transferFromHydra(ServerPlayer player,com.codex.blacklight.entity.BlacklightHydra sourceHydra,BlockPos returnPos,float bodySize) {
        if(!player.isAlive()||player.isPassenger()||HydraTransport.active(player))return false;
        var link=sourceHydra==null?null:sourceHydra.link();
        if(link==null&&player.serverLevel().dimension().equals(CITY))return false;
        var target=player.server.getLevel(link==null?CITY:link.dimension());if(target==null)return false;
        var origin=link==null?arrivalOrigin(target):link.position();
        var centre=new net.minecraft.world.level.ChunkPos(origin);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
            if(target.getChunkSource().getChunkNow(centre.x+x,centre.z+z)==null)return false;
        var safe=safePosition(target,origin);if(safe==null)return false;
        com.codex.blacklight.entity.BlacklightHydra carrier=null;
        if(link!=null){
            var entity=target.getEntity(link.endpoint());
            // A destroyed or busy endpoint must not duplicate a connector or steal a passenger.
            if(!(entity instanceof com.codex.blacklight.entity.BlacklightHydra h)||!h.availableForArrival(player))return false;
            carrier=h;
        }
        boolean created=carrier==null;
        if(created){
            carrier=com.codex.blacklight.registry.ModEntities.BLACKLIGHT_HYDRA.get().create(target);
            if(carrier==null)return false;
            carrier.setBodySize(bodySize);
            carrier.moveTo(safe.getX()+.5,safe.getY(),safe.getZ()+.5,180,0);carrier.yBodyRot=180;carrier.yHeadRot=180;
            if(!target.addFreshEntity(carrier))return false;
        }
        var source=player.serverLevel();var sourcePosition=player.position();float sourceYaw=player.getYRot(),sourcePitch=player.getXRot();
        var previousReturn=player.getPersistentData().getCompound(RETURN).copy();
        if(sourceHydra!=null&&created)carrier.connect(player.getUUID(),new HydraLink(source.dimension(),sourceHydra.blockPosition(),sourceHydra.getUUID()));
        if(!source.dimension().equals(CITY)){
            CompoundTag tag=new CompoundTag();tag.putString("dimension",source.dimension().location().toString());
            tag.putDouble("x",returnPos.getX()+.5);tag.putDouble("y",returnPos.getY());tag.putDouble("z",returnPos.getZ()+.5);
            tag.putFloat("yaw",sourceYaw);tag.putFloat("pitch",0);player.getPersistentData().put(RETURN,tag);
        }
        player.teleportTo(target,safe.getX()+.5,safe.getY(),safe.getZ()+.5,carrier.getYRot(),0);
        player.fallDistance=0;player.setDeltaMovement(0,0,0);
        if(!carrier.beginArrival(player)){
            if(created)carrier.discard();
            player.teleportTo(source,sourcePosition.x,sourcePosition.y,sourcePosition.z,sourceYaw,sourcePitch);
            if(previousReturn.isEmpty())player.getPersistentData().remove(RETURN);else player.getPersistentData().put(RETURN,previousReturn);
            return false;
        }
        if(sourceHydra!=null)sourceHydra.connect(player.getUUID(),new HydraLink(target.dimension(),carrier.blockPosition(),carrier.getUUID()));
        player.getCooldowns().addCooldown(CityBlocks.TRANSIT.get(),100);
        player.displayClientMessage(Component.translatable("hydra.blacklight.arrived"),false);
        return true;
    }

    public static BlockPos safePosition(ServerLevel level,BlockPos origin) {
        // Check the saved floor first; if it changed, find nearby ground without destroying blocks.
        for(int radius=0;radius<=8;radius++) for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            BlockPos p=origin.offset(dx,0,dz);
            if(!level.hasChunkAt(p))continue;
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
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void transitDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        // Ground transit must not trigger suffocation or manufacture adaptation stacks.
        if(HydraTransport.active(p)
            &&(event.getSource().is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)||event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)))event.setCanceled(true);
        else if(event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)
            &&p.getPersistentData().getLong("BlacklightHydraLanding")>p.level().getGameTime()){
            event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void transitLanding(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event){
        var player=event.getEntity();HydraTransport.place(player);
        if(!(player instanceof ServerPlayer p))return;
        if(!HydraTransport.active(p)&&p.getData(com.codex.blacklight.registry.ModAttachments.HYDRA_CARRIER)>=0){
            p.setData(com.codex.blacklight.registry.ModAttachments.HYDRA_CARRIER,-1);p.syncData(com.codex.blacklight.registry.ModAttachments.HYDRA_CARRIER);
        }
        if(!HydraTransport.active(p)&&p.getPersistentData().contains("BlacklightHydraRescue")){
            var rescue=p.getPersistentData().getCompound("BlacklightHydraRescue");
            var id=ResourceLocation.tryParse(rescue.getString("Dimension"));
            if(id!=null&&p.level().dimension().location().equals(id)){
                var safe=safePosition(p.serverLevel(),BlockPos.of(rescue.getLong("Position")));
                if(safe==null)return; // Keep the recovery marker until a safe loaded landing exists.
                p.teleportTo(safe.getX()+.5,safe.getY(),safe.getZ()+.5);p.setDeltaMovement(0,0,0);p.fallDistance=0;
            }
            p.getPersistentData().remove("BlacklightHydraRescue");
        }
        long until=p.getPersistentData().getLong("BlacklightHydraLanding");
        if(until>0){
            // A stale onGround packet can arrive just after the throw. Require a
            // settled landing rather than consuming protection on that one tick.
            int grounded=p.onGround()?p.getPersistentData().getInt("BlacklightHydraGrounded")+1:0;
            p.getPersistentData().putInt("BlacklightHydraGrounded",grounded);
            if(grounded>=5||p.isInWater()||p.level().getGameTime()>=until){
                p.getPersistentData().remove("BlacklightHydraLanding");p.getPersistentData().remove("BlacklightHydraGrounded");
            }
        }
    }
}
