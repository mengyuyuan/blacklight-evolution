package com.codex.blacklight.infection;

import static com.codex.blacklight.infection.BuildingBiomassField.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;

/** Only paths and real support coordinates are saved. Cell and mesh caches have fixed budgets. */
public final class BuildingBiomassPlan {
    public static final int CELL_LIMIT=16384;
    public final List<Stem> stems;
    public final List<BlockPos> supports;
    private List<Cell> cells;
    public BuildingBiomassPlan(List<Stem> stems,List<BlockPos> supports) {
        if(stems.isEmpty()||stems.size()>8||stems.size()!=supports.size())throw new IllegalArgumentException("Invalid biomass path count");
        this.stems=List.copyOf(stems);this.supports=List.copyOf(supports);
    }
    public List<Cell> cells() { if(cells==null)cells=BuildingBiomassField.cells(stems,CELL_LIMIT);return cells; }
    private static boolean support(ServerLevel l,BlockPos p,Direction face) {
        if(!l.hasChunkAt(p)||l.isOutsideBuildHeight(p))return false;
        var state=l.getBlockState(p);
        return !state.hasBlockEntity()&&state.getFluidState().isEmpty()&&state.isFaceSturdy(l,p,face);
    }
    private static BlockPos anchor(ServerLevel l,BlockPos origin,Direction normal,Direction side,int width,int height) {
        for(int down=0;down<7;down++)for(int dx:new int[]{0,-1,1,-2,2}) {
            var p=origin.relative(side,width+dx).above(Math.max(2,height-down)).relative(normal.getOpposite());
            if(support(l,p,normal))return p;
        }
        return null;
    }
    private static BlockPos neighbourAnchor(ServerLevel l,BlockPos origin,Direction normal,Direction side,int top,long seed) {
        // Search already-loaded facades across a street, including diagonal lots.
        // Bounded lookup, not a flood fill and never a chunk ticket.
        int height=com.codex.blacklight.forge.WingMath.clamp((int)(top*(.42+.20*TissueGrowth.sample(seed,140))),6,36);
        int hand=TissueGrowth.sample(seed,141)<.5?-1:1;
        for(int distance=8;distance<=Math.min(30,BuildingInfectionConfig.RADIUS.get()-3);distance++)
            for(int lateral:new int[]{0,hand*4,-hand*4,hand*8,-hand*8,hand*12,-hand*12})
                for(int dy:new int[]{0,-2,2,-4,4,-6,6}) {
                    var p=origin.relative(normal,distance).relative(side,lateral).above(Math.max(4,height+dy));
                    if(support(l,p,normal.getOpposite())&&support(l,p.above(),normal.getOpposite())
                            &&support(l,p.below(),normal.getOpposite()))return p;
                }
        return null;
    }
    public static BuildingBiomassPlan find(ServerLevel l,BlockPos origin,Direction normal) {
        if(!support(l,origin.relative(normal.getOpposite()),normal))return null;
        Direction side=normal.getClockWise();int top=0;
        for(int y=3;y<=Math.min(72,BuildingInfectionConfig.HEIGHT.get()-3);y++)
            if(support(l,origin.relative(normal.getOpposite()).above(y),normal))top=y;
        if(top<8)return null;
        long seed=origin.asLong()^l.getSeed();
        var n=new Point(normal.getStepX(),0,normal.getStepZ());var s=new Point(side.getStepX(),0,side.getStepZ());
        var root=new Point(.5,-.35,.5).add(n.mul(.75));
        var stems=new ArrayList<Stem>();var supports=new ArrayList<BlockPos>();
        // Stable per-site anatomy: a dominant leaning trunk, an asymmetric fork,
        // a wide buttress pair, or a three-way crown. These are coherent branches,
        // not independently randomized blocks. Saved paths survive future updates.
        int anatomy=(int)(TissueGrowth.sample(seed,21)*4);
        int count=top<16?1:anatomy==0?1:anatomy==3?3:2;
        int handed=TissueGrowth.sample(seed,22)<.5?-1:1;
        int primary=-1;
        for(int index=0;index<count;index++) {
            int key=40+index*13;
            int sign=count==1?handed:index==0?-1:index==1?1:handed;
            int width=count==1?handed*(2+(int)(TissueGrowth.sample(seed,key)*4))
                    :index==2?0:sign*(4+(int)(TissueGrowth.sample(seed,key)*4));
            int reach=Math.max(8,(int)(top*(.76+.22*TissueGrowth.sample(seed,key+1))));
            var end=anchor(l,origin,normal,side,width,reach);if(end==null)continue;
            var tip=new Point(end.getX()-origin.getX()+.5,end.getY()-origin.getY()+.5,end.getZ()-origin.getZ()+.5).add(n.mul(.8));
            double bow=2.6+top*.06+TissueGrowth.sample(seed,key+2)*2.5;
            double waist=(anatomy==2?.68:.43)+TissueGrowth.sample(seed,key+3)*.17;
            var p1=root.add(s.mul(sign*(1.3+TissueGrowth.sample(seed,key+4)*1.7))).add(n.mul(bow*.48)).add(new Point(0,reach*.17,0));
            var p2=root.add(s.mul(width*waist+handed*TissueGrowth.sample(seed,key+5))).add(n.mul(bow)).add(new Point(0,reach*(.40+.14*TissueGrowth.sample(seed,key+6)),0));
            var p3=tip.add(n.mul(bow*.48)).add(s.mul(-sign*(.6+TissueGrowth.sample(seed,key+7)*1.6))).add(new Point(0,-reach*.19,0));
            double radius=Math.min(3.8,com.codex.blacklight.forge.WingMath.clamp(1.5+top*.032,1.6,3.5)*(.85+.35*TissueGrowth.sample(seed,key+8))*(count==1?1.2:1));
            int parent=stems.size();
            if(primary<0)primary=parent;
            stems.add(new Stem(List.of(root,p1,p2,p3,tip),radius,radius*(.48+.18*TissueGrowth.sample(seed,key+9)),700+index*330,8200+(int)(TissueGrowth.sample(seed,key+10)*1600),-1));supports.add(end.immutable());
            var branchEnd=anchor(l,origin,normal,side,width+sign*(3+(int)(TissueGrowth.sample(seed,key+11)*4)),Math.max(5,(int)(top*(.36+.25*TissueGrowth.sample(seed,key+12)))));
            if(branchEnd!=null&&stems.size()<6) {
                var btip=new Point(branchEnd.getX()-origin.getX()+.5,branchEnd.getY()-origin.getY()+.5,branchEnd.getZ()-origin.getZ()+.5).add(n.mul(.6));
                stems.add(new Stem(List.of(p2,p2.lerp(btip,.5).add(n.mul(1.3)),btip),radius*.45,.55,5400+index*450,4800,parent));supports.add(branchEnd.immutable());
            }
        }
        if(stems.isEmpty())return null;
        var neighbour=neighbourAnchor(l,origin,normal,side,top,seed);
        if(neighbour!=null&&stems.size()<8) {
            var from=stems.get(primary).points().get(2);
            var to=new Point(neighbour.getX()-origin.getX()+.5,neighbour.getY()-origin.getY()+.5,neighbour.getZ()-origin.getZ()+.5).add(n.mul(-.6));
            double span=to.sub(from).length(),sag=Math.min(5,span*.16);
            // A heavy flesh cable droops between its two actual anchors, then
            // thickens with the colony. Its parent is the supported main trunk.
            var p1=from.lerp(to,.33).add(new Point(0,-sag,0));
            var p2=from.lerp(to,.69).add(new Point(0,-sag*.85,0));
            // Street supports must read as a heavy continuous muscle bundle,
            // not a sub-block wire that aliases into a string of small beads.
            double radius=1.65+TissueGrowth.sample(seed,142)*.65;
            stems.add(new Stem(List.of(from,p1,p2,to),radius,radius*.80,5600,5600,primary));supports.add(neighbour.immutable());
        }
        // Spread into real street anchors as well as the facade. Do not invent
        // unsupported ends in the air, or hide the whole building under a carpet.
        for(int index=0;index<2&&stems.size()<8;index++) {
            int sign=index==0?-1:1;
            var foot=origin.relative(side,sign*(4+(int)(TissueGrowth.sample(seed,100+index)*4)))
                    .relative(normal,4+(int)(TissueGrowth.sample(seed,110+index)*4)).below();
            if(!support(l,foot,Direction.UP))continue;
            var tip=new Point(foot.getX()-origin.getX()+.5,foot.getY()-origin.getY()+1.1,foot.getZ()-origin.getZ()+.5);
            stems.add(new Stem(List.of(root,root.lerp(tip,.5).add(new Point(0,.7,0)),tip),1.45,.65,200,5400,primary));supports.add(foot.immutable());
        }
        var plan=new BuildingBiomassPlan(stems,supports);
        try{plan.cells();return plan;}catch(IllegalArgumentException tooLarge){return null;}
    }
    public int disabled(ServerLevel l,int previous) {
        int disabled=previous;
        for(int i=0;i<stems.size();i++) {
            var p=supports.get(i);
            if(l.hasChunkAt(p)) {
                var s=l.getBlockState(p);
                if(s.isAir()||!s.getFluidState().isEmpty()||s.getCollisionShape(l,p).isEmpty())disabled|=1<<i;
            }
            int parent=stems.get(i).parent();if(parent>=0&&(disabled&(1<<parent))!=0)disabled|=1<<i;
        }
        return disabled;
    }
    public CompoundTag save() {
        var tag=new CompoundTag();var list=new ListTag();
        for(int i=0;i<stems.size();i++) {
            var stem=stems.get(i);var n=new CompoundTag();var points=new ListTag();
            for(var point:stem.points()) {var p=new CompoundTag();p.putDouble("x",point.x());p.putDouble("y",point.y());p.putDouble("z",point.z());points.add(p);}
            n.put("points",points);n.putDouble("radius",stem.radius());n.putDouble("tip",stem.tip());n.putInt("delay",stem.delay());n.putInt("duration",stem.duration());n.putInt("parent",stem.parent());n.putLong("support",supports.get(i).asLong());list.add(n);
        }
        tag.put("stems",list);return tag;
    }
    public static BuildingBiomassPlan load(CompoundTag tag) {
        var list=tag.getList("stems",Tag.TAG_COMPOUND);if(list.isEmpty()||list.size()>8)return null;
        var stems=new ArrayList<Stem>();var supports=new ArrayList<BlockPos>();
        for(int i=0;i<list.size();i++) {
            var n=list.getCompound(i);var source=n.getList("points",Tag.TAG_COMPOUND);if(source.size()<2||source.size()>12)return null;
            var points=new ArrayList<Point>();
            for(int k=0;k<source.size();k++) {var p=source.getCompound(k);double x=p.getDouble("x"),y=p.getDouble("y"),z=p.getDouble("z");if(!Double.isFinite(x+y+z)||Math.max(Math.max(Math.abs(x),Math.abs(z)),Math.abs(y))>200)return null;points.add(new Point(x,y,z));}
            double radius=n.getDouble("radius"),tip=n.getDouble("tip");int parent=n.getInt("parent"),duration=n.getInt("duration"),delay=n.getInt("delay");
            if(!Double.isFinite(radius+tip)||radius<=0||radius>4||tip<=0||tip>4||parent>=i||parent< -1||duration<1||duration>24000||delay<0||delay>24000)return null;
            stems.add(new Stem(points,radius,tip,delay,duration,parent));supports.add(BlockPos.of(n.getLong("support")));
        }
        var plan=new BuildingBiomassPlan(stems,supports);
        try { plan.cells(); return plan; }
        catch (IllegalArgumentException oversized) { return null; }
    }
}
