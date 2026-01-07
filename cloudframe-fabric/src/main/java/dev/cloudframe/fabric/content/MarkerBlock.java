package dev.cloudframe.fabric.content;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Placeable marker block - small visual indicator placed on ground to mark quarry frame corners.
 * 4 markers must be placed on the same Y level, then activated with wrench to create frame.
 */
public class MarkerBlock extends Block {

    // Keep this in sync with the wrench activation listener.
    private static final int SEARCH_RADIUS = 16;

    // Shape matching Blockbench model: base [4,0,4]-[12,1,12], middle [6,0,6]-[10,2,10], pillar [7,2,7]-[9,8,9]
    // Overall bounding box: [4,0,4] to [12,8,12]
    private static final VoxelShape SHAPE = Block.createCuboidShape(4, 0, 4, 12, 8, 12);

    public MarkerBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE; // Use same shape for collision so players can walk through and see the box
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);

        if (!(world instanceof ServerWorld sw)) return;
        if (!(placer instanceof ServerPlayerEntity player)) return;

        int found = countNearbyMarkers(sw, pos);

        player.sendMessage(
            Text.literal(
                "§aMarker placed! §7To create a valid frame for a quarry, right click a marker with a wrench! "
                    + "Need 4 markers (box shape). Found: " + found
            ),
            false
        );

        if (found > 4) {
            player.sendMessage(
                Text.literal("§cToo many markers nearby (" + found + "). Only 4 can be used; remove extras."),
                false
            );
        }
    }

    private static int countNearbyMarkers(ServerWorld world, BlockPos center) {
        if (world == null || center == null) return 0;

        int count = 0;
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -SEARCH_RADIUS; y <= SEARCH_RADIUS; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    BlockPos p = center.add(x, y, z);
                    BlockState s = world.getBlockState(p);
                    if (s != null && s.getBlock() == CloudFrameContent.MARKER_BLOCK) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
