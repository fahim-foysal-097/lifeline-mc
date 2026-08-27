package com.lifeline.config;

import com.lifeline.Lifeline;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Manages configuration loading, clamping, validation, and retrieval for Lifeline.
 */
public class PluginConfig {

    private final Lifeline plugin;

    private int maxRevives;
    private int downedTimerSeconds;
    private boolean downedInvulnerable;
    private int reviveChannelSeconds;
    private double reviveMaxDistance;
    private double reviveHealthRestored;

    // Downed debuffs
    private boolean downedDarkness;
    private boolean downedBlindness;
    private boolean downedGlowing;
    private int downedSlownessLevel;

    // Post-revive recovery buffs
    private boolean regenEnabled;
    private int regenDuration;
    private int regenAmplifier;

    private boolean resistanceEnabled;
    private int resistanceDuration;
    private int resistanceAmplifier;

    private boolean fireResistanceEnabled;
    private int fireResistanceDuration;
    private int fireResistanceAmplifier;

    // Audio & particles
    private boolean soundEffectsEnabled;
    private boolean particlesEnabled;

    // Waypoints
    private int waypointWarmupSeconds;

    public PluginConfig(Lifeline plugin) {
        this.plugin = plugin;
        if (plugin != null) {
            load();
        }
    }

    /**
     * Loads and validates all values from config.yml.
     */
    public void load() {
        plugin.reloadConfig();
        load(plugin.getConfig());
    }

    /**
     * Loads and validates all values from a provided FileConfiguration.
     */
    public void load(FileConfiguration config) {

        // 1. Max Revives (0 = infinite revives)
        this.maxRevives = Math.max(0, config.getInt("max-revives", 0));

        // 2. Downed Timer in seconds (0 = disabled, max = 60)
        int rawTimer = config.getInt("downed-timer-seconds", 30);
        this.downedTimerSeconds = Math.max(0, Math.min(60, rawTimer));

        // 3. Downed invulnerability
        this.downedInvulnerable = config.getBoolean("downed-invulnerable", true);

        // 4. Revive channel duration & range
        this.reviveChannelSeconds = Math.max(1, config.getInt("revive-channel-seconds", 3));
        this.reviveMaxDistance = Math.max(1.0, config.getDouble("revive-max-distance", 4.0));
        this.reviveHealthRestored = Math.max(1.0, config.getDouble("revive-health-restored", 6.0));

        // 5. Downed debuffs
        this.downedDarkness = config.getBoolean("downed-effects.darkness", true);
        this.downedBlindness = config.getBoolean("downed-effects.blindness", true);
        this.downedGlowing = config.getBoolean("downed-effects.glowing", true);
        this.downedSlownessLevel = Math.max(0, config.getInt("downed-effects.slowness-level", 5));

        // 6. Post-revive recovery buffs
        this.regenEnabled = config.getBoolean("revive-buffs.regeneration.enabled", true);
        this.regenDuration = Math.max(1, config.getInt("revive-buffs.regeneration.duration-seconds", 4));
        this.regenAmplifier = Math.max(0, config.getInt("revive-buffs.regeneration.amplifier", 1));

        this.resistanceEnabled = config.getBoolean("revive-buffs.resistance.enabled", true);
        this.resistanceDuration = Math.max(1, config.getInt("revive-buffs.resistance.duration-seconds", 3));
        this.resistanceAmplifier = Math.max(0, config.getInt("revive-buffs.resistance.amplifier", 0));

        this.fireResistanceEnabled = config.getBoolean("revive-buffs.fire-resistance.enabled", true);
        this.fireResistanceDuration = Math.max(1, config.getInt("revive-buffs.fire-resistance.duration-seconds", 10));
        this.fireResistanceAmplifier = Math.max(0, config.getInt("revive-buffs.fire-resistance.amplifier", 0));

        // 7. Audio & particles
        this.soundEffectsEnabled = config.getBoolean("sound-effects", true);
        this.particlesEnabled = config.getBoolean("particles", true);

        // 8. Waypoint settings
        this.waypointWarmupSeconds = Math.max(0, config.getInt("waypoints.teleport-warmup-seconds", 3));
    }

    public int getMaxRevives() {
        return maxRevives;
    }

    public int getDownedTimerSeconds() {
        return downedTimerSeconds;
    }

    public boolean isReviveEnabled() {
        return downedTimerSeconds > 0;
    }

    public boolean isDownedInvulnerable() {
        return downedInvulnerable;
    }

    public int getReviveChannelSeconds() {
        return reviveChannelSeconds;
    }

    public double getReviveMaxDistance() {
        return reviveMaxDistance;
    }

    public double getReviveHealthRestored() {
        return reviveHealthRestored;
    }

    public boolean isDownedDarkness() {
        return downedDarkness;
    }

    public boolean isDownedBlindness() {
        return downedBlindness;
    }

    public boolean isDownedGlowing() {
        return downedGlowing;
    }

    public int getDownedSlownessLevel() {
        return downedSlownessLevel;
    }

    public boolean isRegenEnabled() {
        return regenEnabled;
    }

    public int getRegenDuration() {
        return regenDuration;
    }

    public int getRegenAmplifier() {
        return regenAmplifier;
    }

    public boolean isResistanceEnabled() {
        return resistanceEnabled;
    }

    public int getResistanceDuration() {
        return resistanceDuration;
    }

    public int getResistanceAmplifier() {
        return resistanceAmplifier;
    }

    public boolean isFireResistanceEnabled() {
        return fireResistanceEnabled;
    }

    public int getFireResistanceDuration() {
        return fireResistanceDuration;
    }

    public int getFireResistanceAmplifier() {
        return fireResistanceAmplifier;
    }

    public boolean isSoundEffectsEnabled() {
        return soundEffectsEnabled;
    }

    public boolean isParticlesEnabled() {
        return particlesEnabled;
    }

    public int getWaypointWarmupSeconds() {
        return waypointWarmupSeconds;
    }
}
