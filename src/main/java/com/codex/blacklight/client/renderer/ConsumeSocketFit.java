package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;

/** Retarget the source sockets to the posed MC skeleton and keep its open tendril arc on the skin. */
final class ConsumeSocketFit {
    // Our twelve-pixel character jacket: lower belt at 2 px, mid seam at 6, chest at 9, neck base one pixel above its top.
    private static final double PELVIS=2./12, SPINE_2=6./12, SPINE_3=9./12, NECK=13./12;
    private ConsumeSocketFit() {}

    static Vec3[] actor(Vec3 hipR,Vec3 hipL,Vec3 shoulderR,Vec3 shoulderL){
        Vec3 hips=hipR.lerp(hipL,.5),shoulders=shoulderR.lerp(shoulderL,.5);
        return new Vec3[]{hips.lerp(shoulders,SPINE_3),hipR,hips.lerp(shoulders,PELVIS),
                hips.lerp(shoulders,SPINE_2),hipL,shoulderR,shoulderL};
    }

    static Vec3[] receiver(Vec3 hipR,Vec3 hipL,Vec3 neck){
        Vec3 hips=hipR.lerp(hipL,.5);
        return new Vec3[]{hips.lerp(neck,PELVIS/NECK),neck,hipL,hips.lerp(neck,SPINE_2/NECK),hipR};
    }

    record Skin(Vec3 center,Vec3 side,Vec3 up,Vec3 front,double width,double height,double depth){
        static Skin between(Vec3 hipR,Vec3 hipL,Vec3 upper,double halfWidth){
            Vec3 hip=hipR.lerp(hipL,.5),along=upper.subtract(hip);
            double height=along.length();
            if(height<.02||halfWidth<.015)return null;
            Vec3 up=along.scale(1/height),side=hipL.subtract(hipR);
            side=side.subtract(up.scale(up.dot(side))).normalize();
            if(side.lengthSqr()<.5)return null;
            // BlacklightPlayerModel's body is 8 x 12 x 4 texels with a 0.25-texel jacket.
            return new Skin(hip.lerp(upper,.5),side,up,side.cross(up),halfWidth,height*.5,halfWidth*(2.25/4.25));
        }
        double exit(Vec3 point,Vec3 direction,double radius){
            Vec3 delta=point.subtract(center);
            double x=delta.dot(side),y=delta.dot(up),z=delta.dot(front);
            double w=width+radius,h=height+radius,d=depth+radius;
            if(Math.abs(x)>=w||Math.abs(y)>=h||Math.abs(z)>=d)return 0;
            return Math.min(distanceToFace(x,direction.dot(side),w),
                    Math.min(distanceToFace(y,direction.dot(up),h),distanceToFace(z,direction.dot(front),d)));
        }
        private static double distanceToFace(double position,double direction,double extent){
            return Math.abs(direction)<1e-8?Double.POSITIVE_INFINITY:(Math.copySign(extent,direction)-position)/direction;
        }
    }
    static Skin actorSkin(Vec3[] points){
        Vec3 hips=points[1].lerp(points[4],.5),shoulders=points[5].lerp(points[6],.5);
        return Skin.between(points[1],points[4],hips.lerp(shoulders,NECK),points[5].distanceTo(points[6])*.425);
    }
    static Skin receiverSkin(Vec3[] points){
        return Skin.between(points[4],points[2],points[1],points[4].distanceTo(points[2])*1.1);
    }

    /** Apply only a penetration correction in the source arc plane. Socket positions and coil-free straight spans stay fixed. */
    static void clearSkin(Vec3[] points,Vec3 root,Vec3 tip,Skin actor,Skin receiver,double radius){
        Vec3 axis=tip.subtract(root).normalize();
        if(axis.lengthSqr()<.5)return;
        for(int i=1;i<points.length;i++){
            Vec3 offset=points[i].subtract(root);
            Vec3 arc=offset.subtract(axis.scale(offset.dot(axis)));
            double bow=arc.length();
            if(bow<1e-5)continue;
            Vec3 outward=arc.scale(1/bow);
            double a=actor==null?0:Math.min(actor.depth+radius,actor.exit(points[i],outward,radius));
            double b=receiver==null?0:Math.min(receiver.depth+radius,receiver.exit(points[i],outward,radius));
            // A near-straight path entering a socket must not jump out of the body at contact.
            // Fade the correction at both sockets and cap it to one skin half-depth.
            double lift=Math.min(2*bow,Math.max(a,b));
            if(lift>0)points[i]=points[i].add(outward.scale(lift));
        }
    }
}
