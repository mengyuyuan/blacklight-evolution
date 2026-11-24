package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.GreeneGoddess;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.codex.blacklight.combat.GoddessRig;
import org.joml.Matrix4f;
import java.io.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

/** Rigid groups and sampled world-space joint transforms exported from Greene-second-form.blend. */
public final class GreeneGoddessRenderer extends EntityRenderer<GreeneGoddess> {
    private static final ResourceLocation MESH = ResourceLocation.fromNamespaceAndPath("blacklight","models/greene_fullbody.mesh.gz");
    private static final ResourceLocation COLONY = ResourceLocation.fromNamespaceAndPath("blacklight","models/greene_outbreak_fullbody.mesh.gz");
    private final ResourceLocation mesh;
    private final ResourceLocation bodySkin;
    private static final ResourceLocation OUTBREAK_SKIN = ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/greene_goddess_tissue.png");
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/greene_goddess.png");
    record Group(float[] transforms, float[] vertices, int[] colors, int skin, int carrier, int joint) {}
    private volatile Group[] groups;
    private Group[] jointOrder;
    private GoddessJaw jaw;
    private final GoddessLimbFlow.Profile[] flows=new GoddessLimbFlow.Profile[3];
    private final GoddessLimbFlow.WetSurface liquidSheen=new GoddessLimbFlow.WetSurface();
    private final Quaternionf from=new Quaternionf(), to=new Quaternionf();
    private final org.joml.Matrix4f transform=new org.joml.Matrix4f();
    private java.util.concurrent.CompletableFuture<Void> loading;
    void warm(){
        if(groups==null && loading==null)loading=java.util.concurrent.CompletableFuture.runAsync(this::load,net.minecraft.Util.backgroundExecutor());
    }
    private int frames, fps;

