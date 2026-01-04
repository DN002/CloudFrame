package dev.cloudframe.fabric.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.util.DebugManager;
import dev.cloudframe.fabric.CloudFrameFabric;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public final class CloudFrameCommands {

    private CloudFrameCommands() {
    }

    private static final Debug debug = DebugManager.get(CloudFrameCommands.class);

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        debug.log("register", "Registering /cloudframe root command");
        try {
            var node = root();
            var registered = dispatcher.register(node);
            debug.log("register", "Command registered successfully. Node children: " + registered.getChildren().size());
            debug.log("register", "Command registered. Children count=" + registered.getChildren().size());

            // Log the subcommands
            for (var child : registered.getChildren()) {
                debug.log("register", "  - subcommand: " + child.getName());
            }
        } catch (Exception ex) {
            debug.log("register", "EXCEPTION during command registration: " + ex.getMessage());
            CloudFrameFabric.LOGGER.error("[CloudFrame] Exception during command registration", ex);
            ex.printStackTrace();
        }
    }

    public static LiteralArgumentBuilder<ServerCommandSource> root() {
        return literal("cloudframe")
            .executes(ctx -> {
                // Default: show help when just "/cloudframe" is typed
                ServerCommandSource source = ctx.getSource();
                debug.log("root", "Root command executed by source=" + source.getName());
                source.sendMessage(Text.literal("CloudFrame commands:"));
                source.sendMessage(Text.literal("- /cloudframe help"));
                source.sendMessage(Text.literal("- /cloudframe give <type> [player] (OP only)"));
                source.sendMessage(Text.literal("Give types: marker, wrench, pipe, cable, controller, silk, speed1, speed2, speed3"));
                return 1;
            })
            .then(
                literal("help")
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        debug.log("help", "Help subcommand executed by source=" + source.getName());
                        source.sendMessage(Text.literal("CloudFrame commands:"));
                        source.sendMessage(Text.literal("- /cloudframe help"));
                        source.sendMessage(Text.literal("- /cloudframe give <type> [player] (OP only)"));
                        source.sendMessage(Text.literal("Give types: marker, wrench, pipe, cable, controller, silk, speed1, speed2, speed3"));
                        return 1;
                    })
            )
            .then(CloudFrameGiveCommand.giveNode());
    }
}
