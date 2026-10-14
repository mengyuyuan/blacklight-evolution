package com.codex.blacklight.client.model;

import com.codex.blacklight.combat.MotionResources;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.power.UtilityPowerSystem;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.client.renderer.VisionPowerRenderer;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Approved Minecraft cuboids, with private source plate-growth timing. */
public final class BlockArmorModel {
    public static final ResourceLocation TEXTURE = new ResourceLocation("blacklight", "textures/entity/armor_block.png");
    private static final ResourceLocation WHITE = new ResourceLocation("blacklight", "textures/entity/claw_white.png");
    private record Part(String joint, String region, boolean core, boolean shoulder, int material, Vector3f[] points,
                        Vector3f center, Vector3f[] normals, float[] growth, float[][][] uv) { }
    private static final class Transition {
        long stamp = Long.MIN_VALUE;
        boolean active;
        float[] start, values;
        Transition(int count) { start = new float[count]; values = new float[count]; }
    }
    private static BlockArmorModel instance;
    private final List<Part> parts = new ArrayList<>();
    private final Map<AbstractClientPlayer, Transition> transitions = new WeakHashMap<>();
    private final int[][] faces;
    private final float fps, duration;
    private final int materialCount;
    private final float renderScale;

    public static BlockArmorModel get() {
        if (instance == null) instance = new BlockArmorModel();
        return instance;
    }
    public static void invalidate() { instance = null; }

