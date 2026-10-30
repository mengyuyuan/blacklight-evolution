package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.VehicleRegistry;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;

/** Real Player.interactOn includes vanilla creative stack restoration; direct supply tests miss it. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleCreativeConsumptionGameTest {
    @GameTest(template="blackwatch_arena",batch="vehicle61CreativeSupply",timeoutTicks=140)
    public static void creativeMilitaryAmmunitionThroughPlayerWrapperConsumesOnlyAcceptedItems(GameTestHelper h){creativeAmmo(h,610400,false);}
    @GameTest(template="blackwatch_arena",batch="vehicle61CreativeSupply",timeoutTicks=140)
    public static void creativeBlackwatchHullProxyThroughPlayerWrapperConsumesOnlyAcceptedItems(GameTestHelper h){creativeAmmo(h,611400,true);}
    private static void creativeAmmo(GameTestHelper h,int x,boolean proxy){
        var f=new MilitaryVehicleGameTest.Fixture(h,x);
        VehicleTestReadiness.run(h,f,()->{
            var v=(proxy?ModEntities.BLACKWATCH_APC.get():ModEntities.MILITARY_APC.get()).create(f.level);v.setPos(f.x+.5,81,.5);f.level.addFreshEntity(v);f.entities.add(v);
            var p=player(h,f,GameType.CREATIVE);
            Entity target=v;if(proxy){h.assertTrue(v.getParts().length>0,"real native broad-phase hull part exists");target=v.getParts()[0];}
            h.assertTrue(p.getAbilities().instabuild&&v.inventory().ammunition("autocannon_round")==0,"real creative Player and an empty target vehicle");
            hand(p,"autocannon_round",64);
            h.assertTrue(p.interactOn(target,InteractionHand.MAIN_HAND).consumesAction()&&v.inventory().ammunition("autocannon_round")==64,"normal Player wrapper delivers 64 actual rounds");
            h.assertTrue(p.getMainHandItem().isEmpty(),"creative Player.interactOn must not restore the 64 transferred rounds; actual hand="+p.getMainHandItem());
            hand(p,"autocannon_round",64);
            h.assertTrue(p.interactOn(target,InteractionHand.MAIN_HAND).consumesAction()&&v.inventory().ammunition("autocannon_round")==120&&p.getMainHandItem().getCount()==8,"partial acceptance transfers 56 and retains exactly eight real rounds");
            p.interactOn(target,InteractionHand.MAIN_HAND);
            h.assertTrue(v.inventory().ammunition("autocannon_round")==120&&p.getMainHandItem().getCount()==8,"full magazine capacity rejects without taking or duplicating remaining material");
            hand(p,"tank_he_shell",12);p.interactOn(target,InteractionHand.MAIN_HAND);
            h.assertTrue(v.inventory().ammunition("autocannon_round")==120&&p.getMainHandItem().getCount()==12,"wrong ammunition keeps the actual hand stack unchanged");
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle61CreativeSupply",timeoutTicks=140)
    public static void creativeRepairThroughPlayerWrapperSpendsOnePartAndRefusesInvalidTargets(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,612400);
        VehicleTestReadiness.run(h,f,()->{
            var v=f.truck();var p=player(h,f,GameType.CREATIVE);h.assertTrue(v.hurt(f.level.damageSources().generic(),40)&&v.health()==60,"real truck loses forty existing HP");
            hand(p,"vehicle_repair_parts",3);p.interactOn(v,InteractionHand.MAIN_HAND);
            h.assertTrue(v.health()==80&&p.getMainHandItem().getCount()==2,"creative Player wrapper spends one actual part for twenty HP; actual hand="+p.getMainHandItem());
            p.interactOn(v,InteractionHand.MAIN_HAND);h.assertTrue(v.health()==100&&p.getMainHandItem().getCount()==1,"second actual part reaches health cap");
            p.interactOn(v,InteractionHand.MAIN_HAND);h.assertTrue(v.health()==100&&p.getMainHandItem().getCount()==1,"full vehicle cannot consume material");
            v.hurt(f.level.damageSources().generic(),7);p.setPos(f.x-30,81,0);p.interactOn(v,InteractionHand.MAIN_HAND);
            h.assertTrue(v.health()==93&&p.getMainHandItem().getCount()==1,"remote wrapper request neither repairs nor spends a part");
            p.setPos(f.x-2,81,0);v.hurt(f.level.damageSources().generic(),10000);p.interactOn(v,InteractionHand.MAIN_HAND);
            h.assertTrue(v.health()==0&&p.getMainHandItem().getCount()==1,"destroyed chassis cannot be revived or consume the remaining part");
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle61CreativeSupply",timeoutTicks=140)
    public static void survivalPlayerWrapperKeepsFiniteAmmunitionRepairAndRejectedAttemptSemantics(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,613400);
        VehicleTestReadiness.run(h,f,()->{
            var v=ModEntities.MILITARY_APC.get().create(f.level);v.setPos(f.x+.5,81,.5);f.level.addFreshEntity(v);f.entities.add(v);var p=player(h,f,GameType.SURVIVAL);
            h.assertTrue(!p.getAbilities().instabuild,"actual survival Player does not have creative restoration");
            hand(p,"autocannon_round",64);p.interactOn(v,InteractionHand.MAIN_HAND);
            h.assertTrue(v.inventory().ammunition("autocannon_round")==64&&p.getMainHandItem().isEmpty(),"survival normal wrapper spends all transferred automatic rounds");
            hand(p,"rocket_round",4);p.interactOn(v,InteractionHand.MAIN_HAND);
            h.assertTrue(v.inventory().ammunition("rocket_round")==4&&p.getMainHandItem().isEmpty(),"survival normal wrapper spends all four actual rockets");
            h.assertTrue(v.hurt(f.level.damageSources().generic(),19)&&v.health()==221,"real APC loses nineteen HP");hand(p,"vehicle_repair_parts",2);p.interactOn(v,InteractionHand.MAIN_HAND);
            h.assertTrue(v.health()==240&&p.getMainHandItem().getCount()==1,"survival repair spends one part even for a less-than-twenty-HP gap");
            p.interactOn(v,InteractionHand.MAIN_HAND);h.assertTrue(v.health()==240&&p.getMainHandItem().getCount()==1,"full survival vehicle rejects an extra part");
        });
    }
    private static ServerPlayer player(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,GameType mode){
        var p=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(p);p.setGameMode(mode);p.setPos(f.x-2,81,0);return p;
    }
    private static void hand(ServerPlayer p,String item,int count){
        var id=net.minecraft.resources.ResourceLocation.tryParse("blacklight:"+item);
        if(id==null||!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id))throw new IllegalArgumentException("Missing actual registered ammunition: "+item);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id),count));
    }
}
