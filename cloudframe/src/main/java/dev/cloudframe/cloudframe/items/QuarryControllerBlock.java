package dev.cloudframe.cloudframe.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class QuarryControllerBlock {

    @SuppressWarnings("deprecation")
    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.COPPER_BLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6Quarry Controller");
        meta.setLore(java.util.List.of(
            "§7Place this block on the border of your",
            "§7marked quarry frame, then right-click",
            "§7with the Cloud Wrench to finalize."
        ));

        // Custom model data to distinguish from TubeItem
        meta.setCustomModelData(1004);

        item.setItemMeta(meta);
        return item;
    }
}
