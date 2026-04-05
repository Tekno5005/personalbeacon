package net.tekno.personalbeacon.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.*;

/**
 * Basic in-game GUI layout editor.
 * Unlock with /personalbeaconop edit (toggles the ⚙ button in BeaconAccessScreen).
 */
public class GuiEditorScreen extends Screen {

    /** Toggled via /personalbeaconop edit — shows the ⚙ button in BeaconAccessScreen. */
    public static boolean editorUnlocked = false;

    private static final int GAP = 8; // gap between left and right panels

    // ─── Item names & field keys ──────────────────────────────────────────────
    private static final String[] ITEM_NAMES = {
        "window1", "window2", "window3",
        "titleText", "coordsText", "ownerText",
        "singleplayerTitle", "singleplayerDesc",
        "columnPlayer", "columnAccess",
        "closeButton", "addPlayersButton", "scrollUpButton", "scrollDownButton",
        "toggleButton",
        "manageAccessButton",  // position on the vanilla BeaconScreen
        "ownerToggleButton",   // ★/☆ per-row co-manager toggle
        "emptyStateBox",       // empty-state background box
        "emptyStateTitle",     // empty-state "no restrictions" text
        "emptyStateDesc"       // empty-state "click to add" text
    };

    private static final String[][] FIELD_KEYS = {
        {"offsetX","offsetY","width","height"},                                          // 0  window1
        {"offsetX","offsetY","width","height"},                                          // 1  window2
        {"offsetX","offsetY","width","height"},                                          // 2  window3
        {"offsetX","offsetY","scale","colorHex"},                                        // 3  titleText
        {"offsetX","offsetY","scale","colorHex"},                                        // 4  coordsText
        {"offsetX","offsetY","scale","colorHex"},                                        // 5  ownerText
        {"offsetX","offsetY","scale","colorHex"},                                        // 6  singleplayerTitle
        {"offsetX","offsetY","scale","colorHex"},                                        // 7  singleplayerDesc
        {"offsetX","offsetY","scale","colorHex"},                                        // 8  columnPlayer
        {"offsetX","offsetY","scale","colorHex"},                                        // 9  columnAccess
        {"offsetX","offsetY","width","height","tooltip"},                                // 10 closeButton
        {"offsetX","offsetY","width","height","tooltip"},                                // 11 addPlayersButton
        {"offsetX","offsetY","width","height","tooltip"},                                // 12 scrollUpButton
        {"offsetX","offsetY","width","height","tooltip"},                                // 13 scrollDownButton
        {"offsetXFromRight","rowOffsetY","width","height","tooltipAllowed","tooltipBlocked"}, // 14 toggleButton
        {"offsetX","offsetY","width","height","tooltip"},                                // 15 manageAccessButton
        {"offsetXFromRight","rowOffsetY","width","height","tooltipAllowed","tooltipBlocked"}, // 16 ownerToggleButton
        {"offsetX","offsetY","width","height"},                                          // 17 emptyStateBox
        {"offsetX","offsetY","scale","colorHex"},                                        // 18 emptyStateTitle
        {"offsetX","offsetY","scale","colorHex"},                                        // 19 emptyStateDesc
    };

    // ─── State ────────────────────────────────────────────────────────────────
    private final Screen parent;

    // Computed in init() — sized to fit within the actual screen
    private int px, py, panelW, panelH, leftW, itemRowH;

    private final Map<Integer, Map<String, String>> allValues = new HashMap<>();
    private int selectedIndex = 0;
    private final List<TextFieldWidget> activeFields = new ArrayList<>();

    public GuiEditorScreen(Screen parent) {
        super(Text.literal("GUI Layout Editor"));
        this.parent = parent;
    }

    // ─── Init ─────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        // Clamp panel to screen, always leave 6 px margin
        panelW = Math.min(500, this.width  - 6);
        panelH = Math.min(300, this.height - 6);

        // Left list: 28% of panel width, min 100 px
        leftW = Math.max(100, panelW * 28 / 100);

        // Item row height: fill available list area evenly, 13–18 px
        int listArea = panelH - 50; // 20 title + 30 buttons
        itemRowH = Math.max(13, Math.min(18, listArea / ITEM_NAMES.length));

        px = this.width  / 2 - panelW / 2;
        py = this.height / 2 - panelH / 2;

        GuiLayoutConfig cfg = GuiLayoutConfig.get();
        for (int i = 0; i < ITEM_NAMES.length; i++) {
            allValues.put(i, readValues(i, cfg));
        }

