package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.slf4j.Logger;

/** One entity pass for wet tissue. UV0.x carries wetness, not texture coordinates.
 * The geometry still uses the native arm and body transforms. */
@EventBusSubscriber(modid = "blacklight", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LiquidBiomassMaterial extends RenderType {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SHADER = new ResourceLocation("blacklight", "liquid_biomass");
    private static ShaderInstance shader;
    private static final Function<ResourceLocation, RenderType> MESH = Util.memoize(texture -> create(
            "blacklight_liquid_biomass", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            1536, true, true, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader))
                    .setTextureState(new TextureStateShard(texture, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true)));

    private LiquidBiomassMaterial() {
        super("blacklight_liquid_biomass_factory", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 1536, false, false, () -> { }, () -> { });
    }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) {
        // ShaderInstance belongs to Minecraft's reload lifecycle. Only retain
        // the registered load callback's instance, never the one it replaces.
        shader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADER,
                    DefaultVertexFormat.NEW_ENTITY), loaded -> shader = loaded);
        } catch (IOException error) {
            LOGGER.error("Could not load liquid biomass material; using the native entity material", error);
        }
    }

    /** Use a white texture; vertex RGB supplies tissue color and UV0.x wetness.
     * Call only for an active liquid frame so stable weapons keep their material. */
    public static RenderType mesh(ResourceLocation texture) {
        return ready() ? MESH.apply(texture) : RenderType.entityCutoutNoCull(texture);
    }

    public static boolean ready() {
        return shader != null && !com.codex.blacklight.client.ShaderPackCompat.active();
    }
}
