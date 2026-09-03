package com.lifeline.waypoint;

import com.lifeline.Lifeline;
import com.lifeline.config.PluginConfig;
import com.lifeline.util.MessageUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages per-player personal waypoints (maximum 27 per player), persistence,
 * teleport warm-ups, and chat creation prompts.
 */
public class PersonalWaypointManager implements Listener {

    public static final int MAX_PERSONAL_WAYPOINTS = 27;

    private final Lifeline plugin;
    private final File waypointsFile;
    private final Map<UUID, Map<String, Waypoint>> playerWaypoints = new ConcurrentHashMap<>();

    // Active teleport warmups: Player UUID -> WarmupTask
    private final Map<UUID, BukkitTask> activeWarmups = new ConcurrentHashMap<>();
    private final Map<UUID, Location> warmupStartLocations = new ConcurrentHashMap<>();

    // Pending chat creation prompts: Player UUID -> PromptData
    private final Map<UUID, PendingPrompt> pendingPrompts = new ConcurrentHashMap<>();

    public record PendingPrompt(Location location, long expiryTimeMillis) {}

    public PersonalWaypointManager(Lifeline plugin) {
        this.plugin = plugin;
        this.waypointsFile = new File(plugin.getDataFolder(), "personal-waypoints.yml");
        loadWaypoints();
    }

    public synchronized void loadWaypoints() {
        playerWaypoints.clear();
        File backupDir = new File(plugin.getDataFolder(), "backup");
        File backupFile = new File(backupDir, "personal-waypoints.yml.bak");
        if (!backupFile.exists()) {
            File legacy = new File(backupDir, "persnal-waypoint.yml.bak");
            if (legacy.exists()) {
                backupFile = legacy;
            }
        }

        if (!waypointsFile.exists() && !backupFile.exists()) {
            return;
        }

        YamlConfiguration config = com.lifeline.util.SafeFileUtil.loadWithAutoRecovery(waypointsFile, backupFile, plugin.getLogger());
        if (config.isConfigurationSection("players")) {
            ConfigurationSection playersSection = config.getConfigurationSection("players");
            if (playersSection != null) {
                for (String uuidStr : playersSection.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        ConfigurationSection wpSection = playersSection.getConfigurationSection(uuidStr + ".waypoints");
                        if (wpSection != null) {
                            Map<String, Waypoint> map = new LinkedHashMap<>();
                            for (String wpKey : wpSection.getKeys(false)) {
                                ConfigurationSection entrySection = wpSection.getConfigurationSection(wpKey);
                                if (entrySection == null) {
                                    plugin.getLogger().warning("Skipping corrupted personal waypoint entry '" + wpKey + "' for player " + uuidStr);
                                    continue;
                                }
                                try {
                                    Map<String, Object> values = entrySection.getValues(false);
                                    Waypoint wp = Waypoint.deserialize(values);
                                    map.put(wp.getName().toLowerCase(Locale.ROOT), wp);
                                } catch (Exception ex) {
                                    plugin.getLogger().warning("Failed to deserialize personal waypoint '" + wpKey + "' for player " + uuidStr + ": " + ex.getMessage());
                                }
                            }
                            playerWaypoints.put(uuid, map);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Skip malformed UUID key
                    }
                }
            }
        }
        plugin.getLogger().info("Loaded Personal Waypoints for " + playerWaypoints.size() + " player(s).");
    }

