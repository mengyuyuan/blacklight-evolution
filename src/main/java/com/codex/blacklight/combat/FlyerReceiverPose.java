package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The native horizontal receiver rig, shared by carry placement and skin rendering. */
public final class FlyerReceiverPose {
    private FlyerReceiverPose() {}
    public static float[][] sample(EncounterMob mob,String key,float time,float partial) {
        var table=FlyerMotion.table();
        // Native resting wing folds retain their own shoulder/elbow/wrist chain.
        // Human forearm projection sends these metre-long membranes through the floor.
        var pose=table.sample("idle",time*table.ticks("idle"),true);var projection=ConsumePlacementTrack.projection(key,time);
        if(projection.isEmpty())return pose;
        var worlds=new Quaternionf[table.bones.length];
        for(int i=0;i<table.bones.length;i++) {
            int joint=switch(table.bones[i]){case "torso"->1;case "head"->0;case "right_leg"->4;case "left_leg"->5;default->-1;};
            if(i==0){pose[i][0]=pose[i][1]=pose[i][2]=0;worlds[i]=new Quaternionf();}
            else if(joint>=0) {
                var q=ConsumePlacementTrack.quaternion(projection.get(),7+joint*4);q.set(-q.x,q.y,-q.z,q.w);worlds[i]=new Quaternionf(q);
                var parent=worlds[table.parents[i]];var local=parent==null?q:new Quaternionf(parent).invert().mul(q);
                var a=local.getEulerAnglesZYX(new Vector3f());pose[i][0]=a.x;pose[i][1]=a.y;pose[i][2]=a.z;
            }
        }
        // The head faces the carrier, leaving the horizontal thorax and wings
        // in front of the player. Human receivers can face the same direction;
        // doing that here pulled the thorax through the carrier to reach its head.
        var visual=com.codex.blacklight.registry.ModAttachments.peekData(mob,com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY);
        float turn=GrappleMotion.active(visual)?1:Math.max(0,1-time/.2F);
        turn=turn*turn*(3-2*turn);pose[0][1]=(float)Math.PI*turn;
        return pose;
    }
    /** Actor-local right/up/forward, using the current native head/thorax mass center. */
    public static Vec3 center(EncounterMob mob,String key,float time,String bone) {
        var table=FlyerMotion.table();var pose=sample(mob,key,time,0);var transforms=table.transforms(pose);
        var mass=FlyerContact.masses().stream().filter(m->table.bones[m.bone()].equals(bone)).findFirst().orElseThrow();
        var p=transforms[mass.bone()].transformPosition(mass.center().toVector3f());return new Vec3(p.x,p.y+.01,-p.z);
    }
    /** Wings are not the solid torso: their span must not push a held head out of reach. */
    public static double torsoRadius() {
        int torso=java.util.Arrays.asList(FlyerMotion.table().bones).indexOf("torso");
        var boxes=FlyerContact.worldBoxes(torso,new org.joml.Matrix4f());var bounds=boxes.get(0).bounds;
        for(var box:boxes)bounds=bounds.minmax(box.bounds);
        return Math.min(bounds.getXsize(),bounds.getZsize())*.5;
    }
}
