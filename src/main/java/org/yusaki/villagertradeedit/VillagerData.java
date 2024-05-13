package org.yusaki.villagertradeedit;

import org.bukkit.entity.Villager;

public class VillagerData {
    private Villager.Profession profession;
    private boolean isStatic;

    public VillagerData(Villager.Profession profession, boolean isStatic) {
        this.profession = profession;
        this.isStatic = isStatic;
    }

    public Villager.Profession getProfession() {
        return profession;
    }

    public boolean isStatic() {
        return isStatic;
    }
}