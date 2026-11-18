package com.codex.blacklight.gametest;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="blacklight")
public final class BruteContactGameTest {
    private record Hit(int age,String bone,Vec3 at){}
    private static final Map<UUID,List<Hit>> HITS=new HashMap<>();
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void damage(com.codex.blacklight.combat.ForgeDamageEvent.Post e){
        if(e.getNewDamage()>0&&e.getSource().getEntity() instanceof BlacklightMutant m&&HITS.containsKey(m.getUUID())){
            var hit=m.bruteContact();if(hit==null)throw new IllegalStateException("Brute damage without contact");HITS.get(m.getUUID()).add(new Hit((int)m.attackAge(0),hit.bone(),hit.point()));
        }
    }
    private record Scene(BlacklightMutant m,Mob v,Vec3 origin){}
    private static Scene scene(GameTestHelper h,double distance){
        h.getLevel().setDayTime(18000);for(int x=0;x<16;x++)for(int z=0;z<16;z++){h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<11;y++)h.setBlock(x,y,z,Blocks.AIR);}
        var m=ModEntities.BLACKLIGHT_BRUTE.get().create(h.getLevel());m.setPos(h.absoluteVec(new Vec3(7.5,2,4.5)));m.setPersistenceRequired();m.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);m.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(.05);h.getLevel().addFreshEntity(m);
        var v=EntityType.COW.create(h.getLevel());v.setPos(m.position().add(0,0,distance));v.setNoAi(true);v.setPersistenceRequired();h.getLevel().addFreshEntity(v);v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);v.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);v.setHealth(1000);v.getPersistentData().putLong(com.codex.blacklight.infection.InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);
        HITS.put(m.getUUID(),new ArrayList<>());return new Scene(m,v,m.position());
    }
    private static void begin(GameTestHelper h,Scene s,MutantMove move){h.runAfterDelay(2,()->h.assertTrue(s.m.beginAttack(move,s.v),"starts "+move+" reach="+BruteContact.canReach(move,s.m.position(),0,s.v.getBoundingBox())));}
    private static void checkGroundRecovery(GameTestHelper h,Scene s,boolean slab){
        var checks=new java.util.concurrent.atomic.AtomicInteger();
        var floor=new AABB(s.origin.add(-4,-1,-4),s.origin.add(4,0,4));
        var raised=new AABB(s.origin.add(-1.5,0,.5),s.origin.add(1.5,.5,1.5));
        for(int t=27;t<=65;t++){final int tick=t;h.runAfterDelay(t,()->{
            if(!s.m.groundRecovery())return;checks.incrementAndGet();
            for(var part:BruteContact.body(s.m.attackPose(0),s.m.position(),s.m.getYRot())){
                h.assertTrue(!part.box().touches(floor.deflate(.004)),"recovery floor penetration tick="+tick+" bone="+part.bone());
                if(slab)h.assertTrue(!part.box().touches(raised.deflate(.004)),"recovery slab penetration tick="+tick+" bone="+part.bone());
                if(part.fist())h.assertTrue(part.box().center.y<s.origin.y+2.3,"no repeated overhead windup");
            }
        });}
        h.runAfterDelay(70,()->{h.assertTrue(checks.get()>=10,"ground recovery was sampled");h.assertTrue(!s.m.committedAttack(),"ground recovery returns control");});
    }
    private static void done(GameTestHelper h,Scene s){HITS.remove(s.m.getUUID());s.m.discard();s.v.discard();h.succeed();}
    @GameTest(template="blackwatch_arena",batch="brute_hammer",timeoutTicks=65) public static void hammerUsesRightFistOnce(GameTestHelper h){var s=scene(h,1.65);begin(h,s,MutantMove.BRUTE_HAMMER);h.runAfterDelay(40,()->{var hits=HITS.get(s.m.getUUID());h.assertTrue(hits.size()==1&&hits.get(0).bone.equals("right_forearm"),"one right fist "+hits);h.assertTrue(hits.get(0).age>=16&&hits.get(0).age<=22,"contact on descent");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="brute_range",timeoutTicks=40) public static void hammerRejectsOldInvisibleReach(GameTestHelper h){var s=scene(h,2.8);h.runAfterDelay(2,()->{h.assertTrue(!s.m.beginAttack(MutantMove.BRUTE_HAMMER,s.v),"old cone out of fist range");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="brute_dodge",timeoutTicks=65) public static void dodgedHammerCannotTrackVictim(GameTestHelper h){var s=scene(h,1.65);begin(h,s,MutantMove.BRUTE_HAMMER);h.runAfterDelay(12,()->s.v.setPos(s.v.position().add(3,0,0)));h.runAfterDelay(40,()->{h.assertTrue(HITS.get(s.m.getUUID()).isEmpty()&&s.v.getHealth()==1000,"committed fist misses moved target");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="brute_hammerwall",timeoutTicks=90) public static void newBarsStopFistAndRewind(GameTestHelper h){var s=scene(h,1.65);begin(h,s,MutantMove.BRUTE_HAMMER);h.runAfterDelay(9,()->{for(int x=4;x<11;x++)for(int y=2;y<8;y++)h.setBlock(x,y,5,Blocks.IRON_BARS);});h.runAfterDelay(65,()->{h.assertTrue(s.v.getHealth()==1000&&s.m.wallImpacts()==1&&!s.m.committedAttack(),"one obstacle, no damage, complete recoil: hits="+HITS.get(s.m.getUUID())+" walls="+s.m.wallImpacts());done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="brute_slam",timeoutTicks=100) public static void slamStartsAtActualForwardFistGroundContact(GameTestHelper h){var s=scene(h,3);checkGroundRecovery(h,s,false);begin(h,s,MutantMove.BRUTE_SLAM);h.runAfterDelay(23,()->h.assertTrue(s.m.groundImpactPoint()==null&&s.v.getHealth()==1000,"raised fists have no wave"));h.runAfterDelay(75,()->{var at=s.m.groundImpactPoint();h.assertTrue(at!=null&&Math.abs(at.y-s.origin.y)<.02&&at.z>s.origin.z+.3,"forward fist contact, not feet: "+at);var hits=HITS.get(s.m.getUUID());h.assertTrue(hits.size()==1&&hits.get(0).bone.equals("ground_wave"),"supported shock hits once "+hits);done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="brute_slamledger",timeoutTicks=100) public static void fistAndWaveShareOneVictimBudget(GameTestHelper h){var s=scene(h,1.65);begin(h,s,MutantMove.BRUTE_SLAM);h.runAfterDelay(75,()->{h.assertTrue(HITS.get(s.m.getUUID()).size()==1&&s.v.getHealth()<1000&&s.m.groundImpactPoint()!=null,"fists and ground share accepted blow "+HITS.get(s.m.getUUID()));done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="brute_air",timeoutTicks=85) public static void airborneSlamCannotGenerateDistantGroundDamage(GameTestHelper h){var s=scene(h,3);begin(h,s,MutantMove.BRUTE_SLAM);h.runAfterDelay(12,()->{s.m.setPos(s.origin.add(0,4,0));s.m.setNoGravity(true);});h.runAfterDelay(65,()->{h.assertTrue(s.v.getHealth()==1000&&s.m.groundImpactPoint()==null,"no fictitious ground contact");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="brute_wavecover",timeoutTicks=100) public static void groundWaveCannotCrossThinCover(GameTestHelper h){var s=scene(h,3.55);begin(h,s,MutantMove.BRUTE_SLAM);h.runAfterDelay(12,()->{for(int x=3;x<13;x++)for(int y=2;y<8;y++)h.setBlock(x,y,7,Blocks.IRON_BARS);});h.runAfterDelay(75,()->{h.assertTrue(s.m.groundImpactPoint()!=null&&s.v.getHealth()==1000,"real ground contact but no wave through cover: "+s.m.groundImpactPoint());done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="brute_slab",timeoutTicks=100) public static void fistsLandOnSlabTop(GameTestHelper h){var s=scene(h,3);checkGroundRecovery(h,s,true);begin(h,s,MutantMove.BRUTE_SLAM);h.runAfterDelay(12,()->{for(int x=6;x<=8;x++)h.setBlock(x,2,5,Blocks.STONE_SLAB);});h.runAfterDelay(75,()->{var at=s.m.groundImpactPoint();h.assertTrue(at!=null&&Math.abs(at.y-s.origin.y-.5)<.02,"actual slab collision height: "+at+" walls="+s.m.wallImpacts());done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="brute_charge",timeoutTicks=75) public static void chargeDamagesOnlyOnceOnVisibleBodyContact(GameTestHelper h){var s=scene(h,5.5);begin(h,s,MutantMove.BRUTE_CHARGE);h.runAfterDelay(52,()->{var hits=HITS.get(s.m.getUUID());h.assertTrue(hits.size()==1&&hits.get(0).age>=12&&hits.get(0).age<=27,"one moving surface hit "+hits);h.assertTrue(s.m.getZ()>s.origin.z+3,"real drive");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="brute_chargewall",timeoutTicks=100) public static void chargeMeshStopsBeforeWallAndRewinds(GameTestHelper h){var s=scene(h,6);begin(h,s,MutantMove.BRUTE_CHARGE);var corner=h.absoluteVec(new Vec3(3,2,8));var wall=new AABB(corner,corner.add(9,7,1));h.runAfterDelay(10,()->{for(int x=3;x<12;x++)for(int y=2;y<9;y++)h.setBlock(x,y,8,Blocks.STONE);});
        for(int t=11;t<=70;t++){final int tick=t;h.runAfterDelay(t,()->{if(s.m.committedAttack())h.assertTrue(BruteContact.body(s.m.attackMove(),s.m.attackPoseAge(0),s.m.position(),s.m.getYRot()).stream().noneMatch(part->part.box().touches(wall.deflate(.005))),"visible charge cannot cross wall tick="+tick+" age="+s.m.attackPoseAge(0)+" origin="+s.m.position());});}
        h.runAfterDelay(75,()->{h.assertTrue(s.m.wallImpacts()==1&&s.v.getHealth()==1000&&!s.m.committedAttack(),"wall recoil retires charge");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="brute_chargedodge",timeoutTicks=75) public static void chargeKeepsOriginalDirection(GameTestHelper h){var s=scene(h,5.5);begin(h,s,MutantMove.BRUTE_CHARGE);h.runAfterDelay(16,()->s.v.setPos(s.v.position().add(4,0,0)));h.runAfterDelay(52,()->{h.assertTrue(s.v.getHealth()==1000&&Math.abs(s.m.getX()-s.origin.x)<.1,"charge never homes");done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="brute_cliff",timeoutTicks=100) public static void chargeStopsBeforeUnsupportedGround(GameTestHelper h){var s=scene(h,6);begin(h,s,MutantMove.BRUTE_CHARGE);h.runAfterDelay(9,()->{for(int x=0;x<16;x++)for(int z=7;z<16;z++)for(int y=0;y<2;y++)h.setBlock(x,y,z,Blocks.AIR);});h.runAfterDelay(75,()->{h.assertTrue(Math.abs(s.m.getY()-s.origin.y)<.02&&s.m.getBoundingBox().maxZ<=h.absoluteVec(new Vec3(0,0,7)).z+.14&&s.v.getHealth()==1000,"no blind charge over ledge: "+s.m.position());done(h,s);});}
    @GameTest(template="blackwatch_arena",batch="brute_cancel",timeoutTicks=75) public static void interruptionCancelsPendingSlamAndDamage(GameTestHelper h){var s=scene(h,3);begin(h,s,MutantMove.BRUTE_SLAM);h.runAfterDelay(20,()->s.m.setNoAi(true));h.runAfterDelay(60,()->{h.assertTrue(s.v.getHealth()==1000&&s.m.groundImpactPoint()==null&&!s.m.committedAttack(),"cancelled action has no delayed wave");done(h,s);});}

    private static void standingHit(GameTestHelper h,Scene s,int kind){
        h.assertTrue(s.m.hurt(s.m.damageSources().generic(),2),"accepted incoming damage");
        h.assertTrue(ImpactControl.begin(s.m,new ImpactProfile(kind,ImpactProfile.RIGHT,8,0,0,false),180),"standing impact accepted");
        h.assertTrue(!s.m.committedAttack()&&!s.m.isNoAi(),"impact immediately cancels attack without NoAI");
    }
    @GameTest(template="blackwatch_arena",batch="brute_hurt_hammer",timeoutTicks=100) public static void hurtHammerCancelsAndCanStartAnotherAttack(GameTestHelper h){
        var s=scene(h,1.65);begin(h,s,MutantMove.BRUTE_HAMMER);
        h.runAfterDelay(12,()->standingHit(h,s,HitReaction.STAGGER));
        h.runAfterDelay(38,()->{h.assertTrue(HITS.get(s.m.getUUID()).isEmpty()&&s.v.getHealth()==1000,"interrupted hammer has no late damage");h.assertTrue(!ImpactControl.stunned(s.m)&&s.m.beginAttack(MutantMove.BRUTE_HAMMER,s.v),"recovers and accepts next action");});
        h.runAfterDelay(80,()->{h.assertTrue(HITS.get(s.m.getUUID()).size()==1,"only new hammer damages");done(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="brute_hurt_slam",timeoutTicks=90) public static void hurtSlamCannotEmitADeferredWave(GameTestHelper h){
        var s=scene(h,3);begin(h,s,MutantMove.BRUTE_SLAM);h.runAfterDelay(19,()->standingHit(h,s,HitReaction.HEAVY));
        h.runAfterDelay(75,()->{h.assertTrue(s.m.groundImpactPoint()==null&&s.v.getHealth()==1000,"no ground effect after interrupted windup");done(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="brute_hurt_charge",timeoutTicks=90) public static void hurtChargeStopsItsDrive(GameTestHelper h){
        var s=scene(h,5.5);var stop=new Vec3[1];begin(h,s,MutantMove.BRUTE_CHARGE);
        h.runAfterDelay(16,()->{standingHit(h,s,HitReaction.HEAVY);stop[0]=s.m.position();});
        h.runAfterDelay(55,()->{h.assertTrue(s.m.position().distanceTo(stop[0])<.15&&s.v.getHealth()==1000,"canceled charge has no drive or damage");done(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="brute_hurt_repeat",timeoutTicks=70) public static void repeatedStandingHitUsesLatestDeadline(GameTestHelper h){
        var s=scene(h,1.65);begin(h,s,MutantMove.BRUTE_HAMMER);
        h.runAfterDelay(12,()->standingHit(h,s,HitReaction.STAGGER));
        h.runAfterDelay(16,()->{s.m.invulnerableTime=0;standingHit(h,s,HitReaction.STAGGER);});
        h.runAfterDelay(22,()->h.assertTrue(ImpactControl.stunned(s.m),"second impact still owns recovery"));
        h.runAfterDelay(30,()->{h.assertTrue(!ImpactControl.stunned(s.m)&&!s.m.isNoAi()&&s.v.getHealth()==1000,"control expires without delayed damage");done(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="brute_hurt_recovery",timeoutTicks=100) public static void hurtGroundRecoveryDoesNotRepeatItsWave(GameTestHelper h){
        var s=scene(h,3);begin(h,s,MutantMove.BRUTE_SLAM);
        h.runAfterDelay(30,()->{h.assertTrue(s.m.groundRecovery(),"landed recovery fixture");standingHit(h,s,HitReaction.STAGGER);});
        h.runAfterDelay(80,()->{h.assertTrue(HITS.get(s.m.getUUID()).size()==1&&s.v.getHealth()==987.5F,"original wave only");done(h,s);});
    }
}
