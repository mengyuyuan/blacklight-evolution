package com.codex.blacklight.combat;

import com.codex.blacklight.power.CombatSkill;
import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.LiquidBlock;
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
    public static final int MAX_WORK_PER_TICK=8192, MAX_BREAKS_PER_TICK=512;
    /** Shared crack audio/particles; every successful block still has vanilla loot. */
    public static final int MAX_FEEDBACK_PER_TICK=2;
    private static final int MAX_HISTORY=128;
    private static final long TICK_NANOS=8_000_000;
    private static long tickStarted;
    /** The destruction slice alone does not include next tick's loot physics,
     * fluid/falling-block updates or chunk tracking. Leave time for that work. */
    @SubscribeEvent public static void beforeTick(ServerTickEvent.Pre event){tickStarted=System.nanoTime();}

    static long availableNanos(long elapsed){
        return elapsed<0?0:Math.min(TICK_NANOS,Math.max(0,40_000_000-elapsed));
    }
    static long remainingTickNanos(){return availableNanos(System.nanoTime()-tickStarted);}
    static final class LoadBudget {
        static final int MAX_ITEMS=768, MAX_FALLING=128, MAX_XP=512;
        int items,falling,xp;
        LoadBudget(int items,int falling,int xp){this.items=items;this.falling=falling;this.xp=xp;}
        boolean available(){return items<MAX_ITEMS&&falling<MAX_FALLING&&xp<MAX_XP;}
        void spawnedItems(int count){items+=Math.max(0,count);}
        static LoadBudget sample(ServerLevel level){
            var result=new LoadBudget(0,0,0);
            for(var entity:level.getAllEntities())if(entity.isAlive()){
                if(entity instanceof net.minecraft.world.entity.item.ItemEntity)result.items++;
                else if(entity instanceof net.minecraft.world.entity.item.FallingBlockEntity)result.falling++;
                else if(entity instanceof net.minecraft.world.entity.ExperienceOrb)result.xp++;
            }
            return result;
        }
    }
    /** budget is the theoretical unit-hardness-zero block count, not a work/candidate cutoff. */
    public record Plan(double height,int radius,int depth,long budget,double energy) { }
    public record Snapshot(Plan plan,boolean complete,long visited,long destroyed,long protectedBlocks,
                           long skipped,double energyRemaining,double maximumRadius,int maximumDepth,String reason, long elapsedTicks,long firstTickDestroyed,int peakBreaksPerTick,double peakSliceMillis) { }
    private record Key(UUID owner,long session) { }
    record Column(int x,int z,long distanceSquared) { }
    private static final Map<Key,Job> JOBS=new LinkedHashMap<>();
    private static final OwnerTasks<Job> ACTIVE_OWNERS=new OwnerTasks<>();
    private static final ThreadLocal<BlockEvent.BreakEvent> OWN_BREAK=new ThreadLocal<>();
    private static int nextJob;
    private BulletdiveTerrain() { }

    public static Plan plan(double actualHeight) {
        double height=Double.isFinite(actualHeight)?Math.max(0,actualHeight):0;
        if(height<=6)return new Plan(height,0,0,0,0);
        double excess=height-6;
        // Only Java coordinate/count representation saturates, not a gameplay height cap.
        int radius=(int)Math.min(Integer.MAX_VALUE,2+Math.floor(Math.sqrt(excess*.5)));
        int depth=(int)Math.min(Integer.MAX_VALUE,2+Math.floor(Math.sqrt(excess)*.75));
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
                // Containers and positive-hardness blast-resistant blocks are
                // ordinary break candidates. Negative hardness is unbreakable.
                &&Float.isFinite(hardness)&&hardness>=0&&Float.isFinite(resistance)
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
        var key=new Key(p.getUUID(),session);var prior=JOBS.get(key);
        if(!blockBreakingEnabled(p)){
            var active=ACTIVE_OWNERS.current(p.getUUID());
            if(active!=null)active.finish("block_breaking_disabled");
            return prior==null?plan(fallHeight):prior.plan;
        }
        if(prior!=null)return prior.plan;
        var plan=plan(fallHeight);
        var job=new Job(p,key,plan,impact);JOBS.put(key,job);
        if(!eligible(p)||!Double.isFinite(impact.x)||!Double.isFinite(impact.y)||!Double.isFinite(impact.z)
                ||!job.level.isInWorldBounds(job.center))job.finish("ineligible");
        else if(plan.radius==0)job.finish("below_height_threshold");
        else ACTIVE_OWNERS.install(p.getUUID(),job,old->old.finish("superseded"));
        trimHistory();return plan;
    }
    private static boolean blockBreakingEnabled(ServerPlayer p) {
        return p.getData(ModAttachments.SKILL_SETTINGS).bulletdiveBlockBreaking();
    }
    /** Invalidate now, even if an OFF and ON request arrive in the same tick. */
    public static void settingsChanged(ServerPlayer p){
        if(!p.server.isSameThread())throw new IllegalStateException("Terrain preference changes require the server thread");
        if(!blockBreakingEnabled(p)){
            var job=ACTIVE_OWNERS.current(p.getUUID());
            if(job!=null)job.finish("block_breaking_disabled");
        }
    }
    private static boolean eligible(ServerPlayer p) {
        var state=p.getData(ModAttachments.PLAYER_STATE);
        return p.isAlive()&&!p.isSpectator()&&p.mayBuild()&&state.isInfected()&&state.isPrototypeMode()
                &&state.hasCombatSkill(CombatSkill.BULLETDIVE_DROP);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        long sliceStart=System.nanoTime();
        var budget=new TickBudget(MAX_WORK_PER_TICK,MAX_BREAKS_PER_TICK,availableNanos(sliceStart-tickStarted),System::nanoTime);
        var loads=new IdentityHashMap<ServerLevel,LoadBudget>();
        var active=new ArrayList<Job>();
        for(var job:ACTIVE_OWNERS.snapshot()) {
            if(job.complete)continue;
            if(!blockBreakingEnabled(job.owner)){job.finish("block_breaking_disabled");continue;}
            if(!eligible(job.owner)||job.owner.isRemoved()||job.owner.level()!=job.level){job.finish("owner_unavailable");continue;}
            active.add(job);
        }
        int cursor=active.isEmpty()?0:Math.floorMod(nextJob,active.size()),idle=0;
        while(!active.isEmpty()&&idle<active.size()){
            var job=active.get(cursor);cursor=(cursor+1)%active.size();
            if(job.complete){idle++;continue;}
            var load=loads.computeIfAbsent(job.level,LoadBudget::sample);
            // Pause without consuming energy/candidates. All owners share the
            // same dimension budget, including loot left by completed attacks.
            if(!load.available()){idle++;continue;}
            idle=0;
            if(!budget.tryCandidate())break;
            if(job.next()){budget.recordBreak();load.spawnedItems(job.effects.newItems());}
        }
        double sliceMillis=(System.nanoTime()-sliceStart)/1e6;
        for(var job:active)job.peakSliceMillis=Math.max(job.peakSliceMillis,sliceMillis);
        nextJob=cursor;
        trimHistory();
    }
    private static void trimHistory() {
        if(JOBS.size()<=MAX_HISTORY)return;
        var iterator=JOBS.entrySet().iterator();
        while(JOBS.size()>MAX_HISTORY&&iterator.hasNext())if(iterator.next().getValue().complete)iterator.remove();
    }
    private static void cancel(ServerPlayer p) {
        var job=ACTIVE_OWNERS.current(p.getUUID());if(job!=null&&job.owner==p)job.finish("owner_unavailable");
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void death(LivingDeathEvent event){if(event.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){JOBS.clear();ACTIVE_OWNERS.clear();OWN_BREAK.remove();nextJob=0;}

    /** One pending terrain attack per online owner. Superseding is an explicit
     * new attack, not a per-tick power cutoff. Completion of an old session must
     * never remove the replacement from its owner's active slot.
     */
    static final class OwnerTasks<T> {
        private final Map<UUID,T> tasks=new LinkedHashMap<>();
        T current(UUID owner){return tasks.get(owner);}
        void install(UUID owner,T task,Consumer<T> cancelPrevious){
            var previous=tasks.put(owner,task);
            if(previous!=null&&previous!=task)cancelPrevious.accept(previous);
        }
        void complete(UUID owner,T task){tasks.remove(owner,task);}
        List<T> snapshot(){return List.copyOf(tasks.values());}
        int size(){return tasks.size();}
        void clear(){tasks.clear();}
    }

    /** Global slice limits include actual drop-producing destruction, rather
     * than silently removing the remainder without loot. Pending work resumes
     * next tick; the limits never reduce a job's physical energy or shape.
     */
    static final class TickBudget {
        private final int maximumCandidates,maximumBreaks;
        private final long duration,started;
        private final LongSupplier clock;
        private int candidates,breaks;
        TickBudget(int maximumCandidates,int maximumBreaks,long duration,LongSupplier clock){
            if(maximumCandidates<1||maximumBreaks<1||duration<0)throw new IllegalArgumentException("Invalid terrain slice");
            this.maximumCandidates=maximumCandidates;this.maximumBreaks=maximumBreaks;this.duration=duration;this.clock=clock;
            started=clock.getAsLong();
        }
        boolean tryCandidate(){
            long elapsed=clock.getAsLong()-started;
            if(candidates>=maximumCandidates||breaks>=maximumBreaks||elapsed<0||elapsed>=duration)return false;
            candidates++;return true;
        }
        void recordBreak(){
            if(breaks>=maximumBreaks||breaks>=candidates)throw new IllegalStateException("Unbudgeted block destruction");
            breaks++;
        }
    }

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
        double nextDistance(){return Math.min(probes.isEmpty()?Double.POSITIVE_INFINITY:probes.peek().distanceSquared,
                columns.isEmpty()?Double.POSITIVE_INFINITY:columns.peek().peek().distanceSquared);}
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

    record WaveCell(Column column,int layer) {
        double distance(){return column.distanceSquared+layer*(double)layer*.12;}
    }
    /** Expand a downward-biased front, rather than drilling one complete shaft
     * before its neighbour. Rejected columns never enqueue cells below them. */
    static final class WaveCursor {
        final ColumnCursor columns;final Plan plan;final int maximumDepth;
        final PriorityQueue<WaveCell> pending=new PriorityQueue<>(Comparator.comparingDouble(WaveCell::distance)
                .thenComparingInt(WaveCell::layer).thenComparingInt(c->c.column.x).thenComparingInt(c->c.column.z));
        WaveCursor(Plan plan,int x,int z,int maximumDepth,LoadedChunkLookup loaded){
            this.plan=plan;this.maximumDepth=maximumDepth;columns=new ColumnCursor(plan,x,z,loaded);
        }
        WaveCell step(){
            if(!columns.exhausted()&&(pending.isEmpty()||columns.nextDistance()<=pending.peek().distance())){
                var column=columns.step();
                if(column!=null&&maximumDepth>0)pending.add(new WaveCell(column,0));
                return null;
            }
            return pending.poll();
        }
        void advance(WaveCell cell){
            int next=cell.layer+1;
            if(next<maximumDepth&&next<columnDepth(plan,cell.column.distanceSquared))pending.add(new WaveCell(cell.column,next));
        }
        boolean exhausted(){return columns.exhausted()&&pending.isEmpty();}
        void clear(){columns.clear();pending.clear();}
    }

    private static final class Job {
        final BulletdiveBreakEffects effects=new BulletdiveBreakEffects();
        final ServerPlayer owner;final Key key;final ServerLevel level;final Plan plan;final BlockPos center;
        final WaveCursor wave;final int startedTick;
        int endedTick,firstBreakTick=-1,lastBreakTick=-1,breaksThisTick,peakBreaksPerTick;
        long firstTickDestroyed;double peakSliceMillis;
        boolean complete;String reason="running";long visited,destroyed,protectedBlocks,skipped;int maxDepth;double energy,maxRadius;
        Job(ServerPlayer p,Key key,Plan plan,Vec3 impact) {
            owner=p;this.key=key;level=p.serverLevel();this.plan=plan;energy=plan.energy;startedTick=p.server.getTickCount();
            center=BlockPos.containing(impact.x,impact.y-.075,impact.z);
            wave=new WaveCursor(plan,center.getX(),center.getZ(),
                    (int)Math.min(Integer.MAX_VALUE,Math.max(0,(long)center.getY()-level.getMinBuildHeight()+1)),
                    (x,z)->level.getChunkSource().getChunkNow(x,z)!=null);
        }
        boolean loaded(BlockPos pos){return level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;}
        boolean next() {
            if(!blockBreakingEnabled(owner)){finish("block_breaking_disabled");return false;}
            if(energy<1){finish("energy_exhausted");return false;}
            var cell=wave.step();
            if(cell==null){if(wave.exhausted())finish("complete");return false;}
            var current=cell.column;int currentLayer=cell.layer;
            visited++;
            long x=(long)center.getX()+current.x,y=(long)center.getY()-currentLayer,z=(long)center.getZ()+current.z;
            if(x<Integer.MIN_VALUE||x>Integer.MAX_VALUE||y<Integer.MIN_VALUE||y>Integer.MAX_VALUE||z<Integer.MIN_VALUE||z>Integer.MAX_VALUE){skipped++;return false;}
            var pos=new BlockPos((int)x,(int)y,(int)z);
            if(!level.isInWorldBounds(pos)||!loaded(pos)){skipped++;return false;}
            if(!level.mayInteract(owner,pos)||!owner.mayBuild()){protectedBlocks++;return false;}
            var state=level.getBlockState(pos);
            if(state.isAir()||state.getBlock() instanceof LiquidBlock){skipped++;wave.advance(cell);return false;}
            float hardness=state.getDestroySpeed(level,pos),resistance=state.getBlock().getExplosionResistance();
            if(hardness<0){protectedBlocks++;return false;}
            double cost=hardnessCost(hardness);
            if(!canBreak(plan,current.distanceSquared,currentLayer,hardness,resistance,false)||cost>energy){skipped++;return false;}
            var event=new BlockEvent.BreakEvent(level,pos,state,owner);var previous=OWN_BREAK.get();OWN_BREAK.set(event);
            try { NeoForge.EVENT_BUS.post(event); } finally { if(previous==null)OWN_BREAK.remove();else OWN_BREAK.set(previous); }
            if(!blockBreakingEnabled(owner)){finish("block_breaking_disabled");return false;}
            if(complete)return false;
            if(!eligible(owner)||owner.isRemoved()||owner.level()!=level){finish("owner_unavailable");return false;}
            if(event.isCanceled()||!loaded(pos)||!level.mayInteract(owner,pos)){protectedBlocks++;return false;}
            if(!level.getBlockState(pos).equals(state)){skipped++;return false;}
            boolean broken=effects.destroy(level,pos,owner);
            if(!broken){skipped++;return false;}
            energy=Math.max(0,energy-cost);destroyed++;wave.advance(cell);
            int tick=owner.server.getTickCount();
            if(firstBreakTick<0)firstBreakTick=tick;
            if(tick==firstBreakTick)firstTickDestroyed++;
            if(tick!=lastBreakTick){lastBreakTick=tick;breaksThisTick=0;}
            peakBreaksPerTick=Math.max(peakBreaksPerTick,++breaksThisTick);
            maxRadius=Math.max(maxRadius,Math.sqrt(current.distanceSquared));maxDepth=Math.max(maxDepth,currentLayer+1);
            return true;
        }
        void finish(String reason){
            if(complete)return;
            complete=true;endedTick=owner.server.getTickCount();this.reason=reason;wave.clear();effects.clear();ACTIVE_OWNERS.complete(key.owner,this);
        }
        Snapshot snapshot(){return new Snapshot(plan,complete,visited,destroyed,protectedBlocks,skipped,energy,maxRadius,maxDepth,reason,
                (complete?endedTick:owner.server.getTickCount())-(long)startedTick,firstTickDestroyed,peakBreaksPerTick,peakSliceMillis);}
    }
}
