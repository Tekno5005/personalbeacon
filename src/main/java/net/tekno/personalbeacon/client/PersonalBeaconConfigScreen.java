package net.tekno.personalbeacon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.tekno.personalbeacon.PersonalBeaconConfig;

/**
 * Edits config/personalbeacon.json. The settings are read by the server, so they apply to
 * worlds this game hosts (singleplayer, LAN); a dedicated server uses its own file.
 * Done saves at once, Cancel and Esc discard.
 */
@Environment(EnvType.CLIENT)
public class PersonalBeaconConfigScreen extends Screen {

    private static final int MAX_DISTANCE = 64;
    private static final int WIDTH = 310;

    private final Screen parent;
    private boolean allowNonOpManagement;
    private int maxManageDistance;

    public PersonalBeaconConfigScreen(Screen parent) {
        super(Text.translatable("personalbeacon.config.title"));
        this.parent = parent;
        PersonalBeaconConfig config = PersonalBeaconConfig.get();
        this.allowNonOpManagement = config.allowNonOpManagement;
        this.maxManageDistance = (int) Math.round(config.maxManageDistance);
    }

    @Override
    protected void init() {
        int x = (width - WIDTH) / 2;
        int y = height / 4 + 8;

        addDrawableChild(CyclingButtonWidget.onOffBuilder(allowNonOpManagement)
            .tooltip(value -> Tooltip.of(Text.translatable("personalbeacon.config.allow_non_op.tip")))
            .build(x, y, WIDTH, 20, Text.translatable("personalbeacon.config.allow_non_op"),
                (button, value) -> allowNonOpManagement = value));

        SliderWidget distance = new SliderWidget(x, y + 24, WIDTH, 20, Text.empty(),
                Math.min(maxManageDistance, MAX_DISTANCE) / (double) MAX_DISTANCE) {
            { updateMessage(); }

            @Override
            protected void updateMessage() {
                Text value = maxManageDistance == 0
                    ? ScreenTexts.OFF
                    : Text.translatable("personalbeacon.config.blocks", maxManageDistance);
                setMessage(Text.translatable("personalbeacon.config.max_distance", value));
            }

            @Override
            protected void applyValue() {
                maxManageDistance = (int) Math.round(value * MAX_DISTANCE);
            }
        };
        distance.setTooltip(Tooltip.of(Text.translatable("personalbeacon.config.max_distance.tip")));
        addDrawableChild(distance);

        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> save())
            .dimensions(width / 2 - 154, height - 28, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> close())
            .dimensions(width / 2 + 4, height - 28, 150, 20).build());
    }

    private void save() {
        PersonalBeaconConfig config = PersonalBeaconConfig.get();
        config.allowNonOpManagement = allowNonOpManagement;
        config.maxManageDistance = maxManageDistance;
        PersonalBeaconConfig.save();
        close();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("personalbeacon.config.note").formatted(Formatting.GRAY), width / 2, 36, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}
