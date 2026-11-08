package net.tekno.personalbeacon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BeaconScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.tekno.personalbeacon.ModNetworking;
import net.tekno.personalbeacon.PersonalBeaconMod;
import net.tekno.personalbeacon.client.BeaconWidgets.AccessSwitch;
import net.tekno.personalbeacon.client.BeaconWidgets.FlatButton;

import java.util.*;

@Environment(EnvType.CLIENT)
public class BeaconAccessScreen extends Screen {

    // The panel is always drawn 1:1 (GUI px), so its texture and the text stay crisp.
    private static final int W = 300, H = 240, PAD = 10;
    private static final int LIST_W = W - 2 * PAD;
    private static final int ROW_H = 20, SCROLLBAR_W = 4;

    private static final Identifier PANEL =
        new Identifier(PersonalBeaconMod.MOD_ID, "textures/gui/custom_beacon_gui.png");
    private static final Identifier ICON =
        new Identifier(PersonalBeaconMod.MOD_ID, "textures/gui/beacon_icon.png");
    private static final int ICON_PX = 43;

    private static final int TEXT_DARK = 0xFF404040, TEXT_DIM = 0xFF4A4A4A, WHITE = 0xFFFFFFFF;
    private static final int GOLD = 0xFFFFAA00, GREEN = 0xFF55FF55, GRAY = 0xFFAAAAAA;

    enum SortMode { NAME, ACCESS, ONLINE }

    private enum View { SINGLEPLAYER, LOADING, UNRESTRICTED, LIST }

    /** Remembered while the game is running. */
    private static SortMode sortMode = SortMode.ACCESS;
    private static boolean testMode = false;

    public static void setTestMode(boolean enabled) { testMode = enabled; }
    public void onTestModeChanged() { clearAndInit(); }

    private record Row(UUID uuid, String name, boolean online, boolean allowed, boolean owner, boolean primary) {}

    private final BeaconScreen parent;

    private BlockPos beaconPos = null;
    private UUID primaryOwnerUUID = null;
    private Map<UUID, String> ownerNames = new HashMap<>();
    private Map<UUID, String> allowedPlayers = new LinkedHashMap<>();
    private final Map<UUID, PlayerListEntry> online = new HashMap<>();
    private final Set<UUID> pendingOwnerChanges = new HashSet<>();
    private boolean showingRestrictedView = false;
    private long confirmOpenUntil = 0;

    /** Order of the last sort. Toggling access never re-sorts, so rows stay where they are. */
    private final List<UUID> order = new ArrayList<>();
    private List<Row> rows = new ArrayList<>();
    private int scroll = 0;

    private int px, py, sortLabelX;
    private TextFieldWidget searchField;
    private final List<ClickableWidget> rowWidgets = new ArrayList<>();

    public BeaconAccessScreen(BeaconScreen parent) {
        super(Text.translatable("personalbeacon.screen.title"));
        this.parent = parent;
    }

    // -------------------------------------------------------
    // State
    // -------------------------------------------------------

    private boolean isSingleplayer() {
        if (testMode) return false;
        if (!this.client.isInSingleplayer()) return false;
        // IntegratedServer.getServerPort() returns -1 when not open to LAN.
        IntegratedServer srv = this.client.getServer();
        if (srv == null) return true;
        return srv.getServerPort() < 0;
    }

    private boolean isSelfOwner() {
        if (ownerNames.isEmpty()) return true; // no owners yet — anyone can manage
        return ownerNames.containsKey(this.client.player.getUuid());
    }

    private View view() {
        if (isSingleplayer()) return View.SINGLEPLAYER;
        if (beaconPos == null) return View.LOADING;
        if (!showingRestrictedView && allowedPlayers.isEmpty()) return View.UNRESTRICTED;
        return View.LIST;
    }

    private int listTop() { return py + (isSelfOwner() ? 66 : 82); }
    private int visibleRows() { return isSelfOwner() ? 6 : 5; }

