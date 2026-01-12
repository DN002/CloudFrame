package dev.cloudframe.bukkit.quarry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import dev.cloudframe.common.quarry.QuarryPlatform;
import dev.cloudframe.bukkit.pipes.BukkitPacketService;
import dev.cloudframe.common.pipes.ItemPacketManager;
import dev.cloudframe.common.pipes.PipeNetworkManager;
import dev.cloudframe.common.platform.items.InventoryCapacity;
import dev.cloudframe.common.platform.items.InventoryInsert;
import dev.cloudframe.common.platform.items.ItemStackAdapter;
import dev.cloudframe.common.platform.items.ItemStackKeyAdapter;
import dev.cloudframe.common.platform.items.SlottedInventoryAdapter;
import dev.cloudframe.common.platform.world.LocationKeyAdapter;
import dev.cloudframe.common.platform.world.WorldKeyAdapter;

/**
 * Bukkit implementation of quarry platform hooks.
 */
public class BukkitQuarryPlatform implements QuarryPlatform {

    private final PipeNetworkManager pipeManager;
    private final ItemPacketManager packetManager;
    private final BukkitPacketService packetService;

    private dev.cloudframe.bukkit.power.BukkitPowerNetworkAdapter powerAdapter;
    private boolean powerEnabled = false;

    private static final SlottedInventoryAdapter<Inventory, ItemStack> INVENTORY = new SlottedInventoryAdapter<>() {
        @Override
        public int size(Inventory inventory) {
            return inventory == null ? 0 : inventory.getSize();
        }

        @Override
        public ItemStack getStack(Inventory inventory, int slot) {
            return inventory == null ? null : inventory.getItem(slot);
        }

        @Override
        public void setStack(Inventory inventory, int slot, ItemStack stack) {
            if (inventory == null) return;
            inventory.setItem(slot, stack);
        }

        @Override
        public void markDirty(Inventory inventory) {
            // Bukkit inventories don't have a direct markDirty; setting items is enough.
        }
    };

    private static final ItemStackAdapter<ItemStack> STACKS = new ItemStackAdapter<>() {
        @Override
        public boolean isEmpty(ItemStack stack) {
            return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
        }

        @Override
        public int getCount(ItemStack stack) {
            return stack == null ? 0 : stack.getAmount();
        }

        @Override
        public void setCount(ItemStack stack, int count) {
            if (stack == null) return;
            stack.setAmount(Math.max(0, count));
        }

        @Override
        public int getMaxCount(ItemStack stack) {
            return stack == null ? 64 : stack.getMaxStackSize();
        }

        @Override
        public ItemStack copy(ItemStack stack) {
            return stack == null ? null : stack.clone();
        }

        @Override
        public boolean canMerge(ItemStack existing, ItemStack incoming) {
            if (existing == null || incoming == null) return false;
            if (existing.getType().isAir() || incoming.getType().isAir()) return false;
            return existing.isSimilar(incoming);
        }
    };

    private static final WorldKeyAdapter<Object> WORLD_KEYS = new WorldKeyAdapter<>() {
        @Override
        public String key(Object world) {
            if (world instanceof World w) {
                return w.getName();
            }
            return world != null ? world.toString() : "";
        }

        @Override
        public Object worldByKey(String key) {
            if (key == null || key.isBlank()) return null;
            return Bukkit.getWorld(key);
        }
    };

    public BukkitQuarryPlatform(PipeNetworkManager pipeManager, ItemPacketManager packetManager, BukkitPacketService packetService) {
        this.pipeManager = pipeManager;
        this.packetManager = packetManager;
        this.packetService = packetService;
    }

    /**
     * Optional: enables powering quarries via the shared power network system.
     *
     * <p>Left disabled by default so existing Bukkit installs keep legacy behavior.</p>
     */
    public void setPowerAdapter(dev.cloudframe.bukkit.power.BukkitPowerNetworkAdapter adapter, boolean enabled) {
        this.powerAdapter = adapter;
        this.powerEnabled = enabled;
    }

    /** Called by the platform tick loop to keep power caching in sync. */
    public void onTickStart(long tick) {
        if (!powerEnabled || powerAdapter == null) return;
        powerAdapter.beginTick(tick);
    }

    /** Called by the platform tick loop after consumers run. */
    public void onTickEnd() {
        if (!powerEnabled || powerAdapter == null) return;
        powerAdapter.endTick();
    }

    @Override
    public Object normalize(Object loc) {
        if (loc instanceof Location l) {
            return new Location(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        }
        return loc;
    }

    @Override
    public Object offset(Object loc, int dx, int dy, int dz) {
        if (loc instanceof Location l) {
            return new Location(l.getWorld(), l.getBlockX() + dx, l.getBlockY() + dy, l.getBlockZ() + dz);
        }
        return loc;
    }

    @Override
    public boolean isChunkLoaded(Object loc) {
        if (!(loc instanceof Location l)) return false;
        World w = l.getWorld();
        if (w == null) return false;
        return w.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4);
    }