    public GreeneGoddessRenderer(EntityRendererProvider.Context context) { this(context,false); }
    GreeneGoddessRenderer(EntityRendererProvider.Context context,boolean outbreak) {
        super(context);mesh=outbreak?COLONY:MESH;bodySkin=outbreak?OUTBREAK_SKIN:SKIN;
    }
    @Override public ResourceLocation getTextureLocation(GreeneGoddess entity) { return SKIN; }
    @Override public void render(GreeneGoddess entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(180-yaw));
        draw(entity.tickCount+partial,stack,buffers,light,OverlayTexture.NO_OVERLAY);
        stack.popPose();
        super.render(entity,yaw,partial,stack,buffers,light);
    }

    private void load() {
        var manager=Minecraft.getInstance().getResourceManager();
        try (var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(manager.open(mesh))))) {
            if(in.readInt()!=0x47464d31) throw new IOException("Unknown Greene mesh version");
            var parts=new Group[in.readInt()];
            frames=in.readInt();
            fps=in.readInt();
            for(int p=0;p<parts.length;p++) {
                int count=in.readInt();
                float[] transforms=new float[frames*7], vertices=new float[count*8];
                int[] colors=new int[count];
                for(int i=0;i<transforms.length;i++) transforms[i]=in.readFloat();
                for(int i=0;i<count;i++) {
                    for(int c=0;c<8;c++) vertices[8*i+c]=in.readFloat();
                    colors[i]=in.readInt();
                }
                int skinIndex=-1;
                if(p>=35 && vertices.length>0){
                    float u=0,vv=0;for(int i=0;i<count;i++){u+=vertices[i*8+3];vv+=vertices[i*8+4];}
                    int col=(int)(2*u/count),row=(int)(4*vv/count);skinIndex=col+2*row;
                    for(int i=0;i<count;i++){vertices[i*8+3]=vertices[i*8+3]*2-col;vertices[i*8+4]=vertices[i*8+4]*4-row;}
                }
                parts[p]=new Group(transforms,vertices,colors,skinIndex,mesh.equals(COLONY)?GoddessRig.carrier(p):0,p);
            }
            jointOrder=parts.clone();
            if(mesh.equals(COLONY)){
                jaw=new GoddessJaw(parts[0]);
                parts=expandedColony(parts);
                for(int stage=0;stage<3;stage++)flows[stage]=GoddessLimbFlow.bind(parts,stage);
            }
            java.util.Arrays.sort(parts,java.util.Comparator.comparingInt(Group::skin));groups=parts;
        } catch(IOException e) { throw new IllegalStateException("Cannot load the Greene full-body mesh",e); }
    }
    private Group[] expandedColony(Group[] original){
        var expanded=java.util.Arrays.copyOf(original,GreeneColonies.JOINTS);
        int index=original.length;var q=new Quaternionf();var anchor=new org.joml.Vector3f();
        for(var a:GreeneColonies.GROUPS){
            var owner=original[a.owner()].transforms();var donor=original[a.source()].transforms();
            var delta=new float[3*frames];
            for(int f=0;f<frames;f++){
                int i=f*7;
                q.set(owner[i+3],owner[i+4],owner[i+5],owner[i+6]);
                anchor.set(a.x(),a.y(),a.z()).rotate(q);
                delta[f*3]=anchor.x+owner[i]-donor[i];
                delta[f*3+1]=anchor.y+owner[i+1]-donor[i+1];
                delta[f*3+2]=anchor.z+owner[i+2]-donor[i+2];
            }
            for(int j=0;j<12;j++){
                var src=original[a.source()+j];var t=src.transforms().clone();
                for(int f=0;f<frames;f++)for(int d=0;d<3;d++)t[f*7+d]+=delta[f*3+d];
                expanded[index]=new Group(t,src.vertices(),src.colors(),src.skin(),a.owner(),index);
                index++;
            }
        }
        return expanded;
    }
    private static void drawGroup(Group group,PoseStack.Pose pose,VertexConsumer out,int light,int overlay) {
        var v=group.vertices();
        for(int i=0;i<group.colors().length;i++) {
            int k=i*8;
            out.addVertex(pose,v[k],v[k+1],v[k+2]).setColor(group.colors()[i]).setUv(v[k+3],v[k+4])
                    .setOverlay(overlay).setLight(light).setNormal(pose,v[k+5],v[k+6],v[k+7]);
        }
    }
    // Shader packs receive the very same entity vertices and materials during emergence and combat.
    void drawMolt(float[] matrices,PoseStack stack,MultiBufferSource buffers,int light) {
        if(groups==null){warm();loading.join();}
        int skin=-2;
        VertexConsumer out=null;
        for(var group:groups){
            if(group.skin()!=skin){
                skin=group.skin();
                out=buffers.getBuffer(RenderType.entityCutoutNoCull(skin<0?bodySkin:CommonInfectedRenderer.skin(skin)));
            }
            stack.pushPose();
            transform.set(matrices,group.joint()*16);
            stack.mulPose(transform);
            drawGroup(group,stack.last(),out,light,OverlayTexture.NO_OVERLAY);
            stack.popPose();
        }
    }
    void writeJointMatrices(float ticks,float[] matrices) {
        if(groups==null){warm();loading.join();}
        float sample=ticks*fps/20F%frames;
        int frame=(int)sample, a=7*frame, b=7*((frame+1)%frames);
        float mix=sample-frame;
        for(int i=0;i<jointOrder.length;i++) {
            float[] t=jointOrder[i].transforms();
            from.set(t[a+3],t[a+4],t[a+5],t[a+6]);to.set(t[b+3],t[b+4],t[b+5],t[b+6]);
            transform.translationRotate(t[a]+mix*(t[b]-t[a]),t[a+1]+mix*(t[b+1]-t[a+1]),
                    t[a+2]+mix*(t[b+2]-t[a+2]),from.slerp(to,mix)).get(matrices,16*i);
        }
    }
    void draw(float ticks,PoseStack stack,MultiBufferSource buffers,int light,int overlay) {
        draw(ticks,stack,buffers,light,overlay,null,null,0);
    }
    void draw(float ticks,PoseStack stack,MultiBufferSource buffers,int light,int overlay,Matrix4f[] offsets,com.codex.blacklight.entity.EncounterMob boss,float partial) {
        if(groups==null){warm();loading.join();}
        var flow=GoddessLimbFlow.active(boss,partial,flows);
        if(flow!=null)liquidSheen.reset();
        float jawOpening=boss==null || boss.goddess.attacks.move()!=com.codex.blacklight.combat.GoddessAttacks.Move.SONIC_CRY?0:
                com.codex.blacklight.combat.GoddessTerrainCast.jawOpening(boss.goddess.attacks.age(partial));
        boolean speaking=jawOpening>.001F;
        boolean resident=false;
        if(mesh.equals(COLONY)){
            if(boss!=null)offsets=com.codex.blacklight.combat.GoddessCrownColonies.expanded(boss.goddess.attacks.crown,offsets,ticks,partial);
            var gpuOffsets=offsets;
            if(flow!=null || speaking){
                gpuOffsets=offsets.clone();
                var hidden=new Matrix4f().zero();
                for(int i=0;i<gpuOffsets.length;i++)if((speaking && i==0) || (flow!=null && flow.receives(i)))gpuOffsets[i]=hidden;
            }
            float radiance=boss==null?0:GreeneMoltRenderer.combatRadiance(boss,partial);
            if(com.codex.blacklight.client.ShaderPackCompat.active()){
                if(boss!=null)GreeneMoltRenderer.queueRadiance(boss,this,ticks,light,overlay,gpuOffsets,radiance);
            }else{
                resident=GreeneMoltRenderer.drawColony(this,ticks,stack,light,overlay,gpuOffsets,radiance);
            }
            if(resident && flow==null && !speaking)return;
        }
        float sample=(ticks*fps/20F)%frames;
        int frame=(int)sample, a=frame*7, b=((frame+1)%frames)*7;
        float mix=sample-frame;
        VertexConsumer out=null;int skinIndex=-2;
        var q=new Quaternionf(); var next=new Quaternionf();
        for(var group:groups) {
            int skinSlot=com.codex.blacklight.combat.GoddessCrownColonies.offsetIndex(offsets,group.joint(),group.carrier());
            if(offsets!=null && offsets[skinSlot].m33()==0)continue;
            boolean fluid=flow!=null && flow.receives(group.carrier());
            if(resident && !fluid && !(speaking && skinSlot==0))continue;
            if(skinIndex!=group.skin()){
                skinIndex=group.skin();out=buffers.getBuffer(RenderType.entityCutoutNoCull(skinIndex<0?bodySkin:CommonInfectedRenderer.skin(skinIndex)));
            }
            var t=group.transforms();
            if(fluid){
                q.set(t[a+3],t[a+4],t[a+5],t[a+6]);next.set(t[b+3],t[b+4],t[b+5],t[b+6]);
                var posed=new Matrix4f(offsets[skinSlot]).translate(t[a]+mix*(t[b]-t[a]),
                        t[a+1]+mix*(t[b+1]-t[a+1]),t[a+2]+mix*(t[b+2]-t[a+2])).rotate(q.slerp(next,mix));
                flow.draw(group,posed,stack.last(),out,light,overlay,liquidSheen);
                continue;
            }
            stack.pushPose();
            if(offsets!=null)stack.mulPose(offsets[skinSlot]);
            stack.translate(t[a]+mix*(t[b]-t[a]), t[a+1]+mix*(t[b+1]-t[a+1]), t[a+2]+mix*(t[b+2]-t[a+2]));
            q.set(t[a+3],t[a+4],t[a+5],t[a+6]);
            next.set(t[b+3],t[b+4],t[b+5],t[b+6]);
            stack.mulPose(q.slerp(next,mix));
            drawGroup(speaking && group.joint()==0 ? jaw.pose(jawOpening):group,stack.last(),out,light,overlay);
            stack.popPose();
        }
        if(flow!=null)liquidSheen.draw(stack.last(),buffers);
    }
}
