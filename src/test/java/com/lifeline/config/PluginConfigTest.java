package com.lifeline.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

public class PluginConfigTest {

    @Test
    public void testDefaultConfigValues() {
        String yaml = """
                max-revives: 0
                downed-timer-seconds: 30
                downed-invulnerable: true
                revive-channel-seconds: 3
                revive-max-distance: 4.0
                revive-health-restored: 6.0
                downed-effects:
                  darkness: true
                  blindness: true
                  glowing: true
                  slowness-level: 5
                revive-buffs:
                  regeneration:
                    enabled: true
                    duration-seconds: 4
                    amplifier: 1
                  resistance:
                    enabled: true
                    duration-seconds: 3
                    amplifier: 0
                  fire-resistance:
                    enabled: true
                    duration-seconds: 10
                    amplifier: 0
                sound-effects: true
                particles: true
                waypoints:
                  teleport-warmup-seconds: 3
                """;

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        PluginConfig config = new PluginConfig(null);
        config.load(configuration);

        assertEquals(0, config.getMaxRevives());
        assertEquals(30, config.getDownedTimerSeconds());
        assertTrue(config.isReviveEnabled());
        assertTrue(config.isDownedInvulnerable());
        assertEquals(3, config.getReviveChannelSeconds());
        assertEquals(4.0, config.getReviveMaxDistance());
        assertEquals(6.0, config.getReviveHealthRestored());
        assertTrue(config.isDownedDarkness());
        assertTrue(config.isDownedBlindness());
        assertTrue(config.isDownedGlowing());
        assertEquals(5, config.getDownedSlownessLevel());
        assertTrue(config.isRegenEnabled());
        assertEquals(4, config.getRegenDuration());
        assertEquals(1, config.getRegenAmplifier());
        assertTrue(config.isResistanceEnabled());
        assertEquals(3, config.getResistanceDuration());
        assertEquals(0, config.getResistanceAmplifier());
        assertTrue(config.isFireResistanceEnabled());
        assertEquals(10, config.getFireResistanceDuration());
        assertEquals(0, config.getFireResistanceAmplifier());
        assertTrue(config.isSoundEffectsEnabled());
        assertTrue(config.isParticlesEnabled());
        assertEquals(3, config.getWaypointWarmupSeconds());
        assertEquals(40.0, config.getRadarMaxDistance());
    }

    @Test
    public void testDownedTimerClampingAndDisableToggle() {
        PluginConfig config = new PluginConfig(null);

        // Test 0 disables revive mode
        YamlConfiguration configDisabled = YamlConfiguration.loadConfiguration(new StringReader("downed-timer-seconds: 0"));
        config.load(configDisabled);
        assertEquals(0, config.getDownedTimerSeconds());
        assertFalse(config.isReviveEnabled());

        // Test values above 60 are clamped to 60
        YamlConfiguration configOverMax = YamlConfiguration.loadConfiguration(new StringReader("downed-timer-seconds: 120"));
        config.load(configOverMax);
        assertEquals(60, config.getDownedTimerSeconds());
        assertTrue(config.isReviveEnabled());

        // Test negative values clamped to 0
        YamlConfiguration configNegative = YamlConfiguration.loadConfiguration(new StringReader("downed-timer-seconds: -10"));
        config.load(configNegative);
        assertEquals(0, config.getDownedTimerSeconds());
        assertFalse(config.isReviveEnabled());

        // Test explicit revive-enabled: false toggle
        YamlConfiguration configExplicitDisabled = YamlConfiguration.loadConfiguration(new StringReader("revive-enabled: false\ndowned-timer-seconds: 30"));
        config.load(configExplicitDisabled);
        assertEquals(30, config.getDownedTimerSeconds());
        assertFalse(config.isReviveEnabled());
    }

    @Test
    public void testMaxRevivesParsing() {
        PluginConfig config = new PluginConfig(null);

        // 0 = infinite
        YamlConfiguration infiniteConfig = YamlConfiguration.loadConfiguration(new StringReader("max-revives: 0"));
        config.load(infiniteConfig);
        assertEquals(0, config.getMaxRevives());

        // positive finite count
        YamlConfiguration finiteConfig = YamlConfiguration.loadConfiguration(new StringReader("max-revives: 5"));
        config.load(finiteConfig);
        assertEquals(5, config.getMaxRevives());

        // negative value clamped to 0 (infinite)
        YamlConfiguration negativeConfig = YamlConfiguration.loadConfiguration(new StringReader("max-revives: -2"));
        config.load(negativeConfig);
        assertEquals(0, config.getMaxRevives());
    }

    @Test
    public void testWaypointWarmupInstantSetting() {
        PluginConfig config = new PluginConfig(null);

        // 0 means instant teleportation
        YamlConfiguration instantWp = YamlConfiguration.loadConfiguration(new StringReader("waypoints:\n  teleport-warmup-seconds: 0"));
        config.load(instantWp);
        assertEquals(0, config.getWaypointWarmupSeconds());
    }

