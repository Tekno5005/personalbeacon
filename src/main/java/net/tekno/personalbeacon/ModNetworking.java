package net.tekno.personalbeacon;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ModNetworking {

    public static final Identifier C2S_UPDATE_ACCESS  = new Identifier("personalbeacon", "update_access");
    public static final Identifier C2S_REQUEST_SYNC   = new Identifier("personalbeacon", "request_sync");
    public static final Identifier S2C_SYNC_ACCESS    = new Identifier("personalbeacon", "sync_access");
    public static final Identifier S2C_TEST_MODE      = new Identifier("personalbeacon", "test_mode");

    private static boolean testModeEnabled = false;

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

                // Owner check: if beacon has an owner and this player is not the owner, block
                if (PersonalBeaconConfig.get().allowNonOpManagement) {
                    UUID owner = manager.getOwner(beaconPos);
                    if (owner != null && !owner.equals(player.getUuid())
                            && !player.hasPermissionLevel(2)) {
                        PersonalBeaconMod.LOGGER.warn("Player {} is not the owner of beacon at {}",
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

        // C2S: client opened BeaconAccessScreen, wants current data
        ServerPlayNetworking.registerGlobalReceiver(C2S_REQUEST_SYNC, (server, player, handler, buf, responseSender) -> {
            BlockPos beaconPos = buf.readBlockPos();
            server.execute(() -> sendSyncPacket(player, beaconPos));
        });
    }

    /**
     * Send S2C sync packet.
     * Includes: beacon pos, owner UUID (or null), allowed list with names, all known player names.
     *
     * Packet layout:
     *   BlockPos beaconPos
     *   boolean  hasOwner
     *   UUID     ownerUUID  (if hasOwner)
     *   int      allowedCount
     *   for each: UUID, String name
     *   int      namesCacheCount
     *   for each: UUID, String name
     */
    public static void sendSyncPacket(ServerPlayerEntity player, BlockPos beaconPos) {
        BeaconAccessManager manager = BeaconAccessManager.get(player.getServer());

        // Cache all online player names while we're here
        for (ServerPlayerEntity p : player.getServer().getPlayerManager().getPlayerList()) {
            manager.cachePlayerName(p.getUuid(), p.getName().getString());
        }

        Set<UUID> allowed = manager.getAllowedPlayers(beaconPos);
        UUID owner = manager.getOwner(beaconPos);
        Map<UUID, String> nameCache = manager.getPlayerNames();

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(beaconPos);

        // Owner
        buf.writeBoolean(owner != null);
        if (owner != null) buf.writeUuid(owner);

        // Allowed players with names
        buf.writeInt(allowed.size());
        for (UUID uuid : allowed) {
            buf.writeUuid(uuid);
            buf.writeString(manager.getPlayerName(uuid));
        }

        // Full name cache (for offline display)
        buf.writeInt(nameCache.size());
        for (Map.Entry<UUID, String> entry : nameCache.entrySet()) {
            buf.writeUuid(entry.getKey());
            buf.writeString(entry.getValue());
        }

        ServerPlayNetworking.send(player, S2C_SYNC_ACCESS, buf);
    }

    public static void sendTestModeToggle(MinecraftServer server) {
        testModeEnabled = !testModeEnabled;
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
        return Math.sqrt(player.getBlockPos().getSquaredDistance(beaconPos)) <= maxDist;
    }
}
