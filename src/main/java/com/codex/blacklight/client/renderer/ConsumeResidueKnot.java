package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.ConsumeGiverEffect;
import com.codex.blacklight.combat.ConsumeSkinGrowth;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import static com.codex.blacklight.client.renderer.ConsumeExteriorShape.*;

/** Raised, open folds drawn on the posed body. Each cross-section follows the local skin's
 * propagation value, so clearing the sleeve does not shrink a fully covered chest. */
final class ConsumeResidueKnot {
    // Azimuth from the sternum, relative height between hips and collar, stand-off in blocks.
    private record Pin(double angle, double height, double lift) {}
    private record Fold(double radius, Pin[] pins) {}
    private static Pin p(double a, double y, double lift) { return new Pin(a, y, lift); }
    private static Fold fold(double radius, Pin... pins) { return new Fold(radius, pins); }

    // Skewed open paths go round the ribs. Overlapping high and low points give both sides
    // of the torso a mixture of exposed clothing, narrow seams and thicker bundles.
    private static final Fold[] TRUNK = {
        fold(.083, p(169,-.54,-.035), p(128,-.20,.046), p(102,.43,.088), p(77,.84,.092),
                p(45,.78,.060), p(21,.47,.084), p(4,.04,.107), p(-16,-.47,.082),
                p(-30,-.71,.049), p(-47,-.65,-.026)),
        fold(.075, p(-153,-.38,-.03), p(-125,.03,.077), p(-96,.58,.069), p(-67,.81,.079),
                p(-43,.68,.062), p(-22,.32,.066), p(-2,-.02,.040), p(23,-.57,.057),
                p(43,-.75,.034), p(58,-.52,-.025)),
        fold(.087, p(43,-.72,-.03), p(70,-.64,.043), p(88,-.35,.070), p(114,-.61,.066),
                p(143,-.58,.10), p(166,-.18,.094), p(187,.30,.076), p(208,.78,.096),
                p(230,.71,.060), p(258,.31,-.026)),
        fold(.068, p(-58,.39,-.027), p(-81,.14,.051), p(-114,.60,.063), p(-138,.79,.085),
                p(-162,.49,.075), p(-183,.04,.104), p(-196,-.44,.083), p(-219,-.66,.057),
                p(-239,-.59,-.024)),
        fold(.054, p(113,.65,-.024), p(92,.59,.046), p(69,.21,.072), p(56,-.16,.062),
                p(34,-.31,.035), p(18,-.07,.039), p(22,.15,-.032)),
        fold(.050, p(-214,-.15,-.022), p(-188,-.42,.052), p(-168,-.43,.073), p(-153,-.15,.080),
                p(-147,.30,.061), p(-132,.44,.043), p(-111,.34,-.025)),
        fold(.044, p(-109,-.42,-.019), p(-82,-.52,.040), p(-60,-.32,.067), p(-48,.06,.069),
                p(-27,.19,.044), p(-17,.05,-.022))
    };
    // Each open fibre dives back into one of several rib attachments. Coarser fibres
    // and their finer offshoots do not share a moving tip.
    private static final double[] ROOT_ALONG = {.69,.57,.62,.46,.68,.55,.49};

