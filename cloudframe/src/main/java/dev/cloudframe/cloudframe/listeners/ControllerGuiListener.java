package dev.cloudframe.cloudframe.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import dev.cloudframe.cloudframe.gui.QuarryGUI;
import dev.cloudframe.cloudframe.gui.QuarryHolder;
import dev.cloudframe.cloudframe.quarry.Quarry;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class ControllerGuiListener implements Listener {

    private static final Debug debug = DebugManager.get(ControllerGuiListener.class);

    @SuppressWarnings("deprecation")
	@EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null) return;

        // Only handle our GUI
        if (!e.getView().getTitle().equals("Quarry Controller")) return;

        e.setCancelled(true);

        // Retrieve quarry from GUI holder
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (!(holder instanceof QuarryHolder qh)) {
            debug.log("onInventoryClick", "Inventory holder is not QuarryHolder");
            return;
        }

        Quarry q = qh.getQuarry();
        if (q == null) {
            debug.log("onInventoryClick", "QuarryHolder returned null quarry");
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) {
            return;
        }

        String name = clicked.getItemMeta().getDisplayName();
        debug.log("onInventoryClick", "Clicked item name=" + name);

        // --- Pause Quarry ---
        if (name.contains("Pause")) {
            q.setActive(false);
            p.sendMessage("§cQuarry paused.");
            p.openInventory(QuarryGUI.build(q));
            return;
        }

        // --- Resume Quarry ---
        if (name.contains("Resume")) {
            q.setActive(true);
            p.sendMessage("§aQuarry resumed.");
            p.openInventory(QuarryGUI.build(q));
            return;
        }

        // --- Refresh Metadata ---
        if (name.contains("Refresh Metadata")) {
            q.startMetadataScan();
            p.sendMessage("§bRefreshing metadata...");
            p.openInventory(QuarryGUI.build(q));
            return;
        }

        // --- Remove Quarry ---
        if (name.contains("Remove")) {
            p.sendMessage("§4Quarry removed.");
            dev.cloudframe.cloudframe.core.CloudFrameRegistry.quarries().remove(q);
            p.closeInventory();
            return;
        }
    }
}
