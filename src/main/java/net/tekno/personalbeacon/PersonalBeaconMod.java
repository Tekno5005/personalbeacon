package net.tekno.personalbeacon;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersonalBeaconMod implements ModInitializer {

    public static final String MOD_ID = "personalbeacon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Load config first
        PersonalBeaconConfig.load();

        ModNetworking.registerServerPackets();
        DebugCommand.register();

        // Cache player name when they join
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ServerPlayerEntity player && world.getServer() != null) {
                BeaconAccessManager manager = BeaconAccessManager.get(world.getServer());
                manager.cachePlayerName(player.getUuid(), player.getName().getString());
            }
        });

        // Send sync packet when player right-clicks beacon
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            BlockPos pos = hitResult.getBlockPos();
            if (world.getBlockState(pos).isOf(Blocks.BEACON)) {
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    LOGGER.debug("Player {} right-clicked beacon at {}", serverPlayer.getName().getString(), pos);
                    world.getServer().execute(() -> {
                        // Singleplayer: if beacon has no restrictions, auto-add the player
                        // Prevents softlock where nobody receives effects
                        if (!world.getServer().isDedicated()) {
                            BeaconAccessManager manager = BeaconAccessManager.get(world.getServer());
                            if (!manager.isRestricted(pos)) {
                                manager.addPlayer(pos, serverPlayer.getUuid(), serverPlayer.getName().getString());
                                LOGGER.info("Singleplayer: auto-added {} as beacon owner at {}",
                                    serverPlayer.getName().getString(), pos);
                            }
                        }
                        ModNetworking.sendSyncPacket(serverPlayer, pos);
                    });
                }
            }
            return ActionResult.PASS;
        });

        // Clean up data when beacon is broken
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (state.isOf(Blocks.BEACON)) {
                BeaconAccessManager manager = BeaconAccessManager.get(world.getServer());
                manager.removeBeacon(pos);
                LOGGER.info("Cleaned up access data for broken beacon at {}", pos);
            }
        });

        LOGGER.info("Personal Beacon mod initialized.");
    }
}
