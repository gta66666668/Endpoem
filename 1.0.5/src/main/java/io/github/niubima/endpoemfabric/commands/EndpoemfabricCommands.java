package io.github.niubima.endpoemfabric.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.config.EndpoemConfig;
import io.github.niubima.endpoemfabric.stats.EndpoemStats;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EndpoemfabricCommands {
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();

    private EndpoemfabricCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("endpoem")
                        .requires(src -> src.hasPermissionLevel(cfg().permissionLevel))
                        .executes(ctx -> {
                            ServerPlayerEntity self = ctx.getSource().getPlayer();
                            return replay(ctx.getSource(), self);
                        })
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(ctx -> {
                                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                    return replay(ctx.getSource(), target);
                                })
                        )
                        .then(CommandManager.literal("--all")
                                .requires(src -> src.hasPermissionLevel(cfg().permissionLevel))
                                .executes(ctx -> replayAll(ctx.getSource()))
                        )
        );
    }

    private static EndpoemConfig cfg() {
        return AutoConfig.getConfigHolder(EndpoemConfig.class).getConfig();
    }

    private static int replay(ServerCommandSource src, ServerPlayerEntity player) throws CommandSyntaxException {
        if (player == null) throw new SimpleCommandExceptionType(Text.literal("No player")).create();

        EndpoemConfig c = cfg();
        if (!c.allowSpectator && player.isSpectator()) {
            throw new SimpleCommandExceptionType(Text.translatable("endpoemfabric.msg.spectator_blocked")).create();
        }

        checkCooldown(src, c);
        sendWinPacket(player);
        markCooldown(src, c);
        incrementCommandStat(src);
        src.sendFeedback(() -> Text.translatable(
                "endpoemfabric.msg.replayed",
                player.getGameProfile().getName()
        ), false);
        Endpoemfabric.LOGGER.info("Replayed End Poem for {}", player.getGameProfile().getName());
        return 1;
    }

    private static int replayAll(ServerCommandSource src) throws CommandSyntaxException {
        EndpoemConfig c = cfg();
        int count = 0;
        MinecraftServer server = src.getServer();
        PlayerManager pm = server.getPlayerManager();
        checkCooldown(src, c);
        for (ServerPlayerEntity player : pm.getPlayerList()) {
            if (!c.allowSpectator && player.isSpectator()) continue;
            try {
                sendWinPacket(player);
                count++;
            } catch (Throwable t) {
                Endpoemfabric.LOGGER.warn("Failed to send End Poem to {}: {}", player.getGameProfile().getName(), t.toString());
            }
        }
        if (count > 0) {
            markCooldown(src, c);
            incrementCommandStat(src);
            int replayedCount = count;
            src.sendFeedback(() -> Text.translatable("endpoemfabric.msg.replayed_all", replayedCount), false);
        } else {
            src.sendFeedback(() -> Text.translatable("endpoemfabric.msg.replayed_none"), false);
        }
        Endpoemfabric.LOGGER.info("Replayed End Poem for ALL ({} players) by {}", count, src.getName());
        return count;
    }

    private static void sendWinPacket(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_WON, 1.0F));
    }

    private static void incrementCommandStat(ServerCommandSource src) {
        if (src.getEntity() instanceof ServerPlayerEntity player) {
            player.increaseStat(EndpoemStats.ENDPOEM_COMMANDS_TRIGGERED, 1);
        }
    }

    private static void checkCooldown(ServerCommandSource src, EndpoemConfig config) throws CommandSyntaxException {
        if (config.cooldownSeconds <= 0 || !(src.getEntity() instanceof ServerPlayerEntity player)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long nextAllowedAt = COOLDOWNS.get(player.getUuid());
        if (nextAllowedAt == null || now >= nextAllowedAt) {
            return;
        }

        long remainingSeconds = Math.max(1L, (nextAllowedAt - now + 999L) / 1000L);
        throw new SimpleCommandExceptionType(
                Text.translatable("endpoemfabric.msg.cooldown", remainingSeconds)
        ).create();
    }

    private static void markCooldown(ServerCommandSource src, EndpoemConfig config) {
        if (config.cooldownSeconds <= 0 || !(src.getEntity() instanceof ServerPlayerEntity player)) {
            return;
        }

        COOLDOWNS.put(player.getUuid(), System.currentTimeMillis() + config.cooldownSeconds * 1000L);
    }
}
