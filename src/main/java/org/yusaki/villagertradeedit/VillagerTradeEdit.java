package org.yusaki.villagertradeedit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;


public final class VillagerTradeEdit extends JavaPlugin {


    //TODO add permission
    //TODO stop villager moving if already pathfind
    //TODO add villager rotate to player
    //TODO Toggle item Visibility (On open inventory)
    //TODO Add Command to summon villager that is static
    @Override
    public void onEnable() {
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

    public void SendMessage(Player player, String message) {
        player.sendMessage(message);
    }
}
