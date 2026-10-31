package com.codex.blacklight.infection;

import java.util.*;
import org.junit.jupiter.api.Test;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;
import static org.junit.jupiter.api.Assertions.*;

class TissueAggregateTest {
    private static final V UP=new V(0,-1,0),FRONT=new V(0,0,-1),RIGHT=new V(1,0,0);
    private static TissueInheritance genome(TissueCore core,int plan,double maturityWork){
        return new TissueInheritance(Map.of(core,new TissueInheritance.Organ(105,0,0,maturityWork,0,42,plan)),1,105,false,null,1);
    }
    @Test void aBornSpecialistDoesNotBecomeAnAggregateWithoutForeignTissue(){
        assertFalse(TissueAggregate.sample(TissueInheritance.EMPTY,TissueCore.MUSCLE,0).active());
        assertFalse(TissueAggregate.sample(genome(TissueCore.MUSCLE,2,0),TissueCore.MUSCLE,0).active());
        var before=TissueAggregate.sample(genome(TissueCore.GRASP,2,3600),TissueCore.MUSCLE,0);
        var middle=TissueAggregate.sample(genome(TissueCore.GRASP,2,3300),TissueCore.MUSCLE,0);
        var complete=TissueAggregate.sample(genome(TissueCore.GRASP,2,0),TissueCore.MUSCLE,0);
        assertEquals(0,before.amount());assertTrue(middle.amount()>0&&middle.amount()<complete.amount());
        assertEquals(1,complete.radial());
    }
    @Test void theFirstForeignOrganRebuildsTheTrunkAndFaceRatherThanJustItsWeapon(){
        var form=TissueAggregate.sample(genome(TissueCore.GRASP,2,0),TissueCore.MUSCLE,0);
        var body=new Bone("body",0,new Box(new V(-4,0,-2),new V(4,12,2)),UP,FRONT,RIGHT,new V(0,6,0));
        var head=new Bone("head",1,new Box(new V(-4,-8,-4),new V(4,0,4)),UP,FRONT,RIGHT,new V(0,-4,0));
        var chest=TissueAggregate.develop(body,new V(4,3,-2),form,false);
        var face=TissueAggregate.develop(head,new V(4,-4,-4),form,false);
        assertTrue(chest.x()>6,"the thorax gains a visibly different outline after one foreign organ");
        assertTrue(face.z()< -6,"the head is drawn into a new forward jaw profile");
        assertTrue(face.x()>5,"source anatomy participates in the fused head width");
        var quad=TissueAggregate.sample(genome(TissueCore.GRASP,1,0),TissueCore.MUSCLE,0);
        assertNotEquals(face,TissueAggregate.develop(head,new V(4,-4,-4),quad,false));
    }
    @Test void combinedRemodelingPreservesPlantedFeetAndIsFiniteFarBeyondTheMilestone(){
        var leg=new Bone("right_leg",4,new Box(new V(-2,0,-2),new V(2,12,2)),UP,FRONT,RIGHT,new V(-2,18,0));
        var combined=new EnumMap<TissueCore,TissueInheritance.Organ>(TissueCore.class);
        for(var core:TissueCore.values())combined.put(core,new TissueInheritance.Organ(Double.MAX_VALUE,Double.MAX_VALUE,3600,0,0,core.ordinal(),core.ordinal()%3));
        var inventory=new TissueInheritance(combined,Long.MAX_VALUE,1e100,false,null,1);
        var state=inventory.appearance(TissueEvolution.sample(1,Long.MAX_VALUE),TissueCore.MUSCLE);
        assertTrue(state.aggregate().amount()<1.3F);
        assertTrue(Float.isFinite(state.aggregate().radial())&&Float.isFinite(state.aggregate().handedness()));
        for(float x:new float[]{-2,0,2})for(float y:new float[]{0,6,12})for(float z:new float[]{-2,0,2}){
            V result=TissueEvolution.develop(leg,new V(x,y,z),state,true);
            assertTrue(Float.isFinite(result.x())&&Float.isFinite(result.y())&&Float.isFinite(result.z()));
            assertTrue(result.y()<=12.0001F,"the aggregate cannot grow through its foot plane");
        }
    }
    @Test void replayingPersistedGenesDoesNotRerollTheAggregate(){
        var inventory=genome(TissueCore.JAW,1,1800);
        assertEquals(TissueAggregate.sample(inventory,TissueCore.MUSCLE,0),TissueAggregate.sample(inventory,TissueCore.MUSCLE,0));
        var rest=new V(1,2,3);var body=new Bone("body",0,new Box(new V(-4,0,-2),new V(4,12,2)),UP,FRONT,RIGHT,new V(0,6,0));
        assertEquals(rest,TissueAggregate.develop(body,rest,TissueAggregate.Form.NONE,false));
    }
    @Test void addingAnUndigestedSecondDonorCannotInstantlyChangeTheWholeBody(){
        var first=genome(TissueCore.GRASP,2,0);var organs=new EnumMap<TissueCore,TissueInheritance.Organ>(TissueCore.class);organs.putAll(first.organs());
        organs.put(TissueCore.CARAPACE,new TissueInheritance.Organ(0,150,3600,3600,0,12345,1));
        var second=new TissueInheritance(organs,2,255,false,null,2);
        assertEquals(TissueAggregate.sample(first,TissueCore.MUSCLE,0),TissueAggregate.sample(second,TissueCore.MUSCLE,0));
    }
}
