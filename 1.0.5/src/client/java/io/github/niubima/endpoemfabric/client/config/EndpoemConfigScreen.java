package io.github.niubima.endpoemfabric.client.config;

import io.github.niubima.endpoemfabric.config.EndpoemConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class EndpoemConfigScreen {
    private EndpoemConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ConfigHolder<EndpoemConfig> holder = AutoConfig.getConfigHolder(EndpoemConfig.class);
        EndpoemConfig config = holder.getConfig();
        EndpoemConfig defaults = new EndpoemConfig();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("text.autoconfig.endpoemfabric.title"))
                .setSavingRunnable(holder::save);
        ConfigEntryBuilder entries = builder.entryBuilder();

        if (canEditServerRules()) {
            ConfigCategory rules = builder.getOrCreateCategory(Text.translatable("text.autoconfig.endpoemfabric.category.rules"));
            rules.addEntry(entries.startIntField(
                            Text.translatable("text.autoconfig.endpoemfabric.option.permissionLevel"),
                            config.permissionLevel)
                    .setDefaultValue(defaults.permissionLevel)
                    .setMin(0)
                    .setMax(4)
                    .setTooltip(Text.translatable("text.autoconfig.endpoemfabric.option.permissionLevel.@Tooltip"))
                    .setSaveConsumer(value -> config.permissionLevel = value)
                    .build());
            rules.addEntry(entries.startBooleanToggle(
                            Text.translatable("text.autoconfig.endpoemfabric.option.allowSpectator"),
                            config.allowSpectator)
                    .setDefaultValue(defaults.allowSpectator)
                    .setTooltip(Text.translatable("text.autoconfig.endpoemfabric.option.allowSpectator.@Tooltip"))
                    .setSaveConsumer(value -> config.allowSpectator = value)
                    .build());
            rules.addEntry(entries.startIntField(
                            Text.translatable("text.autoconfig.endpoemfabric.option.cooldownSeconds"),
                            config.cooldownSeconds)
                    .setDefaultValue(defaults.cooldownSeconds)
                    .setMin(0)
                    .setMax(3600)
                    .setTooltip(Text.translatable("text.autoconfig.endpoemfabric.option.cooldownSeconds.@Tooltip"))
                    .setSaveConsumer(value -> config.cooldownSeconds = value)
                    .build());
        }

        ConfigCategory privacy = builder.getOrCreateCategory(Text.translatable("text.autoconfig.endpoemfabric.category.privacy"));
        privacy.addEntry(entries.startBooleanToggle(
                        Text.translatable("text.autoconfig.endpoemfabric.option.acceptEndpoem"),
                        config.acceptEndpoem)
                .setDefaultValue(defaults.acceptEndpoem)
                .setTooltip(Text.translatable("text.autoconfig.endpoemfabric.option.acceptEndpoem.@Tooltip"))
                .setSaveConsumer(value -> config.acceptEndpoem = value)
                .build());

        ConfigCategory poem = builder.getOrCreateCategory(Text.translatable("text.autoconfig.endpoemfabric.category.poem"));
        poem.addEntry(entries.startBooleanToggle(
                        Text.translatable("text.autoconfig.endpoemfabric.option.useCustomEndPoem"),
                        config.useCustomEndPoem)
                .setDefaultValue(defaults.useCustomEndPoem)
                .setTooltip(Text.translatable("text.autoconfig.endpoemfabric.option.useCustomEndPoem.@Tooltip"))
                .setSaveConsumer(value -> config.useCustomEndPoem = value)
                .build());
        poem.addEntry(new EditEndPoemEntry());

        return builder.build();
    }

    private static boolean canEditServerRules() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return true;
        }
        return client.isIntegratedServerRunning();
    }
}
