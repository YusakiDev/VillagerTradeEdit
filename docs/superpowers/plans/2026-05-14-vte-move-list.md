# VTE Move & List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Citizens-style subcommands (`select`, `deselect`, `tp`, `tphere`, `moveto`, `list`) to VillagerTradeEdit with stable integer IDs for managed villagers and a persistent registry that survives chunk unloads and restarts.

**Architecture:** New `VillagerRegistry` owns an in-memory ID-keyed map persisted to `villagers.yml`. Managed villagers receive a `vte_id` integer in their PDC. `VTECommandExecutor` gains a per-player `selections` map and new subcommands. `VillagerEditListener` gains hooks for `EntityRemoveFromWorldEvent` and delete to update the registry. Folia-safe entity access via FoliaLib schedulers + `teleportAsync`.

**Tech Stack:** Java 25, Paper/Folia API 26.1, FoliaLib, YskLib (for config + messages), Adventure components, Bukkit PersistentDataContainer.

**Spec:** `docs/superpowers/specs/2026-05-14-vte-move-list-design.md`

**Branch:** `feat/vte-move-list` (already created)

**Build verification:** `mvn -q -DskipTests package` after each task that changes Java files. Use `mvn -q compile` for faster iteration. Expected: BUILD SUCCESS.

**No test harness:** Project has no JUnit setup. Verification is `mvn compile` for type/syntax correctness plus a final manual test plan (Task 13). Don't add JUnit in this plan.

---

## File Structure

**Create:**
- `src/main/java/org/yusaki/villagertradeedit/VillagerEntry.java` — immutable record
- `src/main/java/org/yusaki/villagertradeedit/VillagerRegistry.java` — registry + persistence
- `src/main/java/org/yusaki/villagertradeedit/SelectionListener.java` — `PlayerQuitEvent` handler

**Modify:**
- `src/main/java/org/yusaki/villagertradeedit/VillagerTradeEdit.java` — add `vteIdKey`, registry init, save on disable
- `src/main/java/org/yusaki/villagertradeedit/VillagerEditListener.java` — ID assign on restore, registry on remove/delete, remove-from-world hook
- `src/main/java/org/yusaki/villagertradeedit/VTECommandExecutor.java` — selections map, new subcommands, tab completion, help
- `src/main/resources/plugin.yml` — `villagertradeedit.command.move` + `villagertradeedit.command.list` permissions
- `src/main/resources/config.yml` — new messages, bump `version` to `4.0`

---

### Task 1: Add `VillagerEntry` record

**Files:**
- Create: `src/main/java/org/yusaki/villagertradeedit/VillagerEntry.java`

- [ ] **Step 1: Write the record**

```java
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
```

- [ ] **Step 2: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/yusaki/villagertradeedit/VillagerEntry.java
git commit -m "feat: add VillagerEntry record for managed-villager registry"
```

---

### Task 2: Add `VillagerRegistry` with load/save

**Files:**
- Create: `src/main/java/org/yusaki/villagertradeedit/VillagerRegistry.java`

- [ ] **Step 1: Write the registry**

```java
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
```

- [ ] **Step 2: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/yusaki/villagertradeedit/VillagerRegistry.java
git commit -m "feat: add VillagerRegistry with YAML persistence and auto-save"
```

---

### Task 3: Wire registry + `vte_id` key into main plugin class

**Files:**
- Modify: `src/main/java/org/yusaki/villagertradeedit/VillagerTradeEdit.java`

- [ ] **Step 1: Add `vteIdKey` field and getter**

Edit `VillagerTradeEdit.java`. Locate the `forceSpawnKey` field declaration and add directly below:

```java
    private NamespacedKey vteIdKey;
    private VillagerRegistry villagerRegistry;
```

Add getter near `getForceSpawnKey()`:

```java
    public NamespacedKey getVteIdKey() {
        return vteIdKey;
    }

    public VillagerRegistry getVillagerRegistry() {
        return villagerRegistry;
    }
```

- [ ] **Step 2: Initialise in `onEnable`**

In `onEnable()`, after the existing `forceSpawnKey = new NamespacedKey(this, "force_spawn");` line, add:

```java
        vteIdKey = new NamespacedKey(this, "vte_id");
        villagerRegistry = new VillagerRegistry(this, foliaLib);
        villagerRegistry.load();
        villagerRegistry.startAutoSave();
```

- [ ] **Step 3: Save on disable**

