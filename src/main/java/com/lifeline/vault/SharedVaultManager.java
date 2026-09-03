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
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/**
 * Manages the shared 54-slot vault, in-memory buffering, and safe persistence
 * synchronized with Paper's autosave cycle to prevent item duplication.
 */
public class SharedVaultManager implements Listener, InventoryHolder {

    private final Lifeline plugin;
    private final File vaultFile;
    private final Inventory vaultInventory;

    private volatile boolean isDirty = false;
    private long lastSaveTick = -1;

    public SharedVaultManager(Lifeline plugin) {
        this.plugin = plugin;
        this.vaultFile = new File(plugin.getDataFolder(), "vault.yml");
        this.vaultInventory = Bukkit.createInventory(this, 54, MessageUtil.get("stash.title"));
        loadVault();
    }

    @Override
    public Inventory getInventory() {
        return vaultInventory;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void markDirty() {
        this.isDirty = true;
    }

    /**
     * Opens the singleton shared vault instance for a player.
     */
    public void openVault(Player player) {
        player.openInventory(vaultInventory);
        if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f);
        }
    }

    /**
     * Loads saved item stacks from vault.yml into the in-memory inventory.
     */
    public synchronized void loadVault() {
        if (!vaultFile.exists()) {
            this.isDirty = false;
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(vaultFile);
        vaultInventory.clear();

        for (int i = 0; i < vaultInventory.getSize(); i++) {
            if (config.contains("items." + i)) {
                ItemStack item = config.getItemStack("items." + i);
                if (item != null) {
                    vaultInventory.setItem(i, item);
                }
            }
        }
        this.isDirty = false;
        plugin.getLogger().info("Loaded Shared Vault inventory from disk.");
    }

    /**
     * Saves the in-memory inventory contents directly to vault.yml atomically.
     *
     * @param force if true, writes to disk even if the inventory has not been marked dirty
     * @return true if saved to disk, false if skipped or failed
     */
    public synchronized boolean saveVault(boolean force) {
        if (!force && !isDirty) {
            return false;
        }

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        YamlConfiguration config = new YamlConfiguration();
        for (int i = 0; i < vaultInventory.getSize(); i++) {
            ItemStack item = vaultInventory.getItem(i);
            if (item != null && !item.getType().isAir()) {
                config.set("items." + i, item);
            }
        }

        try {
            saveConfigurationAtomically(config, vaultFile);
            this.isDirty = false;
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save vault.yml", e);
            return false;
        }
    }

    /**
     * Force-saves the vault inventory to disk.
     */
    public synchronized void saveVault() {
        saveVault(true);
    }

    private void saveConfigurationAtomically(YamlConfiguration config, File targetFile) throws IOException {
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        File tempFile = new File(parentDir, targetFile.getName() + ".tmp");
        try {
            config.save(tempFile);
            try {
                Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof SharedVaultManager) {
            this.isDirty = true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SharedVaultManager) {
            this.isDirty = true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof SharedVaultManager) {
            this.isDirty = true;
            if (event.getPlayer() instanceof Player player && plugin.getPluginConfig().isSoundEffectsEnabled()) {
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 1.0f);
            }
        }
    }

    /**
     * Synchronizes in-memory shared stash with Paper's autosave cycle.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        long currentTick = Bukkit.getCurrentTick();
        if (currentTick == lastSaveTick) {
            return;
        }
        lastSaveTick = currentTick;

        if (isDirty) {
            saveVault(false);
        }
    }

    public void cleanup() {
        saveVault(true);
    }
}
