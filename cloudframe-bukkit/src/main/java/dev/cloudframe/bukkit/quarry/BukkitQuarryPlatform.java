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

import dev.cloudframe.bukkit.tubes.BukkitPacketService;
import dev.cloudframe.common.quarry.QuarryPlatform;
import dev.cloudframe.common.tubes.ItemPacketManager;
import dev.cloudframe.common.tubes.TubeNetworkManager;

/**
 * Bukkit implementation of quarry platform hooks.
 */
public class BukkitQuarryPlatform implements QuarryPlatform {

    private final TubeNetworkManager tubeManager;
    private final ItemPacketManager packetManager;
    private final BukkitPacketService packetService;

    public BukkitQuarryPlatform(TubeNetworkManager tubeManager, ItemPacketManager packetManager, BukkitPacketService packetService) {
        this.tubeManager = tubeManager;
        this.packetManager = packetManager;
        this.packetService = packetService;
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
        int original = stack.getAmount();
        var leftovers = inv.addItem(stack.clone());
        int remaining = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        return Math.max(0, original - remaining);
    }

    @Override
    public boolean hasSpaceFor(Object inventoryHolder, Object itemStack, Map<String, Integer> inFlight) {
        if (!(inventoryHolder instanceof InventoryHolder holder)) return false;
        if (!(itemStack instanceof ItemStack stack)) return false;
        Inventory inv = holder.getInventory();
        if (inv == null) return false;

        int remaining = stack.getAmount();
        int max = stack.getMaxStackSize();

        String destKey = locationKey(((InventoryHolder) inventoryHolder).getInventory().getLocation());
        int inFlightAmount = inFlight.getOrDefault(destKey, 0);
        remaining += inFlightAmount;

        for (ItemStack slot : inv.getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                remaining -= max;
                if (remaining <= 0) return true;
                continue;
            }
            if (!slot.isSimilar(stack)) continue;
            int space = Math.max(0, max - slot.getAmount());
            remaining -= space;
            if (remaining <= 0) return true;
        }
        return false;
    }

    @Override
    public String locationKey(Object loc) {
        if (!(loc instanceof Location l)) return "null";
        World world = l.getWorld();
        if (world == null) return "null";
        return world.getName() + ":" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
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
    public Object worldByName(String name) {
        return Bukkit.getWorld(name);
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
    public int maxStackSize(Object itemStack) {
        if (itemStack instanceof ItemStack stack) return stack.getMaxStackSize();
        return 64;
    }

    @Override
    public TubeNetworkManager tubes() {
        return tubeManager;
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
                packetService.enqueue(stack, locs, dest, callback != null ? (loc, amt) -> callback.delivered(loc, amt) : null);
            }
        };
    }

    @Override
    public UUID ownerFromPlayer(Object player) {
        if (player instanceof Player p) return p.getUniqueId();
        return new UUID(0, 0);
    }
}