    private BlockArmorModel() {
        var motion = MotionResources.read("armor-growth.data", 2_000_000)
                .orElseThrow(() -> new IllegalStateException("Missing armor-growth.data in private motion profile"));
        fps = motion.get("fps").getAsFloat(); duration = motion.get("duration").getAsFloat();
        if (!(fps > 0 && duration > 0)) throw new IllegalStateException("Invalid armor growth timing");
        try (var input = getClass().getResourceAsStream("/assets/blacklight/models/armor_block.json")) {
            if (input == null) throw new IllegalStateException("Missing approved armor model");
            var root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            renderScale = root.has("renderScale") ? root.get("renderScale").getAsFloat() : 1;
            if (!Float.isFinite(renderScale) || renderScale < .25F || renderScale > 3) throw new IllegalStateException("Invalid armor render scale");
            materialCount = root.getAsJsonArray("materials").size();
            var faceData = root.getAsJsonArray("faces"); faces = new int[faceData.size()][4];
            for (int f = 0; f < faces.length; f++) for (int v = 0; v < 4; v++) faces[f][v] = faceData.get(f).getAsJsonArray().get(v).getAsInt();
            for (var element : root.getAsJsonArray("parts")) {
                var part = element.getAsJsonObject(); var vertices = part.getAsJsonArray("points");
                Vector3f[] points = new Vector3f[vertices.size()]; Vector3f center = new Vector3f();
                for (int i = 0; i < points.length; i++) {
                    var v = vertices.get(i).getAsJsonArray();
                    points[i] = new Vector3f(v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat()); center.add(points[i]);
                }
                center.div(points.length);
                Vector3f[] normals = new Vector3f[faces.length];
                for (int f = 0; f < faces.length; f++) normals[f] = new Vector3f(points[faces[f][1]]).sub(points[faces[f][0]])
                        .cross(new Vector3f(points[faces[f][2]]).sub(points[faces[f][0]])).normalize();
                String name = part.get("name").getAsString();
                var track = motion.getAsJsonObject("parts").getAsJsonObject(name).getAsJsonArray("growth");
                float[] curve = new float[track.size()];
                for (int i = 0; i < curve.length; i++) {
                    curve[i] = track.get(i).getAsFloat();
                    if (!Float.isFinite(curve[i]) || curve[i] < 0 || curve[i] > 1) throw new IllegalStateException("Invalid armor curve: " + name);
                }
                if (curve.length != Math.round(duration * fps) + 1) throw new IllegalStateException("Incomplete armor curve: " + name);
                float[][][] uv = null;
                if (part.has("uv")) {
                    var data = part.getAsJsonArray("uv");
                    if (data.size() != faces.length) throw new IllegalStateException("Incomplete armor UV: " + name);
                    uv = new float[faces.length][4][2];
                    for (int f = 0; f < faces.length; f++) {
                        var face = data.get(f).getAsJsonArray();
                        if (face.size() != 4) throw new IllegalStateException("Invalid armor UV face: " + name);
                        for (int v = 0; v < 4; v++) {
                            var point = face.get(v).getAsJsonArray();
                            if (point.size() != 2) throw new IllegalStateException("Invalid armor UV point: " + name);
                            for (int axis = 0; axis < 2; axis++) {
                                float value = point.get(axis).getAsFloat();
                                if (!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalStateException("Invalid armor UV coordinate: " + name);
                                uv[f][v][axis] = value;
                            }
                        }
                    }
                }
                parts.add(new Part(part.get("joint").getAsString(), part.get("region").getAsString(), part.get("core").getAsBoolean(), part.get("shoulder").getAsBoolean(),
                        part.get("material").getAsInt(), points, center, normals, curve, uv));
            }
        } catch (Exception e) { throw new IllegalStateException("Cannot load approved block armor", e); }
    }

    private static float smooth(float t) { t = com.codex.blacklight.util.MathCompat.clamp(t, 0, 1); return t * t * (3 - 2 * t); }
    private float sample(Part part, float seconds) {
        float f = com.codex.blacklight.util.MathCompat.clamp(seconds * fps, 0, part.growth.length - 1);
        int i = (int)f; return part.growth[i] + (part.growth[Math.min(i + 1, part.growth.length - 1)] - part.growth[i]) * (f - i);
    }
    /** A retracted form grows back from its current shape instead of snapping. */
    private Transition state(AbstractClientPlayer player, float partial) {
        var data = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);
        if (!player.isAlive() || !data.isInfected() || !data.isPrototypeMode()) { transitions.remove(player); return null; }
        var utility = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.UTILITY_STATE);
        boolean active = UtilityPowerSystem.active(player, PowerForm.ARMOR);
        var state = transitions.get(player);
        if (state == null && !active && (utility.previousDefense() & PowerForm.ARMOR.bit()) == 0) return null;
        if (state == null) {
            state = new Transition(parts.size());
            if (!active) Arrays.fill(state.values, 1);
            transitions.put(player, state);
        }
        if (state.stamp != utility.changedAt() || state.active != active) {
            state.start = state.values.clone(); state.stamp = utility.changedAt(); state.active = active;
        }
        float age = Math.max(0, (player.level().getGameTime() - state.stamp + partial) / 20F);
        for (int i = 0; i < parts.size(); i++) state.values[i] = active
                ? state.start[i] + (1 - state.start[i]) * sample(parts.get(i), age)
                : state.start[i] * (1 - smooth(age / .45F));
        return state;
    }

    public boolean covers(AbstractClientPlayer player, String region, float partial) {
        var state = state(player, partial); if (state == null) return false;
        boolean found = false;
        for (int i = 0; i < parts.size(); i++) {
            var part = parts.get(i);
            if (!part.core || !part.region.equals(region)) continue;
            found = true; if (state.values[i] < .995F) return false;
        }
        return found;
    }

    public Set<String> joints() {
        Set<String> joints = new LinkedHashSet<>(); for (var part : parts) joints.add(part.joint); return joints;
    }
    /** Keep skeleton, weapons and equipment in one frame while the taller armor grows. */
    public float bodyScale(AbstractClientPlayer player, float partial) {
        var state = state(player, partial);
        if (state == null || renderScale == 1) return 1;
        float weight = 0;
        for (float value : state.values) weight += value;
        return 1 + (renderScale - 1) * weight / state.values.length;
    }
    public boolean visible(AbstractClientPlayer player, float partial) {
        var state = state(player, partial); if (state == null) return false;
        for (float value : state.values) if (value > .005F) return true;
        return false;
    }

    public void renderJoint(String joint, AbstractClientPlayer player, PoseStack stack, MultiBufferSource buffers, int light, float partial, boolean weaponArm) {
        var state = state(player, partial); if (state == null) return;
        var out = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)); var pose = stack.last();
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i); float growth = state.values[i];
            if (!part.joint.equals(joint) || growth <= .005F || weaponArm && !part.shoulder) continue;
            for (int f = 0; f < faces.length; f++) {
                var normal = part.normals[f];
                for (int v = 0; v < 4; v++) {
                    var point = part.points[faces[f][v]]; var center = part.center;
                    float u = (part.material + ((v == 1 || v == 2) ? .969F : .031F)) / materialCount;
                    float tv = v < 2 ? .031F : .969F;
                    if (part.uv != null) { u = part.uv[f][v][0]; tv = part.uv[f][v][1]; }
                    out.vertex(pose.pose(), center.x + (point.x - center.x) * growth, center.y + (point.y - center.y) * growth, center.z + (point.z - center.z) * growth)
                            .color(0xFFFFFFFF).uv(u, tv).overlayCoords(OverlayTexture.pack(0, player.hurtTime > 0)).uv2(light).normal(pose.normal(), normal.x, normal.y, normal.z).endVertex();
                }
            }
        }
        // Small non-emissive tissue strands follow the actual growing cube face.
        // This is a Minecraft effect adaptation, not an extracted original shader.
        if (VisionPowerRenderer.drawingSilhouettes()) return;
        var veins = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i); float g = state.values[i];
            if (!part.joint.equals(joint) || part.core || g <= .08F || g >= .98F || weaponArm && !part.shoulder) continue;
            var a = part.points[faces[0][0]]; var b = part.points[faces[0][1]];
            var c = part.points[faces[0][2]]; var d = part.points[faces[0][3]]; var normal = part.normals[0];
            for (int v = 0; v < 4; v++) {
                float side = v == 0 || v == 3 ? .44F : .56F;
                Vector3f point = v < 2 ? new Vector3f(a).lerp(b, side) : new Vector3f(d).lerp(c, side);
                point.sub(part.center).mul(g).add(part.center).fma(.0008F, normal);
                veins.vertex(pose.pose(), point.x, point.y, point.z).color(91, 24, 25, (int)(155 * Math.sin(Math.PI * g)))
                        .uv(.5F, .5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(), normal.x, normal.y, normal.z).endVertex();
            }
        }
    }
}
