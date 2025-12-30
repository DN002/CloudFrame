package dev.cloudframe.cloudframe.listeners;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.Registry;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.WrappedDataWatcherObject;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;

/**
 * Hover outline using ProtocolLib: applies a client-side glowing flag to the tube/controller
 * entities you're looking at.
 *
 * This avoids particle spam and looks closer to vanilla entity highlighting.
 */
public final class ProtocolHoverOutlineTask {

    private static BukkitTask task;

    private static final double MAX_DISTANCE = 6.0;
    private static final long PERIOD_TICKS = 2L;

    private static ProtocolManager protocol;

    // Per player: currently-glowing entities
    private static final Map<UUID, Set<UUID>> glowingByPlayer = new HashMap<>();

    private ProtocolHoverOutlineTask() {}

    public static boolean startIfAvailable(JavaPlugin plugin) {
        if (task != null) return true;

        Plugin pl = Bukkit.getPluginManager().getPlugin("ProtocolLib");
        if (pl == null || !pl.isEnabled()) {
            return false;
        }

        try {
            protocol = ProtocolLibrary.getProtocolManager();
        } catch (Throwable t) {
            return false;
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, ProtocolHoverOutlineTask::tick, 1L, PERIOD_TICKS);
        return true;
    }

    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        // Best-effort: clear any lingering glows for online players
        if (protocol != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                clearGlow(p);
            }
        }

        glowingByPlayer.clear();
        protocol = null;
    }

    private static void tick() {
        if (protocol == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;

            RayTraceResult rr = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                MAX_DISTANCE,
                0.2,
                ProtocolHoverOutlineTask::isHighlightableEntity
            );

            if (rr == null || rr.getHitEntity() == null) {
                clearGlow(player);
                continue;
            }

            Entity hit = rr.getHitEntity();
            PersistentDataContainer pdc = hit.getPersistentDataContainer();

            Location tubeLoc = null;
            if (CloudFrameRegistry.tubes().visualsManager() != null) {
                tubeLoc = CloudFrameRegistry.tubes().visualsManager().getTaggedTubeLocation(pdc);
            }

            Location controllerLoc = null;
            if (tubeLoc == null && CloudFrameRegistry.quarries().visualsManager() != null) {
                controllerLoc = CloudFrameRegistry.quarries().visualsManager().getTaggedControllerLocation(pdc);
            }

            if (tubeLoc == null && controllerLoc == null) {
                clearGlow(player);
                continue;
            }

            Location base = tubeLoc != null ? tubeLoc : controllerLoc;
            Set<Entity> toGlow = collectHighlightEntitiesForBase(base);

            applyGlowSet(player, toGlow);
        }
    }

    private static boolean isHighlightableEntity(Entity e) {
        if (e == null) return false;
        // Only highlight the Interaction hitbox so the glow outline resembles the
        // vanilla block selection wireframe.
        if (!(e instanceof Interaction)) return false;
        PersistentDataContainer pdc = e.getPersistentDataContainer();

        if (CloudFrameRegistry.tubes().visualsManager() != null && CloudFrameRegistry.tubes().visualsManager().getTaggedTubeLocation(pdc) != null) {
            return true;
        }
        if (CloudFrameRegistry.quarries().visualsManager() != null && CloudFrameRegistry.quarries().visualsManager().getTaggedControllerLocation(pdc) != null) {
            return true;
        }
        return false;
    }

    private static Set<Entity> collectHighlightEntitiesForBase(Location baseBlockLoc) {
        Set<Entity> out = new HashSet<>();
        if (baseBlockLoc == null || baseBlockLoc.getWorld() == null) return out;

        Location center = baseBlockLoc.clone().add(0.5, 0.5, 0.5);
        for (Entity e : baseBlockLoc.getWorld().getNearbyEntities(center, 1.25, 1.25, 1.25)) {
            if (!(e instanceof Interaction)) continue;
            PersistentDataContainer pdc = e.getPersistentDataContainer();

            Location t = null;
            if (CloudFrameRegistry.tubes().visualsManager() != null) {
                t = CloudFrameRegistry.tubes().visualsManager().getTaggedTubeLocation(pdc);
            }
            if (t != null && sameBlock(t, baseBlockLoc)) {
                out.add(e);
                continue;
            }

            Location c = null;
            if (CloudFrameRegistry.quarries().visualsManager() != null) {
                c = CloudFrameRegistry.quarries().visualsManager().getTaggedControllerLocation(pdc);
            }
            if (c != null && sameBlock(c, baseBlockLoc)) {
                out.add(e);
            }
        }

        return out;
    }

    private static boolean sameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || b.getWorld() == null) return false;
        if (!a.getWorld().equals(b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    private static void applyGlowSet(Player player, Set<Entity> desired) {
        Set<UUID> current = glowingByPlayer.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());

        Set<UUID> desiredUuids = new HashSet<>();
        for (Entity e : desired) {
            desiredUuids.add(e.getUniqueId());
        }

        // Turn off entities no longer desired
        for (UUID entityUuid : new HashSet<>(current)) {
            if (!desiredUuids.contains(entityUuid)) {
                Entity e = Bukkit.getEntity(entityUuid);
                if (e != null) {
                    sendGlow(player, e, false);
                }
                current.remove(entityUuid);
            }
        }

        // Turn on new entities
        for (Entity e : desired) {
            UUID entityUuid = e.getUniqueId();
            if (!current.contains(entityUuid)) {
                sendGlow(player, e, true);
                current.add(entityUuid);
            }
        }

        if (current.isEmpty()) {
            glowingByPlayer.remove(player.getUniqueId());
        }
    }

    private static void clearGlow(Player player) {
        Set<UUID> current = glowingByPlayer.remove(player.getUniqueId());
        if (current == null || current.isEmpty()) return;
        for (UUID entityUuid : current) {
            Entity e = Bukkit.getEntity(entityUuid);
            if (e != null) {
                sendGlow(player, e, false);
            }
        }
    }

    private static void sendGlow(Player player, Entity entity, boolean glowing) {
        if (entity == null) return;

        try {
            byte flags = 0;
            try {
                WrappedDataWatcher watcher = WrappedDataWatcher.getEntityWatcher(entity);
                Byte cur = watcher.getByte(0);
                flags = (cur == null) ? 0 : cur;
            } catch (Throwable ignored) {
                // If we can't read current flags, we still can set glowing flag assuming 0.
            }

            byte newFlags;
            if (glowing) {
                newFlags = (byte) (flags | 0x40);
            } else {
                newFlags = (byte) (flags & ~0x40);
            }

            PacketContainer packet = protocol.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, entity.getEntityId());

            // 1.21+ expects SynchedEntityData.DataValue rather than DataItem.
            // ProtocolLib 5.4+ exposes getDataValueCollectionModifier() + WrappedDataValue.
            if (!tryWriteAsDataValues(packet, newFlags)) {
                WrappedDataWatcher out = new WrappedDataWatcher();
                WrappedDataWatcherObject obj0 = new WrappedDataWatcherObject(0, Registry.get(Byte.class));
                out.setObject(obj0, newFlags);
                packet.getWatchableCollectionModifier().write(0, out.getWatchableObjects());
            }
            protocol.sendServerPacket(player, packet);
        } catch (Throwable t) {
            // ignore
        }
    }

    private static boolean tryWriteAsDataValues(PacketContainer packet, byte flags) {
        try {
            Method getDataValueCollectionModifier = PacketContainer.class.getMethod("getDataValueCollectionModifier");
            Object modifier = getDataValueCollectionModifier.invoke(packet);

            Class<?> wrappedDataValueClass = Class.forName("com.comphenix.protocol.wrappers.WrappedDataValue");
            Object serializer = Registry.get(Byte.class);

            Object wrappedDataValue = null;
            for (Constructor<?> ctor : wrappedDataValueClass.getConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length == 3 && p[0] == int.class) {
                    wrappedDataValue = ctor.newInstance(0, serializer, Byte.valueOf(flags));
                    break;
                }
            }
            if (wrappedDataValue == null) return false;

            List<Object> values = new ArrayList<>();
            values.add(wrappedDataValue);

            Method write = modifier.getClass().getMethod("write", int.class, Object.class);
            write.invoke(modifier, 0, values);
            return true;
        } catch (NoSuchMethodException e) {
            // Older ProtocolLib: no data value modifier
            return false;
        } catch (Throwable t) {
            // If this path exists but failed, do NOT fall back to the old DataItem format on 1.21+.
            return true;
        }
    }
}
