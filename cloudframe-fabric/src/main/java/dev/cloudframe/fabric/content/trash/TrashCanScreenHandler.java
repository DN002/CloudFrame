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
            this.addSlot(new Slot(trashInventory, col, x, y) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean canTakeItems(PlayerEntity playerEntity) {
                    return false;
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
        // No special shift-click behavior needed; nothing can move into/out of the trash slot.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return trashInventory.canPlayerUse(player);
    }
}
