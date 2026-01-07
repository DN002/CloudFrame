package dev.cloudframe.common.quarry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import dev.cloudframe.common.pipes.PipeNetworkManager;
import dev.cloudframe.common.pipes.PipeNode;
import dev.cloudframe.common.util.DirIndex;

/**
 * Shared helpers for quarry output routing through pipe networks.
 *
 * <p>This logic is intentionally platform-agnostic and only depends on
 * {@link QuarryPlatform} and the common {@link PipeNetworkManager}.</p>
 */
public final class PipeOutputRouting {

    private PipeOutputRouting() {
    }

    public record AdjacentPipe(Object pipeLocation, PipeNode node) {
    }

    public record Selection(
            Object inventoryLocation,
            Object destinationPipeLocation,
            PipeNode destinationPipe,
            List<PipeNode> path,
            int nextCursor
    ) {
    }

    /**
     * Finds the first adjacent pipe node to {@code baseLocation}.
     */
    public static AdjacentPipe findAdjacentPipe(QuarryPlatform platform, PipeNetworkManager pipes, Object baseLocation) {
        if (platform == null || pipes == null || baseLocation == null) return null;

        for (int dirIndex = 0; dirIndex < 6; dirIndex++) {
            Object adj = platform.offset(baseLocation, DirIndex.dx(dirIndex), DirIndex.dy(dirIndex), DirIndex.dz(dirIndex));
            PipeNode node = pipes.getPipe(adj);
            if (node != null) {
                return new AdjacentPipe(adj, node);
            }
        }

        return null;
    }

    /**
     * Selects a destination inventory reachable by pipes, respecting:
     * - distance sorting
     * - optional round-robin cursor
     * - inventory space check
     * - optional pipe-face filter veto
     */
    public static Selection selectDestination(
            QuarryPlatform platform,
            PipeNetworkManager pipes,
            Object controllerLocation,
            PipeNode startPipe,
            List<Object> inventories,
            Object itemStack,
            Map<String, Integer> inFlightByDestination,
            Map<String, Integer> inFlightByDestinationAndItem,
            boolean outputRoundRobin,
            int outputInventoryCursor
    ) {
        if (platform == null || pipes == null || controllerLocation == null || startPipe == null) return null;
        if (inventories == null || inventories.isEmpty() || itemStack == null) return null;

        List<Object> sorted = new ArrayList<>(inventories);
        sorted.sort(
                Comparator
                        .comparingDouble((Object loc) -> platform.distanceSquared(controllerLocation, loc))
                        .thenComparingInt(platform::blockX)
                        .thenComparingInt(platform::blockY)
                        .thenComparingInt(platform::blockZ)
        );

        int startIndex = outputRoundRobin && !sorted.isEmpty()
                ? Math.floorMod(outputInventoryCursor, sorted.size())
                : 0;

        for (int attempt = 0; attempt < sorted.size(); attempt++) {
            int idx = (startIndex + attempt) % sorted.size();
            Object invLoc = sorted.get(idx);

            Object holder = platform.getInventoryHolder(invLoc);
            if (holder == null) continue;
            if (!InFlightAccounting.canReserveDestination(platform, invLoc, holder, itemStack, inFlightByDestination, inFlightByDestinationAndItem)) continue;

            AdjacentPipe adjacentDestPipe = findAdjacentPipe(platform, pipes, invLoc);
            if (adjacentDestPipe == null) continue;

            // Pipe-face filter veto.
            if (adjacentDestPipe.pipeLocation != null && !platform.allowsPipeFilter(adjacentDestPipe.pipeLocation, invLoc, itemStack)) {
                continue;
            }

            List<PipeNode> path = pipes.findPath(startPipe, adjacentDestPipe.node);
            if (path == null || path.isEmpty()) continue;

            int nextCursor = outputRoundRobin ? idx + 1 : 0;
            return new Selection(invLoc, adjacentDestPipe.pipeLocation, adjacentDestPipe.node, path, nextCursor);
        }

        return null;
    }

    /**
     * Builds packet waypoints for controller -> pipes -> inventory.
     */
    public static List<Object> buildWaypoints(Object controllerLocation, List<PipeNode> path, Object inventoryLocation) {
        List<Object> waypoints = new ArrayList<>();
        waypoints.add(controllerLocation);
        for (PipeNode node : path) {
            waypoints.add(node.getLocation());
        }
        waypoints.add(inventoryLocation);
        if (waypoints.size() < 2) {
            waypoints.add(waypoints.get(0));
        }
        return waypoints;
    }
}
