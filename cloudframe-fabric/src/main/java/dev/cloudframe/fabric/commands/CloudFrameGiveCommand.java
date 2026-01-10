package dev.cloudframe.fabric.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.util.DebugManager;
import dev.cloudframe.fabric.CloudFrameFabric;
import dev.cloudframe.fabric.content.CloudFrameContent;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class CloudFrameGiveCommand {

    private CloudFrameGiveCommand() {
    }

    private static final Debug debug = DebugManager.get(CloudFrameGiveCommand.class);

    /**
     * Get the map of item types using custom registered items with textures.
     */
    private static Map<String, Item> getItemsMap() {
        Map<String, Item> items = new LinkedHashMap<>();
        // Primary naming
        items.put("pipe", CloudFrameContent.CLOUD_PIPE);
        items.put("pipe_filter", CloudFrameContent.getPipeFilter());
        items.put("filter", CloudFrameContent.getPipeFilter());
        items.put("cable", CloudFrameContent.CLOUD_CABLE);
        items.put("cloud_cable", CloudFrameContent.CLOUD_CABLE);
        items.put("stratus_panel", CloudFrameContent.STRATUS_PANEL);
        items.put("cloud_turbine", CloudFrameContent.CLOUD_TURBINE);
        items.put("cell", CloudFrameContent.CLOUD_CELL);
        items.put("cloud_cell", CloudFrameContent.CLOUD_CELL);

        items.put("trash_can", CloudFrameContent.TRASH_CAN);

        items.put("controller", CloudFrameContent.QUARRY_CONTROLLER);
        items.put("quarry_controller", CloudFrameContent.QUARRY_CONTROLLER);
        items.put("marker", CloudFrameContent.MARKER);
        items.put("wrench", CloudFrameContent.WRENCH);
        items.put("silk", CloudFrameContent.SILK_TOUCH_AUGMENT);
        items.put("speed1", CloudFrameContent.SPEED_AUGMENT_1);
        items.put("speed2", CloudFrameContent.SPEED_AUGMENT_2);
        items.put("speed3", CloudFrameContent.SPEED_AUGMENT_3);
        return items;
    }

    /**
     * OP-only subcommand: /cloudframe give <type> [player]
     */
    public static LiteralArgumentBuilder<ServerCommandSource> giveNode() {
        debug.log("register", "Building give subcommand node");

        return literal("give")
            .then(
                argument("type", StringArgumentType.word())
                    .suggests(CloudFrameGiveCommand::suggestTypes)
                    .executes(ctx -> {
                        String type = StringArgumentType.getString(ctx, "type");
                        ServerCommandSource source = ctx.getSource();

                        debug.log("give", "EXECUTING: Player " + source.getName() + " give type=" + type);

                        ServerPlayerEntity self;
                        try {
                            self = source.getPlayer();
                        } catch (Exception ex) {
                            source.sendMessage(Text.literal("This form must be run by a player. Use: /cloudframe give <type> <player>"));
                            debug.log("give", "Give failed: non-player source tried type=" + type);
                            debug.log("give", "Non-player source tried give");
                            return 0;
                        }

                        // Check OP via player permission level (tied to ops.json)
                        boolean isOp = self.hasPermissionLevel(2);
                        int permLevel = self.getPermissionLevel();
                        if (!isOp) {
                            source.sendMessage(Text.literal("You must be a server operator to use this command."));
                            debug.log("give", "Permission denied for " + source.getName() + " permLevel=" + permLevel);
                            return 0;
                        }
                        return executeGive(source, self, type);
                    })
                    .then(
                        argument("player", EntityArgumentType.player())
                            .executes(ctx -> {
                                String type = StringArgumentType.getString(ctx, "type");
                                ServerCommandSource source = ctx.getSource();
                                debug.log("give", "EXECUTING: Player " + source.getName() + " give (other) type=" + type);

                                ServerPlayerEntity executor;
                                try {
                                    executor = source.getPlayer();
                                } catch (Exception ex) {
                                    // Console or command block - always allow
                                    debug.log("give", "Non-player source (console/cmd block) executing give");
                                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                    return executeGive(ctx.getSource(), target, type);
                                }

                                // Check OP via player permission level (tied to ops.json)
                                boolean isOp = executor.hasPermissionLevel(2);
                                int permLevel = executor.getPermissionLevel();
                                if (!isOp) {
                                    source.sendMessage(Text.literal("You must be a server operator to use this command."));
                                    debug.log("give", "Permission denied for " + source.getName() + " permLevel=" + permLevel);
                                    return 0;
                                }

                                ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                return executeGive(ctx.getSource(), target, type);
                            })
                    )
            );
    }

    private static CompletableFuture<Suggestions> suggestTypes(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        for (String key : getItemsMap().keySet()) {
            if ("cloud_cable".equals(key)) continue;
            builder.suggest(key);
        }
        return builder.buildFuture();
    }

    private static int executeGive(ServerCommandSource source, ServerPlayerEntity target, String type) {
        debug.log("give", "Execute give: source=" + source.getName() + " target=" + target.getName().getString() + " type=" + type);

        debug.log("give", "/cloudframe give type=" + type + " target=" + target.getName().getString());

        java.util.function.Consumer<ItemStack> giveOrDropAtFeet = (stack) -> {
            if (stack == null || stack.isEmpty()) return;
            // Try inventory first.
            target.getInventory().insertStack(stack);
            // Spawn any leftovers at the player's feet (reliable even when command feedback is disabled).
            if (!stack.isEmpty()) {
                target.dropItem(stack, false);
            }
        };

        // Augments are represented as enchanted books (for vanilla book textures).
        // These are special stacks with a CloudFrame tag.
        if ("silk".equals(type)) {
            ItemStack stack = dev.cloudframe.fabric.content.AugmentBooks.silkTouch();
            giveOrDropAtFeet.accept(stack);
            target.sendMessage(Text.literal("Received: Silk Touch Augment"), false);
            return 1;
        }

        if (type != null && type.startsWith("speed")) {
            int tier = switch (type) {
                case "speed1" -> 1;
                case "speed2" -> 2;
                case "speed3" -> 3;
                default -> 0;
            };

            if (tier > 0) {
                ItemStack stack = dev.cloudframe.fabric.content.AugmentBooks.speed(tier);
                giveOrDropAtFeet.accept(stack);
                target.sendMessage(Text.literal("Received: Speed Augment (Tier " + tier + ")"), false);
                return 1;
            }
        }

        Map<String, Item> itemsMap = getItemsMap();
        Optional<Item> itemOpt = Optional.ofNullable(itemsMap.get(type));
        if (itemOpt.isEmpty()) {
            java.util.List<String> advertised = new java.util.ArrayList<>(itemsMap.keySet());
            advertised.remove("cloud_cable");
            source.sendError(Text.literal("Unknown type: " + type + ". Try: " + String.join(", ", advertised)));
            debug.log("give", "Unknown type: " + type);
            return 0;
        }

        Item item = itemOpt.get();
        ItemStack stack = new ItemStack(item);

        giveOrDropAtFeet.accept(stack);

        target.sendMessage(Text.literal("Received: " + type), false);
        return 1;
    }
}