    @Override
    public boolean isRedstonePowered(Object loc) {
        if (!(loc instanceof Location l)) return false;
        Block block = l.getBlock();
        try {
            return block.isBlockPowered() || block.isBlockIndirectlyPowered() || block.getBlockPower() > 0;
        } catch (Throwable ignored) {
            return block.getBlockPower() > 0;
        }
    }

    @Override
    public void setChunkForced(Object world, int chunkX, int chunkZ, boolean forced) {
        if (!(world instanceof World w)) return;
        try {
            w.setChunkForceLoaded(chunkX, chunkZ, forced);
        } catch (Throwable ignored) {
            // Best-effort; older servers may not support this.
        }
    }

    @Override
    public boolean isMineable(Object loc) {
        if (!(loc instanceof Location l)) return false;
        Block block = l.getBlock();
        Material type = block.getType();
        if (type == Material.AIR || type == Material.BEDROCK) return false;
        return type.isSolid() || type == Material.WATER || type == Material.LAVA;
    }

    @Override
    public List<Object> getDrops(Object loc, boolean silkTouch) {
        if (!(loc instanceof Location l)) return List.of();
        Block block = l.getBlock();
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        if (silkTouch) {
            try {
                tool.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
            } catch (Throwable ignored) {}
        }
        List<Object> result = new ArrayList<>();
        try {
            for (ItemStack drop : block.getDrops(tool)) {
                if (drop != null && drop.getType() != Material.AIR) {
                    result.add(drop);
                }
            }
        } catch (Throwable ex) {
            result.add(new ItemStack(block.getType()));
        }
        return result;
    }

    @Override
    public void setBlockAir(Object loc) {
        if (!(loc instanceof Location l)) return;
        l.getBlock().setType(Material.AIR);
    }

    @Override
    public void playBreakEffects(Object loc) {
        if (!(loc instanceof Location l)) return;
        Block block = l.getBlock();
        World world = l.getWorld();
        if (world == null) return;

        try {
            Sound sound = block.getBlockData().getSoundGroup().getBreakSound();
            world.playSound(l.clone().add(0.5, 0.5, 0.5), sound, 1.0f, 1.0f);
        } catch (Throwable ignored) {
            world.playSound(l.clone().add(0.5, 0.5, 0.5), Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);
        }

        try {
            world.spawnParticle(Particle.BLOCK, l.clone().add(0.5, 0.5, 0.5), 18, 0.25, 0.25, 0.25, block.getBlockData());
        } catch (Throwable ignored) {}
    }

