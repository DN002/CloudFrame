package dev.cloudframe.fabric.content.trash;

import dev.cloudframe.common.trash.TrashSink;
import dev.cloudframe.fabric.content.CloudFrameContent;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;

public class TrashCanBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory, TrashSink {

    private static final int SLOT_COUNT = 9;

    // Preview-only history. These are not real stored items.
    private final DefaultedList<ItemStack> previewSlots = DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY);

    public TrashCanBlockEntity(BlockPos pos, BlockState state) {
        super(CloudFrameContent.getTrashCanBlockEntity(), pos, state);
    }

    /**
     * Accepts (deletes) the incoming stack and updates the preview to show what will be deleted next.
     * Newest items appear on the LEFT. When full or different, items push RIGHT and fall off the end.
     *
     * @return how many items were accepted (normally the full stack count)
     */
    public int accept(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;

        ItemStack incoming = stack.copy();
        int max = Math.max(1, incoming.getMaxCount());
        if (incoming.getCount() > max) {
            incoming.setCount(max);
        }

        // Check if slot 0 is empty
        ItemStack leftmost = previewSlots.get(0);
        if (leftmost == null || leftmost.isEmpty()) {
            // Slot 0 is empty, just place the item there without shifting
            previewSlots.set(0, incoming);
            markDirty();
            if (world != null && !world.isClient()) {
                world.updateListeners(pos, getCachedState(), getCachedState(), 3);
            }
            return stack.getCount();
        }

        // Slot 0 has an item, check if we can stack
        if (ItemStack.areItemsAndComponentsEqual(leftmost, incoming)) {
            int space = leftmost.getMaxCount() - leftmost.getCount();
            if (space > 0) {
                // Stack into the leftmost slot
                int addAmount = Math.min(space, incoming.getCount());
                leftmost.increment(addAmount);
                incoming.decrement(addAmount);
                
                // If we consumed all the incoming items, we're done
                if (incoming.isEmpty()) {
                    markDirty();
                    if (world != null && !world.isClient()) {
                        world.updateListeners(pos, getCachedState(), getCachedState(), 3);
                    }
                    return stack.getCount();
                }
            }
        }

        // Leftmost is full or different item - shift everything right (far-right is deleted)
        for (int i = SLOT_COUNT - 1; i >= 1; i--) {
            previewSlots.set(i, previewSlots.get(i - 1));
        }

        // Insert remaining/new items on the left
        previewSlots.set(0, incoming);

        markDirty();
        if (world != null && !world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }

        return stack.getCount();
    }

    @Override
    public int accept(Object itemStack) {
        if (itemStack instanceof ItemStack stack) {
            return accept(stack);
        }
        return 0;
    }

    // Inventory (9-slot, read-only for players; insertion is via pipes/quarry)

    @Override
    public int size() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack s = previewSlots.get(i);
            if (s != null && !s.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return ItemStack.EMPTY;
        ItemStack s = previewSlots.get(slot);
        return s == null ? ItemStack.EMPTY : s;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot < 0 || slot >= SLOT_COUNT) return ItemStack.EMPTY;
        ItemStack current = previewSlots.get(slot);
        if (current == null || current.isEmpty()) return ItemStack.EMPTY;

        ItemStack removed = current.split(amount);
        if (current.isEmpty()) {
            previewSlots.set(slot, ItemStack.EMPTY);
            // Shift all slots right to close the gap (slot 0 becomes empty)
            shiftSlotsRight(slot);
        }
        markDirty();
        if (world != null && !world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
        return removed;
    }

    @Override
    public ItemStack removeStack(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return ItemStack.EMPTY;
        ItemStack current = previewSlots.get(slot);
        if (current == null || current.isEmpty()) return ItemStack.EMPTY;

        previewSlots.set(slot, ItemStack.EMPTY);
        // Shift all slots right to close the gap (slot 0 becomes empty)
        shiftSlotsRight(slot);
        markDirty();
        if (world != null && !world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
        return current;
    }

    /**
     * Shifts slots right starting from the removed position to close gaps.
     * All slots before the removed slot move right by one position.
     * This leaves slot 0 empty and ready for new items.
     */
    private void shiftSlotsRight(int removedSlot) {
        for (int i = removedSlot; i > 0; i--) {
            previewSlots.set(i, previewSlots.get(i - 1));
        }
        previewSlots.set(0, ItemStack.EMPTY);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        // Only slot 0 accepts manual insertions (for trashing items)
        if (slot == 0) {
            if (stack != null && !stack.isEmpty()) {
                accept(stack);
            }
        }
        // Other slots cannot be manually set
    }

    @Override
    public int getMaxCountPerStack() {
        return 64;
    }

    @Override
    public void markDirty() {
        super.markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void clear() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            previewSlots.set(i, ItemStack.EMPTY);
        }
        markDirty();
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.cloudframe.trash_can");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new TrashCanScreenHandler(syncId, playerInventory, this);
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);

        // Persist preview history as simple (item id + count) pairs per slot.
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack s = previewSlots.get(i);
            String keyItem = "S" + i + "Item";
            String keyCount = "S" + i + "Count";

            if (s == null || s.isEmpty()) {
                view.putString(keyItem, "");
                view.putInt(keyCount, 0);
                continue;
            }

            try {
                var id = Registries.ITEM.getId(s.getItem());
                view.putString(keyItem, id == null ? "" : id.toString());
                view.putInt(keyCount, Math.max(1, s.getCount()));
            } catch (Throwable ignored) {
                view.putString(keyItem, "");
                view.putInt(keyCount, 0);
            }
        }
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);

        for (int i = 0; i < SLOT_COUNT; i++) {
            String idStr = view.getString("S" + i + "Item", "");
            int count = Math.max(0, view.getInt("S" + i + "Count", 0));

            if (idStr == null || idStr.isBlank() || count <= 0) {
                previewSlots.set(i, ItemStack.EMPTY);
                continue;
            }

            try {
                Identifier id = Identifier.of(idStr);
                var item = Registries.ITEM.get(id);
                if (item == null || item == Items.AIR) {
                    previewSlots.set(i, ItemStack.EMPTY);
                    continue;
                }

                ItemStack s = new ItemStack(item, Math.min(count, item.getMaxCount()));
                previewSlots.set(i, s.isEmpty() ? ItemStack.EMPTY : s);
            } catch (Throwable ignored) {
                previewSlots.set(i, ItemStack.EMPTY);
            }
        }
    }
}
