package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Soft spray retains terrain depth testing, but transparent feathering must not
 * write invisible depth and cut holes in the droplets rendered behind it. Uses
 * the vanilla entity shader so shader packs can still classify the material. */
public final class ImpactSprayMaterial extends RenderType {
    private static final RenderType SPRAY=create("blacklight_contact_spray",DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,8192,false,true,CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(ConsumedMobVisuals.WHITE,false,false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private ImpactSprayMaterial(){super("blacklight_contact_spray_factory",DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,8192,false,true,()->{},()->{});}
    public static RenderType type(){return SPRAY;}
}
