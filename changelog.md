## 2.2.0

- Add `mute-villager-sound` config option to silence managed villagers' "hmm"/trade ambient sounds via `Entity#setSilent`. Applied on spawn and on chunk/entity load.
- Fix managed villagers being shoved by creeper/TNT explosions: `setInvulnerable` blocks damage but not knockback, so explosion knockback is now cancelled via `EntityKnockbackEvent`.
- Fix `UTFDataFormatException` when saving villagers with large trade payloads. Trade data now stored as PDC `BYTE_ARRAY` to bypass NBT `writeUTF` 65535-byte cap.
- Legacy `STRING` trade key retained as read fallback so 2.1.0 villagers load cleanly.
- Skip `onDisable` PDC save loop on Folia to avoid off-region `TickThread` violations; periodic auto-save still covers persistence.
