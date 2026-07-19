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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CustomEndPoemBackground {
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(
            "png",
            "jpg",
            "jpeg",
            "bmp",
            "gif"
    );
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
        initialize();

        Path path;
        try {
            path = findBackgroundPath();
        } catch (IOException e) {
            releaseTexture();
            Endpoemfabric.LOGGER.warn("Failed to inspect custom End Poem background directory at {}", getDirectory(), e);
            return false;
        }
        if (path == null) {
            releaseTexture();
            return false;
        }

        NativeImage image = null;
        DynamicTexture texture = null;
        try {
            image = readImage(path);
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

    private static NativeImage readImage(Path path) throws IOException {
        String fileName = path.getFileName().toString();
        if (fileName.regionMatches(true, fileName.length() - 4, ".png", 0, 4)) {
            try (InputStream stream = Files.newInputStream(path)) {
                return NativeImage.read(stream);
            }
        }

        BufferedImage bufferedImage;
        try (InputStream stream = Files.newInputStream(path)) {
            bufferedImage = ImageIO.read(stream);
        }
        if (bufferedImage == null || bufferedImage.getWidth() <= 0 || bufferedImage.getHeight() <= 0) {
            throw new IOException("Unsupported or invalid background image");
        }

        NativeImage nativeImage = new NativeImage(bufferedImage.getWidth(), bufferedImage.getHeight(), false);
        try {
            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();
            int[] pixels = bufferedImage.getRGB(0, 0, width, height, null, 0, width);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    nativeImage.setPixel(x, y, pixels[y * width + x]);
                }
            }
            return nativeImage;
        } catch (RuntimeException e) {
            nativeImage.close();
            throw e;
        } finally {
            bufferedImage.flush();
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
                    config.backgroundScale,
                    config.backgroundCropPercent
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
        try {
            Path path = findBackgroundPath();
            if (path != null) {
                return path;
            }
        } catch (IOException ignored) {
        }
        return getDirectory().resolve("background.png");
    }

    private static boolean renderCustomImage(
            GuiGraphicsExtractor graphics,
            int width,
            int height,
            String scaleMode,
            int cropPercent
    ) {
        if (!loadAttempted && !reload()) {
            return false;
        }
        if (!textureLoaded) {
            return false;
        }

        graphics.fill(0, 0, width, height, 0xFF000000);
        CropRegion crop = createCropRegion(cropPercent);
        switch (scaleMode) {
            case EndpoemConfig.BACKGROUND_SCALE_CONTAIN -> renderContained(graphics, width, height, crop);
            case EndpoemConfig.BACKGROUND_SCALE_STRETCH -> graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    TEXTURE_ID,
                    0,
                    0,
                    crop.x(),
                    crop.y(),
                    width,
                    height,
                    crop.width(),
                    crop.height(),
                    imageWidth,
                    imageHeight
            );
            default -> renderCovered(graphics, width, height, crop);
        }
        return true;
    }

    private static void renderContained(GuiGraphicsExtractor graphics, int width, int height, CropRegion crop) {
        float scale = Math.min(width / (float) crop.width(), height / (float) crop.height());
        int renderedWidth = Math.max(1, Math.round(crop.width() * scale));
        int renderedHeight = Math.max(1, Math.round(crop.height() * scale));
        int x = (width - renderedWidth) / 2;
        int y = (height - renderedHeight) / 2;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE_ID,
                x,
                y,
                crop.x(),
                crop.y(),
                renderedWidth,
                renderedHeight,
                crop.width(),
                crop.height(),
                imageWidth,
                imageHeight
        );
    }

    private static void renderCovered(GuiGraphicsExtractor graphics, int width, int height, CropRegion crop) {
        float screenAspect = width / (float) height;
        float imageAspect = crop.width() / (float) crop.height();
        int sourceX = crop.x();
        int sourceY = crop.y();
        int sourceWidth = crop.width();
        int sourceHeight = crop.height();

        if (imageAspect > screenAspect) {
            sourceWidth = Math.max(1, Math.round(crop.height() * screenAspect));
            sourceX = crop.x() + (crop.width() - sourceWidth) / 2;
        } else if (imageAspect < screenAspect) {
            sourceHeight = Math.max(1, Math.round(crop.width() / screenAspect));
            sourceY = crop.y() + (crop.height() - sourceHeight) / 2;
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

    private static CropRegion createCropRegion(int cropPercent) {
        int cropX = Math.min((imageWidth - 1) / 2, Math.round(imageWidth * cropPercent / 100.0F));
        int cropY = Math.min((imageHeight - 1) / 2, Math.round(imageHeight * cropPercent / 100.0F));
        return new CropRegion(
                cropX,
                cropY,
                Math.max(1, imageWidth - cropX * 2),
                Math.max(1, imageHeight - cropY * 2)
        );
    }

    private static Path findBackgroundPath() throws IOException {
        Path directory = getDirectory();
        if (!Files.isDirectory(directory)) {
            return null;
        }

        List<Path> files;
        try (var paths = Files.list(directory)) {
            files = paths
                    .filter(Files::isRegularFile)
                    .sorted((left, right) -> left.getFileName().toString()
                            .compareToIgnoreCase(right.getFileName().toString()))
                    .toList();
        }

        for (String extension : SUPPORTED_EXTENSIONS) {
            String expectedName = "background." + extension;
            for (Path file : files) {
                if (file.getFileName().toString().equalsIgnoreCase(expectedName)) {
                    return file;
                }
            }
        }
        return null;
    }

    private static void releaseTexture() {
        if (textureLoaded) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE_ID);
        }
        textureLoaded = false;
        imageWidth = 0;
        imageHeight = 0;
    }

    private record CropRegion(int x, int y, int width, int height) {
    }
}
