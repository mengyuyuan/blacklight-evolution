package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Draws the mod's square, branching fibre using its original four-sided pixel atlas. */
final class ConsumeTendrilSegmentMesh {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/consume_charcoal_fiber.png");
    private ConsumeTendrilSegmentMesh() {}

    static void draw(PoseStack stack,VertexConsumer out,int light,Vec3[] path,double[] widths,Vec3 side,int color){
        draw(stack,out,light,path,widths,side,color,true);
    }

    static void draw(PoseStack stack,VertexConsumer out,int light,Vec3[] path,double[] widths,Vec3 side,int color,boolean jointed){
        if((color>>>24)==0)return;
        var pose=stack.last();
        ConsumeFiberShape.draw(path,widths,side,jointed,(span,fork,face,a,b,c,d,v0,v1)->{
            emitFace(pose,out,light,color,span,fork,face,a,b,c,d,v0,v1);
        });
    }

    private static void emitFace(PoseStack.Pose pose,VertexConsumer out,int light,int color,int span,boolean fork,
                                 int face,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float v0,float v1){
        Vec3 normal=b.subtract(a).cross(c.subtract(a));
        if(normal.lengthSqr()<1e-16)return;
        normal=normal.scale(1/normal.length());
        // Four eight-pixel columns. The side buds use the neighbouring column.
        int col=(face+(fork?1:0))&3;
        float u0=(col*8+.5F)/32F,u1=(col*8+7.5F)/32F;
        float low=(.5F+31*v0)/32F,high=(.5F+31*v1)/32F;
        vertex(pose,out,a,normal,u0,low,light,color);
        vertex(pose,out,b,normal,u1,low,light,color);
        vertex(pose,out,c,normal,u1,high,light,color);
        vertex(pose,out,d,normal,u0,high,light,color);
    }
    private static void vertex(PoseStack.Pose pose,VertexConsumer out,Vec3 p,Vec3 n,float u,float v,int light,int color){
        out.addVertex(pose,(float)p.x,(float)p.y,(float)p.z).setColor(color).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,(float)n.x,(float)n.y,(float)n.z);
    }
}
