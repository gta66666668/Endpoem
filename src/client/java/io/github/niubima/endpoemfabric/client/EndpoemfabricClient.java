package io.github.niubima.endpoemfabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class EndpoemfabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricLoader.getInstance().getModContainer("endpoemfabric").ifPresent(mod -> {
            Identifier packId = Identifier.of("endpoemfabric", "chinese_end_poem");
            ResourceManagerHelper.registerBuiltinResourcePack(
                    packId, mod,
                    Text.translatable("pack.endpoemfabric.chinese_end_poem.name"),
                    ResourcePackActivationType.DEFAULT_ENABLED
            );
        });
    }
}
