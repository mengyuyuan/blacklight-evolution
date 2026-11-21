package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.model.GroundSpikeModel;
import com.codex.blacklight.entity.GroundSpikeEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class GroundSpikeRenderer extends GeoEntityRenderer<GroundSpikeEntity> {
    @Override
    public void render(GroundSpikeEntity entity,float yaw,float partial,
                       com.mojang.blaze3d.vertex.PoseStack stack,
                       net.minecraft.client.renderer.MultiBufferSource buffers,int light) {
        int size=entity.playerShardSize();
        if (size==0) {
            super.render(entity,yaw,partial,stack,buffers,light);
            return;
        }
        String model=switch(size) { case 1 -> "skill_groundspike_small"; case 2 -> "skill_groundspike_mid"; default -> "skill_groundspike_large"; };
        double age=entity.ageTicks(partial)/20.0;
        double growth=com.codex.blacklight.combat.DevastatorEffectTimeline.spike(age,0,Math.max(1,entity.riseTicks())/20.0,
                GroundSpikeEntity.ERUPTION_DURATION_TICKS/20.0-.2);
        AuthoredDevastatorMesh.playerShard(stack,buffers,light,model,(float)Math.toRadians(entity.getYRot()),growth);
    }

    @Override
    public boolean shouldRender(GroundSpikeEntity entity,net.minecraft.client.renderer.culling.Frustum frustum,
                                double x,double y,double z) {
        if (entity.playerShardSize()==0) return super.shouldRender(entity,frustum,x,y,z);
        return entity.shouldRender(x,y,z) && frustum.isVisible(entity.getBoundingBox().inflate(1,0,1).expandTowards(0,8,0));
    }

    public GroundSpikeRenderer(EntityRendererProvider.Context context) {
        super(context, new GroundSpikeModel());
        withScale(1.65F);
        shadowRadius = 0.0F;
    }
}
