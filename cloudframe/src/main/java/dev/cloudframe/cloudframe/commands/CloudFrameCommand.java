package dev.cloudframe.cloudframe.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class CloudFrameCommand implements CommandExecutor {

    private static final Debug debug = DebugManager.get(CloudFrameCommand.class);

    private final CloudFrameGiveCommand give = new CloudFrameGiveCommand();
    private final CloudFrameLogCommand log = new CloudFrameLogCommand();

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        debug.log("onCommand", "Root command invoked by " + sender.getName());

        if (args.length == 0) {
            debug.log("onCommand", "No subcommand provided");
            sender.sendMessage("§e/cloudframe give <item>");
            sender.sendMessage("§e/cloudframe log [count|clear]");
            return true;
        }

        String sub = args[0].toLowerCase();
        debug.log("onCommand", "Dispatching subcommand=" + sub);

        switch (sub) {

            case "give" -> {
                return give.onCommand(sender, cmd, label, args);
            }

            case "log" -> {
                return log.onCommand(sender, cmd, label, args);
            }

            default -> {
                debug.log("onCommand", "Unknown subcommand=" + sub);
                sender.sendMessage("§cUnknown subcommand: " + args[0]);
                sender.sendMessage("§eValid: give, log");
                return true;
            }
        }
    }
}
