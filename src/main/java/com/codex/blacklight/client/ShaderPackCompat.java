package com.codex.blacklight.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Shader pack (Iris/Oculus) compatibility.
 * <ul>
 * <li>Custom core shaders are unknown to the pack pipeline: callers fall back to vanilla render types.</li>
 * <li>Iris composites the shaded frame into the main target only after AFTER_LEVEL, so a full-screen
 * post pass run there is overwritten. With a pack active the pass is deferred to the start of HUD
 * rendering, when the main target holds the final shaded image.</li>
 * </ul>
 * Without a shader pack every path is unchanged.
 */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class ShaderPackCompat {
    private static final List<Runnable> DEFERRED=new ArrayList<>();
    private static java.lang.reflect.Method inUse;
    private static Object api;
    private static boolean missing,reported;
    private static long checkedAt;
    private static boolean checked;
    private static boolean cached;
    /** Debug only: exercise the shader-pack fallback paths without a pack installed. */
    private static final boolean FORCED="1".equals(System.getenv("BLACKLIGHT_FORCE_SHADERPACK_PATH"));
    private ShaderPackCompat(){}

    /** True while an Iris/Oculus shader pack renders the world. Never throws. */
    public static boolean active(){
        long now=System.nanoTime();
        // checkedAt starts unset: subtracting a sentinel such as Long.MIN_VALUE overflows
        // to a negative age and would pin the first cached answer forever.
        if(checked&&now-checkedAt<50_000_000L)return cached;
        checked=true;checkedAt=now;cached=FORCED;
        if(FORCED)return true;
        if(missing)return cached;
        try{
            if(api==null){
                var type=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                inUse=type.getMethod("isShaderPackInUse");
                api=type.getMethod("getInstance").invoke(null);
            }
            cached=Boolean.TRUE.equals(inUse.invoke(api));
        }catch(ClassNotFoundException|NoSuchMethodException|LinkageError absent){
            missing=true; // No Iris/Oculus installed: never probe again.
            com.mojang.logging.LogUtils.getLogger().info("Shader pack API unavailable: {}",absent.toString());
        }catch(Throwable early){
            // Iris answers before its config/pack loads by throwing. Keep the API and
            // retry on the next check instead of caching "no shader pack" forever.
            if(!reported){reported=true;com.mojang.logging.LogUtils.getLogger().info("Shader pack probe not ready yet: {}",early.toString());}
        }
        return cached;
    }

    /** World effect stage. A shader pack drops geometry drawn after translucent terrain,
     * so with a pack active effects join the entity pass instead. */
    public static RenderLevelStageEvent.Stage effectStage(){
        return active()?RenderLevelStageEvent.Stage.AFTER_ENTITIES:RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
    }

    private static net.minecraft.client.renderer.MultiBufferSource.BufferSource effectBuffers;
    /** Buffers for large world effects. A shader pack replaces the shared source with its own
     * fully batched one, which does not honour per-layer flushes; effects then get a private
     * immediate source that is drawn when the effect ends its batch. */
    public static net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers(){
        if(!active())return Minecraft.getInstance().renderBuffers().bufferSource();
        if(effectBuffers==null)effectBuffers=net.minecraft.client.renderer.MultiBufferSource.immediate(
                new com.mojang.blaze3d.vertex.ByteBufferBuilder(1<<21));
        return effectBuffers;
    }

    /** Runs a full-screen post pass now, or after the shader pack composites the frame. */
    public static void afterLevel(Runnable pass){
        if(active())DEFERRED.add(pass);else pass.run();
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void beginFrame(RenderLevelStageEvent event){
        // A hidden HUD (F1) skips the GUI event; never replay a stale frame.
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY)DEFERRED.clear();
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void gui(RenderGuiEvent.Pre event){
        if(DEFERRED.isEmpty())return;
        var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting=RenderSystem.getVertexSorting();
        var view=RenderSystem.getModelViewStack();
        boolean blend=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        boolean depth=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        view.pushMatrix();
        try{
            event.getGuiGraphics().flush();
            main.bindWrite(true);
            for(var pass:List.copyOf(DEFERRED))pass.run();
        }finally{
            DEFERRED.clear();
            view.popMatrix();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection,sorting);
            main.bindWrite(true);
            RenderSystem.defaultBlendFunc();
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
        }
    }
}
