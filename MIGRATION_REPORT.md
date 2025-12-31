# CloudFrame Multi-Platform Migration Analysis Report

**Date:** December 31, 2025  
**Scope:** Analysis of 5 core files for Bukkit-to-Platform-Agnostic migration  
**Target:** Move to cloudframe-common with adapter pattern

---

## Executive Summary

This report analyzes **Quarry.java**, **QuarryManager.java**, **TubeNetworkManager.java**, **ItemPacket.java**, and **ItemPacketManager.java** for multi-platform abstraction. The analysis reveals:

- **~75% of code can be moved to cloudframe-common** (logic, algorithms, state management)
- **~25% is Bukkit-specific** (Location, ItemStack, Block, World, Entity handling)
- **Primary barrier:** Bukkit types embedded in method signatures and constructors
- **Solution:** Adapter pattern + interface abstraction for platform-specific operations

---

## 1. QUARRY.JAVA

### File Overview
- **Lines:** 1014
- **Primary Role:** Core quarry mining state machine and block extraction logic
- **Key Responsibilities:** Mining simulation, output buffering, inventory routing, augment management

### Bukkit Imports Analysis

| Import | Type | Status | Platform-Agnostic? |
|--------|------|--------|-------------------|
| `org.bukkit.Location` | Class | CRITICAL | ❌ No (fundamental positional data) |
| `org.bukkit.Material` | Enum | HIGH | ⚠️ Partial (core logic yes, type checking no) |
| `org.bukkit.Particle` | Class | MEDIUM | ⚠️ Partial (VFX only) |
| `org.bukkit.Sound` | Class | MEDIUM | ⚠️ Partial (audio only) |
| `org.bukkit.SoundGroup` | Class | MEDIUM | ⚠️ Partial (audio metadata only) |
| `org.bukkit.World` | Class | CRITICAL | ❌ No (required for block access) |
| `org.bukkit.block.Block` | Class | CRITICAL | ❌ No (core mining operation) |
| `org.bukkit.block.data.BlockData` | Class | HIGH | ❌ No (type-specific behavior) |
| `org.bukkit.entity.Player` | Class | MEDIUM | ⚠️ Partial (VFX distribution only) |
| `org.bukkit.enchantments.Enchantment` | Class | MEDIUM | ⚠️ Partial (tool creation only) |
| `org.bukkit.inventory.ItemStack` | Class | CRITICAL | ❌ No (core output type) |
| `org.bukkit.util.Vector` | Class | HIGH | ⚠️ Can be abstracted (directional math) |

### Key Methods Needing Refactor

#### 1. **Constructor & State Initialization**
```java
public Quarry(UUID owner, Location posA, Location posB, Region region, 
              Location controller, int controllerYaw)
```
**Issue:** Accepts `Location` and `Region` (which contains Bukkit `Location`)  
**Refactor Strategy:** 
- Create platform-agnostic `BlockCoordinate` class (x, y, z, worldId)
- Create platform-agnostic `QuarryRegion` class (min/max coordinates + worldId)
- Keep this class in common; accept coordinates as primitives + worldId string

#### 2. **Mining Logic - tick()**
```java
Block block = world.getBlockAt(currentX, currentY, currentZ);
Material type = block.getType();
// ... getDrops(tool), setType(Material.AIR), spawnParticle()
```
**Issue:** Direct Block manipulation, Material checking, VFX  
**Refactor Strategy:**
- Move algorithmic logic to common (current iteration, state machine)
- Create `IBlockAccessor` interface in common:
  ```java
  interface IBlockAccessor {
      BlockType getBlockAt(int x, int y, int z);
      List<ItemStack> getDrops(int x, int y, int z, ItemStack tool);
      void setBlockType(int x, int y, int z, BlockType type);
      void playBreakSound(int x, int y, int z, BlockType type);
      void spawnBreakParticles(int x, int y, int z, BlockType type);
  }
  ```
- Implement adapter in cloudframe-bukkit

#### 3. **Mining Tool Creation**
```java
private static ItemStack createMiningTool(boolean silkTouch)
```
**Issue:** `ItemStack` with `Enchantment.SILK_TOUCH`  
**Refactor Strategy:**
- Create `IMiningToolFactory` interface
- Move tool creation logic to adapter layer
- Return abstract `ItemStack` wrapper that holds platform-specific data

#### 4. **Block Crack Visualization**
```java
private void sendBlockCrack(Location loc, float progress01) {
    for (Player player : loc.getWorld().getPlayers()) {
        player.sendBlockDamage(loc, p);
    }
}
```
**Issue:** Bukkit-specific player casting + damage API  
**Refactor Strategy:**
- Create `IVisualsManager` interface with `sendBlockCrack()` method
- Move to adapter, inject into Quarry
- Keep VFX out of core logic entirely (make optional)

#### 5. **Output Routing**
```java
List<Location> inventories = CloudFrameRegistry.tubes().findInventoriesNear(startTube);
```
**Issue:** Returns `List<Location>`, depends on TubeNetworkManager  
**Refactor Strategy:**
- Already good: use coordinates + worldId instead of `Location`
- Return `List<BlockCoordinate>` from adapter instead

### Key Methods That Can Stay As-Is
- `isActive()`, getters for state (uuid, coordinates, yaw, augments)
- `setActive()` - pure state management
- `getBlocksMined()`, `getTotalBlocksInRegion()` - counters
- `findNextBlockToMine()` - algorithmic (operates on coordinates)
- `advancePosition()` - state iteration
- `computeTotalBlocks()` - calculation
- `startMetadataScan()` - scan state

### Platform-Specific Operations to Externalize

