package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BlacklightVirusVialEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The same baked vial as the inventory/hand model, oriented along its physical flight. */
public final class VirusVialRenderer extends EntityRenderer<BlacklightVirusVialEntity> {
    private final ItemRenderer items;
    public VirusVialRenderer(EntityRendererProvider.Context context){super(context);items=context.getItemRenderer();}
    @Override public ResourceLocation getTextureLocation(BlacklightVirusVialEntity vial){return TextureAtlas.LOCATION_BLOCKS;}
    @Override public void render(BlacklightVirusVialEntity vial,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        if(vial.tickCount<2&&entityRenderDispatcher.camera.getPosition().distanceToSqr(vial.getPosition(partial))<2.25)return;
        pose.pushPose();
        try{
            var velocity=vial.getDeltaMovement();
            if(velocity.lengthSqr()>1e-8){
                var axis=new Vector3f((float)velocity.x,(float)velocity.y,(float)velocity.z).normalize();
                pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0,1,0),axis));
            }
            pose.mulPose(Axis.YP.rotationDegrees((vial.tickCount+partial)*18));pose.scale(.5F,.5F,.5F);
            items.renderStatic(vial.getItem(),ItemDisplayContext.NONE,light,OverlayTexture.NO_OVERLAY,pose,buffers,vial.level(),vial.getId());
        }finally{pose.popPose();}
        super.render(vial,yaw,partial,pose,buffers,light);
    }
}
