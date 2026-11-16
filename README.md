# Personal Beacon

**Personal Beacon** is an advanced access management mod for Minecraft 1.20.1. It allows players to customize which entities receive Beacon effects through a dedicated whitelist system, seamlessly integrated into the vanilla interface.

## Features

  * **Granular Access Control:** Restrict Beacon effects to specific players via an integrated list.
  * **Integrated GUI:** Manage player access directly through a custom button injected into the vanilla Beacon interface.
  * **Access Screen:** Player heads with online/offline status, a padlock switch per player (ON / OFF / OWNER), search, and sorting by name, access or online status. Toggling a player never moves their row.
  * **Custom Textures:** The panel background (`custom_beacon_gui`, 300×240), the beacon icon (`beacon_icon`, 43×43) and the Manage Access button (`button.png` and `button_selected.png`, 11×11) are PNG textures that resource packs can replace. They are drawn 1:1, so replacements should keep these sizes.
  * **Multi-Language Support:** Fully translated into English, Turkish, French, and Spanish.
  * **Smart Fallback:** If no restrictions are configured for a given Beacon, it defaults to vanilla behavior (all players within range receive effects).
  * **Automatic State Cleanup:** When a Beacon is removed from the world (broken, blown up, or replaced by commands), its access data is automatically purged. Unloading a chunk keeps the data.
  * **Per-Dimension Data:** Beacons at the same coordinates in the Overworld, the Nether and the End have separate access lists.
  * **Your Beacons:** `/personalbeacon mine` lists the beacons you own or co-manage, for every player.
  * **Mod Menu Settings:** With [Mod Menu](https://modrinth.com/mod/modmenu) installed, the settings can be changed in-game (optional).
  * **Distance Validation:** Configurable interaction range (`maxManageDistance`) prevents exploitation and remote management on multiplayer servers.

-----

## Technical Specifications

| Component | Specification |
| :--- | :--- |
| **Minecraft Version** | 1.20.1 |
| **Mod Loader** | Fabric |
| **Java Version** | 17 (Runtime) / 21 (Development) |
| **Dependencies** | Fabric API (required), Mod Menu (optional) |
| **Configuration** | `config/personalbeacon.json` |

### Architecture Overview

The mod utilizes `PersistentState` for server-side data persistence (`Map<BeaconPos, Set<UUID>>`, where `BeaconPos` is the dimension plus the block position) and a custom packet system (`ModNetworking`) for client-server synchronization.

  * **Mixins:** Extends vanilla behavior by filtering the player list in `BeaconBlockEntity.applyPlayerEffects` (MixinExtras `@ModifyExpressionValue`, chains with other mods) and injecting into `BeaconScreen`. The beacon position is resolved server-side from the player's open `BeaconScreenHandler`.
  * **Networking:** Implements channels (`C2S_UPDATE_ACCESS`, `S2C_SYNC_ACCESS`, `C2S_REQUEST_SYNC`, `S2C_TEST_MODE`) for real-time state synchronization and GUI updates.

-----

## Localization

| Language | File |
| :--- | :--- |
| English | `lang/en_us.json` |
| Turkish | `lang/tr_tr.json` |
| French | `lang/fr_fr.json` |
| Spanish | `lang/es_es.json` |

All user-facing strings use `Text.translatable()`. To add a new language, create a new JSON file in `assets/personalbeacon/lang/` following the same key structure.

-----

## Configuration

The mod behavior can be modified via `config/personalbeacon.json`, or in-game from Mod Menu (Mods → Personal Beacon → settings button). The in-game screen edits this game's file, so it applies to the worlds you host (singleplayer, LAN). A dedicated server reads its own file.

  * `maxManageDistance`: Maximum block distance allowed for a player to modify a Beacon's access list on a dedicated server (Default: 8.0, 0 = no limit). Singleplayer/LAN worlds skip this check.
  * `allowNonOpManagement`: When `true` (default), non-operator players can manage beacons — only the owners once a beacon has an owner. When `false`, only operators can manage beacons.

-----

## Commands

### Player Commands

  * `/personalbeacon mine` - Lists the beacons you own or co-manage: coordinates (click to copy), dimension, access and your role.

### Server Commands (OP Level 2)

Coordinates are in the dimension the command runs in (use `/execute in <dimension> run ...` for another one).


  * `/personalbeacon help` - Displays the command help menu.
  * `/personalbeacon debug` - Lists all restricted beacons with owners and players.
  * `/personalbeacon add <player> <x> <y> <z>` - Add a player to a beacon's access list.
  * `/personalbeacon remove <player> <x> <y> <z>` - Remove a player from a beacon's access list.
  * `/personalbeacon setowner <player> <x> <y> <z>` - Set the owner of a beacon.
  * `/personalbeacon clear <x> <y> <z>` - Deletes all access data for a specific beacon.
  * `/personalbeacon test` - Toggles the singleplayer restriction bypass.

-----

## Known Issues & Technical Constraints

  * **State Volatility:** The `/personalbeacon test` mode toggle is volatile. It defaults to `false` upon server restart.
  * **Data saved before v2.2.0** has no dimension. Each such beacon moves to the dimension of the first beacon at its position that is used (its effects tick, its screen opens, it is broken or named in a command). Until then, `/personalbeacon mine` shows it as "unknown dimension".

-----

## Installation

1.  Install the [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.20.1.
2.  Place the `fabric-api` jar into your `mods` directory.
3.  Place the `personalbeacon.jar` into your `mods` directory.
4.  Launch the client or server.

-----

## Roadmap

  * Modrinth publication refinements (including project icons).
  * Additional language support.

-----

## AI

  * AI was used in 90% of the coding for this project.

-----

## License

This project is licensed under the MIT License.
