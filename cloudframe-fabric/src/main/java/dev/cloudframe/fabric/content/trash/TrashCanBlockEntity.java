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

public class TrashCanBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {

    private ItemStack preview = ItemStack.EMPTY;

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

        // Replace the preview with the latest incoming item.
        this.preview = stack.copy();
        markDirty();
        if (world != null && !world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }

        return stack.getCount();
    }

    // Inventory (1-slot, read-only for players; insertion is via pipes/quarry)

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return preview.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return slot == 0 ? preview : ItemStack.EMPTY;
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
        preview = ItemStack.EMPTY;
        markDirty();
    }

    @Override
    public Text getDisplayName() {
        return Text.literal("Trash Can");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new TrashCanScreenHandler(syncId, playerInventory, this);
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        if (preview != null && !preview.isEmpty()) {
            // Persist a simple preview (item id + count). This is the only thing the UI needs.
            try {
                var id = Registries.ITEM.getId(preview.getItem());
                if (id != null) {
                    view.putString("PreviewItem", id.toString());
                    view.putInt("PreviewCount", Math.max(1, preview.getCount()));
                }
            } catch (Throwable ignored) {
                // Best-effort persistence; safe to ignore if something goes wrong.
            }
        }
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        String idStr = view.getString("PreviewItem", null);
        int count = Math.max(0, view.getInt("PreviewCount", 0));

        if (idStr == null || idStr.isBlank() || count <= 0) {
            preview = ItemStack.EMPTY;
            return;
        }

        try {
            Identifier id = Identifier.of(idStr);
            var item = Registries.ITEM.get(id);
            if (item == null || item == Items.AIR) {
                preview = ItemStack.EMPTY;
                return;
            }

            ItemStack stack = new ItemStack(item, Math.min(count, item.getMaxCount()));
            if (stack.isEmpty()) {
                preview = ItemStack.EMPTY;
            } else {
                preview = stack;
            }
        } catch (Throwable ignored) {
            preview = ItemStack.EMPTY;
        }
    }
}
