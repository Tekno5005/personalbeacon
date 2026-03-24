package net.tekno.personalbeacon.mixin;

import net.minecraft.inventory.Inventory;
import net.minecraft.screen.BeaconScreenHandler;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.util.math.BlockPos;
import net.tekno.personalbeacon.BeaconPosHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeaconScreenHandler.class)
public class BeaconScreenHandlerAccessor {

    @Unique
    private BlockPos personalbeacon$pos;

    @Inject(
        method = "<init>(ILnet/minecraft/inventory/Inventory;Lnet/minecraft/screen/PropertyDelegate;Lnet/minecraft/screen/ScreenHandlerContext;)V",
        at = @At("TAIL")
    )
    private void capturePos(int syncId,
                             Inventory inventory,
                             PropertyDelegate propertyDelegate,
                             ScreenHandlerContext context,
                             CallbackInfo ci) {
        // Store pos in a plain static holder class — no cross-mixin calls
        context.run((world, pos) -> {
            this.personalbeacon$pos = pos;
            BeaconPosHolder.setLastPos(pos);
        });
    }

    @Unique
    public BlockPos personalbeacon$getPos() {
        return personalbeacon$pos;
    }
}
