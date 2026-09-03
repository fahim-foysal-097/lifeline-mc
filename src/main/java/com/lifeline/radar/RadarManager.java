package com.lifeline.radar;

import com.lifeline.Lifeline;
import com.lifeline.config.PluginConfig;
import com.lifeline.revive.DownedManager;
import com.lifeline.revive.DownedState;
import com.lifeline.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages the Teammate Actionbar Radar, displaying live distance, dynamic directional
 * arrow, health, and status of nearby co-op partners.
 *
 * Per-player radar toggle preferences are persisted to radar-toggles.yml so they
 * survive relogs and server restarts.
 */
public class RadarManager implements Listener {

    private final Lifeline plugin;
    private final Map<UUID, Boolean> playerToggles = new ConcurrentHashMap<>();
    private BukkitTask task;

    /** Backing store for persisted toggle preferences. */
    private final File toggleFile;
    private YamlConfiguration toggleConfig;

    public RadarManager(Lifeline plugin) {
        this.plugin = plugin;
        this.toggleFile = new File(plugin.getDataFolder(), "radar-toggles.yml");
        loadToggles();
        startTask();
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private void loadToggles() {
        if (!toggleFile.exists()) {
            toggleConfig = new YamlConfiguration();
            return;
        }
        toggleConfig = YamlConfiguration.loadConfiguration(toggleFile);
        for (String key : toggleConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                playerToggles.put(uuid, toggleConfig.getBoolean(key));
            } catch (IllegalArgumentException ignored) {
                // malformed entry — skip
            }
        }
    }

    private void saveToggle(UUID uuid, boolean state) {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        toggleConfig.set(uuid.toString(), state);
        try {
            com.lifeline.util.SafeFileUtil.saveConfigurationAtomically(toggleConfig, toggleFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save radar-toggles.yml", e);
        }
    }

    // -------------------------------------------------------------------------
    // Task management
    // -------------------------------------------------------------------------

    /**
     * Starts or restarts the periodic actionbar radar update task.
     */
    public synchronized void startTask() {
        if (task != null) {
            task.cancel();
        }

        PluginConfig config = plugin.getPluginConfig();
        if (!config.isRadarEnabled()) {
            return;
        }

        long interval = Math.max(1, config.getRadarUpdateIntervalTicks());
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRadar, interval, interval);
    }

    /**
     * Ticks the radar display for all online players.
     */
    private void tickRadar() {
        PluginConfig config = plugin.getPluginConfig();
        if (!config.isRadarEnabled()) {
            return;
        }

        double maxDist = config.getRadarMaxDistance();
        double maxDistSq = maxDist * maxDist;
        DownedManager downedManager = plugin.getDownedManager();

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!isRadarActive(viewer)) {
                continue;
            }

            // Suppress radar if viewer has an active priority actionbar
            if (isActionbarBusy(viewer, downedManager)) {
                continue;
            }

            // Find closest eligible teammate in the same world
            Player partner = findClosestPartner(viewer, maxDistSq);
            if (partner == null) {
                // Partner not nearby or in another dimension -> don't display actionbar
                continue;
            }

            int dist = (int) viewer.getLocation().distance(partner.getLocation());
            String arrow = calculateDirectionArrow(viewer.getLocation(), partner.getLocation());

