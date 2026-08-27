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
 * Manages shared waypoints, persistence, teleport warm-ups, and chat creation prompts.
 */
public class WaypointManager implements Listener {

    private final Lifeline plugin;
    private final File waypointsFile;
    private final Map<String, Waypoint> waypoints = new LinkedHashMap<>();

    // Active teleport warmups: Player UUID -> WarmupTask
    private final Map<UUID, BukkitTask> activeWarmups = new ConcurrentHashMap<>();
    private final Map<UUID, Location> warmupStartLocations = new ConcurrentHashMap<>();

    // Pending chat creation prompts: Player UUID -> PromptData
    private final Map<UUID, PendingPrompt> pendingPrompts = new ConcurrentHashMap<>();

    public record PendingPrompt(Location location, long expiryTimeMillis) {}

    public WaypointManager(Lifeline plugin) {
        this.plugin = plugin;
        this.waypointsFile = new File(plugin.getDataFolder(), "waypoints.yml");
        loadWaypoints();
    }

    public synchronized void loadWaypoints() {
        waypoints.clear();
        if (!waypointsFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(waypointsFile);
        if (config.isConfigurationSection("waypoints")) {
            for (String key : config.getConfigurationSection("waypoints").getKeys(false)) {
                Map<String, Object> values = config.getConfigurationSection("waypoints." + key).getValues(false);
                Waypoint wp = Waypoint.deserialize(values);
                waypoints.put(wp.getName().toLowerCase(Locale.ROOT), wp);
            }
        }
        plugin.getLogger().info("Loaded " + waypoints.size() + " shared waypoints.");
    }

    public synchronized void saveWaypoints() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        YamlConfiguration config = new YamlConfiguration();
        for (Waypoint wp : waypoints.values()) {
            config.set("waypoints." + wp.getName(), wp.serialize());
        }

        try {
            config.save(waypointsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save waypoints.yml", e);
        }
    }

    public synchronized Collection<Waypoint> getAllWaypoints() {
        return Collections.unmodifiableCollection(waypoints.values());
    }

    public synchronized Waypoint getWaypoint(String name) {
        if (name == null) return null;
        return waypoints.get(name.toLowerCase(Locale.ROOT));
    }

    public synchronized boolean addWaypoint(Waypoint waypoint) {
        String key = waypoint.getName().toLowerCase(Locale.ROOT);
        if (waypoints.containsKey(key)) {
            return false;
        }
        waypoints.put(key, waypoint);
        saveWaypoints();
        return true;
    }

    public synchronized boolean deleteWaypoint(String name) {
        if (name == null) return false;
        String key = name.toLowerCase(Locale.ROOT);
        if (waypoints.remove(key) != null) {
            saveWaypoints();
            return true;
        }
        return false;
    }

    /**
     * Initiates a 15-second chat prompt for the player to name a new waypoint at their current location.
     */
    public void startAddWaypointPrompt(Player player) {
        player.closeInventory();
        long expiry = System.currentTimeMillis() + (15 * 1000);
        pendingPrompts.put(player.getUniqueId(), new PendingPrompt(player.getLocation().clone(), expiry));

        MessageUtil.sendPrefixed(player, "<yellow>Please enter a name for the new waypoint in chat within <gold>15 seconds</gold>.");
        MessageUtil.sendPrefixed(player, "<gray>(Type <red>cancel</red> to abort)</gray>");
        if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
        }

        // Schedule timeout expiration check
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingPrompt prompt = pendingPrompts.get(player.getUniqueId());
            if (prompt != null && System.currentTimeMillis() >= prompt.expiryTimeMillis()) {
                pendingPrompts.remove(player.getUniqueId());
                if (player.isOnline()) {
                    MessageUtil.sendPrefixed(player, "<red>Waypoint creation timed out.");
                    if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    }
                }
            }
        }, 20L * 16);
    }

    /**
     * Starts teleportation to a target waypoint. Warmup is configurable (0 = instant).
     */
    public void startTeleportWarmup(Player player, Waypoint waypoint) {
        Location targetLoc = waypoint.toLocation();
        if (targetLoc == null) {
            MessageUtil.sendPrefixed(player, "<red>World '<gold>" + waypoint.getWorldName() + "</gold>' is not currently loaded.");
            if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return;
        }

        cancelWarmup(player, false);
        if (plugin.getTetherManager() != null) {
            plugin.getTetherManager().cancelWarmup(player, false, null);
        }
        player.closeInventory();

        PluginConfig config = plugin.getPluginConfig();
        int warmupSeconds = config.getWaypointWarmupSeconds();

        // 0 seconds = Instant teleportation
        if (warmupSeconds <= 0) {
            player.teleportAsync(targetLoc).thenAccept(success -> {
                if (success) {
                    MessageUtil.sendPrefixed(player, "<green>Teleported to <aqua>" + waypoint.getName() + "</aqua>!");
                    MessageUtil.sendActionBar(player, "<green>✔ Teleport Complete</green>");
                    if (config.isSoundEffectsEnabled()) {
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    }
                    if (config.isParticlesEnabled()) {
                        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1, 0), 25, 0.5, 1.0, 0.5, 0.1);
                    }
                } else {
                    MessageUtil.sendPrefixed(player, "<red>Failed to teleport to destination.");
                }
            });
            return;
        }

        UUID uuid = player.getUniqueId();
        warmupStartLocations.put(uuid, player.getLocation().clone());

        MessageUtil.sendPrefixed(player, "<yellow>Teleporting to <aqua>" + waypoint.getName() + "</aqua> in <gold>" + warmupSeconds + " seconds</gold>. <red>Do not move!</red>");
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

                // Check movement
                Location initial = warmupStartLocations.get(uuid);
                if (initial == null || initial.getWorld() != player.getWorld() || initial.distanceSquared(player.getLocation()) > 0.05) {
                    cancelWarmup(player, true);
                    return;
                }

                elapsed += interval;
                int remainingSeconds = (int) Math.ceil((totalTicks - elapsed) / 20.0);

                if (elapsed % 20 == 0 && remainingSeconds > 0) {
                    MessageUtil.sendActionBar(player, "<gold>Teleporting in <yellow>" + remainingSeconds + "s</yellow>... <gray>(Stay still)</gray>");
                    if (config.isSoundEffectsEnabled()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
                    }
                }

                if (config.isParticlesEnabled()) {
                    player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0.05);
                }

                if (elapsed >= totalTicks) {
                    // Only teleport if warmup hasn't been externally cancelled (e.g. by event listener)
                    if (activeWarmups.containsKey(uuid)) {
                        cancelWarmup(player, false);
                        player.teleportAsync(targetLoc).thenAccept(success -> {
                            if (success) {
                                MessageUtil.sendPrefixed(player, "<green>Teleported to <aqua>" + waypoint.getName() + "</aqua>!");
                                MessageUtil.sendActionBar(player, "<green>✔ Teleport Complete</green>");
                                if (config.isSoundEffectsEnabled()) {
                                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                                }
                                if (config.isParticlesEnabled()) {
                                    player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1, 0), 25, 0.5, 1.0, 0.5, 0.1);
                                }
                            } else {
                                MessageUtil.sendPrefixed(player, "<red>Failed to teleport to destination.");
                            }
                        });
                    }
                }
            }
        }, 0L, interval);

        activeWarmups.put(uuid, task);
    }

    public void cancelWarmup(Player player, boolean notify) {
        UUID uuid = player.getUniqueId();
        BukkitTask task = activeWarmups.remove(uuid);
        warmupStartLocations.remove(uuid);
        if (task != null) {
            task.cancel();
            if (notify && player.isOnline()) {
                MessageUtil.sendPrefixed(player, "<red>Teleportation cancelled because you moved!");
                MessageUtil.sendActionBar(player, "<red>✖ Teleport Cancelled</red>");
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
            MessageUtil.sendPrefixed(player, "<red>Waypoint creation timed out.");
            return;
        }

        if (plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(player.getUniqueId())) {
            MessageUtil.sendPrefixed(player, "<red>You cannot create waypoints while downed.");
            return;
        }

        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (input.equalsIgnoreCase("cancel")) {
            MessageUtil.sendPrefixed(player, "<yellow>Waypoint creation cancelled.");
            if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
            }
            return;
        }

        if (input.length() < 2 || input.length() > 24) {
            MessageUtil.sendPrefixed(player, "<red>Waypoint name must be between 2 and 24 characters.");
            if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return;
        }

        if (waypoints.containsKey(input.toLowerCase(Locale.ROOT))) {
            MessageUtil.sendPrefixed(player, "<red>A waypoint named '<yellow>" + input + "</yellow>' already exists.");
            if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return;
        }

        // Run waypoint creation on primary thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            Waypoint newWaypoint = Waypoint.fromLocation(input, prompt.location(), player.getUniqueId(), player.getName());
            if (addWaypoint(newWaypoint)) {
                MessageUtil.broadcast("<green>Player <yellow>" + player.getName() + "</yellow> created shared waypoint: <aqua><bold>" + input + "</bold></aqua>!");
                if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                }
            } else {
                MessageUtil.sendPrefixed(player, "<red>Failed to save waypoint.");
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
                cancelWarmup(player, true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (activeWarmups.containsKey(player.getUniqueId())) {
                cancelWarmup(player, true);
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
