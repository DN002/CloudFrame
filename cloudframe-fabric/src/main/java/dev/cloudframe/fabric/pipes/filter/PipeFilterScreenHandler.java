package dev.cloudframe.fabric.pipes.filter;

import dev.cloudframe.fabric.CloudFrameFabric;
import dev.cloudframe.fabric.pipes.FabricPipeFilterManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.ItemScatterer;

import dev.cloudframe.fabric.content.CloudFrameContent;
import dev.cloudframe.fabric.content.TubeBlock;

public class PipeFilterScreenHandler extends ScreenHandler {

    public static final int UI_ROWS = 3;

    private final Inventory filterInventory;
    private final PropertyDelegate properties;

    private final GlobalPos pipePos;
    private final int sideIndex;

    private final FabricPipeFilterManager filterManager;

    /**
     * Client ctor (synced contents + mode will arrive via vanilla slot/property sync).
     */
    public PipeFilterScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(dev.cloudframe.fabric.content.CloudFrameContent.getPipeFilterScreenHandler(), syncId);
        this.pipePos = null;
        this.sideIndex = -1;
        this.filterManager = null;
        this.filterInventory = new SimpleInventory(FabricPipeFilterManager.SLOT_COUNT);
        this.properties = new ArrayPropertyDelegate(1);
        this.addProperties(this.properties);
        addSlots(playerInventory);
    }

    /**
     * Server ctor.
     */
    public PipeFilterScreenHandler(int syncId, PlayerInventory playerInventory, GlobalPos pipePos, int sideIndex) {
        super(dev.cloudframe.fabric.content.CloudFrameContent.getPipeFilterScreenHandler(), syncId);
        this.pipePos = pipePos;
        this.sideIndex = sideIndex;

        CloudFrameFabric instance = CloudFrameFabric.instance();
        this.filterManager = instance != null ? instance.getPipeFilterManager() : null;

        FabricPipeFilterManager.FilterState st = filterManager != null ? filterManager.getOrCreate(pipePos, sideIndex) : null;

        PipeFilterInventory inv = new PipeFilterInventory(FabricPipeFilterManager.SLOT_COUNT, () -> {
            if (this.filterManager != null && this.pipePos != null && this.sideIndex >= 0) {
                this.filterManager.setItems(this.pipePos, this.sideIndex, copyFilterItems());
            }
        });

        if (st != null) {
            ItemStack[] initial = st.copyStacks();
            for (int i = 0; i < FabricPipeFilterManager.SLOT_COUNT; i++) {
                inv.setStack(i, initial[i]);
            }
        }

        this.filterInventory = inv;
        this.properties = new ArrayPropertyDelegate(1);
        if (st != null) {
            this.properties.set(0, st.mode());
        }
        this.addProperties(this.properties);

        addSlots(playerInventory);
    }

    private void addSlots(PlayerInventory playerInventory) {
        // Filter slots (27 -> 3 rows)
        for (int row = 0; row < UI_ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9;
                int x = 8 + col * 18;
                int y = 18 + row * 18;
                this.addSlot(new FilterSlot(filterInventory, index, x, y));
            }
        }

        // Player inventory
        int invY = 18 + UI_ROWS * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int x = 8 + col * 18;
                int y = invY + row * 18;
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, x, y));
            }
        }

        // Hotbar
        int hotbarY = invY + 58;
        for (int col = 0; col < 9; col++) {
            int x = 8 + col * 18;
            this.addSlot(new Slot(playerInventory, col, x, hotbarY));
        }
    }

    public int getMode() {
        return properties.get(0);
    }

    public boolean isWhitelist() {
        return getMode() == FabricPipeFilterManager.MODE_WHITELIST;
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        // 0 = toggle whitelist/blacklist
        if (id == 0) {
            int nextMode = (getMode() == FabricPipeFilterManager.MODE_WHITELIST)
                ? FabricPipeFilterManager.MODE_BLACKLIST
                : FabricPipeFilterManager.MODE_WHITELIST;

            properties.set(0, nextMode);

            if (filterManager != null && pipePos != null && sideIndex >= 0) {
                filterManager.setMode(pipePos, sideIndex, nextMode);
            }

            return true;
        }

        // 1 = remove filter from pipe
        if (id == 1) {
            if (filterManager != null && pipePos != null && sideIndex >= 0 && player instanceof ServerPlayerEntity sp) {
                filterManager.removeFilter(pipePos, sideIndex);

                // Return the filter item.
                ItemStack drop = new ItemStack(CloudFrameContent.getPipeFilter(), 1);
                sp.getInventory().insertStack(drop);
                var server = filterManager.getServer();
                var w = server != null ? server.getWorld(pipePos.dimension()) : null;
                if (w != null) {
                    if (!drop.isEmpty()) {
                        ItemScatterer.spawn(w, sp.getX(), sp.getY() + 0.5, sp.getZ(), drop);
                    }

                    // Update tube blockstate so the visual attachment disappears.
                    TubeBlock.refreshConnections(w, pipePos.pos());
                }

                sp.closeHandledScreen();
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        if (pipePos == null) return true; // client side

        BlockPos pos = pipePos.pos();
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasStack()) {
            return ItemStack.EMPTY;
        }

        ItemStack original = slot.getStack();
        newStack = original.copy();

        int filterSlots = UI_ROWS * 9;
        if (slotIndex < filterSlots) {
            // From filter -> player
            if (!this.insertItem(original, filterSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // From player -> filter
            if (!this.insertItem(original, 0, filterSlots, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (original.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }

        return newStack;
    }

    private ItemStack[] copyFilterItems() {
        ItemStack[] out = new ItemStack[FabricPipeFilterManager.SLOT_COUNT];
        for (int i = 0; i < FabricPipeFilterManager.SLOT_COUNT; i++) {
            ItemStack s = filterInventory.getStack(i);
            out[i] = (s == null) ? ItemStack.EMPTY : s.copy();
            if (!out[i].isEmpty()) {
                out[i].setCount(1);
            }
        }
        return out;
    }

    private static final class FilterSlot extends Slot {
        FilterSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public int getMaxItemCount() {
            return 1;
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return stack != null && !stack.isEmpty();
        }
    }

    private static final class PipeFilterInventory extends SimpleInventory {
        private final Runnable onChanged;

        PipeFilterInventory(int size, Runnable onChanged) {
            super(size);
            this.onChanged = onChanged;
        }

        @Override
        public void markDirty() {
            super.markDirty();
            if (onChanged != null) {
                onChanged.run();
            }
        }
    }
}
