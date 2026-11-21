package com.codex.blacklight.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Trace each tongue of a ground impact over a continuous, dry collision surface. */
public final class ConsumeGroundSurface {
    private ConsumeGroundSurface(){}
    public enum Load {
        BODY(1.68,.135F,.65), STAMP(2.54,.30F,.80), MAUL(3.10,.44F,.86);
        public final double radius,speed;
        public final float height;
        Load(double radius,float height,double speed){this.radius=radius;this.height=height;this.speed=speed;}
    }
    public record Tile(BlockPos pos,BlockState state,double distance){}
    public record Surface(Vec3 contact,List<Tile> tiles){}
    private record Section(BlockPos pos,BlockState state,List<AABB> boxes){}
    private record Top(Section section,double low,double y){}
    private static final double EPS=.00001,STEP=.5001;
    private static final class Trace {
        final Level level;
        final Vec3 origin;
        final Map<BlockPos,Section> cache=new HashMap<>();
        Trace(Level level,Vec3 origin){this.level=level;this.origin=origin;}
        Section section(int x,int y,int z){
            return cache.computeIfAbsent(new BlockPos(x,y,z),p->{
                var state=level.getBlockState(p);
                return new Section(p,state,state.getCollisionShape(level,p).toAabbs());
            });
        }
        boolean overhead(int x,int y,int z,double lx,double lz,double top,double clearance){
            var part=section(x,y,z);
            if(!part.state.getFluidState().isEmpty()&&y+1>top+EPS&&y<top+clearance)return true;
            for(var box:part.boxes)
                if(lx>=box.minX&&lx<box.maxX&&lz>=box.minZ&&lz<box.maxZ
                        &&y+box.maxY>top+EPS&&y+box.minY<top+clearance)return true;
            return false;
        }
        Top sample(double wx,double wz,double previous,double tolerance,double clearance,Top previousTop){
            int x=Mth.floor(wx),z=Mth.floor(wz),y0=Mth.floor(previous);
            if(!level.hasChunkAt(new BlockPos(x,y0,z)))return null;
            double lx=wx-x,lz=wz-z;
            Top best=null;
            for(int y=y0-1;y<=y0+1;y++){
                var part=section(x,y,z);
                if(part.state.getRenderShape()!=RenderShape.MODEL||!part.state.getFluidState().isEmpty())continue;
                for(var box:part.boxes){
                    if(lx<box.minX||lx>=box.maxX||lz<box.minZ||lz>=box.maxZ)continue;
                    double top=y+box.maxY,low=y+box.minY;
                    if(Math.abs(top-previous)>tolerance||Math.abs(top-origin.y)>1.06||best!=null&&top<=best.y)continue;
                    if(previousTop!=null&&(low>previous+EPS||previousTop.low>top+EPS))continue;
                    int head=Mth.floor(top+EPS);
                    if(overhead(x,head,z,lx,lz,top,clearance)||overhead(x,head+1,z,lx,lz,top,clearance))continue;
                    best=new Top(part,low,top);
                }
            }
            return best;
        }
        Top reach(Top root,double x,double z,Load load){
            double dx=x-origin.x,dz=z-origin.z;
            int steps=Math.max(1,(int)Math.ceil(Math.max(Math.abs(dx),Math.abs(dz))*16));
            Top current=root;
            for(int i=1;i<=steps;i++){
                double t=(double)i/steps;
                current=sample(origin.x+dx*t,origin.z+dz*t,current.y,STEP,.06+load.height,current);
                if(current==null)return null;
            }
            return current;
        }
    }
    public static Surface sample(Level level,Vec3 contact,Load load){
        if(!Double.isFinite(contact.x+contact.y+contact.z))return null;
        var trace=new Trace(level,contact);
        var root=trace.sample(contact.x,contact.z,contact.y,.055,.06+load.height,null);
        if(root==null)return null;
        var tiles=new LinkedHashMap<BlockPos,Tile>();
        tiles.put(root.section.pos,new Tile(root.section.pos,root.section.state,0));
        int x0=Mth.floor(contact.x-load.radius),x1=Mth.floor(contact.x+load.radius);
        int z0=Mth.floor(contact.z-load.radius),z1=Mth.floor(contact.z+load.radius);
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            // Half-block treads must be sampled on both sides of their internal riser.
            for(double sx:new double[]{.25,.75})for(double sz:new double[]{.25,.75}){
                double wx=x+sx,wz=z+sz,d=Math.hypot(wx-contact.x,wz-contact.z);
                if(d>=load.radius)continue;
                var reached=trace.reach(root,wx,wz,load);
                if(reached==null)continue;
                var prev=tiles.get(reached.section.pos);
                if(prev==null||d<prev.distance)tiles.put(reached.section.pos,new Tile(reached.section.pos,reached.section.state,d));
            }
        }
        return new Surface(new Vec3(contact.x,root.y,contact.z),List.copyOf(tiles.values()));
    }
}