In `onDisable()`, before `getLogger().info("VillagerTradeEdit disabled!");`, add:

```java
        if (villagerRegistry != null) {
            villagerRegistry.save();
        }
```

- [ ] **Step 4: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/yusaki/villagertradeedit/VillagerTradeEdit.java
git commit -m "feat: wire VillagerRegistry and vte_id NamespacedKey into plugin lifecycle"
```

---

### Task 4: Assign ID + update registry inside `VillagerEditListener`

**Files:**
- Modify: `src/main/java/org/yusaki/villagertradeedit/VillagerEditListener.java`

- [ ] **Step 1: Cache registry + ID key references**

Near the existing `STATIC_KEY` field (around line 87) and its initialization (around line 101), add registry + key fields and assignments. Add field:

```java
    private final org.bukkit.NamespacedKey VTE_ID_KEY;
    private final VillagerRegistry registry;
```

In the constructor (or wherever `STATIC_KEY` is assigned), add:

```java
        VTE_ID_KEY = plugin.getVteIdKey();
        registry = plugin.getVillagerRegistry();
```

(Replace `plugin` with the actual local reference used at that site — match the existing pattern.)

- [ ] **Step 2: Auto-assign on restore**

In `retrieveVillagerData(Villager villager)` (starting around line 240), at the end of the method (after existing restore logic) add:

```java
        if (dataContainer.has(STATIC_KEY, PersistentDataType.STRING)) {
            ensureRegistryEntry(villager);
        }
```

Add private helper at bottom of class:

```java
    private void ensureRegistryEntry(Villager villager) {
        if (registry == null) return;
        org.bukkit.persistence.PersistentDataContainer pdc = villager.getPersistentDataContainer();
        Integer storedId = pdc.get(VTE_ID_KEY, PersistentDataType.INTEGER);
        if (storedId == null) {
            int newId = registry.assignId(villager);
            pdc.set(VTE_ID_KEY, PersistentDataType.INTEGER, newId);
        } else {
            String name = villager.getCustomName() != null ? villager.getCustomName() : "";
            registry.updateLocation(villager.getUniqueId(), villager.getLocation(), name);
        }
    }
```

- [ ] **Step 3: Hook into `storeVillagerDataSync`**

Locate `storeVillagerDataSync` (around line 201). After the existing PDC writes (after `dataContainer.set(STATIC_KEY, ...)`), add:

```java
        ensureRegistryEntry(villager);
```

- [ ] **Step 4: Hook into delete path**

In `handleDeleteVillager` (around line 907), before `villager.remove();` inside the scheduler lambda, add:

```java
                if (registry != null) registry.remove(villager.getUniqueId());
```

- [ ] **Step 5: Add `EntityRemoveFromWorldEvent` handler**

At an appropriate location near other `@EventHandler` methods in `VillagerEditListener`, add:

```java
    @EventHandler
    public void onEntityRemoveFromWorld(io.papermc.paper.event.entity.EntityRemoveFromWorldEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (registry == null) return;
        org.bukkit.persistence.PersistentDataContainer pdc = villager.getPersistentDataContainer();
        if (!pdc.has(STATIC_KEY, PersistentDataType.STRING)) return;
        String name = villager.getCustomName() != null ? villager.getCustomName() : "";
        registry.updateLocation(villager.getUniqueId(), villager.getLocation(), name);
    }
```

Confirm `org.bukkit.event.EventHandler` is already imported (it is — listener has other handlers).

- [ ] **Step 6: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/yusaki/villagertradeedit/VillagerEditListener.java
git commit -m "feat: assign vte_id and sync VillagerRegistry on restore/store/delete/unload"
```

---

### Task 5: Add `SelectionListener` for `PlayerQuitEvent`

**Files:**
- Create: `src/main/java/org/yusaki/villagertradeedit/SelectionListener.java`

- [ ] **Step 1: Write listener**

```java
package org.yusaki.villagertradeedit;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

public class SelectionListener implements Listener {

    private final Map<UUID, Integer> selections;

    public SelectionListener(Map<UUID, Integer> selections) {
        this.selections = selections;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selections.remove(event.getPlayer().getUniqueId());
    }
}
```

