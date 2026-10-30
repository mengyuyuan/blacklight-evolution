package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.VehicleDefinition;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real survival-player movement through the empty space below an articulated barrel. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleContactGameTest {
    @GameTest(template="blackwatch_arena",batch="vehicle6contact",timeoutTicks=120)
    public static void actualPlayerWalksUnderSidewaysBarrelWithoutAnEnvelopeWall(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,731000);
        VehicleTestReadiness.run(h,f,()->{
            MilitaryVehicle tank=ModEntities.MILITARY_TANK.get().create(f.level);
            f.entities.add(tank);
            tank.setPos(f.x+.5,81,.5);tank.setYRot(0);
            // Keep the complete schema, HP and inventory. Only choose a legal saved articulation.
            var saved=tank.saveWithoutId(new CompoundTag());
            saved.putFloat("VehicleTurretYaw",90);saved.putFloat("VehicleGunPitch",0);
            tank.load(saved);tank.setPos(f.x+.5,81,.5);
            h.assertTrue(tank.health()==tank.maxHealth()&&Math.abs(tank.turretYaw()-90)<.001&&tank.gunPitch()==0,
                    "fixture loads a complete valid tank save with a sideways horizontal barrel");
            h.assertTrue(f.level.addFreshEntity(tank),"fixture adds the real tank to the already ticking level");

            var player=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(player);
            player.setGameMode(GameType.SURVIVAL);player.setPos(tank.getX()+7,81,tank.getZ());
            h.assertTrue(!player.noPhysics&&!player.isSpectator()&&!player.getAbilities().flying&&!player.isPassenger(),
                    "fixture uses an ordinary unmounted survival player with native collision enabled");
            Vec3 start=player.position(),motion=new Vec3(-3,0,0),expected=start.add(motion);
            var destination=player.getBoundingBox().move(motion);
            var route=player.getBoundingBox().expandTowards(motion);
            var breech=tank.definition().mountWorld("breech_primary",
                    new VehicleDefinition.Vec3(tank.getX(),tank.getY(),tank.getZ()),0,90,0);
            h.assertTrue(player.getBoundingBox().minX>tank.getBoundingBox().maxX,
                    "fixture starts the entire player outside the total body-plus-barrel bounding box");
            h.assertTrue(destination.intersects(tank.getBoundingBox())
                            &&destination.minX>tank.getX()+tank.definition().width()/2+.5
                            &&destination.maxY<breech.y()-tank.definition().barrelRadius()-.4,
                    "fixture destination is inside the broad envelope, outside the full-width body and below the real barrel");
            h.assertTrue(!f.level.getBlockCollisions(player,route.deflate(.00001)).iterator().hasNext(),
                    "fixture walking route contains no terrain obstruction");
            h.assertTrue(f.level.getEntity(tank.getUUID())==tank
                            &&f.level.getEntities((Entity)null,route,entity->entity.is(tank)).stream().anyMatch(entity->entity.is(tank)),
                    "fixture requires the tracked parent or native multipart proxy in the real walking-route query");
            int queriedShapes=f.level.getEntityCollisions(player,route).size();
            // Fifteen ordinary walking-sized moves; no collision override, teleport or custom resolver.
            for(int step=0;step<15;step++)player.move(MoverType.SELF,new Vec3(-.2,0,0));
            h.assertTrue(player.position().distanceTo(expected)<.001,
                    "native survival-player move must reach empty ground below the sideways barrel; start="+start
                            +" expected="+expected+" actual="+player.position()+" requestedX=-3 actualX="+(player.getX()-start.x)
                            +" nativeShapes="+queriedShapes+" totalBox="+tank.getBoundingBox()+" playerBox="+player.getBoundingBox());
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6contact",timeoutTicks=120)
    public static void actualPlayerWalksThroughTheEmptyCornerOfADiagonalTank(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,732000);VehicleTestReadiness.run(h,f,()->{
            var tank=tank(h,f,45,0);var player=player(h,f,new Vec3(tank.getX()+6,81,tank.getZ()-3.5));
            Vec3 expected=player.position().add(-3,0,0);
            h.assertTrue(player.getBoundingBox().minX>tank.getBoundingBox().maxX
                            &&player.getBoundingBox().move(-3,0,0).intersects(tank.getBoundingBox()),
                    "diagonal fixture crosses the real broad envelope from outside");
            for(int step=0;step<15;step++)player.move(MoverType.SELF,new Vec3(-.2,0,0));
            h.assertTrue(player.position().distanceTo(expected)<.001,
                    "native player must cross a diagonal hull's empty AABB corner; expected="+expected+" actual="+player.position()+" totalBox="+tank.getBoundingBox());
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6contact",timeoutTicks=120)
    public static void actualFullWidthTankBodyStillStopsNativePlayerMovement(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,733000);VehicleTestReadiness.run(h,f,()->{
            var tank=tank(h,f,0,90);var player=player(h,f,new Vec3(tank.getX()+7,81,tank.getZ()+2));
            for(int step=0;step<30;step++)player.move(MoverType.SELF,new Vec3(-.2,0,0));
            double contact=player.getX()-tank.getX();
            h.assertTrue(contact>2.25&&contact<2.4&&Math.abs(player.getY()-81)<.001,
                    "native player reaches and stops at the full four-block body, without an early barrel envelope or lost body collision; contactX="+contact+" actual="+player.position());
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6contact",timeoutTicks=120)
    public static void actualSidewaysBarrelStopsNativePlayerAtItsRealHeight(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,734000);VehicleTestReadiness.run(h,f,()->{
            var tank=tank(h,f,0,90);var player=player(h,f,new Vec3(tank.getX()+7,83.45,tank.getZ()));
            var muzzle=tank.definition().mountWorld("muzzle_primary",new VehicleDefinition.Vec3(tank.getX(),tank.getY(),tank.getZ()),0,90,0);
            var probe=new net.minecraft.world.phys.AABB(muzzle.x()-.05,83.5,tank.getZ()-.05,muzzle.x()+.01,83.6,tank.getZ()+.05);
            h.assertTrue(tank.getBoundingBox().intersects(probe)
                            &&f.level.getEntities((Entity)null,probe,entity->entity.is(tank)).stream().anyMatch(entity->entity.is(tank)),
                    "barrel outside the body remains discoverable through the native broad-phase query");
            for(int step=0;step<20;step++)player.move(MoverType.SELF,new Vec3(-.2,0,0));
            double expected=muzzle.x()+player.getBbWidth()/2;
            h.assertTrue(Math.abs(player.getX()-expected)<.08&&Math.abs(player.getY()-83.45)<.001,
                    "real barrel still stops the native airborne body at barrel height; expectedX="+expected+" actual="+player.position());
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6contact",timeoutTicks=120)
    public static void actualPassengerNeverCollidesWithItsOwnParentOrHullPart(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,735000);VehicleTestReadiness.run(h,f,()->{
            var truck=f.truck();var rider=player(h,f,truck.position());
            h.assertTrue(rider.startRiding(truck),"real survival player boards the physical driver seat");truck.positionRider(rider);
            h.assertTrue(rider.getVehicle()==truck&&"driver".equals(truck.seatOf(rider.getUUID())),"actual mounted passenger owns the real seat");
            h.assertTrue(f.level.getEntityCollisions(rider,rider.getBoundingBox().inflate(.05)).stream().allMatch(shape->shape.isEmpty()),
                    "native collision query must exclude the same vehicle's parent and multipart proxy for its actual passenger");
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6contact",timeoutTicks=120)
    public static void actualLongHullStillStopsNativePlayerAcrossTheCenterSection(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,736000);VehicleTestReadiness.run(h,f,()->{
            var truck=f.truck();truck.setPos(f.x+.5,81,15);
            var probe=new net.minecraft.world.phys.AABB(f.x+.4,82,18.5,f.x+.6,82.2,18.6);
            h.assertTrue(((int)Math.floor(truck.getZ())>>4)!=((int)Math.floor(probe.minZ)>>4)
                            &&f.level.getEntities((Entity)null,probe,entity->entity.is(truck)).stream().anyMatch(entity->entity.is(truck))
                            &&f.level.getEntityCollisions(null,probe).stream().anyMatch(shape->!shape.isEmpty()),
                    "native broad and narrow queries must retain the long hull beyond its center's entity section");
            var player=player(h,f,new Vec3(truck.getX(),81,21));
            for(int step=0;step<30;step++)player.move(MoverType.SELF,new Vec3(0,0,-.2));
            double contact=player.getZ()-truck.getZ();
            h.assertTrue(contact>4&&contact<4.12,"full 7.5-block truck remains a physical wall across the section boundary; contactZ="+contact);
        });
    }
    private static MilitaryVehicle tank(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,float bodyYaw,float turretYaw){
        var tank=ModEntities.MILITARY_TANK.get().create(f.level);f.entities.add(tank);tank.setPos(f.x+.5,81,.5);tank.setYRot(bodyYaw);
        var saved=tank.saveWithoutId(new CompoundTag());saved.putFloat("VehicleTurretYaw",turretYaw);saved.putFloat("VehicleGunPitch",0);
        tank.load(saved);tank.setPos(f.x+.5,81,.5);
        h.assertTrue(tank.health()==tank.maxHealth()&&Math.abs(tank.turretYaw()-turretYaw)<.001&&f.level.addFreshEntity(tank),"contact fixture loads and tracks a full valid tank pose");
        return tank;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,Vec3 at){
        var player=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(player);player.setGameMode(GameType.SURVIVAL);player.setPos(at);
        h.assertTrue(!player.noPhysics&&!player.isSpectator()&&!player.getAbilities().flying,"contact fixture retains ordinary player collision");return player;
    }
    private VehicleContactGameTest(){}
}
