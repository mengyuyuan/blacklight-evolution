import com.codex.blacklight.util.AtlasTileCodec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Build-only reversible prefilter. Unrecognized arrays and optional fields retain their exact UTF-8 bytes. */
final class AtlasTileFilter {
    private final byte[] source;
    private int[] values = new int[AtlasTileCodec.GRID * AtlasTileCodec.GRID];
    private int count;

    private AtlasTileFilter(byte[] source) { this.source = source; }

    static byte[] encode(byte[] source) throws IOException {
        return new AtlasTileFilter(source).filter();
    }

    private byte[] filter() throws IOException {
        var skeleton = new ByteArrayOutputStream();
        var numbers = new ByteArrayOutputStream();
        int from = 0;
        boolean quoted = false, escaped = false;
        for (int i = 0; i < source.length; i++) {
            int ch = source[i];
            if (ch == AtlasTileCodec.HEIGHTS || ch == AtlasTileCodec.COORDINATES) return source;
            if (quoted) {
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') quoted = false;
                continue;
            }
            if (ch == '"') { quoted = true; continue; }
            if (ch != '[') continue;
            boolean heights = i >= 4 && source[i - 4] == '"' && source[i - 3] == 'e'
                    && source[i - 2] == '"' && source[i - 1] == ':';
            int end = array(i + 1, heights);
            if (end < 0) continue;
            skeleton.write(source, from, i - from);
            skeleton.write(heights ? AtlasTileCodec.HEIGHTS : AtlasTileCodec.COORDINATES);
            if (!heights) varInt(numbers, count);
            for (int j = 0; j < count; j++) {
                int predicted = heights ? AtlasTileCodec.predict(values, j) : (j < 2 ? 0 : values[j - 2]);
                int delta = values[j] - predicted;
                varInt(numbers, (delta << 1) ^ (delta >> 31));
            }
            i = end;
            from = i + 1;
        }
        if (from == 0) return source;
        skeleton.write(source, from, source.length - from);
        if (12L + skeleton.size() + numbers.size() >= source.length) return source;
        var packed = new ByteArrayOutputStream(12 + skeleton.size() + numbers.size());
        var header = new DataOutputStream(packed);
        header.writeInt(AtlasTileCodec.MAGIC);
        header.writeInt(source.length);
        header.writeInt(skeleton.size());
        skeleton.writeTo(packed);
        numbers.writeTo(packed);
        return packed.toByteArray();
    }

    // Accept only decimal spellings which our decoder will reproduce byte for byte.
    private int array(int at, boolean heights) {
        count = 0;
        while (at < source.length) {
            boolean negative = source[at] == '-';
            if (negative) at++;
            int begin = at;
            long value = 0;
            while (at < source.length && digit(source[at])) {
                value = value * 10 + source[at++] - '0';
                if (value > Integer.MAX_VALUE) return -1;
            }
            if (at == begin || (at > begin + 1 && source[begin] == '0')) return -1;
            if (!heights) {
                if (at + 1 >= source.length || source[at] != '.' || !digit(source[at + 1])) return -1;
                value = 10 * value + source[at + 1] - '0';
                at += 2;
                // With this bound the difference between two signed coordinates fits an int.
                if (value > Integer.MAX_VALUE / 2) return -1;
            }
            if (negative && value == 0) return -1; // preserve -0 and -0.0 through the literal path
            if (negative) value = -value;
            if (heights && (value < Short.MIN_VALUE || value > Short.MAX_VALUE)) return -1;
            if (count == values.length) values = Arrays.copyOf(values, count * 2);
            values[count++] = (int) value;
            if (at == source.length) return -1;
            if (source[at] == ']') {
                if (heights ? count != AtlasTileCodec.GRID * AtlasTileCodec.GRID : (count & 1) != 0) return -1;
                return at;
            }
            if (source[at++] != ',') return -1;
        }
        return -1;
    }

    private static boolean digit(byte b) { return b >= '0' && b <= '9'; }

    private static void varInt(ByteArrayOutputStream out, int bits) {
        while ((bits & ~127) != 0) {
            out.write((bits & 127) | 128);
            bits >>>= 7;
        }
        out.write(bits);
    }
}
