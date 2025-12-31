package dev.cloudframe.common.quarry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.cloudframe.common.tubes.TubeNetworkManager;
import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.util.DebugFlags;
import dev.cloudframe.common.util.DebugManager;
import dev.cloudframe.common.util.Region;

/**
 * Platform-agnostic Quarry scaffold. Mining logic will be filled in with platform hooks.
 */
public class Quarry {

    private static final Debug debug = DebugManager.get(Quarry.class);

    private final UUID owner;
    private final Object posA;
    private final Object posB;
    private final Object controller;
    private final int controllerYaw;
    private final Region region;
    private final QuarryPlatform platform;

    private boolean active;

    // Simplified state placeholders
    private int blocksMined;
    private int totalBlocks;

    public Quarry(UUID owner, Object posA, Object posB, Region region, Object controller, int controllerYaw, QuarryPlatform platform) {
        this.owner = owner;
        this.posA = platform.normalize(posA);
        this.posB = platform.normalize(posB);
        this.region = region;
        this.controller = platform.normalize(controller);
        this.controllerYaw = controllerYaw;
        this.platform = platform;
    }

    public UUID getOwner() { return owner; }
    public Object getPosA() { return posA; }
    public Object getPosB() { return posB; }
    public Object getController() { return controller; }
    public int getControllerYaw() { return controllerYaw; }
    public Region getRegion() { return region; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getBlocksMined() { return blocksMined; }
    public int getTotalBlocksInRegion() { return totalBlocks; }

    /**
        * Placeholder tick that simply checks chunk loaded.
        */
    public void tick(boolean shouldLog) {
        if (!active) return;
        if (!platform.isChunkLoaded(controller)) return;
        // TODO: port full mining logic with platform hooks
        if (shouldLog && DebugFlags.STARTUP_LOAD_LOGGING) {
            debug.log("tick", "Quarry tick placeholder for owner=" + owner);
        }
    }

    /**
     * Returns true when the controller is connected to a tube network or adjacent inventory.
     */
    public boolean hasValidOutput() {
        Object ctrl = controller;
        if (ctrl == null) return false;

        // Direct adjacent inventory
        for (int[] dir : TubeNetworkManager.DIRS) {
            Object adj = platform.offset(ctrl, dir[0], dir[1], dir[2]);
            if (platform.isInventory(adj)) return true;
        }

        // Tube connectivity via platform tubes()
        TubeNetworkManager tubes = platform.tubes();
        if (tubes == null) return false;

        // Find adjacent tube
        dev.cloudframe.common.tubes.TubeNode start = null;
        for (int[] dir : TubeNetworkManager.DIRS) {
            Object adj = platform.offset(ctrl, dir[0], dir[1], dir[2]);
            var node = tubes.getTube(adj);
            if (node != null) { start = node; break; }
        }
        if (start == null) return false;

        List<Object> inventories = tubes.findInventoriesNear(start);
        return inventories != null && !inventories.isEmpty();
    }

    // Stubs for migration completeness
    public boolean isOutputRoundRobin() { return true; }
    public void setOutputRoundRobin(boolean rr) {}
    public boolean hasSilkTouchAugment() { return false; }
    public void setSilkTouchAugment(boolean b) {}
    public int getSpeedAugmentLevel() { return 0; }
    public void setSpeedAugmentLevel(int lvl) {}
    public int[] getBlocksPerLayer() { return new int[0]; }
    public boolean[] getEmptyLayers() { return new boolean[0]; }
    public boolean isMetadataReady() { return false; }
    public boolean isScanningMetadata() { return false; }
    public boolean isScanning() { return false; }

    public void onItemDelivered(Object destination, int deliveredAmount) {
        // no-op placeholder
    }

    public Map<String, Integer> getInFlightMap() { return Map.of(); }
    public List<Object> getOutputBuffer() { return new ArrayList<>(); }
}
