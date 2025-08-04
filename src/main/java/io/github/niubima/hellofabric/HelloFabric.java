package io.github.niubima.hellofabric;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HelloFabric — 入口类
 * 功能：
 *   1. /endpoem 指令：重播终末之诗
 *   2. 注册内置资源包 chinese_end_poem：提供中文 end.txt / credits.json，玩家可自由开关
 */
public class HelloFabric implements ModInitializer {

    public static final String MOD_ID = "hellofabric";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("HelloFabric loaded!");

        /* ---------- 功能 1：/endpoem 指令 ---------- */
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
                dispatcher.register(
                        CommandManager.literal("endpoem")
                                .requires(src -> src.hasPermissionLevel(0)) // 0=所有人，2=OP
                                .executes(this::playEndPoem)
                ));

        /* ---------- 功能 2：注册内置资源包 ---------- */
        FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(container -> {
            ResourceManagerHelper.registerBuiltinResourcePack(
                    Identifier.of(MOD_ID, "chinese_end_poem"),
                    container,
                    ResourcePackActivationType.DEFAULT_ENABLED   // ← 改成这个
            );
            LOGGER.info("Builtin resource pack 'chinese_end_poem' registered.");
        });
    }

    /** /endpoem 执行体：向玩家发送 GAME_WON 数据包以触发字幕 */
    private int playEndPoem(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player != null) {
            player.networkHandler.sendPacket(
                    new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_WON, 1.0F)
            );
            LOGGER.info("Sent End Poem packet to {}", player.getGameProfile().getName());
        }
        return Command.SINGLE_SUCCESS;
    }
}
