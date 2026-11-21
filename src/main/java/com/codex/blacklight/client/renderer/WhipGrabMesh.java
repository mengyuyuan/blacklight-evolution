package com.codex.blacklight.client.renderer;

import com.codex.blacklight.power.PowerForm;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import static com.codex.blacklight.client.renderer.WhipGrabPath.BANDS;

/** Skins the project's weapon vertices along the grab. UVs and bright vein groups stay on their faces. */
public final class WhipGrabMesh {
    private record Vertex(Vec3 p,Vec3 n,double u,double v){
        Vertex lerp(Vertex b,double t){return new Vertex(p.lerp(b.p,t),n.lerp(b.n,t),u+(b.u-u)*t,v+(b.v-v)*t);}
    }
    private record Corner(int band,double weight,double x,double y,double z,Vec3 normal,float u,float v,Vec3 source) {}
    private record Face(Corner[] corners,int sourceFace,boolean cutting) {}
    private final List<Face> base=new ArrayList<>(),glow=new ArrayList<>();
    private final ResourceLocation atlas,emissive;
    private final Vec3 anchor;
    private final double start,end;
    static final WhipGrabMesh INSTANCE=new WhipGrabMesh();

    private WhipGrabMesh(){
        String asset="/assets/blacklight/models/form_whipfist.json";
        try(var in=Objects.requireNonNull(WhipGrabMesh.class.getResourceAsStream(asset))){
            var root=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            var parts=root.getAsJsonObject("parts");var faces=root.getAsJsonObject("joints").getAsJsonArray("Hand_R");
            anchor=vec(root.getAsJsonObject("anchors").getAsJsonArray("tether_R"));
            atlas=new ResourceLocation("blacklight",root.get("texture").getAsString());
            emissive=new ResourceLocation("blacklight",root.get("emissiveTexture").getAsString());
            var core=parts.getAsJsonObject("Whip distal muscle core / Hand_R");
            double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
            for(int i=core.get("start").getAsInt();i<core.get("start").getAsInt()+core.get("count").getAsInt();i++){
                for(var p:faces.get(i).getAsJsonObject().getAsJsonArray("p")){double y=p.getAsJsonArray().get(1).getAsDouble();lo=Math.min(lo,y);hi=Math.max(hi,y);}
            }
            // The authored end cap is oblique. Its minimum Y is a single corner;
            // stretching from it turns the wrist end into a hairline at long reach.
            // The first 30% contains the entire cap. Skin from the full section above it.
            start=lo+(hi-lo)*.30;end=hi;
            for(var part:parts.entrySet()){
                String name=part.getKey();var spec=part.getValue().getAsJsonObject();
                if(!spec.get("joint").getAsString().equals("Hand_R"))continue;
                boolean rigid=terminal(name);
                if(!rigid&&!name.startsWith("Whip distal muscle core /")&&!name.startsWith("Vein distal core "))continue;
                for(int j=spec.get("start").getAsInt();j<spec.get("start").getAsInt()+spec.get("count").getAsInt();j++){
                    var f=faces.get(j).getAsJsonObject();
                    var p=f.getAsJsonArray("p");var normal=f.getAsJsonArray("n");var uv=f.getAsJsonArray("uv");
                    var flat=vec(p.get(1).getAsJsonArray()).subtract(vec(p.get(0).getAsJsonArray()))
                            .cross(vec(p.get(2).getAsJsonArray()).subtract(vec(p.get(0).getAsJsonArray()))).normalize();
                    var tri=new ArrayList<Vertex>(3);
                    for(int k=0;k<3;k++){
                        var tex=uv.get(k).getAsJsonArray();
                        tri.add(new Vertex(vec(p.get(k).getAsJsonArray()),normal==null?flat:vec(normal.get(k).getAsJsonArray()),tex.get(0).getAsDouble(),tex.get(1).getAsDouble()));
                    }
                    var c=f.getAsJsonArray("c");boolean sharp=rigid&&c.get(0).getAsInt()==156&&c.get(1).getAsInt()==166&&c.get(2).getAsInt()==157;
                    var dest=f.has("emissive")&&f.get("emissive").getAsBoolean()?glow:base;
                    if(rigid){add(dest,tri,true,j,sharp);continue;}
                    // Split the original triangles only at bending stations; affine UV interpolation stays exact.
                    double min=tri.stream().mapToDouble(v->v.p.y).min().orElseThrow(),max=tri.stream().mapToDouble(v->v.p.y).max().orElseThrow();
                    int first=Math.max(0,(int)Math.floor((min-start)/(end-start)*BANDS));
                    int last=Math.min(BANDS-1,(int)Math.floor((max-start)/(end-start)*BANDS));
                    for(int band=first;band<=last;band++){
                        var clipped=clip(clip(tri,start+(end-start)*band/BANDS,true),start+(end-start)*(band+1)/BANDS,false);
                        add(dest,clipped,false,j,false);
                    }
                }
            }
        }catch(Exception e){throw new IllegalStateException("Cannot skin "+asset,e);}
    }
    public static boolean terminal(String part){return part.startsWith("Hook root cuff /")||part.startsWith("Forward main hook /")
            ||part.startsWith("Forward inner hook /")||part.startsWith("Hook outer guard /")||part.startsWith("Hook inner guard /")||part.startsWith("Vein hook root ");}
    private static Vec3 vec(JsonArray v){return new Vec3(v.get(0).getAsDouble(),v.get(1).getAsDouble(),v.get(2).getAsDouble());}
    private static List<Vertex> clip(List<Vertex> input,double y,boolean above){
        var out=new ArrayList<Vertex>(5);if(input.isEmpty())return out;
        Vertex a=input.get(input.size()-1);
        for(var b:input){
            boolean insideA=above?a.p.y>=y:a.p.y<=y,insideB=above?b.p.y>=y:b.p.y<=y;
            if(insideA!=insideB)out.add(a.lerp(b,(y-a.p.y)/(b.p.y-a.p.y)));
            if(insideB)out.add(b);a=b;
        }
        return out;
    }
    private void add(List<Face> out,List<Vertex> poly,boolean rigid,int face,boolean cutting){
        for(int i=2;i<poly.size();i++){
            var vertices=new Vertex[]{poly.get(0),poly.get(i-1),poly.get(i)};var corners=new Corner[3];
            if(vertices[1].p.subtract(vertices[0].p).cross(vertices[2].p.subtract(vertices[0].p)).lengthSqr()<1e-19)continue;
            for(int k=0;k<3;k++){
                var v=vertices[k];double t=Math.max(0,Math.min(1,(v.p.y-start)/(end-start)));
                // The traveling hooks share the equipped weapon's tether_R coordinate system.
                var local=rigid?v.p.subtract(anchor):v.p.subtract(new Vec3(.029+(anchor.x-.029)*t,v.p.y,.041+(anchor.z-.041)*t));
                double u=rigid?BANDS:t*BANDS;int band=Math.min(BANDS-1,(int)u);
                corners[k]=new Corner(band,u-band,local.x,local.y,local.z,v.n,(float)v.u,(float)v.v,v.p.subtract(anchor));
            }
            out.add(new Face(corners,face,cutting));
        }
    }
    void flush(MultiBufferSource.BufferSource buffers){buffers.endBatch(RenderType.entityCutoutNoCull(atlas));buffers.endBatch(RenderType.entityTranslucentEmissive(emissive));}
    void draw(AbstractClientPlayer actor,float partial,WhipGrabPath path,PoseStack stack,MultiBufferSource buffers,int light){
        var material=RenderType.entityCutoutNoCull(atlas);
        try(var contact=ConsumeBladeContact.begin(actor,PowerForm.WHIPFIST,this,"Hand_R",stack,partial)){
            draw(base,path,stack,buffers.getBuffer(material),light,contact!=null);
        }
        draw(glow,path,stack,buffers.getBuffer(RenderType.entityTranslucentEmissive(emissive)),0xf000f0,false);
    }
    private static void draw(List<Face> mesh,WhipGrabPath path,PoseStack stack,VertexConsumer out,int light,boolean contact){
        for(var face:mesh){
            for(int k=0;k<3;k++){
                var v=face.corners[k];var a=path.frame(v.band);var b=path.frame(v.band+1);
                var p=a.point(v.x,v.y,v.z).lerp(b.point(v.x,v.y,v.z),v.weight);
                var n=a.normal(v.normal).lerp(b.normal(v.normal),v.weight).normalize();
                if(contact&&face.cutting&&ConsumeBladeContact.accepts(face.sourceFace))ConsumeBladeContact.vertex(face.sourceFace,k,v.source,p);
                ConsumedMobVisuals.vertex(stack,out,light,p,n,0xffffffff,v.u,v.v);
                if(k==2)ConsumedMobVisuals.vertex(stack,out,light,p,n,0xffffffff,v.u,v.v);
            }
        }
    }
}
