package dev.cloudframe.fabric.quarry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.cloudframe.common.quarry.QuarryPlatform;
import dev.cloudframe.common.tubes.ItemPacketManager;
import dev.cloudframe.common.tubes.TubeNetworkManager;
import dev.cloudframe.fabric.tubes.FabricPacketService;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class FabricQuarryPlatform implements QuarryPlatform {

    private final ServerWorld world;
    private final TubeNetworkManager tubeManager;
    private final ItemPacketManager packetManager;
    private final FabricPacketService packetService;

    public FabricQuarryPlatform(ServerWorld world, TubeNetworkManager tubeManager, ItemPacketManager packetManager) {
        this.world = world;
        this.tubeManager = tubeManager;
        this.packetManager = packetManager;
        this.packetService = new FabricPacketService(packetManager, world);
    }

    @Override
    public Object normalize(Object loc) {
        if (loc instanceof BlockPos pos) return pos.toImmutable();
        return loc;
    }

    @Override
    public Object offset(Object loc, int dx, int dy, int dz) {
        if (loc instanceof BlockPos pos) return pos.add(dx, dy, dz);
        return loc;
    }

    @Override
    public boolean isChunkLoaded(Object loc) {
        if (!(loc instanceof BlockPos pos)) return false;
        return world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Override
    public boolean isMineable(Object loc) {
        if (!(loc instanceof BlockPos pos)) return false;
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block == Blocks.AIR || block == Blocks.BEDROCK) return false;
        return state.isSolid() || block == Blocks.WATER || block == Blocks.LAVA;
    }

    @Override
    public List<Object> getDrops(Object loc, boolean silkTouch) {
        if (!(loc instanceof BlockPos pos)) return List.of();
        BlockState state = world.getBlockState(pos);
        
        List<Object> result = new ArrayList<>();
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        
        if (silkTouch) {
            try {
                var silkTouchEntry = world.getRegistryManager()
                    .get(RegistryKeys.ENCHANTMENT)
                    .getEntry(Enchantments.SILK_TOUCH)
                    .orElse(null);
                if (silkTouchEntry != null) {
                    tool.addEnchantment(silkTouchEntry, 1);
                }
            } catch (Throwable ignored) {}
        }

        try {
            LootContextParameterSet.Builder builder = new LootContextParameterSet.Builder(world);
            builder.add(LootContextParameters.ORIGIN, Vec3d.ofCenter(pos));
            builder.add(LootContextParameters.TOOL, tool);
            
            List<ItemStack> drops = state.getDroppedStacks(builder);
            for (ItemStack drop : drops) {
                if (drop != null && !drop.isEmpty()) {
                    result.add(drop);
                }
            }
        } catch (Throwable ex) {
            result.add(new ItemStack(state.getBlock()));
        }
        
        return result;
    }

    @Override
    public void setBlockAir(Object loc) {
        if (!(loc instanceof BlockPos pos)) return;
        world.setBlockState(pos, Blocks.AIR.getDefaultState());
    }

    @Override
    public void playBreakEffects(Object loc) {
        if (!(loc instanceof BlockPos pos)) return;
        BlockState state = world.getBlockState(pos);
        world.playSound(null, pos, state.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    @Override
    public void sendBlockCrack(Object loc, float progress01) {
        // Fabric doesn't have a simple equivalent for block damage packets
        // Would require custom packet handling
    }

    @Override
    public boolean isInventory(Object loc) {
        if (!(loc instanceof BlockPos pos)) return false;
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity instanceof Inventory;
    }

    @Override
    public Object getInventoryHolder(Object loc) {
        if (!(loc instanceof BlockPos pos)) return null;
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof Inventory) {
            return blockEntity;
        }
        return null;
    }

    @Override
    public int addToInventory(Object inventoryHolder, Object itemStack) {
        if (!(inventoryHolder instanceof Inventory inv)) return 0;
        if (!(itemStack instanceof ItemStack stack)) return 0;
        
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
    public boolean hasSpaceFor(Object inventoryHolder, Object itemStack, Map<String, Integer> inFlight) {
        if (!(inventoryHolder instanceof Inventory inv)) return false;
        if (!(itemStack instanceof ItemStack stack)) return false;

        int remaining = stack.getCount();
        int max = stack.getMaxCount();

        for (int i = 0; i < inv.size(); i++) {
            ItemStack slot = inv.getStack(i);
            if (slot.isEmpty()) {
                remaining -= max;
                if (remaining <= 0) return true;
                continue;
            }
            if (!ItemStack.areItemsAndComponentsEqual(slot, stack)) continue;
            int space = Math.max(0, max - slot.getCount());
            remaining -= space;
            if (remaining <= 0) return true;
        }
        return false;
    }

    @Override
    public String locationKey(Object loc) {
        if (!(loc instanceof BlockPos pos)) return "null";
        return "overworld:" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    @Override
    public double distanceSquared(Object a, Object b) {
        if (!(a instanceof BlockPos pa) || !(b instanceof BlockPos pb)) return Double.MAX_VALUE;
        return pa.getSquaredDistance(pb);
    }

    @Override
    public Object createLocation(Object world, int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    @Override
    public Object worldOf(Object loc) {
        return world;
    }

    @Override
    public Object worldByName(String name) {
        return world;
    }

    @Override
    public int blockX(Object loc) {
        if (loc instanceof BlockPos pos) return pos.getX();
        return 0;
    }

    @Override
    public int blockY(Object loc) {
        if (loc instanceof BlockPos pos) return pos.getY();
        return 0;
    }

    @Override
    public int blockZ(Object loc) {
        if (loc instanceof BlockPos pos) return pos.getZ();
        return 0;
    }

    @Override
    public int maxStackSize(Object itemStack) {
        if (itemStack instanceof ItemStack stack) return stack.getMaxCount();
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
                List<BlockPos> locs = new ArrayList<>();
                for (Object o : waypoints) {
                    if (o instanceof BlockPos pos) locs.add(pos);
                }
                BlockPos dest = destinationInventory instanceof BlockPos pos ? pos : null;
                packetService.enqueue(stack, locs, dest, callback);
            }
        };
    }

    @Override
    public UUID ownerFromPlayer(Object player) {
        if (player instanceof PlayerEntity p) return p.getUuid();
        return new UUID(0, 0);
    }
}
