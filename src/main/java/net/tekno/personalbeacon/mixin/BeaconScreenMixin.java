package net.tekno.personalbeacon.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.ingame.BeaconScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.BeaconScreenHandler;
import net.minecraft.text.Text;
import net.tekno.personalbeacon.ModNetworking;
import net.tekno.personalbeacon.client.BeaconAccessScreen;
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
        // Manage Access button at the top-left of the vanilla beacon panel (11x11, see issue #4)
        this.addDrawableChild(new TextureButtonWidget(
            this.x + 12, this.y + 10, 11, 11,
            Text.translatable("personalbeacon.screen.manage_access"),
            btn -> {
                // Server resolves the beacon pos from our open beacon screen and
                // replies with S2C_SYNC_ACCESS, which carries the pos to the new screen.
                ClientPlayNetworking.send(ModNetworking.C2S_REQUEST_SYNC, PacketByteBufs.empty());
                this.client.setScreen(new BeaconAccessScreen(self));
            }));
    }
}
