package dev.cloudframe.common.quarry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.cloudframe.common.pipes.PipeNetworkManager;
import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.util.DebugFlags;
import dev.cloudframe.common.util.DebugManager;
import dev.cloudframe.common.util.Region;

/**
 * Platform-agnostic Quarry scaffold. Mining logic will be filled in with platform hooks.
 */
public class Quarry {

    private static final Debug debug = DebugManager.get(Quarry.class);
    private static final int[][] DIRS = PipeNetworkManager.DIRS;
    private static final int BASE_MINE_TICKS_PER_BLOCK = 12;

    // Power model (CFE): constant energy-per-block, converted to per-tick draw.
    private static final int ENERGY_PER_BLOCK_CFE = 480;

    private final UUID owner;
    private final String ownerName;
    private final Object posA;
    private final Object posB;
    private final Object controller;
    private final int controllerYaw;
    private final Region region;
    private final QuarryPlatform platform;
    private final Object world;

    // Optional visual frame bounds (may differ from mining region bounds).
    // When set, create/remove glass frame uses these bounds instead of region.
    private Integer frameMinX;
    private Integer frameMinZ;
    private Integer frameMaxX;
    private Integer frameMaxZ;

    private boolean active;

    /**
     * Redstone control mode:
     * 0 = Always On (manual toggle only)
     * 1 = Redstone Enabled (runs only when powered)
     * 2 = Redstone Disabled (runs only when NOT powered)
     */
    private int redstoneMode = 0;

    /** If enabled, keeps the mining region and a 1-chunk radius around the controller loaded. */
    private boolean chunkLoadingEnabled = false;

    /** If enabled, suppresses noisy effects (sound / crack visuals) from mining. */
    private boolean silentMode = false;

    // Tracks which chunks we have force-loaded so we can reliably unload them.
    private final java.util.Set<Long> forcedChunkKeys = new java.util.HashSet<>();

    // Computed each tick for UI.
    private boolean redstoneBlocked = false;
    private boolean redstonePowered = false;
    private boolean outputJammed = false;
    private int outputJamTicks = 0;

    // Computed each tick for UI.
    private boolean powerBlocked = false;
    private long powerRequiredCfePerTick = 0L;
    private long powerReceivedCfePerTick = 0L;

    private boolean outputRoundRobin = true;
    private boolean silkTouchAugment = false;
    private int speedAugmentLevel = 0;

    private int currentX;
    private int currentY;
    private int currentZ;

    private int scanX;
    private int scanY;
    private int scanZ;

    // True once we've completed at least one full scan pass and reset to the start.
    // When true, the entire region is considered "already processed" for dirty detection.
    private boolean scanWrapped;

    private boolean isScanning;
    private float mineProgress;
    private long tickCounter;

    // Active scanning: if blocks are placed in already-mined sections, mine them promptly.
    private static final int DIRTY_QUEUE_LIMIT = 512;
    // Keep dirty targets ordered in the same scan order as normal quarry mining
    // (y desc, x asc, z asc) so detours look consistent instead of random.
    private final java.util.NavigableSet<Long> dirtyBlocks = new java.util.TreeSet<>(Quarry::comparePackedScanOrder);

    // Fallback dirty scanning (works even if platform events/mixins aren't available).
    private static final int DIRTY_SCAN_STEPS_PER_TICK = 4096;
    private int dirtyScanX;
    private int dirtyScanY;
    private int dirtyScanZ;

    private int blocksMined;
    private int totalBlocks;
    private boolean totalBlocksComputed;

    // When true, the current target is a dirty (re-mine) detour. We do not count
    // these toward region completion metrics.
    private boolean currentTargetIsDirty;

    private final List<Object> outputBuffer = new ArrayList<>();
    private final Map<String, Integer> inFlightByDestination = new HashMap<>();
    private final Map<String, Integer> inFlightByDestinationAndItem = new HashMap<>();
    private int outputInventoryCursor = 0;

    public Quarry(UUID owner, String ownerName, Object posA, Object posB, Region region, Object controller, int controllerYaw, QuarryPlatform platform) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.posA = platform.normalize(posA);
        this.posB = platform.normalize(posB);
        this.region = region;
        this.controller = platform.normalize(controller);
        this.controllerYaw = controllerYaw;
        this.platform = platform;
        this.world = platform.worldOf(this.controller);

