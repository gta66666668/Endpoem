package io.github.niubima.endpoemfabric.config;

public class EndpoemConfig {
    public static final String BACKGROUND_VANILLA = "vanilla";
    public static final String BACKGROUND_BLACK = "black";
    public static final String BACKGROUND_PURPLE = "purple";
    public static final String BACKGROUND_CUSTOM = "custom";
    public static final String BACKGROUND_SCALE_COVER = "cover";
    public static final String BACKGROUND_SCALE_CONTAIN = "contain";
    public static final String BACKGROUND_SCALE_STRETCH = "stretch";
    public static final String BACKGROUND_MUSIC_CREDITS = "credits";
    public static final String BACKGROUND_MUSIC_END = "end";
    public static final String BACKGROUND_MUSIC_DRAGON = "dragon";
    public static final String BACKGROUND_MUSIC_MENU = "menu";
    public static final String BACKGROUND_MUSIC_CUSTOM = "custom";

    public int permissionLevel = 2;
    public int cooldownSeconds = 10;
    public boolean acceptEndpoem = true;
    public boolean useCustomEndPoem = false;
    public String backgroundMode = BACKGROUND_VANILLA;
    public String backgroundScale = BACKGROUND_SCALE_COVER;
    public int backgroundCropPercent = 0;
    public boolean showEndPoemVignette = true;
    public String backgroundMusic = BACKGROUND_MUSIC_CREDITS;
    public float scrollSpeedMultiplier = 1.0F;
    public boolean migratedOpenConfigKeyToK = false;
}
