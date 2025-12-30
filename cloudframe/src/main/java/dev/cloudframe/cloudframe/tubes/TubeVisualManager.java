package dev.cloudframe.cloudframe.tubes;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
import dev.cloudframe.cloudframe.util.InventoryUtil;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

/**
 * BuildCraft-style tube visuals (Option 2):
 * - NO world blocks are placed (no collision)
 * - One Interaction entity provides a clickable/hittable hitbox
 * - One or more ItemDisplay entities render a small tube center + connection arms
 */
public final class TubeVisualManager {

    private static final Debug debug = DebugManager.get(TubeVisualManager.class);


    // CustomModelData values for the visual parts (defined in resource pack via copper_block.json)
    private static final int CMD_CENTER = 2001;
    private static final int CMD_CENTER_CONNECTED = 2008;
    private static final int CMD_ARM_EAST = 2002;
    private static final int CMD_ARM_WEST = 2003;
    private static final int CMD_ARM_UP = 2004;
    private static final int CMD_ARM_DOWN = 2005;
    private static final int CMD_ARM_SOUTH = 2006;
    private static final int CMD_ARM_NORTH = 2007;

    private static final int CMD_CAP_EAST = 2009;
    private static final int CMD_CAP_WEST = 2010;
    private static final int CMD_CAP_UP = 2011;
    private static final int CMD_CAP_DOWN = 2012;
    private static final int CMD_CAP_SOUTH = 2013;
    private static final int CMD_CAP_NORTH = 2014;

    public static final String PDC_TAG_KEY = "cloudframe_tube_entity";
    private static final String PDC_X = "cloudframe_tube_x";
    private static final String PDC_Y = "cloudframe_tube_y";
    private static final String PDC_Z = "cloudframe_tube_z";
    private static final String PDC_WORLD = "cloudframe_tube_world"; // UUID string
    private static final String PDC_PART = "cloudframe_tube_part";


    enum Part {
        CENTER,
        CENTER_CONNECTED,
        ARM_EAST,
        ARM_WEST,
        ARM_UP,
        ARM_DOWN,
        ARM_SOUTH,
        ARM_NORTH,
        CAP_EAST,
        CAP_WEST,
        CAP_UP,
        CAP_DOWN,
        CAP_SOUTH,
        CAP_NORTH
    }

    private static int customModelDataFor(Part part) {
        return switch (part) {
            case CENTER -> CMD_CENTER;
            case CENTER_CONNECTED -> CMD_CENTER_CONNECTED;
            // ItemDisplay rendering of block-based models is yaw-flipped on some server/client
            // combinations. Swap horizontal directions so visuals match world directions.
            case ARM_EAST -> CMD_ARM_WEST;
            case ARM_WEST -> CMD_ARM_EAST;
            case ARM_UP -> CMD_ARM_UP;
            case ARM_DOWN -> CMD_ARM_DOWN;
            case ARM_SOUTH -> CMD_ARM_NORTH;
            case ARM_NORTH -> CMD_ARM_SOUTH;
            case CAP_EAST -> CMD_CAP_WEST;
            case CAP_WEST -> CMD_CAP_EAST;
            case CAP_UP -> CMD_CAP_UP;
            case CAP_DOWN -> CMD_CAP_DOWN;
            case CAP_SOUTH -> CMD_CAP_NORTH;
            case CAP_NORTH -> CMD_CAP_SOUTH;
        };
    }

    private final TubeNetworkManager tubeManager;
    private final NamespacedKey tagKey;
    private final NamespacedKey legacyTagKey;
    private final NamespacedKey xKey;
    private final NamespacedKey yKey;
    private final NamespacedKey zKey;
    private final NamespacedKey worldKey;
    private final NamespacedKey partKey;

    // Tube block location -> part -> entity UUID
    private final Map<Location, EnumMap<Part, java.util.UUID>> entitiesByTube = new HashMap<>();

