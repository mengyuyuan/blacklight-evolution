package com.codex.blacklight.client.model;

import com.codex.blacklight.animation.*;
import com.codex.blacklight.client.animation.BlacklightRenderPose;
import com.codex.blacklight.client.renderer.BlacklightBoneRendering;
import com.codex.blacklight.client.renderer.ConsumeAnatomy;
import com.mojang.blaze3d.vertex.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Original CPU skin mesh. Subdivided standard skin surfaces stay connected through the authored pose. */
public final class BlacklightPlayerModel extends PlayerModel<AbstractClientPlayer> {
    private record SkinPiece(String bone, ModelPart visibility, float x, float y, float z,
                             float w, float h, float d, float u, float v, float from, float to, float inflation, int anatomy) { }
    private final List<SkinPiece> pieces = new ArrayList<>();
    private Matrix4f[] matrices;
    private final Matrix4f[] bindInverse;
    private Matrix4f[] skinMatrices;
    private Matrix3f[] skinNormals;
    private boolean rightBiological,leftBiological;
    private AbstractClientPlayer renderedPlayer;
    private float renderPartial;
    public BlacklightPlayerModel(ModelPart root, boolean slim) {
        super(root, slim);
        var skeleton = PlayerMotionController.skeleton();
        bindInverse = skeleton.getPoseMatrices(new BlacklightPose());
        for (var matrix : bindInverse) matrix.invert();
        add("Head", head, -4,-8,-4,8,8,8,0,0,0,8,0,ConsumeAnatomy.HEAD);
        add("Head", hat, -4,-8,-4,8,8,8,32,0,0,8,.5F,ConsumeAnatomy.HEAD);
        add("neck", head, -1.5F,-1,-1.5F,3,2.5F,3,8,8,0,2.5F,0,ConsumeAnatomy.HEAD);
        split("Chest","Torso",body,-4,0,-2,8,12,4,16,16,0,ConsumeAnatomy.BODY);
        split("Chest","Torso",jacket,-4,0,-2,8,12,4,16,32,.25F,ConsumeAnatomy.BODY);
        float armWidth=slim?3:4;
        split("Arm_R","Hand_R",rightArm,-5-armWidth+1,0,-2,armWidth,12,4,40,16,0,ConsumeAnatomy.RIGHT_ARM);
        split("Arm_L","Hand_L",leftArm,5-1,0,-2,armWidth,12,4,32,48,0,ConsumeAnatomy.LEFT_ARM);
        split("Arm_R","Hand_R",rightSleeve,-5-armWidth+1,0,-2,armWidth,12,4,40,32,.25F,ConsumeAnatomy.RIGHT_ARM);
        split("Arm_L","Hand_L",leftSleeve,5-1,0,-2,armWidth,12,4,48,48,.25F,ConsumeAnatomy.LEFT_ARM);
        split("Thigh_R","Leg_R",rightLeg,-3.9F,12,-2,4,12,4,0,16,0,ConsumeAnatomy.RIGHT_LEG);
        split("Thigh_L","Leg_L",leftLeg,-.1F,12,-2,4,12,4,16,48,0,ConsumeAnatomy.LEFT_LEG);
        split("Thigh_R","Leg_R",rightPants,-3.9F,12,-2,4,12,4,0,32,.25F,ConsumeAnatomy.RIGHT_LEG);
        split("Thigh_L","Leg_L",leftPants,-.1F,12,-2,4,12,4,0,48,.25F,ConsumeAnatomy.LEFT_LEG);
    }
    private void add(String bone,ModelPart visibility,float x,float y,float z,float w,float h,float d,float u,float v,float from,float to,float inflation,int anatomy) {
        pieces.add(new SkinPiece(bone,visibility,x,y,z,w,h,d,u,v,from,to,inflation,anatomy));
    }
    private void split(String upper,String lower,ModelPart visible,float x,float y,float z,float w,float h,float d,float u,float v,float inflation,int anatomy) {
        add(upper,visible,x,y,z,w,h,d,u,v,0,h/2,inflation,anatomy);
        add(lower,visible,x,y,z,w,h,d,u,v,h/2,h,inflation,anatomy);
    }
    @Override public void prepareMobModel(AbstractClientPlayer player,float swing,float amount,float partial) {
        // This is the exact interpolation interval passed to this entity render,
        // including paused/frozen rendering; the global timer can be different.
        renderPartial=partial;
        super.prepareMobModel(player,swing,amount,partial);
    }
    @Override public void setupAnim(AbstractClientPlayer player,float swing,float amount,float age,float yaw,float pitch) {
        renderedPlayer=player;
        super.setupAnim(player,swing,amount,age,yaw,pitch);
        float partial=renderPartial;
        matrices=PlayerMotionController.skeleton().getPoseMatrices(BlacklightRenderPose.sample(player,partial));
        // Ordinary equipment layers copy ModelPart transforms. Project the same
        // authored pose into their native frame so equipment follows the body.
        project(head,"Head");project(hat,"Head");project(body,"Chest");project(jacket,"Chest");
        project(rightArm,"Arm_R");project(rightSleeve,"Arm_R");project(leftArm,"Arm_L");project(leftSleeve,"Arm_L");
        project(rightLeg,"Thigh_R");project(rightPants,"Thigh_R");project(leftLeg,"Thigh_L");project(leftPants,"Thigh_L");
        var armor=BlockArmorModel.get();
        if(armor.covers(player,"head",partial)){head.visible=false;hat.visible=false;}
        if(armor.covers(player,"torso",partial)){body.visible=false;jacket.visible=false;}
        if(armor.covers(player,"left_arm",partial)){leftArm.visible=false;leftSleeve.visible=false;}
        if(armor.covers(player,"right_arm",partial)){rightArm.visible=false;rightSleeve.visible=false;}
        if(armor.covers(player,"left_leg",partial)){leftLeg.visible=false;leftPants.visible=false;}
        if(armor.covers(player,"right_leg",partial)){rightLeg.visible=false;rightPants.visible=false;}
        var state=player.getData(com.codex.blacklight.registry.ModAttachments.PLAYER_STATE);
        var form=state.getActivePower();
        rightBiological=leftBiological=false;
        if(state.isPrototypeMode()) {
            var morph=com.codex.blacklight.client.renderer.WeaponMorphTracker.sample(player,partial);
            boolean preview=com.codex.blacklight.client.tuning.ClawTransformSettings.preview()
                    &&(form==com.codex.blacklight.power.PowerForm.NONE||form==com.codex.blacklight.power.PowerForm.CLAWS);
            rightBiological=preview||morph.coverage(false)>=.72F;
            leftBiological=preview||morph.coverage(true)>=.72F;
            if(rightBiological){rightArm.visible=false;rightSleeve.visible=false;}
            if(leftBiological){leftArm.visible=false;leftSleeve.visible=false;}
            if(armor.covers(player,"right_arm",partial))rightBiological=false;
            if(armor.covers(player,"left_arm",partial))leftBiological=false;
        }

    }
    private void project(ModelPart part,String name) {
        var bone=PlayerMotionController.skeleton().searchJointByName(name);
        var bind=part.getInitialPose();
        var conversion=new Matrix4f().translation(0,1.501F,0).scale(-1,-1,1);
        var matrix=new Matrix4f(conversion).invert().mul(matrices[bone.getId()]).mul(bindInverse[bone.getId()]).mul(conversion)
                .translate(bind.x/16,bind.y/16,bind.z/16);
        var rotation=matrix.getUnnormalizedRotation(new Quaternionf()).normalize().getEulerAnglesZYX(new Vector3f());
        part.x=matrix.m30()*16;part.y=matrix.m31()*16;part.z=matrix.m32()*16;
        part.xRot=rotation.x;part.yRot=rotation.y;part.zRot=rotation.z;
        var scale=matrix.getScale(new Vector3f());part.xScale=scale.x;part.yScale=scale.y;part.zScale=scale.z;
    }
    public Matrix4f[] matrices() { return matrices; }
    @Override public void renderToBuffer(PoseStack stack,VertexConsumer out,int light,int overlay,int color) {
        if(matrices==null){super.renderToBuffer(stack,out,light,overlay,color);return;}
        skinMatrices=new Matrix4f[matrices.length];skinNormals=new Matrix3f[matrices.length];
        for(int i=0;i<matrices.length;i++) {
            skinMatrices[i]=new Matrix4f(matrices[i]).mul(bindInverse[i]);
            skinNormals[i]=new Matrix3f(skinMatrices[i]).invert().transpose();
        }
        stack.pushPose();BlacklightBoneRendering.skeletonFrame(stack);
        try {
            if(renderedPlayer!=null&&renderedPlayer.getData(com.codex.blacklight.registry.ModAttachments.DEVASTATOR_VISUAL).kind()>=0)
                com.codex.blacklight.client.renderer.DevastatorEffectRenderer.captureBody(renderedPlayer,stack,matrices);
            for(var piece:pieces) {
                SkinPiece drawn=piece;
                if(!piece.visibility.visible) {
                    boolean bridge=piece.anatomy==ConsumeAnatomy.RIGHT_ARM?rightBiological:piece.anatomy==ConsumeAnatomy.LEFT_ARM&&leftBiological;
                    if(!bridge||piece.from!=0)continue;
                    // The shoulder band shares its top vertices with the chest.
                    // Its lower vertices overlap the biological shoulder cap.
                    drawn=new SkinPiece(piece.bone,piece.visibility,piece.x,piece.y,piece.z,piece.w,piece.h,piece.d,piece.u,piece.v,0,2,piece.inflation,piece.anatomy);
                }
                if(out instanceof ConsumeAnatomy.Consumer anatomy)anatomy.anatomy(piece.anatomy);
                cube(drawn,stack,out,light,overlay,color);
            }
        }finally{stack.popPose();}
    }
    @Override public void translateToHand(HumanoidArm arm,PoseStack stack) {
        if(matrices==null){super.translateToHand(arm,stack);return;}
        var bone=PlayerMotionController.skeleton().searchJointByName(arm==HumanoidArm.RIGHT?"Tool_R":"Tool_L");
        BlacklightBoneRendering.skeletonFrame(stack);
        BlacklightBoneRendering.apply(stack,matrices[bone.getId()]);
        // ItemInHandLayer expects a vanilla arm frame at the wrist.
        stack.scale(-1,-1,1);
    }
    private void cube(SkinPiece p,PoseStack stack,VertexConsumer out,int light,int overlay,int color) {
        float a=p.inflation;
        float x0=(p.x-a)/16,x1=(p.x+p.w+a)/16,z0=(p.z-a)/16,z1=(p.z+p.d+a)/16;
        float y0=(p.y+p.from-(p.from==0?a:0))/16,y1=(p.y+p.to+(p.to==p.h?a:0))/16;
        float row=p.v+p.d;
        face(p,stack,out,light,overlay,color,new float[][]{{x0,y0,z1},{x0,y0,z0},{x0,y1,z0},{x0,y1,z1}},-1,0,0,p.u,row+p.from,p.u+p.d,row+p.to);
        face(p,stack,out,light,overlay,color,new float[][]{{x0,y0,z0},{x1,y0,z0},{x1,y1,z0},{x0,y1,z0}},0,0,-1,p.u+p.d,row+p.from,p.u+p.d+p.w,row+p.to);
        face(p,stack,out,light,overlay,color,new float[][]{{x1,y0,z0},{x1,y0,z1},{x1,y1,z1},{x1,y1,z0}},1,0,0,p.u+p.d+p.w,row+p.from,p.u+2*p.d+p.w,row+p.to);
        face(p,stack,out,light,overlay,color,new float[][]{{x1,y0,z1},{x0,y0,z1},{x0,y1,z1},{x1,y1,z1}},0,0,1,p.u+2*p.d+p.w,row+p.from,p.u+2*p.d+2*p.w,row+p.to);
        face(p,stack,out,light,overlay,color,new float[][]{{x0,y0,z1},{x1,y0,z1},{x1,y0,z0},{x0,y0,z0}},0,-1,0,p.u+p.d,p.v,p.u+p.d+p.w,p.v+p.d);
        face(p,stack,out,light,overlay,color,new float[][]{{x0,y1,z0},{x1,y1,z0},{x1,y1,z1},{x0,y1,z1}},0,1,0,p.u+p.d+p.w,p.v,p.u+p.d+2*p.w,p.v+p.d);
    }
    private void face(SkinPiece piece,PoseStack stack,VertexConsumer out,int light,int overlay,int color,float[][] points,float nx,float ny,float nz,float u0,float v0,float u1,float v1) {
        int columns=1;
        int rows=piece.anatomy==ConsumeAnatomy.HEAD&&!piece.bone.equals("neck")?1:Math.max(1,(int)Math.ceil(Math.abs(points[3][1]-points[0][1])*16));
        for(int row=0;row<rows;row++)for(int column=0;column<columns;column++) {
            float a=column/(float)columns,b=(column+1)/(float)columns,c=row/(float)rows,d=(row+1)/(float)rows;
            vertex(piece,stack,out,light,overlay,color,points,nx,ny,nz,a,d,u0+(u1-u0)*a,v0+(v1-v0)*d);
            vertex(piece,stack,out,light,overlay,color,points,nx,ny,nz,b,d,u0+(u1-u0)*b,v0+(v1-v0)*d);
            vertex(piece,stack,out,light,overlay,color,points,nx,ny,nz,b,c,u0+(u1-u0)*b,v0+(v1-v0)*c);
            vertex(piece,stack,out,light,overlay,color,points,nx,ny,nz,a,c,u0+(u1-u0)*a,v0+(v1-v0)*c);
        }
    }
    private void vertex(SkinPiece piece,PoseStack stack,VertexConsumer out,int light,int overlay,int color,float[][] points,float nx,float ny,float nz,float u,float v,float textureU,float textureV) {
        Vector3f p=new Vector3f(points[0]).lerp(new Vector3f(points[1]),u);
        p.lerp(new Vector3f(points[3]).lerp(new Vector3f(points[2]),u),v);
        // Clothing inflation uses the base skin's weight, keeping copied layers attached.
        float modelY=piece.y+Math.clamp(p.y*16-piece.y,piece.from,piece.to);
        var weight=BlacklightSkinWeights.sample(piece.anatomy,modelY,piece.bone.equals("neck"));
        var skeleton=PlayerMotionController.skeleton();
        int first=skeleton.searchJointByName(weight.first()).getId(),second=skeleton.searchJointByName(weight.second()).getId();
        p.set(-p.x,1.501F-p.y,p.z);
        var position=skinMatrices[first].transformPosition(new Vector3f(p)).lerp(skinMatrices[second].transformPosition(new Vector3f(p)),weight.secondWeight());
        var firstNormal=skinNormals[first].transform(new Vector3f(-nx,-ny,nz));
        var normal=new Vector3f(firstNormal).lerp(skinNormals[second].transform(new Vector3f(-nx,-ny,nz)),weight.secondWeight());
        if(normal.lengthSquared()<1e-8F)normal.set(firstNormal);
        normal.normalize();
        out.addVertex(stack.last(),position.x,position.y,position.z).setColor(color).setUv(textureU/64,textureV/64).setOverlay(overlay).setLight(light).setNormal(stack.last(),normal.x,normal.y,normal.z);
    }
}
