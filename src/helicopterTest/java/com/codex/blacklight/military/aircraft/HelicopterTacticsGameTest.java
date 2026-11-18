package com.codex.blacklight.military.aircraft;

import com.codex.blacklight.entity.BlackwatchHelicopter;
import com.codex.blacklight.registry.ModEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Controller integration against real loaded terrain and Entity.move. The
 * mission stays idle so only this controller moves each craft once per tick.
 * Full crew, weapons, input and NBT integration remain in their dedicated tests. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterTacticsGameTest {
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final ServerLevel level;
        final int x;
        final List<Entity> entities=new ArrayList<>();
        final Map<Long,Boolean> forced=new HashMap<>();
        final Vec3 targetAt;
        final HelicopterAirspace space;
        boolean done;
        int age;
        BlackwatchHelicopter craft;
        Cow target;
        HelicopterTactics tactics;
        HelicopterStrafeManeuver.Route route;
        Vec3 movedTarget;
        int outboundTicks;

        Fixture(GameTestHelper h,int index,boolean raised){
            this.h=h;level=h.getLevel();x=430000+index*512;targetAt=new Vec3(x+.5,81,.5);
            // Candidate reach40 + end-area radius15 + full rotor/chunk margin.
            for(int cx=(x-64)>>4;cx<=(x+64)>>4;cx++)for(int cz=-4;cz<=4;cz++){
                long key=ChunkPos.asLong(cx,cz);forced.put(key,level.getForcedChunks().contains(key));level.setChunkForced(cx,cz,true);
            }
            for(int dx=-56;dx<=56;dx++)for(int z=-56;z<=56;z++){
                level.setBlock(new BlockPos(x+dx,80,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
                int top=Math.max(116,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x+dx,z));
                for(int y=81;y<=top;y++){
                    var pos=new BlockPos(x+dx,y,z);
                    if(!level.isEmptyBlock(pos))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                }
            }
            // Nearby eight-block roof admits STRAFE without blocking the actual
            // target's ground cell or the raised preparation flight envelope.
            if(raised)for(int y=81;y<=88;y++)level.setBlock(new BlockPos(x+10,y,10),Blocks.STONE.defaultBlockState(),2);
            h.runAfterDelay(20,()->{
                for(var old:level.getEntitiesOfClass(Entity.class,new AABB(x-58,75,-58,x+58,140,58),e->!(e instanceof Player)&&!entities.contains(e)))old.discard();
            });
            space=HelicopterAirspace.forLevel(level);
        }
        BlackwatchHelicopter craft(Vec3 position){
            var value=ModEntities.BLACKWATCH_HELICOPTER.get().create(level);
            h.assertTrue(value!=null,"real aircraft type exists");value.setPos(position);
            h.assertTrue(level.addFreshEntity(value),"real aircraft is registered with its world UUID");entities.add(value);return value;
        }
        HelicopterTactics controller(BlackwatchHelicopter aircraft,HelicopterHellfireAttack hellfire,DoubleSupplier draw){
            return new HelicopterTactics(aircraft,new HelicopterCircleStrafe(),hellfire,draw);
        }
        boolean step(HelicopterTactics controller,BlackwatchHelicopter aircraft,Vec3 observed,boolean seen,boolean hover){
            Vec3 before=aircraft.position();
            boolean gun=controller.tick(level,observed,observed.add(0,1,0),seen,false,false,false,hover);
            h.assertTrue(aircraft.clearAt(aircraft.position(),aircraft.getYRot()),"real body and rotor remain clear");
            h.assertTrue(aircraft.position().subtract(before).distanceTo(aircraft.getDeltaMovement())<1e-6,
                    "controller applies exactly its one swept movement vector");
            return gun;
        }
        HelicopterStrafeCache.Snapshot seed(BlackwatchHelicopter aircraft){
            var candidate=space.query(aircraft,targetAt,.5F,2,5,40,false);
            h.assertTrue(candidate!=null&&candidate.valid(),"real loaded terrain admits the source corridor");
            var committed=space.commit(aircraft);
            h.assertTrue(space.reserveSource(aircraft,targetAt,true,5,null),"actual world reserves its corridor");return committed;
        }
        void assertReleased(BlackwatchHelicopter aircraft,Vec3 reserved){
            h.assertTrue(space.pointAvailable(reserved,0,null),"other aircraft can use the released world volume immediately");
            if(!aircraft.isRemoved())h.assertTrue(!space.committed(aircraft).valid()&&!space.candidate(aircraft).valid(),"interruption drops both candidate and committed world geometry");
            h.assertTrue(aircraft.navigation().status()==HelicopterNavigation.Status.DIRECT&&aircraft.navigation().waypoints().isEmpty(),"interruption clears actual navigation state");
        }
        void pass(){done=true;close();h.succeed();}
        public void close(){entities.forEach(Entity::discard);forced.forEach((key,value)->{var chunk=new ChunkPos(key);level.setChunkForced(chunk.x,chunk.z,value);});}
    }
    private static void run(GameTestHelper h,int index,boolean raised,Consumer<Fixture> tick){
        var f=new Fixture(h,index,raised);int[] elapsed={0};
        h.onEachTick(()->{
            if(f.done)return;
            try{
                if(++elapsed[0]<40||!f.level.isPositionEntityTicking(BlockPos.containing(f.targetAt)))return;
                h.assertTrue(f.age<90,"bounded controller scenario must finish");tick.accept(f);f.age++;
            }catch(RuntimeException|AssertionError failure){f.done=true;f.close();throw failure;}
        });
    }

    @GameTest(template="helicopter_arena",batch="airTacticsBank",timeoutTicks=180)
    public static void actualWeightedBankMovesCircleAndBrakesSimpleAndHover(GameTestHelper h){
        run(h,0,false,f->{
            h.assertTrue(f.space.areaHeight(f.targetAt,25)==81&&f.space.areaHeight(f.targetAt,60)==81,"flat loaded heightmaps exclude STRAFE");
            var circle=f.craft(f.targetAt.add(-24,8,0));var simple=f.craft(f.targetAt.add(24,8,0));var hover=f.craft(f.targetAt.add(0,8,24));
            int[] draws={0,0,0};
            var circleController=f.controller(circle,new HelicopterHellfireAttack(),()->{draws[0]++;return .1;});
            var simpleController=f.controller(simple,new HelicopterHellfireAttack(),()->{draws[1]++;return .99;});
            var readyHellfire=new HelicopterHellfireAttack();var ready=readyHellfire.save();ready.putInt("Avoid",0);readyHellfire.load(ready);
            var hoverController=f.controller(hover,readyHellfire,()->{draws[2]++;return .999;});
            simple.setDeltaMovement(.2,0,0);hover.setDeltaMovement(.2,0,0);
            Vec3 circleAt=circle.position(),simpleAt=simple.position(),hoverAt=hover.position();
            h.assertTrue(f.step(circleController,circle,f.targetAt,true,false)&&circleController.name().equals("CIRCLE"),"low actual bank draw selects eligible circle and permits gun thread");
            h.assertTrue(f.space.requestToken(circle),"another aircraft holds the token so this bank-only fixture cannot take the separate open-run opportunity");
            h.assertTrue(f.step(simpleController,simple,f.targetAt,true,false)&&simpleController.name().equals("SIMPLE"),"high draw without ready hover selects simple engage");
            h.assertTrue(!f.step(hoverController,hover,f.targetAt,true,true)&&hoverController.name().equals("HOVER")&&readyHellfire.stage()==HelicopterHellfireAttack.Stage.WAIT,"ready weighted hover enters its real wait and suppresses gun thread");
            h.assertTrue(draws[0]==1&&draws[1]==1&&draws[2]==1,"bank samples once per new choice, without replacing world RNG");
            h.assertTrue(circle.position().distanceTo(circleAt)>.03&&circle.position().distanceTo(circleAt)<.036,"circle applies one accelerated real movement");
            h.assertTrue(Math.abs(simple.position().distanceTo(simpleAt)-.165)<1e-6&&Math.abs(hover.position().distanceTo(hoverAt)-.165)<1e-6,"simple hold and hover brake existing velocity once instead of teleporting to entry");
            f.pass();
        });
    }

    @GameTest(template="helicopter_arena",batch="airTacticsOpen",timeoutTicks=180)
    public static void simpleOpportunityEntersIndependentOpenRunAndReleasesIt(GameTestHelper h){
        run(h,3,false,f->{
            if(f.age==0){
                f.craft=f.craft(f.targetAt.add(0,15,-20));f.craft.setYRot(0);
                f.tactics=f.controller(f.craft,new HelicopterHellfireAttack(),()->.99);
                f.step(f.tactics,f.craft,f.targetAt,true,false);
                h.assertTrue(f.tactics.name().equals("OPEN_STRAFE")&&f.tactics.stage().equals("OPEN_RUN"),"actual SIMPLE selection starts its eligible independent open-run action");
                h.assertTrue(!f.space.pointAvailable(f.targetAt.add(0,10,0),0,null),"open action reserves its raised target corridor");return;
            }
            if(f.age<5){f.step(f.tactics,f.craft,f.targetAt,true,false);return;}
            h.assertTrue(f.craft.position().distanceTo(f.targetAt.add(0,15,-20))>.1,"open run applies real swept movement instead of only changing its phase name");
            var saved=f.tactics.save();h.assertTrue(saved.getString("Tactic").equals("OPEN_STRAFE"),"active independent route is marked in saved diagnostics");
            f.step(f.tactics,f.craft,f.targetAt,false,false);
            h.assertTrue(f.tactics.name().equals("NONE")&&f.space.pointAvailable(f.targetAt.add(0,10,0),0,null),"lost observation ends open run and frees its volume");
            var restored=f.controller(f.craft,new HelicopterHellfireAttack(),()->.99);restored.restore(saved);
            h.assertTrue(restored.name().equals("NONE")&&restored.destination()==null,"reloading does not recreate a world-owned open corridor from a saved phase");
            f.pass();
        });
    }

    @GameTest(template="helicopter_arena",batch="airTacticsAlign",timeoutTicks=180)
    public static void acceptedLateralTargetMoveCannotStallRealPrepareAlignment(GameTestHelper h){
        run(h,1,true,f->{
            if(f.age==0){
                f.target=EntityType.COW.create(f.level);h.assertTrue(f.target!=null,"real moving target exists");
                f.target.setPos(f.targetAt);f.target.setNoAi(true);f.target.setNoGravity(true);f.level.addFreshEntity(f.target);f.entities.add(f.target);
                f.craft=f.craft(f.targetAt.add(0,28,-6));
                var candidate=f.space.query(f.craft,f.targetAt,.5F,2,5,40,false);
                h.assertTrue(candidate!=null&&candidate.valid(),"raised scene has a valid source candidate");
                f.route=HelicopterStrafeManeuver.prepareRoute(candidate.path(),candidate.targetAtPlan(),f.space.groundAt(f.targetAt),false);
                h.assertTrue(f.route!=null,"source candidate supplies preparation geometry");f.craft.setPos(f.route.start());
                Vec3 along=f.route.end().subtract(f.route.start()).multiply(1,0,1).normalize();
                f.movedTarget=f.targetAt.add(new Vec3(along.z,0,-along.x).scale(8));
                h.assertTrue(f.route.accepts(f.movedTarget),"eight-block lateral movement is explicitly inside original path tolerance");
                Vec3 toMoved=f.movedTarget.subtract(f.craft.position());f.craft.setYRot((float)Math.toDegrees(Math.atan2(toMoved.x,toMoved.z)));
                f.tactics=f.controller(f.craft,new HelicopterHellfireAttack(),()->.5);
                f.step(f.tactics,f.craft,f.target.position(),true,false);
                h.assertTrue(f.tactics.name().equals("STRAFE")&&f.tactics.stage().equals("ALIGN"),"real arrival enters source preparation alignment");
                f.target.setPos(f.movedTarget);return;
            }
            f.step(f.tactics,f.craft,f.target.position(),true,false);
            h.assertTrue(f.space.committed(f.craft).targetAtPlan().equals(f.targetAt),"refreshes do not silently replace the committed target geometry");
            if(f.tactics.stage().equals("OUTBOUND"))f.outboundTicks++;
            if(f.outboundTicks>=7){
                h.assertTrue(f.age<60&&f.craft.position().distanceTo(f.route.start())>.1,"actual yaw alignment completes and tight run really starts moving after valid target movement");
                f.pass();
            }
        });
    }

    @GameTest(template="helicopter_arena",batch="airTacticsCleanup",timeoutTicks=180)
    public static void sightPhaseDeathAndUnloadReleaseWorldCoordination(GameTestHelper h){
        run(h,2,true,f->{
            if(f.age==0){
                f.craft=f.craft(f.targetAt.add(0,28,-6));f.tactics=f.controller(f.craft,new HelicopterHellfireAttack(),()->.5);
                f.step(f.tactics,f.craft,f.targetAt,true,false);
                h.assertTrue(f.tactics.name().equals("STRAFE")&&f.space.committed(f.craft).valid(),"bank actually commits a live strafe route");
                h.assertTrue(!f.space.pointAvailable(f.space.committed(f.craft).path().start(),0,null),"world volume initially blocks another aircraft");return;
            }
            if(f.age==1){
                var committed=f.space.committed(f.craft);f.step(f.tactics,f.craft,f.targetAt,false,false);
                h.assertTrue(f.tactics.name().equals("NONE")&&f.space.pointAvailable(committed.path().start(),0,null),"lost observation cancels maneuver and releases reservation immediately");
                h.assertTrue(f.space.committed(f.craft).equals(committed),"ordinary action end retains source cache until engagement interruption");return;
            }
            if(f.age==2){
                f.step(f.tactics,f.craft,f.targetAt,true,false);
                h.assertTrue(f.tactics.name().equals("STRAFE")&&!f.space.pointAvailable(f.space.committed(f.craft).path().start(),0,null),"reobserved target reacquires a live reservation");return;
            }
            Vec3 reserved=f.space.committed(f.craft).path().start();
            f.craft.setFlightPhase(BlackwatchHelicopter.Phase.STRAFE);f.craft.setFlightPhase(BlackwatchHelicopter.Phase.MANUAL);
            f.assertReleased(f.craft,reserved);
            // The source manager is keyed by aircraft UUID. Reinsert real world
            // state to independently exercise entity removal in the same tick,
            // before sync() can prune any missing-owner records for us.
            reserved=f.seed(f.craft).path().start();h.assertTrue(!f.space.pointAvailable(reserved,0,null),"unload fixture has an actual occupied volume");
            f.craft.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);f.assertReleased(f.craft,reserved);
            var doomed=f.craft(f.targetAt.add(24,28,0));reserved=f.seed(doomed).path().start();
            doomed.setFlightPhase(BlackwatchHelicopter.Phase.STRAFE);
            h.assertTrue(doomed.hurt(f.level.damageSources().generic(),500)&&doomed.phase()==BlackwatchHelicopter.Phase.CRASH,"actual lethal hull damage enters crash");
            f.assertReleased(doomed,reserved);f.pass();
        });
    }
}
