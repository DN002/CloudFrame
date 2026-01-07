package dev.cloudframe.fabric.quarry.controller;

import dev.cloudframe.fabric.content.CloudFrameContent;
import dev.cloudframe.fabric.CloudFrameFabric;
import dev.cloudframe.common.quarry.Quarry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.ItemScatterer;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.server.MinecraftServer;

public class QuarryControllerScreenHandler extends ScreenHandler {

    private static final int ENERGY_PER_BLOCK_CFE = 480;

    private static final int UI_ROWS = 3;

    private final PropertyDelegate properties;
    private final BlockPos controllerPos;
    private final QuarryControllerBlockEntity be;

    private static final int AUGMENT_SLOTS = 2;
    private static final int SILK_SLOT_INDEX = 0;
    private static final int SPEED_SLOT_INDEX = 1;

    private final Inventory augmentInventory;

    // Properties (ints only):
    // 0 silkInstalled (0/1)
    // 1 speedLevel (0-3)
    // 2 quarryRegistered (0/1)
    // 3 quarryActive (0/1)
    // 4 quarryState (0=unregistered,1=paused,2=mining,3=scanning,4=metadata)
    // 5 quarryLevelY (int)
    // 6 remainingEstimate (int)
    // 7 outputOk (0/1)
    // 8 redstonePowered (0/1)
    // 13 outputRoundRobin (0/1)
    // 14 redstoneMode (0..2)
    // 15 chunkLoadingEnabled (0/1)
    // 16 silentMode (0/1)
    // 17 redstoneBlocked (0/1)
    // 18 progressPercent (0..100)
    // 19 etaSecondsEstimate (int)
    // 20 affectedChunks (int)
    // 21 outputJammed (0/1)
    // 22 powerBlocked (0/1)
    // 23 powerRequiredCfePerTick (int, clamped)
    // 24 powerReceivedCfePerTick (int, clamped)
    // 28 powerBufferStoredCfe (int, clamped)
    // 29 powerBufferCapacityCfe (int, clamped)
    // 25 controllerX
    // 26 controllerY
    // 27 controllerZ
    // 9 ownerMsh
    // 10 ownerMsl
    // 11 ownerLsh
    // 12 ownerLsl

    // Client ctor (simple ScreenHandlerType)
    public QuarryControllerScreenHandler(int syncId, PlayerInventory inv) {
        super(CloudFrameContent.getQuarryControllerScreenHandler(), syncId);
        this.controllerPos = BlockPos.ORIGIN;
        this.be = null;
        this.augmentInventory = new SimpleInventory(AUGMENT_SLOTS);
        this.properties = new ArrayPropertyDelegate(30);
        addProperties(this.properties);

        addAugmentSlots();
        addPlayerSlots(inv);
    }

