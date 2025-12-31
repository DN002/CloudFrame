package dev.cloudframe.cloudframe.gui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.cloudframe.cloudframe.items.SilkTouchAugment;
import dev.cloudframe.cloudframe.items.SpeedAugment;
import dev.cloudframe.cloudframe.util.CustomBlocks;

public final class UnfinalizedControllerGUI {

    private UnfinalizedControllerGUI() {}

    @SuppressWarnings("deprecation")
    public static Inventory build(Location controllerLoc, CustomBlocks.StoredAugments stored) {
        Inventory inv = Bukkit.createInventory(new UnfinalizedControllerHolder(controllerLoc), 27, "Quarry Controller (Unfinalized)");

        // Decorative pane
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pMeta = pane.getItemMeta();
        pMeta.setDisplayName(" ");
        pane.setItemMeta(pMeta);

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }

        // Info panel
        ItemStack info = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta iMeta = info.getItemMeta();
        iMeta.setDisplayName("§e§lUnfinalized Controller");
        List<String> lore = new ArrayList<>();
        lore.add("§7This controller is not finalized yet.");
        lore.add("§7Use the §6Cloud Wrench§7 on it");
        lore.add("§7to finalize once placed by a frame.");
        lore.add(" ");
        lore.add("§7Stored augments carry over into");
        lore.add("§7the controller item when picked up.");
        iMeta.setLore(lore);
        info.setItemMeta(iMeta);
        inv.setItem(1, info);

        // Augments
        boolean silk = stored != null && stored.silkTouch();
        int speed = stored != null ? Math.max(0, stored.speedLevel()) : 0;

        ItemStack silkItem = SilkTouchAugment.create();
        ItemMeta sMeta = silkItem.getItemMeta();
        sMeta.setLore(List.of(
            silk ? "§aStored" : "§cNot stored",
            "§7Add: §fRight-click controller",
            "§7     §fwith augment in hand",
            "§7Remove: §fClick this slot"
        ));
        silkItem.setItemMeta(sMeta);
        inv.setItem(15, silkItem);

        int displayTier = speed > 0 ? speed : 1;
        ItemStack speedItem = SpeedAugment.create(displayTier);
        ItemMeta spMeta = speedItem.getItemMeta();
        spMeta.setLore(List.of(
            speed > 0 ? ("§aStored (" + toRoman(speed) + ")") : "§cNot stored",
            "§7Add: §fRight-click controller",
            "§7     §fwith augment in hand",
            "§7Remove: §fClick this slot"
        ));
        speedItem.setItemMeta(spMeta);
        inv.setItem(16, speedItem);

        return inv;
    }

    private static String toRoman(int tier) {
        return switch (tier) {
            case 2 -> "II";
            case 3 -> "III";
            default -> "I";
        };
    }
}
