package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.VehicleRegistry;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

/** Uses the real player game-mode wrapper, including the registered Prototype right-click event gate. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehiclePlacementInteractionGameTest {
    private static final String[] CHASSIS={"military_truck_chassis","military_apc_chassis","military_tank_chassis",
            "blackwatch_truck_chassis","blackwatch_apc_chassis","blackwatch_tank_chassis"};
    private static ServerPlayer player(GameTestHelper h,MilitaryVehicleGameTest.Fixture f){
        var p=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(p);p.setGameMode(GameType.SURVIVAL);
        // The hit is on the near edge of the target block, within normal survival block reach.
        p.setPos(f.x+.5,81,-3.9);p.setYRot(0);p.setXRot(22);
        var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);ModAttachments.setData(p,ModAttachments.PLAYER_STATE,state);
        return p;
    }
    private static BlockHitResult hit(MilitaryVehicleGameTest.Fixture f,int blockY,double top){
        return new BlockHitResult(new Vec3(f.x+.5,top,.05),Direction.UP,new BlockPos(f.x,blockY,0),false);
    }
    private static InteractionResult use(ServerPlayer p,InteractionHand hand,BlockHitResult hit){
        return p.gameMode.useItemOn(p,p.level(),p.getItemInHand(hand),hand,hit);
    }
    private static java.util.List<MilitaryVehicle> vehicles(MilitaryVehicleGameTest.Fixture f){
        return f.level.getEntitiesOfClass(MilitaryVehicle.class,new AABB(f.x-9,74,-9,f.x+9,88,12));
    }
    private static MilitaryVehicle placed(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,ServerPlayer p,
                                          InteractionHand hand,String id,BlockHitResult hit,double y,boolean creative){
        p.setItemInHand(hand,new ItemStack(VehicleRegistry.item(id)));
        h.assertTrue(com.codex.blacklight.mode.PrototypeSurvivalLock.holdingPlacementItem(p),"the client ownership predicate recognizes the actual held chassis: "+id);
        h.assertTrue(p.getEyePosition().distanceTo(hit.getLocation())<4.5,"fixture uses a reachable ground click: "+id);
        var result=use(p,hand,hit);var found=vehicles(f);
        f.entities.addAll(found);
        h.assertTrue(result.consumesAction()&&found.size()==1,"real Prototype player item-use event must place one "+id+"; result="+result+" spawned="+found.size());
        var vehicle=found.get(0);
        h.assertTrue(vehicle.getType()==net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(
                new net.minecraft.resources.ResourceLocation("blacklight",id.replace("_chassis","")))
                &&p.getUUID().equals(vehicle.playerOwner()),"the real chassis creates its matching faction/model and records player ownership");
        h.assertTrue(Math.abs(vehicle.getY()-y)<.001,"placement settles onto the actual supporting surface; expected="+y+" actual="+vehicle.getY());
        h.assertTrue(p.getItemInHand(hand).getCount()==(creative?1:0),"successful placement follows the actual survival/creative item-use wrapper debit");
        h.assertTrue(vehicle.inventory().snapshot().ammunition().values().stream().allMatch(n->n==0),"placement never creates free ammunition");
        return vehicle;
    }
    @GameTest(template="blackwatch_arena",batch="vehiclePlacementInteraction",timeoutTicks=240)
    public static void allSixChassisPassRealPrototypeBlockUseInEveryCombatForm(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,750000);
        VehicleTestReadiness.run(h,f,()->{
            var p=player(h,f);
            for(var form:new PowerForm[]{PowerForm.NONE,PowerForm.CLAWS,PowerForm.BLADE,PowerForm.HAMMERFISTS,PowerForm.WHIPFIST,PowerForm.MUSCLE_MASS}){
                var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);state.unlock(form);h.assertTrue(state.activate(form),"fixture activates "+form);ModAttachments.setData(p,ModAttachments.PLAYER_STATE,state);
                for(String id:CHASSIS)placed(h,f,p,InteractionHand.MAIN_HAND,id,hit(f,80,81),81,false).discard();
            }
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehiclePlacementInteraction",timeoutTicks=240)
    public static void creativeOffhandChassisUsesRealWrapperAndKeepsSurvivalBlockInteractionLocked(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,751000);
        VehicleTestReadiness.run(h,f,()->{
            var p=player(h,f);p.setGameMode(GameType.CREATIVE);
            f.level.setBlock(new BlockPos(f.x,80,0),Blocks.CHEST.defaultBlockState(),3);
            p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(VehicleRegistry.item("blackwatch_tank_chassis")));
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIRT));
            h.assertTrue(use(p,InteractionHand.MAIN_HAND,hit(f,80,80.875))==InteractionResult.PASS
                    &&p.gameMode.useItem(p,p.level(),p.getMainHandItem(),InteractionHand.MAIN_HAND)==InteractionResult.PASS
                    &&p.getMainHandItem().getCount()==1&&p.containerMenu==p.inventoryMenu,
                    "the primary-hand block and item events yield to the offhand chassis without using a survival block/item");
            placed(h,f,p,InteractionHand.OFF_HAND,"blackwatch_tank_chassis",hit(f,80,80.875),81,true).discard();
            h.assertTrue(p.containerMenu==p.inventoryMenu,"allowing chassis use must not also open the clicked survival chest");
            p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIRT));
            h.assertTrue(!use(p,InteractionHand.MAIN_HAND,hit(f,80,80.875)).consumesAction()
                    &&f.level.getBlockState(new BlockPos(f.x,81,0)).isAir()&&p.getMainHandItem().getCount()==1,
                    "ordinary survival block placement remains locked and its item stays unchanged");
            p.setGameMode(GameType.SPECTATOR);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VehicleRegistry.item("blackwatch_tank_chassis")));
            h.assertTrue(use(p,InteractionHand.MAIN_HAND,hit(f,80,80.875))==InteractionResult.FAIL&&p.containerMenu==p.inventoryMenu,
                    "the chassis exception cannot bypass the spectator placement and survival-menu restrictions");
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehiclePlacementInteraction",timeoutTicks=240)
    public static void actualGrassAndSlabClicksSettleToTheirSupportedSurface(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,752000);
        VehicleTestReadiness.run(h,f,()->{
            var p=player(h,f);var grass=new BlockPos(f.x,81,0);
            f.level.setBlock(grass,Blocks.GRASS.defaultBlockState(),3);
            var grassHit=new BlockHitResult(new Vec3(f.x+.5,81.25,.05),Direction.NORTH,grass,false);
            var vehicle=placed(h,f,p,InteractionHand.MAIN_HAND,"military_truck_chassis",grassHit,81,false);
            h.assertTrue(Math.abs(vehicle.getZ()-.5)<.001,"replaceable grass uses its own ground cell instead of the clicked side's adjacent block");vehicle.discard();
            f.level.setBlock(grass,Blocks.AIR.defaultBlockState(),3);
            for(int dx=-6;dx<=6;dx++)for(int z=-7;z<=7;z++)f.level.setBlock(new BlockPos(f.x+dx,80,z),Blocks.STONE_SLAB.defaultBlockState(),2);
            p.setPos(f.x+.5,80.5,-3.9);
            placed(h,f,p,InteractionHand.MAIN_HAND,"blackwatch_apc_chassis",hit(f,80,80.5),80.5,false).discard();
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehiclePlacementInteraction",timeoutTicks=240)
    public static void ordinaryOneBlockRisePlacesAboveTheGroundWithoutEmbeddingTheHull(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,753000);
        VehicleTestReadiness.run(h,f,()->{
            var p=player(h,f);
            for(int dx=-5;dx<=5;dx++)for(int z=1;z<=7;z++)f.level.setBlock(new BlockPos(f.x+dx,81,z),Blocks.STONE.defaultBlockState(),2);
            placed(h,f,p,InteractionHand.MAIN_HAND,"military_tank_chassis",hit(f,80,81),82,false).discard();
        });
    }
    private static void rejected(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,ServerPlayer p,String why){
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VehicleRegistry.item("military_truck_chassis")));
        var result=use(p,InteractionHand.MAIN_HAND,hit(f,80,81));
        var found=vehicles(f);f.entities.addAll(found);
        h.assertTrue(result==InteractionResult.FAIL&&found.isEmpty()&&p.getMainHandItem().getCount()==1,
                "actual server event rejects "+why+" without spawning or spending a chassis; result="+result+" spawned="+found.size());
    }
    @GameTest(template="blackwatch_arena",batch="vehiclePlacementInteraction",timeoutTicks=240)
    public static void wallsWaterUnsupportedPitsAndPlayerOverlapRejectWithoutSpendingChassis(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,754000);
        VehicleTestReadiness.run(h,f,()->{
            var p=player(h,f);
            for(int y=81;y<=85;y++)f.level.setBlock(new BlockPos(f.x,y,1),Blocks.STONE.defaultBlockState(),2);
            rejected(h,f,p,"a real wall through the hull");
            for(int y=81;y<=85;y++)f.level.setBlock(new BlockPos(f.x,y,1),Blocks.AIR.defaultBlockState(),2);
            f.level.setBlock(new BlockPos(f.x,81,1),Blocks.WATER.defaultBlockState(),2);
            rejected(h,f,p,"water within the hull");f.level.setBlock(new BlockPos(f.x,81,1),Blocks.AIR.defaultBlockState(),2);
            p.setPos(f.x+.5,81,.5);rejected(h,f,p,"placement through the player");p.setPos(f.x+.5,81,-3.9);
            for(int dx=-5;dx<=5;dx++)for(int z=-6;z<=6;z++)for(int y=68;y<=80;y++)
                if(dx!=0||z!=0)f.level.setBlock(new BlockPos(f.x+dx,y,z),Blocks.AIR.defaultBlockState(),2);
            rejected(h,f,p,"an unsupported pit around one remaining clicked block");
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehiclePlacementInteraction",timeoutTicks=240)
    public static void creativeSpawnEggsReachActualItemUseWhileSurvivalPrototypeEggsStayLocked(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,755000);
        VehicleTestReadiness.run(h,f,()->{
            var p=player(h,f);p.setGameMode(GameType.CREATIVE);
            var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);state.unlock(PowerForm.BLADE);state.activate(PowerForm.BLADE);ModAttachments.setData(p,ModAttachments.PLAYER_STATE,state);
            var eggs=java.util.Map.of(com.codex.blacklight.registry.ModItems.HUNTER_SPAWN_EGG.get(),com.codex.blacklight.registry.ModEntities.BLACKLIGHT_HUNTER.get(),
                    com.codex.blacklight.registry.ModItems.BLACKWATCH_SOLDIER_SPAWN_EGG.get(),com.codex.blacklight.registry.ModEntities.BLACKWATCH_SOLDIER.get());
            for(var entry:eggs.entrySet()){
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(entry.getKey()));
                h.assertTrue(com.codex.blacklight.mode.PrototypeSurvivalLock.holdingPlacementItem(p),"creative spawn eggs keep mouse use while a combat form is active");
                var result=use(p,InteractionHand.MAIN_HAND,hit(f,80,81));
                var mobs=f.level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new AABB(f.x-9,74,-9,f.x+9,88,12));f.entities.addAll(mobs);
                h.assertTrue(result.consumesAction()&&mobs.size()==1&&mobs.get(0).getType()==entry.getValue()&&p.getMainHandItem().getCount()==1,
                        "actual creative Prototype block-use spawns the chosen test creature and keeps its egg; result="+result+" mobs="+mobs.size());
                mobs.forEach(net.minecraft.world.entity.Entity::discard);
            }
            p.setGameMode(GameType.SURVIVAL);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(com.codex.blacklight.registry.ModItems.HUNTER_SPAWN_EGG.get()));
            h.assertTrue(!com.codex.blacklight.mode.PrototypeSurvivalLock.holdingPlacementItem(p)
                    &&use(p,InteractionHand.MAIN_HAND,hit(f,80,81))==InteractionResult.FAIL&&p.getMainHandItem().getCount()==1
                    &&f.level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new AABB(f.x-9,74,-9,f.x+9,88,12)).isEmpty(),
                    "the creative test-tool exception does not unlock survival Prototype spawn eggs");
        });
    }
    private VehiclePlacementInteractionGameTest(){}
}
