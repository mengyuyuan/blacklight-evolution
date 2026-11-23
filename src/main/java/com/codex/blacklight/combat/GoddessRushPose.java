package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Deform the existing exported arm chain around its own joints before sampling the palm contact. */
final class GoddessRushPose {
    private static float ease(float t,float a,float b){return GoddessRush.ease(t,a,b);}
    private static Vector3f joint(Matrix4f mat){return mat.getTranslation(new Vector3f());}
    private static Matrix4f around(Vector3f point,Quaternionf rotation){
        return new Matrix4f().translate(point).rotate(rotation).translate(-point.x,-point.y,-point.z);
    }
    private static void rotate(Matrix4f[] m,int start,int end,Matrix4f op){
        for(int i=start;i<=end;i++)m[i].mulLocal(op);
    }
    static void pose(EncounterMob boss,Matrix4f[] m,float partial,Vec3 palmCenter){
        var attack=boss.goddess.attacks;var rush=attack.rush;
        float t=attack.age(partial);
        boolean left=rush.arm()==1;
        int start=left?5:19, wrist=start+2, end=start+13;
        int g=rush.grip(),s=left?1:-1;
        float held=g<0?0:t-g;
        float lower=ease(t,2,26)*(1-(g<0?ease(t,45,68):ease(held,6,32)));
        float wind=ease(t,8,24)*(1-ease(t,26,40));
        float lift=g<0?0:ease(held,6,32)*(1-ease(held,38,49));
        float sling=g<0?0:ease(held,34,46)*(1-ease(held,48,69));
        // Hips trail the shoulder in the dive. The unsupported side rolls out of the way.
        var torso=around(joint(m[2]),new Quaternionf().rotateY(s*wind*.24F+s*sling*.38F)
                .rotateZ(s*(wind*.22F-lift*.12F)).rotateX(-1.65F*lower+.14F*lift-.36F*sling));
        rotate(m,0,m.length-1,torso);
        var head=around(joint(m[0]),new Quaternionf().rotateX(.30F*lower+.24F*lift).rotateY(s*.22F*wind));
        m[0].mulLocal(head);m[33].mulLocal(head);m[34].mulLocal(head);
        var swing=around(joint(m[start]),new Quaternionf().rotateZ(-s*.22F*lower+s*lift*.68F)
                .rotateX(-.54F*lower+.90F*lift+1.08F*sling));
        rotate(m,start,end,swing);
        if(lift>0){
            // The forearm folds toward the face without stretching its distance to the shoulder.
            var elbow=joint(m[start+1]);
            var towardFace=joint(m[0]).sub(elbow).add(s*.42F,-.78F,-1.25F).normalize();
            var forearm=joint(m[wrist]).sub(elbow).normalize();
            var bend=new Quaternionf().slerp(new Quaternionf().rotationTo(forearm,towardFace),lift);
            rotate(m,start+1,end,around(elbow,bend));
        }
        float clamp=g<0?0:ease(held,0,9)*(1-ease(held,41,47));
        float spread=ease(t,12,26)*(1-ease(t,35,46));
        for(int finger=0;finger<4;finger++){
            int base=start+3+finger*3,last=base+(finger==3?1:2);
            for(int j=base;j<=last;j++){
                float fold=(j==base?.46F:.61F)*clamp;
                var curl=new Quaternionf().rotateX(-fold+.12F*spread).rotateZ(s*(finger==3?-.54F:.22F)*clamp);
                rotate(m,j,last,around(joint(m[j]),curl));
            }
        }
        // Register the actual bind-local palm to the committed low sweep; it then leaves that lane as the elbow lifts.
        float registration=ease(t,0,26)*(1-(g<0?ease(t,45,68):ease(held,5,31)));
        if(registration>0){
            Vec3 origin=boss.level().isClientSide?boss.getPosition(partial):boss.position();
            Vec3 desired=rush.lane(t).subtract(origin).yRot((float)Math.toRadians(rush.bodyYaw(t)-180)).scale(.5);
            var palm=m[wrist].transformPosition(palmCenter.toVector3f());
            var offset=desired.subtract(new Vec3(palm)).scale(registration);
            for(var matrix:m)matrix.translateLocal((float)offset.x,(float)offset.y,(float)offset.z);
        }
    }
    private GoddessRushPose(){}
}
