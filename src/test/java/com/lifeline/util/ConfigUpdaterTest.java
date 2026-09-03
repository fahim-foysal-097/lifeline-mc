package com.lifeline.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigUpdaterTest {

    @Test
    public void testMergeAppendsMissingKeysAndPreservesUserValues() {
        // Defaults from jar (e.g. new plugin version with waypoints.max-pages and tether settings)
        String defaultYaml = """
                max-revives: 0
                downed-timer-seconds: 30
                downed-invulnerable: true
                waypoints:
                  teleport-warmup-seconds: 3
                  max-pages: 2
                tether:
                  teleport-warmup-seconds: 3
                  request-timeout-seconds: 60
                  cooldown-seconds: 30
                new-feature:
                  enabled: true
                  threshold: 10
                """;

        // User's existing config from previous version (missing waypoints.max-pages, tether, new-feature)
        String userYaml = """
                max-revives: 5
                downed-timer-seconds: 45
                waypoints:
                  teleport-warmup-seconds: 0
                """;

        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new StringReader(defaultYaml));
        YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(new StringReader(userYaml));

        List<String> addedKeys = ConfigUpdater.merge(defaultConfig, userConfig);

        // Verify that missing keys were added
        assertTrue(addedKeys.contains("downed-invulnerable"));
        assertTrue(addedKeys.contains("waypoints.max-pages"));
        assertTrue(addedKeys.contains("tether"));
        assertTrue(addedKeys.contains("new-feature"));

        // Verify user values were strictly preserved
        assertEquals(5, userConfig.getInt("max-revives"));
        assertEquals(45, userConfig.getInt("downed-timer-seconds"));
        assertEquals(0, userConfig.getInt("waypoints.teleport-warmup-seconds"));

        // Verify default values for new keys were merged
        assertTrue(userConfig.getBoolean("downed-invulnerable"));
        assertEquals(2, userConfig.getInt("waypoints.max-pages"));
        assertEquals(3, userConfig.getInt("tether.teleport-warmup-seconds"));
        assertEquals(60, userConfig.getInt("tether.request-timeout-seconds"));
        assertEquals(30, userConfig.getInt("tether.cooldown-seconds"));
        assertTrue(userConfig.getBoolean("new-feature.enabled"));
        assertEquals(10, userConfig.getInt("new-feature.threshold"));
    }

    @Test
    public void testMergeMessagesYamlPreservesCustomTranslations() {
        String defaultMessages = """
                prefix: "<gradient:#00FFA3:#00B8D9>Lifeline</gradient> » "
                general:
                  player-only: "<red>Only players can run this."
                  no-permission: "<red>No permission."
                teleport:
                  self-target: "<red>Cannot teleport to self."
                  new-teleport-message: "<green>Teleport started!"
                """;

        String userMessages = """
                prefix: "<gold>[MyServer]</gold> "
                general:
                  player-only: "<red>Custom player only message."
                """;

        YamlConfiguration defaultMsg = YamlConfiguration.loadConfiguration(new StringReader(defaultMessages));
        YamlConfiguration userMsg = YamlConfiguration.loadConfiguration(new StringReader(userMessages));

        List<String> added = ConfigUpdater.merge(defaultMsg, userMsg);

        // Verify user customized values stay untouched
        assertEquals("<gold>[MyServer]</gold> ", userMsg.getString("prefix"));
        assertEquals("<red>Custom player only message.", userMsg.getString("general.player-only"));

        // Verify missing translations were appended
        assertTrue(added.contains("general.no-permission"));
        assertTrue(added.contains("teleport"));
        assertEquals("<red>No permission.", userMsg.getString("general.no-permission"));
        assertEquals("<green>Teleport started!", userMsg.getString("teleport.new-teleport-message"));
    }

    @Test
    public void testMergeWithIdenticalConfigAddsNothing() {
        String yaml = """
                max-revives: 0
                downed-timer-seconds: 30
                """;

        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(new StringReader(yaml));

        List<String> added = ConfigUpdater.merge(defaultConfig, userConfig);
        assertTrue(added.isEmpty());
    }

    @Test
    public void testMergeWithCorruptedOrChangedKeyTypes() {
        String defaultYaml = """
                waypoints:
                  teleport-warmup-seconds: 3
                  max-pages: 2
                """;

        // User had waypoints as a string scalar in an older corrupted file
        String userYaml = """
                waypoints: "old_invalid_value"
                """;

        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new StringReader(defaultYaml));
        YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(new StringReader(userYaml));

        List<String> added = ConfigUpdater.merge(defaultConfig, userConfig);
        assertTrue(added.contains("waypoints"));
        assertTrue(userConfig.isConfigurationSection("waypoints"));
        assertEquals(3, userConfig.getInt("waypoints.teleport-warmup-seconds"));
        assertEquals(2, userConfig.getInt("waypoints.max-pages"));
    }

    @Test
    public void testMergeCopiesCommentsAndInlineComments() {
        String defaultYaml = """
                # Primary revive setting
                # Set to 0 for infinite revives
                max-revives: 3 # default 3
                # Waypoint settings
                waypoints:
                  # Warmup before teleport
                  teleport-warmup-seconds: 3
                """;

        String userYaml = """
                max-revives: 5
                """;

        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new StringReader(defaultYaml));
        YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(new StringReader(userYaml));

        ConfigUpdater.merge(defaultConfig, userConfig);

        // Comments should be copied for existing key if missing
        List<String> maxRevivesComments = userConfig.getComments("max-revives");
        assertFalse(maxRevivesComments.isEmpty());
        assertTrue(maxRevivesComments.contains("Primary revive setting"));

        // Inline comment should be copied
        List<String> maxRevivesInline = userConfig.getInlineComments("max-revives");
        assertFalse(maxRevivesInline.isEmpty());
        assertTrue(maxRevivesInline.contains("default 3"));

        // Comments should be copied for new section and child keys
        List<String> waypointComments = userConfig.getComments("waypoints");
        assertFalse(waypointComments.isEmpty());
        assertTrue(waypointComments.contains("Waypoint settings"));

        List<String> warmupComments = userConfig.getComments("waypoints.teleport-warmup-seconds");
        assertFalse(warmupComments.isEmpty());
        assertTrue(warmupComments.contains("Warmup before teleport"));
    }
}
