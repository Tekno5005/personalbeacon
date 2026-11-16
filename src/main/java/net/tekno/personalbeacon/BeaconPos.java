package net.tekno.personalbeacon;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A beacon's place: its dimension and its block position.
 * The dimension is the world's registry id ({@code minecraft:overworld}, {@code minecraft:the_nether}, ...).
 */
public record BeaconPos(Identifier dimension, BlockPos pos) {

    /** Dimension of entries saved before v2.2.0, which had none. See {@link BeaconAccessData#claimLegacy}. */
    public static final Identifier LEGACY_DIMENSION = new Identifier("personalbeacon", "legacy");

    public static BeaconPos of(World world, BlockPos pos) {
        return new BeaconPos(world.getRegistryKey().getValue(), pos.toImmutable());
    }

    public boolean isLegacy() {
        return dimension.equals(LEGACY_DIMENSION);
    }
}
