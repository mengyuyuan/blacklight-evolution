package com.codex.blacklight.infection;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;

class TissueChimeraTest {
    @Test void quadrupedsRemodelTheirOwnFrameInsteadOfStackingHumanTorsosAndExtraHeads(){
        var a=TissueChimera.assemble(spiderBody(),1,List.of(new TissueChimera.Donor("minecraft:zombie",source(0),human())));
        assertFalse(a.uprightUpper());assertTrue(a.pieces().isEmpty());
        var b=TissueChimera.assemble(spiderBody(),2,List.of(new TissueChimera.Donor("mod:human_a",source(0),human()),new TissueChimera.Donor("mod:human_b",source(0),human())));
        assertEquals(1,b.pieces().stream().filter(p->p.name().equals("upper_body_fusion")).count());
        assertFalse(b.pieces().stream().anyMatch(p->p.name().equals("donor_neck_fusion")));
    }
    @Test void numberedAppendagesRemainSeparateLimbsInsteadOfBecomingTheTrunk(){
        assertEquals(2,TissueChimera.appendageRole("root/tentacle0",0));assertEquals(3,TissueChimera.appendageRole("root/tentacle_7",0));
        assertEquals(0,TissueChimera.appendageRole("root/body",0));assertEquals(6,TissueChimera.appendageRole("root/tail",6));
    }
    @Test void softGraftsExcludeTheTrunkAndKeepAllSurfacesOfEachLimb(){
        var parts=new ArrayList<TissueChimera.Part>();parts.add(part("very_long_body",0,-2,-30,-2,4,90,4));
        for(int i=0;i<10;i++)parts.add(part("tentacle_surface_"+i,2,4,8,-2,2,12,2));
        var body=new TissueChimera.Body(parts,TissueChimera.bounds(parts.stream().flatMap(p->p.vertices().stream()).toList()));
        var a=TissueChimera.assemble(spiderBody(),0,List.of(new TissueChimera.Donor("minecraft:squid",source(1),body)));
        assertFalse(a.pieces().stream().anyMatch(p->p.name().equals("very_long_body")));
        assertEquals(10,a.pieces().stream().filter(p->p.name().startsWith("tentacle_surface_")).count());
        for(var p:a.pieces())if(p.name().startsWith("tentacle_surface_")){
            assertTrue(p.root().y()>spiderBody().low().y()&&p.root().y()<spiderBody().high().y());
            assertTrue(TissueChimera.bounds(p.vertices()).high().y()<=23.001F,"appendages must stay above the native support floor");
        }
    }
    @Test void aForeignUpperBodyDoesNotDoubleExpandItsNativeSupportingBody(){
        var baseline=new TissueEvolution.State(4,.5F,.3F,.1F,0,0);
        var inherited=new TissueEvolution.State(4,2,2,2,2,2,new TissueAggregate.Form(1.2F,.2F,0,.8F,.3F));
        assertEquals(inherited,TissueChimera.receiverEvolution(baseline,inherited,0));
        var middle=TissueChimera.receiverEvolution(baseline,inherited,.5F);
        var fused=TissueChimera.receiverEvolution(baseline,inherited,1);
        assertTrue(middle.muscle()>fused.muscle());assertTrue(fused.muscle()>baseline.muscle());
        assertEquals(.6F,middle.aggregate().amount(),.0001F);assertFalse(fused.aggregate().active());
        assertEquals(2,inherited.jaw());assertEquals(1.2F,inherited.aggregate().amount());
    }
    private static TissueChimera.Part part(String name,int role,float x,float y,float z,float w,float h,float d){
        var vertices=new ArrayList<V>();for(float a:new float[]{x,x+w})for(float b:new float[]{y,y+h})for(float c:new float[]{z,z+d})vertices.add(new V(a,b,c));
        return new TissueChimera.Part(name,role,new V(x,y,z),List.copyOf(vertices),List.of(new int[]{0,1,3,2}));
    }
    private static TissueChimera.Body human(){var p=List.of(part("body",0,-4,0,-2,8,12,4),part("head",1,-4,-8,-4,8,8,8),part("right_arm",2,-8,0,-2,4,12,4),part("left_arm",3,4,0,-2,4,12,4),part("right_leg",4,-4,12,-2,4,12,4),part("left_leg",5,0,12,-2,4,12,4));return new TissueChimera.Body(p,TissueChimera.bounds(p.stream().flatMap(a->a.vertices().stream()).toList()));}
    private static Box spiderBody(){return new Box(new V(-6,11,-5),new V(6,19,9));}
    private static TissueInheritance.BodySource source(int plan){return new TissueInheritance.BodySource(plan,42,0,1);}
    private static TissueInheritance eat(TissueInheritance self,String species,int plan){return self.inherit(UUID.randomUUID(),TissueInheritance.EMPTY,1,216000,42,504000,TissueInheritance.Rules.DEFAULT,plan,TissueCore.MUSCLE,TissueCore.TENDON,species);}
    @Test void aSpiderEatingHumanoidGainsACompleteUpperBodyRatherThanAnotherWeapon(){
        var cephalothorax=new Box(new V(-4,11,-5),new V(4,19,3));
        var a=TissueChimera.assemble(spiderBody(),2,List.of(new TissueChimera.Donor("minecraft:zombie",source(0),human())),cephalothorax);
        assertTrue(a.uprightUpper());assertEquals(Set.of("minecraft:zombie"),a.species());
        for(int role:new int[]{0,1,2,3})assertTrue(a.pieces().stream().anyMatch(p->p.role()==role));
        assertFalse(a.pieces().stream().anyMatch(p->p.role()==4||p.role()==5),"native spider legs remain the lower body; no dangling human legs");
        assertTrue(a.pieces().stream().anyMatch(p->p.name().equals("upper_body_fusion")));
        assertTrue(a.pieces().stream().filter(p->p.name().equals("body")).allMatch(p->p.root().z()>-5&&p.root().z()<3),"the waist joins body tissue, never the separate head in front of it");
        assertTrue(a.pieces().stream().filter(p->p.role()==0).filter(p->!p.name().contains("fusion")).allMatch(p->TissueChimera.bounds(p.vertices()).high().y()<cephalothorax.low().y()+2),"the whole torso remains above the fusion surface, rather than being buried in the old head");
        assertTrue(TissueChimera.bounds(a.pieces().stream().flatMap(p->p.vertices().stream()).toList()).low().y()<spiderBody().low().y()-8);
    }
    @Test void theUpperBodySocketStaysInsideTheThoraxInEveryAxis(){
        var thorax=new Box(new V(-3,10,-4),new V(3,18,6));
        var a=TissueChimera.assemble(spiderBody(),2,List.of(new TissueChimera.Donor("minecraft:zombie",source(0),human())),thorax);
        V root=a.upper().root();
        assertTrue(root.x()>thorax.low().x()&&root.x()<thorax.high().x());
        assertTrue(root.y()>thorax.low().y()&&root.y()<thorax.high().y());
        assertTrue(root.z()>thorax.low().z()&&root.z()<thorax.high().z());
    }
    @Test void bodiesGrowAsConnectedAssembliesAndArmsAnimateIndependently(){
        var a=TissueChimera.assemble(spiderBody(),2,List.of(new TissueChimera.Donor("minecraft:zombie",source(0),human())));
        for(var p:a.pieces())for(var v:TissueChimera.pose(p,0,0,0,0,0,0))assertEquals(p.root(),v);
        var arm=a.pieces().stream().filter(p->p.role()==2).findFirst().orElseThrow();var torso=a.pieces().stream().filter(p->p.name().equals("body")).findFirst().orElseThrow();
        assertNotEquals(TissueChimera.pose(arm,1,0,0,0,0,0),TissueChimera.pose(arm,1,0,0,1,0,0));
        assertEquals(TissueChimera.pose(torso,1,0,0,0,0,0),TissueChimera.pose(torso,1,0,0,1,0,0));
    }
    @Test void sameWeaponCategoryDoesNotEraseDifferentDonorSpecies(){
        var a=eat(eat(TissueInheritance.EMPTY,"minecraft:zombie",0),"minecraft:cow",1);
        assertEquals(1,a.organs().size());assertEquals(2,a.sources().size());assertEquals(2,a.acquiredOrgans());
        assertEquals(504000,a.sources().get("minecraft:zombie").age());assertTrue(a.sources().get("minecraft:zombie").authored().active());
        assertEquals(TissueCore.TENDON.key(),a.sources().get("minecraft:zombie").nativeCore());
        var matured=a.digest(3600,true,1,216000,TissueInheritance.Rules.DEFAULT,TissueCore.MUSCLE);
        assertEquals(1,TissueChimera.growth(matured.sources().get("minecraft:zombie"),0));
        assertEquals(0,TissueChimera.growth(a.sources().get("minecraft:zombie"),0));assertFalse(matured.qualified());
    }
    @Test void ancestrySurvivesRepeatedMealsAndIsInheritedWithoutMintingExtraMeals(){
        var donor=eat(TissueInheritance.EMPTY,"minecraft:cow",1).digest(3600,true,1,216000,TissueInheritance.Rules.DEFAULT,TissueCore.MUSCLE);
        var a=TissueInheritance.EMPTY.inherit(UUID.randomUUID(),donor,1,216000,42,504000,TissueInheritance.Rules.DEFAULT,2,TissueCore.MUSCLE,TissueCore.TENDON,"minecraft:spider");
        assertEquals(Set.of("minecraft:cow","minecraft:spider"),a.sources().keySet());assertEquals(1,a.meals());
        a=eat(a,"minecraft:spider",2);assertEquals(2,a.sources().get("minecraft:spider").portions());
        assertEquals(2,a.sources().size());
        assertEquals(donor.sources().get("minecraft:cow").authored(),a.sources().get("minecraft:cow").authored());
    }
    @Test void rejectedOrZeroGainOperationsCannotInventDonorAnatomy(){
        var a=TissueInheritance.EMPTY.inherit(UUID.randomUUID(),TissueInheritance.EMPTY,1,216000,42,504000,new TissueInheritance.Rules(0,300,6,1000),0,TissueCore.MUSCLE,TissueCore.TENDON,"minecraft:zombie");
        assertTrue(a.sources().isEmpty());
        assertSame(TissueInheritance.EMPTY,TissueInheritance.EMPTY.inherit(null,TissueInheritance.EMPTY,1,216000,42,504000,TissueInheritance.Rules.DEFAULT,0,TissueCore.MUSCLE,TissueCore.TENDON,"minecraft:zombie"));
    }
    @Test void twoHumanoidDonorsDoNotStackTwoTorsosInOneSocket(){
        var a=TissueChimera.assemble(spiderBody(),2,List.of(new TissueChimera.Donor("mod:human_a",source(0),human()),new TissueChimera.Donor("mod:human_b",source(0),human())));
        assertEquals(1,a.pieces().stream().filter(p->p.name().equals("upper_body_fusion")).count());
        assertFalse(TissueChimera.assemble(spiderBody(),0,List.of(new TissueChimera.Donor("minecraft:zombie",source(0),human()))).uprightUpper());
    }
    @Test void completeDonorOrganProfilesAndAppearanceSurviveDigestionAndAncestry(){
        var gene=new TissueInheritance.Organ(280,0,0,0,TissueInheritance.BLADE,927,2);
        var prey=new TissueInheritance(Map.of(TissueCore.BLADE,gene),7,800,false,null,1,7);
        var a=TissueInheritance.EMPTY.inherit(UUID.randomUUID(),prey,1,216000,42,504000,TissueInheritance.Rules.DEFAULT,0,TissueCore.MUSCLE,TissueCore.TENDON,"minecraft:zombie","{Type:\"minecraft:zombie\",Humanoid:1b}");
        assertEquals(gene,a.sources().get("minecraft:zombie").genes().get(TissueCore.BLADE));
        a=a.digest(3600,true,1,216000,TissueInheritance.Rules.DEFAULT,TissueCore.MUSCLE);
        assertEquals(gene,a.sources().get("minecraft:zombie").genes().get(TissueCore.BLADE));assertTrue(a.sources().get("minecraft:zombie").appearance().contains("Humanoid"));
        var descendant=TissueInheritance.EMPTY.inherit(UUID.randomUUID(),a,1,216000,42,504000,TissueInheritance.Rules.DEFAULT,2,TissueCore.MUSCLE,TissueCore.TENDON,"minecraft:spider");
        assertEquals(a.sources().get("minecraft:zombie").genes(),descendant.sources().get("minecraft:zombie").genes());assertEquals(1,descendant.meals());
    }
}
