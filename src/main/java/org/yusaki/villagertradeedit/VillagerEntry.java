package org.yusaki.villagertradeedit;

import java.util.UUID;

public record VillagerEntry(
        int id,
        UUID uuid,
        String world,
        double x,
        double y,
        double z,
        String name,
        long lastSeen
) {
    public VillagerEntry withLocation(String world, double x, double y, double z, long lastSeen) {
        return new VillagerEntry(id, uuid, world, x, y, z, name, lastSeen);
    }

    public VillagerEntry withName(String name) {
        return new VillagerEntry(id, uuid, world, x, y, z, name, lastSeen);
    }
}
