package dev.cloudframe.cloudframe.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class SilkTouchAugment {

    private static final int CMD = 1010;

    private SilkTouchAugment() {}

    @SuppressWarnings("deprecation")
    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bSilk Touch Augment");
        meta.setLore(java.util.List.of(
            "§7Install into a Quarry Controller",
            "§7Mines stone/deepslate as-is"
        ));
        meta.setCustomModelData(CMD);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (!item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        if (meta.hasCustomModelData() && meta.getCustomModelData() == CMD) return true;
        return meta.hasDisplayName() && meta.getDisplayName().contains("Silk Touch Augment");
    }
}
