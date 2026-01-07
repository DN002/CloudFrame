package dev.cloudframe.fabric.content;

import dev.cloudframe.fabric.CloudFrameFabric;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.Inventory;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.block.ShapeContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ItemScatterer;

import dev.cloudframe.fabric.pipes.filter.PipeFilterScreenHandler;
import dev.cloudframe.fabric.pipes.filter.PipeFilterItem;
import dev.cloudframe.fabric.util.ClickSideUtil;

import java.util.HashMap;
import java.util.Map;

public class TubeBlock extends Block {

    public static final BooleanProperty NORTH = Properties.NORTH;
    public static final BooleanProperty SOUTH = Properties.SOUTH;
    public static final BooleanProperty EAST = Properties.EAST;
    public static final BooleanProperty WEST = Properties.WEST;
    public static final BooleanProperty UP = Properties.UP;
    public static final BooleanProperty DOWN = Properties.DOWN;

    public static final BooleanProperty FILTER_NORTH = BooleanProperty.of("filter_north");
    public static final BooleanProperty FILTER_SOUTH = BooleanProperty.of("filter_south");
    public static final BooleanProperty FILTER_EAST = BooleanProperty.of("filter_east");
    public static final BooleanProperty FILTER_WEST = BooleanProperty.of("filter_west");
    public static final BooleanProperty FILTER_UP = BooleanProperty.of("filter_up");
    public static final BooleanProperty FILTER_DOWN = BooleanProperty.of("filter_down");

    // Cached shapes by a 6-bit mask (N,S,E,W,U,D)
    private static final Map<Integer, VoxelShape> SHAPE_CACHE = new HashMap<>();

    private static final VoxelShape CORE = VoxelShapes.cuboid(
        6.0 / 16.0, 6.0 / 16.0, 6.0 / 16.0,
        10.0 / 16.0, 10.0 / 16.0, 10.0 / 16.0
    );

    private static final VoxelShape ARM_NORTH = VoxelShapes.cuboid(
        6.0 / 16.0, 6.0 / 16.0, 0.0 / 16.0,
        10.0 / 16.0, 10.0 / 16.0, 6.0 / 16.0
    );
    private static final VoxelShape ARM_SOUTH = VoxelShapes.cuboid(
        6.0 / 16.0, 6.0 / 16.0, 10.0 / 16.0,
        10.0 / 16.0, 10.0 / 16.0, 16.0 / 16.0
    );
    private static final VoxelShape ARM_WEST = VoxelShapes.cuboid(
        0.0 / 16.0, 6.0 / 16.0, 6.0 / 16.0,
        6.0 / 16.0, 10.0 / 16.0, 10.0 / 16.0
    );
    private static final VoxelShape ARM_EAST = VoxelShapes.cuboid(
        10.0 / 16.0, 6.0 / 16.0, 6.0 / 16.0,
        16.0 / 16.0, 10.0 / 16.0, 10.0 / 16.0
    );
    private static final VoxelShape ARM_DOWN = VoxelShapes.cuboid(
        6.0 / 16.0, 0.0 / 16.0, 6.0 / 16.0,
        10.0 / 16.0, 6.0 / 16.0, 10.0 / 16.0
    );
    private static final VoxelShape ARM_UP = VoxelShapes.cuboid(
        6.0 / 16.0, 10.0 / 16.0, 6.0 / 16.0,
        10.0 / 16.0, 16.0 / 16.0, 10.0 / 16.0
    );

    public TubeBlock(Settings settings) {
        super(settings);
        this.setDefaultState(
            this.stateManager.getDefaultState()
                .with(NORTH, false)
                .with(SOUTH, false)
                .with(EAST, false)
                .with(WEST, false)
                .with(UP, false)
                .with(DOWN, false)
                .with(FILTER_NORTH, false)
                .with(FILTER_SOUTH, false)
                .with(FILTER_EAST, false)
                .with(FILTER_WEST, false)
                .with(FILTER_UP, false)
                .with(FILTER_DOWN, false)
        );
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        // Let the wrench handle connection toggling.
        ItemStack inHand = player.getMainHandStack();
        if (inHand != null && !inHand.isEmpty() && CloudFrameContent.getWrench() != null && inHand.isOf(CloudFrameContent.getWrench())) {
            return ActionResult.PASS;
        }

        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }

        CloudFrameFabric instance = CloudFrameFabric.instance();
        if (instance == null || instance.getPipeFilterManager() == null) {
            return ActionResult.PASS;
        }

        Direction side = ClickSideUtil.getClickedArmSide(serverPlayer, pos, hit.getSide());
        int sideIndex = ClickSideUtil.toDirIndex(side);
        GlobalPos pipePos = GlobalPos.create(world.getRegistryKey(), pos.toImmutable());

        // Never allow interacting with filter attachments on a side that is currently disconnected.
        if (isSideDisabled(world, pos, side)) {
            serverPlayer.sendMessage(Text.literal("§7That side is disconnected."), true);
            return ActionResult.SUCCESS;
        }

