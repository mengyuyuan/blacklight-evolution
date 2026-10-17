package com.codex.blacklight.client.model;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.client.renderer.WeaponMorphTracker;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Broad authored surfaces sharing Mercer's pose, rather than the vanilla body proportions. */
public final class MercerArmorMesh {
    public static final ResourceLocation TEXTURE=new ResourceLocation("blacklight","textures/entity/mercer_armor.png");
    private record Vertex(Vector3f p,Vector3f n,float u,float v,int bone) { }
    private record Part(int region,boolean shoulder,Vector3f center,Vertex[] vertices) { }
    private static final String[] REGIONS={"torso","head","right_arm","left_arm","right_leg","left_leg"};
    private final List<Part> parts=new ArrayList<>();
    private final MercerRig rig;
    private static MercerArmorMesh instance;
    private static boolean attempted;
    private MercerArmorMesh() throws java.io.IOException {
        try(var stream=getClass().getResourceAsStream("/assets/blacklight/models/mercer_armor.json")) {
            if(stream==null)throw new java.io.IOException("Missing Mercer armor mesh");
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("format").getAsInt()!=2)throw new java.io.IOException("Unknown armor mesh format");
            rig=new MercerRig(root.getAsJsonArray("bones"));
            for(var entry:root.getAsJsonArray("parts")) {
                var part=entry.getAsJsonObject();var data=part.getAsJsonArray("vertices");
                if(data.isEmpty()||data.size()%4!=0)throw new java.io.IOException("Incomplete armor surface");
                var vertices=new Vertex[data.size()];var center=new Vector3f();
                for(int i=0;i<vertices.length;i++) {
                    var v=data.get(i).getAsJsonObject();var uv=v.getAsJsonArray("uv");
                    vertices[i]=new Vertex(vector(v,"p"),vector(v,"n"),uv.get(0).getAsFloat(),uv.get(1).getAsFloat(),rig.index(v.getAsJsonArray("w").get(0).getAsString()));
                    center.add(vertices[i].p);
                }
                int region=part.get("region").getAsInt();
                if(region<0||region>=REGIONS.length)throw new java.io.IOException("Invalid armor region");
                parts.add(new Part(region,part.get("shoulder").getAsBoolean(),center.div(vertices.length),vertices));
            }
        }
    }
    private static Vector3f vector(JsonObject o,String name) {
        var a=o.getAsJsonArray(name);var v=new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());
        if(!v.isFinite())throw new IllegalArgumentException("Invalid armor vertex");return v;
    }
    public static MercerArmorMesh get() {
        if(!attempted) {
            attempted=true;
            try{instance=new MercerArmorMesh();}
            catch(java.io.IOException|RuntimeException e){BlacklightNeoMod.LOGGER.error("Cannot load Mercer armor; retaining ordinary armor",e);}
        }
        return instance;
    }
    public static boolean active(AbstractClientPlayer player){return MercerPlayerMesh.active(player)&&get()!=null;}
    public static void invalidate(){instance=null;attempted=false;}
    public static float growth(AbstractClientPlayer player,int region,float partial) {
        return BlockArmorModel.get().regionGrowth(player,REGIONS[region],partial);
    }
    public void render(AbstractClientPlayer player,PoseStack stack,MultiBufferSource buffers,int light,float partial,Matrix4f[] skin,int firstPersonSide) {
        if(player.isInvisible()||!BlockArmorModel.get().visible(player,partial))return;
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        var morph=WeaponMorphTracker.sample(player,partial);
        var normals=new Matrix3f[skin.length];for(int i=0;i<skin.length;i++)normals[i]=new Matrix3f(skin[i]).invert().transpose();
        float[] growth=new float[6];for(int i=0;i<6;i++)growth[i]=growth(player,i,partial);
        var p=new Vector3f();var n=new Vector3f();
        for(var part:parts) {
            if(firstPersonSide>=0&&part.region!=(firstPersonSide==1?3:2))continue;
            if((part.region==2||part.region==3)&&morph.coverage(part.region==3)>=.72F&&!part.shoulder)continue;
            float g=growth[part.region];if(g<=.005F)continue;
            for(var v:part.vertices) {
                p.set(v.p).sub(part.center).mul(g).add(part.center);skin[v.bone].transformPosition(p);
                normals[v.bone].transform(v.n,n).normalize();
                out.vertex(stack.last().pose(),p.x,p.y,p.z).color(0xFFFFFFFF).uv(v.u,v.v)
                    .overlayCoords(OverlayTexture.pack(0,player.hurtTime>0)).uv2(light).normal(stack.last().normal(),n.x,n.y,n.z).endVertex();
            }
        }
    }
    /** First-person poses already contain camera positioning, so keep their attachment anchors. */
    public void renderFirstPerson(AbstractClientPlayer player,PoseStack stack,MultiBufferSource buffers,int light,float partial,Matrix4f[] source,int side) {
        render(player,stack,buffers,light,partial,rig.sample(source,false,1F,1F).skin(),side);
    }
}
