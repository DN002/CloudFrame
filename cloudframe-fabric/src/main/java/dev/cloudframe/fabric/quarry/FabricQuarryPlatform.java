package dev.cloudframe.fabric.quarry;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.cloudframe.common.quarry.QuarryPlatform;
import dev.cloudframe.common.pipes.ItemPacketManager;
import dev.cloudframe.common.pipes.PipeNetworkManager;
import dev.cloudframe.fabric.pipes.FabricPacketService;
import dev.cloudframe.fabric.power.FabricPowerNetworkManager;
import dev.cloudframe.fabric.content.CloudFrameContent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
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
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.world.World;

public class FabricQuarryPlatform implements QuarryPlatform {

    private final MinecraftServer server;
    private final PipeNetworkManager pipeManager;
    private final ItemPacketManager packetManager;
    private final FabricPacketService packetService;

    private static final int MAX_GLASS_FRAME_UPDATES_PER_TICK = 256;

    private final ArrayDeque<GlassFrameRemovalJob> pendingFrameRemovals = new ArrayDeque<>();

    private static final class GlassFrameRemovalJob {
        final RegistryKey<World> worldKey;
        final int minX;
        final int minZ;
        final int maxX;
        final int maxZ;
        final int y;

        // Perimeter iteration state.
        int stage = 0;
        int cursor = 0;

        GlassFrameRemovalJob(RegistryKey<World> worldKey, int minX, int minZ, int maxX, int maxZ, int y) {
            this.worldKey = worldKey;
            this.minX = Math.min(minX, maxX);
            this.maxX = Math.max(minX, maxX);
            this.minZ = Math.min(minZ, maxZ);
            this.maxZ = Math.max(minZ, maxZ);
            this.y = y;
        }
    }

    public FabricQuarryPlatform(MinecraftServer server, PipeNetworkManager pipeManager, ItemPacketManager packetManager) {
        this.server = server;
        this.pipeManager = pipeManager;
        this.packetManager = packetManager;
        this.packetService = new FabricPacketService(packetManager, server);
    }

    @Override
    public boolean supportsPower() {
        return true;
    }

    @Override
    public long extractPowerCfe(Object controllerLoc, long amount) {
        return FabricPowerNetworkManager.extractPowerCfe(server, controllerLoc, amount);
    }

    @Override
    public boolean hasValidOutput(Object controllerLoc) {
        // First, try the normal cached-network check.
        if (QuarryPlatform.super.hasValidOutput(controllerLoc)) return true;

        // Fabric pipes are real blocks. If the PipeNetworkManager cache is missing entries
        // (e.g., from older worlds or DB reset), lazily index the currently-loaded pipe network
        // starting from any pipe adjacent to the controller.
        if (controllerLoc == null) return false;

        ServerWorld world = worldOf(null, controllerLoc);
        BlockPos ctrlPos = posOf(controllerLoc);
        if (world == null || ctrlPos == null) return false;

        final int maxScanPipes = 8192;

        boolean foundInventory = false;
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> visited = new java.util.HashSet<>();

        for (int[] dir : dev.cloudframe.common.pipes.PipeNetworkManager.DIRS) {
            BlockPos adj = ctrlPos.add(dir[0], dir[1], dir[2]);
            if (world.getBlockState(adj).isOf(CloudFrameContent.getTubeBlock())) {
                queue.add(adj.toImmutable());
            }
        }

        while (!queue.isEmpty() && visited.size() < maxScanPipes) {
            BlockPos pos = queue.pollFirst();
            if (pos == null) continue;
            if (!visited.add(pos)) continue;

            GlobalPos gp = GlobalPos.create(world.getRegistryKey(), pos);
            if (pipeManager != null && pipeManager.getPipe(gp) == null) {
                pipeManager.addPipe(gp);
            }

            for (int[] dir : dev.cloudframe.common.pipes.PipeNetworkManager.DIRS) {
                BlockPos adj = pos.add(dir[0], dir[1], dir[2]);

                // Inventory adjacent to any pipe counts as valid output.
                BlockEntity be = world.getBlockEntity(adj);
                if (be instanceof Inventory) {
                    foundInventory = true;
                }

                // Continue BFS through connected pipe blocks.
                if (world.getBlockState(adj).isOf(CloudFrameContent.getTubeBlock())) {
                    if (!visited.contains(adj)) {
                        queue.add(adj.toImmutable());
                    }
                }
            }
        }

        // Re-check using the now-indexed manager (keeps behavior consistent with routing).
        return foundInventory || QuarryPlatform.super.hasValidOutput(controllerLoc);
    }

