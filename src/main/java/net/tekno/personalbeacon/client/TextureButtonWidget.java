package net.tekno.personalbeacon.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The Manage Access button on the vanilla beacon screen: button.png normally,
 * button_selected.png when hovered or focused, drawn 1:1 so the pixel art stays crisp.
 */
public class TextureButtonWidget extends ButtonWidget {

    private static final Identifier TEX_NORMAL =
        new Identifier("personalbeacon", "textures/gui/button.png");
    private static final Identifier TEX_HOVERED =
        new Identifier("personalbeacon", "textures/gui/button_selected.png");

    /** Size of both PNGs and of the button. */
    public static final int SIZE = 11;

    public TextureButtonWidget(int x, int y, Text message, PressAction onPress) {
        super(x, y, SIZE, SIZE, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        // Show the button label as a tooltip so the icon remains clean
        this.setTooltip(Tooltip.of(message));
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        Identifier tex = (this.isHovered() || this.isFocused()) ? TEX_HOVERED : TEX_NORMAL;
        context.drawTexture(tex, this.getX(), this.getY(), 0, 0, SIZE, SIZE, SIZE, SIZE);
    }
}