| Operation | Current Method | Abstraction Needed | Moved To |
|-----------|----------------|-------------------|----------|
| Get block type | `block.getType()` | `IBlockAccessor.getBlockAt()` | BukkitBlockAccessor |
| Mine block | `block.setType()`, `block.getDrops()` | `IBlockAccessor.mineBlock()` | BukkitBlockAccessor |
| Sound FX | `playBreakSound()` | `IVfxManager.playBreakSound()` | BukkitVfxManager |
| Particle FX | `spawnParticle()` | `IVfxManager.spawnParticles()` | BukkitVfxManager |
| Block damage crack | `sendBlockCrack()` | `IVfxManager.sendBlockCrack()` | BukkitVfxManager |
| Get world | `posA.getWorld()` | Store as `worldId` string | Already partially done |
| Check chunk loaded | `world.isChunkLoaded()` | `IWorldAccessor.isChunkLoaded()` | BukkitWorldAccessor |
| Drop items | `world.dropItemNaturally()` | `IInventoryManager.dropItems()` | BukkitInventoryManager |

### Complexity Level: **MEDIUM-HIGH**

**Factors:**
- ✅ Core logic is platform-agnostic (iteration, state machine)
- ❌ Heavy Bukkit integration in mining/output pipeline
- ❌ Requires 4-5 new adapter interfaces
- ✅ Clear separation of concerns possible

---

## 2. QUARRYMANAGER.JAVA

### File Overview
- **Lines:** 513
- **Primary Role:** Quarry lifecycle management, persistence, chunk-based indexing
- **Key Responsibilities:** Registration, DB load/save, controller tracking, tube integration

### Bukkit Imports Analysis

| Import | Type | Status | Platform-Agnostic? |
|--------|------|--------|-------------------|
| `org.bukkit.Location` | Class | CRITICAL | ❌ No (stored as keys in maps) |
| `org.bukkit.Bukkit` | Class | HIGH | ⚠️ Partial (world lookup only) |
| `org.bukkit.Material` | Enum | LOW | ⚠️ Partial (cleanup only) |
| `org.bukkit.plugin.java.JavaPlugin` | Class | MEDIUM | ❌ No (lifecycle dependency) |
| `org.bukkit.util.Vector` | Class | HIGH | ⚠️ Partial (adjacency vectors only) |

### Key Methods Needing Refactor

#### 1. **Constructor & Initialization**
```java
public void initVisuals(JavaPlugin plugin) {
    if (visualManager != null) return;
    visualManager = new ControllerVisualManager(plugin);
    plugin.getServer().getPluginManager().registerEvents(...);
}
```
**Issue:** Bukkit `JavaPlugin` and event registration  
**Refactor Strategy:**
- Create `IVisualsProvider` interface
- Inject in constructor (dependency injection)
- Move plugin registration to platform layer

#### 2. **Controller Location Tracking**
```java
private final Map<Location, UnregisteredControllerData> unregisteredControllers = new HashMap<>();
private final Map<ChunkKey, Set<Location>> controllersByChunk = new HashMap<>();
```
**Issue:** Using `Location` as map key (mutable, platform-specific)  
**Refactor Strategy:**
- Replace `Location` with `BlockCoordinate` class (immutable, x/y/z/worldId)
- Index by `BlockCoordinate` instead
- Keep chunk indexing but use coordinate-based ChunkKey

#### 3. **Controller Location Queries**
```java
public java.util.List<Location> controllerLocationsInChunk(org.bukkit.Chunk chunk)
```
**Issue:** Takes `Chunk`, returns `Location` list  
**Refactor Strategy:**
- Accept (worldId, chunkX, chunkZ) primitives instead
- Return `List<BlockCoordinate>` instead of `Location`
- Adapter converts Bukkit Chunk to primitives

#### 4. **Persistence - LoadAll()**
```java
var w = Bukkit.getWorld(world);
if (w == null) { ... skip ... }
Location a = new Location(w, ax, ay, az);
```
**Issue:** Hard dependency on Bukkit's world lookup  
**Refactor Strategy:**
- Create `IWorldProvider` interface:
  ```java
  interface IWorldProvider {
      boolean worldExists(String name);
      String getWorldName(UUID worldId);
  }
  ```
- Validate world existence in common layer
- Adapter implements using `Bukkit.getWorld()`
- Store coordinates + worldName in DB, validate on load

#### 5. **Controller Yaw Retrieval**
```java
public int getControllerYaw(Location controllerLoc) {
    controllerLoc = norm(controllerLoc);
    Quarry q = getByController(controllerLoc);
    ...
}
```
**Issue:** Uses `Location` for lookup  
**Refactor Strategy:**
- Accept `BlockCoordinate` + `worldId` instead
- Use coordinate-based lookup

#### 6. **Visualization Updates**
```java
if (visualManager != null) {
    visualManager.ensureController(loc, controllerYaw);
}
```
**Issue:** Manual null checking, tight coupling  
**Refactor Strategy:**
- Inject optional `IVisualsProvider`
- Manager calls provider methods without null checks

### Platform-Specific Operations to Externalize

| Operation | Current Code | Abstraction Needed | Solution |
|-----------|--------------|-------------------|----------|
| World lookup | `Bukkit.getWorld(name)` | `IWorldProvider.worldExists()` | BukkitWorldProvider |
| Location normalization | `norm(Location)` | `BlockCoordinate` class | Common code |
| Chunk registration | `loc.getChunk()`, chunk UUID | Use primitives | Common code |
| Visuals management | `new ControllerVisualManager()` | `IVisualsProvider` interface | BukkitVisualsProvider |
| Event registration | `plugin.getServer().getPluginManager()` | Move to startup | Platform layer |
| Tube integration | `refreshAdjacentTubes()` | Keep as-is (manager-to-manager API) | Common code |

### Key Methods That Can Stay As-Is
- `register()` - pure state management
- `getByController()` - lookup logic
- `updateUnregisteredControllerAugments()` - state update
- `tickAll()` - manager lifecycle
- `all()` - collection accessor
- DB persistence logic - already abstracted via `Database.run()`

### Dependency Analysis

**Strong Dependencies:**
1. `Quarry` (1-many) - already in common
2. `TubeNetworkManager` - via Registry, can stay decoupled
3. `ControllerVisualManager` - needs abstraction
4. Database - already abstracted

**Weak Dependencies:**
- Bukkit event system (can be injected)
- World lookup (can be abstracted)

### Complexity Level: **MEDIUM**

