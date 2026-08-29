package com.lifeline.tether;

import com.lifeline.Lifeline;
import com.lifeline.config.PluginConfig;
import com.lifeline.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player-to-player teleport requests (/tpq, /teleportgui),
 * expiration, acceptance, warmups, anti-movement checks, and safe teleportation.
 */
public class TetherManager implements Listener {

    private final Lifeline plugin;

    // Outgoing requests: senderUuid -> TetherRequest
    private final Map<UUID, TetherRequest> outgoingRequests = new ConcurrentHashMap<>();

    // Incoming requests: targetUuid -> (senderUuid -> TetherRequest)
    private final Map<UUID, Map<UUID, TetherRequest>> incomingRequests = new ConcurrentHashMap<>();

    // Active teleport warmups: senderUuid -> BukkitTask
    private final Map<UUID, BukkitTask> activeWarmups = new ConcurrentHashMap<>();
    private final Map<UUID, Location> warmupStartLocations = new ConcurrentHashMap<>();

    // Player cooldowns: senderUuid -> timestamp in millis when cooldown ends
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public TetherManager(Lifeline plugin) {
        this.plugin = plugin;
    }

    public boolean isWarmingUp(UUID uuid) {
        return uuid != null && activeWarmups.containsKey(uuid);
    }

    /**
     * Sends a teleport request from sender to target.
     */
    public boolean sendRequest(Player sender, Player target) {
        if (sender == null || target == null) {
            return false;
        }

        UUID senderUuid = sender.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        // Edge case: Self-teleport
        if (senderUuid.equals(targetUuid)) {
            MessageUtil.sendPrefixed(sender, "teleport.self-target");
            return false;
        }

        // Edge case: Target is in Spectator mode
        if (target.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            MessageUtil.sendPrefixed(sender, "teleport.target-spectator", MessageUtil.p("player", target.getName()));
            return false;
        }

        // Edge case: Sender is downed
        if (plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(senderUuid)) {
            MessageUtil.sendPrefixed(sender, "teleport.sender-downed");
            return false;
        }

        // Edge case: Target is downed
        if (plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(targetUuid)) {
            MessageUtil.sendPrefixed(sender, "teleport.target-downed", MessageUtil.p("player", target.getName()));
            return false;
        }

        PluginConfig config = plugin.getPluginConfig();

        // Check cooldown
        int cooldownSec = config.getTetherCooldownSeconds();
        if (cooldownSec > 0) {
            Long cooldownEnd = cooldowns.get(senderUuid);
            if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
                long remaining = Math.max(1, (cooldownEnd - System.currentTimeMillis()) / 1000);
                MessageUtil.sendPrefixed(sender, "teleport.cooldown", MessageUtil.p("seconds", String.valueOf(remaining)));
                return false;
            }
        }

        // Edge case: If sender already has an outgoing request, cancel it first
        TetherRequest existing = outgoingRequests.remove(senderUuid);
        if (existing != null) {
            removeRequest(existing);
            MessageUtil.sendPrefixed(sender, "teleport.request-cancelled-previous", MessageUtil.p("player", existing.targetName()));
        }

        int timeoutSec = config.getTetherTimeoutSeconds();
        long now = System.currentTimeMillis();
        long expiry = now + (timeoutSec * 1000L);

        TetherRequest request = new TetherRequest(senderUuid, sender.getName(), targetUuid, target.getName(), now, expiry);
        outgoingRequests.put(senderUuid, request);
        incomingRequests.computeIfAbsent(targetUuid, k -> new ConcurrentHashMap<>()).put(senderUuid, request);

        // Notify sender
        MessageUtil.sendPrefixed(sender, "teleport.request-sent-sender",
                MessageUtil.unparsed("player", target.getName()),
                MessageUtil.p("seconds", String.valueOf(timeoutSec)));
        if (config.isSoundEffectsEnabled()) {
            sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
        }

        // Notify target with interactive clickable components
        MessageUtil.sendPrefixed(target, "teleport.request-received-target", MessageUtil.unparsed("player", sender.getName()));

