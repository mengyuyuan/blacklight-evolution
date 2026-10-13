package com.codex.blacklight.combat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Vanilla destruction and loot remain intact. Coalesce its redundant feedback
 * and merge only this job's newly created, nearby drops through vanilla merging. */
@EventBusSubscriber(modid="blacklight")
public final class BulletdiveBreakEffects {
    private static final ThreadLocal<BulletdiveBreakEffects> ACTIVE=new ThreadLocal<>();
    private final HashSet<ItemEntity> recent=new HashSet<>();
    private final HashMap<BlockPos,ArrayList<ItemEntity>> cells=new HashMap<>();
    private int indexedTick=Integer.MIN_VALUE,newItems;
    private final ArrayList<ItemEntity> spawned=new ArrayList<>();
    private static Object server;
    private static int feedbackTick, feedbackCount;
    private Level level;
    public boolean destroy(ServerLevel level,net.minecraft.core.BlockPos pos,net.minecraft.server.level.ServerPlayer owner) {
        var prior=ACTIVE.get();this.level=level;spawned.clear();newItems=0;
        refreshIndex(level.getServer().getTickCount());ACTIVE.set(this);
        boolean broken;
        try {broken=level.destroyBlock(pos,true,owner);}
        finally {if(prior==null)ACTIVE.remove();else ACTIVE.set(prior);}
        // Surviving entities have been added. Preserve all loot events and counts;
        // only empty shells merged during join are canceled below.
        for(var item:spawned) {
            if(!item.isAlive()||!item.isAddedToLevel())continue;
            newItems++;
            for(var existing:nearby(item)) {
                if(!item.isAlive())break;
                if(existing.isAlive()&&existing.distanceToSqr(item)<16
                        &&((com.codex.blacklight.mixin.BulletdiveItemMergeAccess)existing).blacklight$mergeable()
                        &&((com.codex.blacklight.mixin.BulletdiveItemMergeAccess)item).blacklight$mergeable())
                    ((com.codex.blacklight.mixin.BulletdiveItemMergeAccess)existing).blacklight$merge(item);
            }
            remember(item);
        }
        spawned.clear();return broken;
    }
    public int newItems(){return newItems;}
    private static boolean mergeable(ItemEntity item){
        return ((com.codex.blacklight.mixin.BulletdiveItemMergeAccess)item).blacklight$mergeable();
    }
    private static BlockPos cell(ItemEntity item){
        return BlockPos.containing(Math.floor(item.getX()/4),Math.floor(item.getY()/4),Math.floor(item.getZ()/4));
    }
    private void refreshIndex(int tick){
        if(indexedTick==tick)return;
        indexedTick=tick;recent.removeIf(item->item.level()!=level||!mergeable(item));cells.clear();
        for(var item:recent)cells.computeIfAbsent(cell(item),key->new ArrayList<>()).add(item);
    }
    private void remember(ItemEntity item){
        if(!mergeable(item)||recent.size()>=2048||!recent.add(item))return;
        cells.computeIfAbsent(cell(item),key->new ArrayList<>()).add(item);
    }
    private ArrayList<ItemEntity> nearby(ItemEntity item){
        var found=new ArrayList<ItemEntity>();var at=cell(item);
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
            var bucket=cells.get(at.offset(x,y,z));if(bucket!=null)found.addAll(bucket);
        }
        return found;
    }
    public static boolean allowFeedback(Level level,int event) {
        var scope=ACTIVE.get();
        if(scope==null||scope.level!=level||event!=2001)return true;
        var current=level.getServer();int tick=current.getTickCount();
        if(server!=current||feedbackTick!=tick){server=current;feedbackTick=tick;feedbackCount=0;}
        return feedbackCount++<BulletdiveTerrain.MAX_FEEDBACK_PER_TICK;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void spawned(EntityJoinLevelEvent event) {
        var scope=ACTIVE.get();
        if(scope==null||event.isCanceled()||event.getLevel()!=scope.level
                ||!(event.getEntity() instanceof ItemEntity item))return;
        // Merge a smaller incoming stack into a live, already tracked survivor.
        // This avoids thousands of add/remove tracking operations in one impact.
        // Strict count ordering ensures vanilla never discards the existing
        // survivor here; equal-sized stacks still use the post-add path above.
        for(var existing:scope.nearby(item)){
            if(existing.isAlive()&&existing.isAddedToLevel()&&existing.level()==scope.level
                    &&existing.getItem().getCount()>item.getItem().getCount()&&existing.distanceToSqr(item)<16
                    &&((com.codex.blacklight.mixin.BulletdiveItemMergeAccess)existing).blacklight$mergeable()
                    &&((com.codex.blacklight.mixin.BulletdiveItemMergeAccess)item).blacklight$mergeable()){
                ((com.codex.blacklight.mixin.BulletdiveItemMergeAccess)existing).blacklight$merge(item);
                if(!item.isAlive()&&item.getItem().isEmpty()){
                    // Only the empty entity shell is canceled; its entire stack
                    // is already preserved in the vanilla merged survivor.
                    event.setCanceled(true);return;
                }
            }
        }
        if(scope.spawned.size()<256)scope.spawned.add(item);
    }
    public void clear(){recent.clear();cells.clear();spawned.clear();level=null;indexedTick=Integer.MIN_VALUE;}
    @SubscribeEvent public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event){
        server=null;feedbackCount=0;ACTIVE.remove();
    }
}
