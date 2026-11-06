package com.codex.blacklight.gametest;

import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModBlocks;
import java.util.ArrayList;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class InfectionProtectionGameTest {
    @GameTest(template="wall_test",timeoutTicks=100,batch="infection_actor")
    public static void naturalGrowthHasAnIsolatedActorAndPreservesClaimVeto(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,30,5));
        var old=level.getBlockState(pos);level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        var seen=new java.util.concurrent.atomic.AtomicReference<net.minecraft.world.entity.Entity>();
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        Consumer<BlockEvent.EntityPlaceEvent> veto=e->{
            if(!e.getPos().equals(pos))return;
            // Claim listeners in the reported crash dereference the acting entity.
            h.assertTrue(e.getEntity().getUUID()!=null,"claim listener receives a real identity");
            h.assertTrue(e.getEntity() instanceof net.minecraftforge.common.util.FakePlayer,"growth uses its own actor");
            var actor=(net.minecraft.server.level.ServerPlayer)e.getEntity();seen.set(actor);calls.incrementAndGet();
            h.assertTrue(actor.serverLevel()==level&&actor.blockPosition().equals(pos),"actor belongs to the placement dimension and position");
            h.assertTrue(actor.getGameProfile().getName().equals("[Blacklight]")&&!actor.hasPermissions(2),"growth cannot borrow a human or operator identity");
            h.assertTrue(level.getEntity(actor.getUUID())==null&&!level.getServer().getPlayerList().getPlayers().contains(actor),"actor is never spawned or connected");
            h.assertTrue(e.getBlockSnapshot().getReplacedBlock().isAir()&&e.getPlacedBlock().is(Blocks.STONE),"old snapshot and new placement remain distinct");
            e.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(veto);
        try {
            h.assertTrue(!NaturalInfectionPlacement.place(level,pos,Blocks.STONE.defaultBlockState()),"claim veto refuses growth");
            h.assertTrue(calls.get()==1&&seen.get()!=null&&level.getBlockState(pos).isAir(),"cancelled growth restores the original cell");
        } finally {MinecraftForge.EVENT_BUS.unregister(veto);level.setBlock(pos,old,2);}
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=100,batch="infection_actor_nested")
    public static void nestedGrowthRestoresTheOuterActorPosition(GameTestHelper h) {
        var level=h.getLevel();var outer=h.absolutePos(new BlockPos(5,32,5));var inner=outer.offset(3,0,0);
        var oldOuter=level.getBlockState(outer);var oldInner=level.getBlockState(inner);
        level.setBlock(outer,Blocks.AIR.defaultBlockState(),2);level.setBlock(inner,Blocks.AIR.defaultBlockState(),2);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        Consumer<BlockEvent.EntityPlaceEvent> listener=e->{
            if(!e.getPos().equals(outer)&&!e.getPos().equals(inner))return;
            h.assertTrue(e.getEntity().blockPosition().equals(e.getPos()),"each recursive event has its own position");calls.incrementAndGet();
            if(e.getPos().equals(outer)) {
                var actor=e.getEntity();var position=actor.position();
                h.assertTrue(NaturalInfectionPlacement.place(level,inner,Blocks.STONE.defaultBlockState()),"nested growth succeeds");
                h.assertTrue(actor.position().equals(position),"nested listener cannot move the outer event actor");
            }
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            h.assertTrue(NaturalInfectionPlacement.place(level,outer,Blocks.STONE.defaultBlockState())&&calls.get()==2,"both real placements complete");
        } finally {MinecraftForge.EVENT_BUS.unregister(listener);level.setBlock(outer,oldOuter,2);level.setBlock(inner,oldInner,2);}
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=100,batch="infection_plan_load")
    public static void oversizedSavedPathIsRejectedBeforeTick(GameTestHelper h) {
        var stem=new BuildingBiomassField.Stem(java.util.List.of(new BuildingBiomassField.Point(-200,0,0),
            new BuildingBiomassField.Point(200,0,0)),4,4,0,100,-1);
        var excessive=new BuildingBiomassPlan(java.util.List.of(stem),java.util.List.of(BlockPos.ZERO));
        h.assertTrue(BuildingBiomassPlan.load(excessive.save())==null,"saved paths must satisfy the cell budget before entering the colony tick loop");
        var ordinary=new BuildingBiomassPlan(java.util.List.of(new BuildingBiomassField.Stem(java.util.List.of(
            new BuildingBiomassField.Point(0,0,0),new BuildingBiomassField.Point(0,8,0)),1,.5,0,100,-1)),java.util.List.of(BlockPos.ZERO));
        var loaded=BuildingBiomassPlan.load(ordinary.save());
        h.assertTrue(loaded!=null&&loaded.cells().equals(ordinary.cells()),"valid saved paths retain their exact geometry");
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=100,batch="infection_protection")
    public static void rejectedNaturalCoreDoesNotPoisonColonyRegistry(GameTestHelper h) {
        var level=h.getLevel();var data=BuildingInfectionData.get(level);
        var old=new ArrayList<>(data.colonies);data.colonies.clear();
        var oldCleared=new java.util.LinkedHashSet<>(data.clearedSites);data.clearedSites.clear();
        var storage=level.getServer().overworld().getDataStorage();var outbreak=OutbreakData.get(level.getServer());
        var active=new OutbreakData();active.start(level.getServer().overworld().getGameTime());storage.set("blacklight_outbreak",active);
        var pos=h.absolutePos(new BlockPos(5,120,5));
        for(int y=0;y<3;y++)level.setBlock(pos.offset(0,y,1),Blocks.STONE_BRICKS.defaultBlockState(),2);
        var tissue=ModBlocks.TISSUE_ATTACHMENT.get().defaultBlockState();level.setBlock(pos,tissue,3);
        boolean grief=level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());
        Consumer<BlockEvent.EntityPlaceEvent> veto=e->{if(e.getPos().equals(pos)&&e.getPlacedBlock().is(ModBlocks.BUILDING_CORE.get()))e.setCanceled(true);};
        MinecraftForge.EVENT_BUS.addListener(veto);
        try {
            var random=net.minecraft.util.RandomSource.create(7731);
            for(int i=0;i<300;i++)BuildingInfectionSystem.considerNaturalCore(level,pos,random);
            h.assertTrue(level.getBlockState(pos).equals(tissue)&&data.colonies.isEmpty(),"veto restores tissue with no live/dead colony or wasted capacity");
        } finally {MinecraftForge.EVENT_BUS.unregister(veto);}
        try {
            var random=net.minecraft.util.RandomSource.create(7731);
            for(int i=0;i<300;i++)BuildingInfectionSystem.considerNaturalCore(level,pos,random);
            h.assertTrue(level.getBlockState(pos).is(ModBlocks.BUILDING_CORE.get())&&data.colonies.size()==1&&!data.colonies.get(0).dead,
                "lifting protection permits the same site to seed exactly once");
        } finally {
            level.removeBlock(pos,false);data.colonies.clear();data.colonies.addAll(old);data.setDirty();
            data.clearedSites.clear();data.clearedSites.addAll(oldCleared);storage.set("blacklight_outbreak",outbreak);
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(grief,level.getServer());
        }
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=100,batch="infection_surface_protection")
    public static void facadeSpreadCannotBypassPlacementVeto(GameTestHelper h) {
        var level=h.getLevel();var data=BuildingInfectionData.get(level);
        var old=new ArrayList<>(data.colonies);data.colonies.clear();
        var oldCleared=new java.util.LinkedHashSet<>(data.clearedSites);data.clearedSites.clear();
        var storage=level.getServer().overworld().getDataStorage();var outbreak=OutbreakData.get(level.getServer());
        var active=new OutbreakData();active.start(level.getServer().overworld().getGameTime());storage.set("blacklight_outbreak",active);
        var origin=h.absolutePos(new BlockPos(5,120,5));
        for(int x=-5;x<=5;x++)for(int y=0;y<20;y++)level.setBlock(origin.offset(x,y,1),Blocks.STONE_BRICKS.defaultBlockState(),2);
        level.setBlock(origin,ModBlocks.BUILDING_CORE.get().defaultBlockState(),3);
        var c=data.colonies.get(0);
        for(int i=0;i<8;i++){c.supply.offer(TissueDigestion.Kind.MEAT,64);c.supply.digest(200);}
        boolean grief=level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());
        var attempts=new java.util.concurrent.atomic.AtomicInteger();
        Consumer<BlockEvent.EntityPlaceEvent> veto=e->{if(e.getPlacedBlock().is(ModBlocks.TISSUE_ATTACHMENT.get())){attempts.incrementAndGet();e.setCanceled(true);}};
        MinecraftForge.EVENT_BUS.addListener(veto);
        try {
            for(int i=0;i<300;i++)BuildingInfectionSystem.advance(level);
            h.assertTrue(attempts.get()>0,"the fed fixture actually attempts protected construction");
            h.assertTrue(c.surfaces.isEmpty(),"cancelled patches never enter the saved surface list");
            for(int x=-5;x<=5;x++)for(int y=0;y<20;y++)h.assertTrue(!level.getBlockState(origin.offset(x,y,0)).is(ModBlocks.TISSUE_ATTACHMENT.get()),"protected facade remains free of patches");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(veto);level.removeBlock(origin,false);
            data.colonies.clear();data.colonies.addAll(old);data.setDirty();
            data.clearedSites.clear();data.clearedSites.addAll(oldCleared);storage.set("blacklight_outbreak",outbreak);
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(grief,level.getServer());
        }
        h.succeed();
    }
}