        // Pre-substitute player name into raw button template so <click:run_command:'/tpq accept <player>'> has the actual name
        String rawButtons = MessageUtil.getRaw("teleport.request-received-buttons",
                "<green><bold><click:run_command:'/tpq accept <player>'><hover:show_text:'<green>Click to accept teleport request from <player></green>'>[✔ ACCEPT]</click></hover></bold></green>   <red><bold><click:run_command:'/tpq deny <player>'><hover:show_text:'<red>Click to decline teleport request from <player></red>'>[✖ DECLINE]</click></hover></bold></red>")
                .replace("<player>", sender.getName());
        MessageUtil.sendPrefixed(target, rawButtons);

        if (config.isSoundEffectsEnabled()) {
            target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.4f);
        }

        // Schedule auto-expiry
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TetherRequest current = outgoingRequests.get(senderUuid);
            if (current != null && current.createdAtMillis() == now) {
                expireRequest(current);
            }
        }, timeoutSec * 20L);

        return true;
    }

    /**
     * Accepts an incoming teleport request.
     */
    public boolean acceptRequest(Player target, String senderNameOrNull) {
        if (target == null) return false;

        UUID targetUuid = target.getUniqueId();

        if (plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(targetUuid)) {
            MessageUtil.sendPrefixed(target, "teleport.downed-blocked");
            return false;
        }

        Map<UUID, TetherRequest> requests = incomingRequests.get(targetUuid);
        if (requests == null || requests.isEmpty()) {
            MessageUtil.sendPrefixed(target, "teleport.no-pending-requests");
            return false;
        }

        // Prune expired requests first
        requests.values().removeIf(TetherRequest::isExpired);
        if (requests.isEmpty()) {
            incomingRequests.remove(targetUuid);
            MessageUtil.sendPrefixed(target, "teleport.no-pending-requests-expired");
            return false;
        }

        // Normalize senderName: if empty, blank, or placeholder "<player>", treat as null to accept newest
        if (senderNameOrNull != null && (senderNameOrNull.isBlank() || senderNameOrNull.equalsIgnoreCase("<player>"))) {
            senderNameOrNull = null;
        }

        TetherRequest matchingRequest = null;
        if (senderNameOrNull != null) {
            for (TetherRequest req : requests.values()) {
                if (req.senderName().equalsIgnoreCase(senderNameOrNull)) {
                    matchingRequest = req;
                    break;
                }
            }
            if (matchingRequest == null) {
                MessageUtil.sendPrefixed(target, "teleport.no-request-from-player", MessageUtil.unparsed("player", senderNameOrNull));
                return false;
            }
        } else {
            // Pick the only request or most recent
            if (requests.size() == 1) {
                matchingRequest = requests.values().iterator().next();
            } else {
                // If multiple requests, pick the newest
                matchingRequest = requests.values().stream()
                        .max(Comparator.comparingLong(TetherRequest::createdAtMillis))
                        .orElse(null);
            }
        }

        if (matchingRequest == null || matchingRequest.isExpired()) {
            MessageUtil.sendPrefixed(target, "teleport.request-expired-target");
            return false;
        }

        // Remove from tracking
        removeRequest(matchingRequest);

        Player sender = Bukkit.getPlayer(matchingRequest.senderUuid());
        if (sender == null || !sender.isOnline()) {
            MessageUtil.sendPrefixed(target, "teleport.player-offline", MessageUtil.unparsed("player", matchingRequest.senderName()));
            return false;
        }

        if (plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(sender.getUniqueId())) {
            MessageUtil.sendPrefixed(target, "teleport.target-downed-accept", MessageUtil.unparsed("player", sender.getName()));
            return false;
        }

        MessageUtil.sendPrefixed(target, "teleport.accept-target", MessageUtil.unparsed("player", sender.getName()));
        if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
            target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        }

        // Start warmup for sender
        startTeleportWarmup(sender, target);
        return true;
    }

    /**
     * Denies an incoming teleport request.
     */
    public boolean denyRequest(Player target, String senderNameOrNull) {
        if (target == null) return false;

        UUID targetUuid = target.getUniqueId();
        Map<UUID, TetherRequest> requests = incomingRequests.get(targetUuid);
        if (requests == null || requests.isEmpty()) {
            MessageUtil.sendPrefixed(target, "teleport.no-pending-requests");
            return false;
        }

        // Prune expired requests
        requests.values().removeIf(TetherRequest::isExpired);
        if (requests.isEmpty()) {
            incomingRequests.remove(targetUuid);
            MessageUtil.sendPrefixed(target, "teleport.no-pending-requests");
            return false;
        }

        // Normalize senderName: if empty, blank, or placeholder "<player>", treat as null to deny newest
        if (senderNameOrNull != null && (senderNameOrNull.isBlank() || senderNameOrNull.equalsIgnoreCase("<player>"))) {
            senderNameOrNull = null;
        }

        TetherRequest matchingRequest = null;
        if (senderNameOrNull != null) {
            for (TetherRequest req : requests.values()) {
                if (req.senderName().equalsIgnoreCase(senderNameOrNull)) {
                    matchingRequest = req;
                    break;
                }
            }
            if (matchingRequest == null) {
                MessageUtil.sendPrefixed(target, "teleport.no-request-from-player", MessageUtil.unparsed("player", senderNameOrNull));
                return false;
            }
        } else {
            if (requests.size() == 1) {
                matchingRequest = requests.values().iterator().next();
            } else {
                matchingRequest = requests.values().stream()
                        .max(Comparator.comparingLong(TetherRequest::createdAtMillis))
                        .orElse(null);
            }
        }

        if (matchingRequest == null) {
            MessageUtil.sendPrefixed(target, "teleport.no-pending-requests");
            return false;
        }

        removeRequest(matchingRequest);

        MessageUtil.sendPrefixed(target, "teleport.deny-target", MessageUtil.unparsed("player", matchingRequest.senderName()));

        Player sender = Bukkit.getPlayer(matchingRequest.senderUuid());
        if (sender != null && sender.isOnline()) {
            MessageUtil.sendPrefixed(sender, "teleport.deny-sender", MessageUtil.unparsed("player", target.getName()));
            if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            }
        }

        return true;
    }

    /**
     * Cancels any pending outgoing teleport request for the sender.
     */
    public boolean cancelOutgoingRequest(Player sender) {
        if (sender == null) return false;

        TetherRequest req = outgoingRequests.remove(sender.getUniqueId());
        if (req == null) {
            MessageUtil.sendPrefixed(sender, "teleport.no-pending-requests");
            return false;
        }

        // Use removeRequest logic to also prune empty target maps
        Map<UUID, TetherRequest> targetMap = incomingRequests.get(req.targetUuid());
        if (targetMap != null) {
            targetMap.remove(sender.getUniqueId());
            if (targetMap.isEmpty()) {
                incomingRequests.remove(req.targetUuid());
            }
        }

        MessageUtil.sendPrefixed(sender, "teleport.cancel-outgoing", MessageUtil.unparsed("player", req.targetName()));
        return true;
    }

    private void expireRequest(TetherRequest request) {
        if (request == null) return;
        TetherRequest current = outgoingRequests.get(request.senderUuid());
        if (current != null && current.createdAtMillis() == request.createdAtMillis()) {
            removeRequest(request);

            Player sender = Bukkit.getPlayer(request.senderUuid());
            if (sender != null && sender.isOnline()) {
                MessageUtil.sendPrefixed(sender, "teleport.request-expired-sender", MessageUtil.unparsed("player", request.targetName()));
            }
        }
    }

    private void removeRequest(TetherRequest request) {
        if (request == null) return;
        outgoingRequests.remove(request.senderUuid());
        Map<UUID, TetherRequest> targetMap = incomingRequests.get(request.targetUuid());
        if (targetMap != null) {
            targetMap.remove(request.senderUuid());
            if (targetMap.isEmpty()) {
                incomingRequests.remove(request.targetUuid());
            }
        }
    }

    /**
     * Initiates warmup and safe teleportation of sender to target.
     */
    public void startTeleportWarmup(Player sender, Player target) {
        UUID senderUuid = sender.getUniqueId();
        cancelWarmup(sender, false, null);

        // Cancel any active waypoint warmup
        if (plugin.getWaypointManager() != null) {
            plugin.getWaypointManager().cancelWarmup(sender, false);
        }
        if (plugin.getPersonalWaypointManager() != null) {
            plugin.getPersonalWaypointManager().cancelWarmup(sender, false);
        }

        PluginConfig config = plugin.getPluginConfig();
        int warmupSeconds = config.getTetherWarmupSeconds();

        // Instant teleport if warmup <= 0
        if (warmupSeconds <= 0) {
            executeTeleport(sender, target);
            return;
        }

        warmupStartLocations.put(senderUuid, sender.getLocation().clone());

        MessageUtil.sendPrefixed(sender, "teleport.warmup-sender",
                MessageUtil.p("player", target.getName()),
                MessageUtil.p("seconds", String.valueOf(warmupSeconds)));
        MessageUtil.sendPrefixed(target, "teleport.warmup-target",
                MessageUtil.p("player", sender.getName()),
                MessageUtil.p("seconds", String.valueOf(warmupSeconds)));

        if (config.isSoundEffectsEnabled()) {
            sender.playSound(sender.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 1.8f);
        }

        final int totalTicks = warmupSeconds * 20;
        final int interval = 5;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!sender.isOnline() || sender.isDead()) {
                    cancelWarmup(sender, false, null);
                    return;
                }

                if (!target.isOnline() || target.isDead()) {
                    cancelWarmup(sender, true, "teleport.teleport-cancelled-target-unavailable", MessageUtil.p("player", target.getName()));
                    return;
                }

                // Check downed state
                if (plugin.getDownedManager() != null) {
                    if (plugin.getDownedManager().isDowned(senderUuid)) {
                        cancelWarmup(sender, false, null);
                        return;
                    }
                    if (plugin.getDownedManager().isDowned(target.getUniqueId())) {
                        cancelWarmup(sender, true, "teleport.target-downed-warmup", MessageUtil.p("player", target.getName()));
                        return;
                    }
                }

                // Movement check
                Location initial = warmupStartLocations.get(senderUuid);
                if (initial == null || initial.getWorld() != sender.getWorld() || initial.distanceSquared(sender.getLocation()) > 0.05) {
                    cancelWarmup(sender, true, "teleport.teleport-cancelled-moved");
                    return;
                }

                elapsed += interval;
                int remainingSeconds = (int) Math.ceil((totalTicks - elapsed) / 20.0);

                if (elapsed % 20 == 0 && remainingSeconds > 0) {
                    MessageUtil.sendActionBar(sender, "teleport.warmup-actionbar",
                            MessageUtil.p("player", target.getName()),
                            MessageUtil.p("seconds", String.valueOf(remainingSeconds)));
                    if (config.isSoundEffectsEnabled()) {
                        sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
                    }
                }

                if (config.isParticlesEnabled()) {
                    sender.getWorld().spawnParticle(Particle.PORTAL, sender.getLocation().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0.05);
                }

                if (elapsed >= totalTicks) {
                    // Only execute if warmup hasn't been cancelled externally (e.g. by event listener)
                    if (activeWarmups.containsKey(senderUuid)) {
                        cancelWarmup(sender, false, null);
                        executeTeleport(sender, target);
                    }
                }
            }
        }, 0L, interval);

        activeWarmups.put(senderUuid, task);
    }

    private void executeTeleport(Player sender, Player target) {
        if (!sender.isOnline() || !target.isOnline() || sender.isDead() || target.isDead()) {
            return;
        }

        Location dest = target.getLocation();
        if (dest.getWorld() == null) {
            MessageUtil.sendPrefixed(sender, "teleport.teleport-cancelled-invalid-world");
            return;
        }

        // Void safety check
        if (dest.getY() < dest.getWorld().getMinHeight()) {
            MessageUtil.sendPrefixed(sender, "teleport.teleport-cancelled-void");
            return;
        }

        // Leave vehicle before teleporting
        if (sender.isInsideVehicle()) {
            sender.leaveVehicle();
        }

        PluginConfig config = plugin.getPluginConfig();

        sender.teleportAsync(dest).thenAccept(success -> {
            // teleportAsync completes on a background thread - schedule all Bukkit API work onto the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!sender.isOnline()) {
                    return;
                }
                if (success) {
                    MessageUtil.sendPrefixed(sender, "teleport.teleport-success-sender", MessageUtil.p("player", target.getName()));
                    MessageUtil.sendActionBar(sender, "teleport.teleport-success-actionbar");
                    MessageUtil.sendPrefixed(target, "teleport.teleport-success-target", MessageUtil.p("player", sender.getName()));

                    if (config.isSoundEffectsEnabled()) {
                        sender.playSound(sender.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    }

                    if (config.isParticlesEnabled()) {
                        sender.getWorld().spawnParticle(Particle.REVERSE_PORTAL, sender.getLocation().add(0, 1, 0), 25, 0.5, 1.0, 0.5, 0.1);
                        target.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().add(0, 1, 0), 25, 0.5, 1.0, 0.5, 0.1);
                    }

                    // Set cooldown
                    int cooldownSec = config.getTetherCooldownSeconds();
                    if (cooldownSec > 0) {
                        cooldowns.put(sender.getUniqueId(), System.currentTimeMillis() + (cooldownSec * 1000L));
                    }
                } else {
                    MessageUtil.sendPrefixed(sender, "teleport.teleport-failed");
                }
            });
        });
    }

    public void cancelWarmup(Player sender, boolean notify, String reasonKey, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        if (sender == null) return;
        UUID uuid = sender.getUniqueId();
        BukkitTask task = activeWarmups.remove(uuid);
        warmupStartLocations.remove(uuid);
        if (task != null) {
            task.cancel();
            if (notify && sender.isOnline()) {
                // Send a prefixed chat message with the reason key.
                // Only fall back to the actionbar key if no specific reason is provided.
                if (reasonKey != null) {
                    MessageUtil.sendPrefixed(sender, reasonKey, resolvers);
                } else {
                    MessageUtil.sendActionBar(sender, "teleport.teleport-cancelled-actionbar");
                }
                if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                    sender.playSound(sender.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
        }
    }

    /**
     * Returns a list of sender names with pending requests for the given target.
     */
    public List<String> getPendingSenderNames(Player target) {
        if (target == null) return List.of();
        Map<UUID, TetherRequest> map = incomingRequests.get(target.getUniqueId());
        if (map == null || map.isEmpty()) return List.of();
        return map.values().stream()
                .filter(r -> !r.isExpired())
                .map(TetherRequest::senderName)
                .toList();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (activeWarmups.containsKey(player.getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null && (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ())) {
                cancelWarmup(player, true, "teleport.teleport-cancelled-moved");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (activeWarmups.containsKey(player.getUniqueId())) {
                cancelWarmup(player, true, "teleport.teleport-cancelled-damage");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        cancelWarmup(player, false, null);

        // Clean outgoing
        TetherRequest outgoing = outgoingRequests.remove(uuid);
        if (outgoing != null) {
            Map<UUID, TetherRequest> targetMap = incomingRequests.get(outgoing.targetUuid());
            if (targetMap != null) {
                targetMap.remove(uuid);
            }
        }

        // Clean incoming
        Map<UUID, TetherRequest> incoming = incomingRequests.remove(uuid);
        if (incoming != null) {
            for (TetherRequest req : incoming.values()) {
                outgoingRequests.remove(req.senderUuid());
                Player reqSender = Bukkit.getPlayer(req.senderUuid());
                if (reqSender != null && reqSender.isOnline()) {
                    MessageUtil.sendPrefixed(reqSender, "teleport.cancel-target-left", MessageUtil.p("player", player.getName()));
                }
            }
        }
    }

    public void cleanup() {
        for (BukkitTask task : activeWarmups.values()) {
            task.cancel();
        }
        activeWarmups.clear();
        warmupStartLocations.clear();
        outgoingRequests.clear();
        incomingRequests.clear();
        cooldowns.clear();
    }
}
