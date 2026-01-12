package dev.cloudframe.common.power;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.cloudframe.common.power.cables.CableConnectionService;
import dev.cloudframe.common.power.cables.CableKey;
import dev.cloudframe.common.util.DirIndex;

/**
 * Platform-agnostic power network manager.
 *
 * <p>Conceptually mirrors the Fabric implementation (cloud cables, producers, storage cells,
 * optional external energy endpoints), but all world/block interactions are provided via
 * the {@link Access} adapter.</p>
 */
public final class PowerNetworkManager {

    public static final int DEFAULT_BFS_NODE_LIMIT = 8192;

    private final LocationAdapter locations;
    private final Access access;
    private final CableConnectionService cableConnections; // nullable
    private final int bfsNodeLimit;

    private long currentTick = -1L;
    private final Map<NetworkKey, NetworkSnapshot> snapshots = new HashMap<>();

    public PowerNetworkManager(LocationAdapter locations, Access access) {
        this(locations, access, null, DEFAULT_BFS_NODE_LIMIT);
    }

    public PowerNetworkManager(LocationAdapter locations, Access access, CableConnectionService cableConnections) {
        this(locations, access, cableConnections, DEFAULT_BFS_NODE_LIMIT);
    }

    public PowerNetworkManager(LocationAdapter locations, Access access, CableConnectionService cableConnections, int bfsNodeLimit) {
        this.locations = Objects.requireNonNull(locations, "locations");
        this.access = Objects.requireNonNull(access, "access");
        this.cableConnections = cableConnections;
        this.bfsNodeLimit = Math.max(128, bfsNodeLimit);
    }

    public record NetworkInfo(long producedCfePerTick, long storedCfe) {
    }

    public record CableProbeInfo(
        long producedCfePerTick,
        long storedCfe,
        boolean externalApiPresent,
        int externalEndpointCount,
        long externalStoredCfe,
        long externalCapacityCfe
    ) {
    }

    private record NetworkKey(String worldId, long rootCableKeyHash) {
    }

    public record ExternalEndpoint(Object location, int sideDirIndex) {
        public ExternalEndpoint {
            if (sideDirIndex < 0 || sideDirIndex >= 6) {
                throw new IllegalArgumentException("sideDirIndex must be 0..5");
            }
        }
    }

    private static final class NetworkSnapshot {
        final long tick;
        long remainingGenerationCfe;
        final List<Object> cells;
        final List<ExternalEndpoint> external;

        NetworkSnapshot(long tick, long producedCfe, List<Object> cells, List<ExternalEndpoint> external) {
            this.tick = tick;
            this.remainingGenerationCfe = Math.max(0L, producedCfe);
            this.cells = cells;
            this.external = external;
        }
    }

    private static final class NetworkDiscovery {
        final Object rootCableLocation;
        final long producedCfePerTick;
        final List<Object> cells;
        final List<ExternalEndpoint> external;

        NetworkDiscovery(Object rootCableLocation, long producedCfePerTick, List<Object> cells, List<ExternalEndpoint> external) {
            this.rootCableLocation = rootCableLocation;
            this.producedCfePerTick = producedCfePerTick;
            this.cells = cells;
            this.external = external;
        }
    }

    public interface LocationAdapter {
        Object normalize(Object loc);
        Object offset(Object loc, int dx, int dy, int dz);
        String worldId(Object loc);
        int blockX(Object loc);
        int blockY(Object loc);
        int blockZ(Object loc);

        default String key(Object loc) {
            if (loc == null) return "null";
            String w = worldId(loc);
            if (w == null) w = "null";
            return w + ":" + blockX(loc) + "," + blockY(loc) + "," + blockZ(loc);
        }

        default CableKey toCableKey(Object loc) {
            if (loc == null) return null;
            String w = worldId(loc);
            if (w == null) return null;
            return new CableKey(w, blockX(loc), blockY(loc), blockZ(loc));
        }
    }

