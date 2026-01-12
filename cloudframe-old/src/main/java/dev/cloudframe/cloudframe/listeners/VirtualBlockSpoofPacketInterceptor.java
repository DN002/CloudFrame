package dev.cloudframe.cloudframe.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;

/**
 * Prevents server block updates from overwriting our client-side BARRIER spoof.
 *
 * sendBlockChange() is purely client-side; any subsequent server packet that updates
 * that block (typically AIR) will replace the spoofed state. Right-click actions
 * commonly trigger such updates, which is why the barrier "disappears" on interact.
 */
public final class VirtualBlockSpoofPacketInterceptor {

    private static ProtocolManager protocol;
    private static PacketAdapter adapter;
    private static WrappedBlockData barrierData;

    private VirtualBlockSpoofPacketInterceptor() {}

    public static boolean startIfAvailable(JavaPlugin plugin) {
        if (adapter != null) return true;

        Plugin pl = Bukkit.getPluginManager().getPlugin("ProtocolLib");
        if (pl == null || !pl.isEnabled()) {
            return false;
        }

        try {
            protocol = ProtocolLibrary.getProtocolManager();
            barrierData = WrappedBlockData.createData(Material.BARRIER);
        } catch (Throwable t) {
            protocol = null;
            barrierData = null;
            return false;
        }

        adapter = new PacketAdapter(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.BLOCK_CHANGE) {
            @Override
            public void onPacketSending(PacketEvent event) {
                try {
                    Player player = event.getPlayer();
                    if (player == null || !player.isOnline()) return;

                    World world = player.getWorld();
                    if (world == null) return;

                    BlockPosition pos = event.getPacket().getBlockPositionModifier().read(0);
                    if (pos == null) return;

                    Location loc = new Location(world, pos.getX(), pos.getY(), pos.getZ());

                    // Only protect spoofed virtual blockspaces.
                    if (!ClientSelectionBoxTask.isSpoofedFor(player, loc)) return;

                    // Never mask real blocks.
                    if (!loc.getBlock().getType().isAir()) return;

                    event.getPacket().getBlockData().write(0, barrierData);

                } catch (Throwable ignored) {
                    // Best-effort.
                }
            }
        };

        protocol.addPacketListener(adapter);
        return true;
    }

    public static void stop() {
        try {
            if (protocol != null && adapter != null) {
                protocol.removePacketListener(adapter);
            }
        } catch (Throwable ignored) {
            // Best-effort.
        }

        adapter = null;
        protocol = null;
        barrierData = null;
    }
}
