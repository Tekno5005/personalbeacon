# Personal Beacon — Changelog

All notable changes to this project will be documented in this file.
Format follows [Semantic Versioning](https://semver.org/).

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
