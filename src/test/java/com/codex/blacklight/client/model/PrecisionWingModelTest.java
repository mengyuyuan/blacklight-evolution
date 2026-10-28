package com.codex.blacklight.client.model;

import com.codex.blacklight.animation.PrecisionWingPose;
import com.codex.blacklight.animation.SovereignWingMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PrecisionWingModelTest {
    @Test void realExportRetainsAllColourTrianglesAndMovesWithoutRubberStretch(){
        var model=new PrecisionWingModel();assertEquals(157041,model.triangles());
        float[][] mount=new float[model.surfaceCount()][];
        for(int order=0;order<2;order++)for(float air:new float[]{0,.25F,.5F,.75F,1})for(double phase:new double[]{0,.35,.7}){
            var pose=SovereignWingMotion.sample(phase,1,air,.8F,1,.5F,.3F,.3F,0);
            model.prepare(pose,new Vector3f(0,0,1),1,air,order);model.prepareRender(new PoseStack());
            for(int s=0;s<model.surfaceCount();s++){
                var rest=model.restVertices(s);var drawn=model.drawnVertices(s);int part=model.part(s);
                if(part==0&&mount[s]==null)mount[s]=drawn.clone();
                if(part==0)assertArrayEquals(mount[s],drawn,1e-5F,"The body attachment must not flap");
                float expected=part==0?1:PrecisionWingPose.GROUND_SCALE+(1-PrecisionWingPose.GROUND_SCALE)*air*air*(3-2*air);
                for(int v=1;v<rest.length/10;v+=7){
                    var a=new Vector3f(rest[(v-1)*10],rest[(v-1)*10+1],rest[(v-1)*10+2]);
                    float before=a.distance(rest[v*10],rest[v*10+1],rest[v*10+2]);
                    var b=new Vector3f(drawn[(v-1)*6],drawn[(v-1)*6+1],drawn[(v-1)*6+2]);
                    float after=b.distance(drawn[v*6],drawn[v*6+1],drawn[v*6+2]);
                    assertEquals(before*expected,after,2e-5F,"Every local distance must share one uniform scale");
                    if(air==0)assertTrue(drawn[v*6+1]>.08F,"Folded wing goes below ground");
                }
                for(float value:drawn)assertTrue(Float.isFinite(value));
            }
        }
    }
    @Test void physicalFoldOrderReversesAndWakeFollowsTheNewMesh(){
        var model=new PrecisionWingModel();var pose=SovereignWingMotion.sample(0,1,0,0,0,0,0,0,0);
        for(int order=0;order<2;order++){
            model.prepare(pose,new Vector3f(0,0,1),1,0,order);
            var left=model.tip("left",1);var right=model.tip("right",1);
            assertTrue(left.x<0&&right.x>0,"Rigid fans overlap across the back");
            assertTrue(order==0?left.z-right.z>.1F:right.z-left.z>.1F,"The outer wing really changes depth");
            var edge=model.leadingEdge("left");assertEquals(6,edge.length);assertEquals(0,left.distance(edge[5]),1e-6);
        }
    }
}
