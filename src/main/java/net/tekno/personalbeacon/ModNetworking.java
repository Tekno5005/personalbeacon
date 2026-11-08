package net.tekno.personalbeacon;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.BeaconScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.tekno.personalbeacon.mixin.BeaconScreenHandlerAccessor;

import java.util.Set;
import java.util.UUID;

public class ModNetworking {

    public static final Identifier C2S_UPDATE_ACCESS  = new Identifier("personalbeacon", "update_access");
    public static final Identifier C2S_REQUEST_SYNC   = new Identifier("personalbeacon", "request_sync");
    public static final Identifier C2S_TOGGLE_OWNER   = new Identifier("personalbeacon", "toggle_owner");
    public static final Identifier C2S_UNRESTRICT     = new Identifier("personalbeacon", "unrestrict");
    public static final Identifier S2C_SYNC_ACCESS    = new Identifier("personalbeacon", "sync_access");
    public static final Identifier S2C_TEST_MODE      = new Identifier("personalbeacon", "test_mode");

    private static volatile boolean testModeEnabled = false;

    public static boolean isTestModeEnabled() { return testModeEnabled; }

    public static void registerServerPackets() {

        // C2S: player wants to add/remove someone from a beacon
        ServerPlayNetworking.registerGlobalReceiver(C2S_UPDATE_ACCESS, (server, player, handler, buf, responseSender) -> {
            BlockPos beaconPos = buf.readBlockPos();
            UUID targetUUID   = buf.readUuid();
            boolean add       = buf.readBoolean();
            String targetName = buf.readString(); // name sent by client

            server.execute(() -> {
                if (!isPlayerNearBeacon(player, beaconPos)) {
                    PersonalBeaconMod.LOGGER.warn("Player {} tried to modify beacon at {} from too far!",
                        player.getName().getString(), beaconPos);
                    return;
                }

                BeaconAccessManager manager = BeaconAccessManager.get(server);

                // Owner check: if beacon has an owner and this player is not an owner, block
                if (PersonalBeaconConfig.get().allowNonOpManagement) {
                    if (manager.hasOwner(beaconPos)
                            && !manager.isOwner(beaconPos, player.getUuid())
                            && !player.hasPermissionLevel(2)) {
                        PersonalBeaconMod.LOGGER.warn("Player {} is not an owner of beacon at {}",
                            player.getName().getString(), beaconPos);
                        return;
                    }
                }

                if (add) {
                    // Resolve name: online player name takes priority over client-sent name
                    ServerPlayerEntity online = server.getPlayerManager().getPlayer(targetUUID);
                    String resolvedName = online != null ? online.getName().getString() : targetName;
                    manager.addPlayer(beaconPos, targetUUID, resolvedName);
                } else {
                    manager.removePlayer(beaconPos, targetUUID);
                }

                PersonalBeaconMod.LOGGER.info("Player {} {} {} ({}) for beacon at {}",
                    player.getName().getString(), add ? "added" : "removed",
                    targetName, targetUUID, beaconPos);
            });
        });

        // C2S: client opened BeaconAccessScreen, wants current data.
        // The beacon pos comes from the player's open beacon screen on the server —
        // the client-side screen handler has no position (its context is EMPTY).
        ServerPlayNetworking.registerGlobalReceiver(C2S_REQUEST_SYNC, (server, player, handler, buf, responseSender) ->
            server.execute(() -> {
                if (!(player.currentScreenHandler instanceof BeaconScreenHandler beaconHandler)) {
                    PersonalBeaconMod.LOGGER.warn("Player {} requested beacon sync without an open beacon screen",
                        player.getName().getString());
                    return;
                }
                ((BeaconScreenHandlerAccessor) beaconHandler).personalbeacon$getContext().run((world, beaconPos) -> {
                    PersonalBeaconMod.LOGGER.debug("Sync requested by {} for beacon at {}",
                        player.getName().getString(), beaconPos);
                    sendSyncPacket(player, beaconPos);
                });
            }));

        // C2S: player wants to add/remove a co-manager (owner toggle)
        // Packet layout: BlockPos beaconPos, UUID targetUUID, boolean add
        ServerPlayNetworking.registerGlobalReceiver(C2S_TOGGLE_OWNER, (server, player, handler, buf, responseSender) -> {
            BlockPos beaconPos = buf.readBlockPos();
            UUID targetUUID   = buf.readUuid();
            boolean add       = buf.readBoolean();

            server.execute(() -> {
                if (!isPlayerNearBeacon(player, beaconPos)) {
                    PersonalBeaconMod.LOGGER.warn("Player {} tried to modify owners of beacon at {} from too far!",
                        player.getName().getString(), beaconPos);
                    return;
                }

                BeaconAccessManager manager = BeaconAccessManager.get(server);

                // Only existing owners or ops can change owner status
                if (!manager.isOwner(beaconPos, player.getUuid())
                        && !player.hasPermissionLevel(2)) {
                    PersonalBeaconMod.LOGGER.warn("Player {} has no permission to change owners of beacon at {}",
                        player.getName().getString(), beaconPos);
                    return;
                }

                if (add) {
                    boolean ok = manager.addOwner(beaconPos, targetUUID);
                    if (!ok) {
                        PersonalBeaconMod.LOGGER.warn(
                            "Cannot add owner {} to beacon at {} — no primary owner set yet", targetUUID, beaconPos);
                        return;
                    }
                    PersonalBeaconMod.LOGGER.info("Player {} added {} as co-owner of beacon at {}",
                        player.getName().getString(), targetUUID, beaconPos);
                } else {
                    boolean ok = manager.removeOwner(beaconPos, targetUUID);
                    if (!ok) {
                        PersonalBeaconMod.LOGGER.warn(
                            "Cannot remove primary owner {} from beacon at {}", targetUUID, beaconPos);
                        return;
                    }
                    PersonalBeaconMod.LOGGER.info("Player {} removed {} as co-owner of beacon at {}",
                        player.getName().getString(), targetUUID, beaconPos);
                }

                // Push updated data back to the requesting player
                sendSyncPacket(player, beaconPos);
            });
        });

        // C2S: player wants to remove all restrictions (open beacon to everyone)
        // Packet layout: BlockPos beaconPos
        ServerPlayNetworking.registerGlobalReceiver(C2S_UNRESTRICT, (server, player, handler, buf, responseSender) -> {
            BlockPos beaconPos = buf.readBlockPos();

            server.execute(() -> {
                if (!isPlayerNearBeacon(player, beaconPos)) {
                    PersonalBeaconMod.LOGGER.warn("Player {} tried to unrestrict beacon at {} from too far!",
                        player.getName().getString(), beaconPos);
                    return;
                }

                BeaconAccessManager manager = BeaconAccessManager.get(server);

                if (manager.hasOwner(beaconPos)
                        && !manager.isOwner(beaconPos, player.getUuid())
                        && !player.hasPermissionLevel(2)) {
                    PersonalBeaconMod.LOGGER.warn("Player {} has no permission to unrestrict beacon at {}",
                        player.getName().getString(), beaconPos);
                    return;
                }

                manager.unrestrictBeacon(beaconPos);
                PersonalBeaconMod.LOGGER.info("Player {} opened beacon at {} to all players",
                    player.getName().getString(), beaconPos);
                sendSyncPacket(player, beaconPos);
            });
        });
    }

