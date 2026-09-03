package com.lifeline.tether;

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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 27-slot chest GUI displaying online players with custom player heads,
 * dimension status, distance, and health to easily send teleport requests (/tpq, /teleportgui).
 */
public class TetherGUI implements Listener, InventoryHolder {

    private final Lifeline plugin;
    private final TetherManager manager;
    private final NamespacedKey playerKey;

    public TetherGUI(Lifeline plugin, TetherManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.playerKey = new NamespacedKey(plugin, "target_player_uuid");
    }

    // Sentinel inventory returned by the InventoryHolder contract.
    // Each GUI open creates a fresh inventory; this sentinel is never shown to players.
    private Inventory holderSentinel;

    @Override
    public Inventory getInventory() {
        if (holderSentinel == null && Bukkit.getServer() != null) {
            holderSentinel = Bukkit.createInventory(null, 9);
        }
        return holderSentinel;
    }

    /**
     * Opens the Teleport Player GUI for the specified player.
     */
    public void open(Player player) {
        if (plugin.getPluginConfig().isBedrockFormsEnabled() && com.lifeline.bedrock.GeyserHook.isBedrockPlayer(player)) {
            com.lifeline.bedrock.GeyserHook.openTetherForm(player, manager, plugin);
            return;
        }

        Inventory inv = Bukkit.createInventory(this, 27, MessageUtil.get("teleport.gui-title"));

        List<Player> targets = new ArrayList<>(Bukkit.getOnlinePlayers());
        targets.remove(player);

        // Fill background border
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.empty());
        border.setItemMeta(borderMeta);

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        if (targets.isEmpty()) {
            ItemStack noPlayers = new ItemStack(Material.BARRIER);
            ItemMeta meta = noPlayers.getItemMeta();
            meta.displayName(MessageUtil.get("teleport.no-players-name"));
            meta.lore(MessageUtil.getList("teleport.no-players-lore"));
            noPlayers.setItemMeta(meta);
            inv.setItem(13, noPlayers);
        } else {
            // Place player heads across available chest slots
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 1, 2, 3, 4, 5, 6, 7, 19, 20, 21, 22, 23, 24, 25};
            int count = Math.min(targets.size(), slots.length);

            for (int i = 0; i < count; i++) {
                Player target = targets.get(i);
                inv.setItem(slots[i], createPlayerHead(player, target));
            }
        }

        player.openInventory(inv);
        if (plugin.getPluginConfig().isSoundEffectsEnabled()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        }
    }

    private ItemStack createPlayerHead(Player viewer, Player target) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(target);

        boolean isDowned = plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(target.getUniqueId());
        boolean isSpectator = target.getGameMode() == org.bukkit.GameMode.SPECTATOR;

        if (isDowned) {
            meta.displayName(MessageUtil.get("teleport.head-downed", MessageUtil.p("player", target.getName())));
        } else if (isSpectator) {
            meta.displayName(MessageUtil.get("teleport.head-spectator", MessageUtil.p("player", target.getName())));
        } else {
            meta.displayName(MessageUtil.get("teleport.head-normal", MessageUtil.p("player", target.getName())));
        }

        // Dimension
        String dimensionName = MessageUtil.getRaw("waypoints.dim-overworld", "Overworld");
        World world = target.getWorld();
        if (world.getEnvironment() == World.Environment.NETHER) {
            dimensionName = MessageUtil.getRaw("waypoints.dim-nether", "The Nether");
        } else if (world.getEnvironment() == World.Environment.THE_END) {
            dimensionName = MessageUtil.getRaw("waypoints.dim-end", "The End");
        }

        // Distance
        String distanceStr;
        if (viewer.getWorld().equals(target.getWorld())) {
            // Both in same world - distance() is safe
            int dist = (int) viewer.getLocation().distance(target.getLocation());
            distanceStr = String.valueOf(dist) + " blocks";
        } else {
            distanceStr = MessageUtil.getRaw("teleport.head-lore-diff-dim", "Different Dimension");
        }

        // Health
        int health = (int) Math.ceil(target.getHealth());

        List<Component> lore = new ArrayList<>(MessageUtil.getList("teleport.head-lore",
                MessageUtil.p("dim", dimensionName),
                MessageUtil.p("dist", distanceStr),
                MessageUtil.p("health", String.valueOf(health))));

        if (isDowned) {
            lore.add(MessageUtil.get("teleport.head-lore-downed-footer"));
        } else if (isSpectator) {
            lore.add(MessageUtil.get("teleport.head-lore-spectator-footer"));
        } else {
            lore.add(MessageUtil.get("teleport.head-lore-click-footer"));
        }

        meta.lore(lore);
        meta.getPersistentDataContainer().set(playerKey, PersistentDataType.STRING, target.getUniqueId().toString());
        item.setItemMeta(meta);

        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TetherGUI)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) {
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(playerKey, PersistentDataType.STRING)) {
            return;
        }

        String uuidStr = meta.getPersistentDataContainer().get(playerKey, PersistentDataType.STRING);
        if (uuidStr == null) return;

        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        Player target = Bukkit.getPlayer(targetUuid);

        player.closeInventory();

        if (target == null || !target.isOnline()) {
            String offlineName = Bukkit.getOfflinePlayer(targetUuid).getName();
            MessageUtil.sendPrefixed(player, "teleport.player-offline",
                    MessageUtil.unparsed("player", offlineName != null ? offlineName : uuidStr));
            return;
        }

        manager.sendRequest(player, target);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TetherGUI) {
            event.setCancelled(true);
        }
    }
}
