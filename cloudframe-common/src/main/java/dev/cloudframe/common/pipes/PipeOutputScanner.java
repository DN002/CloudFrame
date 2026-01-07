package dev.cloudframe.common.pipes;

import java.util.ArrayDeque;
import java.util.HashSet;

/**
 * Shared traversal utilities for pipe networks that depend only on {@link PipeConnectivityAccess}.
 */
public final class PipeOutputScanner {

    private PipeOutputScanner() {
    }

    /**
     * Returns true when the given controller location is connected (via pipe connectivity arms)
     * to at least one inventory.
     *
     * <p>This method deliberately does not depend on platform-specific world APIs.
     * It also does not depend on the in-memory pipe graph cache, so it stays correct
     * even if the cache is missing or stale.</p>
     */
    public static boolean hasValidOutputFrom(Object controllerLoc, PipeConnectivityAccess access, int maxScanPipes) {
        if (controllerLoc == null || access == null) return false;

        Object ctrl = access.normalize(controllerLoc);

        // Fast path: direct adjacent inventory.
        for (int dirIdx = 0; dirIdx < PipeNetworkManager.DIRS.length; dirIdx++) {
            Object adj = access.offset(ctrl, dirIdx);
            if (adj == null) continue;
            if (!access.isChunkLoaded(adj)) continue;
            if (access.isInventoryAt(adj)) return true;
        }

        int limit = maxScanPipes <= 0 ? 8192 : maxScanPipes;

        ArrayDeque<Object> queue = new ArrayDeque<>();
        HashSet<Object> visited = new HashSet<>();

        // Seed BFS with pipes that are ACTUALLY connected to the controller.
        for (int dirIdx = 0; dirIdx < PipeNetworkManager.DIRS.length; dirIdx++) {
            Object pipeLoc = access.offset(ctrl, dirIdx);
            if (pipeLoc == null) continue;
            if (!access.isChunkLoaded(pipeLoc)) continue;
            if (!access.isPipeAt(pipeLoc)) continue;

            int towardControllerIdx = oppositeDirIndex(dirIdx);
            if (towardControllerIdx < 0) continue;

            // The pipe must expose an arm facing back to the controller.
            if (!access.pipeConnects(pipeLoc, towardControllerIdx)) continue;

            queue.add(access.normalize(pipeLoc));
        }

        while (!queue.isEmpty() && visited.size() < limit) {
            Object pipeLoc = queue.pollFirst();
            if (pipeLoc == null) continue;
            pipeLoc = access.normalize(pipeLoc);
            if (!visited.add(pipeLoc)) continue;

            if (!access.isChunkLoaded(pipeLoc)) continue;
            if (!access.isPipeAt(pipeLoc)) continue;

            for (int dirIdx = 0; dirIdx < PipeNetworkManager.DIRS.length; dirIdx++) {
                if (!access.pipeConnects(pipeLoc, dirIdx)) continue;

                Object neighbor = access.offset(pipeLoc, dirIdx);
                if (neighbor == null) continue;
                if (!access.isChunkLoaded(neighbor)) continue;

                if (access.isPipeAt(neighbor)) {
                    int opposite = oppositeDirIndex(dirIdx);
                    if (opposite >= 0 && access.pipeConnects(neighbor, opposite)) {
                        Object norm = access.normalize(neighbor);
                        if (!visited.contains(norm)) {
                            queue.add(norm);
                        }
                    }
                    continue;
                }

                if (access.isInventoryAt(neighbor)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static int oppositeDirIndex(int dirIdx) {
        return switch (dirIdx) {
            case 0 -> 1;
            case 1 -> 0;
            case 2 -> 3;
            case 3 -> 2;
            case 4 -> 5;
            case 5 -> 4;
            default -> -1;
        };
    }
}
