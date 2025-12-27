package dev.cloudframe.cloudframe.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.cloudframe.cloudframe.items.MarkerTool;
import dev.cloudframe.cloudframe.items.WrenchTool;
import dev.cloudframe.cloudframe.items.TubeItem;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class CloudFrameGiveCommand implements CommandExecutor {

    private static final Debug debug = DebugManager.get(CloudFrameGiveCommand.class);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        debug.log("onCommand", "Command executed by sender=" + sender.getName() +
                " args=" + String.join(" ", args));

        if (!(sender instanceof Player p)) {
            debug.log("onCommand", "Command rejected — sender not a player");
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length < 2) {
            debug.log("onCommand", "Invalid usage — missing arguments");
            p.sendMessage("§eUsage: /cloudframe give <marker|wrench|tube>");
            return true;
        }

        // Require the "give" subcommand
        if (!args[0].equalsIgnoreCase("give")) {
            debug.log("onCommand", "Invalid subcommand: " + args[0]);
            p.sendMessage("§eUsage: /cloudframe give <marker|wrench|tube>");
            return true;
        }

        String type = args[1].toLowerCase();
        debug.log("onCommand", "Processing give request for type=" + type);

        switch (type) {
            case "marker" -> {
                debug.log("onCommand", "Giving Cloud Marker to " + p.getName());
                p.getInventory().addItem(MarkerTool.create());
                p.sendMessage("§aGiven Cloud Marker.");
            }
            case "wrench" -> {
                debug.log("onCommand", "Giving Cloud Wrench to " + p.getName());
                p.getInventory().addItem(WrenchTool.create());
                p.sendMessage("§aGiven Cloud Wrench.");
            }
            case "tube" -> {
                debug.log("onCommand", "Giving Cloud Tube to " + p.getName());
                p.getInventory().addItem(TubeItem.create());
                p.sendMessage("§aGiven Cloud Tube.");
            }
            default -> {
                debug.log("onCommand", "Unknown item type: " + type);
                p.sendMessage("§cUnknown item type: " + args[1]);
                p.sendMessage("§eValid types: marker, wrench, tube");
            }
        }

        return true;
    }
}
