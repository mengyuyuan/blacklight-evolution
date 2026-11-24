package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The carrier joints move before the crown, fingertips, wound colonies and receiver patches are sampled. */
public final class GoddessCastPose {
    public static boolean active(GoddessAttacks.Move move) {
        if(GoddessTerrainCast.active(move)) return true;
        return switch (move) {
            case VACUOLE, NEEDLE_RAIN, WALL_SPEARS, BLOOD_SURF, SEEKING_ORBS, TENDRIL_CRUSH, PACK_POUNCE -> true;
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

    public static Vec3 lips(EncounterMob boss,float partial) {
        var skull=GoddessRig.root(boss,partial).mul(GoddessRig.pose(boss,partial)[0]);
        float open=GoddessTerrainCast.jawOpening(boss.goddess.attacks.age(partial));
        var seam=new Vector3f(-.157537F+.0215F*open,.476313F-.155F*open,-.921028F-.046F*open);
        return boss.getPosition(partial).add(new Vec3(skull.transformPosition(seam)));
    }

    static void apply(EncounterMob boss, Matrix4f[] pose, float partial) {
        if(GoddessTerrainCast.active(boss.goddess.attacks.move())) { GoddessTerrainPose.apply(boss,pose,partial); return; }
        var attack=boss.goddess.attacks;
        float t=attack.age(partial);
        var move=attack.move();
        float end=switch(move){case WALL_SPEARS -> GoddessAttacks.SPEARS_END; case VACUOLE -> 62; case BLOOD_SURF -> GoddessAttacks.SURF_END; case SEEKING_ORBS -> 52; case TENDRIL_CRUSH -> 51; case PACK_POUNCE -> 47; default -> GoddessAttacks.RAIN_END;};
        float engage=beat(t,0,move==GoddessAttacks.Move.VACUOLE?17:10,end-10,end);
        var focus=move==GoddessAttacks.Move.PACK_POUNCE?attack.commands.commandLook(t):attack.center();
        var target=focus.subtract(boss.getPosition(partial)).toVector3f();
        new Matrix4f(GoddessRig.root(boss,partial)).invert().transformPosition(target);
        var line=new Vector3f(target).sub(pose[0].getTranslation(new Vector3f()));
        if(line.lengthSquared()<.0001F) return;
        float yaw=(float)Math.atan2(-line.x,-line.z);
        float pitch=(float)Math.atan2(line.y,Math.hypot(line.x,line.z));
        float draw=0, strike=0, roll=0, crown=0, fold=0;
        switch(move){
            case SEEKING_ORBS -> {
                boolean second=!attack.commands.secondBroken();
                draw=beat(t,0,8,11,16)+(second?beat(t,17,27,30,36):0);
                strike=beat(t,11,14,15,21)+(second?beat(t,30,34,35,45):0);
                roll=(1-2*GoddessRush.ease(t,15,22))*(.24F*draw-.34F*strike);
                crown=-.35F*draw+.52F*strike;
            }
            case TENDRIL_CRUSH -> {
                draw=beat(t,0,10,23,32);
                fold=beat(t,22,32,35,48);
                strike=beat(t,28,32,34,43);
                crown=-.36F*draw+.42F*fold;
            }
            case PACK_POUNCE -> {
                draw=beat(t,0,13,19,26);
                strike=beat(t,20,26,27,39);
                roll=-.32F*draw+.46F*strike;
                crown=-.80F*draw+.78F*strike;
            }
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
                strike=beat(t,9,12,14,23)+beat(t,34,38,40,GoddessAttacks.SURF_END);
                roll=(1-2*GoddessRush.ease(t,18,27))*(.34F*draw-.30F*strike);
                crown=-.30F*draw+.35F*strike;
            }
            case WALL_SPEARS -> {
                draw=beat(t,0,22,25,30);
                strike=beat(t,25,28,35,51);
                fold=beat(t,48,60,66,GoddessAttacks.SPEARS_END);
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
        GoddessGesture.cast(boss,pose,t,draw,strike,fold);
    }

    private static Matrix4f about(Vector3f point,float yaw,float pitch,float roll) {
        return new Matrix4f().translate(point).rotateY(yaw).rotateX(pitch).rotateZ(roll)
                .translate(-point.x,-point.y,-point.z);
    }
    private GoddessCastPose(){}
}