**Factors:**
- ✅ Core registration/lifecycle logic is simple
- ✅ DB operations already abstracted
- ⚠️ Needs Location→BlockCoordinate refactor
- ❌ Tight coupling to ControllerVisualManager
- ✅ Chunk indexing is algorithmic

---

## 3. TUBENETWORKMANAGER.JAVA

### File Overview
- **Lines:** 362
- **Primary Role:** Tube network topology, pathfinding, inventory discovery
- **Key Responsibilities:** Tube graph management, BFS pathfinding, chunk-based indexing, persistence

### Bukkit Imports Analysis

| Import | Type | Status | Platform-Agnostic? |
|--------|------|--------|-------------------|
| `org.bukkit.Location` | Class | CRITICAL | ❌ No (node location storage) |
| `org.bukkit.block.Block` | Class | HIGH | ⚠️ Partial (inventory detection only) |
| `org.bukkit.util.Vector` | Class | HIGH | ✅ Yes (pure math, can stay) |
| `org.bukkit.plugin.java.JavaPlugin` | Class | MEDIUM | ❌ No (visuals init) |

### Key Methods Needing Refactor

#### 1. **Constructor & Visuals**
```java
public void initVisuals(JavaPlugin plugin) {
    if (visualManager != null) return;
    visualManager = new TubeVisualManager(this, plugin);
    plugin.getServer().getPluginManager().registerEvents(...);
}
```
**Issue:** Bukkit plugin dependency  
**Refactor Strategy:** Same as QuarryManager - inject `IVisualsProvider`

#### 2. **Tube Node Storage**
```java
private final Map<Location, TubeNode> tubes = new HashMap<>();
private final Map<ChunkKey, Set<Location>> tubesByChunk = new HashMap<>();

private record ChunkKey(UUID worldId, int cx, int cz) {}
```
**Issue:** `Location` as map key (mutable)  
**Refactor Strategy:**
- `TubeNode` should store `BlockCoordinate` instead of `Location`
- Map key becomes `BlockCoordinate`
- Accessor methods return coordinates, not Location objects

#### 3. **Tube Location Access**
```java
public Collection<Location> tubeLocationsInChunk(org.bukkit.Chunk chunk)
```
**Issue:** Accepts Bukkit Chunk, returns Location list  
**Refactor Strategy:**
- Accept (worldId, chunkX, chunkZ) primitives
- Return `List<BlockCoordinate>`
- Adapter handles conversion

#### 4. **Inventory Discovery**
```java
public List<Location> findInventoriesNear(TubeNode start) {
    ...
    for (Vector v : DIRS) {
        Location adj = base.clone().add(v);
        Block block = adj.getBlock();
        if (InventoryUtil.isInventory(block)) {
            result.add(adj);
        }
    }
    ...
}
```
**Issue:** Block inspection via `Block.getState()`  
**Refactor Strategy:**
- Create `IBlockTypeProvider` interface:
  ```java
  interface IBlockTypeProvider {
      boolean isInventory(int x, int y, int z, String worldId);
      BlockType getBlockType(int x, int y, int z, String worldId);
  }
  ```
- Return `List<BlockCoordinate>` instead of `Location`
- Implement adapter in cloudframe-bukkit

#### 5. **Pathfinding**
```java
public List<TubeNode> findPath(TubeNode start, TubeNode end) {
    // BFS implementation
}
```
**Status:** ✅ **Already platform-agnostic!**  
Operates on TubeNode graph, no Bukkit calls.

#### 6. **Persistence - SaveAll/LoadAll**
```java
public void saveAll() {
    Database.run(conn -> {
        var ps = conn.prepareStatement(
            "INSERT INTO tubes (world, x, y, z) VALUES (?, ?, ?, ?)"
        );
        for (TubeNode node : tubes.values()) {
            Location loc = node.getLocation();
            ps.setString(1, loc.getWorld().getName());
            ps.setInt(2, loc.getBlockX());
            ...
        }
    });
}
```
**Issue:** Calls `loc.getWorld().getName()` on stored Location  
**Refactor Strategy:**
- Store worldId in TubeNode constructor
- DB operations become:
  ```java
  ps.setString(1, node.getWorldId()); // Store worldId directly
  ps.setInt(2, node.getX());
  ps.setInt(3, node.getY());
  ps.setInt(4, node.getZ());
  ```

#### 7. **World Lookup on Load**
```java
var w = org.bukkit.Bukkit.getWorld(world);
if (w == null) { ... skip ... }
Location loc = new Location(w, x, y, z);
```
**Issue:** Hard Bukkit world lookup  
**Refactor Strategy:**
- Validate world via `IWorldProvider.worldExists(worldName)`
- Create TubeNode with (x, y, z, worldName)
- Never convert to Location until passing to adapter

### Platform-Specific Operations to Externalize

| Operation | Code | Abstraction | Solution |
|-----------|------|-----------|----------|
| Inventory detection | `InventoryUtil.isInventory(block)` | `IBlockTypeProvider` | BukkitBlockTypeProvider |
| Get adjacent block | `loc.clone().add(v)` | `BlockCoordinate.add()` | Common class |
| World lookup | `Bukkit.getWorld()` | `IWorldProvider` | BukkitWorldProvider |
| Visuals manager | `TubeVisualManager` | `IVisualsProvider` | BukkitVisualsProvider |
| Chunk key | Built-in, uses worldId UUID | Keep as-is | Common code |

### Key Methods That Can Stay As-Is
- `getTube()` - simple map lookup
- `all()` - collection accessor
- `rebuildNeighbors()`, `rebuildAll()` - topology graph operations
- `findPath()` - BFS algorithm ✅ **Already pure algorithm**
- `buildPath()` - pure algorithm
- `getNeighborsOf()` - graph accessor
- `indexAdd()`, `indexRemove()` - chunk index management

### Complexity Level: **LOW-MEDIUM**

**Factors:**
- ✅ Pathfinding is already pure algorithm (no Bukkit calls)
- ✅ Graph topology is algorithm-only
- ⚠️ Inventory discovery needs adapter
- ⚠️ Location→BlockCoordinate refactor needed
- ✅ DB operations minimal and already abstracted

---

## 4. ITEMPACKET.JAVA

