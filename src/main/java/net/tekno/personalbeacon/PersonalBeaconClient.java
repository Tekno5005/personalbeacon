package net.tekno.personalbeacon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import net.tekno.personalbeacon.client.BeaconAccessScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Environment(EnvType.CLIENT)
public class PersonalBeaconClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("personalbeacon");

    @Override
    public void onInitializeClient() {
        registerClientPackets();
        PersonalBeaconMod.LOGGER.info("Personal Beacon client initialized.");
    }

    private static void registerClientPackets() {

        // S2C: server sends beacon access data
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.S2C_SYNC_ACCESS, (client, handler, buf, responseSender) -> {
            BlockPos beaconPos = buf.readBlockPos();

            // Primary owner
            boolean hasPrimaryOwner = buf.readBoolean();
            UUID primaryOwnerUUID = hasPrimaryOwner ? buf.readUuid() : null;

            // All owners (co-managers including primary) with names
            Map<UUID, String> owners = readPlayers(buf);

            // Allowed players with names
            Map<UUID, String> allowed = readPlayers(buf);

            LOGGER.debug("Received sync for beacon {} — {} allowed, {} owners",
                beaconPos, allowed.size(), owners.size());

            client.execute(() -> {
                if (client.currentScreen instanceof BeaconAccessScreen screen) {
                    screen.receiveData(beaconPos, primaryOwnerUUID, owners, allowed);
                }
            });
        });

        // S2C: test mode toggle
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.S2C_TEST_MODE, (client, handler, buf, responseSender) -> {
            boolean enabled = buf.readBoolean();
            LOGGER.debug("Received test mode toggle — now {}", enabled ? "ON" : "OFF");
            client.execute(() -> {
                BeaconAccessScreen.setTestMode(enabled);
                if (client.currentScreen instanceof BeaconAccessScreen screen) {
                    screen.onTestModeChanged();
                }
            });
        });
    }

    /** Reads the {@code int count; for each: UUID, String name} block written by ModNetworking. */
    private static Map<UUID, String> readPlayers(PacketByteBuf buf) {
        int count = buf.readInt();
        Map<UUID, String> players = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            players.put(buf.readUuid(), buf.readString());
        }
        return players;
    }
}
