package net.tekno.personalbeacon.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;

/**
 * Loads GUI layout configuration from:
 *   <minecraft>/config/personalbeacon_gui_layout.json
 *
 * If the file does not exist it is created automatically with default values.
 * Edit that file in-game (or while the game is closed) and call GuiLayoutConfig.reload()
 * to apply changes without restarting.
 *
 * All offsetX / offsetY values are relative to the panel's top-left corner (panelX, panelY).
 * The panel is 300 × 240 pixels and is centred on the screen.
 */
public class GuiLayoutConfig {

    // ─── singleton ────────────────────────────────────────────────────────────
    private static GuiLayoutConfig INSTANCE = null;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("personalbeacon");

    /** Returns the loaded config (loads on first call). */
    public static GuiLayoutConfig get() {
        if (INSTANCE == null) INSTANCE = loadOrCreate();
        return INSTANCE;
    }

    /** Force-reload from disk (useful after editing the JSON at runtime). */
    public static void reload() {
        INSTANCE = loadOrCreate();
    }

    // ─── window overlays ──────────────────────────────────────────────────────

    /** window1.png — drawn over the header bar (title area). */
    public WindowEntry window1 = new WindowEntry(
        10, 10,        // offsetX, offsetY
        280, 41,       // render width, height in pixels
        335, 46,       // actual PNG dimensions (do not change unless you swap the PNG)
        "Header overlay for the 'Personal Access Control' title. " +
        "offsetX/Y: position relative to panel top-left. " +
        "width/height: rendered size. textureWidth/Height: original PNG size."
    );

    /** window2.png — drawn in place of the old orange singleplayer info box. */
    public WindowEntry window2 = new WindowEntry(
        10, 85,        // offsetX, offsetY
        280, 50,       // render width, height
        335, 46,       // actual PNG dimensions
        "Single-player mode info panel. Replaces the old orange bordered box. " +
        "Only visible when the world is singleplayer."
    );

    /** window3.png — drawn as the background for each player card row. */
    public WindowEntry window3 = new WindowEntry(
        8, 0,          // offsetX (from panel left), offsetY (from row top)
        282, 22,       // render width, height (panel width - 30, row height - 2)
        335, 46,       // actual PNG dimensions
        "Player card row background. offsetX from panel left, offsetY from row top. " +
        "width/height: rendered size per row."
    );

    // ─── text positions ───────────────────────────────────────────────────────
    // For centred texts offsetX is the horizontal centre (e.g. 150 = panel centre for a 300 px wide panel).
    // For left-aligned texts offsetX is the left edge.

    public TextEntry titleText = new TextEntry(
        150, 19, 1.6f, "1A1A2E",
        "Main screen title — 'Personal Access Control' (centred). scale: text zoom factor."
    );

    public TextEntry coordsText = new TextEntry(
        150, 36, 1.2f, "444455",
        "Beacon coordinates line below the title (centred)."
    );

    public TextEntry ownerText = new TextEntry(
        150, 60, 1.0f, "AA7700",
        "Owner line — '★ Owner: <name>' (centred)."
    );

    public TextEntry singleplayerTitle = new TextEntry(
        150, 104, 1.0f, "AA7700",
        "Title text inside the singleplayer window2 (centred)."
    );

    public TextEntry singleplayerDesc = new TextEntry(
        150, 115, 1.0f, "444455",
        "Description text inside the singleplayer window2 (centred)."
    );

    public TextEntry columnPlayer = new TextEntry(
        14, 62, 0.6f, "444455",
        "Column header 'Player' (left-aligned, offsetX from panel left)."
    );

    public TextEntry columnAccess = new TextEntry(
        217, 62, 1.0f, "444455",
        "Column header 'Access' (left-aligned, offsetX from panel left)."
    );

    // ─── buttons ──────────────────────────────────────────────────────────────

    public ButtonEntry closeButton = new ButtonEntry(
        105, 212, 90, 20,
        "Close the access-control screen"
    );

    public ButtonEntry addPlayersButton = new ButtonEntry(
        85, 110, 130, 22,
        "Open the player list to add / manage access"
    );

    public ButtonEntry scrollUpButton = new ButtonEntry(
        275, 75, 18, 18,
        "Scroll the player list up"
    );

    public ButtonEntry scrollDownButton = new ButtonEntry(
        275, 210, 18, 18,
        "Scroll the player list down"
    );

    /**
     * Per-row access toggle buttons.
     * offsetXFromRight = distance from the panel's RIGHT edge (e.g. 86 → button starts at panelX + panelW - 86).
     * rowOffsetY       = vertical offset inside each row.
     */
    public ToggleButtonEntry toggleButton = new ToggleButtonEntry(
        93, 3, 58, 16,
        "Click to block this player",
        "Click to allow this player"
    );

