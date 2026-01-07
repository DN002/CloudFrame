package dev.cloudframe.fabric.content.trash;

import dev.cloudframe.fabric.content.CloudFrameContent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class TrashCanScreenHandler extends ScreenHandler {

    private static final int SLOT_COUNT = 9;

    private final Inventory trashInventory;

    // Client ctor
    public TrashCanScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(CloudFrameContent.getTrashCanScreenHandler(), syncId);
        this.trashInventory = new net.minecraft.inventory.SimpleInventory(SLOT_COUNT);

        addTrashSlots();
        addPlayerSlots(playerInventory);
    }

    // Server ctor
    public TrashCanScreenHandler(int syncId, PlayerInventory playerInventory, Inventory trashInventory) {
        super(CloudFrameContent.getTrashCanScreenHandler(), syncId);
        this.trashInventory = trashInventory;

        addTrashSlots();
        addPlayerSlots(playerInventory);
    }

    private void addTrashSlots() {
        // Matches the vanilla generic_54 background for 1 row.
        int y = 18;
        for (int col = 0; col < SLOT_COUNT; col++) {
            int x = 8 + col * 18;
            final int slotIndex = col;
            this.addSlot(new Slot(trashInventory, col, x, y) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    // Only slot 0 (leftmost) accepts insertions for trashing
                    return slotIndex == 0;
                }

                @Override
                public boolean canTakeItems(PlayerEntity playerEntity) {
                    // Players can take items from any slot to rescue them
                    return true;
                }
            });
        }
    }

    private void addPlayerSlots(PlayerInventory playerInventory) {
        int playerInvY = 49;

        // Player inventory (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, playerInvY + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, playerInvY + 58));
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasStack()) {
            return ItemStack.EMPTY;
        }

        ItemStack originalStack = slot.getStack();
        ItemStack stackCopy = originalStack.copy();

        // If shift-clicking from player inventory, try to trash the item (insert into slot 0)
        if (slotIndex >= SLOT_COUNT) {
            // This is a player inventory slot
            Slot trashSlot = this.slots.get(0);
            if (trashSlot.canInsert(originalStack)) {
                // Trash the item by inserting into slot 0
                trashInventory.setStack(0, originalStack.copy());
                slot.setStack(ItemStack.EMPTY);
                return stackCopy;
            }
        }
        // If shift-clicking from trash slots, try to move to player inventory
        else if (slotIndex < SLOT_COUNT) {
            if (!this.insertItem(originalStack, SLOT_COUNT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickTransfer(originalStack, stackCopy);
            if (originalStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }

        return stackCopy;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return trashInventory.canPlayerUse(player);
    }
}
