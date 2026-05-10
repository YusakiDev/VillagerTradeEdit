# VillagerTradeEdit Patch Notes

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
