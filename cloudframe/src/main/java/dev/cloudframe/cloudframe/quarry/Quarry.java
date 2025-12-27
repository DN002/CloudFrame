package dev.cloudframe.cloudframe.quarry;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
import dev.cloudframe.cloudframe.tubes.ItemPacket;
import dev.cloudframe.cloudframe.tubes.TubeNode;
import dev.cloudframe.cloudframe.util.Region;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Quarry {

    private static final Debug debug = DebugManager.get(Quarry.class);

    private final UUID owner;
    private final Location posA;
    private final Location posB;
    private final Location controller;

    private final Region region;
    private boolean active = false;

    private int currentX;
    private int currentZ;
    private int currentY;

    private int scanX;
    private int scanY;
    private int scanZ;
    private boolean[] layerHasBlocks;
    
    private int totalBlocksInRegion;
    private int blocksMined;
    
    // Async metadata
    private boolean metadataReady = false;
    private int[] blocksPerLayer;     // indexed by layerIndex
    private boolean[] emptyLayers;    // indexed by layerIndex
    private boolean scanningMetadata = false;


    private final List<ItemStack> outputBuffer = new ArrayList<>();

    public Quarry(UUID owner, Location posA, Location posB, Region region, Location controller) {
        this.owner = owner;
        this.posA = posA;
        this.posB = posB;
        this.region = region;
        this.controller = controller;

        this.currentY = region.minY();
        this.currentX = region.minX();
        this.currentZ = region.minZ();

        // Initialize incremental scan state
        this.scanY = region.minY();
        this.scanX = region.minX();
        this.scanZ = region.minZ();

        // Prepare layer metadata array (for future layer skipping)
        int startY = region.minY();
        int endY = region.getWorld().getMinHeight();
        int layerCount = (startY - endY) + 1;
        this.layerHasBlocks = new boolean[layerCount];
        
        this.totalBlocksInRegion = 0; // will be computed on first activation
        this.blocksMined = 0;

        debug.log("constructor", "Created quarry for owner=" + owner +
                " region=" + region +
                " controller=" + controller);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        debug.log("setActive", "Setting active=" + active);
        this.active = active;

        if (active) {
            // Reset incremental scan state
            this.scanY = region.minY();
            this.scanX = region.minX();
            this.scanZ = region.minZ();

            // Reset layer metadata
            for (int i = 0; i < layerHasBlocks.length; i++) {
                layerHasBlocks[i] = false;
            }

            startMetadataScan();
            // Fallback until metadata is ready
            computeTotalBlocks();

            // Begin mining immediately
            findNextBlockToMine(true);

        }
    }

    public int getCurrentY() {
        return currentY;
    }

    public Location getController() {
        return controller;
    }

    public UUID getOwner() {
        return owner;
    }

    public Location getPosA() {
        return posA;
    }

    public Location getPosB() {
        return posB;
    }

    public World getWorld() {
        return posA.getWorld();
    }

    public Region getRegion() {
        return region;
    }

    public boolean isMetadataReady() {
        return metadataReady;
    }

    public boolean isScanningMetadata() {
        return scanningMetadata;
    }

    public int[] getBlocksPerLayer() {
        return blocksPerLayer != null ? blocksPerLayer : new int[0];
    }

    public boolean[] getEmptyLayers() {
        return emptyLayers != null ? emptyLayers : new boolean[0];
    }
    
    public boolean isChunkLoaded() {
        boolean loaded = posA.getWorld().isChunkLoaded(
                posA.getBlockX() >> 4,
                posA.getBlockZ() >> 4
        );
        if (!loaded) {
            debug.log("isChunkLoaded", "Chunk not loaded for quarry owner=" + owner);
        }
        return loaded;
    }

    public List<ItemStack> getOutputBuffer() {
        return outputBuffer;
    }

    public void tick(boolean shouldLog) {
        if (!isChunkLoaded()) return;
        if (!active) return;

        World world = region.getWorld();

        // If current position is invalid or empty, find the next block
        Material typeAtPos = world.getBlockAt(currentX, currentY, currentZ).getType();
        if (typeAtPos == Material.AIR || typeAtPos == Material.BEDROCK) {
            if (!findNextBlockToMine(shouldLog)) {
                if (shouldLog) debug.log("tick", "Region empty — idle");
                return;
            }
        }


        if (shouldLog) {
            debug.log("tick", "Tick at (" + currentX + "," + currentY + "," + currentZ + ")");
        }

        Block block = world.getBlockAt(currentX, currentY, currentZ);
        Material type = block.getType();

        if (type != Material.AIR && type != Material.BEDROCK) {

            if (shouldLog) {
                debug.log("tick", "Mining block " + type +
                        " at (" + currentX + "," + currentY + "," + currentZ + ")");
            }

            // Only add solid blocks to output buffer
            if (type.isSolid()) {
                outputBuffer.add(new ItemStack(type));
            }

            // Replace everything (including fluids) with air
            block.setType(Material.AIR);
            blocksMined++;
        }

        // Try sending items
        if (!outputBuffer.isEmpty()) {
            if (shouldLog) {
                debug.log("tick", "Output buffer size=" + outputBuffer.size());
            }
            trySendToTube(shouldLog);
        }

        // Move to next block
        advancePosition(shouldLog);
    }

    private void advancePosition(boolean shouldLog) {
        // After mining a block, simply find the next one.
        if (!findNextBlockToMine(shouldLog)) {
            // No blocks left — quarry idles but stays active until user disables it
            if (shouldLog) {
                debug.log("advancePosition", "No more blocks to mine — idle");
            }
        }
    }

    private void trySendToTube(boolean shouldLog) {
        if (outputBuffer.isEmpty()) return;

        if (shouldLog) debug.log("trySendToTube", "Attempting to route item...");

        TubeNode nearestTube = null;
        double bestDist = Double.MAX_VALUE;

        for (TubeNode node : CloudFrameRegistry.tubes().all()) {
            double dist = node.getLocation().distanceSquared(controller);
            if (dist < bestDist) {
                bestDist = dist;
                nearestTube = node;
            }
        }

        if (nearestTube == null) {
            if (shouldLog) debug.log("trySendToTube", "No tubes found near controller");
            return;
        }

        if (shouldLog) debug.log("trySendToTube", "Nearest tube at " + nearestTube.getLocation());

        List<Location> inventories = CloudFrameRegistry.tubes().findInventoriesNear(nearestTube);
        if (inventories.isEmpty()) {
        	if (shouldLog) debug.log("trySendToTube", "No inventories found near tube");
            return;
        }

        Location bestInv = inventories.get(0);
        if (shouldLog) debug.log("trySendToTube", "Found inventory at " + bestInv);

        TubeNode destTube = null;
        for (TubeNode node : CloudFrameRegistry.tubes().all()) {
            if (node.getLocation().distance(bestInv) < 1.5) {
                destTube = node;
                break;
            }
        }

        if (destTube == null) {
        	if (shouldLog) debug.log("trySendToTube", "No tube found near inventory");
            return;
        }

        if (shouldLog) debug.log("trySendToTube", "Destination tube at " + destTube.getLocation());

        List<TubeNode> path = CloudFrameRegistry.tubes().findPath(nearestTube, destTube);
        if (path == null) {
        	if (shouldLog) debug.log("trySendToTube", "No valid path found between tubes");
            return;
        }

        ItemStack item = outputBuffer.remove(0);
        if (shouldLog) debug.log("trySendToTube", "Routing item " + item.getType() +
                " along path length=" + path.size());

        CloudFrameRegistry.packets().add(new ItemPacket(item, path));
    }
    
    public double getProgressPercent() {
        if (totalBlocksInRegion == 0) return 100.0;
        return (blocksMined / (double) totalBlocksInRegion) * 100.0;
    }

    private boolean findNextBlockToMine(boolean shouldLog) {
        World world = region.getWorld();

        int minX = region.minX();
        int maxX = region.maxX();
        int minZ = region.minZ();
        int maxZ = region.maxZ();
        int minY = world.getMinHeight();

        while (scanY >= minY) {

            int layerIndex = region.minY() - scanY;

            // If this layer was previously scanned and found empty → skip instantly
            if (!layerHasBlocks[layerIndex] && scanX == minX && scanZ == minZ) {
                if (shouldLog) {
                    debug.log("findNextBlockToMine", "Skipping empty layer Y=" + scanY);
                }
                scanY--;
                continue;
            }

            boolean foundBlockInLayer = false;

            // Scan this Y layer from scanX/scanZ forward
            for (int x = scanX; x <= maxX; x++) {
                for (int z = (x == scanX ? scanZ : minZ); z <= maxZ; z++) {

                    Material type = world.getBlockAt(x, scanY, z).getType();

                    if (type != Material.AIR && type.isSolid() && type != Material.BEDROCK) {
                        foundBlockInLayer = true;
                        layerHasBlocks[layerIndex] = true;

                        currentX = x;
                        currentY = scanY;
                        currentZ = z;

                        // Update scan position for next time
                        scanZ = z + 1;
                        scanX = x;

                        if (scanZ > maxZ) {
                            scanZ = minZ;
                            scanX = x + 1;
                        }
                        if (scanX > maxX) {
                            scanX = minX;
                            scanY--;
                        }

                        if (shouldLog) {
                            debug.log("findNextBlockToMine",
                                    "Found next block " + type + " at (" + currentX + "," + currentY + "," + currentZ + ")");
                        }

                        return true;
                    }
                }
            }

            // Finished scanning this layer
            if (!foundBlockInLayer) {
                layerHasBlocks[layerIndex] = false; // mark empty
                if (shouldLog) {
                    debug.log("findNextBlockToMine", "Layer Y=" + scanY + " is empty");
                }
            }

            // Move down to next layer
            scanX = minX;
            scanZ = minZ;
            scanY--;
        }

        if (shouldLog) {
            debug.log("findNextBlockToMine", "No blocks left in region");
        }
        return false;
    }
    
    private void computeTotalBlocks() {
        if (totalBlocksInRegion > 0) return;

        World world = region.getWorld();
        int count = 0;

        for (int y = region.minY(); y >= world.getMinHeight(); y--) {
            for (int x = region.minX(); x <= region.maxX(); x++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (type.isSolid() && type != Material.BEDROCK) {
                        count++;
                    }
                }
            }
        }

        this.totalBlocksInRegion = count;
    }
    
    public void startMetadataScan() {
        if (scanningMetadata || metadataReady) return;

        scanningMetadata = true;
        metadataReady = false;

        int layerCount = layerHasBlocks.length;
        blocksPerLayer = new int[layerCount];
        emptyLayers = new boolean[layerCount];

        // Step 1: Build snapshot on main thread in small slices
        buildSnapshotAsync(region.minY(), new ArrayList<>());
    }

    private void buildSnapshotAsync(int y, List<Material> snapshot) {
        World world = region.getWorld();

        int endY = world.getMinHeight();

        if (y < endY) {
            // Snapshot complete — process async
            processSnapshotAsync(snapshot);
            return;
        }

        // Scan one layer per tick
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                snapshot.add(world.getBlockAt(x, y, z).getType());
            }
        }

        int nextY = y - 1;

        // Schedule next slice next tick
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            CloudFrameRegistry.plugin(),
            () -> buildSnapshotAsync(nextY, snapshot),
            1L
        );
    }
    
    private void processSnapshotAsync(List<Material> snapshot) {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(
            CloudFrameRegistry.plugin(),
            () -> {
                computeMetadata(snapshot);

                // Return results to main thread
                org.bukkit.Bukkit.getScheduler().runTask(
                    CloudFrameRegistry.plugin(),
                    () -> {
                        metadataReady = true;
                        scanningMetadata = false;
                        debug.log("metadata", "Metadata scan complete");
                    }
                );
            }
        );
    }

    private void computeMetadata(List<Material> snapshot) {
        int width = region.width();
        int length = region.length();

        int layerSize = width * length;
        int layerCount = blocksPerLayer.length;

        int index = 0;
        int total = 0;

        for (int layer = 0; layer < layerCount; layer++) {
            int count = 0;

            for (int i = 0; i < layerSize; i++) {
                Material type = snapshot.get(index++);
                if (type.isSolid() && type != Material.BEDROCK) {
                    count++;
                }
            }

            blocksPerLayer[layer] = count;
            emptyLayers[layer] = (count == 0);
            total += count;
        }

        this.totalBlocksInRegion = total;
    }
}
