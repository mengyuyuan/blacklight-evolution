package com.codex.blacklight.client.renderer;

/**
 * Procedural lighting for grown tissue. The atlas carries the fibre detail; this
 * adds the volume the flat tint lacked: underside occlusion, crease darkening,
 * a wet blood-dark rim on freshly emerging growth, a heartbeat on exposed flesh
 * and per-face value jitter so tiled plates do not read as repeated blocks.
 */
public final class TissueShading {
    /** QA/compat switch; false restores the previous flat tint. */
    public static boolean enabled = true;
    private TissueShading() { }

    /**
     * @param alphaSource colour whose alpha is preserved (fades/invisibility)
     * @param material    tissue material slot (2/5 flesh, 3/6 carapace, else hide)
     * @param ny          model-space normal Y (model space is Y-down)
     * @param inset       0 at face centre .. 1 at corners, used as crease proxy
     * @param reveal      growth reveal 0..1 for this face
     * @param jitter      per-face random 0..1
     * @param beat        heartbeat phase 0..1
     */
    static int shade(int alphaSource, int material, float ny, float inset, float reveal, float jitter, float beat) {
        if (!enabled) return alphaSource | 0x00ffffff;
        // Faces turned downward sit in the body's own shadow.
        float occlusion = .84F + .22F * (.5F - .5F * ny);
        // Corners of each plate fold into the next plate: a narrow crease line.
        float crease = 1 - .16F * inset * inset;
        float value = occlusion * crease * (.94F + .12F * jitter);
        float r = value, g = value, b = value;
        boolean flesh = material == 2 || material == 5;
        if (flesh) {
            float pulse = .92F + .14F * beat;
            r *= pulse * 1.04F; g *= pulse * .9F; b *= pulse * .9F;
        } else if (material == 3 || material == 6) {
            // Carapace: slightly cold and glossier on top.
            float sheen = Math.max(0, -ny) * .08F;
            r = r * .96F + sheen; g = g * .97F + sheen; b = b + sheen;
        }
        // New growth is wet and blood dark until it has fully pushed through.
        float fresh = 1 - Math.min(1, reveal);
        r *= 1 - .25F * fresh; g *= 1 - .55F * fresh; b *= 1 - .5F * fresh;
        int ir = clamp(r), ig = clamp(g), ib = clamp(b);
        return (alphaSource & 0xff000000) | ir << 16 | ig << 8 | ib;
    }

    /** Multiplies an existing tint (keeps its hue) by the same occlusion/crease/pulse model. */
    static int modulate(int color, boolean flesh, float ny, float inset, float jitter, float beat) {
        if (!enabled) return color;
        float occlusion = .80F + .28F * (.5F - .5F * ny);
        float value = occlusion * (1 - .14F * inset * inset) * (.95F + .1F * jitter);
        float r = value, g = value, b = value;
        if (flesh) { float pulse = .9F + .18F * beat; r *= pulse * 1.06F; g *= pulse * .88F; b *= pulse * .9F; }
        else { float sheen = Math.max(0, -ny) * .07F; r += sheen; g += sheen; b += sheen * 1.3F; }
        int cr = color >> 16 & 255, cg = color >> 8 & 255, cb = color & 255;
        return (color & 0xff000000) | clamp(cr / 255F * r) << 16 | clamp(cg / 255F * g) << 8 | clamp(cb / 255F * b);
    }

    static float heartbeat(double ticks, float seed) {
        double t = (ticks / 20.0 * 1.15 + seed) % 1.0;
        // Lub-dub: two short peaks then rest.
        double a = Math.exp(-Math.pow((t - .08) / .045, 2)), c = Math.exp(-Math.pow((t - .26) / .06, 2)) * .6;
        return (float) Math.min(1, a + c);
    }

    private static int clamp(float v) { return Math.max(0, Math.min(255, Math.round(v * 255))); }
}
