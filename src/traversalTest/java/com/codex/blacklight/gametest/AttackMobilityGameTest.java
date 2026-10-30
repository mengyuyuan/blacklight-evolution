package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class AttackMobilityGameTest {
    private static ServerPlayer player(GameTestHelper h,PowerForm form,boolean ground){
        var p=MovingCombatGameTest.player(h,form);p.setPos(h.absoluteVec(new Vec3(3,260,3)));p.setOnGround(ground);p.setNoGravity(true);return p;
    }
    private static void tick(ServerPlayer p){p.move(MoverType.SELF,p.getDeltaMovement());p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));}
    private static void input(ServerPlayer p,WeaponMove.Input input,boolean held){
        WeaponCombatSystem.acceptInput(p,held&&input==WeaponMove.Input.PAIN,held&&input==WeaponMove.Input.SPECIAL,held&&input==WeaponMove.Input.PAIN,false);
    }
    private static void start(GameTestHelper h,ServerPlayer p,WeaponMove.Input intent,boolean charge){
        if(!charge){WeaponCombatSystem.request(p,intent);return;}
        input(p,intent,true);int count=0;
        while(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).charging()&&count++<70){input(p,intent,true);tick(p);}
        h.assertTrue(count<70,"held input must release when full");input(p,intent,false);
    }
    @GameTest(template="wall_test",timeoutTicks=60) public static void emptyAirKickAndCannonballTravelAndCharge(GameTestHelper h){
        for(var intent:new WeaponMove.Input[]{WeaponMove.Input.SPECIAL,WeaponMove.Input.PAIN}){
            double[] distance=new double[2];
            for(int held=0;held<2;held++){
                var p=player(h,PowerForm.NONE,false);var origin=p.position();start(h,p,intent,held==1);
                h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).targetId()<0,"empty fixture must not acquire anyone");
                int t=0;while(WeaponCombatSystem.busy(p)&&t++<160)tick(p);
                distance[held]=p.position().subtract(origin).horizontalDistance();
                h.assertTrue(t<160&&distance[held]>6&&distance[held]<150,"finite real travel "+intent+" charged="+held+" distance="+distance[held]);
                WeaponCombatSystem.cancel(p);
            }
            h.assertTrue(distance[1]>distance[0]*1.5,"charge should increase range "+intent+" "+distance[0]+" -> "+distance[1]);
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40) public static void emptyFlightCommitsReleaseHeadingAndStopsAtWall(GameTestHelper h){
        var p=player(h,PowerForm.NONE,false);var origin=p.position();p.setYRot(-90);WeaponCombatSystem.request(p,true);p.setYRot(90);
        for(int t=0;t<60&&WeaponCombatSystem.busy(p);t++)tick(p);
        h.assertTrue(p.getX()>origin.x+6&&Math.abs(p.getZ()-origin.z)<.5,"free attack follows release heading, not later camera rotation");WeaponCombatSystem.cancel(p);
        p=player(h,PowerForm.NONE,false);origin=p.position();
        for(int x=0;x<7;x++)for(int y=258;y<265;y++)h.setBlock(x,y,8,Blocks.STONE);
        WeaponCombatSystem.request(p,true);for(int t=0;t<70&&WeaponCombatSystem.busy(p);t++)tick(p);
        h.assertTrue(p.getZ()-origin.z<5&&!WeaponCombatSystem.busy(p),"wall blocks body and ends flight");
        for(int x=0;x<7;x++)for(int y=258;y<265;y++)h.setBlock(x,y,8,Blocks.AIR);h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40) public static void freeAttackCanHitVictimEnteringPath(GameTestHelper h){
        var p=player(h,PowerForm.NONE,false);var origin=p.position();WeaponCombatSystem.request(p,true);
        var mob=h.spawn(EntityType.IRON_GOLEM,3,260,10);mob.setNoAi(true);mob.setNoGravity(true);float hp=mob.getHealth();
        for(int t=0;t<60&&WeaponCombatSystem.busy(p);t++)tick(p);
        h.assertTrue(mob.getHealth()<hp,"unselected target entering physical flight path must be struck");
        h.assertTrue(p.position().distanceTo(origin)>1&&p.distanceTo(mob)<7,"contact must be local");mob.discard();WeaponCombatSystem.cancel(p);h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=60) public static void hammerTossHasBallisticRangeAndCharge(GameTestHelper h){
        double[] distances=new double[2];
        for(int charged=0;charged<2;charged++){
            var p=player(h,PowerForm.HAMMERFISTS,true);p.setSprinting(true);var origin=p.position();start(h,p,WeaponMove.Input.SPECIAL,charged==1);
            h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).spec()==WeaponMove.HAMMER_RAM,"sprint special is toss");
            double peak=0;int t=0;
            while(WeaponCombatSystem.busy(p)&&t++<190){
                p.move(MoverType.SELF,p.getDeltaMovement());
                if(t>3&&p.getY()<origin.y){p.setPos(p.getX(),origin.y,p.getZ());p.setOnGround(true);}
                peak=Math.max(peak,p.getY()-origin.y);p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
            }
            distances[charged]=p.position().subtract(origin).horizontalDistance();
            h.assertTrue(t<190&&peak>5&&distances[charged]>20,"toss arcs forward and lands, distance="+distances[charged]+" height="+peak);WeaponCombatSystem.cancel(p);
        }
        h.assertTrue(distances[1]>distances[0]*1.5,"charged toss travels farther");h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40) public static void emptyDivesAndBladeRushHaveOwnMovement(GameTestHelper h){
        for(var form:new PowerForm[]{PowerForm.BLADE,PowerForm.HAMMERFISTS}){
            var p=player(h,form,false);var origin=p.position();WeaponCombatSystem.request(p,true);
            for(int t=0;t<130&&WeaponCombatSystem.busy(p);t++){
                p.move(MoverType.SELF,p.getDeltaMovement());
                if(p.getY()<origin.y-12){p.setPos(p.getX(),origin.y-12,p.getZ());p.setOnGround(true);}
                p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
            }
            h.assertTrue(p.position().subtract(origin).horizontalDistance()>5&&!WeaponCombatSystem.busy(p),"directional descent travels before impact "+form);WeaponCombatSystem.cancel(p);
        }
        var p=player(h,PowerForm.BLADE,true);p.setSprinting(true);var origin=p.position();WeaponCombatSystem.request(p,true);
        h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).spec()==WeaponMove.BLADE_RUSH,"sprint blade selects rush");
        for(int t=0;t<100&&WeaponCombatSystem.busy(p);t++)tick(p);
        double distance=p.position().subtract(origin).horizontalDistance();
        h.assertTrue(distance>5&&distance<12,"blade callback supplies bounded displacement once: "+distance);WeaponCombatSystem.cancel(p);h.succeed();
    }
}
