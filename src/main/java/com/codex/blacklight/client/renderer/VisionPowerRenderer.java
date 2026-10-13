package com.codex.blacklight.client.renderer;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.power.*;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Real posed silhouettes over a world colour pass. Does not change entity glowing flags. */
@EventBusSubscriber(modid="blacklight", value=Dist.CLIENT)
public final class VisionPowerRenderer {
    private static PostChain effect;
    private static com.mojang.blaze3d.pipeline.RenderTarget depthCopy;
    private static int effectMode, width, height;
    private static boolean failed, drawing;
    private static int displayedMode;
    private static float strength;
    private static double lastFrame = -1;
    private static net.minecraft.client.multiplayer.ClientLevel previousLevel;
    public static boolean effectFailed() { return failed; }
    public static boolean drawingSilhouettes() { return drawing; }
    private static int mode() {
        var p = Minecraft.getInstance().player;
        return p == null ? 0 : UtilityPowerSystem.active(p, PowerForm.INFECTED_VISION) ? 2
                : UtilityPowerSystem.active(p, PowerForm.THERMAL_VISION) ? 1 : 0;
    }
    private static void closeEffect() {
        if (effect != null) { effect.close(); effect = null; }
        if (depthCopy != null) { depthCopy.destroyBuffers(); depthCopy = null; }
        effectMode = 0;
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager ->
                Minecraft.getInstance().execute(() -> { closeEffect(); failed = false; }));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        var current = Minecraft.getInstance().level;
        if (current != previousLevel) {
            closeEffect(); previousLevel = current; displayedMode = 0; strength = 0; lastFrame = -1; failed = false;
        }
    }
    private static int transition(Minecraft mc, float partial) {
        int wanted = mode(); double now = mc.level.getGameTime() + partial;
        float elapsed = lastFrame < 0 ? 0 : (float)Math.clamp((now - lastFrame) / 20, 0, .25);
        lastFrame = now;
        if (displayedMode == 0) displayedMode = wanted;
        boolean entering = wanted != 0 && wanted == displayedMode;
        strength = Math.clamp(strength + elapsed * (entering ? 5 : -5), 0, 1);
        if (strength == 0 && !entering) { closeEffect(); displayedMode = wanted; }
        return displayedMode;
    }
    private static void colourWorld(Minecraft mc, int mode, float partial) {
        if (failed) return;
        boolean savedDepth = false;
        try {
            if (effect == null || effectMode != mode) {
                closeEffect();
                effect = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(),
                        ResourceLocation.fromNamespaceAndPath("blacklight", "shaders/post/" + (mode == 1 ? "thermal" : "infected") + ".json"));
                effectMode = mode;
                width = height = -1;
            }
            if (width != mc.getMainRenderTarget().width || height != mc.getMainRenderTarget().height) {
                width = mc.getMainRenderTarget().width; height = mc.getMainRenderTarget().height;
                effect.resize(width, height);
                if (depthCopy != null) depthCopy.destroyBuffers();
                depthCopy = new com.mojang.blaze3d.pipeline.TextureTarget(width, height, true, Minecraft.ON_OSX);
            }
            depthCopy.copyDepthFrom(mc.getMainRenderTarget());
            savedDepth = true;
            effect.setUniform("Strength", strength * strength * (3 - 2 * strength));
            effect.process(partial);
            mc.getMainRenderTarget().copyDepthFrom(depthCopy);
        } catch (Exception ex) {
            if (savedDepth && depthCopy != null) mc.getMainRenderTarget().copyDepthFrom(depthCopy);
            closeEffect(); failed = true;
            BlacklightNeoMod.LOGGER.error("Sensory colour pass unavailable; reload resources to retry", ex);
        } finally { mc.getMainRenderTarget().bindWrite(false); }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || drawing) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        int mode = transition(mc, partial);
        if (mode == 0 || strength <= 0) return;
        var savedProjection = new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        var savedSorting = com.mojang.blaze3d.systems.RenderSystem.getVertexSorting();
        mc.renderBuffers().bufferSource().endBatch();
        colourWorld(mc, mode, partial);
        double radius = mode == 2 ? 32 : 24;
        var targets = mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(radius),
                v -> v != mc.player && v.isAlive() && !v.isSpectator() && !v.isInvisibleTo(mc.player)
                        && v.distanceToSqr(mc.player) < radius * radius);
        targets.sort(java.util.Comparator.comparingDouble(mc.player::distanceToSqr));
        var buffer = mc.renderBuffers().bufferSource();
        var camera = e.getCamera().getPosition();
        // AFTER_LEVEL runs after LevelRenderer has popped the camera matrix.
        // PostChain also replaces projection state. Re-establish both while
        // drawing silhouettes, then return the caller's state even on failure.
        var view = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.set(e.getModelViewMatrix());
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(e.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        drawing = true;
        try {
            for (var target : targets.subList(0, Math.min(48, targets.size()))) {
                boolean infected = BlacklightInfectionSystem.isInfected(target);
                int color = mode == 2 ? (infected ? 0xEFFF5932 : 0x605D8AA0)
                        : target instanceof net.minecraft.world.entity.animal.AbstractGolem ? 0x804D6BB8 : 0xEFFFCE64;
                var out = buffer.getBuffer(RenderType.entityTranslucentEmissive(ConsumedMobVisuals.WHITE));
                var pos = target.getPosition(partial).subtract(camera);
                boolean hot = mode == 1 && !(target instanceof net.minecraft.world.entity.animal.AbstractGolem);
                var tint = new Tint(out, color, strength, hot, (float)pos.y, target.getBbHeight(), target.isOnFire());
                // Non-entity buffers include labels, fire and debug effects; omit them from this pass.
                var discard = new Tint(null, 0, 0, false, 0, 1, false);
                MultiBufferSource colored = type -> type.format() == DefaultVertexFormat.NEW_ENTITY && type.mode() == VertexFormat.Mode.QUADS ? tint : discard;
                // Render only the model. Dispatcher.render also submits its
                // shadow as NEW_ENTITY quads, which turned the floor orange.
                var renderer=mc.getEntityRenderDispatcher().getRenderer(target);
                var offset=renderer.getRenderOffset(target,partial);
                var stack=e.getPoseStack();stack.pushPose();
                try{
                    stack.translate(pos.x+offset.x,pos.y+offset.y,pos.z+offset.z);
                    renderer.render(target,target.getYRot(),partial,stack,colored,LightTexture.FULL_BRIGHT);
                }finally{stack.popPose();}
            }
        } finally {
            try { buffer.endBatch(); } finally {
                drawing = false;
                view.popMatrix();
                com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
                com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(savedProjection,savedSorting);
            }
        }
    }
    private static final class Tint implements VertexConsumer {
        private final VertexConsumer out; private final int color;
        private final float opacity, baseY, height; private final boolean thermal, burning;
        private float vertexY;
        Tint(VertexConsumer out, int color, float opacity, boolean thermal, float baseY, float height, boolean burning) {
            this.out=out;this.color=color;this.opacity=opacity;this.thermal=thermal;this.baseY=baseY;this.height=Math.max(.1F,height);this.burning=burning;
        }
        public VertexConsumer addVertex(float x, float y, float z) { vertexY=y;if (out != null) out.addVertex(x,y,z); return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) {
            if (out != null) {
                int alpha=(int)((color>>>24)*opacity);
                if(thermal) {
                    // Minecraft body-height heat palette, not a claim of simulated temperatures.
                    float h=Math.clamp((vertexY-baseY)/height,0,1);
                    float warmth=burning?1:.3F+.7F*(float)Math.exp(-12*(h-.58F)*(h-.58F));
                    out.setColor(255,(int)(55+200*warmth),(int)(18+155*warmth*warmth),alpha);
                } else out.setColor(color>>16&255,color>>8&255,color&255,alpha);
            }
            return this;
        }
        public VertexConsumer setUv(float u, float v) { if (out != null) out.setUv(.5F,.5F); return this; }
        public VertexConsumer setUv1(int u, int v) { if (out != null) out.setUv1(0,10); return this; }
        public VertexConsumer setUv2(int u, int v) { if (out != null) out.setUv2(240,240); return this; }
        public VertexConsumer setNormal(float x, float y, float z) { if (out != null) out.setNormal(x,y,z); return this; }
    }
}