### File Overview
- **Lines:** 217
- **Primary Role:** Item transport visualization entity, waypoint-based movement
- **Key Responsibilities:** ItemDisplay entity lifecycle, smooth movement interpolation, delivery tracking

### Bukkit Imports Analysis

| Import | Type | Status | Platform-Agnostic? |
|--------|------|--------|-------------------|
| `org.bukkit.Location` | Class | CRITICAL | ❌ No (waypoint storage) |
| `org.bukkit.World` | Class | HIGH | ⚠️ Partial (entity spawning) |
| `org.bukkit.entity.Display` | Class | CRITICAL | ❌ No (visual entity type) |
| `org.bukkit.entity.EntityType` | Class | CRITICAL | ❌ No (ITEM_DISPLAY constant) |
| `org.bukkit.entity.ItemDisplay` | Class | CRITICAL | ❌ No (entity manipulation) |
| `org.bukkit.inventory.ItemStack` | Class | CRITICAL | ❌ No (item data) |
| `org.bukkit.util.Transformation` | Class | CRITICAL | ❌ No (entity transformation) |
| `org.joml.*` | Classes | CRITICAL | ❌ No (quaternion/vector math) |

### Key Methods Needing Refactor

#### 1. **Constructor - Waypoint Initialization**
```java
public ItemPacket(ItemStack item, List<Location> waypoints, 
                  Location destinationInventory, 
                  BiConsumer<Location, Integer> onDeliveryCallback)
```
**Issue:** Accepts `ItemStack` and `Location` objects  
**Refactor Strategy:**
- Create platform-agnostic `ItemData` class:
  ```java
  class ItemData {
      String itemType;      // e.g., "diamond_ore", "oak_log"
      int amount;
      Map<String, Integer> enchantments;  // name -> level
      // ... other NBT-equivalent metadata
  }
  ```
- Accept `List<BlockCoordinate>` instead of `List<Location>`
- Keep `destinationInventory` as `BlockCoordinate` (nullable)
- Change callback to: `BiConsumer<BlockCoordinate, Integer>`

#### 2. **Waypoint Conversion Helper**
```java
private static List<Location> toWaypoints(List<TubeNode> path) {
    List<Location> points = new ArrayList<>();
    for (TubeNode node : path) {
        Location loc = node.getLocation();
        points.add(loc.clone().add(0.5, 0.5, 0.5));
    }
    ...
}
```
**Issue:** Converts TubeNode (which has Location) to Location  
**Refactor Strategy:**
- Update TubeNode to store `BlockCoordinate`
- toWaypoints() becomes:
  ```java
  private static List<BlockCoordinate> toWaypoints(List<TubeNode> path) {
      return path.stream()
          .map(node -> node.getCoordinate().centerOffset())
          .collect(toList());
  }
  ```

#### 3. **Entity Spawning - Critical Bukkit Operation**
```java
private void spawnEntity() {
    Location start = waypoints.get(0).clone();
    World world = start.getWorld();
    
    ItemDisplay display = (ItemDisplay) world.spawnEntity(start, EntityType.ITEM_DISPLAY);
    display.setItemStack(item.clone());
    display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
    display.setTransformation(new Transformation(...));
    display.setGravity(false);
    display.setInvulnerable(true);
    display.setPersistent(false);
    display.setInterpolationDelay(0);
    display.setInterpolationDuration(2);
    display.setTeleportDuration(1);
    display.setBillboard(Display.Billboard.FIXED);
}
```
**Issue:** Entire operation is Bukkit-specific VFX  
**Refactor Strategy:**
- Create `IPacketVisualsFactory` interface:
  ```java
  interface IPacketVisualsFactory {
      IPacketVisual createItemDisplayEntity(ItemData item, BlockCoordinate location);
      void updateEntityPosition(IPacketVisual visual, BlockCoordinate position);
      void destroyEntity(IPacketVisual visual);
  }
  
  interface IPacketVisual {
      void teleport(BlockCoordinate pos);
      boolean isAlive();
      BlockCoordinate getLocation();
  }
  ```
- ItemPacket becomes:
  ```java
  public class ItemPacket {
      private final ItemData item;
      private final List<BlockCoordinate> waypoints;
      private final BlockCoordinate destinationInventory;
      private final IPacketVisualsFactory visualsFactory;
      private final IPacketVisual visual;  // Abstract
  }
  ```
- Implement BukkitPacketVisualsFactory in cloudframe-bukkit

#### 4. **Tick & Movement - Core Logic vs VFX**
```java
public boolean tick(boolean shouldLog) {
    if (entity == null || entity.isDead()) { ... }
    
    Location loc = entity.getLocation();
    if (!loc.getWorld().isChunkLoaded(...)) { ... }
    
    progress += SPEED;
    if (progress >= 1.0) { ... }
    
    moveEntity(shouldLog);
}

private void moveEntity(boolean shouldLog) {
    Location a = waypoints.get(currentIndex);
    Location b = waypoints.get(Math.min(currentIndex + 1, ...));
    
    double x = a.getX() + (b.getX() - a.getX()) * progress;
    double y = a.getY() + (b.getY() - a.getY()) * progress;
    double z = a.getZ() + (b.getZ() - a.getZ()) * progress;
    
    Location newLoc = new Location(a.getWorld(), x, y, z);
    entity.teleport(newLoc);
}
```
**Analysis:** 
- ✅ Movement algorithm (progress += SPEED) is pure math
- ❌ VFX updates (teleport) are Bukkit-specific
- ⚠️ Chunk loading checks need abstraction

**Refactor Strategy:**
- Split into two layers:
  ```java
  // Common layer - pure algorithm
  class PacketMovementState {
      int currentIndex;
      double progress;
      
      void tick() { progress += SPEED; }
      boolean isFinished() { return currentIndex >= waypoints.size() - 1; }
      BlockCoordinate getCurrentWaypoint() { ... }
      BlockCoordinate getNextWaypoint() { ... }
  }
  
  // Platform layer - VFX application
  class BukkitItemPacket {
      private PacketMovementState state;
      private IPacketVisualsFactory factory;
      
      void tick() {
          state.tick();
          BlockCoordinate pos = interpolate(state);
          visual.teleport(pos);
      }
  }
  ```

