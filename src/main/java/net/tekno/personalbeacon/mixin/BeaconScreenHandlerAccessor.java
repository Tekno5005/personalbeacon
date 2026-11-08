package net.tekno.personalbeacon.mixin;

import net.minecraft.screen.BeaconScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the server-side context (world + beacon pos) of an open beacon screen.
 * On the client the context is always EMPTY, so only the server may use this.
 */
@Mixin(BeaconScreenHandler.class)
public interface BeaconScreenHandlerAccessor {

    @Accessor("context")
    ScreenHandlerContext personalbeacon$getContext();
}
