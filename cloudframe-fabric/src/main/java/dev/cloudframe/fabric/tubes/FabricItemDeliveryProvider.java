package dev.cloudframe.fabric.tubes;

import dev.cloudframe.common.tubes.ItemPacketManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Fabric implementation for item delivery operations.
 */
public class FabricItemDeliveryProvider implements ItemPacketManager.IItemDeliveryProvider {

    private static final int[][] DIRS = {
        {1, 0, 0},
        {-1, 0, 0},
        {0, 1, 0},
        {0, -1, 0},
        {0, 0, 1},
        {0, 0, -1}
    };

    private final ServerWorld world;

    public FabricItemDeliveryProvider(ServerWorld world) {
        this.world = world;
    }

    @Override
    public boolean isChunkLoaded(Object location) {
        if (!(location instanceof BlockPos pos)) return false;
        return world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Override
    public Object getInventoryHolder(Object blockLocation) {
        if (!(blockLocation instanceof BlockPos pos)) return null;
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof Inventory) {
            return blockEntity;
        }
        return null;
    }

    @Override
    public int addItem(Object inventoryHolder, Object item) {
        if (!(inventoryHolder instanceof Inventory inv)) return 0;
        if (!(item instanceof ItemStack stack)) return 0;
        
        int original = stack.getCount();
        ItemStack remaining = stack.copy();

        for (int i = 0; i < inv.size() && !remaining.isEmpty(); i++) {
            ItemStack slot = inv.getStack(i);
            
            if (ItemStack.areItemsAndComponentsEqual(slot, remaining)) {
                int space = slot.getMaxCount() - slot.getCount();
                if (space > 0) {
                    int transfer = Math.min(space, remaining.getCount());
                    slot.increment(transfer);
                    remaining.decrement(transfer);
                }
            }
        }

        for (int i = 0; i < inv.size() && !remaining.isEmpty(); i++) {
            ItemStack slot = inv.getStack(i);
            
            if (slot.isEmpty()) {
                inv.setStack(i, remaining.copy());
                remaining.setCount(0);
            }
        }

        inv.markDirty();
        return Math.max(0, original - remaining.getCount());
    }

    @Override
    public void dropItems(Object location, Object[] items) {
        if (!(location instanceof BlockPos pos)) return;
        Vec3d spawnPos = Vec3d.ofCenter(pos);
        
        for (Object obj : items) {
            if (obj instanceof ItemStack stack) {
                ItemEntity itemEntity = new ItemEntity(world, spawnPos.x, spawnPos.y, spawnPos.z, stack.copy());
                itemEntity.setVelocity(Vec3d.ZERO);
                world.spawnEntity(itemEntity);
            }
        }
    }

    @Override
    public Object getAdjacentBlockLocation(Object baseLocation, int dirIndex) {
        if (!(baseLocation instanceof BlockPos base)) return null;
        if (dirIndex < 0 || dirIndex >= DIRS.length) return null;
        int[] d = DIRS[dirIndex];
        return base.add(d[0], d[1], d[2]);
    }
}