- [ ] **Step 2: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/yusaki/villagertradeedit/SelectionListener.java
git commit -m "feat: add SelectionListener to clear player selection on quit"
```

---

### Task 6: Update `plugin.yml` permissions

**Files:**
- Modify: `src/main/resources/plugin.yml`

- [ ] **Step 1: Add new permissions**

In `plugin.yml`, under the `permissions:` block (after `villagertradeedit.command.reload`), add:

```yaml
  villagertradeedit.command.move:
    description: Allows selecting, teleporting, and moving managed villagers
    default: op
  villagertradeedit.command.list:
    description: Allows listing managed villagers
    default: op
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/plugin.yml
git commit -m "feat: add command.move and command.list permissions"
```

---

### Task 7: Update `config.yml` with new messages + version bump

**Files:**
- Modify: `src/main/resources/config.yml`

- [ ] **Step 1: Bump version**

Change `version: 3.1` to `version: 4.0`.

- [ ] **Step 2: Add new message keys**

Under `messages:` append:

```yaml
  noVillagerInSight: "&cNo villager in your line of sight."
  villagerIdUnknown: "&cNo managed villager with ID {0}."
  noSelection: "&cYou have no villager selected. Use /vte select."
  selectionGone: "&cSelected villager no longer exists. Selection cleared."
  selectionUnloaded: "&eVillager chunk loading, retrying..."
  invalidCoords: "&cInvalid coordinates."
  worldNotFound: "&cWorld '{0}' not found."
  outsideWorldBorder: "&cDestination is outside the world border."
  villagerSelected: "&aSelected villager #{0} {1}"
  villagerMoved: "&aVillager #{0} moved."
  playerTeleported: "&aTeleported to villager #{0}."
  selectionCleared: "&aSelection cleared."
  listHeader: "&6Managed Villagers (page {0}/{1}):"
  listEntry: "&e#{0} &f{1} &7- {2} ({3}, {4}, {5})"
  listFooter: "&7Page {0}/{1} - {2} total"
  listEmpty: "&7No managed villagers."
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/config.yml
git commit -m "feat: add messages for /vte move and /vte list, bump config to 4.0"
```

---

### Task 8: Register `SelectionListener` and prepare selections map

**Files:**
- Modify: `src/main/java/org/yusaki/villagertradeedit/VillagerTradeEdit.java`
- Modify: `src/main/java/org/yusaki/villagertradeedit/VTECommandExecutor.java`

- [ ] **Step 1: Add selections map to executor**

In `VTECommandExecutor.java`, add imports if missing:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
```

Add field next to other fields (around line 32):

```java
    private final Map<UUID, Integer> selections = new HashMap<>();
```

Add getter:

```java
    public Map<UUID, Integer> getSelections() {
        return selections;
    }
```

- [ ] **Step 2: Register listener in main plugin**

In `VillagerTradeEdit.onEnable()`, after the line that registers `villagerEditListener`:

```java
        getServer().getPluginManager().registerEvents(new SelectionListener(vteCommandExecutor.getSelections()), this);
```

(Place this line **after** `VTECommandExecutor vteCommandExecutor = new VTECommandExecutor(...)` since it references the executor. Move the registration block accordingly.)

- [ ] **Step 3: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/yusaki/villagertradeedit/VTECommandExecutor.java src/main/java/org/yusaki/villagertradeedit/VillagerTradeEdit.java
git commit -m "feat: wire SelectionListener and per-player selection map"
```

---

### Task 9: Implement `/vte select` and `/vte deselect`

**Files:**
- Modify: `src/main/java/org/yusaki/villagertradeedit/VTECommandExecutor.java`

- [ ] **Step 1: Add imports**

Add to import block:

```java
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.persistence.PersistentDataContainer;
```

- [ ] **Step 2: Add registry + namespaced key fields**

Add fields beside existing ones:

```java
    private final VillagerRegistry registry;
    private final NamespacedKey vteIdKey;
    private final NamespacedKey staticKey;
```

Initialise in constructor (after existing assignments):

```java
        this.registry = plugin.getVillagerRegistry();
        this.vteIdKey = plugin.getVteIdKey();
        this.staticKey = new NamespacedKey(plugin, "static");
```

- [ ] **Step 3: Add subcommand routing**

Inside `onCommand`, before the final `return false;`, add:

```java
        switch (args[0].toLowerCase()) {
            case "select" -> { return handleSelect(player, args); }
            case "deselect" -> { return handleDeselect(player); }
            case "tp" -> { return handleTp(player, args); }
            case "tphere" -> { return handleTpHere(player, args); }
            case "moveto" -> { return handleMoveTo(player, args); }
            case "list" -> { return handleList(player, args); }
            default -> { return false; }
        }
