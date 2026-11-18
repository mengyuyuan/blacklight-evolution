package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlackwatchHelicopter;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.combat.BlockThrowAim;
import com.codex.blacklight.combat.DevastatorSystem;
import com.codex.blacklight.power.CombatSkill;
import com.codex.blacklight.combat.CombatTargeting;
import com.codex.blacklight.combat.WeaponCombatSystem;
import com.codex.blacklight.firearms.FirearmsCombat;
import com.codex.blacklight.firearms.WeaponCatalog;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.military.vehicle.VehicleControlAuthority;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

/** Player input crosses the same server authority as the real cockpit. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterRelaunchGameTest {
    @GameTest(template="helicopter_arena",batch="airCriticalPain",timeoutTicks=260)
    public static void chargedCriticalPainHitsLockedAircraft(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,372512);
        var player=HelicopterPilotGameTest.player(h,f,true);var craft=f.craft(8);
        var state=ModAttachments.getData(player,ModAttachments.PLAYER_STATE);state.unlock(PowerForm.CLAWS);state.activate(PowerForm.CLAWS);state.addEvolutionPoints(10000);
        for(var skill:java.util.List.of(CombatSkill.CRITICAL_MASS,CombatSkill.GROUNDSPIKE_GRAVEYARD,CombatSkill.TENDRIL_BARRAGE,CombatSkill.CRITICAL_PAIN))state.buyCombatSkill(skill);
        ModAttachments.setData(player,ModAttachments.CRITICAL_MASS,new com.codex.blacklight.power.CriticalMassState(1,false,-1000));
        float initial=craft.health();int[] time={0};boolean[] done={false};
        h.onEachTick(()->{
            if(done[0])return;
            try{
                int age=++time[0];if(age<25)return;
                craft.setPos(f.x,89,0);player.setPos(f.x,81,12);player.setOnGround(true);player.setYRot(90);player.setXRot(0);
                if(age==25)CombatTargeting.requestLock(player,craft.getId());
                player.tickCount++;
                DevastatorSystem.acceptInput(player,true,false,true,false);
                DevastatorSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,player));
                if(craft.health()<initial){
                    h.assertTrue(DevastatorSystem.snapshot(player).spent(),"real charged skill spent its reserve at the aircraft contact");
                    done[0]=true;DevastatorSystem.cancel(player);f.close();h.succeed();
                }
                if(age>230)h.fail("critical pain missed overhead aircraft: "+DevastatorSystem.snapshot(player));
            }catch(RuntimeException|Error error){done[0]=true;DevastatorSystem.cancel(player);f.close();throw error;}
        });
    }

    @GameTest(template="helicopter_arena",batch="air_locked_overhead_whip",timeoutTicks=100)
    public static void groundChargedWhipHitsLockedOverheadAircraft(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,367392);
        h.runAfterDelay(25,()->{try(f){
            var craft=f.craft(8);
            var player=HelicopterPilotGameTest.player(h,f,true);
            player.setPos(f.x,81,7);player.setOnGround(true);player.setYRot(90);player.setXRot(0);
            var state=ModAttachments.getData(player,ModAttachments.PLAYER_STATE);state.unlock(PowerForm.WHIPFIST);state.activate(PowerForm.WHIPFIST);
            CombatTargeting.requestLock(player,craft.getId());
            float initial=craft.health();
            boolean charged=false;
            for(int tick=0;tick<100&&craft.health()==initial;tick++){
                WeaponCombatSystem.acceptInput(player,false,true);
                var visual=ModAttachments.getData(player,ModAttachments.WEAPON_VISUAL);
                if(visual.spec()!=null){
                    h.assertTrue(visual.targetId()==craft.getId(),"charge, release and sweep retain the aircraft identity");
                    h.assertTrue(com.codex.blacklight.combat.WhipCurveContact.aimPitch(player)<-25,"level camera still pitches the actual lash upward");
                    charged|=visual.spec()==com.codex.blacklight.combat.WeaponMove.WHIP_LONGSHOT_CHARGED;
                }
                player.tickCount++;
                WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,player));
            }
            h.assertTrue(charged&&craft.health()<initial,"charged longshot makes actual overhead skin contact, move="+ModAttachments.getData(player,ModAttachments.WEAPON_VISUAL).spec()+" hp="+craft.health());
            h.assertTrue(!craft.dents().isEmpty(),"charged lash leaves a dent on the aircraft skin");
            var saved=craft.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            var restored=ModEntities.BLACKWATCH_HELICOPTER.get().create(f.level);
            restored.load(saved);
            h.assertTrue(restored.dents().size()==craft.dents().size() && restored.dents().get(0).point().distanceTo(craft.dents().get(0).point())<1e-6,"airframe keeps its local damage after loading");
            saved.remove("AirframeDents");
            restored.load(saved);
            h.assertTrue(restored.dents().isEmpty() && restored.health()==craft.health(),"legacy save clears stale dents and retains mechanical health");
            WeaponCombatSystem.cancelHeldInput(player);WeaponCombatSystem.cancel(player);
        }h.succeed();});
    }

    @GameTest(template="helicopter_arena",batch="ground_locked_hull_whip",timeoutTicks=100)
    public static void groundWhipDamagesEveryRegisteredVehicle(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,368416);
        h.runAfterDelay(25,()->{try(f){
            var player=HelicopterPilotGameTest.player(h,f,true);
            var state=ModAttachments.getData(player,ModAttachments.PLAYER_STATE);state.unlock(PowerForm.WHIPFIST);state.activate(PowerForm.WHIPFIST);
            var types=java.util.List.of(ModEntities.MILITARY_TRUCK.get(),ModEntities.MILITARY_APC.get(),ModEntities.MILITARY_TANK.get(),
                    ModEntities.BLACKWATCH_TRUCK.get(),ModEntities.BLACKWATCH_APC.get(),ModEntities.BLACKWATCH_TANK.get());
            for(var type:types){
                var hull=type.create(f.level);hull.setPos(f.x,81,0);hull.setYRot(35);f.level.addFreshEntity(hull);f.entities.add(hull);
                player.setPos(f.x+6,81,0);player.setOnGround(true);player.setYRot(0);player.setXRot(0);
                CombatTargeting.requestLock(player,hull.getId());
                float initial=hull.health();WeaponCombatSystem.request(player,true);
                h.assertTrue(ModAttachments.getData(player,ModAttachments.WEAPON_VISUAL).targetId()==hull.getId(),"locked heavy attack uses "+type);
                for(int tick=0;tick<60&&hull.health()==initial;tick++){
                    player.tickCount++;
                    WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,player));
                }
                h.assertTrue(hull.health()<initial,"actual whip contact on "+type);
                h.assertTrue(!hull.dents().isEmpty(),"heavy strike dents "+type);
                hull.repair(hull.maxHealth());
                h.assertTrue(hull.dents().isEmpty(),"full repair smooths the hull of "+type);
                var point=com.codex.blacklight.combat.ExplicitTarget.point(player,hull);
                var marked=com.codex.blacklight.military.vehicle.VehicleDamageBridge.hurt(player,hull,WeaponCatalog.RIFLE,4F,null,point);
                h.assertTrue(marked && !hull.dents().isEmpty(),"an ordinary rifle round marks "+type);
                WeaponCombatSystem.cancel(player);hull.discard();
            }
        }h.succeed();});
    }

    @GameTest(template="helicopter_arena",batch="air_walk_weapons",timeoutTicks=100)
    public static void groundWeaponsStrikeSideAndMissEmptyTailSpace(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,289000);
        h.runAfterDelay(25,()->{try(f){
            var craft=f.craft(0);
            var player=HelicopterPilotGameTest.player(h,f,true);
            player.setPos(f.x-7,81,-4.1);
            var rifle=WeaponCatalog.RIFLE;
            var aim=new Vec3(1,0,0);
            float before=craft.health();
            FirearmsCombat.fire(player,rifle,aim,1);
            h.assertTrue(craft.health()==before,"round crossing space below tail is a miss");
            player.setPos(f.x-7,81,1.9);
            FirearmsCombat.fire(player,rifle,aim,1);
            h.assertTrue(craft.health()<before,"rifle pointing at the visible cabin damages it");
            h.assertTrue(!craft.dents().isEmpty(),"rifle leaves a mark at the cabin");
            var scar=craft.dents().get(0);
            h.assertTrue(scar.inward().x>.8 && scar.depth()>0,"side shot pushes the panel inward");

            CombatTargeting.requestLock(player,craft.getId());
            var throwAim=BlockThrowAim.solve(player,player.getEyePosition(),craft.position(),true,2.2);
            h.assertTrue(throwAim.target()==craft.getId(),"throw assist honours the explicit aircraft lock");
            player.setPos(f.x-2.7,81,1.95);
            player.setYRot(-90);player.setOnGround(true);player.setDeltaMovement(Vec3.ZERO);
            var state=ModAttachments.getData(player,ModAttachments.PLAYER_STATE);state.unlock(PowerForm.CLAWS);state.activate(PowerForm.CLAWS);ModAttachments.setData(player,ModAttachments.PLAYER_STATE,state);
            before=craft.health();
            WeaponCombatSystem.request(player,false);
            var move=ModAttachments.getData(player,ModAttachments.WEAPON_VISUAL).spec();
            h.assertTrue(move!=null,"actual ground claw input starts a swing");
            for(int t=0;t<move.duration+2;t++){
                player.tickCount++;
                WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,player));
            }
            WeaponCombatSystem.cancel(player);
            h.assertTrue(craft.health()<before,"unmounted claw swing hits the cabin from the side");
        }
        h.succeed();});
    }

    @GameTest(template="helicopter_arena",batch="air_walk_contact",timeoutTicks=100)
    public static void walkUpToAngledCabinStrikeAndBoard(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,287000);
        h.runAfterDelay(25,()->{try(f){
            var craft=f.craft(0);
            craft.setYRot(45);
            craft.setPos(f.x+.25,81,.25);
            var player=HelicopterPilotGameTest.player(h,f,false);
            h.assertTrue(f.level.getEntity(craft.getId())==craft,"rotated aircraft registered in entity index");
            var door=craft.local(1.4,0,2.45);
            var start=craft.local(7,0,2.45);
            var inward=door.subtract(start).normalize();
            player.setPos(start);
            var probe=new net.minecraft.world.phys.AABB(door,door.add(0,2,0)).inflate(.35);
            h.assertTrue(!com.codex.blacklight.military.aircraft.HelicopterSkinContact.walkingShape(craft,probe).isEmpty(),"door has physical skin");
            h.assertTrue(!f.level.getEntityCollisions(player,probe).isEmpty(),"native walking query includes cabin");
            for(int i=0;i<68;i++)player.move(net.minecraft.world.entity.MoverType.SELF,inward.scale(.1));
            var delta=player.position().subtract(door);
            double gap=-delta.dot(inward);
            h.assertTrue(gap>.19&&gap<1.05,"walker reaches actual cabin and cannot pass through it: gap="+gap);
            var eye=player.getEyePosition();
            var hit=com.codex.blacklight.military.aircraft.HelicopterSkinContact.raycast(craft,eye,eye.add(inward.scale(3)));
            h.assertTrue(hit.isPresent()&&hit.get().distanceTo(eye)<1.2,"arm's length ray selects the visible side door");
            var miss=craft.local(2.7,1.8,-4);
            h.assertTrue(craft.getBoundingBox().contains(miss)
                    &&com.codex.blacklight.military.aircraft.HelicopterSkinContact.raycast(craft,miss,miss.add(0,1,0)).isEmpty(),"empty air beside tail stays empty");
            float before=craft.health();
            player.attack(craft);
            h.assertTrue(craft.health()<before,"ordinary on-foot player attack removes aircraft health");
            player.interactOn(craft,net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertTrue(player.getVehicle()==craft,"empty-handed interaction actually boards from walked-to position");
        }
        h.succeed();});
    }

    @GameTest(template="helicopter_arena",batch="air_thrown_cargo",timeoutTicks=100)
    public static void thrownStoneHitsAirframeButCannotPassWall(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,288000);
        h.runAfterDelay(25,()->{try(f){
            var craft=f.craft(0);
            var player=HelicopterPilotGameTest.player(h,f,false);
            player.setPos(f.x-8,81,0);
            h.assertTrue(f.level.getEntity(craft.getId())==craft,"thrown target registered");
            for(int pass=0;pass<2;pass++){
                if(pass==1)for(int y=81;y<=84;y++)for(int z=-1;z<=1;z++)
                    f.level.setBlock(new BlockPos(f.x-4,y,z),Blocks.STONE.defaultBlockState(),2);
                float before=craft.health();
                var stone=new com.codex.blacklight.entity.ThrownBlock(com.codex.blacklight.registry.ModEntities.THROWN_BLOCK.get(),f.level);
                stone.setOwner(player);
                stone.arm();
                stone.setPos(f.x-6,82.1,0);
                stone.setDeltaMovement(8,0,0);
                f.level.addFreshEntity(stone);
                f.entities.add(stone);
                stone.tick();
                h.assertTrue(pass==0?craft.health()<before:craft.health()==before,"physical thrown stone resolves nearest aircraft or wall: pass="+pass);
            }
        }
        h.succeed();});
    }

    @GameTest(template="helicopter_arena", batch="air_moving_dismount", timeoutTicks=310)
    public static void airborneDismountClearsRotatedHullAndBrakesThenLands(GameTestHelper h) {
        var f=new HelicopterPilotGameTest.Fixture(h,285000);
        var craft=f.craft(10);craft.setYRot(45);
        var player=HelicopterPilotGameTest.player(h,f,false);
        h.assertTrue(player.startRiding(craft,true),"actual pilot boards airborne rotated aircraft");
        int[] time={0};long[] sequence={0};Vec3[] exit={null};
        h.onEachTick(()->{
            int t=++time[0];if(craft.isRemoved())return;
            if(t>=90&&t<110&&t%2==0)h.assertTrue(craft.pilot().accept(player,craft.pilot().lease(player.getUUID()),
                    sequence[0]++,1,0,0,0,0),"actual forward control accelerates before dismount");
            if(t==110){
                h.assertTrue(craft.getDeltaMovement().horizontalDistance()>.2,"exercise a moving aircraft, not a parked fixture");
                player.stopRiding();exit[0]=craft.position();
                h.assertTrue(!player.isPassenger()&&!player.getBoundingBox().intersects(craft.getBoundingBox()),
                        "dismount is outside the diagonal world hull, rather than on its roof");
                h.assertTrue(player.getY()<craft.getY()+1,"side exit stays below the rotor");
                var rider=player.getBoundingBox();
                h.assertTrue(rider.maxX<=craft.getX()-7||rider.minX>=craft.getX()+7
                        ||rider.maxZ<=craft.getZ()-7||rider.minZ>=craft.getZ()+7,
                        "a hovering former pilot is outside the entire rotor descent column");
                h.assertTrue(craft.pilot().lease(player.getUUID())==null,"departing pilot loses control immediately");
            }
            if(t>111&&exit[0]!=null)h.assertTrue(craft.position().subtract(exit[0]).horizontalDistance()<.01,
                    "unpiloted craft does not carry stale forward input or horizontal momentum");
        });
        h.runAfterDelay(275,()->{try(f){
            h.assertTrue(Math.abs(craft.getY()-81)<.01,"abandoned airborne craft actually descends to ground: y="+craft.getY()+", phase="+craft.phase()+", rotor="+craft.rotor());
            h.assertTrue(craft.phase()==BlackwatchHelicopter.Phase.IDLE&&craft.rotor()==0,
                    "ground contact ends manual flight and spins the rotor down");
            h.assertTrue(player.isAlive()&&!player.isPassenger(),"exiting pilot is not dragged along with the aircraft");
        }h.succeed();});
    }

    @GameTest(template="helicopter_arena", batch="air_uneven_dismount", timeoutTicks=270)
    public static void unpilotedAircraftStopsOnPartialGroundSupport(GameTestHelper h) {
        var f=new HelicopterPilotGameTest.Fixture(h,286000);var craft=f.craft(8);
        f.level.setBlock(new BlockPos(f.x+2,81,2),Blocks.STONE.defaultBlockState(),2);
        var player=HelicopterPilotGameTest.player(h,f,false);
        h.assertTrue(player.startRiding(craft,true),"actual pilot claims aircraft above uneven terrain");
        h.runAfterDelay(90,player::stopRiding);
        h.runAfterDelay(240,()->{try(f){
            h.assertTrue(Math.abs(craft.getY()-82)<.01,"hull rests on the raised solid contact without penetrating: y="+craft.getY()+", phase="+craft.phase()+", rotor="+craft.rotor());
            h.assertTrue(!craft.safeLanding(craft.position()),"the strict nine-point mission landing test is deliberately unsuitable here");
            h.assertTrue(craft.phase()==BlackwatchHelicopter.Phase.IDLE&&craft.rotor()==0,
                    "partial real support is enough to stop an abandoned craft");
        }h.succeed();});
    }

    @GameTest(template="helicopter_arena", batch="air_external_push", timeoutTicks=155)
    public static void externalPushDoesNotBecomeFlightVelocityWhenTheRotorStarts(GameTestHelper h) {
        var f=new HelicopterPilotGameTest.Fixture(h,284000);
        var craft=f.craft(0);var player=HelicopterPilotGameTest.player(h,f,false);
        h.assertTrue(player.startRiding(craft,true),"actual player boards the stationary craft");
        var position=craft.position();
        h.runAfterDelay(30,()->craft.push(.2,0,.2));
        h.runAfterDelay(100,()->{
            h.assertTrue(craft.rotor()>=.99F,"the engine really reached its running state");
            craft.push(.2,0,.2);
            h.assertTrue(craft.hurt(f.level.damageSources().generic(),1),"blocking pedestrian impulses does not block damage");
        });
        h.runAfterDelay(130,()->{try(f){
            h.assertTrue(craft.position().distanceTo(position)<.001,"external push cannot become engine velocity");
            h.assertTrue(player.getVehicle()==craft&&craft.health()==359,"pilot stays seated and ordinary hull damage still works");
        }h.succeed();});
    }

    @GameTest(template="helicopter_arena", batch="air_wall_lift", timeoutTicks=185)
    public static void forwardInputAtAWallDoesNotCancelLift(GameTestHelper h) {
        var f=new HelicopterPilotGameTest.Fixture(h,283000);
        var craft=f.craft(0);craft.setPos(f.x,81,2.999);
        var player=HelicopterPilotGameTest.player(h,f,false);
        h.assertTrue(player.startRiding(craft,true),"actual pilot boards beside a wall");
        for(int x=-12;x<=12;x++)for(int y=81;y<=103;y++)
            f.level.setBlock(new BlockPos(f.x+x,y,10),Blocks.STONE.defaultBlockState(),2);
        int[] time={0};long[] sequence={0};
        h.onEachTick(()->{
            int t=++time[0];if(craft.isRemoved())return;
            if(t>=90&&t<140&&t%2==0)h.assertTrue(craft.pilot().accept(player,craft.pilot().lease(player.getUUID()),
                    sequence[0]++,1,0,0,0,VehicleControlAuthority.ASCEND),"forward and lift both reach the cockpit");
        });
        h.runAfterDelay(155,()->{try(f){
            h.assertTrue(craft.getY()>87,"holding forward against a wall still permits lift: y="+craft.getY());
            h.assertTrue(craft.getZ()<=3.002,"the rotor never crosses the wall: z="+craft.getZ());
            h.assertTrue(craft.clearAt(craft.position(),craft.getYRot()),"full airframe remains outside the wall");
        }h.succeed();});
    }

    @GameTest(template="helicopter_arena", batch="air_relaunch", timeoutTicks=370)
    public static void realPilotCanLandAndTakeOffAgain(GameTestHelper h) {
        var f=new HelicopterPilotGameTest.Fixture(h,281000);
        var craft=f.craft(0);var player=HelicopterPilotGameTest.player(h,f,false);
        h.assertTrue(player.startRiding(craft,true),"actual pilot boards");
        int[] time={0};long[] sequence={0};double[] firstHeight={81};
        h.onEachTick(()->{
            int t=++time[0];if(craft.isRemoved())return;
            if(t<160)firstHeight[0]=Math.max(firstHeight[0],craft.getY());
            int actions=t>=90&&t<125||t>=280&&t<320?VehicleControlAuthority.ASCEND
                    :t>=165&&t<245?VehicleControlAuthority.DESCEND:0;
            if(t>=90&&t%2==0)h.assertTrue(craft.pilot().accept(player,craft.pilot().lease(player.getUUID()),
                    sequence[0]++,0,0,0,0,actions),"cockpit input remains authorized after landing");
            if(t==250)h.assertTrue(firstHeight[0]>86&&Math.abs(craft.getY()-81)<.01
                    &&craft.safeLanding(craft.position()),"first flight actually lands on its pad");
        });
        h.runAfterDelay(335,()->{try(f){
            h.assertTrue(craft.getY()>87&&craft.phase()==BlackwatchHelicopter.Phase.MANUAL,
                    "the same real pilot takes off again after landing: y="+craft.getY());
        }h.succeed();});
    }

    @GameTest(template="helicopter_arena", batch="air_bystander_relaunch", timeoutTicks=180)
    public static void existingBystanderDoesNotTrapUpwardDeparture(GameTestHelper h) {
        var f=new HelicopterPilotGameTest.Fixture(h,282000);
        var craft=f.craft(0);var player=HelicopterPilotGameTest.player(h,f,false);
        h.assertTrue(player.startRiding(craft,true),"actual pilot boards");
        var bystander=EntityType.PIG.create(f.level);
        bystander.setNoAi(true);bystander.setNoGravity(true);bystander.setPersistenceRequired();
        bystander.setPos(f.x+2.9,81,0);
        int[] time={0};long[] sequence={0};
        h.runAfterDelay(90,()->{
            h.assertTrue(craft.rotor()>=.99F,"the real spin-up completed");
            h.assertTrue(f.level.addFreshEntity(bystander),"register the bystander touching the existing hull");
            f.entities.add(bystander);
            h.assertTrue(craft.getBoundingBox().intersects(bystander.getBoundingBox()),"exercise existing overlap");
        });
        h.onEachTick(()->{
            int t=++time[0];if(craft.isRemoved())return;
            if(t>=100&&t<140&&t%2==0)h.assertTrue(craft.pilot().accept(player,craft.pilot().lease(player.getUUID()),
                    sequence[0]++,0,0,0,0,VehicleControlAuthority.ASCEND),"lift request reaches the real driver");
        });
        h.runAfterDelay(155,()->{try(f){
            h.assertTrue(craft.getY()>87,"an existing nearby body cannot veto every upward step: y="+craft.getY());
            h.assertTrue(bystander.isAlive()&&bystander.getY()==81,"departure neither teleports nor damages the bystander");
        }h.succeed();});
    }
}
