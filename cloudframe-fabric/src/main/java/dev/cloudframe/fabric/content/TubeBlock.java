package dev.cloudframe.fabric.content;

import dev.cloudframe.fabric.CloudFrameFabric;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.Inventory;
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

import java.util.HashMap;
import java.util.Map;

public class TubeBlock extends Block {

    public static final BooleanProperty NORTH = Properties.NORTH;
    public static final BooleanProperty SOUTH = Properties.SOUTH;
    public static final BooleanProperty EAST = Properties.EAST;
    public static final BooleanProperty WEST = Properties.WEST;
    public static final BooleanProperty UP = Properties.UP;
    public static final BooleanProperty DOWN = Properties.DOWN;

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
        );
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
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
        boolean connected = connectsTo(world, neighborPos, neighborState);
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
        return state
            .with(NORTH, connectsTo(world, pos.north(), world.getBlockState(pos.north())))
            .with(SOUTH, connectsTo(world, pos.south(), world.getBlockState(pos.south())))
            .with(EAST, connectsTo(world, pos.east(), world.getBlockState(pos.east())))
            .with(WEST, connectsTo(world, pos.west(), world.getBlockState(pos.west())))
            .with(UP, connectsTo(world, pos.up(), world.getBlockState(pos.up())))
            .with(DOWN, connectsTo(world, pos.down(), world.getBlockState(pos.down())));
    }

    private boolean connectsTo(WorldView world, BlockPos neighborPos, BlockState neighborState) {
        if (neighborState == null) return false;

        // Connect to other tubes.
        if (neighborState.getBlock() instanceof TubeBlock) return true;

        // Connect to quarry controller.
        if (CloudFrameContent.getQuarryControllerBlock() != null
            && neighborState.isOf(CloudFrameContent.getQuarryControllerBlock())) {
            return true;
        }

        // Connect to inventories (chests, hoppers, etc.).
        if (neighborState.hasBlockEntity()) {
            var be = world.getBlockEntity(neighborPos);
            return be instanceof Inventory;
        }

        return false;
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
    }
}
