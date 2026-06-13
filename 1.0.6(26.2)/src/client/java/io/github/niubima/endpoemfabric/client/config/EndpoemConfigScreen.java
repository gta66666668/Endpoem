package io.github.niubima.endpoemfabric.client.config;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.client.CustomEndPoem;
import io.github.niubima.endpoemfabric.config.EndpoemConfig;
import io.github.niubima.endpoemfabric.config.EndpoemConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class EndpoemConfigScreen extends Screen {
    private static final int SECTION_HEIGHT = 18;
    private static final int ROW_MIN_HEIGHT = 38;
    private static final int TAB_HEIGHT = 20;
    private static final int CONTROL_WIDTH = 174;
    private static final int CONTROL_HEIGHT = 20;
    private static final int CONTROL_GAP = 4;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int DESCRIPTION_COLOR = 0xFFAAAAAA;
    private static final int SECTION_COLOR = 0xFFFFE0A0;
    private static final int ERROR_COLOR = 0xFFFF5555;

    private final Screen parent;
    private ConfigCategory selectedCategory;
    private final List<TextLine> textLines = new ArrayList<>();
    private Component status = Component.empty();
    private int statusColor = TEXT_COLOR;
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

        int contentWidth = Math.min(520, Math.max(300, width - 24));
        int left = (width - contentWidth) / 2;
        labelX = left;
        controlX = left + contentWidth - CONTROL_WIDTH;
        contentRight = left + contentWidth;

        boolean canEditServerRules = canEditServerRules();
        if (selectedCategory == ConfigCategory.RULES && !canEditServerRules) {
            selectedCategory = ConfigCategory.PRIVACY;
        }

        EndpoemConfig config = EndpoemConfigManager.get();
        int y = 30;
        y = addTabs(left, contentWidth, y, canEditServerRules);
        y += 8;

        if (selectedCategory == ConfigCategory.RULES && canEditServerRules) {
            y = addSection(y, Component.translatable("text.autoconfig.endpoemfabric.category.rules"));
            y = addSmallIntRow(
                    Component.translatable("text.autoconfig.endpoemfabric.option.permissionLevel"),
                    Component.translatable("text.autoconfig.endpoemfabric.option.permissionLevel.@Tooltip"),
                    y,
                    config.permissionLevel,
                    value -> updateConfig(c -> c.permissionLevel = value),
                    0,
                    4
            );
            y = addBooleanRow(
                    Component.translatable("text.autoconfig.endpoemfabric.option.allowSpectator"),
                    Component.translatable("text.autoconfig.endpoemfabric.option.allowSpectator.@Tooltip"),
                    y,
                    config.allowSpectator,
                    value -> updateConfig(c -> c.allowSpectator = value)
            );
            y = addLargeIntRow(
                    Component.translatable("text.autoconfig.endpoemfabric.option.cooldownSeconds"),
                    Component.translatable("text.autoconfig.endpoemfabric.option.cooldownSeconds.@Tooltip"),
                    y,
                    config.cooldownSeconds,
                    value -> updateConfig(c -> c.cooldownSeconds = value),
                    0,
                    3600
            );
        } else if (selectedCategory == ConfigCategory.PRIVACY) {
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
            addEditorRow(
                    Component.translatable("text.autoconfig.endpoemfabric.option.editCustomEndPoem"),
                    Component.translatable("text.autoconfig.endpoemfabric.option.editCustomEndPoem.@Tooltip"),
                    y
            );
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
            minecraft.setScreen(parent);
        }
    }

    private int addSection(int y, Component label) {
        textLines.add(new TextLine(label.getVisualOrderText(), width / 2, y, SECTION_COLOR, true));
        return y + SECTION_HEIGHT;
    }

    private int addTabs(int x, int contentWidth, int y, boolean includeServerRules) {
        List<ConfigCategory> categories = new ArrayList<>();
        if (includeServerRules) {
            categories.add(ConfigCategory.RULES);
        }
        categories.add(ConfigCategory.PRIVACY);
        categories.add(ConfigCategory.POEM);

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

    private int addSmallIntRow(Component label, Component description, int y, int value, Consumer<Integer> setter, int min, int max) {
        int rowHeight = addLabelAndDescription(label, description, y);
        addRenderableWidget(Button.builder(Component.literal("-"), button -> {
                    setter.accept(Math.max(min, value - 1));
                    rebuildWidgets();
                })
                .bounds(controlX, y, 32, CONTROL_HEIGHT)
                .build());
        addNumericField(label, value, controlX + 32 + CONTROL_GAP, y, 102, min, max, setter);
        addRenderableWidget(Button.builder(Component.literal("+"), button -> {
                    setter.accept(Math.min(max, value + 1));
                    rebuildWidgets();
                })
                .bounds(controlX + 138 + CONTROL_GAP, y, 32, CONTROL_HEIGHT)
                .build());
        return y + rowHeight;
    }

    private int addLargeIntRow(Component label, Component description, int y, int value, Consumer<Integer> setter, int min, int max) {
        int rowHeight = addLabelAndDescription(label, description, y);
        addRenderableWidget(Button.builder(Component.literal("-10"), button -> {
                    setter.accept(Math.max(min, value - 10));
                    rebuildWidgets();
                })
                .bounds(controlX, y, 34, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("-"), button -> {
                    setter.accept(Math.max(min, value - 1));
                    rebuildWidgets();
                })
                .bounds(controlX + 36, y, 24, CONTROL_HEIGHT)
                .build());
        addNumericField(label, value, controlX + 62, y, 50, min, max, setter);
        addRenderableWidget(Button.builder(Component.literal("+"), button -> {
                    setter.accept(Math.min(max, value + 1));
                    rebuildWidgets();
                })
                .bounds(controlX + 114, y, 24, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("+10"), button -> {
                    setter.accept(Math.min(max, value + 10));
                    rebuildWidgets();
                })
                .bounds(controlX + 140, y, 34, CONTROL_HEIGHT)
                .build());
        return y + rowHeight;
    }

    private void addEditorRow(Component label, Component description, int y) {
        addLabelAndDescription(label, description, y);
        addRenderableWidget(Button.builder(Component.translatable("text.autoconfig.endpoemfabric.button.edit"), button -> openEditor())
                .bounds(controlX, y, 84, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("text.autoconfig.endpoemfabric.button.open_external"), button -> openExternal())
                .bounds(controlX + 90, y, 84, CONTROL_HEIGHT)
                .build());
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

    private void addNumericField(Component label, int value, int x, int y, int width, int min, int max, Consumer<Integer> setter) {
        EditBox field = new EditBox(font, x, y, width, CONTROL_HEIGHT, label);
        boolean[] updating = new boolean[1];
        field.setMaxLength(Integer.toString(max).length());
        field.setValue(Integer.toString(value));
        field.setResponder(text -> {
            if (updating[0]) {
                return;
            }

            String digits = text.replaceAll("[^0-9]", "");
            if (!digits.equals(text)) {
                updating[0] = true;
                field.setValue(digits);
                updating[0] = false;
                return;
            }

            if (digits.isEmpty()) {
                return;
            }

            int parsed = clamp(Integer.parseInt(digits), min, max);
            if (!Integer.toString(parsed).equals(digits)) {
                updating[0] = true;
                field.setValue(Integer.toString(parsed));
                updating[0] = false;
            }
            setter.accept(parsed);
        });
        addRenderableWidget(field);
    }

    private void openEditor() {
        EndpoemConfigManager.update(config -> config.useCustomEndPoem = true);
        if (minecraft != null) {
            minecraft.setScreen(new EndPoemEditorScreen(new EndpoemConfigScreen(parent, ConfigCategory.POEM)));
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

    private void updateConfig(Consumer<EndpoemConfig> updater) {
        EndpoemConfigManager.update(updater);
    }

    private static Component booleanText(boolean value) {
        return Component.translatable(value ? "options.on" : "options.off");
    }

    private static boolean canEditServerRules() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.hasSingleplayerServer();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record TextLine(FormattedCharSequence text, int x, int y, int color, boolean centered) {
    }

    private enum ConfigCategory {
        RULES("text.autoconfig.endpoemfabric.category.rules"),
        PRIVACY("text.autoconfig.endpoemfabric.category.privacy"),
        POEM("text.autoconfig.endpoemfabric.category.poem");

        private final String translationKey;

        ConfigCategory(String translationKey) {
            this.translationKey = translationKey;
        }
    }
}
