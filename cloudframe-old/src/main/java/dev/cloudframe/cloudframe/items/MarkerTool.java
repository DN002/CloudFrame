package dev.cloudframe.cloudframe.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MarkerTool {

    @SuppressWarnings("deprecation")
	public static ItemStack create() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§bCloud Marker");
        meta.setLore(java.util.List.of(
            "§7Left-click: Set Position A",
            "§7Right-click: Set Position B"
        ));

        // Add this line:
        meta.setCustomModelData(1002);

        item.setItemMeta(meta);
        return item;
    }
}
