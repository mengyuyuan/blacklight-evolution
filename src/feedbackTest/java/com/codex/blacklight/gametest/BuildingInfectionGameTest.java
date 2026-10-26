package com.codex.blacklight.gametest;

import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.world.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class BuildingInfectionGameTest {
    @GameTest(template="wall_test",timeoutTicks=200,batch="building_colony")
    public static void connectedGrowthSaveBroodAndDestroyedCore(GameTestHelper h) {
        var level=h.getLevel();var data=BuildingInfectionData.get(level);
        var old=new java.util.ArrayList<>(data.colonies);data.colonies.clear();
        var oldCleared=new java.util.LinkedHashSet<>(data.clearedSites);data.clearedSites.clear();
        var storage=level.getServer().overworld().getDataStorage();var outbreak=OutbreakData.get(level.getServer());
        var active=new OutbreakData();active.start(level.getServer().overworld().getGameTime()-7L*OutbreakConfig.DAY_TICKS.get());storage.set("blacklight_outbreak",active);
        var origin=h.absolutePos(new BlockPos(5,150,5));
        var grief=level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        var spawning=level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING);
        boolean oldGrief=grief.get(),oldSpawning=spawning.get();
        var difficulty=level.getDifficulty();
        try {
            grief.set(true,level.getServer());spawning.set(true,level.getServer());
            level.getServer().setDifficulty(Difficulty.NORMAL,true);
            for(int x=-5;x<=5;x++) for(int z=-5;z<=5;z++)
                level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
            for(int x=-5;x<=5;x++) for(int y=0;y<32;y++)
                level.setBlock(origin.offset(x,y,1),Blocks.STONE_BRICKS.defaultBlockState(),2);
            var inventory=origin.offset(2,1,0);level.setBlock(inventory,Blocks.CHEST.defaultBlockState(),3);
            var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(inventory);
            chest.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7));
            level.setBlock(origin,ModBlocks.TISSUE_ATTACHMENT.get().defaultBlockState(),3);
            grief.set(false,level.getServer());
            var natural=net.minecraft.util.RandomSource.create(7731);
            for(int i=0;i<100;i++)BuildingInfectionSystem.considerNaturalCore(level,origin,natural);
            h.assertTrue(data.colonies.isEmpty(),"natural seeding respects mobGriefing too");
            grief.set(true,level.getServer());
            for(int i=0;i<300;i++)BuildingInfectionSystem.considerNaturalCore(level,origin,natural);
            h.assertTrue(data.colonies.size()==1&&level.getBlockState(origin).is(ModBlocks.BUILDING_CORE.get()),"floor tissue beside an ordinary wall establishes exactly one core");
            h.assertTrue(!BuildingInfectionSystem.register(level,origin.offset(5,0,0)),"nearby overlapping producer is rejected");
            var c=data.colonies.getFirst();
            for(int i=0;i<32;i++){c.supply.offer(TissueDigestion.Kind.MEAT,64);c.supply.digest(200);}
            grief.set(false,level.getServer());
            int queued=c.frontier.size();
            h.assertTrue(BuildingInfectionSystem.advance(level)==0&&c.activeTicks==0&&c.frontier.size()==queued,"griefing off freezes both work and age");
            grief.set(true,level.getServer());
            // A queued target in an unloaded chunk must rotate without a synchronous chunk load.
            var far=origin.offset(100000,0,100000);c.frontier.addFirst(far.asLong());
            BuildingInfectionSystem.advance(level);
            h.assertTrue(!level.hasChunkAt(far),"frontier cannot force-load remote chunks");c.frontier.remove(far.asLong());
            for(int i=0;i<1600;i++) {
                if(c.supply.reserve()<128)for(int j=0;j<8;j++){c.supply.offer(TissueDigestion.Kind.MEAT,64);c.supply.digest(200);}
                int operations=BuildingInfectionSystem.advance(level);
                h.assertTrue(operations<=BuildingInfectionConfig.WORK.get(),"dimension work budget is shared and bounded");
            }
            h.assertTrue(c.surfaces.size()>=256,"growth covers a substantial facade and turns its corners");
            h.assertTrue(c.surfaces.stream().map(BlockPos::of).anyMatch(p->p.getY()>origin.getY()+8),"growth climbs at least eight blocks up the building");
            h.assertTrue(level.getBlockState(origin.offset(0,15,1)).is(Blocks.STONE_BRICKS),"original architecture remains intact");
            h.assertTrue(chest.getItem(0).getCount()==7&&level.getBlockState(inventory).is(Blocks.CHEST),"growth preserves container and inventory");
            c.activeTicks=BuildingInfectionConfig.MATURITY.get()*20;
            BuildingInfectionSystem.advance(level);
            h.assertTrue(c.stage==3&&level.getBlockState(origin).getValue(TissueAttachmentBlock.AGE)==3,"maturity requires elapsed active time AND substantial growth");
            var saved=data.save(new CompoundTag(),level.registryAccess());
            var restored=BuildingInfectionData.load(saved,level.registryAccess()).colonies.getFirst();
            h.assertTrue(restored.activeTicks==c.activeTicks&&restored.surfaces.equals(c.surfaces)
                    &&new java.util.ArrayList<>(restored.frontier).equals(new java.util.ArrayList<>(c.frontier)),"reload preserves age, appearance ownership and frontier order");
            spawning.set(false,level.getServer());
            h.assertTrue(!BuildingInfectionSystem.hatch(level,c),"doMobSpawning disables brood");
            spawning.set(true,level.getServer());
            boolean born=BuildingInfectionSystem.hatch(level,c);
            h.assertTrue(born,"mature core produces an actual living mob at a collision-free outlet");
            var children=level.getEntitiesOfClass(Mob.class,new AABB(origin).inflate(12),BlacklightInfectionSystem::isInfected);
            h.assertTrue(!children.isEmpty()&&(com.codex.blacklight.city.CityEcology.stage(level,origin)>=2
                    ?children.stream().anyMatch(m->m instanceof com.codex.blacklight.entity.BlacklightHunter)
                    :children.stream().noneMatch(m->m instanceof com.codex.blacklight.entity.BlacklightHunter)),"brood matches the current shared world stage instead of spawning a premature hunter");
            level.removeBlock(origin,false);
            h.assertTrue(c.dead&&c.frontier.isEmpty(),"destroying the core immediately stops the queue");
            int surfaces=c.surfaces.size(),births=c.births;
            for(int i=0;i<20;i++)BuildingInfectionSystem.advance(level);
            h.assertTrue(c.surfaces.size()==surfaces&&c.births==births&&!BuildingInfectionSystem.hatch(level,c),"destroyed core neither grows nor spawns");
            var dead=BuildingInfectionData.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
            h.assertTrue(dead.clearedSites.contains(origin.asLong())&&dead.activeColonies()==0&&!BuildingInfectionSystem.register(level,origin),"compact history keeps the cleared site protected without spending a live colony slot");
            h.succeed();
        } finally {
            for(var mob:level.getEntitiesOfClass(Mob.class,new AABB(origin).inflate(48)))mob.discard();
            grief.set(oldGrief,level.getServer());spawning.set(oldSpawning,level.getServer());level.getServer().setDifficulty(difficulty,true);
            data.colonies.clear();data.colonies.addAll(old);data.setDirty();
            data.clearedSites.clear();data.clearedSites.addAll(oldCleared);storage.set("blacklight_outbreak",outbreak);
        }
    }
}
