package com.codex.blacklight.gametest;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.registry.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class FlyerFlightGameTest {
    private record Scene(EncounterMob a,net.minecraft.world.entity.animal.Cow v){}
    private static Scene scene(GameTestHelper h){
        for(int x=0;x<32;x++)for(int z=0;z<32;z++){h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<20;y++)h.setBlock(x,y,z,Blocks.AIR);}
        var a=EncounterEntities.CREATURES.get(EncounterKind.FLYER).get().create(h.getLevel());a.setPos(h.absoluteVec(new Vec3(14,6,7)));a.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(.25);a.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);a.setPersistenceRequired();h.getLevel().addFreshEntity(a);
        var v=h.spawn(EntityType.COW,new Vec3(14,2,20));v.setNoAi(true);v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);v.setHealth(2000);v.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);return new Scene(a,v);
    }
    private static void start(GameTestHelper h,Scene s){h.assertTrue(s.a.begin(EncounterMove.DIVE,s.v),"begin dive stage="+s.a.flyerStage()+" ticks="+s.a.tickCount);}
    private static void end(GameTestHelper h,Scene s){s.a.discard();s.v.discard();h.succeed();}
    @GameTest(template="blackwatch_facility_arena",batch="flyer_hit",timeoutTicks=135)
    public static void divesIntoVisibleTargetOnce(GameTestHelper h){
        var s=scene(h);h.runAfterDelay(25,()->start(h,s));
        h.runAfterDelay(38,()->h.assertTrue(s.v.getHealth()==2000,"windup cannot damage"));
        h.runAfterDelay(110,()->{h.assertTrue(Math.abs(s.v.getHealth()-1991.95)<.03&&s.a.flight().hits()==1,"one accepted body hit; hp="+s.v.getHealth()+" stage="+s.a.flyerStage()+" at="+s.a.position()+" collisions="+s.a.flight().collisions());h.assertTrue(s.a.move()==EncounterMove.NONE,"no attack during pullout");end(h,s);});
    }
    @GameTest(template="blackwatch_facility_arena",batch="flyer_dodge",timeoutTicks=135)
    public static void lateralDodgeEscapesLockedPath(GameTestHelper h){var s=scene(h);h.runAfterDelay(25,()->start(h,s));h.runAfterDelay(43,()->s.v.setPos(s.v.position().add(7,0,0)));h.runAfterDelay(110,()->{h.assertTrue(s.v.getHealth()==2000&&s.a.flight().hits()==0,"no tracking after commit or range-cone hit");end(h,s);});}
    @GameTest(template="blackwatch_facility_arena",batch="flyer_wall",timeoutTicks=135)
    public static void newlyPlacedWallBlocksDive(GameTestHelper h){var s=scene(h);h.runAfterDelay(25,()->start(h,s));h.runAfterDelay(43,()->{for(int x=8;x<=20;x++)for(int y=2;y<=14;y++)h.setBlock(x,y,16,Blocks.STONE);});h.runAfterDelay(100,()->{h.assertTrue(s.v.getHealth()==2000&&s.a.flight().hits()==0&&s.a.flight().collisions()==1,"wall contact once, enemy shielded, collisions="+s.a.flight().collisions());end(h,s);});}
    @GameTest(template="blackwatch_facility_arena",batch="flyer_hurt",timeoutTicks=135)
    public static void incomingHitInterruptsDive(GameTestHelper h){var s=scene(h);h.runAfterDelay(25,()->start(h,s));h.runAfterDelay(43,()->{h.assertTrue(s.a.hurt(s.a.damageSources().mobAttack(s.v),2),"incoming damage accepted");h.assertTrue(s.a.move()==EncounterMove.NONE&&(s.a.flyerStage()==FlyerMotion.HIT_FRONT||s.a.flyerStage()==FlyerMotion.HIT_BACK),"source hurt animation interrupts attack");});h.runAfterDelay(110,()->{h.assertTrue(s.v.getHealth()==2000&&s.a.flight().hits()==0,"no delayed dive after interruption");end(h,s);});}
    @GameTest(template="blackwatch_facility_arena",batch="flyer_cancel",timeoutTicks=135)
    public static void noAiCancelsDamage(GameTestHelper h){var s=scene(h);h.runAfterDelay(25,()->start(h,s));h.runAfterDelay(43,()->s.a.setNoAi(true));h.runAfterDelay(110,()->{h.assertTrue(s.v.getHealth()==2000&&s.a.flyerStage()==FlyerMotion.IDLE&&s.a.move()==EncounterMove.NONE,"NoAI cancels flight contact");end(h,s);});}
    @GameTest(template="blackwatch_facility_arena",batch="flyer_immune",timeoutTicks=135)
    public static void immuneVictimGetsNoAcceptedHit(GameTestHelper h){var s=scene(h);s.v.setInvulnerable(true);h.runAfterDelay(25,()->start(h,s));h.runAfterDelay(110,()->{h.assertTrue(s.v.getHealth()==2000&&s.a.flight().hits()==0&&s.a.flight().lastContact()!=null,"immune contact stops flight without accepted damage");end(h,s);});}
    @GameTest(template="blackwatch_facility_arena",batch="flyer_roster",timeoutTicks=45)
    public static void noUnverifiedSpitAndRuntimeAttributeDamage(GameTestHelper h){var s=scene(h);h.assertTrue(!EncounterKind.FLYER.permits(EncounterMove.VOLLEY),"source dive replaces unverified spit");s.a.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20);h.assertTrue(s.a.flyerStrike(s.v,s.v.getBoundingBox().getCenter(),new Vec3(0,0,1)),"native hurt");h.assertTrue(Math.abs(s.v.getHealth()-1977)<.02,"runtime attack attribute used");end(h,s);}
    @GameTest(template="blackwatch_facility_arena",batch="flyer_consume",timeoutTicks=135)
    public static void activeConsumeStateYieldsPoseAndCancelsContact(GameTestHelper h){var s=scene(h);h.runAfterDelay(25,()->start(h,s));h.runAfterDelay(43,()->{
        ModAttachments.setData(s.a,ModAttachments.CONSUME_VISUAL,new ConsumeVisual(s.v.getId(),h.getLevel().getGameTime(),100,0,ConsumeVisual.HOLD));h.assertTrue(!s.a.sourcePoseCommitted(),"grip pose wins over source flight");
        // Exercise the actor's consume-state boundary before the global consumer
        // manager correctly rejects this synthetic non-player holder.
        s.a.tick();h.assertTrue(s.a.move()==EncounterMove.NONE&&s.a.flyerStage()==FlyerMotion.IDLE,"active consume state cancels actor contact");ModAttachments.setData(s.a,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY);
    });h.runAfterDelay(105,()->{h.assertTrue(s.v.getHealth()==2000&&s.a.move()==EncounterMove.NONE,"no queued hit after consume interrupt");end(h,s);});}
    @GameTest(template="blackwatch_facility_arena",batch="flyer_reload",timeoutTicks=80)
    public static void reloadedDiveCannotResumeDamage(GameTestHelper h){var s=scene(h);h.runAfterDelay(25,()->start(h,s));h.runAfterDelay(43,()->{var tag=new net.minecraft.nbt.CompoundTag();s.a.addAdditionalSaveData(tag);var copy=EncounterEntities.CREATURES.get(EncounterKind.FLYER).get().create(h.getLevel());copy.readAdditionalSaveData(tag);h.assertTrue(copy.flyerStage()==FlyerMotion.IDLE&&copy.move()==EncounterMove.NONE&&!copy.moveReady(EncounterMove.DIVE),"recharge persists, live dive does not");copy.discard();end(h,s);});}
}
