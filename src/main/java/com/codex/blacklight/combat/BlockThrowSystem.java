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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Server owns the source block and the single recoverable projectile. */
public final class BlockThrowSystem {
    public static final TagKey<Block> THROWABLE=TagKey.create(Registries.BLOCK,new ResourceLocation("blacklight","throwable_blocks"));
    private static final ThreadLocal<net.minecraftforge.event.level.BlockEvent.BreakEvent> OWN_BREAK = new ThreadLocal<>();
    private BlockThrowSystem(){}
    /** Only our exact grab event bypasses the vanilla-interaction lock. Other protection listeners still run. */
    public static boolean ownsBreakEvent(net.minecraftforge.event.level.BlockEvent.BreakEvent event){return OWN_BREAK.get()==event;}
    public static ThrownBlock held(Player p){
        return p.level().getEntitiesOfClass(ThrownBlock.class,p.getBoundingBox().inflate(4),e->e.holderId()==p.getId()&&!e.isRemoved()).stream().findFirst().orElse(null);
    }
    public static int level(Player p){
        if(p==null||p.isRemoved()||!ModAttachments.available(p))return 0;
        return BlockThrowTuning.level(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getCombatSkillMask());
    }
    public static boolean canUse(Player p){
        if(p==null||!p.isAlive()||p.isRemoved()||p.isSpectator()||p.isPassenger()||level(p)==0||com.codex.blacklight.power.BiomassSystem.active(p))return false;
        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);
        return state.isInfected()&&state.isPrototypeMode()&&state.getActivePower()==com.codex.blacklight.power.PowerForm.MUSCLE_MASS;
    }
    public static boolean request(ServerPlayer p){return request(p,false);}
    public static boolean request(ServerPlayer p,boolean large){return request(p,large?ThrowObjectSize.terrainLimit(level(p)):1,0,null);}
    public static boolean request(ServerPlayer p,int limit,long seed,BlockPos selected){
        if(!p.mayBuild()||!p.isAlive()||p.isSpectator()||p.isPassenger()||com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CONSUMING))return false;
        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);
        if(!canUse(p))return false;
        Vec3 start=p.getEyePosition(),direction=Vec3.directionFromRotation(p.getXRot(),com.codex.blacklight.movement.TraversalSteering.cameraYaw(p));
        var hit=p.level().clip(new ClipContext(start,start.add(direction.scale(3.5)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(selected==null&&hit.getType()!=HitResult.Type.BLOCK)return false;
        BlockPos pos=selected==null?hit.getBlockPos():selected;
        if(start.distanceToSqr(Vec3.atCenterOf(pos))>25)return false;
        if(selected!=null){var sight=p.level().clip(new ClipContext(start,Vec3.atCenterOf(pos),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(sight.getType()!=HitResult.Type.BLOCK||!sight.getBlockPos().equals(pos))return false;}
        var level=p.serverLevel();var block=level.getBlockState(pos);
        if(!level.mayInteract(p,pos)||!level.getWorldBorder().isWithinBounds(pos)||!block.is(THROWABLE)
                ||block.hasBlockEntity()||!block.getFluidState().isEmpty()||!block.isCollisionShapeFullBlock(level,pos)
                ||block.getDestroySpeed(level,pos)<0||block.getDestroySpeed(level,pos)>5||block.getBlock().asItem()==net.minecraft.world.item.Items.AIR)return false;
        var selection=new java.util.LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var at:TerrainGrabShape.grow(pos,limit,seed,q->selectable(p,q))){
            var candidate=level.getBlockState(at);var event=new net.minecraftforge.event.level.BlockEvent.BreakEvent(level,at,candidate,p);var previous=OWN_BREAK.get();OWN_BREAK.set(event);
            try{net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);}finally{if(previous==null)OWN_BREAK.remove();else OWN_BREAK.set(previous);}
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
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.CONSUMING,true);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.CONSUMING);
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.GRAB_STAGE,ConsumeVisual.HOLD);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.GRAB_STAGE);
        com.codex.blacklight.disguise.DisguiseSystem.visiblePower(p);
        projectile.beginPickup(p,origin);
        for(var entry:selection.entrySet())level.blockUpdated(entry.getKey(),entry.getValue().getBlock());
        level.playSound(null,pos,block.getSoundType().getBreakSound(),net.minecraft.sounds.SoundSource.PLAYERS,1,.8F);
        return true;
    }
    public static boolean selectable(ServerPlayer p,BlockPos at){
        var level=p.serverLevel();if(!level.hasChunkAt(at)||!level.mayInteract(p,at)||!level.getWorldBorder().isWithinBounds(at))return false;
        var s=level.getBlockState(at);return s.is(THROWABLE)&&!s.hasBlockEntity()&&s.getFluidState().isEmpty()&&s.isCollisionShapeFullBlock(level,at)&&s.getDestroySpeed(level,at)>=0&&s.getDestroySpeed(level,at)<=5&&s.getBlock().asItem()!=net.minecraft.world.item.Items.AIR;
    }
    public static void clear(ServerPlayer p){
        if(!ModAttachments.available(p))return;
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.CONSUMING,false);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.CONSUMING);
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.GRAB_STAGE,ConsumeVisual.NONE);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.GRAB_STAGE);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p)release(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer p)clear(p);}
    private static void release(ServerPlayer p){var block=held(p);if(block!=null)block.drop();}
}
