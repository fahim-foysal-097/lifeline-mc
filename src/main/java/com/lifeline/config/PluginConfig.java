package com.lifeline.config;

import com.lifeline.Lifeline;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Manages configuration loading, clamping, validation, and retrieval for Lifeline.
 */
public class PluginConfig {

    private final Lifeline plugin;

    private int maxRevives;
    private boolean reviveEnabled;
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
    private int waypointMaxPages;

    // Tether (/tpq)
    private int tetherWarmupSeconds;
    private int tetherTimeoutSeconds;
    private int tetherCooldownSeconds;

    // Bedrock Forms
    private boolean bedrockFormsEnabled;

    // Teammate Radar
    private boolean radarEnabled;
    private boolean radarDefaultEnabled;
    private double radarMaxDistance;
    private int radarUpdateIntervalTicks;

    // Personal Stash
    private boolean personalStashEnabled;
    private int personalStashSlots;

    // Personal Waypoints
    private boolean personalWaypointsEnabled;

    // Data Backup & Safety
    private boolean backupEnabled;
    private boolean backupSyncWithAutosave;
    private int backupMaxBackups;

    // Update Checker
    private boolean updateCheckerEnabled;

    // Quick Trash
    private boolean trashEnabled;
    private boolean trashSoundEffectsEnabled;

    public PluginConfig(Lifeline plugin) {
        this.plugin = plugin;
        if (plugin != null) {
            load();
        }
    }

    /**
     * Loads and validates all values from config.yml, automatically merging newly added keys.
     */
    public void load() {
        if (plugin != null) {
            com.lifeline.util.ConfigUpdater.update(plugin, "config.yml");
            plugin.reloadConfig();
            load(plugin.getConfig());
        }
    }

    /**
     * Loads and validates all values from a provided FileConfiguration.
     */
    public void load(FileConfiguration config) {

        // 1. Max Revives (0 = infinite revives)
        this.maxRevives = Math.max(0, config.getInt("max-revives", 0));

        // 2. Explicit revive system enable/disable flag
        this.reviveEnabled = config.getBoolean("revive-enabled", true);

        // 3. Downed Timer in seconds (0 = instant bleed-out, max = 60)
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
        // Hard max is 2 pages (range 1..2)
        this.waypointMaxPages = Math.max(1, Math.min(2, config.getInt("waypoints.max-pages", 2)));

        // 9. Tether settings
        this.tetherWarmupSeconds = Math.max(0, config.getInt("tether.teleport-warmup-seconds", 3));
        this.tetherTimeoutSeconds = Math.max(5, config.getInt("tether.request-timeout-seconds", 60));
        this.tetherCooldownSeconds = Math.max(0, config.getInt("tether.cooldown-seconds", 0));

        // 10. Bedrock Forms
        this.bedrockFormsEnabled = config.getBoolean("bedrock-forms.enabled", true);

        // 11. Teammate Radar
        this.radarEnabled = config.getBoolean("radar.enabled", true);
        this.radarDefaultEnabled = config.getBoolean("radar.enabled-by-default", true);
        this.radarMaxDistance = Math.max(5.0, config.getDouble("radar.max-distance", 40.0));
        this.radarUpdateIntervalTicks = Math.max(1, config.getInt("radar.update-interval-ticks", 10));

        // 12. Personal Stash
        this.personalStashEnabled = config.getBoolean("personal-stash.enabled", true);
        int rawStashSlots = config.getInt("personal-stash.slots", 27);
        this.personalStashSlots = (rawStashSlots == 54) ? 54 : 27;

        // 13. Personal Waypoints
        this.personalWaypointsEnabled = config.getBoolean("personal-waypoints.enabled", true);

        // 14. Data Backup & Safety
        this.backupEnabled = config.getBoolean("backup.enabled", true);
        this.backupSyncWithAutosave = config.getBoolean("backup.sync-with-autosave", true);
        this.backupMaxBackups = Math.max(1, config.getInt("backup.max-backups", 3));

        // 15. Update Checker
        this.updateCheckerEnabled = config.getBoolean("update-checker", true);

        // 16. Quick Trash
        this.trashEnabled = config.getBoolean("trash.enabled", true);
        this.trashSoundEffectsEnabled = config.getBoolean("trash.sound-effects", true);
    }

    public int getMaxRevives() {
        return maxRevives;
    }

    public int getDownedTimerSeconds() {
        return downedTimerSeconds;
    }

    public boolean isReviveEnabled() {
        return reviveEnabled && downedTimerSeconds > 0;
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

    public int getWaypointMaxPages() {
        return waypointMaxPages;
    }

    public int getMaxWaypoints() {
        return waypointMaxPages * 45;
    }

    public int getTetherWarmupSeconds() {
        return tetherWarmupSeconds;
    }

    public int getTetherTimeoutSeconds() {
        return tetherTimeoutSeconds;
    }

    public int getTetherCooldownSeconds() {
        return tetherCooldownSeconds;
    }

    public boolean isBedrockFormsEnabled() {
        return bedrockFormsEnabled;
    }

    public boolean isRadarEnabled() {
        return radarEnabled;
    }

    public boolean isRadarDefaultEnabled() {
        return radarDefaultEnabled;
    }

    public double getRadarMaxDistance() {
        return radarMaxDistance;
    }

    public int getRadarUpdateIntervalTicks() {
        return radarUpdateIntervalTicks;
    }

    public boolean isPersonalStashEnabled() {
        return personalStashEnabled;
    }

    public int getPersonalStashSlots() {
        return personalStashSlots;
    }

    public boolean isPersonalWaypointsEnabled() {
        return personalWaypointsEnabled;
    }

    public boolean isBackupEnabled() {
        return backupEnabled;
    }

    public boolean isBackupSyncWithAutosave() {
        return backupSyncWithAutosave;
    }

    public int getBackupMaxBackups() {
        return backupMaxBackups;
    }

    public boolean isUpdateCheckerEnabled() {
        return updateCheckerEnabled;
    }

    public boolean isTrashEnabled() {
        return trashEnabled;
    }

    public boolean isTrashSoundEffectsEnabled() {
        return trashSoundEffectsEnabled && soundEffectsEnabled;
    }
}
