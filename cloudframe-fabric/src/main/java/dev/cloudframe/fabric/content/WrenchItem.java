package dev.cloudframe.fabric.content;

import dev.cloudframe.common.quarry.Quarry;
import dev.cloudframe.common.util.Region;
import dev.cloudframe.fabric.CloudFrameFabric;
import dev.cloudframe.fabric.quarry.controller.QuarryControllerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;

public class WrenchItem extends Item {

    public WrenchItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getWorld().isClient()) {
            return ActionResult.SUCCESS;
        }

        PlayerEntity player = context.getPlayer();
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }

        BlockPos clickedPos = context.getBlockPos();
        CloudFrameFabric instance = CloudFrameFabric.instance();
        if (instance == null) {
            return ActionResult.PASS;
        }

        if (!(context.getWorld() instanceof ServerWorld world)) {
            return ActionResult.PASS;
        }

        // Check if clicking on a cloud pipe
        if (CloudFrameContent.getTubeBlock() != null && context.getWorld().getBlockState(clickedPos).isOf(CloudFrameContent.getTubeBlock())) {
            if (instance.getPipeManager() == null) return ActionResult.PASS;

            var pipeNode = instance.getPipeManager().getPipe(GlobalPos.create(world.getRegistryKey(), clickedPos.toImmutable()));
            if (pipeNode == null) return ActionResult.PASS;

            // Determine which direction was clicked
            int dirIndex = getClickedDirection(context);
            if (dirIndex < 0) return ActionResult.PASS;

            pipeNode.toggleInventorySideDisabled(dirIndex);
            
            // Rebuild pipe network to apply changes immediately
            instance.getPipeManager().rebuildAll();
            
            instance.getPipeManager().saveAll();
            
            // Give player feedback
            boolean disabled = pipeNode.isInventorySideDisabled(dirIndex);
            String dirName = switch (dirIndex) {
                case 0 -> "East";
                case 1 -> "West";
                case 2 -> "Up";
                case 3 -> "Down";
                case 4 -> "South";
                case 5 -> "North";
                default -> "Unknown";
            };
            serverPlayer.sendMessage(Text.literal(
                "§7Cloud Pipe connection §f" + dirName + "§7: " + (disabled ? "§cDisabled" : "§aEnabled")
            ), true);
            
            return ActionResult.SUCCESS;
        }

        // Wrench activation of marker frames is handled by FabricWrenchMarkerActivationListener
        // Controller placement is handled by QuarryControllerBlock
        return ActionResult.PASS;
    }

    private static int getClickedDirection(ItemUsageContext context) {
        // Map hit side to direction index (0-5)
        return switch (context.getSide()) {
            case EAST -> 0;   // +X
            case WEST -> 1;   // -X
            case UP -> 2;     // +Y
            case DOWN -> 3;   // -Y
            case SOUTH -> 4;  // +Z
            case NORTH -> 5;  // -Z
        };
    }
}
