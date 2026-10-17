package com.codex.blacklight.animation;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Two rigid joint transforms blended without the volume loss of matrix skinning. */
public final class RigidSkinning {
    private RigidSkinning() {}
    public record Joint(Quaternionf real, Quaternionf dual, boolean rigid) {
        public static Joint from(Matrix4f matrix) {
            var real=matrix.getNormalizedRotation(new Quaternionf()).normalize();
            var t=matrix.getTranslation(new Vector3f());
            var dual=new Quaternionf(t.x,t.y,t.z,0).mul(real).mul(.5F);
            var scale=matrix.getScale(new Vector3f());
            return new Joint(real,dual,scale.distanceSquared(1,1,1)<1e-6F);
        }
    }
    /** Scratch quaternions are supplied once per draw, never allocated per vertex. */
    public static Vector3f transform(Joint a,Joint b,float weight,Vector3f p,
                                    Vector3f out,Quaternionf real,Quaternionf dual) {
        float wb=a.real.dot(b.real)<0?-weight:weight,wa=1-weight;
        real.set(a.real.x*wa+b.real.x*wb,a.real.y*wa+b.real.y*wb,
                a.real.z*wa+b.real.z*wb,a.real.w*wa+b.real.w*wb);
        dual.set(a.dual.x*wa+b.dual.x*wb,a.dual.y*wa+b.dual.y*wb,
                a.dual.z*wa+b.dual.z*wb,a.dual.w*wa+b.dual.w*wb);
        float inverseLength=1/(float)Math.sqrt(real.lengthSquared());
        real.mul(inverseLength);dual.mul(inverseLength);
        real.transform(p,out);
        // Vector part of dual * conjugate(real); the scalar projection cancels.
        float x=2*(-dual.w*real.x+dual.x*real.w-dual.y*real.z+dual.z*real.y);
        float y=2*(-dual.w*real.y+dual.x*real.z+dual.y*real.w-dual.z*real.x);
        float z=2*(-dual.w*real.z-dual.x*real.y+dual.y*real.x+dual.z*real.w);
        return out.add(x,y,z);
    }
}
