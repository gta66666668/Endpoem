package io.github.niubima.endpoemfabric.client;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.config.EndpoemConfig;
import io.github.niubima.endpoemfabric.config.EndpoemConfigManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Finds and plays a user-provided End Poem soundtrack without registering the
 * configuration directory as a resource pack.
 */
public final class CustomEndPoemMusic {
    private static final List<String> SUPPORTED_EXTENSIONS = List.of("ogg", "wav", "wave", "mp3");

    private static volatile CustomEndPoemMusicSound activeSound;
    private static volatile Path activePath;

    private CustomEndPoemMusic() {
    }

    public static void initialize() {
        try {
            Files.createDirectories(getDirectory());
        } catch (IOException e) {
            Endpoemfabric.LOGGER.warn("Failed to create custom End Poem music directory at {}", getDirectory(), e);
        }
    }

    public static synchronized void prepareForEndPoem() {
        if (!EndpoemConfig.BACKGROUND_MUSIC_CUSTOM.equals(EndpoemConfigManager.get().backgroundMusic)) {
            stopForEndPoem();
            return;
        }

        Path path = findValidatedMusicPath();
        if (path == null) {
            stopForEndPoem();
            return;
        }
        if (path.equals(activePath) && activeSound != null) {
            return;
        }

        stopForEndPoem();

        CustomEndPoemMusicSound sound = new CustomEndPoemMusicSound(path, CustomEndPoemMusic::onPlaybackFailure);
        activePath = path;
        activeSound = sound;

        Minecraft client = Minecraft.getInstance();
        client.getSoundManager().stop(null, SoundSource.MUSIC);
        if (client.getSoundManager().play(sound) == SoundEngine.PlayResult.NOT_STARTED) {
            clearActiveSound(sound);
            Endpoemfabric.LOGGER.warn("Failed to start custom End Poem music from {}", path);
        }
    }

    public static synchronized void stopForEndPoem() {
        CustomEndPoemMusicSound sound = activeSound;
        activeSound = null;
        activePath = null;

        if (sound != null) {
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
    }

    public static boolean reload() {
        return findValidatedMusicPath() != null;
    }

    public static boolean isActive() {
        return activeSound != null;
    }

    public static Path getDirectory() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve(Endpoemfabric.MODID)
                .resolve("music");
    }

    private static Path findValidatedMusicPath() {
        initialize();

        try {
            Path path = findMusicPath();
            if (path == null) {
                return null;
            }

            CustomEndPoemMusicSound.validate(path);
            return path;
        } catch (IOException | RuntimeException e) {
            Endpoemfabric.LOGGER.warn("Failed to load custom End Poem music", e);
            return null;
        }
    }

    private static Path findMusicPath() throws IOException {
        Path directory = getDirectory();
        if (!Files.isDirectory(directory)) {
            return null;
        }

        Path realDirectory = directory.toRealPath();
        List<Path> files;
        try (var paths = Files.list(realDirectory)) {
            files = paths
                    .filter(Files::isRegularFile)
                    .sorted((left, right) -> left.getFileName().toString()
                            .compareToIgnoreCase(right.getFileName().toString()))
                    .toList();
        }

        for (String extension : SUPPORTED_EXTENSIONS) {
            String expectedName = "music." + extension;
            for (Path file : files) {
                if (!file.getFileName().toString().equalsIgnoreCase(expectedName)) {
                    continue;
                }

                try {
                    Path realFile = file.toRealPath();
                    if (realFile.startsWith(realDirectory) && Files.isRegularFile(realFile)) {
                        return realFile;
                    }
                } catch (IOException ignored) {
                    // Ignore a file that disappears or becomes unreadable while scanning.
                }
            }
        }
        return null;
    }

    private static synchronized void onPlaybackFailure(CustomEndPoemMusicSound sound, Throwable error) {
        if (activeSound == sound) {
            clearActiveSound(sound);
            Endpoemfabric.LOGGER.warn("Custom End Poem music stopped because its audio stream failed", error);
        }
    }

    private static void clearActiveSound(CustomEndPoemMusicSound sound) {
        if (activeSound == sound) {
            activeSound = null;
            activePath = null;
        }
    }
}
