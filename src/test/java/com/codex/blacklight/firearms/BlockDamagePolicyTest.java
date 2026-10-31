package com.codex.blacklight.firearms;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class BlockDamagePolicyTest {
    @Test public void materialsRequireDifferentActualImpacts(){
        assertEquals(1,BlockDamagePolicy.durability(BlockDamagePolicy.Material.FRAGILE,.3F));
        assertEquals(16,BlockDamagePolicy.durability(BlockDamagePolicy.Material.WOOD,2));
        assertEquals(36,BlockDamagePolicy.durability(BlockDamagePolicy.Material.MASONRY,1.5F));
        assertEquals(160,BlockDamagePolicy.durability(BlockDamagePolicy.Material.METAL,5));
    }
    @Test public void bulletsBreakLightCoverAndAccumulateSlowlyOnMasonry(){
        assertEquals(4,BlockDamagePolicy.bulletImpact(BlockDamagePolicy.Material.WOOD,4,false));
        assertEquals(.8F,BlockDamagePolicy.bulletImpact(BlockDamagePolicy.Material.MASONRY,4,false),.0001F);
        assertEquals(4,BlockDamagePolicy.bulletImpact(BlockDamagePolicy.Material.MASONRY,5,true));
        assertEquals(0,BlockDamagePolicy.bulletImpact(BlockDamagePolicy.Material.METAL,5,true));
    }
    @Test public void protectedAndInvalidInputsNeverReceiveDamage(){
        assertEquals(0,BlockDamagePolicy.bulletImpact(BlockDamagePolicy.Material.PROTECTED,100,true));
        assertEquals(0,BlockDamagePolicy.bulletImpact(BlockDamagePolicy.Material.WOOD,Float.NaN,true));
        assertTrue(Float.isInfinite(BlockDamagePolicy.durability(BlockDamagePolicy.Material.WOOD,-1)));
        assertTrue(Float.isInfinite(BlockDamagePolicy.durability(BlockDamagePolicy.Material.MASONRY,Float.NaN)));
    }
    @Test public void infectionHasNoStructuralBlastAndRocketsRemainLocal(){
        assertEquals(0,BlockDamagePolicy.blastRadius(WeaponDefinition.Kind.INFECTION));
        assertEquals(3,BlockDamagePolicy.blastRadius(WeaponDefinition.Kind.ROCKET));
        assertEquals(0,BlockDamagePolicy.blastImpact(BlockDamagePolicy.Material.WOOD,WeaponDefinition.Kind.INFECTION,24,0,3));
        assertEquals(0,BlockDamagePolicy.blastImpact(BlockDamagePolicy.Material.WOOD,WeaponDefinition.Kind.ROCKET,20,4,3));
        assertTrue(BlockDamagePolicy.blastImpact(BlockDamagePolicy.Material.MASONRY,WeaponDefinition.Kind.ROCKET,20,0,3)>36);
        assertTrue(BlockDamagePolicy.blastImpact(BlockDamagePolicy.Material.WOOD,WeaponDefinition.Kind.ROCKET,20,2,3)<BlockDamagePolicy.blastImpact(BlockDamagePolicy.Material.WOOD,WeaponDefinition.Kind.ROCKET,20,0,3));
    }
}
