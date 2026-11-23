package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.world.WeaveCell;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** The same two planted contacts drive the skin rig and the transport fibers. */
public final class WeaveBrace {
    private WeaveBrace() {}

    private static final class PalmOffsets {
        static final Vector3f[] CONTACTS=contacts();
        static Vector3f[] contacts() {
            var rig=PlayerMotionController.skeleton();
            var rest=rig.getPoseMatrices(new BlacklightPose());
            var contacts=new Vector3f[2];
            for(int side:new int[]{-1,1}) {
                var wrist=rig.searchJointByName(side<0?"Tool_L":"Tool_R");
                // Center of the distal skin face in the bind pose. The tool socket is inside the forearm.
                var contact=new Vector3f(side*.36F,.751F,0);
                contacts[side<0?0:1]=new org.joml.Matrix4f(rest[wrist.getId()]).invert().transformPosition(contact);
            }
            return contacts;
        }
    }

    public static double ease(double t) {
        t=Math.max(0,Math.min(1,t));
        return t*t*(3-2*t);
    }

    public static double grip(double time,int side) {
        double delay=side<0?1:3;
        return ease((time-delay)/12) * (1-ease((time-85-delay)/8));
    }

    public static Vec3 wrist(WeaveCell cell, int side, double time) {
        Vec3 f=cell.inward(), right=new Vec3(-f.z,0,f.x);
        Vec3 rest=cell.stationFeet().add(right.scale(.32*side)).add(f.scale(.05)).add(0,.76,0);
        double grip=grip(time,side);
        return rest.lerp(cell.palm(side),grip).add(0,.14*Math.sin(Math.PI*grip),0);
    }

    public static Vec3 elbow(WeaveCell cell, int side, double time) {
        Vec3 f=cell.inward(), right=new Vec3(-f.z,0,f.x);
        double g=grip(time,side);
        return cell.stationFeet().add(right.scale(side*(.34+.075*g)))
                .add(f.scale(.10+.08*g)).add(0,1.15+.045*g+.015*g*Math.sin(time*.38+side),0);
    }

    public static BlacklightPose pose(AbstractClientPlayer player,WeaveCell cell,float partial) {
        double time=player.level().getGameTime()+partial-cell.started();
        float weight=(float)grip(time,-1);
        var pose=new BlacklightPose();
        pose.orElseEmpty("Root").translation().y=-.10F*weight;
        pose.orElseEmpty("Chest").rotation().rotateX(-.38F*weight);
        pose.orElseEmpty("Head").rotation().rotateX(-.13F*weight);
        pose.orElseEmpty("Thigh_L").rotation().rotateX(.14F*weight);
        pose.orElseEmpty("Leg_L").rotation().rotateX(-.23F*weight);
        pose.orElseEmpty("Thigh_R").rotation().rotateX(-.12F*weight);
        var rig=PlayerMotionController.skeleton();
        var f=cell.inward();
        var right=new Vec3(-f.z,0,f.x);
        float scale=.9375F*com.codex.blacklight.client.model.MercerPlayerMesh.displayScale(player,partial)
                *(com.codex.blacklight.client.model.MercerArmorMesh.active(player)?1F:com.codex.blacklight.client.model.BlockArmorModel.get().bodyScale(player,partial));
        for(int side:new int[]{-1,1}) {
            String limb=side<0?"L":"R";
            pose.orElseEmpty("Arm_"+limb).rotation().rotateZ(side*.16F*weight);
            var relative=wrist(cell,side,time).subtract(player.getPosition(partial));
            var goal=new Vector3f((float)relative.dot(right),(float)relative.y,(float)-relative.dot(f)).div(scale);
            BlacklightBoneIk.reachTwoBone(rig,pose,limb,goal,PalmOffsets.CONTACTS[side<0?0:1]);
        }
        return pose;
    }
}
