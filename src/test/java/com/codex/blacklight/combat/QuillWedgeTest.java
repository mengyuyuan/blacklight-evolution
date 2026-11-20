package com.codex.blacklight.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuillWedgeTest {
    @Test void onlyAnEntryNearTheOriginalMarkOnTheSamePartOpensIt() {
        var mark = new QuillWedge.Site("chest", 1, Vec3.ZERO, new Vec3(8, 65, 0));
        assertTrue(mark.near(new QuillWedge.Site("chest", 1, new Vec3(.2, 0, 0), new Vec3(11, 66, 2))));
        assertFalse(mark.near(new QuillWedge.Site("chest", 1, new Vec3(.5, 0, 0), mark.point())));
        assertFalse(mark.near(new QuillWedge.Site("head", 1, Vec3.ZERO, mark.point())));
        assertFalse(mark.near(new QuillWedge.Site("chest", 2, Vec3.ZERO, mark.point())));
    }
}
