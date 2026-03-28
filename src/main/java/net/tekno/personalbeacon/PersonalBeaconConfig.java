package net.tekno.personalbeacon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads and writes config/personalbeacon.json.
 * Call PersonalBeaconConfig.load() once on startup.
 * Access settings via PersonalBeaconConfig.get().
 */
public class PersonalBeaconConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("personalbeacon");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("personalbeacon.json");

    private static PersonalBeaconConfig instance = new PersonalBeaconConfig();

    // -------------------------------------------------------
    // Config fields — these map directly to JSON keys
    // -------------------------------------------------------

    /** Max players allowed per beacon. 0 = unlimited. */
    public int maxPlayersPerBeacon = 0;

    /** Whether non-op players can manage their own beacons. */
    public boolean allowNonOpManagement = true;

    /** Distance check on multiplayer servers (blocks). 0 = disabled. */
    public double maxManageDistance = 8.0;

    /** Whether to skip distance check on singleplayer/integrated server. */
    public boolean skipDistanceCheckInSingleplayer = true;

    /** Whether test mode starts enabled (useful for singleplayer servers). */
    public boolean testModeDefault = false;

    // -------------------------------------------------------
    // Load / save
    // -------------------------------------------------------

    public static PersonalBeaconConfig get() {
        return instance;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                instance = GSON.fromJson(reader, PersonalBeaconConfig.class);
                LOGGER.info("Config loaded from {}", CONFIG_PATH);
            } catch (IOException e) {
                LOGGER.error("Failed to read config, using defaults.", e);
                instance = new PersonalBeaconConfig();
            }
        } else {
            // First run — write defaults
            instance = new PersonalBeaconConfig();
            save();
            LOGGER.info("Config created at {}", CONFIG_PATH);
        }
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save config.", e);
        }
    }
}
