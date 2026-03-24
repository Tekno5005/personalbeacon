package net.tekno.personalbeacon;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Set;
import java.util.UUID;

public class DebugCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("personalbeacon")
                    .requires(source -> source.hasPermissionLevel(2))

                    .then(CommandManager.literal("help")
                        .executes(DebugCommand::executeHelp))

                    .then(CommandManager.literal("debug")
                        .executes(DebugCommand::executeDebug))

                    .then(CommandManager.literal("clear")
                        .then(CommandManager.argument("x", IntegerArgumentType.integer())
                            .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                    .executes(DebugCommand::executeClear)))))

                    .then(CommandManager.literal("test")
                        .executes(DebugCommand::executeTest))

                    // /personalbeacon add <player> <x> <y> <z>
                    .then(CommandManager.literal("add")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("x", IntegerArgumentType.integer())
                                .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                        .executes(DebugCommand::executeAdd))))))

                    // /personalbeacon remove <player> <x> <y> <z>
                    .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("x", IntegerArgumentType.integer())
                                .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                        .executes(DebugCommand::executeRemove))))))

                    // /personalbeacon setowner <player> <x> <y> <z>
                    .then(CommandManager.literal("setowner")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("x", IntegerArgumentType.integer())
                                .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                        .executes(DebugCommand::executeSetOwner))))))
            );
        });
    }

    private static int executeHelp(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        src.sendFeedback(() -> Text.literal("§6§l=== Personal Beacon Commands ==="), false);
        src.sendFeedback(() -> Text.literal("§e/personalbeacon help §7- Shows this message."), false);
        src.sendFeedback(() -> Text.literal("§e/personalbeacon debug §7- Lists all restricted beacons with UUIDs."), false);
        src.sendFeedback(() -> Text.literal("§e/personalbeacon add <player> <x> <y> <z> §7- Add player to beacon."), false);
        src.sendFeedback(() -> Text.literal("§e/personalbeacon remove <player> <x> <y> <z> §7- Remove player from beacon."), false);
        src.sendFeedback(() -> Text.literal("§e/personalbeacon setowner <player> <x> <y> <z> §7- Set beacon owner."), false);
        src.sendFeedback(() -> Text.literal("§e/personalbeacon clear <x> <y> <z> §7- Remove all access data for beacon."), false);
        src.sendFeedback(() -> Text.literal("§e/personalbeacon test §7- Toggle singleplayer restriction bypass."), false);
        src.sendFeedback(() -> Text.literal("§6§l================================="), false);
        return 1;
    }

    private static int executeDebug(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        BeaconAccessManager manager = BeaconAccessManager.get(src.getServer());
        var snapshot = manager.getDump();

        if (snapshot.isEmpty()) {
            src.sendFeedback(() -> Text.literal("§eNo restricted beacons found."), false);
            return 1;
        }

        src.sendFeedback(() -> Text.literal("§6=== Personal Beacon Debug ==="), false);
        src.sendFeedback(() -> Text.literal("§7Restricted beacons: §f" + snapshot.size()), false);

        for (var entry : snapshot.entrySet()) {
            BlockPos pos = entry.getKey();
            Set<UUID> allowed = entry.getValue();
            UUID owner = manager.getOwner(pos);
            String ownerName = owner != null ? manager.getPlayerName(owner) : "none";

            src.sendFeedback(() -> Text.literal(
                "§b[Beacon] §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + " §7(" + allowed.size() + " players) §eOwner: §f" + ownerName
            ), false);

            for (UUID uuid : allowed) {
                ServerPlayerEntity online = src.getServer().getPlayerManager().getPlayer(uuid);
                String name = online != null
                    ? online.getName().getString()
                    : "§8" + manager.getPlayerName(uuid) + " (offline)";
                src.sendFeedback(() -> Text.literal("  §a✓ §f" + name + " §8(" + uuid + ")"), false);
            }
        }
        src.sendFeedback(() -> Text.literal("§6=============================="), false);
        return 1;
    }

    private static int executeAdd(CommandContext<ServerCommandSource> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        BlockPos pos = getPos(ctx);
        ServerCommandSource src = ctx.getSource();
        BeaconAccessManager manager = BeaconAccessManager.get(src.getServer());

        // Try to find online player first
        ServerPlayerEntity online = src.getServer().getPlayerManager().getPlayer(playerName);
        if (online != null) {
            manager.addPlayer(pos, online.getUuid(), online.getName().getString());
            src.sendFeedback(() -> Text.literal("§aAdded §f" + playerName + " §ato beacon at " + posStr(pos)), false);
        } else {
            // Player is offline — look up UUID from name cache
            UUID uuid = findUUIDByName(manager, playerName);
            if (uuid != null) {
                manager.addPlayer(pos, uuid, playerName);
                src.sendFeedback(() -> Text.literal("§aAdded offline player §f" + playerName + " §ato beacon at " + posStr(pos)), false);
            } else {
                src.sendFeedback(() -> Text.literal("§cPlayer §f" + playerName + " §cnot found. They must have joined this server at least once."), false);
                return 0;
            }
        }
        return 1;
    }

    private static int executeRemove(CommandContext<ServerCommandSource> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        BlockPos pos = getPos(ctx);
        ServerCommandSource src = ctx.getSource();
        BeaconAccessManager manager = BeaconAccessManager.get(src.getServer());

        ServerPlayerEntity online = src.getServer().getPlayerManager().getPlayer(playerName);
        UUID uuid = online != null ? online.getUuid() : findUUIDByName(manager, playerName);

        if (uuid == null) {
            src.sendFeedback(() -> Text.literal("§cPlayer §f" + playerName + " §cnot found."), false);
            return 0;
        }

        manager.removePlayer(pos, uuid);
        src.sendFeedback(() -> Text.literal("§aRemoved §f" + playerName + " §afrom beacon at " + posStr(pos)), false);
        return 1;
    }

    private static int executeSetOwner(CommandContext<ServerCommandSource> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        BlockPos pos = getPos(ctx);
        ServerCommandSource src = ctx.getSource();
        BeaconAccessManager manager = BeaconAccessManager.get(src.getServer());

        ServerPlayerEntity online = src.getServer().getPlayerManager().getPlayer(playerName);
        UUID uuid = online != null ? online.getUuid() : findUUIDByName(manager, playerName);

        if (uuid == null) {
            src.sendFeedback(() -> Text.literal("§cPlayer §f" + playerName + " §cnot found."), false);
            return 0;
        }

        manager.setOwner(pos, uuid);
        src.sendFeedback(() -> Text.literal("§aSet owner of beacon at " + posStr(pos) + " §ato §f" + playerName), false);
        return 1;
    }

    private static int executeClear(CommandContext<ServerCommandSource> ctx) {
        BlockPos pos = getPos(ctx);
        BeaconAccessManager manager = BeaconAccessManager.get(ctx.getSource().getServer());
        manager.removeBeacon(pos);
        ctx.getSource().sendFeedback(() -> Text.literal("§aCleared access data for beacon at " + posStr(pos)), false);
        return 1;
    }

    private static int executeTest(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ModNetworking.sendTestModeToggle(src.getServer());
        boolean on = ModNetworking.isTestModeEnabled();
        if (on) {
            src.sendFeedback(() -> Text.literal("§a[Test Mode ON] §fSingleplayer restriction removed."), false);
            src.sendFeedback(() -> Text.literal("§7Tip: Close and reopen the beacon to see the change."), false);
        } else {
            src.sendFeedback(() -> Text.literal("§c[Test Mode OFF] §fSingleplayer restriction restored."), false);
        }
        return 1;
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private static BlockPos getPos(CommandContext<ServerCommandSource> ctx) {
        int x = IntegerArgumentType.getInteger(ctx, "x");
        int y = IntegerArgumentType.getInteger(ctx, "y");
        int z = IntegerArgumentType.getInteger(ctx, "z");
        return new BlockPos(x, y, z);
    }

    private static String posStr(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    /** Search the name cache for a UUID matching the given name. */
    private static UUID findUUIDByName(BeaconAccessManager manager, String name) {
        for (var entry : manager.getPlayerNames().entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
