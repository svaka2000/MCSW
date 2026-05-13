package com.samarth.duels.ui;

import com.samarth.kits.KitService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

/**
 * Kit picker shown when a player runs /duel &lt;target&gt;.
 * Left-click = send challenge with config defaults; right-click = customize GUI.
 */
public final class DuelSetupHolder implements InventoryHolder {
    private final UUID target;
    private final String targetName;
    @Nullable private Inventory inv;

    public DuelSetupHolder(UUID target, String targetName) {
        this.target = target;
        this.targetName = targetName;
    }

    public UUID target() { return target; }
    public String targetName() { return targetName; }

    public Inventory build(KitService kits) {
        List<String> names = kits.names();
        int rows = Math.max(1, Math.min(6, (names.size() + 8) / 9));
        this.inv = Bukkit.createInventory(this, rows * 9,
            Component.text("Duel " + targetName, NamedTextColor.AQUA));
        for (int i = 0; i < names.size() && i < rows * 9; i++) {
            inv.setItem(i, kitIcon(names.get(i)));
        }
        return inv;
    }

    private ItemStack kitIcon(String name) {
        ItemStack it = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Left-click: send challenge", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Right-click: customize", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    @Override
    public Inventory getInventory() { return inv; }
}
