package net.tekno.personalbeacon;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DebugCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("personalbeacon")

                    // /personalbeacon mine — for every player
                    .then(CommandManager.literal("mine")
                        .executes(DebugCommand::executeMine))

                    .then(CommandManager.literal("help").requires(DebugCommand::isOp)
                        .executes(DebugCommand::executeHelp))

                    .then(CommandManager.literal("debug").requires(DebugCommand::isOp)
                        .executes(DebugCommand::executeDebug))

                    .then(CommandManager.literal("clear").requires(DebugCommand::isOp)
                        .then(CommandManager.argument("x", IntegerArgumentType.integer())
                            .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                    .executes(DebugCommand::executeClear)))))

                    .then(CommandManager.literal("test").requires(DebugCommand::isOp)
                        .executes(DebugCommand::executeTest))

                    // /personalbeacon add <player> <x> <y> <z>
                    .then(CommandManager.literal("add").requires(DebugCommand::isOp)
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("x", IntegerArgumentType.integer())
                                .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                        .executes(DebugCommand::executeAdd))))))

                    // /personalbeacon remove <player> <x> <y> <z>
                    .then(CommandManager.literal("remove").requires(DebugCommand::isOp)
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("x", IntegerArgumentType.integer())
                                .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                        .executes(DebugCommand::executeRemove))))))

                    // /personalbeacon setowner <player> <x> <y> <z>
                    .then(CommandManager.literal("setowner").requires(DebugCommand::isOp)
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("x", IntegerArgumentType.integer())
                                .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                        .executes(DebugCommand::executeSetOwner))))))
            );
        });
    }

    private static boolean isOp(ServerCommandSource source) {
        return source.hasPermissionLevel(2);
    }

    /** Lists the beacons the player owns or co-manages. Texts are translated on clients with the mod. */
    private static int executeMine(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player = src.getPlayerOrThrow();
        BeaconAccessManager manager = BeaconAccessManager.get(src.getServer());
        List<BeaconPos> beacons = manager.getOwnedBeacons(player.getUuid());

        if (beacons.isEmpty()) {
            src.sendFeedback(() -> Text.translatableWithFallback("personalbeacon.command.mine.none",
                "You don't own any beacons."), false);
            return 0;
        }

        src.sendFeedback(() -> Text.translatableWithFallback("personalbeacon.command.mine.header",
            "Your beacons (%s):", beacons.size()).formatted(Formatting.GOLD), false);
        for (BeaconPos key : beacons) {
            Text line = Text.literal(" ▪ ").formatted(Formatting.DARK_GRAY)
                .append(coordinates(key.pos()))
                .append(Text.literal("  ").append(dimensionName(key)).formatted(Formatting.GRAY))
                .append(Text.literal("  ·  ").formatted(Formatting.DARK_GRAY))
                .append(access(manager.getAllowedPlayers(key).size()))
                .append(Text.literal("  ·  ").formatted(Formatting.DARK_GRAY))
                .append(player.getUuid().equals(manager.getPrimaryOwner(key))
                    ? Text.translatableWithFallback("personalbeacon.command.mine.owner", "owner").formatted(Formatting.YELLOW)
                    : Text.translatableWithFallback("personalbeacon.command.mine.co_manager", "co-manager").formatted(Formatting.YELLOW));
            src.sendFeedback(() -> line, false);
        }
        return beacons.size();
    }

    /** "x y z" in white; clicking copies it to the clipboard. */
    private static Text coordinates(BlockPos pos) {
        String xyz = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        return Text.literal(xyz).styled(style -> style
            .withColor(Formatting.WHITE)
            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, xyz))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                Text.translatableWithFallback("chat.copy.click", "Click to Copy to Clipboard"))));
    }

    private static Text dimensionName(BeaconPos key) {
        if (key.isLegacy()) {
            return Text.translatableWithFallback("personalbeacon.dimension.unknown", "unknown dimension");
        }
        Identifier id = key.dimension();
        if (id.getNamespace().equals("minecraft")) {
            String fallback = switch (id.getPath()) {
                case "overworld" -> "Overworld";
                case "the_nether" -> "Nether";
                case "the_end" -> "End";
                default -> id.toString();
            };
            return Text.translatableWithFallback("personalbeacon.dimension." + id.getPath(), fallback);
        }
        return Text.literal(id.toString());
    }

    private static Text access(int allowedCount) {
        return allowedCount == 0
            ? Text.translatableWithFallback("personalbeacon.command.mine.open", "open to all").formatted(Formatting.GREEN)
            : Text.translatableWithFallback("personalbeacon.command.mine.restricted", "%s allowed", allowedCount)
                .formatted(Formatting.AQUA);
    }

    private static int executeHelp(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        src.sendFeedback(() -> Text.literal("§6§l=== Personal Beacon Commands ==="), false);
        src.sendFeedback(() -> Text.literal("§e/personalbeacon help §7- Shows this message."), false);
        src.sendFeedback(() -> Text.literal("§e/personalbeacon mine §7- Lists the beacons you own (every player can use it)."), false);
        src.sendFeedback(() -> Text.literal("§7Coordinates are in the dimension you run the command in."), false);
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
            BeaconPos key = entry.getKey();
            BlockPos pos = key.pos();
            Set<UUID> allowed = entry.getValue();
            UUID owner = manager.getPrimaryOwner(key);
            String ownerName = owner != null ? manager.getPlayerName(owner) : "none";

            src.sendFeedback(() -> Text.literal(
                "§b[Beacon] §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + " §7" + key.dimension() + " (" + allowed.size() + " players) §eOwner: §f" + ownerName
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
        ServerWorld world = src.getWorld();
        BeaconAccessManager manager = BeaconAccessManager.get(src.getServer());

        // Try to find online player first
        ServerPlayerEntity online = src.getServer().getPlayerManager().getPlayer(playerName);
        if (online != null) {
            if (!manager.hasOwner(world, pos)) manager.setOwner(world, pos, online.getUuid());
            manager.addPlayer(world, pos, online.getUuid(), online.getName().getString());
            src.sendFeedback(() -> Text.literal("§aAdded §f" + playerName + " §ato beacon at " + posStr(pos)), false);
        } else {
            // Player is offline — look up UUID from name cache
            UUID uuid = findUUIDByName(manager, playerName);
            if (uuid != null) {
                if (!manager.hasOwner(world, pos)) manager.setOwner(world, pos, uuid);
                manager.addPlayer(world, pos, uuid, playerName);
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
        ServerWorld world = src.getWorld();
        BeaconAccessManager manager = BeaconAccessManager.get(src.getServer());

        ServerPlayerEntity online = src.getServer().getPlayerManager().getPlayer(playerName);
        UUID uuid = online != null ? online.getUuid() : findUUIDByName(manager, playerName);

        if (uuid == null) {
            src.sendFeedback(() -> Text.literal("§cPlayer §f" + playerName + " §cnot found."), false);
            return 0;
        }

        manager.removePlayer(world, pos, uuid);
        src.sendFeedback(() -> Text.literal("§aRemoved §f" + playerName + " §afrom beacon at " + posStr(pos)), false);
        return 1;
    }

    private static int executeSetOwner(CommandContext<ServerCommandSource> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        BlockPos pos = getPos(ctx);
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getWorld();
        BeaconAccessManager manager = BeaconAccessManager.get(src.getServer());

        ServerPlayerEntity online = src.getServer().getPlayerManager().getPlayer(playerName);
        UUID uuid = online != null ? online.getUuid() : findUUIDByName(manager, playerName);

        if (uuid == null) {
            src.sendFeedback(() -> Text.literal("§cPlayer §f" + playerName + " §cnot found."), false);
            return 0;
        }

        manager.setOwner(world, pos, uuid);
        src.sendFeedback(() -> Text.literal("§aSet owner of beacon at " + posStr(pos) + " §ato §f" + playerName), false);
        return 1;
    }

    private static int executeClear(CommandContext<ServerCommandSource> ctx) {
        BlockPos pos = getPos(ctx);
        ServerWorld world = ctx.getSource().getWorld();
        BeaconAccessManager manager = BeaconAccessManager.get(ctx.getSource().getServer());
        manager.removeBeacon(world, pos);
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
