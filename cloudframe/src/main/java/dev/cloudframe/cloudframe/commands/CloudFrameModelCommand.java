package dev.cloudframe.cloudframe.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class CloudFrameModelCommand implements CommandExecutor {

    private static final Debug debug = DebugManager.get(CloudFrameModelCommand.class);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        ItemStack inHand = p.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()) {
            p.sendMessage("§eHold an item to inspect.");
            return true;
        }

        ItemMeta meta = inHand.getItemMeta();
        String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "<no name>";
        boolean hasCmd = meta != null && meta.hasCustomModelData();
        Integer cmdVal = (meta != null && hasCmd) ? meta.getCustomModelData() : null;

        p.sendMessage("§bItem: §7" + inHand.getType());
        p.sendMessage("§bName: §7" + name);
        p.sendMessage("§bCustomModelData: §7" + (hasCmd ? String.valueOf(cmdVal) : "<none>"));

        debug.log("modelInspect", "Player=" + p.getName() + " type=" + inHand.getType() +
                " hasCMD=" + hasCmd + (hasCmd ? (" value=" + cmdVal) : ""));

        return true;
    }
}
