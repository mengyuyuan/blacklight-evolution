package com.codex.blacklight.client.model;

import com.codex.blacklight.client.renderer.ConsumeAnatomy;
import org.joml.Vector3f;

import java.io.DataInputStream;
import java.io.IOException;

/** Original UV0 sampled on continuous P1 jacket and sleeve charts in bind space. */
public final class ConsumeAtlasUv {
    private static final int GRID = 5;
    private static final float[][] PATCHES = load();

    private ConsumeAtlasUv() {}

    private static float[][] load() {
        String path = "/assets/blacklight/models/consume_body_uv.bin";
        try (var stream = ConsumeAtlasUv.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing P1 consume UV patches");
            var data = new DataInputStream(stream);
            if (data.readInt() != 0x43555631 || data.readUnsignedByte() != GRID) {
                throw new IllegalStateException("Unsupported P1 consume UV patches");
            }
            float[][] patches = new float[18][GRID * GRID * 2];
            for (float[] patch : patches) {
                for (int i = 0; i < patch.length; i++) patch[i] = data.readUnsignedShort() / 65535F;
            }
            if (data.read() != -1) throw new IllegalStateException("Unexpected P1 consume UV patch data");
            return patches;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read P1 consume UV patches", ex);
        }
    }

    /** Returns mesh-atlas UV0. The consume shader applies its own scale and time offset. */
    public static float[] sample(int region, Vector3f bindPosition, Vector3f face, boolean mercer) {
        boolean torso = region == ConsumeAnatomy.BODY;
        int part = torso ? 0 : region == ConsumeAnatomy.RIGHT_ARM ? 1 : 2;
        float armCentre = mercer ? .241F : .375F;
        float cx = torso ? 0F : (part == 1 ? armCentre : -armCentre);
        float halfWidth = torso ? (mercer ? .217F : .27F) : (mercer ? .074F : .145F);
        float loY = mercer ? (torso ? .909F : .826F) : .731F;
        float hiY = mercer ? (torso ? 1.659F : 1.542F) : 1.521F;
        float loZ = mercer ? (torso ? -.141F : -.079F) : -.145F;
        float hiZ = mercer ? (torso ? .116F : .070F) : .145F;
        float x = (bindPosition.x - cx) / (2F * halfWidth) + .5F;
        float y = (bindPosition.y - loY) / (hiY - loY);
        float z = (bindPosition.z - loZ) / (hiZ - loZ);
        float nx = Math.abs(face.x), ny = Math.abs(face.y), nz = Math.abs(face.z);
        int side;
        float s, t;
        if (ny > nx && ny > nz) {
            side = face.y > 0 ? 4 : 5;
            s = x;
            t = z;
        } else if (nx > nz) {
            side = face.x > 0 ? 2 : 3;
            s = z;
            t = y;
        } else {
            side = face.z > 0 ? 0 : 1;
            s = x;
            t = y;
        }
        s = Math.max(0F, Math.min(1F, s)) * (GRID - 1);
        t = Math.max(0F, Math.min(1F, t)) * (GRID - 1);
        int col = Math.min((int) s, GRID - 2);
        int row = Math.min((int) t, GRID - 2);
        int i = 2 * (row * GRID + col);
        float[] patch = PATCHES[6 * part + side];
        return new float[]{bilinear(patch, i, s - col, t - row), bilinear(patch, i + 1, s - col, t - row)};
    }

    private static float bilinear(float[] p, int i, float s, float t) {
        int nextRow = i + 2 * GRID;
        float low = p[i] + s * (p[i + 2] - p[i]);
        float high = p[nextRow] + s * (p[nextRow + 2] - p[nextRow]);
        return low + t * (high - low);
    }
}
