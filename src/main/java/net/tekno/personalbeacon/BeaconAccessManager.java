package net.tekno.personalbeacon;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BeaconAccessManager extends PersistentState {

    private static final String SAVE_KEY = "personalbeacon_access";
    private static final Logger LOGGER = LoggerFactory.getLogger("personalbeacon");
    private final BeaconAccessData data = new BeaconAccessData();

    public static BeaconAccessManager fromNbt(NbtCompound nbt) {
        BeaconAccessManager manager = new BeaconAccessManager();
        manager.data.fromNbt(nbt);
        return manager;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound dataNbt = data.toNbt();
        for (String key : dataNbt.getKeys()) {
            nbt.put(key, dataNbt.get(key));
        }
        return nbt;
    }

    public static BeaconAccessManager get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        PersistentStateManager stateManager = overworld.getPersistentStateManager();
        return stateManager.getOrCreate(
            BeaconAccessManager::fromNbt,
            BeaconAccessManager::new,
            SAVE_KEY
        );
    }

    /**
     * The key of the beacon at {@code pos} in {@code world}. Hands it the entry saved there
     * before v2.2.0 (without a dimension), if there is one: see {@link BeaconAccessData#claimLegacy}.
     */
    private BeaconPos at(World world, BlockPos pos) {
        BeaconPos key = BeaconPos.of(world, pos);
        if (data.claimLegacy(key)) markDirty();
        return key;
    }

    // -------------------------------------------------------
    // Allowed players
    // -------------------------------------------------------

    /** Add a player to a beacon's allowed list. Ownership is set separately via {@link #setOwner}. */
    public void addPlayer(World world, BlockPos pos, UUID playerUUID, String playerName) {
        data.addPlayer(at(world, pos), playerUUID, playerName);
        LOGGER.debug("Added {} ({}) to beacon at {}", playerName, playerUUID, pos);
        markDirty();
    }

    public void removePlayer(World world, BlockPos pos, UUID playerUUID) {
        data.removePlayer(at(world, pos), playerUUID);
        LOGGER.debug("Removed {} from beacon at {}", playerUUID, pos);
        markDirty();
    }

    public boolean isAllowed(World world, BlockPos pos, UUID playerUUID) {
        return data.isAllowed(at(world, pos), playerUUID);
    }

    public boolean isRestricted(World world, BlockPos pos) {
        return data.isRestricted(at(world, pos));
    }

    public Set<UUID> getAllowedPlayers(World world, BlockPos pos) {
        return data.getAllowedPlayers(at(world, pos));
    }

    public void removeBeacon(World world, BlockPos pos) {
        data.removeBeacon(at(world, pos));
        LOGGER.debug("Removed all access data for beacon at {}", pos);
        markDirty();
    }

    /** Remove all player restrictions — beacon becomes open to everyone. Owner data is preserved. */
    public void unrestrictBeacon(World world, BlockPos pos) {
        data.unrestrictBeacon(at(world, pos));
        LOGGER.info("Beacon at {} unrestricted — all players now receive effects", pos);
        markDirty();
    }

    // -------------------------------------------------------
    // Owner
    // -------------------------------------------------------

    public boolean hasOwner(World world, BlockPos pos) {
        return data.hasOwner(at(world, pos));
    }

    public UUID getPrimaryOwner(World world, BlockPos pos) {
        return data.getPrimaryOwner(at(world, pos));
    }

    public boolean isOwner(World world, BlockPos pos, UUID uuid) {
        return data.isOwner(at(world, pos), uuid);
    }

    public Set<UUID> getOwners(World world, BlockPos pos) {
        return data.getOwners(at(world, pos));
    }

    public void setOwner(World world, BlockPos pos, UUID uuid) {
        data.setOwner(at(world, pos), uuid);
        LOGGER.debug("Set primary owner of beacon at {} to {}", pos, uuid);
        markDirty();
    }

    /**
     * Add a co-manager to the beacon. Does not change primary owner.
     * Returns false if the beacon has no primary owner yet (can't add co-owner without primary).
     */
    public boolean addOwner(World world, BlockPos pos, UUID uuid) {
        BeaconPos key = at(world, pos);
        if (!data.hasOwner(key)) return false;
        data.addOwner(key, uuid);
        LOGGER.debug("Added co-owner {} to beacon at {}", uuid, pos);
        markDirty();
        return true;
    }

    /**
     * Remove a co-manager. Primary owner can never be removed (returns false).
     */
    public boolean removeOwner(World world, BlockPos pos, UUID uuid) {
        boolean removed = data.removeOwner(at(world, pos), uuid);
        if (removed) {
            LOGGER.debug("Removed co-owner {} from beacon at {}", uuid, pos);
            markDirty();
        } else {
            LOGGER.debug("Cannot remove primary owner {} from beacon at {}", uuid, pos);
        }
        return removed;
    }

    // -------------------------------------------------------
    // Listings: keys come from the stored data, so there is nothing to claim
    // -------------------------------------------------------

    public List<BeaconPos> getOwnedBeacons(UUID uuid) {
        return data.getOwnedBeacons(uuid);
    }

    public UUID getPrimaryOwner(BeaconPos key) {
        return data.getPrimaryOwner(key);
    }

    public Set<UUID> getAllowedPlayers(BeaconPos key) {
        return data.getAllowedPlayers(key);
    }

    // -------------------------------------------------------
    // Player name cache
    // -------------------------------------------------------

    public void cachePlayerName(UUID uuid, String name) {
        if (data.cachePlayerName(uuid, name)) markDirty();
    }

    public String getPlayerName(UUID uuid) {
        return data.getPlayerName(uuid);
    }

    public Map<UUID, String> getPlayerNames() {
        return data.getPlayerNames();
    }

    // -------------------------------------------------------
    // Debug
    // -------------------------------------------------------

    public Map<BeaconPos, Set<UUID>> getDump() {
        return data.getDump();
    }
}
