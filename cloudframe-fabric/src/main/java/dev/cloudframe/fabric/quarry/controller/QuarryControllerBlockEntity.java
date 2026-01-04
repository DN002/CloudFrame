package dev.cloudframe.fabric.quarry.controller;

import dev.cloudframe.fabric.content.CloudFrameContent;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
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

public class QuarryControllerBlockEntity extends BlockEntity implements NamedScreenHandlerFactory {

    private boolean silkTouch = false;
    private int speedLevel = 0;

    private ItemStack silkAugmentStack = ItemStack.EMPTY;
    private ItemStack speedAugmentStack = ItemStack.EMPTY;

    // Owner UUID split across 4 ints (so it can be synced via PropertyDelegate later if needed)
    private int ownerMsh = 0;
    private int ownerMsl = 0;
    private int ownerLsh = 0;
    private int ownerLsl = 0;

    // Friendly owner name for UI (UUIDs are not user-friendly).
    private String ownerName = null;

    private boolean outputRoundRobin = true;

    private final Inventory augmentInventory = new Inventory() {
        @Override
        public int size() {
            return 2;
        }

        @Override
        public boolean isEmpty() {
            return silkAugmentStack.isEmpty() && speedAugmentStack.isEmpty();
        }

        @Override
        public ItemStack getStack(int slot) {
            return switch (slot) {
                case 0 -> silkAugmentStack;
                case 1 -> speedAugmentStack;
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
                return;
            }

            if (slot == 0) {
                if (dev.cloudframe.fabric.content.AugmentBooks.isSilkTouch(stack)
                    || stack.getItem() instanceof dev.cloudframe.fabric.content.augments.SilkTouchAugmentItem) {
                    silkAugmentStack = stack.copy();
                    silkAugmentStack.setCount(1);
                    setSilkTouch(true);
                }
            } else if (slot == 1) {
                int tier = dev.cloudframe.fabric.content.AugmentBooks.speedTier(stack);
                if (tier <= 0 && stack.getItem() instanceof dev.cloudframe.fabric.content.augments.SpeedAugmentItem speed) {
                    tier = speed.tier();
                }
                if (tier > 0) {
                    speedAugmentStack = stack.copy();
                    speedAugmentStack.setCount(1);
                    setSpeedLevel(Math.max(1, Math.min(3, tier)));
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
            setSilkTouch(false);
            setSpeedLevel(0);
        }
    };

    public QuarryControllerBlockEntity(BlockPos pos, BlockState state) {
        super(CloudFrameContent.getQuarryControllerBlockEntity(), pos, state);
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
        markDirty();
    }

    public int getSpeedLevel() {
        return speedLevel;
    }

    public void setSpeedLevel(int speedLevel) {
        this.speedLevel = Math.max(0, Math.min(3, speedLevel));
        if (this.speedLevel <= 0) {
            speedAugmentStack = ItemStack.EMPTY;
        } else {
            speedAugmentStack = dev.cloudframe.fabric.content.AugmentBooks.speed(this.speedLevel);
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
        view.putBoolean("SilkTouch", silkTouch);
        view.putInt("SpeedLevel", speedLevel);
        view.putBoolean("OutputRoundRobin", outputRoundRobin);
        view.putInt("OwnerMsh", ownerMsh);
        view.putInt("OwnerMsl", ownerMsl);
        view.putInt("OwnerLsh", ownerLsh);
        view.putInt("OwnerLsl", ownerLsl);
        if (ownerName != null) {
            view.putString("OwnerName", ownerName);
        }
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        outputRoundRobin = view.getBoolean("OutputRoundRobin", true);

        silkTouch = view.getBoolean("SilkTouch", false);
        speedLevel = Math.max(0, Math.min(3, view.getInt("SpeedLevel", 0)));

        // Rebuild stable slot stacks from persisted flags.
        silkAugmentStack = silkTouch ? dev.cloudframe.fabric.content.AugmentBooks.silkTouch() : ItemStack.EMPTY;
        speedAugmentStack = speedLevel > 0 ? dev.cloudframe.fabric.content.AugmentBooks.speed(speedLevel) : ItemStack.EMPTY;

        ownerMsh = view.getInt("OwnerMsh", 0);
        ownerMsl = view.getInt("OwnerMsl", 0);
        ownerLsh = view.getInt("OwnerLsh", 0);
        ownerLsl = view.getInt("OwnerLsl", 0);

        ownerName = view.getString("OwnerName", null);
    }

    public void onBroken() {
        // placeholder hook if we later need cleanup
    }
}
