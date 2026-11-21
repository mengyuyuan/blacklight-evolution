package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.GreeneGoddess;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/greene_goddess.png");
    private record Group(float[] transforms, float[] vertices, int[] colors) {}
    private Group[] groups;
    private int frames, fps;

    public GreeneGoddessRenderer(EntityRendererProvider.Context context) { this(context,false); }
    GreeneGoddessRenderer(EntityRendererProvider.Context context,boolean outbreak) {
        super(context);mesh=outbreak?COLONY:MESH;
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
                parts[p]=new Group(transforms,vertices,colors);
            }
            groups=parts;
        } catch(IOException e) { throw new IllegalStateException("Cannot load the Greene full-body mesh",e); }
    }
    void draw(float ticks,PoseStack stack,MultiBufferSource buffers,int light,int overlay) {
        if(groups==null) load();
        float sample=(ticks*fps/20F)%frames;
        int frame=(int)sample, a=frame*7, b=((frame+1)%frames)*7;
        float mix=sample-frame;
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));
        var q=new Quaternionf(); var next=new Quaternionf();
        for(var group:groups) {
            var t=group.transforms();
            stack.pushPose();
            stack.translate(t[a]+mix*(t[b]-t[a]), t[a+1]+mix*(t[b+1]-t[a+1]), t[a+2]+mix*(t[b+2]-t[a+2]));
            q.set(t[a+3],t[a+4],t[a+5],t[a+6]);
            next.set(t[b+3],t[b+4],t[b+5],t[b+6]);
            stack.mulPose(q.slerp(next,mix));
            var pose=stack.last();
            var v=group.vertices();
            for(int i=0;i<group.colors().length;i++) {
                int k=i*8;
                out.addVertex(pose,v[k],v[k+1],v[k+2]).setColor(group.colors()[i]).setUv(v[k+3],v[k+4])
                        .setOverlay(overlay).setLight(light).setNormal(pose,v[k+5],v[k+6],v[k+7]);
            }
            stack.popPose();
        }
    }
}
