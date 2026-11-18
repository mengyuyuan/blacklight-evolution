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
import java.util.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class EncounterGameTest {
    private static void autonomous(GameTestHelper h,EncounterKind kind){
        arena(h);var m=actor(h,kind);var v=target(h,kind==EncounterKind.FLYER||kind==EncounterKind.ELIZABETH_GREENE?8:5);
        m.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(kind.speed);m.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(24);
        if(kind.military)m.setLastHurtByMob(v);
        var observed=new HashSet<EncounterMove>();boolean[] complete={false};
        h.onEachTick(()->{
            if(complete[0])return;
            if(kind==EncounterKind.FLYER&&m.tickCount%30==0)com.codex.blacklight.BlacklightNeoMod.LOGGER.info("FLYER_AUTO ticks={} stage={} position={} target={} hit={} wall={} contact={}",m.tickCount,m.flyerStage(),m.position(),v.position(),m.flight().hits(),m.flight().collisions(),m.flight().lastContact());
            if(m.move()!=EncounterMove.NONE){observed.add(m.move());h.assertTrue(m.moveAge(0)>=0&&m.moveAge(0)<=m.actionDuration(m.move()),"valid synchronized action clock");}
            if(v.getHealth()<2000&&!observed.isEmpty()){
                complete[0]=true;
                com.codex.blacklight.BlacklightNeoMod.LOGGER.info("ENCOUNTER_AUTONOMOUS kind={} actions={} victimHealth={}",kind,observed,v.getHealth());
                h.getLevel().getEntitiesOfClass(BlacklightMutant.class,m.getBoundingBox().inflate(20),child->child.getPersistentData().hasUUID("EncounterOwner")&&child.getPersistentData().getUUID("EncounterOwner").equals(m.getUUID())).forEach(Entity::discard);
                done(h,m,v);
            }
        });
    }
    @GameTest(template="blackwatch_arena",batch="auto_juggernaut",timeoutTicks=320)public static void autonomousJuggernaut(GameTestHelper h){autonomous(h,EncounterKind.JUGGERNAUT);}
    @GameTest(template="blackwatch_arena",batch="auto_goliath",timeoutTicks=320)public static void autonomousGoliath(GameTestHelper h){autonomous(h,EncounterKind.GOLIATH);}
    @GameTest(template="blackwatch_arena",batch="auto_flyer",timeoutTicks=320)public static void autonomousFlyer(GameTestHelper h){autonomous(h,EncounterKind.FLYER);}
    @GameTest(template="blackwatch_arena",batch="auto_greene",timeoutTicks=320)public static void autonomousGreene(GameTestHelper h){autonomous(h,EncounterKind.ELIZABETH_GREENE);}
    @GameTest(template="blackwatch_arena",batch="auto_super",timeoutTicks=320)public static void autonomousSuper(GameTestHelper h){autonomous(h,EncounterKind.SUPER_SOLDIER);}
    @GameTest(template="blackwatch_arena",batch="e_egg_tab",timeoutTicks=30)public static void completeSpawnEggTab(GameTestHelper h){
        var items=EncounterEntities.allSpawnEggs();var names=new HashSet<String>();
        for(var item:items){h.assertTrue(item instanceof net.minecraft.world.item.SpawnEggItem,"only spawn eggs");h.assertTrue(names.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath()),"no duplicate egg");}
        for(String id:new String[]{"common_infected","infected_host","blacklight_stalker","blacklight_spitter","blacklight_brute","blacklight_hive","blacklight_hunter","supreme_hunter","blacklight_hydra","blackwatch_soldier","military_soldier","recon_uav"})h.assertTrue(names.contains(id+"_spawn_egg"),"missing old/new creature "+id);
        for(var kind:EncounterKind.values())h.assertTrue(names.contains(kind.id+"_spawn_egg")==kind.enabled(),"active roster mismatch "+kind);
        h.assertTrue(items.size()==25,"all 25 active Blacklight creature eggs: "+items.size());
        com.codex.blacklight.BlacklightNeoMod.LOGGER.info("ENCOUNTER_EGG_TAB count={} ids={}",items.size(),names);h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="e_charge_contact",timeoutTicks=100)public static void chargeWaitsForBodyContact(GameTestHelper h){
        arena(h);var m=actor(h,EncounterKind.JUGGERNAUT);var v=target(h,9);
        h.runAfterDelay(25,()->h.assertTrue(m.begin(EncounterMove.CHARGE,v),"charge begins"));
        h.runAfterDelay(44,()->h.assertTrue(v.getHealth()==2000,"charge cannot hit distant target during initial active ticks"));
        h.runAfterDelay(62,()->{h.assertTrue(v.getHealth()<2000,"body reaching target damages it");done(h,m,v);});
    }
    @GameTest(template="blackwatch_arena",batch="e_dive_miss",timeoutTicks=120)public static void diveCannotHitOutsideBodyReach(GameTestHelper h){
        arena(h);var m=actor(h,EncounterKind.FLYER);var v=target(h,7);m.setPos(m.position().add(0,4,0));
        h.runAfterDelay(25,()->h.assertTrue(m.begin(EncounterMove.DIVE,v),"dive begins"));
        // Stay on the fixture floor while dodging the committed path, inside the old range cone.
        h.runAfterDelay(42,()->v.setPos(v.position().add(4,0,2)));
        h.runAfterDelay(85,()->{h.assertTrue(v.getHealth()==2000,"dive misses when its body never reaches target");done(h,m,v);});
    }
    @GameTest(template="blackwatch_arena",batch="e_pounce_miss",timeoutTicks=110)public static void pounceCanBeEvaded(GameTestHelper h){
        arena(h);var m=actor(h,EncounterKind.SUPER_SOLDIER);var v=target(h,5);m.setLastHurtByMob(v);
        h.runAfterDelay(25,()->h.assertTrue(m.begin(EncounterMove.POUNCE,v),"pounce begins"));
        h.runAfterDelay(42,()->v.setPos(v.position().add(0,0,4)));
        h.runAfterDelay(80,()->{h.assertTrue(v.getHealth()==2000,"evading actual leap contact avoids damage");done(h,m,v);});
    }
    @GameTest(template="blackwatch_arena",batch="e_charge_stagger",timeoutTicks=100)public static void chargeIntoWallExposesAttacker(GameTestHelper h){
        arena(h);var m=actor(h,EncounterKind.JUGGERNAUT);var v=target(h,9);
        h.runAfterDelay(25,()->{h.assertTrue(m.begin(EncounterMove.CHARGE,v),"charge begins");for(int x=4;x<13;x++)for(int y=2;y<9;y++)h.setBlock(x,y,8,Blocks.STONE);});
        h.runAfterDelay(55,()->{h.assertTrue(m.move()==EncounterMove.NONE&&m.exposed(),"wall collision interrupts into punishable stagger");h.assertTrue(v.getHealth()==2000,"no through-wall charge damage");done(h,m,v);});
    }
    @GameTest(template="blackwatch_arena",batch="e_recharge",timeoutTicks=65)public static void attackRechargeSurvivesReload(GameTestHelper h){
        arena(h);var m=actor(h,EncounterKind.ELIZABETH_GREENE);m.goalSelector.removeAllGoals(g->true);m.targetSelector.removeAllGoals(g->true);var v=target(h,3);
        h.runAfterDelay(25,()->com.codex.blacklight.BlacklightNeoMod.LOGGER.info("RECHARGE_FIXTURE worldDelay=25 entityTicks={}",m.tickCount));
        boolean[] completed={false};h.onEachTick(()->{if(completed[0]||m.tickCount<25)return;completed[0]=true;h.assertTrue(m.begin(EncounterMove.HOWL,v),"howl starts: move="+m.move()+" ready="+m.moveReady(EncounterMove.HOWL)+" enemy="+m.enemy(v)+" sight="+m.hasLineOfSight(v)+" distance="+m.distanceTo(v)+" hp="+m.getHealth()+" victim="+v.getHealth());m.cancelAttack();var tag=new net.minecraft.nbt.CompoundTag();m.saveWithoutId(tag);var copy=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(h.getLevel());copy.load(tag);h.assertTrue(!copy.moveReady(EncounterMove.HOWL)&&copy.moveReady(EncounterMove.ORBS),"per-ability recharge persists independently");done(h,m,v,copy);});
    }
    @GameTest(template="blackwatch_arena",batch="e_source_roar",timeoutTicks=195)public static void sourceRoarExpandsAndDoesNotRepeatDamage(GameTestHelper h){
        arena(h);var m=actor(h,EncounterKind.ELIZABETH_GREENE);var v=target(h,10*GreeneMotion.table().sourceUnitBlocks());float[] hit={2000};
        h.runAfterDelay(25,()->h.assertTrue(m.begin(EncounterMove.HOWL,v),"roar begins"));
        h.runAfterDelay(94,()->h.assertTrue(v.getHealth()==2000,"converted radius8 cannot reach the target surface at ten source units"));
        h.runAfterDelay(108,()->{h.assertTrue(v.getHealth()<2000,"converted radius12 reaches the same Minecraft target");hit[0]=v.getHealth();});
        h.runAfterDelay(170,()->{h.assertTrue(v.getHealth()==hit[0],"expanding windows share one hit per target");done(h,m,v);});
    }
    @GameTest(template="blackwatch_arena",batch="e_source_recovery",timeoutTicks=3400)public static void sourceExposedCycleRecoversAndPersists(GameTestHelper h){
        arena(h);var m=actor(h,EncounterKind.ELIZABETH_GREENE);var v=target(h,9);
        m.goalSelector.removeAllGoals(g->true);m.targetSelector.removeAllGoals(g->true);
        // Keep this recovery fixture independent of the separate brood arenas.
        m.restrictTo(m.blockPosition(),10);
        double surface=m.getY();float firstArmor=m.greeneSegmentMaximum();
        var standingHead=m.hurtRegions(0).stream().filter(r->r.bone().equals("head")).findFirst().orElseThrow().bounds().getCenter();
        var stages=new LinkedHashSet<Integer>();stages.add(GreeneBattleCycle.GUARDED);
        int[] previous={GreeneBattleCycle.GUARDED};boolean[] damaged={false},targetableAgain={false},complete={false};
        for(int i=0;i<3;i++){final int slot=i;h.runAfterDelay(5+i*12,()->h.assertTrue(m.getParts()[slot].hurt(m.damageSources().mobAttack(v),m.greeneSegmentMaximum()/com.codex.blacklight.power.CombatScale.units(m)+1),"current-wave weakpoint breaks"));}
        h.onEachTick(()->{
            if(complete[0])return;
            var cycle=m.greeneBattle();int stage=cycle.stage();float local=cycle.age(0);
            if(stage!=previous[0]){
                previous[0]=stage;stages.add(stage);checkGreenePhaseReload(h,m);
                if(stage==GreeneBattleCycle.COLLAPSING)h.assertTrue(m.exposed()&&greeneMask(m)==7&&!cycle.coreDamageable(),"all limbs break before core vulnerability");
                if(stage==GreeneBattleCycle.RISING)h.assertTrue(greeneMask(m)==7&&cycle.coreDamageable(),"recovery rise does not prematurely regrow the shield");
                if(stage==GreeneBattleCycle.SUBMERGING)h.assertTrue(!m.exposed()&&greeneMask(m)==7&&!cycle.coreDamageable(),"rise hands off to protected submerge with limbs still broken");
                if(stage==GreeneBattleCycle.UNDERGROUND)h.assertTrue(m.getY()<surface-GreeneBattleCycle.SOURCE_DEPTH*GreeneMotion.table().sourceUnitBlocks()*.9&&greeneMask(m)==7,"the body really sinks before the underground wait");
                if(stage==GreeneBattleCycle.EMERGE_WAIT){
                    var saved=new net.minecraft.nbt.CompoundTag();m.saveWithoutId(saved);
                    h.assertTrue(greeneMask(m)==0&&cycle.armourWave()==2&&m.greeneSegmentMaximum()>firstArmor&&!cycle.targetable(),"next-wave shield restores underground before targetability");
                    for(int slot=0;slot<3;slot++)h.assertTrue(Math.abs(saved.getFloat("EncounterArmor"+slot)-m.greeneSegmentMaximum())<.001,"restored limb has current-wave health");
                }
                if(stage==GreeneBattleCycle.GUARDED){
                    h.assertTrue(new ArrayList<>(stages).equals(List.of(0,1,2,3,4,5,6,7))&&damaged[0]&&targetableAgain[0],"all eight live stages and exposed body contact were observed in order");
                    h.assertTrue(Math.abs(m.getY()-surface)<.05&&!cycle.controlsRoot()&&!m.noPhysics&&cycle.canAttack(),"full emergence returns the body and attack permission to the surface");
                    complete[0]=true;done(h,m,v);return;
                }
            }
            if(stage==GreeneBattleCycle.DOWN&&local>=20&&!damaged[0]){
                float before=m.getHealth();m.invulnerableTime=0;
                h.assertTrue(m.hurt(m.damageSources().mobAttack(v),10/com.codex.blacklight.power.CombatScale.units(m))&&Math.abs(before-m.getHealth()-10)<.001,"down core accepts ten health units after the separate stun debit");
                var head=m.hurtRegions(0).stream().filter(r->r.bone().equals("head")).findFirst().orElseThrow();
                h.assertTrue(!head.boxes().isEmpty()&&head.bounds().getCenter().distanceTo(standingHead)>.5,"the fallen head has a moved, hittable posed receiver");
                h.assertTrue(!m.begin(EncounterMove.HOWL,v),"exposed recovery blocks an otherwise valid close attack");damaged[0]=true;
            }
            if(stage==GreeneBattleCycle.UNDERGROUND||stage==GreeneBattleCycle.EMERGE_WAIT||stage==GreeneBattleCycle.SUBMERGING&&local>=16){
                h.assertTrue(!cycle.targetable()&&m.hurtRegions(0).stream().allMatch(r->r.boxes().isEmpty()),"hidden body cannot retain a receiving skin");
                h.assertTrue(!m.getParts()[0].isPickable(),"hidden limb proxy cannot be selected");
            }
            if(stage==GreeneBattleCycle.EMERGING){
                h.assertTrue(cycle.targetable()==(local>=GreeneBattleCycle.EMERGE_TARGET_TICKS),"emergence targetability follows the movement-end gate");
                if(local>=GreeneBattleCycle.EMERGE_TARGET_TICKS)targetableAgain[0]=true;
                if(local<GreeneBattleCycle.EMERGE_ATTACK_TICKS)h.assertTrue(!cycle.canAttack(),"the restored shield does not skip attack windup");
            }
        });
    }
    private static int greeneMask(EncounterMob m){return (m.broken(0)?1:0)|(m.broken(1)?2:0)|(m.broken(2)?4:0);}
    private static void checkGreenePhaseReload(GameTestHelper h,EncounterMob m){
        var tag=new net.minecraft.nbt.CompoundTag();m.saveWithoutId(tag);var copy=EncounterEntities.CREATURES.get(m.kind()).get().create(h.getLevel());copy.load(tag);
        // The read-only snapshot is never added; disposal must not retire the
        // live mother's children through its copied UUID/ownership record.
        copy.setUUID(UUID.randomUUID());
        try{h.assertTrue(copy.greeneStage()==m.greeneStage()&&Math.abs(copy.greeneBattle().age(0)-m.greeneBattle().age(0))<.01,"reload retains the current stage and local clock");
            h.assertTrue(greeneMask(copy)==greeneMask(m)&&copy.greeneBattle().waveCount()==m.greeneBattle().waveCount()&&copy.greeneBattle().coreDamageable()==m.greeneBattle().coreDamageable(),"reload retains armor wave, vulnerability and broken limbs");
            h.assertTrue(copy.greeneBattle().save().getInt("UndergroundRemaining")==m.greeneBattle().save().getInt("UndergroundRemaining"),"reload preserves the remaining underground wait");
        }finally{copy.discard();}
    }
    private static void arena(GameTestHelper h){h.getLevel().setDayTime(18000);for(int x=0;x<16;x++)for(int z=0;z<16;z++){h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<16;y++)h.setBlock(x,y,z,Blocks.AIR);}}
    private static BlacklightHunter hunter(GameTestHelper h){var m=ModEntities.BLACKLIGHT_HUNTER.get().create(h.getLevel());m.setPos(h.absoluteVec(new Vec3(8,2,5)));m.setNoAi(true);m.setPersistenceRequired();h.assertTrue(h.getLevel().addFreshEntity(m),"original hunter added");return m;}
    private static EncounterMob actor(GameTestHelper h,EncounterKind k){var m=EncounterEntities.CREATURES.get(k).get().create(h.getLevel());
        // Explicit existing encounter fixture; fresh opening has its own regression.
        if(m.kind()==EncounterKind.ELIZABETH_GREENE){var standing=m.greeneBattle().save();standing.putBoolean("OpeningPending",false);m.greeneBattle().load(standing);}
        m.setPos(h.absoluteVec(new Vec3(8,2,5)));m.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);m.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(.25);m.setPersistenceRequired();m.setYRot(0);m.setYBodyRot(0);h.assertTrue(h.getLevel().addFreshEntity(m),"actor added");return m;}
    private static net.minecraft.world.entity.animal.Pig target(GameTestHelper h,double distance){var m=h.spawn(EntityType.PIG,new Vec3(8,2,5+distance));m.setNoAi(true);m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);m.setHealth(2000);m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);return m;}
    private static void done(GameTestHelper h,Entity... entities){for(var e:entities)e.discard();h.succeed();}
    private static void attack(GameTestHelper h,EncounterKind kind,EncounterMove action,double range){arena(h);var m=actor(h,kind);var v=target(h,range);
        // This fixture commands one ability directly; autonomousGreene separately retains production goals.
        if(kind==EncounterKind.ELIZABETH_GREENE){m.goalSelector.removeAllGoals(g->true);m.targetSelector.removeAllGoals(g->true);}
        h.runAfterDelay(25,()->h.assertTrue(m.begin(action,v),"starts "+kind+" "+action));
        h.runAfterDelay(25+m.actionContact(action)-1,()->h.assertTrue(v.getHealth()==2000,"no early damage"));
        h.runAfterDelay(25+m.actionContact(action),()->com.codex.blacklight.BlacklightNeoMod.LOGGER.info("ENCOUNTER_QA kind={} clock={} move={} actor={} victim={} valid={} sight={} targetAlive={} actorTicks={}",kind,m.moveAge(0),m.move(),m.position(),v.position(),m.enemy(v),m.hasLineOfSight(v),v.isAlive(),m.tickCount));
        h.runAfterDelay(25+m.actionDuration(action)+10,()->{h.assertTrue(v.getHealth()<2000,"accepted hit "+action+" health="+v.getHealth());done(h,m,v);});
    }
    @GameTest(template="blackwatch_arena",batch="e_jugger",timeoutTicks=110)public static void juggernautHammer(GameTestHelper h){attack(h,EncounterKind.JUGGERNAUT,EncounterMove.HAMMER,3);}
    @GameTest(template="blackwatch_arena",batch="e_goliath",timeoutTicks=120)public static void goliathWave(GameTestHelper h){attack(h,EncounterKind.GOLIATH,EncounterMove.ERUPTION,5);}
    @GameTest(template="blackwatch_arena",batch="e_greene_shot",timeoutTicks=130)public static void greeneRifleOnFoot(GameTestHelper h){
        arena(h);var m=actor(h,EncounterKind.ELIZABETH_GREENE);var v=target(h,9);m.goalSelector.removeAllGoals(g->true);m.targetSelector.removeAllGoals(g->true);
        h.runAfterDelay(25,()->{h.assertTrue(!m.begin(EncounterMove.ORBS,v),"anti-vehicle program rejects an on-foot pig");h.assertTrue(m.beginGreene(GreeneWeaponProgram.RIFLE,v),"native rifle starts against a walking target");});
        h.runAfterDelay(85,()->{h.assertTrue(m.greeneShots()>0&&v.getHealth()<2000,"dedicated rifle socket emits missiles that reach the visible enemy");done(h,m,v);});
    }
    @GameTest(template="blackwatch_arena",batch="e_flyer",timeoutTicks=140)public static void flyerSourceDive(GameTestHelper h){arena(h);var m=actor(h,EncounterKind.FLYER);var v=target(h,9);m.setPos(m.position().add(0,4,0));h.runAfterDelay(25,()->h.assertTrue(m.begin(EncounterMove.DIVE,v),"source dive starts"));h.runAfterDelay(38,()->h.assertTrue(v.getHealth()==2000,"windup cannot hit"));h.runAfterDelay(115,()->{h.assertTrue(v.getHealth()<2000&&m.flight().hits()==1,"one body contact replaces unverified spit");done(h,m,v);});}
    @GameTest(template="blackwatch_arena",batch="e_super",timeoutTicks=110)public static void superSoldierFaction(GameTestHelper h){arena(h);var m=actor(h,EncounterKind.SUPER_SOLDIER);var innocent=target(h,2);var enemy=hunter(h);enemy.setNoAi(true);enemy.setPos(m.position().add(0,0,3));h.assertTrue(!m.enemy(innocent)&&m.enemy(enemy),"military only targets infected or aggressors");h.runAfterDelay(25,()->h.assertTrue(m.begin(EncounterMove.HAMMER,enemy),"military hammer"));h.runAfterDelay(75,()->{h.assertTrue(enemy.getHealth()<enemy.getMaxHealth(),"infected enemy hit");done(h,m,innocent,enemy);});}
    @GameTest(template="blackwatch_arena",batch="e_phases",timeoutTicks=50)public static void phasesPersist(GameTestHelper h){arena(h);var m=actor(h,EncounterKind.GOLIATH);m.setHealth(m.getMaxHealth()*.6F);h.runAfterDelay(2,()->{h.assertTrue(m.phase()==1,"second phase");m.setHealth(m.getMaxHealth()*.25F);});h.runAfterDelay(4,()->{h.assertTrue(m.phase()==2,"third phase");var tag=new net.minecraft.nbt.CompoundTag();m.saveWithoutId(tag);var copy=EncounterEntities.CREATURES.get(EncounterKind.GOLIATH).get().create(h.getLevel());copy.load(tag);h.assertTrue(copy.phase()==2&&copy.move()==EncounterMove.NONE,"phase persists, pending hit does not");done(h,m,copy);});}
    @GameTest(template="blackwatch_arena",batch="e_parts",timeoutTicks=80)public static void greeneWeakpoints(GameTestHelper h){arena(h);var m=actor(h,EncounterKind.ELIZABETH_GREENE);var v=target(h,9);h.assertTrue(m.hurtRegions(0).stream().filter(r->r.bone().startsWith("weak_")).count()==3,"three independent tendrils plus posed body regions");for(int i=0;i<3;i++){final int slot=i;h.runAfterDelay(5+i*12,()->h.assertTrue(m.getParts()[slot].hurt(m.damageSources().mobAttack(v),m.greeneSegmentMaximum()+1),"accepted current-wave armor strike "+slot));}h.runAfterDelay(32,()->{h.assertTrue(m.greeneStage()==GreeneBattleCycle.COLLAPSING&&greeneMask(m)==7&&!m.greeneBattle().coreDamageable(),"breaking all tendrils starts collapse before core damage is enabled");h.assertTrue(!m.getParts()[0].isPickable(),"broken part cannot be farmed");done(h,m,v);});}
    @GameTest(template="blackwatch_arena",batch="e_wall",timeoutTicks=110)public static void wallStopsDamage(GameTestHelper h){arena(h);var m=actor(h,EncounterKind.JUGGERNAUT);var v=target(h,3);h.runAfterDelay(25,()->{h.assertTrue(m.begin(EncounterMove.HAMMER,v),"start blade");for(int x=5;x<16;x++)for(int y=2;y<10;y++)h.setBlock(x,y,6,Blocks.STONE);});h.runAfterDelay(80,()->{h.assertTrue(v.getHealth()==2000,"no damage through wall");done(h,m,v);});}
    @GameTest(template="blackwatch_arena",batch="e_cancel",timeoutTicks=110)public static void interruptCancelsDamage(GameTestHelper h){arena(h);var m=actor(h,EncounterKind.JUGGERNAUT);var v=target(h,3);h.runAfterDelay(25,()->h.assertTrue(m.begin(EncounterMove.HAMMER,v),"start hammer"));h.runAfterDelay(32,()->m.setNoAi(true));h.runAfterDelay(80,()->{h.assertTrue(m.move()==EncounterMove.NONE&&v.getHealth()==2000,"NoAI cancels pending contact");done(h,m,v);});}
    @GameTest(template="blackwatch_arena",batch="e_ally",timeoutTicks=100)public static void alliesAndRearAreSafe(GameTestHelper h){arena(h);var m=actor(h,EncounterKind.JUGGERNAUT);var v=target(h,3);var rear=target(h,-3);var ally=hunter(h);ally.setPos(m.position().add(1,0,2));ally.setNoAi(true);h.runAfterDelay(25,()->h.assertTrue(m.begin(EncounterMove.HAMMER,v),"begin"));h.runAfterDelay(75,()->{h.assertTrue(v.getHealth()<2000&&rear.getHealth()==2000&&ally.getHealth()==ally.getMaxHealth(),"front enemy only");done(h,m,v,rear,ally);});}
    @GameTest(template="blackwatch_arena",batch="e_drone",timeoutTicks=140)public static void droneReports(GameTestHelper h){arena(h);var drone=EncounterEntities.UAV.get().create(h.getLevel());drone.setPos(h.absoluteVec(new Vec3(8,7,8)));h.getLevel().addFreshEntity(drone);var infected=hunter(h);infected.setNoAi(true);infected.setPos(h.absoluteVec(new Vec3(12,2,12)));h.runAfterDelay(90,()->{h.assertTrue(drone.scanProgress()==60&&drone.scanTargetId()==infected.getId(),"sustained scan completes");drone.setNoAi(true);});h.runAfterDelay(94,()->{h.assertTrue(drone.scanProgress()==0,"disabled scanner loses acquisition");done(h,drone,infected);});}
    @GameTest(template="blackwatch_arena",batch="e_drone_nbt",timeoutTicks=45)public static void commandSpawnDefaultsAndBatteryPersist(GameTestHelper h){arena(h);var drone=EncounterEntities.UAV.get().create(h.getLevel());drone.readAdditionalSaveData(new net.minecraft.nbt.CompoundTag());drone.setPos(h.absoluteVec(new Vec3(8,7,8)));h.getLevel().addFreshEntity(drone);h.runAfterDelay(10,()->{h.assertTrue(drone.isAlive()&&!drone.isRemoved(),"summon without custom NBT receives a full battery");var tag=new net.minecraft.nbt.CompoundTag();drone.addAdditionalSaveData(tag);h.assertTrue(tag.getInt("UavBattery")>5900&&tag.getInt("UavBattery")<6000,"battery counts down and saves");h.assertTrue(new Vec3(tag.getDouble("UavHomeX"),tag.getDouble("UavHomeY"),tag.getDouble("UavHomeZ")).distanceTo(h.absoluteVec(new Vec3(8,7,8)))<1,"default home is actual spawn, not world origin");tag.putInt("UavBattery",2);drone.readAdditionalSaveData(tag);});h.runAfterDelay(15,()->{h.assertTrue(drone.isRemoved(),"saved exhausted battery is not refilled on load");h.succeed();});}
    @GameTest(template="blackwatch_arena",batch="e_radio",timeoutTicks=145)public static void radioCanBeInterrupted(GameTestHelper h){arena(h);var soldier=ModEntities.BLACKWATCH_OFFICER.get().create(h.getLevel());soldier.setPos(h.absoluteVec(new Vec3(8,2,3)));h.getLevel().addFreshEntity(soldier);var m=hunter(h);m.setPos(soldier.position().add(0,0,12));m.setNoAi(true);soldier.setTarget(m);h.runAfterDelay(20,()->{h.assertTrue(soldier.radioStarted()>=0,"officer calls before deploying");soldier.hurt(soldier.damageSources().mobAttack(m),1);h.assertTrue(soldier.radioStarted()<0,"damage interrupts call");soldier.setNoAi(true);});h.runAfterDelay(110,()->{h.assertTrue(h.getLevel().getEntitiesOfClass(MilitaryUav.class,soldier.getBoundingBox().inflate(35)).isEmpty(),"interrupted call cannot deploy later");done(h,soldier,m);});}
    @GameTest(template="blackwatch_arena",batch="e_radio_done",timeoutTicks=145)public static void completedRadioDeploysOneFallbackUav(GameTestHelper h){
        arena(h);var soldier=ModEntities.BLACKWATCH_OFFICER.get().create(h.getLevel());soldier.setPos(h.absoluteVec(new Vec3(8,2,3)));h.getLevel().addFreshEntity(soldier);
        // Exercise UAV fallback explicitly; an eligible Blackwatch radio now prefers a troop helicopter.
        soldier.getPersistentData().putLong("AirSupportReady",h.getLevel().getGameTime()+300);
        var m=hunter(h);m.setPos(soldier.position().add(0,0,11));m.setNoAi(true);soldier.setTarget(m);long[] began={-1};boolean[] complete={false};
        h.onEachTick(()->{
            if(complete[0])return;if(soldier.radioStarted()>=0)began[0]=soldier.radioStarted();
            // Other independent arenas may have a drone within 35 blocks. Only
            // this caller's lease can prove its radio call completed.
            var drones=h.getLevel().getEntitiesOfClass(MilitaryUav.class,soldier.getBoundingBox().inflate(35),d->soldier.getUUID().equals(d.owner()));
            long elapsed=h.getLevel().getGameTime()-began[0];
            if(began[0]>=0&&elapsed<com.codex.blacklight.military.MilitaryTactics.RADIO_TICKS)h.assertTrue(drones.isEmpty(),"no deployment before actual radio contact clock");
            if(drones.isEmpty())return;
            h.assertTrue(began[0]>=0&&elapsed>=com.codex.blacklight.military.MilitaryTactics.RADIO_TICKS&&drones.size()==1,"one owned drone after complete call");
            complete[0]=true;drones.forEach(Entity::discard);done(h,soldier,m);
        });
    }
    @GameTest(template="blackwatch_arena",batch="e_radio_lost",timeoutTicks=145)public static void lostTargetCancelsRadio(GameTestHelper h){arena(h);var soldier=ModEntities.BLACKWATCH_OFFICER.get().create(h.getLevel());soldier.setPos(h.absoluteVec(new Vec3(8,2,3)));h.getLevel().addFreshEntity(soldier);var m=hunter(h);m.setPos(soldier.position().add(0,0,11));m.setNoAi(true);soldier.setTarget(m);h.runAfterDelay(20,()->{h.assertTrue(soldier.radioStarted()>=0,"radio started before target loss");m.discard();});h.runAfterDelay(28,()->h.assertTrue(soldier.radioStarted()<0,"lost target clears radio pose and timer"));h.runAfterDelay(110,()->{h.assertTrue(h.getLevel().getEntitiesOfClass(MilitaryUav.class,soldier.getBoundingBox().inflate(35)).isEmpty(),"lost target cannot produce delayed deployment");done(h,soldier);});}
    @GameTest(template="blackwatch_arena",batch="e_weapon_part",timeoutTicks=45)public static void weaponContactSelectsOneWeakpoint(GameTestHelper h){arena(h);var m=actor(h,EncounterKind.ELIZABETH_GREENE);var v=target(h,9);h.runAfterDelay(3,()->{var box=m.hurtRegions(0).stream().filter(r->r.bone().equals("weak_1")).findFirst().orElseThrow().boxes().get(0);var contact=new WeaponCutContact.Contact(32,"qa-edge",box.center,new Vec3(0,0,.3),new Vec3(1,0,0));h.assertTrue(m.hurtWeapon(m.damageSources().mobAttack(v),m.greeneSegmentMaximum()+1,new WeaponCutContact.Report(WeaponCutContact.Kind.SLICE,List.of(contact)),b->false),"blade contact accepted");h.assertTrue(!m.broken(0)&&m.broken(1)&&!m.broken(2)&&!m.exposed(),"only contacted current-wave armor breaks");done(h,m,v);});}
    @GameTest(template="blackwatch_arena",batch="e_drone_wall",timeoutTicks=120)public static void droneCannotFinishThroughWall(GameTestHelper h){arena(h);var drone=EncounterEntities.UAV.get().create(h.getLevel());drone.setPos(h.absoluteVec(new Vec3(3,5,3)));h.getLevel().addFreshEntity(drone);var infected=hunter(h);infected.setNoAi(true);infected.setPos(h.absoluteVec(new Vec3(12,2,12)));h.runAfterDelay(24,()->{for(int y=2;y<20;y++)for(int n=10;n<=14;n++){h.setBlock(10,y,n,Blocks.STONE);h.setBlock(14,y,n,Blocks.STONE);h.setBlock(n,y,10,Blocks.STONE);h.setBlock(n,y,14,Blocks.STONE);}for(int x=10;x<=14;x++)for(int z=10;z<=14;z++)h.setBlock(x,20,z,Blocks.STONE);});h.runAfterDelay(85,()->{h.assertTrue(!drone.hasLineOfSight(infected),"fixture fully occludes intended target");h.assertTrue(drone.scanTargetId()!=infected.getId()||drone.scanProgress()==0,"occlusion cancels this target; reacquiring another visible target is allowed");done(h,drone,infected);});}
}
