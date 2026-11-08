package net.tekno.personalbeacon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Code-drawn widgets and pixel helpers for the access screen; crisp at every GUI scale. */
@Environment(EnvType.CLIENT)
final class BeaconWidgets {

    private BeaconWidgets() {}

    static final int BLACK = 0xFF000000;
    static final int WHITE = 0xFFFFFFFF;
    static final int LOCK_OUTLINE = 0xFF392A1C;
    static final int LOCK_BODY = 0xFF927E6A;
    static final int LOCK_SHINE = 0xFFEFD8A1;

    /** Rect with a 1px black border and a 1px light (top/left) / dark (bottom/right) bevel inside it. */
    static void bevel(DrawContext ctx, int x, int y, int w, int h, int fill, int light, int dark) {
        ctx.fill(x, y, x + w, y + h, BLACK);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, dark);
        ctx.fill(x + 1, y + 1, x + w - 2, y + h - 2, light);
        ctx.fill(x + 2, y + 2, x + w - 2, y + h - 2, fill);
    }

    /** 8×8 pixel padlock; open = shackle lifted out of the body. */
    static void lock(DrawContext ctx, int x, int y, boolean open, int outline, int body, int shine) {
        if (open) {
            ctx.fill(x + 3, y, x + 7, y + 1, outline);
            ctx.fill(x + 2, y + 1, x + 3, y + 2, outline);
            ctx.fill(x + 7, y + 1, x + 8, y + 3, outline);
        } else {
            ctx.fill(x + 2, y, x + 6, y + 1, outline);
            ctx.fill(x + 1, y + 1, x + 2, y + 3, outline);
            ctx.fill(x + 6, y + 1, x + 7, y + 3, outline);
        }
        ctx.fill(x, y + 3, x + 8, y + 8, outline);
        ctx.fill(x + 1, y + 4, x + 7, y + 7, body);
        ctx.fill(x + 1, y + 4, x + 3, y + 5, shine);
        ctx.fill(x + 3, y + 5, x + 5, y + 7, outline);
    }

    /** Button drawn with fills in one of a few styles. */
    static class FlatButton extends ButtonWidget {

        enum Style {
            GRAY(0xFF6F6F6F, 0xFFAAAAAA, 0xFF555555),
            TEAL(0xFF276468, 0xFF3C9F9C, 0xFF183F39),
            ORANGE(0xFF9A4B12, 0xFFD9853F, 0xFF4D2509),
            DARK(0xFF2A2A2A, 0xFF3A3A3A, 0xFF1A1A1A);

            final int fill, light, dark;

            Style(int fill, int light, int dark) {
                this.fill = fill;
                this.light = light;
                this.dark = dark;
            }
        }

        private final Style style;
        private final int textColor;

        FlatButton(int x, int y, int w, int h, Text label, Style style, int textColor, PressAction onPress) {
            super(x, y, w, h, label, onPress, DEFAULT_NARRATION_SUPPLIER);
            this.style = style;
            this.textColor = textColor;
        }

        @Override
        public void renderButton(DrawContext ctx, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY();
            bevel(ctx, x, y, width, height, style.fill, style.light, style.dark);
            if (active && (isHovered() || isFocused())) ctx.drawBorder(x, y, width, height, WHITE);
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            ctx.drawCenteredTextWithShadow(tr, getMessage(), x + width / 2, y + (height - 7) / 2,
                active ? textColor : 0xFFA0A0A0);
        }
    }

    enum Access { ON, OFF, OWNER }

    /** Access switch: a track, a knob with an open/closed padlock and a short label. */
    static class AccessSwitch extends ButtonWidget {

        static final int W = 54, H = 14;

        private final Access state;
        private final boolean dimmed;

        AccessSwitch(int x, int y, Access state, boolean dimmed, Text label, PressAction onPress) {
            super(x, y, W, H, label, onPress, DEFAULT_NARRATION_SUPPLIER);
            this.state = state;
            this.dimmed = dimmed;
        }

        @Override
        public void renderButton(DrawContext ctx, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY();
            int track = switch (state) { case ON -> 0xFF276468; case OWNER -> 0xFF684C3C; case OFF -> 0xFF2B2B2B; };
            int label = switch (state) { case ON -> WHITE; case OWNER -> 0xFFEFD8A1; case OFF -> 0xFFC8C8C8; };
            boolean open = state != Access.OFF;

            ctx.fill(x, y, x + W, y + H, BLACK);
            ctx.fill(x + 1, y + 1, x + W - 1, y + H - 1, track);

            int kx = open ? x + W - 12 : x + 2;
            ctx.fill(kx, y + 2, kx + 10, y + 12, open ? 0xFFEFD8A1 : 0xFF8B8B8B);
            lock(ctx, kx + 1, y + 3, open, LOCK_OUTLINE, LOCK_BODY, LOCK_SHINE);

            int labelLeft = open ? x + 2 : x + 14;
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            ctx.drawCenteredTextWithShadow(tr, getMessage(), labelLeft + (W - 16) / 2, y + 3, label);

            if (active && (isHovered() || isFocused())) ctx.drawBorder(x, y, W, H, WHITE);
            if (dimmed) ctx.fill(x, y, x + W, y + H, 0x88101010);
        }
    }
}
