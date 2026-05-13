package com.samarth.duels.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

/**
 * Customize GUI shown when a player right-clicks a kit in DuelSetupHolder.
 * Lets the challenger pick rounds (1/3/5/7) and toggle the match time limit.
 *
 * Slot layout (27-slot chest):
 *   slot 4:  title paper
 *   slot 10–13: round buttons (1, 3, 5, 7)
 *   slot 15: time-limit toggle
 *   slot 18: cancel
 *   slot 22: send
 */
public final class DuelCustomizeHolder implements InventoryHolder {
    public static final int SLOT_ROUNDS_1 = 10;
    public static final int SLOT_ROUNDS_3 = 11;
    public static final int SLOT_ROUNDS_5 = 12;
    public static final int SLOT_ROUNDS_7 = 13;
    public static final int SLOT_TIME_LIMIT = 15;
    public static final int SLOT_CANCEL = 18;
    public static final int SLOT_SEND = 22;

    private final UUID target;
    private final String targetName;
    private final String kitName;
    private final boolean party;
    private int rounds;
    private boolean useTimeLimit;
    @Nullable private Inventory inv;

    public DuelCustomizeHolder(UUID target, String targetName, String kitName, int rounds, boolean useTimeLimit) {
        this(target, targetName, kitName, rounds, useTimeLimit, false);
    }

    public DuelCustomizeHolder(UUID target, String targetName, String kitName,
                               int rounds, boolean useTimeLimit, boolean party) {
        this.target = target;
        this.targetName = targetName;
        this.kitName = kitName;
        this.rounds = rounds;
        this.useTimeLimit = useTimeLimit;
        this.party = party;
    }

    public UUID target() { return target; }
    public String targetName() { return targetName; }
    public String kitName() { return kitName; }
    public int rounds() { return rounds; }
    public boolean useTimeLimit() { return useTimeLimit; }
    public boolean party() { return party; }
    public void setRounds(int v) { this.rounds = v; }
    public void setUseTimeLimit(boolean v) { this.useTimeLimit = v; }

    public Inventory build() {
        String title = (party ? "Party Customize: " : "Customize: ") + kitName;
        this.inv = Bukkit.createInventory(this, 27, Component.text(title, NamedTextColor.AQUA));
        populate();
        return inv;
    }

    public void populate() {
        if (inv == null) return;
        inv.clear();

        ItemStack filler = filler();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(4, titleItem());
        inv.setItem(SLOT_ROUNDS_1, roundButton(1));
        inv.setItem(SLOT_ROUNDS_3, roundButton(3));
        inv.setItem(SLOT_ROUNDS_5, roundButton(5));
        inv.setItem(SLOT_ROUNDS_7, roundButton(7));
        inv.setItem(SLOT_TIME_LIMIT, timeLimitButton());
        inv.setItem(SLOT_CANCEL, cancelButton());
        inv.setItem(SLOT_SEND, sendButton());
    }

    private ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Component.text(" "));
            it.setItemMeta(m);
        }
        return it;
    }

    private ItemStack titleItem() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Component.text(kitName + " vs " + targetName,
                NamedTextColor.AQUA, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
            it.setItemMeta(m);
        }
        return it;
    }

    private ItemStack roundButton(int count) {
        Material mat = switch (count) {
            case 1 -> Material.STONE;
            case 3 -> Material.IRON_BLOCK;
            case 5 -> Material.GOLD_BLOCK;
            case 7 -> Material.DIAMOND_BLOCK;
            default -> Material.STONE;
        };
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            boolean selected = (count == rounds);
            m.displayName(Component.text("First to " + count,
                selected ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(selected ? "Selected" : "Click to select",
                selected ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
            m.lore(lore);
            if (selected) {
                m.addEnchant(Enchantment.UNBREAKING, 1, true);
                m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            it.setItemMeta(m);
        }
        return it;
    }

    private ItemStack timeLimitButton() {
        ItemStack it = new ItemStack(useTimeLimit ? Material.CLOCK : Material.LIGHT_GRAY_DYE);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Component.text("Match time limit: " + (useTimeLimit ? "ON" : "OFF"),
                useTimeLimit ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Click to toggle", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
            if (useTimeLimit) {
                lore.add(Component.text("Match auto-ends after the configured cap.",
                    NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("No time cap — first to N rounds wins.",
                    NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            }
            m.lore(lore);
            it.setItemMeta(m);
        }
        return it;
    }

    private ItemStack sendButton() {
        ItemStack it = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Component.text("Send Challenge",
                NamedTextColor.GREEN, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
            it.setItemMeta(m);
        }
        return it;
    }

    private ItemStack cancelButton() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Component.text("Cancel", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
            it.setItemMeta(m);
        }
        return it;
    }

    @Override
    public Inventory getInventory() { return inv; }
}
