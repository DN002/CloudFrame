package dev.cloudframe.fabric.mixin;

import dev.cloudframe.fabric.CloudFrameFabric;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class ServerWorldSetBlockStateMixin {

    @Inject(
        // ServerWorld#setBlockState(BlockPos, BlockState, int)
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z",
        at = @At("RETURN"),
        require = 0
    )
    private void cloudframe$onSetBlockState3(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<Boolean> cir) {
        handleSetBlockState(pos, state, cir);
    }

    @Inject(
        // ServerWorld#setBlockState(BlockPos, BlockState, int, int)
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
        at = @At("RETURN"),
        require = 0
    )
    private void cloudframe$onSetBlockState4(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        handleSetBlockState(pos, state, cir);
    }

    private void handleSetBlockState(BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (pos == null || state == null || cir == null) return;
        if (!cir.getReturnValue()) return;
        if (state.isAir()) return;

        CloudFrameFabric inst = CloudFrameFabric.instance();
        if (inst == null) return;
        Object qm = inst.getQuarryManager();
        if (qm == null) return;

        World self = (World) (Object) this;
        if (!(self instanceof ServerWorld world)) return;

        // Call into cloudframe-common if the method exists (it may be missing if Gradle is still
        // resolving an older local-Maven jar).
        try {
            qm.getClass()
                .getMethod("markDirtyBlock", Object.class, int.class, int.class, int.class)
                .invoke(qm, world, pos.getX(), pos.getY(), pos.getZ());
        } catch (ReflectiveOperationException ignored) {
            // Best-effort.
        }
    }
}
