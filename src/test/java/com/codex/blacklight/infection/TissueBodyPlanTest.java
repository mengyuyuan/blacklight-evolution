package com.codex.blacklight.infection;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TissueBodyPlanTest {
    private static TissueInheritance genes(double strength){return new TissueInheritance(Map.of(TissueCore.CHELA,new TissueInheritance.Organ(strength,0,0,0,2,42),TissueCore.HYDROSTAT,new TissueInheritance.Organ(strength,0,0,0,4,73)),2,strength,false,null,1);}
    @Test void structuralMassDevelopsSeveralAxesAndHealthTogether(){
        var first=TissueBodyPlan.resolve(2,TissueCore.MUSCLE,42,504000,genes(100));var grown=TissueBodyPlan.resolve(2,TissueCore.MUSCLE,42,504000,genes(1200));
        assertTrue(grown.width()>1&&grown.depth()>1&&grown.height()>1);
        assertTrue(grown.width()>=first.width()&&grown.depth()>=first.depth());
        assertTrue(grown.vitality(grown.width(),grown.height(),grown.depth())>=first.vitality(first.width(),first.height(),first.depth()));
        assertTrue(grown.vitality(1,1,1)<grown.vitality(grown.width(),grown.height(),grown.depth()),"health volume follows the actually fitted body, not a desired expansion blocked by a wall");
    }
    @Test void secondarySocketsHaveAWholeLimbBudgetAndStableIndividualShape(){
        var a=TissueBodyPlan.resolve(1,TissueCore.BLADE,73,504000,genes(100));assertEquals(a,TissueBodyPlan.resolve(1,TissueCore.BLADE,73,504000,genes(100)));
        assertEquals(2,a.accessorySockets());assertTrue(TissueBodyPlan.resolve(1,TissueCore.BLADE,73,504000,genes(10000)).accessorySockets()>2);
    }
    @Test void extremeGeneticStrengthCannotProduceInvalidDimensionsOrHealth(){
        for(int plan=0;plan<3;plan++)for(var core:TissueCore.values()){
            var a=TissueBodyPlan.resolve(plan,core,42,Long.MAX_VALUE,genes(Double.MAX_VALUE));
            assertTrue(Float.isFinite(a.width())&&a.width()>0&&a.width()<2);assertTrue(Float.isFinite(a.height())&&a.height()>0&&a.height()<2);
            assertTrue(Float.isFinite(a.depth())&&a.depth()>0&&a.depth()<2);assertTrue(Double.isFinite(a.vitality(a.width(),a.height(),a.depth())));
        }
    }
}
