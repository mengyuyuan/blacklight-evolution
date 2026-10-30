package com.codex.blacklight.client.model;

import com.codex.blacklight.animation.PrecisionWingPose;
import com.codex.blacklight.animation.SovereignWingMotion;
import com.codex.blacklight.animation.BlacklightSkeleton;
import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.animation.FlightAnimationClips;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PrecisionWingModelTest {
    @Test void cleanedExportKeepsTheConnectedSurfaceAndMovesWithoutRubberStretch(){
        // 3,021 triangles belonged to detached reconstruction debris, not the wing.
        var model=new PrecisionWingModel();assertEquals(154020,model.triangles());
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
                    if(part!=0&&air>0&&(PrecisionWingPose.flightWeight(a.x,a.y,a.z)<1||PrecisionWingPose.flightWeight(rest[v*10],rest[v*10+1],rest[v*10+2])<1))continue;
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

    @Test void cleanedMeshHasNoFloatingIslandsAcrossMaterialAndRigSeams(){
        record Point(int x,int y,int z){}
        var model=new PrecisionWingModel();var points=new HashMap<Point,Integer>();
        var parent=new ArrayList<Integer>();var ids=new ArrayList<int[]>();
        for(int surface=0;surface<model.surfaceCount();surface++){
            var values=model.restVertices(surface);int[] current=new int[values.length/10];
            for(int v=0;v<current.length;v++){
                var key=new Point(Math.round(values[v*10]*100_000),Math.round(values[v*10+1]*100_000),Math.round(values[v*10+2]*100_000));
                current[v]=points.computeIfAbsent(key,p->{int id=parent.size();parent.add(id);return id;});
            }
            ids.add(current);
        }
        // Test the real triangle stream, including seams duplicated for UVs.
        for(int surface=0;surface<model.surfaceCount();surface++){
            var vertices=ids.get(surface);var faces=model.faceIndices(surface);
            for(int f=0;f<faces.length;f+=3){
                int a=root(parent,vertices[faces[f]]);
                for(int i=1;i<3;i++)parent.set(root(parent,vertices[faces[f+i]]),a);
            }
        }
        assertEquals(1,java.util.stream.IntStream.range(0,parent.size()).map(i->root(parent,i)).distinct().count(),"No disconnected scraps in wing holes or outside the silhouette");
    }
    private static int root(ArrayList<Integer> parent,int a){while(parent.get(a)!=a){parent.set(a,parent.get(parent.get(a)));a=parent.get(a);}return a;}

    @Test void wingbeatDoesNotPullMovingFansIntoTheAnimatedHead(){
        var model=new PrecisionWingModel();var skeleton=BlacklightSkeleton.get();
        var bind=skeleton.getPoseMatrices(new BlacklightPose());
        var samples=new ArrayList<int[]>();
        for(int surface=0;surface<model.surfaceCount();surface++){
            int part=model.part(surface);if(part==0){samples.add(new int[0]);continue;}
            var vertices=model.restVertices(surface);var near=new ArrayList<Integer>();var hinge=PrecisionWingPose.pivot(part);
            for(int v=0;v<vertices.length;v+=10){
                var p=new Vector3f(vertices[v],vertices[v+1],vertices[v+2]);
                if(p.distance(hinge)<1.1F&&PrecisionWingPose.flightWeight(p.x,p.y,p.z)>0)near.add(v/10);
            }
            samples.add(near.stream().mapToInt(Integer::intValue).toArray());
        }
        for(String clip:FlightAnimationClips.names())for(int frame=0;frame<3;frame++){
            var body=skeleton.getPoseMatrices(FlightAnimationClips.sample(clip,FlightAnimationClips.duration(clip)*frame/3));
            var chest=new Matrix4f(body[skeleton.chest.getId()]).mul(new Matrix4f(bind[skeleton.chest.getId()]).invert());
            var relative=new Matrix4f(body[skeleton.head.getId()]).mul(new Matrix4f(bind[skeleton.head.getId()]).invert()).invert().mul(chest);
            var axis=SovereignWingMotion.strokeAxis(chest.getUnnormalizedRotation(new Quaternionf()));
            for(int steering=-1;steering<=1;steering++)for(int phase:new int[]{0,3,5,8}){
                var pose=SovereignWingMotion.sample(phase/12.,1,1,.8F,1,steering,clip.equals("takeoff")?1:0,steering,clip.equals("brake")?1:0,1);
                var stack=new PoseStack();stack.mulPose(relative);model.prepare(pose,axis,1,1,0);model.prepareRender(stack);
                for(int surface=0;surface<model.surfaceCount();surface++){
                    if(model.part(surface)==0)continue;
                    var moved=new Vector3f();var drawn=model.drawnVertices(surface);
                    for(int vertex:samples.get(surface)){
                        moved.set(drawn[vertex*6],drawn[vertex*6+1],drawn[vertex*6+2]);
                        assertFalse(Math.abs(moved.x)<.283F&&moved.y>1.466F&&moved.y<2.034F&&Math.abs(moved.z)<.283F,
                                "Wing enters the animated head during "+clip+" frame "+frame+" at phase "+phase+" part "+model.part(surface)+" from "+vertex+" to "+moved);
                    }
                }
            }
        }
    }

    @Test void theSoftCollarFollowsTheBodyWithoutFollowingTheWingbeat(){
        var model=new PrecisionWingModel();var stack=new PoseStack();stack.translate(2,.5,-1);
        stack.mulPose(new Quaternionf().rotationX(-.9F).rotateZ(.2F));
        for(double phase:new double[]{0,.25,.45,.7}){
            model.prepare(SovereignWingMotion.sample(phase,1,1,1,1,1,1,1,0,1),new Vector3f(0,1,0),1,1,0);
            model.prepareRender(stack);
            for(int s=0;s<model.surfaceCount();s++){
                if(model.part(s)==0)continue;var rest=model.restVertices(s);var drawn=model.drawnVertices(s);var expected=new Vector3f();
                for(int v=0;v<rest.length/10;v++)if(PrecisionWingPose.flightWeight(rest[v*10],rest[v*10+1],rest[v*10+2])==0){
                    stack.last().pose().transformPosition(rest[v*10],rest[v*10+1],rest[v*10+2],expected);
                    assertEquals(0,expected.distance(drawn[v*6],drawn[v*6+1],drawn[v*6+2]),1e-5F);
                }
            }
        }
    }
}
