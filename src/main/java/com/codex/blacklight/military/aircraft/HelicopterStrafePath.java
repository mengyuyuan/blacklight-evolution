package com.codex.blacklight.military.aircraft;

import net.minecraft.world.phys.Vec3;

/** P1 103e1cc0/103e1ba0: bounded cardinal corridor search, before prepare/tight flight actions.
 * Native instruction-emulation fixtures cover the ordering, thresholds and asymmetric X search.
 * This is path geometry only: acquiring a token, reserving space and flying remain separate.
 */
public final class HelicopterStrafePath {
    private HelicopterStrafePath(){}
    public interface Grid {
        float cellSize();
        float originX();
        float originZ();
        /** World surface height; NaN means unavailable terrain in the Minecraft adapter. */
        float height(int x,int z);
    }
    public record Path(Vec3 targetGround,Vec3 start,Vec3 end,float relativeRise){}
    /** Native cache retains the last ground output even when the corridor search fails. */
    record Search(Path path,Vec3 groundOutput){}

    public static Path find(Grid grid,Vec3 craft,Vec3 target,int minimum,int maximum,boolean farthest){
        return search(grid,craft,target,minimum,maximum,farthest,Vec3.ZERO).path();
    }
    static Search search(Grid grid,Vec3 craft,Vec3 target,int minimum,int maximum,boolean farthest,Vec3 previousGround){
        if(grid==null||!HelicopterFlight.finite(craft)||!HelicopterFlight.finite(target)
                ||!Float.isFinite(grid.cellSize())||grid.cellSize()<=0||20.0/grid.cellSize()>257
                ||!Float.isFinite(grid.originX())||!Float.isFinite(grid.originZ())
                ||minimum<0||maximum<=minimum||maximum>128)return new Search(null,previousGround);
        var p=new Probe(grid);int x=(int)(((float)target.x-grid.originX()+grid.cellSize()*.5F)/grid.cellSize());
        int z=(int)(((float)target.z-grid.originZ()+grid.cellSize()*.5F)/grid.cellSize());float ground=p.height(x,z);
        Vec3 at=new Vec3(target.x,ground,target.z),output=previousGround;
        if(Math.abs(ground-target.y)>2){
            boolean found=false;
            // The native fallback updates the search base after each accepted
            // column. Keep its order; do not replace it with a nearest-point guess.
            for(int dx=-2;dx<=2;dx++){
                int px=x+dx;
                for(int dz=-2;dz<=2;dz++){
                    int pz=z+dz;float h=p.height(px,pz);
                    if(Math.abs(h-target.y)<2){x=px;z=pz;ground=h;at=p.point(x,z,h);output=at;found=true;break;}
                }
                if(!found)return new Search(null,output);
            }
        }else output=at;
        var near=new Scan[]{new Scan(),new Scan(),new Scan(),new Scan()};
        var far=new Scan[]{new Scan(),new Scan(),new Scan(),new Scan()};
        int[][] directions={{0,-1},{0,1},{1,0},{-1,0}};
        for(int d=1;d<maximum;d++){
            boolean active=false;for(int i=0;i<4;i++)active|=near[i].active||far[i].active;if(!active)break;
            for(int i=0;i<4;i++){
                int other=i^1;
                // 103e240e and103e24ca inspect their own far-result flags on X;
                // the Z pair inspects the opposite near result. Verified against
                // original instructions, including the one-cell X asymmetry.
                boolean partner=i<2?near[other].found:far[i].found;
                int dx=directions[i][0]*d,dz=directions[i][1]*d;
                if(near[i].active&&(near[other].active||partner)){
                    near[i].step(p,x,z,dx,dz,ground,minimum,false);
                    if(!near[i].active&&!near[i].found)far[i].active=false;
                }else if(far[i].active)far[i].step(p,x,z,dx,dz,ground,minimum,true);
            }
        }
        for(int a=0;a<4;a+=2){
            int b=a+1;if(!near[a].found||!near[b].found)continue;
            int span=a==0?Math.abs(near[a].z-near[b].z):Math.abs(near[a].x-near[b].x);
            if(span>=2*maximum)continue;
            float high=Math.max(near[a].top,near[b].top);
            Scan one=farthest&&far[a].found?far[a]:near[a],two=farthest&&far[b].found?far[b]:near[b];
            if(one==far[a])high=Math.max(high,one.top);
            Vec3 start=p.point(one.x,one.z,Math.max(high,p.area(one.x,one.z)));
            if(two==far[b])high=Math.max(high,two.top);
            Vec3 end=p.point(two.x,two.z,Math.max(high,p.area(two.x,two.z)));
            if(start.distanceToSqr(craft)>end.distanceToSqr(craft)){var swap=start;start=end;end=swap;}
            return new Search(p.known?new Path(at,start,end,high-ground):null,output);
        }
        return new Search(null,output);
    }
    private static final class Probe {
        final Grid grid;final int radius;boolean known=true;
        // MC work bound: find() rejects resolutions needing more than 257 cells
        // per query. One-block MC cells fit this bound; malformed tiny
        // resolutions must not turn this bounded search into billions of samples.
        Probe(Grid grid){this.grid=grid;int width=(int)Math.ceil(20/grid.cellSize());if((width&1)==0)width++;radius=width/2;}
        float height(int x,int z){float h=grid.height(x,z);if(!Float.isFinite(h)){known=false;return 0;}return h;}
        Vec3 point(int x,int z,float h){return new Vec3(grid.originX()+x*grid.cellSize(),h,grid.originZ()+z*grid.cellSize());}
        float area(int x,int z){float top=height(x,z);for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)top=Math.max(top,height(x+dx,z+dz));return top;}
    }
    private static final class Scan {
        boolean active=true,found;float top=-Float.MAX_VALUE;int x=-1,z=-1;
        void step(Probe p,int baseX,int baseZ,int dx,int dz,float ground,int minimum,boolean keep){
            active=true;float h=p.height(baseX+dx,baseZ+dz);
            if(h>ground+10)active=false;else top=Math.max(top,h);
            found=false;
            if((Math.abs(dx)>minimum||Math.abs(dz)>minimum)&&p.area(baseX+dx,baseZ+dz)<ground+20){
                x=baseX+dx;z=baseZ+dz;active=keep;found=true;
            }
        }
    }
}
