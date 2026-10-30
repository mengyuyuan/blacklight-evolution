package com.codex.blacklight.firearms;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Main-thread structural impacts, with transient bounded cracks and normal protection hooks. */
public final class FirearmBlockDamage {
    private static final Map<ServerLevel,BlockDamageLedger> LEDGERS=new WeakHashMap<>();
    private static TagKey<Block> tag(String name){return TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("blacklight",name));}
    private static final TagKey<Block> PROTECTED=tag("firearm_protected"),FRAGILE=tag("firearm_fragile"),WOOD=tag("firearm_wood"),EARTH=tag("firearm_earth"),METAL=tag("firearm_metal");
    private FirearmBlockDamage(){}
    private static final ThreadLocal<BlockEvent.BreakEvent> BREAK_EVENT=new ThreadLocal<>();
    public static boolean ownsBreakEvent(BlockEvent.BreakEvent event){return BREAK_EVENT.get()==event;}
    public static void bullet(LivingEntity owner,WeaponDefinition definition,BlockHitResult hit){
        if(!FirearmsWorldConfig.BULLETS.get()||definition.kind()!=WeaponDefinition.Kind.BULLET||hit.getType()!=HitResult.Type.BLOCK||!valid(owner))return;
        var level=(ServerLevel)owner.level();var at=hit.getBlockPos();
        if(!loaded(level,at))return;
        var material=material(level,at,level.getBlockState(at));
        apply(level,owner,at,BlockDamagePolicy.bulletImpact(material,definition.damage(),definition==WeaponCatalog.MACHINE_GUN));
    }
    public static int blast(ServerLevel level,LivingEntity owner,WeaponDefinition definition,Vec3 center){
        if(!FirearmsWorldConfig.EXPLOSIVES.get()||!valid(owner)||owner.level()!=level||!Double.isFinite(center.lengthSqr()))return 0;
        double radius=Math.min(definition.radius(),BlockDamagePolicy.blastRadius(definition.kind()));
        if(radius<=0)return 0;
        var candidates=new ArrayList<BlockPos>();var origin=BlockPos.containing(center);int extent=(int)Math.ceil(radius);
        for(int x=-extent;x<=extent;x++)for(int y=-extent;y<=extent;y++)for(int z=-extent;z<=extent;z++){
            var at=origin.offset(x,y,z);if(loaded(level,at)&&Vec3.atCenterOf(at).distanceToSqr(center)<radius*radius)candidates.add(at);
        }
        candidates.sort(Comparator.<BlockPos>comparingDouble(p->Vec3.atCenterOf(p).distanceToSqr(center)).thenComparingLong(BlockPos::asLong));
        int processed=0,broken=0,limit=FirearmsWorldConfig.BLAST_BLOCK_LIMIT.get();
        for(var at:candidates){
            if(processed>=limit)break;
            var state=level.getBlockState(at);var material=material(level,at,state);
            if(state.isAir()||material==BlockDamagePolicy.Material.PROTECTED)continue;
            var point=Vec3.atCenterOf(at);double distance=point.distanceTo(center);
            if(distance>1.5){
                var start=center.add(point.subtract(center).normalize().scale(.75));
                var wall=level.clip(new ClipContext(start,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
                if(wall.getType()!=HitResult.Type.MISS&&!wall.getBlockPos().equals(at))continue;
            }
            float impact=BlockDamagePolicy.blastImpact(material,definition.kind(),definition.damage(),distance,radius);
            if(impact<=0)continue;
            processed++;if(apply(level,owner,at,impact))broken++;
        }
        return broken;
    }
    private static boolean valid(LivingEntity owner){return owner!=null&&owner.isAlive()&&!owner.isRemoved()&&owner.level() instanceof ServerLevel;}
    private static boolean loaded(ServerLevel level,BlockPos at){return !level.isOutsideBuildHeight(at)&&level.getWorldBorder().isWithinBounds(at)&&level.hasChunkAt(at);}
    private static BlockDamagePolicy.Material material(ServerLevel level,BlockPos at,BlockState state){
        if(state.isAir()||state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock||state.is(PROTECTED)||state.getDestroySpeed(level,at)<0
                ||FirearmsWorldConfig.PROTECT_BLOCK_ENTITIES.get()&&state.hasBlockEntity())return BlockDamagePolicy.Material.PROTECTED;
        if(state.is(FRAGILE))return BlockDamagePolicy.Material.FRAGILE;
        if(state.is(WOOD))return BlockDamagePolicy.Material.WOOD;
        if(state.is(METAL))return BlockDamagePolicy.Material.METAL;
        if(state.is(EARTH)||state.getDestroySpeed(level,at)<1)return BlockDamagePolicy.Material.EARTH;
        return BlockDamagePolicy.Material.MASONRY;
    }
    private static boolean allowed(ServerLevel level,LivingEntity owner,BlockPos at,BlockState state){
        if(owner instanceof ServerPlayer player){
            if(player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,at))return false;
            var event=new BlockEvent.BreakEvent(level,at,state,player);var previous=BREAK_EVENT.get();BREAK_EVENT.set(event);
            try{return !NeoForge.EVENT_BUS.post(event).isCanceled();}
            finally{if(previous==null)BREAK_EVENT.remove();else BREAK_EVENT.set(previous);}
        }
        return CommonHooks.canEntityDestroy(level,at,owner);
    }
    private static BlockDamageLedger ledger(ServerLevel level){return LEDGERS.computeIfAbsent(level,ignored->new BlockDamageLedger(512,200));}
    private static boolean apply(ServerLevel level,LivingEntity owner,BlockPos at,float impact){
        if(impact<=0||!loaded(level,at))return false;
        var state=level.getBlockState(at);var material=material(level,at,state);
        float capacity=BlockDamagePolicy.durability(material,state.getDestroySpeed(level,at));
        if(!Float.isFinite(capacity)||!allowed(level,owner,at,state))return false;
        // A protection listener may have replaced the block while handling the event.
        if(level.getBlockState(at)!=state)return false;
        var ledger=ledger(level);var result=ledger.hit(at.asLong(),Block.getId(state),impact,capacity,level.getGameTime());
        flush(level,ledger);
        if(result.broken())return level.destroyBlock(at,!(owner instanceof ServerPlayer player&&player.isCreative()),owner);
        level.destroyBlockProgress(crackId(at),at,result.stage());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,state),at.getX()+.5,at.getY()+.5,at.getZ()+.5,2,.15,.15,.15,.015);
        return false;
    }
    private static int crackId(BlockPos at){return -1_000_000_000-(at.hashCode()&0x3fffffff);}
    private static void flush(ServerLevel level,BlockDamageLedger ledger){for(long key:ledger.drainCleared()){var at=BlockPos.of(key);level.destroyBlockProgress(crackId(at),at,-1);}}
    // Placement distinguishes a new block even when its state ID matches the old cover.
    // BreakEvent is deliberately not used: permission probes also post that event on each hit.
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void placed(BlockEvent.EntityPlaceEvent event){
        if(event.isCanceled()||!(event.getLevel() instanceof ServerLevel level))return;
        var ledger=LEDGERS.get(level);if(ledger==null)return;
        if(event instanceof BlockEvent.EntityMultiPlaceEvent multi){
            for(var snapshot:multi.getReplacedBlockSnapshots())ledger.forget(snapshot.getPos().asLong());
        }else ledger.forget(event.getPos().asLong());
        flush(level,ledger);
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){
        if(event.getLevel() instanceof ServerLevel level&&level.getGameTime()%20==0){var ledger=LEDGERS.get(level);if(ledger!=null){ledger.expire(level.getGameTime());flush(level,ledger);}}
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)LEDGERS.remove(level);}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){LEDGERS.clear();}
}
