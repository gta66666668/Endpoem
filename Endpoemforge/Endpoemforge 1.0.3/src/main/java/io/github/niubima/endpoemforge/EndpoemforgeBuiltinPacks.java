package io.github.niubima.endpoemforge;

import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.PackType;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = Endpoemforge.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EndpoemforgeBuiltinPacks {
    private EndpoemforgeBuiltinPacks() {}

    private static final String PACK_ID = "chinese_end_poem";

    @SubscribeEvent
    public static void onAddPackFinders(final AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;

        var modFile = ModList.get().getModFileById(Endpoemforge.MODID);
        if (modFile == null) return;

        // 对应 src/main/resources/resourcepacks/chinese_end_poem/...
        Path packRoot = modFile.getFile().findResource("resourcepacks", PACK_ID);
        if (!Files.exists(packRoot)) return;

        event.addRepositorySource(consumer -> {
            String fullId = Endpoemforge.MODID + ":" + PACK_ID;

            // 1) 位置描述
            PackLocationInfo location = new PackLocationInfo(
                    fullId,
                    Component.translatable("pack." + Endpoemforge.MODID + "." + PACK_ID),
                    PackSource.BUILT_IN,
                    Optional.empty()
            );

            // 2) 选择配置（可选、置顶、默认不启用）
            PackSelectionConfig select = new PackSelectionConfig(
                    true,
                    Pack.Position.TOP,
                    false
            );

            // 3) 资源供应器（1.21 用 PathResourcesSupplier）
            Pack.ResourcesSupplier supplier = new PathPackResources.PathResourcesSupplier(packRoot);

            // 4) 新签名创建
            Pack pack = Pack.readMetaAndCreate(location, supplier, PackType.CLIENT_RESOURCES, select);
            if (pack != null) consumer.accept(pack);
        });
    }
}
