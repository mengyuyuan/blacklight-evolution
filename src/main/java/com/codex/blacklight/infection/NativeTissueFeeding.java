package com.codex.blacklight.infection;

import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** Source interactions run first. All receipts live only in the current interaction;
 * the forage clock holds weak keys and scalar values, never a world or entity graph. */
public final class NativeTissueFeeding {
    private static final Map<Mob,Long> NEXT=Collections.synchronizedMap(new WeakHashMap<>());
    private NativeTissueFeeding(){}
    public static void forget(Mob mob){if(mob!=null)NEXT.remove(mob);}
    public static boolean free(Mob mob){
        return mob!=null&&!mob.level().isClientSide&&!mob.isRemoved()&&TissueActions.eligible(mob)
                &&!(mob instanceof com.codex.blacklight.entity.BlacklightInfectedHost)&&!mob.isPassenger()
                &&TissueActions.core(mob)==null&&!TissueCompetition.controls(mob)
                &&!ModAttachments.peekData(mob,ModAttachments.CONSUME_VISUAL,com.codex.blacklight.combat.ConsumeVisual.EMPTY).active()
                &&com.codex.blacklight.power.BiomassSystem.claimant(mob)==null
                &&!com.codex.blacklight.combat.ImpactControl.stunned(mob);
    }
    private static boolean visible(Mob mob,Entity target){
        return mob.level().clip(new ClipContext(mob.getEyePosition(),target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob)).getType()==HitResult.Type.MISS;
    }
    private static boolean allowed(Player player,Mob mob){
        return free(mob)&&player!=null&&!player.isSpectator()&&player.isAlive()&&player.level()==mob.level()
                &&player.distanceToSqr(mob)<=Math.pow(3+mob.getBbWidth()*.5,2)&&visible(mob,player);
    }
    private static TissueFeeding.Food food(Mob mob,ItemStack stack){return TissueFeeding.food(mob,NativeTissueDigestion.profile(mob),stack);}
    /** Scoped to one actual Player.interactOn, reached only after uncancelled hooks. */
    public static final class Receipt {
        private final ItemStack before;
        private final TissueFeeding.Food food;
        private InteractionResult source=InteractionResult.FAIL,item=InteractionResult.PASS;
        private boolean settled;
        private Receipt(ItemStack before,TissueFeeding.Food food){this.before=before;this.food=food;}
        public void source(InteractionResult result){source=result;}
        public void item(InteractionResult result){item=result;}
    }
    public static Receipt begin(Player player,Entity target,InteractionHand hand){
        if(!(target instanceof Mob mob)||!allowed(player,mob))return null;
        var stack=player.getItemInHand(hand);var f=food(mob,stack);
        return f==null?null:new Receipt(stack.copy(),f);
    }
    private static boolean same(ItemStack a,ItemStack b){return ItemStack.isSameItemSameTags(a,b);}
    private static int consumed(Receipt receipt,ItemStack after){
        if(after.isEmpty())return receipt.before.getCount();
        if(same(receipt.before,after))return Math.max(0,receipt.before.getCount()-after.getCount());
        // A real food container replacement is consumption; an arbitrary hand swap is not.
        return receipt.before.getCount()==1&&!receipt.food.remainder().isEmpty()&&same(receipt.food.remainder(),after)?1:0;
    }
    public static InteractionResult finish(Receipt receipt,Player player,Entity target,InteractionHand hand,InteractionResult result){
        if(receipt==null||receipt.settled)return result;
        receipt.settled=true;
        if(!(target instanceof Mob mob)||!allowed(player,mob))return result;
        var after=player.getItemInHand(hand);
        if(result.consumesAction()&&consumed(receipt,after)>0){
            // The source already debited the real stack. Never debit it a second time.
            NativeTissueDigestion.offer(mob,receipt.food.kind(),receipt.food.value());return result;
        }
        if(result!=InteractionResult.PASS||receipt.source!=InteractionResult.PASS||receipt.item!=InteractionResult.PASS
                ||after.getCount()!=receipt.before.getCount()||!same(receipt.before,after))return result;
        // Only an explicit source/item PASS permits our food fallback.
        if(!NativeTissueDigestion.offer(mob,receipt.food.kind(),receipt.food.value()))return result;
        if(!player.getAbilities().instabuild){
            after.shrink(1);
            if(!receipt.food.remainder().isEmpty()){
                var rest=receipt.food.remainder().copy();
                if(after.isEmpty())player.setItemInHand(hand,rest);
                else {player.getInventory().add(rest);if(!rest.isEmpty())player.drop(rest,false);}
            }
        }
        sound(mob);return InteractionResult.CONSUME;
    }
    private static boolean grief(Mob mob){return net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(mob.level(),mob);}
    private static boolean accessible(Mob mob,ItemEntity item){
        return item.isAlive()&&!item.isRemoved()&&!item.getItem().isEmpty()&&!item.hasPickUpDelay()
                &&(((com.codex.blacklight.mixin.ItemPickupTargetAccess)item).blacklight$pickupTarget()==null||((com.codex.blacklight.mixin.ItemPickupTargetAccess)item).blacklight$pickupTarget().equals(mob.getUUID()))&&visible(mob,item);
    }
    public static void tick(Mob mob){
        long now=mob.level().getGameTime();var next=NEXT.get(mob);if(next!=null&&next>now)return;
        NEXT.put(mob,now+40+Math.floorMod(mob.getId(),13));
        if(!free(mob)||mob.isNoAi()||NativeTissue.nutrition(mob)>=TissueMetabolism.MAX_RESERVE/4
                ||!NativeTissueDigestion.hasRoom(mob)||mob.getTarget()!=null&&mob.getTarget().isAlive()&&mob.distanceToSqr(mob.getTarget())<16)return;
        if(!TissueFeeding.takeQuery(mob.level())){NEXT.put(mob,now+1+Math.floorMod(mob.getId(),5));return;}
        if(!grief(mob))return;
        // Contact pickup only: no new goal, target, motion or source path reset.
        var nearby=new ArrayList<ItemEntity>();
        mob.level().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(ItemEntity.class),
                mob.getBoundingBox().inflate(.35,.2,.35),item->accessible(mob,item),nearby,32);
        for(var item:nearby.stream().sorted(Comparator.comparingDouble(mob::distanceToSqr)).limit(16).toList()){
            var stack=item.getItem();var f=food(mob,stack);
            if(f==null||f.kind()==TissueDigestion.Kind.FILTER&&!item.isInWater()
                    ||!NativeTissueDigestion.offer(mob,f.kind(),f.value()))continue;
            stack.shrink(1);if(stack.isEmpty())item.discard();else item.setItem(stack.copy());
            if(!f.remainder().isEmpty())mob.spawnAtLocation(f.remainder().copy());sound(mob);break;
        }
    }
    private static void sound(Mob mob){mob.playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT,.6F,.75F);}
}
