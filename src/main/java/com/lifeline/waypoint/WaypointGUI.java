package com.lifeline.waypoint;

import com.lifeline.Lifeline;
import com.lifeline.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 54-Slot chest GUI for displaying, teleporting to, and managing shared waypoints.
 */
public class WaypointGUI implements Listener, InventoryHolder {

    private final Lifeline plugin;
    private final WaypointManager manager;
    private final NamespacedKey waypointKey;

    public WaypointGUI(Lifeline plugin, WaypointManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.waypointKey = new NamespacedKey(plugin, "waypoint_name");
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    /**
     * Opens the 54-slot Waypoint GUI for a player.
     */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(this, 54, MessageUtil.parse("<gradient:#00FFA3:#00B8D9><bold>Shared Waypoints</bold></gradient>"));

        // Populate waypoints (slots 0 - 44)
        List<Waypoint> waypointsList = new ArrayList<>(manager.getAllWaypoints());
        int maxItems = Math.min(waypointsList.size(), 45);

        for (int i = 0; i < maxItems; i++) {
            Waypoint wp = waypointsList.get(i);
            inv.setItem(i, createWaypointItem(wp));
        }

        // Fill bottom row with gray stained glass panes
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.empty());
        border.setItemMeta(borderMeta);

        for (int i = 45; i < 54; i++) {
            inv.setItem(i, border);
        }

        // Add Waypoint Button in slot 49
        ItemStack addButton = new ItemStack(Material.EMERALD);
        ItemMeta addMeta = addButton.getItemMeta();
        addMeta.displayName(MessageUtil.parse("<green><bold>+ Add Waypoint</bold></green>"));
        List<Component> addLore = new ArrayList<>();
        addLore.add(MessageUtil.parse("<gray>Click to save your current location"));
        addLore.add(MessageUtil.parse("<gray>as a new shared waypoint.</gray>"));
        addMeta.lore(addLore);
        addButton.setItemMeta(addMeta);

        inv.setItem(49, addButton);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    private ItemStack createWaypointItem(Waypoint wp) {
        Material material = Material.COMPASS;
        String dimensionName = "Overworld";

        World world = Bukkit.getWorld(wp.getWorldName());
        if (world != null) {
            if (world.getEnvironment() == World.Environment.NETHER) {
                material = Material.NETHER_STAR;
                dimensionName = "The Nether";
            } else if (world.getEnvironment() == World.Environment.THE_END) {
                material = Material.ENDER_EYE;
                dimensionName = "The End";
            }
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(MessageUtil.parse("<aqua><bold>" + wp.getName() + "</bold></aqua>"));

        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtil.parse("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        lore.add(MessageUtil.parse("<gray>Dimension: <white>" + dimensionName + "</white></gray>"));
        lore.add(MessageUtil.parse("<gray>Location: <yellow>" + (int) wp.getX() + ", " + (int) wp.getY() + ", " + (int) wp.getZ() + "</yellow></gray>"));
        lore.add(MessageUtil.parse("<gray>Created by: <white>" + wp.getCreatorName() + "</white></gray>"));
        lore.add(MessageUtil.parse("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        lore.add(MessageUtil.parse("<yellow>✦ Left-Click</yellow> <gray>to Teleport (3s warm-up)</gray>"));
        lore.add(MessageUtil.parse("<red>✖ Shift + Right-Click</red> <gray>to Delete</gray>"));

        meta.lore(lore);

        // Store waypoint identifier in PersistentDataContainer
        meta.getPersistentDataContainer().set(waypointKey, PersistentDataType.STRING, wp.getName());
        item.setItemMeta(meta);

        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof WaypointGUI)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }

        int slot = event.getSlot();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR || clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }

        // Add Waypoint button clicked
        if (slot == 49) {
            if (manager.getAllWaypoints().size() >= 45) {
                MessageUtil.sendPrefixed(player, "<red>Maximum capacity reached! You can have at most 45 shared waypoints. Please delete one first.");
                if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                return;
            }
            manager.startAddWaypointPrompt(player);
            return;
        }

        // Waypoint item clicked
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(waypointKey, PersistentDataType.STRING)) {
            return;
        }

        String wpName = meta.getPersistentDataContainer().get(waypointKey, PersistentDataType.STRING);
        Waypoint wp = manager.getWaypoint(wpName);

        if (wp == null) {
            MessageUtil.sendPrefixed(player, "<red>Waypoint not found.");
            open(player);
            return;
        }

        // Shift + Right-Click: Delete
        if (event.getClick() == ClickType.SHIFT_RIGHT) {
            if (manager.deleteWaypoint(wpName)) {
                MessageUtil.sendPrefixed(player, "<red>Deleted waypoint: <yellow>" + wpName + "</yellow>");
                player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.2f);
                open(player);
            }
            return;
        }

        // Left-Click: Teleport
        if (event.getClick() == ClickType.LEFT) {
            manager.startTeleportWarmup(player, wp);
        }
    }

    @EventHandler
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof WaypointGUI) {
            event.setCancelled(true);
        }
    }
}
