package com.codex.blacklight.combat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.codex.blacklight.util.CompactJson;
import com.codex.blacklight.util.CompactStreams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** One profile source for animation, reaction and contact data on both logical sides. */
public final class MotionResources {
    private static final String ROOT = "/blacklight/bundled_motions/";
    private MotionResources() {}

    private static void validate(String name) {
        if (!name.matches("[a-z0-9_-]+\\.(json|data)"))
            throw new IllegalArgumentException("Invalid motion resource: " + name);
    }

    private static Path override(String name) {
        String directory = System.getProperty(LocalWeaponMotion.DIRECTORY_PROPERTY);
        return directory == null || directory.isBlank() ? null : Path.of(directory).resolve(name);
    }

    public static boolean contains(String name) {
        validate(name);
        Path path = override(name);
        return path != null ? Files.isRegularFile(path) : MotionResources.class.getResource(ROOT + name + ".jz") != null
                || MotionResources.class.getResource(ROOT + name) != null;
    }

    /** An explicit developer directory replaces the whole profile; never mix profiles per file. */
    public static Optional<JsonObject> read(String name, int maximumBytes) {
        validate(name);
        Path path = override(name);
        if (path != null && !Files.isRegularFile(path)) return Optional.empty();
        boolean compact=path==null&&MotionResources.class.getResource(ROOT+name+".jz")!=null;
        try (InputStream input = path != null ? Files.newInputStream(path)
                : MotionResources.class.getResourceAsStream(ROOT + name+(compact?".jz":""))) {
            if (input == null) return Optional.empty();
            byte[] bytes = compact?CompactJson.decode(CompactStreams.read(input,Math.addExact(Math.multiplyExact(maximumBytes,8),64)),maximumBytes)
                    :input.readNBytes(maximumBytes + 1);
            if (bytes.length > maximumBytes) throw new IllegalArgumentException("Motion resource too large: " + name);
            return Optional.of(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot load motion resource " + (path != null ? path : ROOT + name), e);
        }
    }
}
