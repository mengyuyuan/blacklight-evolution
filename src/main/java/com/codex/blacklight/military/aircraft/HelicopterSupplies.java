package com.codex.blacklight.military.aircraft;

import com.codex.blacklight.world.FieldNotes;
import com.codex.blacklight.entity.BlackwatchHelicopter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Finite MC item-to-magazine transfer; it never changes the original AI infinite-ammo policy. */
public final class HelicopterSupplies {
    public static final String CANNON="helicopter_cannon_round",HELLFIRE="helicopter_hellfire_missile",RAPID="helicopter_70mm_rocket";
    public static boolean ammunition(String id){return id.equals(CANNON)||id.equals(HELLFIRE)||id.equals(RAPID);}
    public static InteractionResult supply(Player player,BlackwatchHelicopter craft,InteractionHand hand){
        if(player==null||craft==null||hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        var stack=player.getItemInHand(hand);var key=BuiltInRegistries.ITEM.getKey(stack.getItem());
        if(!key.getNamespace().equals("blacklight")||!ammunition(key.getPath()))return InteractionResult.PASS;
        if(stack.isEmpty())return InteractionResult.FAIL;
        if(craft.level().isClientSide)return InteractionResult.SUCCESS;
        var box=craft.getBoundingBox();var eye=player.getEyePosition();
        Vec3 port=new Vec3(net.minecraft.util.Mth.clamp(eye.x,box.minX,box.maxX),net.minecraft.util.Mth.clamp(eye.y,box.minY,box.maxY),net.minecraft.util.Mth.clamp(eye.z,box.minZ,box.maxZ));
        double speed=craft.getDeltaMovement().lengthSqr();
        if(!player.isAlive()||player.isSpectator()||player.level()!=craft.level()||craft.isRemoved()||craft.health()<=0
                ||!craft.pilot().owned()||!craft.level().mayInteract(player,craft.blockPosition())||eye.distanceToSqr(port)>16
                ||!Double.isFinite(speed)||speed>.0004||!supported(craft)
                ||craft.level().clip(new ClipContext(eye,port,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()!=HitResult.Type.MISS){
            FieldNotes.record(player, Component.translatable("message.blacklight.helicopter_supply_grounded"));return InteractionResult.FAIL;
        }
        int taken=craft.combat().addPlayerAmmunition(key.getPath(),stack.getCount());
        if(taken==0){FieldNotes.record(player, Component.translatable("message.blacklight.helicopter_supply_full"));return InteractionResult.FAIL;}
        // Match ground-vehicle logistics: even creative transfer moves real
        // finite items, rather than letting Player.interactOn restore the count.
        if(player.getAbilities().instabuild){var remainder=stack.copy();remainder.shrink(taken);player.setItemInHand(hand,remainder);}
        else stack.shrink(taken);
        player.getInventory().setChanged();if(player instanceof ServerPlayer server)server.containerMenu.broadcastChanges();
        craft.pilot().sync();return InteractionResult.CONSUME;
    }
    /** Service access needs ground support, not a completely empty rotor/body
     * envelope. A nearby mechanic or soldier must not block stationary reloads,
     * including while the rotor is spinning up. */
    private static boolean supported(BlackwatchHelicopter craft){
        if(!HelicopterFlight.finite(craft.position()))return false;
        for(double x:new double[]{-1.1,0,1.1})for(double z:new double[]{-2.4,0,2.4}){
            var below=net.minecraft.core.BlockPos.containing(craft.position().add(new Vec3(x,-.05,z).yRot((float)Math.toRadians(craft.getYRot()))));
            if(!craft.level().hasChunkAt(below)||!craft.level().getFluidState(below).isEmpty()
                    ||!craft.level().getBlockState(below).isFaceSturdy(craft.level(),below,net.minecraft.core.Direction.UP))return false;
        }
        return true;
    }
    private HelicopterSupplies(){}
}