    private ServerWorld overworld() {
        return server.getOverworld();
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

            BlockPos next = nextPerimeterPos(job);
            if (next == null) {
                pendingFrameRemovals.pollFirst();
                continue;
            }

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

    /**
     * Returns the next perimeter BlockPos to process, or null when job is complete.
     */
    private BlockPos nextPerimeterPos(GlassFrameRemovalJob job) {
        int width = job.maxX - job.minX;
        int depth = job.maxZ - job.minZ;

        // Degenerate cases (line/point): just scan the rectangle edge logic safely.
        if (width < 0 || depth < 0) return null;

        // stage 0: top edge (z=minZ), x=minX..maxX
        // stage 1: bottom edge (z=maxZ), x=minX..maxX
        // stage 2: left edge (x=minX), z=minZ+1..maxZ-1
        // stage 3: right edge (x=maxX), z=minZ+1..maxZ-1
        while (job.stage <= 3) {
            switch (job.stage) {
                case 0 -> {
                    int x = job.minX + job.cursor;
                    if (x > job.maxX) { job.stage++; job.cursor = 0; continue; }
                    job.cursor++;
                    return new BlockPos(x, job.y, job.minZ);
                }
                case 1 -> {
                    int x = job.minX + job.cursor;
                    if (x > job.maxX) { job.stage++; job.cursor = 0; continue; }
                    job.cursor++;
                    // If minZ == maxZ, stage 0 already covered this line.
                    if (job.minZ == job.maxZ) continue;
                    return new BlockPos(x, job.y, job.maxZ);
                }
                case 2 -> {
                    int z = (job.minZ + 1) + job.cursor;
                    if (z >= job.maxZ) { job.stage++; job.cursor = 0; continue; }
                    job.cursor++;
                    // If minX == maxX, this edge is already covered by top/bottom edges.
                    if (job.minX == job.maxX) continue;
                    return new BlockPos(job.minX, job.y, z);
                }
                case 3 -> {
                    int z = (job.minZ + 1) + job.cursor;
                    if (z >= job.maxZ) { job.stage++; job.cursor = 0; continue; }
                    job.cursor++;
                    if (job.minX == job.maxX) continue;
                    return new BlockPos(job.maxX, job.y, z);
                }
                default -> {
                    return null;
                }
            }
        }

        return null;
    }

    @Override
    public Object normalize(Object loc) {
        if (loc instanceof GlobalPos gp) {
            return GlobalPos.create(gp.dimension(), gp.pos().toImmutable());
        }
        if (loc instanceof BlockPos pos) return pos.toImmutable();
        return loc;
    }

    @Override
    public Object offset(Object loc, int dx, int dy, int dz) {
        if (loc instanceof GlobalPos gp) {
            return GlobalPos.create(gp.dimension(), gp.pos().add(dx, dy, dz));
        }
        if (loc instanceof BlockPos pos) return pos.add(dx, dy, dz);
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
        return state.isSolid() || block == Blocks.WATER || block == Blocks.LAVA;
    }

    @Override
    public List<Object> getDrops(Object loc, boolean silkTouch) {
        BlockPos pos = posOf(loc);
        if (pos == null) return List.of();
        ServerWorld world = worldOf(null, loc);
        BlockState state = world.getBlockState(pos);
        
        List<Object> result = new ArrayList<>();
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        
        if (silkTouch) {
            try {
                RegistryEntryLookup<net.minecraft.enchantment.Enchantment> enchantments =
                    world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                tool.addEnchantment(enchantments.getOrThrow(Enchantments.SILK_TOUCH), 1);
            } catch (Throwable ignored) {}
        }

        try {
            LootWorldContext.Builder builder = new LootWorldContext.Builder(world);
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
        BlockPos pos = posOf(loc);
        if (pos == null) return "null";
        String dim = "minecraft:overworld";
        if (loc instanceof GlobalPos gp) {
            dim = gp.dimension().getValue().toString();
        }
        return dim + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
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
        return overworld().getRegistryKey();
    }

    @Override
    public Object worldByName(String name) {
        if (name == null || name.isBlank()) {
            return overworld().getRegistryKey();
        }
        try {
            Identifier id = Identifier.of(name);
            return RegistryKey.of(RegistryKeys.WORLD, id);
        } catch (Throwable ignored) {
            return overworld().getRegistryKey();
        }
    }

    @Override
    public String worldName(Object world) {
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
        return new ItemPacketFactory() {
            @Override
            public void send(Object itemStack, List<Object> waypoints, Object destinationInventory, DeliveryCallback callback) {
                if (!(itemStack instanceof ItemStack stack)) return;
                packetService.enqueue(stack, waypoints, destinationInventory, callback);
            }
        };
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
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean onEdge = (x == minX || x == maxX || z == minZ || z == maxZ);
                if (!onEdge) continue;

                BlockPos pos = new BlockPos(x, y, z);

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
