package com.codex.blacklight.combat;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class ThrowObjectSizeTest {
 @Test void actualDimensionsChooseHands(){assertFalse(ThrowObjectSize.twoHanded(1,1,1));assertTrue(ThrowObjectSize.twoHanded(2,1,1));assertTrue(ThrowObjectSize.twoHanded(1,3,1));assertTrue(ThrowObjectSize.twoHanded(1,1,2));}
 @Test void moreCargoIsSlowerAndStronger(){for(int n=2;n<=27;n++){assertTrue(ThrowObjectSize.speedFactor(n)<ThrowObjectSize.speedFactor(n-1));assertTrue(ThrowObjectSize.damageFactor(n)>ThrowObjectSize.damageFactor(n-1));}assertEquals(4,ThrowObjectSize.terrainLimit(1));assertEquals(8,ThrowObjectSize.terrainLimit(2));assertEquals(27,ThrowObjectSize.terrainLimit(3));}
}
