package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;

/** Clearance for the scalp, both locks and their attached infected in the inverted pose. */
final class GoddessHoverClearance {
    private static final int[] JOINTS = {0, 33, 34};
    private static final float[][] PROBES = readProbes();
    private final Long2DoubleOpenHashMap columns = new Long2DoubleOpenHashMap();
    private final BlockPos.MutableBlockPos sample = new BlockPos.MutableBlockPos();
    private long sampledTick = Long.MIN_VALUE;
    private int top;

    private static float[][] readProbes() {
        var raw = GoddessHoverClearance.class.getResourceAsStream("/assets/blacklight/models/greene_hover_probes.bin");
        try (var input = new DataInputStream(new BufferedInputStream(Objects.requireNonNull(raw)))) {
            float[][] groups = new float[JOINTS.length][];
            for (int i = 0; i < groups.length; i++) {
                groups[i] = new float[6 * input.readInt()];
                for (int j = 0; j < groups[i].length; j++) groups[i][j] = input.readFloat();
            }
            return groups;
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private double surface(EncounterMob boss, int x, int z) {
        long key = BlockPos.asLong(x, 0, z);
        double cached = columns.getOrDefault(key, Double.NaN);
        if (!Double.isNaN(cached)) return cached;
        double height = Double.NEGATIVE_INFINITY;
        for (int y = top; y >= top - 10; y--) {
            sample.set(x, y, z);
            var shape = boss.level().getBlockState(sample).getCollisionShape(boss.level(), sample);
            if (!shape.isEmpty()) {
                height = y + shape.max(Direction.Axis.Y);
                break;
            }
        }
        columns.put(key, height);
        return height;
    }

    double lift(EncounterMob boss, Matrix4f[] pose, Matrix4f root, Vec3 origin, double floor, double gap) {
        int ceiling = Mth.floor(floor) + 5;
        long tick = boss.level().getGameTime();
        if (tick != sampledTick || top != ceiling) {
            sampledTick = tick;
            top = ceiling;
            columns.clear();
        }
        double rise = 0;
        // Fade a ledge in just before the skin overlaps it, so lateral drift cannot pop the pose up a block.
        double margin = 1.25;
        for (int j = 0; j < JOINTS.length; j++) {
            var m = new Matrix4f(root).mul(pose[JOINTS[j]]);
            float[] points = PROBES[j];
            for (int k = 0; k < points.length; k += 6) {
                float x = points[k], y = points[k+1], z = points[k+2];
                float hx = points[k+3], hy = points[k+4], hz = points[k+5];
                double bottom = origin.y + m.m31() + m.m01()*x + m.m11()*y + m.m21()*z
                    - Math.abs(m.m01())*hx - Math.abs(m.m11())*hy - Math.abs(m.m21())*hz;
                if (bottom + rise >= ceiling + 1 + gap) continue;
                double cx = origin.x + m.m30() + m.m00()*x + m.m10()*y + m.m20()*z;
                double cz = origin.z + m.m32() + m.m02()*x + m.m12()*y + m.m22()*z;
                double rx = Math.abs(m.m00())*hx + Math.abs(m.m10())*hy + Math.abs(m.m20())*hz;
                double rz = Math.abs(m.m02())*hx + Math.abs(m.m12())*hy + Math.abs(m.m22())*hz;
                for (int bx = Mth.floor(cx-rx-margin); bx <= Mth.floor(cx+rx+margin); bx++) {
                    double dx = Math.max(0, Math.abs(bx+.5-cx)-.5-rx);
                    for (int bz = Mth.floor(cz-rz-margin); bz <= Mth.floor(cz+rz+margin); bz++) {
                        double need = surface(boss, bx, bz) + gap - bottom;
                        if (need <= rise) continue;
                        double dz = Math.max(0, Math.abs(bz+.5-cz)-.5-rz);
                        double blend = Math.max(0, 1 - Math.max(dx,dz)/margin);
                        rise = Math.max(rise, need*blend*blend*(3-2*blend));
                    }
                }
            }
        }
        return rise;
    }
}
