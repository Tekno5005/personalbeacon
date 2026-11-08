package net.tekno.personalbeacon.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.ingame.BeaconScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.BeaconScreenHandler;
import net.minecraft.text.Text;
import net.tekno.personalbeacon.ModNetworking;
import net.tekno.personalbeacon.client.BeaconAccessScreen;
import net.tekno.personalbeacon.client.GuiEditorScreen;
import net.tekno.personalbeacon.client.GuiLayoutConfig;
import net.tekno.personalbeacon.client.TextureButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(BeaconScreen.class)
public abstract class BeaconScreenMixin extends HandledScreen<BeaconScreenHandler> {

    public BeaconScreenMixin(BeaconScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addAccessButton(CallbackInfo ci) {
        BeaconScreen self = (BeaconScreen)(Object) this;
        GuiLayoutConfig.ButtonEntry manageBtn = GuiLayoutConfig.get().manageAccessButton;

        // ── Manage Access button ──────────────────────────────────────────────
        this.addDrawableChild(new TextureButtonWidget(
            this.x + manageBtn.offsetX, this.y + manageBtn.offsetY, manageBtn.width, manageBtn.height,
            Text.translatable("personalbeacon.screen.manage_access"),
            btn -> {
                if (this.client == null) return;

                // Server resolves the beacon pos from our open beacon screen and
                // replies with S2C_SYNC_ACCESS, which carries the pos to the new screen.
                ClientPlayNetworking.send(ModNetworking.C2S_REQUEST_SYNC, PacketByteBufs.empty());
                this.client.setScreen(new BeaconAccessScreen(self));
            }));

        // ── GUI Editor button — visible on this screen when editor is unlocked ─
        // Lets the user reposition manageAccessButton while seeing it live.
        if (GuiEditorScreen.editorUnlocked) {
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("⚙"),
                b -> { if (this.client != null) this.client.setScreen(new GuiEditorScreen(self)); })
                .dimensions(this.x + this.backgroundWidth - 20, this.y + 2, 18, 18)
                .tooltip(Tooltip.of(Text.literal("Edit GUI Layout  (/personalbeaconop edit to toggle)")))
                .build());
        }
    }
}