```

Remove the trailing `return false;` after this switch.

- [ ] **Step 4: Implement `handleSelect`**

Add private methods:

```java
    private boolean requireMovePermission(Player player) {
        if (!player.hasPermission("villagertradeedit.command.move")) {
            wrapper.sendMessage(player, "noPermission");
            return false;
        }
        return true;
    }

    private boolean handleSelect(Player player, String[] args) {
        if (!requireMovePermission(player)) return true;
        if (args.length >= 2) {
            try {
                int id = Integer.parseInt(args[1]);
                VillagerEntry entry = registry.getById(id);
                if (entry == null) {
                    wrapper.sendMessage(player, "villagerIdUnknown", String.valueOf(id));
                    return true;
                }
                selections.put(player.getUniqueId(), id);
                wrapper.sendMessage(player, "villagerSelected", String.valueOf(id), entry.name());
                return true;
            } catch (NumberFormatException ex) {
                wrapper.sendMessage(player, "villagerIdUnknown", args[1]);
                return true;
            }
        }
        if (!wrapper.canExecuteInWorld(player.getWorld())) {
            wrapper.sendMessage(player, "disabledWorld");
            return true;
        }
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                10.0,
                e -> e instanceof Villager && !e.equals(player));
        if (result == null || !(result.getHitEntity() instanceof Villager villager)) {
            wrapper.sendMessage(player, "noVillagerInSight");
            return true;
        }
        PersistentDataContainer pdc = villager.getPersistentDataContainer();
        if (!pdc.has(staticKey, PersistentDataType.STRING)) {
            wrapper.sendMessage(player, "notManagedVillager");
            return true;
        }
        Integer existingId = pdc.get(vteIdKey, PersistentDataType.INTEGER);
        int id = (existingId != null) ? existingId : registry.assignId(villager);
        if (existingId == null) {
            pdc.set(vteIdKey, PersistentDataType.INTEGER, id);
        }
        selections.put(player.getUniqueId(), id);
        String name = villager.getCustomName() != null ? villager.getCustomName() : "";
        wrapper.sendMessage(player, "villagerSelected", String.valueOf(id), name);
        return true;
    }

    private boolean handleDeselect(Player player) {
        if (!requireMovePermission(player)) return true;
        selections.remove(player.getUniqueId());
        wrapper.sendMessage(player, "selectionCleared");
        return true;
    }
```

- [ ] **Step 5: Stub remaining handlers (compile sentinels)**

Add empty stubs so the switch compiles:

```java
    private boolean handleTp(Player player, String[] args) {
        wrapper.sendMessage(player, "noSelection");
        return true;
    }

    private boolean handleTpHere(Player player, String[] args) {
        wrapper.sendMessage(player, "noSelection");
        return true;
    }

    private boolean handleMoveTo(Player player, String[] args) {
        wrapper.sendMessage(player, "noSelection");
        return true;
    }

    private boolean handleList(Player player, String[] args) {
        wrapper.sendMessage(player, "listEmpty");
        return true;
    }
```

(These get replaced in later tasks.)

- [ ] **Step 6: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/yusaki/villagertradeedit/VTECommandExecutor.java
git commit -m "feat: implement /vte select and /vte deselect"
```

---

### Task 10: Implement `/vte tphere`

**Files:**
- Modify: `src/main/java/org/yusaki/villagertradeedit/VTECommandExecutor.java`

- [ ] **Step 1: Replace `handleTpHere` stub**

```java
    private boolean handleTpHere(Player player, String[] args) {
        if (!requireMovePermission(player)) return true;
        Integer selectedId = selections.get(player.getUniqueId());
        if (selectedId == null) {
            wrapper.sendMessage(player, "noSelection");
            return true;
        }
        VillagerEntry entry = registry.getById(selectedId);
        if (entry == null) {
            selections.remove(player.getUniqueId());
            wrapper.sendMessage(player, "selectionGone");
            return true;
        }
        Location dest = player.getLocation();
        if (!dest.getWorld().getWorldBorder().isInside(dest)) {
            wrapper.sendMessage(player, "outsideWorldBorder");
            return true;
        }
        resolveVillager(entry, villager -> {
            if (villager == null) {
                selections.remove(player.getUniqueId());
                wrapper.sendMessage(player, "selectionGone");
                return;
            }
            foliaLib.getScheduler().runAtEntity(villager, t -> {
                villager.teleportAsync(dest);
                String name = villager.getCustomName() != null ? villager.getCustomName() : "";
                registry.updateLocation(villager.getUniqueId(), dest, name);
                wrapper.sendMessage(player, "villagerMoved", String.valueOf(entry.id()));
            });
        });
        return true;
    }
```

