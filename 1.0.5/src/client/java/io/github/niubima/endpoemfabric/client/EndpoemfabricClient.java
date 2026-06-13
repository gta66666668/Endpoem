package io.github.niubima.endpoemfabric.client;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.client.config.EndpoemConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class EndpoemfabricClient implements ClientModInitializer {
    private KeyBinding openConfigKey;

    @Override
    public void onInitializeClient() {
        CustomEndPoem.initialize();

        FabricLoader.getInstance().getModContainer(Endpoemfabric.MODID).ifPresent(mod -> {
            Identifier packId = Identifier.of(Endpoemfabric.MODID, "chinese_end_poem");
            ResourceManagerHelper.registerBuiltinResourcePack(
                    packId,
                    mod,
                    Text.translatable("pack.endpoemfabric.chinese_end_poem.name"),
                    ResourcePackActivationType.DEFAULT_ENABLED
            );
        });

        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.endpoemfabric.open_config",
                GLFW.GLFW_KEY_O,
                "category.endpoemfabric"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.wasPressed()) {
                openConfigScreen(client);
            }
        });
    }

    private static void openConfigScreen(MinecraftClient client) {
        Screen parent = client.currentScreen;
        client.setScreen(EndpoemConfigScreen.create(parent));
    }
}
