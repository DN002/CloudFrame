package dev.cloudframe.cloudframe.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TubeItem {

    @SuppressWarnings("deprecation")
	public static ItemStack create() {
        ItemStack item = new ItemStack(Material.COPPER_BLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§bCloud Tube");
        meta.setLore(java.util.List.of(
            "§7Place to create a transport tube."
        ));

        // Add this line:
        meta.setCustomModelData(1001);

        item.setItemMeta(meta);
        return item;
    }
}
