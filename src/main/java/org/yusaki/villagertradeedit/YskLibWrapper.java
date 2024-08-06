package org.yusaki.villagertradeedit;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.yusaki.lib.YskLib;

public class YskLibWrapper {

    private final JavaPlugin plugin;
    private final YskLib yskLib;

    public YskLibWrapper(YskLib lib) {
        this.plugin = VillagerTradeEdit.getInstance();
        this.yskLib = lib;
    }

    void sendActionBar(Player player, String message) {
        yskLib.sendActionBar(plugin, player, message);
    }

    void sendTitle(Player player, String title, String subtitle) {
        yskLib.sendTitle(plugin, player, title, subtitle, 20, 70, 20);
    }

    void sendMessage(Player player, String key, Object... args) {
        yskLib.sendMessage(plugin, player, key, args);
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