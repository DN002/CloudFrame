package dev.cloudframe.fabric.quarry.controller;

import dev.cloudframe.common.quarry.Quarry;
import dev.cloudframe.common.quarry.augments.QuarryAugments;
import dev.cloudframe.fabric.CloudFrameFabric;
import dev.cloudframe.fabric.power.FabricPowerNetworkManager;
import dev.cloudframe.fabric.content.CloudFrameContent;
import dev.cloudframe.fabric.quarry.FabricQuarryAugmentResolver;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.util.DebugManager;

public class QuarryControllerBlockEntity extends BlockEntity implements NamedScreenHandlerFactory {

    private static final Debug debug = DebugManager.get(QuarryControllerBlockEntity.class);

    // Debug window to trace power behavior around augment changes.
    // Not persisted; counts down server ticks.
    private int powerDebugTicks = 0;

    public boolean isPowerDebugActive() {
        return powerDebugTicks > 0;
    }

    private void triggerPowerDebugWindow(String reason) {
        // 3 seconds of extra logging.
        powerDebugTicks = 60;
        debug.log(
            "powerDebug",
            "start: " + reason + ", dim=" + (world != null ? world.getRegistryKey().getValue() : "?") + ", pos=" + pos
                + ", speed=" + speedLevel + ", stored=" + powerBufferCfe + ", cap=" + getPowerBufferCapacityCfe()
        );
    }

    // Matches common quarry energy model: 480 CFE per block at base speed.
    private static final long ENERGY_PER_BLOCK_CFE = 480L;
    private static final int BUFFER_SECONDS = 30;
    private static final int TICKS_PER_SECOND = 20;
    private static final int BUFFER_TICKS = BUFFER_SECONDS * TICKS_PER_SECOND;

    private boolean silkTouch = false;
    private int speedLevel = 0;
    private int fortuneLevel = 0;

    private ItemStack silkAugmentStack = ItemStack.EMPTY;
    private ItemStack speedAugmentStack = ItemStack.EMPTY;
    private ItemStack fortuneAugmentStack = ItemStack.EMPTY;

    // Owner UUID split across 4 ints (so it can be synced via PropertyDelegate later if needed)
    private int ownerMsh = 0;
    private int ownerMsl = 0;
    private int ownerLsh = 0;
    private int ownerLsl = 0;

    // Friendly owner name for UI (UUIDs are not user-friendly).
    private String ownerName = null;

    private boolean outputRoundRobin = true;

    // Controller-local power buffer (CFE). This acts as a short hold-up capacitor.
    // It is charged from the connected power network while the quarry is paused.
    private long powerBufferCfe = 0L;

    private final Inventory augmentInventory = new Inventory() {
        @Override
        public int size() {
            return 3;
        }

        @Override
        public boolean isEmpty() {
            return silkAugmentStack.isEmpty() && speedAugmentStack.isEmpty() && fortuneAugmentStack.isEmpty();
        }

        @Override
        public ItemStack getStack(int slot) {
            return switch (slot) {
                case 0 -> silkAugmentStack;
                case 1 -> speedAugmentStack;
                case 2 -> fortuneAugmentStack;
                default -> ItemStack.EMPTY;
            };
        }

        @Override
        public ItemStack removeStack(int slot, int amount) {
            // Slots are effectively size-1, so any removal returns the installed augment.
            ItemStack existing = getStack(slot);
            if (existing.isEmpty()) return ItemStack.EMPTY;

            ItemStack out = existing.copy();
            out.setCount(1);

            if (slot == 0) {
                setSilkTouch(false);
            } else if (slot == 1) {
                setSpeedLevel(0);
            } else if (slot == 2) {
                setFortuneLevel(0);
            }

            return out;
        }

        @Override
        public ItemStack removeStack(int slot) {
            return removeStack(slot, 1);
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                if (slot == 0) setSilkTouch(false);
                if (slot == 1) setSpeedLevel(0);
                if (slot == 2) setFortuneLevel(0);
                return;
            }

            QuarryAugments aug = FabricQuarryAugmentResolver.resolve(stack);
            if (aug == null) return;

            if (slot == 0) {
                // Mutual exclusion: silk touch disables fortune.
                if (fortuneLevel > 0) {
                    return;
                }
                if (aug.silkTouch()) {
                    silkAugmentStack = stack.copy();
                    silkAugmentStack.setCount(1);
                    setSilkTouch(true);
                }
            } else if (slot == 1) {
                int tier = aug.speedTier();
                if (tier > 0) {
                    speedAugmentStack = stack.copy();
                    speedAugmentStack.setCount(1);
                    setSpeedLevel(Math.max(1, Math.min(3, tier)));
                }
            } else if (slot == 2) {
                // Mutual exclusion: fortune disables silk touch.
                if (silkTouch) {
                    return;
                }
                int tier = aug.fortuneTier();
                if (tier > 0) {
                    fortuneAugmentStack = stack.copy();
                    fortuneAugmentStack.setCount(1);
                    setFortuneLevel(Math.max(1, Math.min(3, tier)));
                }
            }
        }

