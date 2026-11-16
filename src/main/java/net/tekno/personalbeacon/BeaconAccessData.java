package net.tekno.personalbeacon;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class BeaconAccessData {

    private static final Logger LOGGER = LoggerFactory.getLogger("personalbeacon");

    // beacon pos -> set of allowed UUIDs
    private final Map<BeaconPos, Set<UUID>> allowedPlayers = new HashMap<>();

    // beacon pos -> set of owner UUIDs (co-managers; always includes the primary owner)
    private final Map<BeaconPos, Set<UUID>> beaconOwners = new HashMap<>();

    // beacon pos -> primary owner UUID (the very first owner; can never be removed)
    private final Map<BeaconPos, UUID> primaryOwners = new HashMap<>();

    // UUID -> last known player name (for offline display)
    private final Map<UUID, String> playerNames = new HashMap<>();

    // -------------------------------------------------------
    // Allowed players
    // -------------------------------------------------------

    public void addPlayer(BeaconPos pos, UUID uuid, String playerName) {
        allowedPlayers.computeIfAbsent(pos, this::newRestriction).add(uuid);
        playerNames.put(uuid, playerName); // cache name for offline display
    }

    /**
     * A new restriction lists the beacon's primary owner. Owner rows can't be toggled in the GUI,
     * so the list never becomes empty there: removing every other player leaves the beacon
     * restricted to its owners instead of silently reopening it to everyone.
     */
    private Set<UUID> newRestriction(BeaconPos pos) {
        Set<UUID> set = new HashSet<>();
        UUID primary = primaryOwners.get(pos);
        if (primary != null) set.add(primary);
        return set;
    }

    public void removePlayer(BeaconPos pos, UUID uuid) {
        Set<UUID> set = allowedPlayers.get(pos);
        if (set != null) {
            set.remove(uuid);
            if (set.isEmpty()) allowedPlayers.remove(pos);
        }
    }

    public boolean isAllowed(BeaconPos pos, UUID uuid) {
        Set<UUID> set = allowedPlayers.get(pos);
        if (set == null) return true; // unrestricted
        return set.contains(uuid) || isOwner(pos, uuid); // owners always receive effects
    }

    public boolean isRestricted(BeaconPos pos) {
        return allowedPlayers.containsKey(pos);
    }

    public Set<UUID> getAllowedPlayers(BeaconPos pos) {
        Set<UUID> set = allowedPlayers.get(pos);
        if (set == null) return Collections.emptySet();
        return Collections.unmodifiableSet(set);
    }

    public void removeBeacon(BeaconPos pos) {
        allowedPlayers.remove(pos);
        beaconOwners.remove(pos);
        primaryOwners.remove(pos);
    }

    /** Remove all access restrictions for a beacon — keeps owner data intact. */
    public void unrestrictBeacon(BeaconPos pos) {
        allowedPlayers.remove(pos);
    }

    // -------------------------------------------------------
    // Entries saved before v2.2.0 (no dimension)
    // -------------------------------------------------------

    /**
     * Moves the legacy entry at {@code pos.pos()} (saved before v2.2.0, without a dimension) to {@code pos}.
     * Call it with the dimension of a beacon that is really at that position (the beacon ticking,
     * its screen being open, ...): the first such dimension takes the entry. Returns true if one moved.
     */
    public boolean claimLegacy(BeaconPos pos) {
        if (pos.isLegacy()) throw new IllegalArgumentException("Cannot claim with the legacy dimension: " + pos);
        BeaconPos legacy = new BeaconPos(BeaconPos.LEGACY_DIMENSION, pos.pos());
        boolean moved = move(allowedPlayers, legacy, pos)
            | move(beaconOwners, legacy, pos)
            | move(primaryOwners, legacy, pos);
        if (moved) LOGGER.info("Beacon data saved without a dimension at {} now belongs to {}", pos.pos(), pos.dimension());
        return moved;
    }

    private static <V> boolean move(Map<BeaconPos, V> map, BeaconPos from, BeaconPos to) {
        V value = map.remove(from);
        if (value == null) return false;
        if (map.containsKey(to)) throw new IllegalStateException("Beacon data exists in both " + from + " and " + to);
        map.put(to, value);
        return true;
    }

    // -------------------------------------------------------
    // Beacon owners (multi-owner / co-manager support)
    // -------------------------------------------------------

    /**
     * Set the primary owner — called when the very first player restricts a beacon.
     * Also adds them to the owner set automatically.
     */
    public void setOwner(BeaconPos pos, UUID uuid) {
        primaryOwners.put(pos, uuid);
        beaconOwners.computeIfAbsent(pos, k -> new HashSet<>()).add(uuid);
    }

    /** Add a co-manager (does NOT change the primary owner). */
    public void addOwner(BeaconPos pos, UUID uuid) {
        beaconOwners.computeIfAbsent(pos, k -> new HashSet<>()).add(uuid);
    }

    /**
     * Remove a co-manager. The primary owner can never be removed.
     * Returns false if the uuid is the primary owner (removal blocked).
     */
    public boolean removeOwner(BeaconPos pos, UUID uuid) {
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
    public boolean isOwner(BeaconPos pos, UUID uuid) {
        Set<UUID> owners = beaconOwners.get(pos);
        return owners != null && owners.contains(uuid);
    }

    /** Returns all current owners (including primary), or empty set. */
    public Set<UUID> getOwners(BeaconPos pos) {
        Set<UUID> owners = beaconOwners.get(pos);
        if (owners == null) return Collections.emptySet();
        return Collections.unmodifiableSet(owners);
    }

    /** Returns the primary (first) owner UUID, or null. */
    public UUID getPrimaryOwner(BeaconPos pos) {
        return primaryOwners.get(pos);
    }

    public boolean hasOwner(BeaconPos pos) {
        return primaryOwners.containsKey(pos);
    }

    /** Beacons the player owns or co-manages, sorted by dimension, then position. */
    public List<BeaconPos> getOwnedBeacons(UUID uuid) {
        return beaconOwners.entrySet().stream()
            .filter(e -> e.getValue().contains(uuid))
            .map(Map.Entry::getKey)
            .sorted(Comparator.comparing((BeaconPos p) -> p.dimension().toString()).thenComparing(BeaconPos::pos))
            .toList();
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

    public Map<BeaconPos, Set<UUID>> getDump() {
        return Collections.unmodifiableMap(allowedPlayers);
    }

    // -------------------------------------------------------
    // NBT serialization
    // -------------------------------------------------------

    /** Entries saved before v2.2.0 have no dimension; they load as {@link BeaconPos#LEGACY_DIMENSION}. */
    private static Identifier readDimension(NbtCompound entry) {
        if (!entry.contains("dimension")) return BeaconPos.LEGACY_DIMENSION;
        Identifier dimension = Identifier.tryParse(entry.getString("dimension"));
        if (dimension == null) {
            throw new IllegalStateException("Malformed beacon dimension: " + entry.getString("dimension"));
        }
        return dimension;
    }

    public NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();

        // Union of all beacon positions — prevents data loss when allowedPlayers is empty
        // but owner data still exists (e.g. all allowed players were removed).
        Set<BeaconPos> allPositions = new HashSet<>();
        allPositions.addAll(allowedPlayers.keySet());
        allPositions.addAll(beaconOwners.keySet());
        allPositions.addAll(primaryOwners.keySet());

        NbtList beaconList = new NbtList();
        for (BeaconPos pos : allPositions) {
            NbtCompound beaconEntry = new NbtCompound();
            beaconEntry.putString("dimension", pos.dimension().toString());
            beaconEntry.putInt("x", pos.pos().getX());
            beaconEntry.putInt("y", pos.pos().getY());
            beaconEntry.putInt("z", pos.pos().getZ());

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
            BeaconPos pos = new BeaconPos(readDimension(entry),
                new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")));

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
