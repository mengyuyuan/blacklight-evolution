package com.codex.blacklight.infection;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;

class TissueFunctionalTest {
    @Test void functionalOrgansRemodelTheirSupportingBodyWithoutGrantingLegacyOrganPowers(){
        var torso=new Bone("body",0,new Box(new V(-4,0,-2),new V(4,12,2)),new V(0,-1,0),new V(0,0,-1),new V(1,0,0),new V(0,6,0));
        V surface=new V(4,6,2);
        for(var core:TissueCore.values())if(!core.legacy()){
            assertFalse(TissueEvolution.nativeState(core,42,12000,false).active());
            var complete=TissueEvolution.nativeState(core,42,48000,false);
            assertNotEquals(surface,TissueEvolution.develop(torso,surface,complete));
            var inherited=new TissueInheritance(Map.of(core,new TissueInheritance.Organ(100,0,0,0,0,42)),1,100,false,null,1);
            var body=inherited.appearance(new TissueEvolution.State(0,0,0,0,0,0),TissueCore.MUSCLE);
            assertTrue(body.muscle()>.5F,core+" requires an actual support body");
            assertEquals(1,inherited.organs().size());assertFalse(body.aggregate().active());
            assertEquals(0,inherited.combat().damage());assertFalse(TissueEvolution.supportingMesh(core,"claw_0"));
        }
        assertTrue(TissueEvolution.supportingMesh(TissueCore.CHELA,"scapular_plate_0"));
        assertTrue(TissueEvolution.supportingMesh(TissueCore.PHARYNX,"mandible"));
    }
    private static final V UP=new V(0,-1,0),FRONT=new V(0,0,-1),RIGHT=new V(1,0,0);
    private static Bone bone(int role){return new Bone("bone_"+role,role,new Box(new V(-2,-4,-2),new V(2,4,2)),UP,FRONT,RIGHT,new V(role%2==0?-5:5,0,0));}
    @Test void addingDefinitionsCannotRerollOldSavedPrimaryOrgans(){
        var old=List.of(TissueCore.MUSCLE,TissueCore.CARAPACE,TissueCore.JAW,TissueCore.GRASP,TissueCore.CREST);
        for(long seed=-1000;seed<1000;seed++)assertEquals(old.get((int)(TissueGrowth.sample(TissueGrowth.mix(seed),846)*5)),TissueCore.forSeed(seed));
        assertEquals(72,TissueOrganCatalog.ALL.size());assertEquals(72,TissueOrganCatalog.ALL.stream().map(TissueOrganCatalog.Definition::id).distinct().count());
        assertEquals(22,TissueOrganCatalog.ALL.stream().filter(TissueOrganCatalog.Definition::implemented).count());
        assertNull(TissueOrganCatalog.get("venom_gland").runtime(),"a planned gland cannot pretend to have venom combat");
    }
    @Test void newNaturalDonorsAreReachableAndCompatibleWithoutSpeciesWhitelists(){
        var radial=EnumSet.noneOf(TissueCore.class);var quadruped=EnumSet.noneOf(TissueCore.class);
        for(long seed=0;seed<10000;seed++){radial.add(TissueCore.forNewHost(seed,2,false));quadruped.add(TissueCore.forNewHost(seed,1,false));}
        assertTrue(radial.containsAll(List.of(TissueCore.HYDRAULIC,TissueCore.CHELA,TissueCore.BLADE)));
        assertFalse(radial.contains(TissueCore.HORN));assertTrue(quadruped.containsAll(List.of(TissueCore.TENDON,TissueCore.HORN,TissueCore.PHARYNX,TissueCore.HYDROSTAT)));
    }
    @Test void savedPrimaryControlsLegacyAnatomyEvenWhenNewSelectionDiffersFromTheOldUuidBias(){
        for(long seed=0;seed<100;seed++){
            var muscle=TissueEvolution.nativeState(TissueCore.MUSCLE,seed,216000,false);
            var jaw=TissueEvolution.nativeState(TissueCore.JAW,seed,216000,false);
            assertTrue(muscle.muscle()>muscle.jaw());assertTrue(jaw.jaw()>jaw.grasp());
            var old=TissueCore.forSeed(seed);assertEquals(TissueEvolution.sample(seed,216000),TissueEvolution.nativeState(old,seed,216000,false));
        }
    }
    @Test void eachFunctionalModuleHasAnActualBoundedConnectedStructureOnItsOwnSocket(){
        for(var core:TissueCore.values())if(!core.legacy()){
            int role=core.digestive()&&core!=TissueCore.CROP?0:switch(core){case HORN,PHARYNX,TONGUE,CROP->1;case TENDON->4;default->2;};var b=bone(role);
            var meshes=functional(core,b,0,42);assertFalse(meshes.isEmpty(),core.toString());
            assertTrue(meshes.stream().mapToInt(m->m.faces().length).sum()<200);
            for(var mesh:meshes){
                assertTrue(mesh.name().startsWith(core.id+"_"));assertTrue(TissueGraftLayout.owns(core,mesh.name()));
                assertEquals(3,mesh.stages().length);assertTrue(mesh.stages()[2].length>24);
                for(var stage:mesh.stages())for(float v:stage)assertTrue(Float.isFinite(v)&&Math.abs(v)<64);
            }
            if(!core.digestive()||core==TissueCore.CROP)assertTrue(functional(core,bone(0),0,42).isEmpty(),"no generic thorax lump substitutes for a real joint organ");
        }
    }
    @Test void locomotorAndWeaponMotionDoesNotDetachTheRootAndActuallyChangesTheDistalStructure(){
        for(var core:List.of(TissueCore.TENDON,TissueCore.HYDRAULIC,TissueCore.HYDROSTAT,TissueCore.BLADE,TissueCore.CHELA,TissueCore.PHARYNX,TissueCore.SADDLE,TissueCore.CLUB,TissueCore.TONGUE,TissueCore.CNIDOCYTE)){
            var b=bone(core==TissueCore.TENDON?4:core==TissueCore.PHARYNX||core==TissueCore.TONGUE?1:2);var mesh=functional(core,b,0,42).getFirst();
            float[] rest=mesh.stages()[2].clone(),moving=rest.clone();
            TissueFunctionalMotion.apply(rest,b,mesh.name(),core,-1,0);
            TissueFunctionalMotion.apply(moving,b,mesh.name(),core,TissueOrganCycle.cycle(core).contact(),.5F);
            assertFalse(Arrays.equals(rest,moving),core+" must really articulate");
            float[] r0=new float[3],r1=new float[3];for(int i=0;i<24;i++){r0[i%3]+=rest[i]/8;r1[i%3]+=moving[i]/8;}
            assertArrayEquals(r0,r1,.0001F,core+" buried root remains on the native bone");
        }
    }
    @Test void limblessModelsGrowARealSocketRatherThanSilentlyLosingAnAcquiredModule(){
        for(var core:TissueCore.values())if(!core.legacy()){
            var meshes=functionalFallback(core,bone(0),1,42);assertFalse(meshes.isEmpty(),core+" must have a structural fallback");
            assertTrue(meshes.stream().mapToInt(m->m.faces().length).sum()<200);
            for(var m:meshes)for(float v:m.stages()[2])assertTrue(Float.isFinite(v)&&Math.abs(v)<64);
        }
    }
    @Test void obliqueSpiderLegsUseTheirMinorSectionAndMuzzlesDoNotBecomeTails(){
        var b=new Bone("right_front_leg",2,new Box(new V(0,-1,-1),new V(16,1,1)),
                new V(.5F,-.707F,.5F),new V(-.707F,0,.707F),new V(.5F,.707F,.5F),new V(-8,0,0));
        var frame=functionalFrame(b);assertEquals(0,frame.right().x(),.00001);assertEquals(1,Math.abs(frame.up().x()));
        for(var core:List.of(TissueCore.HYDRAULIC,TissueCore.HYDROSTAT,TissueCore.BLADE,TissueCore.CHELA))for(var m:functional(core,b,2,42)){
            float[] v=m.stages()[2];V centre=new V(0,0,0);for(int i=0;i<24;i+=3)centre=centre.add(new V(v[i],v[i+1],v[i+2]));centre=centre.mul(.125F);
            for(int i=0;i<24;i+=3)assertTrue(new V(v[i],v[i+1],v[i+2]).sub(centre).length()<3,"a 2x2 leg must not grow a 16-wide blade/pressure chamber");
        }
        var muzzle=new Bone("head#surface_1",6,b.box(),UP,FRONT,RIGHT,b.modelCentre());
        assertTrue(functional(TissueCore.HYDROSTAT,muzzle,1,42).isEmpty());assertTrue(tailBone("root/torso/tail"));assertFalse(tailBone("root/head#surface_1"));
        var tail=new Bone("root/tail",6,b.box(),b.up(),b.forward(),b.right(),b.modelCentre());assertEquals(0,functionalFrame(tail).right().x(),.00001);
    }
    @Test void phasesHaveRecoveryAndCannotTurnAFrameLoopIntoUnendingDamage(){
        for(var core:TissueCore.values())if(!core.legacy()&&!core.digestive()){
            var c=TissueOrganCycle.cycle(core);assertTrue(c.windup()<c.contact()&&c.contact()<c.retract()&&c.retract()<c.end());
            assertTrue(c.cost()>0&&c.cooldown()>c.end());assertEquals(0,c.effort(-1));assertEquals(0,c.effort(c.end()));
            float previous=c.effort(0);for(float t=.1F;t<c.windup();t+=.1F){float value=c.effort(t);assertTrue(Math.abs(value-previous)<.08F);previous=value;}
            assertTrue(Double.isFinite(TissueOrganCycle.launchSpeed(core,Double.MAX_VALUE))&&TissueOrganCycle.launchSpeed(core,Double.MAX_VALUE)<1);
        }
    }
    @Test void aNewNativeOrganTransfersAsItselfAndDoesNotCountAsItsFiveCategoryVisualBias(){
        var inventory=TissueInheritance.EMPTY.inherit(UUID.randomUUID(),TissueInheritance.EMPTY,0,216000,1,504000,TissueInheritance.Rules.DEFAULT,2,TissueCore.MUSCLE,TissueCore.CHELA);
        assertNotNull(inventory.organ(TissueCore.CHELA));assertNull(inventory.organ(TissueCore.GRASP));assertEquals(1,inventory.acquiredOrgans());
        assertFalse(inventory.organ(TissueCore.CHELA).mature());
        inventory=inventory.digest(3600,true,0,216000,TissueInheritance.Rules.DEFAULT,TissueCore.MUSCLE);
        assertTrue(inventory.organ(TissueCore.CHELA).mature());assertFalse(inventory.qualified());
        assertFalse(inventory.appearance(new TissueEvolution.State(0,0,0,0,0,0),TissueCore.MUSCLE).aggregate().active(),"a pincer cannot mint the old generic giant head/chest form");
    }
}
