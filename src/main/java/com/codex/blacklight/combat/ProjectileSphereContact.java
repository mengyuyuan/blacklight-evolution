package com.codex.blacklight.combat;

import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Swept sphere against a static affine cuboid, including shear. The rounded
 * surface is six face patches, twelve finite edge cylinders and eight vertex
 * spheres; an inflated AABB is never the narrow-phase answer.
 *
 * All calculations use a nearby origin. Spatial tolerance is 1e-9 times the
 * shape/radius scale plus eight ulps of the supplied world coordinates; it is
 * independent of travel length. Contacts within that tolerance count as touching.
 * The shape is static for this sweep: callers must supply the pose they mean to hit. */
public final class ProjectileSphereContact {
    /** fraction is in [0,1], center is the projectile center, point lies on the
     * receiving cuboid, and normal points from that surface toward the projectile.
     * For initial containment the nearest outward direction is returned; a zero-
     * radius/zero-motion degenerate contact uses a deterministic unit normal. */
    public record Hit(double fraction,Vec3 center,Vec3 point,Vec3 normal){}
    private record Face(Vec3 a,Vec3 u,Vec3 v,Vec3 cross,Vec3 normal){}
    private record Near(Vec3 point,Vec3 normal,double distanceSqr){}
    private static final int[][] FACES={{0,2,4},{1,3,5},{0,1,4},{2,3,6},{0,1,2},{4,5,6}};
    private ProjectileSphereContact(){}

    public static Hit sweep(Vec3 from,Vec3 to,double radius,HydraContact.Box box){
        // Box.points is a 3x3x3 grid in x-major/y/z order, not an eight-corner list.
        int[] indices={0,18,6,24,2,20,8,26};var corners=new Vec3[8];
        for(int i=0;i<8;i++)corners[i]=box.points.get(indices[i]);
        return sweep(from,to,radius,corners);
    }
    public static Hit sweep(Vec3 from,Vec3 to,double radius,AABB box){
        var corners=new Vec3[8];
        for(int i=0;i<8;i++)corners[i]=new Vec3((i&1)==0?box.minX:box.maxX,(i&2)==0?box.minY:box.maxY,(i&4)==0?box.minZ:box.maxZ);
        return sweep(from,to,radius,corners);
    }
    public static Hit sweep(Vec3 from,Vec3 to,double radius,List<Vec3> corners){
        return sweep(from,to,radius,corners.toArray(Vec3[]::new));
    }
    /** Eight corners of an affine image of a box, with X/Y/Z as index bits 0/1/2:
     * 0=origin, 1=+X, 2=+Y, 3=+X+Y, 4=+Z, 5=+X+Z, 6=+Y+Z, 7=+X+Y+Z.
     * These are local box directions, not world-AABB minima/maxima. A planar,
     * linear or point-degenerate box is allowed. Returns null when there is no hit. */
    public static Hit sweep(Vec3 from,Vec3 to,double radius,Vec3[] corners){
        if(!finite(from)||!finite(to)||!Double.isFinite(radius)||radius<0||corners==null||corners.length!=8)
            throw new IllegalArgumentException("Finite sweep, nonnegative radius and eight box corners required");
        for(var corner:corners)if(!finite(corner))throw new IllegalArgumentException("Nonfinite box corner");
        return new Sweep(from,to,radius,corners).run();
    }
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    private static double maxAbs(Vec3 p){return Math.max(Math.abs(p.x),Math.max(Math.abs(p.y),Math.abs(p.z)));}
    private static double clamp(double v){return Math.max(0,Math.min(1,v));}
    private static Vec3 unit(Vec3 value,Vec3 fallback){double n=value.length();return n>0&&Double.isFinite(n)?new Vec3(value.x/n,value.y/n,value.z/n):fallback;}

