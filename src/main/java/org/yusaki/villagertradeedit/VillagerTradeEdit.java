package org.yusaki.villagertradeedit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.yusaki.lib.YskLib;


public final class VillagerTradeEdit extends JavaPlugin {

    private YskLib yskLib;
    public YskLibWrapper wrapper;


    //TODO stop villager moving if already pathfind
    //TODO add villager rotate to player
    //TODO add VillagerName
    //TODO Cancel Name change event
    //TODO Save Button Instead of Closing button
    @Override
    public void onEnable() {
        saveDefaultConfig();
        yskLib = (YskLib) Bukkit.getPluginManager().getPlugin("YskLib");
        yskLib.updateConfig(this);
        wrapper = new YskLibWrapper(yskLib);
        getLogger().info("VillagerTradeEdit enabled!");
        VillagerEditListener villagerEditListener = new VillagerEditListener();
        getServer().getPluginManager().registerEvents(villagerEditListener, this);
        VTECommandExecutor vteCommandExecutor = new VTECommandExecutor(this, villagerEditListener);
        this.getCommand("vte").setExecutor(vteCommandExecutor);
        this.getCommand("vte").setTabCompleter(vteCommandExecutor);
    }

    @Override
    public void onDisable() {
        getLogger().info("VillagerTradeEdit disabled!");
    }

    public static VillagerTradeEdit getInstance() {
        return getPlugin(VillagerTradeEdit.class);
    }

}
