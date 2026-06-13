package io.github.niubima.endpoemfabric.client.config;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.client.CustomEndPoem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;

import java.io.IOException;

public final class EndPoemEditorScreen extends Screen {
    private final Screen parent;
    private EditBoxWidget editor;
    private String text;
    private Text status = Text.empty();
    private int statusColor = 0xFFFFFF;

    public EndPoemEditorScreen(Screen parent) {
        super(Text.translatable("text.endpoemfabric.editor.title"));
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

        editor = EditBoxWidget.builder()
                .x(editorX)
                .y(editorY)
                .hasBackground(true)
                .hasOverlay(true)
                .placeholder(Text.translatable("text.endpoemfabric.editor.placeholder"))
                .build(textRenderer, editorWidth, editorHeight, title);
        editor.setMaxLength(200_000);
        editor.setMaxLines(10_000);
        editor.setText(text);
        editor.setChangeListener(value -> {
            text = value;
            status = Text.empty();
        });
        addDrawableChild(editor);
        setInitialFocus(editor);

        int buttonY = height - 32;
        addDrawableChild(ButtonWidget.builder(Text.translatable("text.endpoemfabric.editor.save"), button -> save())
                .dimensions(width / 2 - 155, buttonY, 150, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions(width / 2 + 5, buttonY, 150, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xC0101010);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
        if (!status.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 52, statusColor);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.hasControlDown() && keyCode == 83) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private String loadText() {
        try {
            return CustomEndPoem.readText();
        } catch (IOException e) {
            Endpoemfabric.LOGGER.warn("Failed to read custom End Poem for editor.", e);
            status = Text.translatable("text.endpoemfabric.editor.load_failed");
            statusColor = 0xFF5555;
            return "";
        }
    }

    private void save() {
        try {
            CustomEndPoem.writeText(text == null ? "" : text);
            status = Text.translatable("text.endpoemfabric.editor.saved");
            statusColor = 0x55FF55;
        } catch (IOException e) {
            Endpoemfabric.LOGGER.warn("Failed to save custom End Poem.", e);
            status = Text.translatable("text.endpoemfabric.editor.save_failed");
            statusColor = 0xFF5555;
        }
    }
}