        @Override
        public void markDirty() {
            QuarryControllerBlockEntity.this.markDirty();
        }

        @Override
        public boolean canPlayerUse(PlayerEntity player) {
            return true;
        }

        @Override
        public void clear() {
            silkAugmentStack = ItemStack.EMPTY;
            speedAugmentStack = ItemStack.EMPTY;
            fortuneAugmentStack = ItemStack.EMPTY;
            setSilkTouch(false);
            setSpeedLevel(0);
            setFortuneLevel(0);
        }
    };

    public QuarryControllerBlockEntity(BlockPos pos, BlockState state) {
        super(CloudFrameContent.getQuarryControllerBlockEntity(), pos, state);
    }

    private static long requiredCfePerTickForSpeed(int speedTier) {
        int tpb = switch (speedTier) {
            case 1 -> 8;
            case 2 -> 6;
            case 3 -> 5;
            default -> 12;
        };
        if (tpb <= 0) tpb = 1;
        return (ENERGY_PER_BLOCK_CFE + (long) tpb - 1L) / (long) tpb;
    }

    public long getPowerBufferCapacityCfe() {
        long perTick = requiredCfePerTickForSpeed(speedLevel);
        return Math.max(0L, perTick * (long) BUFFER_TICKS);
    }

    public long getPowerBufferStoredCfe() {
        long cap = getPowerBufferCapacityCfe();
        if (cap <= 0L) return 0L;
        return Math.max(0L, Math.min(cap, powerBufferCfe));
    }

    /**
     * Extract from the controller-local power buffer.
     * Returns the actual amount extracted.
     */
    public long extractPowerFromBuffer(long amount) {
        if (amount <= 0L) return 0L;
        long cap = getPowerBufferCapacityCfe();
        if (cap <= 0L) {
            powerBufferCfe = 0L;
            return 0L;
        }

        if (powerBufferCfe > cap) powerBufferCfe = cap;
        long got = Math.min(amount, powerBufferCfe);
        if (got <= 0L) return 0L;

        long before = powerBufferCfe;
        powerBufferCfe -= got;
        markDirty();

        // Log only when we drain to 0 or do a noticeable pull.
        if (isPowerDebugActive() || powerBufferCfe <= 0L || got >= 1000L) {
            String where = (world != null) ? ("dim=" + world.getRegistryKey().getValue() + ", pos=" + pos) : ("pos=" + pos);
            debug.log(
                "extractPowerFromBuffer",
                where + ": req=" + amount + ", got=" + got
                    + ", stored " + before + " -> " + powerBufferCfe
                    + ", cap=" + cap + ", speed=" + speedLevel
            );
        }
        return got;
    }

    /**
     * Insert into the controller-local buffer (clamped to capacity).
     * Returns the actual amount inserted.
     */
    public long insertPowerToBuffer(long amount) {
        if (amount <= 0L) return 0L;
        long cap = getPowerBufferCapacityCfe();
        if (cap <= 0L) {
            powerBufferCfe = 0L;
            return 0L;
        }

        if (powerBufferCfe < 0L) powerBufferCfe = 0L;
        if (powerBufferCfe > cap) powerBufferCfe = cap;

        long missing = cap - powerBufferCfe;
        if (missing <= 0L) return 0L;

        long put = Math.min(amount, missing);
        if (put <= 0L) return 0L;

        long before = powerBufferCfe;
        powerBufferCfe += put;
        markDirty();

        // Log only when we do a noticeable fill.
        if (isPowerDebugActive() || put >= 1000L || (before == 0L && powerBufferCfe > 0L)) {
            String where = (world != null) ? ("dim=" + world.getRegistryKey().getValue() + ", pos=" + pos) : ("pos=" + pos);
            debug.log(
                "insertPowerToBuffer",
                where + ": req=" + amount + ", put=" + put
                    + ", stored " + before + " -> " + powerBufferCfe
                    + ", cap=" + cap + ", speed=" + speedLevel
            );
        }
        return put;
    }

    /**
     * Server tick: charge buffer while paused if a power source exists.
     */
    public static void tick(World world, BlockPos pos, BlockState state, QuarryControllerBlockEntity be) {
        if (world == null || be == null) return;
        if (world.isClient()) return;
        if (!(world instanceof ServerWorld sw)) return;

        MinecraftServer server = sw.getServer();
        if (server == null) return;

        Object controllerLoc = GlobalPos.create(sw.getRegistryKey(), pos.toImmutable());

        CloudFrameFabric inst = CloudFrameFabric.instance();
        Quarry q = (inst != null && inst.getQuarryManager() != null)
            ? inst.getQuarryManager().getByController(controllerLoc)
            : null;
        boolean active = q != null && q.isActive();

        if (be.powerDebugTicks > 0) {
            be.powerDebugTicks--;
        }

        // While active, the quarry tick path consumes power via platform.extractPowerCfe (network first, then buffer).
        // Avoid additionally charging/discharging here to keep behavior predictable.
        if (active) {
            if (be.isPowerDebugActive()) {
                long cap = be.getPowerBufferCapacityCfe();
                long stored = be.getPowerBufferStoredCfe();
                FabricPowerNetworkManager.NetworkInfo info = FabricPowerNetworkManager.measureNetwork(server, controllerLoc);
                debug.log(
                    "tick",
                    "dim=" + sw.getRegistryKey().getValue() + ", pos=" + pos
                        + ": active=1, speed=" + be.speedLevel
                        + ", stored=" + stored + ", cap=" + cap
                        + ", netGen=" + (info != null ? info.producedCfePerTick() : -1)
                        + ", netStored=" + (info != null ? info.storedCfe() : -1)
                );
            }
            return;
        }

        long cap = be.getPowerBufferCapacityCfe();
        if (cap <= 0L) {
            if (be.powerBufferCfe != 0L) {
                be.powerBufferCfe = 0L;
                be.markDirty();
            }
            return;
        }
        if (be.powerBufferCfe > cap) {
            be.powerBufferCfe = cap;
            be.markDirty();
        }

        FabricPowerNetworkManager.NetworkInfo info = FabricPowerNetworkManager.measureNetwork(server, controllerLoc);
        boolean hasSource = info != null && (info.producedCfePerTick() > 0L || info.storedCfe() > 0L);

        if (be.isPowerDebugActive()) {
            debug.log(
                "tick",
                "dim=" + sw.getRegistryKey().getValue() + ", pos=" + pos
                    + ": active=0, speed=" + be.speedLevel
                    + ", stored=" + be.getPowerBufferStoredCfe() + ", cap=" + cap
                    + ", missing=" + Math.max(0L, cap - be.powerBufferCfe)
                    + ", netGen=" + (info != null ? info.producedCfePerTick() : -1)
                    + ", netStored=" + (info != null ? info.storedCfe() : -1)
                    + ", hasSource=" + (hasSource ? 1 : 0)
            );
        }

        if (hasSource) {
            long missing = cap - be.powerBufferCfe;
            if (missing <= 0L) return;

            // Fill the missing buffer from the network (allowed to use stored energy).
            long got = FabricPowerNetworkManager.extractPowerCfe(server, controllerLoc, missing);
            if (got > 0L) {
                long before = be.powerBufferCfe;
                be.powerBufferCfe = Math.min(cap, be.powerBufferCfe + got);
                be.markDirty();

                if (be.isPowerDebugActive()) {
                    debug.log(
                        "tick",
                        "dim=" + sw.getRegistryKey().getValue() + ", pos=" + pos
                            + ": charge got=" + got
                            + ", stored " + before + " -> " + be.powerBufferCfe
                            + ", cap=" + cap + ", speed=" + be.speedLevel
                    );
                }
            }
        }
    }

    public Inventory getAugmentInventory() {
        return augmentInventory;
    }

    public boolean isSilkTouch() {
        return silkTouch;
    }

    public void setSilkTouch(boolean silkTouch) {
        this.silkTouch = silkTouch;
        if (!silkTouch) {
            silkAugmentStack = ItemStack.EMPTY;
        } else if (silkAugmentStack.isEmpty()) {
            silkAugmentStack = dev.cloudframe.fabric.content.AugmentBooks.silkTouch();
        }

        // Mutual exclusion: silk touch disables fortune.
        if (silkTouch) {
            fortuneLevel = 0;
            fortuneAugmentStack = ItemStack.EMPTY;
        }
        markDirty();
    }

    public int getSpeedLevel() {
        return speedLevel;
    }

    public void setSpeedLevel(int speedLevel) {
        int oldLevel = this.speedLevel;
        long oldCap = getPowerBufferCapacityCfe();
        long oldStored = this.powerBufferCfe;

        this.speedLevel = Math.max(0, Math.min(3, speedLevel));
        if (this.speedLevel <= 0) {
            speedAugmentStack = ItemStack.EMPTY;
        } else {
            speedAugmentStack = dev.cloudframe.fabric.content.AugmentBooks.speed(this.speedLevel);
        }

        // Capacity depends on speed tier; clamp stored energy when changing tiers.
        long cap = getPowerBufferCapacityCfe();
        if (cap <= 0L) {
            powerBufferCfe = 0L;
        } else if (powerBufferCfe > cap) {
            powerBufferCfe = cap;
        } else if (powerBufferCfe < 0L) {
            powerBufferCfe = 0L;
        }

        // Debug: track speed changes and any unexpected buffer resets.
        // (Writes to CloudFrame debug.log via DebugFile; no console logging.)
        if (oldLevel != this.speedLevel) {
            String where = (world != null) ? ("dim=" + world.getRegistryKey().getValue() + ", pos=" + pos) : ("pos=" + pos);
            debug.log(
                "setSpeedLevel",
                where + ": speed " + oldLevel + " -> " + this.speedLevel
                    + ", cap " + oldCap + " -> " + cap
                    + ", stored " + oldStored + " -> " + this.powerBufferCfe
            );

            // Enable a short debug window around speed tier changes.
            triggerPowerDebugWindow("speed " + oldLevel + " -> " + this.speedLevel);
        }
        markDirty();
    }

    public int getFortuneLevel() {
        return fortuneLevel;
    }

    public void setFortuneLevel(int fortuneLevel) {
        this.fortuneLevel = Math.max(0, Math.min(3, fortuneLevel));
        if (this.fortuneLevel <= 0) {
            fortuneAugmentStack = ItemStack.EMPTY;
        } else {
            fortuneAugmentStack = dev.cloudframe.fabric.content.AugmentBooks.fortune(this.fortuneLevel);
        }

        // Mutual exclusion: fortune disables silk touch.
        if (this.fortuneLevel > 0) {
            silkTouch = false;
            silkAugmentStack = ItemStack.EMPTY;
        }

        markDirty();
    }

    public int getOwnerMsh() {
        return ownerMsh;
    }

    public int getOwnerMsl() {
        return ownerMsl;
    }

    public int getOwnerLsh() {
        return ownerLsh;
    }

    public int getOwnerLsl() {
        return ownerLsl;
    }

    public void setOwner(java.util.UUID owner) {
        if (owner == null) {
            ownerMsh = ownerMsl = ownerLsh = ownerLsl = 0;
            markDirty();
            return;
        }
        long msb = owner.getMostSignificantBits();
        long lsb = owner.getLeastSignificantBits();
        ownerMsh = (int) (msb >>> 32);
        ownerMsl = (int) msb;
        ownerLsh = (int) (lsb >>> 32);
        ownerLsl = (int) lsb;
        markDirty();
        if (world != null && !world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = (ownerName == null || ownerName.isBlank()) ? null : ownerName;
        markDirty();
        if (world != null && !world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    public boolean isOutputRoundRobin() {
        return outputRoundRobin;
    }

    public void setOutputRoundRobin(boolean outputRoundRobin) {
        this.outputRoundRobin = outputRoundRobin;
        markDirty();
        if (world != null && !world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }

    @Override
    public Text getDisplayName() {
        return Text.literal("Quarry Controller");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new QuarryControllerScreenHandler(syncId, playerInventory, this);
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        view.putBoolean("OutputRoundRobin", outputRoundRobin);

        view.putBoolean("SilkTouch", silkTouch);
        view.putInt("SpeedLevel", Math.max(0, Math.min(3, speedLevel)));
        view.putInt("FortuneLevel", Math.max(0, Math.min(3, fortuneLevel)));

        view.putInt("OwnerMsh", ownerMsh);
        view.putInt("OwnerMsl", ownerMsl);
        view.putInt("OwnerLsh", ownerLsh);
        view.putInt("OwnerLsl", ownerLsl);
        if (ownerName != null) {
            view.putString("OwnerName", ownerName);
        }

        view.putLong("PowerBufferCfe", Math.max(0L, powerBufferCfe));
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        outputRoundRobin = view.getBoolean("OutputRoundRobin", true);

        silkTouch = view.getBoolean("SilkTouch", false);
        speedLevel = Math.max(0, Math.min(3, view.getInt("SpeedLevel", 0)));
        fortuneLevel = Math.max(0, Math.min(3, view.getInt("FortuneLevel", 0)));

        // Mutual exclusion: if both were persisted somehow, prefer silk touch.
        if (silkTouch && fortuneLevel > 0) {
            fortuneLevel = 0;
        }

        // Rebuild stable slot stacks from persisted flags.
        silkAugmentStack = silkTouch ? dev.cloudframe.fabric.content.AugmentBooks.silkTouch() : ItemStack.EMPTY;
        speedAugmentStack = speedLevel > 0 ? dev.cloudframe.fabric.content.AugmentBooks.speed(speedLevel) : ItemStack.EMPTY;
        fortuneAugmentStack = fortuneLevel > 0 ? dev.cloudframe.fabric.content.AugmentBooks.fortune(fortuneLevel) : ItemStack.EMPTY;

        ownerMsh = view.getInt("OwnerMsh", 0);
        ownerMsl = view.getInt("OwnerMsl", 0);
        ownerLsh = view.getInt("OwnerLsh", 0);
        ownerLsl = view.getInt("OwnerLsl", 0);

        ownerName = view.getString("OwnerName", null);

        powerBufferCfe = Math.max(0L, view.getLong("PowerBufferCfe", 0L));
        long cap = getPowerBufferCapacityCfe();
        if (cap > 0L && powerBufferCfe > cap) {
            powerBufferCfe = cap;
        }
    }

    public void onBroken() {
        // placeholder hook if we later need cleanup
    }
}