#### 5. **Chunk Loading Checks**
```java
if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) { ... }
```
**Issue:** World chunk status check  
**Refactor Strategy:**
- Create `IChunkProvider` interface:
  ```java
  interface IChunkProvider {
      boolean isChunkLoaded(String worldId, int chunkX, int chunkZ);
  }
  ```

### Platform-Specific Operations to Externalize

| Operation | Code | Abstraction | Solution |
|-----------|------|-----------|----------|
| Spawn ItemDisplay entity | `world.spawnEntity()`, setup transform | `IPacketVisualsFactory` | BukkitPacketVisualsFactory |
| Move entity | `entity.teleport()` | `IPacketVisual.teleport()` | BukkitPacketVisual |
| Check entity alive | `entity.isDead()` | `IPacketVisual.isAlive()` | BukkitPacketVisual |
| Get entity position | `entity.getLocation()` | `IPacketVisual.getLocation()` | BukkitPacketVisual |
| Check chunk loaded | `world.isChunkLoaded()` | `IChunkProvider` | BukkitChunkProvider |
| Destroy entity | `entity.remove()` | `IPacketVisual.destroy()` | BukkitPacketVisual |

### Key Methods That Can Stay As-Is (With Refactored Parameters)
- Constructor logic - just change types to BlockCoordinate/ItemData
- `tick()` logic - just replace Location with BlockCoordinate, delegate VFX
- Movement interpolation math - pure algorithm
- `getItem()`, `getDestinationInventory()`, `getWaypoints()` - accessors
- `getProgress()`, `getPathLength()` - state queries

### Complexity Level: **MEDIUM**

**Factors:**
- ✅ Core movement algorithm is pure math
- ✅ Waypoint interpolation is coordinate-agnostic
- ❌ Entity spawning is deeply Bukkit-specific
- ❌ Chunk checks need abstraction
- ✅ Can cleanly separate logic from VFX

---

## 5. ITEMPACKETMANAGER.JAVA

### File Overview
- **Lines:** 156
- **Primary Role:** Packet lifecycle management, delivery coordination
- **Key Responsibilities:** Packet ticking, delivery dispatch, inventory routing

### Bukkit Imports Analysis

| Import | Type | Status | Platform-Agnostic? |
|--------|------|--------|-------------------|
| `org.bukkit.Location` | Class | CRITICAL | ❌ No (inventory location) |
| `org.bukkit.block.Block` | Class | HIGH | ⚠️ Partial (inventory check) |
| `org.bukkit.util.Vector` | Class | HIGH | ✅ Yes (directional vectors) |

### Key Methods Needing Refactor

#### 1. **Delivery to Specific Inventory**
```java
private void deliver(ItemPacket p, boolean shouldLog) {
    Location destInvLoc = p.getDestinationInventory();
    
    if (destInvLoc != null && destInvLoc.getWorld() != null) {
        if (!destInvLoc.getWorld().isChunkLoaded(...)) { ... }
        
        var holder = InventoryUtil.getInventory(destInvLoc.getBlock());
        if (holder != null) {
            var leftovers = holder.getInventory().addItem(p.getItem());
            ...
        }
    }
}
```
**Issue:** Direct inventory manipulation, chunk checks  
**Refactor Strategy:**
- Create `IInventoryManager` interface:
  ```java
  interface IInventoryManager {
      InventoryResult tryDeliverItem(ItemData item, BlockCoordinate loc);
      boolean isInventory(BlockCoordinate loc);
      void dropItemNaturally(ItemData item, BlockCoordinate loc);
  }
  
  class InventoryResult {
      int deliveredAmount;
      List<ItemData> leftovers;
  }
  ```
- Inject into manager
- Call interface methods instead of direct Bukkit access

#### 2. **Fallback Delivery - Adjacent Inventory Search**
```java
for (Vector v : DIRS) {
    Location adj = loc.clone().add(v);
    Block block = adj.getBlock();
    var inv = InventoryUtil.getInventory(block);
    if (inv != null) {
        inv.getInventory().addItem(p.getItem());
        return;
    }
}
```
**Issue:** Block iteration + inventory detection  
**Refactor Strategy:**
- Same `IInventoryManager` handles this:
  ```java
  interface IInventoryManager {
      BlockCoordinate findAdjacentInventory(BlockCoordinate startLoc);
      InventoryResult tryDeliverItem(ItemData item, BlockCoordinate loc);
  }
  ```

#### 3. **Item Drop Fallback**
```java
loc.getWorld().dropItemNaturally(
    loc.clone().add(0.5, 1, 0.5),
    p.getItem()
);
```
**Issue:** World-specific drop operation  
**Refactor Strategy:**
- Include in `IInventoryManager.dropItemNaturally()`
- Handles world access internally

### Platform-Specific Operations to Externalize

| Operation | Code | Abstraction | Solution |
|-----------|------|-----------|----------|
| Get inventory holder | `InventoryUtil.getInventory()` | `IInventoryManager` | BukkitInventoryManager |
| Add item to inventory | `inventory.addItem()` | `IInventoryManager` | BukkitInventoryManager |
| Drop item | `world.dropItemNaturally()` | `IInventoryManager` | BukkitInventoryManager |
| Check chunk loaded | `world.isChunkLoaded()` | `IChunkProvider` | BukkitChunkProvider |
| Adjacency iteration | `loc.clone().add(Vector)` | `BlockCoordinate.getAdjacent()` | Common class |

### Key Methods That Can Stay As-Is (With Refactored Parameters)
- `add()` - just packet listing
- `tick()` - iteration logic (pure)
- Callback invocation logic - just change signature to use BlockCoordinate/ItemData
- `all()` (if exposed) - collection accessor

