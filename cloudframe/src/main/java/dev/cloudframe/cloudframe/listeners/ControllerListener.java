package dev.cloudframe.cloudframe.listeners;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
import dev.cloudframe.cloudframe.gui.QuarryGUI;
import dev.cloudframe.cloudframe.quarry.Quarry;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class ControllerListener implements Listener {

    private static final Debug debug = DebugManager.get(ControllerListener.class);

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) {
            return;
        }

        Location loc = e.getClickedBlock().getLocation();
        debug.log("onInteract", "Player " + e.getPlayer().getName() +
                " interacted at " + loc);

        for (Quarry q : CloudFrameRegistry.quarries().all()) {
            if (q.getController().equals(loc)) {

                debug.log("onInteract", "Controller matched for quarry owner=" +
                        q.getOwner() + " at " + loc);

                e.getPlayer().openInventory(QuarryGUI.build(q));
                e.setCancelled(true);

                debug.log("onInteract", "Opened Quarry GUI for player " +
                        e.getPlayer().getName());

                return;
            }
        }

        debug.log("onInteract", "No controller found at " + loc);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Location loc = e.getBlock().getLocation();

        debug.log("onBreak", "Player " + e.getPlayer().getName() +
                " broke block at " + loc);

        for (Quarry q : CloudFrameRegistry.quarries().all()) {
            if (q.getController().equals(loc)) {

                debug.log("onBreak", "Controller block belongs to quarry owner=" +
                        q.getOwner());

                // Optional: prevent breaking unless sneaking
                if (!e.getPlayer().isSneaking()) {
                    debug.log("onBreak", "Player not sneaking — preventing quarry removal");
                    e.getPlayer().sendMessage("§cSneak + break to remove the quarry.");
                    e.setCancelled(true);
                    return;
                }

                debug.log("onBreak", "Removing quarry for owner=" + q.getOwner());
                CloudFrameRegistry.quarries().remove(q);

                e.getPlayer().sendMessage("§cQuarry removed.");
                return;
            }
        }

        debug.log("onBreak", "Broken block is not a quarry controller");
    }
}
