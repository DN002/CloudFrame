package dev.cloudframe.cloudframe.gui;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.cloudframe.cloudframe.quarry.Quarry;

public class QuarryGUI {

	@SuppressWarnings("deprecation")
	public static Inventory build(Quarry q) {
	    Inventory inv = Bukkit.createInventory(null, 27, "Quarry Controller");

	    // --- Status Item ---
	    ItemStack info = new ItemStack(Material.PAPER);
	    ItemMeta meta = info.getItemMeta();
	    meta.setDisplayName("§bQuarry Status");
	    meta.setLore(List.of(
	        "§7Owner: §f" + q.getOwner(),
	        "§7Active: §f" + q.isActive(),
	        "§7Current Y: §f" + q.getCurrentY(),
	        "§7Progress: §f" + String.format("%.1f", q.getProgressPercent()) + "%"
	    ));
	    info.setItemMeta(meta);
	    inv.setItem(13, info);

	    // --- Pause / Resume Button ---
	    ItemStack toggle = new ItemStack(Material.LEVER);
	    ItemMeta tMeta = toggle.getItemMeta();
	    tMeta.setDisplayName(q.isActive() ? "§cPause Quarry" : "§aResume Quarry");
	    toggle.setItemMeta(tMeta);
	    inv.setItem(11, toggle);

	    // --- Remove Quarry Button ---
	    ItemStack remove = new ItemStack(Material.BARRIER);
	    ItemMeta rMeta = remove.getItemMeta();
	    rMeta.setDisplayName("§4Remove Quarry");
	    remove.setItemMeta(rMeta);
	    inv.setItem(15, remove);

	    return inv;
	}

}
