package dev.cloudframe.fabric.pipes;

import dev.cloudframe.common.pipes.ItemPacketManager;
import dev.cloudframe.common.platform.items.InventoryInsert;
import dev.cloudframe.common.platform.items.ItemStackAdapter;
import dev.cloudframe.common.platform.items.SlottedInventoryAdapter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import dev.cloudframe.common.trash.TrashSink;

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

    private final MinecraftServer server;

    private static final ItemStackAdapter<ItemStack> STACKS = FabricItemStackAdapter.INSTANCE;

    private static final SlottedInventoryAdapter<Inventory, ItemStack> INVENTORY = new SlottedInventoryAdapter<>() {
        @Override
        public int size(Inventory inventory) {
            return inventory == null ? 0 : inventory.size();
        }

        @Override
        public ItemStack getStack(Inventory inventory, int slot) {
            return inventory == null ? ItemStack.EMPTY : inventory.getStack(slot);
        }

        @Override
        public void setStack(Inventory inventory, int slot, ItemStack stack) {
            if (inventory == null) return;
            inventory.setStack(slot, stack);
        }

        @Override
        public void markDirty(Inventory inventory) {
            if (inventory == null) return;
            inventory.markDirty();
        }
    };

    public FabricItemDeliveryProvider(MinecraftServer server) {
        this.server = server;
    }

    private ServerWorld worldOf(Object location) {
        if (location instanceof GlobalPos gp) {
            ServerWorld w = server.getWorld(gp.dimension());
            return w != null ? w : server.getOverworld();
        }
        return server.getOverworld();
    }

    private BlockPos posOf(Object location) {
        if (location instanceof GlobalPos gp) return gp.pos();
        if (location instanceof BlockPos pos) return pos;
        return null;
    }

    private Object wrapLike(Object baseLocation, BlockPos newPos) {
        if (baseLocation instanceof GlobalPos gp) {
            return GlobalPos.create(gp.dimension(), newPos.toImmutable());
        }
        return newPos;
    }

    @Override
    public boolean isChunkLoaded(Object location) {
        BlockPos pos = posOf(location);
        if (pos == null) return false;
        ServerWorld w = worldOf(location);
        return w.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Override
    public Object getInventoryHolder(Object blockLocation) {
        BlockPos pos = posOf(blockLocation);
        if (pos == null) return null;
        ServerWorld w = worldOf(blockLocation);
        BlockEntity blockEntity = w.getBlockEntity(pos);
        if (blockEntity instanceof Inventory) {
            return blockEntity;
        }
        return null;
    }

    @Override
    public int addItem(Object inventoryHolder, Object item) {
        if (inventoryHolder instanceof TrashSink trash) {
            return trash.accept(item);
        }
        if (!(inventoryHolder instanceof Inventory inv)) return 0;
        if (!(item instanceof ItemStack stack)) return 0;

        return InventoryInsert.addItem(inv, stack, INVENTORY, STACKS);
    }

    @Override
    public void dropItems(Object location, Object[] items) {
        BlockPos pos = posOf(location);
        if (pos == null) return;
        ServerWorld w = worldOf(location);
        Vec3d spawnPos = Vec3d.ofCenter(pos);

        for (Object obj : items) {
            if (obj instanceof ItemStack stack) {
                ItemEntity itemEntity = new ItemEntity(w, spawnPos.x, spawnPos.y, spawnPos.z, stack.copy());
                itemEntity.setVelocity(Vec3d.ZERO);
                w.spawnEntity(itemEntity);
            }
        }
    }

    @Override
    public Object getAdjacentBlockLocation(Object baseLocation, int dirIndex) {
        BlockPos base = posOf(baseLocation);
        if (base == null) return null;
        if (dirIndex < 0 || dirIndex >= DIRS.length) return null;
        int[] d = DIRS[dirIndex];
        return wrapLike(baseLocation, base.add(d[0], d[1], d[2]));
    }
}
