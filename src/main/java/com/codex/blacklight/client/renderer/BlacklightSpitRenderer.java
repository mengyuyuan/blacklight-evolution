package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BlacklightSpit;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** The spitter/hive dose is a small cohesive bolus, distinct from a boss's six-arm tumor. */
public final class BlacklightSpitRenderer extends EntityRenderer<BlacklightSpit> {
    public BlacklightSpitRenderer(EntityRendererProvider.Context c){super(c);}
    @Override public ResourceLocation getTextureLocation(BlacklightSpit e){return ConsumedMobVisuals.WHITE;}
    @Override public void render(BlacklightSpit e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));float pulse=1+(float)Math.sin((e.tickCount+partial)*.7)*.08F;InfectionCombatVisuals.flesh(pose,out,light,Vec3.ZERO,.18,pulse);
        var behind=e.getDeltaMovement().normalize().scale(-.4);ConsumedMobVisuals.tube(pose,out,light,Vec3.ZERO,behind,.028,0xff7d2939);super.render(e,yaw,partial,pose,buffers,light);
    }
}
