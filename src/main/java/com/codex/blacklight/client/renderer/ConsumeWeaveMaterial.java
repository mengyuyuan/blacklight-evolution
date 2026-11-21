package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.slf4j.Logger;

/** Normal-mapped consume tissue with the P1 cloth shader's per-pixel coverage equation. */
@EventBusSubscriber(modid = "blacklight", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ConsumeWeaveMaterial extends RenderType {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SHADER = new ResourceLocation("blacklight", "consume_body");
    private static final ResourceLocation CORD_SHADER=new ResourceLocation("blacklight","consume_carry_cord");
    private static ShaderInstance shader,cordShader;
    private static ResourceLocation texture(String name){
        return new ResourceLocation("blacklight","textures/entity/consume_body_"+name+".png");
    }
    private static final ResourceLocation BAKED=texture("weave");
    private static final ResourceLocation CORD_TEXTURE=texture("cord");
    private static final RenderType MESH=material("blacklight_consume_body",texture("diffuse"),()->shader,false);
    private static final RenderType SLING=material("blacklight_consume_carry",CORD_TEXTURE,()->cordShader,false);
    private static final RenderType RECEIVER=material("blacklight_consume_receiver",texture("diffuse"),()->shader,true);
    private static final RenderType BAKED_RECEIVER=create("blacklight_consume_receiver_flat",DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,1536,false,true,CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(BAKED,false,false)).setCullState(NO_CULL)
                    .setDepthTestState(EQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY).createCompositeState(false));
    private static RenderType material(String name,ResourceLocation diffuse,java.util.function.Supplier<ShaderInstance> source,boolean decal){
        return create(name,DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,65536,true,true,CompositeState.builder()
                    .setShaderState(new ShaderStateShard(source))
                    .setTextureState(MultiTextureStateShard.builder().add(diffuse,true,false)
                            .add(BAKED,false,false).add(BAKED,false,false) // Overlay and lightmap occupy units 1 and 2.
                            .add(texture("normal"),true,false).add(texture("reflection"),true,false).build())
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                    .setDepthTestState(decal?EQUAL_DEPTH_TEST:LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(decal?COLOR_WRITE:COLOR_DEPTH_WRITE)
                    .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(!decal));

    }

    private ConsumeWeaveMaterial() {
        super("blacklight_consume_body_factory", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 1536, false, false, () -> { }, () -> { });
    }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) {
        // ShaderInstance belongs to Minecraft's reload lifecycle. Only retain
        // the registered load callback's instance, never the one it replaces.
        shader = null;cordShader=null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADER,
                    DefaultVertexFormat.NEW_ENTITY), loaded -> shader = loaded);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),CORD_SHADER,
                    DefaultVertexFormat.NEW_ENTITY),loaded->cordShader=loaded);
        } catch (IOException error) {
            LOGGER.error("Could not load consume body shader; using baked consume material", error);
        }
    }

    public static RenderType sling(){
        return cordShader!=null&&!com.codex.blacklight.client.ShaderPackCompat.active()
                ?SLING:RenderType.entityCutoutNoCull(CORD_TEXTURE);
    }
    public static RenderType mesh(){
        return shader!=null&&!com.codex.blacklight.client.ShaderPackCompat.active()
                ?MESH:RenderType.entityTranslucent(BAKED);
    }
    public static RenderType receiver(){
        return shader!=null&&!com.codex.blacklight.client.ShaderPackCompat.active()?RECEIVER:BAKED_RECEIVER;
    }
}
