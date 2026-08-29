package com.lifeline.vault;

import com.lifeline.Lifeline;
import com.lifeline.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages per-player personal stashes and persistence to personal-stashes.yml.
 */
public class PersonalVaultManager implements Listener {

    private final Lifeline plugin;
    private final File stashFile;
    private YamlConfiguration stashConfig;
    private final Map<UUID, Inventory> activeInventories = new ConcurrentHashMap<>();

    public PersonalVaultManager(Lifeline plugin) {
        this.plugin = plugin;
        this.stashFile = new File(plugin.getDataFolder(), "personal-stashes.yml");
        loadStashes();
    }

    /**
     * Loads the YAML configuration from disk after persisting any active inventories.
     */
    public synchronized void loadStashes() {
        if (stashConfig != null && !activeInventories.isEmpty()) {
            saveAll();
            activeInventories.clear();
        }
        if (!stashFile.exists()) {
            stashConfig = new YamlConfiguration();
            return;
        }
        stashConfig = YamlConfiguration.loadConfiguration(stashFile);
        plugin.getLogger().info("Loaded Personal Stash configuration from disk.");
    }

    /**
     * Retrieves or creates the personal stash inventory for a player.
     */
    public synchronized Inventory getOrCreateInventory(UUID uuid) {
        int slots = plugin.getPluginConfig().getPersonalStashSlots();

        Inventory existing = activeInventories.get(uuid);
        if (existing != null && existing.getSize() == slots) {
            return existing;
        }

        // Slot size changed: persist the old inventory before creating a new one
        if (existing != null) {
            saveStash(uuid, existing);
        }

        Inventory inv = Bukkit.createInventory(
                new PersonalVaultHolder(uuid),
                slots,
                MessageUtil.get("personal-stash.title")
        );

        String pathPrefix = "stashes." + uuid.toString() + ".items.";
        if (stashConfig != null) {
            for (int i = 0; i < slots; i++) {
                if (stashConfig.contains(pathPrefix + i)) {
                    ItemStack item = stashConfig.getItemStack(pathPrefix + i);
                    if (item != null) {
                        inv.setItem(i, item);
                    }
                }
            }
        }

        activeInventories.put(uuid, inv);
        return inv;
    }

    /**
     * Opens the personal stash for a player.
     */
    public void openStash(Player player) {
        if (!plugin.getPluginConfig().isPersonalStashEnabled()) {
            MessageUtil.sendPrefixed(player, "personal-stash.globally-disabled");
            return;
        }

        if (plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(player.getUniqueId())) {
            MessageUtil.sendPrefixed(player, "personal-stash.downed-blocked");
            return;
        }

        Inventory inv = getOrCreateInventory(player.getUniqueId());
        player.openInventory(inv);

        if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f);
        }
    }

    /**
     * Saves a specific player's stash inventory to disk while preserving
     * any items in higher slots if the config slots were reduced.
     */
    public synchronized void saveStash(UUID uuid, Inventory inventory) {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        if (stashConfig == null) {
            stashConfig = new YamlConfiguration();
        }

        String pathPrefix = "stashes." + uuid.toString() + ".items";

        // Preserve any existing items in slots >= inventory.getSize()
        java.util.Map<Integer, ItemStack> preservedItems = new java.util.HashMap<>();
        if (stashConfig.isConfigurationSection(pathPrefix)) {
            org.bukkit.configuration.ConfigurationSection section = stashConfig.getConfigurationSection(pathPrefix);
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    try {
                        int slotIndex = Integer.parseInt(key);
                        if (slotIndex >= inventory.getSize()) {
                            ItemStack item = section.getItemStack(key);
                            if (item != null && !item.getType().isAir()) {
                                preservedItems.put(slotIndex, item);
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        stashConfig.set("stashes." + uuid.toString(), null);

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir()) {
                stashConfig.set(pathPrefix + "." + i, item);
            }
        }

        for (java.util.Map.Entry<Integer, ItemStack> entry : preservedItems.entrySet()) {
            stashConfig.set(pathPrefix + "." + entry.getKey(), entry.getValue());
        }

        try {
            stashConfig.save(stashFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save personal-stashes.yml for " + uuid, e);
        }
    }

    /**
     * Saves all active cached inventories to disk.
     */
    public synchronized void saveAll() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        if (stashConfig == null) {
            stashConfig = new YamlConfiguration();
        }

        for (Map.Entry<UUID, Inventory> entry : activeInventories.entrySet()) {
            saveStash(entry.getKey(), entry.getValue());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PersonalVaultHolder holder) {
            saveStash(holder.getOwnerUuid(), event.getInventory());
            if (event.getPlayer() instanceof Player player && plugin.getPluginConfig().isSoundEffectsEnabled()) {
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 1.0f);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Inventory inv = activeInventories.remove(uuid);
        if (inv != null) {
            saveStash(uuid, inv);
        }
    }

    public void cleanup() {
        saveAll();
        activeInventories.clear();
    }
}
