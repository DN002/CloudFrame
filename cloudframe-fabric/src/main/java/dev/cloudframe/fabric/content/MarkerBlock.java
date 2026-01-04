package dev.cloudframe.fabric.content;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * Placeable marker block - small visual indicator placed on ground to mark quarry frame corners.
 * 4 markers must be placed on the same Y level, then activated with wrench to create frame.
 */
public class MarkerBlock extends Block {

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
}
