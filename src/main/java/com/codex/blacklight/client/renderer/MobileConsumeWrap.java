package com.codex.blacklight.client.renderer;

import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** The remaining tissue winds from the arms into the moving actor's trunk after the grip opens. */
public final class MobileConsumeWrap {
    private MobileConsumeWrap() {}

    public static void render(AbstractClientPlayer player,PoseStack stack,MultiBufferSource buffers,
                              int light,Matrix4f[] poses,float partial){
        long start=ModAttachments.getData(player,ModAttachments.MOBILE_CONSUME_START);
        if(start<0||!player.isAlive()||player.isInvisible())return;
        double age=player.level().getGameTime()-start+partial;
        if(age<0||age>=16)return;
        var rig=PlayerMotionController.skeleton();
        Vec3 left=joint(poses,rig.armL.getId()),right=joint(poses,rig.armR.getId());
        Vec3 shoulder=left.lerp(right,.5),hips=joint(poses,rig.torso.getId());
        Vec3 up=shoulder.subtract(hips).normalize(),across=right.subtract(left).normalize();
        Vec3 forward=across.cross(up).normalize();
        if(up.lengthSqr()<.9||forward.lengthSqr()<.9)return;
        across=up.cross(forward).normalize();
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        double halfWidth=left.distanceTo(right)*.45,halfDepth=halfWidth*.57;
        double shrink=smooth((age-7)/9),appear=smooth((age+1)/3);
        for(int strand=0;strand<4;strand++){
            boolean onRight=(strand&1)==0;
            Vec3 hand=joint(poses,(onRight?rig.handR:rig.handL).getId());
            Vec3 arm=onRight?right:left;
            double side=onRight?1:-1,phase=strand*.53;
            Vec3[] points=new Vec3[20];double[] widths=new double[points.length];
            for(int j=0;j<points.length;j++){
                double u=j/(double)(points.length-1),t=shrink*.86+u*(1-shrink*.86);
                if(t<.28){
                    double a=t/.28;
                    points[j]=hand.lerp(arm,.25+.75*a).add(forward.scale(.075*Math.cos(a*2.8+phase)))
                            .add(across.scale(side*(.105-.06*a)));
                }else{
                    double a=(t-.28)/.72,angle=side*(a*(3.6+.35*strand))+phase*.23;
                    Vec3 center=shoulder.lerp(hips,.08+(strand<2?.77:.46)*a);
                    // Elliptical cross section follows the current shoulder/hip frame on turns and crouches.
                    Vec3 coil=center.add(across.scale(side*halfWidth*Math.cos(angle)))
                            .add(forward.scale(halfDepth*Math.sin(angle)+.015*Math.sin(3*a+age*.32)));
                    points[j]=arm.add(across.scale(side*.045)).add(forward.scale(.075*Math.cos(2.8+phase)))
                            .lerp(coil,Math.min(1,a/.18));
                }
                widths[j]=(.024+.012*(strand<2?1:0))*appear*(1-shrink)*(.16+.84*Math.sin(Math.PI*u))
                        *(1+.16*Math.sin(11*t-age*.42+phase));
            }
            ConsumeTendrils.sweep(stack,out,light,points,widths,forward,true);
        }
    }
    private static Vec3 joint(Matrix4f[] poses,int id){
        var m=poses[id];return new Vec3(m.m30(),m.m31(),m.m32());
    }
    private static double smooth(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
}
