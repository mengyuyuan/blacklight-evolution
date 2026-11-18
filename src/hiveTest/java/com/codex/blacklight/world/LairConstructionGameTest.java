package com.codex.blacklight.world;

import com.codex.blacklight.registry.ModEntities;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class LairConstructionGameTest {
    @GameTest(template="wall_test",batch="lair_journal")
    public static void interruptedLairResumesWithoutOverwritingPlayerEdits(GameTestHelper h) {
        var level=h.getLevel();
        var origin=h.absolutePos(new BlockPos(1,2,1));
        var alreadySaved=origin.east();
        var stillPending=origin.east(2);
        var changed=origin.south();
        var container=origin.south(2);
        var edits=new LinkedHashMap<Long,LairConstruction.Edit>();
        for(var pos:List.of(alreadySaved,stillPending,changed,container)){
            level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
            edits.put(pos.asLong(),new LairConstruction.Edit(Blocks.STONE.defaultBlockState(),Blocks.TUFF.defaultBlockState()));
        }
        var job=LairConstruction.plan(origin,edits,List.of());
        var beforeCrash=new LairSystem.Data();
        beforeCrash.jobs.add(job);
        beforeCrash.seen.add(123L);
        // The job cursor was ahead of the last persisted chunk at shutdown.
        job.cursor=job.positions.length;
        level.setBlock(alreadySaved,Blocks.TUFF.defaultBlockState(),2);
        level.setBlock(changed,Blocks.GOLD_BLOCK.defaultBlockState(),2);
        level.setBlock(container,Blocks.CHEST.defaultBlockState(),2);
        var chest=(ChestBlockEntity)level.getBlockEntity(container);
        chest.setItem(0,new ItemStack(Items.DIAMOND,3));

        var tag=beforeCrash.save(new CompoundTag());
        var resumed=LairSystem.Data.load(tag);
        h.assertTrue(resumed.lairs.isEmpty()&&resumed.jobs.size()==1,"unfinished entrance is journaled, not marked complete");
        h.assertTrue(resumed.seen.isEmpty()&&resumed.pending.isEmpty()&&resumed.pending!=beforeCrash.pending,"runtime candidate queues belong to each world");
        var recovered=resumed.jobs.get(0);
        LairSystem.placeBlocks(level,recovered,Long.MAX_VALUE);
        h.assertTrue(level.getBlockState(stillPending).is(Blocks.TUFF)&&level.getBlockState(alreadySaved).is(Blocks.TUFF),"both sides of the crash checkpoint converge");
        h.assertTrue(level.getBlockState(changed).is(Blocks.GOLD_BLOCK),"changed ordinary block survives replay");
        h.assertTrue(level.getBlockEntity(container)==chest&&chest.getItem(0).is(Items.DIAMOND)&&chest.getItem(0).getCount()==3,"the chest and its actual contents survive replay");
        h.succeed();
    }

    @GameTest(template="wall_test",batch="lair_commit")
    public static void restoredConstructionCompletesInTheNormalWorldTick(GameTestHelper h) {
        var level=h.getLevel();
        var origin=h.absolutePos(new BlockPos(3,2,3));
        var pos=origin.east();
        var data=LairSystem.Data.get(level);
        boolean savingDisabled=level.noSave;
        try{
            level.noSave=true;
            h.assertTrue(LairSystem.tryPlace(level,origin.getX(),origin.getZ(),true)==LairSystem.Result.WAIT,"save-off prevents a forced disk checkpoint");
        }finally{level.noSave=savingDisabled;}
        level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
        var edits=new LinkedHashMap<Long,LairConstruction.Edit>();
        edits.put(pos.asLong(),new LairConstruction.Edit(Blocks.STONE.defaultBlockState(),Blocks.TUFF.defaultBlockState()));
        var job=LairConstruction.plan(origin,edits,List.of());
        data.jobs.add(LairConstruction.load(job.save()));
        data.setDirty();
        h.succeedWhen(()->{
            h.assertTrue(data.lairs.contains(origin.asLong()),"the normal level tick registered completion");
            h.assertTrue(level.getBlockState(pos).is(Blocks.TUFF),"work is applied before the entrance is marked complete");
            h.assertTrue(data.jobs.stream().noneMatch(item->item.entrance.equals(origin)),"the completed journal is released");
            data.lairs.remove(origin.asLong());
            data.setDirty();
        });
    }

    @GameTest(template="wall_test",batch="lair_journal")
    public static void recoveredHiveSpawnsOnce(GameTestHelper h) {
        var level=h.getLevel();
        var origin=h.absolutePos(new BlockPos(2,2,2));
        var seed=ModEntities.BLACKLIGHT_HIVE.get().create(level);
        seed.moveTo(origin.getX()+.5,origin.getY(),origin.getZ()+.5,45,0);
        seed.setGrowth(.45F);
        seed.getPersistentData().putBoolean("BlacklightLair",true);
        var job=LairConstruction.plan(origin,new LinkedHashMap<>(),List.of(seed.saveWithoutId(new CompoundTag())));
        var snapshot=job.save();
        h.succeedWhen(()->{
            boolean[] vetoed={false};
            Consumer<EntityJoinLevelEvent> veto=event->{
                if(event.getEntity().getUUID().equals(seed.getUUID())){vetoed[0]=true;event.setCanceled(true);}
            };
            MinecraftForge.EVENT_BUS.addListener(veto);
            try{
                h.assertTrue(!LairSystem.spawnHives(level,job) && vetoed[0],"rejected join keeps the job incomplete");
            }finally{MinecraftForge.EVENT_BUS.unregister(veto);}
            h.assertTrue(level.getEntity(seed.getUUID())==null,"vetoed hive was not registered");
            h.assertTrue(LairSystem.spawnHives(level,job),"allowed retry adds the planned hive");
            var original=level.getEntity(seed.getUUID());
            h.assertTrue(original!=null,"planned hive registered with saved identity");
            h.assertTrue(LairSystem.spawnHives(level,LairConstruction.load(snapshot))&&level.getEntity(seed.getUUID())==original,"replaying a committed hive keeps the same entity");
            original.discard();
        });
    }
}