    @Test
    public void testTetherConfigSettings() {
        PluginConfig config = new PluginConfig(null);

        String yaml = """
                tether:
                  teleport-warmup-seconds: 5
                  request-timeout-seconds: 45
                  cooldown-seconds: 10
                """;
        YamlConfiguration tetherConfig = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        config.load(tetherConfig);

        assertEquals(5, config.getTetherWarmupSeconds());
        assertEquals(45, config.getTetherTimeoutSeconds());
        assertEquals(10, config.getTetherCooldownSeconds());
    }

    @Test
    public void testWaypointPaginationAndClamping() {
        PluginConfig config = new PluginConfig(null);

        // Default max-pages should be 2, max waypoints 90
        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new StringReader("waypoints:\n  max-pages: 2"));
        config.load(defaultConfig);
        assertEquals(2, config.getWaypointMaxPages());
        assertEquals(90, config.getMaxWaypoints());

        // 1 page should give 45 waypoints
        YamlConfiguration singlePage = YamlConfiguration.loadConfiguration(new StringReader("waypoints:\n  max-pages: 1"));
        config.load(singlePage);
        assertEquals(1, config.getWaypointMaxPages());
        assertEquals(45, config.getMaxWaypoints());

        // Hard max is 2 (values > 2 clamped to 2)
        YamlConfiguration overMax = YamlConfiguration.loadConfiguration(new StringReader("waypoints:\n  max-pages: 5"));
        config.load(overMax);
        assertEquals(2, config.getWaypointMaxPages());
        assertEquals(90, config.getMaxWaypoints());

        // Values < 1 clamped to 1
        YamlConfiguration underMin = YamlConfiguration.loadConfiguration(new StringReader("waypoints:\n  max-pages: 0"));
        config.load(underMin);
        assertEquals(1, config.getWaypointMaxPages());
        assertEquals(45, config.getMaxWaypoints());
    }

    @Test
    public void testMessageUtilLoadingAndPlaceholders() {
        String yaml = """
                prefix: "<gradient:#00FFA3:#00B8D9>Lifeline</gradient> » "
                waypoints:
                  gui-title: "<bold>Waypoints (Page <page>/<max_pages>)</bold>"
                  item-lore:
                    - "Dimension: <dim>"
                    - "Location: <x>, <y>, <z>"
                """;
        YamlConfiguration msgConfig = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        com.lifeline.util.MessageUtil.load(msgConfig);

        assertEquals("<gradient:#00FFA3:#00B8D9>Lifeline</gradient> » ", com.lifeline.util.MessageUtil.getPrefix());

        net.kyori.adventure.text.Component title = com.lifeline.util.MessageUtil.get("waypoints.gui-title",
                com.lifeline.util.MessageUtil.p("page", "1"),
                com.lifeline.util.MessageUtil.p("max_pages", "2"));
        assertNotNull(title);

        java.util.List<net.kyori.adventure.text.Component> lore = com.lifeline.util.MessageUtil.getList("waypoints.item-lore",
                com.lifeline.util.MessageUtil.p("dim", "Overworld"),
                com.lifeline.util.MessageUtil.p("x", "100"),
                com.lifeline.util.MessageUtil.p("y", "64"),
                com.lifeline.util.MessageUtil.p("z", "-200"));
        assertEquals(2, lore.size());
    }

    @Test
    public void testBedrockFormsAndRadarConfigSettings() {
        PluginConfig config = new PluginConfig(null);

        String yaml = """
                bedrock-forms:
                  enabled: true
                radar:
                  enabled: true
                  enabled-by-default: true
                  max-distance: 75.0
                  update-interval-ticks: 8
                """;
        YamlConfiguration yamlConfig = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        config.load(yamlConfig);

        assertTrue(config.isBedrockFormsEnabled());
        assertTrue(config.isRadarEnabled());
        assertTrue(config.isRadarDefaultEnabled());
        assertEquals(75.0, config.getRadarMaxDistance());
        assertEquals(8, config.getRadarUpdateIntervalTicks());
    }

    @Test
    public void testRadarDisabledAndClamping() {
        PluginConfig config = new PluginConfig(null);

        String yaml = """
                radar:
                  enabled: false
                  enabled-by-default: false
                  max-distance: 2.0
                  update-interval-ticks: 0
                """;
        YamlConfiguration yamlConfig = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        config.load(yamlConfig);

        assertFalse(config.isRadarEnabled());
        assertFalse(config.isRadarDefaultEnabled());
        assertEquals(5.0, config.getRadarMaxDistance()); // Clamped to min 5.0
        assertEquals(1, config.getRadarUpdateIntervalTicks()); // Clamped to min 1
    }
}
