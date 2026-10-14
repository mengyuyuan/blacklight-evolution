package com.codex.blacklight.combat;
import org.junit.jupiter.api.Test;import net.minecraft.core.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class TerrainGrabShapeTest {
 @Test void selectionGrowsAsStableConnectedPrefix(){var center=new BlockPos(10,70,-3);var full=TerrainGrabShape.grow(center,27,42,p->true);assertEquals(27,full.size());assertEquals(center,full.get(0));var visited=new HashSet<BlockPos>();for(int n=1;n<=27;n++){assertEquals(full.subList(0,n),TerrainGrabShape.grow(center,n,42,p->true));var added=full.get(n-1);if(n>1)assertTrue(Arrays.stream(Direction.values()).anyMatch(d->visited.contains(added.relative(d))));visited.add(added);}}
 @Test void voidsDoNotBridgeDisconnectedBlocks(){var c=BlockPos.ZERO;assertEquals(List.of(c),TerrainGrabShape.grow(c,27,42,p->p.equals(c)||p.equals(new BlockPos(2,0,0))));}
 @Test void seedChangesShapeAndTierCapsGrowth(){assertNotEquals(TerrainGrabShape.grow(BlockPos.ZERO,8,1,p->true),TerrainGrabShape.grow(BlockPos.ZERO,8,2,p->true));assertEquals(1,TerrainGrabShape.count(2,3));assertEquals(4,TerrainGrabShape.count(999,1));assertEquals(8,TerrainGrabShape.count(999,2));assertEquals(27,TerrainGrabShape.count(999,3));}
}
