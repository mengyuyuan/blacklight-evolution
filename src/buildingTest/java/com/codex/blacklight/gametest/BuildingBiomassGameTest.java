package com.codex.blacklight.gametest;

import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModBlocks;
import com.codex.blacklight.world.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class BuildingBiomassGameTest {
    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;final BuildingInfectionData data;final BlockPos origin;
        final List<BuildingInfectionData.Colony> previous;final BuildingInfectionData.Colony colony;
        final boolean grief,spawns;
        final java.util.Map<BlockPos,net.minecraft.world.level.block.state.BlockState> terrain=new java.util.LinkedHashMap<>();
        void block(BlockPos pos,net.minecraft.world.level.block.state.BlockState state) {
            terrain.putIfAbsent(pos.immutable(),level.getBlockState(pos));level.setBlock(pos,state,2);
        }
        Fixture(GameTestHelper h) {
            level=h.getLevel();data=BuildingInfectionData.get(level);previous=new ArrayList<>(data.colonies);data.colonies.clear();
            grief=level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);spawns=level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
            origin=h.absolutePos(new BlockPos(8,80,8));
            for(int x=-12;x<=12;x++)for(int z=-12;z<=2;z++)block(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState());
            for(int x=-10;x<=10;x++)for(int y=0;y<=30;y++)block(origin.offset(x,y,1),Blocks.STONE_BRICKS.defaultBlockState());
            level.setBlock(origin,ModBlocks.BUILDING_CORE.get().defaultBlockState(),3);
            h.assertTrue(data.colonies.size()==1,"one real building core");colony=data.colonies.getFirst();
            colony.activeTicks=12000;colony.stage=3;
            BuildingBiomassSystem.step(level,colony);h.assertTrue(colony.mass!=null,"plan must anchor to real facade");
        }
        void grow() { for(int i=0;i<colony.mass.cells().size()+1;i++)BuildingBiomassSystem.step(level,colony); }
        BlockPos mass() {
            return colony.mass.cells().stream().map(c->origin.offset(c.x(),c.y(),c.z())).filter(p->level.getBlockState(p).is(ModBlocks.BUILDING_BIOMASS.get())).findFirst().orElseThrow();
        }
        public void close() {
            colony.dead=true;
            for(var c:colony.mass.cells()){var p=origin.offset(c.x(),c.y(),c.z());if(level.getBlockState(p).is(ModBlocks.BUILDING_BIOMASS.get()))level.removeBlock(p,false);}
            level.removeBlock(origin,false);data.colonies.clear();data.colonies.addAll(previous);data.setDirty();
            terrain.forEach((pos,state)->level.setBlock(pos,state,2));
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(grief,level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawns,level.getServer());
        }
    }
    @GameTest(template="wall_test",timeoutTicks=200,batch="biomass_volume")
    public static void columnsHaveRealVolumeAndCollisionOutsideFacade(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.grow();h.assertTrue(f.colony.massBlocks>200,"large continuous volume must grow");
            var outside=f.colony.mass.cells().stream().map(c->f.origin.offset(c.x(),c.y(),c.z()))
                .filter(p->p.getZ()<f.origin.getZ()-3&&p.getY()>f.origin.getY()+9&&f.level.getBlockState(p).is(ModBlocks.BUILDING_BIOMASS.get())).findFirst();
            h.assertTrue(outside.isPresent(),"column projects several metres from high facade");
            var p=outside.orElseThrow();h.assertTrue(!f.level.getBlockState(p).getCollisionShape(f.level,p).isEmpty(),"visible body has real collision");
            h.assertTrue(f.level.getBlockState(f.origin.offset(0,15,1)).is(Blocks.STONE_BRICKS),"original building remains intact");
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=200,batch="biomass_save")
    public static void pathsGrowthAndSeveredCellsSurviveSave(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.colony.massCursor=55;f.colony.massDisabled=1;f.colony.severed.add(f.origin.offset(1,3,-2).asLong());
            var saved=f.data.save(new CompoundTag(),f.level.registryAccess());var restored=BuildingInfectionData.load(saved,f.level.registryAccess()).colonies.getFirst();
            h.assertTrue(restored.mass!=null&&restored.mass.stems.equals(f.colony.mass.stems)&&restored.mass.supports.equals(f.colony.mass.supports),"same paths and anchors after reload");
            h.assertTrue(restored.massCursor==55&&restored.massDisabled==1&&restored.severed.equals(f.colony.severed),"growth cursor and severing persist");
            h.assertTrue(restored.mass.cells().equals(f.colony.mass.cells()),"candidate order is deterministic after reload");
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=200,batch="biomass_anchor")
    public static void removingSupportDisablesItsDependentBranches(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            h.assertTrue(f.colony.mass.disabled(f.level,0)==0,"all initial supports exist");
            f.level.removeBlock(f.colony.mass.supports.getFirst(),false);int disabled=f.colony.mass.disabled(f.level,0);
            h.assertTrue((disabled&1)!=0,"removed target disconnects its primary stem");
            for(int i=1;i<f.colony.mass.stems.size();i++)if(f.colony.mass.stems.get(i).parent()==0)h.assertTrue((disabled&(1<<i))!=0,"dependent branches disconnect too");
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=200,batch="biomass_sever")
    public static void miningColumnDoesNotInstantlyRegrowIt(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.grow();var p=f.mass();f.level.destroyBlock(p,false);
            h.assertTrue(f.colony.severed.contains(p.asLong()),"world removal registers a severed cell");
            f.grow();h.assertTrue(f.level.getBlockState(p).isAir(),"growth cannot immediately undo mining");
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=200,batch="biomass_budget")
    public static void disabledGriefingAndDestroyedCoreFreezeTheSharedQueue(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,f.level.getServer());int cursor=f.colony.massCursor;
            h.assertTrue(BuildingInfectionSystem.advance(f.level)==0&&f.colony.massCursor==cursor,"game rule freezes mass work");
            f.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,f.level.getServer());
            for(int i=0;i<10;i++)h.assertTrue(BuildingInfectionSystem.advance(f.level)<=BuildingInfectionConfig.WORK.get(),"surface and mass share one budget");
            f.level.removeBlock(f.origin,false);cursor=f.colony.massCursor;
            for(int i=0;i<10;i++)BuildingInfectionSystem.advance(f.level);
            h.assertTrue(f.colony.massCursor==cursor,"destroyed core stops further mass growth");
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=200,batch="biomass_veto")
    public static void protectedAirCanRejectColumnPlacement(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            int initial=f.colony.massBlocks;
            Consumer<BlockEvent.EntityPlaceEvent> veto=e->{if(e.getPlacedBlock().is(ModBlocks.BUILDING_BIOMASS.get()))e.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(veto);
            try{f.grow();h.assertTrue(f.colony.massBlocks==initial&&!f.colony.severed.isEmpty(),"cancelled placement is restored and not retried endlessly");}
            finally{NeoForge.EVENT_BUS.unregister(veto);}
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=200,batch="biomass_variation")
    public static void differentSitesHaveStableDistinctAnatomy(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            var shapes=new java.util.HashSet<List<BuildingBiomassField.Stem>>();
            var counts=new java.util.HashSet<Long>();
            for(int offset=-4;offset<=4;offset++) {
                var site=f.origin.offset(offset,0,0);
                var a=BuildingBiomassPlan.find(f.level,site,net.minecraft.core.Direction.NORTH);
                var b=BuildingBiomassPlan.find(f.level,site,net.minecraft.core.Direction.NORTH);
                h.assertTrue(a!=null&&b!=null,"real facade supports every sampled site");
                h.assertTrue(a.stems.equals(b.stems)&&a.supports.equals(b.supports),"same site keeps its anatomy");
                h.assertTrue(a.stems.equals(BuildingBiomassPlan.load(a.save()).stems),"save preserves generated anatomy");
                shapes.add(a.stems);counts.add(a.stems.stream().filter(s->s.parent()<0).count());
            }
            h.assertTrue(shapes.size()>=5&&counts.size()>=2,"sites vary whole anatomy, including primary trunk count: "+shapes.size()+" / "+counts);
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=200,batch="biomass_brood")
    public static void thickRootsCannotSealTheMatureBroodOutlet(GameTestHelper h) {
        var difficulty=h.getLevel().getDifficulty();
        try(var f=new Fixture(h)) {
            f.grow();f.level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
            f.level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,f.level.getServer());
            h.assertTrue(BuildingInfectionSystem.hatch(f.level,f.colony),"living mature colony produces a hunter beside its thick roots");
            var mobs=f.level.getEntitiesOfClass(com.codex.blacklight.entity.BlacklightHunter.class,new net.minecraft.world.phys.AABB(f.origin).inflate(16));
            h.assertTrue(!mobs.isEmpty()&&mobs.stream().allMatch(f.level::noCollision),"brood emerges without colliding with the column");
            mobs.forEach(net.minecraft.world.entity.Entity::discard);
        }finally{h.getLevel().getServer().setDifficulty(difficulty,true);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=200,batch="biomass_bridge")
    public static void streetCableRequiresAndTracksTheOtherBuilding(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            h.assertTrue(f.colony.mass.supports.stream().noneMatch(p->p.getZ()<f.origin.getZ()-8),"no imaginary street anchor without a second building");
            for(int x=-7;x<=7;x++)for(int y=0;y<=30;y++)f.block(f.origin.offset(x,y,-22),Blocks.STONE_BRICKS.defaultBlockState());
            f.colony.mass=null;f.colony.massAttempt=0;BuildingBiomassSystem.step(f.level,f.colony);
            int cable=-1;
            for(int i=0;i<f.colony.mass.supports.size();i++)if(f.colony.mass.supports.get(i).getZ()<f.origin.getZ()-8)cable=i;
            h.assertTrue(cable>=0,"real second facade receives a continuous anchored cable");
            var stem=f.colony.mass.stems.get(cable);h.assertTrue(stem.parent()>=0,"cable is rooted in a primary trunk");
            var middle=stem.points().get(1);
            h.assertTrue(BuildingBiomassField.density(f.colony.mass.stems,middle,12000,0)<0,"cable occupies actual street air");
            var restored=BuildingBiomassPlan.load(f.colony.mass.save());h.assertTrue(restored.supports.equals(f.colony.mass.supports),"other-building anchor survives reload");
            f.level.removeBlock(f.colony.mass.supports.get(cable),false);
            h.assertTrue((f.colony.mass.disabled(f.level,0)&(1<<cable))!=0,"removing the far wall disconnects the cable");
        }h.succeed();
    }
}
