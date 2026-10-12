package com.codex.blacklight.combat;

import com.codex.blacklight.power.CombatSkill;
import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Height-powered terrain is Minecraft adaptation: Prototype has no voxel mining model.
 * A streamed bowl consumes energy by hardness, with ordinary per-block protection events.
 */
@EventBusSubscriber(modid="blacklight")
public final class BulletdiveTerrain {
    public static final int MAX_WORK_PER_TICK=192, MAX_FEEDBACK_PER_TICK=8;
    private static final int MAX_ACTIVE=16, MAX_HISTORY=128;
    private static final long TICK_NANOS=3_000_000;
    /** budget is the theoretical unit-hardness-zero block count, not a work/candidate cutoff. */
    public record Plan(double height,int radius,int depth,long budget,double energy) { }
    public record Snapshot(Plan plan,boolean complete,long visited,long destroyed,long protectedBlocks,
                           long skipped,double energyRemaining,double maximumRadius,int maximumDepth,String reason) { }
    private record Key(UUID owner,long session) { }
    record Column(int x,int z,long distanceSquared) { }
    private static final Map<Key,Job> JOBS=new LinkedHashMap<>();
    private static final ThreadLocal<BlockEvent.BreakEvent> OWN_BREAK=new ThreadLocal<>();
    private static int nextJob;
    private BulletdiveTerrain() { }