    public interface Access {
        boolean isCable(Object ctx, Object loc);

        /**
         * Whether the given cable side is disabled for connectivity.
         *
         * <p>If a {@link CableConnectionService} is supplied to the manager constructor,
         * that service is used instead of this hook.</p>
         */
        default boolean isCableSideDisabled(Object ctx, Object cableLoc, int dirIndex) {
            return false;
        }

        boolean isProducer(Object ctx, Object loc);

        /**
         * Returns the producer's current per-tick output (CFE/t).
         * Only called when {@link #isProducer(Object, Object)} returned true.
         */
        long producerCfePerTick(Object ctx, Object producerLoc);

        boolean isCell(Object ctx, Object loc);

        long cellInsertCfe(Object ctx, Object cellLoc, long amount);

        long cellExtractCfe(Object ctx, Object cellLoc, long amount);

        long cellStoredCfe(Object ctx, Object cellLoc);

        /**
         * Whether external energy endpoints are supported/available.
         *
         * <p>If false, {@link #isExternalStorage(Object, Object, int)} will not be called.</p>
         */
        default boolean externalApiPresent(Object ctx) {
            return false;
        }

        /**
         * True if {@code loc} is an external storage that can be interacted with from the given face.
         *
         * <p>{@code sideDirIndex} is the face on the external block that touches the cable block.</p>
         */
        default boolean isExternalStorage(Object ctx, Object externalLoc, int sideDirIndex) {
            return false;
        }

        default long externalExtractCfe(Object ctx, Object externalLoc, int sideDirIndex, long amount) {
            return 0L;
        }

        default long externalStoredCfe(Object ctx, Object externalLoc, int sideDirIndex) {
            return 0L;
        }

        default long externalCapacityCfe(Object ctx, Object externalLoc, int sideDirIndex) {
            return 0L;
        }
    }

    public void beginTick(Object ctx, long tick) {
        if (tick == currentTick) return;
        currentTick = tick;
        snapshots.clear();
    }

    /**
     * Best-effort: store any unused per-tick generation into available cells.
     */
    public void endTick(Object ctx) {
        if (snapshots.isEmpty()) return;

        for (NetworkSnapshot snap : snapshots.values()) {
            long remaining = snap.remainingGenerationCfe;
            if (remaining <= 0L) continue;

            for (Object cellLoc : snap.cells) {
                if (remaining <= 0L) break;
                if (!access.isCell(ctx, cellLoc)) continue;
                long inserted = Math.max(0L, access.cellInsertCfe(ctx, cellLoc, remaining));
                remaining -= inserted;
            }

            snap.remainingGenerationCfe = 0L;
        }
    }

    public long extractPowerCfe(Object ctx, Object controllerLoc, long amount) {
        if (amount <= 0L) return 0L;

        Object ctrl = locations.normalize(controllerLoc);
        if (ctrl == null) return 0L;

        NetworkDiscovery discovery = discoverFromController(ctx, ctrl);
        if (discovery == null) return 0L;

        NetworkSnapshot snap = getOrCreateSnapshot(ctx, discovery);

        long extracted = 0L;

        long takeGen = Math.min(amount, snap.remainingGenerationCfe);
        if (takeGen > 0L) {
            snap.remainingGenerationCfe -= takeGen;
            extracted += takeGen;
            amount -= takeGen;
        }

        if (amount > 0L && !snap.cells.isEmpty()) {
            for (Object cellLoc : snap.cells) {
                if (amount <= 0L) break;
                if (!access.isCell(ctx, cellLoc)) continue;

                long got = Math.max(0L, access.cellExtractCfe(ctx, cellLoc, amount));
                if (got > 0L) {
                    extracted += got;
                    amount -= got;
                }
            }
        }

        if (amount > 0L && access.externalApiPresent(ctx) && snap.external != null && !snap.external.isEmpty()) {
            for (ExternalEndpoint ep : snap.external) {
                if (amount <= 0L) break;
                if (!access.isExternalStorage(ctx, ep.location, ep.sideDirIndex)) continue;

                long got = Math.max(0L, access.externalExtractCfe(ctx, ep.location, ep.sideDirIndex, amount));
                if (got > 0L) {
                    extracted += got;
                    amount -= got;
                }
            }
        }

        return extracted;
    }

