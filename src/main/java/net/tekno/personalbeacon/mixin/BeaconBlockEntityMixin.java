package net.tekno.personalbeacon.mixin;

import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.tekno.personalbeacon.BeaconAccessManager;
import net.tekno.personalbeacon.PersonalBeaconMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BeaconBlockEntity.class)
public class BeaconBlockEntityMixin {

    @Inject(
        method = "applyPlayerEffects",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void filterAndApplyEffects(
            World world,
            BlockPos pos,
            int beaconLevel,
            StatusEffect primaryEffect,
            StatusEffect secondaryEffect,
            CallbackInfo ci) {

        // Only run on server side
        if (world.isClient()) return;

        // Safety check — server must exist
        if (world.getServer() == null) return;

        BeaconAccessManager manager = BeaconAccessManager.get(world.getServer());

        // No restrictions — let vanilla handle it
        if (!manager.isRestricted(pos)) return;

        // Restricted — cancel vanilla, apply manually
        ci.cancel();

        // Vanilla range: Level 1 = 20, Level 2 = 30, Level 3 = 40, Level 4 = 50 blocks
        double range = 10 + (beaconLevel * 10.0);
        int duration = (9 + beaconLevel * 2) * 20;

        // Use server player list — more reliable than world.getPlayers() in singleplayer
        List<ServerPlayerEntity> serverPlayers = world.getServer().getPlayerManager().getPlayerList();

        for (ServerPlayerEntity player : serverPlayers) {
            // Must be in the same dimension
            if (!player.getWorld().getRegistryKey().equals(world.getRegistryKey())) continue;

            // Range check
            if (player.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) > range * range) continue;

            boolean allowed = manager.isAllowed(pos, player.getUuid());

            PersonalBeaconMod.LOGGER.debug(
                "Effect tick — player {} ({}) at beacon {} — allowed: {}",
                player.getName().getString(), player.getUuid(), pos, allowed
            );

            if (!allowed) continue;

            if (primaryEffect != null) {
                player.addStatusEffect(
                    new StatusEffectInstance(primaryEffect, duration, beaconLevel - 1, true, true)
                );
            }

            if (secondaryEffect != null && beaconLevel == 4) {
                player.addStatusEffect(
                    new StatusEffectInstance(secondaryEffect, duration, 0, true, true)
                );
            }
        }
    }
}