    // Server ctor
    public QuarryControllerScreenHandler(int syncId, PlayerInventory inv, QuarryControllerBlockEntity be) {
        super(CloudFrameContent.getQuarryControllerScreenHandler(), syncId);
        this.controllerPos = be.getPos();
        this.be = be;
        this.augmentInventory = be.getAugmentInventory();

        // Best-effort: backfill owner info onto the BE from the registered quarry.
        // This keeps the client UI user-friendly after restarts (BE NBT persists in the world).
        CloudFrameFabric inst0 = CloudFrameFabric.instance();
        Object ctrlLoc0 = controllerLoc();
        Quarry q0 = (inst0 != null && inst0.getQuarryManager() != null)
            ? inst0.getQuarryManager().getByController(ctrlLoc0)
            : null;
        if (q0 != null) {
            if (q0.getOwner() != null) {
                be.setOwner(q0.getOwner());
            }
            if (be.getOwnerName() == null || be.getOwnerName().isBlank()) {
                be.setOwnerName(q0.getOwnerName());
            }
        }

        this.properties = new PropertyDelegate() {
            private Quarry quarry() {
                CloudFrameFabric inst = CloudFrameFabric.instance();
                if (inst == null || inst.getQuarryManager() == null) return null;
                return inst.getQuarryManager().getByController(controllerLoc());
            }

            @Override
            public int get(int index) {
                Quarry q = quarry();
                java.util.UUID owner = null;
                if (q != null) {
                    owner = q.getOwner();
                }

                int ownerMsh = 0;
                int ownerMsl = 0;
                int ownerLsh = 0;
                int ownerLsl = 0;
                if (owner != null) {
                    long msb = owner.getMostSignificantBits();
                    long lsb = owner.getLeastSignificantBits();
                    ownerMsh = (int) (msb >>> 32);
                    ownerMsl = (int) msb;
                    ownerLsh = (int) (lsb >>> 32);
                    ownerLsl = (int) lsb;
                } else if (be != null) {
                    ownerMsh = be.getOwnerMsh();
                    ownerMsl = be.getOwnerMsl();
                    ownerLsh = be.getOwnerLsh();
                    ownerLsl = be.getOwnerLsl();
                }

                int state;
                if (q == null) {
                    state = 0;
                } else if (q.isScanningMetadata()) {
                    state = 4;
                } else if (q.isScanning()) {
                    state = 3;
                } else if (q.isActive()) {
                    state = 2;
                } else {
                    state = 1;
                }

                return switch (index) {
                    case 0 -> be.isSilkTouch() ? 1 : 0;
                    case 1 -> Math.max(0, Math.min(3, be.getSpeedLevel()));
                    case 2 -> q != null ? 1 : 0;
                    case 3 -> (q != null && q.isActive()) ? 1 : 0;
                    case 4 -> state;
                    case 5 -> {
                        if (q == null) yield 0;
                        var r = q.getRegion();
                        if (r == null) yield 0;
                        int layerSize = Math.max(1, r.width() * r.length());
                        int layersMined = Math.max(0, q.getBlocksMined() / layerSize);
                        int y = r.maxY() - layersMined;
                        if (y < r.minY()) y = r.minY();
                        if (y > r.maxY()) y = r.maxY();
                        yield y;
                    }
                    case 6 -> {
                        if (q == null) yield 0;
                        yield q.getRemainingBlocksEstimate();
                    }
                    case 7 -> (q != null && q.hasValidOutput()) ? 1 : 0;
                    case 8 -> (q != null && q.isRedstonePowered()) ? 1 : 0;
                    case 13 -> (q != null && q.isOutputRoundRobin()) ? 1 : 0;
                    case 14 -> q != null ? q.getRedstoneMode() : 0;
                    case 15 -> (q != null && q.isChunkLoadingEnabled()) ? 1 : 0;
                    case 16 -> (q != null && q.isSilentMode()) ? 1 : 0;
                    case 17 -> (q != null && q.isRedstoneBlocked()) ? 1 : 0;
                    case 18 -> q != null ? q.getProgressPercent() : 0;
                    case 19 -> q != null ? q.getEtaSecondsEstimate() : 0;
                    case 20 -> q != null ? q.getAffectedChunkCount() : 0;
                    case 21 -> (q != null && q.isOutputJammed()) ? 1 : 0;
                    case 22 -> (q != null && q.isPowerBlocked()) ? 1 : 0;
                    case 23 -> {
                        if (q == null) yield 0;
                        // Power using should reflect actual quarry operation.
                        // If paused/off, it uses 0 CFE/t.
                        long v = q.isActive() ? Math.max(0L, q.getPowerRequiredCfePerTick()) : 0L;
                        yield v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) v;
                    }
                    case 24 -> {
                        if (q == null) yield 0;

                        long v;
                        if (q.isActive()) {
                            // Active: show the actual per-tick draw that was consumed.
                            v = Math.max(0L, q.getPowerReceivedCfePerTick());
                        } else {
                            // Paused/off: show available generation on the cable network.
                            MinecraftServer srv = null;
                            if (be != null && be.getWorld() instanceof ServerWorld sw) {
                                srv = sw.getServer();
                            }

                            if (srv == null) {
                                v = 0L;
                            } else {
                                var info = dev.cloudframe.fabric.power.FabricPowerNetworkManager.measureNetwork(srv, controllerLoc());
                                v = Math.max(0L, info.producedCfePerTick());
                            }
                        }

                        yield v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, v);
                    }
                    case 28 -> {
                        if (be == null) yield 0;
                        long v = Math.max(0L, be.getPowerBufferStoredCfe());
                        yield v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) v;
                    }
                    case 29 -> {
                        if (be == null) yield 0;
                        long v = Math.max(0L, be.getPowerBufferCapacityCfe());
                        yield v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) v;
                    }
                    case 25 -> controllerPos.getX();
                    case 26 -> controllerPos.getY();
                    case 27 -> controllerPos.getZ();
                    case 9 -> ownerMsh;
                    case 10 -> ownerMsl;
                    case 11 -> ownerLsh;
                    case 12 -> ownerLsl;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // No client->server property writes.
            }

            @Override
            public int size() {
                return 30;
            }
        };
        addProperties(this.properties);

        addAugmentSlots();
        addPlayerSlots(inv);
    }

    private static long theoreticalRequiredCfePerTick(int speedTier) {
        int tpb = switch (speedTier) {
            case 1 -> 8;
            case 2 -> 6;
            case 3 -> 5;
            default -> 12;
        };
        if (tpb <= 0) tpb = 1;
        return (ENERGY_PER_BLOCK_CFE + (long) tpb - 1L) / (long) tpb;
    }

    private Object controllerLoc() {
        if (be != null && be.getWorld() instanceof ServerWorld sw) {
            return GlobalPos.create(sw.getRegistryKey(), controllerPos.toImmutable());
        }
        // Fallback (shouldn't happen server-side)
        return controllerPos;
    }

    private void addAugmentSlots() {
        // Top row slots: match the client screen layout (generic_54).
        // Col 2 = silk, col 3 = speed.
        this.addSlot(new AugmentSlot(augmentInventory, SILK_SLOT_INDEX, 8 + 2 * 18, 18));
        this.addSlot(new AugmentSlot(augmentInventory, SPEED_SLOT_INDEX, 8 + 3 * 18, 18));
    }

    private void addPlayerSlots(PlayerInventory inv) {
        // Vanilla chest layout (generic_54): player inventory starts at y = rows*18 + 31.
        // This ensures hotbar is aligned and at the correct bottom row.
        this.addPlayerSlots(inv, 8, UI_ROWS * 18 + 31);
    }

    public BlockPos getControllerPos() {
        if (be != null) return controllerPos;
        // Client-side: controllerPos isn't known in the client ctor; sync via properties.
        return new BlockPos(properties.get(25), properties.get(26), properties.get(27));
    }

    public boolean isSilkTouch() {
        return properties.get(0) == 1;
    }

    public int getSpeedLevel() {
        return properties.get(1);
    }

    public boolean hasRegisteredQuarry() {
        return properties.get(2) == 1;
    }

    public boolean isQuarryActive() {
        return properties.get(3) == 1;
    }

    public int getQuarryState() {
        return properties.get(4);
    }

    public int getQuarryLevelY() {
        return properties.get(5);
    }

    /**
     * Remaining estimate based on region volume (not live-scanned mineable blocks).
     */
    public int getRemainingEstimate() {
        return properties.get(6);
    }

    public boolean hasValidOutput() {
        return properties.get(7) == 1;
    }

    /**
     * Redstone power state at the controller.
     * 0 = not powered, 1 = powered.
     */
    public int getPowerStatus() {
        return properties.get(8);
    }

    public boolean isOutputRoundRobin() {
        return properties.get(13) == 1;
    }

    public int getRedstoneMode() {
        return properties.get(14);
    }

    public boolean isChunkLoadingEnabled() {
        return properties.get(15) == 1;
    }

    public boolean isSilentMode() {
        return properties.get(16) == 1;
    }

    public boolean isRedstoneBlocked() {
        return properties.get(17) == 1;
    }

    public int getProgressPercent() {
        return properties.get(18);
    }

    public int getEtaSecondsEstimate() {
        return properties.get(19);
    }

    public int getAffectedChunkCount() {
        return properties.get(20);
    }

    public boolean isOutputJammed() {
        return properties.get(21) == 1;
    }

    public boolean isPowerBlocked() {
        return properties.get(22) == 1;
    }

    public int getPowerRequiredCfePerTick() {
        return properties.get(23);
    }

    public int getPowerReceivedCfePerTick() {
        return properties.get(24);
    }

    public int getPowerBufferStoredCfe() {
        return properties.get(28);
    }

    public int getPowerBufferCapacityCfe() {
        return properties.get(29);
    }

    public java.util.UUID getOwnerUuid() {
        long msb = (((long) properties.get(9)) << 32) | (properties.get(10) & 0xffffffffL);
        long lsb = (((long) properties.get(11)) << 32) | (properties.get(12) & 0xffffffffL);
        if (msb == 0L && lsb == 0L) return null;
        return new java.util.UUID(msb, lsb);
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (be == null) return false;

        CloudFrameFabric inst = CloudFrameFabric.instance();
        Quarry q = (inst != null && inst.getQuarryManager() != null)
            ? inst.getQuarryManager().getByController(controllerLoc())
            : null;

        // Helper: give an item to player or drop if full.
        java.util.function.Consumer<ItemStack> giveOrDrop = (stack) -> {
            if (stack == null || stack.isEmpty()) return;
            boolean inserted = player.getInventory().insertStack(stack);
            if (!stack.isEmpty()) {
                player.dropItem(stack, false);
            }
        };

        switch (id) {
            // Lever: toggle quarry on/off
            case 0 -> {
                if (q == null) {
                    player.sendMessage(Text.literal("Unregistered: use the Wrench to register this controller").formatted(net.minecraft.util.Formatting.YELLOW), false);
                    return true;
                }

                // Starting requires valid output; otherwise stay paused.
                if (!q.isActive() && !q.hasValidOutput()) {
                    player.sendMessage(Text.literal("Cannot start: output not connected").formatted(net.minecraft.util.Formatting.RED), false);
                    return true;
                }

                boolean newActive = !q.isActive();
                q.setActive(newActive);

                // Manual lever control should not be immediately overridden by an automated redstone mode.
                // Comparator can be used to re-enable redstone gating.
                q.setRedstoneMode(0);

                if (inst != null && inst.getQuarryManager() != null) {
                    inst.getQuarryManager().saveQuarry(q);
                }
                return true;
            }

            // Comparator: cycle redstone mode
            case 2 -> {
                if (q != null) {
                    q.setRedstoneMode((q.getRedstoneMode() + 1) % 3);
                    if (inst != null && inst.getQuarryManager() != null) {
                        inst.getQuarryManager().saveQuarry(q);
                    }
                    return true;
                }
                return false;
            }

            // Compass: toggle chunk loading
            case 3 -> {
                if (q != null) {
                    q.setChunkLoadingEnabled(!q.isChunkLoadingEnabled());
                    if (inst != null && inst.getQuarryManager() != null) {
                        inst.getQuarryManager().saveQuarry(q);
                    }
                    return true;
                }
                return false;
            }

            // Map: preview affected chunks (particles outlining chunk borders)
            case 6 -> {
                if (q == null) return false;
                if (!(player instanceof net.minecraft.server.network.ServerPlayerEntity sp)) return false;

                // Toggle continuous preview (off by default).
                Object loc = controllerLoc();
                if (loc instanceof GlobalPos gp) {
                    ChunkPreviewService.toggle(sp, q, gp);
                } else {
                    // Back-compat fallback (shouldn't happen server-side)
                    if (be != null && be.getWorld() instanceof ServerWorld sw) {
                        ChunkPreviewService.toggle(sp, q, GlobalPos.create(sw.getRegistryKey(), controllerPos.toImmutable()));
                    } else {
                        ChunkPreviewService.toggle(sp, q, GlobalPos.create(net.minecraft.world.World.OVERWORLD, controllerPos.toImmutable()));
                    }
                }
                // No chat spam; the icon tooltip reflects behavior.
                return true;
            }

            // Note block: toggle silent mode
            case 5 -> {
                if (q != null) {
                    q.setSilentMode(!q.isSilentMode());
                    if (inst != null && inst.getQuarryManager() != null) {
                        inst.getQuarryManager().saveQuarry(q);
                    }
                    return true;
                }
                return false;
            }

            // Hopper: toggle routing mode (round robin)
            case 4 -> {
                if (q != null) {
                    q.setOutputRoundRobin(!q.isOutputRoundRobin());
                    if (inst != null && inst.getQuarryManager() != null) {
                        inst.getQuarryManager().saveQuarry(q);
                    }
                    return true;
                }
                return false;
            }

            // Barrier: remove controller block (drops controller + augments)
            case 1 -> {
                if (!(be.getWorld() instanceof ServerWorld sw)) return false;

                // Drop installed augments + controller ON THE GROUND.
                ItemStack silk = augmentInventory.removeStack(SILK_SLOT_INDEX);
                if (!silk.isEmpty()) {
                    ItemScatterer.spawn(sw, controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5, silk);
                }

                ItemStack speed = augmentInventory.removeStack(SPEED_SLOT_INDEX);
                if (!speed.isEmpty()) {
                    ItemScatterer.spawn(sw, controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5, speed);
                }

                ItemScatterer.spawn(sw, controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5,
                    new ItemStack(CloudFrameContent.QUARRY_CONTROLLER, 1));

                // Unregister quarry if present
                if (q != null && inst != null && inst.getQuarryManager() != null) {
                    // GUI removal doesn't trigger block-break events, so remove the frame explicitly.
                    q.removeGlassFrame();
                    inst.getQuarryManager().remove(q);
                    inst.getQuarryManager().deleteQuarry(q);
                }

                // Close the UI, then remove the controller block.
                if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                    sp.closeHandledScreen();
                }

                sw.setBlockState(controllerPos, Blocks.AIR.getDefaultState(), 3);
                return true;
            }

            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        Slot clicked = this.slots.get(slot);
        if (clicked == null || !clicked.hasStack()) return ItemStack.EMPTY;

        ItemStack original = clicked.getStack();
        ItemStack copy = original.copy();

        // From augment slots -> player inventory
        if (slot < AUGMENT_SLOTS) {
            if (!this.insertItem(original, AUGMENT_SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            clicked.markDirty();
            syncAugmentsToQuarryIfPresent();
            return copy;
        }

        // From player inventory -> appropriate augment slot
        if (dev.cloudframe.fabric.content.AugmentBooks.isSilkTouch(original)
            || original.getItem() instanceof dev.cloudframe.fabric.content.augments.SilkTouchAugmentItem) {
            if (!this.insertItem(original, SILK_SLOT_INDEX, SILK_SLOT_INDEX + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            int tier = dev.cloudframe.fabric.content.AugmentBooks.speedTier(original);
            if (tier <= 0 && original.getItem() instanceof dev.cloudframe.fabric.content.augments.SpeedAugmentItem speed) {
                tier = speed.tier();
            }
            if (tier > 0) {
                if (!this.insertItem(original, SPEED_SLOT_INDEX, SPEED_SLOT_INDEX + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (original.isEmpty()) {
            clicked.setStack(ItemStack.EMPTY);
        } else {
            clicked.markDirty();
        }

        syncAugmentsToQuarryIfPresent();
        return copy;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // UX: simple left-click on an installed augment slot returns it to inventory.
        if (be != null
            && slotIndex >= 0
            && slotIndex < AUGMENT_SLOTS
            && actionType == SlotActionType.PICKUP
            && button == 0
            && this.getCursorStack().isEmpty()) {

            Slot slot = this.slots.get(slotIndex);
            if (slot != null && slot.hasStack()) {
                ItemStack removed = slot.takeStack(1);
                if (!removed.isEmpty()) {
                    boolean inserted = player.getInventory().insertStack(removed);
                    if (!inserted) {
                        player.dropItem(removed, false);
                    }
                    syncAugmentsToQuarryIfPresent();
                    return;
                }
            }
        }

        super.onSlotClick(slotIndex, button, actionType, player);

        if (be != null && slotIndex >= 0 && slotIndex < AUGMENT_SLOTS) {
            syncAugmentsToQuarryIfPresent();
        }
    }

    private void syncAugmentsToQuarryIfPresent() {
        if (be == null) return;
        CloudFrameFabric inst = CloudFrameFabric.instance();
        if (inst == null || inst.getQuarryManager() == null) return;
        Quarry q = inst.getQuarryManager().getByController(controllerLoc());
        if (q == null) return;

        q.setSilkTouchAugment(be.isSilkTouch());
        q.setSpeedAugmentLevel(be.getSpeedLevel());
        inst.getQuarryManager().saveQuarry(q);
    }

    private class AugmentSlot extends Slot {
        public AugmentSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public int getMaxItemCount() {
            return 1;
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return false;

            if (this.getIndex() == SILK_SLOT_INDEX) {
                return dev.cloudframe.fabric.content.AugmentBooks.isSilkTouch(stack)
                    || stack.getItem() instanceof dev.cloudframe.fabric.content.augments.SilkTouchAugmentItem;
            }

            if (this.getIndex() == SPEED_SLOT_INDEX) {
                int tier = dev.cloudframe.fabric.content.AugmentBooks.speedTier(stack);
                if (tier <= 0 && stack.getItem() instanceof dev.cloudframe.fabric.content.augments.SpeedAugmentItem speed) {
                    tier = speed.tier();
                }
                return tier > 0;
            }

            return false;
        }

        @Override
        public void setStack(ItemStack stack) {
            super.setStack(stack);
            syncAugmentsToQuarryIfPresent();
        }

        @Override
        public void onTakeItem(PlayerEntity player, ItemStack stack) {
            super.onTakeItem(player, stack);
            syncAugmentsToQuarryIfPresent();
        }
    }
}
