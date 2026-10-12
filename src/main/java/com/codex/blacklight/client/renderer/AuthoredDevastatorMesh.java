package com.codex.blacklight.client.renderer;

import com.google.gson.JsonArray;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.ByteOrder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Editable three-dimensional models sculpted from reference, with raised tissue and veins. */
public final class AuthoredDevastatorMesh {
    private record Mesh(float[] p,float[] n,float[] uv,int[] indices,float[] min,float[] max,
                        float scale,ResourceLocation texture,int[] colors,float[] shaped) { }
    private record RigidDraw(VertexBuffer buffer,ResourceLocation texture,Matrix4f pose,
                             Matrix3f normal,int light,int alpha,RigidBiomassMaterial.Curve curve) { }
    private static final Map<String,VertexBuffer> RIGID_BUFFERS=new HashMap<>();
    private static final List<RigidDraw> RIGID_DRAWS=new ArrayList<>();
    private static final class Loaded {
        static final Map<String,Mesh> ALL=read();
    }
    private static Map<String,Mesh> read(){
        var result=new HashMap<String,Mesh>();
        for(String resource:new String[]{"spikes","tendrils"}){
            try(var stream=AuthoredDevastatorMesh.class.getResourceAsStream("/assets/blacklight/models/devastators/"+resource+".json")){
                if(stream==null)throw new IllegalStateException("Missing authored model "+resource);
                var data=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for(var entry:data.getAsJsonObject("meshes").entrySet()){
                    var m=entry.getValue().getAsJsonObject();
                    float[] p=array(m.getAsJsonArray("positions")),n=array(m.getAsJsonArray("normals")),uv=array(m.getAsJsonArray("uvs"));
                    int[] indices=new int[m.getAsJsonArray("indices").size()],colors=new int[p.length/3];
                    for(int i=0;i<indices.length;i++)indices[i]=m.getAsJsonArray("indices").get(i).getAsInt();
                    for(int i=0;i<colors.length;i++)colors[i]=m.getAsJsonArray("colors").get(i).getAsInt();
                    if(p.length%3!=0||n.length!=p.length||uv.length!=p.length/3*2||indices.length%3!=0||indices.length>60_000)
                        throw new IllegalStateException("Invalid authored effect model "+entry.getKey());
                    for(int index:indices)if(index<0||index>=p.length/3)throw new IllegalStateException("Model index outside vertices");
                    var bounds=m.getAsJsonObject("bounds");
                    result.put(entry.getKey(),new Mesh(p,n,uv,indices,array(bounds.getAsJsonArray("min")),array(bounds.getAsJsonArray("max")),
                            1,ConsumedMobVisuals.WHITE,colors,new float[p.length*2]));
                }
            }catch(java.io.IOException error){throw new IllegalStateException("Cannot read authored model "+resource,error);}
        }
        return Map.copyOf(result);
    }
    private static float[] array(JsonArray a){float[] values=new float[a.size()];for(int i=0;i<values.length;i++){values[i]=a.get(i).getAsFloat();if(!Float.isFinite(values[i]))throw new IllegalStateException("Non-finite effect coordinate");}return values;}
    private static Mesh mesh(String id){var mesh=Loaded.ALL.get(id);if(mesh==null)throw new IllegalStateException("Missing authored effect "+id);return mesh;}
    public static void finish(MultiBufferSource.BufferSource buffers){
        // Flush the dynamic tissue consumer before changing VAO/shader. The
        // caller has finished its complete effect pass; no consumer survives it.
        for(var texture:Loaded.ALL.values().stream().map(Mesh::texture).distinct().toList())
            buffers.endBatch(LiquidBiomassMaterial.mesh(texture));
        if(RIGID_DRAWS.isEmpty())return;
        net.minecraft.client.renderer.RenderType material=null;
        try{
            var shader=RigidBiomassMaterial.shader();
            if(shader==null)return; // A resource reload also clears this queue.
            for(var draw:RIGID_DRAWS){
                var next=RigidBiomassMaterial.mesh(draw.texture);
                if(material!=next){
                    if(material!=null)material.clearRenderState();
                    material=next;material.setupRenderState();
                }
                RigidBiomassMaterial.instance(draw.pose,draw.normal,draw.light,draw.alpha,draw.curve);
                draw.buffer.bind();
                draw.buffer.drawWithShader(RenderSystem.getModelViewMatrix(),RenderSystem.getProjectionMatrix(),shader);
            }
        }finally{
            VertexBuffer.unbind();
            if(material!=null)material.clearRenderState();
            RIGID_DRAWS.clear();
        }
    }
    public static boolean available(){return !Loaded.ALL.isEmpty();}
    public static int vertices(String id){return RigidBiomassMaterial.ready()?mesh(id).indices.length:mesh(id).indices.length/3*4;}
    public static int spike(PoseStack stack,MultiBufferSource buffers,int light,String id,Vec3 base,
                            float yaw,double growth,double size,int alpha){
        var m=mesh(id);
        if(RigidBiomassMaterial.ready()){
            // The sculpted positions, normals and colors stay on the GPU. Only
            // an instance transform and lighting change during growth/retraction.
            var buffer=RIGID_BUFFERS.computeIfAbsent(id,key->uploadRigid(m));
            RIGID_DRAWS.add(new RigidDraw(buffer,m.texture,rigidPose(stack,base,yaw,growth,m.scale*size),
                    rigidNormal(stack,yaw,growth),light,alpha,null));
            return m.indices.length;
        }
        var material=LiquidBiomassMaterial.mesh(m.texture);
        var out=buffers.getBuffer(material);double sine=Math.sin(yaw),cosine=Math.cos(yaw);
        double scale=m.scale*size;
        // Grow the real irregular silhouette from its root; retain X/Z width.
        double vertical=Math.max(.001,growth),horizontal=Math.sqrt(Math.max(.001,growth));
        for(int face=0;face<m.indices.length;face+=3)for(int corner=0;corner<4;corner++){
            int index=m.indices[face+Math.min(corner,2)],p=index*3,u=index*2;
            double x=m.p[p]*scale*horizontal,z=m.p[p+2]*scale*horizontal;
            double y=m.p[p+1]*scale*vertical;
            double nx=m.n[p]/horizontal,nz=m.n[p+2]/horizontal,ny=m.n[p+1]/vertical;
            double len=Math.sqrt(nx*nx+ny*ny+nz*nz);
            out.addVertex(stack.last(),(float)(base.x+x*cosine-z*sine),(float)(base.y+y),(float)(base.z+x*sine+z*cosine))
                    .setColor((alpha<<24)|(m.colors[index]&0xFFFFFF)).setUv(.8F,.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                    .setNormal(stack.last(),(float)((nx*cosine-nz*sine)/len),(float)(ny/len),(float)((nx*sine+nz*cosine)/len));
        }
        return m.indices.length/3*4;
    }
    private static VertexBuffer uploadRigid(Mesh mesh){
        RenderSystem.assertOnRenderThread();
        var buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);
        // A private builder cannot interrupt a dynamic effect's active consumer.
        // Indexed TRIANGLES retains every authored face while shared vertices
        // run the growth/Bezier shader once through the GPU's vertex cache.
        int vertices=mesh.p.length/3;
        var indexType=VertexFormat.IndexType.least(vertices);
        try(var bytes=new ByteBufferBuilder(vertices*DefaultVertexFormat.NEW_ENTITY.getVertexSize());
            var indexBytes=new ByteBufferBuilder(mesh.indices.length*indexType.bytes)){
            var out=new BufferBuilder(bytes,VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.NEW_ENTITY);
            for(int index=0;index<vertices;index++){
                int p=index*3;
                out.addVertex(mesh.p[p],mesh.p[p+1],mesh.p[p+2],0xFF000000|(mesh.colors[index]&0xFFFFFF),
                        .8F,.5F,OverlayTexture.NO_OVERLAY,0,mesh.n[p],mesh.n[p+1],mesh.n[p+2]);
            }
            // The full-format addVertex writes all attributes immediately. Use
            // its bytes with the authored index count instead of sequential
            // BufferBuilder topology; both MeshData/result are owned by upload.
            var data=bytes.build();
            if(data==null)throw new IllegalStateException("Empty authored GPU mesh");
            buffer.bind();buffer.upload(new MeshData(data,new MeshData.DrawState(DefaultVertexFormat.NEW_ENTITY,
                    vertices,mesh.indices.length,VertexFormat.Mode.TRIANGLES,indexType)));
            indexBytes.reserve(mesh.indices.length*indexType.bytes);
            var indices=indexBytes.build();
            if(indices==null)throw new IllegalStateException("Empty authored GPU indices");
            var packed=indices.byteBuffer().order(ByteOrder.nativeOrder());
            for(int index:mesh.indices){
                if(indexType==VertexFormat.IndexType.SHORT)packed.putShort((short)index);
                else packed.putInt(index);
            }
            buffer.uploadIndexBuffer(indices);
            return buffer;
        }catch(RuntimeException|Error error){buffer.close();throw error;}
        finally{VertexBuffer.unbind();}
    }
    // Package-visible for differential tests against the previous CPU path.
    static Matrix4f rigidPose(PoseStack stack,Vec3 base,float yaw,double growth,double size){
        double vertical=Math.max(.001,growth),horizontal=Math.sqrt(vertical);
        // JOML's Y rotation has the opposite sign to the authored X/Z convention.
        return new Matrix4f(stack.last().pose()).translate((float)base.x,(float)base.y,(float)base.z)
                .rotateY(-yaw).scale((float)(size*horizontal),(float)(size*vertical),(float)(size*horizontal));
    }
    static Matrix3f rigidNormal(PoseStack stack,float yaw,double growth){
        double vertical=Math.max(.001,growth),horizontal=Math.sqrt(vertical);
        return new Matrix3f(stack.last().normal()).rotateY(-yaw)
                .scale((float)(1/horizontal),(float)(1/vertical),(float)(1/horizontal));
    }
    static void clearRigidBuffers(){
        if(!RenderSystem.isOnRenderThread()){
            RenderSystem.recordRenderCall(AuthoredDevastatorMesh::clearRigidBuffers);return;
        }
        for(var buffer:RIGID_BUFFERS.values())buffer.close();
        RIGID_BUFFERS.clear();RIGID_DRAWS.clear();
    }
    public static int strand(PoseStack stack,MultiBufferSource buffers,int light,String id,Vec3 root,
                             Vec3 control,Vec3 tip,double width,int alpha,double twist){
        var m=mesh(id);
        double sourceLength=m.max[1]-m.min[1],length=root.distanceTo(tip);
        if(length<.025)return 0;
        if(RigidBiomassMaterial.ready()){
            var buffer=RIGID_BUFFERS.computeIfAbsent(id,key->uploadRigid(m));
            // Curve coordinates stay near zero. A root translation in WorldPose
            // preserves precision even far from the world's origin.
            var curve=RigidBiomassMaterial.Curve.of(root,control,tip,m.min[1],sourceLength,length,m.scale*width,twist);
            var pose=new Matrix4f(stack.last().pose()).translate((float)root.x,(float)root.y,(float)root.z);
            RIGID_DRAWS.add(new RigidDraw(buffer,m.texture,pose,new Matrix3f(stack.last().normal()),light,alpha,curve));
            return m.indices.length;
        }
        var material=LiquidBiomassMaterial.mesh(m.texture);var out=buffers.getBuffer(material);
        // Deform each authored vertex once, then reuse it through the model index buffer.
        // No per-triangle Vec3 allocation or procedural replacement topology.
        double ax=root.x,ay=root.y,az=root.z,bx=control.x,by=control.y,bz=control.z,cx=tip.x,cy=tip.y,cz=tip.z;
        double c=Math.cos(twist),s=Math.sin(twist);
        double hx=cx-ax,hz=cz-az,hl=Math.sqrt(hx*hx+hz*hz);
        double refX=hl<.00001?1:-hz/hl,refZ=hl<.00001?0:hx/hl;
        for(int index=0;index<m.p.length/3;index++){
            int p=index*3,q=index*6;
            double t=Math.clamp((m.p[p+1]-m.min[1])/sourceLength,0,1),one=1-t;
            double tx=(bx-ax)*2*one+(cx-bx)*2*t,ty=(by-ay)*2*one+(cy-by)*2*t,tz=(bz-az)*2*one+(cz-bz)*2*t;
            double mag=Math.max(.00001,Math.sqrt(tx*tx+ty*ty+tz*tz));tx/=mag;ty/=mag;tz/=mag;
            double projection=refX*tx+refZ*tz;
            double rx=refX-projection*tx,ry=-projection*ty,rz=refZ-projection*tz;
            double rm=Math.max(.00001,Math.sqrt(rx*rx+ry*ry+rz*rz));rx/=rm;ry/=rm;rz/=rm;
            double ux=ry*tz-rz*ty,uy=rz*tx-rx*tz,uz=rx*ty-ry*tx;
            double taper=Math.max(.08,1-t*.86),cross=m.scale*width*taper;
            double x=(m.p[p]*c-m.p[p+2]*s)*cross,z=(m.p[p]*s+m.p[p+2]*c)*cross;
            m.shaped[q]=(float)(ax*one*one+2*bx*one*t+cx*t*t+rx*x+ux*z);
            m.shaped[q+1]=(float)(ay*one*one+2*by*one*t+cy*t*t+ry*x+uy*z);
            m.shaped[q+2]=(float)(az*one*one+2*bz*one*t+cz*t*t+rz*x+uz*z);
            double nx=m.n[p]*c-m.n[p+2]*s,nz=m.n[p]*s+m.n[p+2]*c,along=m.n[p+1]*sourceLength/length;
            double ox=rx*nx+ux*nz+tx*along,oy=ry*nx+uy*nz+ty*along,oz=rz*nx+uz*nz+tz*along;
            double nm=Math.max(.00001,Math.sqrt(ox*ox+oy*oy+oz*oz));
            m.shaped[q+3]=(float)(ox/nm);m.shaped[q+4]=(float)(oy/nm);m.shaped[q+5]=(float)(oz/nm);
        }
        for(int face=0;face<m.indices.length;face+=3)for(int corner=0;corner<4;corner++){
            int index=m.indices[face+Math.min(corner,2)],q=index*6,u=index*2;
            out.addVertex(stack.last(),m.shaped[q],m.shaped[q+1],m.shaped[q+2]).setColor((alpha<<24)|(m.colors[index]&0xFFFFFF))
                    .setUv(.8F,.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                    .setNormal(stack.last(),m.shaped[q+3],m.shaped[q+4],m.shaped[q+5]);
        }
        return m.indices.length/3*4;
    }
}
