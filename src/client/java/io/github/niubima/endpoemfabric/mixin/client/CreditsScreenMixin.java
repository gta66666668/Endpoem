package io.github.niubima.endpoemfabric.mixin.client;

import io.github.niubima.endpoemfabric.client.CustomEndPoem;
import net.minecraft.client.gui.screens.WinScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.io.Reader;

@Mixin(WinScreen.class)
public abstract class CreditsScreenMixin {
    @ModifyVariable(method = "addPoemFile", at = @At("HEAD"), argsOnly = true)
    private Reader endpoemfabric$useCustomEndPoem(Reader original) {
        return CustomEndPoem.readerOrOriginal(original);
    }
}
