package com.codex.blacklight.gametest.vehicle;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Matches ordinary crafting grids against production-loaded recipes, without fixture recipe data. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleAmmunitionRecipeGameTest {
    @GameTest(template="blackwatch_arena",batch="vehicle6ammunitionrecipes",timeoutTicks=100)
    public static void actualRifleAmmunitionCraftsUniquely(GameTestHelper h) { craft(h,"rifle_round",16,"iron_ingot","gunpowder"); }
    @GameTest(template="blackwatch_arena",batch="vehicle6ammunitionrecipes",timeoutTicks=100)
    public static void actualCoaxMachineGunAmmunitionCraftsUniquely(GameTestHelper h) { craft(h,"machine_gun_round",16,"iron_ingot","copper_ingot","gunpowder"); }
    @GameTest(template="blackwatch_arena",batch="vehicle6ammunitionrecipes",timeoutTicks=100)
    public static void actualGrenadeAmmunitionCraftsUniquely(GameTestHelper h) { craft(h,"grenade_round",2,"iron_ingot","iron_ingot","gunpowder"); }
    @GameTest(template="blackwatch_arena",batch="vehicle6ammunitionrecipes",timeoutTicks=100)
    public static void actualApcRocketAmmunitionCraftsUniquely(GameTestHelper h) { craft(h,"rocket_round",2,"iron_ingot","iron_ingot","gunpowder","paper"); }
    @GameTest(template="blackwatch_arena",batch="vehicle6ammunitionrecipes",timeoutTicks=100)
    public static void actualMissileAmmunitionCraftsUniquely(GameTestHelper h) { craft(h,"missile_round",2,"iron_ingot","iron_ingot","gunpowder","paper","redstone"); }
    @GameTest(template="blackwatch_arena",batch="vehicle6ammunitionrecipes",timeoutTicks=100)
    public static void actualThermobaricAmmunitionCraftsUniquely(GameTestHelper h) { craft(h,"thermobaric_round",2,"iron_ingot","gunpowder","blaze_powder"); }
    @GameTest(template="blackwatch_arena",batch="vehicle6ammunitionrecipes",timeoutTicks=100)
    public static void actualInfectionAmmunitionCraftsUniquely(GameTestHelper h) { craft(h,"infection_round",2,"iron_ingot","gunpowder","blacklight:blacklight_sample"); }
    @GameTest(template="blackwatch_arena",batch="vehicle6ammunitionrecipes",timeoutTicks=100)
    public static void actualAutocannonAmmunitionCraftsUniquely(GameTestHelper h) { craft(h,"autocannon_round",8,"iron_ingot","iron_ingot","copper_ingot","gunpowder"); }
    @GameTest(template="blackwatch_arena",batch="vehicle6ammunitionrecipes",timeoutTicks=100)
    public static void actualTankHeAmmunitionCraftsUniquely(GameTestHelper h) { craft(h,"tank_he_shell",1,"iron_ingot","iron_ingot","gunpowder","gunpowder","copper_ingot"); }
    @GameTest(template="blackwatch_arena",batch="vehicle6ammunitionrecipes",timeoutTicks=100)
    public static void actualTankApAmmunitionCraftsUniquely(GameTestHelper h) { craft(h,"tank_ap_shell",1,"iron_ingot","iron_ingot","iron_ingot","gunpowder","copper_ingot"); }

    private static void craft(GameTestHelper h,String ammunition,int count,String... ingredients) {
        var level=h.getLevel();var manager=level.getRecipeManager();
        var expected=new ResourceLocation("blacklight",ammunition);
        for(boolean scattered:new boolean[]{false,true}) {
            var stacks=new ArrayList<ItemStack>();for(int i=0;i<9;i++)stacks.add(ItemStack.EMPTY);
            for(int i=0;i<ingredients.length;i++) {
                String name=ingredients[i];var ingredientId=new ResourceLocation(name.contains(":")?name:"minecraft:"+name);
                var item=BuiltInRegistries.ITEM.get(ingredientId);
                h.assertTrue(item!=Items.AIR,"fixture ingredient must be registered: "+ingredientId);
                stacks.set(scattered?8-i*2:i,new ItemStack(item));
            }
            var input=new TransientCraftingContainer(new AbstractContainerMenu(null,-1){
                public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player,int slot){return ItemStack.EMPTY;}
                public boolean stillValid(net.minecraft.world.entity.player.Player player){return true;}
            },3,3);
            for(int slot=0;slot<9;slot++)input.setItem(slot,stacks.get(slot));
            var matches=manager.getRecipesFor(RecipeType.CRAFTING,input,level);
            h.assertTrue(matches.size()==1,"one actual crafting match for "+ammunition+", got "+matches.stream().map(recipe->recipe.getId().toString()).toList());
            var selected=manager.getRecipeFor(RecipeType.CRAFTING,input,level).orElseThrow();
            h.assertTrue(selected.getId().equals(expected),"actual recipe manager must select "+expected+", got "+selected.getId());
            var result=selected.assemble(input,level.registryAccess());
            h.assertTrue(BuiltInRegistries.ITEM.getKey(result.getItem()).equals(expected)&&result.getCount()==count,
                    "actual assembled result must be "+count+" "+expected+", got "+result);
        }
        h.succeed();
    }
}
