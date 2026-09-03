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
 * GUI for browsing, teleporting to, and managing personal waypoints (max 27).
 */
public class PersonalWaypointGUI implements Listener, InventoryHolder {

    private final Lifeline plugin;
    private final PersonalWaypointManager manager;
    private final NamespacedKey waypointKey;

    public PersonalWaypointGUI(Lifeline plugin, PersonalWaypointManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.waypointKey = new NamespacedKey(plugin, "personal_waypoint_name");
    }

    private Inventory holderSentinel;

    @Override
    public Inventory getInventory() {
        if (holderSentinel == null && Bukkit.getServer() != null) {
            holderSentinel = Bukkit.createInventory(null, 9);
        }
        return holderSentinel;
    }

    /**
     * Opens the personal waypoints GUI for the player.
     */
    public void open(Player player) {
        if (plugin.getPluginConfig().isBedrockFormsEnabled() && com.lifeline.bedrock.GeyserHook.isBedrockPlayer(player)) {
            com.lifeline.bedrock.GeyserHook.openPersonalWaypointsForm(player, manager, plugin);
            return;
        }

        List<Waypoint> waypointsList = new ArrayList<>(manager.getWaypoints(player.getUniqueId()));
        int count = waypointsList.size();

        Component title = MessageUtil.get("personal-waypoints.gui-title",
                MessageUtil.p("count", String.valueOf(count)),
                MessageUtil.p("max", String.valueOf(PersonalWaypointManager.MAX_PERSONAL_WAYPOINTS)));

        // 36 slots: 3 rows of waypoints (slots 0..26) + 1 bottom row (slots 27..35)
        Inventory inv = Bukkit.createInventory(this, 36, title);

        for (int i = 0; i < Math.min(27, count); i++) {
            Waypoint wp = waypointsList.get(i);
            inv.setItem(i, createWaypointItem(wp));
        }

        // Bottom row border
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.empty());
        border.setItemMeta(borderMeta);

        for (int i = 27; i < 36; i++) {
            inv.setItem(i, border);
        }

        // Add Waypoint Button in slot 31 (center of bottom row)
        ItemStack addButton = new ItemStack(Material.EMERALD);
        ItemMeta addMeta = addButton.getItemMeta();
        addMeta.displayName(MessageUtil.get("personal-waypoints.add-button-name"));
        addMeta.lore(MessageUtil.getList("personal-waypoints.add-button-lore",
                MessageUtil.p("count", String.valueOf(count)),
                MessageUtil.p("max", String.valueOf(PersonalWaypointManager.MAX_PERSONAL_WAYPOINTS))));
        addButton.setItemMeta(addMeta);
        inv.setItem(31, addButton);

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

        meta.displayName(MessageUtil.get("personal-waypoints.item-name", MessageUtil.p("name", wp.getName())));

        int warmupSeconds = plugin.getPluginConfig().getWaypointWarmupSeconds();
        List<Component> lore = MessageUtil.getList("personal-waypoints.item-lore",
                MessageUtil.p("dim", dimensionName),
                MessageUtil.p("x", String.valueOf((int) wp.getX())),
                MessageUtil.p("y", String.valueOf((int) wp.getY())),
                MessageUtil.p("z", String.valueOf((int) wp.getZ())),
                MessageUtil.p("seconds", String.valueOf(warmupSeconds)));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(waypointKey, PersistentDataType.STRING, wp.getName());
        item.setItemMeta(meta);

        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PersonalWaypointGUI)) {
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

        // Slot 31: Add Personal Waypoint button
        if (slot == 31) {
            if (manager.getWaypoints(player.getUniqueId()).size() >= PersonalWaypointManager.MAX_PERSONAL_WAYPOINTS) {
                MessageUtil.sendPrefixed(player, "personal-waypoints.capacity-reached",
                        MessageUtil.p("max", String.valueOf(PersonalWaypointManager.MAX_PERSONAL_WAYPOINTS)));
                if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                return;
            }
            manager.startAddWaypointPrompt(player);
            return;
        }

        // Personal waypoint item clicked (slots 0 - 26)
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(waypointKey, PersistentDataType.STRING)) {
            return;
        }

        String wpName = meta.getPersistentDataContainer().get(waypointKey, PersistentDataType.STRING);
        Waypoint wp = manager.getWaypoint(player.getUniqueId(), wpName);

        if (wp == null) {
            MessageUtil.sendPrefixed(player, "personal-waypoints.not-found");
            open(player);
            return;
        }

        // Delete
        if (event.getClick() == ClickType.SHIFT_RIGHT) {
            if (manager.deleteWaypoint(player.getUniqueId(), wpName)) {
                MessageUtil.sendPrefixed(player, "personal-waypoints.deleted", MessageUtil.unparsed("name", wpName));
                if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.2f);
                }
                open(player);
            }
            return;
        }

        // Teleport
        if (event.getClick() == ClickType.LEFT) {
            manager.startTeleportWarmup(player, wp);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PersonalWaypointGUI) {
            event.setCancelled(true);
        }
    }
}
