package io.github.niubima.endpoemfabric.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import io.github.niubima.endpoemfabric.Endpoemfabric;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class EndpoemConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static EndpoemConfig config = new EndpoemConfig();

    private EndpoemConfigManager() {
    }

    public static synchronized void load() {
        Path path = path();
        if (!Files.exists(path)) {
            save();
            return;
        }

        try {
            EndpoemConfig loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), EndpoemConfig.class);
            config = loaded == null ? new EndpoemConfig() : loaded;
            sanitize(config);
            save();
        } catch (IOException | JsonSyntaxException e) {
            Endpoemfabric.LOGGER.warn("Failed to load config from {}. Using defaults.", path, e);
            config = new EndpoemConfig();
            save();
        }
    }

    public static synchronized EndpoemConfig get() {
        return config;
    }

    public static synchronized void update(Consumer<EndpoemConfig> updater) {
        updater.accept(config);
        sanitize(config);
        save();
    }

    public static synchronized void save() {
        sanitize(config);
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Endpoemfabric.LOGGER.warn("Failed to save config to {}.", path, e);
        }
    }

    public static Path path() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve(Endpoemfabric.MODID + ".json");
    }

    private static void sanitize(EndpoemConfig value) {
        value.permissionLevel = clamp(value.permissionLevel, 0, 4);
        value.cooldownSeconds = clamp(value.cooldownSeconds, 0, 3600);
        value.backgroundMode = sanitizeBackgroundMode(value.backgroundMode);
        value.backgroundScale = sanitizeBackgroundScale(value.backgroundScale);
    }

    private static String sanitizeBackgroundMode(String value) {
        return switch (value == null ? "" : value) {
            case EndpoemConfig.BACKGROUND_BLACK,
                 EndpoemConfig.BACKGROUND_PURPLE,
                 EndpoemConfig.BACKGROUND_CUSTOM -> value;
            default -> EndpoemConfig.BACKGROUND_VANILLA;
        };
    }

    private static String sanitizeBackgroundScale(String value) {
        return switch (value == null ? "" : value) {
            case EndpoemConfig.BACKGROUND_SCALE_CONTAIN,
                 EndpoemConfig.BACKGROUND_SCALE_STRETCH -> value;
            default -> EndpoemConfig.BACKGROUND_SCALE_COVER;
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
