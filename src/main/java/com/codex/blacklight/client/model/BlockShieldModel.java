package com.codex.blacklight.client.model;

import com.codex.blacklight.client.renderer.ShieldShardEffects;
import com.codex.blacklight.client.renderer.VisionPowerRenderer;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Source-oriented forearm grip and authored, physically missing damage cells. */
public final class BlockShieldModel {
    private record Part(Vector3f[] points, Vector3f[] normals, int material, int maxStage, boolean attachment) { }
    private static final class State {
        long stamp = Long.MIN_VALUE, hit;
        boolean enabled, broken;
        float from, strength, hp;
        int stage;
        double start;
        State(UtilityState data) { hit = data.hitAt(); hp = data.shield(); stage = data.damageStage(); }
    }
    private final List<Part> parts = new ArrayList<>();
    private final Map<AbstractClientPlayer,State> states = new WeakHashMap<>();
    private final int[][] faces;
    private final Vector3f right, up, front;
    public BlockShieldModel() {
        try (var input = getClass().getResourceAsStream("/assets/blacklight/models/shield_block.json")) {
            if (input == null) throw new IllegalStateException("Missing rebuilt shield model");
            var root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            right=vector(root.getAsJsonArray("right"));up=vector(root.getAsJsonArray("up"));front=vector(root.getAsJsonArray("front"));
            faces = new int[root.getAsJsonArray("faces").size()][4];
            for (int f=0; f<faces.length; f++) for (int i=0; i<4; i++) faces[f][i]=root.getAsJsonArray("faces").get(f).getAsJsonArray().get(i).getAsInt();
            for (var entry:root.getAsJsonArray("parts")) {
                var data=entry.getAsJsonObject(); var vertices=data.getAsJsonArray("points"); Vector3f[] points=new Vector3f[vertices.size()];
                for(int i=0;i<points.length;i++) { var p=vertices.get(i).getAsJsonArray();points[i]=new Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat()); }
                Vector3f[] normals=new Vector3f[faces.length];
                for(int f=0;f<faces.length;f++) normals[f]=new Vector3f(points[faces[f][1]]).sub(points[faces[f][0]])
                        .cross(new Vector3f(points[faces[f][2]]).sub(points[faces[f][0]])).normalize();
                parts.add(new Part(points,normals,data.get("material").getAsInt(),data.get("maxStage").getAsInt(),data.get("attachment").getAsBoolean()));
            }
        } catch(Exception e) { throw new IllegalStateException("Cannot load rebuilt block shield",e); }
    }
    private static Vector3f vector(com.google.gson.JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());
    }
    /** Rebase the source grip mesh to Minecraft's builtin shield plate centre. */
    public org.joml.Matrix4f itemFrame() {
        var basis=new org.joml.Matrix4f().m00(right.x).m01(right.y).m02(right.z)
                .m10(up.x).m11(up.y).m12(up.z).m20(front.x).m21(front.y).m22(front.z).invert();
        var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);
        for(var part:parts)for(var point:part.points){var local=basis.transformPosition(new Vector3f(point));min.min(local);max.max(local);}
        var center=min.add(max).mul(.5F);
        return new org.joml.Matrix4f().translation(0,0,-1.5F/16F).scale(1,-1,-1)
                .translate(-center.x,-center.y,-center.z).mul(basis);
    }
    public com.codex.blacklight.client.animation.FirstPersonPose.Result projectedItem(org.joml.Matrix4f frame,org.joml.Matrix4f projection) {
        float near=Float.POSITIVE_INFINITY,x=0,y=0;
        for(var part:parts)for(var point:part.points){var p=frame.transformPosition(new Vector3f(point));near=Math.min(near,-p.z);x=Math.max(x,Math.abs(p.x*projection.m00()/-p.z));y=Math.max(y,Math.abs(p.y*projection.m11()/-p.z));}
        return new com.codex.blacklight.client.animation.FirstPersonPose.Result(new org.joml.Matrix4f[0],near,x,y);
    }
    public void render(AbstractClientPlayer player, PoseStack stack, MultiBufferSource buffers, int light, float partial) {
        var data=player.getData(ModAttachments.PLAYER_STATE);
        if(!player.isAlive()||!data.isInfected()||!data.isPrototypeMode()){states.remove(player);return;}
        var utility=player.getData(ModAttachments.UTILITY_STATE);
        boolean active=UtilityPowerSystem.active(player,PowerForm.SHIELD),enabled=active&&!utility.broken();
        State state=states.get(player);
        if(state==null&&!active&&(utility.previousDefense()&PowerForm.SHIELD.bit())==0)return;
        double now=player.level().getGameTime()+partial;
        if(state==null) {
            state=new State(utility);states.put(player,state);
            state.strength=!active?1:0;state.start=utility.changedAt();
        }
        if(state.stamp!=utility.changedAt()||state.enabled!=enabled||state.broken!=utility.broken()) {
            state.from=state.strength;
            state.start=state.stamp==Long.MIN_VALUE?utility.changedAt():now;
            state.stamp=utility.changedAt();state.enabled=enabled;state.broken=utility.broken();
        }
        float step=(float)Math.max(0,now-state.start)*(enabled?7F/20:3F/20);
        state.strength=enabled?Math.min(1,state.from+step):Math.max(0,state.from-step);
        int stage=utility.damageStage();
        if(!VisionPowerRenderer.drawingSilhouettes() && utility.hitAt()!=state.hit) {
            if(now-utility.hitAt()>=0&&now-utility.hitAt()<3&&utility.shield()<state.hp) {
                int emitted=0;
                for(Part p:parts) {
                    if(p.attachment||p.maxStage<state.stage)continue;
                    boolean lost=p.maxStage<stage;
                    if(lost||emitted==0) {
                        ShieldShardEffects.capture(player,stack,p.points,p.material,light,partial,utility.broken());
                        emitted++;
                        if(!lost && stage==state.stage)break;
                    }
                }
            }
            state.hit=utility.hitAt();
        }
        state.hp=utility.shield();state.stage=stage;
        if(state.strength<=.001F && !(active&&utility.broken()))return;
        stack.pushPose();
        stack.mulPose(com.codex.blacklight.client.animation.DefenseImpactPose.shield(player,partial));
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(BlockArmorModel.TEXTURE));var pose=stack.last();
        for(Part p:parts) {
            if(p.maxStage<stage)continue;
            float strength=p.attachment&&active&&utility.broken()?1:state.strength;
            if(strength<=.001F)continue;
            // Grow about the wrist-local attachment, independently of the animated grapple.
            float grow=p.attachment?strength:strength*strength*(3-2*strength);
            for(int f=0;f<faces.length;f++)for(int v=0;v<4;v++) {
                var q=p.points[faces[f][v]];var n=p.normals[f];
                float u=(p.material+((v==1||v==2)?.969F:.031F))/6;
                out.addVertex(pose,q.x*grow,q.y*grow,q.z*grow).setColor(0xFFFFFFFF)
                        .setUv(u,v<2?.031F:.969F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,n.x,n.y,n.z);
            }
        }
        if(active&&utility.broken()&&!VisionPowerRenderer.drawingSilhouettes()) {
            float recovery=utility.rebuildProgress(now);
            // The rebuild remains non-blocking until the server restores shield HP.
            float pulse=.03F+.07F*recovery+(float)Math.sin(now*.65)*.008F;
            var veins=buffers.getBuffer(RenderType.entityTranslucent(com.codex.blacklight.client.renderer.ConsumedMobVisuals.WHITE));
            for(int lane=0;lane<3;lane++)for(int v=0;v<4;v++) {
                float y=(lane-1)*.04F+(v==1||v==2?.012F:0),z=v<2?-pulse:pulse;
                var point=new Vector3f(front).mul(.066F).fma(y,right).fma(z,up);
                veins.addVertex(pose,point.x,point.y,point.z).setColor(111,27,28,180).setUv(.5F,.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,front.x,front.y,front.z);
            }
        }
        stack.popPose();
    }
}
