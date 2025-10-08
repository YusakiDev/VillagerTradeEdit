package org.yusaki.villagertradeedit;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.yusaki.lib.YskLib;
import org.yusaki.lib.modules.MessageManager;

import java.util.Map;

public class YskLibWrapper {

    private final JavaPlugin plugin;
    private final YskLib yskLib;
    private final MessageManager messageManager;

    public YskLibWrapper(YskLib lib) {
        this.plugin = VillagerTradeEdit.getInstance();
        this.yskLib = lib;
        this.messageManager = lib.getMessageManager();
    }

    void sendActionBar(Player player, String message) {
        messageManager.sendActionBar(plugin, player, message);
    }

    void sendActionBar(Player player, String message, Map<String, String> placeholders) {
        messageManager.sendActionBar(plugin, player, message, placeholders);
    }

    void sendTitle(Player player, String title, String subtitle) {
        messageManager.sendTitle(plugin, player, title, subtitle, 20, 70, 20, MessageManager.createPlaceholders());
    }

    void sendTitle(Player player, String title, String subtitle, Map<String, String> placeholders) {
        messageManager.sendTitle(plugin, player, title, subtitle, 20, 70, 20, placeholders);
    }

    void sendMessage(CommandSender sender, String key) {
        messageManager.sendMessage(plugin, sender, key);
    }

    void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        messageManager.sendMessage(plugin, sender, key, placeholders);
    }

    // Backward compatibility method for old varargs style
    void sendMessage(CommandSender sender, String key, Object... args) {
        Map<String, String> placeholders = MessageManager.createPlaceholders();
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i += 2) {
                if (i + 1 < args.length) {
                    placeholders.put(String.valueOf(args[i]), String.valueOf(args[i + 1]));
                } else {
                    // Single arg - assume it's a placeholder value for a default key
                    placeholders.put("value", String.valueOf(args[i]));
                }
            }
        }
        messageManager.sendMessage(plugin, sender, key, placeholders);
    }

    void sendMessageList(CommandSender sender, String key) {
        messageManager.sendMessageList(plugin, sender, key);
    }

    void sendMessageList(CommandSender sender, String key, Map<String, String> placeholders) {
        messageManager.sendMessageList(plugin, sender, key, placeholders);
    }

    String getMessage(String key) {
        return messageManager.getMessage(plugin, key);
    }

    String getMessage(String key, Map<String, String> placeholders) {
        return messageManager.getMessage(plugin, key, placeholders);
    }

    void updateConfig() {
        yskLib.updateConfig(plugin);
    }

    public boolean canExecuteInWorld(World world) {
        return yskLib.canExecuteInWorld(plugin, world);
    }


    public void logSevere(String s) {
        yskLib.logSevere(plugin, s);
    }

    public void logWarn(String s) {
        yskLib.logWarn(plugin, s);
    }

    public void logInfo(String s) {
        yskLib.logInfo(plugin, s);
    }

    public void logDebug(String s) {
        yskLib.logDebug(plugin, s);
    }

    public void logDebugPlayer(Player player, String s) {
        yskLib.logDebugPlayer(plugin, player, s);
    }

}