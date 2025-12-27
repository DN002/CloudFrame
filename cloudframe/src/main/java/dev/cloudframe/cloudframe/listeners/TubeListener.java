package dev.cloudframe.cloudframe.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class TubeListener implements Listener {

    private static final Debug debug = DebugManager.get(TubeListener.class);

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!isTubeItem(e.getItemInHand())) {
            return;
        }

        Location loc = e.getBlock().getLocation();

        debug.log("onPlace", "Player " + e.getPlayer().getName() +
                " placed tube at " + loc);

        CloudFrameRegistry.tubes().addTube(loc);

        // Visual feedback
        pulseTube(loc);

        e.getPlayer().sendMessage("§bTube placed.");
    }

    @SuppressWarnings("deprecation")
    private boolean isTubeItem(org.bukkit.inventory.ItemStack item) {
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasDisplayName()) return false;

        boolean isTube = item.getItemMeta().getDisplayName().contains("Cloud Tube");

        if (isTube) {
            debug.log("isTubeItem", "Detected Cloud Tube item");
        }

        return isTube;
    }

    @EventHandler
    public void onBreak(org.bukkit.event.block.BlockBreakEvent e) {
        Location loc = e.getBlock().getLocation();

        // Optional: ensure it's visually a tube block
        if (e.getBlock().getType() != Material.COPPER_BLOCK) {
            debug.log("onBreak", "Ignored break at " + loc + " (not a copper block)");
            return;
        }

        if (CloudFrameRegistry.tubes().getTube(loc) != null) {
            debug.log("onBreak", "Player " + e.getPlayer().getName() +
                    " removed tube at " + loc);

            CloudFrameRegistry.tubes().removeTube(loc);
            e.getPlayer().sendMessage("§cTube removed.");
        } else {
            debug.log("onBreak", "Copper block broken at " + loc +
                    " but no tube registered there");
        }
    }

    private void pulseTube(Location loc) {
        debug.log("pulseTube", "Pulsing tube visuals at " + loc);

        var world = loc.getWorld();

        // Pulse at the placed tube
        world.spawnParticle(
            org.bukkit.Particle.END_ROD,
            loc.clone().add(0.5, 0.5, 0.5),
            10, 0.2, 0.2, 0.2, 0.01
        );

        // Pulse along neighbors
        for (var neighbor : CloudFrameRegistry.tubes().getNeighborsOf(loc)) {
            Location nLoc = neighbor.getLocation().clone().add(0.5, 0.5, 0.5);

            debug.log("pulseTube", "Pulsing neighbor tube at " + nLoc);

            world.spawnParticle(
                org.bukkit.Particle.END_ROD,
                nLoc,
                10, 0.2, 0.2, 0.2, 0.01
            );
        }
    }
}
