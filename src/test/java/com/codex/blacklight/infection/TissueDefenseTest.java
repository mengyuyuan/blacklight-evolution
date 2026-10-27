package com.codex.blacklight.infection;

import net.minecraft.nbt.CompoundTag;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TissueDefenseTest {
    @Test void boneStopsRearPuncturesWhileTheLamellarShieldHasAFrontArcAndJointOpenings(){
        var d=new TissueDefense();var bone=new TissueDefense.Profile(2,0);var shell=new TissueDefense.Profile(0,2);
        assertTrue(d.reduce(bone,20,true,true,-1,.5).damage()<d.reduce(bone,20,true,false,-1,.5).damage());
        assertEquals(20,d.reduce(bone,20,true,true,1,.5).damage());
        assertTrue(d.reduce(shell,20,true,true,1,.5).damage()<15);assertEquals(20,d.reduce(shell,20,true,true,-1,.5).damage());
        for(var p:new TissueDefense.Profile[]{bone,shell})for(double height:new double[]{.1,.9})assertEquals(20,d.reduce(p,20,true,true,0,height).damage());
        assertEquals(20,d.reduce(shell,20,false,true,1,.5).damage());assertEquals(20,d.reduce(shell,20,true,true,Double.NaN,.5).damage());
    }
    @Test void armourWearsFromAcceptedHitsInsteadOfProvidingPermanentFreeReduction(){
        var d=new TissueDefense();var p=new TissueDefense.Profile(8,8);float first=d.reduce(p,20,true,true,1,.5).damage();
        for(int i=0;i<20;i++)d.accepted(d.reduce(p,20,true,true,1,.5),100,i);
        assertTrue(d.reduce(p,20,true,true,1,.5).damage()>first);assertTrue(d.integrity(TissueCore.NACRE)<.05);assertEquals(1,d.integrity(TissueCore.OSTEODERM));
        assertTrue(d.reduce(p,20,true,true,1,.5).damage()>=5);
    }
    @Test void repairRequiresQuietLoadedWorkAndOneActualNutrientPerDamagedMaterial(){
        var d=new TissueDefense();var p=new TissueDefense.Profile(1,1);d.accepted(new TissueDefense.Decision(1,20,20),100,0);
        float bone=d.integrity(TissueCore.OSTEODERM),shell=d.integrity(TissueCore.NACRE);for(int i=0;i<400;i++)assertEquals(0,d.advance(p,0,true));
        assertEquals(bone,d.integrity(TissueCore.OSTEODERM));assertEquals(shell,d.integrity(TissueCore.NACRE));
        int spent=0;for(int i=0;i<20;i++)spent+=d.advance(p,1,true);assertEquals(1,spent);assertTrue(d.integrity(TissueCore.OSTEODERM)>bone);assertEquals(shell,d.integrity(TissueCore.NACRE));
        for(int i=0;i<100;i++)assertEquals(0,d.advance(p,100,false));
        var saved=TissueDefense.read(d.write());assertEquals(d.integrity(TissueCore.OSTEODERM),saved.integrity(TissueCore.OSTEODERM));assertEquals(d.integrity(TissueCore.NACRE),saved.integrity(TissueCore.NACRE));
    }
    @Test void oldSavesStartWholeAndMalformedIntegrityNeverCreatesProtectionBeyondOne(){
        assertEquals(1,TissueDefense.read(new CompoundTag()).integrity(TissueCore.NACRE));var t=new CompoundTag();t.putFloat("Shell",Float.NaN);t.putFloat("Bone",50);t.putInt("Repair",Integer.MAX_VALUE);
        var d=TissueDefense.read(t);assertEquals(0,d.integrity(TissueCore.NACRE));assertEquals(1,d.integrity(TissueCore.OSTEODERM));assertEquals(19,d.write().getInt("Repair"));
    }
    @Test void impactAndWearDeformOnlyThePlateAwayFromItsBuriedRootAtLongWorldTimes(){
        var d=new TissueDefense();var p=new TissueDefense.Profile(0,1);d.accepted(new TissueDefense.Decision(1,0,20),100,40_000_000);
        float[] v=new float[72];Arrays.fill(v,2);for(int i=0;i<24;i++)v[i]=1;var rest=v.clone();TissueDefense.deform(v,TissueCore.NACRE,d.visual(p),40_000_005);
        for(int i=0;i<24;i++)assertEquals(rest[i],v[i]);assertTrue(v[26]<rest[26]);
    }
    @Test void quadrupedOsteodermsSpreadAlongTheDorsalTorsoInsteadOfProjectingFromTheRump(){
        var c=new TissueMorphogenesis.V(0,0,0);var up=new TissueMorphogenesis.V(0,-1,0);var front=new TissueMorphogenesis.V(0,0,-1);var right=new TissueMorphogenesis.V(1,0,0);
        var b=new TissueMorphogenesis.Bone("body",0,new TissueMorphogenesis.Box(new TissueMorphogenesis.V(-4,-3,-7),new TissueMorphogenesis.V(4,3,7)),up,front,right,c);
        for(var mesh:TissueMorphogenesis.functional(TissueCore.OSTEODERM,b,1,42)){
            float[] v=mesh.stages()[2];float highest=0,fore=0,aft=0;for(int i=0;i<v.length;i+=3){highest=Math.max(highest,-v[i+1]);fore=Math.max(fore,-v[i+2]);aft=Math.max(aft,v[i+2]);}
            assertTrue(highest>3,"the exposed plate belongs on the dorsal surface");assertTrue(fore<7&&aft<7,"the long axis stays along the torso rather than making a rear stalk");
        }
    }
}
