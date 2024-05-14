package org.yusaki.villagertradeedit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.yusaki.lib.YskLib;


public final class VillagerTradeEdit extends JavaPlugin {

    private YskLib yskLib;
    private YskLibWrapper wrapper;

    //TODO stop villager moving if already pathfind
    //TODO add villager rotate to player
    //TODO add VillagerName
    //TODO Cancel Name change event
    //TODO Save Button Instead of Closing button
    @Override
    public void onEnable() {
        saveDefaultConfig();
        yskLib = (YskLib) Bukkit.getPluginManager().getPlugin("YskLib");
        wrapper = new YskLibWrapper(this, yskLib);
        yskLib.updateConfig(this);
        getLogger().info("VillagerTradeEdit enabled!");
        VillagerEditListener villagerEditListener = new VillagerEditListener(this, wrapper);
        getServer().getPluginManager().registerEvents(villagerEditListener, this);
        VTECommandExecutor vteCommandExecutor = new VTECommandExecutor(this, villagerEditListener, wrapper);
        this.getCommand("vte").setExecutor(vteCommandExecutor);
        this.getCommand("vte").setTabCompleter(vteCommandExecutor);
    }

    @Override
    public void onDisable() {
        getLogger().info("VillagerTradeEdit disabled!");
    }


}
