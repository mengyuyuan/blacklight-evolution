package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.HellfireBlastEntity;
import com.codex.blacklight.military.aircraft.HelicopterHellfireAttack;
import com.codex.blacklight.registry.ModEntities;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterHoverGameTest {
    private static final class Fixture {
        final GameTestHelper h;final HelicopterPilotGameTest.Fixture area;final Vec3 targetAt;
        BlackwatchHelicopter craft;BlacklightCommonInfected target;boolean ready,done,hovered,restoredWait,restoredFire,hit,wall;
        int age,entry=-1,firstShot=-1,gunAtEntry,lastRocket;Vec3 anchor,lastSeen;
        Fixture(GameTestHelper h,int i){this.h=h;area=new HelicopterPilotGameTest.Fixture(h,290000+i*512);targetAt=new Vec3(area.x+12,81,0);}
        boolean ready(){
            if(ready)return true;if(!area.level.isPositionEntityTicking(BlockPos.containing(targetAt)))return false;ready=true;
            target=ModEntities.COMMON_INFECTED.get().create(area.level);target.setPos(targetAt);target.setNoAi(true);target.setNoGravity(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);area.level.addFreshEntity(target);area.entities.add(target);
            craft=area.craft(16);var crew=area.crew(craft);for(int i=0;i<crew.size();i++)if(i<2)crew.get(i).setNoAi(false);else crew.get(i).discard();
            var caller=ModEntities.BLACKWATCH_OFFICER.get().create(area.level);caller.setPos(area.x-12,81,0);caller.setAutomaticUnitJoin(false);area.level.addFreshEntity(caller);caller.setTarget(target);
            h.assertTrue(craft.deployTo(new Vec3(area.x,81,12)),"real source hover sortie");craft.combat().supportCall(caller);caller.discard();
            var tag=new CompoundTag();craft.saveWithoutId(tag);tag.putInt("FlightPhase",BlackwatchHelicopter.Phase.PURSUIT.ordinal());craft.load(tag);return true;
        }
        HelicopterHellfireAttack attack(){return craft.combat().hellfireAttack();}
        void reload(){var tag=new CompoundTag();craft.saveWithoutId(tag);craft.load(tag);}
        void pass(String name){done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.info("HELI_HOVER_VERIFIED case={} entry={} firstShot={} age={} rockets={} gunShots={} speed={} reloadWait={} reloadFire={}",name,entry,firstShot,age,craft.combat().rocketShots(),craft.combat().gunShots(),craft.getDeltaMovement().length(),restoredWait,restoredFire);area.close();h.succeed();}
    }
    private static void run(GameTestHelper h,int i,Consumer<Fixture> step){var f=new Fixture(h,i);h.onEachTick(()->{if(f.done)return;try{if(!f.ready())return;f.target.setPos(f.targetAt);f.target.setDeltaMovement(Vec3.ZERO);step.accept(f);f.age++;}catch(RuntimeException ex){f.done=true;f.area.close();throw ex;}});}
    private static void observe(Fixture f){
        if(f.attack().hovering()&&!f.hovered){f.hovered=true;f.entry=f.age;f.gunAtEntry=f.craft.combat().gunShots();f.anchor=f.craft.combat().hoverPoint();f.h.assertTrue(f.entry>=299,"first source hover respects fifteen-second avoidance");f.h.assertTrue(f.gunAtEntry>0,"actual chin gun fired before hover");}
        if(f.attack().hovering()){f.h.assertTrue(f.craft.combat().gunShots()==f.gunAtEntry,"no simultaneous chin gun during Hellfire sequence");f.h.assertTrue(f.anchor.equals(f.craft.combat().hoverPoint()),"hover keeps its saved entry reference while braking");}
        if(f.craft.combat().rocketShots()>f.lastRocket){f.firstShot=f.age;f.h.assertTrue(f.attack().stage()==HelicopterHellfireAttack.Stage.FIRE,"native missile emitted only during Action pulse");f.h.assertTrue(f.firstShot-f.entry>=20,"pre-aim wait elapsed");f.h.assertTrue(f.craft.getDeltaMovement().length()<.04,"aircraft physically brakes into hover before firing: "+f.craft.getDeltaMovement());f.lastRocket=f.craft.combat().rocketShots();}
        f.hit|=f.target.getLastDamageSource()!=null&&f.target.getLastDamageSource().getDirectEntity() instanceof HellfireBlastEntity;
    }
    @GameTest(template="helicopter_arena",batch="hoverSequence",timeoutTicks=650)
    public static void realSourceHoverBrakesWaitsFiresOnceAndReturnsToGun(GameTestHelper h){run(h,0,f->{
        observe(f);if(f.firstShot>=0&&!f.attack().hovering()&&f.craft.combat().gunShots()>f.gunAtEntry){h.assertTrue(f.hit&&f.craft.combat().rocketShots()==1,"one actual guided missile hit, then chin gun resumes");h.assertTrue(f.craft.combat().rounds()==240&&f.craft.combat().rockets()==8,"source AI leaves player stores untouched");f.pass("hover-brake-wait-fire-resume");}
    });}
    @GameTest(template="helicopter_arena",batch="hoverReload",timeoutTicks=650)
    public static void realReloadDuringPreparationAndAcceptedPulseCannotSkipOrDuplicateTheShot(GameTestHelper h){run(h,1,f->{
        observe(f);if(f.attack().stage()==HelicopterHellfireAttack.Stage.WAIT&&f.attack().hoverAge()==10&&!f.restoredWait){int avoid=f.attack().avoidTicks();f.reload();h.assertTrue(f.attack().hoverAge()==10&&f.attack().avoidTicks()==avoid,"real reload retains source preparation and timer");f.restoredWait=true;}
        if(f.craft.combat().rocketShots()==1&&!f.restoredFire){f.reload();h.assertTrue(!f.attack().wantsShot(),"accepted pulse stays consumed after real reload");f.restoredFire=true;}
        if(f.firstShot>=0&&f.age>f.firstShot+80){h.assertTrue(f.restoredWait&&f.restoredFire&&f.hit&&f.craft.combat().rocketShots()==1,"no duplicate shot and actual impact survives both reloads");f.pass("hover-reload-no-duplicate");}
    });}
    @GameTest(template="helicopter_arena",batch="hoverCover",timeoutTicks=800)
    public static void coverDuringPreparationCancelsTheShotAndPreservesAvoidanceBeforeReacquisition(GameTestHelper h){run(h,2,f->{
        if(!f.wall&&f.attack().stage()==HelicopterHellfireAttack.Stage.WAIT){f.wall=true;f.entry=f.age;f.lastSeen=f.craft.combat().lastObserved();
            var mid=BlockPos.containing(f.targetAt);f.anchor=new Vec3(mid.getX(),mid.getY(),mid.getZ());
            // Enclose the target without intersecting it or the moving rotor.
            // A midpoint cube could embed the shorter source circle's airframe.
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=0;dy<=6;dy++)if(Math.abs(dx)==2||Math.abs(dz)==2||dy==6)f.area.level.setBlock(mid.offset(dx,dy,dz),Blocks.BEDROCK.defaultBlockState(),2);
            h.assertTrue(f.area.level.noCollision(f.target),"cover does not intersect target");
        }
        if(f.wall&&f.age==f.entry+25){h.assertTrue(!f.attack().hovering()&&f.craft.combat().rocketShots()==0,"real obstruction cancels preparation without a hidden shot");h.assertTrue(f.lastSeen.equals(f.craft.combat().lastObserved()),"obstruction cannot update live target position");h.assertTrue(f.attack().avoidTicks()>100,"cancel keeps the source avoidance timer");
            var mid=BlockPos.containing(f.anchor);for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=0;dy<=6;dy++)if(Math.abs(dx)==2||Math.abs(dz)==2||dy==6)f.area.level.setBlock(mid.offset(dx,dy,dz),Blocks.AIR.defaultBlockState(),2);
        }
        if(f.wall&&f.age>f.entry+25&&f.age<f.entry+200)h.assertTrue(f.craft.combat().rocketShots()==0,"line of sight restoration cannot bypass ten-second avoidance");
        if(f.craft.combat().rocketShots()>0){f.firstShot=f.age;h.assertTrue(f.age>=f.entry+220,"fresh visible sequence waited and aimed again");f.pass("hover-cover-cancel-reacquire");}
    });}
}
