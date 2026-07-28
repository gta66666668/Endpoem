package io.github.niubima.endpoemfabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.client.CustomEndPoem;
import io.github.niubima.endpoemfabric.client.CustomEndPoemBackground;
import io.github.niubima.endpoemfabric.client.CustomEndPoemMusic;
import io.github.niubima.endpoemfabric.client.CustomCredits;
import io.github.niubima.endpoemfabric.client.EndPoemBackgroundMusic;
import io.github.niubima.endpoemfabric.client.EndPoemCreditsPlacement;
import io.github.niubima.endpoemfabric.config.EndpoemConfig;
import io.github.niubima.endpoemfabric.config.EndpoemConfigManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.BufferedReader;
import java.io.Reader;

@Mixin(WinScreen.class)
public abstract class CreditsScreenMixin {
    @Shadow
    @Final
    private static Identifier END_POEM_LOCATION;

    @Shadow
    @Final
    private static Identifier CREDITS_LOCATION;

    @Shadow
    @Final
    private boolean poem;

    @Invoker("addCreditsFile")
    protected abstract void endpoemfabric$invokeAddCreditsFile(Reader reader);

    @Unique
    private Identifier endpoemfabric$activeCreditsSource;

    @Unique
    private boolean endpoemfabric$creditsInsertedInline;

    @Unique
    private boolean endpoemfabric$creditsPlanCaptured;

    @Unique
    private boolean endpoemfabric$showVanillaCredits = true;

    @Unique
    private String endpoemfabric$customCreditsPlacement =
            EndpoemConfig.CREDITS_PLACEMENT_OFF;

    @Unique
    private boolean endpoemfabric$inlineCreditsRequested;

    @ModifyVariable(method = "wrapCreditsIO", at = @At("HEAD"), argsOnly = true, require = 1)
    private Identifier endpoemfabric$captureCreditsSource(Identifier source) {
        endpoemfabric$activeCreditsSource = source;
        return source;
    }

    @ModifyVariable(
            method = "addPoemFile(Ljava/io/Reader;)V",
            at = @At("HEAD"),
            argsOnly = true,
            require = 1
    )
    private Reader endpoemfabric$useCustomEndPoem(Reader original) {
        if (!END_POEM_LOCATION.equals(endpoemfabric$activeCreditsSource)) {
            return original;
        }
        Reader selected = CustomEndPoem.readerOrOriginal(original);
        EndpoemConfig config = EndpoemConfigManager.get();
        endpoemfabric$captureCreditsPlan(config);
        endpoemfabric$creditsInsertedInline = false;
        return EndPoemCreditsPlacement.injectMarkerOrOriginal(
                selected,
                endpoemfabric$customCreditsPlacement,
                config.creditsInsertionProgress
        );
    }

    @ModifyVariable(
            method = "addCreditsFile(Ljava/io/Reader;)V",
            at = @At("HEAD"),
            argsOnly = true,
            require = 1
    )
    private Reader endpoemfabric$useCustomCredits(Reader original) {
        if (!CREDITS_LOCATION.equals(endpoemfabric$activeCreditsSource)) {
            return original;
        }
        if (!poem) {
            return CustomCredits.previewReaderOrOriginal(original);
        }
        if (!endpoemfabric$creditsPlanCaptured) {
            endpoemfabric$captureCreditsPlan(EndpoemConfigManager.get());
        }
        return CustomCredits.readerOrOriginal(
                original,
                endpoemfabric$showVanillaCredits,
                endpoemfabric$customCreditsPlacement,
                endpoemfabric$creditsInsertedInline
        );
    }

    @WrapOperation(
            method = "addPoemFile(Ljava/io/Reader;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/io/BufferedReader;readLine()Ljava/lang/String;"
            ),
            require = 1
    )
    private String endpoemfabric$insertCreditsAtPoemMarker(
            BufferedReader reader,
            Operation<String> original
    ) {
        String line = original.call(reader);
        while (poem
                && END_POEM_LOCATION.equals(endpoemfabric$activeCreditsSource)
                && endpoemfabric$inlineCreditsRequested
                && EndPoemCreditsPlacement.isInsertionMarker(line)) {
            if (!endpoemfabric$creditsInsertedInline) {
                endpoemfabric$insertConfiguredCredits();
            }
            line = original.call(reader);
        }
        return line;
    }

    @Unique
    private void endpoemfabric$insertConfiguredCredits() {
        if (!endpoemfabric$inlineCreditsRequested) {
            return;
        }
        try {
            var creditsBlock = CustomCredits.validatedCustomCreditsReader();
            if (creditsBlock.isEmpty()) {
                return;
            }
            endpoemfabric$invokeAddCreditsFile(creditsBlock.get());
            endpoemfabric$creditsInsertedInline = true;
        } catch (RuntimeException e) {
            Endpoemfabric.LOGGER.warn(
                    "Failed to insert credits into the End Poem. Falling back to normal credits order.",
                    e
            );
        }
    }

    @Unique
    private void endpoemfabric$captureCreditsPlan(EndpoemConfig config) {
        endpoemfabric$creditsPlanCaptured = true;
        endpoemfabric$showVanillaCredits = config.showVanillaCredits;
        endpoemfabric$customCreditsPlacement = config.customCreditsPlacement;
        endpoemfabric$inlineCreditsRequested =
                EndPoemCreditsPlacement.isInlinePlacement(
                        endpoemfabric$customCreditsPlacement
                );
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
