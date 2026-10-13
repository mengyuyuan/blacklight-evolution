package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class CombatTravelGameTest {
    private static Mob victim(GameTestHelper h,Vec3 pos){
        var mob=h.spawn(EntityType.IRON_GOLEM,3,25,6);mob.setNoAi(true);mob.setNoGravity(true);mob.setPos(pos);return mob;
    }
    private static void prepareTravel(GameTestHelper h,boolean air,boolean special){
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(3,0,3));int cx=pos.getX()>>4,cz=pos.getZ()>>4;
        for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+4;z++)h.getLevel().getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.PORTAL,new net.minecraft.world.level.ChunkPos(x,z),2,pos);
        // Entity-section visibility updates on server ticks after the ticket.
        h.startSequence().thenWaitUntil(()->{
            for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+4;z++)
                h.assertTrue(h.getLevel().isPositionEntityTicking(new net.minecraft.core.BlockPos(x<<4,80,z<<4)),"waiting for remote target entity sections");
        }).thenExecute(()->{
            try{checkTravel(h,air,special);}finally{for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+4;z++)h.getLevel().getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.PORTAL,new net.minecraft.world.level.ChunkPos(x,z),2,pos);}
        });
    }
    private static void checkTravel(GameTestHelper h,boolean air,boolean special){
        h.getLevel().getServer().getPlayerList().setViewDistance(8);
        for(var form:new PowerForm[]{PowerForm.NONE,PowerForm.CLAWS,PowerForm.BLADE,PowerForm.HAMMERFISTS,PowerForm.WHIPFIST,PowerForm.MUSCLE_MASS}){
            if(!air&&special)continue;
            for(int distance:new int[]{12,24,40}){
                // View-wide acquisition must not pick another concurrently running
                // GameTest's victim outside this small structure's footprint.
                var p=MovingCombatGameTest.player(h,form);p.setPos(h.absoluteVec(new Vec3(3,!air?540:special?180:360,3)));p.setOnGround(!air);p.setNoGravity(true);
                var origin=p.position();
                // A tiny GameTest only tickets its own structure. Actors moved
                // forty blocks out must remain in loaded entity sections.
                int cx=net.minecraft.util.Mth.floor(origin.x)>>4,cz=net.minecraft.util.Mth.floor(origin.z)>>4;
                for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+4;z++)h.getLevel().getChunk(x,z);
                var mob=victim(h,origin.add(0,0,distance));float hp=mob.getHealth();
                WeaponCombatSystem.request(p,special);var move=p.getData(ModAttachments.WEAPON_VISUAL).spec();
                // RC45: ordinary aerial attacks and whip heavy attacks remain local.
                if(air&&(!special||form==PowerForm.WHIPFIST)) {
                    for(int t=0;t<260&&WeaponCombatSystem.busy(p);t++) {
                        h.assertTrue(!p.getData(ModAttachments.WEAPON_VISUAL).traveling(),"local aerial action cannot start remote pursuit");
                        p.move(MoverType.SELF,p.getDeltaMovement());p.setOnGround(false);
                        p.tickCount++;WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));
                    }
                    h.assertTrue(Math.hypot(p.getX()-origin.x,p.getZ()-origin.z)<1,"local aerial action cannot tow the player toward a remote target");
                    h.assertTrue((form==PowerForm.WHIPFIST&&special||mob.getHealth()==hp)&&!WeaponCombatSystem.busy(p),"local aerial action recovers; only the long whip may hit remotely: "+form);
                    WeaponCombatSystem.cancel(p);mob.discard();continue;
                }
                h.assertTrue(move!=null&&p.getData(ModAttachments.WEAPON_VISUAL).targetId()==mob.getId(),"acquire visible distant "+form+" "+distance+" selected="+p.getData(ModAttachments.WEAPON_VISUAL).targetId()+" expected="+mob.getId()+" range="+CombatTargeting.viewRange(p));
                int hits=0;double hitDistance=999;boolean traveled=false;
                for(int t=0;t<260&&WeaponCombatSystem.busy(p);t++){
                    if(mob.getHealth()==hp)mob.move(MoverType.SELF,new Vec3(.035,0,.02));
                    p.move(MoverType.SELF,p.getDeltaMovement());
                    if(move.landingAttack()&&p.getY()<=origin.y&&!p.getData(ModAttachments.WEAPON_VISUAL).traveling()){p.setPos(p.getX(),origin.y,p.getZ());p.setOnGround(true);}
                    else p.setOnGround(!air);
                    if(air&&!move.landingAttack())p.setOnGround(false);
                    boolean traveling=p.getData(ModAttachments.WEAPON_VISUAL).traveling();traveled|=traveling;
                    float before=mob.getHealth();p.tickCount++;WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));
                    if(mob.getHealth()<before){hits++;hitDistance=p.distanceTo(mob);h.assertTrue(!traveling,"no damage during approach");}
                }
                h.assertTrue(traveled&&p.distanceToSqr(origin)>16,"must physically cross gap "+move);
                h.assertTrue(hits>0&&hitDistance<(form==PowerForm.WHIPFIST&&!air?15:air&&special&&form==PowerForm.WHIPFIST?15:5),
                        "moving body must be hit after travel: "+move+" range="+distance+" hits="+hits+" separation="+p.distanceTo(mob)+" p="+p.position().subtract(origin)+" target="+mob.position().subtract(origin));
                h.assertTrue(!WeaponCombatSystem.busy(p),"travel and recovery must finish "+move);
                WeaponCombatSystem.cancel(p);mob.discard();
            }
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=600,batch="combat_travel") public static void aerialSpecialsRespectPursuitAndStationaryWhipRules(GameTestHelper h){prepareTravel(h,true,true);}
    @GameTest(template="wall_test",timeoutTicks=600,batch="combat_travel") public static void ordinaryAirOpenersDoNotChaseRemoteTargets(GameTestHelper h){prepareTravel(h,true,false);}
    @GameTest(template="wall_test",timeoutTicks=80) public static void groundAttacksNeverRunToDistantTargets(GameTestHelper h){
        for(var form:new PowerForm[]{PowerForm.NONE,PowerForm.CLAWS,PowerForm.BLADE,PowerForm.HAMMERFISTS,PowerForm.WHIPFIST,PowerForm.MUSCLE_MASS})
            for(var input:WeaponMove.Input.values())for(boolean locked:new boolean[]{false,true}){
                var p=MovingCombatGameTest.player(h,form);p.setPos(h.absoluteVec(new Vec3(3,180,3)));p.setOnGround(true);p.setNoGravity(true);
                var origin=p.position();var mob=victim(h,origin.add(0,0,24));float hp=mob.getHealth();
                if(locked)CombatTargeting.requestLock(p,mob.getId());
                WeaponCombatSystem.request(p,input);var move=p.getData(ModAttachments.WEAPON_VISUAL).spec();
                h.assertTrue(move!=null&&!CombatMotion.canApproach(move),"fixture is a grounded opener "+form+" "+input);
                h.assertTrue(p.getData(ModAttachments.WEAPON_VISUAL).targetId()<0,"distant ground target cannot activate assist, locked="+locked);
                for(int t=0;t<150&&WeaponCombatSystem.busy(p);t++){
                    h.assertTrue(!p.getData(ModAttachments.WEAPON_VISUAL).traveling(),"no automatic run-up "+move);
                    p.move(MoverType.SELF,p.getDeltaMovement());p.setOnGround(true);p.tickCount++;WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));
                }
                h.assertTrue(mob.getHealth()==hp,"ground swing cannot damage distant target "+move);
                h.assertTrue(p.distanceToSqr(origin)<144,"only the move's own bounded displacement remains "+move);
                h.assertTrue(!WeaponCombatSystem.busy(p),"empty ground attack finishes normally "+move);
                WeaponCombatSystem.cancel(p);CombatTargeting.requestLock(p,-1);mob.discard();
            }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40) public static void nearestVisibleTargetAndExplicitLockAreSeparate(GameTestHelper h){
        var p=MovingCombatGameTest.player(h,PowerForm.NONE);p.setPos(h.absoluteVec(new Vec3(3,180,3)));p.setOnGround(false);
        var near=victim(h,p.position().add(2,0,8));var far=victim(h,p.position().add(0,0,22));var behind=victim(h,p.position().add(0,0,-2));
        WeaponCombatSystem.request(p,true);h.assertTrue(p.getData(ModAttachments.WEAPON_VISUAL).targetId()==near.getId(),"nearest in front wins over crosshair-center farther target and closer rear target");WeaponCombatSystem.cancel(p);
        CombatTargeting.requestLock(p,far.getId());
        WeaponCombatSystem.request(p,true);h.assertTrue(p.getData(ModAttachments.WEAPON_VISUAL).targetId()==far.getId(),"manual lock must win");WeaponCombatSystem.cancel(p);CombatTargeting.requestLock(p,-1);
        near.discard();far.discard();behind.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40) public static void chaseCancelsOnOcclusionRemovalAndTeleport(GameTestHelper h){
        for(int kind=0;kind<3;kind++){
            var p=MovingCombatGameTest.player(h,PowerForm.CLAWS);p.setPos(h.absoluteVec(new Vec3(3,180,3)));p.setOnGround(false);var origin=p.position();
            var mob=victim(h,origin.add(0,0,24));float hp=mob.getHealth();
            CombatTargeting.requestLock(p,mob.getId());
            WeaponCombatSystem.request(p,true);
            h.assertTrue(p.getData(ModAttachments.WEAPON_VISUAL).traveling(),"negative fixture actually enters pursuit");
            if(kind==0)for(int x=1;x<=5;x++)for(int y=178;y<=185;y++)h.setBlock(x,y,8,net.minecraft.world.level.block.Blocks.STONE);
            if(kind==1)mob.discard();if(kind==2)mob.setPos(origin.add(0,0,70));
            for(int t=0;t<20&&WeaponCombatSystem.busy(p);t++){p.move(MoverType.SELF,p.getDeltaMovement());p.tickCount++;WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));}
            h.assertTrue(mob.getHealth()==hp&&!WeaponCombatSystem.busy(p),"invalid target cannot produce remote damage or permanent flight");
            if(kind==0){h.assertTrue(p.position().distanceTo(origin)<4,"solid wall stops approach");for(int x=1;x<=5;x++)for(int y=178;y<=185;y++)h.setBlock(x,y,8,net.minecraft.world.level.block.Blocks.AIR);}
            mob.discard();WeaponCombatSystem.cancel(p);
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40) public static void whipPullsOwnerToHeavyVictimBeforeHolding(GameTestHelper h){
        var p=MovingCombatGameTest.player(h,PowerForm.WHIPFIST);p.setPos(h.absoluteVec(new Vec3(3,180,3)));p.setOnGround(true);p.setNoGravity(true);
        var origin=p.position();var mob=h.spawn(EntityType.SNIFFER,3,180,13);mob.setNoAi(true);mob.setNoGravity(true);mob.setHealth(10);var targetStart=mob.position();
        CombatTargeting.requestLock(p,mob.getId());
        ConsumeSystem.request(p);
        h.assertTrue(p.getData(ModAttachments.GRAB_STAGE)==ConsumeVisual.MOUNT,"large sniffer selects mounted owner approach");
        for(int t=0;t<160&&p.getData(ModAttachments.CONSUMING)&&!ConsumeSystem.holding(p);t++){
            p.move(MoverType.SELF,p.getDeltaMovement());p.tickCount++;ConsumeSystem.tick(new PlayerTickEvent.Post(p));
            if(p.getData(ModAttachments.GRAB_STAGE)==ConsumeVisual.MOUNT)
                h.assertTrue(mob.position().distanceTo(targetStart)<.01,"heavy target stays grounded while owner approaches");
        }
        h.assertTrue(ConsumeSystem.holding(p)&&p.position().distanceTo(origin)>7,"owner physically travels then captures heavy target: "+p.position().subtract(origin));
        ConsumeSystem.cancel(p);mob.discard();h.succeed();
    }
}
