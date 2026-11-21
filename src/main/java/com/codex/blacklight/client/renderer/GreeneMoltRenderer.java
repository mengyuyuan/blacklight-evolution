package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.combat.GreeneForm;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import static org.lwjgl.opengl.GL32C.*;

/** Instanced, world-space brood and shroud. All passes depth-test against the actual level. */
public final class GreeneMoltRenderer implements AutoCloseable {
    private static GreeneMoltRenderer scene;
    private record SceneData(ByteBuffer animation,ByteBuffer climb){}
    private static java.util.concurrent.CompletableFuture<SceneData> loading;
    private final int clamberTexture;
    private final float[] climbPose=new float[202*10*12];
    private final float[] climbFrames;
    private static ByteBuffer inflate(String name)throws IOException{
        try(var stream=new GZIPInputStream(open(name))){return ByteBuffer.wrap(stream.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);}
    }
    private static void beginLoad(){
        if(loading==null)loading=java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try{return new SceneData(inflate("scene.bin.gz"),inflate("clamber.bin.gz"));}
            catch(IOException ex){throw new java.io.UncheckedIOException(ex);}
        },net.minecraft.Util.backgroundExecutor());
    }
    public static void warm(){
        if(scene!=null)return;beginLoad();
        if(loading.isDone())try(var saved=new State()){
            scene=new GreeneMoltRenderer(loading.join());loading=null;
        }catch(IOException ex){throw new IllegalStateException("Cannot prepare Greene molt",ex);}
    }
    private static final String DIR="greene/molt/";
    private static final int JOINTS=5363, BONES=JOINTS*16, SAMPLE_RATE=10;
    private static final String[] ATTR={"pos","normal","ink","uv","joint"};
    private final List<Integer> textures=new ArrayList<>(),vaos=new ArrayList<>(),buffers=new ArrayList<>(),programs=new ArrayList<>();
    private final Map<Integer,Map<String,Integer>> uniforms=new HashMap<>();
    private final int[] gridIndex=new int[3];
    private final int[] models=new int[5],counts=new int[5],grids=new int[4],gridCounts=new int[4];
    private final GreeneBroodVisuals brood=new GreeneBroodVisuals();
    private final int[] preyVao=new int[4],preyVbo=new int[4],preyCounts=new int[4],preySkins=new int[4];
    private FloatBuffer staging=org.lwjgl.BufferUtils.createFloatBuffer(65536);
    private final int[] dynamic=new int[3];
    private final int fissures,fissureCount;
    private final int model,strand,shell,wrap,mist,people,lines,frames,schedule,actorSize,pathSize,frameSize;
    private final float[] pivots=new float[36],hydra=new float[9],hunter=new float[9],matrix=new float[16];
    private final float[] timeline,bones=new float[BONES],boneUpload=new float[(JOINTS+1)*16],actors,paths;
    private final float[] animalMatrices=new float[68];

    private static ResourceLocation id(String path){return new ResourceLocation("blacklight",path);}
    private static InputStream open(String path)throws IOException{return Minecraft.getInstance().getResourceManager().open(id(DIR+path));}
    private static String source(String path)throws IOException{
        try(var f=open(path)){return new String(f.readAllBytes(),StandardCharsets.UTF_8);}
    }
    private static float[] values(ByteBuffer b,int n){
        float[] v=new float[n];b.asFloatBuffer().get(v);b.position(b.position()+n*4);return v;
    }
    private int link(String name,String vs,String fs,String... attributes)throws IOException{
        int p=glCreateProgram();programs.add(p);
        for(int k=0;k<2;k++){
            int s=glCreateShader(k==0?GL_VERTEX_SHADER:GL_FRAGMENT_SHADER);
            try{
                glShaderSource(s,source(k==0?vs:fs));glCompileShader(s);
                if(glGetShaderi(s,GL_COMPILE_STATUS)==GL_FALSE)throw new IOException(name+" "+glGetShaderInfoLog(s));
                glAttachShader(p,s);
            }finally{glDeleteShader(s);}
        }
        for(int i=0;i<attributes.length;i++)glBindAttribLocation(p,i,attributes[i]);
        glLinkProgram(p);if(glGetProgrami(p,GL_LINK_STATUS)==GL_FALSE)throw new IOException(name+" "+glGetProgramInfoLog(p));
        uniforms.put(p,new HashMap<>());return p;
    }
    private int mesh(float[] vertices,int...sizes){
        int vao=glGenVertexArrays(),buffer=glGenBuffers();vaos.add(vao);buffers.add(buffer);
        glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,buffer);glBufferData(GL_ARRAY_BUFFER,vertices,GL_STATIC_DRAW);
        int stride=Arrays.stream(sizes).sum()*4,offset=0;
        for(int i=0;i<sizes.length;i++){glEnableVertexAttribArray(i);glVertexAttribPointer(i,sizes[i],GL_FLOAT,false,stride,offset);offset+=4*sizes[i];}
        return vao;
    }
    private void grid(int slot,int nx,int ny){
        float[] uv=new float[(nx+1)*(ny+1)*2];int k=0;
        for(int y=0;y<=ny;y++)for(int x=0;x<=nx;x++){uv[k++]=x/(float)nx;uv[k++]=y/(float)ny;}
        int[] idx=new int[nx*ny*6];k=0;
        for(int y=0;y<ny;y++)for(int x=0;x<nx;x++){
            int a=y*(nx+1)+x,b=a+1,c=a+nx+2,d=a+nx+1;
            idx[k++]=a;idx[k++]=b;idx[k++]=c;idx[k++]=a;idx[k++]=c;idx[k++]=d;
        }
        grids[slot]=mesh(uv,2);gridCounts[slot]=idx.length;
        gridIndex[slot]=glGenBuffers();buffers.add(gridIndex[slot]);glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,gridIndex[slot]);glBufferData(GL_ELEMENT_ARRAY_BUFFER,idx,GL_STATIC_DRAW);
    }
    private int dataTexture(int width,int rows,int channels,float[] data){
        int id=glGenTextures();textures.add(id);glBindTexture(GL_TEXTURE_2D,id);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        int format=channels==4?GL_RGBA:GL_RGB,internal=channels==4?GL_RGBA32F:GL_RGB32F;
        if(data==null)glTexImage2D(GL_TEXTURE_2D,0,internal,width,rows,0,format,GL_FLOAT,(ByteBuffer)null);
        else glTexImage2D(GL_TEXTURE_2D,0,internal,width,rows,0,format,GL_FLOAT,data);
        return id;
    }
    private GreeneMoltRenderer(SceneData dataSet)throws IOException{
        try{
            model=link("model","model.vsh","model.fsh",ATTR);strand=link("strand","strand.vsh","strand.fsh","param");
            shell=link("shroud","shell.vsh","shell.fsh","param");wrap=link("wrap","wrap.vsh","strand.fsh","param");
            mist=link("dust","mist.vsh","mist.fsh","corner");
            ByteBuffer b=dataSet.animation();
            var gait=dataSet.climb();if(gait.getInt()!=0x434c4d31||gait.getInt()!=145||gait.getInt()!=202||gait.getInt()!=10||gait.remaining()!=145*climbPose.length*4)throw new IOException("Invalid Greene climbing rig");
            climbFrames=values(gait,145*climbPose.length);glActiveTexture(GL_TEXTURE0);
            clamberTexture=dataTexture(30,202,4,null);
            if(b.getInt()!=0x474d4c32)throw new IOException("Invalid Greene scene");
            people=b.getInt();lines=b.getInt();frames=b.getInt();
            if(people!=202||lines!=214||frames!=231)throw new IOException("Invalid Greene timeline");
            for(int i=0;i<pivots.length;i++)pivots[i]=b.getFloat();
            glActiveTexture(GL_TEXTURE0);schedule=dataTexture(1,lines,4,values(b,lines*4));
            for(var contact:List.of(hydra,hunter))for(int i=0;i<9;i++)contact[i]=b.getFloat();
            for(int i=0;i<2;i++){counts[i]=b.getInt();models[i]=mesh(values(b,counts[i]*12),3,3,3,2,1);}
            var peopleMesh=brood.civilian();models[2]=mesh(peopleMesh,3,3,3,2,1);counts[2]=peopleMesh.length/12;
            for(int i=0;i<4;i++){
                preyVao[i]=mesh(new float[12],3,3,3,2,1);preyVbo[i]=buffers.get(buffers.size()-1);
            }
            actorSize=people*12;pathSize=lines*16*3;frameSize=BONES+actorSize+pathSize+68;
            if(b.remaining()!=4L*frames*frameSize)throw new IOException("Truncated Greene timeline");
            timeline=values(b,frames*frameSize);actors=new float[actorSize];paths=new float[pathSize];
            dynamic[0]=dataTexture(16,(JOINTS+3)/4,4,null);dynamic[1]=dataTexture(3,people,4,null);dynamic[2]=dataTexture(16,lines,3,null);
            try(var in=open("fissures.bin")){
                var data=ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
                if(data.remaining()%48!=0)throw new IOException("Invalid Greene fissure mesh");
                fissureCount=data.remaining()/48;fissures=mesh(values(data,fissureCount*12),3,3,3,2,1);
            }
            grid(0,40,4);grid(1,170,80);grid(2,57,6);
            grids[3]=mesh(new float[]{-1,-1,1,-1,1,1,-1,-1,1,1,-1,1},2);gridCounts[3]=6;
        }catch(IOException|RuntimeException ex){close();throw ex;}
    }
    private int u(int p,String name){return uniforms.get(p).computeIfAbsent(name,n->glGetUniformLocation(p,n));}
    private void integer(int p,String name,int value){glUniform1i(u(p,name),value);}
    private void scalar(int p,String name,float value){glUniform1f(u(p,name),value);}
    private void mat(int p,String n,Matrix4f m){glUniformMatrix4fv(u(p,n),false,m.get(matrix));}
    private void interpolate(float[] dst,int a,int b,int off,float mix){
        for(int i=0;i<dst.length;i++){float x=timeline[a+off+i];dst[i]=x+(timeline[b+off+i]-x)*mix;}
    }
    private static void bind(int unit,int texture){glActiveTexture(GL_TEXTURE0+unit);glBindTexture(GL_TEXTURE_2D,texture);}
    private static void upload(int unit,int texture,int w,int h,int format,float[] data){
        bind(unit,texture);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,w,h,format,GL_FLOAT,data);
    }
    private void posePrey(EncounterMob boss,float t,float partial,int light){
        for(int i=0;i<4;i++){
            preyCounts[i]=0;int k=i*17;
            if(animalMatrices[k+16]>=1 || (boss.greeneBattle().missingMoltActors()&(1<<(12+i)))!=0)continue;
            brood.pose(boss,i,t,partial,light);int count=brood.vertexCount();if(count==0)continue;
            if(staging.capacity()<count*12)staging=org.lwjgl.BufferUtils.createFloatBuffer(count*12);
            brood.upload(staging);glBindBuffer(GL_ARRAY_BUFFER,preyVbo[i]);glBufferData(GL_ARRAY_BUFFER,staging,GL_STREAM_DRAW);
            preyCounts[i]=count;preySkins[i]=Minecraft.getInstance().getTextureManager().getTexture(brood.skin(i)).getId();
            var hit=brood.hitPoints[i];
            for(int j=0;j<3;j++){
                int p=(people+i*3+j)*48;float x=hit[j*3],y=hit[j*3+1],z=hit[j*3+2];
                for(int axis=0;axis<3;axis++){
                    float tip=animalMatrices[k+axis]*x+animalMatrices[k+4+axis]*y+animalMatrices[k+8+axis]*z+animalMatrices[k+12+axis];
                    float gap=tip-paths[p+45+axis];
                    for(int point=1;point<16;point++){float u=point/15f;paths[p+point*3+axis]+=gap*u*u;}
                }
            }
        }
    }
    private static int skin(String path){return Minecraft.getInstance().getTextureManager().getTexture(id(path)).getId();}
    private void drawModel(int i,int instances){glBindVertexArray(models[i]);glDrawArraysInstanced(GL_TRIANGLES,0,counts[i],instances);}
    private void gridPass(int effect,int slot,int instances,Matrix4f camera,float[] eye,float t,float light){
        glUseProgram(effect);integer(effect,"fiberMap",12);integer(effect,"skinMap",13);mat(effect,"vp",camera);glUniform3fv(u(effect,"eye"),eye);
        scalar(effect,"time",t);scalar(effect,"sceneLight",light);
        glBindVertexArray(grids[slot]);if(slot<3)glDrawElementsInstanced(GL_TRIANGLES,gridCounts[slot],GL_UNSIGNED_INT,0L,instances);else glDrawArraysInstanced(GL_TRIANGLES,0,gridCounts[slot],instances);
    }
    private void render(EncounterMob boss,float partial,PoseStack stack,int packedLight,int[] skins){
        float t=Math.min(23,boss.greeneBattle().age(partial)/20F),frame=t*SAMPLE_RATE;
        int ai=(int)frame,bi=Math.min(frames-1,ai+1),a=ai*frameSize,b=bi*frameSize;
        // Hard rig handoff inside the sealed volume; never interpolate bones of different models.
        interpolate(bones,a,b,0,ai==131?0:frame-ai);interpolate(actors,a,b,BONES,frame-ai);
        interpolate(paths,a,b,BONES+actorSize,frame-ai);interpolate(animalMatrices,a,b,BONES+actorSize+pathSize,frame-ai);
        if(t<14.5F){
            int lo=Math.min(144,ai)*climbPose.length,hi=Math.min(144,bi)*climbPose.length;float mix=frame-ai;
            for(int i=0;i<climbPose.length;i++){float v=climbFrames[lo+i];climbPose[i]=v+(climbFrames[hi+i]-v)*mix;}
        }
        posePrey(boss,t,partial,packedLight);
        System.arraycopy(bones,0,boneUpload,0,BONES);
        upload(0,dynamic[0],16,(JOINTS+3)/4,GL_RGBA,boneUpload);upload(1,dynamic[1],3,people,GL_RGBA,actors);upload(4,dynamic[2],16,lines,GL_RGB,paths);
        bind(2,skins[0]);bind(3,skins[7]);bind(5,schedule);for(int i=0;i<4;i++)bind(7+i,skins[i+1]);
        if(t<14.5F)upload(11,clamberTexture,30,people,GL_RGBA,climbPose);
        bind(12,skins[5]);bind(13,skins[6]);
        float rotation=(float)Math.toRadians(180-boss.greeneBattle().collapseYaw());
        var camera=new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(stack.last().pose()).rotateY(rotation).scale(GreeneForm.OUTBREAK_SCALE);
        var e=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(boss.getPosition(partial)).yRot(-rotation).scale(1/GreeneForm.OUTBREAK_SCALE);
        float[] eye={(float)e.x,(float)e.y,(float)e.z};
        float light=.38F+.62F*Math.min(1,Math.max((packedLight>>20)&15,(packedLight>>4)&15)/15F);
        int absent=boss.greeneBattle().missingMoltActors();
        glDisable(GL_CULL_FACE);glEnable(GL_DEPTH_TEST);glDepthMask(true);glDisable(GL_BLEND);glUseProgram(model);
        integer(model,"liveForm",0);integer(model,"clamber",11);integer(model,"bones",0);integer(model,"actors",1);integer(model,"atlas",2);integer(model,"spiderSkin",3);for(int i=0;i<4;i++)integer(model,"civilian"+i,7+i);
        integer(model,"paths",4);integer(model,"animal",6);integer(model,"absent",absent);
        glUniform3fv(u(model,"pivots"),pivots);glUniform3fv(u(model,"eye"),eye);scalar(model,"time",t);scalar(model,"sceneLight",light);
        mat(model,"camera",camera);mat(model,"root",new Matrix4f());boolean second=t>=13.2F;
        integer(model,"kind",3);glBindVertexArray(fissures);glDrawArrays(GL_TRIANGLES,0,fissureCount);
        integer(model,"kind",second?1:0);drawModel(second?1:0,1);if(t<14.5F){integer(model,"kind",2);drawModel(2,people);}
        integer(model,"kind",6);
        for(int i=0;i<4;i++){
            int k=i*17;float drain=animalMatrices[k+16];if(drain>=1 || (absent&(1<<(12+i)))!=0)continue;
            boolean h=i==0;if(preyCounts[i]==0)continue;bind(6,preySkins[i]);glUniform3fv(u(model,"hit"),brood.hitPoints[i]);
            scalar(model,"drain",drain);scalar(model,"radius",h?2.6F:1.8F);integer(model,"row0",people+i*3);
            System.arraycopy(animalMatrices,k,matrix,0,16);glUniformMatrix4fv(u(model,"root"),false,matrix);
            glBindVertexArray(preyVao[i]);glDrawArrays(GL_TRIANGLES,0,preyCounts[i]);
        }
        if(t<14.3F){glUseProgram(strand);integer(strand,"absent",absent);integer(strand,"paths",4);integer(strand,"timing",5);
            gridPass(strand,0,lines*3,camera,eye,t,light);}
        if(t>7.9F&&t<19.5F){gridPass(shell,1,1,camera,eye,t,light);gridPass(wrap,2,94,camera,eye,t,light);}
        if(t>2.35F&&t<20.5F){
            glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glDepthMask(false);
            gridPass(mist,3,580,camera,eye,t,light);
        }
    }
    static boolean drawColony(GreeneGoddessRenderer colony,float ticks,PoseStack stack,int light,int overlay){
        var renderer=scene;
        if(renderer==null)return false;
        // The mesh is already resident after the transformation. Only joint poses change each frame.
        var mc=Minecraft.getInstance();
        int body=skin("textures/entity/greene_goddess_tissue.png");
        int[] civilians=new int[4];
        for(int i=0;i<civilians.length;i++)civilians[i]=mc.getTextureManager().getTexture(CommonInfectedRenderer.skin(i)).getId();
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        int lightmap=RenderSystem.getShaderTexture(2);
        try(var state=new State()){
            colony.writeJointMatrices(ticks,renderer.boneUpload);
            upload(0,renderer.dynamic[0],16,(JOINTS+3)/4,GL_RGBA,renderer.boneUpload);
            bind(2,body);bind(14,lightmap);
            int shader=renderer.model;
            glUseProgram(shader);glDisable(GL_CULL_FACE);glDisable(GL_BLEND);glEnable(GL_DEPTH_TEST);glDepthMask(true);
            renderer.integer(shader,"liveForm",1);renderer.integer(shader,"kind",1);renderer.integer(shader,"bones",0);
            renderer.integer(shader,"atlas",2);renderer.integer(shader,"levelLight",14);
            for(int i=0;i<4;i++){bind(7+i,civilians[i]);renderer.integer(shader,"civilian"+i,7+i);}
            renderer.scalar(shader,"damage",(overlay>>16)==3?1F:0F);
            renderer.scalar(shader,"coreFlash",(overlay&15)/15F);
            glUniform2i(renderer.u(shader,"lightUV"),(light>>4)&15,(light>>20)&15);
            var view=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(stack.last().pose());
            var eye=view.invert(new Matrix4f()).transformPosition(new org.joml.Vector3f());
            glUniform3f(renderer.u(shader,"eye"),eye.x,eye.y,eye.z);
            renderer.mat(shader,"camera",new Matrix4f(RenderSystem.getProjectionMatrix()).mul(view));
            renderer.mat(shader,"root",new Matrix4f());
            renderer.drawModel(1,1);
        }
        return true;
    }
    public static void draw(EncounterMob boss,float partial,PoseStack stack,int light){
        int[] skins=new int[8];skins[0]=skin("textures/entity/greene_goddess_tissue.png");
        skins[7]=skin("textures/entity/greene_spider_brood.png");
        for(int i=0;i<4;i++)skins[i+1]=Minecraft.getInstance().getTextureManager().getTexture(CommonInfectedRenderer.skin(i)).getId();
        skins[5]=Minecraft.getInstance().getTextureManager().getTexture(ConsumeTendrilSegmentMesh.TEXTURE).getId();
        skins[6]=skin("textures/entity/consume_charcoal_skin.png");
        try(var state=new State()){
            if(scene==null){beginLoad();scene=new GreeneMoltRenderer(loading.join());loading=null;}
            scene.render(boss,partial,stack,light,skins);
        }catch(IOException ex){throw new IllegalStateException("Cannot load Greene molt",ex);}
    }
    public static void invalidate(){loading=null;if(scene!=null){scene.close();scene=null;}}
    @Override public void close(){
        for(int v:vaos)glDeleteVertexArrays(v);for(int v:buffers)glDeleteBuffers(v);
        for(int v:textures)glDeleteTextures(v);for(int v:programs)glDeleteProgram(v);
    }
    private static final class State implements AutoCloseable {
        final int program=glGetInteger(GL_CURRENT_PROGRAM),vao=glGetInteger(GL_VERTEX_ARRAY_BINDING),buffer=glGetInteger(GL_ARRAY_BUFFER_BINDING);
        final int active=glGetInteger(GL_ACTIVE_TEXTURE),src=glGetInteger(GL_BLEND_SRC_RGB),dst=glGetInteger(GL_BLEND_DST_RGB),srcA=glGetInteger(GL_BLEND_SRC_ALPHA),dstA=glGetInteger(GL_BLEND_DST_ALPHA);
        final boolean blend=glIsEnabled(GL_BLEND),cull=glIsEnabled(GL_CULL_FACE),depth=glIsEnabled(GL_DEPTH_TEST),write=glGetBoolean(GL_DEPTH_WRITEMASK);
        final int[] texture=new int[15];
        State(){for(int i=0;i<texture.length;i++){glActiveTexture(GL_TEXTURE0+i);texture[i]=glGetInteger(GL_TEXTURE_BINDING_2D);}}
        private void toggle(int f,boolean on){if(on)glEnable(f);else glDisable(f);}
        public void close(){
            for(int i=0;i<texture.length;i++)bind(i,texture[i]);glActiveTexture(active);
            glUseProgram(program);glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,buffer);
            toggle(GL_BLEND,blend);toggle(GL_CULL_FACE,cull);toggle(GL_DEPTH_TEST,depth);
            glBlendFuncSeparate(src,dst,srcA,dstA);glDepthMask(write);
        }
    }
}
