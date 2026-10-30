package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.state.BlacklightState;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/** Exercises the original server hit entries, never a direct vehicle damage call. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false) @EventBusSubscriber(modid="blacklight")
public final class VehiclePhysicalDamageGameTest {
    private static final Set<UUID> REJECT=new HashSet<>();
    private static int rejected;
    @SubscribeEvent public static void protectedAttack(AttackEntityEvent event){if(REJECT.contains(event.getTarget().getUUID())){rejected++;event.setCanceled(true);}}
    private static ServerPlayer player(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,double z){
        var p=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(p);p.setGameMode(GameType.SURVIVAL);p.setPos(f.x+.5,81,z);p.setYRot(0);p.setXRot(0);
        var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);p.setData(ModAttachments.PLAYER_STATE,state);return p;
    }
    private static MilitaryVehicle tank(MilitaryVehicleGameTest.Fixture f,double z){
        var v=ModEntities.MILITARY_TANK.get().create(f.level);v.setPos(f.x+.5,81,z);f.level.addFreshEntity(v);f.entities.add(v);if(!f.level.getEntitiesOfClass(MilitaryVehicle.class,v.getBoundingBox().inflate(.01)).contains(v))throw new GameTestAssertException("fixture must expose the actual chassis in the already ticking forced chunk before testing its hit entry");return v;
    }
    private static void claw(ServerPlayer player){
        try{
            var combat=new ClawCombatState();combat.begin(ClawCombatState.Action.AUTO_3);combat.setAttackDirection(0,1);
            var hit=ClawCombatSystem.class.getDeclaredMethod("performClawHit",ServerPlayer.class,BlacklightState.class,ClawCombatState.class);hit.setAccessible(true);
            hit.invoke(null,player,player.getData(ModAttachments.PLAYER_STATE),combat);
        }catch(ReflectiveOperationException error){throw new GameTestAssertException("fixture reflection failed: "+error);}
    }
    private static ThrownBlock block(MilitaryVehicleGameTest.Fixture f,ServerPlayer player,Vec3 at,Vec3 velocity){
        var block=ModEntities.THROWN_BLOCK.get().create(f.level);block.setOwner(player);block.arm();block.setPos(at);block.setDeltaMovement(velocity);f.level.addFreshEntity(block);f.entities.add(block);return block;
    }
    @SuppressWarnings("unchecked") private static Map<Mob,Object> thrown(){
        try{var field=ConsumeSystem.class.getDeclaredField("THROWN");field.setAccessible(true);return (Map<Mob,Object>)field.get(null);}
        catch(ReflectiveOperationException error){throw new GameTestAssertException("fixture reflection failed: "+error);}
    }
    private static Mob thrownMob(MilitaryVehicleGameTest.Fixture f,ServerPlayer player,double z,Vec3 velocity){
        var mob=EntityType.COW.create(f.level);mob.setNoAi(true);mob.setPos(f.x+.5,81,z);f.level.addFreshEntity(mob);f.entities.add(mob);
        try{var type=Class.forName(ConsumeSystem.class.getName()+"$Thrown");var constructor=type.getDeclaredConstructor(ServerPlayer.class,int.class,Vec3.class,float.class);constructor.setAccessible(true);thrown().put(mob,constructor.newInstance(player,40,velocity,12F));}
        catch(ReflectiveOperationException error){throw new GameTestAssertException("fixture reflection failed: "+error);}return mob;
    }
    private static void step(Mob mob){ConsumeSystem.thrown(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(mob));}
    private static void wall(MilitaryVehicleGameTest.Fixture f,int z){for(int x=-3;x<=3;x++)for(int y=81;y<85;y++)f.level.setBlock(new BlockPos(f.x+x,y,z),Blocks.STONE.defaultBlockState(),2);}

    @GameTest(template="blackwatch_arena",batch="vehicle6_physical",timeoutTicks=100)
    public static void realClawFinisherHitsOneHeavyHullWithPhysicalCoefficient(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,290000);
        VehicleTestReadiness.run(h,f,()->{
            var p=player(h,f,-4.5);var v=tank(f,1);float before=v.health();claw(p);
            h.assertTrue(Math.abs(before-v.health()-5.5F)<.001,"original AUTO_3 server impact applies its actual eleven damage once at explicit heavy physical .5, including prototype event scope");
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_physical",timeoutTicks=100)
    public static void realClawFinisherCannotReachThroughWall(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,291000);
        VehicleTestReadiness.run(h,f,()->{
            var p=player(h,f,-4.5);var v=tank(f,1);
            h.assertTrue(p.getBoundingBox().maxZ<-4&&v.getBoundingBox().minZ>=-3-.000001,"fixture precondition: the wall at z=-4 lies between the actual player and enlarged hull without intersecting either");
            wall(f,-4);claw(p);h.assertTrue(v.health()==v.maxHealth(),"the real wall before nearest hull contact blocks claw damage");
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_physical",timeoutTicks=100)
    public static void actualThrownBlockDamagesFirstHullOnceAndSettles(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,292000);
        VehicleTestReadiness.run(h,f,()->{
            var p=player(h,f,-7);var v=tank(f,3);var second=tank(f,14);var shot=block(f,p,new Vec3(f.x+.5,82,-4),new Vec3(0,0,18));shot.tick();
            h.assertTrue(Math.abs(v.maxHealth()-v.health()-4)<.001&&second.health()==second.maxHealth(),"actual carried block impact uses original eight damage at physical .5 and stops at nearest real hull");
            shot.tick();h.assertTrue(Math.abs(v.maxHealth()-v.health()-4)<.001,"settled or removed cargo cannot damage the parent/parts again");
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_physical",timeoutTicks=100)
    public static void actualThrownBlockWallWinsBeforeHull(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,293000);
        VehicleTestReadiness.run(h,f,()->{
            var p=player(h,f,-7);var v=tank(f,5);wall(f,-2);var shot=block(f,p,new Vec3(f.x+.5,82,-5),new Vec3(0,0,12));shot.tick();
            h.assertTrue(v.health()==v.maxHealth()&&shot.position().z<0,"actual swept cargo stops at wall before mechanical body");
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_physical",timeoutTicks=100)
    public static void actualThrownMobHitsMechanicalBodyBeforeLivingBehindIt(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,294000);
        VehicleTestReadiness.run(h,f,()->{
            var p=player(h,f,-8);var v=tank(f,3);var behind=EntityType.COW.create(f.level);behind.setNoAi(true);behind.setPos(f.x+.5,81,9);f.level.addFreshEntity(behind);f.entities.add(behind);
            var mob=thrownMob(f,p,-5,new Vec3(0,0,15));step(mob);
            h.assertTrue(Math.abs(v.maxHealth()-v.health()-6)<.001&&behind.getHealth()==behind.getMaxHealth(),"actual thrown-mob Pre uses the actual twelve damage at the first real mechanical contact, excluding a living actor behind it");
            h.assertTrue(!thrown().containsKey(mob)&&mob.position().z<3,"first hull ends the real ballistic record at contact");
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_physical",timeoutTicks=100)
    public static void actualThrownMobNearestLivingWinsBeforeHull(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,295000);
        VehicleTestReadiness.run(h,f,()->{
            var p=player(h,f,-8);var v=tank(f,7);var ahead=EntityType.COW.create(f.level);ahead.setNoAi(true);ahead.setPos(f.x+.5,81,-1);f.level.addFreshEntity(ahead);f.entities.add(ahead);
            var mob=thrownMob(f,p,-5,new Vec3(0,0,15));step(mob);
            h.assertTrue(v.health()==v.maxHealth()&&ahead.getHealth()<ahead.getMaxHealth(),"first living collision and hull share nearest-contact ordering");
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_physical",timeoutTicks=100)
    public static void actualPhysicalImpactRespectsCreativeCrewProtection(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,296000);
        VehicleTestReadiness.run(h,f,()->{
            var p=player(h,f,-7);var v=tank(f,3);var crew=MilitaryVehicleGameTest.mockPlayer(h);crew.setGameMode(GameType.CREATIVE);f.entities.add(crew);h.assertTrue(crew.startRiding(v),"real protected player boards");
            var shot=block(f,p,new Vec3(f.x+.5,82,-4),new Vec3(0,0,12));shot.tick();h.assertTrue(v.health()==v.maxHealth(),"mechanical shell cannot bypass creative passenger protection");
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_physical",timeoutTicks=100)
    public static void actualThrownBlockDeadOwnerHasNoMechanicalDamage(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,297000);
        VehicleTestReadiness.run(h,f,()->{
            var p=player(h,f,-7);var v=tank(f,3);var shot=block(f,p,new Vec3(f.x+.5,82,-4),new Vec3(0,0,12));p.setHealth(0);shot.tick();h.assertTrue(v.health()==v.maxHealth(),"dead actual thrower cannot leave ghost mechanical damage");
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_physical",timeoutTicks=100)
    public static void actualWideCargoEdgeHitsWithoutItsCenterRayEnteringHull(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,298000);
        VehicleTestReadiness.run(h,f,()->{
            var p=player(h,f,-7);var v=tank(f,3);var shot=block(f,p,new Vec3(f.x+3.2,82,-4),new Vec3(0,0,12));
            shot.setCargo(new BlockThrowCargo(List.of(new BlockThrowCargo.Cell(BlockPos.ZERO,Blocks.STONE.defaultBlockState()),new BlockThrowCargo.Cell(new BlockPos(2,0,0),Blocks.STONE.defaultBlockState()))));shot.tick();
            h.assertTrue(Math.abs(v.maxHealth()-v.health()-4)<.001,"the actual three-wide carried cargo edge hits the real hull even though its center ray misses; cargo shape never multiplies its saved impact damage");
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_physical",timeoutTicks=100)
    public static void actualPhysicalAttackDispatchesAndHonorsProtectionEvent(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,299000);
        VehicleTestReadiness.run(h,f,()->{
            var p=player(h,f,-7);var v=tank(f,3);int before=rejected;REJECT.add(v.getUUID());
            try{var shot=block(f,p,new Vec3(f.x+.5,82,-4),new Vec3(0,0,12));shot.tick();}
            finally{REJECT.remove(v.getUUID());}
            h.assertTrue(rejected==before+1&&v.health()==v.maxHealth(),"actual original throw reaches one real protection event, whose denial prevents hull damage");
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_physical",timeoutTicks=100)
    public static void actualPhysicalAttackCannotBypassDisabledPvpThroughCrewedHull(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,300000);
        VehicleTestReadiness.run(h,f,()->{
            var p=player(h,f,-7);var v=tank(f,3);var crew=MilitaryVehicleGameTest.mockPlayer(h);crew.setGameMode(GameType.SURVIVAL);f.entities.add(crew);h.assertTrue(crew.startRiding(v),"real survival passenger boards");
            boolean before=f.level.getServer().isPvpAllowed();
            try{f.level.getServer().setPvpAllowed(false);var shot=block(f,p,new Vec3(f.x+.5,82,-4),new Vec3(0,0,12));shot.tick();h.assertTrue(v.health()==v.maxHealth(),"server PvP denial protects actual player-crewed hull from physical damage");}
            finally{f.level.getServer().setPvpAllowed(before);}
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_physical",timeoutTicks=100)
    public static void actualThrownMobWallStopsBeforeMechanicalDamage(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,301000);
        VehicleTestReadiness.run(h,f,()->{
            var p=player(h,f,-8);var v=tank(f,5);wall(f,-2);var mob=thrownMob(f,p,-5,new Vec3(0,0,15));step(mob);
            h.assertTrue(v.health()==v.maxHealth()&&!thrown().containsKey(mob)&&mob.position().z< -2,"actual thrown body hits the real wall before the hull and ends its one ballistic record");
        });
    }
    private VehiclePhysicalDamageGameTest(){}
}
