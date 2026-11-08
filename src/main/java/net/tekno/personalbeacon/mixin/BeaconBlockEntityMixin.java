package net.tekno.personalbeacon.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.tekno.personalbeacon.BeaconAccessManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Mixin(BeaconBlockEntity.class)
public class BeaconBlockEntityMixin {

    /**
     * Filters the players vanilla found in beacon range down to the allowed ones.
     * Vanilla keeps its own range, duration and amplifier logic, and other mods'
     * modifications of this expression chain with ours.
     */
    @ModifyExpressionValue(
        method = "applyPlayerEffects",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getNonSpectatingEntities(Ljava/lang/Class;Lnet/minecraft/util/math/Box;)Ljava/util/List;"
        )
    )
    private static List<PlayerEntity> personalbeacon$filterAllowedPlayers(
            List<PlayerEntity> players,
            World world,
            BlockPos pos) {

        BeaconAccessManager manager = BeaconAccessManager.get(world.getServer());
        if (!manager.isRestricted(pos)) return players;

        List<PlayerEntity> allowed = new ArrayList<>(players.size());
        for (PlayerEntity player : players) {
            if (manager.isAllowed(pos, player.getUuid())) allowed.add(player);
        }
        return allowed;
    }
}
