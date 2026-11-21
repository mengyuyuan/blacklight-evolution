package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.EncounterEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GoliathOrganGameTest {
    private static void hit(EncounterMob mob,String bone,float damage){
        var region=mob.hurtRegions(0).stream().filter(r->r.bone().equals(bone)).findFirst().orElseThrow();
        var point=region.boxes().get(0).center;
        mob.invulnerableTime=0;
        if(!mob.hurtAt(mob.level().damageSources().generic(),damage,new HydraHurtContact.Contact(bone,point,new Vec3(0,1,0),com.codex.blacklight.infection.InfectedAttackStyle.CRUSH)))
            throw new AssertionError("Rejected real skin contact: "+bone+" / "+mob.goliathWindow());
    }

    @GameTest(template="blackwatch_arena",batch="goliath_organ_chain",timeoutTicks=480)
    public static void sequentialOrgansHoldTheirStateAcrossTwoAttacksAndReload(GameTestHelper h){
        for(int x=0;x<=15;x++)for(int z=0;z<=28;z++){
            h.setBlock(x,1,z,Blocks.STONE);
            for(int y=2;y<=28;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
        var mob=EncounterEntities.CREATURES.get(EncounterKind.GOLIATH).get().create(h.getLevel());
        mob.setPos(h.absoluteVec(new Vec3(7,2,7)));mob.setYRot(0);mob.yBodyRot=mob.yBodyRotO=0;
        mob.goalSelector.removeAllGoals(g->true);mob.targetSelector.removeAllGoals(g->true);
        h.getLevel().addFreshEntity(mob);
        var pig=h.spawn(EntityType.PIG,new Vec3(7,2,12));pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);pig.setHealth(1000);
        boolean[] reached={false,false,false,false,false};double[] highest={mob.getY()};long[] landedAt={-1};
        h.runAfterDelay(4,()->{
            hit(mob,"torso_lower",50);hit(mob,"torso_lower",50);
            h.assertTrue(mob.isAlive()&&!ConsumeSystem.healthReady(mob),"two armored hits open the head without a premature finisher");
            h.assertTrue(mob.goliathWindow()==GoliathWindow.HEAD,"poise breaks to an opening head");
            hit(mob,"neck_top",100);h.assertTrue(mob.goliathWindow()==GoliathWindow.KNEE,"Neck_2 routes to head damage");
            var skin=mob.hurtRegions(0);
            var contacts=java.util.stream.Stream.of("torso_lower","left_shin").map(bone->{
                var region=skin.stream().filter(r->r.bone().equals(bone)).findFirst().orElseThrow();
                return new WeaponCutContact.Contact(32,"blade",region.boxes().get(0).center,new Vec3(1,0,0),new Vec3(0,1,0)).onBody(bone);
            }).toList();
            var cut=new WeaponCutContact.Report(WeaponCutContact.Kind.SLICE,contacts);
            var selected=HydraHurtContact.reportedContact(skin,cut,mob.goliathWindow().part);
            h.assertTrue(selected!=null&&selected.bone().equals("left_shin"),"a confirmed second contact gets credit on the linked Knee_L");
            mob.invulnerableTime=0;
            h.assertTrue(mob.hurtAt(mob.damageSources().generic(),100,selected),"accepted the swept knee hit");
            h.assertTrue(mob.goliathWindow()==GoliathWindow.LEG_FALL&&mob.broken(0)&&!mob.broken(1),"actual knee break drops to crawl");
            h.assertTrue(mob.hurtRegions(0).stream().filter(r->GoliathLockPart.forSkin(r.bone())==GoliathLockPart.RIGHT_LEG).allMatch(r->r.boxes().isEmpty()),"every severed leg box disappears immediately");
            var saved=new CompoundTag();mob.saveWithoutId(saved);
            var copy=EncounterEntities.CREATURES.get(EncounterKind.GOLIATH).get().create(h.getLevel());copy.load(saved);
            h.assertTrue(copy.goliathWindow()==GoliathWindow.LEG_FALL&&copy.broken(0)&&!GoliathLockPart.RIGHT_LEG.available(copy),"restore preserves missing leg and current fall");
            copy.discard();reached[0]=true;
        });
        h.runAfterDelay(465,()->h.fail("Organ chain stalled: reached="+java.util.Arrays.toString(reached)+" window="+mob.goliathWindow()+" move="+mob.move()+" age="+mob.moveAge(0)+" phase="+mob.phase()+" boss="+mob.position()+" victim="+pig.position()+" ticks="+mob.tickCount+" loaded="+mob.level().isLoaded(mob.blockPosition())+" sight="+mob.hasLineOfSight(pig)));
        h.onEachTick(()->{
            if(!reached[0]||reached[4])return;
            if(!reached[1]&&mob.goliathWindow()==GoliathWindow.NONE&&mob.phase()==1){
                pig.setPos(mob.position().add(0,0,16.4));if(mob.begin(EncounterMove.ERUPTION,pig))reached[1]=true;
            }
            if(!reached[2]&&mob.goliathWindow()==GoliathWindow.ARM){
                h.assertTrue(pig.getHealth()<1000,"swept descending claws hit the victim before planting");
                hit(mob,"left_hand",100);
                h.assertTrue(mob.phase()==2&&mob.goliathWindow()==GoliathWindow.ARM_FALL,"strike the planted wrist to break the arm");
                h.assertTrue(!GoliathLockPart.RIGHT_ARM.available(mob)&&mob.hurtRegions(0).stream().filter(r->GoliathLockPart.forSkin(r.bone())==GoliathLockPart.RIGHT_ARM).allMatch(r->r.boxes().isEmpty()),"broken arm loses all lock and contact surfaces");
                reached[2]=true;
            }
            if(reached[2]&&!reached[3]&&mob.goliathWindow()==GoliathWindow.NONE){
                pig.setPos(mob.position().add(0,0,6));if(mob.begin(EncounterMove.SLAM,pig))reached[3]=true;
            }
            if(reached[3])highest[0]=Math.max(highest[0],mob.getY());
            if(reached[3]&&!reached[4]&&mob.goliathWindow()==GoliathWindow.FACE){
                if(landedAt[0]<0){
                    h.assertTrue(mob.hurtTime==0,"its own back slam never registers a hit on the boss");
                    h.assertTrue(highest[0]>mob.getY()+3 && !mob.level().getBlockState(mob.blockPosition().below()).isAir(),
                            "leap/landing geometry: highest="+highest[0]+" landed="+mob.getY()+" support="+mob.level().getBlockState(mob.blockPosition().below()));
                    landedAt[0]=h.getLevel().getGameTime();
                }
                if(h.getLevel().getGameTime()-landedAt[0]<27)return;
                hit(mob,"jaw",100);h.assertTrue(mob.goliathFinisherReady()&&ConsumeSystem.healthReady(mob),"last head break unlocks consumption");
                var save=new CompoundTag();mob.saveWithoutId(save);var copy=EncounterEntities.CREATURES.get(EncounterKind.GOLIATH).get().create(h.getLevel());copy.load(save);
                h.assertTrue(copy.goliathFinisherReady()&&copy.broken(0)&&copy.broken(1),"terminal state survives reload");
                copy.discard();reached[4]=true;mob.discard();pig.discard();h.succeed();
            }
        });
    }

    @GameTest(template="blackwatch_arena",batch="goliath_armored_death",timeoutTicks=30)
    public static void armorSlowsConventionalDamageWithoutAnImmortalPhase(GameTestHelper h){
        var level=h.getLevel();
        for(int mask:new int[]{0,1,3}){
            var mob=EncounterEntities.CREATURES.get(EncounterKind.GOLIATH).get().create(level);
            var save=new CompoundTag();mob.saveWithoutId(save);
            save.putInt("EncounterBroken",mask);mob.load(save);
            mob.setNoAi(true);mob.setPos(h.absoluteVec(new Vec3(7,2,7)));level.addFreshEntity(mob);
            try{
                float hp=Math.max(1,mob.getMaxHealth()*(mask==0?.64F:mask==1?.31F:.01F));
                mob.setHealth(hp);
                hit(mob,"torso_lower",50);
                h.assertTrue(mob.getHealth()<hp,"ordinary hit crosses former floor with mask "+mask);
                for(int i=0;i<40&&mob.isAlive();i++)hit(mob,"torso_lower",1000);
                h.assertTrue(!mob.isAlive(),"sustained body damage can kill with mask "+mask);
            }finally{mob.discard();}
        }
        h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="goliath_organ_gap",timeoutTicks=130)
    public static void missingStreetBelowClawsPreventsAPlantedArm(GameTestHelper h){
        for(int x=0;x<=15;x++)for(int z=0;z<=28;z++){
            h.setBlock(x,1,z,z<16?Blocks.STONE:Blocks.AIR);
            for(int y=z<16?2:-2;y<25;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
        var mob=EncounterEntities.CREATURES.get(EncounterKind.GOLIATH).get().create(h.getLevel());
        mob.setPos(h.absoluteVec(new Vec3(7,2,7)));
        var state=new CompoundTag();mob.saveWithoutId(state);
        state.putInt("EncounterBroken",1);state.putFloat("EncounterArmor0",0);
        mob.load(state);mob.goalSelector.removeAllGoals(g->true);mob.targetSelector.removeAllGoals(g->true);
        h.getLevel().addFreshEntity(mob);
        var target=h.spawn(EntityType.PIG,new Vec3(7,2,13));target.setNoAi(true);
        h.runAfterDelay(35,()->h.assertTrue(mob.begin(EncounterMove.ERUPTION,target),"crawler commits over the missing street"));
        h.runAfterDelay(82,()->{
            h.assertTrue(mob.move()==EncounterMove.NONE && mob.goliathWindow()==GoliathWindow.NONE && !mob.broken(1),
                "a spike over a hole cannot leave a floating arm embedded");
            target.discard();mob.discard();h.succeed();
        });
    }

    @GameTest(template="blackwatch_arena",batch="goliath_organ_legacy",timeoutTicks=15)
    public static void legacySecondPlateDoesNotBecomeAMissingArm(GameTestHelper h){
        for(int oldMask=0;oldMask<4;oldMask++){
            var mob=EncounterEntities.CREATURES.get(EncounterKind.GOLIATH).get().create(h.getLevel());
            var tag=new CompoundTag();mob.saveWithoutId(tag);tag.remove("GoliathWindow");
            tag.putInt("EncounterBroken",oldMask);tag.putInt("EncounterPhase",2);tag.putFloat("EncounterArmor1",0);
            mob.load(tag);
            h.assertTrue(mob.broken(0)==((oldMask&1)!=0) && !mob.broken(1) && !mob.goliathFinisherReady(),
                    "old pair of knee plates cannot skip the wrist for mask "+oldMask);
            mob.discard();
        }
        h.succeed();
    }
}
