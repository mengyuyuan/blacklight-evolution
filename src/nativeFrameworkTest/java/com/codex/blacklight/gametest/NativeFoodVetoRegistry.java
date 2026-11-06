package com.codex.blacklight.gametest;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.item.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="blacklight",bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class NativeFoodVetoRegistry {
    static final AtomicInteger CALLS=new AtomicInteger();static Item ITEM;
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void register(net.minecraftforge.registries.RegisterEvent event){
        event.register(net.minecraft.core.registries.Registries.ITEM,helper->{
            ITEM=new Item(new Item.Properties().food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(3).build())){
                @Override public InteractionResult interactLivingEntity(ItemStack stack,Player player,LivingEntity target,InteractionHand hand){CALLS.incrementAndGet();return InteractionResult.FAIL;}
            };
            helper.register(new net.minecraft.resources.ResourceLocation("blacklight","native_food_veto_qa"),ITEM);
        });
    }
}
