package com.codex.blacklight.gametest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/** Test-only equivalent of per-stack food components through Forge's public item hook. */
@EventBusSubscriber(modid="blacklight",bus=EventBusSubscriber.Bus.MOD)
public final class ForgeTestFood {
    private static Item food;
    private static Item createFood(){return new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationMod(0).build())){
        @Override public FoodProperties getFoodProperties(ItemStack stack,LivingEntity entity){
            var tag=stack.getOrCreateTag();var food=new FoodProperties.Builder().nutrition(tag.getInt("QaNutrition")).saturationMod(0);
            if(tag.getBoolean("QaMeat"))food.meat();return food.build();
        }
    };}
    private ForgeTestFood(){}
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(ForgeRegistries.Keys.ITEMS,helper->{
            food=createFood();helper.register(new ResourceLocation("blacklight","qa_dynamic_food"),food);
        });
    }
    public static ItemStack stack(int count,int nutrition,boolean meat){
        var stack=new ItemStack(java.util.Objects.requireNonNull(food,"QA food registration"),count);stack.getOrCreateTag().putInt("QaNutrition",nutrition);stack.getOrCreateTag().putBoolean("QaMeat",meat);return stack;
    }
}
