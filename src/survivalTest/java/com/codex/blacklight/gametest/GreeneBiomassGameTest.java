package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.world.GreeneHost;
import com.codex.blacklight.world.BossSiteBlock;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.UUID;

import com.codex.blacklight.power.*;
import com.codex.blacklight.combat.ConsumeControl;
import com.codex.blacklight.entity.GreenePart;
import com.codex.blacklight.registry.ModAttachments;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GreeneBiomassGameTest {
    private static ServerPlayer join(GameTestHelper h, String name) {
        var profile = new GameProfile(UUID.randomUUID(), name);
        var server = h.getLevel().getServer();

        var player = new ServerPlayer(server, h.getLevel(), profile);
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);

        server.getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(16, 2, 30)));
        return player;
    }
    private static boolean done(ServerPlayer p, String name) {
        var advancement = p.server.getAdvancements().getAdvancement(new ResourceLocation("blacklight", name));
        return advancement != null && p.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    @GameTest(template="mother_plaza",timeoutTicks=460)
    public static void slimeRejectsTheMotherShellAndTheSpentWomanEscapes(GameTestHelper h) {
        var level=h.getLevel();
        var operator=join(h,"greene-operator");
        var slime=join(h,"greene-absorber");
        var state=ModAttachments.getData(slime,ModAttachments.PLAYER_STATE);
        state.infect();
        state.setPrototypeMode(true);
        var station=h.absolutePos(new BlockPos(16,2,28));
        var pad=h.absolutePos(new BlockPos(16,2,16));
        var near=new AABB(pad).inflate(6);
        var mother=new EncounterMob[1];
        var escaping=new GreeneHost[1];
        // The Forge fixture may be underground. Stop loose gravel falling onto the stage.
        for(var roof:BlockPos.betweenClosed(pad.offset(-5,15,-5),pad.offset(5,15,5)))
            level.setBlockAndUpdate(roof,Blocks.STONE.defaultBlockState());
        for(var at:BlockPos.betweenClosed(pad.offset(-5,0,-5),pad.offset(5,14,5)))
            if(level.getBlockState(at).is(Blocks.GRAVEL))level.setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());
        h.startSequence().thenWaitUntil(()->{
            var actors=level.getEntitiesOfClass(GreeneHost.class,near,e->e.stage()==GreeneHost.WAITING);
            h.assertTrue(actors.size()==1,"waiting host present");
            h.assertTrue(!BiomassSystem.edible(slime,actors.get(0)),"opening human remains protected");
        }).thenExecute(()->{
            operator.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(com.codex.blacklight.registry.ModItems.BLOODTOX_STARTER.get()));
            h.assertTrue(BossSiteBlock.activate(level,station,operator).equals("plaza_started"),"operator starts the encounter");
        }).thenWaitUntil(()->{
            var bosses=level.getEntitiesOfClass(EncounterMob.class,near,e->e.greeneBattle().canAttack());
            h.assertTrue(bosses.size()==1,"mother has emerged; humans="+level.getEntitiesOfClass(GreeneHost.class,near).stream().map(host->host.stage()+":"+host.age(0)).toList()+" mothers="+level.getEntitiesOfClass(EncounterMob.class,near.inflate(20)).stream().map(b->b.greeneStage()+":"+b.getY()).toList());
            mother[0]=bosses.get(0);
        }).thenExecute(()->{
            var boss=mother[0];
            slime.setPos(station.getX()+16,station.getY(),station.getZ());
            h.assertTrue(BiomassSystem.begin(slime,false) && BiomassSystem.state(slime).recallable(),"normal separation creates a shell and a controllable biomass form");
            boss.updateParts();
            var part=(GreenePart)boss.getParts()[0];
            var at=boss.hurtRegions(0).get(0).boxes().get(0).bounds.getCenter();
            slime.setPos(at);
            crawl(slime,level.getGameTime()-14);
            var before=BiomassSystem.state(slime);
            var fatal=new BiomassState(before.phase(),before.changedAt(),true,before.shell(),before.previousPower(),before.victim(),before.age());
            ModAttachments.setData(slime,ModAttachments.BIOMASS_FORM,fatal);
            h.assertTrue(!BiomassSystem.edible(slime,boss),"a fatal biomass form cannot claim full-health Greene");
            boss.setHealth(1);
            tick(slime);
            h.assertTrue(!com.codex.blacklight.combat.ConsumeSystem.eligible(boss) && !BiomassSystem.edible(slime,boss)
                    && BiomassSystem.claimant(boss)==null,"the living Mother shell never enters a generic consume scene, even at one HP");
            h.assertTrue(!com.codex.blacklight.combat.ConsumeSystem.executeConsumption(slime,boss,Float.MAX_VALUE)
                    && boss.isAlive(),"direct execution cannot bypass the story interaction");
            slime.setPos(station.getX()+32,station.getY(),station.getZ()+32);
            h.assertTrue(boss.hurt(level.damageSources().genericKill(),Float.MAX_VALUE),"shell reaches its real death callback");
        }).thenWaitUntil(()->{
            var spent=level.getEntitiesOfClass(GreeneHost.class,near,e->e.stage()==GreeneHost.SPENT);
            h.assertTrue(spent.size()==1,"defeated shell releases one spent human");
        }).thenExecute(()->{
            var host=level.getEntitiesOfClass(GreeneHost.class,near,e->e.stage()==GreeneHost.SPENT).get(0);
            h.assertTrue(!done(operator,"plaza_cleared"),"killing the shell alone awards nothing");
            h.assertTrue(!host.hurt(level.damageSources().playerAttack(operator),1000),"another player cannot kill the finale before absorption");
            slime.setPos(host.position());
            crawl(slime,level.getGameTime()-14);
            tick(slime);
            h.assertTrue(BiomassSystem.state(slime).victim()==host.getId(),"normal slime contact acquired spent Greene");
            // Keep the same claimed victim; fast-forward only the absorption timer.
            var mature=BiomassSystem.state(slime).absorb(host.getId(),level.getGameTime()-BiomassState.ABSORB_TICKS-10);
            ModAttachments.setData(slime,ModAttachments.BIOMASS_FORM,mature);
            tick(slime);
            escaping[0]=host;
            h.assertTrue(host.isAlive() && host.stage()==GreeneHost.ESCAPING && BiomassSystem.state(slime).consumed(),"torn tissue reforms the consumer while Greene escapes alive");
            h.assertTrue(!host.beginEscape(operator) && !BiomassSystem.edible(slime,host),"a second player cannot reclaim the escaping host");
            h.assertTrue(!done(operator,"plaza_cleared") && BossSiteBlock.activate(level,station,operator).equals("active"),"pump remains reserved during the escape");
        }).thenIdle(16).thenExecute(()->{
            var host=escaping[0];
            var snapshot=host.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            var restored=com.codex.blacklight.registry.EncounterEntities.GREENE_HOST.get().create(level);
            restored.load(snapshot);
            h.assertTrue(restored.stage()==GreeneHost.ESCAPING && restored.age(0)==host.age(0)
                    && restored.drainOffset().distanceToSqr(host.drainOffset())<1e-6 && !restored.beginEscape(operator),
                    "save preserves the escape clock, drain and committed stage");
        }).thenWaitUntil(()->{
            h.assertTrue(escaping[0].isRemoved(),"Greene has slipped away");
        }).thenExecute(()->{
            var host=escaping[0];
            h.assertTrue(done(slime,"plaza_cleared") && done(operator,"plaza_cleared"),"finisher and operator both receive the plaza reward");
            h.assertTrue(!done(slime,"greene_outbreak") && !state.hasUnlocked(PowerForm.GAZE_VACUOLE),"the escape gives no goddess victory or gaze");
            var cores=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,host.getBoundingBox().inflate(6),
                    item -> item.getItem().is(com.codex.blacklight.registry.ModItems.GREENE_TISSUE.get()));
            h.assertTrue(cores.size()==1 && cores.get(0).getItem().getCount()==1,"escape leaves a single living tissue sample");
            var reformed=BiomassSystem.state(slime).reform(level.getGameTime()-BiomassState.REFORM_TICKS-1);
            ModAttachments.setData(slime,ModAttachments.BIOMASS_FORM,reformed);
            tick(slime);
            h.assertTrue(!BiomassSystem.active(slime),"the final human meal restores the player and ends the biomass form");
            h.assertTrue(BossSiteBlock.activate(level,station,operator).equals("plaza_started"),"a completed biomass finisher releases the site");
            level.getEntitiesOfClass(GreeneHost.class,near).forEach(GreeneHost::discard);
            level.getServer().getPlayerList().remove(slime);
            level.getServer().getPlayerList().remove(operator);
        }).thenSucceed();
    }

    @GameTest(template="lost_convoy",batch="supreme_absorption",timeoutTicks=120)
    public static void biomassGetsTheSupremeConsumeMilestone(GameTestHelper h) {
        var level=h.getLevel();
        var slime=join(h,"supreme-absorber");
        var state=ModAttachments.getData(slime,ModAttachments.PLAYER_STATE);
        state.infect();
        state.setPrototypeMode(true);
        var boss=com.codex.blacklight.registry.ModEntities.SUPREME_HUNTER.get().create(level);
        try {
            var at=h.absolutePos(new BlockPos(12,1,10));
            var floor=new BlockPos(at.getX(),220,at.getZ());
            level.setBlockAndUpdate(floor,Blocks.STONE.defaultBlockState());
            var origin=net.minecraft.world.phys.Vec3.atBottomCenterOf(floor.above());
            slime.setPos(origin);
            h.assertTrue(BiomassSystem.begin(slime,false),"biomass separation begins");
            boss.setPos(origin);
            boss.setNoAi(true);
            level.addFreshEntity(boss);
            boss.setHealth(1);
            boss.getPersistentData().putBoolean("BlacklightConsumeAwaitingFinisher",true);
            slime.setPos(origin);
            crawl(slime,level.getGameTime()-14);
            tick(slime);
            h.assertTrue(BiomassSystem.state(slime).victim()==boss.getId(),"biomass acquires weakened hunter through contact");
            var mature=BiomassSystem.state(slime).absorb(boss.getId(),level.getGameTime()-BiomassState.ABSORB_TICKS-10);
            ModAttachments.setData(slime,ModAttachments.BIOMASS_FORM,mature);
            tick(slime);
            h.assertTrue(!boss.isAlive() && BiomassSystem.state(slime).consumed() && done(slime,"supreme_hunter_consumed"),
                    "biomass finishes the real execution and earns the consume milestone");
        } finally {
            boss.discard();
            level.getServer().getPlayerList().remove(slime);
        }
        h.succeed();
    }

    private static void crawl(ServerPlayer slime,long at){
        var crawling=BiomassSystem.state(slime).crawl(at);
        ModAttachments.setData(slime,ModAttachments.BIOMASS_FORM,crawling);
        slime.refreshDimensions();
    }
    private static void tick(ServerPlayer slime){BiomassSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,slime));}
}