    /**
     * Send S2C sync packet.
     * Includes: beacon pos, primary owner UUID, owners with names, allowed list with names.
     * Online player names are cached on join (ENTITY_LOAD), so no caching is needed here.
     *
     * Packet layout:
     *   BlockPos beaconPos
     *   boolean  hasPrimaryOwner
     *   UUID     primaryOwnerUUID  (if hasPrimaryOwner)
     *   int      ownerCount
     *   for each: UUID, String name
     *   int      allowedCount
     *   for each: UUID, String name
     */
    public static void sendSyncPacket(ServerPlayerEntity player, BlockPos beaconPos) {
        BeaconAccessManager manager = BeaconAccessManager.get(player.getServer());

        Set<UUID> allowed = manager.getAllowedPlayers(beaconPos);
        UUID primaryOwner = manager.getPrimaryOwner(beaconPos);
        Set<UUID> owners  = manager.getOwners(beaconPos);

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(beaconPos);

        // Primary owner
        buf.writeBoolean(primaryOwner != null);
        if (primaryOwner != null) buf.writeUuid(primaryOwner);

        // All owners (co-managers including primary) with names
        writePlayers(buf, owners, manager);

        // Allowed players with names
        writePlayers(buf, allowed, manager);

        PersonalBeaconMod.LOGGER.debug("Sending sync to {} — beacon {}, {} allowed, {} owners",
            player.getName().getString(), beaconPos, allowed.size(), owners.size());
        ServerPlayNetworking.send(player, S2C_SYNC_ACCESS, buf);
    }

    private static void writePlayers(PacketByteBuf buf, Set<UUID> uuids, BeaconAccessManager manager) {
        buf.writeInt(uuids.size());
        for (UUID uuid : uuids) {
            buf.writeUuid(uuid);
            buf.writeString(manager.getPlayerName(uuid));
        }
    }

    public static void sendTestModeToggle(MinecraftServer server) {
        testModeEnabled = !testModeEnabled;
        PersonalBeaconMod.LOGGER.info("Test mode toggled — now {}", testModeEnabled ? "ON" : "OFF");
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(testModeEnabled);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, S2C_TEST_MODE, buf);
        }
    }

    private static boolean isPlayerNearBeacon(ServerPlayerEntity player, BlockPos beaconPos) {
        // Skip distance check on integrated (singleplayer) server
        if (!player.getServer().isDedicated()) return true;
        double maxDist = PersonalBeaconConfig.get().maxManageDistance;
        if (maxDist <= 0) return true;
        return player.getBlockPos().getSquaredDistance(beaconPos) <= maxDist * maxDist;
    }
}
