package io.github.niubima.endpoemfabric.stats;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;

public final class EndpoemStats {
    public static final Identifier ENDPOEM_COMMANDS_TRIGGERED =
            Identifier.fromNamespaceAndPath(Endpoemfabric.MODID, "endpoem_commands_triggered");

    private EndpoemStats() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.CUSTOM_STAT, ENDPOEM_COMMANDS_TRIGGERED, ENDPOEM_COMMANDS_TRIGGERED);
        Stats.CUSTOM.get(ENDPOEM_COMMANDS_TRIGGERED, StatFormatter.DEFAULT);
    }
}
