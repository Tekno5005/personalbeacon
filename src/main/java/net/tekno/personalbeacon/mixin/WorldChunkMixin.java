package net.tekno.personalbeacon.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import net.tekno.personalbeacon.BeaconAccessManager;
import net.tekno.personalbeacon.PersonalBeaconMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldChunk.class)
public class WorldChunkMixin {

    /**
     * Purges a beacon's access data when its block entity is removed from the world.
     * Every block replacement on the server (player break, explosion, /setblock, /fill)
     * reaches here via AbstractBlock.onStateReplaced -> World.removeBlockEntity.
     * Chunk unloading does not (it uses WorldChunk.clear), so data of unloaded beacons is kept.
     */
    @ModifyExpressionValue(
        method = "removeBlockEntity",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"
        )
    )
    private Object personalbeacon$purgeRemovedBeacon(Object removed, BlockPos pos) {
        if (removed instanceof BeaconBlockEntity
                && ((WorldChunk) (Object) this).getWorld() instanceof ServerWorld world) {
            BeaconAccessManager.get(world.getServer()).removeBeacon(pos);
            PersonalBeaconMod.LOGGER.debug("Purged access data of removed beacon at {}", pos);
        }
        return removed;
    }
}
