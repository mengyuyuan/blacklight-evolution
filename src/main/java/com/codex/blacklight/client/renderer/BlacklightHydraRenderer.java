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
        float scale=e.visualScale();
        if(e.planted()&&e.growth()<1){
            // Original P1 hydras erupt from the ground. A growing gateway pushes up out of the soil
            // instead of sitting in an egg sac: only the skull and upper neck clear the surface.
            float g=e.growth();float pulse=1+.03F*(float)Math.sin((e.tickCount+partial)*(e.action()==1?.45F:.12F));
            pose.translate(0,-(1-g)*6.2F*scale,0);pose.scale(scale*pulse,scale,scale*pulse);
            super.render(e,yaw,partial,pose,buffers,light);
        }else{
            pose.scale(scale,scale,scale);
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
        super.renderRecursively(pose,entity,bone,type,buffers,out,reRender,partial,light,overlay,color);
    }
}
