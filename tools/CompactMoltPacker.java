import com.codex.blacklight.util.MoltAnimationData;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipFile;

/** Build-only, lossless filter for the authored Greene scene and infection climb captures. */
public final class CompactMoltPacker {
    private static final String RESOURCE_DIR = "assets/blacklight/greene/molt/";
    private static final List<String> CLIPS = List.of("scene.bin.gz", "clamber.bin.gz");
    private record Layout(int prefix, int frames, int order) {}

    private static final class BestGzip extends GZIPOutputStream {
        BestGzip(OutputStream output) throws IOException {
            super(output, 65536);
            def.setLevel(Deflater.BEST_COMPRESSION);
        }
    }

    private static Layout layout(byte[] raw, String name) throws IOException {
        if (raw.length < 16) throw new IOException("Missing Greene clip header");
        var view = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int prefix = 16;
        int frames;
        if (name.equals("clamber.bin.gz")) {
            if (view.getInt(0) != 0x434c4d31) throw new IOException("Unknown climbing clip");
            frames = view.getInt(4);
        } else {
            if (view.getInt(0) != 0x474d4c32) throw new IOException("Unknown molt scene");
            int lines = view.getInt(8);
            frames = view.getInt(12);
            long position = 4L * (4 + 36 + 4L * lines + 18);
            for (int mesh = 0; mesh < 2; mesh++) {
                if (position < 16 || position > raw.length - 4) throw new IOException("Invalid Greene scene mesh");
                int vertices = view.getInt((int) position);
                if (vertices < 0) throw new IOException("Negative Greene scene vertex count");
                position += 4 + 48L * vertices;
            }
            if (position >= raw.length) throw new IOException("Missing Greene timeline");
            prefix = (int) position;
        }
        if (frames < 1 || frames > 4096 || (raw.length - prefix) % (4 * frames) != 0)
            throw new IOException("Incomplete Greene frames");
        // Measured on the two authored clips. The climb rigs compress best with
        // first differences, while the smoother molt trajectories use second differences.
        return new Layout(prefix, frames, name.equals("clamber.bin.gz") ? 1 : 2);
    }

    private static void writePlanes(OutputStream stream, byte[] raw, Layout layout) throws IOException {
        int prefix = layout.prefix();
        int frames = layout.frames();
        int stride = (raw.length - prefix) / frames;
        for (int lane = 0; lane < 4; lane++) {
            for (int component = 0; component < MoltAnimationData.VERTEX_COMPONENTS; component++) {
                for (int at = component * 4 + lane; at < prefix; at += MoltAnimationData.VERTEX_COMPONENTS * 4)
                    stream.write(raw[at]);
            }
        }
        var words = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        byte[] track = new byte[frames];
        for (int lane = 0; lane < 4; lane++) {
            for (int offset = 0; offset < stride; offset += 4) {
                int previous = 0, lastStep = 0;
                int at = prefix + offset;
                for (int frame = 0; frame < frames; frame++, at += stride) {
                    int value = words.getInt(at);
                    int step = value - previous;
                    int residual = layout.order() == 2 ? step - lastStep : step;
                    int zigzag = (residual << 1) ^ (residual >> 31);
                    track[frame] = (byte) (zigzag >>> (lane * 8));
                    previous = value;
                    lastStep = step;
                }
                stream.write(track);
            }
        }
    }

    private static void encode(byte[] raw, String name, Path target) throws IOException {
        Layout layout = layout(raw, name);
        try (var envelope = new DataOutputStream(new BufferedOutputStream(new BestGzip(Files.newOutputStream(target)), 65536))) {
            envelope.writeInt(MoltAnimationData.PACKED_MAGIC);
            envelope.writeInt(raw.length);
            envelope.writeInt(layout.prefix());
            envelope.writeInt(layout.frames());
            envelope.writeInt(layout.order());
            writePlanes(envelope, raw, layout);
        }
    }

    private static byte[] original(Path root, String name) throws IOException {
        try (var input = new GZIPInputStream(Files.newInputStream(root.resolve(RESOURCE_DIR + name)))) {
            return input.readAllBytes();
        }
    }

    private static void verify(Path source, Path jar) throws IOException {
        try (var archive = new ZipFile(jar.toFile())) {
            for (String name : CLIPS) {
                var entry = archive.getEntry(RESOURCE_DIR + name);
                if (entry == null) throw new IOException("Missing packaged " + name);
                try (var header = new java.io.DataInputStream(new GZIPInputStream(archive.getInputStream(entry)))) {
                    if (header.readInt() != MoltAnimationData.PACKED_MAGIC)
                        throw new IOException("Unconverted animation in JAR: " + name);
                }
                byte[] raw = original(source, name);
                long start = System.nanoTime();
                byte[] restored = MoltAnimationData.read(archive.getInputStream(entry));
                if (!Arrays.equals(raw, restored)) throw new IOException("Packaged Greene animation differs: " + name);
                System.out.printf("VERIFIED %s decodedBytes=%d jarBytes=%d decodeMs=%d%n",
                        name, raw.length, entry.getCompressedSize(), (System.nanoTime() - start) / 1_000_000);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 3 && args[0].equals("--verify")) {
            verify(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        if (args.length != 2) throw new IllegalArgumentException("sourceResources generatedResources");
        Path source = Path.of(args[0]).toRealPath();
        Path generated = Path.of(args[1]).toAbsolutePath().normalize();
        if (generated.startsWith(source) || source.startsWith(generated)) throw new IOException("Overlapping Greene asset directories");
        Path output = Files.createDirectories(generated.resolve(RESOURCE_DIR));
        for (String name : CLIPS) {
            byte[] raw = original(source, name);
            Path temporary = Files.createTempFile(output, name, ".tmp");
            try {
                encode(raw, name, temporary);
                byte[] restored = MoltAnimationData.read(Files.newInputStream(temporary));
                if (!Arrays.equals(raw, restored)) throw new IOException("Greene animation differs: " + name);
                Files.move(temporary, output.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
