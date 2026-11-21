package com.codex.blacklight.client.renderer;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import com.codex.blacklight.entity.GreeneSpider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class GreeneSpiderRenderer extends GeoEntityRenderer<GreeneSpider> {
    private static final ResourceLocation BROOD_MESH = new ResourceLocation("blacklight","geo/entity/greene_spider_brood.geo.json");
    private static final ResourceLocation BROOD = new ResourceLocation("blacklight","textures/entity/greene_spider_brood.png");
    @Override public ResourceLocation getTextureLocation(GreeneSpider entity) { return BROOD; }

    public GreeneSpiderRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<GreeneSpider>(new ResourceLocation("blacklight", "greene_spider")) {
            @Override public void setCustomAnimations(GreeneSpider entity,long id,software.bernie.geckolib.core.animation.AnimationState<GreeneSpider> state){
                super.setCustomAnimations(entity,id,state);
                var owner=entity.encounter;
                for(var joint:com.codex.blacklight.combat.SpiderAnatomy.PIVOTS.keySet()){
                    var bone=getAnimationProcessor().getBone(joint);if(bone==null)continue;
                    int limb=com.codex.blacklight.combat.SpiderAnatomy.leg(joint);
                    bone.setHidden(owner!=null && limb>=0 && joint.startsWith("tibia_") && owner.spider.severed(limb));
                    if(owner!=null){
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

    @Override public void renderCubesOfBone(PoseStack stack,software.bernie.geckolib.cache.object.GeoBone bone,com.mojang.blaze3d.vertex.VertexConsumer out,int light,int overlay,float red,float green,float blue,float alpha){
        var shell=getAnimatable();String name=bone.getName();
        if(shell.fragment()>=0 && !name.equals(com.codex.blacklight.combat.SpiderAnatomy.shin(shell.fragment())))return;
        if(shell.husk && !(name.startsWith("fang_") || name.equals("carapace") || com.codex.blacklight.combat.SpiderAnatomy.leg(name)>=0))return;
        super.renderCubesOfBone(stack,bone,out,light,overlay,red,green,blue,alpha);
    }
    @Override protected void applyRotations(GreeneSpider entity, PoseStack stack, float age, float yaw, float partial) {
        if(entity.fragment()>=0){
            stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-entity.getYRot()));
            float falling=net.minecraft.util.Mth.clamp((entity.tickCount+partial)/16F,0,1);
            stack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((entity.fragment()<4?1:-1)*falling*70));
            stack.scale(1.9824204F,1.9824204F,1.9824204F);
            var center=com.codex.blacklight.combat.SpiderAnatomy.shinCenter(entity.fragment());stack.translate(-center.x,-center.y,-center.z);
        }else super.applyRotations(entity, stack, age, entity.getYRot(), partial);
    }
}
