package dev.cloudframe.fabric.content.trash;

import dev.cloudframe.fabric.content.CloudFrameContent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class TrashCanScreenHandler extends ScreenHandler {

    private final Inventory trashInventory;

    // Client ctor
    public TrashCanScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(CloudFrameContent.getTrashCanScreenHandler(), syncId);
        this.trashInventory = new net.minecraft.inventory.SimpleInventory(1);

        addTrashSlot();
        addPlayerSlots(playerInventory);
    }

    // Server ctor
    public TrashCanScreenHandler(int syncId, PlayerInventory playerInventory, Inventory trashInventory) {
        super(CloudFrameContent.getTrashCanScreenHandler(), syncId);
        this.trashInventory = trashInventory;

        addTrashSlot();
        addPlayerSlots(playerInventory);
    }

    private void addTrashSlot() {
        // Centered-ish in a generic container background.
        int x = 80;
        int y = 20;

        this.addSlot(new Slot(trashInventory, 0, x, y) {
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

    private void addPlayerSlots(PlayerInventory playerInventory) {
        int playerInvY = 50;

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
