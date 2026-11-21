package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsumeImpactKindTest {
    private String small(PowerForm form,int variant,int hit,int count) {
        return ConsumeImpactKind.forContact(form,false,0,variant,hit,count);
    }
    @Test void successiveHitsCanChangeMaterialResponse() {
        for(int i=0;i<4;i++)assertEquals("dent", small(PowerForm.NONE,ConsumeVariant.BEATDOWN,i,5));
        assertEquals("crush",small(PowerForm.NONE,ConsumeVariant.BEATDOWN,4,5));
        assertEquals("pierce",small(PowerForm.MUSCLE_MASS,ConsumeVariant.CLASSIC,0,2));
        assertEquals("dent",small(PowerForm.MUSCLE_MASS,ConsumeVariant.CLASSIC,1,2));
    }
    @Test void smallAndMountedUseTheirOwnContacts() {
        assertEquals("pierce",small(PowerForm.NONE,ConsumeVariant.DEFAULT,0,1));
        assertEquals("tear",small(PowerForm.NONE,ConsumeVariant.STEALTH_JUGULAR,0,1));
        assertEquals("dent",small(PowerForm.NONE,ConsumeVariant.STEALTH_BEARHUG,0,1));
        assertEquals("cleave",small(PowerForm.BLADE,ConsumeVariant.DEFAULT,0,1));
        assertEquals("crush",small(PowerForm.CLAWS,ConsumeVariant.CLASSIC,0,1));
        assertEquals("slash",ConsumeImpactKind.forContact(PowerForm.BLADE,true,0,0,0,1));
        for(int i=0;i<2;i++)
            assertEquals("tear",ConsumeImpactKind.forContact(PowerForm.NONE,true,ConsumeVisual.HUNTER_CONSUME,0,i,2));
    }
}
