package org.yusaki.villagertradeedit;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Villager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VillagerDataHandler {
    private File dataFile;
    private YamlConfiguration dataConfig;

    public VillagerDataHandler(File dataFile) {
        this.dataFile = dataFile;
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void saveVillagerData(Villager villager) {
        String uuid = villager.getUniqueId().toString();
        dataConfig.set(uuid + ".profession", villager.getProfession().name());
        dataConfig.set(uuid + ".isStatic", villager.isInvulnerable()); // Assuming isStatic is represented by invulnerability
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<UUID, VillagerData> loadAllVillagersData() {
        Map<UUID, VillagerData> villagersData = new HashMap<>();
        for (String key : dataConfig.getKeys(false)) {
            UUID uuid = UUID.fromString(key);
            Villager.Profession profession = Villager.Profession.valueOf(dataConfig.getString(key + ".profession"));
            boolean isStatic = dataConfig.getBoolean(key + ".isStatic");
            villagersData.put(uuid, new VillagerData(profession, isStatic));
        }
        return villagersData;
    }

    public Villager.Profession loadVillagerProfession(String uuid) {
        String professionName = dataConfig.getString(uuid + ".profession");
        return Villager.Profession.valueOf(professionName);
    }

    public boolean loadVillagerIsStatic(String uuid) {
        return dataConfig.getBoolean(uuid + ".isStatic");
    }

    public void removeVillagerData(Villager villager) {
        String uuid = villager.getUniqueId().toString();
        dataConfig.set(uuid, null); // This will remove the villager's data
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

