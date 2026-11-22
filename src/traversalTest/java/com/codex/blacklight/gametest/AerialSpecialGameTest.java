package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class AerialSpecialGameTest {
    static void clearFlightLane(GameTestHelper h) {
        // GameTest clears only the template volume between batches, not blocks
        // placed above it by earlier tests. Give each pounce a known clear corridor.
        for(int x=0;x<=10;x++)for(int y=179;y<=185;y++)for(int z=-2;z<=36;z++)
            h.setBlock(x,y,z,net.minecraft.world.level.block.Blocks.AIR);
    }
    static void loadedFlight(GameTestHelper h, Runnable check) {
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(5,0,1));
        int cx=pos.getX()>>4,cz=pos.getZ()>>4;
        var owned=new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        // NeoForge GameTest runs accelerated ticks. Non-expiring fixture tickets
        // prevent asynchronous entity sections from losing a short portal ticket.
        for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+4;z++) {
            var chunk=new net.minecraft.world.level.ChunkPos(x,z);
            if(!h.getLevel().getForcedChunks().contains(chunk.toLong())) {
                h.getLevel().setChunkForced(x,z,true);owned.add(chunk);
            }
            h.getLevel().getChunk(x,z);
        }
        h.startSequence().thenWaitUntil(()->{
            // Accelerated GameTest ticks must leave wall-clock time for entity-section I/O.
            java.util.concurrent.locks.LockSupport.parkNanos(20_000_000L);
            for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+4;z++)
                h.assertTrue(h.getLevel().isPositionEntityTicking(new net.minecraft.core.BlockPos(x<<4,80,z<<4)),"waiting for loaded flight entity section "+x+","+z);
        }).thenExecute(()->{
            try { check.run(); }
            finally { for(var chunk:owned)h.getLevel().setChunkForced(chunk.x,chunk.z,false); }
        });
    }
    @GameTest(template="wall_test",timeoutTicks=600,batch="pounce_release")
    public static void pounceWaitsForReleaseThenContactsMovingBody(GameTestHelper h){loadedFlight(h,()->checkMovingPounce(h));}
    private static void checkMovingPounce(GameTestHelper h){
        var p=MovingCombatGameTest.player(h,PowerForm.CLAWS);p.setOnGround(false);p.setNoGravity(true);
        clearFlightLane(h);
        p.setPos(h.absoluteVec(new Vec3(5,180,1)));
        var origin=p.position();var mob=h.spawn(EntityType.IRON_GOLEM,3,25,6);mob.setNoAi(true);mob.setNoGravity(true);
        mob.setPos(origin.add(0,0,8));float hp=mob.getHealth();
        h.assertTrue(CombatTargeting.visible(p,mob),"moving pounce fixture requires an unobstructed flight path: eye="+p.getEyePosition()+" target="+mob.getBoundingBox().getCenter()+" ray="+h.getLevel().clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(),mob.getBoundingBox().getCenter(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p)).getBlockPos());
        WeaponCombatSystem.acceptInput(p,false,true);
        for(int t=0;t<12;t++){
            WeaponCombatSystem.acceptInput(p,false,true);p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
            h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).charging()&&mob.getHealth()==hp,"pounce must gather without damaging while held");
        }
        WeaponCombatSystem.acceptInput(p,false,false);p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
        h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).spec()==WeaponMove.CLAW_POUNCE&&!com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).charging(),"release is a claw attack, not a kick or ground slam");
        double contactDistance=100;
        var trace=new StringBuilder();
        for(int t=0;t<60&&WeaponCombatSystem.busy(p);t++){
            if(mob.getHealth()==hp)mob.move(MoverType.SELF,new Vec3(.07,0,.045));
            p.move(MoverType.SELF,p.getDeltaMovement());p.setOnGround(false);
            p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
            if(t<25)trace.append(t).append(":").append(p.position().subtract(origin)).append(" v=").append(p.getDeltaMovement()).append(" target=").append(mob.position().subtract(origin)).append(" clip=").append(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL)).append(";");
            if(mob.getHealth()<hp&&contactDistance==100)contactDistance=p.distanceTo(mob);
        }
        h.assertTrue(mob.getHealth()<hp&&contactDistance<2.8,"pounce must travel before damaging a strafing/retreating body: distance="+contactDistance+" "+trace);
        h.assertTrue(p.position().distanceTo(origin)>5,"visible flight must close the original eight-block gap");
        WeaponCombatSystem.cancelHeldInput(p);WeaponCombatSystem.cancel(p);mob.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=600,batch="pounce_obstruction")
    public static void pounceCannotHitThroughWallOrAfterTargetEscapes(GameTestHelper h){loadedFlight(h,()->checkMissedPounce(h));}
    private static void checkMissedPounce(GameTestHelper h){
        for(boolean wall:new boolean[]{false,true}){
            var p=MovingCombatGameTest.player(h,PowerForm.CLAWS);p.setOnGround(false);p.setNoGravity(true);
            clearFlightLane(h);
            p.setPos(h.absoluteVec(new Vec3(5,180,1)));var origin=p.position();
            var mob=h.spawn(EntityType.IRON_GOLEM,3,25,6);mob.setNoAi(true);mob.setNoGravity(true);mob.setPos(origin.add(0,0,8));float hp=mob.getHealth();
            WeaponCombatSystem.request(p,WeaponMove.Input.SPECIAL);
            h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).targetId()==mob.getId(),"negative case starts with a real selected body");
            if(wall){for(int x=1;x<=9;x++)for(int y=179;y<=185;y++)h.setBlock(x,y,5,net.minecraft.world.level.block.Blocks.STONE);}
            else mob.setPos(origin.add(0,0,32));
            for(int t=0;t<65&&WeaponCombatSystem.busy(p);t++){
                p.move(MoverType.SELF,p.getDeltaMovement());p.setOnGround(false);p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
            }
            h.assertTrue(mob.getHealth()==hp,"wall/escape must not grant a remote pounce hit");
            h.assertTrue(!WeaponCombatSystem.busy(p),"missed pounce must finish rather than chase forever");
            if(wall)h.assertTrue(p.getZ()-origin.z<4,"pounce cannot cross the solid wall");
            WeaponCombatSystem.cancel(p);mob.discard();
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=600,batch="aerial_use_impact")
    public static void aerialUseContactsAcrossAllSevenForms(GameTestHelper h){
        loadedFlight(h,()->{
            clearFlightLane(h);
            var misses=new java.util.ArrayList<String>();
            for(var form:new PowerForm[]{PowerForm.NONE,PowerForm.MUSCLE_MASS,PowerForm.CLAWS,PowerForm.BLADE,
                    PowerForm.HAMMERFISTS,PowerForm.WHIPFIST,PowerForm.BACKSPINES}){
                for(int held:new int[]{1,45}){
                    var p=MovingCombatGameTest.player(h,form);
                    var origin=h.absoluteVec(new Vec3(5,183,3));
                    p.setPos(origin);p.setOnGround(false);p.setNoGravity(true);
                    QuillHarvest.state(p).addBiomass(1000);
                    var target=h.spawn(EntityType.HUSK,5,180,6);
                    target.setNoAi(true);target.setNoGravity(true);
                    boolean dive=form==PowerForm.BLADE||form==PowerForm.HAMMERFISTS;
                    target.setPos(origin.add(0,dive?-3.5:-.65,form==PowerForm.WHIPFIST?5:dive?2.4:2.2));
                    var look=target.getBoundingBox().getCenter().subtract(p.getEyePosition());
                    p.setXRot((float)Math.toDegrees(Math.atan2(-look.y,look.horizontalDistance())));
                    p.xRotO=p.getXRot();p.setYRot(0);p.yRotO=0;
                    float hp=target.getHealth();
                    try{
                        for(int t=0;t<125 && target.getHealth()==hp;t++){
                            WeaponCombatSystem.acceptInput(p,false,t<held);
                            p.move(MoverType.SELF,p.getDeltaMovement());p.setOnGround(false);p.tickCount++;
                            WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
                            for(var shot:p.serverLevel().getEntitiesOfClass(com.codex.blacklight.entity.BackspineQuill.class,
                                    p.getBoundingBox().inflate(48),q->q.getOwner()==p))shot.tick();
                        }
                        if(target.getHealth()>=hp)misses.add(form+" held="+held);
                    }finally{
                        for(var shot:p.serverLevel().getEntitiesOfClass(com.codex.blacklight.entity.BackspineQuill.class,
                                p.getBoundingBox().inflate(64),q->q.getOwner()==p))shot.discard();
                        WeaponCombatSystem.cancelHeldInput(p);WeaponCombatSystem.cancel(p);target.discard();
                    }
                }
            }
            h.assertTrue(misses.isEmpty(),"airborne right-click did not damage an unobstructed body: "+misses);
            h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=600,batch="aerial_dive_obstruction")
    public static void descentAndLandingShareOneHitAndRespectSolidFloors(GameTestHelper h){
        loadedFlight(h,()->{
            for(var form:new PowerForm[]{PowerForm.BLADE,PowerForm.HAMMERFISTS}){
                for(boolean covered:new boolean[]{false,true}){
                    clearFlightLane(h);
                    for(int x=1;x<=9;x++)for(int z=0;z<=10;z++){
                        h.setBlock(x,178,z,net.minecraft.world.level.block.Blocks.STONE);
                        if(covered)h.setBlock(x,182,z,net.minecraft.world.level.block.Blocks.STONE);
                    }
                    var p=MovingCombatGameTest.player(h,form);
                    var origin=h.absoluteVec(new Vec3(5,185,3));p.setPos(origin);
                    p.setNoGravity(true);p.setOnGround(false);p.setXRot(75);p.xRotO=75;
                    var target=h.spawn(EntityType.IRON_GOLEM,5,179,4);
                    target.setNoAi(true);target.setNoGravity(true);
                    target.setPos(origin.add(0,-6,.7));
                    int hits=0;boolean landed=false;
                    try{
                        for(int t=0;t<125;t++){
                            float hp=target.getHealth();target.invulnerableTime=0;
                            WeaponCombatSystem.acceptInput(p,false,t==0);
                            p.move(MoverType.SELF,p.getDeltaMovement());
                            if(p.getY()<=origin.y-(covered?2:6)+.001){p.setOnGround(true);landed=true;}
                            p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
                            if(target.getHealth()<hp)hits++;
                        }
                        h.assertTrue(landed && hits==(covered?0:1),"actual floor: form="+form+" covered="+covered+" landed="+landed+" hits="+hits);
                    }finally{WeaponCombatSystem.cancelHeldInput(p);WeaponCombatSystem.cancel(p);target.discard();}
                }
            }
            h.succeed();
        });
    }

    @GameTest(template="wall_test",timeoutTicks=40)
    public static void airborneKickChargesWithoutDamageAndReleasesAsKick(GameTestHelper h){
        var d=LocalWeaponMotion.find("air_kick");if(d.isEmpty()){h.succeed();return;}
        var p=MovingCombatGameTest.player(h,PowerForm.NONE);p.setOnGround(false);p.setNoGravity(true);
        var mob=h.spawn(EntityType.IRON_GOLEM,3,25,5);mob.setNoAi(true);mob.setPos(p.position().add(0,0,1.2));float hp=mob.getHealth();
        var charge=d.orElseThrow().charge().orElseThrow();
        int full=charge.windupTicks()+(int)Math.ceil(d.orElseThrow().transitionSeconds()*20-.0001)+charge.holdTicks();
        WeaponCombatSystem.acceptInput(p,false,true);
        for(int t=1;t<full;t++){
            p.tickCount++;WeaponCombatSystem.acceptInput(p,false,true);WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
            h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).charging()&&mob.getHealth()==hp,"held flying kick cannot contact before release");
        }
        p.tickCount++;WeaponCombatSystem.acceptInput(p,false,true);WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
        h.assertTrue(!com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).charging()&&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).spec()==WeaponMove.AIR_KICK,"full charge releases original flying kick");
        for(int t=0;t<WeaponMove.AIR_KICK.duration;t++){p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));}
        h.assertTrue(hp-mob.getHealth()>WeaponMove.AIR_KICK.damageFor(PowerForm.NONE),"holding must strengthen the same kick release, not merely delay it");
        WeaponCombatSystem.cancelHeldInput(p);WeaponCombatSystem.cancel(p);mob.discard();h.succeed();
    }
}
