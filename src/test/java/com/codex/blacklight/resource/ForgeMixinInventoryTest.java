package com.codex.blacklight.resource;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForgeMixinInventoryTest {
    @Test void everyConfiguredMixinExistsInTheCompiledForgeOutput() throws Exception {
        try (var input = getClass().getResourceAsStream("/blacklight.mixins.json")) {
            assertNotNull(input);
            var config = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            var prefix = config.get("package").getAsString().replace('.', '/') + "/";
            var names = new HashSet<String>();
            for (String side : List.of("mixins", "client")) for (var entry : config.getAsJsonArray(side)) {
                String name = entry.getAsString();
                assertTrue(names.add(name), "Duplicate Mixin: " + name);
                assertNotNull(getClass().getResource("/" + prefix + name.replace('.', '/') + ".class"),
                        "Missing compiled Forge Mixin: " + name);
            }
        }
    }
}
