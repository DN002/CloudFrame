package dev.cloudframe.fabric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.cloudframe.common.ids.CloudFrameIds;
import dev.cloudframe.common.util.DebugFile;
import dev.cloudframe.common.util.DebugManager;
import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.markers.InMemoryMarkerSelectionService;
import dev.cloudframe.common.storage.Database;
import dev.cloudframe.common.pipes.ItemPacketManager;
import dev.cloudframe.common.pipes.PipeNetworkManager;
import dev.cloudframe.common.quarry.QuarryManager;
import dev.cloudframe.common.quarry.QuarryPlatform;
import dev.cloudframe.common.power.cables.InMemoryCableConnectionService;
import dev.cloudframe.common.pipes.connections.InMemoryPipeConnectionService;
import dev.cloudframe.common.pipes.connections.PipeConnectionService;
import dev.cloudframe.fabric.commands.CloudFrameCommands;
import dev.cloudframe.fabric.quarry.FabricQuarryPlatform;
import dev.cloudframe.fabric.content.CloudFrameContent;
import dev.cloudframe.fabric.markers.FabricMarkerManager;
import dev.cloudframe.fabric.pipes.*;
import dev.cloudframe.fabric.pipes.filter.PipeFilterItem;
import dev.cloudframe.fabric.util.ClickSideUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CloudFrame Fabric Mod - Multi-platform version
 * 
 * Initializes managers and platform adapters for the Fabric platform.
 */
public class CloudFrameFabric implements ModInitializer {

    public static final String MOD_ID = CloudFrameIds.MOD_ID;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Manual build stamp to help validate which jar is running when the mod version is unchanged.
    public static final String BUILD_STAMP = "wrench-shift-rotate-2026-01-12-01";
    private static Debug debug;

    private static CloudFrameFabric INSTANCE;

    private PipeNetworkManager pipeManager;
    private ItemPacketManager packetManager;
    private QuarryManager quarryManager;
    private QuarryPlatform quarryPlatform;
    private FabricMarkerManager markerManager;
    private dev.cloudframe.fabric.power.FabricCableConnectionManager cableConnectionManager;
    private dev.cloudframe.fabric.pipes.FabricPipeFilterManager pipeFilterManager;
    private PipeConnectionService pipeConnectionService;
    private MinecraftServer server;
    private final AtomicBoolean commandsRegistered = new AtomicBoolean(false);
    private int tickCounter = 0;

    private dev.cloudframe.fabric.config.WrenchConfig wrenchConfig;

    public dev.cloudframe.fabric.config.WrenchConfig getWrenchConfig() {
        return wrenchConfig;
    }

