package net.tekno.personalbeacon;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.network.ServerPlayerEntity;
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

        // Access data of removed beacons is cleaned up in WorldChunkMixin.

        LOGGER.info("Personal Beacon mod initialized.");
    }
}
