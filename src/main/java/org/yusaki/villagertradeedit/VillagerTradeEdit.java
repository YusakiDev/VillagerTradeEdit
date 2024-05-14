package org.yusaki.villagertradeedit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.yusaki.lib.YskLib;


public final class VillagerTradeEdit extends JavaPlugin {

    Plugin lib = Bukkit.getPluginManager().getPlugin("YskLib");
    YskLib yskLib = (YskLib) lib;

    //TODO stop villager moving if already pathfind
    //TODO add villager rotate to player
    //TODO add VillagerName
    //TODO Cancel Name change event
    //TODO Save Button Instead of Closing button
    @Override
    public void onEnable() {
        saveDefaultConfig();
        yskLib.updateConfig(this);
        getLogger().info("VillagerTradeEdit enabled!");
        VillagerEditListener villagerEditListener = new VillagerEditListener(this);
        getServer().getPluginManager().registerEvents(villagerEditListener, this);
        VTECommandExecutor vteCommandExecutor = new VTECommandExecutor(this, villagerEditListener);
        this.getCommand("vte").setExecutor(vteCommandExecutor);
        this.getCommand("vte").setTabCompleter(vteCommandExecutor);
    }

    @Override
    public void onDisable() {
        getLogger().info("VillagerTradeEdit disabled!");
    }

    void sendMessage(Player player, String key, Object... args) {
        yskLib.sendMessage(this, player, key, args);
    }

    public boolean canExecuteInWorld(World world) {
        return yskLib.canExecuteInWorld(this, world);
    }

    public void logDebug(String s) {
        yskLib.logDebug(this, s);
    }

    public void logDebugPlayer(Player player, String s) {
        yskLib.logDebugPlayer(this, player, s);
    }
}
