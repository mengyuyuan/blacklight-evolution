package com.codex.blacklight.infection;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TissueGraftLayoutTest {
    @Test void eachInheritedOrganChangesItsOwnAnatomicalStructure(){
        assertTrue(TissueGraftLayout.owns(TissueCore.JAW,"mandible"));
        assertTrue(TissueGraftLayout.owns(TissueCore.JAW,"fang_1_0"));
        assertTrue(TissueGraftLayout.owns(TissueCore.CARAPACE,"scapular_plate_1"));
        assertTrue(TissueGraftLayout.owns(TissueCore.CARAPACE,"costal_arch_1_1"));
        assertTrue(TissueGraftLayout.owns(TissueCore.CREST,"shoulder_crest_1"));
        assertTrue(TissueGraftLayout.owns(TissueCore.GRASP,"claw_2"));
        assertFalse(TissueGraftLayout.owns(TissueCore.CARAPACE,"claw_2"));
        assertFalse(TissueGraftLayout.owns(TissueCore.MUSCLE,"blade_0"),"muscle growth cannot invent a separate blade kit");
    }
    @Test void aDonorReplacesTheDigitSurfaceInsteadOfStackingBladeAndClawMeshes(){
        assertEquals(TissueGraftLayout.key(TissueCore.GRASP,"claw_0"),TissueGraftLayout.key(TissueCore.GRASP,"blade_0"));
        assertNotEquals(TissueGraftLayout.key(TissueCore.GRASP,"claw_0"),TissueGraftLayout.key(TissueCore.GRASP,"claw_1"));
        assertEquals("mandible",TissueGraftLayout.key(TissueCore.JAW,"mandible"));
    }
    @Test void structureGrowsVisiblyInTheFirstFifteenSecondsButDoesNotInstantlyMature(){
        var newborn=new TissueInheritance.Organ(0,105,3600,3600,0,7);
        assertEquals(0,TissueGraftLayout.growth(newborn,0));
        var intermediate=new TissueInheritance.Organ(8.75,96.25,3300,3300,0,7);
        assertTrue(TissueGraftLayout.growth(intermediate,0)>=.5F);
        assertFalse(intermediate.mature());assertTrue(TissueGraftLayout.growth(intermediate,10)>TissueGraftLayout.growth(intermediate,0));
        assertEquals(1,TissueGraftLayout.growth(new TissueInheritance.Organ(105,0,0,0,0,7),0));
    }
    @Test void donorProfilesStayInABoundedCacheAndRetainTheDonatedOrganIdentity(){
        Set<Integer> seeds=new HashSet<>();
        for(var core:TissueCore.values())for(long donor=0;donor<10000;donor++){
            int variant=TissueCore.visualSeed(core,donor);seeds.add(variant);assertEquals(core.structuralBias(),TissueCore.forSeed(variant));
        }
        assertEquals(20,seeds.size());
    }
    @Test void inheritedOrgansProduceARealSilhouetteChangeWithoutChangingOtherCategories(){
        var basic=new TissueEvolution.State(0,.2F,.2F,.2F,.2F,.2F);
        var inherited=new TissueInheritance(Map.of(TissueCore.JAW,new TissueInheritance.Organ(105,0,0,0,0,4)),1,105,false,null,1);
        var changed=inherited.appearance(basic);
        assertTrue(changed.jaw()>1);assertEquals(basic.carapace(),changed.carapace());
        assertEquals(basic.grasp(),changed.grasp());
        assertTrue(inherited.scaleFactor()>1&&inherited.scaleFactor()<1.2F,"support expands proportionally instead of adding only height");
    }
    @Test void aRadialDonorsPairedHooksChangeTheReceivingOrganWithoutReplacingItsBody(){
        var up=new TissueMorphogenesis.V(0,-1,0);var front=new TissueMorphogenesis.V(0,0,-1);var right=new TissueMorphogenesis.V(1,0,0);
        var body=new TissueMorphogenesis.Bone("body",0,new TissueMorphogenesis.Box(new TissueMorphogenesis.V(-4,0,-2),new TissueMorphogenesis.V(4,12,2)),up,front,right,new TissueMorphogenesis.V(0,6,0));
        var limb=new TissueMorphogenesis.Bone("right_arm",2,new TissueMorphogenesis.Box(new TissueMorphogenesis.V(-2,0,-2),new TissueMorphogenesis.V(2,12,2)),up,front,right,new TissueMorphogenesis.V(6,6,0));
        int seed=TissueCore.visualSeed(TissueCore.GRASP,42);
        var ordinary=TissueMorphogenesis.generate(seed,List.of(body,limb),true,1);
        var radial=TissueMorphogenesis.generate(seed,List.of(body,limb),true,2);
        var nativeLimb=ordinary.regions().stream().filter(r->r.anatomy()==2).findFirst().orElseThrow();
        var changed=radial.regions().stream().filter(r->r.anatomy()==2).findFirst().orElseThrow();
        assertEquals(2,changed.meshes().stream().filter(m->TissueGraftLayout.owns(TissueCore.GRASP,m.name())).count());
        assertEquals(nativeLimb.reference()[0],changed.reference()[0]);assertEquals(nativeLimb.reference()[4],changed.reference()[4]);
        assertTrue(radial.faces()<=TissueMorphogenesis.MAX_FACES);assertEquals(ordinary.regions().size(),radial.regions().size());
    }
}