    static Strip[] folds(Surface torso, Surface upperRight, Surface upperLeft, Surface foreRight, Surface foreLeft,
                         Vec3 facing, float envelope, float cycleAge, double time, Vec3 movement) {
        if (!(envelope > .012F) || !(cycleAge >= 0)) return new Strip[0];
        var weights = ConsumeSkinPath.giverWeights(cycleAge);
        // The settled mass is gathered from the prey. On the return the bone field owns its volume.
        double gather = cycleAge < ConsumeGiverEffect.REVERSE_SECONDS ? envelope : 1;
        double scale = Mth.clamp(torso.width() / .235, .45, 1.65);
        Vec3 front = torso.front().dot(facing) < 0 ? torso.front().scale(-1) : torso.front();
        List<Strip> strips = new ArrayList<>(TRUNK.length * 2 + 4);
        for (int index = 0; index < TRUNK.length * 2; index++) {
            Fold fold = TRUNK[index / 2];
            boolean fine = (index & 1) != 0;
            int spans = fold.pins.length - 1;
            double retreat = Mth.clamp((cycleAge - ConsumeGiverEffect.REVERSE_SECONDS) / ConsumeGiverEffect.REVERSE_SECONDS,0,1);
            double retract = Mth.clamp((retreat - .035 * (index % 4)) * (fine ? 1.15 : 1.02),0,1);
            double root = ROOT_ALONG[index / 2] + (fine ? .045 : 0);
            double from = Mth.lerp(retract, fine ? .07 : 0, root);
            double to = Mth.lerp(retract, fine ? .89 : 1, root);
            if (to - from < .012) continue;
            Vec3[] points = new Vec3[spans * 4 + 1];
            double[] widths = new double[points.length];
            for (int n = 0; n < points.length; n++) {
                double length = n / (double)(points.length - 1);
                // Smaller fibres branch away, arc over the main fibre, then dive back into the weave.
                double along = Mth.lerp(length, from, to);
                int s = Math.min((int)(along * spans), spans - 1);
                double u = along * spans - s;
                Pin a = fold.pins[s], b = fold.pins[s + 1];
                double beat = time * 4.0 - along * 8.4 + index * 1.6;
                double angle = Math.toRadians(Mth.lerp(u, a.angle, b.angle) + 2.4 * Math.sin(beat));
                double h = Mth.lerp(u, a.height, b.height);
                double crossing = Math.sin(along * 7.5 + index * .67);
                if (fine) {
                    h += .115 + .23 * crossing;
                    angle += .14 * Math.cos(along * 8 + index);
                }
                h = Mth.clamp(h, -.89, .88);
                double coverage = ConsumeSkinPath.torsoCoverage(weights, (h + 1) * .5) * gather;
                // Slide each local ridge a short distance over the skin as it sinks.
                // Circumferential coordinates stay on their original face during the retreat.
                double taper = Math.min(1, Math.min(.08 + length * 6, .065 + (1 - length) * 3.8));
                double wave = Math.sin(time * 4.5 - 6.5 * along + index * 1.87);
                double lift = (Mth.lerp(u, a.lift, b.lift) + .009 * wave
                        + (fine ? .028 * Math.cos(along * 7.5 + index * .67) : 0)) * scale;
                points[n] = torso.point(front, angle, Mth.lerp(coverage, .12 + .78 * h, h),
                        Mth.lerp(coverage, -.055 * scale, lift))
                        .subtract(movement.scale(.068 * coverage * taper));
                widths[n] = fold.radius * (fine ? .24 : .49) * scale * coverage * taper * (.97 + .085 * wave);
            }
            addVisible(strips, points, widths);
        }
        sleeve(strips, torso, upperRight, foreRight, front, false, weights, gather, scale, time);
        sleeve(strips, torso, upperLeft, foreLeft, front, true, weights, gather, scale, time);
        return strips.toArray(new Strip[0]);
    }

    private static void sleeve(List<Strip> strips, Surface torso, Surface arm, Surface forearm, Vec3 front,
                               boolean left, ConsumeSkinGrowth.Weights weights, double gather, double scale, double time) {
        if (arm == null) return;
        // Choose the side of this actual shoulder, including posed or mirrored avatars.
        Vec3 shoulder = arm.center().subtract(arm.up().scale(arm.half()));
        double sign = Math.signum(shoulder.subtract(torso.center()).dot(torso.up().cross(front)));
        for (int ridge = 0; ridge < 2; ridge++) {
            int aroundArm = 12;
            int aroundForearm = ridge == 0 && forearm != null ? 6 : 0;
            int last = 2 + aroundArm + aroundForearm;
            Vec3[] points = new Vec3[last + 1];
            double[] widths = new double[last + 1];
            double root = Math.toRadians(sign * (ridge == 0 ? 47 : 134));
            Vec3 base = torso.point(front, root, ridge == 0 ? .63 : .47, .035 * scale);
            for (int i = 2; i <= last; i++) {
                int n = i - 2;
                boolean pastElbow = n > aroundArm;
                double t = pastElbow ? (n - aroundArm) / (double)aroundForearm : n / (double)aroundArm;
                double along = pastElbow ? .56 + .26 * t : t * (ridge == 0 ? .56 : .43);
                double local = ConsumeSkinPath.armCoverage(weights, left, along) * gather;
                double angle = sign * (ridge == 0 ? 42 + 194 * along : 145 - 243 * along);
                double h = pastElbow ? -.9 + 1.18*t : -.91 + (ridge == 0 ? 1.84 : 1.41)*t;
                double shell = .031 + .010 * Math.sin(time * 4 - along * 8 + ridge * 1.9);
                Surface segment = pastElbow ? forearm : arm;
                points[i] = segment.point(front, Math.toRadians(angle), h,
                        scale * Mth.lerp(local, -.047, shell));
                double taper = Math.min(1, .14 + (last - i) * .35);
                widths[i] = (ridge == 0 ? .061 : .043) * scale * local * taper;
            }
            points[0] = base;
            points[1] = base.lerp(points[2], .52).add(torso.up().scale(.024 * scale));
            widths[0] = widths[2] * .30;
            widths[1] = widths[2] * .88;
            addVisible(strips, points, widths);
        }
    }

    /** Split at cleared samples to avoid drawing a chord over an exposed patch of skin. */
    private static void addVisible(List<Strip> strips, Vec3[] points, double[] widths) {
        for (int i = 0; i < widths.length;) {
            while (i < widths.length && widths[i] <= .001) i++;
            int start = i;
            while (i < widths.length && widths[i] > .001) i++;
            if (i - start < 2) continue;
            int a = Math.max(0, start - 1), b = Math.min(widths.length, i + 1);
            strips.add(a == 0 && b == widths.length ? new Strip(points, widths) :
                    new Strip(java.util.Arrays.copyOfRange(points, a, b), java.util.Arrays.copyOfRange(widths, a, b)));
        }
    }
    private ConsumeResidueKnot() {}
}