            if (downedManager != null && downedManager.isDowned(partner.getUniqueId())) {
                DownedState state = downedManager.getDownedState(partner.getUniqueId());
                int remainingSeconds = state != null ? state.getRemainingSeconds() : 0;

                MessageUtil.sendActionBar(viewer, "radar.actionbar-downed",
                        MessageUtil.unparsed("partner", partner.getName()),
                        MessageUtil.p("seconds", String.valueOf(remainingSeconds)),
                        MessageUtil.p("arrow", arrow));
            } else if (partner.isDead()) {
                MessageUtil.sendActionBar(viewer, "radar.actionbar-dead",
                        MessageUtil.unparsed("partner", partner.getName()),
                        MessageUtil.p("arrow", arrow));
            } else {
                double healthInHearts = Math.round(partner.getHealth() * 10.0) / 20.0;
                String healthStr = String.format(Locale.ROOT, "%.1f", healthInHearts).replaceAll("\\.0$", "");

                MessageUtil.sendActionBar(viewer, "radar.actionbar-format",
                        MessageUtil.unparsed("partner", partner.getName()),
                        MessageUtil.p("distance", String.valueOf(dist)),
                        MessageUtil.p("arrow", arrow),
                        MessageUtil.p("health", healthStr));
            }
        }
    }

    private Player findClosestPartner(Player viewer, double maxDistSq) {
        Location viewerLoc = viewer.getLocation();
        Player closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (Player other : viewer.getWorld().getPlayers()) {
            if (other.equals(viewer) || !other.isOnline()) {
                continue;
            }
            if (other.getGameMode() == GameMode.SPECTATOR && viewer.getGameMode() != GameMode.SPECTATOR) {
                continue;
            }

            double distSq = viewerLoc.distanceSquared(other.getLocation());
            if (distSq <= maxDistSq && distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = other;
            }
        }

        return closest;
    }

    private boolean isActionbarBusy(Player viewer, DownedManager downedManager) {
        UUID uuid = viewer.getUniqueId();
        if (downedManager != null) {
            if (downedManager.isDowned(uuid) || downedManager.isRevivingSomeone(uuid)) {
                return true;
            }
        }
        if (plugin.getWaypointManager() != null && plugin.getWaypointManager().isWarmingUp(uuid)) {
            return true;
        }
        if (plugin.getPersonalWaypointManager() != null && plugin.getPersonalWaypointManager().isWarmingUp(uuid)) {
            return true;
        }
        if (plugin.getTetherManager() != null && plugin.getTetherManager().isWarmingUp(uuid)) {
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Direction math
    // -------------------------------------------------------------------------

    /**
     * Calculates the 8-direction arrow relative to the viewer's orientation.
     */
    public static String calculateDirectionArrow(Location viewerLoc, Location targetLoc) {
        if (viewerLoc == null || targetLoc == null || viewerLoc.getWorld() != targetLoc.getWorld()) {
            return "●";
        }

        double dx = targetLoc.getX() - viewerLoc.getX();
        double dz = targetLoc.getZ() - viewerLoc.getZ();

        if (dx * dx + dz * dz < 0.25) {
            return "●";
        }

        // Minecraft yaw: 0 = +Z (South), 90 = -X (West), 180 = -Z (North), 270 = +X (East)
        double targetAngle = Math.toDegrees(Math.atan2(-dx, dz));
        targetAngle = (targetAngle % 360 + 360) % 360;

        double viewerYaw = (viewerLoc.getYaw() % 360 + 360) % 360;

        double diff = targetAngle - viewerYaw;
        while (diff < -180.0) diff += 360.0;
        while (diff >= 180.0) diff -= 360.0;

        if (diff >= -22.5 && diff < 22.5) {
            return "↑";
        } else if (diff >= 22.5 && diff < 67.5) {
            return "⬈";
        } else if (diff >= 67.5 && diff < 112.5) {
            return "→";
        } else if (diff >= 112.5 && diff < 157.5) {
            return "⬊";
        } else if (diff >= 157.5 || diff < -157.5) {
            return "↓";
        } else if (diff >= -157.5 && diff < -112.5) {
            return "⬋";
        } else if (diff >= -112.5 && diff < -67.5) {
            return "←";
        } else {
            return "⬉";
        }
    }

    // -------------------------------------------------------------------------
    // Toggle API
    // -------------------------------------------------------------------------

    /**
     * Checks if the radar should currently be rendered for the player.
     */
    public boolean isRadarActive(Player player) {
        if (player == null || !player.isOnline()) return false;
        if (!plugin.getPluginConfig().isRadarEnabled()) return false;
        if (!player.hasPermission("lifeline.radar")) return false;

        return playerToggles.getOrDefault(player.getUniqueId(), plugin.getPluginConfig().isRadarDefaultEnabled());
    }

    /**
     * Toggles the radar for the given player and persists the new state.
     */
    public boolean toggle(Player player) {
        boolean current = isRadarActive(player);
        boolean newState = !current;
        playerToggles.put(player.getUniqueId(), newState);
        saveToggle(player.getUniqueId(), newState);

        if (newState) {
            MessageUtil.sendPrefixed(player, "radar.enabled");
            if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
            }
        } else {
            MessageUtil.sendPrefixed(player, "radar.disabled");
            if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            }
        }
        return newState;
    }

    /**
     * Explicitly sets the radar state for the given player and persists it.
     */
    public void setEnabled(Player player, boolean state) {
        playerToggles.put(player.getUniqueId(), state);
        saveToggle(player.getUniqueId(), state);

        if (state) {
            MessageUtil.sendPrefixed(player, "radar.enabled");
            if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
            }
        } else {
            MessageUtil.sendPrefixed(player, "radar.disabled");
            if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Events
    // -------------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String key = uuid.toString();
        if (toggleConfig != null && toggleConfig.contains(key)) {
            playerToggles.put(uuid, toggleConfig.getBoolean(key));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Preference is already persisted to disk on change; just evict the in-memory entry.
        playerToggles.remove(event.getPlayer().getUniqueId());
    }

    public void cleanup() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        playerToggles.clear();
    }
}
