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
    private final CloudFrameModelCommand model = new CloudFrameModelCommand();

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        debug.log("onCommand", "Root command invoked by " + sender.getName());

        if (args.length == 0) {
            debug.log("onCommand", "No subcommand provided");
            sender.sendMessage("§e/cloudframe give <item>");
            sender.sendMessage("§e/cloudframe log [count|clear|tick|chunk|startup|pack|visuals|pick]");
            sender.sendMessage("§e/cloudframe model  §7(Inspect held item)");
            return true;
        }

        String sub = args[0].toLowerCase();
        debug.log("onCommand", "Dispatching subcommand=" + sub);

        // Pass subcommand args only (strip the subcommand itself).
        String[] subArgs = new String[Math.max(0, args.length - 1)];
        if (args.length > 1) {
            System.arraycopy(args, 1, subArgs, 0, args.length - 1);
        }

        switch (sub) {

            case "give" -> {
                return give.onCommand(sender, cmd, label, subArgs);
            }

            case "log" -> {
                return log.onCommand(sender, cmd, label, subArgs);
            }

            case "model" -> {
                return model.onCommand(sender, cmd, label, subArgs);
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
