package io.github.niubima.endpoemforge;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = Endpoemforge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EndpoemforgeEvents {
    private EndpoemforgeEvents() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("endpoem")
                        .requires(src -> src.hasPermission(0))
                        .executes(ctx -> {
                            ServerPlayer self = ctx.getSource().getPlayerOrException();
                            return replay(ctx.getSource(), self);
                        })
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    return replay(ctx.getSource(), target);
                                })
                        )
        );
    }

    private static int replay(CommandSourceStack src, ServerPlayer player) {
        if (player != null) {
            player.connection.send(new ClientboundGameEventPacket(
                    ClientboundGameEventPacket.WIN_GAME, 1.0F
            ));
            // 只写到控制台，不给玩家发消息
            LOGGER.info("[/endpoem] Replayed End Poem for {}", player.getGameProfile().getName());
        }
        return com.mojang.brigadier.Command.SINGLE_SUCCESS; // = 1
    }
}
