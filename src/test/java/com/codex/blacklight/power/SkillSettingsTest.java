package com.codex.blacklight.power;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillSettingsTest {
    @Test void existingSavesWithoutPreferencesEnableBlockBreaking() {
        assertTrue(SkillSettings.DEFAULT.bulletdiveBlockBreaking());
        assertEquals(SkillSettings.DEFAULT, SkillSettings.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{}")).getOrThrow());
    }

    @Test void disablingBlockBreakingSurvivesSaveAndNetworkRoundTrips() {
        for (boolean enabled : new boolean[]{false, true}) {
            var settings = new SkillSettings(enabled);
            var saved = SkillSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
            assertEquals(settings, SkillSettings.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());
            var packet = Unpooled.buffer();
            try {
                SkillSettings.STREAM_CODEC.encode(packet, settings);
                assertEquals(settings, SkillSettings.STREAM_CODEC.decode(packet));
                assertFalse(packet.isReadable(), "round trip must consume the complete preference packet");
            } finally {
                packet.release();
            }
        }
    }

    @Test void corruptPreferenceCannotSilentlyTurnOffDestruction() {
        assertTrue(SkillSettings.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"bulletdive_block_breaking\":\"invalid\"}")).error().isPresent());
    }
}
