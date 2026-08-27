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

    @Override
    public Inventory getInventory() {
        return null;
    }

    /**
     * Opens the Teleport Player GUI for the specified player.
     */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(this, 27, MessageUtil.parse("<gradient:#00FFA3:#00B8D9><bold>Teleport to Player</bold></gradient>"));

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
            meta.displayName(MessageUtil.parse("<red><bold>No Other Players Online</bold></red>"));
            meta.lore(List.of(
                    MessageUtil.parse("<gray>Wait for a teammate to join the server.</gray>")
            ));
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
            meta.displayName(MessageUtil.parse("<red><bold>" + target.getName() + " (Downed)</bold></red>"));
        } else if (isSpectator) {
            meta.displayName(MessageUtil.parse("<gray><bold>" + target.getName() + " (Spectator)</bold></gray>"));
        } else {
            meta.displayName(MessageUtil.parse("<aqua><bold>" + target.getName() + "</bold></aqua>"));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtil.parse("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));

        // Dimension
        String dimensionName = "Overworld";
        World world = target.getWorld();
        if (world.getEnvironment() == World.Environment.NETHER) {
            dimensionName = "The Nether";
        } else if (world.getEnvironment() == World.Environment.THE_END) {
            dimensionName = "The End";
        }
        lore.add(MessageUtil.parse("<gray>Dimension: <white>" + dimensionName + "</white></gray>"));

        // Distance
        if (viewer.getWorld().equals(target.getWorld())) {
            int dist = (int) viewer.getLocation().distance(target.getLocation());
            lore.add(MessageUtil.parse("<gray>Distance: <yellow>" + dist + " blocks</yellow></gray>"));
        } else {
            lore.add(MessageUtil.parse("<gray>Distance: <white>Different Dimension</white></gray>"));
        }

        // Health
        int health = (int) Math.ceil(target.getHealth());
        lore.add(MessageUtil.parse("<gray>Health: <red>" + health + " HP</red></gray>"));

        lore.add(MessageUtil.parse("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));

        if (isDowned) {
            lore.add(MessageUtil.parse("<red>✖ Cannot teleport to downed players.</red>"));
        } else if (isSpectator) {
            lore.add(MessageUtil.parse("<red>✖ Cannot teleport to Spectator mode players.</red>"));
        } else {
            lore.add(MessageUtil.parse("<yellow>✦ Click</yellow> <gray>to send teleport request</gray>"));
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

        UUID targetUuid = UUID.fromString(uuidStr);
        Player target = Bukkit.getPlayer(targetUuid);

        player.closeInventory();

        if (target == null || !target.isOnline()) {
            MessageUtil.sendPrefixed(player, "<red>That player is no longer online.");
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
