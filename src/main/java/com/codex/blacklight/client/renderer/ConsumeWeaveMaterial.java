package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.slf4j.Logger;

/** Normal-mapped consume tissue with the P1 cloth shader's per-pixel coverage equation. */
@EventBusSubscriber(modid = "blacklight", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ConsumeWeaveMaterial extends RenderType {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath("blacklight", "consume_body");
    private static ShaderInstance shader;
    private static ResourceLocation texture(String name){
        return ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/consume_body_"+name+".png");
    }
    private static final ResourceLocation BAKED=texture("weave");
    private static final RenderType MESH=create("blacklight_consume_body",DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,65536,true,true,CompositeState.builder()
                    .setShaderState(new ShaderStateShard(()->shader))
                    .setTextureState(MultiTextureStateShard.builder().add(texture("diffuse"),true,false)
                            .add(BAKED,false,false).add(BAKED,false,false) // Overlay and lightmap occupy units 1 and 2.
                            .add(texture("normal"),true,false).add(texture("reflection"),true,false).build())
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(true));

    private ConsumeWeaveMaterial() {
        super("blacklight_consume_body_factory", DefaultVertexFormat.NEW_ENTITY,
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
            LOGGER.error("Could not load consume body shader; using baked consume material", error);
        }
    }

    public static RenderType mesh(){
        return shader!=null&&!com.codex.blacklight.client.ShaderPackCompat.active()
                ?MESH:RenderType.entityTranslucent(BAKED);
    }
}
