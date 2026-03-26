package net.tekno.personalbeacon.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * A button that renders using custom PNG textures instead of the vanilla button sprite.
 * Uses Button.png for the normal state and Button_selected.png for the hovered/focused state.
 */
public class TextureButtonWidget extends ButtonWidget {

    private static final Identifier TEX_NORMAL =
        new Identifier("personalbeacon", "textures/gui/button.png");
    private static final Identifier TEX_HOVERED =
        new Identifier("personalbeacon", "textures/gui/button_selected.png");

    /** Native texture size (both PNGs are 20×20). */
    private static final int TEX_SIZE = 20;

    public TextureButtonWidget(int x, int y, int width, int height,
                               Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        // Show the button label as a tooltip so the icon remains clean
        this.setTooltip(Tooltip.of(message));
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        Identifier tex = (this.isHovered() || this.isFocused()) ? TEX_HOVERED : TEX_NORMAL;

        // Scale the 22×22 texture to fill the declared button dimensions
        float scaleX = (float) this.width  / TEX_SIZE;
        float scaleY = (float) this.height / TEX_SIZE;

        context.getMatrices().push();
        context.getMatrices().translate(this.getX(), this.getY(), 0);
        context.getMatrices().scale(scaleX, scaleY, 1.0f);
        context.drawTexture(tex, 0, 0, 0, 0, TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE);
        context.getMatrices().pop();
    }
}
