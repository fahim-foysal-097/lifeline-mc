package com.lifeline.parcel;

import com.lifeline.Lifeline;
import com.lifeline.config.PluginConfig;
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

import java.util.*;

/**
 * 27-slot chest GUI for Item Parcel Delivery (/parcel).
 * Allows players to place up to the configured slot limit of items and dispatch them
 * directly to a teammate with anti-dupe and automatic safety return on close.
 */
public class ParcelGUI implements Listener {

    public static final int SEND_SLOT = 22;
    public static final int INFO_SLOT = 18;

    private final Lifeline plugin;
    private final ParcelManager parcelManager;

    public ParcelGUI(Lifeline plugin, ParcelManager parcelManager) {
        this.plugin = plugin;
        this.parcelManager = parcelManager;
    }

    /**
     * Calculates the set of active parcel placement slots centered in row 1.
     *
     * @param maxSlots Max slots allowed (1-9).
     * @return Set of slot indices.
     */
    public static Set<Integer> getActiveSlots(int maxSlots) {
        Set<Integer> slots = new LinkedHashSet<>();
        int clamped = Math.max(1, Math.min(9, maxSlots));
        int startSlot = 9 + (9 - clamped) / 2;
        for (int i = 0; i < clamped; i++) {
            slots.add(startSlot + i);
        }
        return slots;
    }

    /**
     * Opens the Parcel Delivery GUI for the sender targeting the recipient.
     *
     * @param sender The sender.
     * @param target The recipient.
     */
    public void open(Player sender, Player target) {
        if (!parcelManager.validateDelivery(sender, target, true)) {
            return;
        }

        PluginConfig config = plugin.getPluginConfig();
        int maxSlots = config.getParcelMaxSlots();
        Set<Integer> activeSlots = getActiveSlots(maxSlots);

        ParcelHolder holder = new ParcelHolder(sender.getUniqueId(), target.getUniqueId(), maxSlots);
        Inventory inv = Bukkit.createInventory(holder, 27, MessageUtil.get("parcel.title", MessageUtil.unparsed("target", target.getName())));

        // Fill background border
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.empty());
        border.setItemMeta(borderMeta);

        for (int i = 0; i < 27; i++) {
            if (!activeSlots.contains(i)) {
                inv.setItem(i, border);
            }
        }

        // Info item in slot 18
        ItemStack info = new ItemStack(Material.BUNDLE);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(MessageUtil.get("parcel.info-button-name"));
        infoMeta.lore(MessageUtil.getList("parcel.info-button-lore",
                MessageUtil.unparsed("player", target.getName()),
                MessageUtil.p("slots", String.valueOf(maxSlots))));
        info.setItemMeta(infoMeta);
        inv.setItem(INFO_SLOT, info);

        // Send button in slot 22
        ItemStack sendBtn = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta sendMeta = sendBtn.getItemMeta();
        sendMeta.displayName(MessageUtil.get("parcel.send-button-name"));
        sendMeta.lore(MessageUtil.getList("parcel.send-button-lore",
                MessageUtil.unparsed("player", target.getName()),
                MessageUtil.p("cooldown", String.valueOf(config.getParcelCooldownSeconds()))));
        sendBtn.setItemMeta(sendMeta);
        inv.setItem(SEND_SLOT, sendBtn);

        sender.openInventory(inv);