    private void rebuildRows(boolean resort) {
        Map<UUID, Row> all = new LinkedHashMap<>();
        ownerNames.forEach((uuid, name) -> all.put(uuid,
            new Row(uuid, name, online.containsKey(uuid), true, true, uuid.equals(primaryOwnerUUID))));
        allowedPlayers.forEach((uuid, name) -> all.putIfAbsent(uuid,
            new Row(uuid, name, online.containsKey(uuid), true, false, false)));
        online.forEach((uuid, entry) -> all.putIfAbsent(uuid,
            new Row(uuid, entry.getProfile().getName(), true, false, false, false)));

        if (resort) order.clear();
        order.removeIf(uuid -> !all.containsKey(uuid));
        all.values().stream()
            .filter(r -> !order.contains(r.uuid()))
            .sorted(comparator(sortMode))
            .forEach(r -> order.add(r.uuid()));

        String q = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        rows = order.stream()
            .map(all::get)
            .filter(r -> q.isEmpty() || r.name().toLowerCase(Locale.ROOT).contains(q))
            .toList();
        scroll = Math.max(0, Math.min(scroll, rows.size() - visibleRows()));
    }

    private static Comparator<Row> comparator(SortMode mode) {
        Comparator<Row> byName = Comparator.comparing(Row::name, String.CASE_INSENSITIVE_ORDER);
        return switch (mode) {
            case NAME -> byName;
            case ACCESS -> Comparator.comparing((Row r) -> !(r.owner() || r.allowed())).thenComparing(byName);
            case ONLINE -> Comparator.comparing((Row r) -> !r.online()).thenComparing(byName);
        };
    }

    // -------------------------------------------------------
    // Widgets
    // -------------------------------------------------------

