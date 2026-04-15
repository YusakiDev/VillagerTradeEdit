# VillagerTradeEdit Patch Notes

## Version 1.4.10
- Renaming a villager no longer wipes its trades. Previously, using "Set Name" or "Set Permission" in the editor erased every trade on the villager.
- Setting a villager's trade permission no longer wipes its trades for the same reason.
- Fixed the chat prefix showing `Message not found: prefix` on every plugin message after updating to YskLib 1.7.x. The plugin now reads the prefix through YskLib's proper API.
- Editor state is now preserved when the GUI briefly closes for chat input. You return to the same page with the same trades intact.
