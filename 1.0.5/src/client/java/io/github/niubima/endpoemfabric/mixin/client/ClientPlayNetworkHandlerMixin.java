package io.github.niubima.endpoemfabric.mixin.client;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.config.EndpoemConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onGameStateChange", at = @At("HEAD"), cancellable = true)
    private void endpoemfabric$blockEndPoemPacket(GameStateChangeS2CPacket packet, CallbackInfo ci) {
        if (packet.getReason() != GameStateChangeS2CPacket.GAME_WON) {
            return;
        }

        EndpoemConfig config = AutoConfig.getConfigHolder(EndpoemConfig.class).getConfig();
        if (config.acceptEndpoem) {
            return;
        }

        Endpoemfabric.LOGGER.info("Blocked End Poem screen from server because acceptEndpoem is disabled.");
        notifyBlockedEndPoem();
        ci.cancel();
    }

    private static void notifyBlockedEndPoem() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(Text.translatable("endpoemfabric.msg.blocked_endpoem"), false);
            }
        });
    }
}
