package org.yusaki.villagertradeedit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class VillagerTradeEdit extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("VillagerTradeEdit enabled!");
        getServer().getPluginManager().registerEvents(new VillagerEditListener(), this);
    }

    @Override
    public void onDisable() {
        getLogger().info("VillagerTradeEdit disabled!");
    }

    public void SendMessage(Player player, String message) {
        player.sendMessage(message);
    }
}
