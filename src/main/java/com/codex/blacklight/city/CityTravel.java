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
    public static final ResourceKey<Level> WORLD = ResourceKey.create(Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath("blacklight", "world"));
    public static boolean isCity(ResourceKey<Level> key) { return key.equals(CITY)||key.equals(WORLD); }
    static ServerLevel commandLevel(net.minecraft.commands.CommandSourceStack source) {
        return isCity(source.getLevel().dimension())?source.getLevel():source.getServer().getLevel(WORLD);
    }
    private static final String RETURN = "BlacklightCityReturn";

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        var root = Commands.literal("blacklightcity")
            .then(Commands.literal("travel").executes(c -> travel(c.getSource().getPlayerOrException())))
            .then(Commands.literal("futian").executes(c -> travel(c.getSource().getPlayerOrException(),CITY)))
            .then(Commands.literal("credits").executes(c -> {
                c.getSource().sendSuccess(()->Component.literal("黑光维度 · 深圳：Map data © OpenStreetMap contributors (ODbL 1.0), Geofabrik 2026-10-14; elevation Mapzen Terrain Tiles / USGS / NOAA; Futian core Arnis 3.3.0. https://www.openstreetmap.org/copyright"),false);return 1;
            }))
            .then(Commands.literal("hives").executes(c -> {
                var level=commandLevel(c.getSource());
                if(level==null)return 0;
                var data=CityData.get(level);
                for(int index=0;index<2;index++){
                    var pos=CitySystem.port(level,index,0);
                    String name=CitySystem.futian(level)?FutianLayout.hive(level.getSeed(),index).name():"Hive "+(index+1);
                    var message=Component.translatable("city.blacklight.hive_location",name,pos.getX(),pos.getY(),pos.getZ(),
                            Component.translatable(data.destroyed(index)?"city.blacklight.hive_inactive":"city.blacklight.hive_active"));
                    c.getSource().sendSuccess(()->message,false);
                }
                return 2;
            }))
            .then(Commands.literal("status").executes(c -> {
                var level = commandLevel(c.getSource());
                if (level == null) return 0;
                var data = CityData.get(level);
                c.getSource().sendSuccess(() -> Component.translatable("city.blacklight.status",
                    Integer.bitCount(data.destroyedMask), Long.bitCount(data.infectedMask)), false);
                return 1;
            }));
        for (String setting : new String[]{"population", "spread", "facade"}) {
            root.then(Commands.literal(setting).requires(s -> s.hasPermission(2))
                .then(Commands.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c -> {
                    var level = commandLevel(c.getSource());
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

    private static final java.util.Map<ServerPlayer,PendingTravel> PENDING=new java.util.HashMap<>();
    private static final class PendingTravel {
        final ServerLevel source,target;final BlockPos origin;final net.minecraft.world.phys.Vec3 start;
        final float yaw,pitch;final boolean returning;final long deadline;
        final HydraDestination ticket=new HydraDestination();
        PendingTravel(ServerPlayer p,ServerLevel target,BlockPos origin,float yaw,float pitch,boolean returning){
            source=p.serverLevel();this.target=target;this.origin=origin;this.yaw=yaw;this.pitch=pitch;this.returning=returning;
            start=p.position();deadline=p.server.getTickCount()+600L;
        }
    }
    public static int travel(ServerPlayer player) {
        return travel(player,WORLD);
    }
    static int travel(ServerPlayer player,ResourceKey<Level> destination) {
        if (HydraTransport.active(player)||player.isPassenger()||player.isSleeping()||!player.isAlive()
                ||com.codex.blacklight.power.BiomassSystem.active(player)
                ||player.getCooldowns().isOnCooldown(CityBlocks.TRANSIT.get())) return 0;
        if(PENDING.containsKey(player))return 0;
        var server=player.getServer();if(server==null)return 0;
        boolean returning=isCity(player.level().dimension());
        ServerLevel target;BlockPos origin;float yaw,pitch;
        if(returning){
            var tag=player.getPersistentData().getCompound(RETURN);
            var id=ResourceLocation.tryParse(tag.getString("dimension"));
            target=id==null?server.overworld():server.getLevel(ResourceKey.create(Registries.DIMENSION,id));
            if(target==null||isCity(target.dimension()))target=server.overworld();
            origin=tag.contains("x")?BlockPos.containing(tag.getDouble("x"),tag.getDouble("y"),tag.getDouble("z")):target.getSharedSpawnPos();
            yaw=tag.getFloat("yaw");pitch=tag.getFloat("pitch");
        }else{
            target=server.getLevel(destination);
            if(target==null){player.displayClientMessage(Component.translatable("city.blacklight.unavailable"),false);return 0;}
            origin=arrivalOrigin(target);yaw=CitySystem.futian(target)?180:0;pitch=0;
        }
        // A bounded asynchronous ticket also supports returns to unloaded original worlds.
        if(PENDING.size()>=16)return 0;
        PENDING.put(player,new PendingTravel(player,target,origin,yaw,pitch,returning));
        player.displayClientMessage(Component.translatable("hydra.blacklight.wait"),true);
        tickTravel(player);return 1;
    }
    private static void cancelTravel(ServerPlayer p){var pending=PENDING.remove(p);if(pending!=null)pending.ticket.release();}
    private static void tickTravel(ServerPlayer p){
        var pending=PENDING.get(p);if(pending==null)return;
        if(!p.isAlive()||p.isRemoved()||p.serverLevel()!=pending.source||p.position().distanceToSqr(pending.start)>64
                ||p.isPassenger()||p.isSleeping()||HydraTransport.active(p)||com.codex.blacklight.power.BiomassSystem.active(p)){
            cancelTravel(p);return;
        }
        if(p.server.getTickCount()>pending.deadline){cancelTravel(p);p.displayClientMessage(Component.translatable("city.blacklight.no_safe_exit"),false);return;}
        if(!pending.ticket.prepare(pending.target,pending.origin))return;
        var safe=safePosition(pending.target,pending.origin);
        if(safe==null){cancelTravel(p);p.displayClientMessage(Component.translatable("city.blacklight.no_safe_exit"),false);return;}
        var back=new CompoundTag();back.putString("dimension",p.level().dimension().location().toString());
        back.putDouble("x",p.getX());back.putDouble("y",p.getY());back.putDouble("z",p.getZ());
        back.putFloat("yaw",p.getYRot());back.putFloat("pitch",p.getXRot());
        try{
            p.teleportTo(pending.target,safe.getX()+.5,safe.getY(),safe.getZ()+.5,pending.yaw,pending.pitch);
            if(p.serverLevel()!=pending.target)return; // Another mod may veto dimension travel.
            if(pending.returning)p.getPersistentData().remove(RETURN);else p.getPersistentData().put(RETURN,back);
            p.fallDistance=0;p.setDeltaMovement(0,0,0);p.getCooldowns().addCooldown(CityBlocks.TRANSIT.get(),40);
            if(!pending.returning)p.displayClientMessage(Component.translatable(pending.target.dimension().equals(WORLD)?"city.blacklight.welcome":"city.blacklight.legacy_welcome"),false);
        }finally{cancelTravel(p);}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p)cancelTravel(p);}
    @SubscribeEvent public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event){
        for(var pending:PENDING.values())pending.ticket.release();PENDING.clear();
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
        if(link==null&&isCity(player.serverLevel().dimension()))return false;
        var target=player.server.getLevel(link==null?WORLD:link.dimension());if(target==null)return false;
        var origin=link==null?arrivalOrigin(target):link.position();
        var centre=new net.minecraft.world.level.ChunkPos(origin);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
            if(target.getChunkSource().getChunkNow(centre.x+x,centre.z+z)==null)return false;
        var safe=link==null?arrivalPosition(target,origin,bodySize):safePosition(target,origin);if(safe==null)return false;
        if(!target.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(safe).toLong())||!target.isPositionEntityTicking(safe))return false;
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
        if(!isCity(source.dimension())){
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

    /** Keep new permanent gateways separate inside the already prepared 3x3 chunks. */
    public static BlockPos arrivalPosition(ServerLevel level,BlockPos origin,float size){
        double width=1.8*Math.max(1.6,Math.min(2.4,size));
        for(int radius=0;radius<=20;radius+=4)for(int dx=-radius;dx<=radius;dx+=4)for(int dz=-radius;dz<=radius;dz+=4){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            var at=origin.offset(dx,0,dz);
            if(level.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4)==null)continue;
            if(!safe(level,at))at=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at);
            if(!safe(level,at))continue;
            var box=new net.minecraft.world.phys.AABB(at.getX()+.5-width/2,at.getY(),at.getZ()+.5-width/2,
                    at.getX()+.5+width/2,at.getY()+2,at.getZ()+.5+width/2);
            boolean loaded=true;
            for(int x=net.minecraft.util.Mth.floor(box.minX)>>4;x<=net.minecraft.util.Mth.floor(box.maxX)>>4;x++)
                for(int z=net.minecraft.util.Mth.floor(box.minZ)>>4;z<=net.minecraft.util.Mth.floor(box.maxZ)>>4;z++)
                    if(level.getChunkSource().getChunkNow(x,z)==null)loaded=false;
            if(!loaded||!level.noCollision(box))continue;
            if(level.getEntitiesOfClass(com.codex.blacklight.entity.BlacklightHydra.class,box.inflate(3,16,3),h->h.isAlive()).isEmpty())return at;
        }
        return null;
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
        if((HydraTransport.active(p)||p.getPersistentData().contains("BlacklightHydraRescue"))
            &&(event.getSource().is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)||event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)))event.setCanceled(true);
        else if(event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)
            &&p.getPersistentData().getLong("BlacklightHydraLanding")>p.level().getGameTime()){
            event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void transitLanding(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event){
        var player=event.getEntity();HydraTransport.place(player);
        if(!(player instanceof ServerPlayer p))return;
        tickTravel(p);
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
