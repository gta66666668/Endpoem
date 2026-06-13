package io.github.niubima.endpoemfabric.client.config;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.client.CustomEndPoem;
import io.github.niubima.endpoemfabric.config.EndpoemConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public final class EditEndPoemEntry extends AbstractConfigListEntry<Object> {
    private static final int BUTTON_WIDTH = 108;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;
    private final ButtonWidget editButton;
    private final ButtonWidget externalButton;

    public EditEndPoemEntry() {
        super(Text.translatable("text.autoconfig.endpoemfabric.option.editCustomEndPoem"), false);
        this.editButton = ButtonWidget.builder(
                        Text.translatable("text.autoconfig.endpoemfabric.button.edit"),
                        widget -> openEditor())
                .dimensions(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.externalButton = ButtonWidget.builder(
                        Text.translatable("text.autoconfig.endpoemfabric.button.open_external"),
                        widget -> openExternal())
                .dimensions(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
    }

    @Override
    public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        context.drawTextWithShadow(client.textRenderer, getFieldName(), x, y + 6, getPreferredTextColor());
        int externalX = x + entryWidth - BUTTON_WIDTH;
        int editX = externalX - BUTTON_GAP - BUTTON_WIDTH;
        editButton.setDimensionsAndPosition(BUTTON_WIDTH, BUTTON_HEIGHT, editX, y);
        externalButton.setDimensionsAndPosition(BUTTON_WIDTH, BUTTON_HEIGHT, externalX, y);
        editButton.render(context, mouseX, mouseY, tickDelta);
        externalButton.render(context, mouseX, mouseY, tickDelta);
    }

    @Override
    public int getItemHeight() {
        return 24;
    }

    @Override
    public Object getValue() {
        return null;
    }

    @Override
    public Optional<Object> getDefaultValue() {
        return Optional.empty();
    }

    @Override
    public List<? extends Element> children() {
        return List.of(editButton, externalButton);
    }

    @Override
    public List<? extends Selectable> narratables() {
        return List.of(editButton, externalButton);
    }

    private static void openEditor() {
        enableCustomEndPoem();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(new EndPoemEditorScreen(client.currentScreen));
        }
    }

    private static void openExternal() {
        enableCustomEndPoem();
        try {
            CustomEndPoem.readText();
            Util.getOperatingSystem().open(CustomEndPoem.getPath());
        } catch (IOException e) {
            Endpoemfabric.LOGGER.warn("Failed to open custom End Poem file externally.", e);
        }
    }

    private static void enableCustomEndPoem() {
        AutoConfig.getConfigHolder(EndpoemConfig.class).getConfig().useCustomEndPoem = true;
        AutoConfig.getConfigHolder(EndpoemConfig.class).save();
    }
}