### Refactored Deliver Method Structure
```java
// In common layer
private void deliver(ItemPacket p, boolean shouldLog) {
    BlockCoordinate destInvLoc = p.getDestinationInventory();
    
    if (destInvLoc != null) {
        InventoryResult result = inventoryManager.tryDeliverItem(p.getItem(), destInvLoc);
        if (result.deliveredAmount > 0) {
            p.getOnDeliveryCallback().accept(destInvLoc, result.deliveredAmount);
            return;
        }
    }
    
    // Fallback to adjacent inventory
    BlockCoordinate lastWaypoint = p.getLastWaypoint();
    BlockCoordinate adjacent = inventoryManager.findAdjacentInventory(lastWaypoint);
    if (adjacent != null) {
        inventoryManager.tryDeliverItem(p.getItem(), adjacent);
        return;
    }
    
    // Final fallback: drop
    inventoryManager.dropItemNaturally(p.getItem(), lastWaypoint);
}
```

### Complexity Level: **LOW**

**Factors:**
- ✅ Core logic is simple iteration
- ✅ Delivery coordination is pure state machine
- ⚠️ Needs `IInventoryManager` abstraction
- ✅ Clean separation possible

---

## CROSS-FILE DEPENDENCY ANALYSIS

### Critical Shared Types Needing Abstraction

1. **`BlockCoordinate` (NEW CLASS)**
   - Immutable x, y, z, worldId
   - Replaces `Location` throughout
   - Methods: `add(Vector)`, `clone()`, `centerOffset()`, `getAdjacent(directions)`
   - **Location:** cloudframe-common

2. **`ItemData` (NEW CLASS)**
   - Platform-agnostic item representation
   - Fields: itemType, amount, enchantments, customName, lore, nbt
   - **Location:** cloudframe-common

3. **`IBlockAccessor` (NEW INTERFACE)**
   - Block inspection and modification
   - Used by: `Quarry`, `TubeNetworkManager`
   - **Location:** cloudframe-common

4. **`IInventoryManager` (NEW INTERFACE)**
   - Inventory discovery and item delivery
   - Used by: `Quarry`, `ItemPacketManager`
   - **Location:** cloudframe-common

5. **`IWorldProvider` (NEW INTERFACE)**
   - World existence validation
   - Used by: `Quarry`, `QuarryManager`, `TubeNetworkManager`
   - **Location:** cloudframe-common

6. **`IChunkProvider` (NEW INTERFACE)**
   - Chunk load status
   - Used by: `Quarry`, `ItemPacket`, `ItemPacketManager`
   - **Location:** cloudframe-common

7. **`IPacketVisualsFactory` (NEW INTERFACE)**
   - Item packet entity creation
   - Used by: `ItemPacket`
   - **Location:** cloudframe-common

8. **`IVisualsProvider` (NEW INTERFACE)**
   - Block break FX, controller visuals
   - Used by: `Quarry`, `QuarryManager`, `TubeNetworkManager`
   - **Location:** cloudframe-common

### Dependency Graph
```
Quarry
├── Depends on: IBlockAccessor, IWorldProvider, IChunkProvider, IVisualsProvider
├── Uses: QuarryManager (Registry), TubeNetworkManager (Registry)
└── Can move to: cloudframe-common

QuarryManager
├── Depends on: IWorldProvider, IVisualsProvider, IWorldProvider
├── Uses: Quarry (composition), TubeNetworkManager (Registry)
└── Can move to: cloudframe-common

TubeNetworkManager
├── Depends on: IBlockAccessor, IWorldProvider, IVisualsProvider
├── Uses: QuarryManager (Registry via adapter)
└── Can move to: cloudframe-common

ItemPacket
├── Depends on: IPacketVisualsFactory, IChunkProvider
└── Can move to: cloudframe-common

ItemPacketManager
├── Depends on: IInventoryManager, IChunkProvider
├── Uses: ItemPacket (composition)
└── Can move to: cloudframe-common
```

---

## IMPLEMENTATION STRATEGY

### Phase 1: Create Common Abstractions (cloudframe-common)

**Step 1A: Create Data Classes**
```
cloudframe-common/src/main/java/dev/cloudframe/common/
├── BlockCoordinate.java          (x, y, z, worldId, immutable)
├── ItemData.java                 (item representation)
├── QuarryRegion.java             (coordinate-based region)
└── InventoryResult.java          (delivery result)
```

**Step 1B: Create Platform Interfaces**
```
cloudframe-common/src/main/java/dev/cloudframe/common/platform/
├── IBlockAccessor.java           (block inspection/manipulation)
├── IInventoryManager.java        (inventory operations)
├── IWorldProvider.java           (world validation)
├── IChunkProvider.java           (chunk load status)
├── IPacketVisualsFactory.java    (packet entity creation)
├── IVisualsProvider.java         (FX rendering)
└── IPlatformContext.java         (DI container)
```

### Phase 2: Refactor Core Logic (cloudframe-common)

**Step 2A: Move & Refactor to Common**
- `Quarry.java` → cloudframe-common (with platform interface injection)
- `QuarryManager.java` → cloudframe-common (with DI)
- `TubeNetworkManager.java` → cloudframe-common (with DI)
- `ItemPacket.java` → cloudframe-common (split logic from VFX)
- `ItemPacketManager.java` → cloudframe-common (with DI)
- `TubeNode.java` → cloudframe-common (store BlockCoordinate)
- `Region.java` → cloudframe-common (or replace with BlockCoordinate-based)

**Step 2B: Update Method Signatures**
- Replace all `Location` with `BlockCoordinate`
- Replace all `ItemStack` with `ItemData`
- Replace all `Block` access with `IBlockAccessor` calls
- Replace all `World` access with `IWorldProvider`/`IChunkProvider` calls

### Phase 3: Implement Adapters (cloudframe-bukkit)

**Step 3A: Create Bukkit Implementations**
```
cloudframe-bukkit/src/main/java/dev/cloudframe/bukkit/platform/
├── BukkitBlockAccessor.java
├── BukkitInventoryManager.java
├── BukkitWorldProvider.java
├── BukkitChunkProvider.java
├── BukkitPacketVisualsFactory.java
├── BukkitVisualsProvider.java
└── BukkitPlatformContext.java      (DI container)
```

