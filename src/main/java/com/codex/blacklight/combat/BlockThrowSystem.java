package com.codex.blacklight.combat;

import com.codex.blacklight.entity.ThrownBlock;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Server owns the source block and the single recoverable projectile. */
public final class BlockThrowSystem {
    public static final TagKey<Block> THROWABLE=TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("blacklight","throwable_blocks"));
    public static final TagKey<Block> UNGRABBABLE=TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("blacklight","ungrabbable_blocks"));
    private static final ThreadLocal<net.neoforged.neoforge.event.level.BlockEvent.BreakEvent> OWN_BREAK = new ThreadLocal<>();
    private BlockThrowSystem(){}
    /** Only our exact grab event bypasses the vanilla-interaction lock. Other protection listeners still run. */
    public static boolean ownsBreakEvent(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event){return OWN_BREAK.get()==event;}
    private static final HeldObjectIndex<Player,ThrownBlock> HELD = new HeldObjectIndex<>();
    private static boolean heldBy(Player p,ThrownBlock block){
        return !p.isRemoved()&&!block.isRemoved()&&block.level()==p.level()&&block.holderId()==p.getId();
    }
    /** Register before evaluating the animated grip, which itself asks for the held cargo. */
    public static void trackHeld(Player p,ThrownBlock block){if(heldBy(p,block))HELD.remember(p,block);}
    public static ThrownBlock held(Player p){
        if(p==null)return null;
        var tracked=HELD.get(p,block->heldBy(p,block));
        if(tracked!=null)return tracked;
        // Bootstrap only: subsequent lookups follow identity, not an animation-dependent radius.
        var found=p.level().getEntitiesOfClass(ThrownBlock.class,p.getBoundingBox().inflate(4),e->heldBy(p,e)).stream().findFirst().orElse(null);
        if(found!=null)trackHeld(p,found);
        return found;
    }
    public static int level(Player p){
        if(p==null||p.isRemoved())return 0;
        return BlockThrowTuning.level(p.getData(ModAttachments.PLAYER_STATE).getCombatSkillMask());
    }
    public static boolean canUse(Player p){
        if(p==null||com.codex.blacklight.city.HydraTransport.active(p)||!p.isAlive()||p.isRemoved()||p.isSpectator()||p.isPassenger()||level(p)==0||com.codex.blacklight.power.BiomassSystem.active(p))return false;
        var state=p.getData(ModAttachments.PLAYER_STATE);
        return state.isInfected()&&state.isPrototypeMode()&&state.getActivePower()==com.codex.blacklight.power.PowerForm.MUSCLE_MASS;
    }
    public static boolean request(ServerPlayer p){return request(p,false);}
    public static boolean request(ServerPlayer p,boolean large){return request(p,large?ThrowObjectSize.terrainLimit(level(p)):1,0,null);}
    public static boolean request(ServerPlayer p,int limit,long seed,BlockPos selected){
        if(!p.mayBuild()||!p.isAlive()||p.isSpectator()||p.isPassenger()||p.getData(ModAttachments.CONSUMING))return false;
        var state=p.getData(ModAttachments.PLAYER_STATE);
        if(!canUse(p))return false;
        Vec3 start=p.getEyePosition(),direction=Vec3.directionFromRotation(p.getXRot(),com.codex.blacklight.movement.TraversalSteering.cameraYaw(p));
        var hit=p.level().clip(new ClipContext(start,start.add(direction.scale(3.5)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));
        if(selected==null&&hit.getType()!=HitResult.Type.BLOCK)return false;
        BlockPos pos=selected==null?hit.getBlockPos():selected;
        if(start.distanceToSqr(Vec3.atCenterOf(pos))>25)return false;
        if(selected!=null&&!hasGrabSight(p,pos))return false;
        var level=p.serverLevel();var block=level.getBlockState(pos);
        if(!selectable(p,pos))return false;
        var selection=new java.util.LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var at:TerrainGrabShape.grow(pos,limit,seed,q->selectable(p,q))){
            var candidate=level.getBlockState(at);var event=new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level,at,candidate,p);var previous=OWN_BREAK.get();OWN_BREAK.set(event);
            try{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);}finally{if(previous==null)OWN_BREAK.remove();else OWN_BREAK.set(previous);}
            // A protected cell cancels the whole extraction, preserving connectivity and all source blocks.
            if(event.isCanceled())return false;selection.put(at.immutable(),candidate);
        }
        if(!selection.containsKey(pos)||!canUse(p)||p.level()!=level)return false;
        for(var entry:selection.entrySet())if(level.getBlockState(entry.getKey())!=entry.getValue()||!level.mayInteract(p,entry.getKey()))return false;
        int minX=selection.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow(),minY=selection.keySet().stream().mapToInt(BlockPos::getY).min().orElseThrow(),minZ=selection.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        var origin=new BlockPos(minX,minY,minZ);var cells=new java.util.ArrayList<BlockThrowCargo.Cell>();for(var entry:selection.entrySet())cells.add(new BlockThrowCargo.Cell(entry.getKey().subtract(origin),entry.getValue()));
        var projectile=new ThrownBlock(level,p,block);projectile.setCargo(new BlockThrowCargo(cells));projectile.setPos(p.position());
        if(!level.addFreshEntity(projectile))return false;
        var removed=new java.util.ArrayList<BlockPos>();
        for(var entry:selection.entrySet()){
            if(level.getBlockState(entry.getKey())!=entry.getValue()||!level.setBlock(entry.getKey(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2)){
                for(var restore:removed)if(level.getBlockState(restore).isAir())level.setBlock(restore,selection.get(restore),3);
                projectile.discard();return false;
            }
            removed.add(entry.getKey());
        }
        projectile.arm();
        p.setData(ModAttachments.CONSUMING,true);p.syncData(ModAttachments.CONSUMING);
        p.setData(ModAttachments.GRAB_STAGE,ConsumeVisual.HOLD);p.syncData(ModAttachments.GRAB_STAGE);
        com.codex.blacklight.disguise.DisguiseSystem.visiblePower(p);
        projectile.beginPickup(p,origin);
        for(var entry:selection.entrySet())level.blockUpdated(entry.getKey(),entry.getValue().getBlock());
        level.playSound(null,pos,block.getSoundType().getBreakSound(),net.minecraft.sounds.SoundSource.PLAYERS,1,.8F);
        return true;
    }
    private static boolean hasGrabSight(ServerPlayer p,BlockPos pos){
        // A slab/stair/plant need not contain the center of its block cell.
        // Check actual outline pieces, with world occlusion still enforced.
        var state=p.level().getBlockState(pos);
        for(var piece:state.getShape(p.level(),pos).toAabbs()){
            var target=piece.getCenter().add(pos.getX(),pos.getY(),pos.getZ());
            var hit=p.level().clip(new ClipContext(p.getEyePosition(),target,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));
            if(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos))return true;
        }
        return false;
    }
    public static boolean selectable(ServerPlayer p,BlockPos at){
        var level=p.serverLevel();if(!level.hasChunkAt(at)||!level.mayInteract(p,at)||!level.getWorldBorder().isWithinBounds(at))return false;
        var s=level.getBlockState(at);
        // Default-compatible: IDs and mining-tool tags do not grant permission.
        // Data-bearing and multi-part structures need a lossless cargo adapter first.
        return !s.isAir()&&!s.is(UNGRABBABLE)&&!s.hasBlockEntity()&&s.getFluidState().isEmpty()
                &&s.getDestroySpeed(level,at)>=0&&s.getBlock().asItem()!=net.minecraft.world.item.Items.AIR
                &&!s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                &&!s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART);
    }
    public static void clear(ServerPlayer p){
        p.setData(ModAttachments.CONSUMING,false);p.syncData(ModAttachments.CONSUMING);
        p.setData(ModAttachments.GRAB_STAGE,ConsumeVisual.NONE);p.syncData(ModAttachments.GRAB_STAGE);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p)release(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer p)clear(p);}
    private static void release(ServerPlayer p){var block=held(p);if(block!=null)block.drop();}
}
