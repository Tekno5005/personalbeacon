package net.tekno.personalbeacon.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.ingame.BeaconScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.BeaconScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.tekno.personalbeacon.BeaconPosHolder;
import net.tekno.personalbeacon.ModNetworking;
import net.tekno.personalbeacon.client.BeaconAccessScreen;
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

        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("personalbeacon.screen.manage_access"),
            btn -> {
                if (this.client == null) return;

                BeaconAccessScreen screen = new BeaconAccessScreen(self);

                // Read pos from the plain holder — set by BeaconScreenHandlerAccessor
                BlockPos pos = BeaconPosHolder.getLastPos();
                if (pos != null) {
                    // Send C2S request so server replies with current access list
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeBlockPos(pos);
                    ClientPlayNetworking.send(ModNetworking.C2S_REQUEST_SYNC, buf);
                    screen.setBeaconPos(pos);
                }

                this.client.setScreen(screen);
            })
            .dimensions(this.x, this.y - 24, 130, 20)
            .build());
    }
}
