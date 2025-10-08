# VillagerTradeEdit

VillagerTradeEdit lets administrators curate villager trades safely on modern Paper and Folia servers. The plugin focuses on Folia-friendly scheduling, per-player trade sessions, and built-in safeguards against vanilla discount exploits.

## Supported Software Versions
- Paper 1.21.x
- Folia 1.21.x

Earlier releases may still work, but only the versions above are exercised alongside the current codebase.

## Features
- Summon plugin-managed "static" villagers that stay centered, invulnerable, and collidable-free.
- Shift-right-click managed villagers to open the editor; normal right-click opens an individualized merchant window so multiple players can trade simultaneously.
- Configure per-villager trade permissions, professions, names (MiniMessage or legacy colors), and paginated trade recipes.
- Neutralizes Hero of the Village, reputation, and demand discounts only on managed villagers while preserving vanilla behavior for others.
- Automatically saves trade changes on inventory close and rehydrates villagers as chunks load.
- Built with FoliaLib to keep every villager interaction region-thread safe.

## Requirements
- A compatible YskLib plugin jar (`>= 1.6.4`) installed alongside VillagerTradeEdit.
- Java 21 runtime.
- Paper or Folia server 1.21.x.

## Installation
1. Download or build the latest `VillagerTradeEdit-<version>.jar` (see building instructions below).
2. Place the jar in your server's `plugins/` directory.
3. Ensure `YskLib-1.6.4.jar` (or newer) is also present in `plugins/`.
4. Restart or start the server.

## Building from Source
1. Obtain `YskLib-1.6.4.jar` locally and update the `systemPath` in `pom.xml` if your path differs from the default.
2. Run `mvn clean package` to produce a shaded jar in `target/`.
3. Copy the generated jar to your server's `plugins/` directory.

## Commands and Interactions
- `/vte` – Shows plugin information and usage hints.
- `/vte summon` – Spawns a managed static villager in front of the player.
- `/vte reload` – Reloads the plugin configuration.
- Right-click a managed villager – Opens your personal trading menu respecting any per-villager permission.
- Shift-right-click a managed villager – Opens the trade editor GUI.

### Permissions
- `villagertradeedit.command` – Required for any `/vte` subcommand.
- `villagertradeedit.command.summon` – Allows `/vte summon`.
- `villagertradeedit.command.reload` – Allows `/vte reload`.
- `villagertradeedit.open` – Allows opening the editor via shift-right-click.

## Configuration
The first run creates `plugins/VillagerTradeEdit/config.yml` with:
- `debug` – Enables verbose logging when `true`.
- `version` – Internal config schema indicator; do not change manually.
- `enabled-worlds` – Worlds where the plugin may operate.
- `messages.*` – Chat prefix and localized messages used by YskLib.

Update the configuration, then use `/vte reload` to apply changes.

## Support
Open an issue or discussion on GitHub if you encounter problems, have feature requests, or would like to contribute.

## License
VillagerTradeEdit is distributed under the GNU General Public License v3.0. See [`LICENSE`](LICENSE) for full terms.
