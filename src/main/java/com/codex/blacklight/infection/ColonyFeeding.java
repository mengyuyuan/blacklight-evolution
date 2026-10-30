package com.codex.blacklight.infection;

import java.util.Comparator;
import com.codex.blacklight.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Only existing local stacks enter the colony. No corpse counter or generated block can be recycled as food. */
public final class ColonyFeeding {
    private static final TagKey<net.minecraft.world.item.Item> FIBRE=TagKey.create(Registries.ITEM,new ResourceLocation("blacklight","tissue_fermentable"));
    private ColonyFeeding(){}
    public static TissueFeeding.Food food(ItemStack stack){
        if(stack.isEmpty())return null;var properties=stack.getFoodProperties(null);
        if(properties!=null&&properties.getNutrition()>0)return new TissueFeeding.Food(properties.isMeat()?TissueDigestion.Kind.MEAT:TissueDigestion.Kind.SOFT,(int)com.codex.blacklight.forge.WingMath.clamp((long)properties.getNutrition()*2,1,64),stack.getCraftingRemainingItem());
        return stack.is(FIBRE)?new TissueFeeding.Food(TissueDigestion.Kind.FERMENT,8,ItemStack.EMPTY):null;
    }
    public static Vec3 mouth(ServerLevel level,BlockPos pos){var state=level.getBlockState(pos);return state.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)?Vec3.atCenterOf(pos).relative(state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING),.7):Vec3.atCenterOf(pos);}
    private static boolean visible(ServerLevel level,BlockPos pos,Vec3 target){
        var from=mouth(level,pos);for(int x:new int[]{(int)Math.floor(Math.min(from.x,target.x)),(int)Math.floor(Math.max(from.x,target.x))})for(int z:new int[]{(int)Math.floor(Math.min(from.z,target.z)),(int)Math.floor(Math.max(from.z,target.z))})if(!level.hasChunkAt(new BlockPos(x,pos.getY(),z)))return false;
        return level.clip(new ClipContext(from,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(net.minecraft.world.entity.Entity)null)).getType()==HitResult.Type.MISS;
    }
    public static boolean feed(ServerLevel level,BlockPos pos,Player player,InteractionHand hand){
        if(!BuildingInfectionConfig.ENABLED.get()||!level.hasChunkAt(pos)||!level.getBlockState(pos).is(ModBlocks.BUILDING_CORE.get()))return false;
        var c=BuildingInfectionData.get(level).colonies.stream().filter(a->!a.dead&&a.origin.equals(pos)).findFirst().orElse(null);if(c==null)return false;
        return feedAt(level,pos,player,hand,c.supply,()->BuildingInfectionData.get(level).setDirty());
    }
    /** Caller resolves an actual live mouth; all mouths consume the very same real stack path. */
    public static boolean feedAt(ServerLevel level,BlockPos pos,Player player,InteractionHand hand,ColonySupply supply,Runnable dirty){
        if(player.level()!=level||!level.hasChunkAt(pos)||!(level.getBlockState(pos).is(ModBlocks.BUILDING_CORE.get())||level.getBlockState(pos).is(com.codex.blacklight.city.CityBlocks.HATCH.get()))
                ||player.distanceToSqr(Vec3.atCenterOf(pos))>25||!visible(level,pos,player.getEyePosition()))return false;
        var stack=player.getItemInHand(hand);var meal=food(stack);if(meal==null||!supply.offer(meal.kind(),meal.value()))return false;
        if(!player.getAbilities().instabuild){stack.shrink(1);if(!meal.remainder().isEmpty()){var remainder=meal.remainder().copy();if(stack.isEmpty())player.setItemInHand(hand,remainder);else{player.getInventory().add(remainder);if(!remainder.isEmpty())player.drop(remainder,false);}}}
        dirty.run();level.playSound(null,pos,net.minecraft.sounds.SoundEvents.GENERIC_EAT,net.minecraft.sounds.SoundSource.BLOCKS,.6F,.65F);return true;
    }
    public static void tick(ServerLevel level,BuildingInfectionData.Colony c){
        long now=level.getGameTime();if(c.lastSupplyTick==now)return;c.lastSupplyTick=now;c.supply.digest(5);
        c.nextFoodTick=scavenge(level,c.origin,c.supply,c.nextFoodTick);
    }
    public static long scavenge(ServerLevel level,BlockPos pos,ColonySupply supply,long next){
        long now=level.getGameTime();if(next>now&&next-now<200||supply.reserve()>=TissueMetabolism.MAX_RESERVE/2||supply.queued()>=12)return next;
        if(!TissueFeeding.takeQuery(level))return now+1+Math.floorMod(pos.asLong(),5);
        next=now+40+Math.floorMod(pos.asLong(),13);
        var source=mouth(level,pos);var drops=new java.util.ArrayList<ItemEntity>();level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(ItemEntity.class),new AABB(pos).inflate(3),e->e.isAlive()&&!e.getItem().isEmpty(),drops,32);
        for(var item:drops.stream().sorted(Comparator.comparingDouble(e->e.distanceToSqr(source))).limit(16).toList()){
            var stack=item.getItem();var meal=food(stack);if(meal==null||!visible(level,pos,item.getBoundingBox().getCenter())||!supply.offer(meal.kind(),meal.value()))continue;
            stack.shrink(1);if(stack.isEmpty())item.discard();else item.setItem(stack.copy());
            if(!meal.remainder().isEmpty())level.addFreshEntity(new ItemEntity(level,source.x,source.y,source.z,meal.remainder().copy()));break;
        }
        return next;
    }
}
