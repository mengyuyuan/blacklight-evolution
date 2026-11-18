package com.codex.blacklight.client.screen;

import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TutorialGifTest {
    @Test void allShippedTutorialsDecodeWithinTheMemoryBudget() throws Exception {
        var directory = Path.of("src/main/resources/assets/blacklight/tutorials");
        var manifest = JsonParser.parseString(Files.readString(directory.resolve("manifest.json"))).getAsJsonObject();
        try(var files=Files.list(directory)) {
            var clips=files.filter(p->p.toString().endsWith(".gif")).toList();
            var expected = new HashSet<String>();
            manifest.entrySet().forEach(entry -> expected.add(entry.getValue().getAsJsonObject().get("file").getAsString()));
            var actual = new HashSet<String>();
            clips.forEach(path -> actual.add(path.getFileName().toString()));
            assertEquals(expected, actual);
            for(var path:clips)assertNotNull(TutorialGif.decode(Files.readAllBytes(path),()->false),path.toString());
        }
    }
    @Test void aClosedPageCancelsBeforeImageAllocation() {
        assertThrows(java.util.concurrent.CancellationException.class,()->TutorialGif.decode(new byte[0],()->true));
    }
}
