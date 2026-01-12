package dev.cloudframe.fabric.quarry;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.cloudframe.common.quarry.QuarryPlatform;
import dev.cloudframe.common.pipes.ItemPacketManager;
import dev.cloudframe.common.pipes.PipeNetworkManager;
import dev.cloudframe.common.pipes.PipeConnectivityAccess;
import dev.cloudframe.common.pipes.PipeOutputScanner;
import dev.cloudframe.common.platform.items.InventoryCapacity;
import dev.cloudframe.common.platform.items.InventoryInsert;
import dev.cloudframe.common.platform.items.ItemStackKeyAdapter;
import dev.cloudframe.common.platform.items.SlottedInventoryAdapter;
import dev.cloudframe.common.platform.world.LocationKeyAdapter;
import dev.cloudframe.common.platform.world.LocationNormalizationPolicy;
import dev.cloudframe.common.platform.world.WorldKeyAdapter;
import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.util.DebugManager;
import dev.cloudframe.common.util.RectPerimeter;
import dev.cloudframe.common.quarry.DefaultItemPacketFactory;
import dev.cloudframe.fabric.CloudFrameFabric;
import dev.cloudframe.fabric.content.TubeBlock;
import dev.cloudframe.fabric.pipes.FabricItemStackAdapter;
import dev.cloudframe.fabric.power.FabricPowerNetworkManager;
import dev.cloudframe.fabric.content.CloudFrameContent;
import dev.cloudframe.common.trash.TrashSink;
import dev.cloudframe.fabric.quarry.controller.QuarryControllerBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.world.World;

public class FabricQuarryPlatform implements QuarryPlatform {

    private static final Debug debug = DebugManager.get(FabricQuarryPlatform.class);
    private static final AtomicBoolean warnedBlockPos = new AtomicBoolean(false);

    private final MinecraftServer server;
    private final PipeNetworkManager pipeManager;
    private final ItemPacketManager packetManager;

    private static final int MAX_GLASS_FRAME_UPDATES_PER_TICK = 256;

    private final ArrayDeque<GlassFrameRemovalJob> pendingFrameRemovals = new ArrayDeque<>();

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

    private static final class GlassFrameRemovalJob {
        final RegistryKey<World> worldKey;
        final int minX;
        final int minZ;
        final int maxX;
        final int maxZ;
        final int y;

        final int total;
        int index = 0;

        GlassFrameRemovalJob(RegistryKey<World> worldKey, int minX, int minZ, int maxX, int maxZ, int y) {
            this.worldKey = worldKey;
            this.minX = Math.min(minX, maxX);
            this.maxX = Math.max(minX, maxX);
            this.minZ = Math.min(minZ, maxZ);
            this.maxZ = Math.max(minZ, maxZ);
            this.y = y;

            this.total = RectPerimeter.count(this.minX, this.minZ, this.maxX, this.maxZ);
        }
    }

    public FabricQuarryPlatform(MinecraftServer server, PipeNetworkManager pipeManager, ItemPacketManager packetManager) {
        this.server = server;
        this.pipeManager = pipeManager;
        this.packetManager = packetManager;
    }

    @Override
    public boolean supportsPower() {
        return true;
    }

