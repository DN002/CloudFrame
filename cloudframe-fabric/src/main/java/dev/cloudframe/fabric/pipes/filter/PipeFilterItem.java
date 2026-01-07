package dev.cloudframe.fabric.pipes.filter;

import dev.cloudframe.fabric.CloudFrameFabric;
import dev.cloudframe.fabric.content.CloudFrameContent;
import dev.cloudframe.fabric.content.TubeBlock;
import dev.cloudframe.fabric.util.ClickSideUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.util.Hand;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

import java.util.List;

import dev.cloudframe.fabric.platform.items.FabricItemIdRegistry;
import dev.cloudframe.common.pipes.filter.PipeFilterConfig;
import dev.cloudframe.common.pipes.filter.PipeFilterState;

public class PipeFilterItem extends Item {

    private static final String NBT_ROOT = "CloudFramePipeFilter";
    private static final String NBT_MODE = "Mode";
    private static final String NBT_ITEMS = "Items";

    public PipeFilterItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }

        // Sneak-right-click anywhere (even dirt) configures the item.
        // Normal right-click should fall through to TubeBlock for installation.
        if (!serverPlayer.isSneaking()) return ActionResult.PASS;

        // Client: return PASS so the server still receives the interaction.
        if (context.getWorld().isClient()) {
            return ActionResult.PASS;
        }

        CloudFrameFabric instance = CloudFrameFabric.instance();
        if (instance != null) {
            var dbg = dev.cloudframe.common.util.DebugManager.get(PipeFilterItem.class);
            dbg.log("pipeFilter", "PipeFilterItem.useOnBlock: opening item config GUI (block=" + context.getBlockPos() + ")");
        }

        openItemConfigScreen(serverPlayer);
        return ActionResult.SUCCESS;
    }

    private static ActionResult tryOpenFilter(ServerPlayerEntity serverPlayer, World world, BlockPos tubePos, Direction hitSide, ItemStack inHand) {
        CloudFrameFabric instance = CloudFrameFabric.instance();
        if (instance == null || instance.getPipeFilterManager() == null) {
            return ActionResult.PASS;
        }

        Direction side = ClickSideUtil.getClickedArmSide(serverPlayer, tubePos, hitSide);
        int sideIndex = ClickSideUtil.toDirIndex(side);

        if (TubeBlock.isSideDisabled(world, tubePos, side)) {
            serverPlayer.sendMessage(Text.literal("§7That side is disconnected."), true);
            return ActionResult.SUCCESS;
        }

        // Only meaningful on inventory sides.
        BlockPos neighbor = tubePos.offset(side);
        var be = world.getBlockEntity(neighbor);
        if (!(be instanceof Inventory)) {
            serverPlayer.sendMessage(Text.literal("§7No inventory on that side."), true);
            return ActionResult.SUCCESS;
        }

        GlobalPos pipePos = GlobalPos.create(world.getRegistryKey(), tubePos.toImmutable());
        boolean hasFilter = instance.getPipeFilterManager().hasFilter(pipePos, sideIndex);
        if (!hasFilter) {
            instance.getPipeFilterManager().getOrCreate(pipePos, sideIndex);
            if (!serverPlayer.isCreative()) {
                inHand.decrement(1);
            }
            TubeBlock.refreshConnections(world, tubePos);
        }

        serverPlayer.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Pipe Filter");
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory inv, PlayerEntity p) {
                return new PipeFilterScreenHandler(syncId, inv, pipePos, sideIndex);
            }
        });

        return ActionResult.SUCCESS;
    }

    private static void openItemConfigScreen(ServerPlayerEntity serverPlayer) {
        serverPlayer.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Pipe Filter");
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory inv, PlayerEntity p) {
                return new PipeFilterScreenHandler(syncId, inv, p.getMainHandStack());
            }
        });
    }

    public static final class ItemConfig {
        public final int mode;
        public final ItemStack[] items;

        private ItemConfig(int mode, ItemStack[] items) {
            this.mode = (mode == dev.cloudframe.fabric.pipes.FabricPipeFilterManager.MODE_BLACKLIST)
                ? dev.cloudframe.fabric.pipes.FabricPipeFilterManager.MODE_BLACKLIST
                : dev.cloudframe.fabric.pipes.FabricPipeFilterManager.MODE_WHITELIST;
            this.items = items;
        }
    }

    public static ItemConfig readItemConfig(ItemStack stack) {
        int mode = dev.cloudframe.fabric.pipes.FabricPipeFilterManager.MODE_WHITELIST;
        ItemStack[] items = new ItemStack[dev.cloudframe.fabric.pipes.FabricPipeFilterManager.SLOT_COUNT];
        for (int i = 0; i < items.length; i++) items[i] = ItemStack.EMPTY;

        if (stack == null) {
            return new ItemConfig(mode, items);
        }

        NbtCompound nbt = getCustomDataNbt(stack);
        if (nbt == null || !nbt.contains(NBT_ROOT)) {
            return new ItemConfig(mode, items);
        }

        NbtCompound root = nbt.getCompound(NBT_ROOT).orElse(null);
        if (root == null) {
            return new ItemConfig(mode, items);
        }

        mode = root.getInt(NBT_MODE).orElse(mode);

        String[] rawIds = new String[PipeFilterState.SLOT_COUNT];
        for (int i = 0; i < rawIds.length; i++) rawIds[i] = null;

        if (root.contains(NBT_ITEMS)) {
            NbtList list = root.getList(NBT_ITEMS).orElse(new NbtList());
            for (int i = 0; i < Math.min(rawIds.length, list.size()); i++) {
                String id = list.getString(i).orElse("");
                rawIds[i] = (id == null || id.isBlank()) ? null : id;
            }
        }

        PipeFilterConfig cfg = new PipeFilterConfig(mode, rawIds);

        // Convert canonical ids back into stacks.
        String[] ids = cfg.copyItemIds();
        for (int i = 0; i < Math.min(items.length, ids.length); i++) {
            String id = ids[i];
            if (id == null || id.isBlank()) continue;
            try {
                var item = FabricItemIdRegistry.INSTANCE.itemById(id);
                if (item != null) items[i] = new ItemStack(item, 1);
            } catch (Throwable ignored) {
                // ignore
            }
        }

        return new ItemConfig(cfg.mode(), items);
    }

    public static PipeFilterConfig toConfig(int mode, ItemStack[] items) {
        // Convert stacks -> ids, then canonicalize in Common so semantics match across platforms.
        String[] rawIds = new String[PipeFilterState.SLOT_COUNT];
        for (int i = 0; i < rawIds.length; i++) {
            ItemStack s = (items != null && i < items.length) ? items[i] : ItemStack.EMPTY;
            rawIds[i] = (s == null || s.isEmpty()) ? null : FabricItemIdRegistry.INSTANCE.idOf(s.getItem());
        }

        return new PipeFilterConfig(mode, rawIds);
    }

    public static void writeItemConfig(ItemStack stack, PipeFilterConfig cfg) {
        if (stack == null || cfg == null) return;

        NbtCompound nbt = getCustomDataNbt(stack);
        if (nbt == null) {
            nbt = new NbtCompound();
        }

        NbtCompound root = nbt.getCompound(NBT_ROOT).orElse(new NbtCompound());

        root.putInt(NBT_MODE, cfg.mode());

        NbtList list = new NbtList();
        String[] ids = cfg.copyItemIds();
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            list.add(NbtString.of(id == null ? "" : id));
        }
        root.put(NBT_ITEMS, list);

        nbt.put(NBT_ROOT, root);
        setCustomDataNbt(stack, nbt);
    }

    public static void writeItemConfig(ItemStack stack, int mode, ItemStack[] items) {
        writeItemConfig(stack, toConfig(mode, items));
    }

    public static void writeItemConfigFromFilterState(ItemStack stack, dev.cloudframe.fabric.pipes.FabricPipeFilterManager.FilterState state) {
        if (stack == null) return;
        if (state == null) {
            writeItemConfig(stack, dev.cloudframe.fabric.pipes.FabricPipeFilterManager.MODE_WHITELIST, new ItemStack[0]);
            return;
        }

        writeItemConfig(stack, state.mode(), state.copyStacks());
    }

    private static NbtCompound getCustomDataNbt(ItemStack stack) {
        if (stack == null) return null;
        NbtComponent c = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(new NbtCompound()));
        return c.copyNbt();
    }

    private static void setCustomDataNbt(ItemStack stack, NbtCompound nbt) {
        if (stack == null || nbt == null) return;
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }
}
