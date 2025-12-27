package dev.cloudframe.cloudframe.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
import dev.cloudframe.cloudframe.gui.QuarryGUI;
import dev.cloudframe.cloudframe.quarry.Quarry;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class ControllerGuiListener implements Listener {

    private static final Debug debug = DebugManager.get(ControllerGuiListener.class);

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }

        if (e.getClickedInventory() == null) {
            return;
        }

        // Only handle our GUI
        if (!e.getView().getTitle().equals("Quarry Controller")) {
            return;
        }

        debug.log("onInventoryClick", "Player " + p.getName() + " clicked inside Quarry Controller GUI");

        e.setCancelled(true); // Prevent item pickup

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) {
            debug.log("onInventoryClick", "Clicked item invalid or missing metadata");
            return;
        }

        String name = clicked.getItemMeta().getDisplayName();
        debug.log("onInventoryClick", "Clicked item name=" + name);

        // Find the quarry this GUI belongs to
        if (p.getTargetBlockExact(5) == null) {
            debug.log("onInventoryClick", "Player not looking at controller block");
            return;
        }

        Quarry q = CloudFrameRegistry.quarries().getByController(
                p.getTargetBlockExact(5).getLocation()
        );

        if (q == null) {
            debug.log("onInventoryClick", "No quarry found for controller block");
            return;
        }

        // --- Pause Quarry ---
        if (name.contains("Pause")) {
            debug.log("onInventoryClick", "Pausing quarry owner=" + q.getOwner());
            q.setActive(false);
            p.sendMessage("§cQuarry paused.");
            p.openInventory(QuarryGUI.build(q)); // refresh GUI
            return;
        }

        // --- Resume Quarry ---
        if (name.contains("Resume")) {
            debug.log("onInventoryClick", "Resuming quarry owner=" + q.getOwner());
            q.setActive(true);
            p.sendMessage("§aQuarry resumed.");
            p.openInventory(QuarryGUI.build(q)); // refresh GUI
            return;
        }

        // --- Remove Quarry ---
        if (name.contains("Remove")) {
            debug.log("onInventoryClick", "Removing quarry owner=" + q.getOwner());
            CloudFrameRegistry.quarries().remove(q);
            p.sendMessage("§4Quarry removed.");
            p.closeInventory();
            return;
        }

        debug.log("onInventoryClick", "Clicked item did not match any known action");
    }
}
