package dev.cloudframe.cloudframe.listeners;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;

/**
 * Prevents players from placing real blocks into the same blockspace occupied by
 * entity-only tubes/controllers.
 */
public final class OccupiedBlockSpaceListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e == null) return;
        if (e.getBlockPlaced() == null) return;
        Location loc = e.getBlockPlaced().getLocation();
        if (isOccupied(loc)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMultiPlace(BlockMultiPlaceEvent e) {
        if (e == null) return;

        // Doors/beds/etc can place multiple blocks. Cancel if any target spot is occupied.
        for (var state : e.getReplacedBlockStates()) {
            if (state == null) continue;
            if (isOccupied(state.getLocation())) {
                e.setCancelled(true);
                return;
            }
        }

        if (e.getBlockPlaced() != null && isOccupied(e.getBlockPlaced().getLocation())) {
            e.setCancelled(true);
        }
    }

    private static boolean isOccupied(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        // Tubes occupy their block coordinate.
        if (CloudFrameRegistry.tubes() != null && CloudFrameRegistry.tubes().getTube(loc) != null) {
            return true;
        }

        // Controllers (registered + unregistered) occupy their block coordinate.
        return CloudFrameRegistry.quarries() != null && CloudFrameRegistry.quarries().hasControllerAt(loc);
    }
}
