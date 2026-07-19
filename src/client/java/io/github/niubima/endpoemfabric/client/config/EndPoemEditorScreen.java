package io.github.niubima.endpoemfabric.client.config;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.client.CustomEndPoem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;

public final class EndPoemEditorScreen extends Screen {
    private final Screen parent;
    private EditorMode mode = EditorMode.PLAIN_TEXT;
    private PlainTextEditBox editor;
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
        int editorHeight = Math.max(120, height - 124);
        int editorX = (width - editorWidth) / 2;
        int editorY = 68;

        addModeButtons();
        if (mode == EditorMode.PLAIN_TEXT) {
            editor = new PlainTextEditBox(font, editorX, editorY, editorWidth, editorHeight, title);
            editor.setCharacterLimit(200_000);
            editor.setLineLimit(10_000);
            editor.setValue(text);
            editor.setValueListener(value -> {
                text = value;
                status = Component.empty();
            });
            addRenderableWidget(editor);
            setInitialFocus(editor);
        } else {
            addRenderableWidget(new EndPoemPreviewWidget(
                    font,
                    editorX,
                    editorY,
                    editorWidth,
                    editorHeight,
                    Component.translatable("text.endpoemfabric.editor.preview"),
                    text
            ));
        }

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
        graphics.centeredText(font, Component.translatable(mode.descriptionKey), width / 2, 54, 0xFFAAAAAA);
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

    private void addModeButtons() {
        int buttonWidth = 120;
        int buttonY = 30;
        Button plainText = Button.builder(Component.translatable("text.endpoemfabric.editor.plain_text"), button -> switchMode(EditorMode.PLAIN_TEXT))
                .bounds(width / 2 - buttonWidth - 3, buttonY, buttonWidth, 20)
                .build();
        plainText.active = mode != EditorMode.PLAIN_TEXT;
        addRenderableWidget(plainText);

        Button preview = Button.builder(Component.translatable("text.endpoemfabric.editor.preview"), button -> switchMode(EditorMode.PREVIEW))
                .bounds(width / 2 + 3, buttonY, buttonWidth, 20)
                .build();
        preview.active = mode != EditorMode.PREVIEW;
        addRenderableWidget(preview);
    }

    private void switchMode(EditorMode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            rebuildWidgets();
        }
    }

    private enum EditorMode {
        PLAIN_TEXT("text.endpoemfabric.editor.plain_text.description"),
        PREVIEW("text.endpoemfabric.editor.preview_read_only");

        private final String descriptionKey;

        EditorMode(String descriptionKey) {
            this.descriptionKey = descriptionKey;
        }
    }
}
