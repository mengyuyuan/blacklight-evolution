package com.codex.blacklight.client.model;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.client.renderer.ConsumeAnatomy;
import com.codex.blacklight.client.tuning.MercerAppearance;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** V4 cuboids skinned by the same final pose matrices as Blacklight's weapons and effects. */
public final class MercerPlayerMesh {
    public static final ResourceLocation TEXTURE=new ResourceLocation("blacklight","textures/entity/mercer_v4.png");
    private record Vertex(Vector3f p,Vector3f n,Vector3f blink,float u,float v,int a,int b,float weight) { }
    private record Part(int region,boolean shoulder,Vertex[] vertices) { }
    private final List<Part> parts=new ArrayList<>();
    private final MercerRig rig;
    private final float restHeight;
    private static MercerPlayerMesh instance;
    private static boolean attempted;
    private MercerPlayerMesh() throws java.io.IOException {
        try(var stream=MercerPlayerMesh.class.getResourceAsStream("/assets/blacklight/models/mercer_v4.json")) {
            if(stream==null)throw new java.io.IOException("Missing Mercer V4 mesh");
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("format").getAsInt()!=2)throw new java.io.IOException("Unknown Mercer mesh format");
            rig=new MercerRig(root.getAsJsonArray("bones"));
            restHeight=root.has("restHeight")?root.get("restHeight").getAsFloat():2F;
            if(!Float.isFinite(restHeight)||restHeight<1F||restHeight>4F)throw new java.io.IOException("Invalid Mercer rest height");
            for(var entry:root.getAsJsonArray("parts")) {
                var part=entry.getAsJsonObject();var values=part.getAsJsonArray("vertices");
                if(values.size()%4!=0)throw new java.io.IOException("Incomplete Mercer quad");
                var vertices=new Vertex[values.size()];
                for(int i=0;i<vertices.length;i++) {
                    var v=values.get(i).getAsJsonObject();var w=v.getAsJsonArray("w");var uv=v.getAsJsonArray("uv");
                    int a=rig.index(w.get(0).getAsString()),b=rig.index(w.get(1).getAsString());
                    vertices[i]=new Vertex(vector(v,"p"),vector(v,"n"),vector(v,"blink"),uv.get(0).getAsFloat(),uv.get(1).getAsFloat(),a,b,w.get(2).getAsFloat());
                }
                parts.add(new Part(part.get("region").getAsInt(),part.get("shoulder").getAsBoolean(),vertices));
            }
        }
    }
    private static Vector3f vector(JsonObject o,String key) {
        var a=o.getAsJsonArray(key);return new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());
    }
    public static void invalidate() { instance=null;attempted=false; }
    public static MercerPlayerMesh get() {
        if(!attempted) {
            attempted=true;
            try { instance=new MercerPlayerMesh(); }
            catch(java.io.IOException|RuntimeException e) { BlacklightNeoMod.LOGGER.error("Cannot load Mercer model; keeping player skin",e); }
        }
        return instance;
    }
    public static boolean active(AbstractClientPlayer player) { return MercerAppearance.active(player)&&get()!=null; }
    /** Three-block standing appearance; ordinary armour retains its own proportions. */
    public static float displayScale(AbstractClientPlayer player,float partial) {
        if(!active(player))return 1F;
        float armor=MercerArmorMesh.get()!=null?0F:com.codex.blacklight.power.UtilityPowerSystem.visualWeight(player,com.codex.blacklight.power.PowerForm.ARMOR,partial);
        float size=com.codex.blacklight.client.vehicle.VehicleSeatPose.appearanceScale(player.getBbHeight(),get().restHeight,
                3F/(.9375F*get().restHeight),player.getVehicle() instanceof com.codex.blacklight.entity.MilitaryVehicle);
        return net.minecraft.util.Mth.lerp(net.minecraft.util.Mth.clamp(armor,0F,1F),size,1F);
    }
    public MercerRig.Frame sample(AbstractClientPlayer player,float partial,Matrix4f[] source) {
        boolean grip=com.codex.blacklight.registry.ModAttachments.getData(player,com.codex.blacklight.registry.ModAttachments.GRAB_STAGE)!=0
                ||com.codex.blacklight.combat.BlockThrowSystem.held(player)!=null;
        float armor=MercerArmorMesh.get()!=null?0F:com.codex.blacklight.power.UtilityPowerSystem.visualWeight(player,com.codex.blacklight.power.PowerForm.ARMOR,partial);
        return rig.sample(source,grip,armor,displayScale(player,partial));
    }
    /** First-person sleeve in Minecraft's arm frame. HMI moves this frame and its
     * item together; the YSM full-body arm rig must not apply another camera offset. */
    public void renderHeldArm(PoseStack stack,VertexConsumer out,int light,int overlay,boolean left) {
        int region=left?3:2;
        float wrist=left?-.24375F:.24375F,anchor=left?.375F:-.375F;
        var p=new Vector3f();var n=new Vector3f();
        for(var part:parts)if(part.region==region)for(var v:part.vertices){
            p.set(-(v.p.x-wrist)*1.4F+anchor,-(v.p.y-.928125F)+.625F,v.p.z*1.4F);
            n.set(-v.n.x/1.4F,-v.n.y,v.n.z/1.4F).normalize();
            out.vertex(stack.last().pose(),p.x,p.y,p.z).color(-1).uv(v.u,v.v).overlayCoords(overlay).uv2(light).normal(stack.last().normal(),n.x,n.y,n.z).endVertex();
        }
    }
    public void render(BlacklightPlayerModel model,AbstractClientPlayer player,float partial,PoseStack stack,VertexConsumer out,
                       int light,int overlay,int color,Matrix4f[] skin,Matrix3f[] normals,boolean rightBiological,boolean leftBiological) {
        // Tick time (including partial) keeps eyelids independent of frame rate, pause and death/rejoin caches.
        float phase=(player.tickCount+partial+Math.floorMod(player.getUUID().hashCode(),71))%83F;
        float blink=phase>79?Math.max(0,1-Math.abs(phase-81)/2):0;
        var p=new Vector3f();var q=new Vector3f();var base=new Vector3f();var n=new Vector3f();var n2=new Vector3f();
        for(var part:parts) {
            boolean visible=switch(part.region) {
                case 1->model.head.visible;case 2->model.rightArm.visible;case 3->model.leftArm.visible;
                case 4->model.rightLeg.visible;case 5->model.leftLeg.visible;default->model.body.visible;
            };
            if(!visible && !(part.shoulder&&(part.region==2?rightBiological:leftBiological)))continue;
            if(out instanceof ConsumeAnatomy.Consumer anatomy)anatomy.anatomy(part.region);
            for(var v:part.vertices) {
                base.set(v.p).fma(blink,v.blink);
                skin[v.a].transformPosition(base,p);skin[v.b].transformPosition(base,q);p.lerp(q,v.weight);
                normals[v.a].transform(v.n,n);normals[v.b].transform(v.n,n2);n.lerp(n2,v.weight);
                if(n.lengthSquared()<1e-8F)n.set(v.n);n.normalize();
                out.vertex(stack.last().pose(),p.x,p.y,p.z).color(color).uv(v.u,v.v).overlayCoords(overlay).uv2(light).normal(stack.last().normal(),n.x,n.y,n.z).endVertex();
            }
        }
    }
}