        boolean hasFilter = instance.getPipeFilterManager().hasFilter(pipePos, sideIndex);

        // Empty hand: open/remove existing filter.
        if (inHand == null || inHand.isEmpty()) {
            if (!hasFilter) {
                return ActionResult.PASS;
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

        // Holding a pipe filter item:
        // - Right-click: install only (no GUI)
        if (CloudFrameContent.getPipeFilter() != null && inHand.isOf(CloudFrameContent.getPipeFilter())) {
            // Only allow attaching to a real inventory connection.
            BlockPos neighbor = pos.offset(side);
            BlockEntity be = world.getBlockEntity(neighbor);
            if (!(be instanceof Inventory)) {
                serverPlayer.sendMessage(Text.literal("§7No inventory on that side."), true);
                return ActionResult.SUCCESS;
            }

            if (!hasFilter) {
                instance.getPipeFilterManager().getOrCreate(pipePos, sideIndex);

                // Apply configuration from the item being installed.
                var cfg = PipeFilterItem.readItemConfig(inHand);
                instance.getPipeFilterManager().setMode(pipePos, sideIndex, cfg.mode);
                instance.getPipeFilterManager().setItems(pipePos, sideIndex, cfg.items);

                if (!serverPlayer.isCreative()) {
                    inHand.decrement(1);
                }
                refreshConnections(world, pos);
                serverPlayer.sendMessage(Text.literal("§7Pipe filter installed. Left click to remove. Shift-right-click anywhere with a filter item to configure."), true);
                return ActionResult.SUCCESS;
            }

            serverPlayer.sendMessage(Text.literal("§7Pipe filter already installed. Left click to remove. Shift-right-click anywhere with a filter item to configure."), true);
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN, FILTER_NORTH, FILTER_SOUTH, FILTER_EAST, FILTER_WEST, FILTER_UP, FILTER_DOWN);
    }

    @Override
    public BlockState getPlacementState(net.minecraft.item.ItemPlacementContext ctx) {
        BlockState state = this.getDefaultState();
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        return updateConnections(state, world, pos);
    }

    @Override
    public BlockState getStateForNeighborUpdate(
        BlockState state,
        WorldView world,
        net.minecraft.world.tick.ScheduledTickView tickView,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        net.minecraft.util.math.random.Random random
    ) {
        // Only update the one side that changed.
        boolean connected = shouldConnect(world, pos, direction, neighborPos, neighborState);
        return switch (direction) {
            case NORTH -> state.with(NORTH, connected);
            case SOUTH -> state.with(SOUTH, connected);
            case EAST -> state.with(EAST, connected);
            case WEST -> state.with(WEST, connected);
            case UP -> state.with(UP, connected);
            case DOWN -> state.with(DOWN, connected);
        };
    }

    private BlockState updateConnections(BlockState state, WorldAccess world, BlockPos pos) {
        BlockState northState = world.getBlockState(pos.north());
        BlockState southState = world.getBlockState(pos.south());
        BlockState eastState = world.getBlockState(pos.east());
        BlockState westState = world.getBlockState(pos.west());
        BlockState upState = world.getBlockState(pos.up());
        BlockState downState = world.getBlockState(pos.down());

        return state
            .with(NORTH, shouldConnect(world, pos, Direction.NORTH, pos.north(), northState))
            .with(SOUTH, shouldConnect(world, pos, Direction.SOUTH, pos.south(), southState))
            .with(EAST, shouldConnect(world, pos, Direction.EAST, pos.east(), eastState))
            .with(WEST, shouldConnect(world, pos, Direction.WEST, pos.west(), westState))
            .with(UP, shouldConnect(world, pos, Direction.UP, pos.up(), upState))
            .with(DOWN, shouldConnect(world, pos, Direction.DOWN, pos.down(), downState))
            .with(FILTER_NORTH, hasFilterOnConnectedInventory(world, pos, Direction.NORTH, pos.north(), northState))
            .with(FILTER_SOUTH, hasFilterOnConnectedInventory(world, pos, Direction.SOUTH, pos.south(), southState))
            .with(FILTER_EAST, hasFilterOnConnectedInventory(world, pos, Direction.EAST, pos.east(), eastState))
            .with(FILTER_WEST, hasFilterOnConnectedInventory(world, pos, Direction.WEST, pos.west(), westState))
            .with(FILTER_UP, hasFilterOnConnectedInventory(world, pos, Direction.UP, pos.up(), upState))
            .with(FILTER_DOWN, hasFilterOnConnectedInventory(world, pos, Direction.DOWN, pos.down(), downState));
    }

    private static boolean hasFilterOnConnectedInventory(WorldAccess world, BlockPos pipePos, Direction side, BlockPos neighborPos, BlockState neighborState) {
        if (world == null || pipePos == null || side == null) return false;

        // Filters are only meaningful on enabled inventory connections.
        if (isSideDisabled(world, pipePos, side)) return false;
        if (neighborState == null || !neighborState.hasBlockEntity()) return false;
        var be = world.getBlockEntity(neighborPos);
        if (!(be instanceof Inventory)) return false;

        return hasFilter(world, pipePos, side);
    }

    private static boolean hasFilter(WorldView world, BlockPos pipePos, Direction side) {
        if (!(world instanceof World w)) return false;
        CloudFrameFabric instance = CloudFrameFabric.instance();
        if (instance == null || instance.getPipeFilterManager() == null) return false;
        if (w.getServer() == null) return false;

        int dirIndex = switch (side) {
            case EAST -> 0;
            case WEST -> 1;
            case UP -> 2;
            case DOWN -> 3;
            case SOUTH -> 4;
            case NORTH -> 5;
        };

        return instance.getPipeFilterManager().hasFilter(GlobalPos.create(w.getRegistryKey(), pipePos.toImmutable()), dirIndex);
    }

    private boolean shouldConnect(WorldView world, BlockPos pipePos, Direction dirToNeighbor, BlockPos neighborPos, BlockState neighborState) {
        if (neighborState == null) return false;

        // Connect to other tubes (user-toggleable per side).
        if (neighborState.getBlock() instanceof TubeBlock) {
            return !isSideDisabled(world, pipePos, dirToNeighbor)
                && !isSideDisabled(world, neighborPos, dirToNeighbor.getOpposite());
        }

        // Connect to quarry controller.
        if (CloudFrameContent.getQuarryControllerBlock() != null
            && neighborState.isOf(CloudFrameContent.getQuarryControllerBlock())) {
            return !isSideDisabled(world, pipePos, dirToNeighbor);
        }

        // Connect to inventories (chests, hoppers, etc.).
        if (neighborState.hasBlockEntity()) {
            var be = world.getBlockEntity(neighborPos);
            if (be instanceof Inventory) {
                return !isSideDisabled(world, pipePos, dirToNeighbor);
            }
        }

        return false;
    }

    public static boolean isSideDisabled(WorldView world, BlockPos pipePos, Direction side) {
        if (!(world instanceof World w)) return false;

        CloudFrameFabric instance = CloudFrameFabric.instance();
        if (instance == null || instance.getPipeManager() == null) return false;
        MinecraftServer srv = w.getServer();
        if (srv == null) return false;

        var node = instance.getPipeManager().getPipe(GlobalPos.create(w.getRegistryKey(), pipePos.toImmutable()));
        if (node == null) return false;

        int dirIndex = switch (side) {
            case EAST -> 0;
            case WEST -> 1;
            case UP -> 2;
            case DOWN -> 3;
            case SOUTH -> 4;
            case NORTH -> 5;
        };
        return node.isInventorySideDisabled(dirIndex);
    }

    public static void refreshConnections(World world, BlockPos pos) {
        if (world == null || pos == null) return;
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof TubeBlock tube)) return;

