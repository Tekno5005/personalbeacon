package net.tekno.personalbeacon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BeaconScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.tekno.personalbeacon.ModNetworking;
import net.tekno.personalbeacon.PersonalBeaconMod;

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

    // Colors
    private static final int COLOR_BG_DARK    = 0xE0101018;
    private static final int COLOR_BORDER     = 0xFF3A6BC4;
    private static final int COLOR_BORDER_DIM = 0xFF1E3A6E;
    private static final int COLOR_ALLOWED    = 0xFF44DD77;
    private static final int COLOR_BLOCKED    = 0xFFAA4444;
    private static final int COLOR_OFFLINE    = 0xFF888888;
    private static final int COLOR_OWNER      = 0xFFFFCC44;
    private static final int COLOR_TEXT_MAIN  = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM   = 0xFF8899AA;
    private static final int COLOR_TEXT_GOLD  = 0xFFFFBB33;
    private static final int COLOR_HEADER_BG  = 0xE0000A20;
    private static final int COLOR_ROW_EVEN   = 0x331A2A40;
    private static final int COLOR_ROW_ODD    = 0x22000010;

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

        // Close
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("personalbeacon.screen.close"), btn -> this.close())
            .dimensions(panelX + panelW / 2 - 45, panelY + panelH - 28, 90, 20)
            .build());

        if (isSingleplayer()) return;

        // Empty state
        if (allowedPlayers.isEmpty() && !listExpanded) {
            this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("personalbeacon.screen.add_players"),
                btn -> { listExpanded = true; rebuildDisplayList(); rebuildButtons(); })
                .dimensions(panelX + panelW / 2 - 65, panelY + panelH / 2 - 10, 130, 22)
                .build());
            return;
        }

        // Scroll
        int listStartY = panelY + 75;
        int scrollBtnX = panelX + panelW - 22;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("▲"),
            btn -> { if (scrollOffset > 0) { scrollOffset--; rebuildButtons(); } })
            .dimensions(scrollBtnX, listStartY, 18, 18).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("▼"),
            btn -> { if (scrollOffset < Math.max(0, displayList.size() - VISIBLE_ROWS)) { scrollOffset++; rebuildButtons(); } })
            .dimensions(scrollBtnX, listStartY + (VISIBLE_ROWS - 1) * ROW_HEIGHT, 18, 18).build());

        // Player rows
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = scrollOffset + i;
            if (idx >= displayList.size()) break;

            DisplayEntry entry = displayList.get(idx);
            int rowY = listStartY + i * ROW_HEIGHT;
            boolean canToggle = isSelfOwner() || ownerUUID == null;

            this.addDrawableChild(ButtonWidget.builder(
                entry.allowed
                    ? Text.translatable("personalbeacon.screen.allowed")
                    : Text.translatable("personalbeacon.screen.blocked"),
                b -> { if (canToggle) togglePlayer(entry.uuid, entry.name, !entry.allowed); })
                .dimensions(panelX + panelW - 86, rowY + 3, 62, 18)
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

    private void drawLeftAccent(DrawContext ctx, int x, int y, int h, int color) {
        ctx.fill(x, y, x + 2, y + h, color);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        // Panel
        fillRect(context, panelX, panelY, panelW, panelH, COLOR_BG_DARK);
        drawBorder(context, panelX, panelY, panelW, panelH, COLOR_BORDER);

        // Header
        int headerH = 38;
        fillRect(context, panelX + 1, panelY + 1, panelW - 2, headerH, COLOR_HEADER_BG);
        context.fill(panelX + 1, panelY + headerH, panelX + panelW - 1, panelY + headerH + 1, COLOR_BORDER);
        drawLeftAccent(context, panelX + 1, panelY + 1, headerH, COLOR_BORDER);

        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.translatable("personalbeacon.screen.title"),
            this.width / 2, panelY + 8, COLOR_TEXT_MAIN);

        String coordText = beaconPos != null
            ? beaconPos.getX() + ",  " + beaconPos.getY() + ",  " + beaconPos.getZ()
            : Text.translatable("personalbeacon.screen.loading").getString();
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal(coordText), this.width / 2, panelY + 22, COLOR_TEXT_DIM);

        // Singleplayer
        if (isSingleplayer()) {
            fillRect(context, panelX + 10, panelY + 50, panelW - 20, 60, 0x441A0A00);
            drawBorder(context, panelX + 10, panelY + 50, panelW - 20, 60, 0xFFAA4400);
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("personalbeacon.screen.singleplayer_title"),
                this.width / 2, panelY + 63, COLOR_TEXT_GOLD);
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("personalbeacon.screen.singleplayer_desc"),
                this.width / 2, panelY + 80, COLOR_TEXT_DIM);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        // Owner line
        if (ownerUUID != null) {
            String ownerName = nameCache.getOrDefault(ownerUUID, "Unknown");
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("★ Owner: " + ownerName),
                this.width / 2, panelY + 44, COLOR_OWNER);
        }

        // Empty state
        if (allowedPlayers.isEmpty() && !listExpanded) {
            fillRect(context, panelX + 10, panelY + 55, panelW - 20, 48, 0x33003310);
            drawBorder(context, panelX + 10, panelY + 55, panelW - 20, 48, 0xFF224422);
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("✔  ").append(Text.translatable("personalbeacon.screen.no_restrictions")),
                this.width / 2, panelY + 64, COLOR_ALLOWED);
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("personalbeacon.screen.click_to_add"),
                this.width / 2, panelY + 78, COLOR_TEXT_DIM);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        // Column headers
        int listStartY = panelY + 75;
        context.drawTextWithShadow(this.textRenderer,
            Text.translatable("personalbeacon.screen.column_player"),
            panelX + 14, panelY + 62, 0xFF5588CC);
        context.drawTextWithShadow(this.textRenderer,
            Text.translatable("personalbeacon.screen.column_access"),
            panelX + panelW - 86, panelY + 62, 0xFF5588CC);
        context.fill(panelX + 8, panelY + 72, panelX + panelW - 8, panelY + 73, COLOR_BORDER_DIM);

        // Rows
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = scrollOffset + i;
            if (idx >= displayList.size()) break;

            DisplayEntry entry = displayList.get(idx);
            int rowY = listStartY + i * ROW_HEIGHT;
            int rowColor = (i % 2 == 0) ? COLOR_ROW_EVEN : COLOR_ROW_ODD;

            fillRect(context, panelX + 8, rowY, panelW - 30, ROW_HEIGHT - 2, rowColor);
            drawLeftAccent(context, panelX + 8, rowY, ROW_HEIGHT - 2,
                entry.allowed ? COLOR_ALLOWED : COLOR_BLOCKED);

            // Name with status indicators
            String prefix = entry.isOwner ? "★ " : (idx + 1) + ". ";
            String onlineTag = entry.online ? "" : " §(offline)";
            String nameLabel = prefix + entry.name;

            int nameColor;
            if (entry.isOwner) nameColor = COLOR_OWNER;
            else if (entry.allowed && entry.online) nameColor = COLOR_ALLOWED;
            else if (entry.allowed && !entry.online) nameColor = COLOR_OFFLINE;
            else nameColor = COLOR_TEXT_DIM;

            context.drawTextWithShadow(this.textRenderer,
                nameLabel, panelX + 14, rowY + 7, nameColor);

            // Offline tag
            if (!entry.online) {
                context.drawTextWithShadow(this.textRenderer,
                    "(offline)", panelX + 14 + this.textRenderer.getWidth(nameLabel) + 4,
                    rowY + 7, 0xFF666666);
            }
        }

        // Scroll counter
        if (displayList.size() > VISIBLE_ROWS) {
            String counter = (scrollOffset + 1) + "-"
                + Math.min(scrollOffset + VISIBLE_ROWS, displayList.size())
                + " / " + displayList.size();
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(counter), panelX + panelW - 12, panelY + panelH / 2, COLOR_TEXT_DIM);
        }

        // Non-owner warning
        if (ownerUUID != null && !isSelfOwner()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("⚠ View only — you are not the owner"),
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
