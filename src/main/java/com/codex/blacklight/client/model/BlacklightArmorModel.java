package com.codex.blacklight.client.model;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.client.renderer.BlacklightBoneRendering;
import com.codex.blacklight.client.renderer.ConsumeAnatomy;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Vanilla armour geometry/UVs, skinned with the same continuous field as the player.
 * HumanoidArmorLayer still owns equipment slots, dye, trims, textures and glint. */
public final class BlacklightArmorModel extends HumanoidModel<AbstractClientPlayer> {
    private record Vertex(Vector3f point,Vector3f normal,float u,float v) {}
    private record Surface(ModelPart part,int region,List<Vertex> vertices) {}
    private final BlacklightPlayerModel player;
    private final List<Surface> surfaces=new ArrayList<>();
    private final Matrix4f[] bindInverse;
    public BlacklightArmorModel(ModelPart root,BlacklightPlayerModel player) {
        super(root);this.player=player;
        bindInverse=PlayerMotionController.skeleton().getPoseMatrices(new BlacklightPose());
        for(var m:bindInverse)m.invert();
        capture(head,ConsumeAnatomy.HEAD);capture(hat,ConsumeAnatomy.HEAD);capture(body,ConsumeAnatomy.BODY);
        capture(rightArm,ConsumeAnatomy.RIGHT_ARM);capture(leftArm,ConsumeAnatomy.LEFT_ARM);
        capture(rightLeg,ConsumeAnatomy.RIGHT_LEG);capture(leftLeg,ConsumeAnatomy.LEFT_LEG);
    }
    private void capture(ModelPart part,int region) {
        var capture=new Capture();part.render(new PoseStack(),capture,0,0,0xFFFFFFFF);
        surfaces.add(new Surface(part,region,tessellate(capture.vertices,region)));
    }
    private static List<Vertex> tessellate(List<Vertex> input,int region) {
        var out=new ArrayList<Vertex>();
        for(int i=0;i+3<input.size();i+=4) {
            var a=input.get(i);var b=input.get(i+1);var c=input.get(i+2);var d=input.get(i+3);
            int columns=region==ConsumeAnatomy.HEAD?1:Math.max(1,(int)Math.ceil(Math.abs(b.point.y-a.point.y)*16));
            int rows=region==ConsumeAnatomy.HEAD?1:Math.max(1,(int)Math.ceil(Math.abs(d.point.y-a.point.y)*16));
            for(int y=0;y<rows;y++)for(int x=0;x<columns;x++) {
                float u=x/(float)columns,v=y/(float)rows,un=(x+1)/(float)columns,vn=(y+1)/(float)rows;
                out.add(interpolate(a,b,c,d,u,v));out.add(interpolate(a,b,c,d,un,v));
                out.add(interpolate(a,b,c,d,un,vn));out.add(interpolate(a,b,c,d,u,vn));
            }
        }
        return List.copyOf(out);
    }
    private static Vertex interpolate(Vertex a,Vertex b,Vertex c,Vertex d,float u,float v) {
        return new Vertex(new Vector3f(a.point).lerp(b.point,u).lerp(new Vector3f(d.point).lerp(c.point,u),v),new Vector3f(a.normal),
                lerp(lerp(a.u,b.u,u),lerp(d.u,c.u,u),v),lerp(lerp(a.v,b.v,u),lerp(d.v,c.v,u),v));
    }
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    private boolean covered(int region) {
        return switch(region) {
            case ConsumeAnatomy.HEAD->!player.head.visible;
            case ConsumeAnatomy.BODY->!player.body.visible;
            case ConsumeAnatomy.RIGHT_ARM->!player.rightArm.visible;
            case ConsumeAnatomy.LEFT_ARM->!player.leftArm.visible;
            case ConsumeAnatomy.RIGHT_LEG->!player.rightLeg.visible;
            case ConsumeAnatomy.LEFT_LEG->!player.leftLeg.visible;
            default->false;
        };
    }
    static float skinY(int region,float y) {
        return switch(region) {
            case ConsumeAnatomy.BODY,ConsumeAnatomy.RIGHT_ARM,ConsumeAnatomy.LEFT_ARM->Math.clamp(y,0,12);
            case ConsumeAnatomy.RIGHT_LEG,ConsumeAnatomy.LEFT_LEG->Math.clamp(y,12,24);
            default->y;
        };
    }
    @Override public void renderToBuffer(PoseStack stack,VertexConsumer out,int light,int overlay,int color) {
        var pose=player.matrices();if(pose==null){super.renderToBuffer(stack,out,light,overlay,color);return;}
        var skin=new Matrix4f[bindInverse.length];var normals=new Matrix3f[skin.length];
        for(int i=0;i<skin.length;i++){skin[i]=new Matrix4f(pose[i]).mul(bindInverse[i]);normals[i]=new Matrix3f(skin[i]).invert().transpose();}
        var rig=PlayerMotionController.skeleton();var p=new Vector3f();var other=new Vector3f();var n=new Vector3f();var nOther=new Vector3f();
        stack.pushPose();BlacklightBoneRendering.skeletonFrame(stack);
        try {
            for(var surface:surfaces) {
                if(!surface.part.visible||surface.part.skipDraw||covered(surface.region))continue;
                for(var vertex:surface.vertices) {
                    var weight=BlacklightSkinWeights.sample(surface.region,skinY(surface.region,vertex.point.y*16),false);
                    int first=rig.searchJointByName(weight.first()).getId(),second=rig.searchJointByName(weight.second()).getId();
                    p.set(-vertex.point.x,1.501F-vertex.point.y,vertex.point.z);other.set(p);
                    skin[first].transformPosition(p).lerp(skin[second].transformPosition(other),weight.secondWeight());
                    n.set(-vertex.normal.x,-vertex.normal.y,vertex.normal.z);nOther.set(n);
                    normals[first].transform(n).lerp(normals[second].transform(nOther),weight.secondWeight());if(n.lengthSquared()<1e-8F)n.set(0,1,0);n.normalize();
                    stack.last().pose().transformPosition(p);stack.last().transformNormal(n,n);
                    out.addVertex(p.x,p.y,p.z).setColor(color).setUv(vertex.u,vertex.v).setOverlay(overlay).setLight(light).setNormal(n.x,n.y,n.z);
                }
            }
        }finally{stack.popPose();}
    }
    private static final class Capture implements VertexConsumer {
        final List<Vertex> vertices=new ArrayList<>();Vertex current;
        public VertexConsumer addVertex(float x,float y,float z){current=new Vertex(new Vector3f(x,y,z),new Vector3f(),0,0);vertices.add(current);return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv(float u,float v){current=new Vertex(current.point,current.normal,u,v);vertices.set(vertices.size()-1,current);return this;}
        public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){current.normal.set(x,y,z);return this;}
    }
}
