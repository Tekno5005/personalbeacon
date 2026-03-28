package net.tekno.personalbeacon;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BeaconAccessDataTest {

    // -------------------------------------------------------
    // Unrestricted baseline
    // -------------------------------------------------------

    @Test
    void isAllowed_unrestricted_everyoneAllowed() {
        BeaconAccessData data = new BeaconAccessData();
        BlockPos pos = new BlockPos(0, 64, 0);
        assertTrue(data.isAllowed(pos, UUID.randomUUID()));
        assertFalse(data.isRestricted(pos));
    }

    // -------------------------------------------------------
    // addPlayer
    // -------------------------------------------------------

    @Test
    void addPlayer_restrictsBeacon() {
        BeaconAccessData data = new BeaconAccessData();
        BlockPos pos = new BlockPos(10, 64, 10);
        UUID uuid = UUID.randomUUID();

        data.addPlayer(pos, uuid, "Alice");

        assertTrue(data.isRestricted(pos));
        assertTrue(data.isAllowed(pos, uuid));
        assertFalse(data.isAllowed(pos, UUID.randomUUID())); // unlisted player blocked
    }

    @Test
    void addPlayer_cachesPlayerName() {
        BeaconAccessData data = new BeaconAccessData();
        UUID uuid = UUID.randomUUID();
        data.addPlayer(new BlockPos(0, 64, 0), uuid, "Bob");
        assertEquals("Bob", data.getPlayerName(uuid));
    }

    // -------------------------------------------------------
    // removePlayer
    // -------------------------------------------------------

    @Test
    void removePlayer_unrestrictsWhenSetBecomesEmpty() {
        BeaconAccessData data = new BeaconAccessData();
        BlockPos pos = new BlockPos(0, 64, 0);
        UUID uuid = UUID.randomUUID();

        data.addPlayer(pos, uuid, "Alice");
        data.removePlayer(pos, uuid);

        assertFalse(data.isRestricted(pos));
        assertTrue(data.isAllowed(pos, UUID.randomUUID())); // back to unrestricted
    }

    @Test
    void removePlayer_keepRestrictedWhileOthersRemain() {
        BeaconAccessData data = new BeaconAccessData();
        BlockPos pos = new BlockPos(0, 64, 0);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();

        data.addPlayer(pos, a, "Alice");
        data.addPlayer(pos, b, "Bob");
        data.removePlayer(pos, a);

        assertTrue(data.isRestricted(pos));
        assertFalse(data.isAllowed(pos, a));
        assertTrue(data.isAllowed(pos, b));
    }

    // -------------------------------------------------------
    // removeBeacon
    // -------------------------------------------------------

    @Test
    void removeBeacon_clearsAllowedListAndOwner() {
        BeaconAccessData data = new BeaconAccessData();
        BlockPos pos = new BlockPos(5, 64, 5);
        UUID uuid = UUID.randomUUID();

        data.addPlayer(pos, uuid, "Bob");
        data.setOwner(pos, uuid);
        data.removeBeacon(pos);

        assertFalse(data.isRestricted(pos));
        assertNull(data.getOwner(pos));
        assertFalse(data.hasOwner(pos));
    }

    // -------------------------------------------------------
    // Owner management
    // -------------------------------------------------------

    @Test
    void setOwner_getOwner_hasOwner() {
        BeaconAccessData data = new BeaconAccessData();
        BlockPos pos = new BlockPos(0, 64, 0);
        UUID owner = UUID.randomUUID();

        assertFalse(data.hasOwner(pos));
        assertNull(data.getOwner(pos));

        data.setOwner(pos, owner);

        assertTrue(data.hasOwner(pos));
        assertEquals(owner, data.getOwner(pos));
    }

    // -------------------------------------------------------
    // Player name cache
    // -------------------------------------------------------

    @Test
    void getPlayerName_unknownUuid_returnsShortenedFallback() {
        BeaconAccessData data = new BeaconAccessData();
        String result = data.getPlayerName(UUID.randomUUID());
        assertTrue(result.endsWith("..."));
    }

    @Test
    void cachePlayerName_overwritesPrevious() {
        BeaconAccessData data = new BeaconAccessData();
        UUID uuid = UUID.randomUUID();
        data.cachePlayerName(uuid, "OldName");
        data.cachePlayerName(uuid, "NewName");
        assertEquals("NewName", data.getPlayerName(uuid));
    }

    // -------------------------------------------------------
    // getAllowedPlayers
    // -------------------------------------------------------

    @Test
    void getAllowedPlayers_noBeacon_returnsEmptySet() {
        BeaconAccessData data = new BeaconAccessData();
        assertTrue(data.getAllowedPlayers(new BlockPos(0, 64, 0)).isEmpty());
    }

    @Test
    void getAllowedPlayers_returnsUnmodifiableSet() {
        BeaconAccessData data = new BeaconAccessData();
        BlockPos pos = new BlockPos(0, 64, 0);
        data.addPlayer(pos, UUID.randomUUID(), "X");

        Set<UUID> set = data.getAllowedPlayers(pos);
        assertThrows(UnsupportedOperationException.class, () -> set.add(UUID.randomUUID()));
    }

    // -------------------------------------------------------
    // NBT round-trip
    // -------------------------------------------------------

    @Test
    void nbtRoundTrip_empty() {
        BeaconAccessData original = new BeaconAccessData();
        BeaconAccessData restored = roundTrip(original);

        assertFalse(restored.isRestricted(new BlockPos(0, 64, 0)));
        assertTrue(restored.getDump().isEmpty());
    }

    @Test
    void nbtRoundTrip_singleBeacon() {
        BeaconAccessData original = new BeaconAccessData();
        BlockPos pos = new BlockPos(100, 64, -50);
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();

        original.addPlayer(pos, p1, "Alice");
        original.addPlayer(pos, p2, "Bob");
        original.setOwner(pos, p1);

        BeaconAccessData restored = roundTrip(original);

        assertTrue(restored.isRestricted(pos));
        assertTrue(restored.isAllowed(pos, p1));
        assertTrue(restored.isAllowed(pos, p2));
        assertFalse(restored.isAllowed(pos, UUID.randomUUID()));
        assertEquals(p1, restored.getOwner(pos));
        assertEquals("Alice", restored.getPlayerName(p1));
        assertEquals("Bob",   restored.getPlayerName(p2));
    }

    @Test
    void nbtRoundTrip_multipleBeacons() {
        BeaconAccessData original = new BeaconAccessData();
        BlockPos pos1 = new BlockPos(0, 64, 0);
        BlockPos pos2 = new BlockPos(100, 80, 100);
        UUID u1 = UUID.randomUUID(), u2 = UUID.randomUUID();

        original.addPlayer(pos1, u1, "A");
        original.addPlayer(pos2, u2, "B");

        BeaconAccessData restored = roundTrip(original);

        assertTrue(restored.isAllowed(pos1, u1));
        assertFalse(restored.isAllowed(pos1, u2)); // not in pos1's list
        assertTrue(restored.isAllowed(pos2, u2));
        assertFalse(restored.isAllowed(pos2, u1)); // not in pos2's list
    }

    @Test
    void nbtRoundTrip_ownerPreserved() {
        BeaconAccessData original = new BeaconAccessData();
        BlockPos pos = new BlockPos(0, 64, 0);
        UUID owner = UUID.randomUUID();

        original.addPlayer(pos, owner, "Owner");
        original.setOwner(pos, owner);

        BeaconAccessData restored = roundTrip(original);

        assertEquals(owner, restored.getOwner(pos));
        assertTrue(restored.hasOwner(pos));
    }

    @Test
    void nbtRoundTrip_nameCache_preservedForOfflinePlayers() {
        BeaconAccessData original = new BeaconAccessData();
        UUID uuid = UUID.randomUUID();
        original.cachePlayerName(uuid, "OfflinePlayer");

        BeaconAccessData restored = roundTrip(original);

        assertEquals("OfflinePlayer", restored.getPlayerName(uuid));
    }

    @Test
    void nbtRoundTrip_negativeAndZeroCoordinates() {
        BeaconAccessData original = new BeaconAccessData();
        BlockPos pos = new BlockPos(-500, 0, -999);
        UUID uuid = UUID.randomUUID();
        original.addPlayer(pos, uuid, "Edge");

        BeaconAccessData restored = roundTrip(original);

        assertTrue(restored.isRestricted(pos));
        assertTrue(restored.isAllowed(pos, uuid));
    }

    // -------------------------------------------------------
    // fromNbt — malformed UUID handling
    // -------------------------------------------------------

    @Test
    void fromNbt_malformedPlayerUuid_skipped_validOnesLoaded() {
        UUID valid = UUID.randomUUID();
        NbtCompound nbt = buildBeaconNbt(0, 64, 0, null,
            "not-a-valid-uuid", valid.toString());

        BeaconAccessData data = new BeaconAccessData();
        data.fromNbt(nbt);

        BlockPos pos = new BlockPos(0, 64, 0);
        assertTrue(data.isRestricted(pos));
        assertTrue(data.isAllowed(pos, valid));
    }

    @Test
    void fromNbt_allPlayersmalformed_beaconNotAdded() {
        NbtCompound nbt = buildBeaconNbt(5, 64, 5, null, "bad-1", "bad-2");

        BeaconAccessData data = new BeaconAccessData();
        data.fromNbt(nbt);

        assertFalse(data.isRestricted(new BlockPos(5, 64, 5)));
    }

    @Test
    void fromNbt_malformedOwnerUuid_ownerNotSet() {
        UUID player = UUID.randomUUID();
        NbtCompound nbt = buildBeaconNbt(0, 64, 0, "not-a-uuid", player.toString());

        BeaconAccessData data = new BeaconAccessData();
        data.fromNbt(nbt);

        BlockPos pos = new BlockPos(0, 64, 0);
        assertTrue(data.isRestricted(pos));   // player still loaded
        assertFalse(data.hasOwner(pos));      // owner skipped
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private static BeaconAccessData roundTrip(BeaconAccessData original) {
        NbtCompound nbt = original.toNbt();
        BeaconAccessData restored = new BeaconAccessData();
        restored.fromNbt(nbt);
        return restored;
    }

    /**
     * Builds a minimal NBT compound with one beacon entry.
     *
     * @param ownerUuidStr  optional owner UUID string (may be malformed or null)
     * @param playerUuids   UUID strings for the player list (may include malformed entries)
     */
    private static NbtCompound buildBeaconNbt(int x, int y, int z,
                                              String ownerUuidStr,
                                              String... playerUuids) {
        NbtCompound root = new NbtCompound();

        NbtList beaconList = new NbtList();
        NbtCompound entry = new NbtCompound();
        entry.putInt("x", x);
        entry.putInt("y", y);
        entry.putInt("z", z);

        NbtList players = new NbtList();
        for (String uuid : playerUuids) {
            players.add(NbtString.of(uuid));
        }
        entry.put("players", players);

        if (ownerUuidStr != null) {
            entry.putString("owner", ownerUuidStr);
        }

        beaconList.add(entry);
        root.put("beacons", beaconList);
        root.put("playerNames", new NbtList());
        return root;
    }
}
