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
}
