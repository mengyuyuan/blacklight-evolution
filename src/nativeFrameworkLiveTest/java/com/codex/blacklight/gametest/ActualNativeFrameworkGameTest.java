package com.codex.blacklight.gametest;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class ActualNativeFrameworkGameTest {
    private static final String[] TYPES={"born_in_chaos_v1:decaying_zombie","born_in_chaos_v1:dread_hound","born_in_chaos_v1:lifestealer_true_form","born_in_chaos_v1:corpse_fly","spore:inf_human","spore:spitter","spore:leaper","spore:busser"};
    private static Mob create(GameTestHelper h,String id){
        var key=ResourceLocation.parse(id);
        h.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(key),"Actual optional mod entity must exist: "+id);
        var entity=BuiltInRegistries.ENTITY_TYPE.get(key).create(h.getLevel());
        if(!(entity instanceof Mob))throw new IllegalStateException("Not a real mob: "+id);
        return (Mob)entity;
    }
    private static void expose(GameTestHelper h,Mob mob){
        h.assertTrue(BlacklightInfectionSystem.infect(mob,null)&&NativeInfection.active(mob),"Native infection must apply: "+mob.getType());
        NativeInfection.tick(mob,h.getLevel());
    }
    @GameTest(template="wall_test",batch="actual_native_reload")
    public static void eightActualNativeModelsRetainIdentityAiDataAndCombatThroughReload(GameTestHelper h){
        var attacker=h.spawn(ModEntities.BLACKLIGHT_HUNTER.get(),4,3,4);attacker.setNoAi(true);
        var receipt=new java.util.ArrayList<String>();
        try{
            for(String id:TYPES){
                Mob mob=create(h,id);mob.setNoAi(true);mob.moveTo(h.absolutePos(new BlockPos(3,3,3)).getCenter());
                h.assertTrue(h.getLevel().addFreshEntity(mob),"source added");
                Mob restored=null;
                try{
                    mob.getPersistentData().putString("NativeQaForeignData","preserve-me");
                    var uuid=mob.getUUID();var nav=mob.getNavigation();var control=mob.getMoveControl();var brain=mob.getBrain();
                    var goals=mob.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).toList();
                    double baseline=mob.getMaxHealth();mob.setTarget(attacker);
                    float amount=(float)Math.min(1,baseline*.02);var damage=h.getLevel().damageSources().mobAttack(attacker);
                    float before=mob.getHealth();boolean originalReturned=mob.hurt(damage,amount);boolean originalProgress=mob.getHealth()<before;
                    mob.setHealth(mob.getMaxHealth());mob.invulnerableTime=0;expose(h,mob);
                    h.assertTrue(mob.getUUID().equals(uuid)&&mob.getNavigation()==nav&&mob.getMoveControl()==control&&mob.getBrain()==brain&&mob.getTarget()==attacker,"native identity, controller, brain and target preserved: "+id);
                    h.assertTrue(mob.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).toList().containsAll(goals),"native goals survive: "+id);
                    float infectedBefore=mob.getHealth();boolean infectedReturned=mob.hurt(damage,amount);boolean infectedProgress=mob.getHealth()<infectedBefore;
                    h.assertTrue(!originalProgress||infectedProgress,"infection cannot turn a native-damageable creature into an invulnerable target: "+id);
                    var tag=new CompoundTag();mob.saveWithoutId(tag);float maximum=mob.getMaxHealth();
                    restored=create(h,id);restored.load(tag);mob.discard();
                    h.assertTrue(h.getLevel().addFreshEntity(restored),"restored original class added");
                    h.assertTrue(restored.getUUID().equals(uuid)&&restored.getClass()==mob.getClass()&&NativeInfection.active(restored),"real save/reload keeps original class, UUID and infection: "+id);
                    NativeInfection.tick(restored,h.getLevel());
                    h.assertTrue(Math.abs(restored.getMaxHealth()-maximum)<Math.max(.02,maximum*.001),"infection health does not compound after save: "+id);
                    h.assertTrue(restored.getPersistentData().getString("NativeQaForeignData").equals("preserve-me"),"foreign persistent state survives: "+id);
                    restored.removeEffect(ModEffects.BLACKLIGHT_INFECTION);NativeInfection.clear(restored);
                    h.assertTrue(Math.abs(restored.getMaxHealth()-baseline)<Math.max(.02,baseline*.001),"cure returns only owned infection bonus: "+id);
                    receipt.add(id+" sourceDamage="+originalReturned+"/"+originalProgress+" infectedDamage="+infectedReturned+"/"+infectedProgress+" identity+ai+save+cure=true");
                }finally{mob.discard();if(restored!=null)restored.discard();}
            }
            com.codex.blacklight.BlacklightNeoMod.LOGGER.info("ACTUAL_NATIVE_MATRIX {}",receipt);h.succeed();
        }finally{attacker.discard();}
    }
    @GameTest(template="wall_test",timeoutTicks=180,batch="actual_native_born_combat")
    public static void actualGeoZombieStillPursuesAndAttacksAfterInfection(GameTestHelper h){liveCombat(h,"born_in_chaos_v1:decaying_zombie");}
    @GameTest(template="wall_test",timeoutTicks=180,batch="actual_native_spore_combat")
    public static void actualSporeHumanStillPursuesAndAttacksAfterInfection(GameTestHelper h){liveCombat(h,"spore:inf_human");}
    private static void liveCombat(GameTestHelper h,String id){
        for(int x=1;x<=12;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        Mob mob=create(h,id);mob.moveTo(h.absolutePos(new BlockPos(3,2,3)).getCenter());h.getLevel().addFreshEntity(mob);
        var prey=h.spawn(EntityType.COW,7,2,3);prey.setNoAi(true);prey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);prey.setHealth(1000);
        var uuid=mob.getUUID();var nav=mob.getNavigation();var control=mob.getMoveControl();var start=mob.position();
        expose(h,mob);mob.setTarget(prey);float before=prey.getHealth();
        h.runAfterDelay(140,()->{try{
            h.assertTrue(prey.getHealth()<before,"actual native attack must damage prey: "+id+" health="+prey.getHealth()+" pos="+mob.position());
            h.assertTrue(mob.position().distanceToSqr(start)>.5,"real native AI pursues: "+id);
            h.assertTrue(mob.getUUID().equals(uuid)&&mob.getNavigation()==nav&&mob.getMoveControl()==control,"native behavior is not a replacement entity: "+id);h.succeed();
        }finally{mob.discard();prey.discard();}});
    }

    @GameTest(template="wall_test",timeoutTicks=160,batch="actual_native_mixed_competition")
    public static void actualBornAndSporeRetainSourceActorsThroughAcceptedCrossModConsumption(GameTestHelper h){
        for(int x=1;x<15;x++)for(int z=1;z<15;z++){
            h.setBlock(new BlockPos(x,1,z),Blocks.STONE);for(int y=2;y<9;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
        var a=create(h,"born_in_chaos_v1:decaying_zombie");var b=create(h,"spore:inf_human");
        var c=create(h,"spore:inf_human");var d=create(h,"born_in_chaos_v1:decaying_zombie");
        var all=java.util.List.of(a,b,c,d);
        try{
            for(int i=0;i<all.size();i++){
                var mob=all.get(i);mob.moveTo(h.absolutePos(new BlockPos(i<2?4:10,2,5)).getCenter().add((i%2)*.8,0,0));
                h.getLevel().addFreshEntity(mob);expose(h,mob);
                var state=mob.getPersistentData().getCompound("BlacklightNativeInfection");
                state.putLong("Age",72000);state.putString("Core",(i%2==0?TissueCore.MUSCLE:TissueCore.JAW).key());state.putInt("Nutrition",200);
                mob.getPersistentData().put("BlacklightNativeInfection",state);
                // Initialize the new state with the real loaded-tick update. No clone or foreign NBT transfer.
                mob.tickCount+=20;NativeInfection.tick(mob,h.getLevel());
                mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            }
            var aNav=a.getNavigation();var cNav=c.getNavigation();var aBrain=a.getBrain();var cBrain=c.getBrain();
            var aGoals=a.goalSelector.getAvailableGoals().stream().map(w->w.getGoal()).toList();
            var cGoals=c.goalSelector.getAvailableGoals().stream().map(w->w.getGoal()).toList();
            b.setHealth(b.getMaxHealth()*.1F);d.setHealth(d.getMaxHealth()*.1F);
            h.assertTrue(TissueCompetition.tryStart(a,b)&&TissueCompetition.tryStart(c,d),"two actual mods start reciprocal live captures");
            h.runAfterDelay(110,()->{
                try{
                    for(var pair:java.util.List.of(new Mob[]{a,b},new Mob[]{c,d})){
                        var actor=pair[0];var prey=pair[1];
                        h.assertTrue(!prey.isAlive()&&prey.getKillCredit()==actor&&NativeTissue.inheritance(actor).meals()==1,
                                "actual accepted foreign death inherits exactly once: "+actor.getType()+" prey="+prey.getType()+" alive="+prey.isAlive()+" meals="+NativeTissue.inheritance(actor).meals());
                        h.assertTrue(!TissueCompetition.controls(actor)&&!TissueCompetition.controls(prey),"actual foreign pair released");
                    }
                    h.assertTrue(a.getNavigation()==aNav&&c.getNavigation()==cNav&&a.getBrain()==aBrain&&c.getBrain()==cBrain,"actual mod navigation and brains retain identity");
                    h.assertTrue(aGoals.stream().allMatch(g->a.goalSelector.getAvailableGoals().stream().anyMatch(w->w.getGoal()==g))
                            &&cGoals.stream().allMatch(g->c.goalSelector.getAvailableGoals().stream().anyMatch(w->w.getGoal()==g)),"source combat goals are retained, not replaced by fake native AI");h.succeed();
                }finally{for(var mob:all){TissueCompetition.interrupt(mob);mob.discard();}}
            });
        }catch(Throwable failure){for(var mob:all){TissueCompetition.interrupt(mob);mob.discard();}throw failure;}
    }

    @GameTest(template="wall_test",timeoutTicks=200,batch="actual_native_born_organ")
    public static void matureBornUsesSharedChelaThroughItsActualNativeSelector(GameTestHelper h){liveOrgan(h,"born_in_chaos_v1:decaying_zombie",TissueCore.CHELA);}
    @GameTest(template="wall_test",timeoutTicks=200,batch="actual_native_spore_organ")
    public static void matureSporeUsesSharedHydrostatThroughItsActualNativeSelector(GameTestHelper h){liveOrgan(h,"spore:inf_human",TissueCore.HYDROSTAT);}
    private static void liveOrgan(GameTestHelper h,String id,TissueCore core){
        var arena=NativeTestArena.open(h,0,20);
        for(int x=1;x<15;x++)for(int z=1;z<15;z++){
            h.setBlock(new BlockPos(x,1,z),Blocks.STONE);for(int y=2;y<10;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
        var mob=create(h,id);mob.moveTo(h.absolutePos(new BlockPos(4,2,5)).getCenter());h.getLevel().addFreshEntity(mob);expose(h,mob);
        var data=mob.getPersistentData().getCompound("BlacklightNativeInfection");data.putLong("Age",72000);data.putString("Core",core.key());data.putInt("Nutrition",200);
        mob.getPersistentData().put("BlacklightNativeInfection",data);mob.tickCount+=20;NativeInfection.tick(mob,h.getLevel());
        var prey=h.spawn(EntityType.COW,6,2,5);prey.setNoAi(true);prey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);prey.setHealth(10000);
        mob.setTarget(prey);var nav=mob.getNavigation();var brain=mob.getBrain();var move=mob.getMoveControl();
        var originalGoals=mob.goalSelector.getAvailableGoals().stream().map(w->w.getGoal()).filter(g->!g.getClass().getName().startsWith("com.codex.blacklight.infection.Native")).toList();
        float[] starting={10000};Runnable cleanup=()->{NativeTissueAbilityGoal.clear(mob);mob.discard();prey.discard();arena.close();};h.runAfterDelay(190,cleanup);
        h.startSequence().thenWaitUntil(()->h.assertTrue(TissueActions.core(mob)==core&&NativeTissueAbilityGoal.active(mob),"actual mod selector grants the shared organ action: "+id))
            .thenExecute(()->starting[0]=prey.getHealth()).thenIdle(TissueOrganCycle.cycle(core).end()+5).thenExecute(()->{
                try{
                    h.assertTrue(prey.getHealth()<starting[0]&&TissueActions.core(mob)==null&&TissueGrapples.owner(prey)==null,"actual foreign "+core+" contacts, damages, withdraws and releases: "+id+" health="+prey.getHealth()+"/"+starting[0]);
                    h.assertTrue(mob.getNavigation()==nav&&mob.getBrain()==brain&&mob.getMoveControl()==move&&originalGoals.stream().allMatch(g->mob.goalSelector.getAvailableGoals().stream().anyMatch(w->w.getGoal()==g)),"real native source AI and controller instances survive the organ action: "+id);h.succeed();
                }finally{cleanup.run();}
            });
    }
}
