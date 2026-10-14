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
    private BlockThrowSystem(){}
    public static ThrownBlock held(Player p){
        return p.level().getEntitiesOfClass(ThrownBlock.class,p.getBoundingBox().inflate(4),e->e.holderId()==p.getId()&&!e.isRemoved()).stream().findFirst().orElse(null);
    }
    public static boolean request(ServerPlayer p){
        if(!p.mayBuild()||!p.isAlive()||p.isSpectator()||p.isPassenger()||ModAttachments.getData(p,ModAttachments.CONSUMING))return false;
        var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);
        if(!state.isInfected()||!state.isPrototypeMode())return false;
        Vec3 start=p.getEyePosition(),direction=Vec3.directionFromRotation(p.getXRot(),com.codex.blacklight.movement.TraversalSteering.cameraYaw(p));
        var hit=p.level().clip(new ClipContext(start,start.add(direction.scale(3.5)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(hit.getType()!=HitResult.Type.BLOCK)return false;
        BlockPos pos=hit.getBlockPos();var level=p.serverLevel();var block=level.getBlockState(pos);
        if(!level.mayInteract(p,pos)||!level.getWorldBorder().isWithinBounds(pos)||!block.is(THROWABLE)
                ||block.hasBlockEntity()||!block.getFluidState().isEmpty()||!block.isCollisionShapeFullBlock(level,pos)
                ||block.getDestroySpeed(level,pos)<0||block.getDestroySpeed(level,pos)>5||block.getBlock().asItem()==net.minecraft.world.item.Items.AIR)return false;
        var event=new net.minecraftforge.event.level.BlockEvent.BreakEvent(level,pos,block,p);
        if(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event)||level.getBlockState(pos)!=block)return false;
        var projectile=new ThrownBlock(level,p,block);projectile.setPos(p.position());
        // Spawn cancellation leaves the world unchanged. Neighbor hooks run only after acceptance.
        if(!level.addFreshEntity(projectile))return false;
        if(level.getBlockState(pos)!=block||!level.removeBlock(pos,false)){projectile.discard();return false;}
        projectile.arm();
        ModAttachments.setData(p,ModAttachments.CONSUMING,true);ModAttachments.syncData(p,ModAttachments.CONSUMING);
        ModAttachments.setData(p,ModAttachments.GRAB_STAGE,ConsumeVisual.HOLD);ModAttachments.syncData(p,ModAttachments.GRAB_STAGE);
        com.codex.blacklight.disguise.DisguiseSystem.visiblePower(p);
        com.codex.blacklight.animation.PlayerActions.get().play(p,com.codex.blacklight.animation.PlayerAction.GRAB_REACH,.1F);
        level.playSound(null,pos,block.getSoundType().getBreakSound(),net.minecraft.sounds.SoundSource.PLAYERS,1,.8F);
        return true;
    }
    public static void clear(ServerPlayer p){
        ModAttachments.setData(p,ModAttachments.CONSUMING,false);ModAttachments.syncData(p,ModAttachments.CONSUMING);
        ModAttachments.setData(p,ModAttachments.GRAB_STAGE,ConsumeVisual.NONE);ModAttachments.syncData(p,ModAttachments.GRAB_STAGE);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p)release(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer p)clear(p);}
    private static void release(ServerPlayer p){var block=held(p);if(block!=null)block.drop();}
}
