package dev.cloudframe.fabric.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Placeholder battery block (no GUI).
 * Stores a large amount of CFE to bridge a Cloud Cable network.
 */
public class CloudCellBlock extends BlockWithEntity {

    public static final MapCodec<CloudCellBlock> CODEC = createCodec(CloudCellBlock::new);

    public CloudCellBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CloudCellBlockEntity(pos, state);
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
