package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Wind-up moves through pelvis, sternum, neck and crown before a visible release. */
final class GoddessTerrainPose {
    private static float beat(float age,float begin,float peak,float hold,float end) {
        return GoddessRush.ease(age,begin,peak)*(1-GoddessRush.ease(age,hold,end));
    }
    static void apply(EncounterMob boss, Matrix4f[] pose,float partial) {
        var attack=boss.goddess.attacks;
        float age=attack.age(partial);
        float inhale,shout,sweep;
        switch(attack.move()) {
            case SONIC_CRY -> {
                inhale=beat(age,0,10,11,15)+beat(age,26,40,41,44);
                shout=beat(age,13,15,18,23)+beat(age,41,44,54,65);
                sweep=0;
            }
            case SPIKE_FIELD -> {
                inhale=beat(age,0,12,13,18);
                shout=beat(age,13,18,42,63);
                sweep=.22F*(float)Math.sin((age-18)*.17)*shout;
            }
            case INFECTION_WAVE -> {
                inhale=beat(age,0,21,22,26);
                shout=beat(age,22,26,35,63);
                sweep=0;
            }
            default -> {return;}
        }
        boolean voice=attack.move()==GoddessAttacks.Move.SONIC_CRY;
        boolean wide=attack.move()==GoddessAttacks.Move.INFECTION_WAVE;
        var chest=pose[1].getTranslation(new Vector3f());
        float bend=voice?-.23F*inhale+.12F*shout:wide?.50F*inhale-.31F*shout:-.34F*inhale+.50F*shout;
        var torso=new Matrix4f().translate(chest).rotateX(bend).rotateZ(sweep)
                .scale(1+.06F*inhale,1+.025F*inhale,1+.08F*inhale).translate(-chest.x,-chest.y,-chest.z);
        for(int j=0;j<pose.length;j++)if(j<2 || j>=5)pose[j].mulLocal(torso);
        var head=pose[0].getTranslation(new Vector3f());
        var neck=new Matrix4f().translate(head);
        if(voice){
            var target=attack.terrain.ready()?attack.terrain.aim():attack.center();
            var local=target.subtract(boss.getPosition(partial)).toVector3f();
            new Matrix4f(GoddessRig.root(boss,partial)).invert().transformPosition(local);
            var desired=local.sub(head);
            if(desired.lengthSquared()>.001){
                var front=pose[0].transformDirection(new Vector3f(.42101F,-.087156F,-.902859F)).normalize();
                float focus=beat(age,0,8,64,76);
                neck.rotate(new Quaternionf().slerp(new Quaternionf().rotationTo(front,desired.normalize()),focus));
            }
        }
        neck.rotateX(-.28F*inhale+(wide?-.21F:.06F)*shout).translate(-head.x,-head.y,-head.z);
        pose[0].mulLocal(neck);pose[33].mulLocal(neck);pose[34].mulLocal(neck);
        GoddessGesture.terrain(boss,pose,age,inhale,shout);
        var at=pose[33].getTranslation(new Vector3f());
        var crown=new Matrix4f().translate(at).rotateX(-.22F*inhale+.48F*shout)
                .rotateZ(.12F*(float)Math.sin(age*.36)*shout).translate(-at.x,-at.y,-at.z);
        pose[33].mulLocal(crown);pose[34].mulLocal(crown);
    }
    private GoddessTerrainPose(){}
}
