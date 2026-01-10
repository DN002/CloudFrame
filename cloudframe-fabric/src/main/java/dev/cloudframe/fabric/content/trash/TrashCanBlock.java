package dev.cloudframe.fabric.content.trash;

import com.mojang.serialization.MapCodec;
import dev.cloudframe.fabric.CloudFrameFabric;
import dev.cloudframe.fabric.content.CloudFrameContent;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.block.ShapeContext;

public class TrashCanBlock extends BlockWithEntity {

    public static final MapCodec<TrashCanBlock> CODEC = createCodec(TrashCanBlock::new);
    
    // Collision shape: from [2, 0, 2] to [14, 14, 14] (scaled to 0-1 range: [0.125, 0, 0.125] to [0.875, 0.875, 0.875])
    private static final VoxelShape COLLISION_SHAPE = VoxelShapes.cuboid(0.125, 0, 0.125, 0.875, 0.875, 0.875);
    private static final VoxelShape OUTLINE_SHAPE = COLLISION_SHAPE;

    public TrashCanBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TrashCanBlockEntity(pos, state);
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return COLLISION_SHAPE;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINE_SHAPE;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity)) return ActionResult.PASS;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof TrashCanBlockEntity tbe) {
            player.openHandledScreen(tbe);
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        // No ticking needed.
        return null;
    }

    @Override
    protected boolean hasComparatorOutput(BlockState state) {
        return false;
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        super.onStateReplaced(state, world, pos, moved);

        // Drop any pipe filters connected to this trash can
        CloudFrameFabric instance = CloudFrameFabric.instance();
        if (instance == null || instance.getPipeFilterManager() == null) {
            return;
        }

        GlobalPos trashPos = GlobalPos.create(world.getRegistryKey(), pos.toImmutable());

        // Check all 6 sides for pipe filters (trash can accepts pipes from any direction)
        for (int dirIndex = 0; dirIndex < 6; dirIndex++) {
            if (instance.getPipeFilterManager().hasFilter(trashPos, dirIndex)) {
                var filterState = instance.getPipeFilterManager().get(trashPos, dirIndex);
                instance.getPipeFilterManager().removeFilter(trashPos, dirIndex);

                // Create filter item with saved configuration
                ItemStack drop = new ItemStack(CloudFrameContent.getPipeFilter(), 1);
                dev.cloudframe.fabric.pipes.filter.PipeFilterItem.writeItemConfigFromFilterState(drop, filterState);

                // Drop the filter at the trash can's location
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
            }
        }
    }
}
