package com.codex.blacklight.client.model;

import com.codex.blacklight.animation.PrecisionWingPose;
import com.codex.blacklight.animation.SovereignWingMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Accepted neutral mesh, losslessly packed and moved as two undistorted wings.
 * IO is shared once; per-draw work never rebuilds geometry or allocates per vertex. */
public final class PrecisionWingModel implements WingSurface {
    private record Mesh(int part,ResourceLocation color,ResourceLocation glow,float[] vertices,int[] indices,int[] emission){}
    private static final class Loaded { static final List<Mesh> MESHES=read(); }
    private final List<Mesh> meshes=Loaded.MESHES;
    private final Matrix4f[] parts={new Matrix4f(),new Matrix4f(),new Matrix4f()};
    private final List<float[]> prepared=new ArrayList<>();
    private final Vector3f[][] tips=new Vector3f[2][4];
    private final Vector3f[][] edge=new Vector3f[2][];

    public PrecisionWingModel(){
        for(var mesh:meshes)prepared.add(new float[mesh.vertices.length/10*6]);
        for(int side=0;side<2;side++){
            float sign=side==0?1:-1;
            // Traced on the actual front reference, then converted to this model's
            // skeleton space. These points move with the same rigid wing fan.
            float[][] pixels={{9,497},{62,603},{191,699},{310,633}};
            for(int i=0;i<4;i++)tips[side][i]=reference(pixels[i][0],pixels[i][1],sign);
            edge[side]=new Vector3f[]{reference(355,257,sign),reference(271,177,sign),reference(217,59,sign),reference(112,181,sign),reference(41,383,sign),reference(9,497,sign)};
        }
    }
    private static Vector3f reference(float x,float y,float side){return new Vector3f(side*(.84F-x/840*1.68F)*2.6F,(.71F-y/710*1.42F)*2.6F+1,.2F);}
    private static List<Mesh> read(){
        try(var source=PrecisionWingModel.class.getResourceAsStream("/assets/blacklight/wing_models/precision_rigid.bin.gz")){
            if(source==null)throw new IOException("Missing precision wing mesh");
            byte[] bytes;try(var gzip=new GZIPInputStream(source)){bytes=gzip.readNBytes(32*1024*1024+1);}
            if(bytes.length>32*1024*1024)throw new IOException("Precision wing output budget exceeded");
            try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
                if(in.readInt()!=0x50574731)throw new IOException("Unsupported precision wing format");
                int count=positive(in,12),totalVertices=0,totalIndices=0;var result=new ArrayList<Mesh>();
                for(int s=0;s<count;s++){
                    int part=in.readInt();if(part<0||part>2)throw new IOException("Unknown precision wing group");
                    var color=texture(in.readUTF());String glow=in.readUTF();var emissive=glow.isEmpty()?null:texture(glow);
                    int vertices=positive(in,300_000),indices=positive(in,600_000),emission=in.readInt();
                    totalVertices+=vertices;totalIndices+=indices;
                    if(totalVertices>400_000||totalIndices>600_000||indices%3!=0||emission<0||emission>indices||emission%3!=0||emission>0&&emissive==null)throw new IOException("Precision wing topology budget exceeded");
                    float[] values=new float[vertices*10];for(int i=0;i<values.length;i++){values[i]=in.readFloat();if(!Float.isFinite(values[i]))throw new IOException("Non-finite precision wing vertex");}
                    int[] faces=indices(in,indices,vertices),glowing=indices(in,emission,vertices);
                    result.add(new Mesh(part,color,emissive,values,faces,glowing));
                }
                if(in.read()!=-1)throw new IOException("Trailing precision wing data");return List.copyOf(result);
            }
        }catch(IOException|IllegalArgumentException ex){throw new IllegalStateException("Could not load precision wings",ex);}
    }
    private static ResourceLocation texture(String value)throws IOException{
        var id=ResourceLocation.parse(value);
        if(!id.getNamespace().equals("blacklight")||!id.getPath().startsWith("textures/wings_precision/")||!id.getPath().endsWith(".png"))throw new IOException("Invalid precision wing material");return id;
    }
    private static int positive(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<1||n>max)throw new IOException("Invalid precision wing array");return n;}
    private static int[] indices(DataInputStream in,int count,int vertices)throws IOException{
        int[] data=new int[count];for(int i=0;i<count;i++){data[i]=in.readInt();if(data[i]<0||data[i]>=vertices)throw new IOException("Precision wing index outside vertices");}return data;
    }
    @Override public void prepare(SovereignWingMotion.Pose motion,Vector3f axis,float deployment,float air,int order){
        for(int i=0;i<parts.length;i++)parts[i].set(PrecisionWingPose.transform(i,motion,axis,deployment,air,order));
    }
    @Override public void prepareRender(PoseStack stack){
        var point=new Vector3f();var normal=new Vector3f();var matrix=new Matrix4f();var normals=new Matrix3f();
        for(int s=0;s<meshes.size();s++){
            var mesh=meshes.get(s);var result=prepared.get(s);matrix.set(stack.last().pose()).mul(parts[mesh.part]);matrix.normal(normals);
            for(int v=0;v<mesh.vertices.length/10;v++){
                int source=v*10,target=v*6;matrix.transformPosition(mesh.vertices[source],mesh.vertices[source+1],mesh.vertices[source+2],point);
                normals.transform(mesh.vertices[source+3],mesh.vertices[source+4],mesh.vertices[source+5],normal).normalize();
                result[target]=point.x;result[target+1]=point.y;result[target+2]=point.z;result[target+3]=normal.x;result[target+4]=normal.y;result[target+5]=normal.z;
            }
        }
    }
    @Override public void draw(PoseStack stack,MultiBufferSource buffers,int light){
        for(int s=0;s<meshes.size();s++){
            var mesh=meshes.get(s);emit(s,false,buffers.getBuffer(RenderType.entityCutoutNoCull(mesh.color)),light);
            if(mesh.emission.length>0)emit(s,true,buffers.getBuffer(RenderType.eyes(mesh.glow)),LightTexture.FULL_BRIGHT);
        }
    }
    void emit(int surface,boolean glow,VertexConsumer output,int light){
        var mesh=meshes.get(surface);var xyz=prepared.get(surface);var faces=glow?mesh.emission:mesh.indices;
        int tint=glow?0xFF381F1F:-1,uvOffset=glow?8:6;
        for(int f=0;f<faces.length;f+=3)for(int corner=0;corner<4;corner++){
            int index=faces[f+Math.min(corner,2)],p=index*6,u=index*10+uvOffset;
            output.addVertex(xyz[p],xyz[p+1],xyz[p+2],tint,mesh.vertices[u],mesh.vertices[u+1],OverlayTexture.NO_OVERLAY,light,xyz[p+3],xyz[p+4],xyz[p+5]);
        }
    }
    @Override public Vector3f tip(String side,int number){int wing=side.equals("left")?0:1;return parts[wing+1].transformPosition(new Vector3f(tips[wing][number-1]));}
    @Override public Vector3f[] leadingEdge(String side){int wing=side.equals("left")?0:1;return Arrays.stream(edge[wing]).map(p->parts[wing+1].transformPosition(new Vector3f(p))).toArray(Vector3f[]::new);}
    int surfaceCount(){return meshes.size();}
    int triangles(){return meshes.stream().mapToInt(m->m.indices.length/3).sum();}
    int part(int surface){return meshes.get(surface).part;}
    float[] restVertices(int surface){return meshes.get(surface).vertices;}
    float[] drawnVertices(int surface){return prepared.get(surface);}
}
