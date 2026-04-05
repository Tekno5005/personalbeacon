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
import net.minecraft.server.integrated.IntegratedServer;
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

    // Primary (first) owner — can never be removed
    private UUID primaryOwnerUUID = null;

    // All current owners (co-managers, includes primary)
    private Set<UUID> ownerUUIDs = new HashSet<>();

    // UUID -> name for allowed players (includes offline)
    private Map<UUID, String> allowedPlayers = new LinkedHashMap<>();

    // Full name cache from server (all known players)
    private Map<UUID, String> nameCache = new HashMap<>();

    // Online player list from client
    private List<PlayerListEntry> onlinePlayers = new ArrayList<>();

    // Combined display list: allowed (with offline) + online not yet allowed
    private List<DisplayEntry> displayList = new ArrayList<>();

    private int scrollOffset = 0;
    private int visibleRows = 6;
    private float panelScale = 1.0f;
    private static final int ROW_HEIGHT = 24;

    /** Scale a value from the 300×240 design space to the actual panel size. */
    private int sc(int v) { return Math.round(v * panelScale); }

    private final Queue<AbstractMap.SimpleEntry<UUID, Boolean>> pendingToggles = new LinkedList<>();

    // UUIDs whose owner-toggle is in-flight (waiting for S2C confirmation)
    private final Set<UUID> pendingOwnerChanges = new HashSet<>();

    // True when the user clicked "Restrict Access" and wants to see/edit the player list
    // even while allowedPlayers is still empty (beacon not yet restricted on server)
    private boolean showingRestrictedView = false;

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
        final boolean isPrimaryOwner;

        DisplayEntry(UUID uuid, String name, boolean online, boolean allowed,
                     boolean isOwner, boolean isPrimaryOwner) {
            this.uuid = uuid;
            this.name = name;
            this.online = online;
            this.allowed = allowed;
            this.isOwner = isOwner;
            this.isPrimaryOwner = isPrimaryOwner;
        }
    }

    public BeaconAccessScreen(BeaconScreen parent) {
        super(Text.translatable("personalbeacon.screen.title"));
        this.parent = parent;
    }

    // ── Issue 1 fix: return false when LAN is open ────────────────────────────
    private boolean isSingleplayer() {
        if (testMode) return false;
        if (this.client == null) return false;
        if (!this.client.isInSingleplayer()) return false;
        // IntegratedServer.getServerPort() returns -1 when not open to LAN,
        // and the actual LAN port (> 0) when "Open to LAN" has been used.
        IntegratedServer srv = this.client.getServer();
        if (srv == null) return true;
        return srv.getServerPort() < 0;
    }

    private boolean isSelfOwner() {
        if (ownerUUIDs.isEmpty()) return true; // no owners yet — anyone can manage
        if (this.client == null) return false;
        String selfName = this.client.getSession().getUsername();
        if (this.client.getNetworkHandler() == null) return false;
        for (PlayerListEntry entry : this.client.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile().getName().equals(selfName)) {
                return ownerUUIDs.contains(entry.getProfile().getId());
            }
        }
        return false;
    }

    /** Rebuild the combined display list from allowed + online. */
    private void rebuildDisplayList() {
        displayList.clear();

        // Build online set once for O(1) lookup instead of O(n) per entry
        Set<UUID> onlineSet = new HashSet<>();
        if (onlinePlayers != null) {
            for (PlayerListEntry e : onlinePlayers) onlineSet.add(e.getProfile().getId());
        }

        Set<UUID> seen = new LinkedHashSet<>();

        // 1. Allowed players first (may include offline)
        for (Map.Entry<UUID, String> entry : allowedPlayers.entrySet()) {
            UUID uuid = entry.getKey();
            String name = entry.getValue();
            boolean isOwner = ownerUUIDs.contains(uuid);
            boolean isPrimary = uuid.equals(primaryOwnerUUID);
            displayList.add(new DisplayEntry(uuid, name, onlineSet.contains(uuid), true, isOwner, isPrimary));
            seen.add(uuid);
        }

        // 2. Online players not yet in allowed list
        if (onlinePlayers != null) {
            for (PlayerListEntry entry : onlinePlayers) {
                UUID uuid = entry.getProfile().getId();
                if (seen.contains(uuid)) continue;
                String name = entry.getProfile().getName();
                boolean isOwner = ownerUUIDs.contains(uuid);
                boolean isPrimary = uuid.equals(primaryOwnerUUID);
                displayList.add(new DisplayEntry(uuid, name, true, false, isOwner, isPrimary));
            }
        }
    }

    @Override
    protected void init() {
        // Scale down from the 300×240 design size to fit within the screen (20 px margin each side)
        int availW = this.width  - 40;
        int availH = this.height - 40;
        panelScale = Math.min(1.0f, Math.min(availW / 300f, availH / 240f));
        panelW = Math.round(300 * panelScale);
        panelH = Math.round(240 * panelScale);
        panelX = this.width  / 2 - panelW / 2;
        panelY = this.height / 2 - panelH / 2;

        GuiLayoutConfig layoutCfg = GuiLayoutConfig.get();
        visibleRows = Math.max(2,
            (layoutCfg.scrollDownButton.offsetY - layoutCfg.scrollUpButton.offsetY) / ROW_HEIGHT);

        if (this.client != null && this.client.getNetworkHandler() != null) {
            onlinePlayers = new ArrayList<>(this.client.getNetworkHandler().getPlayerList());
            onlinePlayers.sort((a, b) ->
                a.getProfile().getName().compareToIgnoreCase(b.getProfile().getName()));
        }

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
            .dimensions(panelX + sc(cb.offsetX), panelY + sc(cb.offsetY), sc(cb.width), sc(cb.height))
            .tooltip(Tooltip.of(Text.literal(cb.tooltip)))
            .build());

        if (isSingleplayer()) return;

        // GUI Editor button — only in multiplayer, only when unlocked via /personalbeaconop edit
        if (GuiEditorScreen.editorUnlocked && !isSingleplayer()) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("⚙"),
                b -> { if (this.client != null) this.client.setScreen(new GuiEditorScreen(this)); })
                .dimensions(panelX + panelW - sc(22), panelY + sc(2), sc(20), sc(20))
                .tooltip(Tooltip.of(Text.literal("Edit GUI Layout  (/personalbeaconop edit to toggle)")))
                .build());
        }

        // ── Unrestricted state: show "Restrict Access" button, skip player list ─
        if (!showingRestrictedView && allowedPlayers.isEmpty()) {
            boolean canManage = isSelfOwner() || ownerUUIDs.isEmpty();
            GuiLayoutConfig.ButtonEntry ra = cfg.restrictAccessButton;
            ButtonWidget raBtn = ButtonWidget.builder(
                Text.translatable("personalbeacon.screen.restrict_access"),
                b -> { showingRestrictedView = true; rebuildDisplayList(); rebuildButtons(); })
                .dimensions(panelX + sc(ra.offsetX), panelY + sc(ra.offsetY), sc(ra.width), sc(ra.height))
                .tooltip(Tooltip.of(Text.literal(ra.tooltip)))
                .build();
            if (!canManage) raBtn.active = false;
            this.addDrawableChild(raBtn);
            return;
        }

        // ── "Open to All" button — visible when in restricted view ────────────
        if (isSelfOwner() || ownerUUIDs.isEmpty()) {
            GuiLayoutConfig.ButtonEntry oa = cfg.openToAllButton;
            this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("personalbeacon.screen.open_to_all"),
                b -> sendUnrestrict())
                .dimensions(panelX + sc(oa.offsetX), panelY + sc(oa.offsetY), sc(oa.width), sc(oa.height))
                .tooltip(Tooltip.of(Text.literal(oa.tooltip)))
                .build());
        }

        // Empty list — skip scroll/row buttons; render() will show a hint instead
        if (displayList.isEmpty()) return;

        // Scroll
        GuiLayoutConfig.ButtonEntry su = cfg.scrollUpButton;
        GuiLayoutConfig.ButtonEntry sd = cfg.scrollDownButton;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("▲"),
            btn -> { if (scrollOffset > 0) { scrollOffset--; rebuildButtons(); } })
            .dimensions(panelX + sc(su.offsetX), panelY + sc(su.offsetY), sc(su.width), sc(su.height))
            .tooltip(Tooltip.of(Text.literal(su.tooltip)))
            .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("▼"),
            btn -> { if (scrollOffset < Math.max(0, displayList.size() - visibleRows)) { scrollOffset++; rebuildButtons(); } })
            .dimensions(panelX + sc(sd.offsetX), panelY + sc(sd.offsetY), sc(sd.width), sc(sd.height))
            .tooltip(Tooltip.of(Text.literal(sd.tooltip)))
            .build());

        // Player rows — access toggle + owner star button
        GuiLayoutConfig.ToggleButtonEntry tb  = cfg.toggleButton;
        GuiLayoutConfig.ToggleButtonEntry otb = cfg.ownerToggleButton;
        int listStartY = panelY + sc(su.offsetY);

        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollOffset + i;
            if (idx >= displayList.size()) break;

            DisplayEntry entry = displayList.get(idx);
            int rowY = listStartY + i * sc(ROW_HEIGHT);
            boolean canToggle = isSelfOwner() || ownerUUIDs.isEmpty();

            // ── Access toggle button (ALLOWED / BLOCKED) ──────────────────────
            // Owners are always allowed and cannot be toggled
            boolean isOwnerRow = entry.isOwner;
            String tipText = isOwnerRow
                ? Text.translatable("personalbeacon.screen.owner_always_allowed").getString()
                : (entry.allowed ? tb.tooltipAllowed : tb.tooltipBlocked);
            ButtonWidget toggleBtn = ButtonWidget.builder(
                entry.allowed
                    ? Text.translatable("personalbeacon.screen.allowed")
                    : Text.translatable("personalbeacon.screen.blocked"),
                b -> { if (canToggle && !isOwnerRow) togglePlayer(entry.uuid, entry.name, !entry.allowed); })
                .dimensions(panelX + panelW - sc(tb.offsetXFromRight), rowY + sc(tb.rowOffsetY), sc(tb.width), sc(tb.height))
                .tooltip(Tooltip.of(Text.literal(tipText)))
                .build();
            if (isOwnerRow) toggleBtn.active = false;
            this.addDrawableChild(toggleBtn);

            // ── Owner star button (★ / ☆) — Issue 3 ──────────────────────────
            // Only show if there is at least one owner set (i.e., beacon is owned)
            if (!ownerUUIDs.isEmpty() || primaryOwnerUUID != null) {
                boolean selfCanManageOwners = isSelfOwner();
                String starLabel = entry.isOwner ? "★" : "☆";
                String starTip   = entry.isOwner ? otb.tooltipAllowed : otb.tooltipBlocked;

                if (pendingOwnerChanges.contains(entry.uuid)) {
                    // In-flight — show disabled "…" until S2C confirms
                    ButtonWidget pendingBtn = ButtonWidget.builder(Text.literal("…"), b -> {})
                        .dimensions(panelX + panelW - sc(otb.offsetXFromRight), rowY + sc(otb.rowOffsetY), sc(otb.width), sc(otb.height))
                        .tooltip(Tooltip.of(Text.translatable("personalbeacon.screen.owner_pending")))
                        .build();
                    pendingBtn.active = false;
                    this.addDrawableChild(pendingBtn);
                } else if (entry.isPrimaryOwner) {
                    // Primary owner — disabled, can never be removed
                    ButtonWidget starBtn = ButtonWidget.builder(Text.literal("★"),
                        b -> { /* cannot remove primary owner */ })
                        .dimensions(panelX + panelW - sc(otb.offsetXFromRight), rowY + sc(otb.rowOffsetY), sc(otb.width), sc(otb.height))
                        .tooltip(Tooltip.of(Text.translatable("personalbeacon.screen.primary_owner")))
                        .build();
                    starBtn.active = false;
                    this.addDrawableChild(starBtn);
                } else {
                    this.addDrawableChild(ButtonWidget.builder(Text.literal(starLabel),
                        b -> {
                            if (selfCanManageOwners) {
                                sendOwnerToggle(entry.uuid, !entry.isOwner);
                            }
                        })
                        .dimensions(panelX + panelW - sc(otb.offsetXFromRight), rowY + sc(otb.rowOffsetY), sc(otb.width), sc(otb.height))
                        .tooltip(Tooltip.of(Text.literal(starTip)))
                        .build());
                }
            }
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

        // Action bar feedback — native MC overlay above hotbar
        if (client != null && client.player != null) {
            Text msg = add
                ? Text.translatable("personalbeacon.feedback.added", name)
                : Text.translatable("personalbeacon.feedback.removed", name);
            client.player.sendMessage(msg, true);
        }

        if (allowedPlayers.isEmpty()) scrollOffset = 0;
        rebuildDisplayList();
        rebuildButtons();
    }

    /** Send a C2S owner-toggle packet. */
    private void sendOwnerToggle(UUID uuid, boolean add) {
        if (beaconPos == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(beaconPos);
        buf.writeUuid(uuid);
        buf.writeBoolean(add);
        ClientPlayNetworking.send(ModNetworking.C2S_TOGGLE_OWNER, buf);
        // Mark as pending so the button shows "…" immediately while waiting for S2C
        pendingOwnerChanges.add(uuid);
        rebuildButtons();
    }

    /** Remove all restrictions and return to the open-to-everyone state. */
    private void sendUnrestrict() {
        showingRestrictedView = false;
        if (!allowedPlayers.isEmpty() && beaconPos != null) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(beaconPos);
            ClientPlayNetworking.send(ModNetworking.C2S_UNRESTRICT, buf);
            allowedPlayers.clear();
            scrollOffset = 0;
            if (client != null && client.player != null) {
                client.player.sendMessage(
                    Text.translatable("personalbeacon.feedback.unrestricted"), true);
            }
        }
        rebuildDisplayList();
        rebuildButtons();
    }

    /** Called by PersonalBeaconClient when S2C packet arrives. */
    public void receiveData(BlockPos pos, UUID primaryOwner, Set<UUID> owners,
                            Map<UUID, String> allowed, Map<UUID, String> names) {
        this.beaconPos = pos;
        this.primaryOwnerUUID = primaryOwner;

        // Emit action bar feedback for confirmed owner changes
        if (!pendingOwnerChanges.isEmpty() && client != null && client.player != null) {
            Set<UUID> newOwners = owners;
            for (UUID pending : pendingOwnerChanges) {
                boolean wasOwner = ownerUUIDs.contains(pending);
                boolean isNowOwner = newOwners.contains(pending);
                String playerName = names.getOrDefault(pending, nameCache.getOrDefault(pending, "?"));
                if (!wasOwner && isNowOwner) {
                    client.player.sendMessage(
                        Text.translatable("personalbeacon.feedback.owner_added", playerName), true);
                } else if (wasOwner && !isNowOwner) {
                    client.player.sendMessage(
                        Text.translatable("personalbeacon.feedback.owner_removed", playerName), true);
                }
            }
            pendingOwnerChanges.clear();
        }

        this.ownerUUIDs = new HashSet<>(owners);
        this.allowedPlayers = new LinkedHashMap<>(allowed);
        this.nameCache = new HashMap<>(names);
        // Server confirmed beacon is unrestricted — leave restrict-view mode
        if (allowed.isEmpty()) showingRestrictedView = false;

        while (!pendingToggles.isEmpty()) {
            var entry = pendingToggles.poll();
            PersonalBeaconMod.LOGGER.debug("Processing queued toggle for {}", entry.getKey());
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
        receiveData(pos, primaryOwnerUUID, ownerUUIDs, map, nameCache);
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
            panelX + sc(w1.offsetX), panelY + sc(w1.offsetY),
            sc(w1.width), sc(w1.height), w1.textureWidth, w1.textureHeight);

        // ── Title + coordinates ───────────────────────────────────────────────
        GuiLayoutConfig.TextEntry tt = cfg.titleText;
        drawScaledCenteredText(context,
            Text.translatable("personalbeacon.screen.title"),
            panelX + sc(tt.offsetX), panelY + sc(tt.offsetY), tt.scale * panelScale, tt.color());

        String coordText = beaconPos != null
            ? beaconPos.getX() + ", " + beaconPos.getY() + ", " + beaconPos.getZ()
            : Text.translatable("personalbeacon.screen.loading").getString();
        GuiLayoutConfig.TextEntry ct = cfg.coordsText;
        drawScaledCenteredText(context,
            Text.literal(coordText),
            panelX + sc(ct.offsetX), panelY + sc(ct.offsetY), ct.scale * panelScale, ct.color());

        // ── Singleplayer ──────────────────────────────────────────────────────
        if (isSingleplayer()) {
            // Window 2 replaces the old orange bordered box
            GuiLayoutConfig.WindowEntry w2 = cfg.window2;
            drawWindowTexture(context, WINDOW2_TEXTURE,
                panelX + sc(w2.offsetX), panelY + sc(w2.offsetY),
                sc(w2.width), sc(w2.height), w2.textureWidth, w2.textureHeight);

            GuiLayoutConfig.TextEntry st = cfg.singleplayerTitle;
            drawCenteredText(context,
                Text.translatable("personalbeacon.screen.singleplayer_title"),
                panelX + sc(st.offsetX), panelY + sc(st.offsetY), st.color());

            GuiLayoutConfig.TextEntry sd = cfg.singleplayerDesc;
            drawCenteredText(context,
                Text.translatable("personalbeacon.screen.singleplayer_desc"),
                panelX + sc(sd.offsetX), panelY + sc(sd.offsetY), sd.color());

            super.render(context, mouseX, mouseY, delta);
            return;
        }

        // ── Unrestricted state (beacon open to all) ───────────────────────────
        if (!showingRestrictedView && allowedPlayers.isEmpty()) {
            GuiLayoutConfig.WindowEntry w2 = cfg.window2;
            drawWindowTexture(context, WINDOW2_TEXTURE,
                panelX + sc(w2.offsetX), panelY + sc(w2.offsetY),
                sc(w2.width), sc(w2.height), w2.textureWidth, w2.textureHeight);

            GuiLayoutConfig.TextEntry ut = cfg.singleplayerTitle; // reuse same position
            drawCenteredText(context,
                Text.translatable("personalbeacon.screen.unrestricted_title"),
                panelX + sc(ut.offsetX), panelY + sc(ut.offsetY), ut.color());

            GuiLayoutConfig.TextEntry ud = cfg.singleplayerDesc;
            drawCenteredText(context,
                Text.translatable("personalbeacon.screen.unrestricted_desc"),
                panelX + sc(ud.offsetX), panelY + sc(ud.offsetY), ud.color());

            super.render(context, mouseX, mouseY, delta);
            return;
        }

        // ── Context description line ──────────────────────────────────────────
        GuiLayoutConfig.TextEntry dt = cfg.descText;
        drawScaledCenteredText(context,
            Text.translatable("personalbeacon.screen.desc"),
            panelX + sc(dt.offsetX), panelY + sc(dt.offsetY), dt.scale * panelScale, dt.color());

        // ── Owner line ────────────────────────────────────────────────────────
        if (primaryOwnerUUID != null) {
            String ownerName = nameCache.getOrDefault(primaryOwnerUUID, "Unknown");
            GuiLayoutConfig.TextEntry ot = cfg.ownerText;
            drawCenteredText(context,
                Text.translatable("personalbeacon.screen.owner", ownerName),
                panelX + sc(ot.offsetX), panelY + sc(ot.offsetY), ot.color());
        }

        // ── Column headers ────────────────────────────────────────────────────
        GuiLayoutConfig.TextEntry cp = cfg.columnPlayer;
        GuiLayoutConfig.TextEntry ca = cfg.columnAccess;
        int listStartY = panelY + sc(cfg.scrollUpButton.offsetY);

        context.drawText(this.textRenderer,
            Text.translatable("personalbeacon.screen.column_player"),
            panelX + sc(cp.offsetX), panelY + sc(cp.offsetY), cp.color(), false);
        context.drawText(this.textRenderer,
            Text.translatable("personalbeacon.screen.column_access"),
            panelX + sc(ca.offsetX), panelY + sc(ca.offsetY), ca.color(), false);
        context.fill(panelX + sc(8), panelY + sc(72), panelX + panelW - sc(8), panelY + sc(73), 0x44000000);

        // ── Empty list message ────────────────────────────────────────────────
        if (displayList.isEmpty()) {
            drawCenteredText(context,
                Text.translatable("personalbeacon.screen.no_players_nearby"),
                panelX + panelW / 2, panelY + sc(125), 0xFF888888);
            drawCenteredText(context,
                Text.translatable("personalbeacon.screen.no_players_hint"),
                panelX + panelW / 2, panelY + sc(140), 0xFF3D3D3D);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        // ── Player rows ───────────────────────────────────────────────────────
        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollOffset + i;
            if (idx >= displayList.size()) break;

            DisplayEntry entry = displayList.get(idx);
            int rowY = listStartY + i * sc(ROW_HEIGHT);

            GuiLayoutConfig.WindowEntry w3 = cfg.window3;
            drawWindowTexture(context, WINDOW3_TEXTURE,
                panelX + sc(w3.offsetX), rowY + sc(w3.offsetY),
                sc(w3.width), sc(w3.height), w3.textureWidth, w3.textureHeight);

            String prefix = entry.isOwner ? "★ " : (idx + 1) + ". ";
            String nameLabel = prefix + entry.name;

            int nameColor;
            if (entry.isOwner)                          nameColor = COLOR_OWNER;
            else if (entry.allowed && entry.online)     nameColor = COLOR_ALLOWED;
            else if (entry.allowed && !entry.online)    nameColor = COLOR_OFFLINE;
            else                                        nameColor = COLOR_TEXT_DIM;

            context.drawText(this.textRenderer,
                nameLabel, panelX + sc(14), rowY + sc(7), nameColor, false);

            int statusLabelX = panelX + sc(14) + this.textRenderer.getWidth(nameLabel) + sc(4);
            if (entry.online) {
                context.drawText(this.textRenderer,
                    "(online)", statusLabelX, rowY + sc(7), COLOR_ALLOWED, false);
            } else {
                context.drawText(this.textRenderer,
                    "(offline)", statusLabelX, rowY + sc(7), COLOR_OFFLINE, false);
            }
        }

        // ── Scroll counter ────────────────────────────────────────────────────
        if (displayList.size() > visibleRows) {
            String counter = (scrollOffset + 1) + "-"
                + Math.min(scrollOffset + visibleRows, displayList.size())
                + " / " + displayList.size();
            drawCenteredText(context,
                Text.literal(counter), panelX + panelW - sc(12), panelY + panelH / 2, COLOR_TEXT_DIM);
        }

        // ── Non-owner warning ─────────────────────────────────────────────────
        if (!ownerUUIDs.isEmpty() && !isSelfOwner()) {
            drawCenteredText(context,
                Text.translatable("personalbeacon.screen.view_only"),
                this.width / 2, panelY + panelH - sc(40), 0xFFAA6600);
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