    /**
     * Called when the mod initializes.
     * Set up by Fabric's entry point system (see fabric.mod.json).
     */
    @Override
    public void onInitialize() {
        INSTANCE = this;
        // Initialize debug system BEFORE first log
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("cloudframe");
        configDir.toFile().mkdirs();
        DebugFile.init(configDir.toString());
        debug = DebugManager.get(CloudFrameFabric.class);

        debug.log("onInitialize", "Fabric mod initializing...");
        debug.log("onInitialize", "Build stamp: " + BUILD_STAMP);

        debug.log("onInitialize", "CloudFrame Fabric 2.0.0 initializing");
        debug.log("onInitialize", "Debug logging initialized to: " + configDir);

        // Load server-owner configurable settings (wrench rotation + debug flags)
        // from the same CloudFrame folder as debug.log/cloudframe.db.
        try {
            var loaded = dev.cloudframe.fabric.config.CloudFrameConfigFile.loadOrCreate(configDir.resolve("config.txt"), debug);
            wrenchConfig = loaded.wrenchConfig();
            debug.log("onInitialize", "Config loaded from config.txt");
        } catch (Throwable t) {
            wrenchConfig = new dev.cloudframe.fabric.config.WrenchConfig();
            debug.log("onInitialize", "Config load failed: " + t);
        }

        EnvType envType = FabricLoader.getInstance().getEnvironmentType();
        debug.log("onInitialize", "Fabric environment type: " + envType);
        if (envType == EnvType.CLIENT) {
            debug.log("onInitialize", "Note: commands are server-side. If you're on a multiplayer server, the server must have the CloudFrame mod installed for /cloudframe to exist.");
        }

        // Register blocks/items + block entities + screen handlers
        try {
            CloudFrameContent.registerAll();
            debug.log("onInitialize", "Content registered successfully (blocks/items + block entities + screen handlers)");
            debug.log("onInitialize", "Content registered");
            // Log loaded recipes for diagnostics (server side)
            try {
                if (server != null) {
                    var recipeManager = server.getRecipeManager();
                    int cloudframeCount = 0;
                    for (var entry : recipeManager.values()) {
                        var id = entry.id();
                        if (id != null && id.getValue().getNamespace().equals(MOD_ID)) {
                            cloudframeCount++;
                        }
                    }
                    debug.log("onInitialize", "Loaded CloudFrame recipes: " + cloudframeCount);
                } else {
                    debug.log("onInitialize", "Server not initialized, cannot enumerate recipes yet.");
                }
            } catch (Throwable t) {
                debug.log("onInitialize", "Could not enumerate recipes: " + t);
            }
        } catch (Exception ex) {
            debug.log("onInitialize", "FATAL: Failed to register content: " + ex.getMessage());
            LOGGER.error("[CloudFrame] Failed to register content!", ex);
            ex.printStackTrace();
            return;
        }

        // Commands - try CommandRegistrationCallback
        debug.log("commands", "Registering CommandRegistrationCallback");
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            if (commandsRegistered.get()) return;
            debug.log("commands", "CommandRegistrationCallback fired! environment=" + environment);
            try {
                CloudFrameCommands.register(dispatcher);
                if (commandsRegistered.compareAndSet(false, true)) {
                    debug.log("commands", "Commands registered successfully via CommandRegistrationCallback");
                }
            } catch (Exception ex) {
                LOGGER.error("[CloudFrame] Exception registering commands in callback: {}", ex.getMessage());
                debug.log("commands", "Exception in callback: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        // Initialize SQLite database
        try {
            Database.init(configDir.resolve("cloudframe.db").toString());
            debug.log("onInitialize", "SQLite database initialized");
            debug.log("onInitialize", "SQLite initialized");
        } catch (SQLException e) {
            debug.log("onInitialize", "FATAL: Failed to initialize SQLite: " + e.getMessage());
            LOGGER.error("[CloudFrame] Failed to initialize SQLite!", e);
            return;
        }

        markerManager = new FabricMarkerManager(new InMemoryMarkerSelectionService());

        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        // Ensure recipes are visible in the recipe book by unlocking them for players on join.
        // (Advancement-based unlocks are brittle and can silently fail if any JSON is invalid.)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            try {
                unlockCloudFrameRecipesForPlayer(handler.player, srv);
            } catch (Throwable t) {
                if (debug != null) debug.log("recipes", "Failed to unlock recipes on join: " + t);
            }
        });

        // Register tick event for packet/quarry updates
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        // Active scanning hook: track player block placements without mixins.
        dev.cloudframe.fabric.quarry.PlayerPlacementDirtyHook.register();

        // Power probe (wrench look tooltip): register server networking.
        dev.cloudframe.fabric.power.PowerProbeServer.register();

        // Left-click removal for pipe filters (prevents breaking the pipe).
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

            if (CloudFrameContent.getCloudPipeBlock() == null) return ActionResult.PASS;
            if (!world.getBlockState(pos).isOf(CloudFrameContent.getCloudPipeBlock())) return ActionResult.PASS;

            CloudFrameFabric instance = CloudFrameFabric.instance();
            if (instance == null || instance.getPipeFilterManager() == null) return ActionResult.PASS;

