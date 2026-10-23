package com.codex.blacklight.gametest;

import com.codex.blacklight.registry.ModBlocks;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.world.TissueAttachmentBlock;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TissueGrowthGameTest {
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue")
    public static void ageSurvivesSavingWithoutGrowingGenericHost(GameTestHelper h) {
        var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());
        host.inherit(EntityType.PIG.create(h.getLevel()));
        float width=host.getBbWidth(),height=host.getBbHeight();
        host.advanceTissue(4300);
        var tag=new CompoundTag();host.saveWithoutId(tag);
        var restored=ModEntities.INFECTED_HOST.get().create(h.getLevel());restored.load(tag);
        h.assertTrue(restored.tissueAge()==4300,"tissue age survives reload");
        h.assertTrue(restored.getUUID().equals(host.getUUID()),"variation seed survives reload");
        h.assertTrue(restored.getBbWidth()==width&&restored.getBbHeight()==height,"attachment growth cannot silently inflate the collider");
        restored.advanceTissue(Integer.MAX_VALUE);
        h.assertTrue(restored.tissueAge()==4300L+Integer.MAX_VALUE,"evolution continues beyond the old final form and int range");
        var ancientTag=new CompoundTag();restored.saveWithoutId(ancientTag);
        var ancient=ModEntities.INFECTED_HOST.get().create(h.getLevel());ancient.load(ancientTag);
        h.assertTrue(ancient.tissueAge()==restored.tissueAge()&&ancient.getUUID().equals(host.getUUID()),"late age and anatomy variant survive reload");
        h.assertTrue(restored.getBbWidth()>width&&restored.getBbHeight()>height,"later skeleton growth and collider develop together");
        h.assertTrue(ancient.getBbWidth()==restored.getBbWidth()&&ancient.getBbHeight()==restored.getBbHeight(),"accepted physical growth survives reload");
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue")
    public static void confinedEvolutionWaitsForRoomAndMigratesOldIntAges(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,30,5));
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++) {
            level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
            level.setBlock(pos.offset(x,2,z),Blocks.STONE.defaultBlockState(),2);
        }
        var host=ModEntities.INFECTED_HOST.get().create(level);host.inherit(EntityType.COW.create(level));host.setNoAi(true);
        host.setPos(pos.getCenter().add(0,-.5,0));level.addFreshEntity(host);
        host.advanceTissue(120000);
        h.assertTrue(host.tissueAge()==120000&&host.tissueBodyScale()==1,"mutation continues but a larger body cannot clip into the ceiling");
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)level.setBlock(pos.offset(x,2,z),Blocks.AIR.defaultBlockState(),2);
        double feet=host.getY();host.advanceTissue(1);
        h.assertTrue(host.tissueBodyScale()>1.3F&&host.getY()==feet,"whole skeleton grows from its planted feet when space becomes free");
        var tag=new CompoundTag();host.saveWithoutId(tag);tag.putInt("BlacklightTissueAge",24000);tag.remove("BlacklightTissueScale");
        var migrated=ModEntities.INFECTED_HOST.get().create(level);migrated.load(tag);
        h.assertTrue(migrated.tissueAge()==24000&&migrated.tissueBodyScale()==1,"old integer-age saves remain valid without sudden enlargement");
        host.discard();migrated.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue")
    public static void attachmentMaturesAndLosesSupport(GameTestHelper h) {
        var level=h.getLevel();var center=h.absolutePos(new BlockPos(5,15,5));
        for(var direction:Direction.values()) {
            var pos=center.relative(direction,3);var support=pos.relative(direction);
            level.setBlock(support,Blocks.STONE.defaultBlockState(),3);
            var state=ModBlocks.TISSUE_ATTACHMENT.get().defaultBlockState().setValue(TissueAttachmentBlock.FACING,direction);
            h.assertTrue(state.canSurvive(level,pos),"attachment supports all six faces");
            level.setBlock(pos,state,3);
            var random=RandomSource.create(19);
            for(int i=0;i<100;i++)level.getBlockState(pos).randomTick(level,pos,random);
            h.assertTrue(level.getBlockState(pos).getValue(TissueAttachmentBlock.AGE)==3,"surface tissue reaches maturity");
            level.removeBlock(support,false);
            h.assertTrue(level.isEmptyBlock(pos),"removing the support removes dangling tissue");
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue")
    public static void spreadHonorsGriefingAndDoesNotReplaceBlocks(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,25,5));
        var rule=level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);boolean previous=rule.get();
        var source=ModBlocks.INFECTED_SOIL.get().defaultBlockState();
        try {
            level.setBlock(pos,source,3);
            for(var d:Direction.values())level.setBlock(pos.relative(d),Blocks.AIR.defaultBlockState(),3);
            rule.set(false,level.getServer());
            for(int i=0;i<200;i++)source.randomTick(level,pos,RandomSource.create(i));
            for(var d:Direction.values())h.assertTrue(level.isEmptyBlock(pos.relative(d)),"mobGriefing disables new attachments");
            var outer=new java.util.HashMap<Direction,net.minecraft.world.level.block.state.BlockState>();
            for(var d:Direction.values())outer.put(d,level.getBlockState(pos.relative(d,2)));
            rule.set(true,level.getServer());
            level.setBlock(pos.above(),Blocks.CHEST.defaultBlockState(),3);
            var random=RandomSource.create(98);
            for(int i=0;i<300;i++)source.randomTick(level,pos,random);
            h.assertTrue(level.getBlockState(pos.above()).is(Blocks.CHEST),"growth never replaces existing blocks or inventories");
            int count=0;for(var d:Direction.values())if(level.getBlockState(pos.relative(d)).is(ModBlocks.TISSUE_ATTACHMENT.get()))count++;
            h.assertTrue(count==5,"exposed infected terrain seeds its five free faces");
            for(var d:Direction.values())h.assertTrue(level.getBlockState(pos.relative(d,2)).equals(outer.get(d)),"attachments do not recursively spread");
        } finally {rule.set(previous,level.getServer());}
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=180,batch="tissue_attack")
    public static void matureCowActuallyAttacks(GameTestHelper h) {
        var level=h.getLevel();level.getServer().setDifficulty(Difficulty.NORMAL,true);
        var pos=h.absolutePos(new BlockPos(5,30,5));
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
        var host=ModEntities.INFECTED_HOST.get().create(level);host.inherit(EntityType.COW.create(level));
        host.advanceTissue(12000);host.setPos(pos.getCenter().add(0,-.5,0));level.addFreshEntity(host);
        var prey=EntityType.COW.create(level);prey.setNoAi(true);prey.setPos(host.position().add(1,0,0));level.addFreshEntity(prey);
        float health=prey.getHealth()/prey.getMaxHealth();host.setTarget(prey);
        h.runAfterDelay(100,()->{
            boolean attacked=prey.getHealth()/prey.getMaxHealth()<health||!prey.isAlive()||prey.isRemoved();
            host.discard();prey.discard();
            h.assertTrue(attacked,"formerly passive cow must cause actual server damage using infection AI");
            h.succeed();
        });
    }
}
