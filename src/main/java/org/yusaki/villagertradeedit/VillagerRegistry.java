package org.yusaki.villagertradeedit;

import com.tcoded.folialib.FoliaLib;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class VillagerRegistry {

    private static final int SCHEMA_VERSION = 1;

    private final JavaPlugin plugin;
    private final FoliaLib foliaLib;
    private final File file;

    private final Map<Integer, VillagerEntry> byId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> byUuid = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public VillagerRegistry(JavaPlugin plugin, FoliaLib foliaLib) {
        this.plugin = plugin;
        this.foliaLib = foliaLib;
        this.file = new File(plugin.getDataFolder(), "villagers.yml");
    }

    public synchronized void load() {
        byId.clear();
        byUuid.clear();
        if (!file.exists()) {
            nextId.set(1);
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        nextId.set(Math.max(1, cfg.getInt("nextId", 1)));
        ConfigurationSection entries = cfg.getConfigurationSection("entries");
        if (entries == null) return;
        for (String key : entries.getKeys(false)) {
            ConfigurationSection s = entries.getConfigurationSection(key);
            if (s == null) continue;
            try {
                int id = Integer.parseInt(key);
                UUID uuid = UUID.fromString(s.getString("uuid", ""));
                String world = s.getString("world", "");
                double x = s.getDouble("x");
                double y = s.getDouble("y");
                double z = s.getDouble("z");
                String name = s.getString("name", "");
                long lastSeen = s.getLong("lastSeen", 0L);
                VillagerEntry entry = new VillagerEntry(id, uuid, world, x, y, z, name, lastSeen);
                byId.put(id, entry);
                byUuid.put(uuid, id);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping malformed villager entry: " + key);
            }
        }
    }

    public synchronized void save() {
        plugin.getDataFolder().mkdirs();
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("version", SCHEMA_VERSION);
        cfg.set("nextId", nextId.get());
        for (VillagerEntry e : byId.values()) {
            String base = "entries." + e.id() + ".";
            cfg.set(base + "uuid", e.uuid().toString());
            cfg.set(base + "world", e.world());
            cfg.set(base + "x", e.x());
            cfg.set(base + "y", e.y());
            cfg.set(base + "z", e.z());
            cfg.set(base + "name", e.name());
            cfg.set(base + "lastSeen", e.lastSeen());
        }
        try {
            cfg.save(file);
            dirty.set(false);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save villagers.yml: " + ex.getMessage());
        }
    }

    public void saveAsyncIfDirty() {
        if (!dirty.get()) return;
        foliaLib.getScheduler().runAsync(t -> save());
    }

    public int assignId(Villager villager) {
        UUID uuid = villager.getUniqueId();
        Integer existing = byUuid.get(uuid);
        if (existing != null) return existing;
        int id = nextId.getAndIncrement();
        Location loc = villager.getLocation();
        String name = villager.getCustomName() != null ? villager.getCustomName() : "";
        VillagerEntry entry = new VillagerEntry(
                id, uuid, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                name, System.currentTimeMillis());
        byId.put(id, entry);
        byUuid.put(uuid, id);
        dirty.set(true);
        return id;
    }

    public void updateLocation(UUID uuid, Location loc, String name) {
        Integer id = byUuid.get(uuid);
        if (id == null) return;
        VillagerEntry existing = byId.get(id);
        if (existing == null) return;
        VillagerEntry updated = new VillagerEntry(
                existing.id(), existing.uuid(),
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                name != null ? name : existing.name(),
                System.currentTimeMillis());
        byId.put(id, updated);
        dirty.set(true);
    }

    public void remove(UUID uuid) {
        Integer id = byUuid.remove(uuid);
        if (id != null) {
            byId.remove(id);
            dirty.set(true);
        }
    }

    public VillagerEntry getById(int id) {
        return byId.get(id);
    }

    public Integer getIdByUuid(UUID uuid) {
        return byUuid.get(uuid);
    }

    public List<VillagerEntry> all() {
        List<VillagerEntry> list = new ArrayList<>(byId.values());
        list.sort(Comparator.comparingInt(VillagerEntry::id));
        return list;
    }

    public void startAutoSave() {
        foliaLib.getScheduler().runTimerAsync(t -> saveAsyncIfDirty(), 600L, 600L); // 30s
    }
}
