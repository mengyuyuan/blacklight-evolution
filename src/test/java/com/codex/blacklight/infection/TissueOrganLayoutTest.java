package com.codex.blacklight.infection;

import java.util.*;
import org.junit.jupiter.api.Test;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;
import static org.junit.jupiter.api.Assertions.*;

class TissueOrganLayoutTest {
    private static final V UP=new V(0,-1,0),FRONT=new V(0,0,-1),RIGHT=new V(1,0,0);
    private static Bone bone(String path,int role,float x,float z){return new Bone(path,role,new Box(new V(-2,0,-2),new V(2,12,2)),UP,FRONT,RIGHT,new V(x,8,z));}
    private static List<Bone> rig(){
        var bones=new ArrayList<Bone>();bones.add(new Bone("body",0,new Box(new V(-4,0,-6),new V(4,8,6)),UP,FRONT,RIGHT,new V(0,0,0)));
        bones.add(new Bone("head",1,new Box(new V(-4,-8,-4),new V(4,0,4)),UP,FRONT,RIGHT,new V(0,-4,-8)));
        for(int s:new int[]{-1,1}){
            bones.add(bone("front_"+s,s<0?2:3,s*8,-6));bones.add(bone("mid_front_"+s,s<0?2:3,s*8,-2));
            bones.add(bone("mid_hind_"+s,s<0?4:5,s*8,2));bones.add(bone("hind_"+s,s<0?4:5,s*8,6));
        }return bones;
    }
    @Test void multipleForelegsDoNotReceiveRepeatedCompleteWeaponKits(){
        var bones=rig();var layout=TissueOrganLayout.fit(bones,42);
        assertEquals(1,layout.sockets(TissueCore.BLADE).size());assertEquals(1,layout.sockets(TissueCore.CHELA).size());
        assertEquals(1,layout.sockets(TissueCore.HYDROSTAT).size());assertNotEquals(layout.sockets(TissueCore.BLADE),layout.sockets(TissueCore.CHELA));
        assertEquals(layout.sockets(TissueCore.CHELA),layout.sockets(TissueCore.HYDROSTAT),"muscular support belongs to the gripping wrist");
        assertTrue(layout.sockets(TissueCore.BLADE).iterator().next().startsWith("front_"));
        assertTrue(layout.sockets(TissueCore.CHELA).iterator().next().startsWith("front_"));
        assertEquals(2,layout.sockets(TissueCore.TENDON).size());assertTrue(layout.sockets(TissueCore.TENDON).stream().allMatch(s->s.startsWith("hind_")));
        assertEquals(8,layout.sockets(TissueCore.HYDRAULIC).size(),"all supporting joints retain pressure support, without eight weapon pairs");
        for(var core:TissueCore.values())if(!core.legacy())assertFalse(layout.sockets(core).isEmpty(),core.toString());
    }
    @Test void bindingOrderCannotFlipOrRelocateAnAlreadyGrownOrgan(){
        var bones=rig();var reverse=new ArrayList<>(bones);Collections.reverse(reverse);
        for(long seed=0;seed<100;seed++)for(var core:TissueCore.values())if(!core.legacy())
            assertEquals(TissueOrganLayout.fit(bones,seed).sockets(core),TissueOrganLayout.fit(reverse,seed).sockets(core));
    }
    @Test void aTrueTailTakesTheFlexibleOrganButSecondaryHeadSurfacesDoNot(){
        var bones=rig();bones.add(bone("head#surface_1",6,0,-8));
        assertTrue(TissueOrganLayout.fit(bones,42).sockets(TissueCore.HYDROSTAT).iterator().next().startsWith("front_"));
        bones.add(bone("root/tail",6,0,12));
        assertEquals(Set.of("root/tail"),TissueOrganLayout.fit(bones,42).sockets(TissueCore.HYDROSTAT));
    }
    @Test void missingOppositeLimbsOrEntireLimbSetsDoNotSilentlyDropCapabilities(){
        var body=rig().getFirst();var arm=bone("only_forelimb",2,-8,-6);var single=TissueOrganLayout.fit(List.of(body,arm),42);
        for(var core:List.of(TissueCore.BLADE,TissueCore.CHELA,TissueCore.HYDROSTAT,TissueCore.TENDON)){
            assertTrue(single.mounted(core,arm));assertFalse(functional(core,arm,1,42).isEmpty());
        }
        var absent=TissueOrganLayout.fit(List.of(body),42);
        for(var core:TissueCore.values())if(!core.legacy()){
            if(core.digestive()&&core!=TissueCore.CROP&&core!=TissueCore.GILL_RAKER){assertTrue(absent.mounted(core,body));assertFalse(functional(core,body,1,42).isEmpty());}
            else {assertTrue(absent.fallback(core,body));assertFalse(functionalFallback(core,body,1,42).isEmpty());}
        }
        var unclassified=TissueOrganLayout.fit(List.of(arm),42);assertTrue(unclassified.fallback(TissueCore.GIZZARD,arm));assertFalse(functionalFallback(TissueCore.GIZZARD,arm,1,42).isEmpty());
        assertNotEquals(Math.signum(functionalMount(TissueCore.BLADE,body,42).modelCentre().x()),Math.signum(functionalMount(TissueCore.CHELA,body,42).modelCentre().x()));
    }
    @Test void restingBladeRunsAlongTheForearmAndTheMuscularWristUncoilsOnlyForItsGrab(){
        var b=bone("forelimb",2,-8,-6);var blade=functional(TissueCore.BLADE,b,0,42).getFirst();
        assertTrue(maxProjection(blade.stages()[2],b.box().centre(),FRONT)<2,"the resting blade no longer projects a full limb-length spear forward");
        var muscle=functional(TissueCore.HYDROSTAT,b,0,42).getFirst();float[] rest=muscle.stages()[2].clone(),active=rest.clone();
        TissueFunctionalMotion.apply(rest,b,muscle.name(),TissueCore.HYDROSTAT,-1,0);
        TissueFunctionalMotion.apply(active,b,muscle.name(),TissueCore.HYDROSTAT,TissueOrganCycle.cycle(TissueCore.HYDROSTAT).contact(),0);
        assertTrue(maxProjection(rest,b.box().centre(),FRONT)<3);
        assertTrue(maxProjection(active,b.box().centre(),FRONT)>maxProjection(rest,b.box().centre(),FRONT)+3);
    }
    @Test void pincerTipsAreBluntAndOneContinuousForeheadStructureReplacesTheExtraHornPair(){
        var b=bone("forelimb",2,-8,-6);
        for(var m:functional(TissueCore.CHELA,b,0,42)){
            float[] v=m.stages()[2];int last=v.length-24;V centre=centre(v,last);
            for(int i=last;i<v.length;i+=3)assertTrue(new V(v[i],v[i+1],v[i+2]).sub(centre).length()>.3,"a gripper has a jaw surface rather than another needle");
        }
        var head=rig().get(1);var horns=functional(TissueCore.HORN,head,0,42);assertEquals(1,horns.size());
        assertTrue(maxProjection(horns.getFirst().stages()[2],head.box().centre(),UP)<head.box().span(1)*.5F);
    }
    @Test void completeSixSourceLayoutKeepsEveryCategoryWithFewerExternalParts(){
        var bones=rig();var layout=TissueOrganLayout.fit(bones,42);int pieces=0,faces=0;
        for(var core:TissueCore.values())if(!core.legacy()){
            int count=0;for(var b:bones)if(layout.mounted(core,b))for(var mesh:functional(core,b,2,42)){count++;faces+=mesh.faces().length;}
            assertTrue(count>0,core.toString());pieces+=count;
        }
        assertTrue(pieces<=40&&faces<1100,"nineteen functional categories share a bounded integrated kit rather than duplicating every weapon or stomach on each leg; pieces="+pieces+" faces="+faces);
    }
    @Test void retiringOldWeaponKitsIsContinuousAndDoesNotEraseSkinOrMouthParts(){
        assertTrue(TissueOrganLayout.oldDistalKit("claw_0"));assertTrue(TissueOrganLayout.oldDistalKit("blade_1"));assertTrue(TissueOrganLayout.oldDistalKit("ulnar_blade"));
        for(String name:List.of("tissue_arm","mandible","palate","scapular_plate_0"))assertFalse(TissueOrganLayout.oldDistalKit(name));
        var mesh=functional(TissueCore.BLADE,bone("forelimb",2,-8,-6),0,42).getFirst();var original=mesh.stages()[2];V root=centre(original,0);
        for(float amount:new float[]{1,.75F,.5F,.25F,0}){
            var vertices=original.clone();TissueSkin.rootedScale(vertices,amount);V current=centre(vertices,0);assertEquals(root.x(),current.x(),.0001);assertEquals(root.y(),current.y(),.0001);assertEquals(root.z(),current.z(),.0001);
            for(int i=0;i<vertices.length;i+=3)assertEquals(new V(original[i],original[i+1],original[i+2]).sub(root).length()*amount,new V(vertices[i],vertices[i+1],vertices[i+2]).sub(root).length(),.0001);
        }
    }
    private static float maxProjection(float[] points,V origin,V direction){float value=Float.NEGATIVE_INFINITY;for(int i=0;i<points.length;i+=3)value=Math.max(value,new V(points[i],points[i+1],points[i+2]).sub(origin).dot(direction));return value;}
    private static V centre(float[] v,int from){V c=new V(0,0,0);for(int i=from;i<from+24;i+=3)c=c.add(new V(v[i],v[i+1],v[i+2]));return c.mul(.125F);}
}
