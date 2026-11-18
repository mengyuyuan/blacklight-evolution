package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.combat.*;
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
public final class CreatureSkillsGameTest {
    private static final Map<UUID,List<Integer>> HITS=new HashMap<>(),SHOTS=new HashMap<>();
    private static final Map<UUID,List<String>> COMMON=new HashMap<>();
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void hurt(com.codex.blacklight.combat.ForgeDamageEvent.Post e){
        if(e.getNewDamage()>0&&e.getSource().getEntity() instanceof BlacklightCommonInfected m){
            var values=COMMON.get(m.getUUID());if(values!=null)values.add(m.attackHand()+":"+(m.level().getGameTime()-m.attackStarted()));
        }
        if(e.getNewDamage()>0&&e.getSource().getEntity() instanceof BlacklightMutant m){
            var values=HITS.get(m.getUUID());if(values!=null)values.add((int)m.attackAge(0));
        }
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void join(net.minecraftforge.event.entity.EntityJoinLevelEvent e){
        if(e.getEntity() instanceof BlacklightSpit s&&s.getOwner() instanceof BlacklightMutant m){
            var values=SHOTS.get(m.getUUID());if(values!=null)values.add((int)m.attackAge(0));
        }
    }
    private static void arena(GameTestHelper h){
        h.getLevel().setDayTime(18000);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<10;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
    }
    private static BlacklightMutant actor(GameTestHelper h,MutantMove move){
        var type=switch(move.species){case "STALKER"->ModEntities.BLACKLIGHT_STALKER;case "SPITTER"->ModEntities.BLACKLIGHT_SPITTER;default->ModEntities.BLACKLIGHT_BRUTE;};
        var m=type.get().create(h.getLevel());m.setPos(h.absoluteVec(new Vec3(7.5,2,4.5)));m.setPersistenceRequired();
        m.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);m.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1);
        h.assertTrue(h.getLevel().addFreshEntity(m),"actor joins server");
        HITS.put(m.getUUID(),new ArrayList<>());SHOTS.put(m.getUUID(),new ArrayList<>());return m;
    }
    private static Mob target(GameTestHelper h,double z){
        var m=EntityType.COW.create(h.getLevel());m.setPos(h.absoluteVec(new Vec3(7.5,2,z)));m.setNoAi(true);m.setPersistenceRequired();
        h.getLevel().addFreshEntity(m);m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);m.setHealth(1000);
        m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        // Isolate contact clocks from random ecological conversion in these fixtures.
        m.getPersistentData().putLong(com.codex.blacklight.infection.InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);return m;
    }
    private static void done(GameTestHelper h,BlacklightMutant m,Entity... others){
        HITS.remove(m.getUUID());SHOTS.remove(m.getUUID());m.discard();for(var e:others)e.discard();h.succeed();
    }
    private static void move(GameTestHelper h,MutantMove move,int... contacts){
        arena(h);var m=actor(h,move);
        double range=move.projectile()?9:move==MutantMove.STALKER_POUNCE?5:move==MutantMove.BRUTE_CHARGE?6:move==MutantMove.BRUTE_HAMMER?1.65:move.species.equals("STALKER")?1.25:2;
        var v=target(h,4.5+range);var start=m.position();m.setTarget(v);
        h.assertTrue(m.beginAttack(move,v),"starts "+move);
        h.runAfterDelay(move.ticks,()->{
            var actual=(move.projectile()?SHOTS:HITS).get(m.getUUID());
            if(move.species.equals("STALKER")){
                int[][] windows=switch(move){case STALKER_CLAW->new int[][]{{11,16}};case STALKER_DOUBLE->new int[][]{{9,14},{23,28}};default->new int[][]{{14,23}};};
                h.assertTrue(actual.size()==windows.length,move+" physical contact count "+actual);
                for(int i=0;i<windows.length;i++)h.assertTrue(actual.get(i)>=windows[i][0]&&actual.get(i)<=windows[i][1],move+" sharp-contact window "+actual);
            }else if(move.species.equals("BRUTE")){
                int low=move==MutantMove.BRUTE_HAMMER?16:20,high=move==MutantMove.BRUTE_HAMMER?22:28;
                h.assertTrue(actual.size()==1&&actual.get(0)>=low&&actual.get(0)<=high,move+" physical contact age "+actual);
            }else h.assertTrue(actual.equals(Arrays.stream(contacts).boxed().toList()),move+" contact ages "+actual);
            if(move.driving(move.driveStart))h.assertTrue(m.position().distanceToSqr(start)>1,"drive moves real body: "+move);
            if(move==MutantMove.SPITTER_RETREAT)h.assertTrue(v.getHealth()==1000&&m.getZ()<start.z,"retreat is defensive");
            if(move.projectile())h.assertTrue(v.getHealth()<1000,"spit trajectory reaches a living non-player at nine blocks");
            done(h,m,v);
        });
    }
    @GameTest(template="blackwatch_arena",batch="claw",timeoutTicks=60) public static void claw(GameTestHelper h){move(h,MutantMove.STALKER_CLAW);}
    @GameTest(template="blackwatch_arena",batch="pounce",timeoutTicks=65) public static void pounce(GameTestHelper h){move(h,MutantMove.STALKER_POUNCE);}
    @GameTest(template="blackwatch_arena",batch="double",timeoutTicks=65) public static void doubleClaw(GameTestHelper h){move(h,MutantMove.STALKER_DOUBLE);}
    @GameTest(template="blackwatch_arena",batch="spit",timeoutTicks=65) public static void spit(GameTestHelper h){move(h,MutantMove.SPITTER_SHOT,14);}
    @GameTest(template="blackwatch_arena",batch="volley",timeoutTicks=70) public static void volley(GameTestHelper h){move(h,MutantMove.SPITTER_VOLLEY,14,24,34);}
    @GameTest(template="blackwatch_arena",batch="retreat",timeoutTicks=60) public static void retreat(GameTestHelper h){move(h,MutantMove.SPITTER_RETREAT);}
    @GameTest(template="blackwatch_arena",batch="hammer",timeoutTicks=65) public static void hammer(GameTestHelper h){move(h,MutantMove.BRUTE_HAMMER,18);}
    @GameTest(template="blackwatch_arena",batch="slam",timeoutTicks=70) public static void slam(GameTestHelper h){move(h,MutantMove.BRUTE_SLAM,24);}
    @GameTest(template="blackwatch_arena",batch="charge",timeoutTicks=70) public static void charge(GameTestHelper h){
        arena(h);var m=actor(h,MutantMove.BRUTE_CHARGE);var v=target(h,10.5);m.setTarget(v);
        h.assertTrue(m.beginAttack(MutantMove.BRUTE_CHARGE,v),"charge begins");
        h.runAfterDelay(46,()->{var hits=HITS.get(m.getUUID());h.assertTrue(hits.size()==1&&hits.get(0)>=14&&hits.get(0)<=26,"charge once in contact window "+hits);done(h,m,v);});
    }
    @GameTest(template="blackwatch_arena",batch="wall",timeoutTicks=60) public static void wallAndBehindAreSafe(GameTestHelper h){
        arena(h);var m=actor(h,MutantMove.STALKER_POUNCE);var front=target(h,9.5);var back=target(h,2.5);m.setTarget(front);
        h.assertTrue(m.beginAttack(MutantMove.STALKER_POUNCE,front),"clear sight starts attack");
        for(int x=4;x<11;x++)for(int y=2;y<8;y++)h.setBlock(x,y,7,Blocks.STONE);
        h.runAfterDelay(36,()->{h.assertTrue(front.getHealth()==1000&&back.getHealth()==1000,"wall blocks pounce and claws exclude rear");done(h,m,front,back);});
    }
    @GameTest(template="blackwatch_arena",batch="interrupt",timeoutTicks=60) public static void captureCancelsPendingContacts(GameTestHelper h){
        arena(h);var m=actor(h,MutantMove.STALKER_DOUBLE);var v=target(h,5.75);m.setTarget(v);
        h.assertTrue(m.beginAttack(MutantMove.STALKER_DOUBLE,v),"double starts");
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"MutantCaptureQA"));
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(m.position().add(0,0,-2));p.setYRot(0);p.setXRot(0);p.setOnGround(true);p.tickCount=100;
        var state=com.codex.blacklight.registry.ModAttachments.getData(p,ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(com.codex.blacklight.power.PowerForm.CLAWS);state.activate(com.codex.blacklight.power.PowerForm.CLAWS);
        h.runAfterDelay(5,()->{
            m.setHealth(m.getMaxHealth()*.05F);CombatTargeting.requestLock(p,m.getId());ClawCombatSystem.handle(p,com.codex.blacklight.network.ClawActionPayload.Action.GRAB);
            h.assertTrue(ConsumeSystem.active(p)&&!m.committedAttack(),"real grab cancels committed action immediately");
        });
        h.runAfterDelay(38,()->{h.assertTrue(!m.committedAttack()&&v.getHealth()==1000,"capture clears action and every future contact");ConsumeSystem.cancel(p);done(h,m,v);});
    }
    @GameTest(template="blackwatch_arena",batch="noai",timeoutTicks=60) public static void disabledAiCancelsPendingContacts(GameTestHelper h){
        arena(h);var m=actor(h,MutantMove.BRUTE_SLAM);var v=target(h,6.5);m.setTarget(v);
        h.assertTrue(m.beginAttack(MutantMove.BRUTE_SLAM,v),"slam begins");h.runAfterDelay(5,()->m.setNoAi(true));
        h.runAfterDelay(46,()->{h.assertTrue(!m.committedAttack()&&v.getHealth()==1000,"NoAI cancels slam");done(h,m,v);});
    }
    @GameTest(template="blackwatch_arena",batch="friendly",timeoutTicks=60) public static void friendlyInfectedAreNotValidTargets(GameTestHelper h){
        arena(h);var m=actor(h,MutantMove.BRUTE_SLAM);var v=target(h,6.5);
        var friendly=ModEntities.COMMON_INFECTED.get().create(h.getLevel());friendly.setPos(h.absoluteVec(new Vec3(7.5,2,2.5)));friendly.setNoAi(true);h.getLevel().addFreshEntity(friendly);
        float hp=friendly.getHealth();h.assertTrue(!m.beginAttack(MutantMove.BRUTE_SLAM,friendly),"infected ally rejected");m.setTarget(v);
        h.assertTrue(m.beginAttack(MutantMove.BRUTE_SLAM,v),"hostile accepted");
        h.runAfterDelay(46,()->{h.assertTrue(friendly.getHealth()==hp&&v.getHealth()<1000,"radial slam filters allies");done(h,m,v,friendly);});
    }
    @GameTest(template="blackwatch_arena",batch="chargewall",timeoutTicks=60) public static void chargeDoesNotTunnelThroughWall(GameTestHelper h){
        arena(h);var m=actor(h,MutantMove.BRUTE_CHARGE);var v=target(h,11.5);m.setTarget(v);
        h.assertTrue(m.beginAttack(MutantMove.BRUTE_CHARGE,v),"charge starts with sight");
        for(int x=3;x<12;x++)for(int y=2;y<9;y++)h.setBlock(x,y,8,Blocks.STONE);
        double wall=h.absoluteVec(new Vec3(0,0,8)).z;
        h.runAfterDelay(46,()->{h.assertTrue(m.getBoundingBox().maxZ<=wall+.001&&v.getHealth()==1000,"collision stops charge and blocks damage: bounds="+m.getBoundingBox()+" wall="+wall+" hp="+v.getHealth()+" target="+v.position()+" actor="+m.position());done(h,m,v);});
    }
    @GameTest(template="blackwatch_arena",batch="common",timeoutTicks=190) public static void civilianClawsAndTwoHandLungeUseTheirOwnContactFrames(GameTestHelper h){
        arena(h);var m=ModEntities.COMMON_INFECTED.get().create(h.getLevel());m.setPos(h.absoluteVec(new Vec3(7.5,2,4.5)));m.setPersistenceRequired();
        m.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);h.getLevel().addFreshEntity(m);var v=target(h,5.8);m.setTarget(v);
        var hits=new ArrayList<String>();COMMON.put(m.getUUID(),hits);
        h.runAfterDelay(185,()->h.assertTrue(hits.size()>=3,"civilian contacts "+hits+" hp="+v.getHealth()+" actor="+m.position()+" victim="+v.position()+" target="+m.getTarget()+" infected="+com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(v)));
        h.onEachTick(()->{if(hits.size()>=3){h.assertTrue(hits.equals(List.of("0:11","1:11","2:14")),"authored civilian contacts "+hits);COMMON.remove(m.getUUID());m.discard();v.discard();h.succeed();}});
    }
    @GameTest(template="blackwatch_facility_arena",batch="hydranonplayer",timeoutTicks=80) public static void hydraSweepHitsNonPlayersButNotInfected(GameTestHelper h){
        for(int x=0;x<32;x++)for(int z=0;z<32;z++){h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<24;y++)h.setBlock(x,y,z,Blocks.AIR);}
        var m=ModEntities.BLACKLIGHT_HYDRA.get().create(h.getLevel());m.setBodySize(2);m.setPos(h.absoluteVec(new Vec3(14,2,12)));m.setNoAi(true);h.getLevel().addFreshEntity(m);
        var v=target(h,22);v.setPos(h.absoluteVec(new Vec3(14,2,22)));v.setNoGravity(true);
        var ally=ModEntities.COMMON_INFECTED.get().create(h.getLevel());ally.setPos(h.absoluteVec(new Vec3(18,2,22)));ally.setNoAi(true);ally.setNoGravity(true);h.getLevel().addFreshEntity(ally);float hp=ally.getHealth();
        h.runAfterDelay(5,()->{m.setNoAi(false);h.assertTrue(m.beginCombat(v,9),"hydra accepts non-player enemy");});
        h.runAfterDelay(27,()->{
            h.assertTrue(v.getHealth()==1000,"sweep has visible windup");
            var claw=HydraContact.boxes(9,30,"Claw_L",m.position(),m.getYRot(),m.visualScale()).get(0);
            v.setPos(claw.center.add(0,-.6,0));ally.setPos(claw.center.add(.05,-.6,0));
        });
        h.runAfterDelay(41,()->{h.assertTrue(v.getHealth()<1000&&ally.getHealth()==hp,"physical sweep damages enemy and filters overlapping ally");m.discard();v.discard();ally.discard();h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="commoncommit",timeoutTicks=65) public static void civilianCommittedSwingSurvivesSelectorChange(GameTestHelper h){
        arena(h);var m=ModEntities.COMMON_INFECTED.get().create(h.getLevel());m.setPos(h.absoluteVec(new Vec3(7.5,2,4.5)));m.setPersistenceRequired();
        m.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);h.getLevel().addFreshEntity(m);var v=target(h,5.8);m.setTarget(v);
        boolean[] changed={false};
        h.onEachTick(()->{
            if(!changed[0]&&m.attackStarted()>=0&&h.getLevel().getGameTime()-m.attackStarted()==5){m.setTarget(null);changed[0]=true;}
            if(v.getHealth()<1000){h.assertTrue(changed[0],"selector changed during windup");m.discard();v.discard();h.succeed();}
        });
    }
    private static void hive(GameTestHelper h,boolean interrupt){
        arena(h);h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(false,h.getLevel().getServer());
        h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(true,h.getLevel().getServer());
        com.codex.blacklight.infection.OutbreakData.get(h.getLevel().getServer()).start(h.getLevel().getGameTime());
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"HiveTimingQA"));
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.setPos(h.absoluteVec(new Vec3(4,2,4)));h.getLevel().addNewPlayer(p);
        var m=ModEntities.BLACKLIGHT_HIVE.get().create(h.getLevel());m.setPos(h.absoluteVec(new Vec3(8,2,8)));
        var data=new net.minecraft.nbt.CompoundTag();m.addAdditionalSaveData(data);data.putFloat("BlacklightGrowth",1);data.putInt("BlacklightWaveCooldown",0);data.putString("BlacklightBrood","common");m.readAdditionalSaveData(data);m.tickCount=19;h.getLevel().addFreshEntity(m);
        boolean[] started={false},cancelled={false};
        h.onEachTick(()->{
            float age=m.hatchAge(0);
            if(age>=0&&age<8){started[0]=true;h.assertTrue(m.waves()==0,"no brood before crown opens");}
            if(interrupt&&started[0]&&!cancelled[0]&&age>=4&&age<8){m.setNoAi(true);cancelled[0]=true;}
            if(!interrupt&&m.waves()>0){
                h.assertTrue(started[0]&&m.waves()==1&&age==8,"one brood on release frame: "+age);
                for(var guard:h.getLevel().getEntitiesOfClass(Mob.class,m.getBoundingBox().inflate(30)))if(guard.getPersistentData().hasUUID("BlacklightHiveGuard")&&guard.getPersistentData().getUUID("BlacklightHiveGuard").equals(m.getUUID()))guard.discard();
                m.discard();p.discard();h.succeed();
            }
        });
        if(interrupt)h.runAfterDelay(30,()->{h.assertTrue(cancelled[0]&&m.waves()==0,"interrupted hatch never publishes a brood");m.discard();p.discard();h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="hiverelease",timeoutTicks=80) public static void hiveOpensBeforeBroodRelease(GameTestHelper h){hive(h,false);}
    @GameTest(template="blackwatch_arena",batch="hivecancel",timeoutTicks=80) public static void hiveInterruptCancelsRelease(GameTestHelper h){hive(h,true);}
}
