package com.codex.blacklight.client.animation;

/** Joint-local liquid tissue. Immutable vertex descriptors are baked once per mesh;
 * the render loop uses primitive arrays and a shared periodic wave table. */
public final class BiomassSurface {
    private static final int WAVE_SIZE = 256;
    private static final float[] WAVE = new float[WAVE_SIZE + 1];
    static {
        for (int i = 0; i <= WAVE_SIZE; i++) WAVE[i] = (float)Math.sin(i * Math.PI * 2 / WAVE_SIZE);
    }

    private BiomassSurface() { }

    public record Frame(float growth, float phase, float flow, boolean retracting) {
        public Frame {
            growth = unit(growth); phase = unit(phase); flow = unit(flow);
        }
        /** Compatibility path for meshes whose existing growth must remain exact. */
        public static Frame stable(float growth) { return new Frame(growth, 1, 0, false); }
        public boolean visible() { return growth > .001F; }
        public boolean liquid() { return visible() && flow > .0001F; }
    }

    /** All phases come from position, rather than face index or normal: shared
     * triangle vertices receive identical displacement even across hard edges. */
    public record Vertex(float x, float y, float z, float axial, float radialX, float radialZ,
                         float radius, float seed, float seamWeight) { }

    public static Vertex vertex(float x, float y, float z, float length, boolean forearm) {
        float radius = (float)Math.sqrt(x * x + z * z);
        float radialX = radius > 1E-7F ? x / radius : 1;
        float radialZ = radius > 1E-7F ? z / radius : 0;
        float extent = Math.max(.08F, length);
        float local = unit(Math.abs(y) / extent);
        float axial = forearm ? .32F + .68F * local : .32F * local;
        // The proximal overlap is fixed. Upper-arm distal rings are fixed too,
        // so shoulder/elbow welds cannot acquire moving liquid gaps.
        float cap = smooth((Math.abs(y) - .025F) / .075F);
        if (!forearm) cap *= smooth((extent - y - .025F) / .065F);
        float seed = x * 3.71F + y * .61F + z * 5.13F;
        return new Vertex(x, y, z, axial, radialX, radialZ, radius, seed, cap);
    }

    /** Attached tissue fills behind a soft front, rather than shrinking every
     * part into a wire inside the skin. Stable legacy growth remains unchanged. */
    public static float coating(Vertex vertex, Frame frame) {
        return smooth((frame.growth - vertex.axial + .20F) / .36F);
    }

    /** Writes x/y/z, wetness, ridge and vein into a caller-owned six-float slice.
     * x/y/z already contain ordinary growth. Hammer/wrist transforms follow this
     * stage, preserving their existing attachment and collision presentation. */
    public static void deform(Vertex vertex, Frame frame, float time,
                              float x, float y, float z, float[] target, int offset) {
        target[offset] = x; target[offset + 1] = y; target[offset + 2] = z;
        target[offset + 3] = target[offset + 4] = target[offset + 5] = 0;
        if (!frame.liquid() || vertex.seamWeight <= 0) return;
        float front = 1 - smooth(Math.abs(vertex.axial - frame.growth) / .28F);
        float behind = smooth((frame.growth + .12F - vertex.axial) / .24F);
        float wet = frame.flow * vertex.seamWeight * (.28F * behind + .72F * front);
        // Retargeting resets the transition phase, but cannot reset material waves.
        float phase = time * .024F + vertex.axial * 1.8F + vertex.seed;
        float wave = wave(phase), second = wave(phase * .63F + .29F);
        float bulge = wet * (.016F + .012F * wave);
        // A narrow tangential shear joins the broad lobes into flowing tissue;
        // it is bounded independently of weapon size and disappears on hardening.
        float shear = wet * .014F * second;
        float rounding = 1 - wet * (.055F + .025F * second);
        target[offset] = x * rounding + vertex.radialX * bulge - vertex.radialZ * shear;
        target[offset + 1] = y + wet * .012F * wave * (1 - vertex.axial * .5F);
        target[offset + 2] = z * rounding + vertex.radialZ * bulge + vertex.radialX * shear;
        target[offset + 3] = wet;
        target[offset + 4] = wet * smooth((wave - .25F) / .75F);
        target[offset + 5] = wet * smooth((second - .83F) / .17F) * front;
    }

    /** Wet graphite and sparse dark blood channels use the ordinary lit mesh;
     * no full-mesh glow pass is submitted. */
    public static int color(int color, float wet, float ridge, float vein) {
        if (wet <= 0) return color;
        float darken = wet * .45F;
        float red = ((color >> 16) & 255) * (1 - darken) + 10 * darken;
        float green = ((color >> 8) & 255) * (1 - darken) + 8 * darken;
        float blue = (color & 255) * (1 - darken) + 10 * darken;
        red += ridge * 28; green += ridge * 29; blue += ridge * 31;
        red = red * (1 - vein) + 79 * vein;
        green = green * (1 - vein) + 18 * vein;
        blue = blue * (1 - vein) + 22 * vein;
        return (color & 0xFF000000) | channel(red) << 16 | channel(green) << 8 | channel(blue);
    }

    public static float wave(float cycles) {
        if (!Float.isFinite(cycles)) return 0;
        float wrapped = cycles - (float)Math.floor(cycles);
        // Tiny negative phases can round to exactly one after subtracting floor.
        // Wrap that endpoint before interpolation so index + 1 stays in the table.
        if (wrapped >= 1) return WAVE[0];
        float at = wrapped * WAVE_SIZE;
        int index = (int)at;
        return WAVE[index] + (WAVE[index + 1] - WAVE[index]) * (at - index);
    }
    public static float smooth(float value) { float t = unit(value); return t * t * (3 - 2 * t); }
    private static float unit(float value) { return Float.isFinite(value) ? com.codex.blacklight.util.MathCompat.clamp(value, 0, 1) : 0; }
    private static int channel(float value) { return com.codex.blacklight.util.MathCompat.clamp(Math.round(value), 0, 255); }
}
