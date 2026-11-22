package com.codex.blacklight.util;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Reinstates the original numeric spelling and punctuation of a filtered geographic tile. */
public final class AtlasTileCodec {
    public static final int MAGIC = 0x41544631; // ATF1 inside the existing XZ container
    public static final int GRID = 33;
    public static final byte HEIGHTS = 0, COORDINATES = 1;
    private static final int HEADER = 12;

    private AtlasTileCodec() {}

    public static int predict(int[] heights, int at) {
        if (at < GRID) return at == 0 ? 0 : heights[at - 1];
        if (at % GRID == 0) return heights[at - GRID];
        return heights[at - 1] + heights[at - GRID] - heights[at - GRID - 1];
    }

    public static byte[] restore(byte[] tile, int limit) throws IOException {
        ByteBuffer input = ByteBuffer.wrap(tile);
        if (input.remaining() < 4 || input.getInt() != MAGIC) return tile; // plain JSON in older XZ files
        if (tile.length < HEADER) throw new IOException("Truncated geographic tile header");
        int length = input.getInt();
        int skeleton = input.getInt();
        if (length < 1 || length > limit || skeleton < 1 || skeleton > length || skeleton > input.remaining())
            throw new IOException("Invalid geographic tile size");
        input.position(HEADER + skeleton);
        Output out = new Output(length);
        int[] heights = new int[GRID * GRID];
        int from = HEADER;
        for (int i = HEADER; i < HEADER + skeleton; i++) {
            byte token = tile[i];
            if (token != HEIGHTS && token != COORDINATES) continue;
            out.copy(tile, from, i - from);
            from = i + 1;
            out.write('[');
            if (token == HEIGHTS) {
                for (int j = 0; j < heights.length; j++) {
                    long value = predict(heights, j) + (long) signed(input);
                    if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) throw new IOException("Invalid elevation");
                    heights[j] = (int) value;
                    if (j != 0) out.write(',');
                    out.number((int) value, false);
                }
            } else {
                int count = varInt(input);
                if (count < 2 || (count & 1) != 0 || count > input.remaining()) throw new IOException("Invalid coordinate count");
                int[] previous = {0, 0};
                for (int j = 0; j < count; j++) {
                    long value = previous[j & 1] + (long) signed(input);
                    if (Math.abs(value) > Integer.MAX_VALUE) throw new IOException("Coordinate overflow");
                    previous[j & 1] = (int) value;
                    if (j != 0) out.write(',');
                    out.number((int) value, true);
                }
            }
            out.write(']');
        }
        out.copy(tile, from, HEADER + skeleton - from);
        if (input.hasRemaining() || out.at != length) throw new IOException("Geographic tile length mismatch");
        return out.data;
    }

    private static int signed(ByteBuffer input) throws IOException {
        int bits = varInt(input);
        return (bits >>> 1) ^ -(bits & 1);
    }

    private static int varInt(ByteBuffer input) throws IOException {
        int value = 0;
        for (int shift = 0; shift <= 28 && input.hasRemaining(); shift += 7) {
            int next = input.get() & 255;
            if (shift == 28 && next > 15) break;
            value |= (next & 127) << shift;
            if (next < 128) return value;
        }
        throw new IOException("Invalid geographic tile integer");
    }

    private static final class Output {
        final byte[] data;
        int at;
        Output(int size) { data = new byte[size]; }
        void copy(byte[] source, int offset, int length) throws IOException {
            if (length > data.length - at) throw new IOException("Geographic tile overflow");
            System.arraycopy(source, offset, data, at, length);
            at += length;
        }
        void write(int value) throws IOException {
            if (at == data.length) throw new IOException("Geographic tile overflow");
            data[at++] = (byte) value;
        }
        void number(int value, boolean tenths) throws IOException {
            if (value < 0) { write('-'); value = -value; }
            int number = tenths ? value / 10 : value;
            int start = at;
            do {
                write('0' + number % 10);
                number /= 10;
            } while (number != 0);
            for (int left = start, right = at - 1; left < right; left++, right--) {
                byte digit = data[left]; data[left] = data[right]; data[right] = digit;
            }
            if (tenths) { write('.'); write('0' + value % 10); }
        }
    }
}
