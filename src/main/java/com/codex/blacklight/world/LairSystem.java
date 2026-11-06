package com.codex.blacklight.world;

import com.codex.blacklight.entity.BlacklightHive;
import com.codex.blacklight.infection.OutbreakConfig;
import com.codex.blacklight.infection.OutbreakData;
import com.codex.blacklight.infection.WorldOutbreakSystem;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModBlocks;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;

/** Prototype 2 style Lairs. After the outbreak starts, each chunk seen for the first time
 * rolls once; the chance grows with outbreak days up to a configured maximum. A Lair's surface
 * sign is the source pustule: a burst flesh blister on a grey heap of infected ground, torn
 * open at the crown. The player jumps down a raw shaft into a randomly laid out, infested underground
 * military base (sized from the source interiors) where brood sacs swell and hatch. Overworld and city dimensions. */
public final class LairSystem {
    private LairSystem(){}
    /** Source interiors (structure_*_lair_interior_* tod.p3d) span about 130 x 120 m with platform
     * tiers at 43-49 m and a ceiling near 65 m. Here: 2/3 of that footprint, 4 tiers, ~20 high. */
    private static final int DROP=28,RX=46,RZ=40,CAP_QUEUE=4096,VERSION=5;
    private static final Map<ResourceKey<Level>,LinkedHashSet<Long>> SEEN=new HashMap<>();
    private record Pending(long chunk,long deadline){}
    private static final Map<ResourceKey<Level>,ArrayDeque<Pending>> PENDING=new HashMap<>();

