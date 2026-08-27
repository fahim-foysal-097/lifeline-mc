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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 54-Slot chest GUI for displaying, teleporting to, and managing shared waypoints
 * with multi-page navigation (supporting up to the configured max pages, hard max: 2).
 */
public class WaypointGUI implements Listener, InventoryHolder {

    private final Lifeline plugin;
    private final WaypointManager manager;
    private final NamespacedKey waypointKey;
    private final NamespacedKey pageKey;

    public WaypointGUI(Lifeline plugin, WaypointManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.waypointKey = new NamespacedKey(plugin, "waypoint_name");
        this.pageKey = new NamespacedKey(plugin, "gui_page");
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    /**
     * Opens the first page of the shared waypoints GUI.
     */
    public void open(Player player) {
        open(player, 1);
    }

    /**
     * Opens a specific page of the shared waypoints GUI.
     */
    public void open(Player player, int page) {
        int maxConfigPages = plugin.getPluginConfig().getWaypointMaxPages();
        List<Waypoint> waypointsList = new ArrayList<>(manager.getAllWaypoints());
        int totalWaypoints = waypointsList.size();
        int totalPages = Math.min(maxConfigPages, Math.max(1, (int) Math.ceil((double) totalWaypoints / 45.0)));

        int safePage = Math.max(1, Math.min(totalPages, page));

        Component title = MessageUtil.get("waypoints.gui-title",
                MessageUtil.p("page", String.valueOf(safePage)),
                MessageUtil.p("max_pages", String.valueOf(maxConfigPages)));

        Inventory inv = Bukkit.createInventory(this, 54, title);

        // Populate waypoints for current page (slots 0 - 44)
        int startIndex = Math.min(totalWaypoints, (safePage - 1) * 45);
        int endIndex = Math.min(totalWaypoints, startIndex + 45);

        for (int i = startIndex; i < endIndex; i++) {
            Waypoint wp = waypointsList.get(i);
            inv.setItem(i - startIndex, createWaypointItem(wp));
        }

        // Fill bottom row with gray stained glass panes (slots 45 - 53)
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.empty());
        border.setItemMeta(borderMeta);

        for (int i = 45; i < 54; i++) {
            inv.setItem(i, border);
        }

        // Previous Page Button in slot 45 (if page > 1)
        if (safePage > 1) {
            ItemStack prevButton = new ItemStack(Material.FEATHER);
            ItemMeta prevMeta = prevButton.getItemMeta();
            prevMeta.displayName(MessageUtil.get("waypoints.prev-page-name", MessageUtil.p("page", String.valueOf(safePage - 1))));
            prevMeta.lore(MessageUtil.getList("waypoints.prev-page-lore", MessageUtil.p("page", String.valueOf(safePage - 1))));
            prevMeta.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, safePage - 1);
            prevButton.setItemMeta(prevMeta);
            inv.setItem(45, prevButton);
        }

