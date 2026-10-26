package com.codex.blacklight.infection;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Real stack consumption and a bounded local scavenging query, shared by every species. */
@EventBusSubscriber(modid="blacklight")
public final class TissueFeeding {
    private static final TagKey<net.minecraft.world.item.Item> GRIND=TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("blacklight","tissue_grindable"));
    private static final TagKey<net.minecraft.world.item.Item> FERMENT=TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("blacklight","tissue_fermentable"));
    private static final TagKey<net.minecraft.world.item.Item> FILTER=TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("blacklight","tissue_filterable"));
    private static final Map<Level,Budget> BUDGETS=new WeakHashMap<>();
    private static final class Budget {long tick=Long.MIN_VALUE;int queries;}
    public record Food(TissueDigestion.Kind kind,int value,ItemStack remainder){}
    private TissueFeeding(){}
    public static Food food(BlacklightInfectedHost host,ItemStack stack){
        if(stack.isEmpty())return null;var profile=host.digestiveProfile();
        var properties=stack.getFoodProperties(host);
        if(properties!=null&&properties.nutrition()>0){
            int value=(int)Math.clamp((long)properties.nutrition()*2,1,64);
            return new Food(stack.is(ItemTags.MEAT)?TissueDigestion.Kind.MEAT:TissueDigestion.Kind.SOFT,value,properties.usingConvertsTo().map(ItemStack::copy).orElse(ItemStack.EMPTY));
        }
        if(profile.gizzard()>0&&stack.is(GRIND))return new Food(TissueDigestion.Kind.FIBRE,6,ItemStack.EMPTY);
        if(profile.ferment()>0&&stack.is(FERMENT))return new Food(TissueDigestion.Kind.FERMENT,8,ItemStack.EMPTY);
        if(profile.filter()>0&&host.isInWater()&&stack.is(FILTER))return new Food(TissueDigestion.Kind.FILTER,2,ItemStack.EMPTY);
        return null;
    }
    public static boolean free(BlacklightInfectedHost host){return !host.level().isClientSide&&host.isAlive()&&!host.isRemoved()&&!host.hasMultipartBody()&&!host.devourerIdentity()
            &&!DevourerFeedingSystem.isDevourer(host)&&!host.isMetamorphosing()&&!host.isPassenger()
            &&host.organActionCore()==null&&host.tissueStrikeElapsed(0)<0&&!TissueCompetition.controls(host)
            &&!host.getData(ModAttachments.CONSUME_VISUAL).active()&&com.codex.blacklight.power.BiomassSystem.claimant(host)==null
            &&!com.codex.blacklight.combat.ImpactControl.stunned(host);}
    private static boolean visible(BlacklightInfectedHost host,net.minecraft.world.entity.Entity target){
        return host.level().clip(new ClipContext(host.getEyePosition(),target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,host)).getType()==HitResult.Type.MISS;
    }
    /** One accepted portion consumes exactly one item; a full crop leaves the original stack intact. */
    public static boolean playerFeed(BlacklightInfectedHost host,Player player,net.minecraft.world.InteractionHand hand){
        if(!free(host)||player.level()!=host.level()||player.distanceToSqr(host)>Math.pow(3+host.getBbWidth()*.5,2)||!visible(host,player))return false;
        var stack=player.getItemInHand(hand);var food=food(host,stack);if(food==null||!host.offerTissueFood(food.kind,food.value))return false;
        if(!player.getAbilities().instabuild){stack.shrink(1);if(!food.remainder.isEmpty()){var remaining=food.remainder.copy();if(stack.isEmpty())player.setItemInHand(hand,remaining);else {player.getInventory().add(remaining);if(!remaining.isEmpty())player.drop(remaining,false);}}}
        sound(host);return true;
    }
    public static long scavenge(BlacklightInfectedHost host,long next){
        long now=host.level().getGameTime();if(next>now)return next;
        if(!free(host)||host.isNoAi()||host.tissueNutrition()>=TissueMetabolism.MAX_RESERVE/4||host.digestion().queued()>=host.digestiveProfile().capacity()
                ||host.getTarget()!=null&&host.getTarget().isAlive()&&host.distanceToSqr(host.getTarget())<16)return now+40;
        var budget=BUDGETS.computeIfAbsent(host.level(),k->new Budget());if(budget.tick!=now){budget.tick=now;budget.queries=0;}
        if(budget.queries>=8)return now+1+Math.floorMod(host.getId(),5);budget.queries++;
        double reach=host.isInWater()&&host.digestiveProfile().filter()>0?2:1.25;
        var nearby=host.level().getEntitiesOfClass(ItemEntity.class,host.getBoundingBox().inflate(reach,.7,reach),e->e.isAlive()&&!e.getItem().isEmpty());
        for(var item:nearby.stream().sorted(Comparator.comparingDouble(host::distanceToSqr)).limit(16).toList()){
            var stack=item.getItem();var food=food(host,stack);if(food==null||food.kind==TissueDigestion.Kind.FILTER&&!item.isInWater()||!visible(host,item)||!host.offerTissueFood(food.kind,food.value))continue;
            stack.shrink(1);if(stack.isEmpty())item.discard();else item.setItem(stack.copy());
            if(!food.remainder.isEmpty())host.spawnAtLocation(food.remainder.copy());sound(host);break;
        }return now+40+Math.floorMod(host.getId(),13);
    }
    private static void sound(BlacklightInfectedHost host){host.playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT,.6F,.75F);}
    @SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event){BUDGETS.clear();}
}