    public synchronized void saveWaypoints() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Maintain latest pre-save .bak copy in backup/
        File backupDir = new File(plugin.getDataFolder(), "backup");
        File backupFile = new File(backupDir, "personal-waypoints.yml.bak");
        com.lifeline.util.SafeFileUtil.copyBackupAtomically(waypointsFile, backupFile);
        com.lifeline.util.SafeFileUtil.copyBackupAtomically(waypointsFile, new File(backupDir, "persnal-waypoint.yml.bak"));

        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Waypoint>> entry : playerWaypoints.entrySet()) {
            String uuidStr = entry.getKey().toString();
            for (Map.Entry<String, Waypoint> wpEntry : entry.getValue().entrySet()) {
                // Use the lowercase map key as the YAML key so on-disk keys stay
                // consistent with the in-memory lookup keys (which are always lowercase).
                config.set("players." + uuidStr + ".waypoints." + wpEntry.getKey(), wpEntry.getValue().serialize());
            }
        }

        try {
            com.lifeline.util.SafeFileUtil.saveConfigurationAtomically(config, waypointsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save personal-waypoints.yml", e);
        }
    }

    public synchronized Collection<Waypoint> getWaypoints(UUID uuid) {
        if (uuid == null) return Collections.emptyList();
        Map<String, Waypoint> map = playerWaypoints.get(uuid);
        if (map == null) return Collections.emptyList();
        return Collections.unmodifiableCollection(map.values());
    }

    public synchronized Waypoint getWaypoint(UUID uuid, String name) {
        if (uuid == null || name == null) return null;
        Map<String, Waypoint> map = playerWaypoints.get(uuid);
        if (map == null) return null;
        return map.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean isWarmingUp(UUID uuid) {
        return uuid != null && activeWarmups.containsKey(uuid);
    }

    public synchronized boolean addWaypoint(UUID uuid, Waypoint waypoint) {
        if (uuid == null || waypoint == null) return false;
        Map<String, Waypoint> map = playerWaypoints.computeIfAbsent(uuid, k -> new LinkedHashMap<>());
        if (map.size() >= MAX_PERSONAL_WAYPOINTS) {
            return false;
        }
        String key = waypoint.getName().toLowerCase(Locale.ROOT);
        if (map.containsKey(key)) {
            return false;
        }
        map.put(key, waypoint);
        saveWaypoints();
        return true;
    }

    public synchronized boolean deleteWaypoint(UUID uuid, String name) {
        if (uuid == null || name == null) return false;
        Map<String, Waypoint> map = playerWaypoints.get(uuid);
        if (map == null) return false;
        String key = name.toLowerCase(Locale.ROOT);
        if (map.remove(key) != null) {
            saveWaypoints();
            return true;
        }
        return false;
    }

    /**
     * Initiates a 15-second chat prompt for the player to name a new personal waypoint.
     */
    public void cancelPrompt(UUID uuid) {
        if (uuid != null) {
            pendingPrompts.remove(uuid);
        }
    }

    public void startAddWaypointPrompt(Player player) {
        player.closeInventory();
        if (plugin.getWaypointManager() != null) {
            plugin.getWaypointManager().cancelPrompt(player.getUniqueId());
        }
        long expiry = System.currentTimeMillis() + (15 * 1000);
        pendingPrompts.put(player.getUniqueId(), new PendingPrompt(player.getLocation().clone(), expiry));

        MessageUtil.sendPrefixed(player, "personal-waypoints.prompt-start", MessageUtil.p("seconds", "15"));
        MessageUtil.sendPrefixed(player, "personal-waypoints.prompt-cancel-hint");
        if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingPrompt prompt = pendingPrompts.get(player.getUniqueId());
            if (prompt != null && System.currentTimeMillis() >= prompt.expiryTimeMillis()) {
                pendingPrompts.remove(player.getUniqueId());
                if (player.isOnline()) {
                    MessageUtil.sendPrefixed(player, "personal-waypoints.prompt-timeout");
                    if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    }
                }
            }
        }, 20L * 16);
    }

    /**
     * Starts teleportation to a target personal waypoint.
     */
    public void startTeleportWarmup(Player player, Waypoint waypoint) {
        Location targetLoc = waypoint.toLocation();
        if (targetLoc == null || targetLoc.getWorld() == null) {
            MessageUtil.sendPrefixed(player, "personal-waypoints.world-not-loaded", MessageUtil.unparsed("world", waypoint.getWorldName()));
            if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return;
        }

        if (targetLoc.getY() < targetLoc.getWorld().getMinHeight()) {
            MessageUtil.sendPrefixed(player, "teleport.teleport-cancelled-void");
            return;
        }

        cancelWarmup(player, false);
        if (plugin.getWaypointManager() != null) {
            plugin.getWaypointManager().cancelWarmup(player, false);
        }
        if (plugin.getTetherManager() != null) {
            plugin.getTetherManager().cancelWarmup(player, false, null);
        }
        player.closeInventory();

        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }

        PluginConfig config = plugin.getPluginConfig();
        int warmupSeconds = config.getWaypointWarmupSeconds();

        if (warmupSeconds <= 0) {
            player.teleportAsync(targetLoc).thenAccept(success -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        MessageUtil.sendPrefixed(player, "personal-waypoints.teleport-success", MessageUtil.unparsed("name", waypoint.getName()));
                        MessageUtil.sendActionBar(player, "personal-waypoints.teleport-actionbar");
                        if (config.isSoundEffectsEnabled()) {
                            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        }
                        if (config.isParticlesEnabled()) {
                            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1, 0), 25, 0.5, 1.0, 0.5, 0.1);
                        }
                    } else {
                        MessageUtil.sendPrefixed(player, "personal-waypoints.teleport-failed");
                    }
                });
            });
            return;
        }

        UUID uuid = player.getUniqueId();
        warmupStartLocations.put(uuid, player.getLocation().clone());

        MessageUtil.sendPrefixed(player, "personal-waypoints.warmup-start",
                MessageUtil.unparsed("name", waypoint.getName()),
                MessageUtil.p("seconds", String.valueOf(warmupSeconds)));
        if (config.isSoundEffectsEnabled()) {
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 1.8f);
        }

        final int totalTicks = warmupSeconds * 20;
        final int interval = 5;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancelWarmup(player, false);
                    return;
                }

                Location initial = warmupStartLocations.get(uuid);
                if (initial == null) {
                    // Location already removed — warmup was cancelled externally; stop silently.
                    cancelWarmup(player, false);
                    return;
                }
                if (initial.getWorld() != player.getWorld() || initial.distanceSquared(player.getLocation()) > 0.05) {
                    cancelWarmup(player, true);
                    return;
                }

                elapsed += interval;
                int remainingSeconds = (int) Math.ceil((totalTicks - elapsed) / 20.0);

                if (elapsed % 20 == 0 && remainingSeconds > 0) {
                    MessageUtil.sendActionBar(player, "personal-waypoints.warmup-actionbar", MessageUtil.p("seconds", String.valueOf(remainingSeconds)));
                    if (config.isSoundEffectsEnabled()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
                    }
                }

                if (config.isParticlesEnabled()) {
                    player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0.05);
                }

                if (elapsed >= totalTicks) {
                    if (activeWarmups.containsKey(uuid)) {
                        cancelWarmup(player, false);
                        player.teleportAsync(targetLoc).thenAccept(success -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (!player.isOnline()) {
                                    return;
                                }
                                if (success) {
                                    MessageUtil.sendPrefixed(player, "personal-waypoints.teleport-success", MessageUtil.unparsed("name", waypoint.getName()));
                                    MessageUtil.sendActionBar(player, "personal-waypoints.teleport-actionbar");
                                    if (config.isSoundEffectsEnabled()) {
                                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                                    }
                                    if (config.isParticlesEnabled()) {
                                        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1, 0), 25, 0.5, 1.0, 0.5, 0.1);
                                    }
                                } else {
                                    MessageUtil.sendPrefixed(player, "personal-waypoints.teleport-failed");
                                }
                            });
                        });
                    }
                }
            }
        }, 0L, interval);

        activeWarmups.put(uuid, task);
    }

    public void cancelWarmup(Player player, boolean notify) {
        cancelWarmup(player, notify, "personal-waypoints.warmup-cancelled-moved");
    }

    public void cancelWarmup(Player player, boolean notify, String reasonKey) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        BukkitTask task = activeWarmups.remove(uuid);
        warmupStartLocations.remove(uuid);
        if (task != null) {
            task.cancel();
            if (notify && player.isOnline()) {
                String key = reasonKey != null ? reasonKey : "personal-waypoints.warmup-cancelled-moved";
                MessageUtil.sendPrefixed(player, key);
                MessageUtil.sendActionBar(player, "personal-waypoints.warmup-cancelled-actionbar");
                if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingPrompt prompt = pendingPrompts.get(player.getUniqueId());
        if (prompt == null) {
            return;
        }

        event.setCancelled(true);
        pendingPrompts.remove(player.getUniqueId());

        if (System.currentTimeMillis() > prompt.expiryTimeMillis()) {
            MessageUtil.sendPrefixed(player, "personal-waypoints.prompt-timeout");
            return;
        }

        if (plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(player.getUniqueId())) {
            MessageUtil.sendPrefixed(player, "personal-waypoints.prompt-downed");
            return;
        }

        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (input.equalsIgnoreCase("cancel")) {
            MessageUtil.sendPrefixed(player, "personal-waypoints.prompt-cancelled");
            if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                });
            }
            return;
        }

        if (input.length() < 2 || input.length() > 24) {
            MessageUtil.sendPrefixed(player, "personal-waypoints.name-length-error");
            if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                });
            }
            return;
        }

        if (input.contains(".")) {
            MessageUtil.sendPrefixed(player, "personal-waypoints.name-invalid");
            if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                });
            }
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (getWaypoint(player.getUniqueId(), input) != null) {
                MessageUtil.sendPrefixed(player, "personal-waypoints.already-exists", MessageUtil.p("name", input));
                if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                return;
            }

            if (getWaypoints(player.getUniqueId()).size() >= MAX_PERSONAL_WAYPOINTS) {
                MessageUtil.sendPrefixed(player, "personal-waypoints.capacity-reached",
                        MessageUtil.p("max", String.valueOf(MAX_PERSONAL_WAYPOINTS)));
                if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                return;
            }

            Waypoint newWaypoint = Waypoint.fromLocation(input, prompt.location(), player.getUniqueId(), player.getName());
            if (addWaypoint(player.getUniqueId(), newWaypoint)) {
                MessageUtil.sendPrefixed(player, "personal-waypoints.created-msg",
                        MessageUtil.unparsed("name", input));
                if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                }
            } else {
                MessageUtil.sendPrefixed(player, "personal-waypoints.save-error");
            }
        });
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (activeWarmups.containsKey(player.getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null && (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ())) {
                cancelWarmup(player, true, "personal-waypoints.warmup-cancelled-moved");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (activeWarmups.containsKey(player.getUniqueId())) {
                cancelWarmup(player, true, "personal-waypoints.warmup-cancelled-damage");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cancelWarmup(player, false);
        pendingPrompts.remove(player.getUniqueId());
    }

    public void cleanup() {
        for (BukkitTask task : activeWarmups.values()) {
            task.cancel();
        }
        activeWarmups.clear();
        warmupStartLocations.clear();
        pendingPrompts.clear();
        saveWaypoints();
    }
}
