package com.codex.blacklight.disguise;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SpeciesTraitTest {
    @Test void adaptersCoverDistinctAbilities(){
        String[] mobs={"spider","bee","rabbit","dolphin","enderman","skeleton","blaze","creeper"};
        SpeciesTrait[] powers={SpeciesTrait.CLIMB,SpeciesTrait.FLAP,SpeciesTrait.LEAP,SpeciesTrait.SWIM,SpeciesTrait.BLINK,SpeciesTrait.ARROW,SpeciesTrait.FIREBALL,SpeciesTrait.EXPLODE};
        for(int i=0;i<mobs.length;i++)assertEquals(powers[i],SpeciesTrait.of("minecraft:"+mobs[i]));
    }
    @Test void otherModsCannotAcquireVanillaAbilitiesByMatchingAnIdPath(){
        assertEquals(SpeciesTrait.NONE,SpeciesTrait.of("othermod:creeper"));assertEquals(SpeciesTrait.NONE,SpeciesTrait.of("minecraft:cow"));
        assertFalse(SpeciesTrait.fireproof("othermod:blaze"));assertTrue(SpeciesTrait.fireproof("minecraft:magma_cube"));
    }
}
