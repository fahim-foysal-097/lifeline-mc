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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages per-player personal stashes, in-memory buffering, and safe persistence
 * synchronized with Paper's autosave cycle to prevent item duplication.
 */
public class PersonalVaultManager implements Listener {

    private final Lifeline plugin;
    private final File stashFile;
    private YamlConfiguration stashConfig;
    private final Map<UUID, Inventory> activeInventories = new ConcurrentHashMap<>();

    private volatile boolean isDirty = false;
    private long lastSaveTick = -1;

    public PersonalVaultManager(Lifeline plugin) {
        this.plugin = plugin;
        this.stashFile = new File(plugin.getDataFolder(), "personal-stashes.yml");
        loadStashes();
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void markDirty() {
        this.isDirty = true;
    }

    /**
     * Loads the YAML configuration from disk after persisting any active inventories.
     */
    public synchronized void loadStashes() {
        if (stashConfig != null && !activeInventories.isEmpty()) {
            savePersonalStashes(true);
            activeInventories.clear();
        }
        File backupFile = new File(new File(plugin.getDataFolder(), "backup"), "personal-stashes.yml.bak");
        if (!stashFile.exists() && !backupFile.exists()) {
            stashConfig = new YamlConfiguration();
            this.isDirty = false;
            return;
        }
        stashConfig = com.lifeline.util.SafeFileUtil.loadWithAutoRecovery(stashFile, backupFile, plugin.getLogger());
        this.isDirty = false;
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

        // Slot size changed: buffer old inventory into config before creating new one
        if (existing != null) {
            syncInventoryToConfig(uuid, existing);
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
     * Synchronizes a player's in-memory inventory into the YAML configuration model
     * without triggering disk writes. Preserves higher slot items if slot capacity was reduced.
     */
    public synchronized void syncInventoryToConfig(UUID uuid, Inventory inventory) {
        if (stashConfig == null) {
            stashConfig = new YamlConfiguration();
        }

        String pathPrefix = "stashes." + uuid.toString() + ".items";

        // Preserve any existing items in slots >= inventory.getSize()
        Map<Integer, ItemStack> preservedItems = new java.util.HashMap<>();
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

        for (Map.Entry<Integer, ItemStack> entry : preservedItems.entrySet()) {
            stashConfig.set(pathPrefix + "." + entry.getKey(), entry.getValue());
        }

        this.isDirty = true;
    }

    /**
     * Saves a specific player's stash inventory to disk.
     */
    public synchronized void saveStash(UUID uuid, Inventory inventory) {
        syncInventoryToConfig(uuid, inventory);
        savePersonalStashes(true);
    }

    /**
     * Flushes all active personal stashes to personal-stashes.yml atomically.
     *
     * @param force if true, writes to disk even if no changes are marked dirty
     * @return true if saved to disk, false if skipped or failed
     */
    public synchronized boolean savePersonalStashes(boolean force) {
        if (!force && !isDirty) {
            return false;
        }

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        if (stashConfig == null) {
            stashConfig = new YamlConfiguration();
        }

        for (Map.Entry<UUID, Inventory> entry : activeInventories.entrySet()) {
            syncInventoryToConfig(entry.getKey(), entry.getValue());
        }

        // Maintain latest pre-save .bak copy in backup/
        File backupFile = new File(new File(plugin.getDataFolder(), "backup"), "personal-stashes.yml.bak");
        com.lifeline.util.SafeFileUtil.copyBackupAtomically(stashFile, backupFile);

        try {
            com.lifeline.util.SafeFileUtil.saveConfigurationAtomically(stashConfig, stashFile);
            this.isDirty = false;
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save personal-stashes.yml", e);
            return false;
        }
    }

    /**
     * Legacy alias for savePersonalStashes(true).
     */
    public synchronized void saveAll() {
        savePersonalStashes(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof PersonalVaultHolder) {
            this.isDirty = true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PersonalVaultHolder) {
            this.isDirty = true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PersonalVaultHolder holder) {
            syncInventoryToConfig(holder.getOwnerUuid(), event.getInventory());
            if (event.getPlayer() instanceof Player player && plugin.getPluginConfig().isSoundEffectsEnabled()) {
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 1.0f);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Inventory inv = activeInventories.remove(uuid);
        if (inv != null) {
            syncInventoryToConfig(uuid, inv);
        }

        // If any stash was modified, sync all online players and stashes to disk
        // to prevent desynchronization if a crash occurs after player quit.
        if (isDirty || (plugin.getSharedVaultManager() != null && plugin.getSharedVaultManager().isDirty())) {
            plugin.saveAllStashesAndPlayers(false);
        }
    }

    /**
     * Synchronizes in-memory personal stashes with Paper's autosave cycle.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        long currentTick = Bukkit.getCurrentTick();
        if (currentTick == lastSaveTick) {
            return;
        }
        lastSaveTick = currentTick;

        if (isDirty) {
            savePersonalStashes(false);
        }
    }

    public void cleanup() {
        savePersonalStashes(true);
        activeInventories.clear();
    }
}
