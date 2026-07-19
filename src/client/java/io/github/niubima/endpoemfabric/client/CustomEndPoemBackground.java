package io.github.niubima.endpoemfabric.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.config.EndpoemConfig;
import io.github.niubima.endpoemfabric.config.EndpoemConfigManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CustomEndPoemBackground {
    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath(
            Endpoemfabric.MODID,
            "custom_end_poem_background"
    );

    private static boolean loadAttempted;
    private static boolean textureLoaded;
    private static int imageWidth;
    private static int imageHeight;

    private CustomEndPoemBackground() {
    }

    public static void initialize() {
        try {
            Files.createDirectories(getDirectory());
        } catch (IOException e) {
            Endpoemfabric.LOGGER.warn("Failed to create custom End Poem background directory at {}", getDirectory(), e);
        }
    }

    public static void prepareForEndPoem() {
        if (EndpoemConfig.BACKGROUND_CUSTOM.equals(EndpoemConfigManager.get().backgroundMode)) {
            reload();
        }
    }

    public static boolean reload() {
        loadAttempted = true;
        Path path = getPath();
        if (!Files.isRegularFile(path)) {
            releaseTexture();
            return false;
        }

        NativeImage image = null;
        DynamicTexture texture = null;
        try (InputStream stream = Files.newInputStream(path)) {
            image = NativeImage.read(stream);
            if (image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new IOException("Background image has invalid dimensions");
            }

            imageWidth = image.getWidth();
            imageHeight = image.getHeight();
            texture = new DynamicTexture(() -> "Endpoem custom background", image);
            Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
            textureLoaded = true;
            return true;
        } catch (IOException | RuntimeException e) {
            if (texture != null) {
                texture.close();
            } else if (image != null) {
                image.close();
            }
            releaseTexture();
            Endpoemfabric.LOGGER.warn("Failed to load custom End Poem background from {}", path, e);
            return false;
        }
    }

    public static boolean render(GuiGraphicsExtractor graphics, int width, int height) {
        EndpoemConfig config = EndpoemConfigManager.get();
        return switch (config.backgroundMode) {
            case EndpoemConfig.BACKGROUND_BLACK -> {
                graphics.fill(0, 0, width, height, 0xFF000000);
                yield true;
            }
            case EndpoemConfig.BACKGROUND_PURPLE -> {
                graphics.fillGradient(0, 0, width, height, 0xFF05010C, 0xFF24083A);
                yield true;
            }
            case EndpoemConfig.BACKGROUND_CUSTOM -> renderCustomImage(
                    graphics,
                    width,
                    height,
                    config.backgroundScale
            );
            default -> false;
        };
    }

    public static Path getDirectory() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve(Endpoemfabric.MODID);
    }

    public static Path getPath() {
        return getDirectory().resolve("background.png");
    }

    private static boolean renderCustomImage(
            GuiGraphicsExtractor graphics,
            int width,
            int height,
            String scaleMode
    ) {
        if (!loadAttempted && !reload()) {
            return false;
        }
        if (!textureLoaded) {
            return false;
        }

        graphics.fill(0, 0, width, height, 0xFF000000);
        switch (scaleMode) {
            case EndpoemConfig.BACKGROUND_SCALE_CONTAIN -> renderContained(graphics, width, height);
            case EndpoemConfig.BACKGROUND_SCALE_STRETCH -> graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    TEXTURE_ID,
                    0,
                    0,
                    0.0F,
                    0.0F,
                    width,
                    height,
                    imageWidth,
                    imageHeight
            );
            default -> renderCovered(graphics, width, height);
        }
        return true;
    }

    private static void renderContained(GuiGraphicsExtractor graphics, int width, int height) {
        float scale = Math.min(width / (float) imageWidth, height / (float) imageHeight);
        int renderedWidth = Math.max(1, Math.round(imageWidth * scale));
        int renderedHeight = Math.max(1, Math.round(imageHeight * scale));
        int x = (width - renderedWidth) / 2;
        int y = (height - renderedHeight) / 2;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE_ID,
                x,
                y,
                0.0F,
                0.0F,
                renderedWidth,
                renderedHeight,
                imageWidth,
                imageHeight
        );
    }

    private static void renderCovered(GuiGraphicsExtractor graphics, int width, int height) {
        float screenAspect = width / (float) height;
        float imageAspect = imageWidth / (float) imageHeight;
        int sourceX = 0;
        int sourceY = 0;
        int sourceWidth = imageWidth;
        int sourceHeight = imageHeight;

        if (imageAspect > screenAspect) {
            sourceWidth = Math.max(1, Math.round(imageHeight * screenAspect));
            sourceX = (imageWidth - sourceWidth) / 2;
        } else if (imageAspect < screenAspect) {
            sourceHeight = Math.max(1, Math.round(imageWidth / screenAspect));
            sourceY = (imageHeight - sourceHeight) / 2;
        }

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE_ID,
                0,
                0,
                sourceX,
                sourceY,
                width,
                height,
                sourceWidth,
                sourceHeight,
                imageWidth,
                imageHeight
        );
    }

    private static void releaseTexture() {
        if (textureLoaded) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE_ID);
        }
        textureLoaded = false;
        imageWidth = 0;
        imageHeight = 0;
    }
}