- [ ] **Step 2: Add `resolveVillager` helper**

Add private method:

```java
    private void resolveVillager(VillagerEntry entry, java.util.function.Consumer<Villager> callback) {
        Entity direct = Bukkit.getEntity(entry.uuid());
        if (direct instanceof Villager v && v.isValid()) {
            callback.accept(v);
            return;
        }
        World world = Bukkit.getWorld(entry.world());
        if (world == null) {
            callback.accept(null);
            return;
        }
        int chunkX = (int) Math.floor(entry.x()) >> 4;
        int chunkZ = (int) Math.floor(entry.z()) >> 4;
        world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk -> {
            Entity retry = Bukkit.getEntity(entry.uuid());
            callback.accept(retry instanceof Villager vv && vv.isValid() ? vv : null);
        });
    }
```

- [ ] **Step 3: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/yusaki/villagertradeedit/VTECommandExecutor.java
git commit -m "feat: implement /vte tphere with chunk-load retry"
```

---

### Task 11: Implement `/vte tp` and `/vte moveto`

**Files:**
- Modify: `src/main/java/org/yusaki/villagertradeedit/VTECommandExecutor.java`

- [ ] **Step 1: Replace `handleTp` stub**

```java
    private boolean handleTp(Player player, String[] args) {
        if (!requireMovePermission(player)) return true;
        Integer id;
        if (args.length >= 2) {
            try {
                id = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                wrapper.sendMessage(player, "villagerIdUnknown", args[1]);
                return true;
            }
        } else {
            id = selections.get(player.getUniqueId());
            if (id == null) {
                wrapper.sendMessage(player, "noSelection");
                return true;
            }
        }
        VillagerEntry entry = registry.getById(id);
        if (entry == null) {
            wrapper.sendMessage(player, "villagerIdUnknown", String.valueOf(id));
            return true;
        }
        World world = Bukkit.getWorld(entry.world());
        if (world == null) {
            wrapper.sendMessage(player, "worldNotFound", entry.world());
            return true;
        }
        Location dest = new Location(world, entry.x(), entry.y(), entry.z());
        foliaLib.getScheduler().runAtLocation(dest, t ->
                player.teleportAsync(dest).thenAccept(success ->
                        wrapper.sendMessage(player, "playerTeleported", String.valueOf(entry.id()))));
        return true;
    }
```

- [ ] **Step 2: Replace `handleMoveTo` stub**

```java
    private boolean handleMoveTo(Player player, String[] args) {
        if (!requireMovePermission(player)) return true;
        if (args.length < 4) {
            wrapper.sendMessage(player, "invalidCoords");
            return true;
        }
        double x, y, z;
        try {
            x = Double.parseDouble(args[1]);
            y = Double.parseDouble(args[2]);
            z = Double.parseDouble(args[3]);
        } catch (NumberFormatException ex) {
            wrapper.sendMessage(player, "invalidCoords");
            return true;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            wrapper.sendMessage(player, "invalidCoords");
            return true;
        }
        World world = (args.length >= 5)
                ? Bukkit.getWorld(args[4])
                : player.getWorld();
        if (world == null) {
            wrapper.sendMessage(player, "worldNotFound", args[4]);
            return true;
        }
        Integer selectedId = selections.get(player.getUniqueId());
        if (selectedId == null) {
            wrapper.sendMessage(player, "noSelection");
            return true;
        }
        VillagerEntry entry = registry.getById(selectedId);
        if (entry == null) {
            selections.remove(player.getUniqueId());
            wrapper.sendMessage(player, "selectionGone");
            return true;
        }
        Location dest = new Location(world, x, y, z);
        if (!world.getWorldBorder().isInside(dest)) {
            wrapper.sendMessage(player, "outsideWorldBorder");
            return true;
        }
        resolveVillager(entry, villager -> {
            if (villager == null) {
                selections.remove(player.getUniqueId());
                wrapper.sendMessage(player, "selectionGone");
                return;
            }
            foliaLib.getScheduler().runAtEntity(villager, t -> {
                villager.teleportAsync(dest);
                String name = villager.getCustomName() != null ? villager.getCustomName() : "";
                registry.updateLocation(villager.getUniqueId(), dest, name);
                wrapper.sendMessage(player, "villagerMoved", String.valueOf(entry.id()));
            });
        });
        return true;
    }
