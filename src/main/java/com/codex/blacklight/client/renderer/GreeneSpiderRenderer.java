package com.codex.blacklight.client.renderer;

import java.util.IdentityHashMap;
import software.bernie.geckolib.cache.object.GeoBone;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import com.codex.blacklight.entity.GreeneSpider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class GreeneSpiderRenderer extends GeoEntityRenderer<GreeneSpider> {
    private final IdentityHashMap<GeoBone,SpiderBoneMesh> meshes=new IdentityHashMap<>();
    private GeoBone meshRoot;
    private static final ResourceLocation BROOD_MESH = ResourceLocation.fromNamespaceAndPath("blacklight","geo/entity/greene_spider_brood.geo.json");
    private static final ResourceLocation BROOD = ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/greene_spider_brood.png");
    @Override public ResourceLocation getTextureLocation(GreeneSpider entity) { return BROOD; }

    public GreeneSpiderRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<GreeneSpider>(ResourceLocation.fromNamespaceAndPath("blacklight", "greene_spider")) {
            @Override public void setCustomAnimations(GreeneSpider entity,long id,software.bernie.geckolib.animation.AnimationState<GreeneSpider> state){
                super.setCustomAnimations(entity,id,state);
                var owner=entity.encounter;
                for(var joint:com.codex.blacklight.combat.SpiderAnatomy.PIVOTS.keySet()){
                    var bone=getAnimationProcessor().getBone(joint);if(bone==null)continue;
                    int limb=com.codex.blacklight.combat.SpiderAnatomy.leg(joint);
                    bone.setHidden(owner!=null && limb>=0 && joint.startsWith("tibia_") && owner.spider.severed(limb));
                    if(entity.fragment()>=0){bone.setRotX(0);bone.setRotY(0);bone.setRotZ(0);}
                    else if(owner!=null){
                        var rotation=owner.spider.legRotation(joint,state.getPartialTick());
                        if(rotation!=null){
                            var euler=rotation.getEulerAnglesZYX(new org.joml.Vector3f());
                            bone.setRotX(euler.x);bone.setRotY(euler.y);bone.setRotZ(euler.z);
                        }else{
                            bone.setRotX(owner.spider.flex(joint,state.getPartialTick()));bone.setRotY(0);
                            bone.setRotZ(owner.spider.fold(joint,state.getPartialTick()));
                        }
                    }
                }
            }
            @Override public ResourceLocation getModelResource(GreeneSpider entity) { return BROOD_MESH; }
        });
        shadowRadius = 2.2F;
    }

    @Override public void renderCubesOfBone(PoseStack stack,software.bernie.geckolib.cache.object.GeoBone bone,com.mojang.blaze3d.vertex.VertexConsumer out,int light,int overlay,int color){
        if(bone.getParent()==null && meshRoot!=bone){meshRoot=bone;meshes.clear();}
        var shell=getAnimatable();String name=bone.getName();
        if(shell.encounter!=null && name.startsWith("tibia_")
                && shell.encounter.spider.severed(com.codex.blacklight.combat.SpiderAnatomy.leg(name)))return;
        if(shell.fragment()>=0 && !name.equals(com.codex.blacklight.combat.SpiderAnatomy.shin(shell.fragment())))return;
        if(!bone.getCubes().isEmpty())meshes.computeIfAbsent(bone,SpiderBoneMesh::new).draw(stack.last(),out,light,overlay,color);
    }
    void drawMolt(com.codex.blacklight.entity.EncounterMob boss,float partial,PoseStack stack,
                  net.minecraft.client.renderer.MultiBufferSource buffers,int light,float[] matrices,
                  float seconds,float[] bends) {
        var shell=boss.greeneSpiderShell();
        var baked=getGeoModel().getBakedModel(BROOD_MESH);
        var root=baked.topLevelBones().get(0);
        if(root!=meshRoot){meshRoot=root;meshes.clear();}
        var type=getRenderType(shell,BROOD,buffers,partial);
        var out=buffers.getBuffer(type);
        float close=net.minecraft.util.Mth.clamp((seconds-11.65F)/(13.15F-11.65F),0,1);
        close=close*close*(3-2*close);
        if(close>=1)return;
        stack.pushPose();
        stack.translate(.6F*close,3.15F*close,-.65F*close);
        float scale=.9912102F*(1-close);
        stack.scale(scale,scale,scale);
        var transform=new org.joml.Matrix4f();
        var joints=com.codex.blacklight.combat.SpiderAnatomy.MOLT_JOINTS;
        for(int joint=0;joint<joints.size();joint++){
            var name=joints.get(joint);
            var bone=baked.getBone(name).orElseThrow();
            if(bone.getCubes().isEmpty())continue;
            int leg=com.codex.blacklight.combat.SpiderAnatomy.leg(name);
            boolean shin=name.startsWith("tibia_");
            if(shin && boss.spider.severed(leg))continue;
            transform.set(matrices,joint*16);
            if(leg>=0){
                rollJoint(transform,joints.get(13+leg*2),bends[leg*2]);
                if(shin)rollJoint(transform,name,bends[leg*2+1]);
            }
            stack.pushPose();
            stack.mulPose(transform);
            meshes.computeIfAbsent(bone,SpiderBoneMesh::new).draw(stack.last(),out,light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,0xffffffff);
            stack.popPose();
        }
        stack.popPose();
        // The translucent shroud draws immediately on vanilla; commit just this body's depth first.
        if(!com.codex.blacklight.client.ShaderPackCompat.active() && buffers instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource batch)
            batch.endBatch(type);
    }
    private static void rollJoint(org.joml.Matrix4f matrix,String joint,float angle){
        if(angle==0)return;
        var p=com.codex.blacklight.combat.SpiderAnatomy.PIVOTS.get(joint);
        matrix.translate((float)p.x,(float)p.y,(float)p.z).rotateZ(angle).translate((float)-p.x,(float)-p.y,(float)-p.z);
    }
    @Override protected void applyRotations(GreeneSpider entity, PoseStack stack, float age, float yaw, float partial, float scale) {
        if(entity.fragment()>=0){
            stack.mulPose(entity.fragmentPose(partial));
            stack.scale(1.9824204F,1.9824204F,1.9824204F);
            var center=com.codex.blacklight.combat.SpiderAnatomy.shinCenter(entity.fragment());stack.translate(-center.x,-center.y,-center.z);
        }else super.applyRotations(entity, stack, age, entity.getYRot(), partial, scale);
    }
}
