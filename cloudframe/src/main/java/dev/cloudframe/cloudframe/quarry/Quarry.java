package dev.cloudframe.cloudframe.quarry;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

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
    private final int controllerYaw;

    private final Region region;
    private boolean active = false;

    private int currentX;
    private int currentZ;
    private int currentY;

    private int scanX;
    private int scanY;
    private int scanZ;

    private boolean[] layerHasBlocks;
    private boolean[] layerScanned;

    private int totalBlocksInRegion;
    private int blocksMined;

    // Async metadata
    private boolean metadataReady = false;
    private int[] blocksPerLayer;
    private boolean[] emptyLayers;
    private boolean scanningMetadata = false;

    // Scanning state (when region is empty but quarry is active)
    private boolean isScanning = false;

    private final List<ItemStack> outputBuffer = new ArrayList<>();

    public Quarry(UUID owner, Location posA, Location posB, Region region, Location controller, int controllerYaw) {
        this.owner = owner;
        this.posA = posA;
        this.posB = posB;
        this.region = region;
        this.controller = controller;
        this.controllerYaw = controllerYaw;

        // Start at the TOP of the region
        this.currentX = region.minX();
        this.currentZ = region.minZ();
        this.currentY = region.maxY();

        this.scanX = region.minX();
        this.scanZ = region.minZ();
        this.scanY = region.maxY();

        int layerCount = region.height();
        this.layerHasBlocks = new boolean[layerCount];
        this.layerScanned = new boolean[layerCount];

        this.totalBlocksInRegion = 0;
        this.blocksMined = 0;

        debug.log("constructor", "Created quarry for owner=" + owner +
                " region=" + region +
            " controller=" + controller +
            " yaw=" + controllerYaw);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        debug.log("setActive", "Setting active=" + active);
        this.active = active;

        if (active) {
            // Reset scan state
            this.scanX = region.minX();
            this.scanZ = region.minZ();
            this.scanY = region.maxY();

            this.currentX = region.minX();
            this.currentZ = region.minZ();
            this.currentY = region.maxY();

            this.blocksMined = 0;
            this.totalBlocksInRegion = 0;

            for (int i = 0; i < layerHasBlocks.length; i++) {
                layerHasBlocks[i] = false;
                layerScanned[i] = false;
            }

            startMetadataScan();
            computeTotalBlocks(); // fallback

            findNextBlockToMine(true);
        }
    }

    public int getBlocksMined() {
        return blocksMined;
    }

    public int getTotalBlocksInRegion() {
        return totalBlocksInRegion;
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

    public int getCurrentY() {
        return currentY;
    }

    public Location getController() {
        return controller;
    }

    public int getControllerYaw() {
        return controllerYaw;
    }

    public UUID getOwner() {
        return owner;
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

    public boolean isScanning() {
        return isScanning;
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

        Material typeAtPos = world.getBlockAt(currentX, currentY, currentZ).getType();
        if (!isMineable(typeAtPos)) {
            if (!findNextBlockToMine(shouldLog)) {
                if (shouldLog) debug.log("tick", "Region empty — scanning");
                isScanning = true;
                return;
            } else {
                isScanning = false;
            }
        } else {
            isScanning = false;
        }

        if (shouldLog) {
            debug.log("tick", "Tick at (" + currentX + "," + currentY + "," + currentZ + ")");
        }

        Block block = world.getBlockAt(currentX, currentY, currentZ);
        Material type = block.getType();

        if (isMineable(type)) {
            if (shouldLog) {
                debug.log("tick", "Mining block " + type +
                        " at (" + currentX + "," + currentY + "," + currentZ + ")");
            }

            // Only add to output buffer if it's not a fluid
            if (type != Material.WATER && type != Material.LAVA) {
                outputBuffer.add(new ItemStack(type));
            }
            block.setType(Material.AIR);
            blocksMined++;
        }

        if (!outputBuffer.isEmpty()) {
            if (shouldLog) debug.log("tick", "Output buffer size=" + outputBuffer.size());
            trySendToTube(shouldLog);
        }

        advancePosition(shouldLog);
    }

    private boolean isMineable(Material type) {
        if (type == Material.AIR) return false;
        if (type == Material.BEDROCK) return false;
        if (type.isSolid()) return true;
        if (type == Material.WATER || type == Material.LAVA) return true;
        return false;
    }

    private void advancePosition(boolean shouldLog) {
        if (!findNextBlockToMine(shouldLog)) {
            if (shouldLog) debug.log("advancePosition", "No more blocks to mine — idle");
        }
    }

    private void trySendToTube(boolean shouldLog) {
        if (outputBuffer.isEmpty()) return;

        if (shouldLog) debug.log("trySendToTube", "Attempting to route item...");

        // Controller must be physically connected to the tube network: require an adjacent tube.
        final Vector[] DIRS = new Vector[] {
            new Vector(1, 0, 0),
            new Vector(-1, 0, 0),
            new Vector(0, 1, 0),
            new Vector(0, -1, 0),
            new Vector(0, 0, 1),
            new Vector(0, 0, -1)
        };

        TubeNode startTube = null;
        for (Vector v : DIRS) {
            TubeNode node = CloudFrameRegistry.tubes().getTube(controller.clone().add(v));
            if (node != null) {
                startTube = node;
                break;
            }
        }

        if (startTube == null) {
            if (shouldLog) debug.log("trySendToTube", "No adjacent tube found for controller at " + controller + " — not connected");
            return;
        }

        List<Location> inventories = CloudFrameRegistry.tubes().findInventoriesNear(startTube);
        if (inventories.isEmpty()) return;

        // Route to the first inventory we can reach.
        for (Location invLoc : inventories) {
            TubeNode destTube = null;
            for (TubeNode node : CloudFrameRegistry.tubes().all()) {
                if (node.getLocation().distance(invLoc) < 1.5) {
                    destTube = node;
                    break;
                }
            }
            if (destTube == null) continue;

            List<TubeNode> path = CloudFrameRegistry.tubes().findPath(startTube, destTube);
            if (path == null) continue;

            ItemStack item = outputBuffer.remove(0);
            CloudFrameRegistry.packets().add(new ItemPacket(item, path));
            return;
        }
    }

    public double getProgressPercent() {
        if (totalBlocksInRegion == 0) return 0.0;
        return (blocksMined / (double) totalBlocksInRegion) * 100.0;
    }

    private boolean findNextBlockToMine(boolean shouldLog) {

        World world = region.getWorld();

        int minX = region.minX();
        int maxX = region.maxX();
        int minZ = region.minZ();
        int maxZ = region.maxZ();

        while (scanY >= region.minY()) {

            int layerIndex = region.maxY() - scanY;

            if (layerIndex < 0 || layerIndex >= layerHasBlocks.length) {
                scanY--;
                scanX = minX;
                scanZ = minZ;
                continue;
            }

            if (layerScanned[layerIndex] && !layerHasBlocks[layerIndex] &&
                scanX == minX && scanZ == minZ) {

                if (shouldLog) debug.log("findNextBlockToMine", "Skipping empty layer Y=" + scanY);
                scanY--;
                continue;
            }

            boolean foundBlockInLayer = false;

            for (int x = scanX; x <= maxX; x++) {
                for (int z = (x == scanX ? scanZ : minZ); z <= maxZ; z++) {

                    Material type = world.getBlockAt(x, scanY, z).getType();
                    if (shouldLog) debug.log("scan", "Checking (" + x + "," + scanY + "," + z + ") = " + type);

                    if (isMineable(type)) {
                        foundBlockInLayer = true;
                        layerHasBlocks[layerIndex] = true;

                        currentX = x;
                        currentY = scanY;
                        currentZ = z;

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

            if (!foundBlockInLayer) {
                layerHasBlocks[layerIndex] = false;
                if (shouldLog) debug.log("findNextBlockToMine", "Layer Y=" + scanY + " is empty");
            }

            layerScanned[layerIndex] = true;

            scanX = minX;
            scanZ = minZ;
            scanY--;
        }

        // Region complete — reset to top and restart scan
        if (shouldLog) debug.log("findNextBlockToMine", "Region scan complete, resetting to top");
        resetScanPosition();
        
        // Try one more pass from the top
        return findNextBlockToMineFromTop(shouldLog);
    }

    private boolean findNextBlockToMineFromTop(boolean shouldLog) {
        World world = region.getWorld();

        int minX = region.minX();
        int maxX = region.maxX();
        int minZ = region.minZ();
        int maxZ = region.maxZ();

        for (int y = region.maxY(); y >= region.minY(); y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    
                    if (isMineable(type)) {
                        currentX = x;
                        currentY = y;
                        currentZ = z;
                        
                        if (shouldLog) {
                            debug.log("findNextBlockToMineFromTop",
                                    "Found block " + type + " at (" + x + "," + y + "," + z + ")");
                        }
                        return true;
                    }
                }
            }
        }

        if (shouldLog) debug.log("findNextBlockToMineFromTop", "No blocks found in region - empty");
        return false;
    }

    private void resetScanPosition() {
        this.scanX = region.minX();
        this.scanZ = region.minZ();
        this.scanY = region.maxY();
        
        // Reset layer tracking
        for (int i = 0; i < layerHasBlocks.length; i++) {
            layerScanned[i] = false;
        }
        
        debug.log("resetScanPosition", "Scan position reset to top");
    }

        public int countLayersWithBlocks() {
            // Count layers that still have mineable blocks (live check)
            if (blocksPerLayer == null || blocksPerLayer.length == 0) {
                return 0;
            }

            World world = region.getWorld();
            int layersRemaining = 0;

            for (int layer = 0; layer < blocksPerLayer.length; layer++) {
                int y = region.maxY() - layer;

                for (int x = region.minX(); x <= region.maxX(); x++) {
                    for (int z = region.minZ(); z <= region.maxZ(); z++) {
                        Material type = world.getBlockAt(x, y, z).getType();
                        if (isMineable(type)) {
                            layersRemaining++;
                            break;
                        }
                    }
                    if (layersRemaining > 0) break; // Found a block in this layer, move to next layer
                }
            }

            return layersRemaining;
        }
    private void computeTotalBlocks() {
        if (totalBlocksInRegion > 0) return;

        World world = region.getWorld();
        int count = 0;

        for (int y = region.maxY(); y >= region.minY(); y--) {
            for (int x = region.minX(); x <= region.maxX(); x++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (isMineable(type)) count++;
                }
            }
        }

        this.totalBlocksInRegion = count;
    }

    public void startMetadataScan() {
        if (scanningMetadata || metadataReady) return;
        scanningMetadata = true;
        metadataReady = false;

        int layerCount = region.height();
        blocksPerLayer = new int[layerCount];
        emptyLayers = new boolean[layerCount];

        buildSnapshotAsync(region.maxY(), new ArrayList<>());
    }

        public void resetMetadataAndProgress() {
            // Reset mining progress
            this.blocksMined = 0;
            this.totalBlocksInRegion = 0;
        
            // Reset scan position
            this.scanX = region.minX();
            this.scanZ = region.minZ();
            this.scanY = region.maxY();
        
            // Reset layer tracking
            for (int i = 0; i < layerHasBlocks.length; i++) {
                layerHasBlocks[i] = false;
                layerScanned[i] = false;
            }
        
            // Reset metadata
            metadataReady = false;
        
            // Start fresh metadata scan
            startMetadataScan();
        
            debug.log("resetMetadataAndProgress", "Reset metadata and progress");
        }
    private void buildSnapshotAsync(int y, List<Material> snapshot) {
        World world = region.getWorld();

        if (y < region.minY()) {
            processSnapshotAsync(snapshot);
            return;
        }

        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                snapshot.add(world.getBlockAt(x, y, z).getType());
            }
        }

        int nextY = y - 1;

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
                    try {
                        computeMetadata(snapshot);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        debug.log("metadata", "Metadata scan failed: " + ex.getMessage());
                    }

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
        if (snapshot.isEmpty()) {
            for (int i = 0; i < blocksPerLayer.length; i++) {
                blocksPerLayer[i] = 0;
                emptyLayers[i] = true;
            }
            totalBlocksInRegion = 0;
            return;
        }

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
                if (isMineable(type)) count++;
            }

            blocksPerLayer[layer] = count;
            emptyLayers[layer] = (count == 0);
            total += count;
        }

        this.totalBlocksInRegion = total;
    }
}