    private static final class Sweep {
        final Vec3 worldFrom,worldTo,origin,from,delta;final Vec3[] corners=new Vec3[8];
        final Face[] faces=new Face[6];final double radius,epsilon,timeEpsilon;Hit best;
        Sweep(Vec3 a,Vec3 b,double r,Vec3[] points){
            worldFrom=a;worldTo=b;origin=points[0];from=a.subtract(origin);delta=b.subtract(a);radius=r;
            if(!Double.isFinite(from.lengthSqr())||!Double.isFinite(delta.lengthSqr())||!Double.isFinite(r*r))
                throw new IllegalArgumentException("Sweep exceeds finite double geometry range");
            double coordinate=Math.max(maxAbs(a),maxAbs(b)),scale=Math.max(1,r);
            for(int i=0;i<8;i++){
                corners[i]=points[i].subtract(origin);if(!Double.isFinite(corners[i].lengthSqr()))throw new IllegalArgumentException("Box exceeds finite double geometry range");
                coordinate=Math.max(coordinate,maxAbs(points[i]));scale=Math.max(scale,corners[i].length());
            }
            epsilon=1e-9*scale+8*Math.ulp(coordinate);timeEpsilon=1e-12+epsilon/Math.max(delta.length(),epsilon);
            if(!Double.isFinite((r+epsilon)*(r+epsilon)))throw new IllegalArgumentException("Radius exceeds finite double geometry range");
            // Do not silently treat an arbitrary warped hexahedron as an affine box.
            for(int i=0;i<8;i++){
                var expected=Vec3.ZERO;
                for(int bit:new int[]{1,2,4})if((i&bit)!=0)expected=expected.add(corners[bit]);
                if(expected.distanceTo(corners[i])>4*epsilon)throw new IllegalArgumentException("Corners do not form an affine cuboid");
            }
            for(int i=0;i<FACES.length;i++){
                var ids=FACES[i];var p=corners[ids[0]];var u=corners[ids[1]].subtract(p);var v=corners[ids[2]].subtract(p);var cross=u.cross(v);
                if(!Double.isFinite(cross.lengthSqr()))throw new IllegalArgumentException("Box face exceeds finite double geometry range");
                if(cross.lengthSqr()>0)faces[i]=new Face(p,u,v,cross,unit(cross,new Vec3(0,1,0)));
            }
            if(!Double.isFinite(corners[1].dot(corners[2].cross(corners[4]))))throw new IllegalArgumentException("Box volume exceeds finite double geometry range");
        }
        Hit run(){
            var near=nearest(from);boolean inside=inside(from);
            if(inside||near.distanceSqr<=(radius+epsilon)*(radius+epsilon)){
                var normal=unit(inside?near.point.subtract(from):from.subtract(near.point),near.normal);
                return new Hit(0,worldFrom,near.point.add(origin),normal);
            }
            if(delta.lengthSqr()==0)return null;
            for(var face:faces)if(face!=null){
                double distance=from.subtract(face.a).dot(face.normal),speed=delta.dot(face.normal);
                if(speed==0)continue;
                // Both sides also cover planar boxes. Every candidate remains an
                // actual face point at sphere distance, not a face-plane extension.
                for(int sign:new int[]{-1,1}){
                    double time=(sign*radius-distance)/speed;
                    if(!inTime(time))continue;
                    var center=from.add(delta.scale(clamp(time)));
                    var point=onFace(face,center.subtract(face.normal.scale(sign*radius)));
                    if(point!=null)accept(time,point,face.normal.scale(speed<0?1:-1));
                }
            }
            for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0)edge(corners[i],corners[i|bit]);
            for(var corner:corners)roots(from.subtract(corner),delta,time->accept(time,corner,unit(delta.scale(-1),new Vec3(0,1,0))));
            return best;
        }
        private boolean inTime(double time){return Double.isFinite(time)&&time>=-timeEpsilon&&time<=1+timeEpsilon;}
        private void accept(double time,Vec3 point,Vec3 fallbackNormal){
            if(!inTime(time))return;time=clamp(time);
            if(best!=null&&time>=best.fraction)return;
            var center=from.add(delta.scale(time));var offset=center.subtract(point);
            if(offset.lengthSqr()>(radius+epsilon)*(radius+epsilon))return;
            best=new Hit(time,worldFrom.lerp(worldTo,time),point.add(origin),unit(offset,fallbackNormal));
        }
        private void edge(Vec3 a,Vec3 b){
            var edge=b.subtract(a);double length=edge.length();if(length==0)return;var axis=edge.scale(1/length);
            var offset=from.subtract(a);var radial=offset.subtract(axis.scale(offset.dot(axis)));var travel=delta.subtract(axis.scale(delta.dot(axis)));
            roots(radial,travel,time->{
                if(!inTime(time))return;
                double along=offset.add(delta.scale(clamp(time))).dot(axis);
                if(along< -epsilon||along>length+epsilon)return;
                accept(time,a.add(axis.scale(Math.max(0,Math.min(length,along)))),unit(travel.scale(-1),new Vec3(0,1,0)));
            });
        }
        /** Closest-line form avoids subtracting two large quadratic discriminant terms. */
        private void roots(Vec3 offset,Vec3 travel,java.util.function.DoubleConsumer consume){
            double a=travel.lengthSqr();if(a==0)return;
            double middle=-offset.dot(travel)/a;
            if(!Double.isFinite(middle))return;
            var closest=offset.add(travel.scale(middle));double distance=closest.lengthSqr();
            if(distance>(radius+epsilon)*(radius+epsilon))return;
            double half=Math.sqrt(Math.max(0,radius*radius-distance)/a);
            consume.accept(middle-half);if(half>0)consume.accept(middle+half);
        }
        private Vec3 onFace(Face face,Vec3 point){
            var d=point.subtract(face.a);double divisor=face.cross.lengthSqr();
            double u=d.cross(face.v).dot(face.cross)/divisor,v=face.u.cross(d).dot(face.cross)/divisor;
            double tolerance=epsilon/Math.max(epsilon,Math.min(face.u.length(),face.v.length()));
            if(u< -tolerance||u>1+tolerance||v< -tolerance||v>1+tolerance)return null;
            return face.a.add(face.u.scale(clamp(u))).add(face.v.scale(clamp(v)));
        }
        private boolean inside(Vec3 point){
            var x=corners[1];var y=corners[2];var z=corners[4];double determinant=x.dot(y.cross(z));
            if(determinant==0)return false;
            double a=point.dot(y.cross(z))/determinant,b=point.dot(z.cross(x))/determinant,c=point.dot(x.cross(y))/determinant;
            return a>=0&&a<=1&&b>=0&&b<=1&&c>=0&&c<=1;
        }
        private Near nearest(Vec3 point){
            Vec3 result=corners[0],normal=unit(delta.scale(-1),new Vec3(0,1,0));double distance=point.distanceToSqr(result);
            var middle=corners[7].scale(.5);
            for(var face:faces)if(face!=null){
                var projection=point.subtract(face.normal.scale(point.subtract(face.a).dot(face.normal)));var p=onFace(face,projection);
                if(p!=null&&point.distanceToSqr(p)<distance){
                    result=p;distance=point.distanceToSqr(p);normal=face.normal.scale(face.a.subtract(middle).dot(face.normal)<0?-1:1);
                }
            }
            for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0){
                var a=corners[i];var edge=corners[i|bit].subtract(a);double length=edge.lengthSqr();
                var p=length==0?a:a.add(edge.scale(clamp(point.subtract(a).dot(edge)/length)));
                if(point.distanceToSqr(p)<distance){result=p;distance=point.distanceToSqr(p);normal=unit(point.subtract(p),normal);}
            }
            return new Near(result,normal,distance);
        }
    }
}
