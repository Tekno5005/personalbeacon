package net.tekno.personalbeacon;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class BeaconAccessData {

    private static final Logger LOGGER = LoggerFactory.getLogger("personalbeacon");

    // beacon pos -> set of allowed UUIDs
    private final Map<BlockPos, Set<UUID>> allowedPlayers = new HashMap<>();

    // beacon pos -> owner UUID (first player to restrict it, or manually set)
    private final Map<BlockPos, UUID> beaconOwners = new HashMap<>();

    // UUID -> last known player name (for offline display)
    private final Map<UUID, String> playerNames = new HashMap<>();

    // -------------------------------------------------------
    // Allowed players
    // -------------------------------------------------------

    public void addPlayer(BlockPos pos, UUID uuid, String playerName) {
        allowedPlayers.computeIfAbsent(pos, k -> new HashSet<>()).add(uuid);
        playerNames.put(uuid, playerName); // cache name for offline display
    }

    public void removePlayer(BlockPos pos, UUID uuid) {
        Set<UUID> set = allowedPlayers.get(pos);
        if (set != null) {
            set.remove(uuid);
            if (set.isEmpty()) allowedPlayers.remove(pos);
        }
    }

    public boolean isAllowed(BlockPos pos, UUID uuid) {
        Set<UUID> set = allowedPlayers.get(pos);
        if (set == null) return true; // unrestricted
        return set.contains(uuid);
    }

    public boolean isRestricted(BlockPos pos) {
        return allowedPlayers.containsKey(pos);
    }

    public Set<UUID> getAllowedPlayers(BlockPos pos) {
        Set<UUID> set = allowedPlayers.get(pos);
        if (set == null) return Collections.emptySet();
        return Collections.unmodifiableSet(set);
    }

    public void removeBeacon(BlockPos pos) {
        allowedPlayers.remove(pos);
        beaconOwners.remove(pos);
    }

    // -------------------------------------------------------
    // Beacon owner
    // -------------------------------------------------------

    /** Set owner — called when first player restricts a beacon. */
    public void setOwner(BlockPos pos, UUID uuid) {
        beaconOwners.put(pos, uuid);
    }

    /** Get owner UUID, or null if none set. */
    public UUID getOwner(BlockPos pos) {
        return beaconOwners.get(pos);
    }

    public boolean hasOwner(BlockPos pos) {
        return beaconOwners.containsKey(pos);
    }

    // -------------------------------------------------------
    // Player name cache
    // -------------------------------------------------------

    public void cachePlayerName(UUID uuid, String name) {
        playerNames.put(uuid, name);
    }

    /** Returns cached name, or shortened UUID if unknown. */
    public String getPlayerName(UUID uuid) {
        return playerNames.getOrDefault(uuid, uuid.toString().substring(0, 8) + "...");
    }

    public Map<UUID, String> getPlayerNames() {
        return Collections.unmodifiableMap(playerNames);
    }

    // -------------------------------------------------------
    // Debug dump
    // -------------------------------------------------------

    public Map<BlockPos, Set<UUID>> getDump() {
        return Collections.unmodifiableMap(allowedPlayers);
    }

    // -------------------------------------------------------
    // NBT serialization
    // -------------------------------------------------------

    public NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();

        // Allowed players list
        NbtList beaconList = new NbtList();
        for (Map.Entry<BlockPos, Set<UUID>> entry : allowedPlayers.entrySet()) {
            NbtCompound beaconEntry = new NbtCompound();
            BlockPos pos = entry.getKey();
            beaconEntry.putInt("x", pos.getX());
            beaconEntry.putInt("y", pos.getY());
            beaconEntry.putInt("z", pos.getZ());

            NbtList playerList = new NbtList();
            for (UUID uuid : entry.getValue()) {
                playerList.add(NbtString.of(uuid.toString()));
            }
            beaconEntry.put("players", playerList);

            // Owner
            UUID owner = beaconOwners.get(pos);
            if (owner != null) {
                beaconEntry.putString("owner", owner.toString());
            }

            beaconList.add(beaconEntry);
        }
        root.put("beacons", beaconList);

        // Player name cache
        NbtList nameList = new NbtList();
        for (Map.Entry<UUID, String> entry : playerNames.entrySet()) {
            NbtCompound nameEntry = new NbtCompound();
            nameEntry.putString("uuid", entry.getKey().toString());
            nameEntry.putString("name", entry.getValue());
            nameList.add(nameEntry);
        }
        root.put("playerNames", nameList);

        return root;
    }

    public void fromNbt(NbtCompound nbt) {
        allowedPlayers.clear();
        beaconOwners.clear();
        playerNames.clear();

        NbtList beaconList = nbt.getList("beacons", 10);
        for (int i = 0; i < beaconList.size(); i++) {
            NbtCompound entry = beaconList.getCompound(i);
            BlockPos pos = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));

            NbtList playerList = entry.getList("players", 8);
            Set<UUID> uuids = new HashSet<>();
            for (int j = 0; j < playerList.size(); j++) {
                try {
                    uuids.add(UUID.fromString(playerList.getString(j)));
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Skipping malformed UUID: {}", playerList.getString(j));
                }
            }
            if (!uuids.isEmpty()) allowedPlayers.put(pos, uuids);

            if (entry.contains("owner")) {
                try {
                    beaconOwners.put(pos, UUID.fromString(entry.getString("owner")));
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Skipping malformed owner UUID for beacon at {}", pos);
                }
            }
        }

        NbtList nameList = nbt.getList("playerNames", 10);
        for (int i = 0; i < nameList.size(); i++) {
            NbtCompound entry = nameList.getCompound(i);
            try {
                playerNames.put(UUID.fromString(entry.getString("uuid")), entry.getString("name"));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Skipping malformed UUID in name cache");
            }
        }
    }
}
