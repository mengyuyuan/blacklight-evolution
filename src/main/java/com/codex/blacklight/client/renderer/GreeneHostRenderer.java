package com.codex.blacklight.client.renderer;

import com.codex.blacklight.world.GreeneHost;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class GreeneHostRenderer extends MobRenderer<GreeneHost,GreeneHostRenderer.HostModel> {
    private static final ResourceLocation SKIN=new ResourceLocation("blacklight","textures/entity/greene_host.png");
    public GreeneHostRenderer(EntityRendererProvider.Context ctx){super(ctx,new HostModel(ctx),.32F);}
    @Override public ResourceLocation getTextureLocation(GreeneHost host){return SKIN;}

    private static float ease(float time,float start,float duration){
        float t=Mth.clamp((time-start)/duration,0,1);
        return t*t*(3-2*t);
    }
    public static final class HostModel extends HumanoidModel<GreeneHost> {
        HostModel(EntityRendererProvider.Context ctx){ super(ctx.bakeLayer(ModelLayers.PLAYER)); }
        @Override public void setupAnim(GreeneHost host,float walk,float speed,float time,float yaw,float pitch) {
            rootPartsReset();
            super.setupAnim(host,walk,speed,time,yaw,pitch);
            float t=host.age(time-host.tickCount);
            float partial=time-host.tickCount;
            float attention=host.attention(partial);
            float pulse=Mth.sin(time*.11F);
            boolean spent=host.stage()==GreeneHost.SPENT;
            boolean changing=host.stage()!=GreeneHost.WAITING && !spent;
            float kneel=spent?1:1-attention*.88F;
            float flinch=0, arch=0;
            if(changing){
                float recoil=ease(t,34,10);
                kneel=Mth.lerp(ease(t,0,8),kneel,1)-recoil*.9F+ease(t,57,18)*.75F;
                flinch=recoil*(1-ease(t,55,17));
                arch=ease(t,72,22);
            }
            if(spent && com.codex.blacklight.combat.ConsumeControl.protectedEntity(host)) kneel=0;
            body.y+=kneel*8; body.xRot+=kneel*.78F-arch*.5F+ .022F*pulse;
            body.zRot+=kneel*.17F+flinch*.15F;
            head.y+=kneel*10; head.z-=kneel*5;
            head.xRot+=kneel*.57F-flinch*.40F-arch*.58F;
            head.yRot+=host.glance(partial)+(spent?-.18F:0);
            hat.copyFrom(head);
            leftArm.y+=kneel*9;rightArm.y+=kneel*10;
            leftArm.z-=kneel*3;rightArm.z-=kneel*6;
            leftArm.xRot-=kneel*.80F+flinch*1.25F;
            rightArm.xRot-=kneel*.22F+flinch*1.4F+.08F*pulse*kneel;
            rightArm.zRot+=kneel*.12F+flinch*.38F;
            leftArm.zRot-=kneel*.27F+arch*.61F;
            leftLeg.xRot-=kneel*1.35F;rightLeg.xRot+=kneel*.64F;
            leftLeg.y+=kneel*4;rightLeg.y+=kneel*3;
            if(spent && kneel>0){
                float push=(float)Math.pow(Math.max(0,Mth.sin(time*.065F)),4);
                body.y-=push*2;head.y-=push*2;head.zRot+=.26F;leftArm.xRot-=push*.35F;
                hat.copyFrom(head);
            }
        }
        private void rootPartsReset(){
            head.resetPose();hat.resetPose();body.resetPose();leftArm.resetPose();rightArm.resetPose();leftLeg.resetPose();rightLeg.resetPose();
        }
    }
    @Override public void render(GreeneHost host,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        boolean changing=host.stage()==GreeneHost.CHANGING||host.stage()==GreeneHost.BLOCKED||host.stage()==GreeneHost.OVERLAP;
        float t=host.age(partial);
        if(!changing || t<GreeneHost.BREAKOUT+18) super.render(host,yaw,partial,stack,buffers,light);
        if(host.stage()==GreeneHost.WAITING || (changing && t<64)){
            stack.pushPose();stack.mulPose(Axis.YP.rotationDegrees(-host.getYRot()));
            var out=buffers.getBuffer(RenderType.entityCutoutNoCull(BiomassGeometry.TEXTURE));
            float pulse=(1+Mth.sin((host.tickCount+partial)*.11F))*.5F;
            double radius=.046+.035*pulse*(1-host.attention(partial));
            for(int side:new int[]{-1,1}){
                Vec3[] vein={new Vec3(.20,.04,.12),new Vec3(side*.55,.052,.43),new Vec3(side*1.06,.04,.25),
                        new Vec3(side*1.48,.034,.78),new Vec3(side*2.09,.02,.67)};
                BiomassGeometry.strand(stack,out,light,vein,radius,.009);
            }
            stack.popPose();
        }
        if(!changing) return;
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(-host.getYRot()));
        float fade=1-ease(t,GreeneHost.BREAKOUT+13,GreeneHost.CROSSFADE-13);
        for(int side : new int[]{-1,1}){
            float reach=ease(t,side<0?52:62,24)*fade;
            if(reach<=.002F) continue;
            var out=buffers.getBuffer(RenderType.entityCutoutNoCull(BiomassGeometry.TEXTURE));
            Vec3 root=new Vec3(side*.15,.68,-.12);
            Vec3[] chain={root,root.add(side*.38*reach,.85*reach,-.08),
                    root.add(side*1.04*reach,1.04*reach,-.22),root.add(side*1.60*reach,.23*reach,-.05),
                    root.add(side*1.92*reach,-.70*reach,.18)};
            BiomassGeometry.strand(stack,out,light,chain,.15*reach,.035*reach);
            float lift=ease(t,side<0?70:80,9)*fade;
            if(lift>.01F) {
                stack.pushPose();stack.translate(side*1.94,.06+.17*lift,.13);
                stack.mulPose(Axis.ZP.rotationDegrees(side*27*lift));stack.scale(.93F,.28F,.73F);
                stack.translate(-.5,-.5,-.5);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),stack,buffers,light,OverlayTexture.NO_OVERLAY);
                stack.popPose();
            }
        }
        float growth=ease(t,76,32)*fade;
        if(growth>.003F) {
            var out=buffers.getBuffer(RenderType.entityCutoutNoCull(BiomassGeometry.TEXTURE));
            BiomassGeometry.ellipsoid(stack,out,light,new Vec3(0,1.05*growth,-.25),.87*growth,1.42*growth,.75*growth,t,0xff81383a);
            for(int i=0;i<7;i++){
                double a=i*2*Math.PI/7;
                var base=new Vec3(Math.sin(a)*growth,.06,-.25+Math.cos(a)*growth);
                var middle=new Vec3(Math.sin(a)*.79*growth,1.64*growth,-.25+Math.cos(a)*.67*growth);
                var tip=new Vec3(Math.sin(a)*.23*growth,2.46*growth,-.25+Math.cos(a)*.18*growth);
                BiomassGeometry.strand(stack,out,light,new Vec3[]{base,middle,tip},.15*growth,.025*growth);
            }
        }
        stack.popPose();
    }
    @Override public boolean shouldRender(GreeneHost host,net.minecraft.client.renderer.culling.Frustum camera,double x,double y,double z){
        return host.shouldRender(x,y,z)&&camera.isVisible(host.getBoundingBox().inflate(2.5,1.8,2.5));
    }
}
