package dev.cloudframe.cloudframe.quarry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

/**
 * Quarry Controller visuals (Option 2): entity-only.
 * - An Interaction entity provides a clickable/hittable hitbox.
 * - An ItemDisplay renders the controller model.
 */
public final class ControllerVisualManager {

    private static final Debug debug = DebugManager.get(ControllerVisualManager.class);

    // CustomModelData used for the placed controller display.
    // (Defined in resource pack via assets/minecraft/items/copper_block.json)
    private static final int CMD_CONTROLLER_DISPLAY = 2101;

    // The controller model's "front" is authored facing the opposite direction.
    // Apply a fixed offset so when a player places it facing X, the front appears on X.
    private static final int MODEL_YAW_OFFSET = 180;

    public static final String PDC_TAG_KEY = "cloudframe_controller_entity";
    private static final String PDC_X = "cloudframe_controller_x";
    private static final String PDC_Y = "cloudframe_controller_y";
    private static final String PDC_Z = "cloudframe_controller_z";
    private static final String PDC_WORLD = "cloudframe_controller_world"; // UUID string
    private static final String PDC_KIND = "cloudframe_controller_kind"; // interaction|display

    private final NamespacedKey tagKey;
    private final NamespacedKey xKey;
    private final NamespacedKey yKey;
    private final NamespacedKey zKey;
    private final NamespacedKey worldKey;
    private final NamespacedKey kindKey;

    private final Map<Location, UUID> interactionByLoc = new HashMap<>();
    private final Map<Location, UUID> displayByLoc = new HashMap<>();

    public ControllerVisualManager(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.tagKey = new NamespacedKey(plugin, PDC_TAG_KEY);
        this.xKey = new NamespacedKey(plugin, PDC_X);
        this.yKey = new NamespacedKey(plugin, PDC_Y);
        this.zKey = new NamespacedKey(plugin, PDC_Z);
        this.worldKey = new NamespacedKey(plugin, PDC_WORLD);
        this.kindKey = new NamespacedKey(plugin, PDC_KIND);
    }

