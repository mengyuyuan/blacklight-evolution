package com.codex.blacklight.infection;

import java.util.*;
import org.junit.jupiter.api.Test;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;
import static org.junit.jupiter.api.Assertions.*;

class TissueEvolutionTest {
    @Test void planarNativeEarsRemainFiniteAfterMatureRemodeling(){
        for(int axis=0;axis<3;axis++){
            float[] high={3,5,2};high[axis]=0;
            var b=new Bone("head/ear",1,new Box(new V(0,0,0),new V(high[0],high[1],high[2])),new V(0,-1,0),new V(0,0,-1),new V(1,0,0),new V(0,0,0));
            for(long age:new long[]{48001,504000,10000000})for(var point:List.of(new V(0,0,0),new V(high[0],high[1],high[2]))){
                var result=TissueEvolution.develop(b,point,TissueEvolution.authoredState(91,age),false);
                assertTrue(Float.isFinite(result.x())&&Float.isFinite(result.y())&&Float.isFinite(result.z()));
            }
        }
    }
    private static final V UP=new V(0,-1,0),FRONT=new V(0,0,-1),RIGHT=new V(1,0,0);
    private static Bone torso(){return new Bone("body",0,new Box(new V(-4,0,-2),new V(4,12,2)),UP,FRONT,RIGHT,new V(0,6,0));}
    @Test void approvedMatureAndAncientGeometryIncludingSpidersRemainsExact() {
        var bones=new ArrayList<Bone>();bones.add(torso());
        for(int i=0;i<8;i++)bones.add(new Bone("leg_"+i,2+i%4,new Box(new V(-12,-1,-1),new V(0,1,1)),UP,FRONT,RIGHT,new V(i%2==0?-4:4,6,i)));
        var plan=generate(1,bones,true);
        for(long age:new long[]{0,12000,24000,48000}) {
            var state=TissueEvolution.sample(492,age);assertFalse(state.active());assertEquals(1,TissueEvolution.desiredScale(age));
            for(var region:plan.regions())for(var mesh:region.meshes()) {
                float[] copy=mesh.stages()[4].clone();TissueEvolution.organ(copy,region.frame(),mesh.name(),state);
                assertArrayEquals(mesh.stages()[4],copy);
            }
        }
    }
    @Test void generationsKeepChangingFarBeyondAnIntegerAgeWithStableRandomInheritance() {
        Set<TissueEvolution.State> states=new HashSet<>();Set<Integer> foci=new HashSet<>();
        for(int generation=1;generation<=100;generation++) {
            long age=48000+generation*TissueEvolution.GENERATION_TICKS;
            var state=TissueEvolution.sample(171,age);states.add(state);
            assertEquals(state,TissueEvolution.sample(171,age));
            assertNotEquals(state,TissueEvolution.sample(172,age));
            float[] traits={state.muscle(),state.carapace(),state.jaw(),state.grasp(),state.crest()};
            int focus=0;for(int i=0;i<5;i++){assertTrue(traits[i]>=0&&traits[i]<=1);if(traits[i]>traits[focus])focus=i;}
            foci.add(focus);
        }
        assertEquals(100,states.size());assertEquals(Set.of(TissueCore.forSeed(171).ordinal()),foci,"one host retains its strongest organ across generations");
        Set<TissueCore> diversity=new HashSet<>();for(long seed=0;seed<100;seed++)diversity.add(TissueCore.forSeed(seed));assertEquals(5,diversity.size());
        long remote=10_000_000_000_000L;
        assertNotEquals(TissueEvolution.sample(171,remote),TissueEvolution.sample(171,remote+24000));
        assertTrue(TissueEvolution.desiredScale(remote)>TissueEvolution.desiredScale(2_400_000));
        assertTrue(Float.isFinite(TissueEvolution.desiredScale(Long.MAX_VALUE)));
    }
    @Test void mutationAndSizeAreContinuousAtEveryGenerationBoundary() {
        for(long age:new long[]{48000,72000,96000,120000,100*24000L+48000}) {
            var a=TissueEvolution.sample(171,age-1);var b=TissueEvolution.sample(171,age+1);
            assertEquals(a.muscle(),b.muscle(),.00001F);assertEquals(a.jaw(),b.jaw(),.00001F);
            assertEquals(a.grasp(),b.grasp(),.00001F);assertEquals(a.carapace(),b.carapace(),.00001F);
            assertEquals(TissueEvolution.desiredScale(age-1),TissueEvolution.desiredScale(age+1),.0001F);
        }
    }
    @Test void evolvedGlandRemainsJoinedToItsActualOutlet() {
        var body=torso();var region=generate(11,List.of(body),true).regions().get(0);
        var gland=region.meshes().stream().filter(m->m.name().equals("tendril_gland")).findFirst().orElseThrow();
        for(long seed=0;seed<30;seed++)for(int generation=1;generation<10;generation++) {
            var state=TissueEvolution.sample(seed,48000+24000L*generation);
            float[] points=gland.stages()[4].clone();TissueEvolution.organ(points,body,gland.name(),state);
            var root=new V(region.outlet()[4][0],region.outlet()[4][1],region.outlet()[4][2]);
            V expected=TissueEvolution.develop(body,root,state);
            assertEquals(expected.x(),centre(points,points.length-24).x(),.0001F);
            assertEquals(expected.y(),centre(points,points.length-24).y(),.0001F);
            assertEquals(expected.z(),centre(points,points.length-24).z(),.0001F);
        }
    }
    @Test void evolutionReshapesMultipleVolumesAndKeepsDigitsPlantedAtTheirHinges() {
        var body=torso();var leg=new Bone("left_front_leg",3,new Box(new V(-2,0,-2),new V(2,12,2)),UP,FRONT,RIGHT,new V(0,0,0));
        var plan=generate(1,List.of(body,leg),true);var state=TissueEvolution.sample(171,168000);
        for(var region:plan.regions())for(var mesh:region.meshes()) {
            float[] original=mesh.stages()[4],points=original.clone();
            TissueEvolution.organ(points,region.frame(),mesh.name(),state);
            for(float value:points)assertTrue(Float.isFinite(value));
            if(mesh.skin()&&region.frame().role()==0) {
                assertTrue(extent(points,0)>extent(original,0)*1.1F);
                assertTrue(extent(points,2)>extent(original,2)*1.1F);
            }
            if(mesh.motion().equals("toe")) {
                for(int i=1;i<points.length;i+=3)assertTrue(points[i]<=12.001F);
                V expected=TissueEvolution.develop(leg,new V(mesh.pivots()[4][0],mesh.pivots()[4][1],mesh.pivots()[4][2]),state);
                V actual=centre(points,0);assertEquals(expected.x(),actual.x(),.0001F);assertEquals(expected.z(),actual.z(),.0001F);
            }
        }
    }
    @Test void worldReachGrowsExactlyOnceWithThePhysicalSkeleton() {
        long age=48000+20*TissueEvolution.GENERATION_TICKS;float size=TissueEvolution.desiredScale(age);
        float original=TissueOrgans.reach(171,age,1,1,1),grown=TissueOrgans.reach(171,age,size,size,size);
        assertEquals(original*size,grown,.0001F);
        assertTrue(grown>TissueOrgans.reach(171,48000,1,1));
    }
    @Test void aHangingArmIsNotTreatedAsAPlantedFoot() {
        var arm=new Bone("left_arm",3,new Box(new V(-2,0,-2),new V(2,12,2)),UP,FRONT,RIGHT,new V(5,6,0));
        var state=TissueEvolution.sample(171,120000);var point=new V(1,18,-2);
        assertTrue(TissueEvolution.develop(arm,point,state).y()>18,"hand organs may extend below the wrist");
        var plan=generate(1,List.of(torso(),arm),true);
        assertFalse(plan.regions().stream().filter(r->r.name().equals("left_arm")).findFirst().orElseThrow().groundSupport());
    }
    @Test void humanoidNeckHasAContinuousRootIndependentOfJawOpening() {
        var head=new Bone("head",1,new Box(new V(-4,-8,-4),new V(4,0,4)),UP,FRONT,RIGHT,new V(0,-4,0));
        var plan=generate(9,List.of(torso(),head),true);
        var region=plan.regions().stream().filter(r->r.anatomy()==1).findFirst().orElseThrow();
        var neck=region.meshes().stream().filter(m->m.name().equals("tissue_neck")).findFirst().orElseThrow();
        assertEquals("",neck.motion());assertTrue(neck.skin());
        for(long age:new long[]{12000,48000,120000,816000}) {
            var state=TissueEvolution.sample(385,age);float[] points=neck.stages()[age<=12000?2:4].clone();
            TissueEvolution.organ(points,head,neck.name(),state);
            V low=centre(points,0),high=centre(points,24);
            assertTrue(low.y()>-.1F&&low.y()<1,"cervical root remains buried within the first shoulder unit; age="+age+", y="+low.y());
            assertTrue(high.y()<-3,"upper neck remains embedded in the skull");
        }
    }
    @Test void onlySomeStableGenotypesGrowSupportingTentaclesAndTheBranchBlendsContinuously() {
        int evolved=0;for(long seed=0;seed<100;seed++) {
            float amount=TissueEvolution.tentacles(TissueEvolution.sample(seed,120000));
            if(amount>.9F)evolved++;
            assertEquals(amount,TissueEvolution.tentacles(TissueEvolution.sample(seed,120000)));
            assertTrue(TissueEvolution.tentacleCount(seed)==4||TissueEvolution.tentacleCount(seed)==6);
        }
        assertTrue(evolved>5&&evolved<60,"tentacle locomotion is one random branch, not every host");
        for(long age=48000;age<180000;age+=20) {
            float a=TissueEvolution.tentacles(TissueEvolution.sample(171,age)),b=TissueEvolution.tentacles(TissueEvolution.sample(171,age+20));
            assertTrue(Math.abs(a-b)<.01F,"new appendages grow and retract rather than popping into existence");
        }
    }
    private static V centre(float[] points,int offset){V sum=new V(0,0,0);for(int i=offset;i<offset+24;i+=3)sum=sum.add(new V(points[i],points[i+1],points[i+2]));return sum.mul(.125F);}
    private static float extent(float[] points,int axis){float lo=Float.POSITIVE_INFINITY,hi=Float.NEGATIVE_INFINITY;for(int i=axis;i<points.length;i+=3){lo=Math.min(lo,points[i]);hi=Math.max(hi,points[i]);}return hi-lo;}
}
