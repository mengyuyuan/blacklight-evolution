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
        return net.minecraft.client.renderer.LightTexture.pack(Math.max(13,net.minecraft.client.renderer.LightTexture.block(local)),
                net.minecraft.client.renderer.LightTexture.sky(local));
    }
    private void controlLines(EncounterMob boss,float partial,Vec3 origin,GoddessHandPose shape,PoseStack poses,
                              com.mojang.blaze3d.vertex.VertexConsumer out,boolean glow){
        var controller=boss.hurtRegions(partial).get((boss.goddess.removed()&1)==0?5:9);
        if(!controller.boxes().isEmpty()){
            Vec3 start=controller.bounds().getCenter().subtract(origin);
            var wrist=shape.links().get(0);
            Vec3 end=wrist.base().subtract(origin);
            Vec3 axis=end.subtract(start).normalize();
            Vec3 edge=axis.cross(new Vec3(0,1,0));
            if(edge.lengthSqr()<.01)edge=new Vec3(1,0,0);else edge=edge.normalize();
            Vec3 normal=axis.cross(edge).normalize();
            float t=boss.goddess.attacks.hand.melee()?boss.goddess.attacks.hand.controlTime(partial):boss.tickCount+partial;
            double arc=Math.min(2.7,start.distanceTo(end)*.095);
            for(int strand=0;strand<3;strand++){
                Vec3[] line=new Vec3[13];
                double[] radii=new double[line.length];
                for(int j=0;j<line.length;j++){
                    double u=j/(line.length-1.), taper=Math.sin(Math.PI*u);
                    double angle=strand*Math.PI*2/3+u*7-t*.075;
                    double pulse=.5+.5*Math.cos(10*u-.46*t);
                    line[j]=start.lerp(end,u).add(edge.scale(arc*taper*Math.cos(angle)))
                            .add(normal.scale(arc*taper*Math.sin(angle))).add(0,-1.45*taper,0);
                    double width=.11+.22*taper*(.32+.68*pulse);
                    if(glow)line[j]=line[j].add(edge.scale(width*.91));
                    radii[j]=glow?.045+.11*pulse*pulse:width;
                }
                ConsumeTendrilSegmentMesh.draw(poses,out,glow?0xf000f0:jointLight(boss,wrist.middle()),line,radii,edge,
                        glow?0xc6f4825d:0xffbb8993,!glow);
            }
        }
    }
    void draw(EncounterMob boss,float partial,PoseStack poses,MultiBufferSource buffers){
        var hand=boss.goddess.attacks.hand;
        var shape=hand.pose(partial);if(shape==null)return;
        var origin=boss.getPosition(partial);
        var skin=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumeTendrilSegmentMesh.TEXTURE));
        boolean conducting=boss.goddess.attacks.move()==com.codex.blacklight.combat.GoddessAttacks.Move.HAND;
        if(conducting)controlLines(boss,partial,origin,shape,poses,skin,false);
        for(var link:shape.links()){
            var a=link.base().subtract(origin);var b=link.end().subtract(origin);
            var center=link.middle().subtract(origin);
            double girth=link.width();
            ConsumeTendrilSegmentMesh.draw(poses,skin,jointLight(boss,link.middle()),new Vec3[]{a,center,b},
                    new double[]{girth*.60,girth*.85,girth*.53},link.right(),0xffaa8790);
        }
        if(conducting){
            var glow=buffers.getBuffer(RenderType.entityTranslucentEmissive(ConsumedMobVisuals.WHITE));
            controlLines(boss,partial,origin,shape,poses,glow,true);
            float phase=(boss.tickCount+partial)*.30F;
            for(var link:shape.links()){
                var a=link.base().subtract(origin);var b=link.end().subtract(origin);
                for(int side:new int[]{-1,1}){
                    var rim=link.normal().scale(link.depth()*1.18*side);
                    Vec3[] vein={a.lerp(b,.04).add(rim.scale(.63)),
                            a.lerp(b,.46).add(rim).add(link.right().scale(link.width()*.47)),
                            a.lerp(b,.83).add(rim).subtract(link.right().scale(link.width()*.25)),
                            b.add(rim.scale(.72))};
                    double pulse=.5+.5*Math.cos(phase-link.slot()*.69);
                    double width=(link.slot()<14?.22:.14)*(.55+.45*pulse);
                    ConsumeTendrilSegmentMesh.draw(poses,glow,0xf000f0,vein,
                            new double[]{width*.40,width,width*.72,width*.30},link.right(),0xeff39773,false);
                }
            }
        }
        if(conducting && hand.melee()){
            float time=hand.controlTime(partial);
            if(time>=11 && time<=20 || time>=42 && time<=49){
                var earlier=hand.pose(partial-2.5F);
                if(earlier!=null && earlier.links().size()==shape.links().size()){
                    for(int i=4;i<shape.links().size();i+=3){
                        var oldTip=earlier.links().get(i+2).end().subtract(origin);
                        var tip=shape.links().get(i+2).end().subtract(origin);
                        if(oldTip.distanceToSqr(tip)>.8)
                            GoddessCastRenderer.coil(poses,buffers,0xf000f0,oldTip,tip,0,1,.55F,.12F,i,.81F,2);
                    }
                }
            }
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
