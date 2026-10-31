package com.codex.blacklight.infection;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;

class TissueLateDevelopmentTest {
    private static final TissueCore[] ORDER={TissueCore.TENDON,TissueCore.HYDRAULIC,TissueCore.HYDROSTAT,TissueCore.BLADE,TissueCore.CHELA,TissueCore.HORN};
    private static TissueInheritance stage(int count){
        var genes=new EnumMap<TissueCore,TissueInheritance.Organ>(TissueCore.class);
        for(int i=0;i<count;i++)genes.put(ORDER[i],new TissueInheritance.Organ(105,0,0,0,i%3,42+i));
        return new TissueInheritance(genes,count,105*count,false,null,count);
    }
    private static TissueEvolution.State phenotype(int count){return stage(count).appearance(TissueEvolution.nativeState(TissueCore.MUSCLE,42,504000,true),TissueCore.MUSCLE);}
    @Test void weakerLateOrgansStillRemodelTheBodyAfterTheEarlyStrongestProfile(){
        var third=phenotype(3);var sixth=phenotype(6);
        assertTrue(sixth.muscle()>third.muscle()+.4F);
        assertTrue(sixth.carapace()>third.carapace()+.4F);
        assertTrue(sixth.crest()>third.crest()+.3F);
        assertTrue(stage(6).scaleFactor()>stage(3).scaleFactor()+.06F);
        for(int i=1;i<=6;i++)assertTrue(phenotype(i).muscle()>phenotype(i-1).muscle());
    }
    @Test void lateDevelopmentChangesTheBorrowedThoraxInsteadOfOnlyRaisingHealthOrScale(){
        var captured=TissueEvolution.nativeState(TissueCore.TENDON,42,504000,true);
        var body=new Bone("body",0,new Box(new V(-4,0,-2),new V(4,12,2)),new V(0,-1,0),new V(0,0,-1),new V(1,0,0),new V(0,6,0));
        var third=TissueEvolution.additionalDevelopment(captured,phenotype(3));var sixth=TissueEvolution.additionalDevelopment(captured,phenotype(6));
        var point=new V(4,6,2);var early=TissueEvolution.develop(body,point,third,false);var late=TissueEvolution.develop(body,point,sixth,false);
        assertTrue(late.x()>early.x()*1.1F,"fixed-scale chest silhouette must keep widening in late meals");
        assertTrue(late.z()>early.z()*1.1F,"body depth develops with width");
        var buriedWaist=new V(4,12,2);assertEquals(buriedWaist.x(),TissueEvolution.develop(body,buriedWaist,sixth,false).x(),.0001F);
        assertEquals(captured,TissueEvolution.retainStronger(captured,new TissueEvolution.State(0,0,0,0,0,0)));
    }
    @Test void supportArmourContinuesFormingInRoundsFourFiveAndSixWithoutExtraBodyCopies(){
        assertTrue(stage(3).supportExpression(TissueCore.CARAPACE,0)>.35F);
        for(int i=4;i<=6;i++)assertTrue(stage(i).supportExpression(TissueCore.CARAPACE,0)>stage(i-1).supportExpression(TissueCore.CARAPACE,0)+.35F);
        assertTrue(stage(6).supportExpression(TissueCore.CREST,0)>stage(5).supportExpression(TissueCore.CREST,0));
        var strong=new TissueInheritance(Map.of(TissueCore.HYDRAULIC,new TissueInheritance.Organ(10000,0,0,0,2,42)),20,10000,false,null,1);
        var weak=new TissueInheritance(Map.of(TissueCore.HYDRAULIC,new TissueInheritance.Organ(100,0,0,0,2,42)),1,100,false,null,1);
        var baseline=new TissueEvolution.State(0,1,.5F,.1F,.1F,.1F);
        assertTrue(strong.appearance(baseline).muscle()>weak.appearance(baseline).muscle()+.2F);
        assertTrue(strong.supportExpression(TissueCore.CARAPACE,0)>weak.supportExpression(TissueCore.CARAPACE,0)*2);
    }
    @Test void capturedBulkIsNotReappliedAndPendingNewStructuresStartAtTheirBuriedRoots(){
        var captured=phenotype(6);assertFalse(TissueEvolution.additionalDevelopment(captured,captured).active());
        assertFalse(TissueEvolution.additionalDevelopment(captured,phenotype(3)).active());
        var pending=new TissueInheritance(Map.of(TissueCore.CHELA,new TissueInheritance.Organ(0,105,3600,3600,2,42)),1,105,false,null,1);
        var base=new TissueEvolution.State(0,0,0,0,0,0);
        assertEquals(0,pending.appearance(base).muscle());assertEquals(0,pending.supportExpression(TissueCore.CARAPACE,0));
        assertTrue(pending.appearance(base,null,20).muscle()>0);
        var matureMuscle=new TissueInheritance(Map.of(TissueCore.MUSCLE,new TissueInheritance.Organ(1000,0,0,0,0,42)),1,1000,false,null,1);
        assertTrue(matureMuscle.appearance(captured).muscle()>=captured.muscle(),"legacy support must not shrink the stronger late functional body");
    }
}