    public void shutdown() {
        debug.log("shutdown", "Removing controller visuals (interaction=" + interactionByLoc.size() + ", display=" + displayByLoc.size() + ")");
        for (UUID id : interactionByLoc.values()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        for (UUID id : displayByLoc.values()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        interactionByLoc.clear();
        displayByLoc.clear();
    }

    public void cleanupChunkEntities(Chunk chunk) {
        int removed = 0;
        for (Entity e : chunk.getEntities()) {
            if (isTagged(e.getPersistentDataContainer())) {
                e.remove();
                removed++;
            }
        }

        if (removed > 0) {
            debug.log("cleanupChunkEntities", "Removed " + removed + " tagged controller entities in chunk " +
                chunk.getWorld().getName() + " " + chunk.getX() + "," + chunk.getZ());
        }

        interactionByLoc.entrySet().removeIf(en -> isInChunk(en.getKey(), chunk));
        displayByLoc.entrySet().removeIf(en -> isInChunk(en.getKey(), chunk));
    }

    private static boolean isInChunk(Location loc, Chunk chunk) {
        if (loc == null || loc.getWorld() == null) return true;
        if (!loc.getWorld().equals(chunk.getWorld())) return false;
        return (loc.getBlockX() >> 4) == chunk.getX() && (loc.getBlockZ() >> 4) == chunk.getZ();
    }

    public void ensureController(Location controllerLoc, int controllerYaw) {
        controllerLoc = norm(controllerLoc);

        if (controllerLoc.getWorld() == null || !controllerLoc.getChunk().isLoaded()) {
            return;
        }

        debug.log("ensureController", "Ensuring controller visuals at " + controllerLoc);

        ensureInteraction(controllerLoc);
        ensureDisplay(controllerLoc, controllerYaw);
    }

    public void removeController(Location controllerLoc) {
        controllerLoc = norm(controllerLoc);

        debug.log("removeController", "Removing controller visuals at " + controllerLoc);

        UUID iid = interactionByLoc.remove(controllerLoc);
        if (iid != null) {
            Entity e = Bukkit.getEntity(iid);
            if (e != null) e.remove();
        }

        UUID did = displayByLoc.remove(controllerLoc);
        if (did != null) {
            Entity e = Bukkit.getEntity(did);
            if (e != null) e.remove();
        }
    }

    private void ensureInteraction(Location controllerLoc) {
        Location desired = controllerLoc.clone().add(0.5, 0.0, 0.5);

        UUID id = interactionByLoc.get(controllerLoc);
        if (id != null) {
            Entity existing = Bukkit.getEntity(id);
            if (existing instanceof Interaction interaction && !existing.isDead()) {
                // Older versions spawned this at y+0.5; respawn at bottom-center so the
                // hitbox reliably covers the entire blockspace.
                if (interaction.getLocation().distanceSquared(desired) < 0.01) return;
                existing.remove();
            }
        }

        Interaction interaction = spawnInteraction(controllerLoc);
        interactionByLoc.put(controllerLoc, interaction.getUniqueId());
    }

    private void ensureDisplay(Location controllerLoc, int controllerYaw) {
        UUID id = displayByLoc.get(controllerLoc);
        if (id != null) {
            Entity existing = Bukkit.getEntity(id);
            if (existing instanceof ItemDisplay disp && !existing.isDead()) {
                disp.setRotation(applyModelYawOffset(controllerYaw), 0.0f);
                return;
            }
        }

        ItemDisplay display = spawnDisplay(controllerLoc, controllerYaw);
        displayByLoc.put(controllerLoc, display.getUniqueId());
    }

    private Interaction spawnInteraction(Location controllerLoc) {
        World world = controllerLoc.getWorld();
        if (world == null) throw new IllegalStateException("Controller location has no world");

        // Interaction hitboxes are bottom-anchored; spawn at bottom-center so the 1x1x1
        // hitbox covers the full blockspace.
        Location base = controllerLoc.clone().add(0.5, 0.0, 0.5);
        Interaction interaction = (Interaction) world.spawnEntity(base, EntityType.INTERACTION);
        interaction.setGravity(false);
        interaction.setInvulnerable(true);
        interaction.setPersistent(false);
        interaction.setInteractionWidth(1.0f);
        interaction.setInteractionHeight(1.0f);

        tag(interaction.getPersistentDataContainer(), controllerLoc, "interaction");

        debug.log("spawnInteraction", "Spawned interaction id=" + interaction.getUniqueId() + " at " + controllerLoc);
        return interaction;
    }

    private ItemDisplay spawnDisplay(Location controllerLoc, int controllerYaw) {
        World world = controllerLoc.getWorld();
        if (world == null) throw new IllegalStateException("Controller location has no world");

        Location center = controllerLoc.clone().add(0.5, 0.5, 0.5);
        ItemDisplay display = (ItemDisplay) world.spawnEntity(center, EntityType.ITEM_DISPLAY);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setPersistent(false);

        display.setItemStack(controllerDisplayStack());
        display.setRotation(applyModelYawOffset(controllerYaw), 0.0f);

        tag(display.getPersistentDataContainer(), controllerLoc, "display");

        debug.log("spawnDisplay", "Spawned display id=" + display.getUniqueId() + " at " + controllerLoc);
        return display;
    }

    private static float applyModelYawOffset(int yaw) {
        int y = (yaw + MODEL_YAW_OFFSET) % 360;
        if (y < 0) y += 360;
        return (float) y;
    }

    private ItemStack controllerDisplayStack() {
        ItemStack stack = new ItemStack(org.bukkit.Material.COPPER_BLOCK);
        ItemMeta meta = stack.getItemMeta();
        meta.setCustomModelData(CMD_CONTROLLER_DISPLAY);
        stack.setItemMeta(meta);
        return stack;
    }

    private void tag(PersistentDataContainer pdc, Location loc, String kind) {
        pdc.set(tagKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(worldKey, PersistentDataType.STRING, loc.getWorld().getUID().toString());
        pdc.set(xKey, PersistentDataType.INTEGER, loc.getBlockX());
        pdc.set(yKey, PersistentDataType.INTEGER, loc.getBlockY());
        pdc.set(zKey, PersistentDataType.INTEGER, loc.getBlockZ());
        pdc.set(kindKey, PersistentDataType.STRING, kind);
    }

    private boolean isTagged(PersistentDataContainer pdc) {
        Byte b = pdc.get(tagKey, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    public Location getTaggedControllerLocation(PersistentDataContainer pdc) {
        if (!isTagged(pdc)) return null;

        String worldId = pdc.get(worldKey, PersistentDataType.STRING);
        Integer x = pdc.get(xKey, PersistentDataType.INTEGER);
        Integer y = pdc.get(yKey, PersistentDataType.INTEGER);
        Integer z = pdc.get(zKey, PersistentDataType.INTEGER);
        if (worldId == null || x == null || y == null || z == null) return null;

        UUID uuid;
        try {
            uuid = UUID.fromString(worldId);
        } catch (IllegalArgumentException ex) {
            debug.log("getTaggedControllerLocation", "Invalid world UUID in PDC: '" + worldId + "'");
            return null;
        }

        World w = Bukkit.getWorld(uuid);
        if (w == null) {
            debug.log("getTaggedControllerLocation", "World not loaded for uuid=" + uuid);
            return null;
        }

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
