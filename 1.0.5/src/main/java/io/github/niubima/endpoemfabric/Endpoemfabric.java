package io.github.niubima.endpoemfabric;

import io.github.niubima.endpoemfabric.config.EndpoemConfig;
import io.github.niubima.endpoemfabric.stats.EndpoemStats;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Endpoemfabric implements ModInitializer {
    public static final String MODID = "endpoemfabric";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        AutoConfig.register(EndpoemConfig.class, GsonConfigSerializer::new);
        EndpoemStats.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                io.github.niubima.endpoemfabric.commands.EndpoemfabricCommands.register(dispatcher));

        LOGGER.info("Endpoem (Fabric) initialized.");
    }
}
