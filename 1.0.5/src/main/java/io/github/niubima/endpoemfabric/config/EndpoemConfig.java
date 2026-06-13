package io.github.niubima.endpoemfabric.config;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = Endpoemfabric.MODID)
public class EndpoemConfig implements ConfigData {

    @ConfigEntry.Category("rules")
    @ConfigEntry.Gui.Tooltip
    public int permissionLevel = 2;

    @ConfigEntry.Category("rules")
    @ConfigEntry.Gui.Tooltip
    public boolean allowSpectator = true;

    @ConfigEntry.Category("rules")
    @ConfigEntry.Gui.Tooltip
    public int cooldownSeconds = 10;

    @ConfigEntry.Category("privacy")
    @ConfigEntry.Gui.Tooltip
    public boolean acceptEndpoem = true;

    @ConfigEntry.Category("poem")
    @ConfigEntry.Gui.Tooltip
    public boolean useCustomEndPoem = false;
}