    /**
     * The "Manage Access" button rendered on the vanilla BeaconScreen.
     * offsetX/Y are relative to BeaconScreen's top-left corner (this.x, this.y).
     * Default position: right side of the player-inventory area.
     */
    public ButtonEntry manageAccessButton = new ButtonEntry(
        12, 10, 11, 11,
        "Manage who can use this beacon"
    );

    // ─── inner config types ───────────────────────────────────────────────────

    public static class WindowEntry {
        public String _comment;
        public int offsetX;
        public int offsetY;
        public int width;
        public int height;
        public int textureWidth;
        public int textureHeight;

        WindowEntry(int ox, int oy, int w, int h, int tw, int th, String comment) {
            this.offsetX = ox;       this.offsetY = oy;
            this.width   = w;        this.height  = h;
            this.textureWidth = tw;  this.textureHeight = th;
            this._comment = comment;
        }
    }

    public static class TextEntry {
        public String _comment;
        /** Horizontal offset from panel left (centred texts: this is the centre X). */
        public int offsetX;
        /** Vertical offset from panel top. */
        public int offsetY;
        /** Text scale multiplier (1.0 = default Minecraft text size). */
        public float scale;
        /**
         * Text colour in RRGGBB hex (no '#', no alpha prefix).
         * Examples: "1A1A2E" (dark blue), "AA7700" (gold), "444455" (grey).
         */
        public String colorHex;

        TextEntry(int ox, int oy, float s, String col, String comment) {
            this.offsetX = ox;  this.offsetY = oy;
            this.scale   = s;   this.colorHex = col;
            this._comment = comment;
        }

        /** Returns the colour as a packed ARGB int (alpha = FF). */
        public int color() {
            try {
                return 0xFF000000 | Integer.parseInt(colorHex.replace("#", ""), 16);
            } catch (NumberFormatException e) {
                return 0xFF1A1A2E; // fallback: dark blue
            }
        }
    }

    public static class ButtonEntry {
        public String _comment;
        /** Offset from panel left. */
        public int offsetX;
        /** Offset from panel top. */
        public int offsetY;
        public int width;
        public int height;
        /** Tooltip shown on hover. */
        public String tooltip;

        ButtonEntry(int ox, int oy, int w, int h, String tip) {
            this.offsetX = ox;  this.offsetY = oy;
            this.width   = w;   this.height  = h;
            this.tooltip = tip;
        }
    }

    public static class ToggleButtonEntry {
        public String _comment =
            "Per-row toggle buttons. " +
            "offsetXFromRight: distance from panel RIGHT edge (button left = panelX + panelW - offsetXFromRight). " +
            "rowOffsetY: vertical offset inside each 24 px tall row.";
        public int offsetXFromRight;
        public int rowOffsetY;
        public int width;
        public int height;
        /** Tooltip when the player is currently ALLOWED (click will block). */
        public String tooltipAllowed;
        /** Tooltip when the player is currently BLOCKED (click will allow). */
        public String tooltipBlocked;

        ToggleButtonEntry(int oxr, int roy, int w, int h, String tipA, String tipB) {
            this.offsetXFromRight = oxr;  this.rowOffsetY = roy;
            this.width            = w;    this.height     = h;
            this.tooltipAllowed   = tipA; this.tooltipBlocked = tipB;
        }
    }

    // ─── load / save ──────────────────────────────────────────────────────────

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir()
                           .resolve("personalbeacon_gui_layout.json");
    }

    /** Saves the given config instance to disk and sets it as the active instance. */
    public static void save(GuiLayoutConfig cfg) {
        INSTANCE = cfg;
        try (Writer w = Files.newBufferedWriter(configPath())) {
            GSON.toJson(cfg, w);
            LOGGER.debug("GUI layout config saved to {}", configPath());
        } catch (Exception e) {
            LOGGER.warn("Failed to save GUI layout config to {}", configPath(), e);
        }
    }

    private static GuiLayoutConfig loadOrCreate() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                GuiLayoutConfig cfg = GSON.fromJson(r, GuiLayoutConfig.class);
                if (cfg != null) {
                    LOGGER.debug("GUI layout config loaded from {}", path);
                    return cfg;
                }
                LOGGER.warn("GUI layout config at {} parsed as null, using defaults", path);
            } catch (Exception e) {
                LOGGER.warn("Failed to read GUI layout config at {} (corrupted?), using defaults", path, e);
            }
        }
        // Write defaults so the user can see and edit the file
        GuiLayoutConfig defaults = new GuiLayoutConfig();
        try (Writer w = Files.newBufferedWriter(path)) {
            GSON.toJson(defaults, w);
            LOGGER.info("GUI layout config created with defaults at {}", path);
        } catch (Exception e) {
            LOGGER.warn("Failed to write default GUI layout config to {}", path, e);
        }
        return defaults;
    }
}
