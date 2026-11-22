package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Three feeding tendrils attach the floating arms and basal spiral to the plaza.
 * The renderer and melee/projectile receivers follow these same world-space segments. */
public final class GreeneFeedingRoots {
    private GreeneFeedingRoots(){}
    private static final Vec3[] SOCKETS={new Vec3(3.2,11,-.3),new Vec3(-5.2,11,-.1),new Vec3(-.5,2.4,.2)};
    private static final Vec3[] FEET={new Vec3(8.2,.25,-4),new Vec3(-9.6,.25,-2.3),new Vec3(.4,.25,8.4)};
    public static boolean active(EncounterMob boss){
        return boss.greeneOutbreak() && boss.greeneBattle().armourWave()>1 && boss.greeneStage()==GreeneBattleCycle.GUARDED;
    }
    public static List<Vec3> points(EncounterMob boss,int slot,float partial){
        var base=boss.getPosition(partial);
        float yaw=boss.skinYaw(partial);
        var pose=GreeneForm.GODDESS.transform(GreeneMotion.table().transforms(GreeneMotion.pose(boss,partial)),yaw);
        var top=new Vec3(pose.transformPosition(SOCKETS[slot].scale(GreeneForm.GODDESS_RIG_SCALE).toVector3f())).add(0,boss.greeneHover(partial),0);
        var foot=FEET[slot].multiply(GreeneForm.OUTBREAK_SCALE,1,GreeneForm.OUTBREAK_SCALE).yRot((float)Math.toRadians(180-yaw));
        var cross=new Vec3(-foot.z,0,foot.x).normalize();
        var out=new ArrayList<Vec3>(9);
        for(int i=0;i<=8;i++){
            double t=i/8.,bow=Math.sin(t*Math.PI);
            double sway=(1.25+.3*Math.sin((boss.tickCount+partial)*.06+t*5+slot*2))*bow;
            out.add(base.add(top.lerp(foot,t)).add(cross.scale(sway)).add(0,-2.5*bow,0));
        }
        return out;
    }
    public static double radius(int segment){return .23+.14*segment/7.;}
    public static List<HydraContact.Region> addReceivers(List<HydraContact.Region> skin,EncounterMob boss,float partial){
        var all=new ArrayList<>(skin);
        for(int slot=0;slot<3;slot++)if(!boss.broken(slot)){
            var r=skin.get(slot);var boxes=new ArrayList<>(r.boxes());var bounds=r.bounds();var path=points(boss,slot,partial);
            for(int i=0;i<8;i++){
                var a=path.get(i);var b=path.get(i+1);var up=b.subtract(a).normalize();
                var side=up.cross(new Vec3(0,0,1)).normalize();var front=side.cross(up);
                var box=HydraContact.affine(a.lerp(b,.5),new Vec3(radius(i),a.distanceTo(b)*.5+.05,radius(i)),side,up,front);
                boxes.add(box);bounds=bounds.minmax(box.bounds);
            }
            all.set(slot,new HydraContact.Region(r.bone(),List.copyOf(boxes),bounds));
        }
        return List.copyOf(all);
    }
}
