package net.tekno.personalbeacon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BeaconScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.tekno.personalbeacon.ModNetworking;
import net.tekno.personalbeacon.PersonalBeaconMod;
import net.tekno.personalbeacon.client.GuiEditorScreen;

import java.util.*;

@Environment(EnvType.CLIENT)
public class BeaconAccessScreen extends Screen {

    private final BeaconScreen parent;

    private static boolean testMode = false;

    public static void setTestMode(boolean enabled) { testMode = enabled; }
    public void onTestModeChanged() { rebuildButtons(); }

    private BlockPos beaconPos = null;
    private UUID ownerUUID = null;

    // UUID -> name for allowed players (includes offline)
    private Map<UUID, String> allowedPlayers = new LinkedHashMap<>();

    // Full name cache from server (all known players)
    private Map<UUID, String> nameCache = new HashMap<>();

    // Online player list from client
    private List<PlayerListEntry> onlinePlayers = new ArrayList<>();

    // Combined display list: allowed (with offline) + online not yet allowed
    private List<DisplayEntry> displayList = new ArrayList<>();

    private boolean listExpanded = false;
    private int scrollOffset = 0;
    private static final int VISIBLE_ROWS = 7;
    private static final int ROW_HEIGHT = 24;

    private final Queue<AbstractMap.SimpleEntry<UUID, Boolean>> pendingToggles = new LinkedList<>();

    private int panelX, panelY, panelW, panelH;

    private static final Identifier GUI_TEXTURE =
        new Identifier("personalbeacon", "textures/gui/custom_beacon_gui.png");
    private static final int TEXTURE_W = 352;
    private static final int TEXTURE_H = 334;

    private static final Identifier WINDOW1_TEXTURE =
        new Identifier("personalbeacon", "textures/gui/window1.png");
    private static final Identifier WINDOW2_TEXTURE =
        new Identifier("personalbeacon", "textures/gui/window2.png");
    private static final Identifier WINDOW3_TEXTURE =
        new Identifier("personalbeacon", "textures/gui/window3.png");

    // Colors — designed for the light-grey texture background
    private static final int COLOR_ALLOWED    = 0xFF1A8A40;   // green — allowed / online
    private static final int COLOR_BLOCKED    = 0xFF8B2222;
    private static final int COLOR_OFFLINE    = 0xFFB03030;   // dim red — offline
    private static final int COLOR_OWNER      = 0xFFAA7700;
    private static final int COLOR_TEXT_MAIN  = 0xFF3D3D3D;
    private static final int COLOR_TEXT_DIM   = 0xFF3D3D3D;
    private static final int COLOR_TEXT_GOLD  = 0xFFAA7700;

    /** One row in the combined display list. */
    private static class DisplayEntry {
        final UUID uuid;
        final String name;
        final boolean online;
        final boolean allowed;
        final boolean isOwner;

        DisplayEntry(UUID uuid, String name, boolean online, boolean allowed, boolean isOwner) {
            this.uuid = uuid;
            this.name = name;
            this.online = online;
            this.allowed = allowed;
            this.isOwner = isOwner;
        }
    }

    public BeaconAccessScreen(BeaconScreen parent) {
        super(Text.translatable("personalbeacon.screen.title"));
        this.parent = parent;
    }

    private boolean isSingleplayer() {
        if (testMode) return false;
        return this.client != null && this.client.isInSingleplayer();
    }

