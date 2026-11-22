package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.model.CommonInfectedModel;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.entity.BlacklightHydra;
import com.codex.blacklight.combat.GreeneHydraVents;
import com.codex.blacklight.registry.ModEntities;
import com.mojang.blaze3d.vertex.*;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/** Capture the registered mob renderer's posed quads before the feeding tendril deforms them. */
final class GreeneBroodVisuals implements VertexConsumer {
    private final Mob[] prey=new Mob[4];
    final float[] height=new float[4];
    final float[][] hitPoints=new float[4][9];
    private static final float[][] CONTACTS={{0,6.4F,-1, 0,4.2F,0, .9F,2.0F,-.2F},{0,1.4F,-.6F,.55F,.95F,-.4F,-.55F,1.05F,-.2F}};
    private float[] vertices=new float[4096];private int size,segment;
    private final float[] quad=new float[48];private int current=-1,quadSize;
    private void start(float x,float y,float z){
        finishVertex();current=quadSize*12;quad[current]=x;quad[current+1]=y;quad[current+2]=z;
        quad[current+6]=quad[current+7]=quad[current+8]=1;quad[current+11]=segment;
    }
    private void finishVertex(){
        if(current<0)return;current=-1;
        if(++quadSize<4)return;
        if(size+72>vertices.length)vertices=Arrays.copyOf(vertices,vertices.length*2);
        for(int idx:TRI){System.arraycopy(quad,idx*12,vertices,size,12);size+=12;}
        quadSize=0;
    }
    private static final int[] TRI={0,1,2,0,2,3};
    private void reset(){current=-1;size=quadSize=segment=0;}
    @Override public VertexConsumer addVertex(float x,float y,float z){start(x,y,z);return this;}
    @Override public VertexConsumer setColor(int r,int g,int b,int a){return this;}
    @Override public VertexConsumer setUv(float u,float v){quad[current+9]=u;quad[current+10]=v;return this;}
    @Override public VertexConsumer setUv1(int u,int v){return this;}
    @Override public VertexConsumer setUv2(int u,int v){return this;}
    @Override public VertexConsumer setNormal(float x,float y,float z){quad[current+3]=x;quad[current+4]=y;quad[current+5]=z;return this;}
    float[] civilian(){
        reset();var root=CommonInfectedModel.layer().bakeRoot();
        var pose=new PoseStack();pose.translate(0,1.5,0);pose.scale(-1,-1,1);
        draw(root.getChild("body"),pose,0);draw(root.getChild("head"),pose,1);draw(root.getChild("hat"),pose,1);
        String[] limbs={"left_arm","left_leg","right_arm","right_leg"};
        for(int i=0;i<4;i++){
            var limb=root.getChild(limbs[i]);var lower=limb.getChild(i%2==0?"forearm":"shin");
            lower.visible=false;draw(limb,pose,2+i*2);lower.visible=true;
            pose.pushPose();limb.translateAndRotate(pose);draw(lower,pose,3+i*2);pose.popPose();
        }
        return Arrays.copyOf(vertices,size);
    }
    private void draw(ModelPart part,PoseStack pose,int index){
        segment=index;part.render(pose,this,0xf000f0,OverlayTexture.NO_OVERLAY);finishVertex();
    }
    void pose(EncounterMob boss,int slot,float time,float partial,int light){
        reset();var mob=prey[slot];
        boolean hydra=GreeneHydraVents.isHydra(boss.greeneBattleData(),12+slot);
        if(mob==null || mob.level()!=boss.level() || hydra!=(mob instanceof BlacklightHydra)){
            mob=hydra?ModEntities.BLACKLIGHT_HYDRA.get().create(boss.level()):ModEntities.BLACKLIGHT_HUNTER.get().create(boss.level());
            if(mob instanceof BlacklightHydra h)h.setBodySize(BlacklightHydra.MAX_BODY_SIZE);
            prey[slot]=mob;
        }
        var ground=hydra?GreeneHydraVents.read(boss.greeneBattleData(),12+slot):null;
        mob.setPos(ground==null?boss.position():ground);mob.tickCount=(int)(time*20)+slot*9;
        mob.yBodyRot=mob.yBodyRotO=mob.yHeadRot=mob.yHeadRotO=180;
        mob.setYRot(180);mob.yRotO=180;
        if(!hydra && time<5)mob.walkAnimation.setSpeed(.68F);
        EntityRenderer<? super Mob> renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(mob);
        renderer.render(mob,180,partial,new PoseStack(),type->this,light);
        finishVertex();
        if(size==0)return;
        if(hydra)seatHydra(slot);
        float[] ref=CONTACTS[hydra?0:1],hit=hitPoints[slot];
        for(int k=0;k<3;k++){
            int h=k*3,nearest=0;float best=Float.MAX_VALUE;
            for(int v=0;v<size;v+=12){
                float x=vertices[v]-ref[h],y=vertices[v+1]-(hydra?height[slot]*ref[h+1]/7F:ref[h+1]),z=vertices[v+2]-ref[h+2],d=x*x+y*y+z*z;
                if(d<best){best=d;nearest=v;}
            }
            System.arraycopy(vertices,nearest,hit,h,3);
        }
        for(int v=0;v<size;v+=12){
            float best=Float.MAX_VALUE;int kBest=0;
            for(int k=0;k<3;k++){float x=vertices[v]-hit[k*3],y=vertices[v+1]-hit[k*3+1],z=vertices[v+2]-hit[k*3+2];float d=x*x+y*y+z*z;if(d<best){best=d;kBest=k;}}
            vertices[v+11]=kBest;
        }
    }
    // Seat the lowest ring of the posed skin itself; the Gecko model origin is above/beside that ring in some poses.
    private void seatHydra(int slot){
        float low=Float.POSITIVE_INFINITY,high=Float.NEGATIVE_INFINITY;
        for(int v=0;v<size;v+=12){low=Math.min(low,vertices[v+1]);high=Math.max(high,vertices[v+1]);}
        height[slot]=Math.max(.1F,high-low);
        float x=0,z=0;int n=0;
        for(int v=0;v<size;v+=12)if(vertices[v+1]<=low+.10F*height[slot]){x+=vertices[v];z+=vertices[v+2];n++;}
        x/=n;z/=n;
        for(int v=0;v<size;v+=12){vertices[v]-=x;vertices[v+1]-=low;vertices[v+2]-=z;}
    }
    ResourceLocation skin(int slot){
        var e=prey[slot];return Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e).getTextureLocation(e);
    }
    int vertexCount(){return size/12;}
    void upload(java.nio.FloatBuffer target){target.clear();target.put(vertices,0,size).flip();}
}
