package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.model.CommonInfectedModel;
import com.codex.blacklight.combat.GoddessHandPose;
import com.codex.blacklight.entity.EncounterMob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Civilian spines interlock along each articulated link. No extra living actors are rendered or ticked. */
final class GoddessHandRenderer {
    private final CommonInfectedModel body=new CommonInfectedModel(CommonInfectedModel.layer().bakeRoot());
    private int jointLight(EncounterMob boss,Vec3 point){
        int local=net.minecraft.client.renderer.LevelRenderer.getLightColor(boss.level(),net.minecraft.core.BlockPos.containing(point));
        return net.minecraft.client.renderer.LightTexture.pack(Math.max(10,net.minecraft.client.renderer.LightTexture.block(local)),
                net.minecraft.client.renderer.LightTexture.sky(local));
    }
    void draw(EncounterMob boss,float partial,PoseStack poses,MultiBufferSource buffers){
        var hand=boss.goddess.attacks.hand;
        var shape=hand.pose(partial);if(shape==null)return;
        var origin=boss.getPosition(partial);
        var skin=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumeTendrilSegmentMesh.TEXTURE));
        for(var link:shape.links()){
            var a=link.base().subtract(origin);var b=link.end().subtract(origin);
            var center=link.middle().subtract(origin);
            double girth=link.width();
            ConsumeTendrilSegmentMesh.draw(poses,skin,jointLight(boss,link.middle()),new Vec3[]{a,center,b},
                    new double[]{girth*.60,girth*.85,girth*.53},link.right(),0xffaa8790);
        }
        int skins=hand.skins();
        for(int appearance=0;appearance<4;appearance++){
            var people=buffers.getBuffer(RenderType.entityCutoutNoCull(CommonInfectedRenderer.skin(appearance)));
            for(int index=0;index<shape.links().size();index++){
                var link=shape.links().get(index);
                var axis=link.up();var edge=link.right();var normal=link.normal();
                double girth=link.width();
                var center=link.middle().subtract(origin);
                int jointLight=jointLight(boss,link.middle());
                int count=link.slot()==GoddessHandPose.WRIST?6:link.slot()==GoddessHandPose.PALM?5:3;
                double length=link.base().distanceTo(link.end());
                for(int row=0;row<count;row++){
                    if(((skins>>>(((index+row)%8)*2))&3)!=appearance)continue;
                    double theta=2*Math.PI*row/count+.39*index;
                    double phase=theta+.6*Math.sin(index+row);
                    var at=center.add(edge.scale(Math.cos(theta)*girth*.75)).add(normal.scale(Math.sin(theta)*link.depth()*.77))
                            .add(axis.scale((row%2==0?1:-1)*Math.max(0,length-2.0)*.24));
                    poses.pushPose();
                    poses.translate(at.x,at.y,at.z);
                    poses.mulPose(new Quaternionf().rotationTo(0,1,0,(float)axis.x,(float)axis.y,(float)axis.z));
                    poses.mulPose(Axis.YP.rotation((float)phase));poses.mulPose(Axis.ZP.rotation((float)(.20*Math.sin(phase))));
                    float wide=(float)Math.min(1.08,girth*1.48);
                    poses.scale(wide,(float)Math.min(length*.70,1.45),wide);
                    poses.mulPose(Axis.ZP.rotationDegrees(180));poses.translate(0,-.65,0);
                    float pull=(float)(.11*Math.sin((boss.tickCount+partial)*.19+phase));
                    body.body.xRot=.16F;body.head.xRot=-.25F+pull;body.head.yRot=(float)Math.sin(phase)*.7F;
                    body.hat.copyFrom(body.head);
                    body.rightArm.xRot=-1.64F+pull;body.leftArm.xRot=-2.11F-pull;
                    body.rightArm.zRot=.54F;body.leftArm.zRot=-.51F;
                    body.rightLeg.xRot=-.48F-pull;body.leftLeg.xRot=.16F+pull;
                    body.rightLeg.getChild("shin").xRot=.62F;body.leftLeg.getChild("shin").xRot=.34F;
                    body.rightArm.getChild("forearm").xRot=-.74F;body.leftArm.getChild("forearm").xRot=-.51F;
                    body.renderToBuffer(poses,people,jointLight,OverlayTexture.NO_OVERLAY);
                    poses.popPose();
                }
            }
        }
    }
}