```

- [ ] **Step 3: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/yusaki/villagertradeedit/VTECommandExecutor.java
git commit -m "feat: implement /vte tp and /vte moveto"
```

---

### Task 12: Implement `/vte list` with pagination + clickable tp

**Files:**
- Modify: `src/main/java/org/yusaki/villagertradeedit/VTECommandExecutor.java`

- [ ] **Step 1: Add Adventure imports**

Add if missing:

```java
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
```

- [ ] **Step 2: Replace `handleList` stub**

```java
    private boolean handleList(Player player, String[] args) {
        if (!player.hasPermission("villagertradeedit.command.list")) {
            wrapper.sendMessage(player, "noPermission");
            return true;
        }
        java.util.List<VillagerEntry> entries = registry.all();
        if (entries.isEmpty()) {
            wrapper.sendMessage(player, "listEmpty");
            return true;
        }
        final int pageSize = 10;
        int totalPages = (entries.size() + pageSize - 1) / pageSize;
        int page = 1;
        if (args.length >= 2) {
            try { page = Math.max(1, Math.min(totalPages, Integer.parseInt(args[1]))); }
            catch (NumberFormatException ignored) {}
        }
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, entries.size());

        wrapper.sendMessage(player, "listHeader", String.valueOf(page), String.valueOf(totalPages));
        for (int i = from; i < to; i++) {
            VillagerEntry e = entries.get(i);
            String coords = String.format("%.1f, %.1f, %.1f", e.x(), e.y(), e.z());
            String nameDisplay = e.name().isEmpty() ? "(unnamed)" : e.name();
            Component row = LegacyComponentSerializer.legacyAmpersand().deserialize(
                    String.format("&e#%d &f%s &7- %s (%s)",
                            e.id(), nameDisplay, e.world(), coords))
                    .clickEvent(ClickEvent.suggestCommand("/vte tp " + e.id()))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("Click to teleport to villager #" + e.id())));
            if (prefixEnabled) {
                player.sendMessage(prefixComponent.append(row));
            } else {
                player.sendMessage(row);
            }
        }
        if (totalPages > 1) {
            wrapper.sendMessage(player, "listFooter",
                    String.valueOf(page), String.valueOf(totalPages), String.valueOf(entries.size()));
        }
        return true;
    }
```

- [ ] **Step 3: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/yusaki/villagertradeedit/VTECommandExecutor.java
git commit -m "feat: implement /vte list with pagination and clickable tp rows"
```

---

### Task 13: Update help output + tab completion

**Files:**
- Modify: `src/main/java/org/yusaki/villagertradeedit/VTECommandExecutor.java`

- [ ] **Step 1: Extend `sendPluginInfo`**

In `sendPluginInfo`, after the existing `/vte reload` line, add (matching the existing style):

```java
        sendPrefixed(player, Component.text(" • /vte select [id]").color(NamedTextColor.YELLOW)
                .append(Component.text(" – Select villager by look-at or ID").color(NamedTextColor.GRAY)));
        sendPrefixed(player, Component.text(" • /vte deselect").color(NamedTextColor.YELLOW)
                .append(Component.text(" – Clear selection").color(NamedTextColor.GRAY)));
        sendPrefixed(player, Component.text(" • /vte tp [id]").color(NamedTextColor.YELLOW)
                .append(Component.text(" – Teleport to selected (or by ID)").color(NamedTextColor.GRAY)));
        sendPrefixed(player, Component.text(" • /vte tphere").color(NamedTextColor.YELLOW)
                .append(Component.text(" – Teleport selected villager to you").color(NamedTextColor.GRAY)));
        sendPrefixed(player, Component.text(" • /vte moveto <x> <y> <z> [world]").color(NamedTextColor.YELLOW)
                .append(Component.text(" – Move selected to coords").color(NamedTextColor.GRAY)));
        sendPrefixed(player, Component.text(" • /vte list [page]").color(NamedTextColor.YELLOW)
                .append(Component.text(" – List managed villagers").color(NamedTextColor.GRAY)));
