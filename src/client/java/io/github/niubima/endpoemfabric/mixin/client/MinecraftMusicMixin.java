package io.github.niubima.endpoemfabric.mixin.client;

import io.github.niubima.endpoemfabric.client.CustomEndPoemMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents the global music manager from falling back to biome music while a
 * custom End Poem stream owns the music channel.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMusicMixin {
    @Inject(method = "getSituationalMusic", at = @At("HEAD"), cancellable = true)
    private void endpoemfabric$suppressVanillaMusicForCustomEndPoem(CallbackInfoReturnable<Music> cir) {
        if (CustomEndPoemMusic.isActive()) {
            cir.setReturnValue(null);
        }
    }
}