        // Add Waypoint Button in slot 49
        ItemStack addButton = new ItemStack(Material.EMERALD);
        ItemMeta addMeta = addButton.getItemMeta();
        addMeta.displayName(MessageUtil.get("waypoints.add-button-name"));
        addMeta.lore(MessageUtil.getList("waypoints.add-button-lore"));
        // Store current page on the button's metadata BEFORE placing it in the inventory.
        // Bukkit stores a copy of the ItemStack internally, so any mutation after inv.setItem() is ignored.
        addMeta.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, safePage);
        addButton.setItemMeta(addMeta);
        inv.setItem(49, addButton);

        // Next Page Button in slot 53 (if safePage < totalPages && safePage < maxConfigPages)
        if (safePage < totalPages && safePage < maxConfigPages) {
            ItemStack nextButton = new ItemStack(Material.FEATHER);
            ItemMeta nextMeta = nextButton.getItemMeta();
            nextMeta.displayName(MessageUtil.get("waypoints.next-page-name", MessageUtil.p("page", String.valueOf(safePage + 1))));
            nextMeta.lore(MessageUtil.getList("waypoints.next-page-lore", MessageUtil.p("page", String.valueOf(safePage + 1))));
            nextMeta.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, safePage + 1);
            nextButton.setItemMeta(nextMeta);
            inv.setItem(53, nextButton);
        }

        player.openInventory(inv);
        if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        }
    }

    private ItemStack createWaypointItem(Waypoint wp) {
        Material material = Material.COMPASS;
        String dimensionName = MessageUtil.getRaw("waypoints.dim-overworld", "Overworld");

        World world = Bukkit.getWorld(wp.getWorldName());
        if (world != null) {
            if (world.getEnvironment() == World.Environment.NETHER) {
                material = Material.NETHER_STAR;
                dimensionName = MessageUtil.getRaw("waypoints.dim-nether", "The Nether");
            } else if (world.getEnvironment() == World.Environment.THE_END) {
                material = Material.ENDER_EYE;
                dimensionName = MessageUtil.getRaw("waypoints.dim-end", "The End");
            }
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(MessageUtil.get("waypoints.item-name", MessageUtil.p("name", wp.getName())));

        int warmupSeconds = plugin.getPluginConfig().getWaypointWarmupSeconds();
        List<Component> lore = MessageUtil.getList("waypoints.item-lore",
                MessageUtil.p("dim", dimensionName),
                MessageUtil.p("x", String.valueOf((int) wp.getX())),
                MessageUtil.p("y", String.valueOf((int) wp.getY())),
                MessageUtil.p("z", String.valueOf((int) wp.getZ())),
                MessageUtil.p("creator", wp.getCreatorName()),
                MessageUtil.p("seconds", String.valueOf(warmupSeconds)));

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

        // Determine current page from the Add Button in slot 49
        int currentPage = 1;
        ItemStack addBtn = event.getInventory().getItem(49);
        if (addBtn != null && addBtn.hasItemMeta() && addBtn.getItemMeta().getPersistentDataContainer().has(pageKey, PersistentDataType.INTEGER)) {
            Integer storedPage = addBtn.getItemMeta().getPersistentDataContainer().get(pageKey, PersistentDataType.INTEGER);
            if (storedPage != null) currentPage = storedPage;
        }

        // Slot 45: Previous Page
        if (slot == 45 && clickedItem.getType() == Material.FEATHER) {
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(pageKey, PersistentDataType.INTEGER)) {
                int targetPage = meta.getPersistentDataContainer().get(pageKey, PersistentDataType.INTEGER);
                open(player, targetPage);
                if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
                }
            }
            return;
        }

        // Slot 53: Next Page
        if (slot == 53 && clickedItem.getType() == Material.FEATHER) {
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(pageKey, PersistentDataType.INTEGER)) {
                int targetPage = meta.getPersistentDataContainer().get(pageKey, PersistentDataType.INTEGER);
                open(player, targetPage);
                if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
                }
            }
            return;
        }

        // Slot 49: Add Waypoint button
        if (slot == 49) {
            int maxWaypoints = plugin.getPluginConfig().getMaxWaypoints();
            int maxPages = plugin.getPluginConfig().getWaypointMaxPages();
            if (manager.getAllWaypoints().size() >= maxWaypoints) {
                MessageUtil.sendPrefixed(player, "waypoints.capacity-reached",
                        MessageUtil.p("max", String.valueOf(maxWaypoints)),
                        MessageUtil.p("pages", String.valueOf(maxPages)));
                if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                return;
            }
            manager.startAddWaypointPrompt(player);
            return;
        }

        // Waypoint item clicked (slots 0 - 44)
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(waypointKey, PersistentDataType.STRING)) {
            return;
        }

        String wpName = meta.getPersistentDataContainer().get(waypointKey, PersistentDataType.STRING);
        Waypoint wp = manager.getWaypoint(wpName);

        if (wp == null) {
            MessageUtil.sendPrefixed(player, "waypoints.not-found");
            open(player, currentPage);
            return;
        }

        // Shift + Right-Click: Delete
        if (event.getClick() == ClickType.SHIFT_RIGHT) {
            if (manager.deleteWaypoint(wpName)) {
                MessageUtil.sendPrefixed(player, "waypoints.deleted", MessageUtil.p("name", wpName));
                if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.2f);
                }
                open(player, currentPage);
            }
            return;
        }

        // Left-Click: Teleport
        if (event.getClick() == ClickType.LEFT) {
            manager.startTeleportWarmup(player, wp);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof WaypointGUI) {
            event.setCancelled(true);
        }
    }
}
