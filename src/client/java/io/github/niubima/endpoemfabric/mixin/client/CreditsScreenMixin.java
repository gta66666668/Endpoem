package io.github.niubima.endpoemfabric.mixin.client;

import io.github.niubima.endpoemfabric.client.CustomEndPoem;
import io.github.niubima.endpoemfabric.client.CustomEndPoemBackground;
import io.github.niubima.endpoemfabric.client.CustomEndPoemMusic;
import io.github.niubima.endpoemfabric.client.EndPoemBackgroundMusic;
import io.github.niubima.endpoemfabric.config.EndpoemConfigManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.Reader;

@Mixin(WinScreen.class)
public abstract class CreditsScreenMixin {
    @Shadow
    @Final
    private boolean poem;

    @ModifyVariable(method = "addPoemFile", at = @At("HEAD"), argsOnly = true)
    private Reader endpoemfabric$useCustomEndPoem(Reader original) {
        return CustomEndPoem.readerOrOriginal(original);
    }

    @ModifyConstant(method = "<init>", constant = @Constant(floatValue = 0.5F), require = 1)
    private float endpoemfabric$applyScrollSpeed(float vanillaSpeed) {
        return vanillaSpeed * EndpoemConfigManager.get().scrollSpeedMultiplier;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void endpoemfabric$prepareCustomAssets(CallbackInfo ci) {
        if (poem) {
            CustomEndPoemBackground.prepareForEndPoem();
            CustomEndPoemMusic.prepareForEndPoem();
        }
    }

    @Inject(method = "getBackgroundMusic", at = @At("HEAD"), cancellable = true)
    private void endpoemfabric$useConfiguredBackgroundMusic(CallbackInfoReturnable<Music> cir) {
        if (poem) {
            cir.setReturnValue(EndPoemBackgroundMusic.getConfiguredMusic());
        }
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void endpoemfabric$renderCustomBackground(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        WinScreen screen = (WinScreen) (Object) this;
        if (poem && CustomEndPoemBackground.render(graphics, screen.width, screen.height)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void endpoemfabric$hideEndPoemVignette(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (poem && !EndpoemConfigManager.get().showEndPoemVignette) {
            ci.cancel();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void endpoemfabric$stopCustomBackgroundMusic(CallbackInfo ci) {
        if (poem) {
            CustomEndPoemMusic.stopForEndPoem();
        }
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void endpoemfabric$stopConfiguredBackgroundMusic(CallbackInfo ci) {
        if (poem) {
            EndPoemBackgroundMusic.stopForEndPoem();
        }
    }
}
