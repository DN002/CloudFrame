package dev.cloudframe.cloudframe.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class SpeedAugment {

    private static final int CMD_I = 1011;
    private static final int CMD_II = 1012;
    private static final int CMD_III = 1013;

    private SpeedAugment() {}

    @SuppressWarnings("deprecation")
    public static ItemStack create() {
        return create(1);
    }

    @SuppressWarnings("deprecation")
    public static ItemStack create(int tier) {
        int t = Math.max(1, Math.min(3, tier));
        ItemStack item = new ItemStack(Material.SUGAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§aSpeed Augment " + roman(t));
        meta.setLore(java.util.List.of(
            "§7Install into a Quarry Controller",
            "§7Increases mining speed"
        ));
        meta.setCustomModelData(cmdForTier(t));
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (!item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        if (meta.hasCustomModelData()) {
            int cmd = meta.getCustomModelData();
            if (cmd == CMD_I || cmd == CMD_II || cmd == CMD_III) return true;
        }
        return meta.hasDisplayName() && meta.getDisplayName().contains("Speed Augment");
    }

    public static int getTier(ItemStack item) {
        if (!isItem(item)) return 0;
        var meta = item.getItemMeta();
        if (meta != null && meta.hasCustomModelData()) {
            int cmd = meta.getCustomModelData();
            if (cmd == CMD_I) return 1;
            if (cmd == CMD_II) return 2;
            if (cmd == CMD_III) return 3;
        }

        // Fallback: parse name.
        String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
        if (name.contains("III")) return 3;
        if (name.contains("II")) return 2;
        if (name.contains("I")) return 1;
        return 1;
    }

    private static int cmdForTier(int tier) {
        return switch (tier) {
            case 2 -> CMD_II;
            case 3 -> CMD_III;
            default -> CMD_I;
        };
    }

    private static String roman(int tier) {
        return switch (tier) {
            case 2 -> "II";
            case 3 -> "III";
            default -> "I";
        };
    }
}
