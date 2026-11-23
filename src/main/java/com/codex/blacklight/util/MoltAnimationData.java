package com.codex.blacklight.util;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.GZIPInputStream;

/** Decodes the two Greene molt resources. Their logical names and GZIP envelopes stay stable
 * so legacy resource packs, filters and reloads keep Minecraft's ordinary priority rules. */
public final class MoltAnimationData {
    public static final int PACKED_MAGIC = 0x474D4432; // GMD2, network byte order
    public static final int VERTEX_COMPONENTS = 12;
    private static final int MAX_BYTES = 128 * 1024 * 1024;
    private static final int MAX_FRAMES = 4096;

    private MoltAnimationData() {}

    public static byte[] read(InputStream source) throws IOException {
        try (source; var input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(source, 65536), 65536))) {
            input.mark(4);
            if (input.readInt() != PACKED_MAGIC) {
                input.reset();
                return input.readAllBytes();
            }
            int size = input.readInt();
            int prefix = input.readInt();
            int frames = input.readInt();
            int order = input.readInt();
            if (size < 16 || size > MAX_BYTES || prefix < 16 || prefix >= size
                    || prefix % 4 != 0 || frames < 1 || frames > MAX_FRAMES || (size - prefix) % (4 * frames) != 0
                    || (order != 1 && order != 2)) {
                throw new IOException("Invalid packed Greene animation dimensions");
            }
            byte[] raw = new byte[size];
            byte[] scratch = new byte[Math.max(8192, frames)];
            int frameBytes = (size - prefix) / frames;
            for (int lane = 0; lane < 4; lane++) {
                for (int component = 0; component < VERTEX_COMPONENTS; component++) {
                    final int step = VERTEX_COMPONENTS * 4;
                    int at = component * 4 + lane;
                    while (at < prefix) {
                        int count = Math.min(scratch.length, 1 + (prefix - 1 - at) / step);
                        input.readFully(scratch, 0, count);
                        for (int i = 0; i < count; i++, at += step) raw[at] = scratch[i];
                    }
                }
            }
            for (int lane = 0; lane < 4; lane++) {
                for (int offset = 0; offset < frameBytes; offset += 4) {
                    input.readFully(scratch, 0, frames);
                    int at = prefix + offset + lane;
                    for (int frame = 0; frame < frames; frame++, at += frameBytes) raw[at] = scratch[frame];
                }
            }
            if (input.read() != -1) throw new IOException("Trailing Greene animation data");

            // Integer addition wraps exactly like the encoder's subtraction, including for
            // signed zero and all IEEE-754 NaN payloads. No float is ever rounded.
            var words = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
            int channels = frameBytes / 4;
            int at = prefix / 4;
            for (int frame = 0; frame < frames; frame++) {
                for (int channel = 0; channel < channels; channel++, at++) {
                    int code = words.get(at);
                    int value = (code >>> 1) ^ -(code & 1);
                    if (frame > 0) {
                        int previous = words.get(at - channels);
                        value += previous;
                        if (order == 2) value += previous - (frame == 1 ? 0 : words.get(at - 2 * channels));
                    }
                    words.put(at, value);
                }
            }
            return raw;
        }
    }
}
