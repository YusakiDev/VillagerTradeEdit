package org.yusaki.villagertradeedit;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.util.BlockIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class VTECommandExecutor implements CommandExecutor, TabCompleter {
    private final VillagerTradeEdit plugin;
    private final VillagerEditListener villagerEditListener;

    public VTECommandExecutor(VillagerTradeEdit plugin, VillagerEditListener villagerEditListener) {
        this.plugin = plugin;
        this.villagerEditListener = villagerEditListener;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        if (!player.hasPermission("villagertradeedit.command")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length > 0 && "summon".equalsIgnoreCase(args[0])) {
            if (!player.hasPermission("villagertradeedit.command.summon")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use this subcommand.");
                return true;
            }
            BlockIterator iterator = new BlockIterator(player, 5);
            while (iterator.hasNext()) {
                Block block = iterator.next();
                if (block.getType() != Material.AIR) {
                    Location spawnLocation = block.getLocation().add(0, 1, 0);
                    // Adjust the spawn location to be at the center of the block
                    spawnLocation.setX(Math.floor(spawnLocation.getX()) + 0.5);
                    spawnLocation.setZ(Math.floor(spawnLocation.getZ()) + 0.5);

                    // Check if the distance is too far
                    if (player.getLocation().distance(spawnLocation) > 5) {
                        player.sendMessage(ChatColor.RED + "The distance is too far to summon a villager.");
                        return true;
                    }

                    Villager villager = (Villager) player.getWorld().spawnEntity(spawnLocation, EntityType.VILLAGER);
                    villagerEditListener.activateStaticMode(villager, player);
                    villager.teleportAsync(spawnLocation);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (sender instanceof Player && args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("summon");
            return completions;
        }
        return null;
    }
}