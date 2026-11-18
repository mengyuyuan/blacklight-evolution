package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="blacklight")
public final class StalkerContactGameTest {
    private record Hit(int tick,String bone,Vec3 point){}
    private static final Map<UUID,List<Hit>> HITS=new HashMap<>();
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void hurt(com.codex.blacklight.combat.ForgeDamageEvent.Post e){
        if(e.getNewDamage()>0&&e.getSource().getEntity() instanceof BlacklightMutant m&&HITS.containsKey(m.getUUID())){
            var c=m.clawContact();if(c==null)throw new IllegalStateException("Stalker damage without a physical claw");
            HITS.get(m.getUUID()).add(new Hit((int)m.attackAge(0),c.bone(),c.point()));
        }
    }
    private record Scene(BlacklightMutant m,Mob v,Vec3 origin){}
    private static Scene scene(GameTestHelper h,double distance){
        h.getLevel().setDayTime(18000);for(int x=0;x<16;x++)for(int z=0;z<16;z++){h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<10;y++)h.setBlock(x,y,z,Blocks.AIR);}
        var m=ModEntities.BLACKLIGHT_STALKER.get().create(h.getLevel());m.setPos(h.absoluteVec(new Vec3(7.5,2,4.5)));m.setPersistenceRequired();m.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);m.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(.05);h.getLevel().addFreshEntity(m);
        var v=EntityType.COW.create(h.getLevel());v.setPos(m.position().add(0,0,distance));v.setNoAi(true);v.setPersistenceRequired();h.getLevel().addFreshEntity(v);v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);v.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);v.setHealth(1000);
        HITS.put(m.getUUID(),new ArrayList<>());return new Scene(m,v,m.position());
    }
    private static void begin(GameTestHelper h,Scene s,MutantMove move){h.runAfterDelay(2,()->h.assertTrue(s.m.beginAttack(move,s.v),"starts "+move));}
    private static void done(GameTestHelper h,Scene s){HITS.remove(s.m.getUUID());s.m.discard();s.v.discard();h.succeed();}
    @GameTest(template="blackwatch_arena",batch="stalker_claw",timeoutTicks=50) public static void slashUsesRightDigitsOnce(GameTestHelper h){var s=scene(h,1.25);begin(h,s,MutantMove.STALKER_CLAW);h.runAfterDelay(30,()->{
        var hits=HITS.get(s.m.getUUID());h.assertTrue(hits.size()==1,"one slash "+hits);h.assertTrue(hits.get(0).bone.equals("right_arm")&&hits.get(0).tick>=11&&hits.get(0).tick<=16,"right claw in active sweep");h.assertTrue(s.v.getHealth()<1000,"real damage");done(h,s);
    });}
    @GameTest(template="blackwatch_arena",batch="stalker_double",timeoutTicks=60) public static void comboUsesSeparateRightAndLeftContacts(GameTestHelper h){var s=scene(h,1.25);begin(h,s,MutantMove.STALKER_DOUBLE);h.runAfterDelay(43,()->{
        var hits=HITS.get(s.m.getUUID());h.assertTrue(hits.size()==2,"two separate hand contacts "+hits);h.assertTrue(hits.get(0).bone.equals("right_arm")&&hits.get(1).bone.equals("left_arm"),"right then left");h.assertTrue(hits.get(0).tick>=9&&hits.get(0).tick<=14&&hits.get(1).tick>=23&&hits.get(1).tick<=28,"authored windows");done(h,s);
    });}
    @GameTest(template="blackwatch_arena",batch="stalker_range",timeoutTicks=45) public static void oldConeOutsideClawsDoesNotStartAttack(GameTestHelper h){var s=scene(h,2.1);h.runAfterDelay(2,()->h.assertTrue(!s.m.beginAttack(MutantMove.STALKER_CLAW,s.v),"cannot reach old cone victim"));h.runAfterDelay(30,()->{h.assertTrue(s.v.getHealth()==1000,"no invisible reach");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="stalker_dodge",timeoutTicks=50) public static void committedSlashCanBeDodged(GameTestHelper h){var s=scene(h,1.25);begin(h,s,MutantMove.STALKER_CLAW);h.runAfterDelay(8,()->s.v.setPos(s.v.position().add(3,0,0)));h.runAfterDelay(30,()->{h.assertTrue(HITS.get(s.m.getUUID()).isEmpty()&&s.v.getHealth()==1000,"claw does not track moved target");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="stalker_wall",timeoutTicks=50) public static void newWallBlocksClaw(GameTestHelper h){
        var original=scene(h,1.4);original.v.discard();var v=EntityType.VILLAGER.create(h.getLevel());v.setPos(original.m.position().add(0,0,1.4));v.setNoAi(true);h.getLevel().addFreshEntity(v);v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);v.setHealth(1000);var s=new Scene(original.m,v,original.origin);
        begin(h,s,MutantMove.STALKER_CLAW);h.runAfterDelay(7,()->{for(int x=5;x<10;x++)for(int y=2;y<6;y++)h.setBlock(x,y,5,Blocks.IRON_BARS);h.assertTrue(!h.getLevel().getBlockCollisions(v,v.getBoundingBox()).iterator().hasNext(),"barrier fixture must not suffocate the victim");});
        h.runAfterDelay(30,()->{h.assertTrue(HITS.get(s.m.getUUID()).isEmpty()&&s.v.getHealth()==1000,"no through-wall claw: "+HITS.get(s.m.getUUID())+" hp="+s.v.getHealth());done(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="stalker_pounce",timeoutTicks=65) public static void pounceLeavesGroundHitsOnceAndLands(GameTestHelper h){var s=scene(h,5);begin(h,s,MutantMove.STALKER_POUNCE);double[] peak={s.origin.y};h.onEachTick(()->peak[0]=Math.max(peak[0],s.m.getY()));h.runAfterDelay(43,()->{
        h.assertTrue(peak[0]>s.origin.y+.8,"real airborne apex "+peak[0]);var hits=HITS.get(s.m.getUUID());h.assertTrue(hits.size()==1,"one pounce claw contact "+hits);h.assertTrue(s.m.groundImpactPoint()!=null&&s.m.onGround()&&Math.abs(s.m.getY()-s.origin.y)<.01,"actual landing feedback and ground");h.assertTrue(s.m.getZ()>s.origin.z+3,"committed flight advances body");done(h,s);
    });}
    @GameTest(template="blackwatch_arena",batch="stalker_pouncedodge",timeoutTicks=65) public static void pounceKeepsCommittedDirectionWhenTargetDodges(GameTestHelper h){var s=scene(h,5);begin(h,s,MutantMove.STALKER_POUNCE);h.runAfterDelay(14,()->s.v.setPos(s.v.position().add(3,0,0)));h.runAfterDelay(43,()->{h.assertTrue(HITS.get(s.m.getUUID()).isEmpty()&&s.v.getHealth()==1000,"pounce does not home");h.assertTrue(s.m.groundImpactPoint()!=null&&Math.abs(s.m.getX()-s.origin.x)<.1,"miss still lands along original direction");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="stalker_pouncewall",timeoutTicks=65) public static void wallStopsPounceAndFutureContacts(GameTestHelper h){var s=scene(h,5);begin(h,s,MutantMove.STALKER_POUNCE);h.runAfterDelay(8,()->{for(int x=4;x<12;x++)for(int y=2;y<7;y++)h.setBlock(x,y,7,Blocks.STONE);});h.runAfterDelay(43,()->{
        h.assertTrue(HITS.get(s.m.getUUID()).isEmpty()&&s.v.getHealth()==1000,"wall blocks pounce damage");h.assertTrue(s.m.getBoundingBox().maxZ<=h.absoluteVec(new Vec3(0,0,7)).z+.001,"body did not tunnel");h.assertTrue(s.m.groundImpactPoint()!=null,"collision still ends in real landing");done(h,s);
    });}
    @GameTest(template="blackwatch_arena",batch="stalker_interrupt",timeoutTicks=65) public static void disabledAiCancelsAirborneAttack(GameTestHelper h){var s=scene(h,5);begin(h,s,MutantMove.STALKER_POUNCE);h.runAfterDelay(14,()->s.m.setNoAi(true));h.runAfterDelay(43,()->{h.assertTrue(!s.m.committedAttack()&&s.v.getHealth()==1000,"airborne interruption owns future contact");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="stalker_ledge",timeoutTicks=40) public static void pounceRequiresSupportedLandingArea(GameTestHelper h){var s=scene(h,5);for(int x=5;x<11;x++)for(int z=8;z<12;z++)for(int y=0;y<2;y++)h.setBlock(x,y,z,Blocks.AIR);h.runAfterDelay(2,()->{h.assertTrue(!s.m.beginAttack(MutantMove.STALKER_POUNCE,s.v),"no unsupported leap");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="stalker_air",timeoutTicks=40) public static void cannotLaunchAgainWhileAirborne(GameTestHelper h){var s=scene(h,5);s.m.setPos(s.m.position().add(0,1.5,0));s.m.setNoGravity(true);h.runAfterDelay(2,()->{h.assertTrue(!s.m.beginAttack(MutantMove.STALKER_POUNCE,s.v),"no air jump");done(h,s);});}
}