**Step 3B: Wrapper Classes for Bukkit Integration**
```
cloudframe-bukkit/src/main/java/dev/cloudframe/bukkit/
├── QuarryBukkitWrapper.java        (Bukkit-specific event handlers)
├── ItemPacketBukkitWrapper.java    (VFX updates)
└── ControllerVisualManager.java    (stays here, injected into Manager)
```

### Phase 4: Update Listeners & Commands (cloudframe-bukkit)

**Step 4A: Adapter Pattern in Event Handlers**
- Convert Bukkit events → `BlockCoordinate` + `ItemData`
- Call common layer methods
- Convert results back to Bukkit types for display

**Step 4B: Dependency Injection Setup**
```java
// In CloudFrame.onEnable()
IPlatformContext context = new BukkitPlatformContext();
context.setBlockAccessor(new BukkitBlockAccessor());
context.setInventoryManager(new BukkitInventoryManager());
// ... etc

Quarry.setPlatformContext(context);
QuarryManager.setPlatformContext(context);
// ... etc
```

---

## DETAILED MIGRATION INSTRUCTIONS

### For Each File

#### QUARRY.JAVA
1. **Create new `Quarry.java` in cloudframe-common**
2. **Change constructor:**
   ```java
   // BEFORE
   public Quarry(UUID owner, Location posA, Location posB, Region region, 
                 Location controller, int controllerYaw)
   
   // AFTER
   public Quarry(UUID owner, BlockCoordinate posA, BlockCoordinate posB, 
                 QuarryRegion region, BlockCoordinate controller, int controllerYaw,
                 IBlockAccessor blockAccessor, IChunkProvider chunkProvider,
                 IWorldProvider worldProvider, IVisualsProvider visualsProvider)
   ```

3. **Replace all Location operations:**
   ```java
   // BEFORE
   Material typeAtPos = world.getBlockAt(currentX, currentY, currentZ).getType();
   
   // AFTER
   BlockType typeAtPos = blockAccessor.getBlockType(currentX, currentY, currentZ, 
                                                      region.getWorldId());
   ```

4. **Extract VFX to interface:**
   ```java
   // BEFORE
   sendBlockCrack(block.getLocation(), mineProgress);
   
   // AFTER
   visualsProvider.sendBlockCrack(
       new BlockCoordinate(currentX, currentY, currentZ, region.getWorldId()),
       mineProgress
   );
   ```

5. **Update output routing:**
   ```java
   // BEFORE
   List<Location> inventories = CloudFrameRegistry.tubes().findInventoriesNear(startTube);
   
   // AFTER
   List<BlockCoordinate> inventories = tubeManager.findInventoriesNear(startTube);
   ```

#### QUARRYMANAGER.JAVA
1. **Create new `QuarryManager.java` in cloudframe-common**
2. **Replace Location maps with BlockCoordinate:**
   ```java
   // BEFORE
   private final Map<Location, UnregisteredControllerData> unregisteredControllers = new HashMap<>();
   
   // AFTER
   private final Map<BlockCoordinate, UnregisteredControllerData> unregisteredControllers = new HashMap<>();
   ```

3. **Inject platform provider:**
   ```java
   public QuarryManager(IWorldProvider worldProvider, IVisualsProvider visualsProvider)
   ```

4. **Update all Location parameters:**
   ```java
   // BEFORE
   public void register(Quarry q)
   
   // AFTER - no change needed, takes Quarry object
   // But update internal calls:
   public void register(Quarry q) {
       quarries.add(q);
       unmarkUnregisteredController(q.getController()); // now BlockCoordinate
   ```

5. **Update getByController():**
   ```java
   public Quarry getByController(BlockCoordinate controllerLoc)
   ```

6. **Update DB queries to use BlockCoordinate:**
   ```java
   // BEFORE
   ps.setString(1, q.getOwner().toString());
   ps.setString(2, q.getWorld().getName());
   ps.setInt(3, q.getPosA().getBlockX());
   
   // AFTER
   ps.setString(1, q.getOwner().toString());
   ps.setString(2, q.getRegion().getWorldId());
   ps.setInt(3, q.getPosA().getX());
   ```

#### TUBENETWORKMANAGER.JAVA
1. **Create new `TubenetworkManager.java` in cloudframe-common**
2. **Update TubeNode storage:**
   ```java
   // Before storing in TubeNode
   private final BlockCoordinate location;
   private final String worldId;
   
   public TubeNode(BlockCoordinate location)
   public BlockCoordinate getCoordinate()
   public String getWorldId()
   ```

3. **Change tube map key:**
   ```java
   // BEFORE
   private final Map<Location, TubeNode> tubes = new HashMap<>();
   
   // AFTER
   private final Map<BlockCoordinate, TubeNode> tubes = new HashMap<>();
   ```

4. **Update inventory discovery:**
   ```java
   // BEFORE
   public List<Location> findInventoriesNear(TubeNode start)
   
   // AFTER
   public List<BlockCoordinate> findInventoriesNear(TubeNode start)
   // And inject:
   private final IBlockAccessor blockAccessor;
   ```

5. **Update chunk-based methods:**
   ```java
   // BEFORE
   public Collection<Location> tubeLocationsInChunk(org.bukkit.Chunk chunk)
   
   // AFTER
   public Collection<BlockCoordinate> tubeLocationsInChunk(String worldId, int chunkX, int chunkZ)
   ```

#### ITEMPACKET.JAVA
1. **Create new `ItemPacket.java` in cloudframe-common**
2. **Refactor constructor:**
   ```java
   // BEFORE
   public ItemPacket(ItemStack item, List<Location> waypoints, 
                     Location destinationInventory, 
                     BiConsumer<Location, Integer> onDeliveryCallback)
   
   // AFTER
   public ItemPacket(ItemData item, List<BlockCoordinate> waypoints, 
                     BlockCoordinate destinationInventory,
                     BiConsumer<BlockCoordinate, Integer> onDeliveryCallback,
                     IPacketVisualsFactory visualsFactory,
                     IChunkProvider chunkProvider)
   ```

3. **Extract entity spawning:**
   ```java
   // BEFORE
   private void spawnEntity()
   
   // AFTER - Replace with:
   private IPacketVisual visual;
   
   public ItemPacket(...) {
       // ... init ...
       this.visual = visualsFactory.createItemDisplay(item, waypoints.get(0));
   }
   ```

