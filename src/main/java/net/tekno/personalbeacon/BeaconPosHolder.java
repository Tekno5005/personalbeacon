package net.tekno.personalbeacon;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;

/**
 * Simple client-side static holder for the last opened beacon's position.
 * Set by BeaconScreenHandlerAccessor, read by BeaconScreenMixin.
 * Not a mixin — just a plain class to avoid cross-mixin static calls.
 */
@Environment(EnvType.CLIENT)
public class BeaconPosHolder {

    private static BlockPos lastPos = null;

    public static void setLastPos(BlockPos pos) {
        lastPos = pos;
    }

    public static BlockPos getLastPos() {
        return lastPos;
    }
}
