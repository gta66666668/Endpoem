package io.github.niubima.endpoemfabric.client;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.client.config.EndpoemConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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

        FabricLoader.getInstance().getModContainer(Endpoemfabric.MODID).ifPresent(mod -> {
            Identifier packId = Identifier.fromNamespaceAndPath(Endpoemfabric.MODID, "chinese_end_poem");
            ResourceManagerHelper.registerBuiltinResourcePack(
                    packId,
                    mod,
                    Component.translatable("pack.endpoemfabric.chinese_end_poem.name"),
                    ResourcePackActivationType.DEFAULT_ENABLED
            );
        });

        openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.endpoemfabric.open_config",
                GLFW.GLFW_KEY_O,
                ENDPOEM_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.consumeClick()) {
                openConfigScreen(client);
            }
        });
    }

    private static void openConfigScreen(Minecraft client) {
        Screen parent = client.screen;
        client.setScreen(EndpoemConfigScreen.create(parent));
    }
}
