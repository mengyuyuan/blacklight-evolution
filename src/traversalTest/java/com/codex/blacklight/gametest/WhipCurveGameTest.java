package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.WhipCurve;
import com.codex.blacklight.combat.WhipCurveContact;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("blacklight")
@PrefixGameTestTemplate(false)
public final class WhipCurveGameTest {
    private static WhipCurve.Sample sample(double time, double right, double forward) {
        return new WhipCurve.Sample(time, List.of(new WhipCurve.Point(0, 1, 0),
                new WhipCurve.Point(right, 1, forward)));
    }

    @GameTest(template = "wall_test", timeoutTicks = 20)
    public static void anatomicalRightAndForwardRotateTogether(GameTestHelper h) {
        var curve = new WhipCurve(.045, .4, List.of(sample(0, 2, 3), sample(1, 2, 3)));
        var south = WhipCurveContact.worldPoints(curve, 0, new Vec3(10, 20, 30), 0).get(WhipCurveContact.worldPoints(curve, 0, new Vec3(10, 20, 30), 0).size()-1);
        h.assertTrue(south.distanceTo(new Vec3(8, 21, 33)) < .000001,
                "actor right points west when facing south, with height unchanged");
        var north = WhipCurveContact.worldPoints(curve, 0, new Vec3(10, 20, 30), 180).get(WhipCurveContact.worldPoints(curve, 0, new Vec3(10, 20, 30), 180).size()-1);
        h.assertTrue(north.distanceTo(new Vec3(12, 21, 27)) < .000001,
                "a half turn rotates both anatomical right and forward exactly once");
        h.succeed();
    }

    @GameTest(template = "wall_test", timeoutTicks = 20)
    public static void sweepBoundsIncludeAReversalBetweenIdenticalEndpoints(GameTestHelper h) {
        var curve = new WhipCurve(.045, .4,
                List.of(sample(0, 0, 1), sample(.025, 8, 1), sample(.05, 0, 1)));
        var bounds = WhipCurveContact.sweptBounds(curve, 0, .05, Vec3.ZERO, Vec3.ZERO, 180);
        h.assertTrue(bounds.maxX > 8 && bounds.minX < 0,
                "broadphase includes the intermediate eight-block lash even when endpoints match");
        h.succeed();
    }

    @GameTest(template = "wall_test", timeoutTicks = 20)
    public static void ropeSegmentsHitCrossedBodiesButNotAnEmptyBoundingBoxCorner(GameTestHelper h) {
        var rope = List.of(new Vec3(-3, 1, 0), new Vec3(0, 1, 3), new Vec3(3, 1, 0));
        h.assertTrue(WhipCurveContact.intersectsPolyline(rope, new AABB(-.2, .5, 2.7, .2, 1.5, 3.2)),
                "the tip crosses the target body");
        h.assertTrue(!WhipCurveContact.intersectsPolyline(rope, new AABB(2.7, .5, 2.7, 3.2, 1.5, 3.2)),
                "an empty bounding-box corner is not a whip contact");
        h.succeed();
    }
}
