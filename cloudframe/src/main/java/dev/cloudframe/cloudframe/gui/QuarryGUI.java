package dev.cloudframe.cloudframe.gui;

import java.util.ArrayList;
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
        Inventory inv = Bukkit.createInventory(new QuarryHolder(q), 27, "Quarry Controller");

        // --- Decorative Pane ---
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pMeta = pane.getItemMeta();
        pMeta.setDisplayName(" ");
        pane.setItemMeta(pMeta);

        // Fill everything with panes first
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }

        // --- STATUS PANEL ---
        ItemStack status = new ItemStack(Material.REDSTONE_TORCH);
        ItemMeta sMeta = status.getItemMeta();
        sMeta.setDisplayName("§e§lStatus");

        List<String> sLore = new ArrayList<>();

        if (q.isScanningMetadata()) {
            sLore.add("§7State: §eScanning Metadata...");
        } else if (!q.isActive()) {
            sLore.add("§7State: §cPaused");
        } else if (q.isScanning()) {
            sLore.add("§7State: §eScanning Region");
        } else if (q.getProgressPercent() >= 100.0) {
            sLore.add("§7State: §aIdle (Complete)");
        } else {
            sLore.add("§7State: §aMining");
        }

        sLore.add("§7Owner: §f" + Bukkit.getOfflinePlayer(q.getOwner()).getName());
        sLore.add("§7Current Y: §f" + q.getCurrentY());

        sMeta.setLore(sLore);
        status.setItemMeta(sMeta);
        inv.setItem(1, status);

        // --- PROGRESS PANEL ---
        ItemStack progress = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta prMeta = progress.getItemMeta();
        prMeta.setDisplayName("§b§lProgress");

        List<String> prLore = new ArrayList<>();
        prLore.add("§7Progress: §f" + String.format("%.1f", q.getProgressPercent()) + "%");
        prLore.add("§7Mined: §f" + q.getBlocksMined());
        prLore.add("§7Total: §f" + q.getTotalBlocksInRegion());
        prLore.add("§7Remaining: §f" + Math.max(0, q.getTotalBlocksInRegion() - q.getBlocksMined()));

        prMeta.setLore(prLore);
        progress.setItemMeta(prMeta);
        inv.setItem(3, progress);

        // --- METADATA PANEL ---
        ItemStack meta = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta mMeta = meta.getItemMeta();
        mMeta.setDisplayName("§d§lMetadata");

        List<String> mLore = new ArrayList<>();

        if (q.isScanningMetadata()) {
            mLore.add("§7Metadata: §eScanning...");
        } else if (q.isMetadataReady()) {
            mLore.add("§7Metadata: §aReady");
        } else {
            mLore.add("§7Metadata: §cNot Ready");
        }

        if (q.getBlocksPerLayer().length > 0) {
            int layersRemaining = q.countLayersWithBlocks();
            mLore.add("§7Layers Remaining: §f" + layersRemaining);
        }

        mMeta.setLore(mLore);
        meta.setItemMeta(mMeta);
        inv.setItem(5, meta);

        // --- PAUSE / RESUME BUTTON ---
        ItemStack toggle = new ItemStack(Material.LEVER);
        ItemMeta tMeta = toggle.getItemMeta();
        tMeta.setDisplayName(q.isActive() ? "§c§lPause Quarry" : "§a§lResume Quarry");
        toggle.setItemMeta(tMeta);
        inv.setItem(7, toggle);

        // --- REFRESH METADATA BUTTON ---
        ItemStack refresh = new ItemStack(Material.COMPARATOR);
        ItemMeta rfMeta = refresh.getItemMeta();
        rfMeta.setDisplayName("§b§lRefresh Metadata");
        refresh.setItemMeta(rfMeta);
        inv.setItem(10, refresh);

        // --- REMOVE QUARRY BUTTON ---
        ItemStack remove = new ItemStack(Material.BARRIER);
        ItemMeta rMeta = remove.getItemMeta();
        rMeta.setDisplayName("§4§lRemove Quarry");
        remove.setItemMeta(rMeta);
        inv.setItem(13, remove);

        return inv;
    }
}
