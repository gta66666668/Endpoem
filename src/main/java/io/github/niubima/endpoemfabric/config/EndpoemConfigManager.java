package io.github.niubima.endpoemfabric.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.github.niubima.endpoemfabric.Endpoemfabric;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class EndpoemConfigManager {
    static final String LEGACY_CREDITS_MODE_VANILLA = "vanilla";
    static final String LEGACY_CREDITS_MODE_CUSTOM_BEFORE = "custom_before";
    static final String LEGACY_CREDITS_MODE_CUSTOM_AFTER = "custom_after";
    static final String LEGACY_CREDITS_MODE_CUSTOM_ONLY = "custom_only";

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
            JsonElement parsed = JsonParser.parseString(
                    Files.readString(path, StandardCharsets.UTF_8)
            );
            if (!parsed.isJsonObject()) {
                throw new JsonSyntaxException("Config root must be a JSON object.");
            }
            JsonObject root = parsed.getAsJsonObject();
            EndpoemConfig loaded = GSON.fromJson(root, EndpoemConfig.class);
            config = loaded == null ? new EndpoemConfig() : loaded;
            applyCreditsMigration(config, root);
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

    static void sanitize(EndpoemConfig value) {
        value.permissionLevel = clamp(value.permissionLevel, 0, 4);
        value.cooldownSeconds = clamp(value.cooldownSeconds, 0, 3600);
        value.backgroundMode = sanitizeBackgroundMode(value.backgroundMode);
        value.backgroundScale = sanitizeBackgroundScale(value.backgroundScale);
        value.backgroundCropPercent = clamp(value.backgroundCropPercent, 0, 40);
        value.backgroundMusic = sanitizeBackgroundMusic(value.backgroundMusic);
        value.scrollSpeedMultiplier = sanitizeScrollSpeed(value.scrollSpeedMultiplier);
        value.customCreditsPlacement = sanitizeCreditsPlacement(
                value.customCreditsPlacement
        );
        if (!value.showVanillaCredits
                && EndpoemConfig.CREDITS_PLACEMENT_AFTER_VANILLA.equals(
                        value.customCreditsPlacement
                )) {
            value.customCreditsPlacement = EndpoemConfig.CREDITS_PLACEMENT_AFTER_POEM;
        }
        value.creditsInsertionProgress = clamp(value.creditsInsertionProgress, 1, 9999);
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

    private static String sanitizeBackgroundMusic(String value) {
        return switch (value == null ? "" : value) {
            case EndpoemConfig.BACKGROUND_MUSIC_END,
                 EndpoemConfig.BACKGROUND_MUSIC_DRAGON,
                 EndpoemConfig.BACKGROUND_MUSIC_MENU,
                 EndpoemConfig.BACKGROUND_MUSIC_CUSTOM -> value;
            default -> EndpoemConfig.BACKGROUND_MUSIC_CREDITS;
        };
    }

    private static String sanitizeCreditsPlacement(String value) {
        return switch (value == null ? "" : value) {
            case EndpoemConfig.CREDITS_PLACEMENT_OFF,
                 EndpoemConfig.CREDITS_PLACEMENT_BEFORE_POEM,
                 EndpoemConfig.CREDITS_PLACEMENT_INSIDE_POEM,
                 EndpoemConfig.CREDITS_PLACEMENT_AFTER_POEM,
                 EndpoemConfig.CREDITS_PLACEMENT_AFTER_VANILLA -> value;
            default -> EndpoemConfig.CREDITS_PLACEMENT_OFF;
        };
    }

    static void migrateLegacyCredits(
            EndpoemConfig value,
        String legacyMode,
        String legacyPlacement
    ) {
        value.showVanillaCredits = !LEGACY_CREDITS_MODE_CUSTOM_ONLY.equals(
                legacyMode
        );
        boolean showCustomCredits = switch (legacyMode == null ? "" : legacyMode) {
            case LEGACY_CREDITS_MODE_CUSTOM_BEFORE,
                 LEGACY_CREDITS_MODE_CUSTOM_AFTER,
                 LEGACY_CREDITS_MODE_CUSTOM_ONLY -> true;
            default -> false;
        };

        if (!showCustomCredits) {
            value.customCreditsPlacement = EndpoemConfig.CREDITS_PLACEMENT_OFF;
        } else if (EndpoemConfig.CREDITS_PLACEMENT_BEFORE_POEM.equals(
                legacyPlacement
        )) {
            value.customCreditsPlacement = EndpoemConfig.CREDITS_PLACEMENT_BEFORE_POEM;
        } else if (EndpoemConfig.CREDITS_PLACEMENT_INSIDE_POEM.equals(
                legacyPlacement
        )) {
            value.customCreditsPlacement = EndpoemConfig.CREDITS_PLACEMENT_INSIDE_POEM;
        } else if (LEGACY_CREDITS_MODE_CUSTOM_AFTER.equals(legacyMode)) {
            value.customCreditsPlacement = EndpoemConfig.CREDITS_PLACEMENT_AFTER_VANILLA;
        } else {
            value.customCreditsPlacement = EndpoemConfig.CREDITS_PLACEMENT_AFTER_POEM;
        }
    }

    /**
     * Migrates both the released two-axis schema and the short-lived
     * development schema. Canonical fields explicitly present in the file win
     * over released legacy values; the transitional visibility flag is folded
     * into the canonical {@code off} placement.
     */
    static void applyCreditsMigration(EndpoemConfig value, JsonObject root) {
        boolean hasLegacy = root.has("creditsMode") || root.has("creditsPlacement");
        if (hasLegacy) {
            EndpoemConfig migrated = new EndpoemConfig();
            migrateLegacyCredits(
                    migrated,
                    stringMember(
                            root,
                            "creditsMode",
                            LEGACY_CREDITS_MODE_VANILLA
                    ),
                    stringMember(
                            root,
                            "creditsPlacement",
                            EndpoemConfig.CREDITS_PLACEMENT_AFTER_POEM
                    )
            );
            if (!root.has("showVanillaCredits")) {
                value.showVanillaCredits = migrated.showVanillaCredits;
            }
            if (!root.has("customCreditsPlacement")) {
                value.customCreditsPlacement = migrated.customCreditsPlacement;
            }
        }

        JsonElement transitionalVisibility = root.get("showCustomCredits");
        if (transitionalVisibility != null
                && transitionalVisibility.isJsonPrimitive()
                && transitionalVisibility.getAsJsonPrimitive().isBoolean()
                && !transitionalVisibility.getAsBoolean()) {
            value.customCreditsPlacement = EndpoemConfig.CREDITS_PLACEMENT_OFF;
        } else if (transitionalVisibility != null
                && transitionalVisibility.isJsonPrimitive()
                && transitionalVisibility.getAsJsonPrimitive().isBoolean()
                && transitionalVisibility.getAsBoolean()
                && !root.has("customCreditsPlacement")
                && !hasLegacy) {
            value.customCreditsPlacement = EndpoemConfig.CREDITS_PLACEMENT_AFTER_POEM;
        }
    }

    private static String stringMember(
            JsonObject root,
            String member,
            String fallback
    ) {
        JsonElement element = root.get(member);
        return element != null && element.isJsonPrimitive()
                ? element.getAsString()
                : fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float sanitizeScrollSpeed(float value) {
        if (!Float.isFinite(value)) {
            return 1.0F;
        }
        return Math.max(0.25F, Math.min(4.0F, value));
    }
}
