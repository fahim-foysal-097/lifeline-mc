package com.lifeline.parcel;

import com.lifeline.Lifeline;
import com.lifeline.config.PluginConfig;
import com.lifeline.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages item parcel delivery validation, cooldowns, direct deliveries,
 * hand item sending, and overflow drop handling.
 */
public class ParcelManager {

    private final Lifeline plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public ParcelManager(Lifeline plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks how many seconds remain on a player's parcel delivery cooldown.
     *
     * @param uuid The player's UUID.
     * @return Remaining seconds, or 0 if no active cooldown.
     */
    public int getRemainingCooldown(UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        Long expiresAt = cooldowns.get(uuid);
        if (expiresAt == null) {
            return 0;
        }
        long remainingMillis = expiresAt - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            cooldowns.remove(uuid);
            return 0;
        }
        return (int) Math.ceil(remainingMillis / 1000.0);
    }

    /**
     * Sets the delivery cooldown for the player based on plugin configuration.
     *
     * @param uuid The sender's UUID.
     */
    public void setCooldown(UUID uuid) {
        if (uuid == null) {
            return;
        }
        int seconds = plugin.getPluginConfig().getParcelCooldownSeconds();
        if (seconds > 0) {
            cooldowns.put(uuid, System.currentTimeMillis() + (seconds * 1000L));
        }
    }

    /**
     * Clears all cooldowns.
     */
    public void cleanup() {
        cooldowns.clear();
    }