    @Override
    public long extractPowerCfe(Object controllerLoc, long amount) {
        if (amount <= 0L) return 0L;

        long fromNetwork = FabricPowerNetworkManager.extractPowerCfe(server, controllerLoc, amount);
        if (fromNetwork >= amount) {
            // If the network can satisfy the quarry, keep the controller-local buffer topped off.
            // This buffer is intended to behave like a short hold-up capacitor at the controller.
            if (controllerLoc instanceof GlobalPos gp) {
                ServerWorld w = server.getWorld(gp.dimension());
                if (w != null) {
                    BlockEntity be = w.getBlockEntity(gp.pos());
                    if (be instanceof QuarryControllerBlockEntity qbe) {
                        long cap = qbe.getPowerBufferCapacityCfe();
                        long stored = qbe.getPowerBufferStoredCfe();
                        long missing = Math.max(0L, cap - stored);
                        if (missing > 0L) {
                            long got = FabricPowerNetworkManager.extractPowerCfe(server, controllerLoc, missing);
                            if (got > 0L) {
                                qbe.insertPowerToBuffer(got);
                            }
                        }
                    }
                }
            }

            return fromNetwork;
        }

        // If the network couldn't supply the full amount, fall back to the controller-local buffer.
        if (!(controllerLoc instanceof GlobalPos gp)) return fromNetwork;
        ServerWorld w = server.getWorld(gp.dimension());
        if (w == null) return fromNetwork;
        BlockEntity be = w.getBlockEntity(gp.pos());
        if (!(be instanceof QuarryControllerBlockEntity qbe)) return fromNetwork;

        long remaining = amount - Math.max(0L, fromNetwork);
        if (remaining <= 0L) return fromNetwork;

        long before = qbe.getPowerBufferStoredCfe();
        long fromBuffer = qbe.extractPowerFromBuffer(remaining);

        // Debug: if we ever fall back to the buffer (especially large pulls), log it.
        // Also: during controller power debug window, log every call regardless of size.
        if (qbe.isPowerDebugActive() || fromBuffer > 0L || remaining >= 1000L) {
            String where = "";
            try {
                where = "dim=" + gp.dimension().getValue() + ", pos=" + gp.pos() + ": ";
            } catch (Throwable t) {
                // ignore
            }
            debug.log(
                "extractPowerCfe",
                where + "req=" + amount
                    + ", net=" + fromNetwork
                    + ", remaining=" + remaining
                    + ", bufBefore=" + before
                    + ", bufAfter=" + qbe.getPowerBufferStoredCfe()
                    + ", speed=" + qbe.getSpeedLevel()
            );
        }
        return fromNetwork + Math.max(0L, fromBuffer);
    }

    @Override
    public boolean hasValidOutput(Object controllerLoc) {
        if (!(controllerLoc instanceof GlobalPos gp)) return false;
        if (CloudFrameContent.getCloudPipeBlock() == null) return false;

        PipeConnectivityAccess access = new PipeConnectivityAccess() {
            @Override
            public Object normalize(Object loc) {
                if (loc instanceof GlobalPos p) {
                    return GlobalPos.create(p.dimension(), p.pos().toImmutable());
                }
                return loc;
            }

            @Override
            public boolean isChunkLoaded(Object loc) {
                if (!(loc instanceof GlobalPos p)) return false;
                ServerWorld w = server.getWorld(p.dimension());
                if (w == null) return false;
                BlockPos pos = p.pos();
                return w.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
            }

            @Override
            public Object offset(Object loc, int dirIndex) {
                if (!(loc instanceof GlobalPos p)) return null;
                Direction dir = dirFromIndex(dirIndex);
                if (dir == null) return null;
                return GlobalPos.create(p.dimension(), p.pos().offset(dir).toImmutable());
            }

            @Override
            public boolean isPipeAt(Object loc) {
                if (!(loc instanceof GlobalPos p)) return false;
                ServerWorld w = server.getWorld(p.dimension());
                if (w == null) return false;
                if (!isChunkLoaded(p)) return false;
                return w.getBlockState(p.pos()).isOf(CloudFrameContent.getCloudPipeBlock());
            }

            @Override
            public boolean pipeConnects(Object pipeLoc, int dirIndex) {
                if (!(pipeLoc instanceof GlobalPos p)) return false;
                ServerWorld w = server.getWorld(p.dimension());
                if (w == null) return false;
                if (!isChunkLoaded(p)) return false;
                BlockState state = w.getBlockState(p.pos());
                if (!state.isOf(CloudFrameContent.getCloudPipeBlock())) return false;
                Direction dir = dirFromIndex(dirIndex);
                return dir != null && tubeConnects(state, dir);
            }

            @Override
            public boolean isInventoryAt(Object loc) {
                if (!(loc instanceof GlobalPos p)) return false;
                ServerWorld w = server.getWorld(p.dimension());
                if (w == null) return false;
                if (!isChunkLoaded(p)) return false;
                BlockEntity be = w.getBlockEntity(p.pos());
                return be instanceof Inventory;
            }

            private Direction dirFromIndex(int dirIndex) {
                return switch (dirIndex) {
                    case 0 -> Direction.EAST;
                    case 1 -> Direction.WEST;
                    case 2 -> Direction.UP;
                    case 3 -> Direction.DOWN;
                    case 4 -> Direction.SOUTH;
                    case 5 -> Direction.NORTH;
                    default -> null;
                };
            }
        };

        return PipeOutputScanner.hasValidOutputFrom(gp, access, 8192);
    }

