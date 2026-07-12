package io.github.niubima.endpoemfabric.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.config.EndpoemConfig;
import io.github.niubima.endpoemfabric.config.EndpoemConfigManager;
import io.github.niubima.endpoemfabric.stats.EndpoemStats;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EndpoemfabricCommands {
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();

    private EndpoemfabricCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("endpoem")
                        .requires(src -> hasPermission(src, cfg().permissionLevel))
                        .executes(ctx -> {
                            ServerPlayer self = ctx.getSource().getPlayer();
                            return replay(ctx.getSource(), self);
                        })
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    return replay(ctx.getSource(), target);
                                })
                        )
                        .then(Commands.literal("--all")
                                .executes(ctx -> replayAll(ctx.getSource()))
                        )
                        .then(Commands.literal("config")
                                .requires(EndpoemfabricCommands::hasOwnerPermission)
                                .then(Commands.literal("op")
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 4))
                                                .executes(ctx -> updatePermissionLevel(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "level")
                                                ))
                                        )
                                )
                                .then(Commands.literal("cooldown")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                                .executes(ctx -> updateCooldown(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "seconds")
                                                ))
                                        )
                                )
                        )
        );
    }

    private static EndpoemConfig cfg() {
        return EndpoemConfigManager.get();
    }

    private static int replay(CommandSourceStack src, ServerPlayer player) throws CommandSyntaxException {
        if (player == null) throw new SimpleCommandExceptionType(Component.literal("No player")).create();

        checkCooldown(src);
        sendWinPacket(player);
        markCooldown(src);
        incrementCommandStat(src);
        src.sendSuccess(() -> Component.translatable(
                "endpoemfabric.msg.replayed",
                player.getName().getString()
        ), false);
        Endpoemfabric.LOGGER.info("Replayed End Poem for {}", player.getName().getString());
        return 1;
    }

    private static int replayAll(CommandSourceStack src) throws CommandSyntaxException {
        int count = 0;
        MinecraftServer server = src.getServer();
        PlayerList pm = server.getPlayerList();
        checkCooldown(src);
        for (ServerPlayer player : pm.getPlayers()) {
            try {
                sendWinPacket(player);
                count++;
            } catch (Throwable t) {
                Endpoemfabric.LOGGER.warn("Failed to send End Poem to {}: {}", player.getName().getString(), t.toString());
            }
        }
        if (count > 0) {
            markCooldown(src);
            incrementCommandStat(src);
            int replayedCount = count;
            src.sendSuccess(() -> Component.translatable("endpoemfabric.msg.replayed_all", replayedCount), false);
        } else {
            src.sendSuccess(() -> Component.translatable("endpoemfabric.msg.replayed_none"), false);
        }
        Endpoemfabric.LOGGER.info("Replayed End Poem for ALL ({} players) by {}", count, src.getTextName());
        return count;
    }

    private static void sendWinPacket(ServerPlayer player) {
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, 1.0F));
    }

    private static void incrementCommandStat(CommandSourceStack src) {
        if (src.getEntity() instanceof ServerPlayer player) {
            player.awardStat(EndpoemStats.ENDPOEM_COMMANDS_TRIGGERED, 1);
        }
    }

    private static void checkCooldown(CommandSourceStack src) throws CommandSyntaxException {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long nextAllowedAt = COOLDOWNS.get(player.getUUID());
        if (nextAllowedAt == null || now >= nextAllowedAt) {
            return;
        }

        long remainingSeconds = Math.max(1L, (nextAllowedAt - now + 999L) / 1000L);
        throw new SimpleCommandExceptionType(
                Component.translatable("endpoemfabric.msg.cooldown", remainingSeconds)
        ).create();
    }

    private static void markCooldown(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        COOLDOWNS.put(player.getUUID(), System.currentTimeMillis() + cfg().cooldownSeconds * 1000L);
    }

    private static int updatePermissionLevel(CommandSourceStack src, int permissionLevel) {
        EndpoemConfigManager.update(config -> config.permissionLevel = permissionLevel);
        src.sendSuccess(() -> Component.translatable("endpoemfabric.msg.permission_updated", permissionLevel), true);
        return permissionLevel;
    }

    private static int updateCooldown(CommandSourceStack src, int cooldownSeconds) {
        EndpoemConfigManager.update(config -> config.cooldownSeconds = cooldownSeconds);
        COOLDOWNS.clear();
        src.sendSuccess(() -> Component.translatable("endpoemfabric.msg.cooldown_updated", cooldownSeconds), true);
        return cooldownSeconds;
    }

    private static boolean hasPermission(CommandSourceStack src, int permissionLevel) {
        return Commands.<CommandSourceStack>hasPermission(switch (permissionLevel) {
            case 0 -> Commands.LEVEL_ALL;
            case 1 -> Commands.LEVEL_MODERATORS;
            case 2 -> Commands.LEVEL_GAMEMASTERS;
            case 3 -> Commands.LEVEL_ADMINS;
            default -> Commands.LEVEL_OWNERS;
        }).test(src);
    }

    private static boolean hasOwnerPermission(CommandSourceStack src) {
        return Commands.<CommandSourceStack>hasPermission(Commands.LEVEL_OWNERS).test(src);
    }
}
