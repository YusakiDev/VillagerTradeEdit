# VTE Move & List — Citizens-style Villager Management

**Date:** 2026-05-14
**Branch:** `feat/vte-move-list`
**Status:** Design approved

## Summary

Add Citizens-style subcommands to VillagerTradeEdit for selecting, teleporting, and listing managed villagers. Each managed villager receives a stable integer ID (like Citizens NPC IDs) persisted to a registry file. Operators can select a villager (by look-at raytrace or by ID), teleport to/from it, move it to coordinates, and list all managed villagers across enabled worlds — including those in unloaded chunks.

## Goals

- Citizens-parity UX for villager management without depending on Citizens.
- Stable, short integer IDs for managed villagers.
- Persistent registry that survives chunk unloads and server restarts.
- Folia-safe entity access and teleportation.
- Localised messages via existing YskLib message system.

## Non-Goals

- Pathfinding / walk-to behaviour (only instant teleport, like `/npc tphere`/`moveto`).
- Relative coordinate parsing (`~`, `^`) — out of scope, add later if needed.
- ID assignment for unmanaged villagers (only villagers with `static` PDC marker get IDs).
- Backfill IDs eagerly across all worlds at enable. Auto-assign on first load only.

## Commands

| Command | Permission | Behaviour |
|---|---|---|
| `/vte select` | `villagertradeedit.command.move` | Raytrace 10 blocks, select villager player looks at. Auto-assign ID if managed villager has none. |
| `/vte select <id>` | `villagertradeedit.command.move` | Select villager by registry ID. |
| `/vte deselect` | `villagertradeedit.command.move` | Clear selection for player. |
| `/vte tp` | `villagertradeedit.command.move` | Teleport player to selected villager. Load chunk if unloaded. |
| `/vte tphere` | `villagertradeedit.command.move` | Teleport selected villager to player. |
| `/vte moveto <x> <y> <z> [world]` | `villagertradeedit.command.move` | Teleport selected villager to coords. World defaults to player's world. |
| `/vte list [page]` | `villagertradeedit.command.list` | Paginated list (10 per page) of managed villagers across enabled worlds. Each row shows ID, name, world, coords with a clickable `[tp]` suggesting `/vte tp <id>`. |

Existing commands (`/vte summon`, `/vte reload`, `/vte help`) unchanged.

## Architecture

### New Components

**`VillagerRegistry`** (new class)
- In-memory store: `Map<Integer, VillagerEntry>` keyed by ID, plus `Map<UUID, Integer>` reverse index for quick lookup.
- `AtomicInteger nextId` counter.
- Dirty flag with debounced async writes (every 30s + immediate flush onDisable).
- Methods: `assignId(Villager)`, `update(id, location, name)`, `remove(id)`, `getById(id)`, `getByUuid(uuid)`, `all()`, `load()`, `save()`.
- All public methods synchronized; file I/O via `foliaLib.getScheduler().runAsync`.

**`VillagerEntry`** (record)
- Fields: `int id`, `UUID uuid`, `String world`, `double x`, `double y`, `double z`, `String name`, `long lastSeen`.

**`VTECommandExecutor`** (extended)
- New `Map<UUID playerId, Integer villagerId> selections` (in-memory, cleared on `PlayerQuitEvent`).
- Subcommand dispatcher refactored into per-subcommand methods to keep file under 400 lines.

**`VillagerTradeEdit`** (extended)
- New `NamespacedKey vteIdKey` exposed via `getVteIdKey()`.
- Registry instance constructed in `onEnable`, loaded after `YskLib` is ready, saved in `onDisable`.

**`VillagerEditListener`** (extended)
- `EntityRemoveFromWorldEvent`: updates registry entry's location + `lastSeen` when managed villager unloads.
- `retrieveVillagerData`: auto-assigns ID if managed villager lacks one, updates registry.
- Existing delete path (`handleDelete`): removes registry entry.

**`PlayerQuitEvent`** listener (new method on `VillagerEditListener` or new tiny listener): clear player's selection from `selections` map.

### Data File

`plugins/VillagerTradeEdit/villagers.yml`:

```yaml
version: 1
nextId: 5
entries:
  '1':
    uuid: 'aaaa-bbbb-cccc-dddd'
    world: 'world1'
    x: 100.5
    y: 64.0
    z: -200.5
    name: 'Bob'
    lastSeen: 1715000000000
  '2':
    uuid: '...'
    world: 'world2'
    x: 0.5
    y: 70.0
    z: 0.5
    name: ''
    lastSeen: 1715000000000
```

Loaded synchronously in `onEnable` (small file). Saved asynchronously on dirty + onDisable.

### PDC Keys

| Key | Type | Owner | Purpose |
|---|---|---|---|
| `vte_id` (new) | INTEGER | Villager | Stable registry ID |
| `static` (existing) | STRING | Villager | Managed-villager marker |
| `force_spawn` (existing) | BYTE | Villager | Spawn-event suppression |

## Data Flow

### Summon
1. `/vte summon` spawns villager.
2. `activateStaticMode` sets `static` PDC.
3. `VillagerRegistry.assignId(villager)` assigns next ID, writes PDC `vte_id`, adds entry, marks dirty.

### Select (raytrace)
1. `player.rayTraceEntities(eye, dir, 10, e -> e instanceof Villager)`.
2. If hit Villager and has `static` PDC:
   - If no `vte_id`, assign one.
   - Store `selections.put(playerId, id)`.
   - Reply with `villagerSelected` (id, name).
3. Else `noVillagerInSight` or `notManagedVillager`.

### Select by ID
1. Look up `registry.getById(id)`. If missing → `villagerIdUnknown`.
2. Store selection.

### Teleport (tphere, moveto)
1. `entry = registry.getById(selectedId)`. If null → `noSelection` or `villagerIdUnknown`.
2. `world = Bukkit.getWorld(entry.world)`. If null → `worldNotFound`.
3. Validate destination world border (`outsideWorldBorder` if violated).
4. Resolve entity:
   - `Entity e = Bukkit.getEntity(entry.uuid)`.
   - If null (Folia cross-region or unloaded): `world.getChunkAtAsync(entry.chunkX, entry.chunkZ).thenAccept(c -> { retry Bukkit.getEntity })`.
5. On Folia: `foliaLib.getScheduler().runAtEntity(villager, t -> villager.teleportAsync(dest))`.
6. Update registry entry location, mark dirty.

### Teleport player to villager (`/vte tp`)
1. Resolve entry → location.
2. Load chunk via `getChunkAtAsync` if needed.
3. `foliaLib.getScheduler().runAtLocation(loc, t -> player.teleportAsync(loc))`.

### List
1. Pure registry read — no entity access.
2. Filter by `wrapper.canExecuteInWorld` per entry world.
3. Sort by ID.
4. Paginate (10/page) and render with clickable `[tp]` components.

### Removal
1. `handleDelete` in `VillagerEditListener` calls `registry.remove(id)`.
2. `EntityRemoveFromWorldEvent` does NOT remove from registry (unload ≠ delete) — only updates `lastSeen` and location.

## Folia Considerations

| Operation | Strategy |
|---|---|
| Entity teleport | `teleportAsync` from entity's region via `runAtEntity` |
| Player teleport | `teleportAsync` from player's region (default scheduler context) |
| Cross-region entity lookup | `Bukkit.getEntity(uuid)` may return null; chunk-load then retry |
| Registry I/O | `runAsync` for writes; synchronous load only at enable |
| Registry mutation | Synchronized methods on registry; safe from any region |

## Error Handling

New message keys in `config.yml` under `messages:`:

- `noVillagerInSight` — raytrace miss.
- `notManagedVillager` (existing) — raytrace hit unmanaged.
- `villagerIdUnknown` — invalid ID for select/tp.
- `noSelection` — tp/tphere/moveto without selection.
- `selectionUnloaded` — villager entity not currently available; chunk load attempted.
- `selectionGone` — selected villager no longer exists (dead/removed); selection cleared.
- `invalidCoords` — non-numeric / NaN / infinity coords.
- `worldNotFound` — bad world name.
- `outsideWorldBorder` — destination outside border.
- `villagerSelected` — success on select, with `{0}=id` `{1}=name`.
- `villagerMoved` — success on teleport, with `{0}=id`.
- `playerTeleported` — success on `/vte tp`, with `{0}=id`.
- `selectionCleared` — success on deselect.
- `listHeader` — `&6Managed Villagers (page {0}/{1}):`
- `listEntry` — `&e#{0} &f{1} &7- {2} ({3}, {4}, {5})` — id, name, world, x, y, z.
- `listFooter` — `&7Page {0}/{1} - {2} total` (omitted on single page).
- `listEmpty` — `&7No managed villagers.`

Config schema version bumped (e.g. 3.1 → 4.0) so YskLib migration adds new keys without overwriting user customisations.

## Permissions

Added to `plugin.yml`:

```yaml
villagertradeedit.command.move:
  description: Allows selecting, teleporting, and moving managed villagers
  default: op
villagertradeedit.command.list:
  description: Allows listing managed villagers
  default: op
```

## Help Output

`sendPluginInfo` extended with new lines:

```
• /vte select [id]   – Select managed villager (look-at or by ID)
• /vte deselect      – Clear selection
• /vte tp            – Teleport to selected villager
• /vte tphere        – Teleport selected villager to you
• /vte moveto <x> <y> <z> [world] – Move selected to coords
• /vte list [page]   – List managed villagers
```

Tab-completion extended:
- Arg 1: existing `summon`/`reload` + `select`/`deselect`/`tp`/`tphere`/`moveto`/`list`.
- After `select`: registry IDs.
- After `moveto`: empty (numbers) → after 3 coords: world names.

## File Layout

```
src/main/java/org/yusaki/villagertradeedit/
  VillagerTradeEdit.java          (extended: keys, registry wiring)
  VTECommandExecutor.java         (extended: subcommands, selections map)
  VillagerEditListener.java       (extended: ID assign on restore, registry update on remove)
  VillagerRegistry.java           (NEW)
  VillagerEntry.java              (NEW — record)
  SelectionListener.java          (NEW — small listener for PlayerQuitEvent)
src/main/resources/
  config.yml                      (new message keys, version bump)
  plugin.yml                      (new permissions)
```

## Test Plan (Manual)

1. Fresh start: `/vte summon` → ID assigned, `villagers.yml` written with entry.
2. `/vte list` → shows villager with id 1, world, coords, clickable tp.
3. `/vte select` look at villager → "Selected Villager #1 (name)".
4. `/vte select 1` → same villager selected.
5. `/vte tphere` → villager teleports to player position. Registry updates.
6. `/vte tp` → player teleports to villager position.
7. `/vte moveto 0 64 0` → villager teleports to coords. `/vte moveto 0 64 0 world2` → cross-world.
8. `/vte deselect` → selection cleared. `/vte tphere` → `noSelection` message.
9. Server restart → `villagers.yml` reloaded, IDs persist, `/vte list` works.
10. Unload chunk (move far away): `/vte tp 1` → chunk loads, teleport succeeds.
11. Existing pre-feature managed villager: load chunk, no ID. `/vte select` raytrace → ID auto-assigned, persisted.
12. Delete villager via editor → registry entry removed, `/vte list` no longer shows it.
13. World disabled: `/vte select` in disabled world → `disabledWorld` message.
14. Folia build: re-run steps 1-12.

### Edge Cases

- Selected villager killed/removed → next `/vte tp`/`tphere` shows `selectionGone`, selection cleared.
- Coord parse: `NaN`/`Infinity`/non-numeric → `invalidCoords`.
- World border violation → `outsideWorldBorder`.
- `/vte list` on empty registry → `listEmpty`.
- Two players selecting same villager → independent selections, both work.
- ID counter overflow: int max is 2.1B, ignore.

## Open Decisions Deferred

- Relative coords (`~`, `^`) in `moveto` — punt to later if requested.
- Bulk operations (`/vte tphere @e[id=1..5]`) — punt.
- GUI list (chest inventory of heads) — punt.
- ID reuse after delete — keep monotonic for now.