    public static Plan plan(double actualHeight) {
        double height=Double.isFinite(actualHeight)?Math.max(0,actualHeight):0;
        if(height<=6)return new Plan(height,0,0,0,0);
        double excess=height-6;
        // Only Java coordinate/count representation saturates, not a gameplay height cap.
        int radius=(int)Math.min(Integer.MAX_VALUE,2+Math.floor(Math.sqrt(excess*.5)));
        int depth=(int)Math.min(Integer.MAX_VALUE,1+Math.floor(Math.cbrt(excess)));
        double energy=excess>Double.MAX_VALUE/8?Double.MAX_VALUE:excess*8;
        long budget=energy>=Long.MAX_VALUE?Long.MAX_VALUE:(long)Math.floor(energy);
        return new Plan(height,radius,depth,budget,energy);
    }
    public static int columnDepth(Plan plan,long distanceSquared) {
        long radiusSquared=(long)plan.radius*plan.radius;
        if(plan.radius<=0||distanceSquared<0||distanceSquared>=radiusSquared)return 0;
        return (int)Math.ceil(plan.depth*(1-distanceSquared/(double)radiusSquared));
    }
    public static double localStrength(Plan plan,long distanceSquared,int layer) {
        return Math.max(0,(plan.height-6)*.12-Math.sqrt(Math.max(0,distanceSquared))*.15-Math.max(0,layer)*.5);
    }
    public static double hardnessCost(float hardness) { return Float.isFinite(hardness)&&hardness>=0?1D+hardness*3D:Double.POSITIVE_INFINITY; }
    public static boolean canBreak(Plan plan,long distanceSquared,int layer,float hardness,float resistance,boolean blockEntity) {
        return distanceSquared>=0&&layer>=0&&layer<columnDepth(plan,distanceSquared)
                &&!blockEntity&&Float.isFinite(hardness)&&hardness>=0&&Float.isFinite(resistance)&&resistance<1200
                &&hardness<=localStrength(plan,distanceSquared,layer)&&hardnessCost(hardness)<=plan.energy;
    }
    /** Only this exact event bypasses Blacklight's vanilla-mining lock, never external protection. */
    public static boolean ownsBreakEvent(BlockEvent.BreakEvent event) { return OWN_BREAK.get()==event; }
    public static Snapshot snapshot(ServerPlayer p,long session) {
        var job=JOBS.get(new Key(p.getUUID(),session));return job==null?null:job.snapshot();
    }
    public static void cancel(ServerPlayer p,long session) {
        var job=JOBS.get(new Key(p.getUUID(),session));if(job!=null&&!job.complete)job.finish("canceled");
    }
    public static Plan begin(ServerPlayer p,long session,Vec3 impact,double fallHeight) {
        if(!p.server.isSameThread())throw new IllegalStateException("Bulletdive terrain must start on the server thread");
        var key=new Key(p.getUUID(),session);var prior=JOBS.get(key);if(prior!=null)return prior.plan;
        var plan=plan(fallHeight);
        var job=new Job(p,key,plan,impact);JOBS.put(key,job);
        if(!eligible(p)||!Double.isFinite(impact.x)||!Double.isFinite(impact.y)||!Double.isFinite(impact.z)
                ||!job.level.isInWorldBounds(job.center))job.finish("ineligible");
        else if(plan.radius==0)job.finish("below_height_threshold");
        else if(JOBS.values().stream().filter(j->!j.complete).count()>MAX_ACTIVE)job.finish("queue_full");
        trimHistory();return plan;
    }
    private static boolean eligible(ServerPlayer p) {
        var state=p.getData(ModAttachments.PLAYER_STATE);
        return p.isAlive()&&!p.isSpectator()&&p.mayBuild()&&state.isInfected()&&state.isPrototypeMode()
                &&state.hasCombatSkill(CombatSkill.BULLETDIVE_DROP);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        int work=0,feedback=0;long until=System.nanoTime()+TICK_NANOS;
        var active=new ArrayList<Job>();
        for(var job:JOBS.values()) {
            if(job.complete)continue;
            if(!eligible(job.owner)||job.owner.isRemoved()||job.owner.level()!=job.level){job.finish("owner_unavailable");continue;}
            active.add(job);
        }
        int cursor=active.isEmpty()?0:Math.floorMod(nextJob,active.size()),idle=0;
        while(!active.isEmpty()&&work<MAX_WORK_PER_TICK&&System.nanoTime()<until&&idle<active.size()){
            var job=active.get(cursor);cursor=(cursor+1)%active.size();
            if(job.complete){idle++;continue;}
            idle=0;
            if(job.next(feedback<MAX_FEEDBACK_PER_TICK))feedback++;
            work++;
        }
        nextJob=cursor;
        trimHistory();
    }
    private static void trimHistory() {
        if(JOBS.size()<=MAX_HISTORY)return;
        var iterator=JOBS.entrySet().iterator();
        while(JOBS.size()>MAX_HISTORY&&iterator.hasNext())if(iterator.next().getValue().complete)iterator.remove();
    }
    private static void cancel(ServerPlayer p) { for(var job:JOBS.values())if(job.owner==p&&!job.complete)job.finish("owner_unavailable"); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void death(LivingDeathEvent event){if(event.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){JOBS.clear();OWN_BREAK.remove();nextJob=0;}

    @FunctionalInterface interface LoadedChunkLookup { boolean loaded(int x,int z); }
    private record ChunkProbe(int x,int z,long distanceSquared) { }
    private static final class ChunkColumns {
        final Column[] columns;int index;
        ChunkColumns(Plan plan,int centerX,int centerZ,int chunkX,int chunkZ){
            var found=new ArrayList<Column>(256);
            long originX=(long)chunkX*16,originZ=(long)chunkZ*16;
            for(int x=0;x<16;x++)for(int z=0;z<16;z++){
                long worldX=originX+x,worldZ=originZ+z,dx=worldX-centerX,dz=worldZ-centerZ;
                if(worldX<Integer.MIN_VALUE||worldX>Integer.MAX_VALUE||worldZ<Integer.MIN_VALUE||worldZ>Integer.MAX_VALUE
                        ||Math.abs(dx)>=plan.radius||Math.abs(dz)>=plan.radius)continue;
                long distance=dx*dx+dz*dz;
                if(columnDepth(plan,distance)>0)found.add(new Column((int)dx,(int)dz,distance));
            }
            found.sort(Comparator.comparingLong(Column::distanceSquared).thenComparingInt(Column::x).thenComparingInt(Column::z));
            columns=found.toArray(Column[]::new);
        }
        Column peek(){return columns[index];}
    }
    /** At most one 16x16 column array per active loaded chunk; never radius²×depth candidates.
     * A step performs either one non-loading chunk probe or yields one radial column.
     * Unloaded gaps stop propagation; remote loaded islands cannot be reached through a gap.
     */
    static final class ColumnCursor {
        private final Plan plan;private final int centerX,centerZ;private final LoadedChunkLookup loaded;
        private final Set<Long> discovered=new HashSet<>();
        private final PriorityQueue<ChunkProbe> probes=new PriorityQueue<>(Comparator.comparingLong(ChunkProbe::distanceSquared)
                .thenComparingInt(ChunkProbe::x).thenComparingInt(ChunkProbe::z));
        private final PriorityQueue<ChunkColumns> columns=new PriorityQueue<>(Comparator.comparingLong((ChunkColumns c)->c.peek().distanceSquared)
                .thenComparingInt(c->c.peek().x).thenComparingInt(c->c.peek().z));
        ColumnCursor(Plan plan,int centerX,int centerZ,LoadedChunkLookup loaded){
            this.plan=plan;this.centerX=centerX;this.centerZ=centerZ;this.loaded=loaded;
            offer(centerX>>4,centerZ>>4);
        }
        private void offer(long x,long z){
            if(x<Integer.MIN_VALUE||x>Integer.MAX_VALUE||z<Integer.MIN_VALUE||z>Integer.MAX_VALUE||plan.radius<=0)return;
            long originX=x*16,originZ=z*16;
            long dx=Math.clamp((long)centerX,originX,originX+15)-centerX;
            long dz=Math.clamp((long)centerZ,originZ,originZ+15)-centerZ;
            // Check each axis before squaring: even a distant absolute chunk coordinate cannot overflow.
            if(Math.abs(dx)>=plan.radius||Math.abs(dz)>=plan.radius)return;
            long distance=dx*dx+dz*dz;
            if(distance>=(long)plan.radius*plan.radius)return;
            long key=(x&0xffffffffL)|(z&0xffffffffL)<<32;
            if(discovered.add(key))probes.add(new ChunkProbe((int)x,(int)z,distance));
        }
        boolean exhausted(){return probes.isEmpty()&&columns.isEmpty();}
        int trackedChunks(){return discovered.size();}
        void clear(){probes.clear();columns.clear();discovered.clear();}
        Column step(){
            if(!probes.isEmpty()&&(columns.isEmpty()||probes.peek().distanceSquared<=columns.peek().peek().distanceSquared)){
                var probe=probes.remove();
                if(loaded.loaded(probe.x,probe.z)){
                    var chunk=new ChunkColumns(plan,centerX,centerZ,probe.x,probe.z);
                    if(chunk.columns.length>0)columns.add(chunk);
                    offer((long)probe.x-1,probe.z);offer((long)probe.x+1,probe.z);
                    offer(probe.x,(long)probe.z-1);offer(probe.x,(long)probe.z+1);
                }
                return null;
            }
            if(columns.isEmpty())return null;
            var chunk=columns.remove();var column=chunk.columns[chunk.index++];
            if(chunk.index<chunk.columns.length)columns.add(chunk);
            return column;
        }
    }

    private static final class Job {
        final ServerPlayer owner;final Key key;final ServerLevel level;final Plan plan;final BlockPos center;
        final ColumnCursor columns;Column column;int layer,columnLayers;
        boolean complete;String reason="running";long visited,destroyed,protectedBlocks,skipped;int maxDepth;double energy,maxRadius;
        Job(ServerPlayer p,Key key,Plan plan,Vec3 impact) {
            owner=p;this.key=key;level=p.serverLevel();this.plan=plan;energy=plan.energy;
            center=BlockPos.containing(impact.x,impact.y-.075,impact.z);
            columns=new ColumnCursor(plan,center.getX(),center.getZ(),(x,z)->level.getChunkSource().getChunkNow(x,z)!=null);
        }
        boolean loaded(BlockPos pos){return level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;}
        boolean next(boolean feedback) {
            if(energy<1){finish("energy_exhausted");return false;}
            if(column==null){
                column=columns.step();
                if(column==null){if(columns.exhausted())finish("complete");return false;}
                layer=0;columnLayers=(int)Math.min(columnDepth(plan,column.distanceSquared),
                        Math.max(0,(long)center.getY()-level.getMinBuildHeight()+1));
                if(columnLayers==0){column=null;return false;}
            }
            var current=column;int currentLayer=layer++;
            if(layer>=columnLayers)column=null;
            visited++;
            long x=(long)center.getX()+current.x,y=(long)center.getY()-currentLayer,z=(long)center.getZ()+current.z;
            if(x<Integer.MIN_VALUE||x>Integer.MAX_VALUE||y<Integer.MIN_VALUE||y>Integer.MAX_VALUE||z<Integer.MIN_VALUE||z>Integer.MAX_VALUE){skipped++;column=null;return false;}
            var pos=new BlockPos((int)x,(int)y,(int)z);
            if(!level.isInWorldBounds(pos)||!loaded(pos)){skipped++;column=null;return false;}
            if(!level.mayInteract(owner,pos)||!owner.mayBuild()){protectedBlocks++;column=null;return false;}
            var state=level.getBlockState(pos);
            if(state.isAir()||!state.getFluidState().isEmpty()){skipped++;return false;}
            float hardness=state.getDestroySpeed(level,pos),resistance=state.getBlock().getExplosionResistance();
            if(state.hasBlockEntity()||level.getBlockEntity(pos)!=null||hardness<0||resistance>=1200){protectedBlocks++;column=null;return false;}
            double cost=hardnessCost(hardness);
            if(!canBreak(plan,current.distanceSquared,currentLayer,hardness,resistance,false)||cost>energy){skipped++;column=null;return false;}
            var event=new BlockEvent.BreakEvent(level,pos,state,owner);var previous=OWN_BREAK.get();OWN_BREAK.set(event);
            try { NeoForge.EVENT_BUS.post(event); } finally { if(previous==null)OWN_BREAK.remove();else OWN_BREAK.set(previous); }
            if(complete||!eligible(owner)||owner.level()!=level){finish("owner_unavailable");return false;}
            if(event.isCanceled()||!loaded(pos)||!level.mayInteract(owner,pos)){protectedBlocks++;column=null;return false;}
            if(!level.getBlockState(pos).equals(state)){skipped++;column=null;return false;}
            boolean broken;
            if(feedback)broken=level.destroyBlock(pos,false,owner);
            else {
                broken=level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
                if(broken)level.gameEvent(GameEvent.BLOCK_DESTROY,pos,GameEvent.Context.of(owner,state));
            }
            if(!broken){skipped++;column=null;return false;}
            energy=Math.max(0,energy-cost);destroyed++;
            maxRadius=Math.max(maxRadius,Math.sqrt(current.distanceSquared));maxDepth=Math.max(maxDepth,currentLayer+1);
            return feedback;
        }
        void finish(String reason){complete=true;this.reason=reason;column=null;columns.clear();}
        Snapshot snapshot(){return new Snapshot(plan,complete,visited,destroyed,protectedBlocks,skipped,energy,maxRadius,maxDepth,reason);}
    }
}