        if (config.isParcelSoundEffectsEnabled()) {
            sender.playSound(sender.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ParcelHolder holder)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Intercept if player is downed
        if (plugin.getDownedManager() != null && plugin.getDownedManager().isDowned(player.getUniqueId())) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        // Prevent double click collection pulling items out of border
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        Set<Integer> activeSlots = getActiveSlots(holder.getMaxSlots());

        // Shift click from player's inventory into the parcel GUI
        if (event.isShiftClick() && event.getClickedInventory() != null && !event.getClickedInventory().equals(event.getInventory())) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && !clicked.getType().isAir()) {
                event.setCancelled(true);
                ItemStack toAdd = clicked.clone();
                int remaining = addItemToParcelSlots(event.getInventory(), activeSlots, toAdd);
                if (remaining <= 0) {
                    event.getClickedInventory().setItem(event.getSlot(), null);
                } else if (remaining != clicked.getAmount()) {
                    clicked.setAmount(remaining);
                    event.getClickedInventory().setItem(event.getSlot(), clicked);
                }
            }
            return;
        }

        // Interaction inside the top inventory
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getInventory())) {
            int slot = event.getSlot();

            // Block number-key hotbar swapping into border or control buttons
            if (event.getClick() == ClickType.NUMBER_KEY && !activeSlots.contains(slot)) {
                event.setCancelled(true);
                return;
            }

            // Clicked a non-parcel slot
            if (!activeSlots.contains(slot)) {
                event.setCancelled(true);

                if (slot == SEND_SLOT) {
                    handleSend(player, event.getInventory(), holder, activeSlots);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof ParcelHolder holder)) {
            return;
        }

        if (event.getWhoClicked() instanceof Player player
                && plugin.getDownedManager() != null
                && plugin.getDownedManager().isDowned(player.getUniqueId())) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        Set<Integer> activeSlots = getActiveSlots(holder.getMaxSlots());

        // Cancel drag if any target slot is outside active parcel slots
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < 27 && !activeSlots.contains(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ParcelHolder holder)) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        // If parcel was successfully delivered, do nothing on close
        if (holder.isDelivered()) {
            return;
        }

        Set<Integer> activeSlots = getActiveSlots(holder.getMaxSlots());
        List<ItemStack> itemsToReturn = new ArrayList<>();

        for (int slot : activeSlots) {
            ItemStack item = event.getInventory().getItem(slot);
            if (item != null && !item.getType().isAir()) {
                itemsToReturn.add(item);
                event.getInventory().setItem(slot, null);
            }
        }

        if (!itemsToReturn.isEmpty()) {
            // Safely return items to player
            for (ItemStack item : itemsToReturn) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                if (!overflow.isEmpty()) {
                    for (ItemStack leftover : overflow.values()) {
                        if (leftover != null && !leftover.getType().isAir()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                        }
                    }
                }
            }
            MessageUtil.sendPrefixed(player, "parcel.cancelled");
            if (plugin.getPluginConfig().isParcelSoundEffectsEnabled()) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.0f);
            }
        }
    }

    private void handleSend(Player sender, Inventory inv, ParcelHolder holder, Set<Integer> activeSlots) {
        Player target = Bukkit.getPlayer(holder.getTargetUuid());
        if (!parcelManager.validateDelivery(sender, target, true)) {
            sender.closeInventory();
            return;
        }

        List<ItemStack> items = new ArrayList<>();
        for (int slot : activeSlots) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }

        if (items.isEmpty()) {
            MessageUtil.sendPrefixed(sender, "parcel.empty");
            if (plugin.getPluginConfig().isParcelSoundEffectsEnabled()) {
                sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            }
            return;
        }

        // Mark as delivered to avoid double refund on close
        holder.setDelivered(true);

        // Clear slots
        for (int slot : activeSlots) {
            inv.setItem(slot, null);
        }

        // Close sender inventory
        sender.closeInventory();

        // Deliver
        boolean success = parcelManager.deliverParcel(sender, target, items);
        if (!success) {
            for (ItemStack item : items) {
                Map<Integer, ItemStack> overflow = sender.getInventory().addItem(item);
                if (!overflow.isEmpty()) {
                    for (ItemStack leftover : overflow.values()) {
                        if (leftover != null && !leftover.getType().isAir()) {
                            sender.getWorld().dropItemNaturally(sender.getLocation(), leftover);
                        }
                    }
                }
            }
        }
    }

    private int addItemToParcelSlots(Inventory inv, Set<Integer> activeSlots, ItemStack toAdd) {
        int remaining = toAdd.getAmount();

        // 1. Stack into existing matching items in active slots
        for (int slot : activeSlots) {
            ItemStack existing = inv.getItem(slot);
            if (existing != null && existing.isSimilar(toAdd)) {
                int maxStack = existing.getMaxStackSize();
                int space = maxStack - existing.getAmount();
                if (space > 0) {
                    int add = Math.min(space, remaining);
                    existing.setAmount(existing.getAmount() + add);
                    remaining -= add;
                    if (remaining <= 0) {
                        return 0;
                    }
                }
            }
        }

        // 2. Place into empty active slots
        for (int slot : activeSlots) {
            ItemStack existing = inv.getItem(slot);
            if (existing == null || existing.getType().isAir()) {
                ItemStack placed = toAdd.clone();
                placed.setAmount(remaining);
                inv.setItem(slot, placed);
                return 0;
            }
        }

        return remaining;
    }
}