        buildRightFields();
        buildBottomButtons();
    }

    private void buildBottomButtons() {
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save & Close"), b -> {
            commitCurrentFields();
            saveAll();
            this.close();
        }).dimensions(px + panelW / 2 - 105, py + panelH - 24, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"),
            b -> this.close())
            .dimensions(px + panelW / 2 + 5, py + panelH - 24, 100, 20).build());
    }

    private void buildRightFields() {
        for (TextFieldWidget f : activeFields) this.remove(f);
        activeFields.clear();

        String[] keys  = FIELD_KEYS[selectedIndex];
        Map<String, String> values = allValues.get(selectedIndex);

        int rightX = px + leftW + GAP;
        int rightW = panelW - leftW - GAP - 4;
        int colW   = (rightW - 10) / 2;
        int labelW = Math.min(90, colW / 2);
        int fieldW = colW - labelW - 6;

        for (int i = 0; i < keys.length; i++) {
            int col    = i % 2;
            int row    = i / 2;
            int fieldX = rightX + col * (colW + 10) + labelW + 4;
            int fieldY = py + 36 + row * 26;

            // Don't render fields that fall outside the panel
            if (fieldY + 16 > py + panelH - 28) break;

            TextFieldWidget tf = new TextFieldWidget(
                this.textRenderer, fieldX, fieldY, fieldW, 16,
                Text.literal(keys[i])
            );
            tf.setText(values.getOrDefault(keys[i], ""));
            tf.setMaxLength(256);
            activeFields.add(tf);
            this.addDrawableChild(tf);
        }
    }

    // ─── Selection ────────────────────────────────────────────────────────────
    private void selectItem(int index) {
        if (index == selectedIndex) return;
        commitCurrentFields();
        selectedIndex = index;
        buildRightFields();
    }

    private void commitCurrentFields() {
        String[] keys  = FIELD_KEYS[selectedIndex];
        Map<String, String> values = allValues.get(selectedIndex);
        for (int i = 0; i < activeFields.size() && i < keys.length; i++) {
            values.put(keys[i], activeFields.get(i).getText().trim());
        }
    }

    // ─── Save ─────────────────────────────────────────────────────────────────
    private void saveAll() {
        GuiLayoutConfig cfg = GuiLayoutConfig.get();
        for (int i = 0; i < ITEM_NAMES.length; i++) {
            writeValues(i, allValues.get(i), cfg);
        }
        GuiLayoutConfig.save(cfg);
    }

    // ─── Read helpers ─────────────────────────────────────────────────────────
    private static Map<String, String> readValues(int idx, GuiLayoutConfig cfg) {
        Map<String, String> m = new LinkedHashMap<>();
        switch (idx) {
            case 0  -> fillWindow(m, cfg.window1);
            case 1  -> fillWindow(m, cfg.window2);
            case 2  -> fillWindow(m, cfg.window3);
            case 3  -> fillText(m, cfg.titleText);
            case 4  -> fillText(m, cfg.coordsText);
            case 5  -> fillText(m, cfg.ownerText);
            case 6  -> fillText(m, cfg.singleplayerTitle);
            case 7  -> fillText(m, cfg.singleplayerDesc);
            case 8  -> fillText(m, cfg.columnPlayer);
            case 9  -> fillText(m, cfg.columnAccess);
            case 10 -> fillButton(m, cfg.closeButton);
            case 11 -> fillButton(m, cfg.addPlayersButton);
            case 12 -> fillButton(m, cfg.scrollUpButton);
            case 13 -> fillButton(m, cfg.scrollDownButton);
            case 14 -> fillToggle(m, cfg.toggleButton);
            case 15 -> fillButton(m, cfg.manageAccessButton);
            case 16 -> fillToggle(m, cfg.ownerToggleButton);
            case 17 -> fillBoxButton(m, cfg.emptyStateBox);
            case 18 -> fillText(m, cfg.emptyStateTitle);
            case 19 -> fillText(m, cfg.emptyStateDesc);
        }
        return m;
    }

    private static void fillWindow(Map<String,String> m, GuiLayoutConfig.WindowEntry e) {
        m.put("offsetX", str(e.offsetX));
        m.put("offsetY", str(e.offsetY));
        m.put("width",   str(e.width));
        m.put("height",  str(e.height));
    }

    private static void fillText(Map<String,String> m, GuiLayoutConfig.TextEntry e) {
        m.put("offsetX",  str(e.offsetX));
        m.put("offsetY",  str(e.offsetY));
        m.put("scale",    str(e.scale));
        m.put("colorHex", e.colorHex != null ? e.colorHex : "FFFFFF");
    }

    private static void fillButton(Map<String,String> m, GuiLayoutConfig.ButtonEntry e) {
        m.put("offsetX",  str(e.offsetX));
        m.put("offsetY",  str(e.offsetY));
        m.put("width",    str(e.width));
        m.put("height",   str(e.height));
        m.put("tooltip",  e.tooltip != null ? e.tooltip : "");
    }

    /** Box entry: 4 fields only (no tooltip). */
    private static void fillBoxButton(Map<String,String> m, GuiLayoutConfig.ButtonEntry e) {
        m.put("offsetX", str(e.offsetX));
        m.put("offsetY", str(e.offsetY));
        m.put("width",   str(e.width));
        m.put("height",  str(e.height));
    }

    private static void fillToggle(Map<String,String> m, GuiLayoutConfig.ToggleButtonEntry e) {
        m.put("offsetXFromRight", str(e.offsetXFromRight));
        m.put("rowOffsetY",       str(e.rowOffsetY));
        m.put("width",            str(e.width));
        m.put("height",           str(e.height));
        m.put("tooltipAllowed",   e.tooltipAllowed != null ? e.tooltipAllowed : "");
        m.put("tooltipBlocked",   e.tooltipBlocked != null ? e.tooltipBlocked : "");
    }

    // ─── Write helpers ────────────────────────────────────────────────────────
    private static void writeValues(int idx, Map<String, String> m, GuiLayoutConfig cfg) {
        switch (idx) {
            case 0  -> applyWindow(m, cfg.window1);
            case 1  -> applyWindow(m, cfg.window2);
            case 2  -> applyWindow(m, cfg.window3);
            case 3  -> applyText(m, cfg.titleText);
            case 4  -> applyText(m, cfg.coordsText);
            case 5  -> applyText(m, cfg.ownerText);
            case 6  -> applyText(m, cfg.singleplayerTitle);
            case 7  -> applyText(m, cfg.singleplayerDesc);
            case 8  -> applyText(m, cfg.columnPlayer);
            case 9  -> applyText(m, cfg.columnAccess);
            case 10 -> applyButton(m, cfg.closeButton);
            case 11 -> applyButton(m, cfg.addPlayersButton);
            case 12 -> applyButton(m, cfg.scrollUpButton);
            case 13 -> applyButton(m, cfg.scrollDownButton);
            case 14 -> applyToggle(m, cfg.toggleButton);
            case 15 -> applyButton(m, cfg.manageAccessButton);
            case 16 -> applyToggle(m, cfg.ownerToggleButton);
            case 17 -> applyBoxButton(m, cfg.emptyStateBox);
            case 18 -> applyText(m, cfg.emptyStateTitle);
            case 19 -> applyText(m, cfg.emptyStateDesc);
        }
    }

    private static void applyWindow(Map<String,String> m, GuiLayoutConfig.WindowEntry e) {
        e.offsetX = parseInt(m, "offsetX", e.offsetX);
        e.offsetY = parseInt(m, "offsetY", e.offsetY);
        e.width   = parseInt(m, "width",   e.width);
        e.height  = parseInt(m, "height",  e.height);
    }

    private static void applyText(Map<String,String> m, GuiLayoutConfig.TextEntry e) {
        e.offsetX  = parseInt(m,   "offsetX",  e.offsetX);
        e.offsetY  = parseInt(m,   "offsetY",  e.offsetY);
        e.scale    = parseFloat(m, "scale",    e.scale);
        String hex = m.getOrDefault("colorHex", e.colorHex != null ? e.colorHex : "FFFFFF");
        e.colorHex = hex.replaceAll("[^0-9A-Fa-f]", "");
    }

    private static void applyButton(Map<String,String> m, GuiLayoutConfig.ButtonEntry e) {
        e.offsetX = parseInt(m, "offsetX", e.offsetX);
        e.offsetY = parseInt(m, "offsetY", e.offsetY);
        e.width   = parseInt(m, "width",   e.width);
        e.height  = parseInt(m, "height",  e.height);
        e.tooltip = m.getOrDefault("tooltip", e.tooltip != null ? e.tooltip : "");
    }

    private static void applyBoxButton(Map<String,String> m, GuiLayoutConfig.ButtonEntry e) {
        e.offsetX = parseInt(m, "offsetX", e.offsetX);
        e.offsetY = parseInt(m, "offsetY", e.offsetY);
        e.width   = parseInt(m, "width",   e.width);
        e.height  = parseInt(m, "height",  e.height);
    }

    private static void applyToggle(Map<String,String> m, GuiLayoutConfig.ToggleButtonEntry e) {
        e.offsetXFromRight = parseInt(m, "offsetXFromRight", e.offsetXFromRight);
        e.rowOffsetY       = parseInt(m, "rowOffsetY",       e.rowOffsetY);
        e.width            = parseInt(m, "width",            e.width);
        e.height           = parseInt(m, "height",           e.height);
        e.tooltipAllowed   = m.getOrDefault("tooltipAllowed", e.tooltipAllowed != null ? e.tooltipAllowed : "");
        e.tooltipBlocked   = m.getOrDefault("tooltipBlocked", e.tooltipBlocked != null ? e.tooltipBlocked : "");
    }

    // ─── Parse utilities ──────────────────────────────────────────────────────
    private static String str(int v)   { return String.valueOf(v); }
    private static String str(float v) { return String.valueOf(v); }

    private static int parseInt(Map<String,String> m, String key, int fallback) {
        try   { return Integer.parseInt(m.getOrDefault(key, str(fallback)).trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static float parseFloat(Map<String,String> m, String key, float fallback) {
        try   { return Float.parseFloat(m.getOrDefault(key, str(fallback)).trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    // ─── Mouse ────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < ITEM_NAMES.length; i++) {
                int itemY = py + 22 + i * itemRowH;
                if (mouseX >= px + 2 && mouseX <= px + leftW + 2
                        && mouseY >= itemY && mouseY <= itemY + itemRowH - 2) {
                    selectItem(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ─── Render ───────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);

        // ── Outer panel
        ctx.fill(px, py, px + panelW, py + panelH, 0xE01A1A2E);
        ctx.fill(px,              py,              px + panelW, py + 1,          0xFF6666AA);
        ctx.fill(px,              py + panelH - 1, px + panelW, py + panelH,     0xFF6666AA);
        ctx.fill(px,              py,              px + 1,      py + panelH,     0xFF6666AA);
        ctx.fill(px + panelW - 1, py,              px + panelW, py + panelH,     0xFF6666AA);

        // ── Title bar
        ctx.fill(px + 1, py + 1, px + panelW - 1, py + 18, 0xFF2D2D5E);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§bGUI Layout Editor  §7(§e/personalbeaconop edit§7 to toggle)"),
            px + panelW / 2, py + 5, 0xFFFFFF);

        // ── Left panel bg + divider
        ctx.fill(px + 1,       py + 19, px + leftW + 2, py + panelH - 28, 0x44000000);
        ctx.fill(px + leftW + 2, py + 19, px + leftW + 3, py + panelH - 28, 0x88AAAACC);

        // ── List items
        for (int i = 0; i < ITEM_NAMES.length; i++) {
            int itemY = py + 22 + i * itemRowH;
            if (itemY + itemRowH > py + panelH - 28) break; // clip to panel

            boolean sel = i == selectedIndex;
            boolean hov = mouseX >= px + 2 && mouseX <= px + leftW + 2
                       && mouseY >= itemY  && mouseY <= itemY + itemRowH - 2;

            if (sel)      ctx.fill(px + 2, itemY - 1, px + leftW + 2, itemY + itemRowH - 1, 0x884466CC);
            else if (hov) ctx.fill(px + 2, itemY - 1, px + leftW + 2, itemY + itemRowH - 1, 0x44FFFFFF);

            ctx.drawTextWithShadow(textRenderer,
                Text.literal(sel ? "§e" + ITEM_NAMES[i] : ITEM_NAMES[i]),
                px + 6, itemY + (itemRowH - 9) / 2, 0xCCCCCC);
        }

        // ── Right panel bg + header
        int rightX = px + leftW + GAP;
        int rightW = panelW - leftW - GAP - 4;
        ctx.fill(rightX, py + 19, px + panelW - 2, py + panelH - 28, 0x33000000);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§f" + ITEM_NAMES[selectedIndex]),
            rightX + rightW / 2, py + 22, 0xAAAAFF);

        // ── Field labels
        String[] keys = FIELD_KEYS[selectedIndex];
        int colW   = (rightW - 10) / 2;
        int labelW = Math.min(90, colW / 2);

        for (int i = 0; i < keys.length; i++) {
            int col    = i % 2;
            int row    = i / 2;
            int labelX = rightX + col * (colW + 10) + 2;
            int labelY = py + 39 + row * 26;
            if (labelY + 9 > py + panelH - 28) break;
            ctx.drawTextWithShadow(textRenderer,
                Text.literal(keys[i] + ":"), labelX, labelY, 0xAAAAAA);
        }

        // ── Bottom separator
        ctx.fill(px + 1, py + panelH - 28, px + panelW - 1, py + panelH - 27, 0x88AAAACC);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}