        BlockState updated = tube.updateConnections(state, world, pos);
        if (updated != state) {
            world.setBlockState(pos, updated, Block.NOTIFY_ALL);
        }
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, net.minecraft.world.BlockView world, BlockPos pos, ShapeContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, net.minecraft.world.BlockView world, BlockPos pos, ShapeContext context) {
        return shapeFor(state);
    }

    private static VoxelShape shapeFor(BlockState state) {
        int mask = 0;
        if (state.get(NORTH)) mask |= 1;
        if (state.get(SOUTH)) mask |= 2;
        if (state.get(EAST)) mask |= 4;
        if (state.get(WEST)) mask |= 8;
        if (state.get(UP)) mask |= 16;
        if (state.get(DOWN)) mask |= 32;

        VoxelShape cached = SHAPE_CACHE.get(mask);
        if (cached != null) return cached;

        VoxelShape out = CORE;
        if ((mask & 1) != 0) out = VoxelShapes.union(out, ARM_NORTH);
        if ((mask & 2) != 0) out = VoxelShapes.union(out, ARM_SOUTH);
        if ((mask & 4) != 0) out = VoxelShapes.union(out, ARM_EAST);
        if ((mask & 8) != 0) out = VoxelShapes.union(out, ARM_WEST);
        if ((mask & 16) != 0) out = VoxelShapes.union(out, ARM_UP);
        if ((mask & 32) != 0) out = VoxelShapes.union(out, ARM_DOWN);

        SHAPE_CACHE.put(mask, out);
        return out;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);

        if (world.isClient()) return;

        CloudFrameFabric instance = CloudFrameFabric.instance();
        if (instance == null || instance.getPipeManager() == null) return;

        instance.getPipeManager().addPipe(GlobalPos.create(world.getRegistryKey(), pos.toImmutable()));
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        super.onStateReplaced(state, world, pos, moved);

        CloudFrameFabric instance = CloudFrameFabric.instance();
        if (instance == null || instance.getPipeManager() == null) return;

        instance.getPipeManager().removePipe(GlobalPos.create(world.getRegistryKey(), pos.toImmutable()));

        if (instance.getPipeFilterManager() != null) {
            instance.getPipeFilterManager().removeAllAt(GlobalPos.create(world.getRegistryKey(), pos.toImmutable()));
        }
    }
}
