package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The carrier joints move before the crown, fingertips, wound colonies and receiver patches are sampled. */
public final class GoddessCastPose {
    public static boolean active(GoddessAttacks.Move move) {
        return switch (move) {
            case VACUOLE, NEEDLE_RAIN, WALL_SPEARS, BLOOD_SURF -> true;
            default -> false;
        };
    }

    private static float beat(float t, float start, float peak, float hold, float end) {
        return GoddessRush.ease(t,start,peak) * (1-GoddessRush.ease(t,hold,end));
    }

    // Midpoint of the two eyeCore vertices in the player's fitted portrait, in the skull joint's coordinates.
    private static final Vector3f EYES=new Vector3f(-.149738F,1.058815F,-.937716F);
    // The face is fitted to the head with -25 degrees of yaw and 5 degrees of pitch in Blender.
    private static final Vector3f FRONT=new Vector3f(.421010F,-.087156F,-.902859F);

    public static Vec3 face(EncounterMob boss, float partial) {
        var skull=GoddessRig.root(boss,partial).mul(GoddessRig.pose(boss,partial)[0]);
        return boss.getPosition(partial).add(new Vec3(skull.transformPosition(new Vector3f(EYES))));
    }

    static void apply(EncounterMob boss, Matrix4f[] pose, float partial) {
        var attack=boss.goddess.attacks;
        float t=attack.age(partial);
        var move=attack.move();
        float end=switch(move){case WALL_SPEARS -> 112; case VACUOLE -> 62; default -> 55;};
        float engage=beat(t,0,move==GoddessAttacks.Move.VACUOLE?17:10,end-10,end);
        var target=attack.center().subtract(boss.getPosition(partial)).toVector3f();
        new Matrix4f(GoddessRig.root(boss,partial)).invert().transformPosition(target);
        var line=new Vector3f(target).sub(pose[0].getTranslation(new Vector3f()));
        if(line.lengthSquared()<.0001F) return;
        float yaw=(float)Math.atan2(-line.x,-line.z);
        float pitch=(float)Math.atan2(line.y,Math.hypot(line.x,line.z));
        float draw=0, strike=0, roll=0, crown=0, fold=0;
        switch(move){
            case VACUOLE -> {
                draw=beat(t,2,16,19,27);
                fold=beat(t,22,45,51,57);
                strike=beat(t,49,52,53,61);
                crown=-.28F*draw+.18F*fold-.5F*strike;
            }
            case NEEDLE_RAIN -> {
                draw=beat(t,0,8,10,16);
                for(int i=0;i<5;i++){
                    float hit=12+6*i;
                    float stroke=beat(t,hit-4,hit,hit+.4F,hit+3);
                    strike+=stroke;
                    roll+=stroke*(i%2==0?1:-1)*.21F;
                }
                crown=-.56F*draw+.40F*strike;
            }
            case BLOOD_SURF -> {
                draw=beat(t,0,8,9,13)+beat(t,19,31,33,39);
                strike=beat(t,9,12,14,23)+beat(t,34,38,40,51);
                roll=(t<24?1:-1)*(.34F*draw-.30F*strike);
                crown=-.30F*draw+.35F*strike;
            }
            case WALL_SPEARS -> {
                draw=beat(t,0,22,25,30);
                strike=beat(t,25,28,35,51);
                fold=beat(t,52,65,71,93);
                crown=-.28F*draw+.30F*strike-.32F*fold;
            }
            default -> {return;}
        }
        var hips=pose[2].getTranslation(new Vector3f());
        var lower=about(hips,yaw*.54F*engage,pitch*.10F*engage,0);
        for(var bone:pose)bone.mulLocal(lower);
        var chest=pose[1].getTranslation(new Vector3f());
        float bend=pitch*.35F*engage+.19F*draw-.24F*strike-.13F*fold;
        var torso=about(chest,yaw*.30F*engage,bend,roll);
        for(int j=0;j<pose.length;j++) if(j<=1 || j>=5)pose[j].mulLocal(torso);
        var head=pose[0].getTranslation(new Vector3f());
        // Turn around the skull joint. A second, small correction accounts for the eyes' offset from the neck.
        for(int pass=0;pass<2;pass++){
            var eye=pose[0].transformPosition(new Vector3f(EYES));
            var sight=new Vector3f(target).sub(eye);
            if(sight.lengthSquared()<.0001F)break;
            var front=pose[0].transformDirection(new Vector3f(FRONT)).normalize();
            var correction=new org.joml.Quaternionf().rotationTo(front,sight.normalize());
            float weight=pass==0?engage:engage*engage;
            var neck=new Matrix4f().translate(head).rotate(new org.joml.Quaternionf().slerp(correction,weight))
                    .translate(-head.x,-head.y,-head.z);
            pose[0].mulLocal(neck);pose[33].mulLocal(neck);pose[34].mulLocal(neck);
        }
        var mane=pose[33].getTranslation(new Vector3f());
        var whip=about(mane,-roll*1.2F,crown,-roll*.62F);
        pose[33].mulLocal(whip);pose[34].mulLocal(whip);
        for(int side=0;side<2;side++){
            int first=side==0?5:19;
            float sign=side==0?1:-1;
            var shoulder=pose[first].getTranslation(new Vector3f());
            float reach=(move==GoddessAttacks.Move.BLOOD_SURF ? 1.23F:.56F)*draw;
            var gesture=about(shoulder,sign*(.23F*draw-.37F*fold),-reach+.66F*strike,
                    sign*(.47F*draw-.65F*strike-.47F*fold));
            for(int j=first;j<first+14;j++)pose[j].mulLocal(gesture);
            var wrist=pose[first+2].getTranslation(new Vector3f());
            var curl=about(wrist,-sign*.45F*fold,.33F*draw-.36F*strike,sign*.26F*fold);
            for(int j=first+2;j<first+14;j++)pose[j].mulLocal(curl);
        }
    }

    private static Matrix4f about(Vector3f point,float yaw,float pitch,float roll) {
        return new Matrix4f().translate(point).rotateY(yaw).rotateX(pitch).rotateZ(roll)
                .translate(-point.x,-point.y,-point.z);
    }
    private GoddessCastPose(){}
}
