package dev.cloudframe.fabric.content.trash;

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

public class TrashCanBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {

    private static final int SLOT_COUNT = 9;

    // Preview-only history. These are not real stored items.
    private final DefaultedList<ItemStack> previewSlots = DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY);

    public TrashCanBlockEntity(BlockPos pos, BlockState state) {
        super(CloudFrameContent.getTrashCanBlockEntity(), pos, state);
    }

    /**
     * Accepts (deletes) the incoming stack and updates the preview to show what will be deleted next.
     *
     * @return how many items were accepted (normally the full stack count)
     */
    public int accept(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;

        // Shift history to the right; far-right is forgotten (already deleted).
        for (int i = SLOT_COUNT - 1; i >= 1; i--) {
            previewSlots.set(i, previewSlots.get(i - 1));
        }

        // Insert newest on the left.
        ItemStack incoming = stack.copy();
        int max = Math.max(1, incoming.getMaxCount());
        if (incoming.getCount() > max) {
            incoming.setCount(max);
        }
        previewSlots.set(0, incoming);

        // Merge adjacent identical stacks left-to-right (keep newest on the left).
        for (int i = 0; i < SLOT_COUNT - 1; i++) {
            ItemStack left = previewSlots.get(i);
            if (left == null || left.isEmpty()) continue;

            ItemStack right = previewSlots.get(i + 1);
            if (right == null || right.isEmpty()) continue;

            if (!ItemStack.areItemsAndComponentsEqual(left, right)) continue;

            int space = left.getMaxCount() - left.getCount();
            if (space <= 0) continue;

            int move = Math.min(space, right.getCount());
            if (move <= 0) continue;

            left.increment(move);
            right.decrement(move);
            if (right.isEmpty()) {
                previewSlots.set(i + 1, ItemStack.EMPTY);
            }
        }

        markDirty();
        if (world != null && !world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }

        return stack.getCount();
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
        // Players cannot take items out of the trash can.
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        // Players cannot insert items through the GUI; ignore.
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
