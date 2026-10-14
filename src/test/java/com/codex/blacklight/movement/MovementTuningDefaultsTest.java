package com.codex.blacklight.movement;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MovementTuningDefaultsTest {
    @Test
    void shippedPresetMatchesCompiledMvpDefaults() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
                Path.of(System.getProperty("user.dir"), "config-presets",
                        "blacklight-movement-engine.properties"))) {
            properties.load(reader);
        }

        MovementTuningSnapshot defaults = MovementTuningSettings.MVP_DEFAULTS;
        assertProperty(properties, "sprint_acceleration", defaults.sprintAcceleration());
        assertProperty(properties, "sprint_max_speed", defaults.sprintMaxSpeed());
        assertProperty(properties, "jump_min_velocity", defaults.jumpMinVelocity());
        assertProperty(properties, "jump_max_velocity", defaults.jumpMaxVelocity());
        assertProperty(properties, "jump_charge_ticks", defaults.jumpChargeTicks());
        assertProperty(properties, "wall_detect_distance", defaults.wallDetectDistance());
        assertProperty(properties, "wall_climb_velocity", defaults.wallClimbVelocity());
        assertProperty(properties, "glide_fall_speed", defaults.glideFallSpeed());
        assertProperty(properties, "glide_max_speed", defaults.glideMaxSpeed());
        assertProperty(properties, "air_dash_speed", defaults.airDashSpeed());
        assertProperty(properties, "air_dash_ticks", defaults.airDashTicks());
        assertProperty(properties, "fov_boost", defaults.fovBoost());
    }

    private static void assertProperty(Properties properties, String key, double expected) {
        assertEquals(expected, Double.parseDouble(properties.getProperty(key)), 1.0E-12D, key);
    }
    @Test void oldShippedSprintPresetIsMigratedWithoutChangingOtherSettings() {
        Properties values=new Properties();
        values.setProperty("sprint_acceleration","0.23968446601941748");
        values.setProperty("sprint_max_speed","2.0645631067961165");
        values.setProperty("jump_max_velocity","1.5");
        org.junit.jupiter.api.Assertions.assertTrue(MovementTuningSettings.migrateLegacySprint(values));
        assertProperty(values,"sprint_acceleration",.055);
        assertProperty(values,"sprint_max_speed",.95);
        assertProperty(values,"jump_max_velocity",1.5);
        org.junit.jupiter.api.Assertions.assertFalse(MovementTuningSettings.migrateLegacySprint(values));
    }
    @Test void customSprintAndMissingPresetsRemainUserOwned() {
        Properties values=new Properties();
        org.junit.jupiter.api.Assertions.assertFalse(MovementTuningSettings.migrateLegacySprint(values));
        values.setProperty("sprint_acceleration","0.1");
        values.setProperty("sprint_max_speed","2.0645631067961165");
        org.junit.jupiter.api.Assertions.assertFalse(MovementTuningSettings.migrateLegacySprint(values));
        assertProperty(values,"sprint_acceleration",.1);
        assertProperty(values,"sprint_max_speed",2.0645631067961165);
    }
}
