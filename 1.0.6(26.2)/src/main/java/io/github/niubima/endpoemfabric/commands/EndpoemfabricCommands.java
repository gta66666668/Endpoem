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
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
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
                        .requires(src -> hasConfiguredPermission(src, cfg().permissionLevel))
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
                                .requires(src -> hasConfiguredPermission(src, cfg().permissionLevel))
                                .executes(ctx -> replayAll(ctx.getSource()))
                        )
        );
    }

    private static EndpoemConfig cfg() {
        return EndpoemConfigManager.get();
    }

    private static int replay(CommandSourceStack src, ServerPlayer player) throws CommandSyntaxException {
        if (player == null) throw new SimpleCommandExceptionType(Component.literal("No player")).create();

        EndpoemConfig c = cfg();
        if (!c.allowSpectator && player.isSpectator()) {
            throw new SimpleCommandExceptionType(Component.translatable("endpoemfabric.msg.spectator_blocked")).create();
        }

        checkCooldown(src, c);
        sendWinPacket(player);
        markCooldown(src, c);
        incrementCommandStat(src);
        src.sendSuccess(() -> Component.translatable(
                "endpoemfabric.msg.replayed",
                player.getName().getString()
        ), false);
        Endpoemfabric.LOGGER.info("Replayed End Poem for {}", player.getName().getString());
        return 1;
    }

    private static int replayAll(CommandSourceStack src) throws CommandSyntaxException {
        EndpoemConfig c = cfg();
        int count = 0;
        MinecraftServer server = src.getServer();
        PlayerList pm = server.getPlayerList();
        checkCooldown(src, c);
        for (ServerPlayer player : pm.getPlayers()) {
            if (!c.allowSpectator && player.isSpectator()) continue;
            try {
                sendWinPacket(player);
                count++;
            } catch (Throwable t) {
                Endpoemfabric.LOGGER.warn("Failed to send End Poem to {}: {}", player.getName().getString(), t.toString());
            }
        }
        if (count > 0) {
            markCooldown(src, c);
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

    private static void checkCooldown(CommandSourceStack src, EndpoemConfig config) throws CommandSyntaxException {
        if (config.cooldownSeconds <= 0 || !(src.getEntity() instanceof ServerPlayer player)) {
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

    private static void markCooldown(CommandSourceStack src, EndpoemConfig config) {
        if (config.cooldownSeconds <= 0 || !(src.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        COOLDOWNS.put(player.getUUID(), System.currentTimeMillis() + config.cooldownSeconds * 1000L);
    }

    private static boolean hasConfiguredPermission(CommandSourceStack src, int permissionLevel) {
        PermissionCheck permission = switch (permissionLevel) {
            case 0 -> Commands.LEVEL_ALL;
            case 1 -> Commands.LEVEL_MODERATORS;
            case 2 -> Commands.LEVEL_GAMEMASTERS;
            case 3 -> Commands.LEVEL_ADMINS;
            default -> Commands.LEVEL_OWNERS;
        };
        return Commands.<CommandSourceStack>hasPermission(permission).test(src);
    }
}
