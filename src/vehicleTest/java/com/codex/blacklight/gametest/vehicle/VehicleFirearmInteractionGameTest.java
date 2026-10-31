package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.firearms.*;
import com.codex.blacklight.military.vehicle.VehicleRegistry;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.*;

/** Actual entity-interaction wrapper must leave normal firearm Item.use available. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleFirearmInteractionGameTest {
    private static void scenario(GameTestHelper h,int x,boolean proxy){
        var f=new MilitaryVehicleGameTest.Fixture(h,x);
        VehicleTestReadiness.await(h,f,()->{
            var vehicle=(proxy?ModEntities.BLACKWATCH_APC.get():ModEntities.MILITARY_APC.get()).create(f.level);
            vehicle.setPos(f.x+.5,81,.5);f.level.addFreshEntity(vehicle);f.entities.add(vehicle);
            Entity target=proxy?vehicle.getParts()[0]:vehicle;
            var player=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(player);player.setGameMode(GameType.CREATIVE);
            player.setPos(f.x-2,81,.5);player.setYRot(-90);player.setXRot(0);
            var rifle=FirearmsRegistry.weapon("military_rifle");var gun=new ItemStack(rifle);
            player.setItemInHand(InteractionHand.MAIN_HAND,gun);player.setShiftKeyDown(true);
            h.assertTrue(player.interactOn(target,InteractionHand.MAIN_HAND)==InteractionResult.PASS&&!player.isPassenger(),"real Player.interactOn must let an empty held firearm reload instead of boarding its parent or native hull proxy");
            player.getInventory().setItem(10,new ItemStack(FirearmsRegistry.ammo("rifle_round"),32));
            boolean reload=rifle.reload(player,InteractionHand.MAIN_HAND);
            h.assertTrue(reload&&FirearmItem.state(player.getMainHandItem(),0).magazine()==30&&player.getInventory().getItem(10).getCount()==2,"after entity PASS the validated R-key reload operation loads thirty actual rounds and spends the real reserve");
            player.setShiftKeyDown(false);
            h.assertTrue(player.interactOn(target,InteractionHand.MAIN_HAND)==InteractionResult.PASS&&!player.isPassenger()&&FirearmItem.state(player.getMainHandItem(),0).magazine()==30,"a genuinely loaded held firearm also remains on foot and passes entity interaction");
            long readyAt=FirearmItem.state(player.getMainHandItem(),0).readyAt();float afterReloadHull=vehicle.health();
            var immediate=player.getMainHandItem().getItem().use(f.level,player,InteractionHand.MAIN_HAND);
            h.assertTrue(immediate.getResult()==InteractionResult.FAIL&&player.getCooldowns().isOnCooldown(rifle)
                    &&FirearmItem.state(player.getMainHandItem(),0).magazine()==30&&vehicle.health()==afterReloadHull,
                    "actual immediate Item.use remains blocked by the original forty-tick reload, without ammunition or hull changes");
            // Embedded connections may omit doTick, or already advance it in a different
            // server phase. Read native progress first and tick ONLY a component deficit
            // relative to the unchanged saved forty-world-tick deadline, never twice blindly.
            long reloadAt=readyAt-rifle.definition().reloadTicks();
            var checkedBeforeReady=new java.util.concurrent.atomic.AtomicBoolean();
            var timeline=new StringBuilder();
            var sequence=h.startSequence();
            for(int t=1;t<=rifle.definition().reloadTicks();t++)sequence.thenIdle(1).thenExecute(()->{
                long now=f.level.getGameTime();float percent=player.getCooldowns().getCooldownPercent(rifle,0);
                int nativeRemaining=Math.round(percent*rifle.definition().reloadTicks());
                int savedRemaining=(int)com.codex.blacklight.forge.WingMath.clamp(readyAt-now,0,rifle.definition().reloadTicks());
                int deficit=Math.max(0,nativeRemaining-savedRemaining);
                for(int missing=0;missing<deficit;missing++)player.getCooldowns().tick();
                timeline.append("[now=").append(now).append(",ready=").append(readyAt).append(",playerTick=").append(player.tickCount)
                        .append(",nativeBefore=").append(percent).append(",missing=").append(deficit)
                        .append(",nativeAfter=").append(player.getCooldowns().getCooldownPercent(rifle,0)).append(']');
                if(now-reloadAt==rifle.definition().reloadTicks()-1){
                    checkedBeforeReady.set(true);
                    var blocked=player.getMainHandItem().getItem().use(f.level,player,InteractionHand.MAIN_HAND);
                    h.assertTrue(now<readyAt&&blocked.getResult()==InteractionResult.FAIL
                            &&FirearmItem.state(player.getMainHandItem(),0).magazine()==30&&vehicle.health()==afterReloadHull,
                            "saved forty-tick deadline still rejects actual Item.use at thirty-nine real WORLD ticks regardless of native counter phase; "+timeline);
                }
            });
            sequence.thenIdle(1).thenExecute(()->{try(f){
                h.assertTrue(checkedBeforeReady.get()&&!player.getCooldowns().isOnCooldown(rifle)&&f.level.getGameTime()>=readyAt,
                        "the thirty-nine-world-tick refusal was observed and both original reload clocks expire before normal firing; "+timeline);
                com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Vehicle firearm clock fixture proxy={} {}",proxy,timeline);
                float before=vehicle.health();
                var fire=player.getMainHandItem().getItem().use(f.level,player,InteractionHand.MAIN_HAND);
                h.assertTrue(fire.getResult().consumesAction()&&FirearmItem.state(player.getMainHandItem(),0).magazine()==29&&!player.isPassenger(),"ordinary loaded firearm use fires exactly one held round while remaining on foot; result="+fire.getResult()+" nativeCooldown="+player.getCooldowns().isOnCooldown(rifle)+" now="+f.level.getGameTime()+" readyAt="+readyAt+" magazine="+FirearmItem.state(player.getMainHandItem(),0).magazine());
                h.assertTrue(vehicle.health()<before,"the actual on-foot firearm ray reaches and damages the same real mechanical chassis");
                player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VehicleRegistry.item("autocannon_round"),64));
                h.assertTrue(player.interactOn(target,InteractionHand.MAIN_HAND).consumesAction()&&vehicle.inventory().ammunition("autocannon_round")==64&&player.getMainHandItem().isEmpty()&&!player.isPassenger(),"the real creative wrapper still transfers and spends sixty-four supplied vehicle rounds without boarding");
                h.assertTrue(player.interactOn(target,InteractionHand.MAIN_HAND).consumesAction()&&player.getVehicle()==vehicle,"ordinary empty-hand interaction still boards the same real chassis through the parent or proxy");
            }h.succeed();});
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle61FirearmInteraction",timeoutTicks=180)
    public static void actualHeldRifleReloadAndFireYieldOnMilitaryVehicleParentWhileSupplyAndEmptyHandStillWork(GameTestHelper h){scenario(h,614400,false);}
    @GameTest(template="blackwatch_arena",batch="vehicle61FirearmInteraction",timeoutTicks=180)
    public static void actualHeldRifleReloadAndFireYieldOnBlackwatchHullProxyWhileSupplyAndEmptyHandStillWork(GameTestHelper h){scenario(h,615400,true);}
    private VehicleFirearmInteractionGameTest(){}
}
