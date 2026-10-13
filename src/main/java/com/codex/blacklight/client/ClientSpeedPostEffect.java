package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.movement.MovementTuningSettings;
import com.codex.blacklight.movement.PrototypeLocomotion;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.io.IOException;
import org.lwjgl.opengl.GL11;

/**
 * Velocity-corrected radial post effect inspired by the MIT-licensed
 * AnimeSpeedLines mod, rewritten for NeoForge's native PostChain.
 */
@EventBusSubscriber(modid = BlacklightNeoMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSpeedPostEffect {
    private static final ResourceLocation EFFECT = ResourceLocation.fromNamespaceAndPath(
            BlacklightNeoMod.MOD_ID, "shaders/post/prototype_speed.json");

    private static PostChain chain;
    private static RenderTarget savedDepth;
    private static boolean failed;
    private static int targetWidth = -1;
    private static int targetHeight = -1;
    private static float previousIntensity;
    private static float intensity;
    private static float previousBiasX;
    private static float biasX;
    private static float previousBiasY;
    private static float biasY;
    private static net.minecraft.server.packs.resources.ResourceManager lastResourceManager;

    private ClientSpeedPostEffect() {
    }

    @EventBusSubscriber(modid="blacklight",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class ReloadEvents {
    @SubscribeEvent
    public static void onReload(RegisterClientReloadListenersEvent event){
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->
                Minecraft.getInstance().execute(()->{closeChain();failed=false;}));
    }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            // The world unloaded: release the PostChain so a resource reload can
            // never leave us holding stale shader references.
            closeChain();
            failed = false;
            return;
        }
        previousIntensity = intensity;
        previousBiasX = biasX;
        previousBiasY = biasY;

        float target = 0.0F;
        float targetBiasX = 0.0F;
        float targetBiasY = 0.0F;
        if (minecraft.player != null && minecraft.level != null) {
            var state = minecraft.player.getData(ModAttachments.PLAYER_STATE);
            boolean running = state.isInfected() && state.isPrototypeMode()
                    && minecraft.player.onGround()
                    && ClientMovementEvents.isRunningForAttack(minecraft)
                    && !ClientClawEvents.isGroundSpikeActionActive();
            if (running) {
                double speed = minecraft.player.getDeltaMovement().horizontalDistance();
                target = PrototypeLocomotion.sprintVisualIntensity(speed,
                        MovementTuningSettings.sprintMaxSpeed());

                Vec3 velocity = minecraft.player.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
                Vec3 view = minecraft.player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
                if (velocity.lengthSqr() > 1.0E-5D && view.lengthSqr() > 1.0E-5D) {
                    velocity = velocity.normalize();
                    view = view.normalize();
                    Vec3 right = new Vec3(-view.z, 0.0D, view.x);
                    double side = velocity.dot(right);
                    double forward = velocity.dot(view);
                    targetBiasX = (float)Mth.clamp(-side * 0.105D, -0.105D, 0.105D);
                    targetBiasY = (float)Mth.clamp((1.0D - Math.abs(forward)) * 0.018D,
                            0.0D, 0.018D);
                }
            }
        }

        float riseOrFall = target > intensity ? 0.34F : 0.20F;
        intensity += (target - intensity) * riseOrFall;
        biasX += (targetBiasX - biasX) * 0.28F;
        biasY += (targetBiasY - biasY) * 0.28F;
        if (intensity < 0.001F && target == 0.0F) intensity = 0.0F;
    }

    /** World logout also invalidates the chain; recreate it lazily on next render. */
    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        closeChain();
        failed = false;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || failed) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float renderedIntensity = Mth.lerp(partial, previousIntensity, intensity);
        boolean copied=false;
        boolean wasBlend=GL11.glIsEnabled(GL11.GL_BLEND);
        boolean wasDepth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        try {
            ensureChain(minecraft);
            if (chain == null) return;
            // Compile and validate the chain as soon as a world renders. Keeping the
            // zero-intensity fast path after that makes shader failures deterministic
            // instead of hiding them until the player happens to sprint.
            if (renderedIntensity < 0.015F) return;
            minecraft.renderBuffers().bufferSource().endBatch();
            savedDepth.copyDepthFrom(minecraft.getMainRenderTarget());
            copied=true;
            chain.setUniform("Time", (minecraft.player.tickCount + partial) / 20.0F);
            chain.setUniform("Intensity", renderedIntensity);
            chain.setUniform("BiasX", Mth.lerp(partial, previousBiasX, biasX));
            chain.setUniform("BiasY", Mth.lerp(partial, previousBiasY, biasY));
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            chain.process(partial);
        } catch (RuntimeException | IOException exception) {
            failed = true;
            BlacklightNeoMod.LOGGER.error("Unable to render Prototype speed post effect", exception);
        } finally {
            // A full-screen post pass clears its output depth. Preserve the scene
            // for later outlines/post effects and never leave hands on a swap FBO.
            if(copied&&savedDepth!=null)minecraft.getMainRenderTarget().copyDepthFrom(savedDepth);
            if(wasDepth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(wasBlend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            minecraft.getMainRenderTarget().bindWrite(false);
            if(failed)closeChain();
        }
    }

    private static void ensureChain(Minecraft minecraft) throws IOException {
        // A resource reload replaces the ResourceManager; the cached chain then
        // holds stale shader objects, so rebuild whenever the instance changes.
        net.minecraft.server.packs.resources.ResourceManager current = minecraft.getResourceManager();
        if (chain != null && lastResourceManager != current) {
            closeChain();
            failed = false;
        }
        if (chain == null) {
            chain = new PostChain(minecraft.getTextureManager(), current,
                    minecraft.getMainRenderTarget(), EFFECT);
            lastResourceManager = current;
            targetWidth = -1;
            targetHeight = -1;
            BlacklightNeoMod.LOGGER.info("Loaded Prototype velocity-corrected speed shader");
        }
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width != targetWidth || height != targetHeight) {
            chain.resize(width, height);
            if(savedDepth!=null)savedDepth.destroyBuffers();
            savedDepth=new TextureTarget(width,height,true,Minecraft.ON_OSX);
            targetWidth = width;
            targetHeight = height;
        }
    }

    private static void closeChain() {
        boolean destroyed=chain!=null||savedDepth!=null;
        if (chain != null) chain.close();
        chain = null;
        if(savedDepth!=null)savedDepth.destroyBuffers();
        savedDepth=null;
        lastResourceManager = null;
        targetWidth = -1;
        targetHeight = -1;
        if(destroyed)Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }
}
