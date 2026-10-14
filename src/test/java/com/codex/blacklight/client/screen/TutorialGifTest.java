package com.codex.blacklight.client.screen;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TutorialGifTest {
    @Test void allShippedTutorialsDecodeWithinTheMemoryBudget() throws Exception {
        try(var files=Files.list(Path.of("src/main/resources/assets/blacklight/tutorials"))) {
            var clips=files.filter(p->p.toString().endsWith(".gif")).toList();
            assertEquals(29,clips.size());
            for(var path:clips)assertNotNull(TutorialGif.decode(Files.readAllBytes(path),()->false),path.toString());
        }
    }
    @Test void aClosedPageCancelsBeforeImageAllocation() {
        assertThrows(java.util.concurrent.CancellationException.class,()->TutorialGif.decode(new byte[0],()->true));
    }
}
