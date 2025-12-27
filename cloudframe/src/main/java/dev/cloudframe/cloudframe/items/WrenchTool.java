package dev.cloudframe.cloudframe.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class WrenchTool {

    @SuppressWarnings("deprecation")
	public static ItemStack create() {
        ItemStack item = new ItemStack(Material.IRON_HOE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§eCloud Wrench");
        meta.setLore(java.util.List.of(
            "§7Right-click: Finalize Quarry Frame"
        ));

        // Add this line:
        meta.setCustomModelData(1003);

        item.setItemMeta(meta);
        return item;
    }
}
