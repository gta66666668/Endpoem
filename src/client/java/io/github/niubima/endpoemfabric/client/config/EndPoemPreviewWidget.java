package io.github.niubima.endpoemfabric.client.config;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** A scrollable, read-only rendering of an End Poem with legacy formatting applied. */
public final class EndPoemPreviewWidget extends AbstractTextAreaWidget {
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final Font font;
    private final List<FormattedCharSequence> lines = new ArrayList<>();

    public EndPoemPreviewWidget(Font font, int x, int y, int width, int height, Component message, String text) {
        super(x, y, width, height, message, AbstractScrollArea.defaultSettings(font.lineHeight / 2), true, true);
        this.font = font;
        addPreviewLines(text == null ? "" : text);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = getInnerLeft();
        int y = getInnerTop();
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, x, y, TEXT_COLOR, true);
            y += font.lineHeight;
        }
    }

    @Override
    protected int getInnerHeight() {
        return Math.max(font.lineHeight, lines.size() * font.lineHeight);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("text.endpoemfabric.editor.preview_read_only"));
    }

    private void addPreviewLines(String text) {
        int maxWidth = Math.max(1, getWidth() - totalInnerPadding());
        String[] sourceLines = text.split("\\n", -1);
        for (String sourceLine : sourceLines) {
            List<FormattedCharSequence> wrapped = font.split(parseLegacyFormatting(sourceLine), maxWidth);
            if (wrapped.isEmpty()) {
                lines.add(FormattedCharSequence.EMPTY);
            } else {
                lines.addAll(wrapped);
            }
        }
    }

    private static Component parseLegacyFormatting(String text) {
        MutableComponent result = Component.empty();
        StringBuilder currentText = new StringBuilder();
        Style style = Style.EMPTY;

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == ChatFormatting.PREFIX_CODE && index + 1 < text.length()) {
                ChatFormatting formatting = ChatFormatting.getByCode(text.charAt(index + 1));
                if (formatting != null) {
                    append(result, currentText, style);
                    style = formatting == ChatFormatting.RESET
                            ? Style.EMPTY
                            : style.applyLegacyFormat(formatting);
                    index++;
                    continue;
                }
            }
            currentText.append(character);
        }
        append(result, currentText, style);
        return result;
    }

    private static void append(MutableComponent result, StringBuilder text, Style style) {
        if (!text.isEmpty()) {
            result.append(Component.literal(text.toString()).withStyle(style));
            text.setLength(0);
        }
    }
}
