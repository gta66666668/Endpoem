package io.github.niubima.endpoemfabric.mixin.client;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.config.EndpoemConfig;
import io.github.niubima.endpoemfabric.config.EndpoemConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "handleGameEvent", at = @At("HEAD"), cancellable = true)
    private void endpoemfabric$blockEndPoemPacket(ClientboundGameEventPacket packet, CallbackInfo ci) {
        if (packet.getEvent() != ClientboundGameEventPacket.WIN_GAME) {
            return;
        }

        EndpoemConfig config = EndpoemConfigManager.get();
        if (config.acceptEndpoem) {
            return;
        }

        Endpoemfabric.LOGGER.info("Blocked End Poem screen from server because acceptEndpoem is disabled.");
        notifyBlockedEndPoem();
        ci.cancel();
    }

    private static void notifyBlockedEndPoem() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(Component.translatable("endpoemfabric.msg.blocked_endpoem"));
            }
        });
    }
}
