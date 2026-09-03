package com.lifeline.trash;

import com.lifeline.Lifeline;
import com.lifeline.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 27-slot chest GUI for Quick Trash (/lltrash).
 * Allows players to drop unwanted items into slots 0-17 and permanently dispose
 * of them by clicking [ Confirm ] in slot 22.
 * If the GUI is closed without confirming, items are safely returned to the player.
 */
public class TrashGUI implements Listener {

    public static final int CONFIRM_SLOT = 22;
    public static final int INFO_SLOT = 18;
    public static final int DISPOSAL_SLOTS_COUNT = 18;

    private final Lifeline plugin;

    public TrashGUI(Lifeline plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the Quick Trash GUI for the given player.
     *
     * @param player The player opening the trash GUI.
     */
    public void open(Player player) {
        TrashHolder holder = new TrashHolder(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 27, MessageUtil.get("trash.title"));

        // Bottom row filler border (slots 18-26)
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.empty());
        border.setItemMeta(borderMeta);

        for (int i = 18; i < 27; i++) {
            inv.setItem(i, border);
        }

        // Info item in slot 18 (Hopper)
        ItemStack info = new ItemStack(Material.HOPPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(MessageUtil.get("trash.info-button-name"));
        infoMeta.lore(MessageUtil.getList("trash.info-button-lore"));
        info.setItemMeta(infoMeta);
        inv.setItem(INFO_SLOT, info);

        // Confirm button in slot 22 (Red Concrete)
        ItemStack confirm = new ItemStack(Material.RED_CONCRETE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.displayName(MessageUtil.get("trash.confirm-button-name"));
        confirmMeta.lore(MessageUtil.getList("trash.confirm-button-lore"));
        confirm.setItemMeta(confirmMeta);
        inv.setItem(CONFIRM_SLOT, confirm);

        player.openInventory(inv);

        if (plugin.getPluginConfig().isTrashSoundEffectsEnabled()) {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TrashHolder holder)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Prevent double click collection pulling items out of control bar
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        // Shift click from player inventory into the trash GUI
        if (event.isShiftClick() && event.getClickedInventory() != null && !event.getClickedInventory().equals(event.getInventory())) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && !clicked.getType().isAir()) {
                event.setCancelled(true);
                int remaining = addItemToDisposalSlots(event.getInventory(), clicked);
                if (remaining <= 0) {
                    event.setCurrentItem(null);
                } else {
                    clicked.setAmount(remaining);
                    event.setCurrentItem(clicked);
                }
            }
            return;
        }

        // Interaction within the top inventory (trash GUI)
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getInventory())) {
            int slot = event.getSlot();

            // Bottom control row (slots 18-26)
            if (slot >= DISPOSAL_SLOTS_COUNT && slot < 27) {
                event.setCancelled(true);

                if (slot == CONFIRM_SLOT) {
                    handleConfirm(player, event.getInventory(), holder);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TrashHolder) {
            // Cancel drag if any target slot is in the bottom control bar (18-26)
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot >= DISPOSAL_SLOTS_COUNT && rawSlot < 27) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TrashHolder holder)) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        // If the player closed the inventory without confirming, return all items safely
        if (!holder.isConfirmed()) {
            List<ItemStack> toReturn = new ArrayList<>();
            for (int i = 0; i < DISPOSAL_SLOTS_COUNT; i++) {
                ItemStack item = event.getInventory().getItem(i);
                if (item != null && !item.getType().isAir()) {
                    toReturn.add(item);
                    event.getInventory().setItem(i, null);
                }
            }

            if (!toReturn.isEmpty()) {
                for (ItemStack item : toReturn) {
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                    if (!leftover.isEmpty()) {
                        for (ItemStack drop : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                        }
                    }
                }

                MessageUtil.sendPrefixed(player, "trash.cancelled");
                if (plugin.getPluginConfig().isTrashSoundEffectsEnabled()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.0f);
                }
            }
        }
    }

    private void handleConfirm(Player player, Inventory inv, TrashHolder holder) {
        int totalCount = 0;
        List<Integer> slotsToClear = new ArrayList<>();

        for (int i = 0; i < DISPOSAL_SLOTS_COUNT; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && !item.getType().isAir()) {
                totalCount += item.getAmount();
                slotsToClear.add(i);
            }
        }

        if (totalCount == 0) {
            MessageUtil.sendPrefixed(player, "trash.empty");
            if (plugin.getPluginConfig().isTrashSoundEffectsEnabled()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
            return;
        }

        // Mark as confirmed so close handler doesn't return items
        holder.setConfirmed(true);

        for (int slot : slotsToClear) {
            inv.setItem(slot, null);
        }

        MessageUtil.sendPrefixed(player, "trash.disposed", MessageUtil.p("count", String.valueOf(totalCount)));

        if (plugin.getPluginConfig().isTrashSoundEffectsEnabled()) {
            player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.8f, 1.2f);
        }

        player.closeInventory();
    }

    private int addItemToDisposalSlots(Inventory inv, ItemStack toAdd) {
        int remaining = toAdd.getAmount();
        int maxStack = toAdd.getMaxStackSize();

        // Pass 1: merge into existing matching stacks in slots 0-17
        for (int i = 0; i < DISPOSAL_SLOTS_COUNT; i++) {
            ItemStack current = inv.getItem(i);
            if (current != null && current.isSimilar(toAdd)) {
                int canAdd = maxStack - current.getAmount();
                if (canAdd > 0) {
                    int add = Math.min(canAdd, remaining);
                    current.setAmount(current.getAmount() + add);
                    remaining -= add;
                    if (remaining <= 0) {
                        return 0;
                    }
                }
            }
        }

        // Pass 2: place into empty slots in slots 0-17
        for (int i = 0; i < DISPOSAL_SLOTS_COUNT; i++) {
            ItemStack current = inv.getItem(i);
            if (current == null || current.getType().isAir()) {
                int add = Math.min(maxStack, remaining);
                ItemStack clone = toAdd.clone();
                clone.setAmount(add);
                inv.setItem(i, clone);
                remaining -= add;
                if (remaining <= 0) {
                    return 0;
                }
            }
        }

        return remaining;
    }
}
