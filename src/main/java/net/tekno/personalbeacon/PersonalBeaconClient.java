package net.tekno.personalbeacon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.tekno.personalbeacon.client.BeaconAccessScreen;
import net.tekno.personalbeacon.client.GuiEditorScreen;

import java.util.*;

@Environment(EnvType.CLIENT)
public class PersonalBeaconClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        registerClientPackets();
        registerClientCommands();
        PersonalBeaconMod.LOGGER.info("Personal Beacon client initialized.");
    }

    private static void registerClientCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(
                ClientCommandManager.literal("personalbeaconop")
                    .then(ClientCommandManager.literal("edit")
                        .executes(ctx -> {
                            GuiEditorScreen.editorUnlocked = !GuiEditorScreen.editorUnlocked;
                            ctx.getSource().sendFeedback(
                                Text.literal(GuiEditorScreen.editorUnlocked
                                    ? "§a[PersonalBeacon] §fGUI Editor enabled — open a beacon to see the §e⚙§f button."
                                    : "§c[PersonalBeacon] §fGUI Editor disabled.")
                            );
                            return 1;
                        })
                    )
            )
        );
    }

    private static void registerClientPackets() {

        // S2C: server sends beacon access data
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.S2C_SYNC_ACCESS, (client, handler, buf, responseSender) -> {
            BlockPos beaconPos = buf.readBlockPos();

            // Owner
            boolean hasOwner = buf.readBoolean();
            UUID ownerUUID = hasOwner ? buf.readUuid() : null;

            // Allowed players
            int allowedCount = buf.readInt();
            Map<UUID, String> allowed = new LinkedHashMap<>();
            for (int i = 0; i < allowedCount; i++) {
                UUID uuid = buf.readUuid();
                String name = buf.readString();
                allowed.put(uuid, name);
            }

            // Full name cache
            int cacheCount = buf.readInt();
            Map<UUID, String> nameCache = new HashMap<>();
            for (int i = 0; i < cacheCount; i++) {
                UUID uuid = buf.readUuid();
                String name = buf.readString();
                nameCache.put(uuid, name);
            }

            client.execute(() -> {
                if (client.currentScreen instanceof BeaconAccessScreen screen) {
                    screen.receiveData(beaconPos, ownerUUID, allowed, nameCache);
                }
            });
        });

        // S2C: test mode toggle
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.S2C_TEST_MODE, (client, handler, buf, responseSender) -> {
            boolean enabled = buf.readBoolean();
            client.execute(() -> {
                BeaconAccessScreen.setTestMode(enabled);
                if (client.currentScreen instanceof BeaconAccessScreen screen) {
                    screen.onTestModeChanged();
                }
            });
        });
    }
}
