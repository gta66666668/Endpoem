package io.github.niubima.endpoemfabric.stats;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.stat.StatFormatter;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;

public final class EndpoemStats {
    public static final Identifier ENDPOEM_COMMANDS_TRIGGERED =
            Identifier.of(Endpoemfabric.MODID, "endpoem_commands_triggered");

    private EndpoemStats() {
    }

    public static void register() {
        Registry.register(Registries.CUSTOM_STAT, ENDPOEM_COMMANDS_TRIGGERED, ENDPOEM_COMMANDS_TRIGGERED);
        Stats.CUSTOM.getOrCreateStat(ENDPOEM_COMMANDS_TRIGGERED, StatFormatter.DEFAULT);
    }
}
