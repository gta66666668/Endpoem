package io.github.niubima.endpoemfabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.client.config.EndPoemEditorScreen;
import io.github.niubima.endpoemfabric.client.config.EndpoemConfigScreen;
import io.github.niubima.endpoemfabric.config.EndpoemConfigManager;
import io.github.niubima.endpoemfabric.network.PermissionLevelNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class EndpoemfabricClient implements ClientModInitializer {
    private static final KeyMapping.Category ENDPOEM_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Endpoemfabric.MODID, "category"));

    private KeyMapping openConfigKey;

    @Override
    public void onInitializeClient() {
        CustomEndPoem.initialize();
        CustomEndPoemBackground.initialize();

        FabricLoader.getInstance().getModContainer(Endpoemfabric.MODID).ifPresent(mod -> {
            Identifier packId = Identifier.fromNamespaceAndPath(Endpoemfabric.MODID, "chinese_end_poem");
            ResourceLoader.registerBuiltinPack(
                    packId,
                    mod,
                    Component.translatable("pack.endpoemfabric.chinese_end_poem.name"),
                    PackActivationType.DEFAULT_ENABLED
            );
        });

        openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.endpoemfabric.open_config",
                GLFW.GLFW_KEY_K,
                ENDPOEM_CATEGORY
        ));
        migrateLegacyOpenConfigKey();

        ClientPlayNetworking.registerGlobalReceiver(PermissionLevelNetworking.StatePayload.TYPE, (payload, context) -> {
            if (context.client().gui.screen() instanceof EndpoemConfigScreen screen) {
                screen.updatePermissionLevelState(payload.authorized(), payload.permissionLevel());
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openConfigKey.consumeClick()) {
                openConfigScreen(client);
            }
        });

        // Minecraft only forwards key mappings while no ordinary GUI is open. Register a
        // screen listener as well so the configured shortcut also works from the title,
        // inventory, and other non-text-entry screens.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
                ScreenKeyboardEvents.allowKeyPress(screen).register((currentScreen, keyEvent) -> {
                    if (!shouldOpenConfigFrom(currentScreen)
                            || !openConfigKey.matches(keyEvent)) {
                        return true;
                    }

                    openConfigScreen(client);
                    return false;
                })
        );
    }

    private static void openConfigScreen(Minecraft client) {
        Screen parent = client.gui.screen();
        client.gui.setScreen(EndpoemConfigScreen.create(parent));
    }

    private void migrateLegacyOpenConfigKey() {
        if (EndpoemConfigManager.get().migratedOpenConfigKeyToK) {
            return;
        }

        if ("key.keyboard.o".equals(openConfigKey.saveString())) {
            openConfigKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
            KeyMapping.resetMapping();
            Minecraft.getInstance().options.save();
        }

        EndpoemConfigManager.update(config -> config.migratedOpenConfigKeyToK = true);
    }

    private static boolean shouldOpenConfigFrom(Screen screen) {
        return !(screen instanceof EndpoemConfigScreen)
                && !(screen instanceof EndPoemEditorScreen)
                && !(screen.getFocused() instanceof EditBox)
                && !(screen.getFocused() instanceof MultiLineEditBox);
    }
}
