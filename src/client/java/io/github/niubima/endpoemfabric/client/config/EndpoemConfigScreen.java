package io.github.niubima.endpoemfabric.client.config;

import com.mojang.brigadier.tree.CommandNode;
import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.client.CustomEndPoem;
import io.github.niubima.endpoemfabric.client.CustomEndPoemBackground;
import io.github.niubima.endpoemfabric.config.EndpoemConfig;
import io.github.niubima.endpoemfabric.config.EndpoemConfigManager;
import io.github.niubima.endpoemfabric.network.PermissionLevelNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class EndpoemConfigScreen extends Screen {
    private static final float[] SCROLL_SPEED_PRESETS = {0.5F, 0.75F, 1.0F, 1.5F, 2.0F, 3.0F};
    private static final int[] BACKGROUND_CROP_PRESETS = {0, 5, 10, 15, 20, 25, 30, 35, 40};
    private static final int SECTION_HEIGHT = 18;
    private static final int ROW_MIN_HEIGHT = 38;
    private static final int TAB_HEIGHT = 20;
    private static final int CONTROL_WIDTH = 174;
    private static final int CONTROL_HEIGHT = 20;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int DESCRIPTION_COLOR = 0xFFAAAAAA;
    private static final int SECTION_COLOR = 0xFFFFE0A0;
    private static final int ERROR_COLOR = 0xFFFF5555;

    private final Screen parent;
    private ConfigCategory selectedCategory;
    private final List<TextLine> textLines = new ArrayList<>();
    private Component status = Component.empty();
    private int statusColor = TEXT_COLOR;
    private boolean commandSettingsCandidate;
    private boolean commandSettingsAuthorized;
    private boolean commandSettingsRequestSent;
    private int serverPermissionLevel = -1;
    private int pendingPermissionLevel = -1;
    private int labelX;
    private int controlX;
    private int contentRight;

    private EndpoemConfigScreen(Screen parent) {
        this(parent, ConfigCategory.PRIVACY);
    }

    private EndpoemConfigScreen(Screen parent, ConfigCategory selectedCategory) {
        super(Component.translatable("text.autoconfig.endpoemfabric.title"));
        this.parent = parent;
        this.selectedCategory = selectedCategory;
    }

    public static Screen create(Screen parent) {
        return new EndpoemConfigScreen(parent);
    }

    @Override
    protected void init() {
        textLines.clear();

        boolean candidate = canRequestCommandSettings();
        if (candidate != commandSettingsCandidate) {
            commandSettingsCandidate = candidate;
            commandSettingsAuthorized = false;
            commandSettingsRequestSent = false;
            serverPermissionLevel = -1;
            pendingPermissionLevel = -1;
        }
        if (!commandSettingsAuthorized && selectedCategory == ConfigCategory.COMMAND) {
            selectedCategory = ConfigCategory.PRIVACY;
        }
        if (commandSettingsCandidate && !commandSettingsRequestSent) {
            commandSettingsRequestSent = true;
            ClientPlayNetworking.send(PermissionLevelNetworking.RequestPayload.INSTANCE);
        }

        int contentWidth = Math.min(520, Math.max(300, width - 24));
        int left = (width - contentWidth) / 2;
        labelX = left;
        controlX = left + contentWidth - CONTROL_WIDTH;
        contentRight = left + contentWidth;

        EndpoemConfig config = EndpoemConfigManager.get();
        int y = 30;
        y = addTabs(left, contentWidth, y);
        y += 8;

        if (selectedCategory == ConfigCategory.PRIVACY) {
            y = addSection(y, Component.translatable("text.autoconfig.endpoemfabric.category.privacy"));
            addBooleanRow(
                    Component.translatable("text.autoconfig.endpoemfabric.option.acceptEndpoem"),
                    Component.translatable("text.autoconfig.endpoemfabric.option.acceptEndpoem.@Tooltip"),
                    y,
                    config.acceptEndpoem,
                    value -> updateConfig(c -> c.acceptEndpoem = value)
            );
        } else if (selectedCategory == ConfigCategory.POEM) {
            y = addSection(y, Component.translatable("text.autoconfig.endpoemfabric.category.poem"));
            y = addBooleanRow(
                    Component.translatable("text.autoconfig.endpoemfabric.option.useCustomEndPoem"),
                    Component.translatable("text.autoconfig.endpoemfabric.option.useCustomEndPoem.@Tooltip"),
                    y,
                    config.useCustomEndPoem,
                    value -> updateConfig(c -> c.useCustomEndPoem = value)
            );
            y = addEditorRow(
                    Component.translatable("text.autoconfig.endpoemfabric.option.editCustomEndPoem"),
                    Component.translatable("text.autoconfig.endpoemfabric.option.editCustomEndPoem.@Tooltip"),
                    y
            );
        } else if (selectedCategory == ConfigCategory.BACKGROUND) {
            y = addSection(y, Component.translatable("text.autoconfig.endpoemfabric.category.background"));
            y = addCycleRow(
                    Component.translatable("text.autoconfig.endpoemfabric.option.backgroundMode"),
                    Component.translatable("text.autoconfig.endpoemfabric.option.backgroundMode.@Tooltip"),
                    y,
                    backgroundModeText(config.backgroundMode),
                    () -> cycleBackgroundMode(config.backgroundMode)
            );
            if (EndpoemConfig.BACKGROUND_CUSTOM.equals(config.backgroundMode)) {
                y = addBackgroundLayoutRow(y, config.backgroundScale, config.backgroundCropPercent);
                addBackgroundFileRow(y);
            }
        } else if (selectedCategory == ConfigCategory.PLAYBACK) {
            y = addSection(y, Component.translatable("text.autoconfig.endpoemfabric.category.playback"));
            y = addCycleRow(
                    Component.translatable("text.autoconfig.endpoemfabric.option.scrollSpeed"),
                    Component.translatable("text.autoconfig.endpoemfabric.option.scrollSpeed.@Tooltip"),
                    y,
                    scrollSpeedText(config.scrollSpeedMultiplier),
                    () -> cycleScrollSpeed(config.scrollSpeedMultiplier)
            );
            addPreviewRow(y);
        } else if (selectedCategory == ConfigCategory.COMMAND) {
            y = addSection(y, Component.translatable("text.autoconfig.endpoemfabric.category.command"));
            addPermissionLevelRow(y);
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 75, height - 28, 150, CONTROL_HEIGHT)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xC0101010);
        graphics.centeredText(font, title, width / 2, 14, TEXT_COLOR);
        for (TextLine line : textLines) {
            if (line.centered()) {
                graphics.centeredText(font, line.text(), line.x(), line.y(), line.color());
            } else {
                graphics.text(font, line.text(), line.x(), line.y(), line.color(), true);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (!status.getString().isEmpty()) {
            graphics.centeredText(font, status, width / 2, height - 50, statusColor);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (canRequestCommandSettings() != commandSettingsCandidate) {
            rebuildWidgets();
        }
    }

    public void updatePermissionLevelState(boolean authorized, int permissionLevel) {
        boolean canShow = authorized
                && permissionLevel >= 0
                && permissionLevel <= 4
                && canRequestCommandSettings();
        commandSettingsAuthorized = canShow;
        serverPermissionLevel = canShow ? permissionLevel : -1;

        if (pendingPermissionLevel >= 0 && canShow) {
            status = Component.translatable(
                    "text.autoconfig.endpoemfabric.status.permission_level_updated",
                    permissionLevel
            );
            statusColor = TEXT_COLOR;
        }
        pendingPermissionLevel = -1;

        if (!canShow && selectedCategory == ConfigCategory.COMMAND) {
            selectedCategory = ConfigCategory.PRIVACY;
        }
        rebuildWidgets();
    }

    private int addSection(int y, Component label) {
        textLines.add(new TextLine(label.getVisualOrderText(), width / 2, y, SECTION_COLOR, true));
        return y + SECTION_HEIGHT;
    }

    private int addTabs(int x, int contentWidth, int y) {
        List<ConfigCategory> categories = new ArrayList<>();
        categories.add(ConfigCategory.PRIVACY);
        categories.add(ConfigCategory.POEM);
        categories.add(ConfigCategory.BACKGROUND);
        categories.add(ConfigCategory.PLAYBACK);
        if (commandSettingsAuthorized) {
            categories.add(ConfigCategory.COMMAND);
        }

        int gap = 4;
        int tabWidth = (contentWidth - gap * (categories.size() - 1)) / categories.size();
        int tabX = x;
        for (int i = 0; i < categories.size(); i++) {
            ConfigCategory category = categories.get(i);
            int widthForTab = i == categories.size() - 1 ? x + contentWidth - tabX : tabWidth;
            Button tab = Button.builder(Component.translatable(category.translationKey), button -> {
                        selectedCategory = category;
                        rebuildWidgets();
                    })
                    .bounds(tabX, y, widthForTab, TAB_HEIGHT)
                    .build();
            tab.active = category != selectedCategory;
            addRenderableWidget(tab);
            tabX += widthForTab + gap;
        }
        return y + TAB_HEIGHT;
    }

    private int addBooleanRow(Component label, Component description, int y, boolean value, Consumer<Boolean> setter) {
        int rowHeight = addLabelAndDescription(label, description, y);
        addRenderableWidget(Button.builder(booleanText(value), button -> {
                    setter.accept(!value);
                    rebuildWidgets();
                })
                .bounds(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT)
                .build());
        return y + rowHeight;
    }

    private int addEditorRow(Component label, Component description, int y) {
        int rowHeight = addLabelAndDescription(label, description, y);
        addRenderableWidget(Button.builder(Component.translatable("text.autoconfig.endpoemfabric.button.edit"), button -> openEditor())
                .bounds(controlX, y, 84, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("text.autoconfig.endpoemfabric.button.open_external"), button -> openExternal())
                .bounds(controlX + 90, y, 84, CONTROL_HEIGHT)
                .build());
        return y + rowHeight;
    }

    private int addCycleRow(
            Component label,
            Component description,
            int y,
            Component value,
            Runnable cycle
    ) {
        int rowHeight = addLabelAndDescription(label, description, y);
        addRenderableWidget(Button.builder(value, button -> cycle.run())
                .bounds(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT)
                .build());
        return y + rowHeight;
    }

    private int addBackgroundLayoutRow(int y, String scaleMode, int cropPercent) {
        int rowHeight = addLabelAndDescription(
                Component.translatable("text.autoconfig.endpoemfabric.option.backgroundScale"),
                Component.translatable("text.autoconfig.endpoemfabric.option.backgroundScale.@Tooltip"),
                y
        );
        addRenderableWidget(Button.builder(
                        backgroundScaleText(scaleMode),
                        button -> cycleBackgroundScale(scaleMode)
                )
                .bounds(controlX, y, 84, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal(cropPercent + "%"),
                        button -> cycleBackgroundCrop(cropPercent)
                )
                .bounds(controlX + 90, y, 84, CONTROL_HEIGHT)
                .build());
        return y + rowHeight;
    }

    private void addBackgroundFileRow(int y) {
        addLabelAndDescription(
                Component.translatable("text.autoconfig.endpoemfabric.option.backgroundFile"),
                Component.translatable("text.autoconfig.endpoemfabric.option.backgroundFile.@Tooltip"),
                y
        );
        addRenderableWidget(Button.builder(
                        Component.translatable("text.autoconfig.endpoemfabric.button.open_background_folder"),
                        button -> openBackgroundFolder()
                )
                .bounds(controlX, y, 84, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("text.autoconfig.endpoemfabric.button.reload_background"),
                        button -> reloadBackground()
                )
                .bounds(controlX + 90, y, 84, CONTROL_HEIGHT)
                .build());
    }

    private void addPreviewRow(int y) {
        addLabelAndDescription(
                Component.translatable("text.autoconfig.endpoemfabric.option.playEndPoem"),
                Component.translatable("text.autoconfig.endpoemfabric.option.playEndPoem.@Tooltip"),
                y
        );
        addRenderableWidget(Button.builder(
                        Component.translatable("text.autoconfig.endpoemfabric.button.play_end_poem"),
                        button -> playEndPoemPreview()
                )
                .bounds(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT)
                .build());
    }

    private void addPermissionLevelRow(int y) {
        addLabelAndDescription(
                Component.translatable("text.autoconfig.endpoemfabric.option.permissionLevel"),
                Component.translatable("text.autoconfig.endpoemfabric.option.permissionLevel.@Tooltip"),
                y
        );

        int gap = 4;
        int buttonWidth = (CONTROL_WIDTH - gap * 4) / 5;
        int buttonX = controlX;
        for (int level = 0; level <= 4; level++) {
            int selectedLevel = level;
            int widthForButton = level == 4 ? controlX + CONTROL_WIDTH - buttonX : buttonWidth;
            Button button = Button.builder(Component.literal(Integer.toString(level)), ignored ->
                            updateServerPermissionLevel(selectedLevel))
                    .bounds(buttonX, y, widthForButton, CONTROL_HEIGHT)
                    .build();
            button.active = pendingPermissionLevel < 0 && level != serverPermissionLevel;
            addRenderableWidget(button);
            buttonX += widthForButton + gap;
        }
    }

    private int addLabelAndDescription(Component label, Component description, int y) {
        textLines.add(new TextLine(label.getVisualOrderText(), labelX, y + 1, TEXT_COLOR, false));

        int descriptionY = y + CONTROL_HEIGHT + 4;
        int descriptionWidth = Math.max(64, contentRight - labelX);
        List<FormattedCharSequence> wrappedDescription = font.split(description, descriptionWidth);
        for (int i = 0; i < wrappedDescription.size(); i++) {
            textLines.add(new TextLine(wrappedDescription.get(i), labelX, descriptionY + i * 9, DESCRIPTION_COLOR, false));
        }

        return Math.max(ROW_MIN_HEIGHT, CONTROL_HEIGHT + 12 + wrappedDescription.size() * 9);
    }

    private void openEditor() {
        EndpoemConfigManager.update(config -> config.useCustomEndPoem = true);
        if (minecraft != null) {
            minecraft.gui.setScreen(new EndPoemEditorScreen(new EndpoemConfigScreen(parent, ConfigCategory.POEM)));
        }
    }

    private void openExternal() {
        EndpoemConfigManager.update(config -> config.useCustomEndPoem = true);
        try {
            CustomEndPoem.readText();
            Util.getPlatform().openPath(CustomEndPoem.getPath());
        } catch (IOException e) {
            Endpoemfabric.LOGGER.warn("Failed to open custom End Poem file externally.", e);
            status = Component.translatable("text.endpoemfabric.editor.load_failed");
            statusColor = ERROR_COLOR;
        }
        rebuildWidgets();
    }

    private void openBackgroundFolder() {
        CustomEndPoemBackground.initialize();
        Util.getPlatform().openPath(CustomEndPoemBackground.getDirectory());
    }

    private void reloadBackground() {
        setBackgroundLoadStatus(CustomEndPoemBackground.reload());
        rebuildWidgets();
    }

    private void setBackgroundLoadStatus(boolean loaded) {
        status = Component.translatable(loaded
                ? "text.autoconfig.endpoemfabric.status.background_loaded"
                : "text.autoconfig.endpoemfabric.status.background_missing");
        statusColor = loaded ? TEXT_COLOR : ERROR_COLOR;
    }

    private void clearStatus() {
        status = Component.empty();
        statusColor = TEXT_COLOR;
    }

    private void cycleBackgroundMode(String currentMode) {
        String nextMode = switch (currentMode) {
            case EndpoemConfig.BACKGROUND_VANILLA -> EndpoemConfig.BACKGROUND_BLACK;
            case EndpoemConfig.BACKGROUND_BLACK -> EndpoemConfig.BACKGROUND_PURPLE;
            case EndpoemConfig.BACKGROUND_PURPLE -> EndpoemConfig.BACKGROUND_CUSTOM;
            default -> EndpoemConfig.BACKGROUND_VANILLA;
        };
        updateConfig(config -> config.backgroundMode = nextMode);
        clearStatus();
        if (EndpoemConfig.BACKGROUND_CUSTOM.equals(nextMode)) {
            setBackgroundLoadStatus(CustomEndPoemBackground.reload());
        }
        rebuildWidgets();
    }

    private void cycleBackgroundScale(String currentScale) {
        String nextScale = switch (currentScale) {
            case EndpoemConfig.BACKGROUND_SCALE_COVER -> EndpoemConfig.BACKGROUND_SCALE_CONTAIN;
            case EndpoemConfig.BACKGROUND_SCALE_CONTAIN -> EndpoemConfig.BACKGROUND_SCALE_STRETCH;
            default -> EndpoemConfig.BACKGROUND_SCALE_COVER;
        };
        updateConfig(config -> config.backgroundScale = nextScale);
        rebuildWidgets();
    }

    private void cycleBackgroundCrop(int currentPercent) {
        int nearest = 0;
        for (int i = 1; i < BACKGROUND_CROP_PRESETS.length; i++) {
            if (Math.abs(currentPercent - BACKGROUND_CROP_PRESETS[i])
                    < Math.abs(currentPercent - BACKGROUND_CROP_PRESETS[nearest])) {
                nearest = i;
            }
        }
        int nextPercent = BACKGROUND_CROP_PRESETS[(nearest + 1) % BACKGROUND_CROP_PRESETS.length];
        updateConfig(config -> config.backgroundCropPercent = nextPercent);
        rebuildWidgets();
    }

    private void cycleScrollSpeed(float currentSpeed) {
        int nearest = 0;
        for (int i = 1; i < SCROLL_SPEED_PRESETS.length; i++) {
            if (Math.abs(currentSpeed - SCROLL_SPEED_PRESETS[i])
                    < Math.abs(currentSpeed - SCROLL_SPEED_PRESETS[nearest])) {
                nearest = i;
            }
        }
        float nextSpeed = SCROLL_SPEED_PRESETS[(nearest + 1) % SCROLL_SPEED_PRESETS.length];
        updateConfig(config -> config.scrollSpeedMultiplier = nextSpeed);
        rebuildWidgets();
    }

    private void playEndPoemPreview() {
        if (minecraft == null) {
            return;
        }

        var client = minecraft;
        client.gui.setScreen(new WinScreen(true, () -> client.gui.setScreen(this)));
    }

    private void updateConfig(Consumer<EndpoemConfig> updater) {
        EndpoemConfigManager.update(updater);
    }

    private void updateServerPermissionLevel(int permissionLevel) {
        if (!commandSettingsAuthorized || pendingPermissionLevel >= 0 || !canRequestCommandSettings()) {
            return;
        }

        pendingPermissionLevel = permissionLevel;
        status = Component.translatable(
                "text.autoconfig.endpoemfabric.status.permission_level_updating",
                permissionLevel
        );
        statusColor = TEXT_COLOR;
        ClientPlayNetworking.send(new PermissionLevelNetworking.UpdatePayload(permissionLevel));
        rebuildWidgets();
    }

    private boolean canRequestCommandSettings() {
        if (minecraft == null || minecraft.player == null || minecraft.getConnection() == null
                || !ClientPlayNetworking.canSend(PermissionLevelNetworking.RequestPayload.TYPE)) {
            return false;
        }

        CommandNode<?> levelNode = minecraft.getConnection().getCommands()
                .findNode(List.of("endpoem", "config", "op", "level"));
        return levelNode != null && levelNode.getCommand() != null;
    }

    private static Component booleanText(boolean value) {
        return Component.translatable(value ? "options.on" : "options.off");
    }

    private static Component backgroundModeText(String mode) {
        String suffix = switch (mode) {
            case EndpoemConfig.BACKGROUND_BLACK -> "black";
            case EndpoemConfig.BACKGROUND_PURPLE -> "purple";
            case EndpoemConfig.BACKGROUND_CUSTOM -> "custom";
            default -> "vanilla";
        };
        return Component.translatable("text.autoconfig.endpoemfabric.background_mode." + suffix);
    }

    private static Component backgroundScaleText(String scale) {
        String suffix = switch (scale) {
            case EndpoemConfig.BACKGROUND_SCALE_CONTAIN -> "contain";
            case EndpoemConfig.BACKGROUND_SCALE_STRETCH -> "stretch";
            default -> "cover";
        };
        return Component.translatable("text.autoconfig.endpoemfabric.background_scale." + suffix);
    }

    private static Component scrollSpeedText(float speed) {
        String value = speed == (int) speed ? Integer.toString((int) speed) : Float.toString(speed);
        return Component.literal(value + "×");
    }

    private record TextLine(FormattedCharSequence text, int x, int y, int color, boolean centered) {
    }

    private enum ConfigCategory {
        PRIVACY("text.autoconfig.endpoemfabric.category.privacy"),
        POEM("text.autoconfig.endpoemfabric.category.poem"),
        BACKGROUND("text.autoconfig.endpoemfabric.category.background"),
        PLAYBACK("text.autoconfig.endpoemfabric.category.playback"),
        COMMAND("text.autoconfig.endpoemfabric.category.command");

        private final String translationKey;

        ConfigCategory(String translationKey) {
            this.translationKey = translationKey;
        }
    }
}
