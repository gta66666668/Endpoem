package io.github.niubima.endpoemfabric;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class Endpoemfabric implements ModInitializer {
    public static final String MOD_ID = "endpoemfabric";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            dispatcher.register(
                    CommandManager.literal("endpoem")
                            .requires(src -> src.hasPermissionLevel(0)) // 想op可用改成 2
                            .executes(ctx -> runEndPoem(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(CommandManager.argument("targets", EntityArgumentType.players())
                                    .executes(ctx -> {
                                        Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(ctx, "targets");
                                        int n = 0;
                                        for (ServerPlayerEntity p : targets) { runEndPoem(ctx.getSource(), p); n++; }
                                        return n;
                                    }))
            );
        });
    }

    private int runEndPoem(ServerCommandSource src, ServerPlayerEntity player) {
        if (player != null) {
            player.networkHandler.sendPacket(
                    new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_WON, 1.0F)
            );
            LOGGER.info("Replayed End Poem for {}", player.getGameProfile().getName());
        }
        return Command.SINGLE_SUCCESS;
    }
}
