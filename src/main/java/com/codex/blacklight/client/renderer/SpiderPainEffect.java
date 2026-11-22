package com.codex.blacklight.client.renderer;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.combat.SpiderActions;
import com.codex.blacklight.combat.SpiderPainBeam;
import com.codex.blacklight.entity.EncounterMob;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/** Refract the rendered world inside the visible volume of each mouth blast. */
public final class SpiderPainEffect {
    private record Pulse(Vec3 start, Vec3 end, float radius, float strength, float age, GoddessBubbleRenderer.Shell shell) {}
    private static final LinkedHashMap<Integer,Pulse> pending = new LinkedHashMap<>();
    private static PostChain chain;
    private static RenderTarget depth;
    private static boolean failed;
    private static long lastVisible;
    private SpiderPainEffect() {}

    static void collect(EncounterMob boss, float partial) {
        if (!boss.spider.live()) return;
        lastVisible = System.nanoTime();
        if (boss.spider.actions.move() != SpiderActions.Move.MOUTH_BEAM) return;
        float age = boss.spider.actions.age(partial);
        float opening = SpiderPainBeam.opening(age);
        if (opening < .01F || age >= SpiderPainBeam.SHUT + 7) return;
        var lane = SpiderPainBeam.lane(boss, partial);
        boolean charge = age < SpiderPainBeam.FIRE;
        float radius = charge ? 2.0F * opening : (float)lane.radius() * 1.32F;
        if (radius < .02F) return;
        Vec3 end = charge ? lane.mouth() : lane.end();
        pending.put(boss.getId(), new Pulse(lane.mouth(), end, radius, opening * (charge ? .78F : 1F), age,null));
    }

    static void shell(int id,GoddessBubbleRenderer.Shell shape,float strength,float age){
        lastVisible=System.nanoTime();
        float bound=(float)Math.max(shape.axes().x,Math.max(shape.axes().y,shape.axes().z))*1.40F;
        pending.put(id,new Pulse(shape.center(),shape.center(),bound,strength,age,shape));
    }

    private static void vector(String name,Vector3f v){
        uniform(name+"X",v.x);uniform(name+"Y",v.y);uniform(name+"Z",v.z);
    }

    public static void reset() {
        pending.clear();
        lastVisible = 0;
        release();
        failed = false;
    }

    private static void release() {
        if (chain == null && depth == null) return;
        if (chain != null) { chain.close(); chain = null; }
        if (depth != null) { depth.destroyBuffers(); depth = null; }
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }

    public static void finishFrame(Matrix4f view, Matrix4f projection, Vec3 camera, float partial) {
        // The camera may see the trapped player while Greene herself is outside its frustum.
        var level=Minecraft.getInstance().level;
        if(level!=null)for(var entity:level.entitiesForRendering())
            if(entity instanceof EncounterMob boss && boss.goddess.active())GoddessBubbleRenderer.collect(boss,partial);
        if (pending.isEmpty()) {
            if (lastVisible == 0 || System.nanoTime() - lastVisible > 2_000_000_000L) { release(); return; }
            if (chain != null || failed) return;
            // Compile and prime the driver while the shell is idle, before players have to dodge the first cast.
        }
        var pulses = List.copyOf(pending.values());
        pending.clear();
        if (failed) return;
        // AFTER_LEVEL follows the shader pack's world composite and precedes vanilla's hand depth clear.
        // Use that colour/depth pair even in F1, where the GUI callback may be skipped.
        apply(pulses, view, projection, camera, partial);
    }

    private static Vector3f toView(Vec3 point, Vec3 camera, Matrix4f view) {
        return view.transformPosition(point.subtract(camera).toVector3f());
    }

    private static void uniform(String name, float value) {
        com.codex.blacklight.client.ClientPostUniforms.set(chain, name, value);
    }

    private static void apply(List<Pulse> pulses, Matrix4f view, Matrix4f proj, Vec3 camera, float partial) {
        var mc = Minecraft.getInstance();
        var main = mc.getMainRenderTarget();
        var previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), test = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean saved = false;
        try {
            mc.renderBuffers().bufferSource().endBatch();
            if (chain == null) chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main,
                    new ResourceLocation("blacklight", "shaders/post/greene_refraction.json"));
            if (depth == null || depth.width != main.width || depth.height != main.height) {
                if (depth != null) depth.destroyBuffers();
                depth = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
                chain.resize(main.width, main.height);
            }
            depth.copyDepthFrom(main);
            saved = true;
            uniform("ProjX", proj.m00()); uniform("ProjY", proj.m11());
            uniform("OffsetX", proj.m20()); uniform("OffsetY", proj.m21());
            uniform("DepthA", proj.m22()); uniform("DepthB", proj.m32());
            if (pulses.isEmpty()) {
                uniform("Strength", 0);
                chain.process(partial);
                main.copyDepthFrom(depth);
            }
            for (var pulse : pulses) {
                var start = toView(pulse.start(), camera, view);
                var end = toView(pulse.end(), camera, view);
                var shell=pulse.shell();
                uniform("Inward",shell==null?0F:1F);
                if(shell!=null){
                    vector("Axis",shell.axes().toVector3f());
                    vector("Right",view.transformDirection(shell.right().toVector3f()));
                    vector("Up",view.transformDirection(new Vector3f(0,1,0)));
                    vector("PalmR",view.transformDirection(shell.palmR().toVector3f()));
                    vector("PalmL",view.transformDirection(shell.palmL().toVector3f()));
                    vector("Knee",view.transformDirection(shell.knee().toVector3f()));
                    vector("Shoulder",view.transformDirection(shell.shoulder().toVector3f()));
                    uniform("Contact",shell.contact());uniform("Fracture",shell.rupture());
                }
                uniform("Ax", start.x); uniform("Ay", start.y); uniform("Az", start.z);
                uniform("Bx", end.x); uniform("By", end.y); uniform("Bz", end.z);
                uniform("Radius", pulse.radius()); uniform("Strength", pulse.strength()); uniform("Age", pulse.age());
                chain.process(partial);
                main.copyDepthFrom(depth);
            }
        } catch (Exception ex) {
            failed = true;
            BlacklightNeoMod.LOGGER.error("Greene mouth refraction could not render", ex);
            if (saved) main.copyDepthFrom(depth);
            release();
        } finally {
            main.bindWrite(true);
            RenderSystem.setProjectionMatrix(previousProjection, sorting);
            RenderSystem.depthMask(depthWrite);
            if (test) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }
}
