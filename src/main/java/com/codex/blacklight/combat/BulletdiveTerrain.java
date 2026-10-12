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
 * A bounded bowl consumes energy by hardness, with ordinary per-block protection events.
 */
@EventBusSubscriber(modid="blacklight")
public final class BulletdiveTerrain {
    public static final int MAX_BUDGET=4096, MAX_WORK_PER_TICK=192, MAX_FEEDBACK_PER_TICK=8;
    private static final int MAX_ACTIVE=16, MAX_HISTORY=128;
    private static final long TICK_NANOS=3_000_000;
    public record Plan(double height,int radius,int depth,int budget,double energy) { }
    public record Snapshot(Plan plan,boolean complete,int visited,int destroyed,int protectedBlocks,
                           int skipped,double energyRemaining,double maximumRadius,int maximumDepth,String reason) { }
    private record Key(UUID owner,long session) { }
    private record Candidate(int x,int z,int layer,int distanceSquared) { }
    private static final Map<Key,Job> JOBS=new LinkedHashMap<>();
    private static final ThreadLocal<BlockEvent.BreakEvent> OWN_BREAK=new ThreadLocal<>();
    private BulletdiveTerrain() { }

    public static Plan plan(double actualHeight) {
        double height=Double.isFinite(actualHeight)?Math.clamp(actualHeight,0,512):0;
        if(height<=6)return new Plan(height,0,0,0,0);
        double excess=height-6;
        int radius=Math.min(12,2+(int)Math.floor(Math.sqrt(excess*.5)));
        int depth=Math.min(10,1+(int)Math.floor(excess/16));
        int budget=Math.min(MAX_BUDGET,48+(int)Math.floor(excess*12));
        return new Plan(height,radius,depth,budget,excess*8);
    }
    public static int columnDepth(Plan plan,int distanceSquared) {
        if(plan.radius<=0||distanceSquared>=plan.radius*plan.radius)return 0;
        return (int)Math.ceil(plan.depth*(1-distanceSquared/(double)(plan.radius*plan.radius)));
    }
    public static double localStrength(Plan plan,int distanceSquared,int layer) {
        return Math.max(0,(plan.height-6)*.12-Math.sqrt(Math.max(0,distanceSquared))*.15-Math.max(0,layer)*.5);
    }
    public static double hardnessCost(float hardness) { return Float.isFinite(hardness)&&hardness>=0?1+hardness*3:Double.POSITIVE_INFINITY; }
    public static boolean canBreak(Plan plan,int distanceSquared,int layer,float hardness,float resistance,boolean blockEntity) {
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
        if(!eligible(p)||!Double.isFinite(impact.x)||!Double.isFinite(impact.y)||!Double.isFinite(impact.z))job.finish("ineligible");
        else if(plan.budget==0)job.finish("below_height_threshold");
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
        for(var job:new ArrayList<>(JOBS.values())) {
            if(job.complete)continue;
            if(!eligible(job.owner)||job.owner.isRemoved()||job.owner.level()!=job.level){job.finish("owner_unavailable");continue;}
            while(!job.complete&&work<MAX_WORK_PER_TICK&&System.nanoTime()<until) {
                boolean sound=feedback<MAX_FEEDBACK_PER_TICK;
                if(job.next(sound))feedback++;
                work++;
            }
            if(work>=MAX_WORK_PER_TICK||System.nanoTime()>=until)break;
        }
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
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){JOBS.clear();OWN_BREAK.remove();}

    private static final class Job {
        final ServerPlayer owner;final Key key;final ServerLevel level;final Plan plan;final BlockPos center;
        final List<Candidate> candidates=new ArrayList<>();
        final Set<Integer> stoppedColumns=new HashSet<>();
        boolean complete;String reason="running";int visited,destroyed,protectedBlocks,skipped,maxDepth;double energy,maxRadius;
        Job(ServerPlayer p,Key key,Plan plan,Vec3 impact) {
            owner=p;this.key=key;level=p.serverLevel();this.plan=plan;energy=plan.energy;
            center=BlockPos.containing(impact.x,impact.y-.075,impact.z);
            for(int x=-plan.radius;x<=plan.radius;x++)for(int z=-plan.radius;z<=plan.radius;z++) {
                int distance=x*x+z*z;int depth=columnDepth(plan,distance);
                for(int layer=0;layer<depth;layer++)candidates.add(new Candidate(x,z,layer,distance));
            }
            candidates.sort(Comparator.comparingInt(Candidate::distanceSquared).thenComparingInt(Candidate::layer)
                    .thenComparingInt(Candidate::x).thenComparingInt(Candidate::z));
            if(candidates.size()>plan.budget)candidates.subList(plan.budget,candidates.size()).clear();
        }
        boolean loaded(BlockPos pos){return level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;}
        boolean next(boolean feedback) {
            if(visited>=candidates.size()||energy<1){finish(energy<1?"energy_exhausted":"complete");return false;}
            var candidate=candidates.get(visited++);var pos=center.offset(candidate.x,-candidate.layer,candidate.z);
            int column=(candidate.x+12)*25+candidate.z+12;
            if(stoppedColumns.contains(column)){skipped++;return false;}
            if(!level.isInWorldBounds(pos)||!loaded(pos)){skipped++;return false;}
            if(!level.mayInteract(owner,pos)||!owner.mayBuild()){protectedBlocks++;stoppedColumns.add(column);return false;}
            var state=level.getBlockState(pos);
            if(state.isAir()||!state.getFluidState().isEmpty()){skipped++;return false;}
            float hardness=state.getDestroySpeed(level,pos),resistance=state.getBlock().getExplosionResistance();
            if(state.hasBlockEntity()||level.getBlockEntity(pos)!=null||hardness<0||resistance>=1200){protectedBlocks++;stoppedColumns.add(column);return false;}
            double cost=hardnessCost(hardness);
            if(!canBreak(plan,candidate.distanceSquared,candidate.layer,hardness,resistance,false)||cost>energy){skipped++;stoppedColumns.add(column);return false;}
            var event=new BlockEvent.BreakEvent(level,pos,state,owner);var previous=OWN_BREAK.get();OWN_BREAK.set(event);
            try { NeoForge.EVENT_BUS.post(event); } finally { if(previous==null)OWN_BREAK.remove();else OWN_BREAK.set(previous); }
            if(event.isCanceled()||!loaded(pos)||!level.mayInteract(owner,pos)){protectedBlocks++;stoppedColumns.add(column);return false;}
            if(!level.getBlockState(pos).equals(state)){skipped++;return false;}
            boolean broken;
            if(feedback)broken=level.destroyBlock(pos,false,owner);
            else {
                broken=level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
                if(broken)level.gameEvent(GameEvent.BLOCK_DESTROY,pos,GameEvent.Context.of(owner,state));
            }
            if(!broken){skipped++;return false;}
            energy=Math.max(0,energy-cost);destroyed++;
            maxRadius=Math.max(maxRadius,Math.sqrt(candidate.distanceSquared));maxDepth=Math.max(maxDepth,candidate.layer+1);
            return feedback;
        }
        void finish(String reason){complete=true;this.reason=reason;}
        Snapshot snapshot(){return new Snapshot(plan,complete,visited,destroyed,protectedBlocks,skipped,energy,maxRadius,maxDepth,reason);}
    }
}
