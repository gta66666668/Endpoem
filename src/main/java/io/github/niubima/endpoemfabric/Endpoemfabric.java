package io.github.niubima.endpoemfabric;

import io.github.niubima.endpoemfabric.config.EndpoemConfigManager;
import io.github.niubima.endpoemfabric.network.PermissionLevelNetworking;
import io.github.niubima.endpoemfabric.stats.EndpoemStats;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Endpoemfabric implements ModInitializer {
    public static final String MODID = "endpoemfabric";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        EndpoemConfigManager.load();
        EndpoemStats.register();
        PermissionLevelNetworking.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                io.github.niubima.endpoemfabric.commands.EndpoemfabricCommands.register(dispatcher));

        LOGGER.info("Endpoem (Fabric) initialized.");
    }
}
