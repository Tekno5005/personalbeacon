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

    // beacon pos -> set of owner UUIDs (co-managers; always includes the primary owner)
    private final Map<BlockPos, Set<UUID>> beaconOwners = new HashMap<>();

    // beacon pos -> primary owner UUID (the very first owner; can never be removed)
    private final Map<BlockPos, UUID> primaryOwners = new HashMap<>();

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
        primaryOwners.remove(pos);
    }

    /** Remove all access restrictions for a beacon — keeps owner data intact. */
    public void unrestrictBeacon(BlockPos pos) {
        allowedPlayers.remove(pos);
    }

    // -------------------------------------------------------
    // Beacon owners (multi-owner / co-manager support)
    // -------------------------------------------------------

    /**
     * Set the primary owner — called when the very first player restricts a beacon.
     * Also adds them to the owner set automatically.
     */
    public void setOwner(BlockPos pos, UUID uuid) {
        primaryOwners.put(pos, uuid);
        beaconOwners.computeIfAbsent(pos, k -> new HashSet<>()).add(uuid);
    }

    /** Add a co-manager (does NOT change the primary owner). */
    public void addOwner(BlockPos pos, UUID uuid) {
        beaconOwners.computeIfAbsent(pos, k -> new HashSet<>()).add(uuid);
    }

    /**
     * Remove a co-manager. The primary owner can never be removed.
     * Returns false if the uuid is the primary owner (removal blocked).
     */
    public boolean removeOwner(BlockPos pos, UUID uuid) {
        UUID primary = primaryOwners.get(pos);
        if (uuid.equals(primary)) return false;
        Set<UUID> owners = beaconOwners.get(pos);
        if (owners != null) {
            owners.remove(uuid);
            if (owners.isEmpty()) beaconOwners.remove(pos);
        }
        return true;
    }

    /** Returns true if uuid is in the owner set for this beacon. */
    public boolean isOwner(BlockPos pos, UUID uuid) {
        Set<UUID> owners = beaconOwners.get(pos);
        return owners != null && owners.contains(uuid);
    }

    /** Returns all current owners (including primary), or empty set. */
    public Set<UUID> getOwners(BlockPos pos) {
        Set<UUID> owners = beaconOwners.get(pos);
        if (owners == null) return Collections.emptySet();
        return Collections.unmodifiableSet(owners);
    }

    /** Returns the primary (first) owner UUID, or null. */
    public UUID getPrimaryOwner(BlockPos pos) {
        return primaryOwners.get(pos);
    }

    /** @deprecated Use {@link #getPrimaryOwner(BlockPos)} instead. */
    @Deprecated
    public UUID getOwner(BlockPos pos) {
        return getPrimaryOwner(pos);
    }

    public boolean hasOwner(BlockPos pos) {
        return primaryOwners.containsKey(pos);
    }

    // -------------------------------------------------------
    // Player name cache
    // -------------------------------------------------------

    /** Caches the name. Returns true if the value was new or changed. */
    public boolean cachePlayerName(UUID uuid, String name) {
        return !name.equals(playerNames.put(uuid, name));
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

        // Union of all beacon positions — prevents data loss when allowedPlayers is empty
        // but owner data still exists (e.g. all allowed players were removed).
        Set<BlockPos> allPositions = new HashSet<>();
        allPositions.addAll(allowedPlayers.keySet());
        allPositions.addAll(beaconOwners.keySet());
        allPositions.addAll(primaryOwners.keySet());

        NbtList beaconList = new NbtList();
        for (BlockPos pos : allPositions) {
            NbtCompound beaconEntry = new NbtCompound();
            beaconEntry.putInt("x", pos.getX());
            beaconEntry.putInt("y", pos.getY());
            beaconEntry.putInt("z", pos.getZ());

            // Allowed players (may be empty)
            Set<UUID> players = allowedPlayers.get(pos);
            NbtList playerList = new NbtList();
            if (players != null) {
                for (UUID uuid : players) {
                    playerList.add(NbtString.of(uuid.toString()));
                }
            }
            beaconEntry.put("players", playerList);

            // Primary owner
            UUID primary = primaryOwners.get(pos);
            if (primary != null) {
                beaconEntry.putString("owner", primary.toString());
            }

            // Co-owners (all owners including primary)
            Set<UUID> owners = beaconOwners.get(pos);
            if (owners != null && !owners.isEmpty()) {
                NbtList ownerList = new NbtList();
                for (UUID ownerUuid : owners) {
                    ownerList.add(NbtString.of(ownerUuid.toString()));
                }
                beaconEntry.put("coOwners", ownerList);
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
        primaryOwners.clear();
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

            // Primary owner (backward-compatible "owner" field)
            if (entry.contains("owner")) {
                try {
                    UUID primary = UUID.fromString(entry.getString("owner"));
                    primaryOwners.put(pos, primary);
                    beaconOwners.computeIfAbsent(pos, k -> new HashSet<>()).add(primary);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Skipping malformed owner UUID for beacon at {}", pos);
                }
            }

            // Co-owners list (may include primary; we just add all to owner set)
            if (entry.contains("coOwners")) {
                NbtList ownerList = entry.getList("coOwners", 8);
                for (int j = 0; j < ownerList.size(); j++) {
                    try {
                        UUID ownerUuid = UUID.fromString(ownerList.getString(j));
                        beaconOwners.computeIfAbsent(pos, k -> new HashSet<>()).add(ownerUuid);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Skipping malformed coOwner UUID for beacon at {}", pos);
                    }
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
