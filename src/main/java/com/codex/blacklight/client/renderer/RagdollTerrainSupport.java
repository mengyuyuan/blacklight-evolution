package com.codex.blacklight.client.renderer;

import java.util.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Support under the actual skin footprint, including a slab touched only by an edge. */
final class RagdollTerrainSupport {
    /** Clear a shallow side contact before interpreting the block as support.
     * Lifting first turns a toe barely across a slab edge into a whole-body hop. */
    static Vec3 settle(List<Vec3> quads,List<AABB> terrain){
        terrain=mergeTiles(terrain);
        var shift=correction(quads,terrain);
        var moved=shift.lengthSqr()==0?quads:quads.stream().map(p->p.add(shift)).toList();
        return shift.add(0,lift(moved,terrain),0);
    }
    private static List<AABB> mergeTiles(List<AABB> terrain){
        var merged=new ArrayList<>(terrain);
        // Internal faces between adjacent floor/slab tiles are not exits. Keep
        // nonrectangular unions separate so empty corners remain empty.
        boolean changed;
        do{
            changed=false;
            outer:for(int i=0;i<merged.size();i++)for(int j=i+1;j<merged.size();j++){
                var a=merged.get(i);var b=merged.get(j);
                boolean x=a.minY==b.minY&&a.maxY==b.maxY&&a.minZ==b.minZ&&a.maxZ==b.maxZ&&a.minX<=b.maxX&&b.minX<=a.maxX;
                boolean z=a.minY==b.minY&&a.maxY==b.maxY&&a.minX==b.minX&&a.maxX==b.maxX&&a.minZ<=b.maxZ&&b.minZ<=a.maxZ;
                if(x||z){merged.set(i,a.minmax(b));merged.remove(j);changed=true;break outer;}
            }
        }while(changed);
        return merged;
    }
    /** Joint projection can put connected skin through a wall even when each
     * individual movement was collision-clipped. Move the intact component by
     * its shortest escape, rather than lifting a sideways contact onto the wall. */
    static Vec3 correction(List<Vec3> quads,List<AABB> terrain){
        if(quads.isEmpty()||terrain.isEmpty())return Vec3.ZERO;
        Vec3 shift=Vec3.ZERO;
        for(int iteration=0;iteration<6;iteration++){
            var moved=new ArrayList<Vec3>(quads.size());for(var p:quads)moved.add(p.add(shift));
            double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
            for(var p:moved){minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);minZ=Math.min(minZ,p.z);maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);maxZ=Math.max(maxZ,p.z);}
            boolean grounded=grounded(moved,terrain,minY);
            Vec3 escape=null;
            for(var box:terrain){
                if(maxX<=box.minX||minX>=box.maxX||maxY<=box.minY||minY>=box.maxY||maxZ<=box.minZ||minZ>=box.maxZ||!intersects(moved,box))continue;
                Vec3 candidate=null,side=null;
                for(var v:escapes(moved,box)){
                    if(candidate==null||v.lengthSqr()<candidate.lengthSqr())candidate=v;
                    if(v.y==0&&shift.add(v).lengthSqr()<=.75*.75&&(side==null||v.lengthSqr()<side.lengthSqr()))side=v;
                }
                // When skin already rests on a lower floor, keep that support
                // while clearing a higher step. The shortest 3D escape can be
                // an upward hop even though a bounded sideways exit is clear.
                if(grounded&&box.maxY>minY+.025&&side!=null)candidate=side;
                if(candidate==null)continue;
                if(escape==null||candidate.lengthSqr()<escape.lengthSqr())escape=candidate;
            }
            if(escape==null)break;
            // Bounded per projection: malformed or trapped large rigs must not
            // teleport across the world while trying to clear an enclosure.
            if(shift.add(escape).lengthSqr()>.75*.75)break;
            shift=shift.add(escape);
        }
        return shift;
    }
    private static boolean grounded(List<Vec3> skin,List<AABB> terrain,double bottom){
        for(var box:terrain)if(Math.abs(box.maxY-bottom)<.025)
            for(var p:skin)if(p.y<bottom+.001&&p.x>=box.minX&&p.x<=box.maxX&&p.z>=box.minZ&&p.z<=box.maxZ)return true;
        return false;
    }
    private static List<Vec3> escapes(List<Vec3> quads,AABB box){
        var result=new ArrayList<Vec3>(6);
        double[] lower={box.minX,box.minY,box.minZ},upper={box.maxX,box.maxY,box.maxZ};
        for(int axis=0;axis<3;axis++){
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(int i=0;i+3<quads.size();i+=4){
                List<Vec3> face=quads.subList(i,i+4);
                // Only skin in the block's perpendicular footprint limits this
                // escape. A raised foot beyond a slab must not prevent a thigh
                // beside its edge from moving sideways by a few centimeters.
                for(int other=0;other<3;other++)if(other!=axis){
                    face=clip(face,other,lower[other]+.00001,true);
                    face=clip(face,other,upper[other]-.00001,false);
                }
                for(var p:face){min=Math.min(min,coordinate(p,axis));max=Math.max(max,coordinate(p,axis));}
            }
            if(!Double.isFinite(min))continue;
            double positive=upper[axis]+.001-min,negative=lower[axis]-.001-max;
            result.add(axis==0?new Vec3(positive,0,0):axis==1?new Vec3(0,positive,0):new Vec3(0,0,positive));
            result.add(axis==0?new Vec3(negative,0,0):axis==1?new Vec3(0,negative,0):new Vec3(0,0,negative));
        }
        return result;
    }
    private static boolean intersects(List<Vec3> quads,AABB box){
        for(int i=0;i+3<quads.size();i+=4){
            List<Vec3> face=quads.subList(i,i+4);
            face=clip(face,0,box.minX+.00001,true);face=clip(face,0,box.maxX-.00001,false);
            face=clip(face,1,box.minY+.00001,true);face=clip(face,1,box.maxY-.00001,false);
            face=clip(face,2,box.minZ+.00001,true);face=clip(face,2,box.maxZ-.00001,false);
            if(!face.isEmpty())return true;
        }
        return false;
    }
    static double lift(List<Vec3> quads,List<AABB> terrain){
        double top=quads.stream().mapToDouble(v->v.y).max().orElse(Double.NEGATIVE_INFINITY),lift=0;
        for(var box:terrain){
            // A wall/ceiling above the skin is not a floor onto which the body can teleport.
            if(box.maxY>top+.12)continue;
            for(int i=0;i+3<quads.size();i+=4){
                var face=quads.subList(i,i+4);
                double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
                for(var p:face){minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);minZ=Math.min(minZ,p.z);maxZ=Math.max(maxZ,p.z);}
                if(maxX<box.minX||minX>box.maxX||maxZ<box.minZ||minZ>box.maxZ||minY>=box.maxY||maxY<box.minY)continue;
                List<Vec3> clipped=face;
                clipped=clip(clipped,0,box.minX,true);clipped=clip(clipped,0,box.maxX,false);
                clipped=clip(clipped,2,box.minZ,true);clipped=clip(clipped,2,box.maxZ,false);
                for(var p:clipped)lift=Math.max(lift,box.maxY+.001-p.y);
            }
        }
        return Double.isFinite(lift)?lift:0;
    }
    /** Signed displacement to the first top surface touched by the projected skin. */
    static double rest(List<Vec3> skin,List<AABB> terrain,double bottom){
        double best=Double.NEGATIVE_INFINITY;
        for(var box:mergeTiles(terrain)){
            if(box.maxY>bottom+1.02 || box.maxY<bottom-3)continue;
            for(int i=0;i+3<skin.size();i+=4){
                List<Vec3> face=skin.subList(i,i+4);
                face=clip(face,0,box.minX+.00001,true);face=clip(face,0,box.maxX-.00001,false);
                face=clip(face,2,box.minZ+.00001,true);face=clip(face,2,box.maxZ-.00001,false);
                for(var point:face)best=Math.max(best,box.maxY-point.y);
            }
        }
        return Double.isFinite(best)?best:0;
    }
    private static List<Vec3> clip(List<Vec3> face,int axis,double plane,boolean lower){
        if(face.isEmpty())return face;
        var result=new ArrayList<Vec3>();Vec3 a=face.get(face.size()-1);double da=coordinate(a,axis)-plane;boolean inA=lower?da>=0:da<=0;
        for(var b:face){double db=coordinate(b,axis)-plane;boolean inB=lower?db>=0:db<=0;
            if(inA!=inB)result.add(a.lerp(b,da/(da-db)));
            if(inB)result.add(b);a=b;da=db;inA=inB;
        }
        return result;
    }
    private static double coordinate(Vec3 p,int axis){return axis==0?p.x:axis==1?p.y:p.z;}
    private RagdollTerrainSupport(){}
}
