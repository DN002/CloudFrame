package dev.cloudframe.cloudframe.tubes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import dev.cloudframe.cloudframe.util.InventoryUtil;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class ItemPacketManager {

    private static final Debug debug = DebugManager.get(ItemPacketManager.class);

    private final List<ItemPacket> packets = new ArrayList<>();

    // Shared 6-direction vectors (same as TubeNetworkManager)
    private static final Vector[] DIRS = {
        new Vector(1,0,0),
        new Vector(-1,0,0),
        new Vector(0,1,0),
        new Vector(0,-1,0),
        new Vector(0,0,1),
        new Vector(0,0,-1)
    };

    public void add(ItemPacket packet) {
        packets.add(packet);
        debug.log("add", "Added packet for item=" + packet.getItem().getType() +
                " pathLength=" + packet.getPath().size());
    }

    public void tick(boolean shouldLog) {

        if (shouldLog) {
            debug.log("tick", "Ticking " + packets.size() + " packets");
        }

        Iterator<ItemPacket> it = packets.iterator();

        while (it.hasNext()) {
            ItemPacket p = it.next();

            try {
                boolean finished = p.tick(shouldLog);

                if (shouldLog) {
                    debug.log("tick", "Packet tick item=" + p.getItem().getType() +
                            " progress=" + p.getProgress() +
                            " finished=" + finished);
                }

                if (finished) {
                    if (shouldLog) {
                        debug.log("tick", "Packet finished, delivering item=" + p.getItem().getType());
                    }
                    deliver(p, shouldLog);
                    p.destroy();
                    it.remove();
                }

            } catch (Exception ex) {
                debug.log("tick", "Exception ticking packet: " + ex.getMessage());
                ex.printStackTrace();
                it.remove();
            }
        }
    }

    private void deliver(ItemPacket p, boolean shouldLog) {
        TubeNode destNode = p.getDestination();
        Location loc = destNode.getLocation();

        if (shouldLog) {
            debug.log("deliver", "Delivering item=" + p.getItem().getType() +
                    " to tube at " + loc);
        }

        // Ensure destination chunk is loaded
        if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            if (shouldLog) {
                debug.log("deliver", "Chunk not loaded — dropping item safely at " + loc);
            }

            loc.getWorld().dropItemNaturally(
                loc.clone().add(0.5, 1, 0.5),
                p.getItem()
            );
            return;
        }

        // Check all 6 sides for an inventory
        for (Vector v : DIRS) {
            Location adj = loc.clone().add(v);
            Block block = adj.getBlock();

            var inv = InventoryUtil.getInventory(block);
            if (inv != null) {
                if (shouldLog) {
                    debug.log("deliver", "Delivered item=" + p.getItem().getType() +
                            " into inventory at " + adj);
                }
                inv.getInventory().addItem(p.getItem());
                return;
            }
        }

        // Fallback: drop item
        if (shouldLog) {
            debug.log("deliver", "No inventory found — dropping item at " + loc);
        }

        loc.getWorld().dropItemNaturally(
            loc.clone().add(0.5, 1, 0.5),
            p.getItem()
        );
    }
}
