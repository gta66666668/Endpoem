package io.github.niubima.endpoemfabric.config;

public class EndpoemConfig {
    public static final String BACKGROUND_VANILLA = "vanilla";
    public static final String BACKGROUND_BLACK = "black";
    public static final String BACKGROUND_PURPLE = "purple";
    public static final String BACKGROUND_CUSTOM = "custom";
    public static final String BACKGROUND_SCALE_COVER = "cover";
    public static final String BACKGROUND_SCALE_CONTAIN = "contain";
    public static final String BACKGROUND_SCALE_STRETCH = "stretch";

    public int permissionLevel = 2;
    public int cooldownSeconds = 10;
    public boolean acceptEndpoem = true;
    public boolean useCustomEndPoem = false;
    public String backgroundMode = BACKGROUND_VANILLA;
    public String backgroundScale = BACKGROUND_SCALE_COVER;
    public boolean migratedOpenConfigKeyToK = false;
}
