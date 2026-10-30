package com.codex.blacklight.military.vehicle;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import com.codex.blacklight.military.vehicle.VehicleDefinition.Vec3;
import com.codex.blacklight.military.vehicle.VehicleHullShape.Bounds;

/** Body/barrel contact only; the enclosing query bbox is deliberately not a solid. */
public final class VehicleCollisionGeometry {
    public static final double CELL_SIZE=1.0/16;
    public static final int MAX_CELLS=262144,MAX_AXIS_CELLS=256;
    public record Grid(double[] xs,double[] ys,double[] zs,BitSet filled,double maximumError) {
        public boolean isEmpty(){return filled.isEmpty();}
        public int xSize(){return xs.length-1;}
        public int ySize(){return ys.length-1;}
        public int zSize(){return zs.length-1;}
    }
    private static final Grid EMPTY=new Grid(new double[]{0},new double[]{0},new double[]{0},new BitSet(),0);
    private record Projection(double x,double y,double z,double radius){}
    private static final class Box {
        final Vec3 center;final Bounds bounds;final List<Projection> projections;final boolean axisAligned;
        Box(Vec3 center,Vec3 half,Vec3 right,Vec3 up,Vec3 forward){
            this.center=center;
            Vec3[] axes={right,up,forward},world={new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1)};
            var separating=new ArrayList<Vec3>(15);separating.addAll(List.of(world));separating.addAll(List.of(axes));
            for(Vec3 a:world)for(Vec3 b:axes)separating.add(cross(a,b));
            projections=new ArrayList<>(15);
            for(Vec3 axis:separating)if(dot(axis,axis)>1e-16)
                projections.add(new Projection(axis.x(),axis.y(),axis.z(),half.x()*Math.abs(dot(axis,right))
                        +half.y()*Math.abs(dot(axis,up))+half.z()*Math.abs(dot(axis,forward))));
            double x=half.x()*Math.abs(right.x())+half.y()*Math.abs(up.x())+half.z()*Math.abs(forward.x());
            double y=half.x()*Math.abs(right.y())+half.y()*Math.abs(up.y())+half.z()*Math.abs(forward.y());
            double z=half.x()*Math.abs(right.z())+half.y()*Math.abs(up.z())+half.z()*Math.abs(forward.z());
            bounds=new Bounds(center.x()-x,center.y()-y,center.z()-z,center.x()+x,center.y()+y,center.z()+z);
            axisAligned=aligned(right)&&aligned(up)&&aligned(forward);
        }
        boolean overlaps(Bounds b){return overlaps(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ());}
        boolean overlaps(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
            if(maxX<bounds.minX()||minX>bounds.maxX()||maxY<bounds.minY()||minY>bounds.maxY()||maxZ<bounds.minZ()||minZ>bounds.maxZ())return false;
            double x=(minX+maxX)/2-center.x(),y=(minY+maxY)/2-center.y(),z=(minZ+maxZ)/2-center.z();
            double hx=(maxX-minX)/2,hy=(maxY-minY)/2,hz=(maxZ-minZ)/2;
            for(Projection p:projections)if(Math.abs(x*p.x+y*p.y+z*p.z)>p.radius+hx*Math.abs(p.x)+hy*Math.abs(p.y)+hz*Math.abs(p.z)+1e-9)return false;
            return true;
        }
    }
    private final List<Box> parts;
    private final Bounds envelope;
    public VehicleCollisionGeometry(VehicleDefinition definition,double bodyYaw,double turretYaw,double pitch){
        VehicleDefinition.finiteAngle(bodyYaw);VehicleDefinition.finiteAngle(turretYaw);VehicleDefinition.finiteAngle(pitch);
        var boxes=new ArrayList<Box>();Vec3 zero=new Vec3(0,0,0);
        boxes.add(new Box(new Vec3(0,definition.height()/2,0),new Vec3(definition.width()/2,definition.height()/2,definition.length()/2),
                VehicleDefinition.rotateYaw(new Vec3(1,0,0),bodyYaw),new Vec3(0,1,0),VehicleDefinition.rotateYaw(new Vec3(0,0,1),bodyYaw)));
        if(definition.barrelRadius()>0)for(String mount:definition.anchors().keySet())if(mount.startsWith("muzzle_")){
            String breech="breech_"+mount.substring("muzzle_".length());
            Vec3 a=definition.mountWorld(breech,zero,bodyYaw,turretYaw,pitch),b=definition.mountWorld(mount,zero,bodyYaw,turretYaw,pitch);
            Vec3 segment=VehicleDefinition.subtract(b,a);double length=Math.sqrt(dot(segment,segment));
            Vec3 forward=scale(segment,1/length),right=new Vec3(1,0,0);
            var frame=definition.anchors().get(breech).frame();
            if(frame==VehicleDefinition.Frame.GUN_PITCH)right=VehicleDefinition.rotatePitch(right,pitch);
            if(frame!=VehicleDefinition.Frame.BODY)right=VehicleDefinition.rotateYaw(right,turretYaw);
            right=VehicleDefinition.rotateYaw(right,bodyYaw);
            right=VehicleDefinition.subtract(right,scale(forward,dot(right,forward)));right=scale(right,1/Math.sqrt(dot(right,right)));
            double r=definition.barrelRadius();
            boxes.add(new Box(scale(VehicleDefinition.add(a,b),.5),new Vec3(r,r,length/2),right,cross(forward,right),forward));
        }
        parts=List.copyOf(boxes);Bounds all=parts.get(0).bounds;for(int i=1;i<parts.size();i++)all=all.union(parts.get(i).bounds);envelope=all;
    }
    /** Conservative cells cover every contact. Their excess distance is at most sqrt(3)*cell size. */
    public Grid rasterize(Bounds query){
        var relevant=new ArrayList<Box>(parts.size());for(Box part:parts)if(part.overlaps(query))relevant.add(part);
        if(relevant.isEmpty())return EMPTY;
        if(relevant.size()==1&&relevant.get(0).axisAligned){
            var b=relevant.get(0).bounds;var filled=new BitSet(1);filled.set(0);
            return new Grid(new double[]{b.minX(),b.maxX()},new double[]{b.minY(),b.maxY()},new double[]{b.minZ(),b.maxZ()},filled,0);
        }
        var clipped=new Bounds(Math.max(query.minX(),envelope.minX()),Math.max(query.minY(),envelope.minY()),Math.max(query.minZ(),envelope.minZ()),
                Math.min(query.maxX(),envelope.maxX()),Math.min(query.maxY(),envelope.maxY()),Math.min(query.maxZ(),envelope.maxZ()));
        double step=CELL_SIZE;int nx,ny,nz;
        while(true){
            nx=count(clipped.minX(),clipped.maxX(),step);ny=count(clipped.minY(),clipped.maxY(),step);nz=count(clipped.minZ(),clipped.maxZ(),step);
            if(nx<=MAX_AXIS_CELLS&&ny<=MAX_AXIS_CELLS&&nz<=MAX_AXIS_CELLS&&(long)nx*ny*nz<=MAX_CELLS)break;
            step*=2;
        }
        double[] xs=coordinates(clipped.minX(),step,nx),ys=coordinates(clipped.minY(),step,ny),zs=coordinates(clipped.minZ(),step,nz);
        var filled=new BitSet(nx*ny*nz);
        for(int x=0;x<nx;x++)for(int y=0;y<ny;y++)for(int z=0;z<nz;z++)for(Box part:relevant)
            if(part.overlaps(xs[x],ys[y],zs[z],xs[x+1],ys[y+1],zs[z+1])){filled.set((x*ny+y)*nz+z);break;}
        return new Grid(xs,ys,zs,filled,Math.sqrt(3)*step);
    }
    private static int count(double min,double max,double step){return Math.max(1,(int)(Math.ceil(max/step)-Math.floor(min/step)));}
    private static double[] coordinates(double min,double step,int count){double[] result=new double[count+1];double start=Math.floor(min/step)*step;for(int i=0;i<=count;i++)result[i]=start+i*step;return result;}
    private static boolean aligned(Vec3 axis){double max=Math.max(Math.abs(axis.x()),Math.max(Math.abs(axis.y()),Math.abs(axis.z())));return max>1-1e-12&&Math.abs(axis.x())+Math.abs(axis.y())+Math.abs(axis.z())-max<1e-12;}
    private static double dot(Vec3 a,Vec3 b){return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}
    private static Vec3 scale(Vec3 a,double factor){return new Vec3(a.x()*factor,a.y()*factor,a.z()*factor);}
    private static Vec3 cross(Vec3 a,Vec3 b){return new Vec3(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x());}
}
