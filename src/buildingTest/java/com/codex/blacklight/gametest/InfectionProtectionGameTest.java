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
