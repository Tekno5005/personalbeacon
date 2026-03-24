# Personal Beacon

**Personal Beacon** is an advanced access management mod for Minecraft 1.20.1. It allows players to customize which entities receive Beacon effects through a dedicated whitelist system, seamlessly integrated into the vanilla interface.

## Features

  * **Granular Access Control:** Restrict Beacon effects to specific players via an integrated list.
  * **Integrated GUI:** Manage player access directly through a custom button injected into the vanilla Beacon interface.
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

### Architecture Overview

The mod utilizes `PersistentState` for server-side data persistence (`Map<BlockPos, Set<UUID>>`) and a custom packet system (`ModNetworking`) for client-server synchronization.

  * **Mixins:** Extends vanilla behavior by injecting into `BeaconBlockEntity` and `BeaconScreen`.
  * **Networking:** Implements channels (`C2S_UPDATE_ACCESS`, `S2C_SYNC_ACCESS`, `C2S_REQUEST_SYNC`, `S2C_TEST_MODE`) for real-time state synchronization and GUI updates.

-----

## Configuration

The mod behavior can be modified via `config/personalbeacon.json`:

  * `maxPlayersPerBeacon`: Maximum number of players allowed on a single Beacon's list (0 = Unlimited).
  * `maxManageDistance`: Maximum block distance allowed for a player to modify a Beacon's access list (Default: 8.0).
  * `allowNonOpManagement`: Determines if non-operator players can manage the Beacons they interact with.
  * `skipDistanceCheckInSingleplayer`: Bypasses distance validation in singleplayer environments.

-----

## Commands

Requires OP Level 2 for execution:

  * `/personalbeacon help` - Displays the command help menu.
  * `/personalbeacon debug` - Logs technical state data to the console.
  * `/personalbeacon clear <x> <y> <z>` - Deletes the PersistentState data for a specific coordinate.
  * `/personalbeacon test` - Toggles the singleplayer restriction bypass.

-----

## Known Issues & Technical Constraints

  * **Mixin Conflicts:** `BeaconBlockEntityMixin` utilizes `@Inject(at = @At("HEAD"), cancellable = true)`. This early injection and cancellation may cause compatibility issues with other mods that heavily modify Beacon logic or effect distribution (e.g., Create, though currently untested).
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

  * Offline player support via Mojang API UUID resolution.
  * Strict Beacon ownership system (restricting management strictly to the block placer).
  * GUI search functionality for large server environments.
  * Modrinth publication refinements (including project icons).

-----

## License

This project is licensed under the MIT License.
