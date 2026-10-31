package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.military.MilitaryRank;
import com.codex.blacklight.military.vehicle.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleHijackGameTest {
    private static ServerPlayer prototype(GameTestHelper h,MilitaryVehicleGameTest.Fixture f){
        var p=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(p);p.setPos(f.x-2,81,0);
        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,state);return p;
    }
    private static MilitarySoldier crew(MilitaryVehicleGameTest.Fixture f,MilitaryVehicle v){
        var soldier=ModEntities.MILITARY_SOLDIER.get().create(f.level);soldier.setPos(f.x+.5,81,0);soldier.setNoAi(true);soldier.initializeWeapon();soldier.setRank(MilitaryRank.values()[3]);soldier.setEquipped(false);
        f.level.addFreshEntity(soldier);f.entities.add(soldier);if(!soldier.startRiding(v))throw new IllegalStateException("actual AI crew failed to board");return soldier;
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=160)
    public static void prototypeHijacksSameDamagedVehicleAndExtractsSameFiniteCrew(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,203000);var v=ModEntities.MILITARY_APC.get().create(f.level);v.setPos(f.x+.5,81,.5);f.level.addFreshEntity(v);f.entities.add(v);var driver=crew(f,v);var gunner=crew(f,v);var p=prototype(h,f);
        v.inventory().addAmmunition("autocannon_round",7);v.inventory().addFuel(2400);v.inventory().tryFire("autocannon",0);v.hurt(f.level.damageSources().generic(),7);
        h.runAfterDelay(40,()->{
            var resources=v.inventory().snapshot();float hp=v.health();int reserve=driver.reserveAmmo(),magazine=driver.magazineAmmo();var driverId=driver.getUUID();var vehicleId=v.getUUID();
            h.assertTrue(VehicleHijackSystem.request(p,v),"real on-foot prototype begins extraction of the actual AI driver");
            h.runAfterDelay(35,()->{try(f){
                h.assertTrue(p.getVehicle()==v&&v.getUUID().equals(vehicleId)&&!VehicleHijackSystem.active(p),"the same actual vehicle transfers its real driving seat exactly once");
                h.assertTrue(v.health()==hp&&v.inventory().snapshot().equals(resources),"hijack does not repair, refuel, rearm or reset weapon cooldowns");
                h.assertTrue(driver.getVehicle()==null&&gunner.getVehicle()==null&&f.level.getEntity(driverId)==driver&&driver.isAlive(),"original AI crew are actually out, alive and not replaced");
                h.assertTrue(driver.reserveAmmo()==reserve&&driver.magazineAmmo()==magazine&&!driver.equipped()&&driver.rank()==MilitaryRank.values()[3],"actual extracted soldier retains rank, uniform and both finite ammunition counts");
            }h.succeed();});
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=160)
    public static void steppingAwayCancelsWithoutRemoteCaptureOrDuplicatedCrew(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,204000);var v=f.truck();var driver=crew(f,v);var p=prototype(h,f);
        h.runAfterDelay(40,()->{
            h.assertTrue(VehicleHijackSystem.request(p,v),"actual extraction starts while in contact");p.setPos(f.x-30,81,0);
            h.runAfterDelay(35,()->{try(f){h.assertTrue(p.getVehicle()==null&&driver.getVehicle()==v&&!VehicleHijackSystem.active(p)&&f.level.getEntity(driver.getUUID())==driver,"loss of ongoing real contact cancels instead of completing remotely");}h.succeed();});
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=160)
    public static void twoPrototypePlayersCannotClaimSameAiVehicle(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,205000);var v=f.truck();crew(f,v);var first=prototype(h,f);var second=prototype(h,f);
        h.runAfterDelay(40,()->{
            h.assertTrue(VehicleHijackSystem.request(first,v)&&!VehicleHijackSystem.request(second,v),"actual vehicle/driver claim is exclusive for two real server players");
            h.runAfterDelay(35,()->{try(f){h.assertTrue(first.getVehicle()==v&&second.getVehicle()==null,"exactly one actual player becomes the driver");}h.succeed();});
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=120)
    public static void normalHumansAndVehiclesWithHumanOccupantsCannotBeForceCaptured(GameTestHelper h){
        try(var f=new MilitaryVehicleGameTest.Fixture(h,206000)){
            var v=f.truck();var human=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(human);human.setPos(f.x-2,81,0);var p=prototype(h,f);
            h.assertTrue(!VehicleHijackSystem.request(human,v),"ordinary human cannot invoke prototype extraction");h.assertTrue(human.startRiding(v),"actual human driver boards normally");
            h.assertTrue(!VehicleHijackSystem.request(p,v)&&human.getVehicle()==v,"forcing actual human occupants out is not silently added as PvP");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=160)
    public static void blockedContactAndMidwayCrewLossCannotCommit(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,207000);var v=f.truck();var driver=crew(f,v);var p=prototype(h,f);
        h.runAfterDelay(40,()->{
            h.assertTrue(VehicleHijackSystem.request(p,v),"actual contact starts before the crew changes");driver.stopRiding();
            h.runAfterDelay(35,()->{try(f){h.assertTrue(p.getVehicle()==null&&!VehicleHijackSystem.active(p)&&driver.isAlive(),"losing actual driver cancels without moving or duplicating it");}h.succeed();});
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=160)
    public static void wallInsertedDuringActualExtractionCancelsTheSession(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,208000);var v=f.truck();crew(f,v);var p=prototype(h,f);
        h.runAfterDelay(40,()->{
            h.assertTrue(VehicleHijackSystem.request(p,v),"actual contact begins with clear sight");
            for(int y=81;y<=85;y++)for(int z=-4;z<=5;z++)f.level.setBlock(new net.minecraft.core.BlockPos(f.x-1,y,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
            h.runAfterDelay(35,()->{try(f){h.assertTrue(p.getVehicle()==null&&!VehicleHijackSystem.active(p),"new real wall invalidates continued sight before commit");}h.succeed();});
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=100)
    public static void prototypeVehicleInteractionPassesTheRealModeBoundaryOnlyForVehicles(GameTestHelper h){
        try(var f=new MilitaryVehicleGameTest.Fixture(h,209000)){
            var v=f.truck();var p=prototype(h,f);
            var event=new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract(p,net.minecraft.world.InteractionHand.MAIN_HAND,v);
            com.codex.blacklight.mode.PrototypeSurvivalLock.onEntityInteract(event);
            h.assertTrue(!event.isCanceled(),"real prototype survival guard permits the dedicated mechanical interaction");
            var animal=net.minecraft.world.entity.EntityType.COW.create(f.level);
            var ordinary=new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract(p,net.minecraft.world.InteractionHand.MAIN_HAND,animal);
            com.codex.blacklight.mode.PrototypeSurvivalLock.onEntityInteract(ordinary);
            h.assertTrue(ordinary.isCanceled(),"the same mode retains its unrelated vanilla survival restriction");
        }h.succeed();
    }
}