    private boolean isSelfOwner() {
        if (ownerUUID == null) return true; // no owner yet — anyone can manage
        if (this.client == null) return false;
        String selfName = this.client.getSession().getUsername();
        // Find self UUID in online list
        if (this.client.getNetworkHandler() == null) return false;
        for (PlayerListEntry entry : this.client.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile().getName().equals(selfName)) {
                return entry.getProfile().getId().equals(ownerUUID);
            }
        }
        return false;
    }

    /** Rebuild the combined display list from allowed + online. */
    private void rebuildDisplayList() {
        displayList.clear();

        Set<UUID> seen = new LinkedHashSet<>();

        // 1. Allowed players first (may include offline)
        for (Map.Entry<UUID, String> entry : allowedPlayers.entrySet()) {
            UUID uuid = entry.getKey();
            String name = entry.getValue();
            boolean online = isOnline(uuid);
            boolean isOwner = uuid.equals(ownerUUID);
            displayList.add(new DisplayEntry(uuid, name, online, true, isOwner));
            seen.add(uuid);
        }

        // 2. Online players not yet in allowed list
        if (onlinePlayers != null) {
            for (PlayerListEntry entry : onlinePlayers) {
                UUID uuid = entry.getProfile().getId();
                if (seen.contains(uuid)) continue;
                String name = entry.getProfile().getName();
                boolean isOwner = uuid.equals(ownerUUID);
                displayList.add(new DisplayEntry(uuid, name, true, false, isOwner));
            }
        }
    }

    private boolean isOnline(UUID uuid) {
        if (this.client == null || this.client.getNetworkHandler() == null) return false;
        for (PlayerListEntry e : this.client.getNetworkHandler().getPlayerList()) {
            if (e.getProfile().getId().equals(uuid)) return true;
        }
        return false;
    }

    @Override
    protected void init() {
        panelW = 300;
        panelH = 240;
        panelX = this.width / 2 - panelW / 2;
        panelY = this.height / 2 - panelH / 2;

        if (this.client != null && this.client.getNetworkHandler() != null) {
            onlinePlayers = new ArrayList<>(this.client.getNetworkHandler().getPlayerList());
            onlinePlayers.sort((a, b) ->
                a.getProfile().getName().compareToIgnoreCase(b.getProfile().getName()));
        }
        if (!allowedPlayers.isEmpty()) listExpanded = true;
        rebuildDisplayList();
        rebuildButtons();
    }

    private void rebuildButtons() {
        this.clearChildren();
        GuiLayoutConfig cfg = GuiLayoutConfig.get();

        // Close
        GuiLayoutConfig.ButtonEntry cb = cfg.closeButton;
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("personalbeacon.screen.close"), btn -> this.close())
            .dimensions(panelX + cb.offsetX, panelY + cb.offsetY, cb.width, cb.height)
            .tooltip(Tooltip.of(Text.literal(cb.tooltip)))
            .build());

        if (isSingleplayer()) return;

        // GUI Editor button — only in multiplayer, only when unlocked via /personalbeaconop edit
        if (GuiEditorScreen.editorUnlocked && !isSingleplayer()) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("⚙"),
                b -> { if (this.client != null) this.client.setScreen(new GuiEditorScreen(this)); })
                .dimensions(panelX + panelW - 22, panelY + 2, 20, 20)
                .tooltip(Tooltip.of(Text.literal("Edit GUI Layout  (/personalbeaconop edit to toggle)")))
                .build());
        }

        // Empty state
        if (allowedPlayers.isEmpty() && !listExpanded) {
            GuiLayoutConfig.ButtonEntry ab = cfg.addPlayersButton;
            this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("personalbeacon.screen.add_players"),
                btn -> { listExpanded = true; rebuildDisplayList(); rebuildButtons(); })
                .dimensions(panelX + ab.offsetX, panelY + ab.offsetY, ab.width, ab.height)
                .tooltip(Tooltip.of(Text.literal(ab.tooltip)))
                .build());
            return;
        }

        // Scroll
        GuiLayoutConfig.ButtonEntry su = cfg.scrollUpButton;
        GuiLayoutConfig.ButtonEntry sd = cfg.scrollDownButton;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("▲"),
            btn -> { if (scrollOffset > 0) { scrollOffset--; rebuildButtons(); } })
            .dimensions(panelX + su.offsetX, panelY + su.offsetY, su.width, su.height)
            .tooltip(Tooltip.of(Text.literal(su.tooltip)))
            .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("▼"),
            btn -> { if (scrollOffset < Math.max(0, displayList.size() - VISIBLE_ROWS)) { scrollOffset++; rebuildButtons(); } })
            .dimensions(panelX + sd.offsetX, panelY + sd.offsetY, sd.width, sd.height)
            .tooltip(Tooltip.of(Text.literal(sd.tooltip)))
            .build());

        // Player rows
        GuiLayoutConfig.ToggleButtonEntry tb = cfg.toggleButton;
        int listStartY = panelY + su.offsetY; // align with scroll-up button top
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = scrollOffset + i;
            if (idx >= displayList.size()) break;

            DisplayEntry entry = displayList.get(idx);
            int rowY = listStartY + i * ROW_HEIGHT;
            boolean canToggle = isSelfOwner() || ownerUUID == null;
            String tipText = entry.allowed ? tb.tooltipAllowed : tb.tooltipBlocked;

            this.addDrawableChild(ButtonWidget.builder(
                entry.allowed
                    ? Text.translatable("personalbeacon.screen.allowed")
                    : Text.translatable("personalbeacon.screen.blocked"),
                b -> { if (canToggle) togglePlayer(entry.uuid, entry.name, !entry.allowed); })
                .dimensions(panelX + panelW - tb.offsetXFromRight, rowY + tb.rowOffsetY, tb.width, tb.height)
                .tooltip(Tooltip.of(Text.literal(tipText)))
                .build());
        }
    }

    private void togglePlayer(UUID uuid, String name, boolean add) {
        if (beaconPos == null) {
            pendingToggles.add(new AbstractMap.SimpleEntry<>(uuid, add));
            return;
        }
        sendToggle(uuid, name, add);
    }

    private void sendToggle(UUID uuid, String name, boolean add) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(beaconPos);
        buf.writeUuid(uuid);
        buf.writeBoolean(add);
        buf.writeString(name); // send name so server can cache it
        ClientPlayNetworking.send(ModNetworking.C2S_UPDATE_ACCESS, buf);

        if (add) allowedPlayers.put(uuid, name);
        else allowedPlayers.remove(uuid);

        if (allowedPlayers.isEmpty()) {
            listExpanded = false;
            scrollOffset = 0;
        }
        rebuildDisplayList();
        rebuildButtons();
    }

    /** Called by PersonalBeaconClient when S2C packet arrives. */
    public void receiveData(BlockPos pos, UUID owner, Map<UUID, String> allowed, Map<UUID, String> names) {
        this.beaconPos = pos;
        this.ownerUUID = owner;
        this.allowedPlayers = new LinkedHashMap<>(allowed);
        this.nameCache = new HashMap<>(names);
        if (!allowed.isEmpty()) listExpanded = true;

        while (!pendingToggles.isEmpty()) {
            var entry = pendingToggles.poll();
            PersonalBeaconMod.LOGGER.info("[PersonalBeacon] Processing queued toggle for {}", entry.getKey());
            String name = nameCache.getOrDefault(entry.getKey(), entry.getKey().toString().substring(0, 8));
            sendToggle(entry.getKey(), name, entry.getValue());
        }
        rebuildDisplayList();
        rebuildButtons();
    }

    // Legacy compatibility
    public void receiveAllowedPlayers(BlockPos pos, Set<UUID> allowed) {
        Map<UUID, String> map = new LinkedHashMap<>();
        for (UUID u : allowed) map.put(u, nameCache.getOrDefault(u, u.toString().substring(0, 8)));
        receiveData(pos, ownerUUID, map, nameCache);
    }

    public void setBeaconPos(BlockPos pos) {
        this.beaconPos = pos;
    }

    // -------------------------------------------------------
    // Rendering
    // -------------------------------------------------------

    private void fillRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + h, color);
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    /**
     * Draws centered text scaled by {@code scale}.
     * {@code centerX} / {@code topY} are normal screen-space coordinates.
     * The text is horizontally centred at centerX and its top edge sits at topY.
     */
    private void drawScaledCenteredText(DrawContext ctx, Text text,
                                        int centerX, int topY, float scale, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(centerX, topY, 0);
        ctx.getMatrices().scale(scale, scale, 1.0f);
        // In scaled-matrix space: x=0 is the horizontal centre, y=0 is the text top
        ctx.drawText(this.textRenderer, text, -this.textRenderer.getWidth(text) / 2, 0, color, false);
        ctx.getMatrices().pop();
    }

    /** Centered text without shadow. */
    private void drawCenteredText(DrawContext ctx, Text text, int centerX, int y, int color) {
        ctx.drawText(this.textRenderer, text, centerX - this.textRenderer.getWidth(text) / 2, y, color, false);
    }

    /** Draws a PNG texture scaled to the given render size. */
    private void drawWindowTexture(DrawContext ctx, Identifier texture,
                                   int x, int y, int renderW, int renderH,
                                   int texW, int texH) {
        float sx = (float) renderW / texW;
        float sy = (float) renderH / texH;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(sx, sy, 1.0f);
        ctx.drawTexture(texture, 0, 0, 0, 0, texW, texH, texW, texH);
        ctx.getMatrices().pop();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        GuiLayoutConfig cfg = GuiLayoutConfig.get();

        // ── Main background ───────────────────────────────────────────────────
        float scaleX = (float) panelW / TEXTURE_W;
        float scaleY = (float) panelH / TEXTURE_H;
        context.getMatrices().push();
        context.getMatrices().translate(panelX, panelY, 0);
        context.getMatrices().scale(scaleX, scaleY, 1.0f);
        context.drawTexture(GUI_TEXTURE, 0, 0, 0, 0, TEXTURE_W, TEXTURE_H, TEXTURE_W, TEXTURE_H);
        context.getMatrices().pop();

        // ── Window 1: header overlay ──────────────────────────────────────────
        GuiLayoutConfig.WindowEntry w1 = cfg.window1;
        drawWindowTexture(context, WINDOW1_TEXTURE,
            panelX + w1.offsetX, panelY + w1.offsetY,
            w1.width, w1.height, w1.textureWidth, w1.textureHeight);

        // ── Title + coordinates ───────────────────────────────────────────────
        GuiLayoutConfig.TextEntry tt = cfg.titleText;
        drawScaledCenteredText(context,
            Text.translatable("personalbeacon.screen.title"),
            panelX + tt.offsetX, panelY + tt.offsetY, tt.scale, tt.color());

        String coordText = beaconPos != null
            ? beaconPos.getX() + ", " + beaconPos.getY() + ", " + beaconPos.getZ()
            : Text.translatable("personalbeacon.screen.loading").getString();
        GuiLayoutConfig.TextEntry ct = cfg.coordsText;
        drawScaledCenteredText(context,
            Text.literal(coordText),
            panelX + ct.offsetX, panelY + ct.offsetY, ct.scale, ct.color());

        // ── Singleplayer ──────────────────────────────────────────────────────
        if (isSingleplayer()) {
            // Window 2 replaces the old orange bordered box
            GuiLayoutConfig.WindowEntry w2 = cfg.window2;
            drawWindowTexture(context, WINDOW2_TEXTURE,
                panelX + w2.offsetX, panelY + w2.offsetY,
                w2.width, w2.height, w2.textureWidth, w2.textureHeight);

            GuiLayoutConfig.TextEntry st = cfg.singleplayerTitle;
            drawCenteredText(context,
                Text.translatable("personalbeacon.screen.singleplayer_title"),
                panelX + st.offsetX, panelY + st.offsetY, st.color());

            GuiLayoutConfig.TextEntry sd = cfg.singleplayerDesc;
            drawCenteredText(context,
                Text.translatable("personalbeacon.screen.singleplayer_desc"),
                panelX + sd.offsetX, panelY + sd.offsetY, sd.color());

            super.render(context, mouseX, mouseY, delta);
            return;
        }

        // ── Owner line ────────────────────────────────────────────────────────
        if (ownerUUID != null) {
            String ownerName = nameCache.getOrDefault(ownerUUID, "Unknown");
            GuiLayoutConfig.TextEntry ot = cfg.ownerText;
            drawCenteredText(context,
                Text.translatable("personalbeacon.screen.owner", ownerName),
                panelX + ot.offsetX, panelY + ot.offsetY, ot.color());
        }

        // ── Empty state ───────────────────────────────────────────────────────
        if (allowedPlayers.isEmpty() && !listExpanded) {
            fillRect(context, panelX + 10, panelY + 55, panelW - 20, 48, 0x33003310);
            drawBorder(context, panelX + 10, panelY + 55, panelW - 20, 48, 0xFF224422);
            drawCenteredText(context,
                Text.literal("✔  ").append(Text.translatable("personalbeacon.screen.no_restrictions")),
                this.width / 2, panelY + 64, COLOR_ALLOWED);
            drawCenteredText(context,
                Text.translatable("personalbeacon.screen.click_to_add"),
                this.width / 2, panelY + 78, COLOR_TEXT_DIM);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        // ── Column headers ────────────────────────────────────────────────────
        GuiLayoutConfig.TextEntry cp = cfg.columnPlayer;
        GuiLayoutConfig.TextEntry ca = cfg.columnAccess;
        int listStartY = panelY + cfg.scrollUpButton.offsetY;

        context.drawText(this.textRenderer,
            Text.translatable("personalbeacon.screen.column_player"),
            panelX + cp.offsetX, panelY + cp.offsetY, cp.color(), false);
        context.drawText(this.textRenderer,
            Text.translatable("personalbeacon.screen.column_access"),
            panelX + ca.offsetX, panelY + ca.offsetY, ca.color(), false);
        context.fill(panelX + 8, panelY + 72, panelX + panelW - 8, panelY + 73, 0x44000000);

        // ── Player rows ───────────────────────────────────────────────────────
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = scrollOffset + i;
            if (idx >= displayList.size()) break;

            DisplayEntry entry = displayList.get(idx);
            int rowY = listStartY + i * ROW_HEIGHT;

            GuiLayoutConfig.WindowEntry w3 = cfg.window3;
            drawWindowTexture(context, WINDOW3_TEXTURE,
                panelX + w3.offsetX, rowY + w3.offsetY,
                w3.width, w3.height, w3.textureWidth, w3.textureHeight);

            String prefix = entry.isOwner ? "★ " : (idx + 1) + ". ";
            String nameLabel = prefix + entry.name;

            int nameColor;
            if (entry.isOwner)                          nameColor = COLOR_OWNER;
            else if (entry.allowed && entry.online)     nameColor = COLOR_ALLOWED;
            else if (entry.allowed && !entry.online)    nameColor = COLOR_OFFLINE;
            else                                        nameColor = COLOR_TEXT_DIM;

            context.drawText(this.textRenderer,
                nameLabel, panelX + 14, rowY + 7, nameColor, false);

            int statusLabelX = panelX + 14 + this.textRenderer.getWidth(nameLabel) + 4;
            if (entry.online) {
                context.drawText(this.textRenderer,
                    "(online)", statusLabelX, rowY + 7, COLOR_ALLOWED, false);
            } else {
                context.drawText(this.textRenderer,
                    "(offline)", statusLabelX, rowY + 7, COLOR_OFFLINE, false);
            }
        }

        // ── Scroll counter ────────────────────────────────────────────────────
        if (displayList.size() > VISIBLE_ROWS) {
            String counter = (scrollOffset + 1) + "-"
                + Math.min(scrollOffset + VISIBLE_ROWS, displayList.size())
                + " / " + displayList.size();
            drawCenteredText(context,
                Text.literal(counter), panelX + panelW - 12, panelY + panelH / 2, COLOR_TEXT_DIM);
        }

        // ── Non-owner warning ─────────────────────────────────────────────────
        if (ownerUUID != null && !isSelfOwner()) {
            drawCenteredText(context,
                Text.translatable("personalbeacon.screen.view_only"),
                this.width / 2, panelY + panelH - 40, 0xFFAA6600);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}
