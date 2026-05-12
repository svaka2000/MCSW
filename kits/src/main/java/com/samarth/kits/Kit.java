package com.samarth.kits;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Immutable snapshot of a player's loadout, identified by name. */
public final class Kit {
    private final String name;
    private final ItemStack[] inventory;
    private final ItemStack[] armor;
    @Nullable private final ItemStack offhand;

    public Kit(String name, ItemStack[] inventory, ItemStack[] armor, @Nullable ItemStack offhand) {
        this.name = name;
        this.inventory = inventory;
        this.armor = armor;
        this.offhand = offhand;
    }

    public String name() { return name; }
    public ItemStack[] inventory() { return inventory.clone(); }
    public ItemStack[] armor() { return armor.clone(); }
    public @Nullable ItemStack offhand() { return offhand == null ? null : offhand.clone(); }
}