4. **Update tick() to use coordinates:**
   ```java
   // BEFORE
   Location loc = entity.getLocation();
   if (!loc.getWorld().isChunkLoaded(...))
   
   // AFTER
   BlockCoordinate loc = visual.getCoordinate();
   if (!chunkProvider.isChunkLoaded(loc.getWorldId(), loc.getX() >> 4, loc.getZ() >> 4))
   ```

5. **Update waypoint interpolation:**
   ```java
   // BEFORE
   double x = a.getX() + (b.getX() - a.getX()) * progress;
   Location newLoc = new Location(a.getWorld(), x, y, z);
   entity.teleport(newLoc);
   
   // AFTER
   BlockCoordinate pos = interpolate(a, b, progress);
   visual.teleport(pos);
   ```

#### ITEMPACKETMANAGER.JAVA
1. **Create new `ItemPacketManager.java` in cloudframe-common**
2. **Inject inventory manager:**
   ```java
   public ItemPacketManager(IInventoryManager inventoryManager, IChunkProvider chunkProvider)
   ```

3. **Update deliver() method:**
   ```java
   // BEFORE
   Location destInvLoc = p.getDestinationInventory();
   var holder = InventoryUtil.getInventory(destInvLoc.getBlock());
   holder.getInventory().addItem(p.getItem());
   
   // AFTER
   BlockCoordinate destInvLoc = p.getDestinationInventory();
   InventoryResult result = inventoryManager.tryDeliverItem(p.getItem(), destInvLoc);
   if (result.deliveredAmount > 0) { ... }
   ```

4. **Update adjacency check:**
   ```java
   // BEFORE
   for (Vector v : DIRS) {
       Location adj = loc.clone().add(v);
       if (InventoryUtil.isInventory(block))
   
   // AFTER
   BlockCoordinate adjacent = inventoryManager.findAdjacentInventory(loc);
   if (adjacent != null) { ... }
   ```

---

## SUMMARY: WHAT CAN BE MOVED TO CLOUDFRAME-COMMON

### Can Move As-Is (No Changes Needed)
- ✅ `TubeNode` - just add `BlockCoordinate` support
- ✅ All pathfinding algorithms (BFS, neighbor rebuilding)
- ✅ All counting/iteration logic
- ✅ Movement interpolation math
- ✅ Chunk index structures (ChunkKey)
- ✅ Database initialization & run() helper

### Can Move With Type Substitutions
- ✅ `Quarry.java` - replace Location→BlockCoordinate, ItemStack→ItemData
- ✅ `QuarryManager.java` - replace Location→BlockCoordinate
- ✅ `TubeNetworkManager.java` - replace Location→BlockCoordinate
- ✅ `ItemPacket.java` - replace Location→BlockCoordinate, ItemStack→ItemData
- ✅ `ItemPacketManager.java` - replace Location→BlockCoordinate, ItemStack→ItemData

### Must Stay in cloudframe-bukkit
- ❌ `ControllerVisualManager` - Bukkit entity handling
- ❌ `TubeVisualManager` - Bukkit entity handling
- ❌ All event listeners - Bukkit-specific
- ❌ All commands - Bukkit-specific

### New Classes to Create in cloudframe-common
- **Data Classes:**
  - `BlockCoordinate` (immutable, (x, y, z, worldId))
  - `ItemData` (platform-agnostic item)
  - `QuarryRegion` (coordinate-based)
  - `InventoryResult` (delivery metadata)

- **Platform Interfaces:**
  - `IBlockAccessor` (block inspection/modification)
  - `IInventoryManager` (inventory operations)
  - `IWorldProvider` (world validation)
  - `IChunkProvider` (chunk loading)
  - `IPacketVisualsFactory` (packet entities)
  - `IVisualsProvider` (FX rendering)
  - `IPlatformContext` (DI container)

### New Classes to Create in cloudframe-bukkit
- **Implementations:**
  - `BukkitBlockAccessor` implements `IBlockAccessor`
  - `BukkitInventoryManager` implements `IInventoryManager`
  - `BukkitWorldProvider` implements `IWorldProvider`
  - `BukkitChunkProvider` implements `IChunkProvider`
  - `BukkitPacketVisualsFactory` implements `IPacketVisualsFactory`
  - `BukkitVisualsProvider` implements `IVisualsProvider`
  - `BukkitPlatformContext` implements `IPlatformContext`

- **Wrappers/Integration:**
  - `QuarryBukkitWrapper` - Bukkit event handler wrapper
  - `ItemPacketBukkitWrapper` - VFX coordination wrapper

---

## RISKS & MITIGATION

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Breaking Location-dependent code | HIGH | Phase migration: adapt one class at a time, keep Bukkit wrapper bridges |
| Database schema compatibility | MEDIUM | Run migration script on load, validate BlockCoordinate → Location → DB serialization |
| Performance regression from interface calls | LOW | Interfaces are single-method dispatches; negligible overhead |
| Fabric/other platform missing world UUIDs | MEDIUM | Pre-populate platform context with available worlds on startup |
| Missing ItemStack NBT data in ItemData | MEDIUM | Add flexible Map<String, Object> for custom NBT, handle in adapter |
| Chunk loading semantics differ per platform | MEDIUM | IChunkProvider abstraction handles per-platform logic |
| Entity rendering differences | HIGH | Accept platform-specific visual differences; core logic stays pure |

---

## NEXT STEPS

1. **Validate this analysis** - review with team, confirm no missed dependencies
2. **Create Phase 1 classes** - BlockCoordinate, ItemData, all platform interfaces
3. **Write Phase 1 tests** - unit test BlockCoordinate operations, mock adapters
4. **Refactor one class at a time** - start with TubeNetworkManager (fewest dependencies)
5. **Create adapter implementations** - one adapter per interface in cloudframe-bukkit
6. **Migrate event handlers** - convert Bukkit → BlockCoordinate → common layer → result
7. **Test each phase** - local Bukkit server validation before moving next class
8. **Plan Fabric/other platforms** - now that common layer is clean, implement other adapters

