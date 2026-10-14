package com.codex.blacklight.client.renderer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BiomassResidueFieldTest {
    @Test void marksRemainWithoutAnOwnerAndThenExpire(){
        var field=new BiomassResidueField();field.put(12,34,80,10,0,0);
        field.expire(60);assertEquals(1,field.marks().size());
        var mark=field.marks().iterator().next();assertEquals(12,mark.key().x());
        assertEquals(1,mark.remaining(60));assertTrue(mark.remaining(85)<.5);
        field.expire(100);assertTrue(field.marks().isEmpty());
    }
    @Test void repeatedPassRefreshesOnePixelRatherThanStackingFaces(){
        var field=new BiomassResidueField();field.put(1,2,80,0,0,0);field.put(1,2,80,40,1,1);
        assertEquals(1,field.marks().size());field.expire(90);assertEquals(1,field.marks().size());
        field.expire(130);assertTrue(field.marks().isEmpty());
    }
    @Test void longTrailsAreBoundedAndWorldResetClearsThem(){
        var field=new BiomassResidueField();for(int i=0;i<5000;i++)field.put(i,0,80,i,0,0);
        assertEquals(BiomassResidueField.LIMIT,field.marks().size());
        assertTrue(field.marks().iterator().next().key().x()>0);
        field.clear();assertTrue(field.marks().isEmpty());
        field.put(0,0,80,100,0,0);field.expire(0);assertTrue(field.marks().isEmpty());
    }
}