    /**
     * Extract ONLY from the network's per-tick generation budget (no cells, no external storages).
     */
    public long extractGenerationOnlyCfe(Object ctx, Object controllerLoc, long amount) {
        if (amount <= 0L) return 0L;

        Object ctrl = locations.normalize(controllerLoc);
        if (ctrl == null) return 0L;

        NetworkDiscovery discovery = discoverFromController(ctx, ctrl);
        if (discovery == null) return 0L;

        NetworkSnapshot snap = getOrCreateSnapshot(ctx, discovery);

        long take = Math.min(amount, snap.remainingGenerationCfe);
        if (take <= 0L) return 0L;

        snap.remainingGenerationCfe -= take;
        return take;
    }

    public NetworkInfo measureNetwork(Object ctx, Object controllerLoc) {
        Object ctrl = locations.normalize(controllerLoc);
        if (ctrl == null) return new NetworkInfo(0L, 0L);

        NetworkDiscovery discovery = discoverFromController(ctx, ctrl);
        if (discovery == null) return new NetworkInfo(0L, 0L);

        long stored = 0L;
        if (discovery.cells != null && !discovery.cells.isEmpty()) {
            for (Object cellLoc : discovery.cells) {
                if (!access.isCell(ctx, cellLoc)) continue;
                stored += Math.max(0L, access.cellStoredCfe(ctx, cellLoc));
            }
        }

        return new NetworkInfo(Math.max(0L, discovery.producedCfePerTick), Math.max(0L, stored));
    }

    /**
     * Read-only probe info for the cable network adjacent to a controller location.
     * This does NOT consume power.
     */
    public CableProbeInfo measureNetworkForProbe(Object ctx, Object controllerLoc) {
        Object ctrl = locations.normalize(controllerLoc);
        boolean externalApi = access.externalApiPresent(ctx);

        if (ctrl == null) {
            return new CableProbeInfo(0L, 0L, externalApi, 0, 0L, 0L);
        }

        NetworkDiscovery discovery = discoverFromController(ctx, ctrl);
        if (discovery == null) {
            return new CableProbeInfo(0L, 0L, externalApi, 0, 0L, 0L);
        }

        long stored = 0L;
        if (discovery.cells != null && !discovery.cells.isEmpty()) {
            for (Object cellLoc : discovery.cells) {
                if (!access.isCell(ctx, cellLoc)) continue;
                stored += Math.max(0L, access.cellStoredCfe(ctx, cellLoc));
            }
        }

        int externalCount = 0;
        long externalStored = 0L;
        long externalCap = 0L;

        if (externalApi && discovery.external != null && !discovery.external.isEmpty()) {
            for (ExternalEndpoint ep : discovery.external) {
                if (!access.isExternalStorage(ctx, ep.location, ep.sideDirIndex)) continue;
                externalCount++;
                externalStored += Math.max(0L, access.externalStoredCfe(ctx, ep.location, ep.sideDirIndex));
                externalCap += Math.max(0L, access.externalCapacityCfe(ctx, ep.location, ep.sideDirIndex));
            }
        }

        return new CableProbeInfo(
            Math.max(0L, discovery.producedCfePerTick),
            Math.max(0L, stored),
            externalApi,
            externalCount,
            Math.max(0L, externalStored),
            Math.max(0L, externalCap)
        );
    }

