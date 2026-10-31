package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class EcologyBehaviorGameTest{
    private static BuildingAssimilationGameTest.Fixture fixture(GameTestHelper h){return new BuildingAssimilationGameTest.Fixture(h,h.getLevel().getServer().getLevel(Level.NETHER));}
    private static <T extends Entity>T join(BuildingAssimilationGameTest.Fixture f,T entity,double x,double z){
        entity.setPos(f.origin.getX()+x,f.origin.getY(),f.origin.getZ()+z);
        if(!f.level.addFreshEntity(entity))throw new AssertionError("fixture actor join rejected");f.born.add(entity);return entity;
    }
    @GameTest(template="wall_test",timeoutTicks=600,batch="civilian_old_save")
    public static void oldCommonSaveRetainsItsIdentityEquipmentAndHumanoidConsumeRoute(GameTestHelper h){var f=fixture(h);f.after(1,()->{},()->{
        var mob=ModEntities.COMMON_INFECTED.get().create(f.level);mob.setNoAi(true);mob.setItemSlot(EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        var tag=new net.minecraft.nbt.CompoundTag();mob.saveWithoutId(tag);tag.putBoolean("IsBaby",true);tag.putInt("BlacklightMeals",2);
        var restored=ModEntities.COMMON_INFECTED.get().create(f.level);restored.load(tag);
        h.assertTrue(restored.getUUID().equals(mob.getUUID())&&restored.appearance()==mob.appearance()&&restored.getMainHandItem().is(net.minecraft.world.item.Items.IRON_SWORD),"old identity, clothing selection and equipment survive without entity replacement");
        h.assertTrue(com.codex.blacklight.combat.ConsumeSequence.humanoid(restored)&&!restored.isBaby(),"new adult civilian retains the paired humanoid grab/consume route");
    });}
    @GameTest(template="wall_test",timeoutTicks=600,batch="civilian_contact")
    public static void civilianAiTelegraphsItsRealContactAndDoesNotHitOnEveryAnimationTick(GameTestHelper h){var f=fixture(h);BlacklightCommonInfected[] mob={null};LivingEntity[] target={null};int[] hits={0};long[] first={-1};
        f.after(30,()->{
            mob[0]=join(f,ModEntities.COMMON_INFECTED.get().create(f.level),-4,-2);
            var cow=EntityType.COW.create(f.level);cow.setNoAi(true);cow.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);cow.setHealth(200);target[0]=join(f,cow,-2.7,-2);mob[0].setTarget(cow);
            float[] previous={cow.getHealth()};for(int t=1;t<=29;t++)h.runAfterDelay(t,()->{if(cow.getHealth()<previous[0]){hits[0]++;first[0]=mob[0].attackStarted()<0?-1:f.level.getGameTime()-mob[0].attackStarted();}previous[0]=cow.getHealth();});
        },()->{h.assertTrue(hits[0]==1&&first[0]>=CommonInfectedMotion.CONTACT_TICK&&first[0]<=CommonInfectedMotion.CONTACT_TICK+1,"one accepted contact after windup, not a repeated pose-driven hit; hits="+hits[0]+" contact="+first[0]+" action="+mob[0].attackStarted()+" tick="+f.level.getGameTime()+" target="+mob[0].getTarget()+" hp="+target[0].getHealth()+" ai="+mob[0].isNoAi()+" distance="+mob[0].distanceTo(target[0])+" hurt="+mob[0].hurtTime+" age="+mob[0].tickCount);});}
    @GameTest(template="wall_test",timeoutTicks=600,batch="civilian_capture_abort")
    public static void grabbingACivilianDuringWindupCancelsItsPendingDamage(GameTestHelper h){var f=fixture(h);BlacklightCommonInfected[] mob={null};LivingEntity[] target={null};
        f.after(24,()->{
            mob[0]=join(f,ModEntities.COMMON_INFECTED.get().create(f.level),-4,-2);var cow=EntityType.COW.create(f.level);cow.setNoAi(true);target[0]=join(f,cow,-2.7,-2);mob[0].setTarget(cow);
            h.runAfterDelay(5,()->{
                var p=f.observer;p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(mob[0].position().add(0,0,2));p.setYRot(180);p.setXRot(0);p.setOnGround(true);p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);p.setHealth(p.getMaxHealth());p.tickCount=100;
                var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,state);
                com.codex.blacklight.combat.ConsumeSystem.request(p);
                h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(mob[0], ModAttachments.CONSUME_VISUAL).active(),"real E request claims the actor; ready="+com.codex.blacklight.combat.ConsumeSystem.healthReady(p,mob[0])+" eligible="+com.codex.blacklight.combat.ConsumeSystem.eligible(mob[0])+" ground="+p.onGround()+" p="+p.position()+" mob="+mob[0].position());
            });
            // FakePlayer.tick is a no-op; drive the real player-tick entry just as the existing consume fixtures do.
            for(int t=6;t<=23;t++)h.runAfterDelay(t,()->{f.observer.tickCount++;com.codex.blacklight.combat.ConsumeSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,f.observer));});
        },()->{try{h.assertTrue(target[0].getHealth()==target[0].getMaxHealth()&&mob[0].attackStarted()<0,"capture owns the actor before its contact frame; health="+target[0].getHealth()+" action="+mob[0].attackStarted()+" visual="+com.codex.blacklight.registry.ModAttachments.getData(mob[0], ModAttachments.CONSUME_VISUAL));}finally{com.codex.blacklight.combat.ConsumeSystem.cancel(f.observer);}});}
    private static void clearFlightArena(BuildingAssimilationGameTest.Fixture f){
        // The architecture fixture only clears a narrow corridor. Idle flight
        // samples randomized destinations outside it, into generated Nether rock.
        // Test takeoff in loaded open air rather than a world-seed-dependent cave.
        for(int x=-13;x<=13;x++)for(int z=-13;z<=13;z++)for(int y=-1;y<=8;y++)
            f.block(f.origin.offset(x,y,z),y==-1?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
    }
    private static void flight(GameTestHelper h,EntityType<? extends net.minecraft.world.entity.Mob> type){var f=fixture(h);BlacklightInfectedHost[] actor={null};double[] raised={0};net.minecraft.world.phys.Vec3[] initial={null};
        f.after(100,()->{
            clearFlightArena(f);
            actor[0]=ModEntities.INFECTED_HOST.get().create(f.level);actor[0].inherit(type.create(f.level));join(f,actor[0],-4,-2);initial[0]=actor[0].position();
            for(int t=1;t<=99;t++)h.runAfterDelay(t,()->raised[0]=Math.max(raised[0],actor[0].getY()-initial[0].y));
        },()->{
            h.assertTrue(actor[0].isAlive()&&actor[0].isNoGravity()&&raised[0]>.6&&actor[0].position().distanceToSqr(initial[0])>.3,"idle infected "+type+" really leaves the floor and moves in loaded air; rise="+raised[0]+" position="+actor[0].position()+" age="+actor[0].tickCount+" gravity="+actor[0].isNoGravity()+" alive="+actor[0].isAlive()+" ai="+actor[0].isNoAi()+" target="+actor[0].getTarget()+" action="+actor[0].nativeAction()+" velocity="+actor[0].getDeltaMovement());
            var proxy=actor[0].appearance();actor[0].advanceAppearanceAnimation();
            if(proxy instanceof net.minecraft.world.entity.ambient.Bat bat)h.assertTrue(!bat.isResting(),"unticked render proxy explicitly starts its flight animation, not its resting pose");
        });}
    @GameTest(template="wall_test",timeoutTicks=600,batch="infected_bat_flight")
    public static void infectedBatActuallyTakesOffWithoutACombatTarget(GameTestHelper h){flight(h,EntityType.BAT);}
    @GameTest(template="wall_test",timeoutTicks=600,batch="infected_phantom_flight")
    public static void infectedPhantomActuallyTakesOffWithoutACombatTarget(GameTestHelper h){flight(h,EntityType.PHANTOM);}
    @GameTest(template="wall_test",timeoutTicks=600,batch="infected_bat_flight_seeds")
    public static void infectedBatsTakeOffAcrossIndependentRoamingSeeds(GameTestHelper h){
        var f=fixture(h);var bats=new java.util.ArrayList<BlacklightInfectedHost>();double[] rise=new double[16];
        f.after(100,()->{
            clearFlightArena(f);
            for(int i=0;i<16;i++){
                var bat=ModEntities.INFECTED_HOST.get().create(f.level);bat.inherit(EntityType.BAT.create(f.level));bat.getRandom().setSeed(i);
                join(f,bat,(i%4)*4-6,(i/4)*4-6);bats.add(bat);
            }
            for(int t=1;t<=99;t++)h.runAfterDelay(t,()->{for(int i=0;i<bats.size();i++)rise[i]=Math.max(rise[i],bats.get(i).getY()-f.origin.getY());});
        },()->{for(int i=0;i<bats.size();i++)h.assertTrue(bats.get(i).isAlive()&&rise[i]>.6,"real no-target bat leaves a loaded open floor; seed="+i+" rise="+rise[i]+" age="+bats.get(i).tickCount+" position="+bats.get(i).position()+" target="+bats.get(i).getTarget());});
    }
    @GameTest(template="wall_test",timeoutTicks=700,batch="automatic_contact_meal")
    public static void realAiFinishesAHighDamageDuelByConsumingAndRestoringItsWoundedBody(GameTestHelper h){var f=fixture(h);BlacklightInfectedHost[] pair=new BlacklightInfectedHost[2];
        f.after(180,()->{
            for(int i=0;i<2;i++){
                var mob=ModEntities.INFECTED_HOST.get().create(f.level);mob.setUUID(new java.util.UUID(0,7001+i));mob.inherit(EntityType.COW.create(f.level));mob.advanceTissue(48000);
                mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(i==0?100000:.01);pair[i]=join(f,mob,-4+i*1.8,-2);
            }
            pair[0].setHealth(pair[0].getMaxHealth()*.25F);pair[0].setTarget(pair[1]);pair[1].setTarget(pair[0]);
        },()->h.assertTrue(pair[0].isAlive()&&!pair[1].isAlive()&&pair[0].tissueInheritance().meals()==1
                &&pair[0].getHealth()/pair[0].getMaxHealth()>.6F&&!TissueCompetition.controls(pair[0]),"actual AI contact secures the final bite, completes one real death/organ settlement and heals its predator; meals="+pair[0].tissueInheritance().meals()+" health="+pair[0].getHealth()+" prey="+pair[1].getHealth()));}
}
