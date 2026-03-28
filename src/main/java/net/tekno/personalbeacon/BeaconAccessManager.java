package net.tekno.personalbeacon;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    // -------------------------------------------------------
    // Allowed players
    // -------------------------------------------------------

    /**
     * Add a player to a beacon's allowed list.
     * Automatically sets owner if this is the first restriction on this beacon.
     */
    public void addPlayer(BlockPos pos, UUID playerUUID, String playerName) {
        // If no owner yet, the first person to add a restriction becomes the owner
        if (!data.hasOwner(pos)) {
            data.setOwner(pos, playerUUID);
            LOGGER.info("{} is now owner of beacon at {}", playerName, pos);
        }
        data.addPlayer(pos, playerUUID, playerName);
        LOGGER.debug("Added {} ({}) to beacon at {}", playerName, playerUUID, pos);
        markDirty();
    }

    public void removePlayer(BlockPos pos, UUID playerUUID) {
        data.removePlayer(pos, playerUUID);
        LOGGER.debug("Removed {} from beacon at {}", playerUUID, pos);
        markDirty();
    }

    public boolean isAllowed(BlockPos pos, UUID playerUUID) {
        return data.isAllowed(pos, playerUUID);
    }

    public boolean isRestricted(BlockPos pos) {
        return data.isRestricted(pos);
    }

    public Set<UUID> getAllowedPlayers(BlockPos pos) {
        return data.getAllowedPlayers(pos);
    }

    public void removeBeacon(BlockPos pos) {
        data.removeBeacon(pos);
        LOGGER.debug("Removed all access data for beacon at {}", pos);
        markDirty();
    }

    // -------------------------------------------------------
    // Owner
    // -------------------------------------------------------

    public UUID getOwner(BlockPos pos) {
        return data.getOwner(pos);
    }

    public boolean isOwner(BlockPos pos, UUID uuid) {
        UUID owner = data.getOwner(pos);
        return owner != null && owner.equals(uuid);
    }

    public void setOwner(BlockPos pos, UUID uuid) {
        data.setOwner(pos, uuid);
        LOGGER.debug("Set owner of beacon at {} to {}", pos, uuid);
        markDirty();
    }

    // -------------------------------------------------------
    // Player name cache
    // -------------------------------------------------------

    public void cachePlayerName(UUID uuid, String name) {
        data.cachePlayerName(uuid, name);
        markDirty();
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

    public Map<BlockPos, Set<UUID>> getDump() {
        return data.getDump();
    }
}
