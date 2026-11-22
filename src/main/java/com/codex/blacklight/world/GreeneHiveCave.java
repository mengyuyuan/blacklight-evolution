package com.codex.blacklight.world;

import com.codex.blacklight.city.FutianLayout;
import com.codex.blacklight.registry.ModBlocks;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Underground chambers sit beneath the two actual infected districts selected by this city's seed. */
public final class GreeneHiveCave {
    private GreeneHiveCave(){}
    public static BlockPos site(long seed,int index){
        var district=FutianLayout.hive(seed,index);
        return new BlockPos((district.minX()+district.maxX())/2,4,(district.minZ()+district.maxZ())/2);
    }
    public static BlockPos nearest(long seed,BlockPos from){
        var a=site(seed,0);var b=site(seed,1);return from.distSqr(a)<=from.distSqr(b)?a:b;
    }
    public record Column(int floor,int roof,boolean vein,boolean lamp){
        public BlockState replace(int y,BlockState natural){
            if(y<floor-1 || y>roof+1)return natural;
            if(y==floor-1 || y==roof+1){
                if(lamp)return Blocks.SHROOMLIGHT.defaultBlockState();
                return vein?ModBlocks.HIVE_VEIN.get().defaultBlockState():natural;
            }
            return Blocks.CAVE_AIR.defaultBlockState();
        }
    }
    public static Column at(long seed,int x,int z){
        if(x<0 || z<0 || x>=com.codex.blacklight.city.FutianMap.WIDTH || z>=com.codex.blacklight.city.FutianMap.DEPTH)return null;
        return column(nearest(seed,new BlockPos(x,4,z)),x,z);
    }
    public static Column column(BlockPos site,int x,int z){
        int dx=x-site.getX(),dz=z-site.getZ();
        if(Math.abs(dx)>66 || Math.abs(dz)>50)return null;
        double a=Math.atan2(dz,dx);
        double radius=Math.hypot(dx/49D,dz/45D)/(1+.055*Math.sin(a*5)+.03*Math.cos(a*9));
        boolean tunnel=dx>=33 && dx<65 && Math.abs(dz-3*Math.sin(dx*.13))<4;
        if(radius>=1 && !tunnel)return null;
        double edge=Math.min(1,radius);
        int floor=site.getY()+(int)(edge*edge*3);
        int roof=site.getY()+56-(int)(26*Math.pow(edge,5));
        if(tunnel){
            int stair=Math.min(58,site.getY()+3+Math.max(0,dx-34)*2);
            if(radius>=1){floor=stair;roof=stair+4;}
            else roof=Math.max(roof,stair+4);
        }
        double warp=Math.sin(dz*.083+Math.cos(dx*.105))*2.4+Math.sin(dx*.24)*.7;
        double branch=dx+warp+.47*dz+3.6*Math.sin(dz*.16);
        double rib=Math.abs(Math.sin(branch*.19+Math.sin(dz*.10)))*5.2;
        boolean vein=rib<1.05 || radius>.85 && Math.sin(a*19+edge*3)>.81;
        boolean lamp=vein && Math.cos(dz*.32+dx*.12+warp*.2)>.72;
        int ridge=(int)(Math.max(0,1-rib*.37)*(1+3.8*edge*edge));
        if(!tunnel){floor+=ridge;roof-=ridge*2;}
        return new Column(floor,Math.min(62,roof),vein,lamp);
    }
    public static BlockPos sac(BlockPos site,int index){
        if(index%2==0){
            Direction heading=wall(index);
            BlockPos last=site.above(12);
            for(int r=27;r<=58;r++){
                var p=site.relative(heading,r).above(12);
                if(column(site,p.getX(),p.getZ())==null)return last;
                last=p;
            }
        }
        double angle=index*Math.PI/3;
        for(int r=29;r<59;r++){
            int x=site.getX()+(int)Math.round(Math.cos(angle)*r),z=site.getZ()+(int)Math.round(Math.sin(angle)*r);
            var c=column(site,x,z);
            if(c!=null && c.roof()<site.getY()+48)return new BlockPos(x,c.roof(),z);
        }
        return site.offset(0,49,0);
    }
    private static Direction wall(int index){return switch(index){case 0->Direction.WEST;case 2->Direction.NORTH;default->Direction.SOUTH;};}
    public static Direction support(int index){return index%2==0?wall(index):Direction.UP;}
    public static Vec3 guidance(BlockPos cave,Vec3 eye){
        return Vec3.atCenterOf(cave).subtract(eye).normalize();
    }
}
