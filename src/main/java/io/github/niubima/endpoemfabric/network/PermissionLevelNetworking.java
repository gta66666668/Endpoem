package io.github.niubima.endpoemfabric.network;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.commands.EndpoemfabricCommands;
import io.github.niubima.endpoemfabric.config.EndpoemConfigManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class PermissionLevelNetworking {
    private PermissionLevelNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(RequestPayload.TYPE, RequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdatePayload.TYPE, UpdatePayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RequestPayload.TYPE, (payload, context) -> {
            sendState(context.player(), canManage(context.player()));
        });
        ServerPlayNetworking.registerGlobalReceiver(UpdatePayload.TYPE, (payload, context) -> {
            if (!canManage(context.player())) {
                sendState(context.player(), false);
                return;
            }
            if (payload.permissionLevel() < 0 || payload.permissionLevel() > 4) {
                sendState(context.player(), true);
                return;
            }

            EndpoemfabricCommands.updatePermissionLevel(
                    context.player().createCommandSourceStack(),
                    payload.permissionLevel()
            );
            sendState(context.player(), true);
        });
    }

    private static boolean canManage(ServerPlayer player) {
        return Commands.<CommandSourceStack>hasPermission(Commands.LEVEL_OWNERS)
                .test(player.createCommandSourceStack());
    }

    private static void sendState(ServerPlayer player, boolean authorized) {
        if (ServerPlayNetworking.canSend(player, StatePayload.TYPE)) {
            ServerPlayNetworking.send(player, new StatePayload(
                    authorized,
                    authorized ? EndpoemConfigManager.get().permissionLevel : 0
            ));
        }
    }

    public record RequestPayload() implements CustomPacketPayload {
        public static final RequestPayload INSTANCE = new RequestPayload();
        public static final Type<RequestPayload> TYPE = new Type<>(id("permission_level_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestPayload> STREAM_CODEC =
                StreamCodec.unit(INSTANCE);

        @Override
        public Type<RequestPayload> type() {
            return TYPE;
        }
    }

    public record UpdatePayload(int permissionLevel) implements CustomPacketPayload {
        public static final Type<UpdatePayload> TYPE = new Type<>(id("permission_level_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdatePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                UpdatePayload::permissionLevel,
                UpdatePayload::new
        );

        @Override
        public Type<UpdatePayload> type() {
            return TYPE;
        }
    }

    public record StatePayload(boolean authorized, int permissionLevel) implements CustomPacketPayload {
        public static final Type<StatePayload> TYPE = new Type<>(id("permission_level_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL,
                StatePayload::authorized,
                ByteBufCodecs.VAR_INT,
                StatePayload::permissionLevel,
                StatePayload::new
        );

        @Override
        public Type<StatePayload> type() {
            return TYPE;
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Endpoemfabric.MODID, path);
    }
}
