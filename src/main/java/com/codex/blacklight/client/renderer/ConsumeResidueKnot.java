package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.ConsumeGiverEffect;
import com.codex.blacklight.combat.ConsumeSkinGrowth;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import static com.codex.blacklight.client.renderer.ConsumeExteriorShape.*;

/** Open, tapered tendrils on the chest, back and posed arms after a victim is absorbed. */
final class ConsumeResidueKnot {
    // Unwrapped azimuth in degrees from sternum, and height from waist (-1) to collar (+1).
    private record Pin(double angle, double height) {}
    private record Tendril(double radius, double arch, double delay, Pin[] pins) {}
    private static Pin p(double angle, double height) { return new Pin(angle, height); }
    private static Tendril strand(double radius, double arch, double delay, Pin... pins) {
        return new Tendril(radius, arch, delay, pins);
    }

    // Start each curve inside a different rib attachment. Make room between neighbouring
    // sweeps for the jacket to show, and cross at different heights on the two faces.
    private static final Tendril[] TRUNK = {
        strand(.020,.039,.04, p(-118,-.40),p(-86,-.53),p(-48,-.32),p(-24,.10),p(7,.29),p(38,.23),p(72,.51),p(99,.72)),
        strand(.017,.028,.13, p(21,-.50),p(46,-.66),p(82,-.48),p(117,-.13),p(150,-.09),p(181,.20),p(206,.55)),
        strand(.016,.042,.00, p(134,.42),p(161,.52),p(189,.38),p(219,.08),p(246,.13),p(273,.49),p(288,.47)),
        strand(.014,.033,.17, p(-37,.68),p(-63,.70),p(-91,.45),p(-111,.19),p(-132,.23),p(-153,.12),p(-175,-.24)),
        strand(.015,.034,.07, p(103,.41),p(76,.25),p(54,-.10),p(28,-.28),p(2,-.27),p(-20,-.44),p(-32,-.70)),
        strand(.012,.028,.01, p(-159,-.71),p(-134,-.58),p(-111,-.28),p(-85,-.13),p(-68,.06),p(-60,.32)),
        strand(.012,.035,.12, p(34,.56),p(12,.64),p(-9,.54),p(-29,.33),p(-46,.32),p(-64,.48)),
        strand(.010,.021,.20, p(93,-.68),p(122,-.48),p(146,-.44),p(160,-.53),p(185,-.53),p(204,-.34),p(215,-.04)),
        strand(.011,.029,.03, p(-4,-.67),p(-29,-.58),p(-50,-.33),p(-71,-.28),p(-93,-.39)),
        strand(.009,.024,.09, p(-175,.72),p(-151,.59),p(-131,.56),p(-110,.71),p(-93,.67)),
        strand(.010,.027,.16, p(55,.72),p(72,.62),p(95,.63),p(122,.78),p(145,.67),p(162,.49)),
        strand(.009,.022,.06, p(221,-.69),p(200,-.58),p(185,-.27),p(171,-.17),p(158,-.20))
    };

    static Strip[] folds(Surface torso, Surface upperRight, Surface upperLeft, Surface foreRight, Surface foreLeft,
                         Vec3 facing, float envelope, float cycleAge, double time, Vec3 movement) {
        if (!(envelope > .012F) || !(cycleAge >= 0)) return new Strip[0];
        var weights = ConsumeSkinPath.giverWeights(cycleAge);
        double gather = cycleAge < ConsumeGiverEffect.REVERSE_SECONDS ? envelope : 1;
        double scale = Mth.clamp(torso.width() / .235, .45, 1.65);
        Vec3 front = torso.front().dot(facing) < 0 ? torso.front().scale(-1) : torso.front();
        double reverse = Mth.clamp((cycleAge - ConsumeGiverEffect.REVERSE_SECONDS) / ConsumeGiverEffect.REVERSE_SECONDS, 0, 1);
        List<Strip> strips = new ArrayList<>(TRUNK.length + 4);
        for (int i = 0; i < TRUNK.length; i++) {
            Tendril strand = TRUNK[i];
            double retreat = Mth.clamp((reverse - strand.delay) / (1 - strand.delay), 0, 1);
            double reach = 1 - retreat;
            if (reach < .025) continue;
            // Keep the shaft's width and UV sampling independent of how short the returning tip is.
            int last = Math.max(3, (int) Math.ceil((strand.pins.length - 1) * 5 * reach));
            Vec3[] points = new Vec3[last + 1];
            double[] widths = new double[last + 1];
            for (int n = 0; n <= last; n++) {
                double along = reach * n / last;
                double sample = along * (strand.pins.length - 1);
                int segment = Math.min((int) sample, strand.pins.length - 2);
                double u = sample - segment;
                Pin a = strand.pins[Math.max(0, segment - 1)], b = strand.pins[segment];
                Pin c = strand.pins[segment + 1], d = strand.pins[Math.min(segment + 2, strand.pins.length - 1)];
                double phase = along * 10 - time * 5 + i * 2.39;
                double flex = Math.sin(Math.PI * along);
                double angle = Math.toRadians(curve(a.angle,b.angle,c.angle,d.angle,u) + 2.4 * flex * Math.sin(phase));
                double h = curve(a.height,b.height,c.height,d.height,u) + .042 * flex * Math.cos(phase);
                double coverage = ConsumeSkinPath.torsoCoverage(weights, (h + 1) * .5) * gather;
                // Merge the root into the jacket. The remaining tip travels all the way back to it.
                double anchored = Mth.clamp(along / .19, 0, 1);
                double lift = Mth.lerp(anchored, -.060, .002 + strand.arch * (.5 + .5 * Math.sin(phase * .7)));
                double clearance = scale * Mth.lerp(coverage, -.085, lift);
                points[n] = torso.point(front, angle, h, clearance)
                        .subtract(movement.scale(.035 * along * coverage));
                double tip = Math.min(1, (reach - along) / .19);
                double taper = (.64 + .36 * Math.sin(Math.PI * along)) * (.025 + .975 * tip);
                widths[n] = strand.radius * scale * coverage * taper * (.95 + .10 * Math.sin(phase));
            }
            addVisible(strips, points, widths);
        }
        sleeve(strips, torso, upperRight, foreRight, front, false, weights, gather, scale, time);
        sleeve(strips, torso, upperLeft, foreLeft, front, true, weights, gather, scale, time);
        return strips.toArray(new Strip[0]);
    }

    private static double curve(double a, double b, double c, double d, double t) {
        return .5 * (2*b + (c-a)*t + (2*a-5*b+4*c-d)*t*t + (3*b-3*c+d-a)*t*t*t);
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
                double shell = .014 + .007 * Math.sin(time * 4 - along * 8 + ridge * 1.9);
                Surface segment = pastElbow ? forearm : arm;
                points[i] = segment.point(front, Math.toRadians(angle), h,
                        scale * Mth.lerp(local, -.047, shell));
                double taper = Math.min(1, .14 + (last - i) * .35);
                widths[i] = (ridge == 0 ? .025 : .015) * scale * local * taper;
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