    @Override
    public void sendBlockCrack(Object loc, float progress01) {
        if (!(loc instanceof Location l)) return;
        World world = l.getWorld();
        if (world == null) return;
        float p = Math.max(0.0f, Math.min(1.0f, progress01));
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(l) > (32.0 * 32.0)) continue;
            try {
                player.sendBlockDamage(l, p);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public boolean isInventory(Object loc) {
        if (!(loc instanceof Location l)) return false;
        Block block = l.getBlock();
        return block.getState() instanceof InventoryHolder;
    }

    @Override
    public Object getInventoryHolder(Object loc) {
        if (!(loc instanceof Location l)) return null;
        Block block = l.getBlock();
        if (block.getState() instanceof InventoryHolder holder) {
            return holder;
        }
        return null;
    }

    @Override
    public int addToInventory(Object inventoryHolder, Object itemStack) {
        if (!(inventoryHolder instanceof InventoryHolder holder)) return 0;
        if (!(itemStack instanceof ItemStack stack)) return 0;
        Inventory inv = holder.getInventory();
        if (inv == null) return 0;
        return InventoryInsert.addItem(inv, stack, INVENTORY, STACKS);
    }

    @Override
    public int totalRoomFor(Object inventoryHolder, Object itemStack) {
        if (!(inventoryHolder instanceof InventoryHolder holder)) return 0;
        if (!(itemStack instanceof ItemStack stack)) return 0;
        Inventory inv = holder.getInventory();
        if (inv == null) return 0;
        return InventoryCapacity.totalRoomFor(inv, stack, INVENTORY, STACKS);
    }

    @Override
    public int emptySlotCount(Object inventoryHolder) {
        if (!(inventoryHolder instanceof InventoryHolder holder)) return 0;
        Inventory inv = holder.getInventory();
        if (inv == null) return 0;
        return InventoryCapacity.emptySlotCount(inv, INVENTORY, STACKS);
    }

    @Override
    public LocationKeyAdapter<Object> locationKeyAdapter() {
        return (obj) -> {
            if (!(obj instanceof Location l)) return "null";
            World world = l.getWorld();
            String w = world != null ? world.getName() : "null";
            return w + ":" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
        };
    }

    @Override
    public ItemStackKeyAdapter<Object> itemKeyAdapter() {
        return (obj) -> {
            if (!(obj instanceof ItemStack stack)) return "null";
            try {
                return stack.getType().getKey().toString();
            } catch (Throwable ignored) {
                return stack.getType().name();
            }
        };
    }

    @Override
    public double distanceSquared(Object a, Object b) {
        if (!(a instanceof Location la) || !(b instanceof Location lb)) return Double.MAX_VALUE;
        if (!la.getWorld().equals(lb.getWorld())) return Double.MAX_VALUE;
        return la.distanceSquared(lb);
    }

    @Override
    public Object createLocation(Object world, int x, int y, int z) {
        if (world instanceof World w) {
            return new Location(w, x, y, z);
        }
        return null;
    }

    @Override
    public Object worldOf(Object loc) {
        if (loc instanceof Location l) return l.getWorld();
        return null;
    }

    @Override
    public WorldKeyAdapter<Object> worldKeyAdapter() {
        return WORLD_KEYS;
    }

    @Override
    public int blockX(Object loc) {
        if (loc instanceof Location l) return l.getBlockX();
        return 0;
    }

    @Override
    public int blockY(Object loc) {
        if (loc instanceof Location l) return l.getBlockY();
        return 0;
    }

    @Override
    public int blockZ(Object loc) {
        if (loc instanceof Location l) return l.getBlockZ();
        return 0;
    }

    @Override
    public int stackAmount(Object itemStack) {
        if (itemStack instanceof ItemStack stack) return stack.getAmount();
        return 0;
    }

    @Override
    public Object copyWithAmount(Object itemStack, int amount) {
        if (itemStack instanceof ItemStack stack) {
            ItemStack copy = stack.clone();
            copy.setAmount(Math.max(0, amount));
            return copy;
        }
        return itemStack;
    }

    @Override
    public int maxStackSize(Object itemStack) {
        if (itemStack instanceof ItemStack stack) return stack.getMaxStackSize();
        return 64;
    }

    @Override
    public PipeNetworkManager pipes() {
        return pipeManager;
    }

    @Override
    public ItemPacketManager packets() {
        return packetManager;
    }

    @Override
    public ItemPacketFactory packetFactory() {
        return new ItemPacketFactory() {
            @Override
            public void send(Object itemStack, List<Object> waypoints, Object destinationInventory, DeliveryCallback callback) {
                if (!(itemStack instanceof ItemStack stack)) return;
                List<Location> locs = new ArrayList<>();
                for (Object o : waypoints) {
                    if (o instanceof Location l) locs.add(l);
                }
                Location dest = destinationInventory instanceof Location l ? l : null;
                packetService.enqueue(stack, locs, dest, callback);
            }
        };
    }

    @Override
    public UUID ownerFromPlayer(Object player) {
        if (player instanceof Player p) return p.getUniqueId();
        return new UUID(0, 0);
    }

    @Override
    public boolean supportsPower() {
        return powerEnabled && powerAdapter != null;
    }

    @Override
    public long extractPowerCfe(Object controllerLoc, long amount) {
        if (!supportsPower()) return 0L;
        if (!(controllerLoc instanceof Location l)) return 0L;
        return powerAdapter.extractPowerCfe(l, amount);
    }
    
    @Override
    public void placeGlassFrame(Object worldObj, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        // Create a visible glass frame around the quarry region (Bukkit implementation)
        if (!(worldObj instanceof org.bukkit.World w)) return;
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    // Only place glass on the edges/corners (frame outline)
                    boolean isEdgeX = (x == minX || x == maxX);
                    boolean isEdgeY = (y == minY || y == maxY);
                    boolean isEdgeZ = (z == minZ || z == maxZ);
                    
                    // Place glass if it's on at least 2 edges (forms the frame structure)
                    int edgeCount = (isEdgeX ? 1 : 0) + (isEdgeY ? 1 : 0) + (isEdgeZ ? 1 : 0);
                    if (edgeCount >= 2) {
                        Location loc = new Location(w, x, y, z);
                        org.bukkit.block.Block block = loc.getBlock();
                        
                        // Only replace air blocks
                        if (block.getType() == org.bukkit.Material.AIR) {
                            block.setType(org.bukkit.Material.GLASS, false);
                        }
                    }
                }
            }
        }
    }
    
    @Override
    public void removeGlassFrame(Object worldObj, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        // Remove the glass frame when quarry is removed (Bukkit implementation)
        if (!(worldObj instanceof org.bukkit.World w)) return;
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean isEdgeX = (x == minX || x == maxX);
                    boolean isEdgeY = (y == minY || y == maxY);
                    boolean isEdgeZ = (z == minZ || z == maxZ);
                    
                    int edgeCount = (isEdgeX ? 1 : 0) + (isEdgeY ? 1 : 0) + (isEdgeZ ? 1 : 0);
                    if (edgeCount >= 2) {
                        Location loc = new Location(w, x, y, z);
                        if (isGlassFrameBlock(loc)) {
                            loc.getBlock().setType(org.bukkit.Material.AIR, false);
                        }
                    }
                }
            }
        }
    }
    
    @Override
    public boolean isGlassFrameBlock(Object loc) {
        if (!(loc instanceof Location l)) return false;
        return l.getBlock().getType() == org.bukkit.Material.GLASS;
    }
}
