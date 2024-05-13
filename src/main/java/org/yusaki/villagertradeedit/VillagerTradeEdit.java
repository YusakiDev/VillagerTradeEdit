package org.yusaki.villagertradeedit;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;


public final class VillagerTradeEdit extends JavaPlugin {

    private final VillagerEditListener villagerEditListener = new VillagerEditListener();


    @Override
    public void onEnable() {
        getLogger().info("VillagerTradeEdit enabled!");
        getServer().getPluginManager().registerEvents(villagerEditListener, this);

        // Load all villager data
        Map<UUID, VillagerData> villagersData = villagerEditListener.loadAllVillagersData();


        for (World world : getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Villager) {
                    Villager villager = (Villager) entity;
                    VillagerData villagerData = villagersData.get(villager.getUniqueId());
                    if (villagerData != null) {
                        villager.setProfession(villagerData.getProfession());
                        villager.setInvulnerable(villagerData.isStatic());
                        if(villagerData.isStatic()){
                            villagerEditListener.getStaticMap().put(villager, true);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("VillagerTradeEdit disabled!");
    }

    public void SendMessage(Player player, String message) {
        player.sendMessage(message);
    }
}
