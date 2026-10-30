package com.codex.blacklight.client.renderer;
import com.codex.blacklight.client.model.BlacklightHydraModel;
import com.codex.blacklight.entity.BlacklightHydra;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class BlacklightHydraRenderer extends GeoEntityRenderer<BlacklightHydra> {
    private static final net.minecraft.resources.ResourceLocation SKIN = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blacklight", "textures/entity/hydra_connected.png");
    public BlacklightHydraRenderer(EntityRendererProvider.Context c){super(c,new BlacklightHydraModel());shadowRadius=1.2F;}
    @Override public void render(BlacklightHydra e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();
        if(e.planted()&&e.growth()<1){
            // A planted organ develops before the mature creature first breaks the surface.
            float size=.65F+e.growth()*.9F;
            float pulse=1+.035F*(float)Math.sin((e.tickCount+partial)*(e.action()==1?.45F:.12F));
            pose.scale(size*pulse,size/pulse,size*pulse);pose.translate(-.5,0,-.5);
            net.minecraft.client.Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                com.codex.blacklight.city.CityBlocks.SAC.get().defaultBlockState(),pose,buffers,light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        }else{
            float scale=e.visualScale();pose.scale(scale,scale,scale);
            super.render(e,yaw,partial,pose,buffers,light);
        }
        pose.popPose();
        var food=e.level().getEntity(e.feedTarget());
        if(food!=null&&e.action()==1){
            var out=buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
            var end=food.getPosition(partial).subtract(e.getPosition(partial)).add(0,food.getBbHeight()*.5,0);
            var start=new net.minecraft.world.phys.Vec3(0,.5,0);
            for(int strand=0;strand<3;strand++){
                var previous=start;double phase=strand*2.094;
                for(int i=1;i<=12;i++){
                    double t=i/12.0,arc=Math.sin(t*Math.PI)*.32;
                    var next=start.lerp(end,t).add(Math.cos(phase+t*9)*arc,Math.sin(phase+t*9)*arc,0);
                    ConsumedMobVisuals.tube(pose,out,light,previous,next,.07, strand==1?0xff80333d:0xff3c252b);previous=next;
                }
            }
        }
    }
    @Override public void renderRecursively(com.mojang.blaze3d.vertex.PoseStack pose, com.codex.blacklight.entity.BlacklightHydra entity,
            software.bernie.geckolib.cache.object.GeoBone bone, net.minecraft.client.renderer.RenderType type,
            net.minecraft.client.renderer.MultiBufferSource buffers, com.mojang.blaze3d.vertex.VertexConsumer out,
            boolean reRender,float partial,int light,int overlay,int color){
        if(bone.getParent()==null&&!bone.isHidden()){
            var mesh=com.codex.blacklight.client.model.ConnectedCreatureMesh.get("hydra");
            if(mesh!=null)mesh.render(bone,pose,buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(SKIN)),light,overlay,color);
        }
        super.renderRecursively(pose,entity,bone,type,buffers,out,reRender,partial,light,overlay,color);
    }
}
