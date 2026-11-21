package com.codex.blacklight.combat;

import com.codex.blacklight.entity.QuillOrgan;
import com.codex.blacklight.power.CombatScale;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class QuillPriorityGameTest {
    @GameTest(template="wall_test",timeoutTicks=80,batch="quill_drain_and_reserve")
    public static void livingMarkTracksStrengthAndRespectsAnOpening(GameTestHelper h){
        var p=join(h,"QuillReserve");var mobs=new ArrayList<Mob>();
        p.setPos(h.absolutePos(new BlockPos(5,130,5)).getCenter());
        var state=QuillHarvest.state(p);state.infect();state.setPrototypeMode(true);
        state.unlock(PowerForm.BACKSPINES);state.activate(PowerForm.BACKSPINES);state.addEvolutionPoints(500000);
        p.setData(ModAttachments.EVOLUTION_MILESTONES,new com.codex.blacklight.state.EvolutionMilestones(1));
        var cow=spawn(p,mobs,EntityType.COW,3,0,1000,1000);
        for(int i=0;i<10;i++)QuillHarvest.plant(p,cow,cow.getBoundingBox().getCenter(),0);
        var root=(QuillOrgan)h.getLevel().getEntity(QuillHarvest.mark(cow).getUUID("organ"));
        try {
            cow.tickCount=20;QuillHarvest.tick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(cow));
            h.assertTrue(cow.getHealth()==999,"10 layers at base remove 1 / 1000: "+cow.getHealth());
            h.assertTrue(com.codex.blacklight.power.InfiniteEvolutionSystem.purchase(p,com.codex.blacklight.power.EvolutionAttribute.STRENGTH,20)==20,"20 strength ranks purchased");
            cow.tickCount=40;QuillHarvest.tick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(cow));
            h.assertTrue(cow.getHealth()==997,"same mark removes 2 / 1000 after upgrading: "+cow.getHealth());
            var volley=UUID.randomUUID();
            for(int i=0;i<2;i++) {
                var shot=new com.codex.blacklight.entity.BackspineQuill(h.getLevel(),p,10000,0);
                shot.setVolleyId(volley);cow.invulnerableTime=0;
                cow.hurt(p.damageSources().thrown(shot,p),10000);shot.discard();
                h.assertTrue(cow.getHealth()==1 && cow.isAlive(),"all pellets in the first lethal volley preserve one HP");
            }
            cow.tickCount=60;QuillHarvest.tick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(cow));
            h.assertTrue(cow.getHealth()==1 && root.phase()==QuillOrgan.ROOT,"parasite leaves the host for manual consumption");
            h.assertTrue(state.buyCombatSkill(com.codex.blacklight.power.CombatSkill.QUILL_HARVEST),"learn automatic harvest");
            cow.tickCount=80;QuillHarvest.tick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(cow));
            h.assertTrue(root.phase()==QuillOrgan.EXTRACT,"the next drain starts the automatic harvest");
            releaseHarvest(root);
            h.assertTrue((!cow.isAlive() || cow.isRemoved()) && root.phase()==QuillOrgan.RETURN && root.supply()>0,
                    "automatic harvest extracts once and sends the payload back");
            h.succeed();
        } finally {root.discard();cow.discard();h.getLevel().getServer().getPlayerList().remove(p);}
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="goliath_parasite_damage")
    public static void implantedQuillsDrainThroughOldFloorsWithoutKnockdowns(GameTestHelper h){
        var player=join(h,"GoliathQuill");
        var level=h.getLevel();
        var boss=com.codex.blacklight.registry.EncounterEntities.CREATURES.get(com.codex.blacklight.entity.EncounterKind.GOLIATH).get().create(level);
        try {
            var state=QuillHarvest.state(player);state.infect();state.setPrototypeMode(true);
            player.setPos(h.absolutePos(new BlockPos(5,130,5)).getCenter());
            boss.setPos(player.position().add(5,0,0));boss.setNoAi(true);level.addFreshEntity(boss);
            for(int i=0;i<10;i++)QuillHarvest.plant(player,boss,boss.position().add(0,1,0),0);
            float max=boss.getMaxHealth();
            double expected=max*QuillHarvest.drainPercent(player,10)/100;
            for(double ratio:new double[]{1,.64,.31,.01}){
                boss.setHealth((float)(max*ratio));
                float before=boss.getHealth();
                boss.tickCount+=20;
                QuillHarvest.tick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(boss));
                double loss=before-boss.getHealth();
                h.assertTrue(Math.abs(loss-expected)<Math.max(.0001,Math.ulp(max)*2),
                        "Goliath parasite at "+ratio+" HP: expected "+expected+", actual "+loss);
            }
            boss.setHealth(max);
            for(int i=0;i<100;i++){
                boss.tickCount+=20;QuillHarvest.tick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(boss));
                h.assertTrue(boss.goliathWindow()==GoliathWindow.NONE,"parasite ticks must never open another stagger");
            }
            boss.setHealth((float)(expected*.5));boss.tickCount+=20;QuillHarvest.tick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(boss));
            h.assertTrue(ConsumeLastChance.awaitingFinisher(boss)&&ConsumeSystem.healthReady(player,boss),
                    "first lethal parasite tick keeps a usable execution opportunity");
            h.assertTrue(QuillHarvest.available(player,boss),"parasite can be recalled after a lethal opening");
            var root=(QuillOrgan)level.getEntity(QuillHarvest.mark(boss).getUUID("organ"));
            h.assertTrue(QuillHarvest.finish(player,boss,root)&&!boss.isAlive(),"parasite execution reaches accepted death");
            h.succeed();
        }finally{
            var mark=QuillHarvest.mark(boss);
            if(mark.hasUUID("organ") && level.getEntity(mark.getUUID("organ")) instanceof QuillOrgan organ)organ.discard();
            boss.discard();level.getServer().getPlayerList().remove(player);
        }
    }
    @GameTest(template="wall_test",timeoutTicks=420,batch="goliath_parasite_recovery")
    public static void plantedQuillsKeepDrainingAfterOneRealStagger(GameTestHelper h){
        var player=join(h,"QuillRecovery");
        var level=h.getLevel();
        var state=QuillHarvest.state(player);
        state.infect();state.setPrototypeMode(true);
        player.setPos(h.absolutePos(new BlockPos(5,8,5)).getCenter());
        var boss=com.codex.blacklight.registry.EncounterEntities.CREATURES.get(com.codex.blacklight.entity.EncounterKind.GOLIATH).get().create(level);
        boss.setPos(player.position().add(4,0,0));
        boss.setNoAi(true);boss.setNoGravity(true);level.addFreshEntity(boss);
        for(int i=0;i<10;i++)QuillHarvest.plant(player,boss,boss.position().add(0,1,0),0);
        boss.hurt(level.damageSources().generic(),50);
        boss.invulnerableTime=0;
        boss.hurt(level.damageSources().generic(),50);
        h.assertTrue(boss.goliathWindow()==GoliathWindow.HEAD,"two direct hits cause real stagger");
        long start=level.getGameTime();
        float initial=boss.getHealth();
        float perPulse=boss.getMaxHealth()*(float)QuillHarvest.drainPercent(player,10)/100;
        h.onEachTick(()->{
            if(level.getGameTime()-start>62)
                h.assertTrue(boss.goliathWindow()==GoliathWindow.NONE,"the same parasites reopened a recovered stagger");
        });
        h.runAfterDelay(390,()->{
            try{
                h.assertTrue(boss.isAlive() && initial-boss.getHealth()>=18*perPulse,"parasites remain active across recovery");
                h.succeed();
            }finally{
                var mark=QuillHarvest.mark(boss);
                if(mark.hasUUID("organ") && level.getEntity(mark.getUUID("organ")) instanceof QuillOrgan organ)organ.discard();
                boss.discard();level.getServer().getPlayerList().remove(player);
            }
        });
    }

    @GameTest(template="wall_test",timeoutTicks=125,batch="goliath_followup_swing")
    public static void independentPrimaryAttackCanFinishAnArmoredGoliath(GameTestHelper h){
        checkGoliathFollowup(h,false);
    }

    @GameTest(template="wall_test",timeoutTicks=125,batch="goliath_followup_parasite")
    public static void primaryAttackCanFinishTheOpeningLeftByQuills(GameTestHelper h){
        checkGoliathFollowup(h,true);
    }

    private static void checkGoliathFollowup(GameTestHelper h,boolean parasite){
        var player=join(h,parasite?"QuillFollowup":"MeleeFollowup");
        var level=h.getLevel();
        var boss=com.codex.blacklight.registry.EncounterEntities.CREATURES.get(com.codex.blacklight.entity.EncounterKind.GOLIATH).get().create(level);
        var state=QuillHarvest.state(player);
        state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.HAMMERFISTS);state.activate(PowerForm.HAMMERFISTS);
        boss.moveTo(h.absolutePos(new BlockPos(6,90,6)).getCenter());
        boss.setNoAi(true);boss.setNoGravity(true);level.addFreshEntity(boss);
        player.setNoGravity(true);player.setPos(boss.position());
        Runnable swing=()->{
            var torso=boss.hurtRegions(0).stream().filter(r->r.bone().equals("torso_lower")).findFirst().orElseThrow().boxes().get(0);
            var surface=torso.nearest(torso.center.add(0,0,-50));
            player.setPos(surface.add(0,-1,-1));player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);player.setOnGround(true);
            player.setXRot(0);player.setYRot(0);player.setYHeadRot(0);player.yBodyRot=player.yBodyRotO=0;
            WeaponCombatSystem.request(player,false);
            h.assertTrue(WeaponCombatSystem.busy(player),"server accepted a primary strike");
            // The embedded test client sends no movement packets to drive a player's combat clock.
            var move=player.getData(ModAttachments.WEAPON_VISUAL).spec();
            for(int tick=0;tick<move.duration+12 && WeaponCombatSystem.busy(player);tick++){
                player.tickCount++;
                WeaponCombatSystem.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
            }
        };
        h.runAfterDelay(3,()->{
            boss.setHealth(.02F);
            if(parasite){
                for(int i=0;i<10;i++)QuillHarvest.plant(player,boss,boss.position().add(0,1,0),0);
                boss.tickCount+=20-boss.tickCount%20;
                QuillHarvest.tick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(boss));
            }else swing.run();
        });
        h.runAfterDelay(50,()->{
            h.assertTrue(boss.getHealth()==1 && ConsumeLastChance.awaitingFinisher(boss),"first lethal contact offers execution, hp="+boss.getHealth());
            swing.run();
        });
        h.runAfterDelay(98,()->{
            try{
                h.assertTrue(!boss.isAlive() && ((com.codex.blacklight.mixin.LivingDeathAccess)(Mob)boss).blacklight$deathAccepted(),
                        "an independent primary strike finishes the boss, hp="+boss.getHealth());
                h.succeed();
            }finally{
                WeaponCombatSystem.cancel(player);
                var mark=QuillHarvest.mark(boss);
                if(mark.hasUUID("organ") && level.getEntity(mark.getUUID("organ")) instanceof QuillOrgan organ)organ.discard();
                boss.discard();level.getServer().getPlayerList().remove(player);
            }
        });
    }

    @GameTest(template="wall_test",timeoutTicks=80,batch="quill_hunter_damage")
    public static void supremeHunterDistinguishesQuillsFromThrownDebris(GameTestHelper h){
        var p=join(h,"HunterDamage");
        var level=h.getLevel();
        var boss=com.codex.blacklight.registry.ModEntities.SUPREME_HUNTER.get().create(level);
        var shot=new com.codex.blacklight.entity.BackspineQuill(level,p,1,0);
        var shard=new QuillOrgan(com.codex.blacklight.registry.ModEntities.QUILL_ORGAN.get(),level);
        var block=new com.codex.blacklight.entity.ThrownBlock(level,p,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        try {
            p.setPos(h.absolutePos(new BlockPos(5,130,5)).getCenter());p.setOnGround(true);
            var state=QuillHarvest.state(p);state.infect();state.setPrototypeMode(true);
            state.unlock(PowerForm.BACKSPINES);state.activate(PowerForm.BACKSPINES);
            p.setData(ModAttachments.EVOLUTION_MILESTONES,new com.codex.blacklight.state.EvolutionMilestones(1));
            p.setData(ModAttachments.INFINITE_EVOLUTION,com.codex.blacklight.state.InfiniteProgress.EMPTY.buy(com.codex.blacklight.power.EvolutionAttribute.STRENGTH,1312));
            boss.setPos(p.position().add(3,0,0));boss.setNoAi(true);level.addFreshEntity(boss);
            float max=boss.getMaxHealth();
            var sources=List.of(p.damageSources().playerAttack(p),p.damageSources().thrown(shot,p),
                    p.damageSources().thrown(shard,p),p.damageSources().thrown(block,p));
            float[] loss=new float[sources.size()];
            for(int i=0;i<loss.length;i++){
                boss.setHealth(max);boss.invulnerableTime=0;
                h.assertTrue(boss.hurt(sources.get(i),1),"accepted damage source "+i);
                loss[i]=max-boss.getHealth();
            }
            org.slf4j.LoggerFactory.getLogger(QuillPriorityGameTest.class).info("Supreme Hunter HP loss at equal base damage, rank 1312 (melee, shoulder, orbit, debris): {}",java.util.Arrays.toString(loss));
            h.assertTrue(loss[0]>0 && Math.abs(loss[1]-loss[0])<.01 && Math.abs(loss[2]-loss[0])<.01
                    && loss[3]>loss[0],"equal base melee, shoulder, orbit and debris: "+java.util.Arrays.toString(loss));
            for(int i=0;i<2;i++)QuillHarvest.plant(p,boss,boss.getBoundingBox().getCenter(),0);
            for(boolean inAir:List.of(false,true)){
                if(inAir){p.setOnGround(false);p.setPos(p.position().add(0,4,0));}
                boss.setHealth(max);boss.tickCount+=20;boss.invulnerableTime=0;
                var velocity=p.getDeltaMovement();
                QuillHarvest.tick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(boss));
                double drained=max-boss.getHealth();
                h.assertTrue(Math.abs(drained-max*.01332)<.01 && p.getDeltaMovement().equals(velocity),
                        "two parasite layers at rank 1312: drained="+drained+" max="+max+" airborne="+inAir);
            }
            h.succeed();
        } finally {
            var mark=QuillHarvest.mark(boss);
            if(mark.hasUUID("organ") && level.getEntity(mark.getUUID("organ")) instanceof QuillOrgan q)q.discard();
            shot.discard();shard.discard();block.discard();boss.discard();level.getServer().getPlayerList().remove(p);
        }
    }

    @GameTest(template="wall_test",timeoutTicks=80,batch="quill_hunter_harvest")
    public static void supremeHunterHarvestClosesTheEncounterOnce(GameTestHelper h){
        checkHunterHarvest(h,false);
    }

    @GameTest(template="wall_test",timeoutTicks=80,batch="quill_hunter_veto")
    public static void aVetoedHunterHarvestStopsUntilManualRecall(GameTestHelper h){
        checkHunterHarvest(h,true);
    }

    private static void checkHunterHarvest(GameTestHelper h,boolean vetoFirst){
        var p=join(h,vetoFirst?"HunterVeto":"HunterHarvest");
        var level=h.getLevel();
        var boss=com.codex.blacklight.registry.ModEntities.SUPREME_HUNTER.get().create(level);
        QuillOrgan root=null;
        var veto=new Object(){
            @net.neoforged.bus.api.SubscribeEvent public void cancel(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event){
                if(event.getEntity()==boss && ConsumeLastChance.executing(boss)){
                    event.setCanceled(true);boss.setHealth(1);
                }
            }
        };
        try {
            p.setPos(h.absolutePos(new BlockPos(5,130,5)).getCenter());p.setOnGround(true);
            var state=QuillHarvest.state(p);state.infect();state.setPrototypeMode(true);
            state.unlock(PowerForm.BACKSPINES);state.activate(PowerForm.BACKSPINES);state.addEvolutionPoints(5000);
            h.assertTrue(state.buyCombatSkill(com.codex.blacklight.power.CombatSkill.QUILL_HARVEST),"automatic harvest unlocked");
            boss.setPos(p.position().add(4,0,0));level.addFreshEntity(boss);boss.setHealth(boss.getMaxHealth());
            QuillHarvest.plant(p,boss,boss.getBoundingBox().getCenter(),0);
            root=(QuillOrgan)level.getEntity(QuillHarvest.mark(boss).getUUID("organ"));
            boss.hurt(p.damageSources().playerAttack(p),boss.getMaxHealth()*100);
            h.assertTrue(ConsumeLastChance.awaitingFinisher(boss),"first lethal hit reserves the living boss at one HP");
            boss.tickCount=20;
            QuillHarvest.tick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(boss));
            h.assertTrue(root.phase()==QuillOrgan.EXTRACT,"automatic extraction starts while the boss awaits a finisher");
            if(vetoFirst){
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(veto);
                int ep=state.getEvolutionPoints();
                releaseHarvest(root);
                h.assertTrue(root.phase()==QuillOrgan.ROOT && root.supply()==0 && ep==state.getEvolutionPoints()
                        && !boss.isBeingConsumed() && !boss.isNoAi(),"a veto returns the living boss and pays nothing");
                for(int i=0;i<5;i++){boss.tickCount+=20;QuillHarvest.tick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(boss));}
                h.assertTrue(root.phase()==QuillOrgan.ROOT,"subsequent pulses do not replay a vetoed extraction");
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(veto);
                ConsumeSystem.request(p);
                h.assertTrue(root.phase()==QuillOrgan.EXTRACT,"E can retry when the veto is lifted");
            }
            releaseHarvest(root);
            h.assertTrue(!boss.isAlive() && root.phase()==QuillOrgan.RETURN && root.supply()>0 && !boss.isBeingConsumed(),
                    "one execution accepts boss death, releases its claim and sends a paid quill home");
            int paid=state.getEvolutionPoints();
            for(int i=0;i<5;i++){boss.tickCount+=20;QuillHarvest.tick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(boss)); QuillHarvest.recall(p);}
            h.assertTrue(state.getEvolutionPoints()==paid,"a settled boss cannot award evolution points twice");
            h.assertTrue(root.phase()==QuillOrgan.RETURN && !QuillHarvest.mark(boss).hasUUID("owner"),
                    "following drain pulses and E cannot extract a second time");
            h.succeed();
        } finally {
            if(vetoFirst)net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(veto);
            if(root!=null)root.discard();
            boss.discard();level.getServer().getPlayerList().remove(p);
        }
    }

    @GameTest(template="wall_test",timeoutTicks=80,batch="quill_impact_harvest")
    public static void lethalShoulderAndOrbitHitsBringBackOneCarrierEach(GameTestHelper h){
        var p=join(h,"QuillFatal");var level=h.getLevel();
        var mobs=new ArrayList<Mob>();var scraps=new ArrayList<Entity>();
        try{
            p.setPos(h.absolutePos(new BlockPos(5,140,5)).getCenter());
            var state=QuillHarvest.state(p);state.infect();state.setPrototypeMode(true);
            state.unlock(PowerForm.BACKSPINES);state.activate(PowerForm.BACKSPINES);
            for(boolean orbit:List.of(false,true)){
                var host=spawn(p,mobs,EntityType.COW,0,orbit?8:3,10,1);
                var center=host.getBoundingBox().getCenter();
                if(orbit){
                    QuillHarvest.plant(p,host,center,2);
                    var shard=new QuillOrgan(com.codex.blacklight.registry.ModEntities.QUILL_ORGAN.get(),level);
                    var data=shard.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                    data.putInt("phase",QuillOrgan.STRIKE);data.putLong("age",0);data.putUUID("owner",p.getUUID());data.putUUID("target",host.getUUID());
                    data.putInt("life",100);data.putInt("totalLife",100);data.putInt("supply",0);data.putInt("targetRegion",-1);
                    shard.load(data);shard.setPos(center);level.addFreshEntity(shard);scraps.add(shard);shard.tick();
                }else{
                    var shot=new com.codex.blacklight.entity.BackspineQuill(level,p,8,0);
                    shot.setPos(center.add(0,0,-1.4));shot.setDeltaMovement(0,0,3);level.addFreshEntity(shot);scraps.add(shot);shot.tick();
                }
                h.assertTrue(!host.isAlive(),"real projectile kills the host, orbit="+orbit+" health="+host.getHealth()+" max="+host.getMaxHealth());
                var roots=level.getEntitiesOfClass(QuillOrgan.class,host.getBoundingBox().inflate(2),q->q.phase()==QuillOrgan.EXTRACT);
                h.assertTrue(roots.size()==1 && roots.get(0).hostId()==host.getId() && roots.get(0).supply()>0,
                        "one paid extraction remains bound to the dead host, orbit="+orbit);
                var root=roots.get(0);scraps.add(root);
                int paid=state.getEvolutionPoints();
                QuillHarvest.recall(p);releaseHarvest(root);
                h.assertTrue(root.phase()==QuillOrgan.RETURN && state.getEvolutionPoints()==paid,"carrier returns without a second payment");
            }
            h.succeed();
        }finally{for(var e:scraps)e.discard();for(var m:mobs)m.discard();level.getServer().getPlayerList().remove(p);}
    }

    @GameTest(template="wall_test",timeoutTicks=80,batch="quill_live_extract")
    public static void liveRecallPuncturesBeforeRewardAndDoesNotKillTheHost(GameTestHelper h){
        var p=join(h,"QuillExtract"); var mobs=new ArrayList<Mob>();
        QuillOrgan organ=null;
        try {
            p.setPos(h.absolutePos(new BlockPos(5,140,5)).getCenter());
            var state=QuillHarvest.state(p);
            state.infect(); state.setPrototypeMode(true);
            state.unlock(PowerForm.BACKSPINES); state.activate(PowerForm.BACKSPINES);
            var victim=spawn(p,mobs,EntityType.COW,0,3,10,1);
            QuillHarvest.plant(p,victim,victim.getBoundingBox().getCenter(),0);
            QuillHarvest.recall(p);
            organ=(QuillOrgan)p.serverLevel().getEntity(QuillHarvest.mark(victim).getUUID("organ"));
            h.assertTrue(organ!=null && organ.phase()==QuillOrgan.EXTRACT,"recall did not start");
            float hp=victim.getHealth(); int ep=state.getEvolutionPoints();
            var saved=organ.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            saved.putLong("age",QuillOrgan.PIERCE_TICK);organ.load(saved);organ.tick();
            h.assertTrue(victim.getHealth()>0 && victim.getHealth()<hp,"no nonlethal puncture on live host");
            h.assertTrue(state.getEvolutionPoints()==ep,"premature reward");
            releaseHarvest(organ);
            h.assertTrue(organ.phase()==QuillOrgan.RETURN && victim.isRemoved(),"failed to return to owner");
            h.succeed();
        } finally {
            if(organ!=null)organ.discard();
            for(var mob:mobs)mob.discard();
            p.serverLevel().getServer().getPlayerList().remove(p);
        }
    }

    private static void releaseHarvest(QuillOrgan root){
        var saved=root.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        saved.putLong("age",QuillOrgan.RELEASE_TICK);
        root.load(saved);root.tick();
    }

    private static Mob spawn(ServerPlayer p, List<Mob> spawned, EntityType<? extends Mob> type,
                             double x, double z, double maximum, float health) {
        var mob=type.create(p.serverLevel());
        mob.setPos(p.position().add(x,0,z));
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maximum);
        p.serverLevel().addFreshEntity(mob);
        mob.setHealth(health);
        spawned.add(mob);
        return mob;
    }

    @GameTest(template="wall_test",timeoutTicks=80,batch="quill_glide_input")
    public static void glideUseSelectsBulletdiveOnceWhileOrdinaryAirUseStillFiresQuills(GameTestHelper h){
        var p=join(h,"QuillGlide");
        try{
            p.setPos(h.absolutePos(new BlockPos(4,20,4)).getCenter());
            p.setOnGround(false);
            var state=QuillHarvest.state(p);
            state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.BACKSPINES);state.activate(PowerForm.BACKSPINES);
            state.addEvolutionPoints(10000);state.addBiomass(40);
            var movement=p.getData(ModAttachments.MOVEMENT_STATE);
            movement.setMode(com.codex.blacklight.movement.MovementState.Mode.GLIDE);
            WeaponCombatSystem.acceptInput(p,false,true,false,true);
            h.assertTrue(BackspineCombat.busy(p)&&!DevastatorSystem.busy(p),"gliding before learning Bulletdive keeps the shoulder heavy");
            WeaponCombatSystem.cancelHeldInput(p);
            state.buyMovementUpgrade(com.codex.blacklight.power.MovementUpgrade.GLIDE_1);
            h.assertTrue(state.buyCombatSkill(com.codex.blacklight.power.CombatSkill.BULLETDIVE_DROP),"fixture learns Bulletdive");
            movement.setMode(com.codex.blacklight.movement.MovementState.Mode.NORMAL);
            WeaponCombatSystem.acceptInput(p,false,true,false,false);
            h.assertTrue(BackspineCombat.busy(p)&&!DevastatorSystem.busy(p),"use during a plain jump begins a quill heavy");
            movement.setMode(com.codex.blacklight.movement.MovementState.Mode.GLIDE);
            WeaponCombatSystem.acceptInput(p,false,true,false,true);
            h.assertTrue(BackspineCombat.busy(p)&&!DevastatorSystem.busy(p),"entering glide with use already held does not convert a shoulder attack");
            WeaponCombatSystem.cancelHeldInput(p);
            movement.setMode(com.codex.blacklight.movement.MovementState.Mode.NORMAL);
            p.setDeltaMovement(0,-.28,0);
            com.codex.blacklight.movement.PrototypeMovementSystem.acceptInput(p,false,false,1,0,false,false);
            com.codex.blacklight.movement.PrototypeMovementSystem.acceptInput(p,true,false,1,0,false,false);
            for(int tick=0;tick<8;tick++){
                p.tickCount++;
                com.codex.blacklight.movement.PrototypeMovementSystem.onPlayerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
            }
            h.assertTrue(movement.mode()==com.codex.blacklight.movement.MovementState.Mode.GLIDE,"holding jump during descent establishes an actual glide");
            WeaponCombatSystem.acceptInput(p,false,false,false,true);
            WeaponCombatSystem.acceptInput(p,false,true,false,true);
            var dive=DevastatorSystem.snapshot(p);
            h.assertTrue(dive!=null&&dive.move().equals(WeaponMove.BULLETDIVE_DROP.id)&&dive.phase()==DevastatorSystem.CHARGE
                    &&!BackspineCombat.busy(p),"a fresh use press while gliding must charge Bulletdive instead of the quill air heavy");
            WeaponCombatSystem.acceptInput(p,false,true,false,true);
            WeaponCombatSystem.acceptInput(p,false,false,false,true);
            for(int tick=0;tick<24&&DevastatorSystem.snapshot(p).phase()==DevastatorSystem.CHARGE;tick++){
                p.tickCount++;
                DevastatorSystem.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
            }
            var released=DevastatorSystem.snapshot(p);
            h.assertTrue(released!=null&&released.session()==dive.session()&&released.phase()==DevastatorSystem.RELEASE
                    &&!BackspineCombat.busy(p),"releasing use commits the same dive without also releasing a volley");
            h.succeed();
        }finally{WeaponCombatSystem.cancelHeldInput(p);h.getLevel().getServer().getPlayerList().remove(p);}
    }
    private static ServerPlayer join(GameTestHelper h,String name){
        var level=h.getLevel();
        var profile=new GameProfile(UUID.randomUUID(),name);
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false);
        var p=new ServerPlayer(level.getServer(),level,profile,cookie.clientInformation());
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection,p,cookie);
        return p;
    }

    @GameTest(template="wall_test", timeoutTicks=80)
    public static void runningFireSpreadsParasitesAndRecallsAtRange(GameTestHelper h) {
        // A full terrain chunk can still be waiting for its entity section to start ticking.
        var start=h.absolutePos(new BlockPos(5,180,5));
        var level=h.getLevel();
        var chunks=new ArrayList<net.minecraft.world.level.ChunkPos>();
        var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
        for(int x=start.getX()>>4;x<=(start.getX()+97)>>4;x++){
            for(int z=start.getZ()>>4;z<=(start.getZ()+3)>>4;z++){
                var chunk=new net.minecraft.world.level.ChunkPos(x,z);
                chunks.add(chunk);
                if(level.setChunkForced(x,z,true))forced.add(chunk);
            }
        }
        h.startSequence().thenWaitUntil(()->{
            for(var chunk:chunks)
                h.assertTrue(level.isPositionEntityTicking(chunk.getBlockAt(0,start.getY(),0)),"waiting for distant quill hosts to tick");
        }).thenExecute(()->{
            try{checkRunningFire(h,start);}
            finally{for(var chunk:forced)level.setChunkForced(chunk.x,chunk.z,false);}
        }).thenSucceed();
    }

    private static void checkRunningFire(GameTestHelper h,BlockPos start) {
        var level=h.getLevel();
        var p=join(h,"QuillSpread");
        p.setPos(start.getCenter());
        var mobs=new ArrayList<Mob>();
        var shard=new QuillOrgan(com.codex.blacklight.registry.ModEntities.QUILL_ORGAN.get(),level);
        try {
            var state=QuillHarvest.state(p);
            state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.BACKSPINES);state.activate(PowerForm.BACKSPINES);
            var cow=spawn(p,mobs,EntityType.COW,64,0,20,20);
            QuillHarvest.plant(p,cow,cow.getBoundingBox().getCenter(),0);
            var root=(QuillOrgan)level.getEntity(QuillHarvest.mark(cow).getUUID("organ"));
            cow.setPos(p.position().add(65,0,0));ConsumeSystem.request(p);
            h.assertTrue(root.phase()==QuillOrgan.ROOT,"65 blocks is outside the base radius");
            cow.setPos(p.position().add(64,0,0));ConsumeSystem.request(p);
            h.assertTrue(root.phase()==QuillOrgan.EXTRACT,"E starts a harvest at 64 blocks");
            state.addEvolutionPoints(1000);
            h.assertTrue(state.buyCombatSkill(com.codex.blacklight.power.CombatSkill.QUILL_REACH),"reach upgrade purchased");
            cow.setPos(p.position().add(96,0,0));
            h.assertTrue(QuillHarvest.available(p,cow),"upgraded radius includes 96 blocks");
            cow.setPos(p.position().add(97,0,0));
            h.assertTrue(!QuillHarvest.available(p,cow),"upgraded radius excludes 97 blocks");

            var weak=spawn(p,mobs,EntityType.HUSK,3,0,100,10);
            var distant=spawn(p,mobs,EntityType.ZOMBIFIED_PIGLIN,58,0,100,60);
            h.assertTrue(distant.getTarget()==null && p.getLastHurtMob()==null && p.getLastHurtByMob()==null,"no aggro or combat history");
            QuillHarvest.plant(p,weak,weak.getBoundingBox().getCenter(),0);
            for(boolean orbit:List.of(false,true))
                h.assertTrue(QuillHarvest.attackTarget(p,64,orbit).target()==distant,"fresh enemy outranks a wounded host");
            CombatTargeting.requestLock(p,weak.getId());
            h.assertTrue(QuillHarvest.attackTarget(p,64,false).target()==weak,"lock overrides spread");
            CombatTargeting.requestLock(p,-1);

            shard.setPos(p.getEyePosition());
            var tag=shard.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            tag.putUUID("owner",p.getUUID());tag.putInt("phase",QuillOrgan.ORBIT);
            tag.putDouble("orbitPhase",0);
            tag.putInt("life",240);tag.putInt("totalLife",240);tag.putInt("supply",8);tag.putInt("initial",8);
            shard.load(tag);level.addFreshEntity(shard);shard.tick();
            h.assertTrue(distant.getUUID().equals(shard.strikeTarget()),"orbit actually launches toward the unmarked enemy at 58 blocks");
            var spare=spawn(p,mobs,EntityType.HUSK,7,3,100,90);
            h.assertTrue(QuillHarvest.attackTarget(p,64,false).target()==spare,"shoulder covers the next enemy while the orbit quill travels");
            var inFlight=shard.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            inFlight.putLong("age",21);shard.load(inFlight);
            for(int t=0;t<45 && shard.phase()==QuillOrgan.STRIKE;t++)shard.tick();
            h.assertTrue(QuillHarvest.mark(distant).getInt("layers")==1,"a long-range orbit hit plants a first layer");
            QuillHarvest.plant(p,spare,spare.getBoundingBox().getCenter(),1);
            h.assertTrue(QuillHarvest.attackTarget(p,64,false).target()==weak,"after coverage return to the lowest health host");
            distant.invulnerableTime=0;
            shard.load(inFlight);
            for(int t=0;t<45 && shard.phase()==QuillOrgan.STRIKE;t++)shard.tick();
            h.assertTrue(QuillHarvest.mark(distant).getInt("layers")==2,"a second accepted orbit hit adds another layer");
        } finally {
            shard.discard();CombatTargeting.requestLock(p,-1);
            for(var mob:mobs){
                var mark=QuillHarvest.mark(mob);
                if(mark.hasUUID("organ") && level.getEntity(mark.getUUID("organ")) instanceof QuillOrgan organ)organ.discard();
                mob.discard();
            }
            level.getServer().getPlayerList().remove(p);
        }
    }

    @GameTest(template="wall_test",timeoutTicks=180)
    public static void sprintingAwayHitsTheUnmarkedEnemyBehindThePlayer(GameTestHelper h){
        var level=h.getLevel();
        var p=join(h,"RearQuill");
        var origin=h.absolutePos(new BlockPos(5,275,5)).offset(0,0,-23);
        var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
        for(int x=(origin.getX()-16)>>4;x<=(origin.getX()+16)>>4;x++)
        for(int z=(origin.getZ()-16)>>4;z<=(origin.getZ()+64)>>4;z++){
            var chunk=new net.minecraft.world.level.ChunkPos(x,z);
            if(level.setChunkForced(chunk.x,chunk.z,true))forced.add(chunk);
            level.getChunk(chunk.x,chunk.z);
        }
        var at=origin.getCenter();
        p.connection.teleport(at.x,at.y,at.z,180,0);p.setNoGravity(true);
        h.runAfterDelay(6,()->{
            p.setPos(at);
            p.setYRot(180);p.setYHeadRot(180);p.setOnGround(true);p.setSprinting(true);
            var state=QuillHarvest.state(p);
            state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.BACKSPINES);state.activate(PowerForm.BACKSPINES);
            state.addBiomass(100);
            p.getData(ModAttachments.MOVEMENT_STATE).acceptInput(false,false,1,0,true,false);
            var mobs=new ArrayList<Mob>();
            var near=spawn(p,mobs,EntityType.HUSK,3,-2,100,8);
            var rear=spawn(p,mobs,EntityType.PIGLIN,0,46,100,80);
            near.setNoGravity(true);rear.setNoGravity(true);
            h.runAfterDelay(15,()->h.assertTrue(level.getEntity(rear.getId())==rear && level.getEntity(near.getId())==near,
                    "tracked near="+(level.getEntity(near.getId())==near)+" rear="+(level.getEntity(rear.getId())==rear)
                    +" rear removed="+rear.getRemovalReason()+" chunk="+rear.chunkPosition()+" owner="+p.position()));
            h.startSequence().thenWaitUntil(()->h.assertTrue(level.getEntity(rear.getId())==rear && level.getEntity(near.getId())==near,"both enemies must be tracked before shooting")).thenExecute(()->{
            p.setPos(at);p.setOnGround(true);p.setSprinting(true);
            QuillHarvest.plant(p,near,near.getBoundingBox().getCenter(),0);
            BackspineCombat.input(p,true,false);BackspineCombat.input(p,false,false);
            h.assertTrue(p.getData(ModAttachments.QUILL_AIM_TARGET)==rear.getId(),"running aim="+p.getData(ModAttachments.QUILL_AIM_TARGET)+" rear="+rear.getId()
                    +" tracked="+(level.getEntity(rear.getId())==rear)+" visible="+CombatTargeting.visibleWithinRange(p,rear,64)
                    +" nearLayers="+QuillHarvest.mark(near).getInt("layers")+" distance="+rear.distanceTo(p));
            for(int i=0;i<8;i++){p.tickCount++;p.setPos(p.position().add(0,0,-.18));BackspineCombat.tick(p);}
            var shots=level.getEntitiesOfClass(com.codex.blacklight.entity.BackspineQuill.class,p.getBoundingBox().inflate(4));
            h.assertTrue(shots.stream().anyMatch(shot->rear.getUUID().equals(shot.aimTarget()) && shot.getDeltaMovement().z>0),
                    "shoulder releases backwards while the player advances forwards");
            }).thenExecuteAfter(41,()->{
                try{
                    h.assertTrue(QuillHarvest.mark(rear).getInt("layers")>0,"unlocked shoulder physically reaches a fresh target 46 blocks behind");
                    h.succeed();
                }finally{
                    for(var mob:mobs){
                        var mark=QuillHarvest.mark(mob);
                        if(mark.hasUUID("organ") && level.getEntity(mark.getUUID("organ")) instanceof QuillOrgan root)root.discard();
                        mob.discard();
                    }
                    BackspineCombat.cancel(p);
                    for(var chunk:forced)level.setChunkForced(chunk.x,chunk.z,false);
                    level.getServer().getPlayerList().remove(p);
                }
            });
        });
    }

    @GameTest(template="wall_test", timeoutTicks=40)
    public static void quillsShareConsumeEligibilityAndPrioritizeTheWeakestEnemy(GameTestHelper h) {
        var profile=new GameProfile(UUID.randomUUID(),"QuillPriority");
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,ClientInformation.createDefault());
        p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;
        p.setPos(h.absolutePos(new BlockPos(5,70,5)).getCenter());
        h.getLevel().addNewPlayer(p);
        var mobs=new ArrayList<Mob>();
        try {
            var state=p.getData(ModAttachments.PLAYER_STATE);
            state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.BACKSPINES);state.activate(PowerForm.BACKSPINES);
            var small=spawn(p,mobs,EntityType.COW,-2,2,49,49);
            var boundary=spawn(p,mobs,EntityType.COW,2,2,50,50);
            var unmarked=spawn(p,mobs,EntityType.COW,2,-2,10,1);
            small.setHealth(small.getMaxHealth());boundary.setHealth(boundary.getMaxHealth());
            h.assertTrue(CombatScale.originalMaximum(small)==49 && QuillHarvest.available(p,small),"49 max HP qualifies at full health");
            h.assertTrue(!QuillHarvest.available(p,boundary),"exactly 50 max HP still requires weakening");
            for(var mob:List.of(small,boundary))QuillHarvest.plant(p,mob,mob.position().add(0,1,0),0);
            var first=(QuillOrgan)h.getLevel().getEntity(QuillHarvest.mark(small).getUUID("organ"));
            var second=(QuillOrgan)h.getLevel().getEntity(QuillHarvest.mark(boundary).getUUID("organ"));
            ConsumeSystem.request(p);
            h.assertTrue(first.phase()==QuillOrgan.EXTRACT && second.phase()==QuillOrgan.ROOT && unmarked.isAlive(),"E recalls the marked small host only");
            boundary.setHealth(5);
            ConsumeSystem.request(p);
            h.assertTrue(second.phase()==QuillOrgan.EXTRACT,"E recalls a larger host after weakening");
            var near=spawn(p,mobs,EntityType.HUSK,2,0,200,10);
            var far=spawn(p,mobs,EntityType.HUSK,0,5,20,5);
            h.assertTrue(QuillHarvest.attackTarget(p,8,true).target()==far,"an unlocked orbit can acquire an idle hostile before the first hit");
            for(boolean orbit:List.of(false,true)) {
                var selection=QuillHarvest.attackTarget(p,8,orbit);
                h.assertTrue(selection!=null && selection.target()==far,"absolute HP outranks range and percent HP");
            }
            CombatTargeting.requestLock(p,near.getId());
            h.assertTrue(QuillHarvest.attackTarget(p,64,false).target()==near && QuillHarvest.sortie(p).target()==near,"both attacks honor the explicit lock");
            CombatTargeting.requestLock(p,-1);
            var tied=spawn(p,mobs,EntityType.HUSK,-3,0,60,5);
            for(boolean orbit:List.of(false,true)){
                var seen=new HashSet<Entity>();
                for(int i=0;i<80;i++)seen.add(QuillHarvest.attackTarget(p,8,orbit).target());
                h.assertTrue(seen.equals(java.util.Set.of(far,tied)),"only tied minimum-HP enemies are sampled");
            }
            tied.discard();
            near.setInvisible(true);near.setHealth(1);
            p.setYRot(180);p.setXRot(0);p.setOnGround(true);
            state.addBiomass(80);
            BackspineCombat.input(p,true,false);
            BackspineCombat.input(p,false,false);
            for(int i=0;i<14;i++){p.tickCount++;BackspineCombat.tick(p);}
            var shots=h.getLevel().getEntitiesOfClass(com.codex.blacklight.entity.BackspineQuill.class,p.getBoundingBox().inflate(5));
            h.assertTrue(!shots.isEmpty(),"the shoulder input fired a quill");
            for(var shot:shots){
                var towardEnemy=far.getBoundingBox().getCenter().subtract(shot.position()).normalize();
                h.assertTrue(shot.getDeltaMovement().normalize().dot(towardEnemy)>.8,"unlocked shoulder fire travels toward the visible weakest enemy even when looking away");
                shot.discard();
            }
            far.discard();near.discard();
            h.assertTrue(QuillHarvest.attackTarget(p,8,false).target()==unmarked,"shoulder fire can select a wild animal");
            h.assertTrue(QuillHarvest.attackTarget(p,8,true)==null,"without a lock orbiting quills leave livestock alone");
            var dog=(net.minecraft.world.entity.animal.Wolf)spawn(p,mobs,EntityType.WOLF,0,2,40,20);
            dog.tame(p);
            var piglin=spawn(p,mobs,EntityType.ZOMBIFIED_PIGLIN,0,5,100,80);
            for(var dogOwner:List.of(p.getUUID(),UUID.randomUUID())){
                dog.setOwnerUUID(dogOwner);
                float before=dog.getHealth();
                var line=dog.getBoundingBox().getCenter();
                var shot=new com.codex.blacklight.entity.BackspineQuill(h.getLevel(),p,4,0);
                shot.setPos(line.x,line.y,line.z-1.5);
                shot.setDeltaMovement(0,0,5);
                h.getLevel().addFreshEntity(shot);
                int layers=QuillHarvest.mark(piglin).getInt("layers");
                piglin.invulnerableTime=0;
                shot.tick();
                h.assertTrue(QuillHarvest.mark(piglin).getInt("layers")==layers+1,"the projectile hit the piglin beyond the pet");
                h.assertTrue(dog.getHealth()==before && !QuillHarvest.mark(dog).hasUUID("owner"),"crossing a tamed wolf causes neither damage nor parasitism");
                shot.discard();
            }
            h.succeed();
        } finally {
            BackspineCombat.cancel(p);
            CombatTargeting.requestLock(p,-1);
            for(var mob:mobs){
                var mark=QuillHarvest.mark(mob);
                if(mark.hasUUID("organ") && h.getLevel().getEntity(mark.getUUID("organ")) instanceof QuillOrgan organ)organ.discard();
                mob.discard();
            }
            h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);
        }
    }
}
