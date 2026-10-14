package com.codex.blacklight.client.model;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.client.animation.BiomassSurface;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Blender surfaces authored in the Blacklight skeleton's joint-local space. */
public final class ClawArmModel extends Model {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(BlacklightNeoMod.MOD_ID, "claw_arm"), "main");
    private record Triangle(float[][] points, float[][] normals, int color, int[] vertices) { }
    private record Position(int x, int y, int z) {
        Position(float[] p) { this(Float.floatToIntBits(p[0]), Float.floatToIntBits(p[1]), Float.floatToIntBits(p[2])); }
    }
    private record Surface(BiomassSurface.Vertex[] vertices, float[] posed,float[] opacity) { }
    private final Map<String, Triangle[]> joints = new HashMap<>();
    private final Map<String, Surface> surfaces = new HashMap<>();

    public ClawArmModel(ModelPart unused) {
        this("claw_blender");
    }

    public ClawArmModel(String asset) {
        super(RenderType::entityCutoutNoCull);
        try (var stream = ClawArmModel.class.getResourceAsStream("/assets/blacklight/models/" + asset + ".json")) {
            if (stream == null) throw new IllegalStateException("Missing authored claw mesh");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var entry : root.getAsJsonObject("joints").entrySet()) {
                var source = entry.getValue().getAsJsonArray();
                Triangle[] triangles = new Triangle[source.size()];
                var indices = new HashMap<Position, Integer>();
                var unique = new ArrayList<float[]>();
                for (int i = 0; i < triangles.length; i++) {
                    var face = source.get(i).getAsJsonObject();
                    float[][] p = new float[3][3];
                    for (int v = 0; v < 3; v++) for (int axis = 0; axis < 3; axis++)
                        p[v][axis] = face.getAsJsonArray("p").get(v).getAsJsonArray().get(axis).getAsFloat();
                    float ux=p[1][0]-p[0][0], uy=p[1][1]-p[0][1], uz=p[1][2]-p[0][2];
                    float vx=p[2][0]-p[0][0], vy=p[2][1]-p[0][1], vz=p[2][2]-p[0][2];
                    float nx=uy*vz-uz*vy, ny=uz*vx-ux*vz, nz=ux*vy-uy*vx;
                    float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
                    if (len < 1e-8F) throw new IllegalStateException("Degenerate claw face");
                    var c = face.getAsJsonArray("c");
                    int color=0xFF000000 | c.get(0).getAsInt()<<16 | c.get(1).getAsInt()<<8 | c.get(2).getAsInt();
                    float[][] normals=new float[3][3];
                    for(int v=0;v<3;v++) {
                        if(face.has("n")) {
                            var n=face.getAsJsonArray("n").get(v).getAsJsonArray();
                            for(int axis=0;axis<3;axis++)normals[v][axis]=n.get(axis).getAsFloat();
                        } else normals[v]=new float[]{nx/len,ny/len,nz/len};
                    }
                    if(asset.equals("form_blade")&&entry.getKey().equals("Hand_R"))for(int v=0;v<3;v++){
                        float sy=BladeShape.lengthScale(p[v][1]);
                        p[v][0]*=BladeShape.WIDTH;p[v][2]*=BladeShape.WIDTH;p[v][1]=BladeShape.length(p[v][1]);
                        var n=normals[v];n[0]/=BladeShape.WIDTH;n[1]/=sy;n[2]/=BladeShape.WIDTH;
                        float size=(float)Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]);
                        for(int axis=0;axis<3;axis++)n[axis]/=size;
                    }
                    int[] vertices = new int[3];
                    for (int v = 0; v < 3; v++) {
                        Position position = new Position(p[v]);
                        Integer index = indices.get(position);
                        if (index == null) { index = unique.size(); indices.put(position, index); unique.add(p[v]); }
                        vertices[v] = index;
                    }
                    triangles[i] = new Triangle(p,normals,color,vertices);
                }
                joints.put(entry.getKey(), triangles);
                float length = .08F;
                for (float[] point : unique) length = Math.max(length, Math.abs(point[1]));
                boolean lower = entry.getKey().startsWith("Hand") || entry.getKey().startsWith("Tip");
                var descriptors = new BiomassSurface.Vertex[unique.size()];
                for (int v = 0; v < descriptors.length; v++) {
                    float[] point = unique.get(v);
                    descriptors[v] = BiomassSurface.vertex(point[0], point[1], point[2], length, lower);
                }
                surfaces.put(entry.getKey(), new Surface(descriptors, new float[descriptors.length * 6],new float[descriptors.length]));
            }
            // The whip hook is a separate draw group in the forearm's coordinate
            // system, not another anatomical segment. Both groups must sample
            // the same liquid field at their overlapping attachment surface.
            for (var entry : surfaces.entrySet()) if (entry.getKey().startsWith("Tip_")) {
                String handName = "Hand_" + entry.getKey().substring(4);
                Surface hand = surfaces.get(handName), tip = entry.getValue();
                if (hand == null) continue;
                float extent = .08F;
                for (var vertex : hand.vertices) extent = Math.max(extent, Math.abs(vertex.y()));
                for (var vertex : tip.vertices) extent = Math.max(extent, Math.abs(vertex.y()));
                surfaces.put(handName, sharedDistalSurface(hand, extent));
                surfaces.put(entry.getKey(), sharedDistalSurface(tip, extent));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load Blender claw mesh", e);
        }
    }

    private static Surface sharedDistalSurface(Surface surface, float extent) {
        var vertices = new BiomassSurface.Vertex[surface.vertices.length];
        for (int i = 0; i < vertices.length; i++) {
            var vertex = surface.vertices[i];
            vertices[i] = BiomassSurface.vertex(vertex.x(), vertex.y(), vertex.z(), extent, true);
        }
        return new Surface(vertices, surface.posed,surface.opacity);
    }

    public static LayerDefinition createLayer() {
        return LayerDefinition.create(new MeshDefinition(), 1, 1);
    }

    public void renderJoint(String joint, PoseStack stack, VertexConsumer consumer,
                            int light, int overlay, float progress, float talonLength) {
        renderJoint(joint,stack,consumer,light,overlay,progress,talonLength,null);
    }

    public void renderJoint(String joint, PoseStack stack, VertexConsumer consumer,
                             int light, int overlay, float progress, float talonLength,Matrix4f wristDelta) {
        renderJoint(joint,stack,consumer,light,overlay,progress,talonLength,wristDelta,Float.POSITIVE_INFINITY);
    }
    public void renderJoint(String joint, PoseStack stack, VertexConsumer consumer,
                             int light, int overlay, float progress, float talonLength,Matrix4f wristDelta,float distalLimit) {
        renderJoint(joint,stack,consumer,light,overlay,progress,talonLength,wristDelta,distalLimit,null);
    }
    public void renderJoint(String joint, PoseStack stack, VertexConsumer consumer,
                             int light, int overlay, float progress, float talonLength,Matrix4f wristDelta,float distalLimit,
                             com.codex.blacklight.combat.WeaponAttackScales.Shape hammer) {
        renderJoint(joint,stack,consumer,light,overlay,progress,talonLength,wristDelta,distalLimit,hammer,BiomassSurface.Frame.stable(progress),0);
    }
    public void renderJoint(String joint, PoseStack stack, VertexConsumer consumer,
                             int light, int overlay, float progress, float talonLength,Matrix4f wristDelta,float distalLimit,
                             com.codex.blacklight.combat.WeaponAttackScales.Shape hammer, BiomassSurface.Frame liquid) {
        float time = 0;
        if (liquid != null && liquid.liquid()) {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.level != null) time = minecraft.level.getGameTime() + minecraft.getFrameTime();
        }
        renderJoint(joint,stack,consumer,light,overlay,progress,talonLength,wristDelta,distalLimit,hammer,liquid,time);
    }
    public void renderJoint(String joint, PoseStack stack, VertexConsumer consumer,
                             int light, int overlay, float progress, float talonLength,Matrix4f wristDelta,float distalLimit,
                             com.codex.blacklight.combat.WeaponAttackScales.Shape hammer, BiomassSurface.Frame liquid, float time) {
        Triangle[] faces = joints.get(joint);
        if (faces == null || progress <= .001F || liquid != null && !liquid.visible()) return;
        if (liquid == null) liquid = BiomassSurface.Frame.stable(progress);
        float t=com.codex.blacklight.util.MathCompat.clamp(progress,0,1);
        float sheath=com.codex.blacklight.infection.InfectionTimeline.stage(t,.05F,.70F);
        float blade=com.codex.blacklight.infection.InfectionTimeline.stage(t,.48F,1);
        float width=.08F+.92F*sheath;
        boolean forearm=joint.startsWith("Hand")||joint.startsWith("Tip");
        Surface surface = surfaces.get(joint);
        float[] posed = surface.posed;
        for (int i = 0; i < surface.vertices.length; i++) {
            var vertex = surface.vertices[i];
            float y = vertex.y();
            if (forearm && y > .30F) y = .30F + (y - .30F) * blade * talonLength;
            float coating=BiomassSurface.coating(vertex,liquid);
            float tissueWidth=width+(1-width)*liquid.flow()*coating;
            surface.opacity[i]=1-liquid.flow()+liquid.flow()*coating;
            BiomassSurface.deform(vertex, liquid, time, vertex.x() * tissueWidth, y, vertex.z() * tissueWidth, posed, i * 6);
        }
        var growthPoint=new org.joml.Vector3f();var growthNormal=new org.joml.Vector3f();
        PoseStack.Pose pose=stack.last();
        for (Triangle face : faces) {
            if(forearm&&Math.max(face.points[0][1],Math.max(face.points[1][1],face.points[2][1]))>distalLimit)continue;
            // QUADS require a repeated final vertex for triangular authored surfaces.
            for (int v=0;v<4;v++) {
                int corner = Math.min(v,2), index = face.vertices[corner], offset = index * 6;
                float[] p=face.points[corner];
                float[] n=face.normals[corner];
                float x=posed[offset],y=posed[offset+1],z=posed[offset+2],nx=n[0],ny=n[1],nz=n[2];
                float wet = posed[offset+3];
                if (wet > 0) {
                    var vertex = surface.vertices[index];
                    float soften = wet * .24F;
                    nx = nx * (1 - soften) + vertex.radialX() * soften;
                    ny *= 1 - wet * .12F;
                    nz = nz * (1 - soften) + vertex.radialZ() * soften;
                    float normalLength = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (normalLength > 1E-6F) { nx /= normalLength; ny /= normalLength; nz /= normalLength; }
                }
                if(hammer!=null&&forearm) {
                    growthNormal.set(nx,ny,nz);
                    hammer.normal(x,y,z,growthNormal);
                    growthPoint.set(x,y,z);hammer.point(growthPoint);
                    x=growthPoint.x;y=growthPoint.y;z=growthPoint.z;
                    nx=growthNormal.x;ny=growthNormal.y;nz=growthNormal.z;
                }
                if(wristDelta!=null&&forearm){
                    // Blend only the wrist/palm and talons; the forearm keeps its elbow
                    // attachment. This transports the native wrist instead of posing it.
                    float w=net.minecraft.util.Mth.clamp((p[1]-.28F)/.10F,0,1);w=w*w*(3-2*w);
                    float tx=wristDelta.m00()*x+wristDelta.m10()*y+wristDelta.m20()*z+wristDelta.m30();
                    float ty=wristDelta.m01()*x+wristDelta.m11()*y+wristDelta.m21()*z+wristDelta.m31();
                    float tz=wristDelta.m02()*x+wristDelta.m12()*y+wristDelta.m22()*z+wristDelta.m32();
                    x+=(tx-x)*w;y+=(ty-y)*w;z+=(tz-z)*w;
                    float rx=wristDelta.m00()*nx+wristDelta.m10()*ny+wristDelta.m20()*nz;
                    float ry=wristDelta.m01()*nx+wristDelta.m11()*ny+wristDelta.m21()*nz;
                    float rz=wristDelta.m02()*nx+wristDelta.m12()*ny+wristDelta.m22()*nz;
                    nx+=(rx-nx)*w;ny+=(ry-ny)*w;nz+=(rz-nz)*w;
                    float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
                    if(length>1e-6F){nx/=length;ny/=length;nz/=length;}
                }
                int color = BiomassSurface.color(face.color, wet, posed[offset+4], posed[offset+5]);
                color=(Math.round(surface.opacity[index]*255)<<24)|(color&0xFFFFFF);
                consumer.vertex(pose.pose(),x,y,z).color(color)
                        .uv(liquid.liquid()?wet:.5F,.5F).overlayCoords(overlay).uv2(light)
                        .normal(pose.normal(),nx,ny,nz).endVertex();
            }
        }
    }

    private static float smooth(float t){t=net.minecraft.util.Mth.clamp(t,0,1);return t*t*(3-2*t);}
    private static float slope(float value,float width){float t=value/width;return t<=0||t>=1?0:6*t*(1-t)/width;}
    private static float integral(float value,float width){
        if(value<=0)return 0;if(value>=width)return value-width*.5F;
        float t=value/width;return width*(t*t*t-.5F*t*t*t*t);
    }

    @Override
    public void renderToBuffer(PoseStack stack, VertexConsumer consumer, int light, int overlay, float red,float green,float blue,float alpha) {
        renderJoint("Hand_R",stack,consumer,light,overlay,1,1);
    }
}