    public NetworkInfo measureCableNetwork(Object ctx, Object cableLoc) {
        Object start = locations.normalize(cableLoc);
        if (start == null) return new NetworkInfo(0L, 0L);
        if (!access.isCable(ctx, start)) return new NetworkInfo(0L, 0L);

        NetworkDiscovery discovery = discoverFromCable(ctx, start);
        if (discovery == null) return new NetworkInfo(0L, 0L);

        long stored = 0L;
        if (discovery.cells != null && !discovery.cells.isEmpty()) {
            for (Object cellLoc : discovery.cells) {
                if (!access.isCell(ctx, cellLoc)) continue;
                stored += Math.max(0L, access.cellStoredCfe(ctx, cellLoc));
            }
        }

        return new NetworkInfo(Math.max(0L, discovery.producedCfePerTick), Math.max(0L, stored));
    }

    public CableProbeInfo measureCableNetworkForProbe(Object ctx, Object cableLoc) {
        Object start = locations.normalize(cableLoc);
        boolean externalApi = access.externalApiPresent(ctx);

        if (start == null || !access.isCable(ctx, start)) {
            return new CableProbeInfo(0L, 0L, externalApi, 0, 0L, 0L);
        }

        NetworkDiscovery discovery = discoverFromCable(ctx, start);
        if (discovery == null) {
            return new CableProbeInfo(0L, 0L, externalApi, 0, 0L, 0L);
        }

        long stored = 0L;
        if (discovery.cells != null && !discovery.cells.isEmpty()) {
            for (Object cellLoc : discovery.cells) {
                if (!access.isCell(ctx, cellLoc)) continue;
                stored += Math.max(0L, access.cellStoredCfe(ctx, cellLoc));
            }
        }

        int externalCount = 0;
        long externalStored = 0L;
        long externalCap = 0L;

        if (externalApi && discovery.external != null && !discovery.external.isEmpty()) {
            for (ExternalEndpoint ep : discovery.external) {
                if (!access.isExternalStorage(ctx, ep.location, ep.sideDirIndex)) continue;
                externalCount++;
                externalStored += Math.max(0L, access.externalStoredCfe(ctx, ep.location, ep.sideDirIndex));
                externalCap += Math.max(0L, access.externalCapacityCfe(ctx, ep.location, ep.sideDirIndex));
            }
        }

        return new CableProbeInfo(
            Math.max(0L, discovery.producedCfePerTick),
            Math.max(0L, stored),
            externalApi,
            externalCount,
            Math.max(0L, externalStored),
            Math.max(0L, externalCap)
        );
    }

    private NetworkSnapshot getOrCreateSnapshot(Object ctx, NetworkDiscovery discovery) {
        Object rootLoc = discovery.rootCableLocation;
        String worldId = locations.worldId(rootLoc);
        if (worldId == null) worldId = "null";

        long rootHash = locations.key(rootLoc).hashCode();
        NetworkKey key = new NetworkKey(worldId, rootHash);

        NetworkSnapshot snap = snapshots.get(key);
        if (snap == null || snap.tick != currentTick) {
            snap = new NetworkSnapshot(currentTick, discovery.producedCfePerTick, discovery.cells, discovery.external);
            snapshots.put(key, snap);
        }

        return snap;
    }

    private NetworkDiscovery discoverFromController(Object ctx, Object controllerLoc) {
        // Start from adjacent cables (controller does not count as a cable node).
        ArrayDeque<Object> queue = new ArrayDeque<>();
        HashSet<String> visited = new HashSet<>();

        for (int dirIndex = 0; dirIndex < 6; dirIndex++) {
            Object adj = locations.offset(controllerLoc, DirIndex.dx(dirIndex), DirIndex.dy(dirIndex), DirIndex.dz(dirIndex));
            adj = locations.normalize(adj);
            if (adj == null) continue;

            if (!access.isCable(ctx, adj)) continue;
            if (isSideDisabled(ctx, adj, dirIndex ^ 1)) continue; // opposite face on the cable

            String k = locations.key(adj);
            if (visited.add(k)) {
                queue.add(adj);
            }
        }

        if (queue.isEmpty()) return null;

        return bfsDiscover(ctx, queue, visited);
    }