    // Tube block location -> interaction entity UUID
    private final Map<Location, java.util.UUID> interactionByTube = new HashMap<>();

    public TubeVisualManager(TubeNetworkManager tubeManager, JavaPlugin plugin) {
        this.tubeManager = Objects.requireNonNull(tubeManager, "tubeManager");
        this.tagKey = new NamespacedKey(plugin, PDC_TAG_KEY);
        this.legacyTagKey = new NamespacedKey(plugin, "cloudframe_tube_display");
        this.xKey = new NamespacedKey(plugin, PDC_X);
        this.yKey = new NamespacedKey(plugin, PDC_Y);
        this.zKey = new NamespacedKey(plugin, PDC_Z);
        this.worldKey = new NamespacedKey(plugin, PDC_WORLD);
        this.partKey = new NamespacedKey(plugin, PDC_PART);
    }

    public void shutdown() {
        debug.log("shutdown", "Shutting down tube visuals; trackedTubes=" + entitiesByTube.size() + " interactions=" + interactionByTube.size());
        // Remove all tracked entities
        for (EnumMap<Part, java.util.UUID> map : entitiesByTube.values()) {
            for (var id : map.values()) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) e.remove();
            }
        }
        for (var id : interactionByTube.values()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        entitiesByTube.clear();
        interactionByTube.clear();
    }

    public void cleanupChunkDisplays(Chunk chunk) {
        int removed = 0;
        for (Entity e : chunk.getEntities()) {
            if (isTagged(e.getPersistentDataContainer())) {
                e.remove();
                removed++;
            }
        }

        if (removed > 0) {
            debug.log(
                "cleanupChunkDisplays",
                "Removed " + removed + " tagged tube entities in chunk " + chunk.getWorld().getName() + " " + chunk.getX() + "," + chunk.getZ()
            );
        }

        // Drop any stale UUIDs for tubes in this chunk
        entitiesByTube.entrySet().removeIf(en -> {
            Location loc = en.getKey();
            if (loc == null || loc.getWorld() == null) return true;
            if (!loc.getWorld().equals(chunk.getWorld())) return false;
            return (loc.getBlockX() >> 4) == chunk.getX() && (loc.getBlockZ() >> 4) == chunk.getZ();
        });

        interactionByTube.entrySet().removeIf(en -> {
            Location loc = en.getKey();
            if (loc == null || loc.getWorld() == null) return true;
            if (!loc.getWorld().equals(chunk.getWorld())) return false;
            return (loc.getBlockX() >> 4) == chunk.getX() && (loc.getBlockZ() >> 4) == chunk.getZ();
        });
    }

    public void updateTubeAndNeighbors(Location tubeLoc) {
        tubeLoc = norm(tubeLoc);
        updateTube(tubeLoc);

        for (var dir : TubeNetworkManager.DIRS) {
            Location adj = tubeLoc.clone().add(dir);
            if (tubeManager.getTube(adj) != null) {
                updateTube(adj);
            }
        }
    }

    public void updateTube(Location tubeLoc) {
        tubeLoc = norm(tubeLoc);

        if (debug != null) {
            debug.log("updateTube", "Updating tube visuals at " + tubeLoc);
        }

        if (tubeManager.getTube(tubeLoc) == null) {
            removeTubeDisplays(tubeLoc);
            return;
        }

        if (tubeLoc.getWorld() == null || !tubeLoc.getChunk().isLoaded()) {
            debug.log("updateTube", "Skipping visuals (world/chunk not loaded) at " + tubeLoc);
            return;
        }

        // Migration cleanup: older versions used a pink-stained-glass anchor block.
        // Option 2 is entity-only, so remove that leftover block if present.
        var block = tubeLoc.getBlock();
        if (block.getType() == Material.PINK_STAINED_GLASS) {
            block.setType(Material.AIR, false);
        }

        ensureInteraction(tubeLoc);

        boolean connE = isConnectable(tubeLoc.clone().add(1, 0, 0));
        boolean connW = isConnectable(tubeLoc.clone().add(-1, 0, 0));
        boolean connU = isConnectable(tubeLoc.clone().add(0, 1, 0));
        boolean connD = isConnectable(tubeLoc.clone().add(0, -1, 0));
        boolean connS = isConnectable(tubeLoc.clone().add(0, 0, 1));
        boolean connN = isConnectable(tubeLoc.clone().add(0, 0, -1));

        int connectionCount = 0;
        if (connE) connectionCount++;
        if (connW) connectionCount++;
        if (connU) connectionCount++;
        if (connD) connectionCount++;
        if (connS) connectionCount++;
        if (connN) connectionCount++;

        boolean anyConnection = connectionCount > 0;
        boolean isEndpoint = connectionCount == 1;

        // If this tube is an endpoint (only one neighbor), we render a "stub" in the
        // opposite direction plus a cap at the end. This avoids caps on unrelated faces
        // while still capping true tube ends.
        boolean stubE = false;
        boolean stubW = false;
        boolean stubU = false;
        boolean stubD = false;
        boolean stubS = false;
        boolean stubN = false;
        if (isEndpoint) {
            if (connE) stubW = true;
            else if (connW) stubE = true;
            else if (connU) stubD = true;
            else if (connD) stubU = true;
            else if (connS) stubN = true;
            else if (connN) stubS = true;
        }
        if (anyConnection) {
            ensurePart(tubeLoc, Part.CENTER_CONNECTED);
            removePart(tubeLoc, Part.CENTER);
        } else {
            ensurePart(tubeLoc, Part.CENTER);
            removePart(tubeLoc, Part.CENTER_CONNECTED);
        }

        // Arms render when connected OR when we need an endpoint stub.
        ensureOrRemove(tubeLoc, Part.ARM_EAST, connE || stubE);
        ensureOrRemove(tubeLoc, Part.ARM_WEST, connW || stubW);
        ensureOrRemove(tubeLoc, Part.ARM_UP, connU || stubU);
        ensureOrRemove(tubeLoc, Part.ARM_DOWN, connD || stubD);
        ensureOrRemove(tubeLoc, Part.ARM_SOUTH, connS || stubS);
        ensureOrRemove(tubeLoc, Part.ARM_NORTH, connN || stubN);

        // Caps only render on true tube ends (the stub direction).
        ensureOrRemove(tubeLoc, Part.CAP_EAST, stubE);
        ensureOrRemove(tubeLoc, Part.CAP_WEST, stubW);
        ensureOrRemove(tubeLoc, Part.CAP_UP, stubU);
        ensureOrRemove(tubeLoc, Part.CAP_DOWN, stubD);
        ensureOrRemove(tubeLoc, Part.CAP_SOUTH, stubS);
        ensureOrRemove(tubeLoc, Part.CAP_NORTH, stubN);
    }

    private boolean isConnectable(Location neighborLoc) {
        neighborLoc = norm(neighborLoc);
        if (tubeManager.getTube(neighborLoc) != null) return true;
        if (CloudFrameRegistry.quarries().hasControllerAt(neighborLoc)) return true;
        return InventoryUtil.isInventory(neighborLoc.getBlock());
    }

    private void ensureOrRemove(Location tubeLoc, Part part, boolean shouldExist) {
        if (shouldExist) {
            ensurePart(tubeLoc, part);
        } else {
            removePart(tubeLoc, part);
        }
    }

    private void ensurePart(Location tubeLoc, Part part) {
        EnumMap<Part, java.util.UUID> map = entitiesByTube.computeIfAbsent(tubeLoc, k -> new EnumMap<>(Part.class));
        java.util.UUID id = map.get(part);
        if (id != null) {
            Entity existing = Bukkit.getEntity(id);
            if (existing instanceof ItemDisplay display && !existing.isDead()) {
                // Always re-apply transform + item stack so tweaking visuals doesn't require
                // manually cleaning up old entities after jar swaps.
                configureTubeDisplay(display);
                display.setItemStack(tubePartStack(part));
                return;
            }
        }

        ItemDisplay spawned = spawnPart(tubeLoc, part);
        map.put(part, spawned.getUniqueId());
    }

    private void removePart(Location tubeLoc, Part part) {
        EnumMap<Part, java.util.UUID> map = entitiesByTube.get(tubeLoc);
        if (map == null) return;

        java.util.UUID id = map.remove(part);
        if (id != null) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }

        if (map.isEmpty()) {
            entitiesByTube.remove(tubeLoc);
        }
    }

    private void removeTubeDisplays(Location tubeLoc) {
        EnumMap<Part, java.util.UUID> map = entitiesByTube.remove(tubeLoc);
        if (map == null) return;
        for (var id : map.values()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }

        java.util.UUID iid = interactionByTube.remove(tubeLoc);
        if (iid != null) {
            Entity e = Bukkit.getEntity(iid);
            if (e != null) e.remove();
        }
    }

    private void ensureInteraction(Location tubeLoc) {
        Location desired = tubeLoc.clone().add(0.5, 0.5, 0.5);

        java.util.UUID id = interactionByTube.get(tubeLoc);
        if (id != null) {
            Entity existing = Bukkit.getEntity(id);
            if (existing instanceof Interaction interaction && !existing.isDead()) {
                // If an older version spawned the hitbox centered vertically (y+0.5),
                // the lower half of the block can miss raytraces. Respawn at the correct
                // bottom-centered position.
                if (interaction.getLocation().distanceSquared(desired) < 0.01) {
                    return;
                }

                existing.remove();
            }
        }

        Interaction interaction = spawnInteraction(tubeLoc);
        interactionByTube.put(tubeLoc, interaction.getUniqueId());
    }

    private Interaction spawnInteraction(Location tubeLoc) {
        World world = tubeLoc.getWorld();
        if (world == null) throw new IllegalStateException("Tube location has no world");

        // Interaction hitboxes are centered on the entity location; spawn at block-center
        // so the 1x1x1 hitbox covers the full blockspace.
        Location center = tubeLoc.clone().add(0.5, 0.5, 0.5);
        Interaction interaction = (Interaction) world.spawnEntity(center, EntityType.INTERACTION);
        interaction.setGravity(false);
        interaction.setInvulnerable(true);
        interaction.setPersistent(false);
        // Make the entity very hard to crosshair-target so the spoofed client-side block
        // can win and render the vanilla selection outline.
        // Interactions are still usable via block-click handlers (selection-box clicks).
        interaction.setInteractionWidth(0.01f);
        interaction.setInteractionHeight(0.01f);

        tagTubeEntity(interaction.getPersistentDataContainer(), tubeLoc, "interaction");

        debug.log("spawnInteraction", "Spawned tube interaction id=" + interaction.getUniqueId() + " at " + tubeLoc);
        return interaction;
    }

    private ItemDisplay spawnPart(Location tubeLoc, Part part) {
        World world = tubeLoc.getWorld();
        if (world == null) throw new IllegalStateException("Tube location has no world");

        Location center = tubeLoc.clone().add(0.5, 0.5, 0.5);
        ItemDisplay display = (ItemDisplay) world.spawnEntity(center, EntityType.ITEM_DISPLAY);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setPersistent(false);

        // Configure transform so block-model coordinates line up with world blocks.
        configureTubeDisplay(display);

        // Ensure it remains visible at normal distances.
        // (Default view range can be too small for tiny tube parts.)
        display.setViewRange(64.0f);

        display.setItemStack(tubePartStack(part));

        tagTubeEntity(display.getPersistentDataContainer(), tubeLoc, part.name());

        debug.log("spawnPart", "Spawned tube part=" + part + " id=" + display.getUniqueId() + " at " + tubeLoc);
        return display;
    }

    private static void configureTubeDisplay(ItemDisplay display) {
        // Make the visual entity very hard to crosshair-target.
        // Otherwise the client targets the ItemDisplay instead of the spoofed block,
        // preventing the vanilla block selection outline from appearing.
        try {
            display.setDisplayWidth(0.01f);
            display.setDisplayHeight(0.01f);
        } catch (Throwable ignored) {
            // Older API.
        }

        // Ensure displays don't camera-billboard (keeps caps/arms facing the right way).
        try {
            display.setBillboard(Display.Billboard.FIXED);
        } catch (Throwable ignored) {
            // Older API.
        }

        // Prefer a world-aligned transform (NONE) so model faces match world axes.
        // Fall back to FIXED for forks/older APIs.
        try {
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.valueOf("NONE"));
        } catch (Throwable ignored) {
            try {
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            } catch (Throwable ignored2) {
                // Older API / unexpected server forks.
            }
        }

        // Preserve the server's base transformation (it encodes the correct centering
        // for the chosen item transform), and only scale up if it looks shrunken.
        try {
            Transformation base = display.getTransformation();

            Vector3f translation = new Vector3f(base.getTranslation());
            AxisAngle4f leftRot = new AxisAngle4f(base.getLeftRotation());
            Vector3f scale = new Vector3f(base.getScale());
            AxisAngle4f rightRot = new AxisAngle4f(base.getRightRotation());

            float factor = (scale.x < 0.75f || scale.y < 0.75f || scale.z < 0.75f) ? 2.0f : 1.0f;
            if (factor != 1.0f) {
                scale.mul(factor);
                display.setTransformation(new Transformation(translation, leftRot, scale, rightRot));
            }
        } catch (Throwable ignored) {
            // If Transformation/JOML isn't available at runtime, leave defaults.
        }
    }

    private ItemStack tubePartStack(Part part) {
        ItemStack stack = new ItemStack(org.bukkit.Material.COPPER_BLOCK);
        ItemMeta meta = stack.getItemMeta();
        meta.setCustomModelData(customModelDataFor(part));
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isTagged(PersistentDataContainer pdc) {
        Byte b = pdc.get(tagKey, PersistentDataType.BYTE);
        if (b != null && b == (byte) 1) return true;
        Byte legacy = pdc.get(legacyTagKey, PersistentDataType.BYTE);
        return legacy != null && legacy == (byte) 1;
    }

    private void tagTubeEntity(PersistentDataContainer pdc, Location tubeLoc, String partName) {
        pdc.set(tagKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(worldKey, PersistentDataType.STRING, tubeLoc.getWorld().getUID().toString());
        pdc.set(xKey, PersistentDataType.INTEGER, tubeLoc.getBlockX());
        pdc.set(yKey, PersistentDataType.INTEGER, tubeLoc.getBlockY());
        pdc.set(zKey, PersistentDataType.INTEGER, tubeLoc.getBlockZ());
        pdc.set(partKey, PersistentDataType.STRING, partName);
    }

    public Location getTaggedTubeLocation(PersistentDataContainer pdc) {
        if (!isTagged(pdc)) return null;

        String worldId = pdc.get(worldKey, PersistentDataType.STRING);
        Integer x = pdc.get(xKey, PersistentDataType.INTEGER);
        Integer y = pdc.get(yKey, PersistentDataType.INTEGER);
        Integer z = pdc.get(zKey, PersistentDataType.INTEGER);
        if (worldId == null || x == null || y == null || z == null) return null;

        java.util.UUID uuid;
        try {
            uuid = java.util.UUID.fromString(worldId);
        } catch (IllegalArgumentException ex) {
            return null;
        }

        World w = Bukkit.getWorld(uuid);
        if (w == null) return null;
        return new Location(w, x, y, z);
    }

    private static Location norm(Location loc) {
        return new Location(
                loc.getWorld(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        );
    }
}
