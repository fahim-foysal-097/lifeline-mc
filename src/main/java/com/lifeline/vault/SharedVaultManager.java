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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Manages the shared 54-slot vault and persistence to vault.yml.
 */
public class SharedVaultManager implements Listener, InventoryHolder {

    private final Lifeline plugin;
    private final File vaultFile;
    private final Inventory vaultInventory;

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
     * Loads saved item stacks from vault.yml into the inventory.
     */
    public synchronized void loadVault() {
        if (!vaultFile.exists()) {
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
        plugin.getLogger().info("Loaded Shared Vault inventory from disk.");
    }

    /**
     * Saves the inventory contents directly to vault.yml.
     */
    public synchronized void saveVault() {
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
            config.save(vaultFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save vault.yml", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof SharedVaultManager) {
            saveVault();
            if (event.getPlayer() instanceof Player player && plugin.getPluginConfig().isSoundEffectsEnabled()) {
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 1.0f);
            }
        }
    }

    public void cleanup() {
        saveVault();
    }
}