    /**
     * Finds the default partner for the sender if there is exactly one other player online.
     *
     * @param sender The sender.
     * @return The single other online player, or null if none or multiple.
     */
    public Player getDefaultPartner(Player sender) {
        if (sender == null) {
            return null;
        }
        List<Player> others = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getUniqueId().equals(sender.getUniqueId())) {
                others.add(p);
            }
        }
        return others.size() == 1 ? others.getFirst() : null;
    }

    /**
     * Validates if a parcel delivery can be initiated from sender to target.
     *
     * @param sender The sender.
     * @param target The recipient.
     * @param notify Whether to send error messages to the sender.
     * @return true if valid, false otherwise.
     */
    public boolean validateDelivery(Player sender, Player target, boolean notify) {
        if (sender == null) {
            return false;
        }

        PluginConfig config = plugin.getPluginConfig();
        if (!config.isParcelEnabled()) {
            if (notify) {
                MessageUtil.sendPrefixed(sender, "parcel.globally-disabled");
            }
            return false;
        }

        // Downed check on sender
        if (plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(sender.getUniqueId())) {
            if (notify) {
                MessageUtil.sendPrefixed(sender, "parcel.downed-blocked");
            }
            return false;
        }

        // Sender spectator check
        if (sender.getGameMode() == GameMode.SPECTATOR) {
            if (notify) {
                MessageUtil.sendPrefixed(sender, "parcel.sender-spectator");
            }
            return false;
        }

        // Target null or offline
        if (target == null || !target.isOnline()) {
            if (notify) {
                String targetName = target != null ? target.getName() : "Unknown";
                MessageUtil.sendPrefixed(sender, "parcel.player-offline", MessageUtil.unparsed("player", targetName));
            }
            return false;
        }

        // Self-target check
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            if (notify) {
                MessageUtil.sendPrefixed(sender, "parcel.self-target");
            }
            return false;
        }

        // Target spectator check
        if (target.getGameMode() == GameMode.SPECTATOR) {
            if (notify) {
                MessageUtil.sendPrefixed(sender, "parcel.target-spectator", MessageUtil.unparsed("player", target.getName()));
            }
            return false;
        }

        // Target downed check
        if (plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(target.getUniqueId())) {
            if (notify) {
                MessageUtil.sendPrefixed(sender, "parcel.target-downed", MessageUtil.unparsed("player", target.getName()));
            }
            return false;
        }

        // Dimension check
        if (!config.isParcelAllowCrossDimension() && !sender.getWorld().equals(target.getWorld())) {
            if (notify) {
                MessageUtil.sendPrefixed(sender, "parcel.different-dimension", MessageUtil.unparsed("player", target.getName()));
            }
            return false;
        }

        // Cooldown check (only bypasses if explicit bypass permission is granted)
        if (!sender.hasPermission("lifeline.parcel.bypass")) {
            int remaining = getRemainingCooldown(sender.getUniqueId());
            if (remaining > 0) {
                if (notify) {
                    MessageUtil.sendPrefixed(sender, "parcel.cooldown", MessageUtil.p("seconds", String.valueOf(remaining)));
                }
                return false;
            }
        }

        return true;
    }

    /**
     * Delivers a list of items from sender to target.
     * Handles inventory overflow by dropping leftovers at target's feet safely.
     *
     * @param sender The sender player.
     * @param target The recipient player.
     * @param items  The items to deliver.
     * @return true if successfully delivered, false otherwise.
     */
    public boolean deliverParcel(Player sender, Player target, List<ItemStack> items) {
        if (!validateDelivery(sender, target, true)) {
            return false;
        }

        if (items == null || items.isEmpty()) {
            MessageUtil.sendPrefixed(sender, "parcel.empty");
            return false;
        }

        int totalCount = 0;
        boolean hadOverflow = false;

        for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            totalCount += item.getAmount();

            // Attempt to add to target inventory
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(item.clone());
            if (!overflow.isEmpty()) {
                hadOverflow = true;
                Location targetLoc = target.getLocation();
                for (ItemStack leftover : overflow.values()) {
                    if (leftover != null && !leftover.getType().isAir()) {
                        target.getWorld().dropItemNaturally(targetLoc, leftover);
                    }
                }
            }
        }

        if (totalCount == 0) {
            MessageUtil.sendPrefixed(sender, "parcel.empty");
            return false;
        }

        // Apply cooldown
        setCooldown(sender.getUniqueId());

        // Notify sender and target
        MessageUtil.sendPrefixed(sender, "parcel.sent",
                MessageUtil.p("count", String.valueOf(totalCount)),
                MessageUtil.unparsed("player", target.getName()));

        MessageUtil.sendPrefixed(target, "parcel.received",
                MessageUtil.p("count", String.valueOf(totalCount)),
                MessageUtil.unparsed("player", sender.getName()));

        if (hadOverflow) {
            MessageUtil.sendPrefixed(target, "parcel.received-overflow");
        }

        // Audio & particle feedback
        if (plugin.getPluginConfig().isParcelSoundEffectsEnabled()) {
            sender.playSound(sender.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.2f);
            target.playSound(target.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 1.0f, 1.1f);
        }
        if (plugin.getPluginConfig().isParticlesEnabled()) {
            target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, target.getLocation().add(0, 1.0, 0), 10, 0.4, 0.4, 0.4, 0.1);
        }

        return true;
    }

    /**
     * Quickly delivers the item currently in the sender's main hand to the target.
     *
     * @param sender The sender player.
     * @param target The recipient player.
     * @return true if successful, false otherwise.
     */
    public boolean sendHandItem(Player sender, Player target) {
        if (!validateDelivery(sender, target, true)) {
            return false;
        }

        ItemStack handItem = sender.getInventory().getItemInMainHand();
        if (handItem.getType().isAir() || handItem.getAmount() <= 0) {
            MessageUtil.sendPrefixed(sender, "parcel.hand-empty");
            return false;
        }

        ItemStack toSend = handItem.clone();
        int amount = toSend.getAmount();
        String itemName = toSend.getType().name().replace('_', ' ').toLowerCase(Locale.ROOT);
        if (toSend.hasItemMeta() && toSend.getItemMeta().hasDisplayName() && toSend.getItemMeta().displayName() != null) {
            String customName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(toSend.getItemMeta().displayName());
            if (customName != null && !customName.isBlank()) {
                itemName = customName;
            }
        }

        // Remove from sender hand
        sender.getInventory().setItemInMainHand(null);

        // Transfer to target
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(toSend);
        boolean hadOverflow = !overflow.isEmpty();
        if (hadOverflow) {
            Location loc = target.getLocation();
            for (ItemStack leftover : overflow.values()) {
                if (leftover != null && !leftover.getType().isAir()) {
                    target.getWorld().dropItemNaturally(loc, leftover);
                }
            }
        }

        // Apply cooldown
        setCooldown(sender.getUniqueId());

        // Notify
        MessageUtil.sendPrefixed(sender, "parcel.sent-hand",
                MessageUtil.p("amount", String.valueOf(amount)),
                MessageUtil.p("item", itemName),
                MessageUtil.unparsed("player", target.getName()));

        MessageUtil.sendPrefixed(target, "parcel.received-hand",
                MessageUtil.p("amount", String.valueOf(amount)),
                MessageUtil.p("item", itemName),
                MessageUtil.unparsed("player", sender.getName()));

        if (hadOverflow) {
            MessageUtil.sendPrefixed(target, "parcel.received-overflow");
        }

        // Audio & particles
        if (plugin.getPluginConfig().isParcelSoundEffectsEnabled()) {
            sender.playSound(sender.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.2f);
            target.playSound(target.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 1.0f, 1.1f);
        }
        if (plugin.getPluginConfig().isParticlesEnabled()) {
            target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, target.getLocation().add(0, 1.0, 0), 10, 0.4, 0.4, 0.4, 0.1);
        }

        return true;
    }
}
