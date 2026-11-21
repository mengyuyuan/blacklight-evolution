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
    private static final String DIR="greene/molt/";
    private static final int JOINTS=2483, BONES=JOINTS*16, SAMPLE_RATE=10;
    private static final String[] ATTR={"pos","normal","ink","uv","joint"};
    private final List<Integer> textures=new ArrayList<>(),vaos=new ArrayList<>(),buffers=new ArrayList<>(),programs=new ArrayList<>();
    private final Map<Integer,Map<String,Integer>> uniforms=new HashMap<>();
    private final int[] models=new int[5],counts=new int[5],grids=new int[4],gridCounts=new int[4];
    private final int[] dynamic=new int[3];
    private final int fissures,fissureCount;
    private final int model,strand,shell,wrap,mist,people,lines,frames,schedule,actorSize,pathSize,frameSize;
    private final float[] pivots=new float[36],hydra=new float[9],hunter=new float[9],matrix=new float[16];
    private final float[] timeline,bones=new float[BONES],boneUpload=new float[(JOINTS+1)*16],actors,paths;
    private final float[] animalMatrices=new float[68];

    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("blacklight",path);}
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
        var uv=new float[nx*ny*12];int k=0;
        int[] dx={0,1,1,0,1,0},dy={0,0,1,0,1,1};
        for(int y=0;y<ny;y++)for(int x=0;x<nx;x++)for(int v=0;v<6;v++){
            uv[k++]=(x+dx[v])/(float)nx;uv[k++]=(y+dy[v])/(float)ny;
        }
        grids[slot]=mesh(uv,2);gridCounts[slot]=uv.length/2;
    }
    private int dataTexture(int width,int rows,int channels,float[] data){
        int id=glGenTextures();textures.add(id);glBindTexture(GL_TEXTURE_2D,id);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        int format=channels==4?GL_RGBA:GL_RGB,internal=channels==4?GL_RGBA32F:GL_RGB32F;
        if(data==null)glTexImage2D(GL_TEXTURE_2D,0,internal,width,rows,0,format,GL_FLOAT,(ByteBuffer)null);
        else glTexImage2D(GL_TEXTURE_2D,0,internal,width,rows,0,format,GL_FLOAT,data);
        return id;
    }
    private GreeneMoltRenderer()throws IOException{
        try{
            model=link("model","model.vsh","model.fsh",ATTR);strand=link("strand","strand.vsh","strand.fsh","param");
            shell=link("shroud","shell.vsh","shell.fsh","param");wrap=link("wrap","wrap.vsh","strand.fsh","param");
            mist=link("dust","mist.vsh","mist.fsh","corner");
            ByteBuffer b;
            try(var input=new GZIPInputStream(open("scene.bin.gz"))){b=ByteBuffer.wrap(input.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);}
            if(b.getInt()!=0x474d4c31)throw new IOException("Invalid Greene scene");
            people=b.getInt();lines=b.getInt();frames=b.getInt();
            if(people!=202||lines!=214||frames!=231)throw new IOException("Invalid Greene timeline");
            for(int i=0;i<pivots.length;i++)pivots[i]=b.getFloat();
            glActiveTexture(GL_TEXTURE0);schedule=dataTexture(1,lines,4,values(b,lines*4));
            for(var contact:List.of(hydra,hunter))for(int i=0;i<9;i++)contact[i]=b.getFloat();
            for(int i=0;i<models.length;i++){counts[i]=b.getInt();models[i]=mesh(values(b,counts[i]*12),3,3,3,2,1);}
            actorSize=people*12;pathSize=lines*16*3;frameSize=BONES+actorSize+pathSize+68;
            if(b.remaining()!=4L*frames*frameSize)throw new IOException("Truncated Greene timeline");
            timeline=values(b,frames*frameSize);actors=new float[actorSize];paths=new float[pathSize];
            dynamic[0]=dataTexture(8,(JOINTS+1)/2,4,null);dynamic[1]=dataTexture(3,people,4,null);dynamic[2]=dataTexture(16,lines,3,null);
            try(var in=open("fissures.bin")){
                var data=ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
                if(data.remaining()%48!=0)throw new IOException("Invalid Greene fissure mesh");
                fissureCount=data.remaining()/48;fissures=mesh(values(data,fissureCount*12),3,3,3,2,1);
            }
            grid(0,64,7);grid(1,170,80);grid(2,57,6);
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
    private static int skin(String path){return Minecraft.getInstance().getTextureManager().getTexture(id(path)).getId();}
    private void drawModel(int i,int instances){glBindVertexArray(models[i]);glDrawArraysInstanced(GL_TRIANGLES,0,counts[i],instances);}
    private void gridPass(int effect,int slot,int instances,Matrix4f camera,float[] eye,float t,float light){
        glUseProgram(effect);mat(effect,"vp",camera);glUniform3fv(u(effect,"eye"),eye);
        scalar(effect,"time",t);scalar(effect,"sceneLight",light);
        glBindVertexArray(grids[slot]);glDrawArraysInstanced(GL_TRIANGLES,0,gridCounts[slot],instances);
    }
    private void render(EncounterMob boss,float partial,PoseStack stack,int packedLight,int[] skins){
        float t=Math.min(23,boss.greeneBattle().age(partial)/20F),frame=t*SAMPLE_RATE;
        int ai=(int)frame,bi=Math.min(frames-1,ai+1),a=ai*frameSize,b=bi*frameSize;
        // Hard rig handoff inside the sealed volume; never interpolate bones of different models.
        interpolate(bones,a,b,0,ai==131?0:frame-ai);interpolate(actors,a,b,BONES,frame-ai);
        interpolate(paths,a,b,BONES+actorSize,frame-ai);interpolate(animalMatrices,a,b,BONES+actorSize+pathSize,frame-ai);
        System.arraycopy(bones,0,boneUpload,0,BONES);
        upload(0,dynamic[0],8,(JOINTS+1)/2,GL_RGBA,boneUpload);upload(1,dynamic[1],3,people,GL_RGBA,actors);upload(4,dynamic[2],16,lines,GL_RGB,paths);
        bind(2,skins[0]);bind(3,skins[1]);bind(5,schedule);
        float rotation=(float)Math.toRadians(180-boss.greeneBattle().collapseYaw());
        var camera=new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(stack.last().pose()).rotateY(rotation).scale(GreeneForm.OUTBREAK_SCALE);
        var e=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(boss.getPosition(partial)).yRot(-rotation).scale(1/GreeneForm.OUTBREAK_SCALE);
        float[] eye={(float)e.x,(float)e.y,(float)e.z};
        float light=.38F+.62F*Math.min(1,Math.max((packedLight>>20)&15,(packedLight>>4)&15)/15F);
        int absent=boss.greeneBattle().missingMoltActors();
        glDisable(GL_CULL_FACE);glEnable(GL_DEPTH_TEST);glDepthMask(true);glDisable(GL_BLEND);glUseProgram(model);
        integer(model,"bones",0);integer(model,"actors",1);integer(model,"atlas",2);integer(model,"skins",3);
        integer(model,"paths",4);integer(model,"animal",6);integer(model,"absent",absent);
        glUniform3fv(u(model,"pivots"),pivots);glUniform3fv(u(model,"eye"),eye);scalar(model,"time",t);scalar(model,"sceneLight",light);
        mat(model,"camera",camera);mat(model,"root",new Matrix4f());boolean second=t>=13.2F;
        integer(model,"kind",3);glBindVertexArray(fissures);glDrawArrays(GL_TRIANGLES,0,fissureCount);
        integer(model,"kind",second?1:0);drawModel(second?1:0,1);integer(model,"kind",2);drawModel(2,people);
        integer(model,"kind",6);
        for(int i=0;i<4;i++){
            int k=i*17;float drain=animalMatrices[k+16];if(drain>=1 || (absent&(1<<(12+i)))!=0)continue;
            boolean h=i==0;bind(6,skins[h?2:3]);glUniform3fv(u(model,"hit"),h?hydra:hunter);
            scalar(model,"drain",drain);scalar(model,"radius",h?2.6F:1.8F);integer(model,"row0",people+i*3);
            System.arraycopy(animalMatrices,k,matrix,0,16);glUniformMatrix4fv(u(model,"root"),false,matrix);
            drawModel(h?3:4,1);
        }
        if(t<14.3F){glUseProgram(strand);integer(strand,"absent",absent);integer(strand,"paths",4);integer(strand,"timing",5);
            gridPass(strand,0,lines*3,camera,eye,t,light);}
        if(t>7.9F&&t<19.5F){gridPass(shell,1,1,camera,eye,t,light);gridPass(wrap,2,94,camera,eye,t,light);}
        if(t>2.35F&&t<20.5F){
            glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glDepthMask(false);
            gridPass(mist,3,580,camera,eye,t,light);
        }
    }
    public static void draw(EncounterMob boss,float partial,PoseStack stack,int light){
        int[] skins={skin("textures/entity/greene_goddess.png"),skin(DIR+"people.png"),skin("textures/entity/hydra_connected.png"),skin("textures/entity/hunter_connected.png")};
        try(var state=new State()){
            if(scene==null)scene=new GreeneMoltRenderer();
            scene.render(boss,partial,stack,light,skins);
        }catch(IOException ex){throw new IllegalStateException("Cannot load Greene molt",ex);}
    }
    public static void invalidate(){if(scene!=null){scene.close();scene=null;}}
    @Override public void close(){
        for(int v:vaos)glDeleteVertexArrays(v);for(int v:buffers)glDeleteBuffers(v);
        for(int v:textures)glDeleteTextures(v);for(int v:programs)glDeleteProgram(v);
    }
    private static final class State implements AutoCloseable {
        final int program=glGetInteger(GL_CURRENT_PROGRAM),vao=glGetInteger(GL_VERTEX_ARRAY_BINDING),buffer=glGetInteger(GL_ARRAY_BUFFER_BINDING);
        final int active=glGetInteger(GL_ACTIVE_TEXTURE),src=glGetInteger(GL_BLEND_SRC_RGB),dst=glGetInteger(GL_BLEND_DST_RGB),srcA=glGetInteger(GL_BLEND_SRC_ALPHA),dstA=glGetInteger(GL_BLEND_DST_ALPHA);
        final boolean blend=glIsEnabled(GL_BLEND),cull=glIsEnabled(GL_CULL_FACE),depth=glIsEnabled(GL_DEPTH_TEST),write=glGetBoolean(GL_DEPTH_WRITEMASK);
        final int[] texture=new int[7];
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
