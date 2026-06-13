package io.github.niubima.endpoemfabric.client;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.config.EndpoemConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CustomEndPoem {
    private static final String DEFAULT_END_POEM_RESOURCE = "/resourcepacks/chinese_end_poem/assets/minecraft/texts/end.txt";

    private CustomEndPoem() {
    }

    public static void initialize() {
        ensureFileExists();
    }

    public static Reader readerOrOriginal(Reader original) {
        EndpoemConfig config = AutoConfig.getConfigHolder(EndpoemConfig.class).getConfig();
        if (!config.useCustomEndPoem) {
            return original;
        }

        try {
            return new StringReader(readText());
        } catch (IOException e) {
            Endpoemfabric.LOGGER.warn("Failed to load custom End Poem from {}. Falling back to resource pack text.", getPath(), e);
            return original;
        }
    }

    public static String readText() throws IOException {
        ensureFileExists();
        return Files.readString(getPath(), StandardCharsets.UTF_8);
    }

    public static void writeText(String text) throws IOException {
        Path path = getPath();
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    public static Path getPath() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve(Endpoemfabric.MODID)
                .resolve("end.txt");
    }

    private static void ensureFileExists() {
        Path path = getPath();
        if (Files.exists(path)) {
            return;
        }

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, defaultEndPoem(), StandardCharsets.UTF_8);
            Endpoemfabric.LOGGER.info("Created custom End Poem template at {}", path);
        } catch (IOException e) {
            Endpoemfabric.LOGGER.warn("Failed to create custom End Poem template at {}", path, e);
        }
    }

    private static String defaultEndPoem() throws IOException {
        try (InputStream stream = CustomEndPoem.class.getResourceAsStream(DEFAULT_END_POEM_RESOURCE)) {
            if (stream == null) {
                return "PLAYERNAME?\n\nWake up.\n";
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