    private static boolean tubeConnects(BlockState tubeState, Direction dir) {
        if (tubeState == null || dir == null) return false;
        return switch (dir) {
            case NORTH -> tubeState.get(TubeBlock.NORTH);
            case SOUTH -> tubeState.get(TubeBlock.SOUTH);
            case EAST -> tubeState.get(TubeBlock.EAST);
            case WEST -> tubeState.get(TubeBlock.WEST);
            case UP -> tubeState.get(TubeBlock.UP);
            case DOWN -> tubeState.get(TubeBlock.DOWN);
        };
    }

    @Override
    public boolean allowsPipeFilter(Object pipeLocAdjacentToInventory, Object inventoryLoc, Object itemStack) {
        if (!(itemStack instanceof ItemStack stack)) return true;

        CloudFrameFabric instance = CloudFrameFabric.instance();
        if (instance == null || instance.getPipeFilterManager() == null) return true;

        ServerWorld world = worldOf(null, pipeLocAdjacentToInventory);
        BlockPos pipePos = posOf(pipeLocAdjacentToInventory);
        if (world == null || pipePos == null) return true;

        BlockPos invPos = posOf(inventoryLoc);
        if (invPos == null) return true;

        int sideIndex = dirIndexBetween(pipePos, invPos);
        if (sideIndex < 0) return true;

        GlobalPos gp = GlobalPos.create(world.getRegistryKey(), pipePos.toImmutable());
        return instance.getPipeFilterManager().allows(gp, sideIndex, stack);
    }

    @Override
    public boolean dropItemAtController(Object controllerLoc, Object itemStack) {
        if (!(itemStack instanceof ItemStack stack)) return false;
        if (stack.isEmpty() || stack.getCount() <= 0) return true;

        BlockPos pos = posOf(controllerLoc);
        if (pos == null) return false;
        ServerWorld world = worldOf(null, controllerLoc);
        if (world == null) return false;

        Vec3d spawnPos = Vec3d.ofCenter(pos).add(0.0, 0.25, 0.0);
        ItemEntity itemEntity = new ItemEntity(world, spawnPos.x, spawnPos.y, spawnPos.z, stack.copy());
        itemEntity.setVelocity(Vec3d.ZERO);
        world.spawnEntity(itemEntity);
        return true;
    }

    private ServerWorld overworld() {
        return server.getOverworld();
    }

    private static void warnBlockPosOnce(String methodName) {
        LocationNormalizationPolicy.warnAssumingDefaultWorld(
                warnedBlockPos,
                debug,
                methodName,
                "BlockPos",
                World.OVERWORLD.getValue().toString()
        );
    }

    private BlockPos posOf(Object loc) {
        if (loc instanceof GlobalPos gp) return gp.pos();
        if (loc instanceof BlockPos pos) return pos;
        return null;
    }