    private NetworkDiscovery discoverFromCable(Object ctx, Object cableLoc) {
        ArrayDeque<Object> queue = new ArrayDeque<>();
        HashSet<String> visited = new HashSet<>();

        Object start = locations.normalize(cableLoc);
        if (start == null || !access.isCable(ctx, start)) return null;

        visited.add(locations.key(start));
        queue.add(start);

        return bfsDiscover(ctx, queue, visited);
    }

    private NetworkDiscovery bfsDiscover(Object ctx, ArrayDeque<Object> queue, HashSet<String> visited) {
        Object rootLoc = null;
        String rootKey = null;
        int nodes = 0;

        long produced = 0L;

        HashSet<String> producerKeys = new HashSet<>();
        HashSet<String> cellKeys = new HashSet<>();
        List<Object> cellLocs = new ArrayList<>();

        HashSet<String> externalKeys = new HashSet<>();
        List<ExternalEndpoint> externalEndpoints = new ArrayList<>();

        while (!queue.isEmpty() && nodes < bfsNodeLimit) {
            Object pos = queue.poll();
            if (pos == null) continue;
            nodes++;

            String key = locations.key(pos);
            if (rootKey == null || key.compareTo(rootKey) < 0) {
                rootKey = key;
                rootLoc = pos;
            }

            // Discover adjacent producers/storage/external.
            for (int dirIndex = 0; dirIndex < 6; dirIndex++) {
                if (isSideDisabled(ctx, pos, dirIndex)) continue;

                Object n = locations.offset(pos, DirIndex.dx(dirIndex), DirIndex.dy(dirIndex), DirIndex.dz(dirIndex));
                n = locations.normalize(n);
                if (n == null) continue;

                if (access.isProducer(ctx, n)) {
                    String pk = locations.key(n);
                    if (producerKeys.add(pk)) {
                        produced += Math.max(0L, access.producerCfePerTick(ctx, n));
                    }
                } else if (access.isCell(ctx, n)) {
                    String ck = locations.key(n);
                    if (cellKeys.add(ck)) {
                        cellLocs.add(n);
                    }
                } else if (access.externalApiPresent(ctx) && !access.isCable(ctx, n)) {
                    int faceOnExternal = dirIndex ^ 1; // neighbor face pointing back to cable
                    if (access.isExternalStorage(ctx, n, faceOnExternal)) {
                        String ek = locations.key(n) + "@" + faceOnExternal;
                        if (externalKeys.add(ek)) {
                            externalEndpoints.add(new ExternalEndpoint(n, faceOnExternal));
                        }
                    }
                }
            }

            // Traverse to adjacent cables.
            for (int dirIndex = 0; dirIndex < 6; dirIndex++) {
                Object n = locations.offset(pos, DirIndex.dx(dirIndex), DirIndex.dy(dirIndex), DirIndex.dz(dirIndex));
                n = locations.normalize(n);
                if (n == null) continue;
                if (!access.isCable(ctx, n)) continue;

                if (isSideDisabled(ctx, pos, dirIndex)) continue;
                if (isSideDisabled(ctx, n, dirIndex ^ 1)) continue;

                String nk = locations.key(n);
                if (visited.add(nk)) {
                    queue.add(n);
                }
            }
        }

        if (rootLoc == null) return null;

        return new NetworkDiscovery(rootLoc, produced, cellLocs, externalEndpoints);
    }

    private boolean isSideDisabled(Object ctx, Object cableLoc, int dirIndex) {
        if (cableConnections != null) {
            CableKey key = locations.toCableKey(cableLoc);
            if (key == null) return false;
            return cableConnections.isSideDisabled(key, dirIndex);
        }

        return access.isCableSideDisabled(ctx, cableLoc, dirIndex);
    }
}
