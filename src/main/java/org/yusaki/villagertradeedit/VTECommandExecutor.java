package org.yusaki.villagertradeedit;

import com.tcoded.folialib.FoliaLib;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.util.BlockIterator;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

public class VTECommandExecutor implements CommandExecutor, TabCompleter {
    private final VillagerTradeEdit plugin;
    private final YskLibWrapper wrapper;
    private final VillagerEditListener villagerEditListener;
    private final FoliaLib foliaLib;
    private final Component prefixComponent;
    private final boolean prefixEnabled;

    public VTECommandExecutor(VillagerTradeEdit plugin, VillagerEditListener villagerEditListener) {
        this.plugin = plugin;
        this.wrapper = VillagerTradeEdit.getInstance().wrapper;
        this.villagerEditListener = villagerEditListener;
        this.foliaLib = plugin.getFoliaLib();

        String prefixRaw = wrapper.getMessage("prefix");
        if (prefixRaw == null || prefixRaw.isBlank()) {
            prefixRaw = plugin.getConfig().getString("messages.prefix", "");
        }

        boolean hasPrefix = prefixRaw != null && !prefixRaw.isBlank();
        this.prefixEnabled = hasPrefix;
        this.prefixComponent = hasPrefix
                ? LegacyComponentSerializer.legacyAmpersand().deserialize(prefixRaw)
                : Component.empty();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }


        if (!player.hasPermission("villagertradeedit.command")) {
            wrapper.sendMessage(player, "noPermission");
            return true;
        }

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendPluginInfo(player);
            return true;
        }

        if ("summon".equalsIgnoreCase(args[0])) {
            if (!player.hasPermission("villagertradeedit.command.summon")) {
                wrapper.sendMessage(player, "noPermission");
                return true;
            }
            if (!wrapper.canExecuteInWorld(player.getWorld())) {
                wrapper.sendMessage(player, "disabledWorld");
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

                    Villager villager = (Villager) player.getWorld().spawnEntity(spawnLocation, EntityType.VILLAGER);
                    foliaLib.getScheduler().runAtEntity(villager, task -> {
                        villagerEditListener.activateStaticMode(villager, player);
                        villager.teleportAsync(spawnLocation);
                    });
                    return true;
                }
            }
            wrapper.sendMessage(player, "noBlockInRange");
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            if (!player.hasPermission("villagertradeedit.command.reload")) {
                wrapper.sendMessage(player, "noPermission");
                return true;
            }
            plugin.reloadPluginConfig();
            wrapper.sendMessage(player, "configReloaded");
            return true;
        }

        return false;
    }

    private void sendPluginInfo(Player player) {
        String name = plugin.getDescription().getName();
        String version = plugin.getDescription().getVersion();
        String author = String.join(", ", plugin.getDescription().getAuthors());

        player.sendMessage(Component.text(""));
        sendPrefixed(player, Component.text(name + " v" + version)
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        sendPrefixed(player, Component.text("Custom villager trade editor (Paper/Folia).")
                .color(NamedTextColor.GRAY));
        sendPrefixed(player, Component.text("Author: ")
                .color(NamedTextColor.DARK_GRAY)
                .append(Component.text(author).color(NamedTextColor.WHITE)));

        player.sendMessage(Component.text(""));
        sendPrefixed(player, Component.text("Commands:").color(NamedTextColor.GOLD));
        sendPrefixed(player, Component.text(" • /vte summon").color(NamedTextColor.YELLOW)
                .append(Component.text(" – Spawn managed static villager").color(NamedTextColor.GRAY)));
        sendPrefixed(player, Component.text(" • /vte reload").color(NamedTextColor.YELLOW)
                .append(Component.text(" – Reload configuration").color(NamedTextColor.GRAY)));

        player.sendMessage(Component.text(""));
        sendPrefixed(player, Component.text("Interactions:").color(NamedTextColor.GOLD));
        sendPrefixed(player, Component.text(" • Right-click: trade (permission if set)").color(NamedTextColor.GRAY));
        sendPrefixed(player, Component.text(" • Shift-right-click: open editor").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void sendPrefixed(Player player, Component message) {
        if (prefixEnabled) {
            player.sendMessage(prefixComponent.append(message));
        } else {
            player.sendMessage(message);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (sender instanceof Player && args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("summon");
            completions.add("reload");
            return completions;
        }
        return null;
    }
}
