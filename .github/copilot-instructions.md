<!-- Copilot / AI agent instructions for CloudFrame -->

# CloudFrame — quick AI contributor guide

Purpose: help an AI coding agent become productive in this Bukkit/Paper Minecraft plugin.

- Main entry: `dev.cloudframe.cloudframe.CloudFrame` (see plugin.yml main).
- Build: Maven module at `cloudframe/pom.xml`.

Big picture
- This is a single Bukkit/Paper plugin. The lifecycle is in `CloudFrame.onEnable()` / `onDisable()`.
- Core runtime pieces:
  - `CloudFrameEngine` — global tick engine (runs every server tick via Bukkit scheduler).
  - `CloudFrameRegistry` — global singleton that exposes managers: `QuarryManager`, `TubeNetworkManager`, `ItemPacketManager`, `MarkerManager`.
  - `Database` — single SQLite connection used via `Database.run(conn -> { ... })` for persistence.
- Startup order is important: DebugManager -> Database.init -> new CloudFrameEngine -> CloudFrameRegistry.init(engine) -> loadAll() for tubes/quarries -> register listeners -> start engine. See `CloudFrame.java` for the exact sequence.

Key patterns & conventions (use these when making changes)
- Manager pattern: managers expose `loadAll()`, `saveAll()`, `tickAll()` (when applicable). Persisted managers call these from `onEnable`/`onDisable`.
  - Examples: `QuarryManager` and `TubeNetworkManager` implement save/load and are invoked from `CloudFrame`.
- Persistence: schema defined in `Database.init(...)` (tables: `schema_version`, `quarries`, `tubes`, `markers`). Use `Database.run` for any DB ops.
- Location normalization: tube and region code uses block coordinates (see `TubeNetworkManager.norm()` and `Region`). When storing/reading locations use block X/Y/Z and world name.
- Networking/traversal: tubes use BFS pathfinding across `TubeNode` graphs (see `TubeNetworkManager.findPath`). Prefer reuse of manager APIs.
- Threading: the engine ticks on the main server thread (Bukkit scheduler). Avoid blocking operations on tick paths — DB calls currently run on main thread via `Database.run` so keep queries quick or move to async if adding heavy IO.
- Debug/logging: use `DebugManager.get(Class)` and `DebugFlags` for controlled logging. See `CloudFrameEngine` for tick logging pattern.

Build & run (developer workflows)
- Build jar: run from repo root:

```bash
mvn -f cloudframe/pom.xml clean package
```

- After build, pick the produced jar from `cloudframe/target/` and drop into a Paper/Spigot server `plugins/` folder for manual testing.
- The project depends on Paper API and `org.xerial:sqlite-jdbc` (see `cloudframe/pom.xml`).

Files to inspect for common edits
- Plugin entry & lifecycle: [cloudframe/src/main/java/dev/cloudframe/cloudframe/CloudFrame.java](../cloudframe/src/main/java/dev/cloudframe/cloudframe/CloudFrame.java)
- Engine & registry: [cloudframe/src/main/java/dev/cloudframe/cloudframe/core/CloudFrameEngine.java](../cloudframe/src/main/java/dev/cloudframe/cloudframe/core/CloudFrameEngine.java), [cloudframe/src/main/java/dev/cloudframe/cloudframe/core/CloudFrameRegistry.java](../cloudframe/src/main/java/dev/cloudframe/cloudframe/core/CloudFrameRegistry.java)
- Persistence: [cloudframe/src/main/java/dev/cloudframe/cloudframe/storage/Database.java](../cloudframe/src/main/java/dev/cloudframe/cloudframe/storage/Database.java) and `cloudframe/pom.xml` for JDBC dependency
- Tubes: [cloudframe/src/main/java/dev/cloudframe/cloudframe/tubes/TubeNetworkManager.java](../cloudframe/src/main/java/dev/cloudframe/cloudframe/tubes/TubeNetworkManager.java)
- Quarries: [cloudframe/src/main/java/dev/cloudframe/cloudframe/quarry/QuarryManager.java](../cloudframe/src/main/java/dev/cloudframe/cloudframe/quarry/QuarryManager.java)
- Event listeners & commands live under `listeners/` and `commands/` — register order matters at startup.

What an AI agent should do first (recommended checklist)
1. Read `CloudFrame.onEnable()` to understand initialization order.
2. Inspect target manager (e.g., tubes or quarries) for `loadAll()` / `saveAll()` patterns before adding persistence.
3. Follow `DebugManager` logging style and use `DebugFlags` when adding verbose logs.
4. Avoid long-running sync DB work on tick paths; flag major DB changes as candidates for async migration.

Notes / gotchas
- The code assumes world names used in DB are present; `loadAll()` logs and skips entries when worlds are missing.
- When restoring blocks (e.g., quarry controller), code manipulates blocks on the main thread — ensure block changes are safe and intended.
- There are no automated tests in the repo; rely on a local Paper server for behavioral verification.

If anything above is unclear or you'd like me to expand examples (e.g., common refactors, async DB migration plan, or sample unit-test harness), tell me which area to expand.