        this.currentX = region.minX();
        this.currentZ = region.minZ();
        this.currentY = region.maxY();

        this.scanX = region.minX();
        this.scanZ = region.minZ();
        this.scanY = region.maxY();

        this.dirtyScanX = region.minX();
        this.dirtyScanZ = region.minZ();
        this.dirtyScanY = region.maxY();

        this.scanWrapped = false;

        this.blocksMined = 0;

        // Compute a precise mineable total lazily on first activation.
        this.totalBlocks = 0;
        this.totalBlocksComputed = false;
        this.currentTargetIsDirty = false;
    }

    public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    public Object getPosA() { return posA; }
    public Object getPosB() { return posB; }
    public Object getController() { return controller; }
    public int getControllerYaw() { return controllerYaw; }
    public Region getRegion() { return region; }

    public void setFrameBounds(int minX, int minZ, int maxX, int maxZ) {
        this.frameMinX = Math.min(minX, maxX);
        this.frameMaxX = Math.max(minX, maxX);
        this.frameMinZ = Math.min(minZ, maxZ);
        this.frameMaxZ = Math.max(minZ, maxZ);
    }

    public boolean hasFrameBounds() {
        return frameMinX != null && frameMinZ != null && frameMaxX != null && frameMaxZ != null;
    }

    public int frameMinX() { return hasFrameBounds() ? frameMinX : region.minX(); }
    public int frameMinZ() { return hasFrameBounds() ? frameMinZ : region.minZ(); }
    public int frameMaxX() { return hasFrameBounds() ? frameMaxX : region.maxX(); }
    public int frameMaxZ() { return hasFrameBounds() ? frameMaxZ : region.maxZ(); }
    public boolean isActive() { return active; }
    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            // Ensure UI reflects paused and doesn't get stuck showing "scanning".
            this.isScanning = false;
            this.mineProgress = 0.0f;
            this.outputJamTicks = 0;
            return;
        }

        // Resume behavior: do not reset progress counters/position.
        // Just ensure we are pointed at a valid next block to mine.
        mineProgress = 0.0f;
        outputJammed = false;
        outputJamTicks = 0;
        if (!totalBlocksComputed) {
            totalBlocks = computeTotalBlocks();
            totalBlocksComputed = true;
        }
        if (!isMineableForQuarry(location(currentX, currentY, currentZ), currentX, currentY, currentZ)) {
            findNextBlockToMine(false);
        }
    }
    public int getBlocksMined() { return blocksMined; }
    public int getTotalBlocksInRegion() { return totalBlocks; }

    public int getRedstoneMode() { return redstoneMode; }
    public void setRedstoneMode(int mode) {
        this.redstoneMode = Math.max(0, Math.min(2, mode));
    }

    public boolean isChunkLoadingEnabled() { return chunkLoadingEnabled; }
    public void setChunkLoadingEnabled(boolean enabled) {
        if (this.chunkLoadingEnabled == enabled) return;
        this.chunkLoadingEnabled = enabled;
        applyChunkForcing(enabled);
    }

    public boolean isSilentMode() { return silentMode; }
    public void setSilentMode(boolean silentMode) { this.silentMode = silentMode; }

    public boolean isRedstoneBlocked() { return redstoneBlocked; }
    public boolean isRedstonePowered() { return redstonePowered; }
    public boolean isOutputJammed() { return outputJammed; }

    public boolean isPowerBlocked() { return powerBlocked; }
    public long getPowerRequiredCfePerTick() { return powerRequiredCfePerTick; }
    public long getPowerReceivedCfePerTick() { return powerReceivedCfePerTick; }

    public int getMineTicksPerBlockForUi() {
        return getMineTicksPerBlock();
    }

    public int getRemainingBlocksEstimate() {
        long total = Math.max(0L, (long) totalBlocks);
        long mined = Math.max(0L, (long) blocksMined);
        long regionRemaining = Math.max(0L, total - mined);
        long dirtyPending = Math.max(0L, (long) dirtyBlocks.size());
        long remaining = regionRemaining + dirtyPending;
        return remaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
    }

    private static int comparePackedScanOrder(Long aObj, Long bObj) {
        if (aObj == null && bObj == null) return 0;
        if (aObj == null) return -1;
        if (bObj == null) return 1;
        long a = aObj;
        long b = bObj;

        int ay = unpackY(a);
        int by = unpackY(b);
        if (ay != by) return Integer.compare(by, ay); // y desc

        int ax = unpackX(a);
        int bx = unpackX(b);
        if (ax != bx) return Integer.compare(ax, bx); // x asc

        int az = unpackZ(a);
        int bz = unpackZ(b);
        if (az != bz) return Integer.compare(az, bz); // z asc

        // Stable tie-breaker.
        return Long.compare(a, b);
    }

    public int getProgressPercent() {
        // UI progress is meant to reflect completion of the mineable region.
        // This avoids jumping ahead just because the initial scan skips non-mineable
        // blocks (air, frame ring, etc.).
        if (scanWrapped && dirtyBlocks.isEmpty()) return 100;

        int total = Math.max(0, totalBlocks);
        if (total <= 0) return 0;

        int mined = Math.max(0, blocksMined);
        if (mined >= total) return 100;

        long pct = ((long) mined * 100L) / (long) total;
        if (pct < 0L) pct = 0L;
        if (pct > 100L) pct = 100L;
        return (int) pct;
    }

    public int getEtaSecondsEstimate() {
        long remaining = Math.max(0L, (long) getRemainingBlocksEstimate());
        if (remaining <= 0L) return 0;
        long ticks = remaining * (long) getMineTicksPerBlock();
        long seconds = ticks / 20L;
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }

    public int getAffectedChunkCount() {
        if (controller == null) return 0;
        java.util.Set<Long> desired = new java.util.HashSet<>();

        int minCx = region.minX() >> 4;
        int maxCx = region.maxX() >> 4;
        int minCz = region.minZ() >> 4;
        int maxCz = region.maxZ() >> 4;

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                desired.add((((long) cx) << 32) | (cz & 0xffffffffL));
            }
        }

        int ccx = platform.blockX(controller) >> 4;
        int ccz = platform.blockZ(controller) >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = ccx + dx;
                int cz = ccz + dz;
                desired.add((((long) cx) << 32) | (cz & 0xffffffffL));
            }
        }

        return desired.size();
    }

    private void applyChunkForcing(boolean forced) {
        if (world == null) return;

        if (!forced) {
            if (forcedChunkKeys.isEmpty()) return;
            for (long key : new java.util.HashSet<>(forcedChunkKeys)) {
                int cx = (int) (key >> 32);
                int cz = (int) key;
                platform.setChunkForced(world, cx, cz, false);
            }
            forcedChunkKeys.clear();
            return;
        }

        // Build chunk set: all chunks touched by the mining region
        // plus a 1-chunk radius around the controller chunk.
        java.util.Set<Long> desired = new java.util.HashSet<>();

        int minCx = region.minX() >> 4;
        int maxCx = region.maxX() >> 4;
        int minCz = region.minZ() >> 4;
        int maxCz = region.maxZ() >> 4;

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                desired.add((((long) cx) << 32) | (cz & 0xffffffffL));
            }
        }

        int ccx = platform.blockX(controller) >> 4;
        int ccz = platform.blockZ(controller) >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = ccx + dx;
                int cz = ccz + dz;
                desired.add((((long) cx) << 32) | (cz & 0xffffffffL));
            }
        }

        // Apply delta.
        for (long key : desired) {
            if (forcedChunkKeys.contains(key)) continue;
            int cx = (int) (key >> 32);
            int cz = (int) key;
            platform.setChunkForced(world, cx, cz, true);
        }
        for (long key : new java.util.HashSet<>(forcedChunkKeys)) {
            if (desired.contains(key)) continue;
            int cx = (int) (key >> 32);
            int cz = (int) key;
            platform.setChunkForced(world, cx, cz, false);
        }
        forcedChunkKeys.clear();
        forcedChunkKeys.addAll(desired);
    }

    /**
     * Best-effort current working Y level for UI display.
     */
    public int getCurrentLevelY() {
        return currentY;
    }

    /**
        * Placeholder tick that simply checks chunk loaded.
        */
    public void tick(boolean shouldLog) {
        tickCounter++;

        if (!platform.isChunkLoaded(controller)) return;

        // Redstone gating.
        redstoneBlocked = false;
        redstonePowered = platform.isRedstonePowered(controller);

        // Power gating (optional; platform-controlled).
        powerBlocked = false;
        powerRequiredCfePerTick = 0L;
        powerReceivedCfePerTick = 0L;

        if (redstoneMode != 0) {
            boolean shouldRun = (redstoneMode == 1) ? redstonePowered : !redstonePowered;
            if (shouldRun != active) {
                if (shouldRun) {
                    // Auto-start only if output is valid; otherwise stay paused.
                    if (hasValidOutput()) {
                        setActive(true);
                    }
                } else {
                    setActive(false);
                }
            }

            // If mode says "don't run", stop here.
            if (!shouldRun) {
                redstoneBlocked = true;
                return;
            }
        }

        if (!active) return;

        // Fallback active scanning: re-scan already-processed space for newly mineable blocks.
        // This keeps the quarry responsive even if platform-level block-place hooks are unavailable.
        scanForDirtyBlocksFallback(DIRTY_SCAN_STEPS_PER_TICK);

        // Active scanning: mine newly placed blocks in already-cleared areas promptly.
        // Only switch targets between blocks so we don't interrupt mining progress mid-block.
        if (mineProgress == 0.0f) {
            if (trySwitchToDirtyTarget(shouldLog)) {
                isScanning = false;
            }
        }

        if (!hasValidOutput()) {
            debug.log("tick", "No valid output for quarry controller=" + controller + " — pausing");
            setActive(false);
            return;
        }

        // Pause-on-jammed-output: if we have buffered items and cannot send any,
        // do not keep mining and filling the buffer.
        if (!outputBuffer.isEmpty()) {
            boolean sent = trySendOutput(shouldLog);
            if (!sent) {
                // Preferred behavior: if nothing can accept (filters/full), drop at the controller.
                // Only do this when we *do* have a valid output network; if output is truly missing,
                // keep the old jam/pause behavior to avoid voiding items unexpectedly.
                if (hasValidOutput()) {
                    Object item = outputBuffer.remove(0);
                    boolean dropped = platform.dropItemAtController(controller, item);
                    if (dropped) {
                        outputJammed = false;
                        outputJamTicks = 0;
                        // Continue running; we removed one buffered item.
                    } else {
                        // Platform didn't support dropping; fall back to jam behavior.
                        outputBuffer.add(0, item);
                        outputJammed = true;
                        outputJamTicks++;
                        if (outputJamTicks >= 40) {
                            setActive(false);
                        }
                        return;
                    }
                } else {
                    outputJammed = true;
                    outputJamTicks++;
                    // After a short grace period, pause until player fixes the output.
                    if (outputJamTicks >= 40) {
                        setActive(false);
                    }
                    return;
                }
            }
            outputJammed = false;
            outputJamTicks = 0;
        }

        // Require power to mine when supported by the platform.
        if (platform.supportsPower()) {
            powerRequiredCfePerTick = requiredPowerCfePerTick();
            if (powerRequiredCfePerTick > 0L) {
                powerReceivedCfePerTick = Math.max(0L, platform.extractPowerCfe(controller, powerRequiredCfePerTick));
                if (powerReceivedCfePerTick < powerRequiredCfePerTick) {
                    powerBlocked = true;
                    return;
                }
            }
        }

        Object currentLoc = location(currentX, currentY, currentZ);
        if (currentLoc == null) return;

        if (!isMineableForQuarry(currentLoc, currentX, currentY, currentZ)) {
            if (!findNextBlockToMine(shouldLog)) {
                isScanning = true;
                mineProgress = 0.0f;
                return;
            }
            currentLoc = location(currentX, currentY, currentZ);
            isScanning = false;
        } else {
            isScanning = false;
        }

        mineProgress = Math.min(1.0f, mineProgress + (1.0f / (float) getMineTicksPerBlock()));
        if (!silentMode) {
            platform.sendBlockCrack(currentLoc, mineProgress);
        }

        if (mineProgress >= 1.0f) {
            List<Object> drops = platform.getDrops(currentLoc, silkTouchAugment);
            if (drops != null) {
                for (Object drop : drops) {
                    if (drop == null) continue;
                    if (platform.stackAmount(drop) <= 0) continue;
                    outputBuffer.add(drop);
                }
            }

            if (!silentMode) {
                platform.playBreakEffects(currentLoc);
            }
            platform.setBlockAir(currentLoc);
            if (!currentTargetIsDirty) {
                blocksMined++;
            }

            if (!silentMode) {
                platform.sendBlockCrack(currentLoc, 0.0f);
            }
            mineProgress = 0.0f;
        }

        if (!outputBuffer.isEmpty()) {
            // Best-effort flush; jam state is handled at the top of tick.
            trySendOutput(shouldLog);
        }

        if (!platform.isMineable(currentLoc) || mineProgress == 0.0f) {
            advancePosition(shouldLog);
        }
    }

    /**
     * Returns true when the controller is connected to a pipe network or adjacent inventory.
     */
    public boolean hasValidOutput() {
        return platform.hasValidOutput(controller);
    }

    public boolean isOutputRoundRobin() { return outputRoundRobin; }
    public void setOutputRoundRobin(boolean rr) {
        boolean changed = this.outputRoundRobin != rr;
        this.outputRoundRobin = rr;
        if (changed && !rr) {
            outputInventoryCursor = 0;
        }
    }
    public boolean hasSilkTouchAugment() { return silkTouchAugment; }
    public void setSilkTouchAugment(boolean b) { this.silkTouchAugment = b; }
    public int getSpeedAugmentLevel() { return speedAugmentLevel; }
    public void setSpeedAugmentLevel(int lvl) { this.speedAugmentLevel = Math.max(0, Math.min(3, lvl)); }
    public int[] getBlocksPerLayer() { return new int[0]; }
    public boolean[] getEmptyLayers() { return new boolean[0]; }
    public boolean isMetadataReady() { return false; }
    public boolean isScanningMetadata() { return false; }
    public boolean isScanning() { return isScanning; }

    /**
     * Marks a block inside the quarry region as dirty so it can be mined promptly.
     * Intended to be called by platform hooks when blocks are placed/changed.
     */
    public void markDirtyBlock(Object worldObj, int x, int y, int z) {
        if (worldObj == null || world == null) return;
        if (!world.equals(worldObj)) return;
        if (!region.contains(worldObj, x, y, z)) return;

        // Only treat blocks as "dirty" if they are in already-processed space.
        // Otherwise we'd mine ahead of the normal scan order.
        if (!isBehindScanPointer(x, y, z)) return;

        Object loc = location(x, y, z);
        if (loc == null) return;
        if (!isMineableForQuarry(loc, x, y, z)) return;

        long packed = packPos(x, y, z);
        if (!dirtyBlocks.add(packed)) return;
        while (dirtyBlocks.size() > DIRTY_QUEUE_LIMIT) {
            dirtyBlocks.pollLast();
        }
    }

    private boolean trySwitchToDirtyTarget(boolean shouldLog) {
        while (!dirtyBlocks.isEmpty()) {
            Long packedObj = dirtyBlocks.pollFirst();
            if (packedObj == null) break;
            long packed = packedObj;

            int x = unpackX(packed);
            int y = unpackY(packed);
            int z = unpackZ(packed);
            Object loc = location(x, y, z);
            if (loc == null) continue;
            if (!isMineableForQuarry(loc, x, y, z)) continue;

            currentX = x;
            currentY = y;
            currentZ = z;
            currentTargetIsDirty = true;

            if (shouldLog) {
                debug.log("dirty", "Switching to newly-placed block at (" + x + "," + y + "," + z + ")");
            }
            return true;
        }
        return false;
    }

    private void scanForDirtyBlocksFallback(int steps) {
        if (steps <= 0) return;
        if (blocksMined <= 0) return;

        int minX = region.minX();
        int maxX = region.maxX();
        int minY = region.minY();
        int maxY = region.maxY();
        int minZ = region.minZ();
        int maxZ = region.maxZ();

        for (int i = 0; i < steps; i++) {
            // Only scan already-processed space; otherwise we'd enqueue future blocks.
            if (!isBehindScanPointer(dirtyScanX, dirtyScanY, dirtyScanZ)) {
                // Wrap back to start to continuously re-check old space.
                dirtyScanX = minX;
                dirtyScanZ = minZ;
                dirtyScanY = maxY;
                if (!isBehindScanPointer(dirtyScanX, dirtyScanY, dirtyScanZ)) {
                    return;
                }
            }

            Object loc = location(dirtyScanX, dirtyScanY, dirtyScanZ);
            if (loc != null && isMineableForQuarry(loc, dirtyScanX, dirtyScanY, dirtyScanZ)) {
                long packed = packPos(dirtyScanX, dirtyScanY, dirtyScanZ);
                if (dirtyBlocks.add(packed)) {
                    while (dirtyBlocks.size() > DIRTY_QUEUE_LIMIT) {
                        dirtyBlocks.pollLast();
                    }
                }
            }

            // Advance dirty scan position (y desc, x asc, z asc).
            dirtyScanZ++;
            if (dirtyScanZ > maxZ) {
                dirtyScanZ = minZ;
                dirtyScanX++;
                if (dirtyScanX > maxX) {
                    dirtyScanX = minX;
                    dirtyScanY--;
                    if (dirtyScanY < minY) {
                        dirtyScanY = maxY;
                    }
                }
            }
        }
    }

    private boolean isBehindScanPointer(int x, int y, int z) {
        if (scanWrapped) return true;

        // Scan order: y desc, x asc, z asc.
        if (y > scanY) return true;
        if (y < scanY) return false;

        if (x < scanX) return true;
        if (x > scanX) return false;

        return z < scanZ;
    }

    private void advancePosition(boolean shouldLog) {
        if (!findNextBlockToMine(shouldLog) && shouldLog) {
            debug.log("advancePosition", "No more blocks to mine — idle");
        }
    }

    private boolean trySendOutput(boolean shouldLog) {
        if (trySendToAdjacentInventory(shouldLog)) return true;
        return trySendThroughPipes(shouldLog);
    }

    private boolean trySendToAdjacentInventory(boolean shouldLog) {
        if (outputBuffer.isEmpty()) return false;

        Object peek = outputBuffer.get(0);

        Object adj = AdjacentInventorySelection.findFirstAdjacentInventoryWithSpace(
                platform,
                controller,
                peek,
            inFlightByDestination,
            inFlightByDestinationAndItem
        );

        if (adj == null) return false;

        Object holder = platform.getInventoryHolder(adj);
        if (holder == null) return false;

        int added = platform.addToInventory(holder, peek);
        int amount = platform.stackAmount(peek);

        if (added >= amount) {
            outputBuffer.remove(0);
        } else if (added > 0) {
            int remaining = amount - added;
            outputBuffer.set(0, platform.copyWithAmount(peek, remaining));
        }

        if (shouldLog) {
            debug.log("trySendToAdjacentInventory", "Inserted into adjacent inventory at " + adj);
        }
        return true;

    }

    private boolean trySendThroughPipes(boolean shouldLog) {
        if (outputBuffer.isEmpty()) return false;
        PipeNetworkManager pipes = platform.pipes();
        if (pipes == null) return false;

        PipeOutputRouting.AdjacentPipe startAdj = PipeOutputRouting.findAdjacentPipe(platform, pipes, controller);
        dev.cloudframe.common.pipes.PipeNode startPipe = startAdj != null ? startAdj.node() : null;

        if (startPipe == null) {
            if (shouldLog) debug.log("trySendThroughPipes", "No adjacent pipe for controller " + controller);
            return false;
        }

        List<Object> inventories = pipes.findInventoriesNear(startPipe);
        if (inventories == null || inventories.isEmpty()) return false;

        Object peek = outputBuffer.get(0);

        PipeOutputRouting.Selection sel = PipeOutputRouting.selectDestination(
                platform,
                pipes,
                controller,
                startPipe,
                inventories,
                peek,
                inFlightByDestination,
            inFlightByDestinationAndItem,
                outputRoundRobin,
                outputInventoryCursor
        );

        if (sel == null) return false;

        List<Object> waypoints = PipeOutputRouting.buildWaypoints(controller, sel.path(), sel.inventoryLocation());

        Object item = outputBuffer.remove(0);
        InFlightAccounting.reserve(inFlightByDestination, inFlightByDestinationAndItem, platform, sel.inventoryLocation(), item);

        platform.packetFactory().send(item, waypoints, sel.inventoryLocation(), this::onItemDelivered);

        outputInventoryCursor = sel.nextCursor();
        return true;
    }

    private boolean findNextBlockToMine(boolean shouldLog) {
        int minX = region.minX();
        int maxX = region.maxX();
        int minZ = region.minZ();
        int maxZ = region.maxZ();

        for (int y = scanY; y >= region.minY(); y--) {
            for (int x = (y == scanY ? scanX : minX); x <= maxX; x++) {
                for (int z = (y == scanY && x == scanX ? scanZ : minZ); z <= maxZ; z++) {
                    Object loc = location(x, y, z);
                    if (!isMineableForQuarry(loc, x, y, z)) continue;

                    currentX = x;
                    currentY = y;
                    currentZ = z;
                    currentTargetIsDirty = false;

                    scanZ = z + 1;
                    scanX = x;
                    if (scanZ > maxZ) {
                        scanZ = minZ;
                        scanX = x + 1;
                    }
                    if (scanX > maxX) {
                        scanX = minX;
                        scanY = y - 1;
                    } else {
                        scanY = y;
                    }

                    if (shouldLog && DebugFlags.STARTUP_LOAD_LOGGING) {
                        debug.log("findNextBlockToMine", "Next block at (" + x + "," + y + "," + z + ")");
                    }
                    return true;
                }
                scanZ = minZ;
            }
            scanX = minX;
        }

        resetScanPosition();
        return false;
    }

    private void resetScanPosition() {
        scanWrapped = true;
        scanX = region.minX();
        scanZ = region.minZ();
        scanY = region.maxY();

        currentX = scanX;
        currentZ = scanZ;
        currentY = scanY;
        currentTargetIsDirty = false;
    }

    private int computeTotalBlocks() {
        int count = 0;

        for (int y = region.maxY(); y >= region.minY(); y--) {
            for (int x = region.minX(); x <= region.maxX(); x++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    Object loc = location(x, y, z);
                    if (isMineableForQuarry(loc, x, y, z)) count++;
                }
            }
        }

        return count;
    }

    private int getMineTicksPerBlock() {
        return switch (speedAugmentLevel) {
            case 1 -> 8;
            case 2 -> 6;
            case 3 -> 5;
            default -> BASE_MINE_TICKS_PER_BLOCK;
        };
    }

    private long requiredPowerCfePerTick() {
        int tpb = Math.max(1, getMineTicksPerBlock());
        // ceil(ENERGY_PER_BLOCK_CFE / tpb)
        return (ENERGY_PER_BLOCK_CFE + (long) tpb - 1L) / (long) tpb;
    }

    // Vanilla-style packing (26 bits X, 12 bits Y, 26 bits Z).
    // Supports typical world bounds (±33M), and keeps this module dependency-free.
    private static long packPos(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    private static int unpackY(long packed) {
        // sign-extend 12-bit Y
        return (int) (packed << 52 >> 52);
    }

    private static int unpackZ(long packed) {
        // sign-extend 26-bit Z
        return (int) (packed << 26 >> 38);
    }

    private Object location(int x, int y, int z) {
        if (world == null) return null;
        return platform.createLocation(world, x, y, z);
    }

    private boolean isFramePerimeterPos(int x, int y, int z) {
        // The visual glass frame is a 2D ring placed on the top layer only.
        if (y != region.maxY()) return false;

        int minX = frameMinX();
        int maxX = frameMaxX();
        int minZ = frameMinZ();
        int maxZ = frameMaxZ();

        if (x < minX || x > maxX || z < minZ || z > maxZ) return false;
        return (x == minX || x == maxX || z == minZ || z == maxZ);
    }

    private boolean isMineableForQuarry(Object loc, int x, int y, int z) {
        if (loc == null) return false;
        if (!platform.isMineable(loc)) return false;

        // Protect the quarry's visual frame boundary from mining.
        if (platform.isGlassFrameBlock(loc) && isFramePerimeterPos(x, y, z)) return false;
        return true;
    }

    private void onItemDelivered(Object destination, Object itemStack, int deliveredAmount) {
        InFlightAccounting.release(inFlightByDestination, inFlightByDestinationAndItem, platform, destination, itemStack, deliveredAmount);
    }

    public Map<String, Integer> getInFlightMap() { return inFlightByDestination; }
    public List<Object> getOutputBuffer() { return outputBuffer; }
    
    // Glass frame management
    public void createGlassFrame() {
        platform.placeGlassFrame(world, frameMinX(), region.minY(), frameMinZ(), frameMaxX(), region.maxY(), frameMaxZ());
    }
    
    public void removeGlassFrame() {
        platform.removeGlassFrame(world, frameMinX(), region.minY(), frameMinZ(), frameMaxX(), region.maxY(), frameMaxZ());
    }
}
