package dev.cloudframe.cloudframe.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.cloudframe.cloudframe.util.DebugBuffer;
import dev.cloudframe.cloudframe.util.DebugFlags;

public class CloudFrameLogCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }

        // /cloudframe log clear
        if (args.length == 1 && args[0].equalsIgnoreCase("clear")) {
            DebugBuffer.clear();
            p.sendMessage("§aCloudFrame log cleared.");
            return true;
        }

        // /cloudframe log tick
        if (args.length == 1 && args[0].equalsIgnoreCase("tick")) {
            DebugFlags.TICK_LOGGING = !DebugFlags.TICK_LOGGING;

            p.sendMessage("§bTick logging is now " +
                    (DebugFlags.TICK_LOGGING ? "§aENABLED" : "§cDISABLED"));

            return true;
        }
        
        // /cloudframe log <count>
        int count = 30;
        if (args.length == 1) {
            try {
                count = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        var lines = DebugBuffer.getLast(count);

        p.sendMessage("§b--- CloudFrame Log (last " + lines.size() + " lines) ---");

        for (String line : lines) {
            p.sendMessage("§7" + line);
        }

        return true;
    }
}
