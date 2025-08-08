package io.github.niubima.hellofabric;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class HelloFabric implements ModInitializer {
    public static final String MOD_ID = "hellofabric";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // 注册带选择器的 endpoem 指令
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    CommandManager.literal("endpoem")
                            // 任何权限级别都能执行
                            .executes(ctx -> runEndPoem(ctx.getSource(), ctx.getSource().getPlayer()))
                            // 加一个可选的 targets 参数，支持 @e、@a、@p、@s、@r
                            .then(CommandManager.argument("targets", EntityArgumentType.players())
                                    .executes(ctx -> {
                                        Collection<ServerPlayerEntity> targets =
                                                EntityArgumentType.getPlayers(ctx, "targets");
                                        int success = 0;
                                        for (ServerPlayerEntity p : targets) {
                                            runEndPoem(ctx.getSource(), p);
                                            success++;
                                        }
                                        return success;
                                    }))
            );
        });

        // 注册内置资源包
        FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .ifPresent(mod -> {
                    Identifier packId = Identifier.of(MOD_ID, "chinese_end_poem");
                    ResourceManagerHelper.registerBuiltinResourcePack(
                            packId, mod, ResourcePackActivationType.DEFAULT_ENABLED
                    );
                    LOGGER.info("Registered builtin resource pack {}", packId);
                });
    }

    /**
     * 给指定玩家发送 End Poem 重放包
     * @param src    命令源（用于日志/反馈）
     * @param player 要重放给的玩家
     * @return       Command.SINGLE_SUCCESS
     */
    private int runEndPoem(ServerCommandSource src, ServerPlayerEntity player) {
        if (player != null) {
            // 发给客户端一个“胜利”游戏状态包，触发结束诗重播
            player.networkHandler.sendPacket(
                    new GameStateChangeS2CPacket(
                            GameStateChangeS2CPacket.GAME_WON, 1.0F
                    )
            );
            LOGGER.info("Replayed End Poem for {}", player.getGameProfile().getName());
        }
        return Command.SINGLE_SUCCESS;
    }
}