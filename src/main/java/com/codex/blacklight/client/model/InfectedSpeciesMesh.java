package com.codex.blacklight.client.model;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.client.renderer.ConsumeAnatomy;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authored replacement anatomy, cached once per species and shared across entities.
 * Positions and absolute joint pivots are in blocks, Y up, facing negative Z. */
@EventBusSubscriber(modid = BlacklightNeoMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class InfectedSpeciesMesh {
    private static final Map<String, Optional<InfectedSpeciesMesh>> CACHE = new HashMap<>();
    private final String species;
    private final List<Bone> roots;
    private final AABB bounds;
    private final float referenceScale;
    private final boolean boss;

    public static final class Bone {
        public final String name, parent;
        public final float x, y, z;
        private final float[] vertices, normals;
        private final int[] triangles;
        private final Bone late;
        private final float[] lateRoots;
        private final int anatomy;
        private final List<Bone> children = new ArrayList<>();

        private Bone(String species, JsonObject data) {
            name = data.get("name").getAsString();
            parent = data.has("parent") && !data.get("parent").isJsonNull() ? data.get("parent").getAsString() : null;
            var pivot = data.getAsJsonArray("pivot");
            x = pivot.get(0).getAsFloat(); y = pivot.get(1).getAsFloat(); z = pivot.get(2).getAsFloat();
            var vs = data.getAsJsonArray("vertices"); vertices = new float[vs.size()];
            for (int i = 0; i < vertices.length; i++) {
                vertices[i] = vs.get(i).getAsFloat();
                if (!Float.isFinite(vertices[i])) throw new IllegalArgumentException("Non-finite vertex: " + name);
            }
            var ts = data.getAsJsonArray("triangles"); triangles = new int[ts.size()];
            if (vertices.length % 3 != 0 || triangles.length % 4 != 0) throw new IllegalArgumentException("Malformed mesh: " + name);
            for (int i = 0; i < triangles.length; i++) {
                triangles[i] = ts.get(i).getAsInt();
                if (i % 4 != 3 && (triangles[i] < 0 || triangles[i] >= vertices.length / 3))
                    throw new IllegalArgumentException("Invalid triangle index: " + name);
            }
            normals = new float[triangles.length / 4 * 3];
            for (int i = 0, n = 0; i < triangles.length; i += 4, n += 3) {
                int a = triangles[i] * 3, b = triangles[i + 1] * 3, c = triangles[i + 2] * 3;
                float ux = vertices[b] - vertices[a], uy = vertices[b + 1] - vertices[a + 1], uz = vertices[b + 2] - vertices[a + 2];
                float vx = vertices[c] - vertices[a], vy = vertices[c + 1] - vertices[a + 1], vz = vertices[c + 2] - vertices[a + 2];
                float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (length > 1.0e-8F) { normals[n] = nx / length; normals[n + 1] = ny / length; normals[n + 2] = nz / length; }
                else normals[n + 1] = 1;
            }
            anatomy = InfectedModelPose.anatomy(species, name);
            if(data.has("lateGrowth")) {
                var graft=data.getAsJsonObject("lateGrowth").deepCopy();
                graft.addProperty("name",name);graft.add("pivot",pivot.deepCopy());
                late=new Bone(species,graft);
                lateRoots=new float[late.vertices.length];
                float[] lo={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
                float[] hi={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
                for(int i=0;i<vertices.length;i++){int axis=i%3;lo[axis]=Math.min(lo[axis],vertices[i]);hi[axis]=Math.max(hi[axis],vertices[i]);}
                for(int i=0;i<lateRoots.length;i++)lateRoots[i]=vertices.length==0?pivot.get(i%3).getAsFloat():Math.clamp(late.vertices[i],lo[i%3],hi[i%3]);
            }else{late=null;lateRoots=null;}
        }

        private void draw(PoseStack stack, VertexConsumer out, int light, int overlay, int alpha) {
            if (out instanceof ConsumeAnatomy.Consumer consumer) consumer.anatomy(anatomy);
            var pose = stack.last();
            for (int i = 0, n = 0; i < triangles.length; i += 4, n += 3) {
                int color = alpha << 24 | triangles[i + 3] & 0xFFFFFF;
                // Entity render types use quads; repeating the last vertex keeps
                // source triangulation, lighting and fracture collectors compatible.
                for (int corner = 0; corner < 4; corner++) {
                    int v = triangles[i + Math.min(corner, 2)] * 3;
                    out.addVertex(pose, vertices[v], vertices[v + 1], vertices[v + 2]).setColor(color)
                            .setUv(.5F, .5F).setOverlay(overlay).setLight(light)
                            .setNormal(pose, normals[n], normals[n + 1], normals[n + 2]);
                }
            }
        }

        private void drawLate(PoseStack stack,VertexConsumer out,int light,int overlay,int alpha,float amount) {
            if(late==null||amount<=0)return;
            var pose=stack.last();
            if(out instanceof ConsumeAnatomy.Consumer consumer)consumer.anatomy(anatomy);
            for(int i=0;i<late.triangles.length;i+=4){
                int a=late.triangles[i]*3,b=late.triangles[i+1]*3,c=late.triangles[i+2]*3;
                float ax=latePoint(a,amount),ay=latePoint(a+1,amount),az=latePoint(a+2,amount);
                float bx=latePoint(b,amount),by=latePoint(b+1,amount),bz=latePoint(b+2,amount);
                float cx=latePoint(c,amount),cy=latePoint(c+1,amount),cz=latePoint(c+2,amount);
                float nx=(by-ay)*(cz-az)-(bz-az)*(cy-ay),ny=(bz-az)*(cx-ax)-(bx-ax)*(cz-az),nz=(bx-ax)*(cy-ay)-(by-ay)*(cx-ax);
                float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);if(length<1e-8F)continue;
                nx/=length;ny/=length;nz/=length;
                int color=alpha<<24|late.triangles[i+3]&0xffffff;
                for(int corner=0;corner<4;corner++){
                    int v=late.triangles[i+Math.min(corner,2)]*3;
                    out.addVertex(pose,latePoint(v,amount),latePoint(v+1,amount),latePoint(v+2,amount)).setColor(color)
                            .setUv(.5F,.5F).setOverlay(overlay).setLight(light).setNormal(pose,nx,ny,nz);
                }
            }
        }
        private float latePoint(int index,float amount){return lateRoots[index]+(late.vertices[index]-lateRoots[index])*amount;}
    }

    @FunctionalInterface public interface Pose { void apply(Bone bone, PoseStack stack); }

    private InfectedSpeciesMesh(JsonObject data) {
        species = data.get("species").getAsString();
        boss = data.has("boss") && data.get("boss").getAsBoolean();
        referenceScale = data.has("referenceScale") ? data.get("referenceScale").getAsFloat() : 1;
        if (!Float.isFinite(referenceScale) || referenceScale <= 0 || referenceScale > 16)
            throw new IllegalArgumentException("Invalid reference scale for " + species);
        var b = data.getAsJsonObject("bounds"); var lo = b.getAsJsonArray("min"); var hi = b.getAsJsonArray("max");
        bounds = new AABB(lo.get(0).getAsDouble(), lo.get(1).getAsDouble(), lo.get(2).getAsDouble(),
                hi.get(0).getAsDouble(), hi.get(1).getAsDouble(), hi.get(2).getAsDouble());
        Map<String, Bone> nodes = new LinkedHashMap<>();
        for (var entry : data.getAsJsonArray("bones")) {
            var bone = new Bone(species, entry.getAsJsonObject());
            if (nodes.put(bone.name, bone) != null) throw new IllegalArgumentException("Duplicate joint: " + bone.name);
        }
        roots = new ArrayList<>();
        for (var bone : nodes.values()) {
            if (bone.parent == null) roots.add(bone);
            else {
                var parent = nodes.get(bone.parent);
                if (parent == null) throw new IllegalArgumentException("Missing parent: " + bone.parent);
                Set<String> visited = new HashSet<>(); Bone ancestor = bone;
                while (ancestor != null) {
                    if (!visited.add(ancestor.name)) throw new IllegalArgumentException("Cyclic joint: " + bone.name);
                    ancestor = nodes.get(ancestor.parent);
                }
                parent.children.add(bone);
            }
        }
        if (roots.isEmpty()) throw new IllegalArgumentException("No mesh root: " + species);
    }

    public static InfectedSpeciesMesh get(String species) {
        return CACHE.computeIfAbsent(species, InfectedSpeciesMesh::load).orElse(null);
    }

    private static Optional<InfectedSpeciesMesh> load(String species) {
        ResourceLocation id = ResourceLocation.tryParse(species);
        if (id == null || !id.getNamespace().equals("minecraft")) return Optional.empty();
        var resource = Minecraft.getInstance().getResourceManager().getResource(
                ResourceLocation.fromNamespaceAndPath("blacklight", "infected_models/" + id.getPath() + ".json"));
        if (resource.isEmpty()) return Optional.empty();
        try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            return Optional.of(new InfectedSpeciesMesh(JsonParser.parseReader(reader).getAsJsonObject()));
        } catch (Exception exception) {
            BlacklightNeoMod.LOGGER.error("Cannot load infected model {}; using original host fallback", species, exception);
            return Optional.empty();
        }
    }

    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> {CACHE.clear();ConnectedCreatureMesh.invalidate();com.codex.blacklight.client.renderer.AuthoredInfectionParts.reload();});
    }

    public AABB bounds() { return bounds; }
    public float referenceScale() { return referenceScale; }
    public boolean boss() { return boss; }
    public String species() { return species; }

    public void render(PoseStack stack, VertexConsumer out, int light, int overlay, int alpha, Pose pose) {
        render(stack,out,light,overlay,alpha,pose,0);
    }
    public void render(PoseStack stack,VertexConsumer out,int light,int overlay,int alpha,Pose pose,float lateBody) {
        for (var root : roots) renderBone(root, stack, out, light, overlay, alpha, pose,Math.clamp(lateBody,0,1));
    }

    private static void renderBone(Bone bone, PoseStack stack, VertexConsumer out, int light, int overlay, int alpha, Pose pose,float lateBody) {
        stack.pushPose();
        try {
            stack.translate(bone.x, bone.y, bone.z); pose.apply(bone, stack); stack.translate(-bone.x, -bone.y, -bone.z);
            bone.draw(stack, out, light, overlay, alpha);
            bone.drawLate(stack,out,light,overlay,alpha,lateBody);
            for (var child : bone.children) renderBone(child, stack, out, light, overlay, alpha, pose,lateBody);
        } finally { stack.popPose(); }
    }
}
