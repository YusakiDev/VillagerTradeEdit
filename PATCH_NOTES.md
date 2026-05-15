# VillagerTradeEdit Patch Notes

## Version 2.1.0

**Citizens-style management commands for managed villagers.**

- `/vte select [id]` — select a managed villager by raytrace or numeric ID.
- `/vte deselect` — clear current selection.
- `/vte tp [id]` — teleport to a managed villager (current selection or by ID).
- `/vte tphere` — teleport the selected villager to you (chunk-load aware, Folia-safe).
- `/vte moveto <x> <y> <z> [world]` — move the selected villager to coordinates.
- `/vte list [page]` — paginated list of managed villagers with clickable teleport.
- Persistent integer IDs assigned lazily on chunk load via new `vte_id` PDC key, indexed in `villagers.yml`. No eager backfill; existing managed villagers get IDs on first load.
- Fixed managed villagers being affected by gravity. New summons and existing managed villagers (on next chunk reload) now get `setGravity(false)` alongside `setAware(false)` and `setInvulnerable(true)`.
- Folia-safe teleportation throughout: `teleportAsync`, region-scheduler dispatch on entity and target location, chunk preload via `getChunkAtAsync`.
- Per-subcommand permissions: `villagertradeedit.command.select`, `.move`, `.list`. Tab completion gated by perms.
- `villagers.yml` is auto-generated with a "DO NOT EDIT" header. Use plugin commands instead.

## Version 2.0.1

- Defensive `deserializeMerchantRecipes`: handles empty/blank, bad base64, and truncated PDC data without crashing entity load. Legacy wiped trades now load as an empty list instead of throwing `IllegalStateException: Unable to load trades` (caused by `EOFException`).
- Per-recipe error isolation during deserialize. `Material` enum drift or class signature changes across Minecraft versions now skip the affected recipe and continue, instead of failing the whole villager's trade list. Truncation / stream corruption returns the recipes loaded so far.
- Fixed `ClassCastException` opening per-player merchant on Paper 26.1. `CraftHumanEntity.openMerchant` no longer accepts `Bukkit.createMerchant()` outputs (casts Merchant → CraftEntity). Now opens the villager directly; `onInventoryOpen` still neutralizes discounts for managed villagers.
- Fixed `TickThread` "Cannot init menu async" when opening trade editor. `player.openInventory` now runs on the player's region scheduler thread (required by Folia/Canvas 26.1).

## Version 2.0.0

**Minecraft 26.1.2 compatibility update. Drops support for 1.21.x.**

- Targets Paper API `26.1.2.build.61-stable`. Older Paper builds are no longer supported.
- Build now requires JDK 25 (matches Paper 26.1's runtime).
- Migrated `Villager.Profession.valueOf` and `Villager.Type.valueOf` to `Registry.VILLAGER_PROFESSION` / `Registry.VILLAGER_TYPE` lookups. Profession and type are interfaces in 26.1, no longer enums.
- Stored profession/type names switched from uppercase enum names (e.g. `FARMER`) to lowercase registry keys (`farmer`). Existing villagers from 1.4.x are read seamlessly via case-insensitive fallback.
- Migrated `AsyncPlayerChatEvent` to Paper's `AsyncChatEvent` for permission and name prompts. Adventure `Component`-based message extraction.
- Bumped FoliaLib to `0.5.2` and YskLib to `1.9.0` (both required for 26.1 compat).

## Version 1.4.10
- Renaming a villager no longer wipes its trades. Previously, using "Set Name" or "Set Permission" in the editor erased every trade on the villager.
- Setting a villager's trade permission no longer wipes its trades for the same reason.
- Fixed the chat prefix showing `Message not found: prefix` on every plugin message after updating to YskLib 1.7.x. The plugin now reads the prefix through YskLib's proper API.
- Editor state is now preserved when the GUI briefly closes for chat input. You return to the same page with the same trades intact.