    private RegistryKey<World> keyOf(Object worldObj) {
        if (worldObj instanceof GlobalPos gp) return gp.dimension();
        if (worldObj instanceof ServerWorld sw) return sw.getRegistryKey();
        if (worldObj instanceof RegistryKey<?> k) {
            try {
                @SuppressWarnings("unchecked")
                RegistryKey<World> wk = (RegistryKey<World>) k;
                return wk;
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private RegistryKey<World> keyOf(Object worldObj, Object loc) {
        if (loc instanceof GlobalPos gp) return gp.dimension();
        RegistryKey<World> key = keyOf(worldObj);
        if (key != null) return key;
        return overworld().getRegistryKey();
    }

    private ServerWorld worldOf(Object worldObj, Object loc) {
        if (worldObj instanceof ServerWorld sw) return sw;
        if (loc instanceof GlobalPos gp) {
            ServerWorld w = server.getWorld(gp.dimension());
            return w != null ? w : overworld();
        }
        if (worldObj instanceof RegistryKey<?> k) {
            try {
                @SuppressWarnings("unchecked")
                RegistryKey<World> wk = (RegistryKey<World>) k;
                ServerWorld w = server.getWorld(wk);
                return w != null ? w : overworld();
            } catch (Throwable ignored) {
                return overworld();
            }
        }
        return overworld();
    }

    /**
     * Process pending glass-frame removals incrementally to avoid lag spikes.
     * Called once per server tick from the mod tick handler.
     */
    public void tickFrameRemovalJobs() {
        if (pendingFrameRemovals.isEmpty()) return;

        int budget = MAX_GLASS_FRAME_UPDATES_PER_TICK;
        while (budget > 0 && !pendingFrameRemovals.isEmpty()) {
            GlassFrameRemovalJob job = pendingFrameRemovals.peekFirst();
            if (job == null) break;

            ServerWorld world = server.getWorld(job.worldKey);
            if (world == null) {
                pendingFrameRemovals.pollFirst();
                continue;
            }

            if (job.total <= 0 || job.index >= job.total) {
                pendingFrameRemovals.pollFirst();
                continue;
            }

            RectPerimeter.Pos nextXZ = RectPerimeter.at(job.minX, job.minZ, job.maxX, job.maxZ, job.index);
            job.index++;
            if (nextXZ == null) {
                pendingFrameRemovals.pollFirst();
                continue;
            }

            BlockPos next = new BlockPos(nextXZ.x(), job.y, nextXZ.z());

            // Force-load chunk so removal is reliable after restarts / large frames.
            int cx = next.getX() >> 4;
            int cz = next.getZ() >> 4;
            if (!world.isChunkLoaded(cx, cz)) {
                world.getChunk(cx, cz);
            }

            if (world.getBlockState(next).isOf(net.minecraft.block.Blocks.GLASS)) {
                world.setBlockState(next, Blocks.AIR.getDefaultState(), 3);
            }
            budget--;
        }
    }

    @Override
    public Object normalize(Object loc) {
        if (loc instanceof GlobalPos gp) {
            return GlobalPos.create(gp.dimension(), gp.pos().toImmutable());
        }
        if (loc instanceof BlockPos pos) {
            warnBlockPosOnce("normalize");
            return GlobalPos.create(World.OVERWORLD, pos.toImmutable());
        }
        return loc;
    }

    @Override
    public Object offset(Object loc, int dx, int dy, int dz) {
        if (loc instanceof GlobalPos gp) {
            return GlobalPos.create(gp.dimension(), gp.pos().add(dx, dy, dz));
        }
        if (loc instanceof BlockPos pos) {
            warnBlockPosOnce("offset");
            return GlobalPos.create(World.OVERWORLD, pos.add(dx, dy, dz));
        }
        return loc;
    }

    @Override
    public boolean isChunkLoaded(Object loc) {
        BlockPos pos = posOf(loc);
        if (pos == null) return false;
        ServerWorld world = worldOf(null, loc);
        return world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Override
    public boolean isRedstonePowered(Object loc) {
        BlockPos pos = posOf(loc);
        if (pos == null) return false;
        ServerWorld world = worldOf(null, loc);
        try {
            if (world.isReceivingRedstonePower(pos)) return true;
        } catch (Throwable ignored) {
            // fall through
        }

        int direct = 0;
        try {
            direct = world.getReceivedRedstonePower(pos);
        } catch (Throwable ignored) {
            direct = 0;
        }
        if (direct > 0) return true;

        // Some blocks (and some mappings/versions) can be finicky with isReceivingRedstonePower;
        // check neighbors emitting into us.
        try {
            for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
                BlockPos from = pos.offset(dir);
                int emitted = world.getEmittedRedstonePower(from, dir.getOpposite());
                if (emitted > 0) return true;
            }
        } catch (Throwable ignored) {
            // best-effort
        }

        return false;
    }

    @Override
    public void setChunkForced(Object worldObj, int chunkX, int chunkZ, boolean forced) {
        // Fabric: force-load/unforce-load chunks via vanilla forced-chunk API.
        // This keeps the quarry running when players leave.
        try {
            ServerWorld w = worldOf(worldObj, null);
            if (w != null) {
                w.setChunkForced(chunkX, chunkZ, forced);
            }
        } catch (Throwable ignored) {
            // Best-effort; some environments may restrict this.
        }
    }

    @Override
    public boolean isMineable(Object loc) {
        BlockPos pos = posOf(loc);
        if (pos == null) return false;
        ServerWorld world = worldOf(null, loc);
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        // Never mine air/bedrock. Glass is mineable; the common-layer quarry logic prevents
        // mining the glass *frame ring* specifically so players can place glass inside the
        // mined area and have it removed.
        if (block == Blocks.AIR || block == Blocks.BEDROCK) return false;
        
        // CloudFrame blocks have custom shapes and don't fill the block, so state.isSolid() is false.
        // Explicitly check for CloudFrame placeable blocks.
        if (block == CloudFrameContent.getCloudPipeBlock()) return true;
        if (block == CloudFrameContent.getCloudCableBlock()) return true;
        if (block == CloudFrameContent.TRASH_CAN_BLOCK) return true;
        if (block == CloudFrameContent.getQuarryControllerBlock()) return true;
        if (block == CloudFrameContent.getStratusPanelBlock()) return true;
        if (block == CloudFrameContent.getCloudTurbineBlock()) return true;
        if (block == CloudFrameContent.getCloudCellBlock()) return true;
        
        return state.isSolid() || block == Blocks.WATER || block == Blocks.LAVA;
    }

    @Override
    public List<Object> getDrops(Object loc, boolean silkTouch) {
        return getDrops(loc, silkTouch, 0);
    }

    @Override
    public List<Object> getDrops(Object loc, boolean silkTouch, int fortuneLevel) {
        BlockPos pos = posOf(loc);
        if (pos == null) return List.of();
        ServerWorld world = worldOf(null, loc);
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        
        List<Object> result = new ArrayList<>();
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        
        if (silkTouch) {
            try {
                RegistryEntryLookup<net.minecraft.enchantment.Enchantment> enchantments =
                    world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                tool.addEnchantment(enchantments.getOrThrow(Enchantments.SILK_TOUCH), 1);
            } catch (Throwable ignored) {}
        } else {
            int f = Math.max(0, Math.min(3, fortuneLevel));
            if (f > 0) {
                try {
                    RegistryEntryLookup<net.minecraft.enchantment.Enchantment> enchantments =
                        world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                    tool.addEnchantment(enchantments.getOrThrow(Enchantments.FORTUNE), f);
                } catch (Throwable ignored) {}
            }
        }

        try {
            LootWorldContext.Builder builder = new LootWorldContext.Builder(world);
            builder.add(LootContextParameters.ORIGIN, Vec3d.ofCenter(pos));
            builder.add(LootContextParameters.TOOL, tool);
            
            List<ItemStack> drops = state.getDroppedStacks(builder);
            
            // If loot table didn't return anything for CloudFrame blocks, manually create drops
            if ((drops == null || drops.isEmpty()) && isCloudFrameBlock(block)) {
                drops = new ArrayList<>();
                if (block == CloudFrameContent.getCloudPipeBlock()) {
                    drops.add(new ItemStack(CloudFrameContent.CLOUD_PIPE));
                } else if (block == CloudFrameContent.getCloudCableBlock()) {
                    drops.add(new ItemStack(CloudFrameContent.CLOUD_CABLE));
                } else if (block == CloudFrameContent.TRASH_CAN_BLOCK) {
                    drops.add(new ItemStack(CloudFrameContent.TRASH_CAN));
                } else if (block == CloudFrameContent.getQuarryControllerBlock()) {
                    drops.add(new ItemStack(CloudFrameContent.QUARRY_CONTROLLER));
                } else if (block == CloudFrameContent.getStratusPanelBlock()) {
                    drops.add(new ItemStack(CloudFrameContent.STRATUS_PANEL));
                } else if (block == CloudFrameContent.getCloudTurbineBlock()) {
                    drops.add(new ItemStack(CloudFrameContent.CLOUD_TURBINE));
                } else if (block == CloudFrameContent.getCloudCellBlock()) {
                    drops.add(new ItemStack(CloudFrameContent.CLOUD_CELL));
                }
            }
            
            for (ItemStack drop : drops) {
                if (drop != null && !drop.isEmpty()) {
                    result.add(drop);
                }
            }
        } catch (Throwable ex) {
            // On error, fall back to creating the block item
            if (isCloudFrameBlock(block)) {
                if (block == CloudFrameContent.getCloudPipeBlock()) {
                    result.add(new ItemStack(CloudFrameContent.CLOUD_PIPE));
                } else if (block == CloudFrameContent.getCloudCableBlock()) {
                    result.add(new ItemStack(CloudFrameContent.CLOUD_CABLE));
                } else if (block == CloudFrameContent.TRASH_CAN_BLOCK) {
                    result.add(new ItemStack(CloudFrameContent.TRASH_CAN));
                } else if (block == CloudFrameContent.getQuarryControllerBlock()) {
                    result.add(new ItemStack(CloudFrameContent.QUARRY_CONTROLLER));
                } else if (block == CloudFrameContent.getStratusPanelBlock()) {
                    result.add(new ItemStack(CloudFrameContent.STRATUS_PANEL));
                } else if (block == CloudFrameContent.getCloudTurbineBlock()) {
                    result.add(new ItemStack(CloudFrameContent.CLOUD_TURBINE));
                } else if (block == CloudFrameContent.getCloudCellBlock()) {
                    result.add(new ItemStack(CloudFrameContent.CLOUD_CELL));
                }
            } else {
                result.add(new ItemStack(block));
            }
        }
        
        return result;
    }
    
    private boolean isCloudFrameBlock(Block block) {
        return block == CloudFrameContent.getCloudPipeBlock() || 
               block == CloudFrameContent.getCloudCableBlock() || 
               block == CloudFrameContent.TRASH_CAN_BLOCK || 
               block == CloudFrameContent.getQuarryControllerBlock() ||
               block == CloudFrameContent.getStratusPanelBlock() || 
               block == CloudFrameContent.getCloudTurbineBlock() ||
               block == CloudFrameContent.getCloudCellBlock();
    }

    @Override
    public void setBlockAir(Object loc) {
        BlockPos pos = posOf(loc);
        if (pos == null) return;
        ServerWorld world = worldOf(null, loc);
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
    }

    @Override
    public void playBreakEffects(Object loc) {
        BlockPos pos = posOf(loc);
        if (pos == null) return;
        ServerWorld world = worldOf(null, loc);
        BlockState state = world.getBlockState(pos);
        // Vanilla break event: spawns particles and plays the appropriate sound.
        try {
            world.syncWorldEvent(null, 2001, pos, Block.getRawIdFromState(state));
        } catch (Throwable ignored) {
            // Fallback: sound only.
            world.playSound(null, pos, state.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
    }

    @Override
    public void sendBlockCrack(Object loc, float progress01) {
        BlockPos pos = posOf(loc);
        if (pos == null) return;
        ServerWorld world = worldOf(null, loc);

        // Render the vanilla cracking overlay.
        // Uses a deterministic id so cracks update cleanly per-position.
        int stage = Math.round(Math.max(0.0f, Math.min(1.0f, progress01)) * 9.0f);
        if (progress01 <= 0.0f) stage = -1;

        long key = pos.asLong();
        int id = 0x4C46514B ^ (int) (key ^ (key >>> 32));
        try {
            world.setBlockBreakingInfo(id, pos, stage);
        } catch (Throwable ignored) {
            // best-effort; not critical
        }
    }

    @Override
    public boolean isInventory(Object loc) {
        BlockPos pos = posOf(loc);
        if (pos == null) return false;
        ServerWorld world = worldOf(null, loc);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity instanceof Inventory;
    }

    @Override
    public Object getInventoryHolder(Object loc) {
        BlockPos pos = posOf(loc);
        if (pos == null) return null;
        ServerWorld world = worldOf(null, loc);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof Inventory) {
            return blockEntity;
        }
        return null;
    }

    @Override
    public int addToInventory(Object inventoryHolder, Object itemStack) {
        if (inventoryHolder instanceof TrashSink trash) {
            return trash.accept(itemStack);
        }
        if (!(inventoryHolder instanceof Inventory inv)) return 0;
        if (!(itemStack instanceof ItemStack stack)) return 0;

        return InventoryInsert.addItem(inv, stack, INVENTORY, FabricItemStackAdapter.INSTANCE);
    }

    @Override
    public int totalRoomFor(Object inventoryHolder, Object itemStack) {
        if (inventoryHolder instanceof TrashSink && itemStack instanceof ItemStack stack) {
            // Always accept full stack; this is a sink.
            return Math.max(0, stack.getCount());
        }
        if (!(inventoryHolder instanceof Inventory inv)) return 0;
        if (!(itemStack instanceof ItemStack stack)) return 0;

        return InventoryCapacity.totalRoomFor(inv, stack, INVENTORY, FabricItemStackAdapter.INSTANCE);
    }

    @Override
    public int emptySlotCount(Object inventoryHolder) {
        if (inventoryHolder instanceof TrashSink) {
            return 1;
        }
        if (!(inventoryHolder instanceof Inventory inv)) return 0;
        return InventoryCapacity.emptySlotCount(inv, INVENTORY, FabricItemStackAdapter.INSTANCE);
    }

    @Override
    public LocationKeyAdapter<Object> locationKeyAdapter() {
        return (obj) -> {
            BlockPos pos = posOf(obj);
            if (pos == null) return "null";
            String dim = "minecraft:overworld";
            if (obj instanceof GlobalPos gp) {
                dim = gp.dimension().getValue().toString();
            }
            return dim + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        };
    }

    @Override
    public ItemStackKeyAdapter<Object> itemKeyAdapter() {
        return (obj) -> {
            if (!(obj instanceof ItemStack stack)) return "null";
            return FabricItemStackAdapter.INSTANCE.key(stack);
        };
    }

    @Override
    public double distanceSquared(Object a, Object b) {
        if (a instanceof GlobalPos ga && b instanceof GlobalPos gb) {
            if (!ga.dimension().equals(gb.dimension())) return Double.MAX_VALUE;
            return ga.pos().getSquaredDistance(gb.pos());
        }
        if (a instanceof BlockPos pa && b instanceof BlockPos pb) {
            return pa.getSquaredDistance(pb);
        }
        BlockPos pa = posOf(a);
        BlockPos pb = posOf(b);
        if (pa == null || pb == null) return Double.MAX_VALUE;
        return pa.getSquaredDistance(pb);
    }

    @Override
    public Object createLocation(Object world, int x, int y, int z) {
        RegistryKey<World> key = keyOf(world);
        if (key == null) key = overworld().getRegistryKey();
        return GlobalPos.create(key, new BlockPos(x, y, z));
    }

    @Override
    public Object worldOf(Object loc) {
        if (loc instanceof GlobalPos gp) return gp.dimension();
        if (loc instanceof BlockPos) {
            warnBlockPosOnce("worldOf");
        }
        return overworld().getRegistryKey();
    }

    @Override
    public Object worldByName(String name) {
        return QuarryPlatform.super.worldByName(name);
    }

    @Override
    public String worldName(Object world) {
        return QuarryPlatform.super.worldName(world);
    }

    @Override
    public WorldKeyAdapter<Object> worldKeyAdapter() {
        return new WorldKeyAdapter<>() {
            @Override
            public String key(Object world) {
                if (world instanceof ServerWorld sw) {
                    return sw.getRegistryKey().getValue().toString();
                }
                if (world instanceof RegistryKey<?> k) {
                    try {
                        @SuppressWarnings("unchecked")
                        RegistryKey<World> wk = (RegistryKey<World>) k;
                        return wk.getValue().toString();
                    } catch (Throwable ignored) {
                        return "minecraft:overworld";
                    }
                }
                return "minecraft:overworld";
            }

            @Override
            public Object worldByKey(String key) {
                if (key == null || key.isBlank()) {
                    return overworld().getRegistryKey();
                }
                try {
                    Identifier id = Identifier.of(key);
                    return RegistryKey.of(RegistryKeys.WORLD, id);
                } catch (Throwable ignored) {
                    return overworld().getRegistryKey();
                }
            }
        };
    }

    @Override
    public int blockX(Object loc) {
        BlockPos pos = posOf(loc);
        if (pos != null) return pos.getX();
        return 0;
    }

    @Override
    public int blockY(Object loc) {
        BlockPos pos = posOf(loc);
        if (pos != null) return pos.getY();
        return 0;
    }

    @Override
    public int blockZ(Object loc) {
        BlockPos pos = posOf(loc);
        if (pos != null) return pos.getZ();
        return 0;
    }

    public int stackAmount(Object itemStack) {
        if (itemStack instanceof ItemStack stack) return stack.getCount();
        return 0;
    }

    public Object copyWithAmount(Object itemStack, int amount) {
        if (itemStack instanceof ItemStack stack) {
            ItemStack copy = stack.copy();
            copy.setCount(Math.max(0, amount));
            return copy;
        }
        return itemStack;
    }

    @Override
    public int maxStackSize(Object itemStack) {
        if (itemStack instanceof ItemStack stack) return stack.getMaxCount();
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
        return new DefaultItemPacketFactory(
                packetManager,
                () -> new dev.cloudframe.fabric.pipes.FabricPacketVisuals(server),
                FabricItemStackAdapter.INSTANCE
        );
    }

    @Override
    public UUID ownerFromPlayer(Object player) {
        if (player instanceof PlayerEntity p) return p.getUuid();
        return new UUID(0, 0);
    }
    
    @Override
    public void placeGlassFrame(Object worldObj, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        ServerWorld world = worldOf(worldObj, null);
        // Create a visible glass frame (2D ring) on the top layer around the quarry region.
        // This matches the marker-selection perimeter shown to the player.
        int placedCount = 0;
        int y = maxY;
        int lastCx = Integer.MIN_VALUE;
        int lastCz = Integer.MIN_VALUE;
        int total = RectPerimeter.count(minX, minZ, maxX, maxZ);
        for (int i = 0; i < total; i++) {
            RectPerimeter.Pos xz = RectPerimeter.at(minX, minZ, maxX, maxZ, i);
            if (xz == null) continue;

            BlockPos pos = new BlockPos(xz.x(), y, xz.z());

                // Ensure the chunk is loaded so the frame is consistently placed.
                int cx = pos.getX() >> 4;
                int cz = pos.getZ() >> 4;
                if (cx != lastCx || cz != lastCz) {
                    lastCx = cx;
                    lastCz = cz;
                    if (!world.isChunkLoaded(cx, cz)) world.getChunk(cx, cz);
                }

                BlockState current = world.getBlockState(pos);

                // Only replace air blocks
                if (current.isAir()) {
                    world.setBlockState(pos, net.minecraft.block.Blocks.GLASS.getDefaultState(), 3);
                    placedCount++;
                }
        }
        dev.cloudframe.common.util.DebugManager.get(FabricQuarryPlatform.class).log(
            "placeGlassFrame", 
            "Placed " + placedCount + " glass blocks for frame bounds: (" + 
            minX + "," + minY + "," + minZ + ") to (" + maxX + "," + maxY + "," + maxZ + ")"
        );
    }
    
    @Override
    public void removeGlassFrame(Object worldObj, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        // Queue incremental removal of the 2D ring on the top layer.
        // This avoids lag spikes for large frames and still force-loads chunks as needed.
        RegistryKey<World> key = keyOf(worldObj);
        if (key == null) key = overworld().getRegistryKey();
        pendingFrameRemovals.addLast(new GlassFrameRemovalJob(key, minX, minZ, maxX, maxZ, maxY));
    }
    
    @Override
    public boolean isGlassFrameBlock(Object loc) {
        BlockPos pos = posOf(loc);
        if (pos == null) return false;
        ServerWorld world = worldOf(null, loc);
        BlockState state = world.getBlockState(pos);
        return state.isOf(net.minecraft.block.Blocks.GLASS);
    }
}
