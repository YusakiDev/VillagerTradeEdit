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

    void sendMessage(Player player, String key, Object... args) {
        yskLib.sendMessage(plugin, player, key, args);
    }

    public boolean canExecuteInWorld(World world) {
        return yskLib.canExecuteInWorld(plugin, world);
    }

    public void logDebug(String s) {
        yskLib.logDebug(plugin, s);
    }

    public void logDebugPlayer(Player player, String s) {
        yskLib.logDebugPlayer(plugin, player, s);
    }
    
}