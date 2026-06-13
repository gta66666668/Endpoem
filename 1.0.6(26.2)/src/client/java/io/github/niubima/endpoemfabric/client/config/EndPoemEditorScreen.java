package io.github.niubima.endpoemfabric.client.config;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.client.CustomEndPoem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;

public final class EndPoemEditorScreen extends Screen {
    private final Screen parent;
    private MultiLineEditBox editor;
    private String text;
    private Component status = Component.empty();
    private int statusColor = 0xFFFFFFFF;

    public EndPoemEditorScreen(Screen parent) {
        super(Component.translatable("text.endpoemfabric.editor.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (text == null) {
            text = loadText();
        }

        int editorWidth = Math.min(width - 40, 760);
        int editorHeight = Math.max(120, height - 92);
        int editorX = (width - editorWidth) / 2;
        int editorY = 36;

        editor = MultiLineEditBox.builder()
                .setX(editorX)
                .setY(editorY)
                .setShowBackground(true)
                .setShowDecorations(true)
                .setPlaceholder(Component.translatable("text.endpoemfabric.editor.placeholder"))
                .build(font, editorWidth, editorHeight, title);
        editor.setCharacterLimit(200_000);
        editor.setLineLimit(10_000);
        editor.setValue(text);
        editor.setValueListener(value -> {
            text = value;
            status = Component.empty();
        });
        addRenderableWidget(editor);
        setInitialFocus(editor);

        int buttonY = height - 32;
        addRenderableWidget(Button.builder(Component.translatable("text.endpoemfabric.editor.save"), button -> save())
                .bounds(width / 2 - 155, buttonY, 150, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 + 5, buttonY, 150, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xC0101010);
        graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (!status.getString().isEmpty()) {
            graphics.centeredText(font, status, width / 2, height - 52, statusColor);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDown() && event.key() == GLFW.GLFW_KEY_S) {
            save();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private String loadText() {
        try {
            return CustomEndPoem.readText();
        } catch (IOException e) {
            Endpoemfabric.LOGGER.warn("Failed to read custom End Poem for editor.", e);
            status = Component.translatable("text.endpoemfabric.editor.load_failed");
            statusColor = 0xFFFF5555;
            return "";
        }
    }

    private void save() {
        try {
            CustomEndPoem.writeText(text == null ? "" : text);
            status = Component.translatable("text.endpoemfabric.editor.saved");
            statusColor = 0xFF55FF55;
        } catch (IOException e) {
            Endpoemfabric.LOGGER.warn("Failed to save custom End Poem.", e);
            status = Component.translatable("text.endpoemfabric.editor.save_failed");
            statusColor = 0xFFFF5555;
        }
    }
}
