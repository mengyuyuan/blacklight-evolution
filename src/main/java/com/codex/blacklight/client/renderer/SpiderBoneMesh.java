package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtil;

/** A spider cube is rigid within its animated bone. Bake its pivot and rotation only once. */
final class SpiderBoneMesh {
    // A face contains a normal, the flat-cube normal correction, then four XYZ/UV vertices.
    private static final int FACE=24;
    private final float[] faces;

    SpiderBoneMesh(GeoBone bone){
        int count=0;
        for(var cube:bone.getCubes())for(var quad:cube.quads())if(quad!=null)count++;
        faces=new float[count*FACE];
        var stack=new PoseStack();
        var point=new Vector3f();
        int i=0;
        for(var cube:bone.getCubes()){
            stack.pushPose();
            RenderUtil.translateToPivotPoint(stack,cube);
            RenderUtil.rotateMatrixAroundCube(stack,cube);
            RenderUtil.translateAwayFromPivotPoint(stack,cube);
            var pose=stack.last();
            var size=cube.size();
            int flat=(size.y==0||size.z==0?1:0)|(size.x==0||size.z==0?2:0)|(size.x==0||size.y==0?4:0);
            for(var quad:cube.quads()){
                if(quad==null)continue;
                pose.normal().transform(quad.normal(),point);
                faces[i++]=point.x;faces[i++]=point.y;faces[i++]=point.z;faces[i++]=flat;
                for(var vertex:quad.vertices()){
                    pose.pose().transformPosition(vertex.position(),point);
                    faces[i++]=point.x;faces[i++]=point.y;faces[i++]=point.z;
                    faces[i++]=vertex.texU();faces[i++]=vertex.texV();
                }
            }
            stack.popPose();
        }
    }
    void draw(PoseStack.Pose pose,VertexConsumer out,int light,int overlay,int color){
        var point=new Vector3f();var normal=new Vector3f();
        for(int i=0;i<faces.length;i+=FACE){
            pose.normal().transform(faces[i],faces[i+1],faces[i+2],normal);
            int flat=(int)faces[i+3];
            if(flat!=0){
                if((flat&1)!=0)normal.x=Math.abs(normal.x);
                if((flat&2)!=0)normal.y=Math.abs(normal.y);
                if((flat&4)!=0)normal.z=Math.abs(normal.z);
            }
            for(int j=i+4;j<i+FACE;j+=5){
                pose.pose().transformPosition(faces[j],faces[j+1],faces[j+2],point);
                out.addVertex(point.x,point.y,point.z,color,faces[j+3],faces[j+4],overlay,light,normal.x,normal.y,normal.z);
            }
        }
    }
}
