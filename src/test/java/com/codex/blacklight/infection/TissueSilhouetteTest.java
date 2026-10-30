package com.codex.blacklight.infection;

import java.util.*;
import org.junit.jupiter.api.Test;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;
import static org.junit.jupiter.api.Assertions.*;

class TissueSilhouetteTest {
    private static final V UP=new V(0,-1,0),FRONT=new V(0,0,-1),RIGHT=new V(1,0,0);
    private static Bone leg(String name,int role,float x){return new Bone(name,role,new Box(new V(-2,0,-2),new V(2,6,2)),UP,FRONT,RIGHT,new V(x,12,0));}
    @Test void cachedShapeAlwaysRetainsTheRealPrimaryOrganWithAFiniteInventory(){
        var shapes=new HashSet<Integer>();
        for(long seed=0;seed<4096;seed++){
            int shape=TissueCore.visualSeed(seed);shapes.add(shape);
            assertEquals(TissueCore.forSeed(seed),TissueCore.forSeed(shape));
            assertEquals(shape,TissueCore.visualSeed(seed));
        }
        assertEquals(20,shapes.size());
    }
    @Test void oppositeLegsKeepAWalkingCorridorAtMatureAndVeryOldAges(){
        var left=leg("left_front_leg",3,3);var right=leg("right_front_leg",2,-3);
        for(int seed=0;seed<32;seed++){
            var plan=generate(seed,List.of(left,right),true);
            for(var region:plan.regions())for(var mesh:region.meshes())if(mesh.skin()){
                assertFalse(region.frame().clearances().isEmpty());
                for(int stage=2;stage<=4;stage++)for(long age:new long[]{12000,48000,240000,816000,Long.MAX_VALUE}){
                    float[] points=mesh.stages()[stage].clone();
                    TissueEvolution.organ(points,region.frame(),mesh.name(),TissueEvolution.sample(seed,age),true);
                    for(int i=0;i<points.length;i+=3)assertTrue(Math.abs(points[i])<=2.841F,"limbs cannot grow together across their unchanged joint spacing");
                }
            }
        }
    }
    @Test void nativeSocketsAndSeparatedOrRadialLimbsAreNotSqueezed(){
        var nativeLeg=leg("left_leg",5,2);var opposite=leg("right_leg",4,-2);
        var fitted=TissueSilhouette.bind(List.of(nativeLeg,opposite)).get(0);
        for(float x:new float[]{-2,0,2})for(float y:new float[]{0,6})assertEquals(new V(x,y,2),TissueSilhouette.fit(fitted,new V(x,y,2)));
        var radial=new Bone("left_radial",3,new Box(new V(0,-1,-1),new V(14,1,1)),UP,FRONT,RIGHT,new V(6,8,0));
        var distant=leg("far_leg",4,-30);
        var free=TissueSilhouette.bind(List.of(nativeLeg,radial,distant));
        assertTrue(free.stream().allMatch(b->b.clearances().isEmpty()),"long radial limbs and distant joints retain their own silhouette");
    }
    @Test void secondaryArmourDoesNotFillEveryHostWithTheEntireOrganKit(){
        var torso=new Bone("body",0,new Box(new V(-4,0,-2),new V(4,12,2)),UP,FRONT,RIGHT,new V(0,6,0));
        for(var core:TissueCore.values()){
            if(!core.legacy())continue;
            long seed=0;while(TissueCore.forSeed(seed)!=core)seed++;
            var plan=generate(seed,List.of(torso),true);
            long ribs=plan.regions().get(0).meshes().stream().filter(m->m.name().startsWith("costal_arch")).count();
            assertEquals(core==TissueCore.CARAPACE?4:2,ribs);
            assertTrue(plan.faces()<=MAX_FACES&&plan.organs()>0);
        }
    }
    @Test void dedicatedBodiesKeepTheirEarlyShapeThenContinuouslyDevelopTheirOwnOrgans(){
        for(long seed=0;seed<40;seed++){
            assertEquals(0,TissueEvolution.attachmentGrowth(12000));
            assertFalse(TissueEvolution.authoredState(seed,12000).active());
            assertTrue(TissueEvolution.authoredState(seed,24000).active());
            assertEquals(1,TissueEvolution.attachmentGrowth(48000));
            for(long age:new long[]{12000,48000,72000,816000}){
                var a=TissueEvolution.authoredState(seed,age-1);var b=TissueEvolution.authoredState(seed,age+1);
                assertEquals(a.muscle(),b.muscle(),.00001F);assertEquals(a.jaw(),b.jaw(),.00001F);assertEquals(a.grasp(),b.grasp(),.00001F);
            }
        }
    }
    @Test void lateGraftsMoveTheirRootsAndHingesBackIntoTheSurvivingBody(){
        var torso=new Bone("body",0,new Box(new V(-4,0,-2),new V(4,12,2)),UP,FRONT,RIGHT,new V(0,6,0));
        var root=new V(7,5,1);var hinge=root.add(new V(0,.5F,0));
        var offset=TissueSilhouette.attachmentShift(torso,root);var anchored=root.add(offset);
        assertTrue(anchored.x()<4&&anchored.x()>3.9F);
        var actual=hinge.add(offset).sub(anchored);var expected=hinge.sub(root);
        assertEquals(expected.x(),actual.x(),.00001F);assertEquals(expected.y(),actual.y(),.00001F);assertEquals(expected.z(),actual.z(),.00001F);
        assertEquals(new V(0,0,0),TissueSilhouette.attachmentShift(torso,new V(1,5,1)));
    }
}
