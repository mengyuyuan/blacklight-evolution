package com.codex.blacklight.client.renderer;

import com.codex.blacklight.world.GreeneHost;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.combat.GrappleMotion;
import com.codex.blacklight.registry.ModAttachments;
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
    private static final ResourceLocation SKIN=ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/greene_host.png");
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
            if(host.stage()==GreeneHost.ESCAPING) {
                brace(1,ease(t,1,6)*(1-ease(t,GreeneHost.TEAR,8)),ease(t,GreeneHost.TEAR,14));
                return;
            }
            var visual=host.getData(ModAttachments.CONSUME_VISUAL);
            boolean held=GrappleMotion.greeneGrip(host,visual);
            float grip=held ? visual.stage()==ConsumeVisual.CAPTURE
                    ? ease(host.level().getGameTime()-visual.started()+partial,0,12) : 1 : 0;
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
            if(held) kneel=1-grip;
            else if(spent && com.codex.blacklight.combat.ConsumeControl.protectedEntity(host)) kneel=0;
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
                body.y-=push*2;head.y-=push*2;head.zRot+=.26F*kneel;leftArm.xRot-=push*.35F;
                hat.copyFrom(head);
            }
            if(held) brace(grip,0,0);
        }

        private void brace(float weight,float shove,float dive) {
            head.xRot+=weight*(.24F-.53F*shove+.52F*dive);
            head.yRot-=weight*.32F*dive;
            body.xRot+=weight*(.13F-.38F*shove+.70F*dive);
            rightArm.xRot-=weight*(.72F+1.03F*shove+.74F*dive);
            leftArm.xRot-=weight*(.54F+.38F*shove+.90F*dive);
            leftArm.zRot-=weight*.31F*shove;
            rightLeg.xRot-=weight*.56F*dive;
            leftLeg.xRot+=weight*.48F*dive;
            hat.copyFrom(head);
        }
        private void rootPartsReset(){
            head.resetPose();hat.resetPose();body.resetPose();leftArm.resetPose();rightArm.resetPose();leftLeg.resetPose();rightLeg.resetPose();
        }
    }
    @Override public void render(GreeneHost host,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        boolean changing=host.stage()==GreeneHost.CHANGING||host.stage()==GreeneHost.BLOCKED||host.stage()==GreeneHost.OVERLAP;
        float t=host.age(partial);
        shadowRadius=host.stage()==GreeneHost.ESCAPING ? .32F*(1-ease(t,GreeneHost.TEAR,8)) : .32F;
        if(host.stage()==GreeneHost.ESCAPING) {
            renderEscape(host,yaw,partial,stack,buffers,light,t);
            return;
        }
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
    private void renderEscape(GreeneHost host,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light,float t) {
        Vec3 drain=host.drainOffset();
        float slide=ease(t,GreeneHost.TEAR,18);
        float sink=ease(t,21,GreeneHost.SUBMERGED-21);
        Vec3 body=drain.scale(slide);
        double travel=Math.hypot(drain.x,drain.z);
        double heading=Math.toRadians(yaw);
        Vec3 forward=travel>.18 ? new Vec3(drain.x/travel,0,drain.z/travel) : new Vec3(-Math.sin(heading),0,Math.cos(heading));
        float dive=ease(t,GreeneHost.TEAR,11);
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(BiomassGeometry.TEXTURE));
        float opening=ease(t,6,10)*(1-ease(t,GreeneHost.SUBMERGED,6));
        if(opening>.005F) {
            Vec3 mouth=drain.add(0,.025,0);
            BiomassGeometry.ellipsoid(stack,out,light,mouth,.62*opening,.024,.49*opening,t,0xff180e16);
            for(int side:new int[]{-1,1}) {
                Vec3 lip=mouth.add(side*.47*opening,0,0);
                BiomassGeometry.strand(stack,out,light,new Vec3[]{mouth.add(0,0,-.46*opening),
                        lip.add(0,.055*opening,-.18*opening),lip.add(0,.035*opening,.22*opening),
                        mouth.add(.03*opening,0,.47*opening)},.065*opening,.018*opening);
            }
        }
        if(t<GreeneHost.SUBMERGED) {
            stack.pushPose();
            stack.translate(body.x,body.y-1.85*sink,body.z);
            // Lead with the reaching arm and head, then let the hips tip past the rim.
            stack.translate(0,.70,0);
            stack.mulPose(new org.joml.Quaternionf().rotationAxis((58*dive+62*sink)*Mth.DEG_TO_RAD,
                    (float)forward.z,0,(float)-forward.x));
            stack.translate(0,-.70,0);
            stack.scale(1-.37F*sink,1-.18F*sink,1-.37F*sink);
            super.render(host,yaw,partial,stack,buffers,light);
            stack.popPose();
        }
        // MobRenderer can switch buffer types; acquire the strand consumer again after drawing the skin.
        out=buffers.getBuffer(RenderType.entityCutoutNoCull(BiomassGeometry.TEXTURE));
        Vec3 origin=host.getPosition(partial);
        var witness=host.escapeWitness();
        if(witness!=null && t<GreeneHost.TEAR+4 && witness.distanceToSqr(host)<9) {
            Vec3 chest=witness.getPosition(partial).subtract(origin).add(0,witness.getBbHeight()*.65,0);
            for(int side:new int[]{-1,1}) {
                Vec3 right=new Vec3(Math.cos(Math.toRadians(yaw)),0,Math.sin(Math.toRadians(yaw))).scale(side);
                Vec3 start=chest.add(right.scale(.19));
                float pull=(1-ease(t,GreeneHost.TEAR,4))*ease(t,0,5);
                if(pull<=.01F) continue;
                Vec3 tip=start.lerp(body.add(right.scale(.20)).add(0,1.13,0),pull);
                Vec3 coil=start.lerp(tip,.64).add(right.scale(.18*(1-ease(t,4,7))+.04)).add(0,.05,0);
                BiomassGeometry.strand(stack,out,light,new Vec3[]{start,coil,tip},.066*pull,.021*pull);
            }
        }
        if(t>=GreeneHost.TEAR) {
            float drop=ease(t,GreeneHost.TEAR,13);
            double angle=Math.toRadians(yaw);
            Vec3 tissue=new Vec3(-Math.sin(angle)*.24*drop,1.03*(1-drop)+.13*drop+.32*Math.sin(Math.PI*drop),Math.cos(angle)*.24*drop);
            float beat=.92F+.08F*Mth.sin(t*.74F);
            BiomassGeometry.ellipsoid(stack,out,light,tissue,.14*beat,.10*beat,.12*beat,t,0xff8b3439);
        }
        float reach=ease(t,5,13);
        float vanish=ease(t,21,16);
        if(reach<=.01F || vanish>=1) return;
        for(int side:new int[]{-1,1}) {
            double a=Math.toRadians(yaw);
            var ridge=new Vec3(side*Math.cos(a),0,side*Math.sin(a));
            Vec3 root=body.add(ridge.scale(.14)).add(0,.43*(1-vanish),0);
            Vec3 elbow=root.lerp(drain,.35).add(ridge.scale(.24*(1-vanish))).add(0,.15*(1-vanish),0);
            Vec3 tip=root.lerp(drain.add(0,.025,0),reach);
            BiomassGeometry.strand(stack,out,light,new Vec3[]{root,elbow,tip},.094*reach*(1-vanish),.022*(1-vanish));
        }
    }
    @Override public boolean shouldRender(GreeneHost host,net.minecraft.client.renderer.culling.Frustum camera,double x,double y,double z){
        return host.shouldRender(x,y,z)&&camera.isVisible(host.stage()==GreeneHost.ESCAPING ? host.getBoundingBox().expandTowards(host.drainOffset()).inflate(1) : host.getBoundingBox().inflate(2.5,1.8,2.5));
    }
}
