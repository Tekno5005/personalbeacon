# Personal Beacon

**Personal Beacon** is an advanced access management mod for Minecraft 1.20.1. It allows players to customize which entities receive Beacon effects through a dedicated whitelist system, seamlessly integrated into the vanilla interface.

## Features

  * **Granular Access Control:** Restrict Beacon effects to specific players via an integrated list.
  * **Integrated GUI:** Manage player access directly through a custom button injected into the vanilla Beacon interface.
<<<<<<< HEAD
=======
  * **Custom Texture System:** All GUI panels and buttons use custom PNG textures (`window1`, `window2`, `window3`, `Button.png`) that can be replaced by resource packs.
  * **In-Game GUI Editor:** Reposition and resize every UI element live with the built-in layout editor — no restarts needed.
  * **Multi-Language Support:** Fully translated into English, Turkish, French, and Spanish.
>>>>>>> a032e3d (release: v1.1.0 — GUI editor fixes, window3 texture, i18n, CI fix)
  * **Smart Fallback:** If no restrictions are configured for a given Beacon, it defaults to vanilla behavior (all players within range receive effects).
  * **Automatic State Cleanup:** When a Beacon is broken, its associated access data is automatically purged from the world data to prevent memory leaks and state bloat.
  * **Softlock Prevention:** In singleplayer worlds, the first interaction automatically adds the player to the access list to prevent accidental self-exclusion.
  * **Distance Validation:** Configurable interaction range (`maxManageDistance`) prevents exploitation and remote management on multiplayer servers.

-----

## Technical Specifications

| Component | Specification |
| :--- | :--- |
| **Minecraft Version** | 1.20.1 |
| **Mod Loader** | Fabric |
| **Java Version** | 17 (Runtime) / 21 (Development) |
| **Dependencies** | Fabric API |
| **Configuration** | `config/personalbeacon.json` |
<<<<<<< HEAD
=======
| **GUI Layout** | `config/personalbeacon_gui_layout.json` |
>>>>>>> a032e3d (release: v1.1.0 — GUI editor fixes, window3 texture, i18n, CI fix)

### Architecture Overview

The mod utilizes `PersistentState` for server-side data persistence (`Map<BlockPos, Set<UUID>>`) and a custom packet system (`ModNetworking`) for client-server synchronization.

  * **Mixins:** Extends vanilla behavior by injecting into `BeaconBlockEntity` and `BeaconScreen`.
  * **Networking:** Implements channels (`C2S_UPDATE_ACCESS`, `S2C_SYNC_ACCESS`, `C2S_REQUEST_SYNC`, `S2C_TEST_MODE`) for real-time state synchronization and GUI updates.

-----

<<<<<<< HEAD
=======
## GUI Layout Editor

The mod includes a built-in GUI editor for repositioning all UI elements without editing JSON files manually.

### How to Use

1. Run `/personalbeaconop edit` in chat to unlock the editor.
2. Open any Beacon — you will see a **⚙** button on both the vanilla Beacon screen and the Access Control screen.
3. Click **⚙** to open the editor. Select any element from the left panel and adjust its properties on the right.
4. Click **Save & Close** — changes apply immediately.

### Editable Elements

Windows (`window1`, `window2`, `window3`), text positions (title, coordinates, owner, column headers), all buttons (close, add players, scroll, toggle, manage access), and the Manage Access button on the vanilla Beacon screen.

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

>>>>>>> a032e3d (release: v1.1.0 — GUI editor fixes, window3 texture, i18n, CI fix)
## Configuration

The mod behavior can be modified via `config/personalbeacon.json`:

  * `maxPlayersPerBeacon`: Maximum number of players allowed on a single Beacon's list (0 = Unlimited).
  * `maxManageDistance`: Maximum block distance allowed for a player to modify a Beacon's access list (Default: 8.0).
  * `allowNonOpManagement`: Determines if non-operator players can manage the Beacons they interact with.
  * `skipDistanceCheckInSingleplayer`: Bypasses distance validation in singleplayer environments.

-----

## Commands

<<<<<<< HEAD
Requires OP Level 2 for execution:

  * `/personalbeacon help` - Displays the command help menu.
  * `/personalbeacon debug` - Logs technical state data to the console.
  * `/personalbeacon clear <x> <y> <z>` - Deletes the PersistentState data for a specific coordinate.
  * `/personalbeacon test` - Toggles the singleplayer restriction bypass.

=======
### Server Commands (OP Level 2)

  * `/personalbeacon help` - Displays the command help menu.
  * `/personalbeacon debug` - Lists all restricted beacons with owners and players.
  * `/personalbeacon add <player> <x> <y> <z>` - Add a player to a beacon's access list.
  * `/personalbeacon remove <player> <x> <y> <z>` - Remove a player from a beacon's access list.
  * `/personalbeacon setowner <player> <x> <y> <z>` - Set the owner of a beacon.
  * `/personalbeacon clear <x> <y> <z>` - Deletes all access data for a specific beacon.
  * `/personalbeacon test` - Toggles the singleplayer restriction bypass.

### Client Commands

  * `/personalbeaconop edit` - Toggles the GUI Layout Editor (shows/hides the ⚙ button).

>>>>>>> a032e3d (release: v1.1.0 — GUI editor fixes, window3 texture, i18n, CI fix)
-----

## Known Issues & Technical Constraints

<<<<<<< HEAD
  * **Mixin Conflicts:** `BeaconBlockEntityMixin` utilizes `@Inject(at = @At("HEAD"), cancellable = true)`. This early injection and cancellation may cause compatibility issues with other mods that heavily modify Beacon logic or effect distribution (e.g., Create, though currently untested).
=======
  * **Mixin Conflicts:** `BeaconBlockEntityMixin` utilizes `@Inject(at = @At("HEAD"), cancellable = true)`. This early injection and cancellation may cause compatibility issues with other mods that heavily modify Beacon logic or effect distribution.
>>>>>>> a032e3d (release: v1.1.0 — GUI editor fixes, window3 texture, i18n, CI fix)
  * **Accessor Implementation:** `BeaconScreenHandlerAccessor` is implemented as a class mixin rather than an interface. To prevent Local Variable Table (LVT) conflicts, a utility class (`BeaconPosHolder`) is actively used.
  * **State Volatility:** The `/personalbeacon test` mode toggle is volatile. It defaults to `false` upon server restart as defined by `testModeDefault`.

-----

## Installation

1.  Install the [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.20.1.
2.  Place the `fabric-api` jar into your `mods` directory.
3.  Place the `personalbeacon.jar` into your `mods` directory.
4.  Launch the client or server.

-----

## Roadmap

<<<<<<< HEAD
  * Offline player support via Mojang API UUID resolution.
  * Strict Beacon ownership system (restricting management strictly to the block placer).
  * GUI search functionality for large server environments.
  * Modrinth publication refinements (including project icons).
=======
  * GUI search functionality for large server environments.
  * Modrinth publication refinements (including project icons).
  * Additional language support.
>>>>>>> a032e3d (release: v1.1.0 — GUI editor fixes, window3 texture, i18n, CI fix)

-----

## License

This project is licensed under the MIT License.