            Direction side = ClickSideUtil.getClickedArmSide(serverPlayer, pos, direction);
            int sideIndex = ClickSideUtil.toDirIndex(side);
            GlobalPos pipePos = GlobalPos.create(world.getRegistryKey(), pos.toImmutable());

            if (!instance.getPipeFilterManager().hasFilter(pipePos, sideIndex)) return ActionResult.PASS;

            var st = instance.getPipeFilterManager().get(pipePos, sideIndex);
            instance.getPipeFilterManager().removeFilter(pipePos, sideIndex);

            ItemStack drop = new ItemStack(CloudFrameContent.getPipeFilter(), 1);
            PipeFilterItem.writeItemConfigFromFilterState(drop, st);
            serverPlayer.getInventory().insertStack(drop);
            if (!drop.isEmpty() && world instanceof ServerWorld sw) {
                ItemScatterer.spawn(sw, serverPlayer.getX(), serverPlayer.getY() + 0.5, serverPlayer.getZ(), drop);
            }

            dev.cloudframe.fabric.content.TubeBlock.refreshConnections(world, pos);
            serverPlayer.sendMessage(Text.literal("§7Removed pipe filter."), true);
            return ActionResult.SUCCESS;
        });

        // Shift-right-click in air configures the held pipe filter item.
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!sp.isSneaking()) return ActionResult.PASS;

            ItemStack stack = sp.getStackInHand(hand);
            if (stack == null || stack.isEmpty()) return ActionResult.PASS;
            if (CloudFrameContent.getPipeFilter() == null || !stack.isOf(CloudFrameContent.getPipeFilter())) return ActionResult.PASS;

            if (debug != null) {
                debug.log("pipeFilter", "UseItemCallback: opening item config GUI (player=" + sp.getName().getString() + ")");
            }

            sp.openHandledScreen(new net.minecraft.screen.NamedScreenHandlerFactory() {
                @Override
                public Text getDisplayName() {
                    return Text.literal("Pipe Filter");
                }

                @Override
                public net.minecraft.screen.ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory inv, net.minecraft.entity.player.PlayerEntity p) {
                    return new dev.cloudframe.fabric.pipes.filter.PipeFilterScreenHandler(syncId, inv, stack);
                }
            });

            return ActionResult.SUCCESS;
        });
        
        // Register glass frame protection listener
        dev.cloudframe.fabric.listeners.GlassFrameProtectionListener.register();
        debug.log("onInitialize", "Glass frame protection listener registered");
        
        // Register quarry controller break listener
        dev.cloudframe.fabric.listeners.QuarryControllerBreakListener.register();
        debug.log("onInitialize", "Quarry controller break listener registered");
        
        // Register wrench marker activation listener (scans for placed marker blocks)
        dev.cloudframe.fabric.listeners.FabricWrenchMarkerActivationListener.register();
        debug.log("onInitialize", "Wrench marker activation listener registered");

        // Wrench shift-right-click rotation interception (so it works on blocks with GUIs).
        dev.cloudframe.fabric.listeners.FabricWrenchRotateIoListener.register();
        debug.log("onInitialize", "Wrench shift-rotate listener registered");

        // Deactivate frames when marker blocks are broken
        dev.cloudframe.fabric.listeners.MarkerBlockBreakListener.register();
        debug.log("onInitialize", "Marker block break listener registered");

        debug.log("onInitialize", "Lifecycle events registered");
        debug.log("onInitialize", "Fabric mod ready.");
    }

    private void onServerStarted(MinecraftServer server) {
        this.server = server;
        debug.log("onServerStarted", "===== SERVER STARTED CALLBACK FIRED =====");
        debug.log("onServerStarted", "Server started, initializing managers...");

        // Recipe diagnostics + warm-up: confirm recipes are present on the server.
        try {
            int cloudframeCount = countCloudFrameRecipes(server);
            debug.log("recipes", "Server recipe manager contains CloudFrame recipes: " + cloudframeCount);
        } catch (Throwable t) {
            debug.log("recipes", "Could not count recipes on server start: " + t);
        }

        // Fallback: if CommandRegistrationCallback didn't register, do it now
        if (!commandsRegistered.get()) {
            debug.log("commands", "CommandRegistrationCallback did not register commands, attempting fallback registration");
            try {
                CloudFrameCommands.register(server.getCommandManager().getDispatcher());
                commandsRegistered.set(true);
                boolean present = server.getCommandManager().getDispatcher().getRoot().getChild("cloudframe") != null;
                debug.log("commands", "Commands registered via fallback. Command node present: " + present);
            } catch (Exception ex) {
                LOGGER.error("[CloudFrame] FATAL: Failed to register commands in fallback: {}", ex.getMessage());
                debug.log("commands", "FATAL: Failed to register commands in fallback: " + ex.getMessage());
                ex.printStackTrace();
            }
        } else {
            debug.log("commands", "Commands already registered via callback");
        }

        pipeManager = new PipeNetworkManager(new FabricPipeLocationAdapter(server));
        // Fabric uses real blocks for pipes, so we don't need entity-only pipe visuals/hitboxes.
        debug.log("onServerStarted", "PipeNetworkManager initialized");

        cableConnectionManager = new dev.cloudframe.fabric.power.FabricCableConnectionManager(server, new InMemoryCableConnectionService());
        cableConnectionManager.loadAll();
        debug.log("onServerStarted", "CableConnectionManager initialized");

        pipeFilterManager = new dev.cloudframe.fabric.pipes.FabricPipeFilterManager(server);
        pipeFilterManager.loadAll();
        debug.log("onServerStarted", "PipeFilterManager initialized");

        packetManager = new ItemPacketManager(new FabricItemDeliveryProvider(server));
        debug.log("onServerStarted", "ItemPacketManager initialized");

        quarryPlatform = new FabricQuarryPlatform(server, pipeManager, packetManager);
        quarryManager = new QuarryManager(quarryPlatform);
        debug.log("onServerStarted", "QuarryManager initialized");

        pipeManager.loadAll();
        debug.log("onServerStarted", "Pipes loaded from database");

        pipeConnectionService = new InMemoryPipeConnectionService();
        pipeConnectionService.loadAll();
        debug.log("onServerStarted", "PipeConnectionService initialized");
        
        quarryManager.loadAll();
        debug.log("onServerStarted", "Quarries loaded from database");

        // Migration: older saves stored quarry posA/posB at marker Y only, which collapses the
        // mining region to a single layer after restart. Expand such regions to bottomY..topY.
        try {
            migrateQuarryVerticalRegionsIfNeeded(server);
        } catch (Exception ex) {
            debug.log("onServerStarted", "Exception migrating quarry regions: " + ex.getMessage());
            ex.printStackTrace();
        }

        if (markerManager != null) {
            markerManager.loadAll();
        }

        debug.log("onServerStarted", "Managers initialized and data loaded.");
    }

    private static int countCloudFrameRecipes(MinecraftServer server) {
        if (server == null) return 0;
        int cloudframeCount = 0;
        for (var entry : server.getRecipeManager().values()) {
            var id = entry.id();
            if (id != null && MOD_ID.equals(id.getValue().getNamespace())) {
                cloudframeCount++;
            }
        }
        return cloudframeCount;
    }

    private static Collection<RecipeEntry<?>> getCloudFrameRecipeEntries(MinecraftServer server) {
        ArrayList<RecipeEntry<?>> out = new ArrayList<>();
        if (server == null) return out;
        for (var entry : server.getRecipeManager().values()) {
            var id = entry.id();
            if (id != null && MOD_ID.equals(id.getValue().getNamespace())) {
                out.add(entry);
            }
        }
        return out;
    }

    private static void unlockCloudFrameRecipesForPlayer(ServerPlayerEntity player, MinecraftServer server) {
        if (player == null || server == null) return;

        Collection<RecipeEntry<?>> recipes = getCloudFrameRecipeEntries(server);
        if (recipes.isEmpty()) return;

        // Method signatures differ across MC versions; use reflection to stay resilient.
        try {
            // 1.21+ commonly: unlockRecipes(Collection<RecipeEntry<?>>)
            var m = ServerPlayerEntity.class.getMethod("unlockRecipes", Collection.class);
            m.invoke(player, recipes);
            return;
        } catch (NoSuchMethodException ignored) {
            // fall through
        } catch (Throwable t) {
            // fall through to alternate signature
        }

        try {
            RecipeEntry<?>[] arr = recipes.toArray(new RecipeEntry[0]);
            var m = ServerPlayerEntity.class.getMethod("unlockRecipes", RecipeEntry[].class);
            m.invoke(player, (Object) arr);
        } catch (Throwable ignored) {
            // If this fails too, recipes just won't show in the recipe book.
        }
    }

    private void migrateQuarryVerticalRegionsIfNeeded(MinecraftServer server) {
        if (quarryManager == null || quarryPlatform == null || server == null) return;
        boolean changed = false;

        java.util.List<dev.cloudframe.common.quarry.Quarry> list = quarryManager.all();
        for (int i = 0; i < list.size(); i++) {
            dev.cloudframe.common.quarry.Quarry q = list.get(i);
            if (q == null) continue;
            dev.cloudframe.common.util.Region r = q.getRegion();
            if (r == null) continue;

            Object worldObj = r.getWorld();
            net.minecraft.server.world.ServerWorld sw = null;
            try {
                if (worldObj instanceof net.minecraft.server.world.ServerWorld sww) {
                    sw = sww;
                } else if (worldObj instanceof net.minecraft.registry.RegistryKey<?> k) {
                    @SuppressWarnings("unchecked")
                    net.minecraft.registry.RegistryKey<net.minecraft.world.World> wk = (net.minecraft.registry.RegistryKey<net.minecraft.world.World>) k;
                    sw = server.getWorld(wk);
                }
            } catch (Throwable ignored) {
                sw = null;
            }
            if (sw == null) continue;

            int bottomY = sw.getBottomY();

            // If region is a single Y slice, it was persisted incorrectly.
            if (r.minY() != r.maxY()) continue;

            int topY = r.maxY();
            if (bottomY >= topY) continue;

            dev.cloudframe.common.util.Region region = new dev.cloudframe.common.util.Region(
                worldObj,
                r.minX(), bottomY, r.minZ(),
                worldObj,
                r.maxX(), topY, r.maxZ()
            );

            Object a = quarryPlatform.createLocation(worldObj, r.minX(), bottomY, r.minZ());
            Object b = quarryPlatform.createLocation(worldObj, r.maxX(), topY, r.maxZ());
            Object controller = q.getController();

            dev.cloudframe.common.quarry.Quarry migrated = new dev.cloudframe.common.quarry.Quarry(
                q.getOwner(), q.getOwnerName(), a, b, region, controller, q.getControllerYaw(), quarryPlatform
            );

            // Preserve settings.
            migrated.setSilkTouchAugment(q.hasSilkTouchAugment());
            migrated.setSpeedAugmentLevel(q.getSpeedAugmentLevel());
            migrated.setFortuneAugmentLevel(q.getFortuneAugmentLevel());
            migrated.setOutputRoundRobin(q.isOutputRoundRobin());

            // Preserve visual frame bounds if present.
            migrated.setFrameBounds(q.frameMinX(), q.frameMinZ(), q.frameMaxX(), q.frameMaxZ());

            // Never auto-start on load.
            migrated.setActive(false);

            list.set(i, migrated);
            changed = true;
            debug.log("migrate", "Expanded quarry region for controller=" + controller + " to Y=" + bottomY + ".." + topY);
        }

        if (changed) {
            // Migration complete; all quarries already persisted via targeted saveQuarry().
            debug.log("migrate", "Quarry region migration complete; database updated.");
        }
    }

    private void onServerStopping(MinecraftServer server) {
        debug.log("onServerStopping", "Server stopping, saving data...");

        // All persistence is now write-through (not batched at shutdown):
        // - Quarries: persisted via saveQuarry() on every GUI toggle
        // - Markers: persisted via upsert/delete on every corner add/clear
        // - Pipes: loaded once on startup and not mutated during gameplay
        // No saveAll() calls needed.

        Database.close();
        debug.log("onServerStopping", "Database closed");
    }

    private void onServerTick(MinecraftServer server) {
        tickCounter++;

        // If players place blocks back into already-mined space, enqueue them immediately.
        // This path avoids mixins and should work in hybrid server environments.
        try {
            dev.cloudframe.fabric.quarry.PlayerPlacementDirtyHook.tick();
        } catch (Exception ex) {
            debug.log("onServerTick", "Exception ticking placement dirty hook: " + ex.getMessage());
        }

        // Per-player chunk preview outlines (off by default; toggled via controller GUI).
        try {
            dev.cloudframe.fabric.quarry.controller.ChunkPreviewService.tick(server, tickCounter);
        } catch (Exception ex) {
            debug.log("onServerTick", "Exception ticking chunk previews: " + ex.getMessage());
        }

        // Process queued glass-frame removals incrementally to avoid lag spikes.
        if (quarryPlatform instanceof FabricQuarryPlatform fp) {
            try {
                fp.tickFrameRemovalJobs();
            } catch (Exception ex) {
                debug.log("onServerTick", "Exception ticking frame removal jobs: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        if (markerManager != null) {
            try {
                markerManager.tick(server, tickCounter);
            } catch (Exception ex) {
                debug.log("onServerTick", "Exception ticking marker frames: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        if (packetManager != null) {
            try {
                packetManager.tick(false);
            } catch (Exception ex) {
                debug.log("onServerTick", "Exception ticking packets: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        // Power network tick lifecycle: clear/prepare per-tick caches.
        try {
            dev.cloudframe.fabric.power.FabricPowerNetworkManager.beginTick(server, tickCounter);
        } catch (Exception ex) {
            debug.log("onServerTick", "Exception beginning power tick: " + ex.getMessage());
            ex.printStackTrace();
        }

        if (quarryManager != null) {
            try {
                quarryManager.tickAll(false);
            } catch (Exception ex) {
                debug.log("onServerTick", "Exception ticking quarries: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        // End power tick: store unused generation into batteries.
        try {
            dev.cloudframe.fabric.power.FabricPowerNetworkManager.endTick(server);
        } catch (Exception ex) {
            debug.log("onServerTick", "Exception ending power tick: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Called when the mod shuts down (if implemented).
     */
    public void onShutdown() {
        if (debug != null) {
            debug.log("onShutdown", "CloudFrame Fabric 2.0.0 shutting down");
        }
        debug.log("onShutdown", "Fabric mod disabled.");
        DebugManager.shutdown();
    }

    // Public accessors for managers (for event handlers, commands, etc.)
    public PipeNetworkManager getPipeManager() {
        return pipeManager;
    }

    public ItemPacketManager getPacketManager() {
        return packetManager;
    }

    public QuarryManager getQuarryManager() {
        return quarryManager;
    }

    public QuarryPlatform getQuarryPlatform() {
        return quarryPlatform;
    }

    public FabricMarkerManager getMarkerManager() {
        return markerManager;
    }

    public dev.cloudframe.fabric.power.FabricCableConnectionManager getCableConnectionManager() {
        return cableConnectionManager;
    }

    public dev.cloudframe.fabric.pipes.FabricPipeFilterManager getPipeFilterManager() {
        return pipeFilterManager;
    }

    public PipeConnectionService getPipeConnectionService() {
        return pipeConnectionService;
    }

    public static CloudFrameFabric instance() {
        return INSTANCE;
    }
}

