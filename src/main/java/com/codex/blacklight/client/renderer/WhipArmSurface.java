package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** A continuous tapered forearm with longitudinal tissue and a traveling hooked blade. */
public final class WhipArmSurface {
    private static com.codex.blacklight.client.model.ClawArmModel terminal;
    private WhipArmSurface(){}
    public static void render(PoseStack stack,VertexConsumer out,int light,List<Vec3> points,double radius){
        render(stack,out,light,points,radius,true);
    }
    public static void render(PoseStack stack,VertexConsumer out,int light,List<Vec3> points,double radius,boolean drawTip){
        render(stack,out,light,points,radius,drawTip,null,0);
    }
    public static void render(PoseStack stack,VertexConsumer out,int light,List<Vec3> points,double radius,boolean drawTip,
                              net.minecraft.client.player.AbstractClientPlayer consumer,float partial){
        if(points.size()<2)return;
        for(int i=1;i<points.size();i++){
            double t=(i-1.)/(points.size()-1),r=Math.max(.033,radius*(1-.68*t));
            Vec3 a=points.get(i-1),b=points.get(i),axis=b.subtract(a).normalize();
            ConsumedMobVisuals.tube(stack,out,light,a,b,r,0xff292429);
            Vec3 right=axis.cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<.1)right=new Vec3(1,0,0);
            Vec3 up=axis.cross(right).normalize();
            for(int strand=0;strand<3;strand++){
                double angle=strand*Math.PI*2/3+t*3;
                Vec3 offset=right.scale(Math.cos(angle)*r*.86).add(up.scale(Math.sin(angle)*r*.86));
                ConsumedMobVisuals.tube(stack,out,light,a.add(offset),b.add(offset),r*.18,strand==1?0xff622c2a:0xff47434a);
            }
        }
        if(!drawTip)return;
        Vec3 end=points.getLast(),axis=end.subtract(points.get(points.size()-2)).normalize();
        if(axis.lengthSqr()<.1)return;
        if(terminal==null)terminal=new com.codex.blacklight.client.model.ClawArmModel("whip_tip");
        stack.pushPose();stack.translate(end.x,end.y,end.z);
        stack.mulPose(new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0,1,0),new org.joml.Vector3f((float)axis.x,(float)axis.y,(float)axis.z)));
        try(var cutting=consumer==null?null:ConsumeBladeContact.begin(consumer,com.codex.blacklight.power.PowerForm.WHIPFIST,terminal,"Hand_R",stack,partial)){
            terminal.renderJoint("Hand_R",stack,out,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,1,1);
        }
        stack.popPose();
    }
    private static void blade(PoseStack stack,VertexConsumer out,int light,Vec3 root,Vec3 tip,Vec3 shoulder,int color){
        Vec3 normal=tip.subtract(root).cross(shoulder.subtract(root)).normalize();
        for(int side:new int[]{-1,1}){
            Vec3 ridge=root.lerp(shoulder,.55).add(normal.scale(.045*side));
            triangle(stack,out,light,root,tip,ridge,color);triangle(stack,out,light,tip,shoulder,ridge,0xff45414a);triangle(stack,out,light,shoulder,root,ridge,0xff302a30);
        }
    }
    private static void triangle(PoseStack stack,VertexConsumer out,int light,Vec3 a,Vec3 b,Vec3 c,int color){
        Vec3 n=b.subtract(a).cross(c.subtract(a)).normalize();
        for(Vec3 v:new Vec3[]{a,b,c,c})out.addVertex(stack.last(),(float)v.x,(float)v.y,(float)v.z).setColor(color).setUv(.5F,.5F)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(stack.last(),(float)n.x,(float)n.y,(float)n.z);
    }
}
