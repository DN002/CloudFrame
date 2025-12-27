package dev.cloudframe.cloudframe.tubes;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import dev.cloudframe.cloudframe.util.InventoryUtil;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

import java.util.*;

public class TubeNetworkManager {

    private static final Debug debug = DebugManager.get(TubeNetworkManager.class);

    private final Map<Location, TubeNode> tubes = new HashMap<>();

    // 6-direction adjacency vectors
    private static final Vector[] DIRS = {
        new Vector(1,0,0),
        new Vector(-1,0,0),
        new Vector(0,1,0),
        new Vector(0,-1,0),
        new Vector(0,0,1),
        new Vector(0,0,-1)
    };

    // Normalize a location to block coordinates only
    private Location norm(Location loc) {
        return new Location(
            loc.getWorld(),
            loc.getBlockX(),
            loc.getBlockY(),
            loc.getBlockZ()
        );
    }

    // =========================
    //  Tube Registration
    // =========================

    public void addTube(Location loc) {
        loc = norm(loc);

        debug.log("addTube", "Adding tube at " + loc);

        TubeNode node = new TubeNode(loc);
        tubes.put(loc, node);

        rebuildNeighbors(loc);
    }

    public void removeTube(Location loc) {
        loc = norm(loc);

        debug.log("removeTube", "Removing tube at " + loc);

        tubes.remove(loc);
        rebuildAll();
    }

    public TubeNode getTube(Location loc) {
        return tubes.get(norm(loc));
    }

    public Collection<TubeNode> all() {
        return tubes.values();
    }

    // =========================
    //  Neighbor Rebuilding
    // =========================

    private void rebuildNeighbors(Location loc) {
        loc = norm(loc);

        debug.log("rebuildNeighbors", "Rebuilding neighbors for " + loc);

        TubeNode node = tubes.get(loc);
        if (node == null) {
            debug.log("rebuildNeighbors", "No tube found at " + loc);
            return;
        }

        node.clearNeighbors();

        for (Vector v : DIRS) {
            Location adj = loc.clone().add(v);
            TubeNode neighbor = tubes.get(adj);

            if (neighbor != null) {
                node.addNeighbor(neighbor);
                neighbor.addNeighbor(node);
                debug.log("rebuildNeighbors", "Connected " + loc + " <-> " + adj);
            }
        }
    }

    private void rebuildAll() {
        debug.log("rebuildAll", "Rebuilding all tube neighbors (" + tubes.size() + " tubes)");

        for (TubeNode node : tubes.values()) {
            node.clearNeighbors();
        }
        for (Location loc : tubes.keySet()) {
            rebuildNeighbors(loc);
        }
    }

    // =========================
    //  BFS Pathfinding
    // =========================

    public List<TubeNode> findPath(TubeNode start, TubeNode end) {
        debug.log("findPath", "Finding path from " + start.getLocation() +
                " to " + end.getLocation());

        Queue<TubeNode> queue = new LinkedList<>();
        Map<TubeNode, TubeNode> parent = new HashMap<>();

        queue.add(start);
        parent.put(start, null);

        while (!queue.isEmpty()) {
            TubeNode current = queue.poll();

            if (current == end) {
                List<TubeNode> path = buildPath(parent, end);
                debug.log("findPath", "Path found, length=" + path.size());
                return path;
            }

            for (TubeNode n : current.getNeighbors()) {
                if (!parent.containsKey(n)) {
                    parent.put(n, current);
                    queue.add(n);
                }
            }
        }

        debug.log("findPath", "No path found");
        return null;
    }

    private List<TubeNode> buildPath(Map<TubeNode, TubeNode> parent, TubeNode end) {
        List<TubeNode> path = new ArrayList<>();
        TubeNode cur = end;

        while (cur != null) {
            path.add(cur);
            cur = parent.get(cur);
        }

        Collections.reverse(path);
        return path;
    }

    // =========================
    //  Inventory Discovery
    // =========================

    public List<Location> findInventoriesNear(TubeNode start) {
        debug.log("findInventoriesNear", "Searching inventories near " + start.getLocation());

        Set<Location> result = new HashSet<>();

        Queue<TubeNode> queue = new LinkedList<>();
        Set<TubeNode> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            TubeNode current = queue.poll();
            Location base = current.getLocation();

            // Check all 6 adjacent blocks for inventories
            for (Vector v : DIRS) {
                Location adj = base.clone().add(v);
                Block block = adj.getBlock();

                if (InventoryUtil.isInventory(block)) {
                    debug.log("findInventoriesNear", "Found inventory at " + adj);
                    result.add(adj);
                }
            }

            // Continue BFS through tubes
            for (TubeNode n : current.getNeighbors()) {
                if (visited.add(n)) {
                    queue.add(n);
                }
            }
        }

        debug.log("findInventoriesNear", "Found " + result.size() + " inventories");
        return new ArrayList<>(result);
    }

    public List<TubeNode> getNeighborsOf(Location loc) {
        TubeNode node = tubes.get(norm(loc));
        if (node == null) {
            debug.log("getNeighborsOf", "No tube at " + loc);
            return List.of();
        }
        return node.getNeighbors();
    }

    // =========================
    //  Persistence
    // =========================

    public void saveAll() {
        debug.log("saveAll", "Saving " + tubes.size() + " tubes to database");

        dev.cloudframe.cloudframe.storage.Database.run(conn -> {
            conn.createStatement().executeUpdate("DELETE FROM tubes");

            var ps = conn.prepareStatement(
                "INSERT INTO tubes (world, x, y, z) VALUES (?, ?, ?, ?)"
            );

            for (TubeNode node : tubes.values()) {
                Location loc = node.getLocation();

                debug.log("saveAll", "Saving tube at " + loc);

                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());

                ps.addBatch();
            }

            ps.executeBatch();
        });

        debug.log("saveAll", "Finished saving tubes");
    }

    public void loadAll() {
        debug.log("loadAll", "Loading tubes from database");

        dev.cloudframe.cloudframe.storage.Database.run(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT * FROM tubes");

            while (rs.next()) {
                String world = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");

                var w = org.bukkit.Bukkit.getWorld(world);
                if (w == null) {
                    debug.log("loadAll", "World not found: " + world + " — skipping tube");
                    continue;
                }

                Location loc = new Location(w, x, y, z);

                debug.log("loadAll", "Loaded tube at " + loc);

                addTube(loc);
            }
        });

        debug.log("loadAll", "Finished loading tubes");
    }
}