```

- [ ] **Step 2: Replace `onTabComplete`**

```java
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();
        if (args.length == 1) {
            return List.of("summon", "reload", "select", "deselect", "tp", "tphere", "moveto", "list");
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2 && (sub.equals("select") || sub.equals("tp"))) {
            List<String> ids = new ArrayList<>();
            for (VillagerEntry e : registry.all()) ids.add(String.valueOf(e.id()));
            return ids;
        }
        if (sub.equals("moveto")) {
            if (args.length == 5) {
                List<String> worlds = new ArrayList<>();
                for (World w : Bukkit.getWorlds()) worlds.add(w.getName());
                return worlds;
            }
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }
```

Add import if missing: `import java.util.Collections;`

- [ ] **Step 3: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/yusaki/villagertradeedit/VTECommandExecutor.java
git commit -m "feat: extend help and tab completion for new subcommands"
```

---

### Task 14: Full build + package

**Files:** none

- [ ] **Step 1: Run full package build**

Run: `mvn -q -DskipTests package`
Expected: `BUILD SUCCESS`, jar in `target/`.

- [ ] **Step 2: Inspect jar**

Run: `ls -la target/*.jar`
Expected: shaded jar present.

- [ ] **Step 3: Commit any incidental build artifacts (none expected)**

If there are tracked changes, investigate before committing. Otherwise skip.

---

### Task 15: Manual in-game test plan

**Files:** none (verification step, not code)

This task does not run automatically. Drop the jar from Task 14 into a Paper 1.21.x server's `plugins/` (with YskLib present) and run through the matrix. Mark each step as PASS / FAIL / NOTES in the task list.

- [ ] **Step 1: Fresh start**

  - Server with empty `villagers.yml`.
  - `/vte summon` → expect spawn near you, console log no errors. Inspect `plugins/VillagerTradeEdit/villagers.yml` → entry id 1 present.

- [ ] **Step 2: List**

  - `/vte list` → shows row `#1 (unnamed) - <world> (x, y, z)` with clickable text.
  - Click `[#1 ...]` → suggests `/vte tp 1` in chat box.

- [ ] **Step 3: Select via raytrace**

  - Look at the villager. `/vte select` → "Selected villager #1 ...".

- [ ] **Step 4: Select by ID**

  - `/vte select 1` → same villager.
  - `/vte select 999` → `villagerIdUnknown` message.

- [ ] **Step 5: `tphere`**

  - Walk 20 blocks away. `/vte tphere` → villager appears at your feet. `/vte list` shows updated coords.

- [ ] **Step 6: `tp`**

  - `/vte tp` (with selection) → you teleport to villager. `/vte tp 1` → same.

- [ ] **Step 7: `moveto`**

  - `/vte moveto 0 64 0` → villager moves to those coords. `/vte moveto 0 1000 0` → outside-border check if applicable, otherwise success.
  - `/vte moveto 0 64 0 <secondWorldName>` → cross-world if you have another enabled world.

- [ ] **Step 8: `deselect`**

  - `/vte deselect` → "Selection cleared." `/vte tphere` → `noSelection`.

- [ ] **Step 9: Restart persistence**

  - Stop server. Start again. `/vte list` → entries persist, IDs unchanged.

- [ ] **Step 10: Unloaded-chunk tp**

  - Use `/vte moveto <far coords>` (e.g. 10000 64 10000), then walk back and unload that chunk. `/vte tp 1` → expect chunk loads and player arrives at villager.

- [ ] **Step 11: Existing villager backfill**

  - On a server with managed villagers from prior plugin version (no `vte_id` PDC), load their chunk. `/vte list` shows them after a brief moment (auto-assign via `retrieveVillagerData`), or use `/vte select` raytrace which assigns on demand.

- [ ] **Step 12: Delete**

  - In editor, delete a villager. `/vte list` no longer shows it. `villagers.yml` entry gone after autosave (or restart).

- [ ] **Step 13: World disabled**

  - In `config.yml` `enabled-worlds:` keep one world; in the disabled world, `/vte select` → `disabledWorld`.

- [ ] **Step 14: Folia (optional)**

  - Same matrix on Folia build. Watch console for region-thread violations.

- [ ] **Step 15: Final commit if doc tweaks needed**

  - If any message wording changed during testing, commit those tweaks.

---

### Task 16: Open PR

**Files:** none

- [ ] **Step 1: Push branch**

```bash
git push -u origin feat/vte-move-list
```

- [ ] **Step 2: Open PR**

Use `gh pr create` with title `feat: Citizens-style /vte move and /vte list` and body summarising commands and spec link.
