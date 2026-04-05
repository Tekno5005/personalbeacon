# Personal Beacon — Changelog

All notable changes to this project will be documented in this file.
Format follows [Semantic Versioning](https://semver.org/).

-----

## v1.2.0 — 2026-04-05

### Added
- **"Open to All" / "Restrict Access" flow** — Beacon screen now has two distinct states. When unrestricted, `window2` is shown with an *"Open to Everyone"* message and a **Restrict Access** button. When restricted, an **Open to All** button appears at the bottom-left; clicking it sends the new `C2S_UNRESTRICT` packet, clears all restrictions on the server, and returns to the unrestricted view.
- **Action bar feedback** — Every access toggle (✔/✘) and confirmed owner change (★/☆) now displays a short message in the native Minecraft action bar (above the hotbar) so players always know their action was applied.
- **Owner toggle pending state** — Clicking ★/☆ immediately disables the button and shows `…` while the server processes the request. The button updates to its final state only after the S2C confirmation arrives, eliminating the "did it work?" confusion.
- **Context description line** — A subtle one-line hint *"Only listed players receive this beacon's effects."* is now shown below the header on every multiplayer screen, giving first-time users immediate context.
- **Empty list message** — When no players are present in the display list (no online players and no allowed players), the scroll buttons are hidden and a friendly *"No players nearby / Server players will appear here."* message is shown instead.
- **Improved tooltips** — Access toggle now reads *"Receiving effects — click to remove"* / *"Not receiving effects — click to add"*. Owner star reads *"Co-manager — can add/remove players. Click to remove."* / *"Make co-manager — this player can also manage the access list."*
- **`C2S_UNRESTRICT` network packet** — New server-side handler validates distance and owner permissions before clearing `allowedPlayers` for the beacon and sending a fresh sync.
- **`unrestrictBeacon()` API** — Added to both `BeaconAccessData` and `BeaconAccessManager`. Clears the allowed-player list while preserving owner data.
- New `openToAllButton` and `restrictAccessButton` entries in `GuiLayoutConfig` (fully configurable via `personalbeacon_gui_layout.json`).
- New translation keys in all four languages (EN / TR / ES / FR): `unrestricted_title`, `unrestricted_desc`, `open_to_all`, `restrict_access`, `owner_pending`, `no_players_nearby`, `no_players_hint`, `owner_always_allowed`, and all `personalbeacon.feedback.*` keys.

### Changed
- **Owner rows — Allow/Block button disabled** — Rows belonging to an owner (primary or co-manager) now render the access toggle in a permanently disabled state with the tooltip *"Owners always receive beacon effects"*, preventing accidental removal of their access.
- **`getOwner()` deprecated** — Both `BeaconAccessData.getOwner()` and `BeaconAccessManager.getOwner()` are now `@Deprecated` and delegate to `getPrimaryOwner()`. Existing call sites still work; migrate to `getPrimaryOwner()` going forward.

### Fixed
- **`BeaconAccessData.toNbt()` data loss** — When all allowed players were removed from a beacon, `allowedPlayers.remove(pos)` left the entry absent from the map. `toNbt()` only iterated `allowedPlayers`, so owner and co-manager data were silently lost on server restart. Fixed by iterating over the union of `allowedPlayers`, `beaconOwners`, and `primaryOwners` key sets.
- **Thread safety — `testModeEnabled`** — Field was a plain `boolean` read from the client thread and written from the server thread. Changed to `volatile boolean`.
- **Unnecessary `markDirty()` on name cache** — `sendSyncPacket()` called `manager.cachePlayerName()` for every online player on every beacon-screen open, triggering a disk write each time regardless of whether any name had changed. `BeaconAccessData.cachePlayerName()` now returns `true` only when the stored value actually changed; `BeaconAccessManager` calls `markDirty()` conditionally.
- **`Math.sqrt()` in distance check** — `isPlayerNearBeacon()` called `Math.sqrt(squaredDist) <= maxDist`, which is equivalent to `squaredDist <= maxDist * maxDist` but wastes a square-root computation per packet. Replaced with the squared comparison.
- **`isOnline()` O(n²) complexity** — `rebuildDisplayList()` called `isOnline(uuid)` for every allowed player, and each call iterated the full `NetworkHandler.getPlayerList()`. A single `Set<UUID>` is now built once before the loop, reducing the check to O(1) per entry.
- **`listExpanded` dead code removed** — The field was set to `true` unconditionally in `init()`, making all `!listExpanded` branches unreachable. The field, the empty-state box render block, and the now-unused `addPlayersButton` branch in `rebuildButtons()` have been removed.

-----

## v1.1.3 — 2026-03-28

### Fixed
- **GUI layout defaults fully synced** — `titleText`, `coordsText`, `ownerText` offsets and scales, `window1`/`window2` positions, and `window3` width were still using stale hardcoded values in `GuiLayoutConfig.java`. All defaults now match the tuned layout so fresh installs no longer require a manual config file.

-----

## v1.1.2 — 2026-03-28

### Fixed
- **Broken texture on Manage Access button** — `Button.png` / `Button_selected.png` renamed to lowercase (`button.png` / `button_selected.png`) to match the `Identifier` paths in `TextureButtonWidget`. JAR files are case-sensitive (ZIP format), so the uppercase names caused missing textures in the released build while working fine in the dev environment (Windows case-insensitive FS).
- **GUI layout reverting to defaults on fresh installs** — Default values in `GuiLayoutConfig.java` were out of sync with the tuned layout. Updated all defaults to match: `columnPlayer` scale, `columnAccess` offsetX, `addPlayersButton` offsetX, `scrollUpButton/Down` offsetX, `toggleButton` dimensions, `manageAccessButton` position and size, `singleplayerTitle/Desc` offsets and colors.

-----

## v1.1.1 — 2026-03-28

### Changed
- **Responsive GUI layout** — `BeaconAccessScreen` panel now scales down proportionally on screens smaller than 340×280 px. All button positions, texture sizes, row heights, and text positions use a `panelScale` factor computed from available screen space. On standard resolutions behaviour is identical to before.
- **Dynamic visible rows** — Row count is now derived from the configured scroll button positions instead of a hardcoded constant (`VISIBLE_ROWS = 7`), so it stays correct when the GUI layout is customised.
- **Logging levels** — Per-tick player effect checks demoted from `INFO` to `DEBUG` (were firing every few seconds per player). Packet flow, data mutations, and beacon interactions now log at `DEBUG`. Silent `catch (Exception ignored)` blocks in `GuiLayoutConfig` replaced with `WARN` logging including the exception.
- **Log prefix cleanup** — Redundant `[PersonalBeacon]` prefix removed from log messages (the SLF4J logger name already identifies the mod).

### Added
- **Unit tests** — `BeaconAccessDataTest` covers 15 scenarios: unrestricted baseline, `addPlayer`/`removePlayer`/`removeBeacon` lifecycle, owner management, player name cache, `getAllowedPlayers` immutability, full NBT round-trips (empty, single beacon, multiple beacons, owner, offline name cache, negative coordinates), and `fromNbt` malformed UUID handling.
- **JUnit 5** (`junit-jupiter 5.10.2`) added to `testImplementation`; run with `./gradlew test`.

-----

## v1.1.0 — 2026-03-26

### Added
- **Player card texture (`window3.png`)** — Each player row now renders with a custom texture background instead of flat color fills.
- **GUI Layout Editor on Beacon Screen** — The ⚙ editor button now appears directly on the vanilla Beacon screen (when unlocked), allowing live repositioning of the Manage Access button.
- **`window3` in GUI Editor** — The new player card texture is fully configurable (position, size) through the in-game editor.
- **Localization for owner & view-only text** — `"★ Owner: ..."` and `"⚠ View only"` strings now use translation keys (`personalbeacon.screen.owner`, `personalbeacon.screen.view_only`) across all supported languages.

### Fixed
- **GUI Editor index mismatch** — Adding `window3` caused all editor item indices to be off by one. All `ITEM_NAMES`, `FIELD_KEYS`, `readValues`, and `writeValues` switch-cases are now correctly aligned (0–15).
- **⚙ button hidden in singleplayer** — The editor button was gated behind `!client.isInSingleplayer()`, ignoring test mode. Now uses `!isSingleplayer()` so the button appears when test mode is active.
- **Command conflict (`/personalbeacon` vs `/personalbeaconop`)** — Client command was accidentally renamed to `/personalbeacon`, conflicting with the server-side `DebugCommand` tree and breaking all commands. Reverted to `/personalbeaconop edit`.

### Changed
- **Button texture size** — `Button.png` and `Button_selected.png` resized from 22×22 to 20×20. `TextureButtonWidget.TEX_SIZE` updated accordingly.
- **Text color** — All dark text now uses `#3D3D3D` instead of pure black.
- **Shadow removed** — All text rendering uses `drawText(..., false)` / `drawCenteredText` instead of `WithShadow` variants.
- **Online/Offline indicators** — Online players show green `(online)`, offline players show dim red `(offline)`. Left accent bars removed.
- **Singleplayer warning color** — `singleplayerDesc` text color changed to dark red (`#8B1A1A`) to clearly convey the warning.

-----

## v1.0.0 — 2025-XX-XX

### Initial Release
- Beacon access control system with per-player whitelist.
- Custom GUI injected into vanilla Beacon screen via Mixin.
- Owner-based permission model — first player to restrict becomes owner.
- Server-side persistence via `PersistentState` (NBT).
- Client-server sync via custom Fabric networking packets.
- Admin commands: `/personalbeacon help|debug|add|remove|setowner|clear|test`.
- GUI Layout Editor (`/personalbeaconop edit`) for in-game UI customization.
- Custom texture overlays (`window1.png`, `window2.png`, `custom_beacon_gui.png`).
- Configuration via `config/personalbeacon.json`.
- Localization: English, Turkish, French, Spanish.
- Softlock prevention in singleplayer.
- Automatic cleanup when beacons are broken.