    @Override
    protected void init() {
        px = (this.width - W) / 2;
        py = (this.height - H) / 2;

        online.clear();
        for (PlayerListEntry e : this.client.getNetworkHandler().getPlayerList()) {
            online.put(e.getProfile().getId(), e);
        }
        rebuildRows(order.isEmpty());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("personalbeacon.screen.close"), b -> close())
            .dimensions(px + W - PAD - 90, py + 210, 90, 20).build());

        View v = view();
        if (v == View.UNRESTRICTED) {
            ButtonWidget restrict = new FlatButton(px + W / 2 - 60, py + 172, 120, 20,
                Text.translatable("personalbeacon.screen.restrict_access"), FlatButton.Style.TEAL, WHITE,
                b -> { showingRestrictedView = true; rebuildRows(true); clearAndInit(); });
            restrict.active = isSelfOwner();
            this.addDrawableChild(restrict);
        } else if (v == View.LIST) {
            initListWidgets();
        }
        refreshRowWidgets();
    }

    private void initListWidgets() {
        boolean manager = isSelfOwner();
        int rowY = py + (manager ? 46 : 61);

        // Sort buttons, right-aligned and joined (1px shared border)
        SortMode[] modes = SortMode.values();
        Text[] labels = new Text[modes.length];
        int total = 0;
        for (int i = 0; i < modes.length; i++) {
            labels[i] = Text.translatable("personalbeacon.screen.sort." + modes[i].name().toLowerCase(Locale.ROOT));
            total += textRenderer.getWidth(labels[i]) + 10 - 1;
        }
        int x = px + W - PAD - total - 1;
        for (int i = 0; i < modes.length; i++) {
            SortMode mode = modes[i];
            int w = textRenderer.getWidth(labels[i]) + 10;
            this.addDrawableChild(new FlatButton(x, rowY, w, 16, labels[i],
                mode == sortMode ? FlatButton.Style.TEAL : FlatButton.Style.GRAY, WHITE,
                b -> { sortMode = mode; scroll = 0; rebuildRows(true); clearAndInit(); }));
            if (i == 0) sortLabelX = x - 4 - textRenderer.getWidth(Text.translatable("personalbeacon.screen.sort"));
            x += w - 1;
        }

        // Search field: keeps its text across re-inits
        if (searchField == null) {
            Text hint = Text.translatable("personalbeacon.screen.search");
            searchField = new TextFieldWidget(textRenderer, 0, 0, 0, 14, hint);
            searchField.setPlaceholder(hint.copy().formatted(Formatting.DARK_GRAY));
            searchField.setChangedListener(s -> { scroll = 0; rebuildRows(false); refreshRowWidgets(); });
        }
        searchField.setX(px + PAD + 1);
        searchField.setY(rowY + 1);
        searchField.setWidth(sortLabelX - 6 - (px + PAD + 1));
        this.addDrawableChild(searchField);

        // "Open to All" asks once more before it clears the list
        if (manager) {
            boolean confirming = confirmOpenUntil != 0;
            this.addDrawableChild(new FlatButton(px + PAD, py + 210, 90, 20,
                Text.translatable(confirming ? "personalbeacon.screen.confirm_open" : "personalbeacon.screen.open_to_all"),
                confirming ? FlatButton.Style.ORANGE : FlatButton.Style.GRAY, WHITE,
                b -> {
                    if (confirmOpenUntil != 0) sendUnrestrict();
                    else { confirmOpenUntil = Util.getMeasuringTimeMs() + 3000; clearAndInit(); }
                }));
        }
    }

    /** Star and switch widgets of the visible rows; rebuilt on scroll, search, sort and toggles. */
    private void refreshRowWidgets() {
        rowWidgets.forEach(this::remove);
        rowWidgets.clear();
        if (view() != View.LIST) return;

        boolean manager = isSelfOwner();
        int top = listTop();
        int switchX = px + PAD + LIST_W - 1 - SCROLLBAR_W - 2 - AccessSwitch.W;
        int starX = switchX - 4 - 16;
        for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
            Row r = rows.get(scroll + i);
            int y = top + 1 + i * ROW_H;

            BeaconWidgets.Access state = r.owner() ? BeaconWidgets.Access.OWNER
                : r.allowed() ? BeaconWidgets.Access.ON : BeaconWidgets.Access.OFF;
            AccessSwitch sw = new AccessSwitch(switchX, y + 3, state, !manager,
                Text.translatable("personalbeacon.screen.switch." + state.name().toLowerCase(Locale.ROOT)),
                b -> sendToggle(r.uuid(), r.name(), !r.allowed()));
            sw.active = manager && !r.owner();
            sw.setTooltip(Tooltip.of(switchTooltip(r, manager)));
            rowWidgets.add(this.addDrawableChild(sw));

            if (manager && !ownerNames.isEmpty()) {
                boolean pending = pendingOwnerChanges.contains(r.uuid());
                FlatButton star = new FlatButton(starX, y + 2, 16, 16,
                    Text.literal(pending ? "…" : r.owner() ? "★" : "☆"), FlatButton.Style.DARK,
                    r.owner() ? GOLD : GRAY,
                    b -> sendOwnerToggle(r.uuid(), !r.owner()));
                star.active = !pending && !r.primary();
                star.setTooltip(Tooltip.of(starTooltip(r, pending)));
                rowWidgets.add(this.addDrawableChild(star));
            }
        }
    }

    private static Text switchTooltip(Row r, boolean manager) {
        if (r.owner()) return Text.translatable("personalbeacon.screen.owner_always_allowed");
        if (!manager) return Text.translatable("personalbeacon.feedback.no_permission");
        return Text.translatable(r.allowed() ? "personalbeacon.screen.tip.on" : "personalbeacon.screen.tip.off");
    }

    private static Text starTooltip(Row r, boolean pending) {
        if (pending) return Text.translatable("personalbeacon.screen.owner_pending");
        if (r.primary()) return Text.translatable("personalbeacon.screen.primary_owner");
        return Text.translatable(r.owner() ? "personalbeacon.screen.tip.co_manager" : "personalbeacon.screen.tip.make_co_manager");
    }

    @Override
    public void tick() {
        if (searchField != null) searchField.tick();
        if (confirmOpenUntil != 0 && Util.getMeasuringTimeMs() > confirmOpenUntil) {
            confirmOpenUntil = 0;
            clearAndInit();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (view() == View.LIST && rows.size() > visibleRows()) {
            scroll = Math.max(0, Math.min(scroll - (int) Math.signum(amount), rows.size() - visibleRows()));
            refreshRowWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    // -------------------------------------------------------
    // Network
    // -------------------------------------------------------

    private void sendToggle(UUID uuid, String name, boolean add) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(beaconPos);
        buf.writeUuid(uuid);
        buf.writeBoolean(add);
        ClientPlayNetworking.send(ModNetworking.C2S_UPDATE_ACCESS, buf);

        if (add) allowedPlayers.put(uuid, name);
        else allowedPlayers.remove(uuid);

        this.client.player.sendMessage(add
            ? Text.translatable("personalbeacon.feedback.added", name)
            : Text.translatable("personalbeacon.feedback.removed", name), true);

        rebuildRows(false);
        refreshRowWidgets();
    }

    private void sendOwnerToggle(UUID uuid, boolean add) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(beaconPos);
        buf.writeUuid(uuid);
        buf.writeBoolean(add);
        ClientPlayNetworking.send(ModNetworking.C2S_TOGGLE_OWNER, buf);
        // Shows "…" until the server's sync confirms the change
        pendingOwnerChanges.add(uuid);
        refreshRowWidgets();
    }

    /** Remove all restrictions and return to the open-to-everyone state. */
    private void sendUnrestrict() {
        showingRestrictedView = false;
        confirmOpenUntil = 0;
        if (!allowedPlayers.isEmpty()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(beaconPos);
            ClientPlayNetworking.send(ModNetworking.C2S_UNRESTRICT, buf);
            allowedPlayers.clear();
            this.client.player.sendMessage(Text.translatable("personalbeacon.feedback.unrestricted"), true);
        }
        rebuildRows(true);
        clearAndInit();
    }

    /** Called by PersonalBeaconClient when the S2C sync packet arrives. */
    public void receiveData(BlockPos pos, UUID primaryOwner,
                            Map<UUID, String> owners, Map<UUID, String> allowed) {
        boolean firstData = this.beaconPos == null;
        this.beaconPos = pos;
        this.primaryOwnerUUID = primaryOwner;

        // Action bar feedback for confirmed owner changes
        for (UUID pending : pendingOwnerChanges) {
            boolean wasOwner = ownerNames.containsKey(pending);
            boolean isNowOwner = owners.containsKey(pending);
            String playerName = owners.getOrDefault(pending, ownerNames.getOrDefault(pending, "?"));
            if (!wasOwner && isNowOwner) {
                this.client.player.sendMessage(Text.translatable("personalbeacon.feedback.owner_added", playerName), true);
            } else if (wasOwner && !isNowOwner) {
                this.client.player.sendMessage(Text.translatable("personalbeacon.feedback.owner_removed", playerName), true);
            }
        }
        pendingOwnerChanges.clear();

        this.ownerNames = new HashMap<>(owners);
        this.allowedPlayers = new LinkedHashMap<>(allowed);
        // Server confirmed the beacon is unrestricted — leave the restrict view
        if (allowed.isEmpty()) showingRestrictedView = false;

        rebuildRows(firstData);
        clearAndInit();
    }

    // -------------------------------------------------------
    // Rendering
    // -------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        ctx.drawTexture(PANEL, px, py, 0, 0, W, H, W, H);

        View v = view();
        drawHeader(ctx, v);
        switch (v) {
            case SINGLEPLAYER -> drawCentered(ctx, py + 58,
                Text.translatable("personalbeacon.screen.singleplayer_title"),
                Text.translatable("personalbeacon.screen.singleplayer_desc"));
            case LOADING -> drawSkeleton(ctx);
            case UNRESTRICTED -> {
                drawOwnerLine(ctx, py + 35);
                drawCentered(ctx, py + 52,
                    Text.translatable("personalbeacon.screen.unrestricted_title"),
                    Text.translatable("personalbeacon.screen.unrestricted_desc"));
            }
            case LIST -> drawList(ctx);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawHeader(DrawContext ctx, View v) {
        // 43x43 pixel art at half size (crisp at even GUI scales)
        ctx.getMatrices().push();
        ctx.getMatrices().translate(px + PAD, py + 8, 0);
        ctx.getMatrices().scale(0.5f, 0.5f, 1f);
        ctx.drawTexture(ICON, 0, 0, 0, 0, ICON_PX, ICON_PX, ICON_PX, ICON_PX);
        ctx.getMatrices().pop();

        int tx = px + PAD + 26;
        ctx.drawText(textRenderer, this.title, tx, py + 9, TEXT_DARK, false);
        Text sub = beaconPos == null
            ? Text.translatable("personalbeacon.screen.loading")
            : Text.translatable("personalbeacon.screen.beacon_pos", beaconPos.getX(), beaconPos.getY(), beaconPos.getZ());
        ctx.drawText(textRenderer, sub, tx, py + 20, TEXT_DIM, false);

        Text pill;
        int color;
        switch (v) {
            case SINGLEPLAYER -> { pill = Text.translatable("personalbeacon.screen.pill_singleplayer"); color = 0xFF5A5A5A; }
            case UNRESTRICTED -> { pill = Text.translatable("personalbeacon.screen.pill_open"); color = 0xFF2F7D32; }
            case LIST -> {
                Set<UUID> withAccess = new HashSet<>(allowedPlayers.keySet());
                withAccess.addAll(ownerNames.keySet());
                pill = Text.translatable("personalbeacon.screen.pill_restricted", withAccess.size());
                color = 0xFF276468;
            }
            default -> { return; }
        }
        int w = textRenderer.getWidth(pill) + 10;
        int x = px + W - PAD - w, y = py + 11;
        ctx.fill(x, y, x + w, y + 12, BeaconWidgets.BLACK);
        ctx.fill(x + 1, y + 1, x + w - 1, y + 11, color);
        ctx.drawText(textRenderer, pill, x + 5, y + 2, WHITE, true);
    }

    private void drawOwnerLine(DrawContext ctx, int y) {
        if (primaryOwnerUUID == null) return;
        Text line = Text.translatable("personalbeacon.screen.owner", ownerNames.getOrDefault(primaryOwnerUUID, "?"));
        int coManagers = ownerNames.size() - 1;
        if (coManagers > 0) line = line.copy().append(Text.translatable("personalbeacon.screen.co_managers", coManagers));
        ctx.drawText(textRenderer, line, px + PAD, y, TEXT_DIM, false);
    }

    /** Big icon, a title and a wrapped description, centered. */
    private void drawCentered(DrawContext ctx, int y, Text title, Text desc) {
        ctx.drawTexture(ICON, px + (W - ICON_PX) / 2, y, 0, 0, ICON_PX, ICON_PX, ICON_PX, ICON_PX);
        ctx.drawCenteredTextWithShadow(textRenderer, title, px + W / 2, y + 50, WHITE);
        int ly = y + 64;
        for (OrderedText line : textRenderer.wrapLines(desc, W - 40)) {
            ctx.drawText(textRenderer, line, px + (W - textRenderer.getWidth(line)) / 2, ly, TEXT_DIM, false);
            ly += 10;
        }
    }

    private void drawListBox(DrawContext ctx, int top, int rowCount, int fill) {
        int lx = px + PAD, h = rowCount * ROW_H + 2;
        ctx.fill(lx, top, lx + LIST_W, top + h, 0xFFFFFFFF);          // bottom/right light edge
        ctx.fill(lx, top, lx + LIST_W - 1, top + h - 1, 0xFF373737);  // top/left dark edge
        ctx.fill(lx + 1, top + 1, lx + LIST_W - 1, top + h - 1, fill);
    }

    private void drawList(DrawContext ctx) {
        boolean manager = isSelfOwner();
        if (manager) {
            drawOwnerLine(ctx, py + 35);
        } else {
            int y = py + 33;
            ctx.fill(px + PAD, y, px + W - PAD, y + 13, BeaconWidgets.BLACK);
            ctx.fill(px + PAD + 1, y + 1, px + W - PAD - 1, y + 12, 0xFFF2C14E);
            BeaconWidgets.lock(ctx, px + PAD + 3, y + 3, false,
                BeaconWidgets.LOCK_OUTLINE, BeaconWidgets.LOCK_BODY, BeaconWidgets.LOCK_SHINE);
            ctx.drawText(textRenderer, Text.translatable("personalbeacon.screen.view_only"), px + PAD + 14, y + 3, 0xFF3A2A00, false);
            drawOwnerLine(ctx, py + 50);
        }
        ctx.drawText(textRenderer, Text.translatable("personalbeacon.screen.sort"), sortLabelX, py + (manager ? 50 : 65), TEXT_DIM, false);

        int lx = px + PAD, top = listTop(), n = visibleRows();
        drawListBox(ctx, top, n, manager ? 0xFF1E1E1E : 0xFF101010);

        if (rows.isEmpty()) {
            boolean searching = searchField != null && !searchField.getText().isBlank();
            ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable(searching
                ? "personalbeacon.screen.no_results" : "personalbeacon.screen.no_players_nearby"),
                px + W / 2, top + 40, GRAY);
            if (!searching) ctx.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("personalbeacon.screen.no_players_hint"), px + W / 2, top + 54, 0xFF777777);
        }

        int starX = px + PAD + LIST_W - 1 - SCROLLBAR_W - 2 - AccessSwitch.W - 4 - 16;
        for (int i = 0; i < n && scroll + i < rows.size(); i++) {
            Row r = rows.get(scroll + i);
            int y = top + 1 + i * ROW_H;

            PlayerListEntry entry = online.get(r.uuid());
            Identifier skin = entry != null ? entry.getSkinTexture() : DefaultSkinHelper.getTexture(r.uuid());
            PlayerSkinDrawer.draw(ctx, skin, lx + 3, y + 2, 16);
            if (!manager) ctx.fill(lx + 3, y + 2, lx + 19, y + 18, 0x66000000);

            int nameColor = r.owner() ? (manager ? GOLD : 0xFFD49A1E) : (manager ? WHITE : 0xFFD0D0D0);
            ctx.drawText(textRenderer, r.name(), lx + 23, y + 2, nameColor, true);
            if (!manager && r.owner()) ctx.drawText(textRenderer, "★", starX + 5, y + 6, 0xFFD49A1E, true);

            int statusColor = r.online() ? (manager ? GREEN : 0xFF45CC45) : (manager ? GRAY : 0xFF9A9A9A);
            ctx.fill(lx + 23, y + 13, lx + 26, y + 16, statusColor);
            ctx.drawText(textRenderer, Text.translatable(r.online()
                ? "personalbeacon.screen.online" : "personalbeacon.screen.offline"), lx + 29, y + 11, statusColor, false);

            ctx.fill(lx + 1, y + ROW_H - 1, lx + LIST_W - 2 - SCROLLBAR_W, y + ROW_H, manager ? 0xFF262626 : 0xFF1A1A1A);
        }

        if (rows.size() > n) {
            int trackX = lx + LIST_W - 1 - SCROLLBAR_W, trackH = n * ROW_H;
            int thumbH = Math.max(8, trackH * n / rows.size());
            int thumbY = top + 1 + (trackH - thumbH) * scroll / (rows.size() - n);
            ctx.fill(trackX, top + 1, trackX + SCROLLBAR_W, top + 1 + trackH, 0xFF111111);
            ctx.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, 0xFF8B8B8B);
        }

        ctx.drawText(textRenderer, Text.translatable("personalbeacon.screen.desc"),
            px + PAD, top + n * ROW_H + 6, TEXT_DIM, false);
    }

    /** Placeholder rows while the server's data is on its way; no actions are possible yet. */
    private void drawSkeleton(DrawContext ctx) {
        int lx = px + PAD, top = py + 66;
        ctx.fill(px + PAD, py + 35, px + PAD + 110, py + 42, 0xFFA8A8A8);
        ctx.fill(px + PAD, py + 46, px + W - PAD, py + 62, 0xFF3A3A3A);
        drawListBox(ctx, top, 6, 0xFF1E1E1E);
        for (int i = 0; i < 6; i++) {
            int y = top + 1 + i * ROW_H;
            ctx.fill(lx + 3, y + 2, lx + 19, y + 18, 0xFF3A3A3A);
            ctx.fill(lx + 23, y + 4, lx + 23 + 50 + (i * 17) % 40, y + 10, 0xFF3A3A3A);
            ctx.fill(lx + 23, y + 13, lx + 59, y + 16, 0xFF2E2E2E);
            ctx.fill(lx + LIST_W - 1 - SCROLLBAR_W - 2 - AccessSwitch.W, y + 3, lx + LIST_W - 1 - SCROLLBAR_W - 2, y + 17, 0xFF2E2E2E);
        }
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}
