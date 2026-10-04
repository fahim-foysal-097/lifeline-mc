package com.lifeline.synergy;

import com.lifeline.Lifeline;
import com.lifeline.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages proximity synergy buffs between co-op partners.
 * When teammates stay within close proximity (default: 20 blocks), grants a passive
 * movement speed boost via safe transient attribute modifiers, along with optional potion buffs.
 */
public class SynergyManager implements Listener {

    private final Lifeline plugin;
    private final NamespacedKey speedKey;
    private final Set<UUID> activeSynergyPlayers = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    public SynergyManager(Lifeline plugin) {
        this.plugin = plugin;
        this.speedKey = new NamespacedKey(plugin, "synergy_speed_boost");
        startTask();
    }

    /**
     * Starts or restarts the periodic proximity check task.
     */
    public synchronized void startTask() {
        if (task != null) {
            task.cancel();
        }

        PluginConfig config = plugin.getPluginConfig();
        if (!config.isSynergyEnabled()) {
            return;
        }

        long interval = Math.max(1, config.getSynergyUpdateIntervalTicks());
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSynergy, interval, interval);
    }

    /**
     * Periodic check evaluating teammate distances and applying or revoking synergy buffs.
     */
    public void tickSynergy() {
        PluginConfig config = plugin.getPluginConfig();
        if (!config.isSynergyEnabled()) {
            cleanupActiveBuffs();
            return;
        }

        double maxDist = config.getSynergyRangeBlocks();
        double maxDistSq = maxDist * maxDist;

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            UUID uuid = viewer.getUniqueId();

            // Downed or spectator players cannot receive or grant synergy
            if (!isEligible(viewer)) {
                if (activeSynergyPlayers.contains(uuid)) {
                    removeSynergy(viewer);
                }
                continue;
            }

            Player partner = findClosestTeammate(viewer, maxDistSq);
            if (partner != null) {
                applySynergy(viewer, config);
            } else {
                if (activeSynergyPlayers.contains(uuid)) {
                    removeSynergy(viewer);
                }
            }
        }
    }

    private boolean isEligible(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(player.getUniqueId())) {
            return false;
        }
        return true;
    }

    private Player findClosestTeammate(Player viewer, double maxDistSq) {
        Location viewerLoc = viewer.getLocation();
        Player closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (Player other : viewer.getWorld().getPlayers()) {
            if (other.equals(viewer) || !isEligible(other)) {
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

    private void applySynergy(Player player, PluginConfig config) {
        UUID uuid = player.getUniqueId();
        boolean newlyActivated = activeSynergyPlayers.add(uuid);

        if (newlyActivated && config.isSynergySoundEffectsEnabled()) {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.6f);
        }

        // Apply transient attribute modifier for movement speed
        AttributeInstance speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            double boostScalar = config.getSynergySpeedBoostPercentage() / 100.0;
            AttributeModifier existing = speedAttr.getModifier(speedKey);
            if (existing != null && Math.abs(existing.getAmount() - boostScalar) > 0.0001) {
                speedAttr.removeModifier(speedKey);
                existing = null;
            }
            if (existing == null && boostScalar > 0.0) {
                AttributeModifier modifier = new AttributeModifier(speedKey, boostScalar, AttributeModifier.Operation.ADD_SCALAR);
                speedAttr.addTransientModifier(modifier);
            }
        }

        // Apply optional potion buffs (ambient, hidden particles, icon visible)
        // Duration is 40 ticks so it remains seamless across 20-tick check intervals
        if (config.isSynergyRegenEnabled()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, config.getSynergyRegenAmplifier(), true, false, true));
        }
        if (config.isSynergyResistanceEnabled()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, config.getSynergyResistanceAmplifier(), true, false, true));
        }
        if (config.isSynergyHasteEnabled()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, config.getSynergyHasteAmplifier(), true, false, true));
        }
    }

    private void removeSynergy(Player player) {
        UUID uuid = player.getUniqueId();
        activeSynergyPlayers.remove(uuid);

        AttributeInstance speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(speedKey);
        }
    }

    private void cleanupActiveBuffs() {
        for (UUID uuid : activeSynergyPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                AttributeInstance speedAttr = p.getAttribute(Attribute.MOVEMENT_SPEED);
                if (speedAttr != null) {
                    speedAttr.removeModifier(speedKey);
                }
            }
        }
        activeSynergyPlayers.clear();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeSynergy(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        removeSynergy(event.getEntity());
    }

    /**
     * Completely cleans up all synergy tasks and revokes modifiers from all players.
     */
    public synchronized void cleanup() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        cleanupActiveBuffs();
    }

    public boolean hasActiveSynergy(UUID uuid) {
        return uuid != null && activeSynergyPlayers.contains(uuid);
    }
}