    static boolean dimension(ServerLevel level){
        return level.dimension()==Level.OVERWORLD||com.codex.blacklight.city.CityTravel.isCity(level.dimension());
    }
    public static double chance(ServerLevel level){
        if(!WorldOutbreakSystem.active(level))return 0;
        var server=level.getServer();
        double days=OutbreakData.get(server).elapsed(server.overworld().getGameTime())/(double)Math.max(1,OutbreakConfig.DAY_TICKS.get());
        return Math.min(OutbreakConfig.LAIR_MAX_CHANCE.get(),OutbreakConfig.LAIR_CHANCE_PER_DAY.get()*(1+Math.max(0,days)));
    }
    @SubscribeEvent public static void load(ChunkEvent.Load event){
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getChunk() instanceof LevelChunk chunk)||!dimension(level))return;
        var set=SEEN.computeIfAbsent(level.dimension(),k->new LinkedHashSet<>());
        if(set.size()<CAP_QUEUE)set.add(chunk.getPos().toLong());
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof ServerLevel level)||!dimension(level))return;
        var set=SEEN.get(level.dimension());
        if(set!=null&&!set.isEmpty()){
            boolean active=WorldOutbreakSystem.active(level);double chance=active?chance(level):0;
            var it=set.iterator();
            for(int n=0;n<64&&it.hasNext();n++){
                long key=it.next();it.remove();
                if(!active)continue; // chunks seen before the outbreak roll on their next load
                var chunk=level.getChunkSource().getChunkNow(ChunkPos.getX(key),ChunkPos.getZ(key));
                if(chunk==null||chunk.getData(ModAttachments.LAIR_ROLLED))continue;
                chunk.setData(ModAttachments.LAIR_ROLLED,true);chunk.setUnsaved(true);
                // Never surprise a place players have lived in.
                if(level.dimension()==Level.OVERWORLD&&chunk.getInhabitedTime()>6000)continue;
                if(level.random.nextDouble()<chance)
                    PENDING.computeIfAbsent(level.dimension(),k->new ArrayDeque<>()).add(new Pending(key,level.getGameTime()+1200));
            }
        }
        for(var j:JOBS)if(j.dim==level.dimension()){flush(level,j,20_000_000L);if(!j.it.hasNext()&&j.hives.isEmpty())JOBS.remove(j);return;}
        if(level.getGameTime()%40==0)rebuildStale(level);
        var queue=PENDING.get(level.dimension());
        if(queue==null||queue.isEmpty()||level.getGameTime()%10!=0)return;
        var p=queue.poll();var cp=new ChunkPos(p.chunk);
        var result=tryPlace(level,cp.getMiddleBlockX(),cp.getMiddleBlockZ(),false);
        if(result==Result.WAIT&&level.getGameTime()<p.deadline)queue.add(p);
    }
    @SubscribeEvent public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent e){
        for(var j:JOBS){var l=e.getServer().getLevel(j.dim);if(l!=null)flush(l,j,Long.MAX_VALUE/4);}JOBS.clear();}
    public enum Result{PLACED,WAIT,REJECTED}

    /** Force=true is the operator command: skips the spacing rule only. */
    public static Result tryPlace(ServerLevel level,int x,int z,boolean force){
        var data=Data.get(level);
        int spacing=OutbreakConfig.LAIR_SPACING.get();
        if(!force)for(long l:data.lairs){var b=BlockPos.of(l);if((long)(b.getX()-x)*(b.getX()-x)+(long)(b.getZ()-z)*(b.getZ()-z)<(long)spacing*spacing)return Result.REJECTED;}
        if(WorldOutbreakSystem.sanctuary(level,new BlockPos(x,64,z)))return Result.REJECTED;
        int span=RX+8;
        for(int cx=(x-span)>>4;cx<=(x+span)>>4;cx++)for(int cz=(z-span)>>4;cz<=(z+span)>>4;cz++)
            if(level.getChunkSource().getChunkNow(cx,cz)==null)return Result.WAIT;
        var dirs=new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());Collections.shuffle(dirs,new Random(level.random.nextLong()));
        for(int ox=0;ox<=8;ox+=8)for(var d:dirs){
            int sx=x+ox,sz=z+ox;
            var plan=plan(level,sx,sz,d);
            if(plan==null)continue;
            build(level,plan);
            data.lairs.add(plan.entrance.asLong());data.setDirty();
            com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Blacklight lair placed at {} facing {} in {}",plan.entrance,d,level.dimension().location());
            return Result.PLACED;
        }
        return Result.REJECTED;
    }
    private record Plan(BlockPos entrance,Direction d,int surface,int floor,BlockPos centre){}
    private static BlockPos at(BlockPos s,Direction d,int along,int right,int y){
        var r=d.getClockWise();return new BlockPos(s.getX()+d.getStepX()*along+r.getStepX()*right,y,s.getZ()+d.getStepZ()*along+r.getStepZ()*right);
    }
    private static boolean clearAbove(BlockState s){return s.isAir()||s.canBeReplaced()&&s.getFluidState().isEmpty();}
    private static Plan plan(ServerLevel level,int x,int z,Direction d){
        int sy=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        var s=new BlockPos(x,sy,z);
        if(sy<level.getMinBuildHeight()+40||sy+10>=level.getMaxBuildHeight())return null;
        // Mound footprint: roughly level, solid ground, open sky over the blister and its crust.
        for(int a=-8;a<=8;a++)for(int r=-8;r<=8;r++){
            if(a*a+r*r>(MOUND+2.5)*(MOUND+2.5))continue;
            var c=at(s,d,a,r,0);int h=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,c.getX(),c.getZ());
            if(Math.abs(h-sy)>2)return null;
            var g=level.getBlockState(new BlockPos(c.getX(),h-1,c.getZ()));
            if(!g.getFluidState().isEmpty()||!g.isSolid())return null;
            for(int y=h;y<=sy+6;y++)if(!clearAbove(level.getBlockState(new BlockPos(c.getX(),y,c.getZ()))))return null;
        }
        int floor=sy-DROP;
        if(floor-4<=level.getMinBuildHeight()+2)return null;
        var centre=new BlockPos(x,floor,z);
        // Hall stays sealed below ground; no fluid, bedrock or block entities anywhere in the work volume.
        for(int dx=-RX-4;dx<=RX+4;dx++)for(int dz=-RZ-4;dz<=RZ+4;dz++){
            if(dx*dx/(double)((RX+4)*(RX+4))+dz*dz/(double)((RZ+4)*(RZ+4))>1)continue;
            int cx=x+dx,cz=z+dz;boolean shaft=dx*dx+dz*dz<=16;
            int top=shaft?sy-1:floor+24;
            if(!shaft&&level.getHeight(Heightmap.Types.OCEAN_FLOOR,cx,cz)<top+4)return null;
            for(int y=floor-4;y<=top;y++){
                var q=new BlockPos(cx,y,cz);var st=level.getBlockState(q);
                if(!st.getFluidState().isEmpty()||st.is(Blocks.BEDROCK)||level.getBlockEntity(q)!=null)return null;
            }
        }
        return new Plan(s,d,sy,floor,centre);
    }
    private static long hash(int x,int y,int z){long h=x*3129871L^z*116129781L^y*7919L;h=h*h*42317861L+h*11L;return h>>>16;}
    private static double noise(int x,int y,int z){return (hash(x,y,z)%1000)/1000D;}
    /** Builds are staged in memory, then placed over several ticks so a new Lair does not stall the server. */
    private static final class Job{final ResourceKey<Level> dim;final Iterator<Map.Entry<Long,BlockState>> it;final List<BlacklightHive> hives;int placed;
        Job(ResourceKey<Level> dim,LinkedHashMap<Long,BlockState> blocks,List<BlacklightHive> hives){this.dim=dim;this.it=blocks.entrySet().iterator();this.hives=hives;}}
    private static final ArrayDeque<Job> JOBS=new ArrayDeque<>();
    private static LinkedHashMap<Long,BlockState> stage;private static List<BlacklightHive> stagedHives;
    private static BlockState get(ServerLevel l,BlockPos p){var st=stage==null?null:stage.get(p.asLong());return st!=null?st:l.getBlockState(p);}
    private static void set(ServerLevel l,BlockPos p,BlockState s){
        if(stage!=null){if(!l.getBlockState(p).is(Blocks.BEDROCK)&&l.getBlockEntity(p)==null)stage.put(p.asLong(),s);return;}
        if(!l.getBlockState(p).is(Blocks.BEDROCK)&&l.getBlockEntity(p)==null)l.setBlock(p,s,2);}
    private static void flush(ServerLevel l,Job j,long budgetNanos){
        long end=System.nanoTime()+budgetNanos;var cursor=new BlockPos.MutableBlockPos();
        while(j.it.hasNext()){var e=j.it.next();cursor.set(e.getKey());
            if(l.getBlockState(cursor)!=e.getValue())l.setBlock(cursor,e.getValue(),2);
            if((++j.placed&255)==0&&System.nanoTime()>end)return;}
        for(var h:j.hives)l.addFreshEntity(h);j.hives.clear();
        com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Blacklight lair finished placing {} blocks",j.placed);
    }
    private static BlockState b(net.minecraft.world.level.block.Block block){return block.defaultBlockState();}

    private static final double MOUND=4.6;
    private static double shaftR(BlockPos s,int y){return 1.55+.55*noise(s.getX(),y>>1,s.getZ());}
    private static void build(ServerLevel l,Plan p){
        stage=new LinkedHashMap<>();stagedHives=new ArrayList<>();long t0=System.nanoTime();
        try{buildStaged(l,p);JOBS.add(new Job(l.dimension(),stage,stagedHives));
            com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Blacklight lair staged {} blocks in {} ms",stage.size(),(System.nanoTime()-t0)/1000000);}
        finally{stage=null;stagedHives=null;}
    }
    private static void buildStaged(ServerLevel l,Plan p){
        var s=p.entrance;var d=p.d;int sy=p.surface;
        int roof=cavern(l,p);
        // --- Drop shaft: the original lair is entered by jumping into the burst pustule; no stairs.
        for(int y=roof;y<sy;y++)for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){
            var pos=new BlockPos(s.getX()+dx,y,s.getZ()+dz);
            double ox=.6*(noise(s.getX(),y>>2,1)-.5),oz=.6*(noise(s.getZ(),y>>2,2)-.5);
            double dist=Math.sqrt((dx-ox)*(dx-ox)+(dz-oz)*(dz-oz)),R=shaftR(s,y);
            if(dist<=R){set(l,pos,b(Blocks.AIR));continue;}
            if(dist>R+1.4||get(l,pos).isAir())continue;
            double n=noise(pos.getX(),pos.getY(),pos.getZ()),tissue=.35+.5*(sy-y)/(double)(sy-roof);
            set(l,pos,n<tissue*.5?b(n<tissue*.12?ModBlocks.HIVE_SAC.get():n<tissue*.3?ModBlocks.HIVE_FLESH.get():ModBlocks.HIVE_VEIN.get())
                    :n>.8?b(ModBlocks.INFECTED_STONE.get()):n>.6?b(Blocks.TUFF):n>.35?b(Blocks.COBBLED_DEEPSLATE):b(Blocks.DEEPSLATE));
        }
        // --- Pustule mound (source: packages/missions/pustule): a burst flesh blister sitting on a
        // grey heap of infected ground, its crown torn open over the drop.
        for(int a=-10;a<=10;a++)for(int r=-10;r<=10;r++){
            var c0=at(s,d,a,r,0);int x=c0.getX(),z=c0.getZ();
            double dist=Math.sqrt(a*a+r*r),ang=Math.atan2(r,a);
            double R=MOUND+.9*(noise(x>>1,5,z>>1)-.5)+.6*Math.sin(ang*3+s.getX());
            if(dist>R+5)continue;
            int h=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            var ground=new BlockPos(x,h-1,z);double n=noise(x,h,z);
            double sx=x-s.getX(),sz=z-s.getZ(),sd=Math.sqrt(sx*sx+sz*sz);
            boolean crater=sd<=shaftR(s,sy)+.15;
            if(dist>R){
                // Grey crust thinning out, with veins radiating from the blister.
                double fall=(dist-R)/5;boolean vein=Math.abs(Math.sin(ang*5+s.getZ()))<.16;
                if(!get(l,ground).isSolid())continue;
                if(vein&&n>fall*.7)set(l,ground,b(ModBlocks.HIVE_VEIN.get()));
                else if(n>fall*1.1+.1)set(l,ground,b(n>.8?ModBlocks.INFECTED_STONE.get():n>.55?ModBlocks.INFECTED_SOIL.get():n>.35?Blocks.TUFF:Blocks.GRAVEL));
                if(dist<R+1.6&&n>.72&&get(l,ground.above()).isAir())set(l,ground.above(),b(n>.9?Blocks.COBBLED_DEEPSLATE:Blocks.TUFF));
                continue;
            }
            if(crater)continue; // the shaft already opened this column
            set(l,ground,b(ModBlocks.INFECTED_STONE.get()));
            double t=dist/R,grey=Math.max(0,(t-.5)/.5);
            int H=(int)Math.round(3.6*Math.pow(Math.max(0,1-t*t),.7)+1.1*(noise(x,9,z)-.4));
            boolean rim=sd<=shaftR(s,sy)+1.3;
            int top=h+H-1+(rim?1:0);
            for(int y=h;y<=top;y++){
                var pos=new BlockPos(x,y,z);double m=noise(x,y,z);
                BlockState st;
                if(y-h<grey*2.2)st=b(m>.6?Blocks.TUFF:m>.3?ModBlocks.INFECTED_STONE.get():Blocks.COBBLED_DEEPSLATE);
                else if(rim&&y>=top-1)st=b(m>.5?ModBlocks.HIVE_MEMBRANE.get():m>.2?ModBlocks.HIVE_SAC.get():ModBlocks.TISSUE_FIBER.get());
                else if(y==top||t>.7)st=b(m<.12?ModBlocks.HIVE_SAC.get():m<.45?ModBlocks.HIVE_FLESH.get():m<.7?ModBlocks.HIVE_CARAPACE.get():ModBlocks.HIVE_VEIN.get());
                else st=b(ModBlocks.HIVE_FLESH.get());
                set(l,pos,st);
            }
        }
    }
    private static double smooth(int x,int z,int cell,int seed){
        int gx=Math.floorDiv(x,cell),gz=Math.floorDiv(z,cell);
        double fx=(x-gx*cell)/(double)cell,fz=(z-gz*cell)/(double)cell;fx=fx*fx*(3-2*fx);fz=fz*fz*(3-2*fz);
        double a=noise(gx,seed,gz),b=noise(gx+1,seed,gz),c=noise(gx,seed,gz+1),d=noise(gx+1,seed,gz+1);
        return (a+(b-a)*fx)*(1-fz)+(c+(d-c)*fx)*fz;
    }
    // ---------------------------------------------------------------------------------------
    // Source lairs are infested military interiors (lairWall001 / militaryBaseWall001 bulkheads,
    // rocketLauncher001 and rifleSAW001 caches, F2 rubble falling from a ~22 m ceiling) inside a
    // ~130 x 120 m footprint. Here a random base: central hangar under the drop, a randomized
    // spanning tree of rooms and corridors on a 12-block grid, torn bulkheads, and tissue that
    // overgrows the concrete patchily, thickest in breaches.
    private static final int OX=RX+4,OZ=RZ+4,HY=20,CELL=12,GC=3;
    private static final class Base{
        final boolean[] open=new boolean[(OX*2+1)*(OZ*2+1)*HY];
        final byte[] zone=new byte[open.length];final float[] inf=new float[open.length];
        int i(int x,int y,int z){return ((x+OX)*(OZ*2+1)+(z+OZ))*HY+y;}
        boolean in(int x,int y,int z){return x>=-OX&&x<=OX&&z>=-OZ&&z<=OZ&&y>=0&&y<HY;}
        boolean open(int x,int y,int z){return in(x,y,z)&&open[i(x,y,z)];}
        void box(int x0,int x1,int y0,int y1,int z0,int z1,int zn,float f,boolean set){
            for(int x=Math.min(x0,x1);x<=Math.max(x0,x1);x++)for(int z=Math.min(z0,z1);z<=Math.max(z0,z1);z++){
                if(x*x/(double)(RX*RX)+z*z/(double)(RZ*RZ)>1)continue;
                for(int y=Math.max(1,y0);y<=Math.min(HY-2,y1);y++){int k=i(x,y,z);
                    if(set){if(!open[k]){open[k]=true;zone[k]=(byte)zn;}inf[k]=Math.max(inf[k],f);}else open[k]=false;}}
        }
    }
    private static final byte HANGAR=0,ROOM=1,CORRIDOR=2,BREACH=3,STORE=4,ARMORY=5,LAB=6;
    /** Builds the base and returns the hangar ceiling height (the drop shaft opens there). */
    private static int cavern(ServerLevel l,Plan p){
        var c=p.centre;int floor=p.floor;var rnd=new Random(hash(c.getX(),floor,c.getZ()));
        var B=new Base();
        // Hangar: 37 x 37, 15 high, with raised container/platform blocks along its edges.
        B.box(-18,18,1,15,-18,18,HANGAR,.55F,true);
        for(int k=0;k<6;k++){int side=rnd.nextInt(4),len=5+rnd.nextInt(8),dep=3+rnd.nextInt(3),h=2+rnd.nextInt(3)*2,off=rnd.nextInt(30)-15;
            int x0,x1,z0,z1;
            if(side<2){z0=side==0?-18:18-dep+1;z1=z0+dep-1;x0=off-len/2;x1=x0+len;}else{x0=side==2?-18:18-dep+1;x1=x0+dep-1;z0=off-len/2;z1=z0+len;}
            B.box(x0,x1,1,h,z0,z1,HANGAR,0,false);}
        // Keep the three corridor mouths on each hangar wall walkable through any platform.
        for(int lane=-CELL;lane<=CELL;lane+=CELL){B.box(-18,-12,1,4,lane-1,lane+1,HANGAR,.3F,true);B.box(12,18,1,4,lane-1,lane+1,HANGAR,.3F,true);
            B.box(lane-1,lane+1,1,4,-18,-12,HANGAR,.3F,true);B.box(lane-1,lane+1,1,4,12,18,HANGAR,.3F,true);}
        // Grid of rooms around the hangar, linked by a randomized spanning tree.
        int N=GC*2+1;boolean[][] ok=new boolean[N][N],vis=new boolean[N][N];
        for(int gx=-GC;gx<=GC;gx++)for(int gz=-GC;gz<=GC;gz++){int cx=gx*CELL,cz=gz*CELL;
            ok[gx+GC][gz+GC]=Math.abs(gx)<=1&&Math.abs(gz)<=1||(Math.pow(Math.abs(cx)+6,2)/(RX*RX)+Math.pow(Math.abs(cz)+6,2)/(RZ*RZ)<=1);}
        for(int gx=-1;gx<=1;gx++)for(int gz=-1;gz<=1;gz++)vis[gx+GC][gz+GC]=true;
        List<int[]> edges=new ArrayList<>();List<int[]> front=new ArrayList<>();
        int[][] dirs={{1,0},{-1,0},{0,1},{0,-1}};
        java.util.function.BiConsumer<Integer,Integer> addFront=(gx,gz)->{for(var d:dirs){int nx=gx+d[0],nz=gz+d[1];
            if(Math.abs(nx)<=GC&&Math.abs(nz)<=GC&&ok[nx+GC][nz+GC]&&!vis[nx+GC][nz+GC])front.add(new int[]{nx,nz,gx,gz});}};
        for(int[] e0:new int[][]{{2,0},{-2,0},{0,2},{0,-2}})if(ok[e0[0]+GC][e0[1]+GC])front.add(new int[]{e0[0],e0[1],e0[0]/2,e0[1]/2});
        for(int g=-1;g<=1;g+=2)for(int h=-1;h<=1;h++){if(rnd.nextFloat()<.35){front.add(new int[]{2*g,h,g,h});front.add(new int[]{h,2*g,h,g});}}
        int eligible=0;for(var r:ok)for(var v:r)if(v)eligible++;
        int target=(int)((eligible-9)*(.6+rnd.nextDouble()*.25)),count=0;
        while(!front.isEmpty()&&count<target){var f=front.remove(rnd.nextInt(front.size()));
            if(Math.abs(f[0])>GC||Math.abs(f[1])>GC||!ok[f[0]+GC][f[1]+GC]||vis[f[0]+GC][f[1]+GC])continue;
            vis[f[0]+GC][f[1]+GC]=true;count++;edges.add(f);addFront.accept(f[0],f[1]);}
        for(int gx=-GC;gx<GC;gx++)for(int gz=-GC;gz<=GC;gz++)if(vis[gx+GC][gz+GC]&&vis[gx+1+GC][gz+GC]&&!(Math.abs(gx)<=1&&Math.abs(gx+1)<=1&&Math.abs(gz)<=1)&&rnd.nextFloat()<.18)edges.add(new int[]{gx,gz,gx+1,gz});
        for(int gx=-GC;gx<=GC;gx++)for(int gz=-GC;gz<GC;gz++)if(vis[gx+GC][gz+GC]&&vis[gx+GC][gz+1+GC]&&!(Math.abs(gz)<=1&&Math.abs(gz+1)<=1&&Math.abs(gx)<=1)&&rnd.nextFloat()<.18)edges.add(new int[]{gx,gz,gx,gz+1});
        List<int[]> rooms=new ArrayList<>();
        for(int gx=-GC;gx<=GC;gx++)for(int gz=-GC;gz<=GC;gz++){
            if(!vis[gx+GC][gz+GC]||Math.abs(gx)<=1&&Math.abs(gz)<=1)continue;int cx=gx*CELL,cz=gz*CELL;
            if(rnd.nextFloat()<.75){int hw=3+rnd.nextInt(3),hd=3+rnd.nextInt(3),h=5+rnd.nextInt(3);
                double roll=rnd.nextDouble();byte type=roll<.3?STORE:roll<.5?ARMORY:roll<.65?LAB:ROOM;float f=rnd.nextFloat()<.3?.85F:.2F+rnd.nextFloat()*.4F;
                B.box(cx-hw,cx+hw,1,h,cz-hd,cz+hd,type,f,true);rooms.add(new int[]{cx,cz,hw,hd,type,h});}
            else B.box(cx-2,cx+2,1,4,cz-2,cz+2,CORRIDOR,.3F,true);
        }
        List<int[]> halls=new ArrayList<>();
        for(var e:edges){int ax=e[0]*CELL,az=e[1]*CELL,bx=e[2]*CELL,bz=e[3]*CELL;
            // Corridors into the hangar stop at its wall; the hangar interior stays as built.
            if(Math.abs(e[2])<=1&&Math.abs(e[3])<=1){bx=e[0]==e[2]?bx:Integer.signum(ax)*18;bz=e[1]==e[3]?bz:Integer.signum(az)*18;
                if(e[0]!=e[2])bz=e[3]*CELL;else bx=e[2]*CELL;}
            float f=.15F+rnd.nextFloat()*.35F;
            if(ax==bx)B.box(ax-1,ax+1,1,4,az,bz,CORRIDOR,f,true);else B.box(ax,bx,1,4,az-1,az+1,CORRIDOR,f,true);
            halls.add(new int[]{ax,az,bx,bz});}
        // Breaches: tissue has eaten organic cavities through walls between areas.
        List<int[]> breaches=new ArrayList<>();
        for(int k=0;k<5+rnd.nextInt(4);k++){int bx=(int)((rnd.nextDouble()*2-1)*RX*.75),bz=(int)((rnd.nextDouble()*2-1)*RZ*.75),by=3+rnd.nextInt(3);
            double rx=3+rnd.nextDouble()*4,ry=2.5+rnd.nextDouble()*2,rz=3+rnd.nextDouble()*4;breaches.add(new int[]{bx,by,bz});
            for(int x=(int)(bx-rx-1);x<=bx+rx+1;x++)for(int z=(int)(bz-rz-1);z<=bz+rz+1;z++)for(int y=Math.max(1,(int)(by-ry));y<=Math.min(HY-2,by+ry);y++){
                if(!B.in(x,y,z)||x*x/(double)(RX*RX)+z*z/(double)(RZ*RZ)>1)continue;
                double e=Math.pow((x-bx)/rx,2)+Math.pow((y-by)/ry,2)+Math.pow((z-bz)/rz,2)+.35*(noise(c.getX()+x,y,c.getZ()+z)-.5);
                if(e<=1){int k2=B.i(x,y,z);if(!B.open[k2]){B.open[k2]=true;B.zone[k2]=BREACH;}B.inf[k2]=1;}}}
        // --- Blocks.
        for(int x=-OX;x<=OX;x++)for(int z=-OZ;z<=OZ;z++){
            if(x*x/(double)(OX*OX)+z*z/(double)(OZ*OZ)>1)continue;
            for(int y=0;y<HY;y++){var pos=new BlockPos(c.getX()+x,floor+y-1,c.getZ()+z);
                if(B.open(x,y,z)){if(!get(l,pos).isAir())set(l,pos,b(Blocks.AIR));continue;}
                int kind=-1;int nx=x,ny=y,nz=z; // nearest open neighbour: 0 floor,1 ceiling,2 wall
                if(B.open(x,y+1,z)){kind=0;ny=y+1;}else if(B.open(x,y-1,z)){kind=1;ny=y-1;}
                else for(var d:dirs)if(B.open(x+d[0],y,z+d[1])){kind=2;nx=x+d[0];nz=z+d[1];break;}
                if(kind<0){if(get(l,pos).isAir()||y==0||y==HY-1)set(l,pos,b(Blocks.DEEPSLATE));continue;}
                int k=B.i(nx,ny,nz);set(l,pos,shell(pos,kind,B.zone[k],B.inf[k],y));
            }
        }
        // --- Set dressing.
        for(var r:rooms){int cx=r[0],cz=r[1],hw=r[2],hd=r[3],type=r[4],h=r[5];
            for(int x=cx-hw;x<=cx+hw;x++)for(int z=cz-hd;z<=cz+hd;z++){
                boolean wall=Math.abs(x-cx)==hw||Math.abs(z-cz)==hd;if(!wall||!B.open(x,1,z))continue;
                boolean door=false;for(var d:dirs)if(!B.in(x+d[0],1,z+d[1])||B.open(x+d[0],1,z+d[1])&&B.zone[B.i(x+d[0],1,z+d[1])]==CORRIDOR)door=true;
                if(door||Math.abs(x-cx)<=1||Math.abs(z-cz)<=1)continue;double n=noise(c.getX()+x,3,c.getZ()+z);
                var at=new BlockPos(c.getX()+x,floor,c.getZ()+z);
                if(type==STORE&&n<.6){int st=1+(int)(n*4);for(int y=0;y<st&&y<h-1;y++)set(l,at.above(y),b(noise(x,y,z)<.5?Blocks.GREEN_TERRACOTTA:Blocks.BROWN_TERRACOTTA));}
                else if(type==ARMORY&&n<.7){set(l,at,b(Blocks.IRON_BLOCK));set(l,at.above(),b(Blocks.IRON_BARS));if(n<.3)set(l,at.above(2),b(Blocks.IRON_BARS));}
                else if(type==LAB&&n<.6){set(l,at,b(Blocks.POLISHED_ANDESITE));set(l,at.above(),b(n<.25?Blocks.LIGHT_GRAY_STAINED_GLASS:Blocks.GLASS));}
            }}
        for(int k=0;k<14;k++){int x=rnd.nextInt(33)-16,z=rnd.nextInt(33)-16;if(Math.hypot(x,z)<5||!B.open(x,1,z)||!B.open(x,2,z))continue;
            var at=new BlockPos(c.getX()+x,floor,c.getZ()+z);int st=1+rnd.nextInt(3);
            for(int y=0;y<st;y++)for(int w=0;w<2;w++)set(l,at.offset(w,y,0),b(rnd.nextFloat()<.5?Blocks.GREEN_TERRACOTTA:Blocks.BROWN_TERRACOTTA));}
        for(int k=0;k<10;k++){int x=rnd.nextInt(31)-15,z=rnd.nextInt(31)-15;if(!B.open(x,15,z))continue;int len=1+rnd.nextInt(4);
            for(int y=15;y>15-len;y--)set(l,new BlockPos(c.getX()+x,floor+y-1,c.getZ()+z),b(Blocks.CHAIN));
            set(l,new BlockPos(c.getX()+x,floor+15-len-1,c.getZ()+z),b(rnd.nextFloat()<.6?Blocks.LANTERN:Blocks.SOUL_LANTERN));}
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){double n=noise(c.getX()+x,7,c.getZ()+z);if(Math.hypot(x,z)>4.5||n<.45||!B.open(x,1,z))continue;
            var at=new BlockPos(c.getX()+x,floor,c.getZ()+z);set(l,at,b(n>.85?Blocks.CRACKED_STONE_BRICKS:n>.65?Blocks.GRAY_CONCRETE_POWDER:Blocks.COBBLED_DEEPSLATE));
            if(n>.9&&B.open(x,2,z))set(l,at.above(),b(Blocks.COBBLED_DEEPSLATE));}
        // Torn bulkheads (lairWall001): bar grate across some corridors, lower panels ripped out.
        for(var h:halls){if(rnd.nextFloat()>.45)continue;int mx=(h[0]+h[2])/2,mz=(h[1]+h[3])/2;boolean alongX=h[1]==h[3];
            for(int w=-2;w<=2;w++)for(int y=1;y<=5;y++){int x=alongX?mx:mx+w,z=alongX?mz+w:mz;var pos=new BlockPos(c.getX()+x,floor+y-1,c.getZ()+z);
                if(!B.in(x,y,z))continue;boolean o=B.open(x,y,z);
                if(!o&&(Math.abs(w)==2||y==5)){set(l,pos,b(Blocks.POLISHED_DEEPSLATE));continue;}
                if(o&&(y==4||Math.abs(w)==1&&y>=3&&rnd.nextFloat()<.6))set(l,pos,b(Blocks.IRON_BARS));}
            for(int w=-1;w<=1;w++){int x=alongX?mx:mx+w,z=alongX?mz+w:mz;if(B.open(x,1,z))set(l,new BlockPos(c.getX()+x,floor-1,c.getZ()+z),b((x+z&1)==0?Blocks.YELLOW_CONCRETE:Blocks.BLACK_CONCRETE));}}
        // Tendrils from infested ceilings.
        for(int x=-OX;x<=OX;x++)for(int z=-OZ;z<=OZ;z++)for(int y=HY-2;y>=3;y--){
            if(!B.open(x,y,z)||B.open(x,y+1,z))continue;int k=B.i(x,y,z);
            if(noise(c.getX()+x,y*7,c.getZ()+z)<B.inf[k]*.09){int len=1+(int)(noise(x,y,z)*4);
                for(int t=0;t<len&&B.open(x,y-t,z)&&B.open(x,y-t-2,z);t++)set(l,new BlockPos(c.getX()+x,floor+y-t-1,c.getZ()+z),b(t==len-1?ModBlocks.HIVE_SAC.get():ModBlocks.HIVE_VEIN.get()));}
            break;}
        // Brood sacs: a few in the hangar, then infested rooms and breaches.
        List<int[]> spots=new ArrayList<>();
        for(int k=0;k<40&&spots.size()<3;k++){int x=rnd.nextInt(29)-14,z=rnd.nextInt(29)-14;if(Math.hypot(x,z)>7&&B.open(x,1,z)&&B.open(x,4,z))spots.add(new int[]{x,z});}
        for(var r:rooms)if(spots.size()<10&&rnd.nextFloat()<.55&&B.open(r[0],1,r[1]))spots.add(new int[]{r[0],r[1]});
        for(var br:breaches)if(spots.size()<10&&rnd.nextFloat()<.5){for(int y=1;y<6;y++)if(B.open(br[0],y,br[2])&&!B.open(br[0],y-1,br[2])){spots.add(new int[]{br[0],br[2],y});break;}}
        for(var sp:spots){var hive=ModEntities.BLACKLIGHT_HIVE.get().create(l);if(hive==null)break;int y=sp.length>2?sp[2]:1;
            hive.moveTo(c.getX()+sp[0]+.5,floor+y-1,c.getZ()+sp[1]+.5,rnd.nextFloat()*360,0);
            hive.setGrowth(.3F+rnd.nextFloat()*.3F);hive.getPersistentData().putBoolean("BlacklightLair",true);if(stagedHives!=null)stagedHives.add(hive);else l.addFreshEntity(hive);}
        return floor+15;
    }
    private static BlockState shell(BlockPos pos,int kind,byte zone,float inf,int y){
        int x=pos.getX(),z=pos.getZ();double n=noise(x,pos.getY(),z);
        double blob=smooth(x+pos.getY()*3,z-pos.getY()*5,5,71)*.7+n*.3;
        if(zone==BREACH||blob<inf*.85){
            if(kind==0)return b(n<.08?ModBlocks.HIVE_SAC.get():n<.55?ModBlocks.HIVE_FLESH.get():n<.75?ModBlocks.INFECTED_STONE.get():ModBlocks.HIVE_CARAPACE.get());
            if(kind==1)return b(n<.015?Blocks.SHROOMLIGHT:n<.4?ModBlocks.HIVE_MEMBRANE.get():n<.75?ModBlocks.HIVE_FLESH.get():ModBlocks.HIVE_VEIN.get());
            return b(n<.35?ModBlocks.HIVE_VEIN.get():n<.6?ModBlocks.HIVE_FLESH.get():n<.8?ModBlocks.TISSUE_FIBER.get():ModBlocks.HIVE_CARAPACE.get());
        }
        if(kind==0){
            if(zone==HANGAR)return b(n<.1?Blocks.CRACKED_STONE_BRICKS:(Math.floorMod(x,6)==0||Math.floorMod(z,6)==0)?Blocks.GRAY_CONCRETE:Blocks.LIGHT_GRAY_CONCRETE);
            if(zone==CORRIDOR)return b(n<.15?Blocks.CRACKED_STONE_BRICKS:Blocks.POLISHED_ANDESITE);
            return b(((x+z)&1)==0?Blocks.GRAY_CONCRETE:Blocks.POLISHED_ANDESITE);
        }
        if(kind==1){
            boolean light=(zone==CORRIDOR?Math.floorMod(x+z,6)==0:Math.floorMod(x,5)==0&&Math.floorMod(z,5)==0);
            if(light)return b(n<.55?Blocks.SEA_LANTERN:Blocks.REDSTONE_LAMP); // some lights are dead
            return b(zone==HANGAR&&(Math.floorMod(x,8)==0||Math.floorMod(z,8)==0)?Blocks.IRON_BLOCK:n<.2?Blocks.CRACKED_STONE_BRICKS:Blocks.SMOOTH_STONE);
        }
        if(y==1)return b(Blocks.POLISHED_DEEPSLATE); // skirting
        if(zone==HANGAR&&(Math.floorMod(x+z,9)==0))return b(Blocks.POLISHED_DEEPSLATE);
        if(zone==HANGAR&&y>9)return b(n<.5?Blocks.STONE_BRICKS:Blocks.CRACKED_STONE_BRICKS);
        if(y==3&&zone!=CORRIDOR)return b(Blocks.YELLOW_TERRACOTTA); // painted stripe
        return b(n<.12?Blocks.CRACKED_STONE_BRICKS:n<.55?Blocks.LIGHT_GRAY_CONCRETE:Blocks.GRAY_CONCRETE);
    }
    private static final Set<net.minecraft.world.level.block.Block> OLD=new HashSet<>();
    /** Removes the surface part of an older Lair (stone headhouse/grate or first pustule pass). */
    private static void clearOld(ServerLevel l,BlockPos e){
        if(OLD.isEmpty()){OLD.addAll(List.of(Blocks.STONE_BRICKS,Blocks.CRACKED_STONE_BRICKS,Blocks.POLISHED_ANDESITE,Blocks.POLISHED_DEEPSLATE,Blocks.POLISHED_DEEPSLATE_SLAB,
            Blocks.POLISHED_DEEPSLATE_STAIRS,Blocks.POLISHED_DEEPSLATE_WALL,Blocks.DEEPSLATE_TILES,Blocks.IRON_BARS,Blocks.TUFF,Blocks.GRAVEL,Blocks.COBBLED_DEEPSLATE,Blocks.DEEPSLATE,
            Blocks.BLACKSTONE,Blocks.DEEPSLATE_BRICKS,Blocks.CRACKED_DEEPSLATE_BRICKS,Blocks.DEEPSLATE_BRICK_STAIRS,Blocks.TUFF_STAIRS,Blocks.COBBLED_DEEPSLATE_STAIRS,Blocks.BLACKSTONE_STAIRS,
            ModBlocks.HIVE_FLESH.get(),ModBlocks.HIVE_CARAPACE.get(),ModBlocks.HIVE_SAC.get(),ModBlocks.HIVE_VEIN.get(),ModBlocks.HIVE_MEMBRANE.get(),ModBlocks.HIVE_BONE.get(),
            ModBlocks.INFECTED_STONE.get(),ModBlocks.INFECTED_SOIL.get(),ModBlocks.TISSUE_FIBER.get(),Blocks.SHROOMLIGHT));}
        int sy=e.getY();var groundSample=l.getBlockState(new BlockPos(e.getX()+16,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,e.getX()+16,e.getZ())-1,e.getZ()));
        if(!groundSample.isSolid()||OLD.contains(groundSample.getBlock()))groundSample=b(Blocks.STONE);
        for(int dx=-12;dx<=12;dx++)for(int dz=-12;dz<=12;dz++){
            for(int y=sy+10;y>=sy;y--){var q=new BlockPos(e.getX()+dx,y,e.getZ()+dz);if(OLD.contains(l.getBlockState(q).getBlock()))set(l,q,b(Blocks.AIR));}
            for(int y=sy-1;y>=sy-4;y--){var q=new BlockPos(e.getX()+dx,y,e.getZ()+dz);var st=l.getBlockState(q);
                if(st.isAir()||OLD.contains(st.getBlock()))set(l,q,groundSample);else break;}
        }
    }
    /** Lairs saved by an older layout are rebuilt in place once the area is loaded. */
    private static void rebuildStale(ServerLevel l){
        var data=Data.get(l);if(data.stale.isEmpty())return;
        for(var it=data.stale.iterator();it.hasNext();){
            var e=BlockPos.of(it.next());boolean loaded=true;
            for(int cx=(e.getX()-RX-8)>>4;cx<=(e.getX()+RX+8)>>4&&loaded;cx++)for(int cz=(e.getZ()-RZ-8)>>4;cz<=(e.getZ()+RZ+8)>>4;cz++)
                if(l.getChunkSource().getChunkNow(cx,cz)==null){loaded=false;break;}
            if(!loaded)continue;
            it.remove();data.setDirty();clearOld(l,e);
            int spacing=OutbreakConfig.LAIR_SPACING.get();boolean crowded=false;
            for(long o:data.lairs){var b=BlockPos.of(o);if(b.distSqr(new BlockPos(e.getX(),b.getY(),e.getZ()))<(double)spacing*spacing)crowded=true;}
            if(crowded){com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Old lair at {} removed (too close to another)",e);return;}
            var plan=plan(l,e.getX(),e.getZ(),Direction.NORTH);
            if(plan==null){com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Old lair at {} removed (no room for the new hall)",e);return;}
            build(l,plan);data.lairs.add(plan.entrance.asLong());
            com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Old lair at {} rebuilt",e);
            return; // one per pass
        }
    }
    public static List<BlockPos> lairs(ServerLevel level){return Data.get(level).lairs.stream().map(BlockPos::of).toList();}

    public static final class Data extends SavedData {
        final List<Long> lairs=new ArrayList<>(),stale=new ArrayList<>();
        static Data get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new Factory<>(Data::new,Data::load),"blacklight_lairs");}
        static Data load(CompoundTag tag,HolderLookup.Provider p){var d=new Data();
            for(long l:tag.getLongArray("Stale"))d.stale.add(l);
            if(tag.getInt("Version")<VERSION){for(long l:tag.getLongArray("Lairs"))d.stale.add(l);d.setDirty();}
            else for(long l:tag.getLongArray("Lairs"))d.lairs.add(l);
            return d;}
        @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider p){tag.putInt("Version",VERSION);tag.put("Lairs",new LongArrayTag(lairs));tag.put("Stale",new LongArrayTag(stale));return tag;}
    }
}
