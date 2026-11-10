package com.codex.blacklight.client.renderer;

import com.codex.blacklight.mixin.client.AgeableModelAccessor;
import com.codex.blacklight.mixin.client.ModelPartChildrenAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import java.util.*;

/** Where each pixel of a species texture sits on the bind-pose body: anatomy
 * role, model-space position (pixels, Y down) and outward normal. Lets the
 * blight paint armour on backs, muscle on flanks and keep faces readable,
 * instead of scattering one noise field over the UV sheet. */
final class InfectedSkinLayout {
    record Quad(float[] p,float[] u,float[] v,float[] n,int role) {}
    static final class Grid {
        final int width,height;final byte[] role;final float[] pos,normal;
        /** Per role: minX,minY,minZ,maxX,maxY,maxZ in model pixels. */
        final float[][] bounds=new float[ConsumeAnatomy.COUNT][];
        Grid(int w,int h){width=w;height=h;role=new byte[w*h];Arrays.fill(role,(byte)-1);pos=new float[w*h*3];normal=new float[w*h*3];}
        int role(int x,int y){return role[y*width+x];}
    }
    private static final Map<EntityModel<?>,List<Quad>> QUADS=new WeakHashMap<>();
    private InfectedSkinLayout(){}
    static void reload(){QUADS.clear();}
    static List<Quad> quads(EntityModel<?> model){
        if(model==null)return List.of();
        return QUADS.computeIfAbsent(model,InfectedSkinLayout::capture);
    }
    private static List<Quad> capture(EntityModel<?> model){
        var out=new ArrayList<Quad>();
        try{
            if(model instanceof HumanoidModel<?> h){
                walk(out,h.body,ConsumeAnatomy.BODY,new PoseStack());walk(out,h.head,ConsumeAnatomy.HEAD,new PoseStack());walk(out,h.hat,ConsumeAnatomy.HEAD,new PoseStack());
                walk(out,h.rightArm,ConsumeAnatomy.RIGHT_ARM,new PoseStack());walk(out,h.leftArm,ConsumeAnatomy.LEFT_ARM,new PoseStack());
                walk(out,h.rightLeg,ConsumeAnatomy.RIGHT_LEG,new PoseStack());walk(out,h.leftLeg,ConsumeAnatomy.LEFT_LEG,new PoseStack());
            }else if(com.codex.blacklight.client.animation.ReceiverModelPose.root(model)!=null){
                walk(out,com.codex.blacklight.client.animation.ReceiverModelPose.root(model),ConsumeAnatomy.BODY,new PoseStack());
            }else if(model instanceof AgeableModelAccessor a){
                for(var head:a.blacklight$heads())walk(out,head,ConsumeAnatomy.HEAD,new PoseStack());
                int[] ids={ConsumeAnatomy.BODY,ConsumeAnatomy.RIGHT_LEG,ConsumeAnatomy.LEFT_LEG,ConsumeAnatomy.RIGHT_ARM,ConsumeAnatomy.LEFT_ARM,ConsumeAnatomy.TAIL};int i=0;
                for(var body:a.blacklight$body())walk(out,body,ids[Math.min(i++,ids.length-1)],new PoseStack());
            }
        }catch(RuntimeException e){return List.of();}
        return List.copyOf(out);
    }
    private static void walk(List<Quad> out,ModelPart part,int role,PoseStack stack){
        var bind=part.getInitialPose();
        stack.pushPose();
        stack.translate(bind.x/16F,bind.y/16F,bind.z/16F);
        if(bind.xRot!=0||bind.yRot!=0||bind.zRot!=0)stack.mulPose(new Quaternionf().rotationZYX(bind.zRot,bind.yRot,bind.xRot));
        var accessor=(ModelPartChildrenAccessor)(Object)part;
        for(var cube:accessor.blacklight$cubes())cube.compile(stack.last(),new Capture(out,role),0,0,-1);
        accessor.blacklight$children().forEach((name,child)->walk(out,child,ConsumeAnatomy.named(name,role),stack));
        stack.popPose();
    }
    private static final class Capture implements VertexConsumer {
        final List<Quad> out;final int role;float[] p=new float[12],u=new float[4],v=new float[4];int index;
        Capture(List<Quad> out,int role){this.out=out;this.role=role;}
        @Override public void addVertex(float x,float y,float z,int color,float tu,float tv,int overlay,int light,float nx,float ny,float nz){
            p[index*3]=x*16;p[index*3+1]=y*16;p[index*3+2]=z*16;u[index]=tu;v[index]=tv;
            if(++index==4){float l=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);if(l<1e-6F)l=1;
                out.add(new Quad(p,u,v,new float[]{nx/l,ny/l,nz/l},role));p=new float[12];u=new float[4];v=new float[4];index=0;}
        }
        @Override public VertexConsumer addVertex(float x,float y,float z){return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        @Override public VertexConsumer setUv(float a,float b){return this;}
        @Override public VertexConsumer setUv1(int a,int b){return this;}
        @Override public VertexConsumer setUv2(int a,int b){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    /** Rasterise UV rectangles onto a grid of texture cells (one per native pixel). */
    static Grid grid(List<Quad> quads,int gw,int gh){
        var g=new Grid(gw,gh);
        for(var q:quads){
            float u0=q.u[0]*gw,v0=q.v[0]*gh,au=q.u[1]*gw-u0,av=q.v[1]*gh-v0,bu=q.u[3]*gw-u0,bv=q.v[3]*gh-v0;
            float la=au*au+av*av,lb=bu*bu+bv*bv;if(la<1e-4F||lb<1e-4F)continue;
            float minU=Float.MAX_VALUE,maxU=-Float.MAX_VALUE,minV=Float.MAX_VALUE,maxV=-Float.MAX_VALUE;
            for(int i=0;i<4;i++){minU=Math.min(minU,q.u[i]*gw);maxU=Math.max(maxU,q.u[i]*gw);minV=Math.min(minV,q.v[i]*gh);maxV=Math.max(maxV,q.v[i]*gh);}
            int x0=Math.max(0,(int)Math.floor(minU)),x1=Math.min(gw,(int)Math.ceil(maxU)),y0=Math.max(0,(int)Math.floor(minV)),y1=Math.min(gh,(int)Math.ceil(maxV));
            var b=g.bounds[q.role];
            for(int i=0;i<4;i++){
                if(b==null){b=new float[]{q.p[0],q.p[1],q.p[2],q.p[0],q.p[1],q.p[2]};g.bounds[q.role]=b;}
                for(int k=0;k<3;k++){b[k]=Math.min(b[k],q.p[i*3+k]);b[k+3]=Math.max(b[k+3],q.p[i*3+k]);}
            }
            for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){
                int at=y*gw+x;if(g.role[at]>=0)continue;
                float du=x+.5F-u0,dv=y+.5F-v0,s=(du*au+dv*av)/la,t=(du*bu+dv*bv)/lb;
                if(s<-.01F||s>1.01F||t<-.01F||t>1.01F)continue;
                g.role[at]=(byte)q.role;
                for(int k=0;k<3;k++){
                    float a=q.p[k],bb=q.p[3+k],d=q.p[9+k];
                    g.pos[at*3+k]=a+(bb-a)*s+(d-a)*t;g.normal[at*3+k]=q.n[k];
                }
            }
        }
        return g;
    }
}