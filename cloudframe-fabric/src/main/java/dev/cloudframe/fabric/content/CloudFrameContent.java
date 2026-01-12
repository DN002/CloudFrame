package dev.cloudframe.fabric.content;

import dev.cloudframe.fabric.CloudFrameFabric;
import dev.cloudframe.fabric.content.augments.FortuneAugmentItem;
import dev.cloudframe.fabric.content.augments.SilkTouchAugmentItem;
import dev.cloudframe.fabric.content.augments.SpeedAugmentItem;
import dev.cloudframe.fabric.power.EnergyInterop;
import dev.cloudframe.fabric.quarry.controller.QuarryControllerBlockEntity;
import dev.cloudframe.fabric.quarry.controller.QuarryControllerBlock;
import dev.cloudframe.fabric.quarry.controller.QuarryControllerScreenHandler;
import dev.cloudframe.fabric.pipes.filter.PipeFilterItem;
import dev.cloudframe.fabric.pipes.filter.PipeFilterScreenHandler;
import dev.cloudframe.fabric.content.trash.TrashCanBlock;
import dev.cloudframe.fabric.content.trash.TrashCanBlockEntity;
import dev.cloudframe.fabric.content.trash.TrashCanScreenHandler;
import dev.cloudframe.common.ids.CloudFrameIds;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class CloudFrameContent {

    private CloudFrameContent() {
    }

    public static final Identifier CLOUD_PIPE_ID = Identifier.of(CloudFrameFabric.MOD_ID, CloudFrameIds.CLOUD_PIPE);
    public static final Identifier CLOUD_CABLE_ID = Identifier.of(CloudFrameFabric.MOD_ID, CloudFrameIds.CLOUD_CABLE);
    public static final Identifier STRATUS_PANEL_ID = Identifier.of(CloudFrameFabric.MOD_ID, CloudFrameIds.STRATUS_PANEL);
    public static final Identifier CLOUD_TURBINE_ID = Identifier.of(CloudFrameFabric.MOD_ID, CloudFrameIds.CLOUD_TURBINE);
    public static final Identifier CLOUD_CELL_ID = Identifier.of(CloudFrameFabric.MOD_ID, CloudFrameIds.CLOUD_CELL);
    public static final Identifier CONTROLLER_ID = Identifier.of(CloudFrameFabric.MOD_ID, CloudFrameIds.QUARRY_CONTROLLER);
    public static final Identifier MARKER_ID = Identifier.of(CloudFrameFabric.MOD_ID, CloudFrameIds.MARKER);
    public static final Identifier WRENCH_ID = Identifier.of(CloudFrameFabric.MOD_ID, CloudFrameIds.WRENCH);
    public static final Identifier PIPE_FILTER_ID = Identifier.of(CloudFrameFabric.MOD_ID, CloudFrameIds.PIPE_FILTER);
    public static final Identifier TRASH_CAN_ID = Identifier.of(CloudFrameFabric.MOD_ID, CloudFrameIds.TRASH_CAN);
    public static final Identifier SILK_TOUCH_AUGMENT_ID = Identifier.of(CloudFrameFabric.MOD_ID, CloudFrameIds.SILK_TOUCH_AUGMENT);
    public static final Identifier SPEED_AUGMENT_1_ID = Identifier.of(CloudFrameFabric.MOD_ID, CloudFrameIds.SPEED_AUGMENT_1);
    public static final Identifier SPEED_AUGMENT_2_ID = Identifier.of(CloudFrameFabric.MOD_ID, CloudFrameIds.SPEED_AUGMENT_2);
    public static final Identifier SPEED_AUGMENT_3_ID = Identifier.of(CloudFrameFabric.MOD_ID, CloudFrameIds.SPEED_AUGMENT_3);
    public static final Identifier FORTUNE_AUGMENT_1_ID = Identifier.of(CloudFrameFabric.MOD_ID, CloudFrameIds.FORTUNE_AUGMENT_1);
    public static final Identifier FORTUNE_AUGMENT_2_ID = Identifier.of(CloudFrameFabric.MOD_ID, CloudFrameIds.FORTUNE_AUGMENT_2);
    public static final Identifier FORTUNE_AUGMENT_3_ID = Identifier.of(CloudFrameFabric.MOD_ID, CloudFrameIds.FORTUNE_AUGMENT_3);

    public static Block CLOUD_PIPE_BLOCK;
    public static Block CLOUD_CABLE_BLOCK;
    public static Block STRATUS_PANEL_BLOCK;
    public static Block CLOUD_TURBINE_BLOCK;
    public static Block CLOUD_CELL_BLOCK;
    public static Block QUARRY_CONTROLLER_BLOCK;
    public static Block MARKER_BLOCK;
    public static Block TRASH_CAN_BLOCK;

    public static Item CLOUD_PIPE;
    public static Item CLOUD_CABLE;
    public static Item STRATUS_PANEL;
    public static Item CLOUD_TURBINE;
    public static Item CLOUD_CELL;
    public static Item QUARRY_CONTROLLER;
    public static Item MARKER;
    public static Item WRENCH;
    public static Item PIPE_FILTER;
    public static Item TRASH_CAN;
    public static Item SILK_TOUCH_AUGMENT;
    public static Item SPEED_AUGMENT_1;
    public static Item SPEED_AUGMENT_2;
    public static Item SPEED_AUGMENT_3;
    public static Item FORTUNE_AUGMENT_1;
    public static Item FORTUNE_AUGMENT_2;
    public static Item FORTUNE_AUGMENT_3;

    public static ItemGroup CLOUD_FRAME_ITEM_GROUP;

    // Block entity types and screen handlers
    private static BlockEntityType<QuarryControllerBlockEntity> quarryControllerBE;
    private static BlockEntityType<CloudCellBlockEntity> cloudCellBE;
    private static BlockEntityType<TrashCanBlockEntity> trashCanBE;
    private static ScreenHandlerType<QuarryControllerScreenHandler> quarryControllerScreenHandler;
    private static ScreenHandlerType<PipeFilterScreenHandler> pipeFilterScreenHandler;
    private static ScreenHandlerType<TrashCanScreenHandler> trashCanScreenHandler;

    public static Block getQuarryControllerBlock() {
        return QUARRY_CONTROLLER_BLOCK;
    }

    public static Block getStratusPanelBlock() {
        return STRATUS_PANEL_BLOCK;
    }

    public static Block getCloudTurbineBlock() {
        return CLOUD_TURBINE_BLOCK;
    }

    public static Block getCloudCellBlock() {
        return CLOUD_CELL_BLOCK;
    }

    public static Block getCloudPipeBlock() {
        return CLOUD_PIPE_BLOCK;
    }

    public static Block getCloudCableBlock() {
        return CLOUD_CABLE_BLOCK;
    }

    public static Item getMarker() {
        return MARKER;
    }

    public static Item getWrench() {
        return WRENCH;
    }

    public static Item getPipeFilter() {
        return PIPE_FILTER;
    }

    public static Item getSilkTouchAugment() {
        return SILK_TOUCH_AUGMENT;
    }

    public static Item getSpeedAugment(int level) {
        return switch(level) {
            case 1 -> SPEED_AUGMENT_1;
            case 2 -> SPEED_AUGMENT_2;
            case 3 -> SPEED_AUGMENT_3;
            default -> null;
        };
    }

    public static Item getFortuneAugment(int level) {
        return switch (level) {
            case 1 -> FORTUNE_AUGMENT_1;
            case 2 -> FORTUNE_AUGMENT_2;
            case 3 -> FORTUNE_AUGMENT_3;
            default -> null;
        };
    }

    public static BlockEntityType<QuarryControllerBlockEntity> getQuarryControllerBlockEntity() {
        return quarryControllerBE;
    }

    public static BlockEntityType<CloudCellBlockEntity> getCloudCellBlockEntity() {
        return cloudCellBE;
    }

    public static ScreenHandlerType<QuarryControllerScreenHandler> getQuarryControllerScreenHandler() {
        return quarryControllerScreenHandler;
    }

    public static ScreenHandlerType<PipeFilterScreenHandler> getPipeFilterScreenHandler() {
        return pipeFilterScreenHandler;
    }

    public static BlockEntityType<TrashCanBlockEntity> getTrashCanBlockEntity() {
        return trashCanBE;
    }

    public static ScreenHandlerType<TrashCanScreenHandler> getTrashCanScreenHandler() {
        return trashCanScreenHandler;
    }

    private static RegistryKey<Item> itemKey(Identifier id) {
        return RegistryKey.of(RegistryKeys.ITEM, id);
    }


    private static RegistryKey<Block> blockKey(Identifier id) {
        return RegistryKey.of(RegistryKeys.BLOCK, id);
    }

    public static void registerAll() {
        // Blocks
        CLOUD_PIPE_BLOCK = Registry.register(
            Registries.BLOCK,
            CLOUD_PIPE_ID,
            new TubeBlock(
                AbstractBlock.Settings.copy(Blocks.GLASS)
                    .strength(3.0f, 6.0f)  // Requires holding pickaxe to mine
                    .requiresTool()
                    .registryKey(blockKey(CLOUD_PIPE_ID))
            )
        );

        CLOUD_CABLE_BLOCK = Registry.register(
            Registries.BLOCK,
            CLOUD_CABLE_ID,
            new CloudCableBlock(
                AbstractBlock.Settings.copy(Blocks.GLASS)
                    .strength(3.0f, 6.0f)  // Requires holding pickaxe to mine
                    .requiresTool()
                    .registryKey(blockKey(CLOUD_CABLE_ID))
            )
        );

        STRATUS_PANEL_BLOCK = Registry.register(
            Registries.BLOCK,
            STRATUS_PANEL_ID,
            new StratusPanelBlock(
                AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)
                    .requiresTool()  // Requires pickaxe (iron-level)
                    .registryKey(blockKey(STRATUS_PANEL_ID))
            )
        );

        CLOUD_TURBINE_BLOCK = Registry.register(
            Registries.BLOCK,
            CLOUD_TURBINE_ID,
            new CloudTurbineBlock(
                AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)
                    .requiresTool()  // Requires pickaxe (iron-level)
                    .registryKey(blockKey(CLOUD_TURBINE_ID))
            )
        );

        CLOUD_CELL_BLOCK = Registry.register(
            Registries.BLOCK,
            CLOUD_CELL_ID,
            new CloudCellBlock(
                AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)
                    .requiresTool()  // Requires pickaxe (iron-level)
                    .registryKey(blockKey(CLOUD_CELL_ID))
            )
        );

        QUARRY_CONTROLLER_BLOCK = Registry.register(
            Registries.BLOCK,
            CONTROLLER_ID,
            new QuarryControllerBlock(
                AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)
                    .requiresTool()  // Requires pickaxe (iron-level)
                    .registryKey(blockKey(CONTROLLER_ID))
            )
        );

        MARKER_BLOCK = Registry.register(
            Registries.BLOCK,
            MARKER_ID,
            new MarkerBlock(
                AbstractBlock.Settings.copy(Blocks.GOLD_BLOCK)
                    .nonOpaque()
                    .registryKey(blockKey(MARKER_ID))
            )
        );

        TRASH_CAN_BLOCK = Registry.register(
            Registries.BLOCK,
            TRASH_CAN_ID,
            new TrashCanBlock(
                AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)
                    .requiresTool()  // Requires pickaxe (iron-level)
                    .registryKey(blockKey(TRASH_CAN_ID))
            )
        );

        // Items
        CLOUD_PIPE = Registry.register(
            Registries.ITEM,
            CLOUD_PIPE_ID,
            new BlockItem(CLOUD_PIPE_BLOCK, new Item.Settings().registryKey(itemKey(CLOUD_PIPE_ID)))
        );

        CLOUD_CABLE = Registry.register(
            Registries.ITEM,
            CLOUD_CABLE_ID,
            new BlockItem(CLOUD_CABLE_BLOCK, new Item.Settings().registryKey(itemKey(CLOUD_CABLE_ID)))
        );

        STRATUS_PANEL = Registry.register(
            Registries.ITEM,
            STRATUS_PANEL_ID,
            new BlockItem(STRATUS_PANEL_BLOCK, new Item.Settings().registryKey(itemKey(STRATUS_PANEL_ID)))
        );

        CLOUD_TURBINE = Registry.register(
            Registries.ITEM,
            CLOUD_TURBINE_ID,
            new BlockItem(CLOUD_TURBINE_BLOCK, new Item.Settings().registryKey(itemKey(CLOUD_TURBINE_ID)))
        );

        CLOUD_CELL = Registry.register(
            Registries.ITEM,
            CLOUD_CELL_ID,
            new BlockItem(CLOUD_CELL_BLOCK, new Item.Settings().registryKey(itemKey(CLOUD_CELL_ID)))
        );

        QUARRY_CONTROLLER = Registry.register(
            Registries.ITEM,
            CONTROLLER_ID,
            new BlockItem(QUARRY_CONTROLLER_BLOCK, new Item.Settings().registryKey(itemKey(CONTROLLER_ID)))
        );

        MARKER = Registry.register(
            Registries.ITEM,
            MARKER_ID,
            new BlockItem(MARKER_BLOCK, new Item.Settings().registryKey(itemKey(MARKER_ID)))
        );

        TRASH_CAN = Registry.register(
            Registries.ITEM,
            TRASH_CAN_ID,
            new BlockItem(TRASH_CAN_BLOCK, new Item.Settings().registryKey(itemKey(TRASH_CAN_ID)))
        );

        WRENCH = Registry.register(
            Registries.ITEM,
            WRENCH_ID,
            new WrenchItem(new Item.Settings().maxCount(1).registryKey(itemKey(WRENCH_ID)))
        );

        PIPE_FILTER = Registry.register(
            Registries.ITEM,
            PIPE_FILTER_ID,
            new PipeFilterItem(new Item.Settings().registryKey(itemKey(PIPE_FILTER_ID)))
        );

        SILK_TOUCH_AUGMENT = Registry.register(
            Registries.ITEM,
            SILK_TOUCH_AUGMENT_ID,
            new SilkTouchAugmentItem(new Item.Settings().registryKey(itemKey(SILK_TOUCH_AUGMENT_ID)))
        );

        SPEED_AUGMENT_1 = Registry.register(
            Registries.ITEM,
            SPEED_AUGMENT_1_ID,
            new SpeedAugmentItem(1, new Item.Settings().registryKey(itemKey(SPEED_AUGMENT_1_ID)))
        );

        SPEED_AUGMENT_2 = Registry.register(
            Registries.ITEM,
            SPEED_AUGMENT_2_ID,
            new SpeedAugmentItem(2, new Item.Settings().registryKey(itemKey(SPEED_AUGMENT_2_ID)))
        );

        SPEED_AUGMENT_3 = Registry.register(
            Registries.ITEM,
            SPEED_AUGMENT_3_ID,
            new SpeedAugmentItem(3, new Item.Settings().registryKey(itemKey(SPEED_AUGMENT_3_ID)))
        );

        FORTUNE_AUGMENT_1 = Registry.register(
            Registries.ITEM,
            FORTUNE_AUGMENT_1_ID,
            new FortuneAugmentItem(1, new Item.Settings().registryKey(itemKey(FORTUNE_AUGMENT_1_ID)))
        );

        FORTUNE_AUGMENT_2 = Registry.register(
            Registries.ITEM,
            FORTUNE_AUGMENT_2_ID,
            new FortuneAugmentItem(2, new Item.Settings().registryKey(itemKey(FORTUNE_AUGMENT_2_ID)))
        );

        FORTUNE_AUGMENT_3 = Registry.register(
            Registries.ITEM,
            FORTUNE_AUGMENT_3_ID,
            new FortuneAugmentItem(3, new Item.Settings().registryKey(itemKey(FORTUNE_AUGMENT_3_ID)))
        );

        // Creative tab (shows all CloudFrame content in creative inventory)
        CLOUD_FRAME_ITEM_GROUP = Registry.register(
            Registries.ITEM_GROUP,
            Identifier.of(CloudFrameFabric.MOD_ID, "cloudframe"),
            FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.cloudframe"))
                .icon(() -> new ItemStack(QUARRY_CONTROLLER))
                .entries((context, entries) -> {
                    entries.add(CLOUD_PIPE);
                    entries.add(CLOUD_CABLE);
                    entries.add(PIPE_FILTER);
                    entries.add(MARKER);
                    entries.add(WRENCH);
                    entries.add(QUARRY_CONTROLLER);
                    entries.add(TRASH_CAN);
                    entries.add(STRATUS_PANEL);
                    entries.add(CLOUD_TURBINE);
                    entries.add(CLOUD_CELL);
                    entries.add(SILK_TOUCH_AUGMENT);
                    entries.add(SPEED_AUGMENT_1);
                    entries.add(SPEED_AUGMENT_2);
                    entries.add(SPEED_AUGMENT_3);
                    entries.add(FORTUNE_AUGMENT_1);
                    entries.add(FORTUNE_AUGMENT_2);
                    entries.add(FORTUNE_AUGMENT_3);
                })
                .build()
        );

        
        // Register block entities and screen handlers
        quarryControllerBE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(CloudFrameFabric.MOD_ID, "quarry_controller"),
            FabricBlockEntityTypeBuilder.create(QuarryControllerBlockEntity::new, QUARRY_CONTROLLER_BLOCK).build()
        );

        cloudCellBE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(CloudFrameFabric.MOD_ID, "cloud_cell"),
            FabricBlockEntityTypeBuilder.create(CloudCellBlockEntity::new, CLOUD_CELL_BLOCK).build()
        );

        trashCanBE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(CloudFrameFabric.MOD_ID, "trash_can"),
            FabricBlockEntityTypeBuilder.create(TrashCanBlockEntity::new, TRASH_CAN_BLOCK).build()
        );

        // Optional: expose Cloud Cells to external energy mods (soft dependency via reflection).
        EnergyInterop.tryRegisterCloudCell(cloudCellBE);

        quarryControllerScreenHandler = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(CloudFrameFabric.MOD_ID, "quarry_controller"),
            new ScreenHandlerType<>(QuarryControllerScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
        );

        pipeFilterScreenHandler = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(CloudFrameFabric.MOD_ID, "pipe_filter"),
            new ScreenHandlerType<>(PipeFilterScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
        );

        trashCanScreenHandler = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(CloudFrameFabric.MOD_ID, "trash_can"),
            new ScreenHandlerType<>(TrashCanScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
        );

        // Startup sanity: log what actually got registered.
        try {
            var debug = dev.cloudframe.common.util.DebugManager.get(CloudFrameContent.class);
            debug.log("register", "Registered blocks: pipe=" + Registries.BLOCK.getId(CLOUD_PIPE_BLOCK) + " cable=" + Registries.BLOCK.getId(CLOUD_CABLE_BLOCK) + " stratus=" + Registries.BLOCK.getId(STRATUS_PANEL_BLOCK) + " turbine=" + Registries.BLOCK.getId(CLOUD_TURBINE_BLOCK) + " cell=" + Registries.BLOCK.getId(CLOUD_CELL_BLOCK) + " controller=" + Registries.BLOCK.getId(QUARRY_CONTROLLER_BLOCK));
            debug.log("register", "Registered items: pipe=" + Registries.ITEM.getId(CLOUD_PIPE) + " cable=" + Registries.ITEM.getId(CLOUD_CABLE) + " stratus=" + Registries.ITEM.getId(STRATUS_PANEL) + " turbine=" + Registries.ITEM.getId(CLOUD_TURBINE) + " cell=" + Registries.ITEM.getId(CLOUD_CELL) + " controller=" + Registries.ITEM.getId(QUARRY_CONTROLLER) + " marker=" + Registries.ITEM.getId(MARKER) + " wrench=" + Registries.ITEM.getId(WRENCH) + " silk=" + Registries.ITEM.getId(SILK_TOUCH_AUGMENT) + " speed1=" + Registries.ITEM.getId(SPEED_AUGMENT_1) + " speed2=" + Registries.ITEM.getId(SPEED_AUGMENT_2) + " speed3=" + Registries.ITEM.getId(SPEED_AUGMENT_3) + " fortune1=" + Registries.ITEM.getId(FORTUNE_AUGMENT_1) + " fortune2=" + Registries.ITEM.getId(FORTUNE_AUGMENT_2) + " fortune3=" + Registries.ITEM.getId(FORTUNE_AUGMENT_3));
        } catch (Exception ignored) {
            // Avoid hard-failing startup just for logging.
        }
    }
}
